# yamyam-server

날씨 기반 강남구 맛집 추천 **API 서버**.
데이터 분석 프로젝트에서 검증한 "날씨로 음식 수요 예측(Hit@1 58.8%)" 로직을
실시간 REST API로 구현했다. (분석 원본: https://github.com/hwasun-zip/yamyam)

## 기술 스택
Java 25 · Spring Boot 4 · Spring MVC · JPA/Hibernate · H2(→ MySQL 예정) · (예정) Redis

## 실행
```bash
gradle bootRun
```
- Health: `GET /api/v1/health`
- 추천: `GET /api/v1/recommendations?lat=37.4979&lng=127.0276&temp=32&precip=0`
  - 더운 날 → 냉면·팥빙수 / 비 오는 날(precip↑) → 파전 이 상위로

## 동작 원리
1. 날씨값으로 음식별 예측 검색량 계산 (`예측 = 절편 + Σ 계수×날씨`)
2. 음식 간 비교를 위해 z-점수로 순위화
3. 상위 음식 카테고리 음식점을 haversine 거리로 가까운 순 정렬

## 데이터
- 음식점 10,932곳(강남구) + 음식별 회귀계수는 분석 노트북 산출물이며,
  `data-prep/export_data.py`로 추출해 `src/main/resources`에 적재한다.
- 서버는 학습하지 않고, 적재된 계수로 예측만 수행한다(학습/서빙 분리).

## 로드맵
- [x] M1: 실행되는 스타터 서버
- [x] M2: 실제 데이터 적재 + 추천 로직
- [ ] M4: 위경도 반경 검색 최적화 (공간 인덱스/바운딩박스)
- [ ] M5: 기상청 실시간 날씨 API + Redis 캐싱
- [ ] M6: k6 부하 테스트로 성능 실측
