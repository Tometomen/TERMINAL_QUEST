// Locate the visible mint primary button from rendered pixels. Scene2D lives
// inside a WebGL canvas, so DOM text locators cannot inspect its controls.
exports.primaryY = async (page, x, minY = 180, maxY = 735) => {
  const png = (await page.screenshot()).toString('base64');
  return page.evaluate(async ({ png, x, minY, maxY }) => {
    const image = new Image(); image.src = 'data:image/png;base64,' + png; await image.decode();
    const canvas = document.createElement('canvas'); canvas.width = image.width; canvas.height = image.height;
    const context = canvas.getContext('2d'); context.drawImage(image, 0, 0);
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
    let start = -1;
    for (let y = minY; y <= maxY; y++) {
      const i = (y * canvas.width + x) * 4;
      const mint = y < maxY && pixels[i + 1] > 150 && pixels[i + 2] > 100 && pixels[i] < 200 && pixels[i + 1] - pixels[i] > 40 && pixels[i + 2] - pixels[i] > 40;
      if (mint && start < 0) start = y;
      if (!mint && start >= 0) { if (y - start >= 20) return (start + y) / 2; start = -1; }
    }
    throw new Error('No visible primary button at column ' + x);
  }, { png, x, minY, maxY });
};

exports.outputChanged = async (page, before, after) => page.evaluate(async ({ before, after }) => {
  async function pixels(base64) {
    const image = new Image(); image.src = 'data:image/png;base64,' + base64; await image.decode();
    const canvas = document.createElement('canvas'); canvas.width = image.width; canvas.height = image.height;
    const context = canvas.getContext('2d'); context.drawImage(image, 0, 0);
    return context.getImageData(60, 240, 815, 340).data;
  }
  const a = await pixels(before), b = await pixels(after); let count = 0;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i] && ++count > 100) return true;
  return false;
}, { before: before.toString('base64'), after: after.toString('base64') });
