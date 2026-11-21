import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Allow LAN access to the console while developing on PC1.
  allowedDevOrigins: ["*"],
};

export default nextConfig;
