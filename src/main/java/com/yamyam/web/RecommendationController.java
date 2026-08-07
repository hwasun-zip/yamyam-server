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
