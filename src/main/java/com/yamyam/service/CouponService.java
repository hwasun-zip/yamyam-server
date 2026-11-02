package com.yamyam.service;

import com.yamyam.domain.Coupon;
import com.yamyam.domain.CouponRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private final CouponRepository repo;
    private final CouponIssuer issuer;
    private final UserKeyHasher hasher;

    public CouponService(CouponRepository repo, CouponIssuer issuer, UserKeyHasher hasher) {
        this.repo = repo; this.issuer = issuer; this.hasher = hasher;
    }

    /**
     * 1인 1매 발급. rawIdentity는 본인확인을 마친 사용자의 식별값(설계상 CI)이며,
     * 해시만 저장한다. 같은 사람이 동시에 여러 번 요청해도 한 장만 발급된다.
     */
    public CouponIssueResult issueOnce(Long couponId, String rawIdentity) {
        String userKey = hasher.hash(rawIdentity);
        try {
            return issuer.issue(couponId, userKey);
        } catch (DataIntegrityViolationException e) {
            return CouponIssueResult.ALREADY_ISSUED;   // 동시 중복 요청: 유일 제약에 걸려 롤백됨
        }
    }

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
