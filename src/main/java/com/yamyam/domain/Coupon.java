package com.yamyam.domain;

import jakarta.persistence.*;

@Entity
public class Coupon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int totalQuantity;
    private int issuedCount;
    private Long restaurantId;      // 어느 음식점 쿠폰인지
    private String restaurantName;
    private int discountPercent;    // 할인율(%)

    protected Coupon() {}
    // 테스트용 간단 생성자
    public Coupon(String name, int totalQuantity) {
        this(name, totalQuantity, null, null, 0);
    }
    public Coupon(String name, int totalQuantity, Long restaurantId, String restaurantName, int discountPercent) {
        this.name = name; this.totalQuantity = totalQuantity; this.issuedCount = 0;
        this.restaurantId = restaurantId; this.restaurantName = restaurantName; this.discountPercent = discountPercent;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getIssuedCount() { return issuedCount; }
    public void setIssuedCount(int c) { this.issuedCount = c; }
    public Long getRestaurantId() { return restaurantId; }
    public String getRestaurantName() { return restaurantName; }
    public int getDiscountPercent() { return discountPercent; }
    public int getRemaining() { return totalQuantity - issuedCount; }
}
