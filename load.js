import http from "k6/http";
import { check } from "k6";

export const options = {
  scenarios: {
    redirect_load: {
      executor: "constant-vus",
      vus: 200,
      duration: "1m",
    },
  },

  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<200"],
  },
};

const urls = [
  "http://localhost:8080/sudhanshu",
  "http://localhost:8080/65IgW1vc",
  "http://localhost:8080/wbyyKFMZ",
  "http://localhost:8080/QuGCxRrC",
];

export default function () {
  const url = urls[Math.floor(Math.random() * urls.length)];

  const response = http.get(url, {
    redirects: 0,
  });

  check(response, {
    "returned redirect": (r) => r.status === 301 || r.status === 302,
  });
}
