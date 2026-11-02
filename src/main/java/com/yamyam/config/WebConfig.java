package com.yamyam.config;

import com.yamyam.web.PartnerApiKeyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 제휴사에 공개하는 추천 API에만 API 키를 요구한다.
 * 쿠폰 API는 제휴사에 열지 않는 자체 채널 전용이라 사용자 본인확인으로 따로 보호한다. (docs/api-spec.md 1장)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PartnerApiKeyInterceptor partnerApiKeyInterceptor;

    public WebConfig(PartnerApiKeyInterceptor i) { this.partnerApiKeyInterceptor = i; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(partnerApiKeyInterceptor).addPathPatterns("/api/v1/recommendations");
    }
}
