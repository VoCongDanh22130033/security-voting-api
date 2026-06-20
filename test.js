import http from 'k6/http';

export const options = {
  vus: 100,
  duration: '30s',
};

export default function () {
  const res = http.get('http://localhost:8080/api/elections');

  if (res.status !== 200) {
    console.log(`status=${res.status}, body=${res.body}`);
  }
}