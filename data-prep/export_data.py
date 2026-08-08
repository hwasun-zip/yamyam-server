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
