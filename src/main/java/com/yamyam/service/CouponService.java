package com.yamyam.service;

import com.yamyam.domain.Coupon;
import com.yamyam.domain.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private final CouponRepository repo;
    public CouponService(CouponRepository repo) { this.repo = repo; }

    // 락 없음: 읽고 -> 확인 -> 증가 (동시 요청 시 초과 발급 버그)
    @Transactional
    public boolean issueNaive(Long id) {
        Coupon c = repo.findById(id).orElseThrow();
        if (c.getIssuedCount() >= c.getTotalQuantity()) return false;
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}  // 경쟁 구간 재현
        c.setIssuedCount(c.getIssuedCount() + 1);
        repo.save(c);
        return true;
    }

    // 원자적 UPDATE: 한도 초과 발급 원천 차단
    @Transactional
    public boolean issueSafe(Long id) {
        return repo.issueAtomic(id) == 1;
    }
}
