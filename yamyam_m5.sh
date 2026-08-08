#!/usr/bin/env bash
set -e
PKG="src/main/java/com/yamyam"

# ── Weather 레코드 (별도 파일로 분리) ──
cat > "$PKG/service/Weather.java" <<'EOF'
package com.yamyam.service;

public record Weather(double temp, double precip, double humidity, double wind) {}
EOF

# ── WeatherProvider: Open-Meteo에서 실시간 날씨 조회 ──
cat > "$PKG/service/WeatherProvider.java" <<'EOF'
package com.yamyam.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WeatherProvider {

    private final RestClient client = RestClient.create();

    public Weather getWeather(double lat, double lng) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + lat + "&longitude=" + lng
                    + "&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m"
                    + "&wind_speed_unit=ms";
            JsonNode c = client.get().uri(url).retrieve().body(JsonNode.class).get("current");
            return new Weather(
                    c.get("temperature_2m").asDouble(),
                    c.get("precipitation").asDouble(),
                    c.get("relative_humidity_2m").asDouble(),
                    c.get("wind_speed_10m").asDouble());
        } catch (Exception e) {
            // 외부 API 실패 시 기본값으로 안전하게 (graceful degradation)
            System.out.println("날씨 조회 실패, 기본값 사용: " + e.getMessage());
            return new Weather(20, 0, 60, 2);
        }
    }
}
EOF

# ── RecommendationService: Weather 객체를 받도록 수정 ──
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

    public RecommendationResponse recommend(double lat, double lng, Weather w,
            double radiusKm, int limit, int topFoodCount) {

        List<FoodScore> scores = new ArrayList<>();
        for (FoodWeatherModel m : modelRepo.findAll()) {
            double pred = m.getIntercept()
                    + m.getCoefTemp() * w.temp()
                    + m.getCoefPrecip() * w.precip()
                    + m.getCoefHumidity() * w.humidity()
                    + m.getCoefWind() * w.wind();
            double z = (m.getStdPred() == 0) ? 0 : (pred - m.getMeanPred()) / m.getStdPred();
            scores.add(new FoodScore(m.getFood(), round(z)));
        }
        scores.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<FoodScore> topFoods = scores.stream().limit(topFoodCount).collect(Collectors.toList());
        Set<String> names = topFoods.stream().map(FoodScore::food).collect(Collectors.toSet());

        List<RestaurantDto> nearby = restaurantRepo.findByCategoryIn(names).stream()
                .map(r -> new RestaurantDto(r.getName(), r.getCategory(),
                        round(haversine(lat, lng, r.getLatitude(), r.getLongitude()))))
                .filter(dto -> dto.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(RestaurantDto::distanceKm))
                .limit(limit)
                .collect(Collectors.toList());

        return new RecommendationResponse(w, topFoods, nearby);
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
    public record FoodScore(String food, double score) {}
    public record RestaurantDto(String name, String category, double distanceKm) {}
}
EOF

# ── Controller: 날씨 파라미터 없으면 자동 조회 ──
cat > "$PKG/web/RecommendationController.java" <<'EOF'
package com.yamyam.web;

import com.yamyam.service.RecommendationService;
import com.yamyam.service.RecommendationService.RecommendationResponse;
import com.yamyam.service.Weather;
import com.yamyam.service.WeatherProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    private final RecommendationService service;
    private final WeatherProvider weatherProvider;

    public RecommendationController(RecommendationService s, WeatherProvider w) {
        this.service = s; this.weatherProvider = w;
    }

    @GetMapping("/recommendations")
    public RecommendationResponse recommend(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Double temp,
            @RequestParam(required = false) Double precip,
            @RequestParam(required = false) Double humidity,
            @RequestParam(required = false) Double wind,
            @RequestParam(defaultValue = "3.0") double radiusKm,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "3") int topFoods
    ) {
        Weather weather = (temp != null)
                ? new Weather(temp, precip != null ? precip : 0,
                              humidity != null ? humidity : 60,
                              wind != null ? wind : 2)
                : weatherProvider.getWeather(lat, lng);   // 자동 실시간 조회
        return service.recommend(lat, lng, weather, radiusKm, limit, topFoods);
    }
}
EOF

echo "=== M5-1 완료! 이제: gradle bootRun ==="