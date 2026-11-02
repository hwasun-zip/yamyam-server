package com.yamyam.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {
    boolean existsByCouponIdAndUserKey(Long couponId, String userKey);
    long countByCouponId(Long couponId);
}
