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
    private final CouponRepository couponRepo;

    public DataLoader(RestaurantRepository r, FoodWeatherModelRepository m, CouponRepository c) {
        this.restaurantRepo = r; this.modelRepo = m; this.couponRepo = c;
    }

    @Override
    public void run(String... args) throws Exception {
        loadModels();
        loadRestaurants();
        seedCoupon();
        System.out.println("=== 데이터 적재 완료: 음식점 " + restaurantRepo.count()
                + "곳, 회귀계수 " + modelRepo.count() + "개 ===");
    }

    private void seedCoupon() {
        List<Restaurant> list = restaurantRepo.findByCategoryIn(List.of("냉면"));
        Restaurant r = list.isEmpty() ? null : list.get(0);
        Long rid = (r != null) ? r.getId() : null;
        String rname = (r != null) ? r.getName() : "강남 맛집";
        Coupon saved = couponRepo.save(new Coupon("강남 맛집 선착순 할인 쿠폰", 100, rid, rname, 30));
        System.out.println("=== 쿠폰 준비 완료: ID=" + saved.getId() + ", '" + rname + "' 30% 할인, 선착순 100장 ===");
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
