#!/usr/bin/env bash
set -e
cat > README.md <<'EOF'
# yamyam-server

날씨 기반 강남구 맛집 추천 **API 서버**.
데이터 분석에서 검증한 "날씨로 음식 수요 예측(Hit@1 58.8%)" 로직을,
실시간 외부 날씨 연동·캐싱·동시성 제어를 갖춘 REST API로 구현했다.
(분석 원본: https://github.com/hwasun-zip/yamyam)

## 기술 스택
Java 25 · Spring Boot 4 · Spring MVC · JPA/Hibernate · H2 · Caffeine Cache · Open-Meteo API · k6

## 실행
```bash
gradle bootRun     # 서버 실행
gradle test        # 동시성 테스트 실행
```

## 주요 API
- `GET /api/v1/recommendations?lat=37.4979&lng=127.0276` — 실시간 날씨 기반 맛집 추천
  - 위치만 주면 실제 날씨 조회 (더운 날 → 냉면·팥빙수 / 비 → 파전), `&temp=32&precip=20`로 수동 테스트 가능
- `GET /api/v1/coupons/{id}` — 맛집 선착순 할인 쿠폰 조회
- `POST /api/v1/coupons/{id}/issue` — 쿠폰 발급 (동시성 안전)

## 동작 원리
1. 위치로 실시간 날씨 조회 (Open-Meteo, 좌표를 1km 격자로 캐싱)
2. 날씨값으로 음식별 예측 검색량 계산 후 z-점수 순위화 (학습은 오프라인, 서버는 예측만)
3. 상위 음식 카테고리 음식점을 haversine 거리순으로 반환

## 성능 최적화 (실측)
매 요청의 외부 날씨 API 호출이 병목이었다. 좌표를 약 1km 격자로 양자화한 캐싱(Caffeine, TTL 10분)으로 해결했다.

| 항목 | 결과 |
| --- | --- |
| 단건 응답 (캐시 미스, 외부 호출 포함) | **1,125 ms** |
| 단건 응답 (캐시 히트) | **9.6 ms** (약 117배 개선) |
| 부하 테스트 (k6, 동시 50, 20초) | **706 req/s**, 총 14,148건 |
| 실패율 | **0%** |
| 응답시간 | p50 52 ms · p95 177 ms |

## 동시성 제어
맛집 선착순 할인 쿠폰(한도 100장) 발급에서 정합성을 보장했다.

- **문제 재현**: 락 없이 구현 시 300개 동시 요청이 한도를 초과 발급하고, 갱신 손실로 카운터가 붕괴(예: 6)
- **해결**: 조건 포함 원자적 UPDATE(`... WHERE issued_count < total`)로 DB 행 단위 직렬화에 위임 → 정확히 100장만 발급
- **검증**: 단위 테스트(CountDownLatch 300스레드) + 실제 API 동시 요청, 두 방식으로 확인
- 계좌 잔액 차감·이체와 동형의 정합성 문제

## 데이터
음식점 10,932곳 + 음식별 회귀계수는 분석 노트북 산출물이며, `data-prep/export_data.py`로 추출해 `src/main/resources`에 적재한다.

## 로드맵
- [x] M1 실행되는 스타터 서버
- [x] M2 실제 데이터 적재 + 추천 로직
- [x] M5 실시간 날씨 API 연동 + 캐싱
- [x] M6 k6 부하 테스트 (성능 실측)
- [x] M7 선착순 쿠폰 동시성 제어
- [ ] 위경도 반경 검색 최적화 (공간 인덱스)
- [ ] MySQL 전환 · Redis 분산락으로 다중 서버 대응
EOF
echo "=== README 업데이트 완료! ==="