import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 50,          // 동시 사용자 50명
  duration: '20s',  // 20초 동안 계속 요청
};

const URL = 'http://localhost:8080/api/v1/recommendations?lat=37.4979&lng=127.0276';

export default function () {
  const res = http.get(URL);
  check(res, { 'status 200': (r) => r.status === 200 });
}
