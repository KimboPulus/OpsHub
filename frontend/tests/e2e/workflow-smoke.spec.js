import { expect, test } from '@playwright/test'

async function login(page, username) {
  await page.goto('/')
  await page.locator('input[autocomplete="username"]').fill(username)
  await page.locator('input[autocomplete="current-password"]').fill('opshub')
  await page.getByRole('button', { name: /zaloguj/i }).click()
  await expect(page.locator('.topbar')).toContainText(username === 'lider' ? 'Leader' : 'Operator')
}

async function logout(page) {
  await page.locator('.session-tools button').click()
  await expect(page.locator('input[autocomplete="username"]')).toBeVisible()
}

test('leader and operator workflow smoke', async ({ page }) => {
  const title = `E2E workflow ${Date.now()}`

  await login(page, 'lider')
  await expect(page.locator('.live-pill')).toContainText(/Live|Reconnecting/)

  await page.goto('/issues/create')
  await page.locator('form input[maxlength="120"]').fill(title)
  await page.locator('form textarea[maxlength="1000"]').fill('Playwright smoke issue for role, SLA and audit workflow.')
  await page.locator('form input[type="number"]').fill('7')
  await page.locator('form select').nth(1).selectOption('HIGH')
  await page.locator('form select').nth(3).selectOption({ index: 1 })
  await page.locator('form .form-footer .btn-dark').click()

  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await expect(page.locator('body')).toContainText('Response SLA')
  await expect(page.locator('body')).toContainText('Resolution SLA')

  const issueId = Number(page.url().match(/\/issues\/(\d+)/)?.[1])
  expect(issueId).toBeGreaterThan(0)

  await logout(page)
  await login(page, 'operator')
  await page.goto(`/issues/${issueId}`)
  await page.getByRole('button', { name: 'Start work' }).click()
  await expect(page.locator('body')).toContainText('IN_PROGRESS', { timeout: 10_000 }).catch(async () => {
    await expect(page.locator('body')).toContainText('W toku')
  })

  const deniedStatus = await page.evaluate(async id => {
    const response = await fetch(`http://127.0.0.1:18080/api/issues/${id}/status`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ status: 'RESOLVED' }),
    })
    return response.status
  }, issueId)
  expect(deniedStatus).toBe(403)

  await logout(page)
  await login(page, 'lider')
  await page.goto(`/issues/${issueId}`)
  await page.locator('.action-stack button').first().click()
  await expect(page.locator('body')).toContainText('Met')

  await page.goto('/audit')
  await page.locator('input[placeholder="message, issue title, description..."]').fill(title)
  await expect(page.locator('.ops-table')).toContainText(title)
  await page.getByRole('button', { name: 'Open' }).first().click()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
  await page.screenshot({ path: 'test-results/opshub-workflow-evidence.png', fullPage: true })
})
