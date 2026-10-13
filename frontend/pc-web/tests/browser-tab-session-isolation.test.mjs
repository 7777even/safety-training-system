import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8');

test('PC 登录会话使用持久化槽位而不是共享单令牌', async () => {
  const source = await read('../src/stores/modules/user.ts');

  assert.match(source, /SESSION_SLOTS_STORAGE_KEY/);
  assert.match(source, /window\.localStorage\.getItem/);
  assert.match(source, /window\.localStorage\.setItem/);
  assert.doesNotMatch(source, /window\.sessionStorage\.(getItem|setItem|removeItem)/);
});
