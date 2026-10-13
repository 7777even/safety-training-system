import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8');

test('PC 登录支持持久化会话槽位和标签页绑定', async () => {
  const [store, page] = await Promise.all([
    read('../src/stores/modules/user.ts'),
    read('../src/views/pages/LoginPage.vue')
  ]);

  assert.match(store, /SESSION_SLOTS_STORAGE_KEY/);
  assert.match(store, /window\.name/);
  assert.match(store, /restoreSessionSlot/);
  assert.match(store, /removeSessionSlot/);
  assert.match(page, /availableSessionSlots/);
  assert.match(page, /restoreSessionSlot/);
});
