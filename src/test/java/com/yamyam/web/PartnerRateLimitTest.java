package com.yamyam.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 키별 분당 호출 제한 (docs/api-spec.md 4장).
 * 시계를 고정해 분이 바뀌는 순간에 테스트가 흔들리지 않게 한다.
 */
class PartnerRateLimitTest {

    static final String KEY = "yk_test_rate";

    /** 분당 한도 3인 키 하나, 지정한 시각에 멈춘 시계로 인터셉터를 만든다. */
    static PartnerApiKeyInterceptor interceptorAt(Instant now) {
        String keys = "rate-partner:test:3:" + PartnerApiKeyInterceptor.sha256(KEY);
        return new PartnerApiKeyInterceptor(keys, "test", Clock.fixed(now, ZoneOffset.UTC));
    }

    static MockHttpServletResponse call(PartnerApiKeyInterceptor it) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/recommendations");
        req.addHeader("X-API-Key", KEY);
        MockHttpServletResponse res = new MockHttpServletResponse();
        it.preHandle(req, res, new Object());
        return res;
    }

    @Test
    void 한도까지는_통과하고_남은_횟수가_줄어든다() {
        PartnerApiKeyInterceptor it = interceptorAt(Instant.parse("2026-11-02T03:00:10Z"));
        assertThat(call(it).getHeader("X-RateLimit-Remaining")).isEqualTo("2");
        assertThat(call(it).getHeader("X-RateLimit-Remaining")).isEqualTo("1");
        assertThat(call(it).getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void 한도를_넘으면_429와_다음_분까지_남은_초를_알려준다() {
        PartnerApiKeyInterceptor it = interceptorAt(Instant.parse("2026-11-02T03:00:10Z"));
        call(it); call(it); call(it);
        assertThatThrownBy(() -> call(it))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ae = (ApiException) e;
                    assertThat(ae.code()).isEqualTo("RATE_LIMITED");
                    assertThat(ae.status().value()).isEqualTo(429);
                    assertThat(ae.headers().get("Retry-After")).isEqualTo("50");   // 03:00:10 → 03:01:00까지 50초
                });
    }

    @Test
    void 다음_분이_되면_다시_호출할_수_있다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-02T03:00:10Z"));
        String keys = "rate-partner:test:3:" + PartnerApiKeyInterceptor.sha256(KEY);
        PartnerApiKeyInterceptor it = new PartnerApiKeyInterceptor(keys, "test", clock);
        call(it); call(it); call(it);
        assertThatThrownBy(() -> call(it)).isInstanceOf(ApiException.class);

        clock.now = clock.now.plusSeconds(50);   // 03:01:00, 새 1분 구간
        assertThat(call(it).getHeader("X-RateLimit-Remaining")).isEqualTo("2");
    }

    /** 테스트 중에 시각을 앞으로 옮길 수 있는 시계 */
    static class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
