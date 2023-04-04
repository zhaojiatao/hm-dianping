package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhaojiatao
 * @version 1.0.0
 * @date 2023/4/4 15:41
 * @Description
 * @ClassName RedissonConfig
 * Copyright: Copyright (c) 2022-2023 All Rights Reserved.
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://192.168.136.128:6379")
                .setPassword("zhao0123");
        return Redisson.create(config);
    }

}
