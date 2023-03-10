package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) {
//        //缓存穿透
//          Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

//        // 互斥锁缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,LOCK_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if(shop==null){
            Result.fail("商户不存在");
        }
        return Result.ok(shop);
    }
/*    public Shop queryWithLogicalExpire(Long id){
        //热点访问，不用考虑缓存穿透

        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(shopJson)) {//isNotBlank方法，对""判断也是false
            // 2.不存在，返回空
            return null;
        }
        // 4.命中，判断过期时间，需要反序列化json
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        // 5.1判断未过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        // 5.2已过期，缓存重建
        // 6.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY+shop.getId();
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 6.2获取成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    this.saveShop2Redis(id, 20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.3返回信息
        return shop;
    }*/
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {//isNotBlank方法，对""判断也是false
            // 2.命中，返回商户信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断是否未空缓存
        if (shopJson!=null) {
            return null;
        }
        // 3.缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            // 3.1获取互斥锁
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                // 3.2获取不成功，休眠，重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 3.3获取成功
            //再次检测redis缓存是否存在

            //模拟延时
            Thread.sleep(200);
            // 查询数据库
            shop = getById(id);
            if (shop == null) {
                // 不存在，将空值存进redis,缓存时间设置短一点
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 4.不存在，返回错误
                return null;
            }
            // 5.存在，写入redis，设置超时时间，实现超时剔除，增加随机时间，解决缓存雪崩
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + RandomUtil.randomInt(10), TimeUnit.MINUTES);

        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unLock(lockKey);
        }
        // 6.返回商户信息
        return shop;
    }

    //解决缓存穿透封装方法
    /*public Shop queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        // 1.从redis查询商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {//isNotBlank方法，对""判断也是false
            // 2.命中，返回商户信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //未命中，判断是否未空缓存
        if (shopJson!=null) {
            return null;
        }
        // 3.未命中，查询数据库
        Shop shop = getById(id);
        if(shop==null) {
            // 不存在，将空值存进redis,缓存时间设置短一点
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            // 4.不存在，返回错误
            return null;
        }
        // 5.存在，写入redis，设置超时时间，实现超时剔除，增加随机时间，解决缓存雪崩
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL+ RandomUtil.randomInt(10), TimeUnit.MINUTES);
        // 6.返回商户信息

        return shop;
    }*/

    //获取锁方法
    private boolean tryLock(String key){
        //setIfAbsent是指令里的setnx方法
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁方法
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    //存逻辑过期时间至redis
    public void saveShop2Redis(Long id,Long expireSeconds){
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);

        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 1. 判断shop id
        if (shop.getId()==null) {
            return Result.fail("shop的id不能为空");
        }
        // 2.先更新数据库
        updateById(shop);
        // 3.后删除缓存，实现主动更新
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());

        return Result.ok();
    }
}
