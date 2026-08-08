#!/usr/bin/env bash
set -e
# k6 설치 (바이너리 다운로드)
if [ ! -f ./k6 ]; then
  echo "k6 다운로드 중..."
  curl -sL https://github.com/grafana/k6/releases/download/v0.49.0/k6-v0.49.0-linux-amd64.tar.gz -o k6.tar.gz
  tar xzf k6.tar.gz
  mv k6-v0.49.0-linux-amd64/k6 ./k6
  rm -rf k6.tar.gz k6-v0.49.0-linux-amd64
fi
./k6 version

# 부하 테스트 스크립트
cat > load-test.js <<'EOF'
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
EOF
echo "=== k6 준비 완료! ==="