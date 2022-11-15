package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        // Shop shop = queryWithMutex(id);
        //id2 -> getById(id2)==this::getById
        Shop shop = cacheClient.queryWithMutex(RedisConstants.CACHE_SHOP_KEY, id,
                Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);// TODO 单位先改成秒，快速过期
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    public Result updateShop(Shop shop) {
        //1. 先更新数据库
        //1.1获取id
        Long id = shop.getId();
        //1.2如果id为空，则返回
        if (id == null) {
            return Result.fail("该商铺不存在");
        }
        //1.3否则更新数据库
        updateById(shop);
        //2. 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    // /**
    //  * 获取互斥锁
    //  * @param key
    //  * @return ture/flase
    //  */
    // private boolean tryLock(String key){
    //     //使用redis自带的nx
    //     Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1");
    //     return BooleanUtil.isTrue(flag);
    // }
    //
    // /**
    //  * 解开互斥锁
    //  * @param key
    //  */
    // private void unLock(String key){
    //     //将redis中的锁删除
    //     stringRedisTemplate.delete(key);
    // }
    //
    // /**
    //  * 获取互斥锁进行查询
    //  * @param id
    //  * @return
    //  */
    // private Shop queryWithMutex(Long id){
    //     String key = RedisConstants.CACHE_SHOP_KEY+id;
    //     //1.在redis缓存中查找
    //     String shopJSON = stringRedisTemplate.opsForValue().get(key);
    //     // TODO 避免缓存穿透，读到空数据则返回空数据
    //     if (Objects.equals(shopJSON, "")) {
    //         return null;
    //     }
    //     //2.如果找到则返回
    //     if (StrUtil.isNotBlank(shopJSON)) {
    //         return JSONUtil.toBean(shopJSON, Shop.class);
    //     }
    //     //3.没有找到，使用查询函数在数据库中查找
    //     // TODO 4.1先获取互斥锁
    //     String lockKey =RedisConstants.LOCK_SHOP_KEY+id;
    //     Shop shop = null;
    //     try {
    //         //没获取到，休眠,并重试
    //         if (!tryLock(lockKey)) {
    //             Thread.sleep(50);
    //             return queryWithMutex(id);
    //         }
    //         log.debug("获取到互斥锁");
    //         //获取到互斥锁，新建线程去访问数据库
    //         shop = getById(id);
    //         // TODO 模拟重建延迟
    //         Thread.sleep(200);
    //         if (shop == null) {
    //             // TODO 为避免缓存穿透，缓存和数据库中都没有，则插入空缓存
    //             stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
    //             //4.如果数据库中也没找到，返回错误信息
    //             log.debug("在数据库中寻找不到：{}",id);
    //             return null;
    //         }
    //         //5.查询结果存到redis缓存中
    //         stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //     } catch (InterruptedException e) {
    //         throw new RuntimeException(e);
    //     }finally {
    //         // TODO 释放锁
    //         //log.debug("释放互斥锁");
    //         unLock(lockKey);
    //     }
    //     log.debug("在数据库中寻找：{}",shop);
    //     //6.返回查询结果
    //     return shop;
    // }
    //
    // /**
    //  * 保存初版代码用于查询
    //  * @param id
    //  * @return
    //  */
    // private Result queryWithPassThrough(Long id){
    //     log.debug("在缓存按id搜索商铺：{}",id);
    //     String key = RedisConstants.CACHE_SHOP_KEY+id;
    //     //1.在redis缓存中查找
    //     String shopJSON = stringRedisTemplate.opsForValue().get(key);
    //     // TODO 避免缓存穿透，读到空数据则返回空数据
    //     if (Objects.equals(shopJSON, "")) {
    //         return Result.fail("都说不存在该商铺了！");
    //     }
    //     //2.如果找到则返回
    //     if (StrUtil.isNotBlank(shopJSON)) {
    //         Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
    //         return Result.ok(shop);
    //     }
    //     log.debug("在数据库中按id搜索商铺：{}",id);
    //     //3.没有找到，使用查询函数在数据库中查找
    //     Shop shop = getById(id);
    //     if (shop == null) {
    //         // TODO 为避免缓存穿透，缓存和数据库中都没有，则插入空缓存
    //         stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
    //         //4.如果数据库中也没找到，返回错误信息
    //         return Result.fail("不存在该商铺");
    //     }
    //     //5.查询结果存到redis缓存中
    //     log.debug("商铺：{}",shop);
    //     stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //     //6.返回查询结果
    //     return Result.ok(shop);
    // }


}
