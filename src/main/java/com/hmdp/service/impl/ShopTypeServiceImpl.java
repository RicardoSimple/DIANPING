package com.hmdp.service.impl;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_KEY+"type";

        // 1.从redis查询店铺list
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(shopTypeJson)) {
            // 2.存在，返回list
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 3.不存在，从数据库查询list
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 4.将数据存入redis，设置超时时间，实现超时剔除
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 5.返回list
        return Result.ok(typeList);
    }
}
