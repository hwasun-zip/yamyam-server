package com.yamyam.service;

/**
 * 추천에 쓴 날씨.
 * source: REALTIME(실시간 조회) / FALLBACK(날씨 API 장애로 기본값 사용) / REQUEST(요청에 직접 넣은 값)
 * 제휴사가 기본값으로 만든 추천인지 구분할 수 있게 한다. (docs/api-spec.md 6.4)
 */
public record Weather(double temp, double precip, double humidity, double wind, String source) {
    public static final String REALTIME = "REALTIME";
    public static final String FALLBACK = "FALLBACK";
    public static final String REQUEST = "REQUEST";
}
