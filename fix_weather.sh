#!/usr/bin/env bash
set -e
cat > src/main/java/com/yamyam/service/WeatherProvider.java <<'EOF'
package com.yamyam.service;

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
            OpenMeteo r = client.get().uri(url).retrieve().body(OpenMeteo.class);
            Current c = r.current();
            return new Weather(c.temperature_2m(), c.precipitation(),
                    c.relative_humidity_2m(), c.wind_speed_10m());
        } catch (Exception e) {
            // 외부 API 실패 시 기본값 (graceful degradation)
            System.out.println("날씨 조회 실패, 기본값 사용: " + e.getMessage());
            return new Weather(20, 0, 60, 2);
        }
    }

    // Open-Meteo 응답을 담는 그릇 (필드명이 JSON 키와 같으면 자동 매핑됨)
    public record OpenMeteo(Current current) {}
    public record Current(double temperature_2m, double precipitation,
                          double relative_humidity_2m, double wind_speed_10m) {}
}
EOF
echo "=== WeatherProvider 수정 완료! 이제: gradle bootRun ==="