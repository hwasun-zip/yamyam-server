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
