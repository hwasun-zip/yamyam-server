#!/usr/bin/env bash
set -e
mkdir -p data-prep

# ── 데이터 추출 스크립트 (코랩에서 실행한 코드 기록용) ──
cat > data-prep/export_data.py <<'EOF'
"""
YamYam 데이터 추출 스크립트 (Google Colab에서 실행)

분석 노트북(https://github.com/hwasun-zip/yamyam)을 '모두 실행'해
df(날씨+검색량 결합)와 op(강남 음식점 1만곳)가 메모리에 있는 상태에서 실행하면,
서버(src/main/resources)에 넣을 두 개의 CSV를 생성한다.
  - food_weather_model.csv : 음식별 날씨 회귀계수 (서버가 예측에 사용)
  - restaurants.csv        : 강남 음식점 (상호/카테고리/위경도)
"""
import pandas as pd
from sklearn.linear_model import LinearRegression

# 1) 음식별 회귀계수 (빈 값 제거 후 학습)
foods = ["국밥", "칼국수", "냉면", "파전", "팥빙수"]
Xcols = ["기온", "강수", "습도", "풍속"]
rows = []
for f in foods:
    s = df.dropna(subset=Xcols + [f])
    m = LinearRegression().fit(s[Xcols].values, s[f].values)
    pred = m.predict(s[Xcols].values)
    rows.append({
        "food": f,
        "coefTemp": m.coef_[0], "coefPrecip": m.coef_[1],
        "coefHumidity": m.coef_[2], "coefWind": m.coef_[3],
        "intercept": m.intercept_,
        "meanPred": pred.mean(), "stdPred": pred.std(),
    })
pd.DataFrame(rows).to_csv("food_weather_model.csv", index=False)

# 2) 음식점: 상호에 음식 키워드가 있으면 그 음식으로 분류
keyword_map = {
    "냉면": ["냉면"], "칼국수": ["칼국수"], "국밥": ["국밥"],
    "파전": ["파전", "빈대떡", "전집"], "팥빙수": ["빙수", "설빙"],
}
def to_food(name):
    name = str(name)
    for food, kws in keyword_map.items():
        if any(k in name for k in kws):
            return food
    return "기타"

out = pd.DataFrame({
    "name": op["상호"],
    "category": op["상호"].apply(to_food),
    "latitude": pd.to_numeric(op["lat"], errors="coerce"),
    "longitude": pd.to_numeric(op["lon"], errors="coerce"),
    "status": op["영업상태"],
    "roadAddress": op["도로명주소"],
}).dropna(subset=["latitude", "longitude"])
out.to_csv("restaurants.csv", index=False, encoding="utf-8")
EOF

# ── README 업그레이드 ──
cat > README.md <<'EOF'
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
EOF

echo "=== data-prep/export_data.py + README 생성 완료 ==="