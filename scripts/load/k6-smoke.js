import http from "k6/http";
import { check, sleep } from "k6";
import { Rate } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "https://coreable-saf.com").replace(/\/$/, "");
const PRODUCT_IDS = (__ENV.PRODUCT_IDS || "")
  .split(",")
  .map((item) => item.trim())
  .filter(Boolean);

const serverErrorRate = new Rate("server_error_rate");

export const options = {
  stages: [
    { duration: "1m", target: 5 },
    { duration: "2m", target: 20 },
    { duration: "10s", target: 0 },
  ],
  thresholds: {
    server_error_rate: [{ threshold: "rate<0.05", abortOnFail: true, delayAbortEval: "10s" }],
    http_req_duration: ["p(95)<5000"],
  },
  summaryTrendStats: ["min", "avg", "med", "p(90)", "p(95)", "p(99)", "max"],
};

export function setup() {
  if (PRODUCT_IDS.length > 0) {
    return { productIds: PRODUCT_IDS.slice(0, 3) };
  }

  const response = http.get(`${BASE_URL}/api/products`, { tags: { name: "GET /api/products setup" } });
  check(response, { "setup products returned 200": (res) => res.status === 200 });
  const products = response.json();
  return { productIds: products.slice(0, 3).map((item) => item.id) };
}

export default function (data) {
  const productId = data.productIds[__ITER % data.productIds.length];
  const targets = [
    { name: "GET /", path: "/" },
    { name: "GET /products", path: "/products" },
    { name: "GET /products/{id}", path: `/products/${productId}` },
    { name: "GET /api/products", path: "/api/products" },
    { name: "GET /api/health", path: "/api/health" },
  ];

  const target = targets[__ITER % targets.length];
  const response = http.get(`${BASE_URL}${target.path}`, { tags: { name: target.name } });

  serverErrorRate.add(response.status >= 500);
  check(response, {
    "status is below 500": (res) => res.status < 500,
    "status is not 404": (res) => res.status !== 404,
  });

  sleep(1);
}
