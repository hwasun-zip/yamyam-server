package com.yamyam.web;

import com.yamyam.domain.Coupon;
import com.yamyam.service.CouponIssueResult;
import com.yamyam.service.CouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    /**
     * 본인확인을 마친 사용자의 식별값을 담는 헤더.
     * 실제 서비스에서는 로그인 세션과 본인확인 결과에서 서버가 꺼내 쓰며, 지금은 이 헤더로 대체한다.
     * (docs/auth-design.md 6장)
     */
    static final String USER_HEADER = "X-User-Identity";

    private final CouponService service;
    public CouponController(CouponService service) { this.service = service; }

    @GetMapping("/{id}")
    public CouponView view(@PathVariable Long id) {
        Coupon c = service.get(id);
        return new CouponView(c.getId(), c.getName(), c.getRestaurantName(),
                c.getDiscountPercent(), c.getTotalQuantity(), c.getIssuedCount(), c.getRemaining());
    }

    @PostMapping("/{id}/issue")
    public ResponseEntity<IssueResult> issue(@PathVariable Long id,
            @RequestHeader(value = USER_HEADER, required = false) String identity) {
        if (identity == null || identity.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new IssueResult(false, "IDENTITY_REQUIRED", "본인확인이 필요합니다", service.get(id).getRemaining()));
        }
        CouponIssueResult r = service.issueOnce(id, identity);
        HttpStatus status = (r == CouponIssueResult.ISSUED) ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .body(new IssueResult(r == CouponIssueResult.ISSUED, r.name(), r.message(), service.get(id).getRemaining()));
    }

    public record CouponView(Long id, String name, String restaurantName, int discountPercent,
                             int totalQuantity, int issuedCount, int remaining) {}
    public record IssueResult(boolean success, String code, String message, int remaining) {}
}
