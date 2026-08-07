#!/usr/bin/env bash
set -e
PKG_DIR="src/main/java/com/yamyam"
mkdir -p "$PKG_DIR/web" "$PKG_DIR/domain" src/main/resources

cat > settings.gradle <<'EOF'
rootProject.name = 'yamyam'
EOF

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
    runtimeOnly 'com.h2database:h2'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.withType(JavaCompile) { options.encoding = 'UTF-8' }
tasks.named('test') { useJUnitPlatform() }
EOF

cat > src/main/resources/application.yml <<'EOF'
spring:
  datasource:
    url: jdbc:h2:mem:yamyam;DB_CLOSE_DELAY=-1
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
server:
  port: 8080
EOF

cat > "$PKG_DIR/YamyamApplication.java" <<'EOF'
package com.yamyam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class YamyamApplication {
    public static void main(String[] args) {
        SpringApplication.run(YamyamApplication.class, args);
    }
}
EOF

cat > "$PKG_DIR/web/HealthController.java" <<'EOF'
package com.yamyam.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "yamyam");
    }
}
EOF

cat > "$PKG_DIR/web/RecommendationController.java" <<'EOF'
package com.yamyam.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    @GetMapping("/recommendations")
    public RecommendationResponse recommend(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1.0") double radiusKm,
            @RequestParam(defaultValue = "10") int limit
    ) {
        Weather weather = new Weather(31.2, 0.0, 55, 2.1);
        List<FoodScore> topFoods = List.of(
                new FoodScore("냉면", 1.83),
                new FoodScore("팥빙수", 1.51)
        );
        List<RestaurantDto> restaurants = List.of(
                new RestaurantDto("설빙 강남역지점", "팥빙수", 0.36),
                new RestaurantDto("동아냉면", "냉면", 0.41)
        );
        return new RecommendationResponse(weather, topFoods, restaurants);
    }

    public record RecommendationResponse(Weather weather, List<FoodScore> topFoods, List<RestaurantDto> restaurants) {}
    public record Weather(double temp, double precip, int humidity, double wind) {}
    public record FoodScore(String food, double score) {}
    public record RestaurantDto(String name, String category, double distanceKm) {}
}
EOF

cat > "$PKG_DIR/domain/Restaurant.java" <<'EOF'
package com.yamyam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Restaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String category;
    private Double latitude;
    private Double longitude;
    private String status;
    private String roadAddress;

    protected Restaurant() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}
EOF

cat > "$PKG_DIR/domain/FoodWeatherModel.java" <<'EOF'
package com.yamyam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FoodWeatherModel {
    @Id
    private String food;
    private Double coefTemp;
    private Double coefPrecip;
    private Double coefHumidity;
    private Double coefWind;
    private Double intercept;
    private Double meanPred;
    private Double stdPred;

    protected FoodWeatherModel() {}
}
EOF

echo ""
echo "=== 완료! 이제 서버 실행: gradle bootRun ==="