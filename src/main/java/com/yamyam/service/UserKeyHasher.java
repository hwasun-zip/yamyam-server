package com.yamyam.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 본인확인 식별값(원문)을 서비스 고유 비밀값(salt)과 섞어 해시한다.
 * DB가 유출되어도 원문이나 다른 서비스의 값과 대조할 수 없게 하기 위함. (docs/auth-design.md 5장)
 */
@Component
public class UserKeyHasher {

    private final String salt;

    public UserKeyHasher(@Value("${yamyam.user-key-salt}") String salt) { this.salt = salt; }

    public String hash(String rawIdentity) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((salt + ":" + rawIdentity).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("해시 생성 실패", e);
        }
    }
}
