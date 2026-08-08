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
