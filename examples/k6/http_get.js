import http from 'lyocell/http';

export default function () {
  http.get(`${__ENV.BASE_URL}/`);
};
