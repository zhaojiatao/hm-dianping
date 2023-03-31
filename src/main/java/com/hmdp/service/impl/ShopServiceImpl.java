package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryById(Long id) {
        //防止缓存穿透
//        Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //用逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);

        if(shop==null){
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }



    /**
     * 防止缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id)  {
        String lockKey=LOCK_SHOP_KEY+id;
        Shop shop=null;
        //1、从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2、判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3、存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //未命中且命中空值
        if("".equals(shopJson)){
            return null;
        }

        try {
            //实现缓存重建
            //获取互斥锁
            //判断是否获取成功
            while(true){
                boolean isLock = tryLock(lockKey);
                if(!isLock){
                    //失败则休眠并重试
                    Thread.sleep(50);
                    continue;
                }else{
                    break;
                }
            }

            //成功，根据id查询数据库
            //4、不存在，根据id查询数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);

            //5、不存在，返回错误
            if(shop==null){
                //返回空值，防止缓存穿透
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6、存在，写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //释放互斥锁
            unlock(lockKey);
        }
        return shop;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);


    /**
     * 防止缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isBlank(shopJson)){
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        String lockKey=LOCK_SHOP_KEY+id;

        boolean isLock = tryLock(lockKey);

        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try{
                    saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }

        return shop;
    }






    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }


    /**
     * 通过逻辑过期时间防止缓存击穿
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id,Long expireSeconds){
        Shop shop = getById(id);
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }



    /**
     * 防止缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
        //1、从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2、判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3、存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if("".equals(shopJson)){
            return null;
        }
        //4、不存在，根据id查询数据库
        Shop shop = getById(id);
        //5、不存在，返回错误
        if(shop==null){
            //返回空值，防止缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6、存在，写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }










    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(Objects.isNull(id)){
            return Result.fail("店铺id不可为空");
        }
        //1、先更新数据库
        updateById(shop);
        //2、删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
}
