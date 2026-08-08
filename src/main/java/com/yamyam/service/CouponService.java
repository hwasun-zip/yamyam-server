package com.yamyam.service;

import com.yamyam.domain.Coupon;
import com.yamyam.domain.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private final CouponRepository repo;
    public CouponService(CouponRepository repo) { this.repo = repo; }

    @Transactional
    public boolean issueNaive(Long id) {
        Coupon c = repo.findById(id).orElseThrow();
        if (c.getIssuedCount() >= c.getTotalQuantity()) return false;
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}
        c.setIssuedCount(c.getIssuedCount() + 1);
        repo.save(c);
        return true;
    }

    @Transactional
    public boolean issueSafe(Long id) {
        return repo.issueAtomic(id) == 1;
    }

    @Transactional(readOnly = true)
    public Coupon get(Long id) {
        return repo.findById(id).orElseThrow();
    }
}
