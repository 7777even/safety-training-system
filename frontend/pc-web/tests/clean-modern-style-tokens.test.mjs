import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(currentDirectory, '..', '..', '..');

/** 清爽现代风 Token 是 PC Web 与移动端共享的视觉契约。 */
const expectedTokens = [
  '--color-bg-page: #f8fafc;',
  '--color-bg-surface: #ffffff;',
  '--color-text-primary: #0f172a;',
  '--color-text-secondary: #64748b;',
  '--color-border: #e5e7eb;',
  '--color-primary: #2563eb;',
  '--color-primary-hover: #1d4ed8;',
  '--font-family-base:',
  '--space-4: 16px;',
  '--radius-control: 8px;',
  '--radius-card: 12px;',
  '--shadow-card:',
];

test('PC Web 与移动端声明相同的清爽现代基础 Token', async () => {
  const styleFiles = [
    'frontend/pc-web/src/styles/variables.less',
    'frontend/mobile-app/src/styles/variables.less',
  ];

  for (const styleFile of styleFiles) {
    const source = (await readFile(path.join(projectRoot, styleFile), 'utf8')).toLowerCase();

    for (const token of expectedTokens) {
      assert.ok(source.includes(token), `${styleFile} 缺少 ${token}`);
    }
  }
});

test('PC Web 通过 Element Plus 主题变量复用清爽现代 Token', async () => {
  const source = (
    await readFile(path.join(projectRoot, 'frontend/pc-web/src/styles/element-plus.less'), 'utf8')
  ).toLowerCase();

  for (const declaration of [
    '--el-color-primary: var(--color-primary);',
    '--el-color-primary-dark-2: var(--color-primary-hover);',
    '--el-border-color: var(--color-border);',
    '--el-border-radius-base: var(--radius-control);',
  ]) {
    assert.ok(source.includes(declaration), `Element Plus 主题缺少 ${declaration}`);
  }
});

test('Element Plus 常见组件使用清爽现代的卡片、导航和表格状态', async () => {
  const source = (
    await readFile(path.join(projectRoot, 'frontend/pc-web/src/styles/element-plus.less'), 'utf8')
  ).toLowerCase();

  for (const declaration of [
    '--el-card-border-color: var(--color-border);',
    '--el-menu-active-color: var(--color-primary);',
    '--el-menu-hover-bg-color: var(--color-primary-subtle);',
    '--el-table-border-color: var(--color-border);',
    '--el-table-header-bg-color: var(--color-bg-subtle);',
  ]) {
    assert.ok(source.includes(declaration), `Element Plus 组件主题缺少 ${declaration}`);
  }
});

test('PC Web 核心壳层使用语义 Token 而不是页面专属色值', async () => {
  const sourceExpectations = new Map([
    ['frontend/pc-web/src/layouts/AdminLayout.vue', ['background: var(--color-bg-page);']],
    ['frontend/pc-web/src/layouts/TopToolbar.vue', ['background: var(--color-bg-surface);', 'box-shadow: var(--shadow-card);']],
    ['frontend/pc-web/src/layouts/PortalLayout.vue', ['background: var(--color-bg-page);']],
    ['frontend/pc-web/src/views/portal/home/index.vue', ['border-radius: var(--radius-card);']],
    ['frontend/pc-web/src/views/pages/LoginPage.vue', ['background: var(--color-bg-page);', 'border-radius: var(--radius-card);']],
    ['frontend/pc-web/src/views/admin/overview/index.vue', ['background: var(--color-bg-subtle);', 'color: var(--color-text-primary);']],
  ]);

  for (const [styleFile, expectedDeclarations] of sourceExpectations) {
    const source = (await readFile(path.join(projectRoot, styleFile), 'utf8')).toLowerCase();

    for (const declaration of expectedDeclarations) {
      assert.ok(source.includes(declaration), `${styleFile} 缺少 ${declaration}`);
    }
  }
});

test('PC Web 在根元素消费统一字体 Token', async () => {
  const source = (
    await readFile(path.join(projectRoot, 'frontend/pc-web/src/styles/reset.less'), 'utf8')
  ).toLowerCase();

  assert.ok(source.includes('font-family: var(--font-family-base);'), 'PC Web 根元素缺少统一字体 Token');
});

test('移动端基础布局、登录页和首页遵循留白、卡片与触控基准', async () => {
  const sourceExpectations = new Map([
    ['frontend/mobile-app/src/layouts/MobileBaseLayout.vue', ['padding: 0 32rpx;', 'background: var(--color-bg-page);']],
    ['frontend/mobile-app/src/pages/auth/LoginPage.vue', ['border-radius: var(--radius-card);', 'min-height: 88rpx;', 'background: var(--color-primary);']],
    ['frontend/mobile-app/src/pages/index/index.vue', ['padding: 48rpx 0;', 'color: var(--color-text-primary);', 'min-height: 88rpx;']],
  ]);

  for (const [styleFile, expectedDeclarations] of sourceExpectations) {
    const source = (await readFile(path.join(projectRoot, styleFile), 'utf8')).toLowerCase();

    for (const declaration of expectedDeclarations) {
      assert.ok(source.includes(declaration), `${styleFile} 缺少 ${declaration}`);
    }
  }
});
