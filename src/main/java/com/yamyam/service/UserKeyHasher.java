package com.yamyam.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 본인확인 식별값(설계상 CI) 원문을 서비스 비밀키 기반 HMAC-SHA256으로 변환한다. (docs/auth-design.md 4장, 5장)
 *
 * - CI는 서비스가 달라도 같은 값이라, 원문을 저장하면 유출 시 다른 서비스 이력과 연결될 수 있다.
 * - yamyam만 아는 비밀키로 HMAC을 만들면, yamyam 안에서는 같은 사람이 같은 값이 되지만
 *   저장된 값을 다른 서비스의 CI와 대조할 수 없다. 비밀키 없이는 같은 값을 다시 만들 수도 없다.
 * - 비밀키는 설정 파일이 아니라 환경변수 YAMYAM_USER_KEY_SALT로 주입한다. (application.yml의 값은 로컬 개발용)
 */
@Component
public class UserKeyHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public UserKeyHasher(@Value("${yamyam.user-key-salt}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String rawIdentity) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);   // Mac은 스레드 안전하지 않아 호출마다 새로 만든다
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(rawIdentity.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("식별값 변환 실패", e);
        }
    }
}
