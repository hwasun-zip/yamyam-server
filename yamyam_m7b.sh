#!/usr/bin/env bash
set -e
PKG="src/main/java/com/yamyam"

# ── Coupon: 음식점 연결 필드 추가 ──
cat > "$PKG/domain/Coupon.java" <<'EOF'
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
EOF

# ── CouponService: 조회 메서드 추가 ──
cat > "$PKG/service/CouponService.java" <<'EOF'
package com.yamyam.service;

import com.yamyam.domain.Coupon;
import com.yamyam.domain.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private final CouponRepository repo;
    public CouponService(CouponRepository repo) { this.repo = repo; }

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
EOF

# ── CouponController: 조회/발급 API ──
cat > "$PKG/web/CouponController.java" <<'EOF'
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
EOF

# ── DataLoader: 시작 시 쿠폰도 자동 생성 ──
cat > "$PKG/config/DataLoader.java" <<'EOF'
package com.yamyam.config;

import com.yamyam.domain.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class DataLoader implements CommandLineRunner {

    private final RestaurantRepository restaurantRepo;
    private final FoodWeatherModelRepository modelRepo;
    private final CouponRepository couponRepo;

    public DataLoader(RestaurantRepository r, FoodWeatherModelRepository m, CouponRepository c) {
        this.restaurantRepo = r; this.modelRepo = m; this.couponRepo = c;
    }

    @Override
    public void run(String... args) throws Exception {
        loadModels();
        loadRestaurants();
        seedCoupon();
        System.out.println("=== 데이터 적재 완료: 음식점 " + restaurantRepo.count()
                + "곳, 회귀계수 " + modelRepo.count() + "개 ===");
    }

    private void seedCoupon() {
        List<Restaurant> list = restaurantRepo.findByCategoryIn(List.of("냉면"));
        Restaurant r = list.isEmpty() ? null : list.get(0);
        Long rid = (r != null) ? r.getId() : null;
        String rname = (r != null) ? r.getName() : "강남 맛집";
        Coupon saved = couponRepo.save(new Coupon("강남 맛집 선착순 할인 쿠폰", 100, rid, rname, 30));
        System.out.println("=== 쿠폰 준비 완료: ID=" + saved.getId() + ", '" + rname + "' 30% 할인, 선착순 100장 ===");
    }

    private void loadModels() throws Exception {
        try (BufferedReader br = reader("food_weather_model.csv")) {
            br.readLine();
            String line;
            List<FoodWeatherModel> batch = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> c = parseCsv(line);
                batch.add(new FoodWeatherModel(c.get(0), d(c.get(1)), d(c.get(2)),
                        d(c.get(3)), d(c.get(4)), d(c.get(5)), d(c.get(6)), d(c.get(7))));
            }
            modelRepo.saveAll(batch);
        }
    }

    private void loadRestaurants() throws Exception {
        try (BufferedReader br = reader("restaurants.csv")) {
            br.readLine();
            String line;
            List<Restaurant> batch = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> c = parseCsv(line);
                if (c.size() < 4) continue;
                Double lat = dOrNull(c.get(2)), lng = dOrNull(c.get(3));
                if (lat == null || lng == null) continue;
                batch.add(new Restaurant(c.get(0), c.get(1), lat, lng,
                        c.size() > 4 ? c.get(4) : "", c.size() > 5 ? c.get(5) : ""));
                if (batch.size() >= 1000) { restaurantRepo.saveAll(batch); batch.clear(); }
            }
            if (!batch.isEmpty()) restaurantRepo.saveAll(batch);
        }
    }

    private BufferedReader reader(String name) throws Exception {
        return new BufferedReader(new InputStreamReader(
                new ClassPathResource(name).getInputStream(), StandardCharsets.UTF_8));
    }
    private static double d(String s) { return Double.parseDouble(s.trim()); }
    private static Double dOrNull(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return null; }
    }
    private static List<String> parseCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (q) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; }
                    else q = false;
                } else sb.append(ch);
            } else {
                if (ch == '"') q = true;
                else if (ch == ',') { out.add(sb.toString()); sb.setLength(0); }
                else sb.append(ch);
            }
        }
        out.add(sb.toString());
        return out;
    }
}
EOF

# ── 테스트: 쿠폰 이름만 교체(로직 동일) ──
cat > src/test/java/com/yamyam/CouponConcurrencyTest.java <<'EOF'
package com.yamyam;

import com.yamyam.domain.Coupon;
import com.yamyam.domain.CouponRepository;
import com.yamyam.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired CouponService service;
    @Autowired CouponRepository repo;

    static final int LIMIT = 100;
    static final int ATTEMPTS = 300;

    @Test
    void 락없으면_초과발급된다() throws Exception {
        Long id = repo.save(new Coupon("강남 맛집 선착순 할인 쿠폰", LIMIT)).getId();
        AtomicInteger success = new AtomicInteger();
        runConcurrent(ATTEMPTS, () -> { if (service.issueNaive(id)) success.incrementAndGet(); });
        int issued = repo.findById(id).orElseThrow().getIssuedCount();
        System.out.println("[락 없음] 쿠폰 받은 사람 = " + success.get()
                + " (한도 " + LIMIT + "), 기록된 수량 = " + issued + " -> 초과 발급 + 카운터 붕괴!");
        assertThat(success.get()).isGreaterThan(LIMIT);
    }

    @Test
    void 원자적UPDATE면_정확히_한도만_발급된다() throws Exception {
        Long id = repo.save(new Coupon("강남 맛집 선착순 할인 쿠폰", LIMIT)).getId();
        AtomicInteger success = new AtomicInteger();
        runConcurrent(ATTEMPTS, () -> { if (service.issueSafe(id)) success.incrementAndGet(); });
        int issued = repo.findById(id).orElseThrow().getIssuedCount();
        System.out.println("[원자적 UPDATE] 발급 수량 = " + issued + ", 성공 " + success.get() + " / 시도 " + ATTEMPTS);
        assertThat(issued).isEqualTo(LIMIT);
        assertThat(success.get()).isEqualTo(LIMIT);
    }

    private void runConcurrent(int n, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try { start.await(); task.run(); }
                catch (Exception ignored) {}
                finally { done.countDown(); }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();
    }
}
EOF

echo "=== M7-2 완료! 테스트: gradle test / 데모: gradle bootRun ==="