import { defineConfig } from "vitest/config";

// The backend serves dist/ at / (backend/.../web/WebRoutes.kt); in dev, /api goes to a local backend.
export default defineConfig({
  base: "/",
  build: {
    outDir: "dist",
    emptyOutDir: true,
    target: "es2022",
    chunkSizeWarningLimit: 1200,
  },
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts", "scripts/**/*.test.ts"],
  },
});
