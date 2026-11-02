package com.yamyam.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 제휴사 API 키 인증 + 키별 분당 호출 제한. (docs/api-spec.md 2장, 4장)
 *
 * - 키 원문은 저장하지 않고 SHA-256 해시로만 비교한다. 설정 파일이 유출되어도 키를 알 수 없다.
 * - 서버 환경(test/live)과 키 종류가 다르면 거절한다. 테스트 키로 운영 서버를 부르는 실수를 막는다.
 * - 호출 제한은 키별 1분 고정 구간으로 센다. 서버 1대 기준이며, 여러 대로 늘리면 Redis 같은 공유 저장소로 옮긴다.
 */
@Component
public class PartnerApiKeyInterceptor implements HandlerInterceptor {

    static final String KEY_HEADER = "X-API-Key";

    record Partner(String name, String type, int limitPerMinute) {}
    private record Window(long minute, AtomicInteger count) {}

    private final Map<String, Partner> partnersByHash = new ConcurrentHashMap<>();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final String environment;
    private final Clock clock;

    /**
     * @param keys 쉼표로 구분한 "이름:종류:분당한도:키해시" 목록 (application.yml의 yamyam.api.keys)
     */
    @Autowired
    public PartnerApiKeyInterceptor(@Value("${yamyam.api.keys}") String keys,
                                    @Value("${yamyam.api.environment}") String environment) {
        this(keys, environment, Clock.systemUTC());
    }

    PartnerApiKeyInterceptor(String keys, String environment, Clock clock) {
        this.environment = environment; this.clock = clock;
        for (String entry : keys.split(",")) {
            String[] p = entry.trim().split(":");
            if (p.length != 4) continue;
            partnersByHash.put(p[3], new Partner(p[0], p[1], Integer.parseInt(p[2])));
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String key = req.getHeader(KEY_HEADER);
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "API_KEY_MISSING", KEY_HEADER + " 헤더가 필요합니다", null, Map.of());
        }
        Partner partner = partnersByHash.get(sha256(key.trim()));
        if (partner == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "API_KEY_INVALID", "유효하지 않거나 폐기된 키입니다", null, Map.of());
        }
        if (!partner.type().equals(environment)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KEY_SCOPE_DENIED",
                    partner.type() + " 키로는 " + environment + " 서버를 호출할 수 없습니다", null, Map.of());
        }

        long nowSec = clock.millis() / 1000;
        long minute = nowSec / 60;
        Window w = windows.compute(partner.name(),
                (k, old) -> (old == null || old.minute() != minute) ? new Window(minute, new AtomicInteger()) : old);
        int used = w.count().incrementAndGet();
        int remaining = Math.max(0, partner.limitPerMinute() - used);

        res.setHeader("X-RateLimit-Limit", String.valueOf(partner.limitPerMinute()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        if (used > partner.limitPerMinute()) {
            String retryAfter = String.valueOf(60 - nowSec % 60);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "분당 호출 한도를 넘었습니다", null,
                    Map.of("Retry-After", retryAfter,
                           "X-RateLimit-Limit", String.valueOf(partner.limitPerMinute()),
                           "X-RateLimit-Remaining", "0"));
        }
        req.setAttribute("partner", partner.name());
        return true;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
