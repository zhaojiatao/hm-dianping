package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author zhaojiatao
 * @version 1.0.0
 * @date 2023/4/3 14:29
 * @Description
 * @ClassName RedisIdWorker
 * Copyright: Copyright (c) 2022-2023 All Rights Reserved.
 */
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP=1640995200L;
    private static final int COUNT_BITS=32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        //1、生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond- BEGIN_TIMESTAMP;

        //2、生成序列号
        //2.1 获取当前日期，精确到天
        String yyyyMMdd = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + yyyyMMdd);

        //3、拼接并返回

        //这里要使用位运算，十分巧妙
        return timeStamp<<COUNT_BITS|count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }



}
