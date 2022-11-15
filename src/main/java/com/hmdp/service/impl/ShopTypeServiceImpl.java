package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private String SHOP_TYPE_KEY = "cache:shop_type";

    @Override
    public Result queryAll() {
        //1.从redis缓存中查找
        //如果缓存中已经存在店铺类型
        if (BooleanUtil.isTrue(stringRedisTemplate.hasKey(SHOP_TYPE_KEY))){
            List<ShopType> shopTypeList = new ArrayList<ShopType>();
            //从缓存中获取所有的店铺类型信息
            Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().rangeWithScores(SHOP_TYPE_KEY, 0, -1);
            log.debug("Set:{}",typedTuples);
            if (typedTuples.isEmpty()){
                log.debug("店铺类型为空");
                return Result.fail("店铺类型为空");
            }
            //取出set的元素，放到list中
            for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
                String type = typedTuple.getValue();
                //log.debug("type:{}",type);
                ShopType shopType = JSONUtil.toBean(type, ShopType.class);
                shopTypeList.add(shopType);
            }
            //返回
            return Result.ok(shopTypeList);
        }

        //2.从数据库中获取到数据，存到Redis缓存中
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        for (ShopType shopType : shopTypeList) {
            stringRedisTemplate.opsForZSet().add(SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopType),shopType.getSort());
        }
        log.debug("从数据库中获取商铺类型:{}",shopTypeList);
        return Result.ok(shopTypeList);
    }
}
