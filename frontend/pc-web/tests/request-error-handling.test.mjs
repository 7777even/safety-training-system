import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const requestSource = await readFile(new URL('../src/api/request.ts', import.meta.url), 'utf8');
const mainSource = await readFile(new URL('../src/main.ts', import.meta.url), 'utf8');

test('请求层将 HTTP 错误交给全局错误处理器', () => {
  assert.match(requestSource, /requestErrorHandler\?\.\(error\);[\s\S]*?return Promise\.reject\(error\);/);
});

test('全局错误处理器优先展示接口返回的 message，包括 401', () => {
  assert.match(mainSource, /const message = typeof responseData\?\.message === 'string'/);
  assert.match(mainSource, /ElMessage\.error\(message\);/);
  assert.doesNotMatch(mainSource, /ElMessage\.warning\('登录已失效，请重新登录'\)/);
});
