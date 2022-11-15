package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    //线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Resource
    private RedisIdWorker redisIdWorker;

    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(300);//计数器
        Runnable task=()->{//匿名函数
            for (int i=0;i<100;i++){//执行100次方法
                long id = redisIdWorker.nextId("order");
                System.out.println("id = "+id);
            }
            latch.countDown();//执行完一次函数就减一
        };
        long begin = System.currentTimeMillis();//起始时间
        for (int i = 0; i < 300; i++) {//提交300次任务
            es.submit(task);
        }
        latch.await();//等待所有线程执行完
        long end = System.currentTimeMillis();//结束时间
        System.out.println("time : "+(end-begin));
    }
}
