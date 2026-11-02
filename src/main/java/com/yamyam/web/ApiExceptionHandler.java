package com.yamyam.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

/**
 * 모든 오류를 같은 형식 {code, message, field}로 돌려준다. (docs/api-spec.md 5장)
 * 제휴사는 code만 보고 재시도 여부를 판단할 수 있다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String code, String message, String field) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handle(ApiException e) {
        HttpHeaders headers = new HttpHeaders();
        e.headers().forEach(headers::add);
        return ResponseEntity.status(e.status()).headers(headers)
                .body(new ApiError(e.code(), e.getMessage(), e.field()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missing(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_PARAMETER",
                e.getParameterName() + "는 필수입니다", e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_PARAMETER",
                e.getName() + "의 형식이 올바르지 않습니다", e.getName()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "대상을 찾을 수 없습니다", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> other(Exception e) {
        // 없는 주소(404), 허용되지 않는 메서드(405) 같은 스프링 기본 오류는 원래 상태 코드를 유지한다
        if (e instanceof ErrorResponse er) {
            HttpStatusCode status = er.getStatusCode();
            return ResponseEntity.status(status).body(new ApiError("HTTP_" + status.value(), e.getMessage(), null));
        }
        e.printStackTrace();
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "서버 내부 오류입니다", null));
    }
}
