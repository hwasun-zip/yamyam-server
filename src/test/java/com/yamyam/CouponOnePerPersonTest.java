package com.yamyam;

import com.yamyam.domain.Coupon;
import com.yamyam.domain.CouponIssueRepository;
import com.yamyam.domain.CouponRepository;
import com.yamyam.service.CouponIssueResult;
import com.yamyam.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1인 1매 검증 (docs/auth-design.md 4장).
 * 수량 정합성(총 한도)과 공정성(같은 사람 중복 금지)을 동시에 지키는지 확인한다.
 */
@SpringBootTest
class CouponOnePerPersonTest {

    @Autowired CouponService service;
    @Autowired CouponRepository couponRepo;
    @Autowired CouponIssueRepository issueRepo;

    @Test
    void 같은_사람이_동시에_50번_눌러도_한장만_발급된다() throws Exception {
        Long id = couponRepo.save(new Coupon("1인 1매 테스트", 100)).getId();

        Map<CouponIssueResult, Integer> r = runConcurrent(50, i -> () -> service.issueOnce(id, "same-person"));

        int issued = couponRepo.findById(id).orElseThrow().getIssuedCount();
        System.out.println("[같은 사람 50회] 결과 = " + r + ", 차감된 수량 = " + issued);
        assertThat(r.getOrDefault(CouponIssueResult.ISSUED, 0)).isEqualTo(1);
        assertThat(issued).isEqualTo(1);                        // 거절된 요청의 차감은 롤백됨
        assertThat(issueRepo.countByCouponId(id)).isEqualTo(1);
    }

    @Test
    void 서로_다른_300명이면_정확히_한도만큼_발급된다() throws Exception {
        Long id = couponRepo.save(new Coupon("1인 1매 테스트", 100)).getId();

        Map<CouponIssueResult, Integer> r = runConcurrent(300, i -> () -> service.issueOnce(id, "person-" + i));

        int issued = couponRepo.findById(id).orElseThrow().getIssuedCount();
        System.out.println("[서로 다른 300명] 결과 = " + r + ", 차감된 수량 = " + issued);
        assertThat(r.get(CouponIssueResult.ISSUED)).isEqualTo(100);
        assertThat(r.get(CouponIssueResult.SOLD_OUT)).isEqualTo(200);
        assertThat(issued).isEqualTo(100);
        assertThat(issueRepo.countByCouponId(id)).isEqualTo(100);
    }

    @Test
    void 150명이_각자_2번씩_동시에_눌러도_한도와_1인1매를_모두_지킨다() throws Exception {
        Long id = couponRepo.save(new Coupon("1인 1매 테스트", 100)).getId();

        Map<CouponIssueResult, Integer> r = runConcurrent(300, i -> () -> service.issueOnce(id, "person-" + (i % 150)));

        int issued = couponRepo.findById(id).orElseThrow().getIssuedCount();
        System.out.println("[150명 x 2회] 결과 = " + r + ", 차감된 수량 = " + issued);
        assertThat(r.get(CouponIssueResult.ISSUED)).isEqualTo(100);
        assertThat(issued).isEqualTo(100);
        assertThat(issueRepo.countByCouponId(id)).isEqualTo(100);  // 발급 이력 = 서로 다른 100명
    }

    /** n개 요청을 동시에 출발시키고 결과를 종류별로 센다. 예외가 나면 테스트 실패. */
    private Map<CouponIssueResult, Integer> runConcurrent(int n, IntFunction<Callable<CouponIssueResult>> task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        Map<CouponIssueResult, Integer> counts = new ConcurrentHashMap<>();
        java.util.List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<CouponIssueResult> t = task.apply(i);
            futures.add(pool.submit(() -> {
                start.await();
                counts.merge(t.call(), 1, Integer::sum);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get();   // 예상 못 한 예외가 있으면 여기서 드러난다
        pool.shutdown();
        return counts;
    }
}
