package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    //注入redis
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /*
    开始时间戳
    2022年一月一日*/
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /**
     * 位移位数
     */
    private static final int COUNT_BITS = 32;
    public long nextId(String prefix){
        // 1.生成时间戳，当前时间减去起始时间
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSeconds - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1获取当天的日期，精确到天，防止序列号生成上限
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 2.2自增长
        long count = stringRedisTemplate.opsForValue().increment("icr" + prefix + ":" + date);
        // 3.拼接并返回,拼接方式可以采用位运算，因为返回值为long，拼接count采用或运算
        return timeStamp<<COUNT_BITS | count;
    }
}
