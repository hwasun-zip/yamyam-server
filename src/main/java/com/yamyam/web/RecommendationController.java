package com.yamyam.web;

import com.yamyam.service.RecommendationService;
import com.yamyam.service.RecommendationService.RecommendationResponse;
import com.yamyam.service.Weather;
import com.yamyam.service.WeatherProvider;
import org.springframework.web.bind.annotation.*;

/**
 * 제휴사 공개 API. API 키 인증과 호출 제한은 PartnerApiKeyInterceptor가 먼저 처리한다.
 * 파라미터 범위는 docs/api-spec.md 3장을 따른다.
 */
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
        range("lat", lat, 33.0, 39.0);
        range("lng", lng, 124.0, 132.0);
        range("radiusKm", radiusKm, 0.1, 10.0);
        range("limit", limit, 0, 50);
        range("topFoods", topFoods, 1, 5);
        if (humidity != null) range("humidity", humidity, 0, 100);
        if (precip != null) range("precip", precip, 0, 500);
        if (wind != null) range("wind", wind, 0, 60);
        if (temp != null) range("temp", temp, -40, 50);

        Weather weather = (temp != null)
                ? new Weather(temp, precip != null ? precip : 0,
                              humidity != null ? humidity : 60,
                              wind != null ? wind : 2, Weather.REQUEST)
                : weatherProvider.getWeather(lat, lng);   // 자동 실시간 조회
        return service.recommend(lat, lng, weather, radiusKm, limit, topFoods);
    }

    private static void range(String field, double v, double min, double max) {
        if (Double.isNaN(v) || v < min || v > max) {
            throw ApiException.invalid(field, field + "는 " + fmt(min) + " ~ " + fmt(max) + " 범위여야 합니다");
        }
    }
    private static String fmt(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
}
