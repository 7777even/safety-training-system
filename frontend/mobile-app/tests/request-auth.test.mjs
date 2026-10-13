import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const mainUrl = new URL('../src/main.ts', import.meta.url);
const requestUrl = new URL('../src/api/request.ts', import.meta.url);

test('移动端收到未登录响应后清理会话，并由用户确认跳转登录页', async () => {
  const [mainSource, requestSource] = await Promise.all([
    readFile(mainUrl, 'utf8'),
    readFile(requestUrl, 'utf8')
  ]);

  assert.match(mainSource, /setRequestErrorHandler/);
  assert.match(mainSource, /requestError\.response\?\.status !== 401/);
  assert.match(mainSource, /userStore\.clearSession\(\)/);
  assert.match(mainSource, /uni\.showModal/);
  assert.match(mainSource, /confirmText:\s*'去登录'/);
  assert.match(mainSource, /uni\.reLaunch\(\{\s*url:\s*'\/pages\/auth\/LoginPage'/);
  assert.match(requestSource, /error\.response = \{\s*status:\s*response\.statusCode/);
});
