#!/usr/bin/env bash
set -e
cat > README.md <<'EOF'
# yamyam-server

날씨 기반 강남구 맛집 추천 **API 서버**.
데이터 분석에서 검증한 "날씨로 음식 수요 예측(Hit@1 58.8%)" 로직을,
실시간 외부 날씨 연동과 캐싱을 갖춘 REST API로 구현했다.
(분석 원본: https://github.com/hwasun-zip/yamyam)

## 기술 스택
Java 25 · Spring Boot 4 · Spring MVC · JPA/Hibernate · H2 · Caffeine Cache · Open-Meteo API

## 실행
```bash
gradle bootRun
```
- Health: `GET /api/v1/health`
- 추천: `GET /api/v1/recommendations?lat=37.4979&lng=127.0276`
  - 위치만 주면 실시간 날씨를 조회해 추천 (더운 날 → 냉면·팥빙수 / 비 → 파전)
  - `&temp=32&precip=20` 처럼 날씨를 직접 넣어 테스트도 가능

## 동작 원리
1. 위치(lat/lng)로 **실시간 날씨 조회** (Open-Meteo, 좌표를 1km 격자로 캐싱)
2. 날씨값으로 음식별 예측 검색량 계산 후 **z-점수 순위화** (학습은 오프라인, 서버는 예측만)
3. 상위 음식 카테고리 음식점을 **haversine 거리순**으로 반환

## 성능 최적화 (실측)
매 요청마다 외부 날씨 API를 호출하는 것이 병목이었다.
좌표를 약 1km 격자로 양자화한 캐싱(Caffeine, TTL 10분)으로 이를 해결했다.

| 항목 | 결과 |
| --- | --- |
| 단건 응답 (캐시 미스, 외부 호출 포함) | **1,125 ms** |
| 단건 응답 (캐시 히트) | **9.6 ms** (약 117배 개선) |
| 부하 테스트 (k6, 동시 50, 20초) | **706 req/s**, 총 14,148건 |
| 실패율 | **0%** |
| 응답시간 | p50 52 ms · p95 177 ms |

> 다음 최적화 지점: 부하 시 남은 지연은 외부 호출이 아니라 위경도 조회이므로,
> 공간 인덱스/바운딩박스로 개선 예정.

## 데이터
- 음식점 10,932곳(강남구) + 음식별 회귀계수는 분석 노트북 산출물이며,
  `data-prep/export_data.py`로 추출해 `src/main/resources`에 적재한다.

## 로드맵
- [x] M1 실행되는 스타터 서버
- [x] M2 실제 데이터 적재 + 추천 로직
- [x] M5 실시간 날씨 API 연동 + 캐싱
- [x] M6 k6 부하 테스트 (성능 실측)
- [ ] 위경도 반경 검색 최적화 (공간 인덱스)
- [ ] MySQL 전환 · Redis 캐시로 확장
EOF
echo "=== README 업데이트 완료! ==="