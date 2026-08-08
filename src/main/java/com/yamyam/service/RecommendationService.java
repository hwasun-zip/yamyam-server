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
