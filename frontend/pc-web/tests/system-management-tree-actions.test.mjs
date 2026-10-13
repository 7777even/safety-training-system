import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const rolePageUrl = new URL('../src/views/admin/system/role/index.vue', import.meta.url);
const menuPageUrl = new URL('../src/views/admin/system/menu/index.vue', import.meta.url);
const deptPageUrl = new URL('../src/views/admin/system/dept/index.vue', import.meta.url);

test('角色授权树支持父子节点联动和节点点击勾选', async () => {
  const source = await readFile(rolePageUrl, 'utf8');

  assert.doesNotMatch(source, /<ElTree[\s\S]*?check-strictly/);
  assert.match(source, /<ElTree[\s\S]*?check-on-click-node/);
});

test('菜单列表提供添加子级入口并预选当前菜单为父菜单', async () => {
  const source = await readFile(menuPageUrl, 'utf8');

  assert.match(source, /async function openCreateChildDialog\(parentMenu: SystemMenu\)/);
  assert.match(source, /parentId:\s*parentMenu\.menuId/);
  assert.match(source, /@click="openCreateChildDialog\(asSystemMenu\(row\)\)"[^>]*>添加子级/);
});

test('组织列表树提供添加子级入口并预选当前组织为父级', async () => {
  const source = await readFile(deptPageUrl, 'utf8');

  assert.match(source, /async function openCreateChildDialog\(parentDept: SystemDept\)/);
  assert.match(source, /parentId:\s*parentDept\.deptId/);
  assert.match(source, /@click="openCreateChildDialog\(asSystemDept\(row\)\)"[^>]*>添加子级/);
});

test('菜单批量可见性树保持独立勾选，避免父节点操作误改子菜单状态', async () => {
  const source = await readFile(menuPageUrl, 'utf8');

  assert.match(source, /<ElDialog v-model="batchDialogVisible"[\s\S]*?<ElTree[\s\S]*?check-strictly/);
});

test('菜单新增和编辑表单支持选择图标并展示当前图标预览', async () => {
  const source = await readFile(menuPageUrl, 'utf8');

  assert.match(source, /const MENU_ICON_OPTIONS:/);
  assert.match(source, /function getMenuIcon\(iconName: string\): Component/);
  assert.match(source, /<ElSelect v-model="menuForm\.icon"[\s\S]*?placeholder="请选择菜单图标"/);
  assert.match(source, /<ElIcon class="menu-icon-selector__preview-icon"><component :is="getMenuIcon\(menuForm\.icon\)" \/><\/ElIcon>/);
});
