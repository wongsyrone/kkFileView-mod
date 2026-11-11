import { test, expect } from '@playwright/test';

const fixtureBase = process.env.FIXTURE_BASE_URL || 'http://127.0.0.1:18080';
const source = `${fixtureBase}/sample.docx`;
const previewUrl = `/onlinePreview?url=${encodeURIComponent(Buffer.from(source).toString('base64'))}&forceUpdatedCache=true`;

test('keeps the conversion message visible until preview HTML is ready', async ({ page }) => {
  let finishConversion!: () => void;
  const converting = new Promise<void>(resolve => { finishConversion = resolve; });
  let conversions = 0;
  let forceUpdate: string | null = null;
  await page.route('**/onlinePreview?**', async route => {
    if (route.request().headers()['x-kk-preview-loading'] !== 'true') {
      await route.continue();
      return;
    }
    conversions++;
    forceUpdate = new URL(route.request().url()).searchParams.get('forceUpdatedCache');
    await converting;
    await route.fulfill({
      contentType: 'text/html',
      body: '<html><head><title>Converted preview</title></head><body><p id="preview">Ready</p>' +
        '<script src="js/preview-loading-test.js"></script></body></html>',
    });
  });
  await page.route('**/js/preview-loading-test.js', route => route.fulfill({
    contentType: 'application/javascript',
    body: 'let loading = false; const loadPreview = () => {}; ' +
      'document.getElementById("preview").textContent = "Preview script loaded";',
  }));

  try {
    await page.goto(previewUrl);
    await expect(page.locator('h1')).toHaveText('文件转换中');
    await expect(page.locator('#refreshBtn')).toBeDisabled();
    expect(conversions).toBe(1);
    expect(forceUpdate).toBe('true');
    // A second event while the request is running must not start another conversion.
    await page.locator('#refreshBtn').dispatchEvent('click');
    expect(conversions).toBe(1);
  } finally {
    finishConversion();
  }
  await expect(page.locator('#preview')).toHaveText('Preview script loaded');
  await expect(page).toHaveTitle('Converted preview');
  expect(new URL(page.url()).searchParams.get('forceUpdatedCache')).toBe('true');
});

test('offers a retry when the preview request fails', async ({ page }) => {
  let attempts = 0;
  await page.route('**/onlinePreview?**', async route => {
    if (route.request().headers()['x-kk-preview-loading'] !== 'true') {
      await route.continue();
      return;
    }
    attempts++;
    await route.fulfill(attempts === 1
      ? { status: 503, body: 'Unavailable' }
      : { contentType: 'text/html', body: '<p id="preview">Ready after retry</p>' });
  });

  await page.goto(previewUrl);
  await expect(page.locator('#message')).toContainText('预览请求失败');
  await expect(page.locator('#refreshBtn')).toBeEnabled();
  await page.locator('#refreshBtn').click();
  await expect(page.locator('#preview')).toHaveText('Ready after retry');
  expect(attempts).toBe(2);
});
