package com.yamyam.web;

import com.yamyam.domain.Coupon;
import com.yamyam.service.CouponService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponService service;
    public CouponController(CouponService service) { this.service = service; }

    @GetMapping("/{id}")
    public CouponView view(@PathVariable Long id) {
        Coupon c = service.get(id);
        return new CouponView(c.getId(), c.getName(), c.getRestaurantName(),
                c.getDiscountPercent(), c.getTotalQuantity(), c.getIssuedCount(), c.getRemaining());
    }

    @PostMapping("/{id}/issue")
    public IssueResult issue(@PathVariable Long id) {
        boolean ok = service.issueSafe(id);
        Coupon c = service.get(id);
        return new IssueResult(ok, ok ? "발급 성공" : "품절", c.getRemaining());
    }

    public record CouponView(Long id, String name, String restaurantName, int discountPercent,
                             int totalQuantity, int issuedCount, int remaining) {}
    public record IssueResult(boolean success, String message, int remaining) {}
}
