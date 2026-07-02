import type { NextConfig } from "next";

const apiBaseUrl = process.env.DROPSHIP_API_BASE_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      {
        source: "/uploads/products/:path*",
        destination: `${apiBaseUrl}/uploads/products/:path*`,
      },
    ];
  },
};

export default nextConfig;
