#!/usr/bin/env bash
set -e
PKG="src/main/java/com/yamyam"
mkdir -p "$PKG/domain" "$PKG/service" src/test/java/com/yamyam

# ── build.gradle: 테스트 출력 보이게 (test 블록만 교체) ──
cat > build.gradle <<'EOF'
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
}

group = 'com.yamyam'
version = '0.0.1-SNAPSHOT'

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

dependencies {
    implementation platform('org.springframework.boot:spring-boot-dependencies:4.1.0')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-cache'
    implementation 'com.github.ben-manes.caffeine:caffeine'
    runtimeOnly 'com.h2database:h2'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(JavaCompile) { options.encoding = 'UTF-8' }
test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true; events "passed", "failed" }
}
EOF

# ── application.yml: DB 커넥션 풀 늘려서 동시성 재현 ──
cat > src/main/resources/application.yml <<'EOF'
spring:
  datasource:
    url: jdbc:h2:mem:yamyam;DB_CLOSE_DELAY=-1
    username: sa
    password:
    hikari:
      maximum-pool-size: 50
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    properties:
      hibernate:
        jdbc:
          batch_size: 500
        order_inserts: true
  cache:
    type: caffeine
    caffeine:
      spec: expireAfterWrite=10m,maximumSize=1000
server:
  port: 8080
EOF

# ── Coupon 엔티티 ──
cat > "$PKG/domain/Coupon.java" <<'EOF'
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
EOF

# ── CouponRepository: 원자적 발급 쿼리 포함 ──
cat > "$PKG/domain/CouponRepository.java" <<'EOF'
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
EOF

# ── CouponService: naive(버그) vs safe(수정) ──
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

    // 락 없음: 읽고 -> 확인 -> 증가 (동시 요청 시 초과 발급 버그)
    @Transactional
    public boolean issueNaive(Long id) {
        Coupon c = repo.findById(id).orElseThrow();
        if (c.getIssuedCount() >= c.getTotalQuantity()) return false;
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}  // 경쟁 구간 재현
        c.setIssuedCount(c.getIssuedCount() + 1);
        repo.save(c);
        return true;
    }

    // 원자적 UPDATE: 한도 초과 발급 원천 차단
    @Transactional
    public boolean issueSafe(Long id) {
        return repo.issueAtomic(id) == 1;
    }
}
EOF

# ── 동시성 테스트 ──
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

    static final int LIMIT = 100;      // 선착순 한도
    static final int ATTEMPTS = 300;   // 동시 요청 수

    @Test
    void 락없으면_초과발급된다() throws Exception {
        Long id = repo.save(new Coupon("여름쿠폰", LIMIT)).getId();
        runConcurrent(ATTEMPTS, () -> service.issueNaive(id));
        int issued = repo.findById(id).orElseThrow().getIssuedCount();
        System.out.println("[락 없음] 발급 수량 = " + issued + " (한도 " + LIMIT + ") -> 초과 발급 버그!");
        assertThat(issued).isGreaterThan(LIMIT);   // 100장 넘게 발급됨 = 버그 재현
    }

    @Test
    void 원자적UPDATE면_정확히_한도만_발급된다() throws Exception {
        Long id = repo.save(new Coupon("여름쿠폰", LIMIT)).getId();
        AtomicInteger success = new AtomicInteger();
        runConcurrent(ATTEMPTS, () -> { if (service.issueSafe(id)) success.incrementAndGet(); });
        int issued = repo.findById(id).orElseThrow().getIssuedCount();
        System.out.println("[원자적 UPDATE] 발급 수량 = " + issued + ", 성공 " + success.get() + " / 시도 " + ATTEMPTS);
        assertThat(issued).isEqualTo(LIMIT);        // 정확히 100장
        assertThat(success.get()).isEqualTo(LIMIT); // 성공도 정확히 100건
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
        start.countDown();   // 모든 스레드 동시 출발
        done.await();
        pool.shutdown();
    }
}
EOF

echo "=== M7 코드 생성 완료! 이제: gradle test ==="