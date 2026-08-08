#!/usr/bin/env bash
set -e
PKG="src/main/java/com/yamyam"
mkdir -p "$PKG/domain" "$PKG/service" "$PKG/config" "$PKG/web"

# ── application.yml (로그 줄이고 배치 삽입) ──
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
server:
  port: 8080
EOF

# ── Restaurant 엔티티 (생성자 추가) ──
cat > "$PKG/domain/Restaurant.java" <<'EOF'
package com.yamyam.domain;

import jakarta.persistence.*;

@Entity
public class Restaurant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String category;
    private Double latitude;
    private Double longitude;
    @Column(length = 50)  private String status;
    @Column(length = 300) private String roadAddress;

    protected Restaurant() {}
    public Restaurant(String name, String category, Double latitude, Double longitude,
                      String status, String roadAddress) {
        this.name = name; this.category = category;
        this.latitude = latitude; this.longitude = longitude;
        this.status = status; this.roadAddress = roadAddress;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getStatus() { return status; }
    public String getRoadAddress() { return roadAddress; }
}
EOF

# ── FoodWeatherModel 엔티티 (생성자 + getter) ──
cat > "$PKG/domain/FoodWeatherModel.java" <<'EOF'
package com.yamyam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FoodWeatherModel {
    @Id
    private String food;
    private Double coefTemp, coefPrecip, coefHumidity, coefWind, intercept, meanPred, stdPred;

    protected FoodWeatherModel() {}
    public FoodWeatherModel(String food, Double coefTemp, Double coefPrecip, Double coefHumidity,
                            Double coefWind, Double intercept, Double meanPred, Double stdPred) {
        this.food = food; this.coefTemp = coefTemp; this.coefPrecip = coefPrecip;
        this.coefHumidity = coefHumidity; this.coefWind = coefWind; this.intercept = intercept;
        this.meanPred = meanPred; this.stdPred = stdPred;
    }
    public String getFood() { return food; }
    public Double getCoefTemp() { return coefTemp; }
    public Double getCoefPrecip() { return coefPrecip; }
    public Double getCoefHumidity() { return coefHumidity; }
    public Double getCoefWind() { return coefWind; }
    public Double getIntercept() { return intercept; }
    public Double getMeanPred() { return meanPred; }
    public Double getStdPred() { return stdPred; }
}
EOF

# ── Repository 2개 ──
cat > "$PKG/domain/RestaurantRepository.java" <<'EOF'
package com.yamyam.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    List<Restaurant> findByCategoryIn(Collection<String> categories);
}
EOF

cat > "$PKG/domain/FoodWeatherModelRepository.java" <<'EOF'
package com.yamyam.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodWeatherModelRepository extends JpaRepository<FoodWeatherModel, String> {}
EOF

# ── DataLoader: 시작 시 CSV -> DB ──
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

    public DataLoader(RestaurantRepository r, FoodWeatherModelRepository m) {
        this.restaurantRepo = r; this.modelRepo = m;
    }

    @Override
    public void run(String... args) throws Exception {
        loadModels();
        loadRestaurants();
        System.out.println("=== 데이터 적재 완료: 음식점 " + restaurantRepo.count()
                + "곳, 회귀계수 " + modelRepo.count() + "개 ===");
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

# ── RecommendationService: 실제 추천 로직 ──
cat > "$PKG/service/RecommendationService.java" <<'EOF'
package com.yamyam.service;

import com.yamyam.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final RestaurantRepository restaurantRepo;
    private final FoodWeatherModelRepository modelRepo;

    public RecommendationService(RestaurantRepository r, FoodWeatherModelRepository m) {
        this.restaurantRepo = r; this.modelRepo = m;
    }

    public RecommendationResponse recommend(double lat, double lng, double temp, double precip,
            double humidity, double wind, double radiusKm, int limit, int topFoodCount) {

        // 1) 음식별 예측 검색량 -> z-점수 순위화
        List<FoodScore> scores = new ArrayList<>();
        for (FoodWeatherModel m : modelRepo.findAll()) {
            double pred = m.getIntercept()
                    + m.getCoefTemp() * temp
                    + m.getCoefPrecip() * precip
                    + m.getCoefHumidity() * humidity
                    + m.getCoefWind() * wind;
            double z = (m.getStdPred() == 0) ? 0 : (pred - m.getMeanPred()) / m.getStdPred();
            scores.add(new FoodScore(m.getFood(), round(z)));
        }
        scores.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<FoodScore> topFoods = scores.stream().limit(topFoodCount).collect(Collectors.toList());
        Set<String> names = topFoods.stream().map(FoodScore::food).collect(Collectors.toSet());

        // 2) 상위 음식 카테고리 음식점을 반경 내에서 가까운 순으로
        List<RestaurantDto> nearby = restaurantRepo.findByCategoryIn(names).stream()
                .map(r -> new RestaurantDto(r.getName(), r.getCategory(),
                        round(haversine(lat, lng, r.getLatitude(), r.getLongitude()))))
                .filter(dto -> dto.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(RestaurantDto::distanceKm))
                .limit(limit)
                .collect(Collectors.toList());

        return new RecommendationResponse(new Weather(temp, precip, humidity, wind), topFoods, nearby);
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.asin(Math.sqrt(a));
    }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    public record RecommendationResponse(Weather weather, List<FoodScore> topFoods, List<RestaurantDto> restaurants) {}
    public record Weather(double temp, double precip, double humidity, double wind) {}
    public record FoodScore(String food, double score) {}
    public record RestaurantDto(String name, String category, double distanceKm) {}
}
EOF

# ── Controller: 서비스 호출하도록 교체 ──
cat > "$PKG/web/RecommendationController.java" <<'EOF'
package com.yamyam.web;

import com.yamyam.service.RecommendationService;
import com.yamyam.service.RecommendationService.RecommendationResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    private final RecommendationService service;
    public RecommendationController(RecommendationService service) { this.service = service; }

    @GetMapping("/recommendations")
    public RecommendationResponse recommend(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "28") double temp,
            @RequestParam(defaultValue = "0") double precip,
            @RequestParam(defaultValue = "60") double humidity,
            @RequestParam(defaultValue = "2") double wind,
            @RequestParam(defaultValue = "3.0") double radiusKm,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "3") int topFoods
    ) {
        return service.recommend(lat, lng, temp, precip, humidity, wind, radiusKm, limit, topFoods);
    }
}
EOF

echo ""
echo "=== M2 코드 생성 완료! 이제: gradle bootRun ==="