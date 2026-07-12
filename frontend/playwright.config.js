import { defineConfig, devices } from '@playwright/test'

const backendCommand = process.platform === 'win32'
  ? 'cd .. && mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080"'
  : 'cd .. && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080'

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 90_000,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
  },
  webServer: [
    {
      command: backendCommand,
      url: 'http://127.0.0.1:18080/actuator/health',
      timeout: 120_000,
      reuseExistingServer: true,
    },
    {
      command: 'npm run dev -- --port 5173',
      url: 'http://127.0.0.1:5173',
      timeout: 120_000,
      reuseExistingServer: true,
      env: {
        VITE_API_BASE: 'http://127.0.0.1:18080',
      },
    },
  ],
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
