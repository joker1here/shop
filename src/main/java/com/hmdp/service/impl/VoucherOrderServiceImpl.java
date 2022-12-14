package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService voucherService;
    @Autowired
    private IVoucherOrderService orderService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private CacheClient cacheClient;
    @Override
    public Result secKillOrder(Long voucherId) {
        //1. 查询优惠券
        SeckillVoucher voucher = voucherService.getById(voucherId);
        if (voucher==null){
            return Result.fail("不存在该优惠券");
        }
        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀还没开始");
        }
        //3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束了");
        }
        //4.判断库存是否充足
        if (voucher.getStock()<1) {
            return Result.fail("库存不足");
        }

//        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {//给不同的id加上不同的锁，intern使字符串去寻找相同内容的地址
//            //获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        return tryDo(voucherId);
    }
    //我写的分布式锁
    public Result tryDo(Long voucherId){
        Long userId = UserHolder.getUser().getId();
        String key = "cache:lock:"+userId.toString();
        if (cacheClient.tryLock(key)) {
            //TODO 断点插在这里
            Result result = createVoucherOrder(voucherId);
            cacheClient.unLock(key);
            return result;
        }else {
//            try {
//                Thread.sleep(500);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            return tryDo(voucherId);
            return Result.fail("服务器繁忙！");
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        //判断是否已经下过单了
        Integer count = orderService.query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if (count > 0) {
            return Result.fail("你已经拿到券了");
        }
        //5.扣减库存
        boolean success = voucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)//乐观锁，修改前判断是否大于0，解决超卖问题
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //6.2用户id
        voucherOrder.setUserId(userId);
        //6.3代金券id
        voucherOrder.setVoucherId(voucherId);
        //6.4保存订单
        boolean save = save(voucherOrder);
        if (!save) {
            return Result.fail("库存不足");
        }
        //7.返回订单id
        return Result.ok(orderId);

    }
}
