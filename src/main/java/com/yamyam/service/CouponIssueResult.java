package com.yamyam.service;

public enum CouponIssueResult {
    ISSUED("발급 성공"),
    ALREADY_ISSUED("이미 받은 쿠폰"),
    SOLD_OUT("품절");

    private final String message;
    CouponIssueResult(String message) { this.message = message; }
    public String message() { return message; }
}
