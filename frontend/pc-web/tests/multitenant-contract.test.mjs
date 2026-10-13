import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8');

test('PC 登录请求必须包含企业编码', async () => {
  const [auth, store, page, shared] = await Promise.all([
    read('../src/api/auth.ts'),
    read('../src/stores/modules/user.ts'),
    read('../src/views/pages/LoginPage.vue'),
    read('../../shared/src/types/api.ts')
  ]);

  assert.match(auth, /orgCode/);
  assert.match(store, /loginWithPassword\(orgCode/);
  assert.match(page, /orgCode/);
  assert.match(shared, /orgCode: string/);
});

test('租户契约只保留企业和监管平台类型并支持编辑', async () => {
  const [api, page] = await Promise.all([
    read('../src/api/organization.ts'),
    read('../src/views/admin/system/org/organization/index.vue')
  ]);

  assert.match(api, /'ENTERPRISE' \| 'REGULATOR'/);
  assert.doesNotMatch(api, /parentOrganizationId/);
  assert.match(api, /updateOrganization/);
  assert.match(page, /adminUserCode/);
  assert.doesNotMatch(page, /parentOrganizationId/);
});

test('前端不再保留后端已删除的成员和监管关系接口', async () => {
  const files = [
    '../src/api/org-member.ts',
    '../src/api/regulatory-relation.ts',
    '../src/views/admin/system/org/member/index.vue',
    '../src/views/admin/system/common/regulatory-relation/index.vue'
  ];

  await Promise.all(files.map(async (file) => {
    await assert.rejects(read(file), /ENOENT/);
  }));
});

test('监管聚合 API 和目标租户请求头已接入', async () => {
  const [api, request] = await Promise.all([
    read('../src/api/regulator.ts'),
    read('../src/api/request.ts')
  ]);

  assert.match(api, /\/api\/regulator\/aggregates\/tenants/);
  assert.match(request, /X-Target-Tenant-Id/);
});

test('素材播放格式使用后端闭合枚举', async () => {
  const [asset, viewer] = await Promise.all([
    read('../src/api/asset.ts'),
    read('../src/components/asset-viewer/AssetViewer.vue')
  ]);

  assert.match(asset, /'excel'/);
  assert.match(asset, /'image'/);
  assert.match(asset, /'original'/);
  assert.match(viewer, /excel/);
  assert.match(viewer, /image/);
});
