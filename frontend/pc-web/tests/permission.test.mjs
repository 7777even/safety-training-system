import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const permissionUrl = new URL('../src/utils/permission.ts', import.meta.url);

/** 将纯权限工具转译为可由 Node 直接执行的模块。 */
async function loadPermission() {
  const source = await readFile(permissionUrl, 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022
    }
  });

  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('按钮权限仅根据 profile 返回的 perms 数组判断', async () => {
  const { hasPermission } = await loadPermission();
  const userInfo = {
    menus: [],
    perms: ['sys:user:add', 'sys:role:edit']
  };

  assert.equal(hasPermission(userInfo, 'sys:user:add'), true);
  assert.equal(hasPermission(userInfo, 'sys:user:delete'), false);
  assert.equal(hasPermission(null, 'sys:user:add'), false);
});
