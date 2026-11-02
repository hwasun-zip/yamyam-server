package com.yamyam;

import com.yamyam.domain.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 제휴사 API 명세(docs/api-spec.md)대로 동작하는지 확인한다.
 * 날씨는 temp를 직접 넣어 외부 날씨 API를 부르지 않는다.
 */
@SpringBootTest
class PartnerApiTest {

    static final String TEST_KEY = "yk_test_local_demo";
    static final String LIVE_KEY = "yk_live_local_demo";
    static final String URL = "/api/v1/recommendations";

    @Autowired WebApplicationContext ctx;
    @Autowired CouponRepository couponRepo;
    MockMvc mvc;

    @BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(ctx).build(); }

    // ── 2장: 인증 ──
    @Test
    void 키가_없으면_401_API_KEY_MISSING() throws Exception {
        mvc.perform(get(URL).param("lat", "37.4979").param("lng", "127.0276").param("temp", "33"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("API_KEY_MISSING"));
    }

    @Test
    void 등록되지_않은_키는_401_API_KEY_INVALID() throws Exception {
        mvc.perform(get(URL).header("X-API-Key", "yk_test_wrong").param("lat", "37.4979").param("lng", "127.0276"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("API_KEY_INVALID"));
    }

    @Test
    void 운영_키로_테스트_서버를_부르면_403_KEY_SCOPE_DENIED() throws Exception {
        mvc.perform(get(URL).header("X-API-Key", LIVE_KEY).param("lat", "37.4979").param("lng", "127.0276"))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("KEY_SCOPE_DENIED"));
    }

    // ── 3장, 4장: 정상 응답과 호출 제한 헤더 ──
    @Test
    void 올바른_키면_추천과_호출_제한_헤더를_돌려준다() throws Exception {
        mvc.perform(get(URL).header("X-API-Key", TEST_KEY)
                        .param("lat", "37.4979").param("lng", "127.0276").param("temp", "33").param("limit", "3"))
           .andExpect(status().isOk())
           .andExpect(header().string("X-RateLimit-Limit", "30"))
           .andExpect(header().exists("X-RateLimit-Remaining"))
           .andExpect(jsonPath("$.weather.source").value("REQUEST"))
           .andExpect(jsonPath("$.topFoods[0].food").value("냉면"))     // 33도 맑은 날 → 냉면 1위 (분석 노트북과 동일)
           .andExpect(jsonPath("$.restaurants.length()").value(3));
    }

    // ── 5장: 오류 규격 ──
    @Test
    void 범위_밖_위도는_400과_field를_돌려준다() throws Exception {
        mvc.perform(get(URL).header("X-API-Key", TEST_KEY).param("lat", "10").param("lng", "127.0276").param("temp", "20"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
           .andExpect(jsonPath("$.field").value("lat"));
    }

    @Test
    void 음수_limit은_이전에는_500이었지만_이제_400이다() throws Exception {
        mvc.perform(get(URL).header("X-API-Key", TEST_KEY)
                        .param("lat", "37.4979").param("lng", "127.0276").param("temp", "20").param("limit", "-1"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.field").value("limit"));
    }

    @Test
    void 필수값_누락과_형식_오류도_같은_규격으로_돌려준다() throws Exception {
        mvc.perform(get(URL).header("X-API-Key", TEST_KEY).param("lng", "127.0276"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
           .andExpect(jsonPath("$.field").value("lat"));
        mvc.perform(get(URL).header("X-API-Key", TEST_KEY).param("lat", "abc").param("lng", "127.0276"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.field").value("lat"));
    }

    // ── 1장: 쿠폰은 제휴사 API가 아니다 ──
    @Test
    void 쿠폰_API는_API_키가_아니라_본인확인을_요구한다() throws Exception {
        Long couponId = couponRepo.findAll().get(0).getId();
        mvc.perform(post("/api/v1/coupons/" + couponId + "/issue"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    void 없는_쿠폰은_404_NOT_FOUND() throws Exception {
        mvc.perform(get("/api/v1/coupons/999999"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
