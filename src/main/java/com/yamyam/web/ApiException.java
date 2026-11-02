package com.yamyam.web;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** 오류 규격(docs/api-spec.md 5장)에 맞춰 응답할 예외. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;
    private final Map<String, String> headers;

    public ApiException(HttpStatus status, String code, String message, String field, Map<String, String> headers) {
        super(message);
        this.status = status; this.code = code; this.field = field; this.headers = headers;
    }

    public static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, field, Map.of());
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public String field() { return field; }
    public Map<String, String> headers() { return headers; }
}
