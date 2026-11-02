package com.yamyam.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 식별값 HMAC 변환 검증 (docs/auth-design.md 4장) */
class UserKeyHasherTest {

    @Test
    void 표준_HMAC_SHA256_값과_일치한다() {
        // RFC 2104 계열의 널리 쓰이는 검증 예시: key="key", message="The quick brown fox jumps over the lazy dog"
        UserKeyHasher h = new UserKeyHasher("key");
        assertThat(h.hash("The quick brown fox jumps over the lazy dog"))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void 같은_사람은_같은_값_서비스_비밀키가_다르면_다른_값() {
        String ci = "test-ci-value";
        assertThat(new UserKeyHasher("yamyam-secret").hash(ci))
                .isEqualTo(new UserKeyHasher("yamyam-secret").hash(ci));      // yamyam 안에서는 같은 사람으로 묶임
        assertThat(new UserKeyHasher("yamyam-secret").hash(ci))
                .isNotEqualTo(new UserKeyHasher("other-service").hash(ci));   // 다른 서비스의 값과 대조 불가
    }

    @Test
    void 결과에_원문이_드러나지_않는다() {
        assertThat(new UserKeyHasher("yamyam-secret").hash("test-ci-value"))
                .doesNotContain("test-ci-value")
                .hasSize(64);
    }
}
