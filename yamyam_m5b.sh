#!/usr/bin/env bash
set -e
PKG="src/main/java/com/yamyam"

# ── build.gradle: 캐시 + Caffeine 의존성 추가 ──
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
tasks.named('test') { useJUnitPlatform() }
EOF

# ── application.yml: 캐시 TTL 설정 (10분) ──
cat > src/main/resources/application.yml <<'EOF'
spring:
  datasource:
    url: jdbc:h2:mem:yamyam;DB_CLOSE_DELAY=-1
    username: sa
    password:
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

# ── CacheConfig: 캐싱 기능 켜기 ──
cat > "$PKG/config/CacheConfig.java" <<'EOF'
package com.yamyam.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {}
EOF

# ── WeatherProvider: @Cacheable 추가 (좌표를 격자로 반올림해 캐시) ──
cat > "$PKG/service/WeatherProvider.java" <<'EOF'
package com.yamyam.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WeatherProvider {

    private final RestClient client = RestClient.create();

    // 좌표를 약 1km 격자로 반올림한 값을 캐시 키로 사용 → 근처 좌표는 같은 캐시 재사용
    @Cacheable(value = "weather",
            key = "T(java.lang.Math).round(#lat*100)/100.0 + ',' + T(java.lang.Math).round(#lng*100)/100.0")
    public Weather getWeather(double lat, double lng) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + lat + "&longitude=" + lng
                    + "&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m"
                    + "&wind_speed_unit=ms";
            OpenMeteo r = client.get().uri(url).retrieve().body(OpenMeteo.class);
            Current c = r.current();
            System.out.println("[날씨 API 실제 호출] lat=" + lat + ", lng=" + lng);  // 캐시 미스일 때만 찍힘
            return new Weather(c.temperature_2m(), c.precipitation(),
                    c.relative_humidity_2m(), c.wind_speed_10m());
        } catch (Exception e) {
            System.out.println("날씨 조회 실패, 기본값 사용: " + e.getMessage());
            return new Weather(20, 0, 60, 2);
        }
    }

    public record OpenMeteo(Current current) {}
    public record Current(double temperature_2m, double precipitation,
                          double relative_humidity_2m, double wind_speed_10m) {}
}
EOF

echo "=== M5-2 캐싱 완료! 이제: gradle bootRun ==="