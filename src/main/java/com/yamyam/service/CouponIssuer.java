package com.yamyam.service;

import com.yamyam.domain.CouponIssue;
import com.yamyam.domain.CouponIssueRepository;
import com.yamyam.domain.CouponRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수량 차감과 발급 이력 저장을 하나의 트랜잭션으로 묶는다.
 * - 수량: 조건부 원자적 UPDATE (총 한도 초과 방지)
 * - 1인 1매: (쿠폰, 사용자) 유일 제약 (같은 사람 중복 방지)
 * 이력 저장이 유일 제약에 걸리면 예외로 트랜잭션 전체가 롤백되어, 차감한 수량도 되돌려진다.
 */
@Component
public class CouponIssuer {

    private final CouponRepository couponRepo;
    private final CouponIssueRepository issueRepo;

    public CouponIssuer(CouponRepository c, CouponIssueRepository i) { this.couponRepo = c; this.issueRepo = i; }

    @Transactional
    public CouponIssueResult issue(Long couponId, String userKey) {
        if (issueRepo.existsByCouponIdAndUserKey(couponId, userKey)) {
            return CouponIssueResult.ALREADY_ISSUED;           // 빠른 거절 (동시 요청은 유일 제약이 최종 방어)
        }
        if (couponRepo.issueAtomic(couponId) == 0) {
            return CouponIssueResult.SOLD_OUT;
        }
        issueRepo.saveAndFlush(new CouponIssue(couponId, userKey));  // 중복이면 여기서 예외 → 롤백
        return CouponIssueResult.ISSUED;
    }
}
