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
