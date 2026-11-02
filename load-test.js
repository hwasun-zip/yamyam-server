import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 50,          // 동시 사용자 50명
  duration: '20s',  // 20초 동안 계속 요청
};

const URL = 'http://localhost:8080/api/v1/recommendations?lat=37.4979&lng=127.0276';

// 제휴사 API 키 필요 (docs/api-spec.md 2장). 부하 테스트용 키는 호출 한도를 크게 잡아 두었다.
const PARAMS = { headers: { 'X-API-Key': 'yk_test_load_demo' } };

export default function () {
  const res = http.get(URL, PARAMS);
  check(res, { 'status 200': (r) => r.status === 200 });
}
