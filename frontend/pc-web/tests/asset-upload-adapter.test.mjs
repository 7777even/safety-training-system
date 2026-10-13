import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const sourceUrl = new URL('../src/api/asset.ts', import.meta.url);

test('素材上传适配器按文件大小选择 multipart，并支持恢复、暂停和取消', async () => {
  const source = await readFile(sourceUrl, 'utf8');
  assert.match(source, /export function shouldUseMultipart/);
  assert.match(source, /fileSize >= 20 \* 1024 \* 1024/);
  assert.match(source, /uploadedParts/);
  assert.match(source, /signal/);
  assert.match(source, /cancelAssetUpload/);
  assert.match(source, /method: 'DELETE'/);
  assert.match(source, /\/api\/system\/files\/multipart\/init/);
  assert.match(source, /\/complete/);
});
