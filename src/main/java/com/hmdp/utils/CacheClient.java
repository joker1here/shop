package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData),time,unit);
    }

    /**
     * 获取互斥锁进行查询
     * @param id
     * @return
     */
    //定义泛型才能使用泛型，定义<R>,使用R作为返回值,ID的类型也可自定义
    //必须要输入.class类型，才能确定要返回什么类型，泛型的推断，
    //传函数，Function<ID,R> dbFallback
    public  <R,ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit unit){
        String key = keyPrefix+id;
        //1.在redis缓存中查找
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        // TODO 避免缓存穿透，读到空数据则返回空数据
        if (Objects.equals(shopJSON, "")) {
            return null;
        }
        //2.如果找到则返回
        if (StrUtil.isNotBlank(shopJSON)) {
            return JSONUtil.toBean(shopJSON, type);
        }
        //3.没有找到，使用查询函数在数据库中查找
        // TODO 4.1先获取互斥锁
        String lockKey =RedisConstants.LOCK_SHOP_KEY+id;
        R r=null;
        try {
            //没获取到，休眠,并重试
            if (!tryLock(lockKey)) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix,id,type,dbFallback,time,unit);
            }
            //log.debug("获取到互斥锁");
            //获取到互斥锁，新建线程去访问数据库
            r = dbFallback.apply(id);
            // TODO 模拟重建延迟
            Thread.sleep(200);
            if (r == null) {
                // TODO 为避免缓存穿透，缓存和数据库中都没有，则插入空缓存
                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                //4.如果数据库中也没找到，返回错误信息
                //log.debug("在数据库中寻找不到：{}",id);
                return null;
            }
            //5.查询结果存到redis缓存中
            set(key,JSONUtil.toJsonStr(r),time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // TODO 释放锁
            unLock(lockKey);
        }
        //log.debug("在数据库中寻找：{}",r);
        //6.返回查询结果
        return r;
    }


    /**
     * 获取互斥锁
     * @param key
     * @return ture/flase
     */
    public boolean tryLock(String key){
        //使用redis自带的nx,顺便设置过期时间为10s防止死锁
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10L,TimeUnit.SECONDS);
        System.out.println(BooleanUtil.isTrue(flag) ? "获取到锁" : "没有获取到锁");
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 解开互斥锁
     * @param key
     */
    public void unLock(String key){
        System.out.println("释放锁");
        //将redis中的锁删除
        stringRedisTemplate.delete(key);
    }
}
