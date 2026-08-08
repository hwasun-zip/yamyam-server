package com.yamyam.domain;

import jakarta.persistence.*;

@Entity
public class Coupon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int totalQuantity;   // 총 수량 (선착순 한도)
    private int issuedCount;     // 발급된 수량

    protected Coupon() {}
    public Coupon(String name, int totalQuantity) {
        this.name = name; this.totalQuantity = totalQuantity; this.issuedCount = 0;
    }
    public Long getId() { return id; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getIssuedCount() { return issuedCount; }
    public void setIssuedCount(int c) { this.issuedCount = c; }
}
