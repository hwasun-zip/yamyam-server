package com.yamyam.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // 한도 미만일 때만 1 증가 (DB가 행 단위로 직렬화 -> 초과 발급 불가)
    @Modifying
    @Query("update Coupon c set c.issuedCount = c.issuedCount + 1 " +
           "where c.id = :id and c.issuedCount < c.totalQuantity")
    int issueAtomic(@Param("id") Long id);
}
