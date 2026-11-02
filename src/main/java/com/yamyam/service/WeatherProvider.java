package com.yamyam.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WeatherProvider {

    private final RestClient client = RestClient.create();

    // 좌표를 약 1km 격자로 반올림한 값을 캐시 키로 사용 → 근처 좌표는 같은 캐시 재사용
    // 날씨 API 장애로 만든 기본값은 캐시하지 않는다 → 장애가 풀리면 다음 요청부터 바로 실시간 날씨로 복구
    @Cacheable(value = "weather",
            key = "T(java.lang.Math).round(#lat*100)/100.0 + ',' + T(java.lang.Math).round(#lng*100)/100.0",
            unless = "#result.source() == 'FALLBACK'")
    public Weather getWeather(double lat, double lng) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + lat + "&longitude=" + lng
                    + "&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m"
                    + "&wind_speed_unit=ms";
            OpenMeteo r = client.get().uri(url).retrieve().body(OpenMeteo.class);
            Current c = r.current();
            System.out.println("[날씨 API 실제 호출] lat=" + lat + ", lng=" + lng);  // 캐시 미스일 때만 찍힘
            return new Weather(c.temperature_2m(), c.precipitation(),
                    c.relative_humidity_2m(), c.wind_speed_10m(), Weather.REALTIME);
        } catch (Exception e) {
            System.out.println("날씨 조회 실패, 기본값 사용: " + e.getMessage());
            return new Weather(20, 0, 60, 2, Weather.FALLBACK);
        }
    }

    public record OpenMeteo(Current current) {}
    public record Current(double temperature_2m, double precipitation,
                          double relative_humidity_2m, double wind_speed_10m) {}
}
