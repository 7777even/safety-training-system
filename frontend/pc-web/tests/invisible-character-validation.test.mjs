import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const organizationPageUrl = new URL('../src/views/admin/system/org/organization/index.vue', import.meta.url);
const userPageUrl = new URL('../src/views/admin/system/org/user/index.vue', import.meta.url);

test('机构管理员账号和初始密码拒绝不可见 Unicode 字符', async () => {
  const source = await readFile(organizationPageUrl, 'utf8');

  assert.match(source, /const INVISIBLE_UNICODE = \/\[\\p\{Cc\}\\p\{Cf\}\\p\{Cs\}\\p\{Zl\}\\p\{Zp\}\\p\{Default_Ignorable_Code_Point\}\]\/u/);
  assert.match(source, /adminUserCode:\s*\[[\s\S]*?invisibleCharacterRule/);
  assert.match(source, /adminPassword:\s*\[[\s\S]*?invisibleCharacterRule/);
});

test('用户编码、初始密码和重置密码拒绝不可见 Unicode 字符', async () => {
  const source = await readFile(userPageUrl, 'utf8');

  assert.match(source, /const INVISIBLE_UNICODE = \/\[\\p\{Cc\}\\p\{Cf\}\\p\{Cs\}\\p\{Zl\}\\p\{Zp\}\\p\{Default_Ignorable_Code_Point\}\]\/u/);
  assert.match(source, /userCode:\s*\[[\s\S]*?invisibleCharacterRule/);
  assert.match(source, /password:\s*\[[\s\S]*?invisibleCharacterRule/);
  assert.match(source, /const resetFormRules[\s\S]*?invisibleCharacterRule/);
});
