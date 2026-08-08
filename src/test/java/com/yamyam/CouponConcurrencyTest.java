package com.yamyam;

import com.yamyam.domain.Coupon;
import com.yamyam.domain.CouponRepository;
import com.yamyam.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired CouponService service;
    @Autowired CouponRepository repo;

    static final int LIMIT = 100;
    static final int ATTEMPTS = 300;

    @Test
    void 락없으면_초과발급된다() throws Exception {
        Long id = repo.save(new Coupon("여름쿠폰", LIMIT)).getId();
        AtomicInteger success = new AtomicInteger();
        runConcurrent(ATTEMPTS, () -> { if (service.issueNaive(id)) success.incrementAndGet(); });
        int issued = repo.findById(id).orElseThrow().getIssuedCount();
        System.out.println("[락 없음] 쿠폰 받은 사람 = " + success.get()
                + " (한도 " + LIMIT + "), 기록된 수량 = " + issued + " -> 초과 발급 + 카운터 붕괴!");
        assertThat(success.get()).isGreaterThan(LIMIT);   // 한도보다 많은 사람이 받음 = 버그
    }

    @Test
    void 원자적UPDATE면_정확히_한도만_발급된다() throws Exception {
        Long id = repo.save(new Coupon("여름쿠폰", LIMIT)).getId();
        AtomicInteger success = new AtomicInteger();
        runConcurrent(ATTEMPTS, () -> { if (service.issueSafe(id)) success.incrementAndGet(); });
        int issued = repo.findById(id).orElseThrow().getIssuedCount();
        System.out.println("[원자적 UPDATE] 발급 수량 = " + issued + ", 성공 " + success.get() + " / 시도 " + ATTEMPTS);
        assertThat(issued).isEqualTo(LIMIT);
        assertThat(success.get()).isEqualTo(LIMIT);
    }

    private void runConcurrent(int n, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try { start.await(); task.run(); }
                catch (Exception ignored) {}
                finally { done.countDown(); }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
    }
}
