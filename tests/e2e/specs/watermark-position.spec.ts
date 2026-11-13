import { test, expect } from '@playwright/test';
import { fileURLToPath } from 'node:url';

const watermarkScript = fileURLToPath(new URL('../../../server/src/main/resources/static/js/watermark.js', import.meta.url));

// Exercise the shared renderer directly: no Office conversion or running server is needed.
test('watermarks keep their document coordinates when lazy loading grows the page', async ({ page }) => {
  await page.setViewportSize({ width: 800, height: 600 });
  await page.setContent(`
    <style>html, body { margin: 0; width: 100%; height: 100%; } img { display: block; }</style>
    <img width="600" height="1800" alt="Loaded page">
    <img id="next-page" width="600" height="32" alt="Loading page">
  `);
  await page.addScriptTag({ path: watermarkScript });
  const settings = {
    watermark_txt: 'Internal\\nFor review',
    watermark_x: 0,
    watermark_y: 0,
    watermark_width: 240,
    watermark_height: 80,
    watermark_x_space: 10,
    watermark_y_space: 10,
  };
  await page.evaluate(options => (window as any).watermark.init(options), settings);

  const firstMark = page.locator('#wm_div_id').locator('[id="mask_div_id00"]');
  const marks = page.locator('#wm_div_id').locator('[id^="mask_div_id"]');
  await expect(firstMark).toBeAttached();
  const position = () => firstMark.evaluate(mark => ({
    left: (mark as HTMLElement).style.left,
    top: (mark as HTMLElement).style.top,
  }));
  const before = await position();
  const initialCount = await marks.count();

  await page.mouse.move(700, 300);
  await page.mouse.wheel(0, 700);
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(0);
  // A newly loaded page replaces a short loading placeholder with a full-height image.
  await page.locator('#next-page').evaluate(img => img.setAttribute('height', '1723'));
  await expect.poll(() => marks.count()).toBeGreaterThan(initialCount);
  expect(await position()).toEqual(before);

  // commonHeader.ftl also re-initializes after document dimensions change.
  await page.evaluate(options => (window as any).watermark.init(options), settings);
  expect(await position()).toEqual(before);
  const expandedCount = await marks.count();
  await page.setViewportSize({ width: 1100, height: 700 });
  await expect.poll(() => marks.count()).toBeGreaterThan(expandedCount);
  expect(await position()).toEqual(before);
  await expect(page.locator('#wm_div_id')).toHaveCount(1);
  await expect(firstMark.locator('div')).toHaveText(['Internal', 'For review']);
});
