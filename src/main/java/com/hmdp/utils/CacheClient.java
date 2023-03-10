package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String preFix, ID id, Class<R> type, Function<ID,R> dbCallBack,Long time, TimeUnit unit){
        String key = preFix + id;
        // 1.从redis查询商户信息
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {//isNotBlank方法，对""判断也是false
            // 2.命中，返回商户信息
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        //未命中，判断是否未空缓存
        if (json!=null) {
            return null;
        }
        // 3.未命中，查询数据库
        R r = dbCallBack.apply(id);
        if(r==null) {
            // 不存在，将空值存进redis,缓存时间设置短一点
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            // 4.不存在，返回错误
            return null;
        }
        // 5.存在，写入redis，设置超时时间，实现超时剔除，增加随机时间，解决缓存雪崩
        this.set(key,r,time,unit);
        // 6.返回商户信息
        return r;
    }
    public <R,ID> R queryWithLogicalExpire(String preFix,String cachePreFix,ID id,Class<R> type,Function<ID,R> dbCallBack,Long time, TimeUnit unit){
        //热点访问，不用考虑缓存穿透

        String key = preFix + id;
        // 1.从redis查询商户信息
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(json)) {//isNotBlank方法，对""判断也是false
            // 2.不存在，返回空
            return null;
        }
        // 4.命中，判断过期时间，需要反序列化json
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 5.1判断未过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        // 5.2已过期，缓存重建
        // 6.1获取互斥锁
        String lockKey = cachePreFix+id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 6.2获取成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    // 查询数据库
                    R r1 = dbCallBack.apply(id);
                    //写入redis
                    setWithLogicalExpire(key,r1,time,unit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.3返回信息
        return r;
    }


    private boolean tryLock(String key){
        //setIfAbsent是指令里的setnx方法
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁方法
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
