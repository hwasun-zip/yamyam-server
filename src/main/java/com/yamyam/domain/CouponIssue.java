package com.yamyam.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 쿠폰 발급 이력. (쿠폰, 사용자) 유일 제약으로 1인 1매를 DB가 보장한다.
 * userKey는 본인확인 식별값의 해시이며, 원문은 저장하지 않는다. (docs/auth-design.md 5장)
 */
@Entity
@Table(name = "coupon_issues",
       uniqueConstraints = @UniqueConstraint(name = "uk_coupon_user", columnNames = {"coupon_id", "user_key"}))
public class CouponIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "coupon_id", nullable = false) private Long couponId;
    @Column(name = "user_key", length = 64, nullable = false) private String userKey;
    private LocalDateTime issuedAt;

    protected CouponIssue() {}
    public CouponIssue(Long couponId, String userKey) {
        this.couponId = couponId; this.userKey = userKey; this.issuedAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public Long getCouponId() { return couponId; }
    public String getUserKey() { return userKey; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
}
