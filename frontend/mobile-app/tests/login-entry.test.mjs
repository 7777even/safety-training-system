import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

test('移动登录入口校验编码、切换机构并显式退出已有账号', async () => {
  const source = readFileSync(new URL('../src/pages/auth/LoginPage.vue', import.meta.url), 'utf8');
  const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*;$/gm, '');
  let load;
  let show;
  const redirects = [];
  const location = { href: 'http://localhost:5174/login?orgCode=DEMO#/pages/auth/LoginPage', search: '?orgCode=DEMO' };
  const store = {
    token: undefined,
    async logout() { this.token = undefined; },
    async loginWithPassword(...args) { this.credentials = args; }
  };
  const context = vm.createContext({
    URL, URLSearchParams,
    window: { location, history: { state: null, replaceState(_state, _title, url) {
      location.href = url.toString();
      location.search = new URL(url).search;
    } } },
    ref: (value) => ({ value }), useUserStore: () => store,
    onLoad: (callback) => { load = callback; }, onShow: (callback) => { show = callback; },
    uni: { reLaunch: ({ url }) => redirects.push(url) }
  });
  vm.runInContext(ts.transpile(script), context);
  load({});
  assert.equal(vm.runInContext('orgCode.value', context), 'DEMO');
  load({ orgCode: 'other' });
  assert.equal(vm.runInContext('orgCode.value', context), 'OTHER');
  vm.runInContext('switchOrganization()', context);
  assert.equal(new URL(location.href).searchParams.has('orgCode'), false);
  redirects.length = 0;
  for (const [code, expected] of [['ab_12', 'AB_12'], ['A', ''], ['bad code', ''], ['A'.repeat(65), ''], [undefined, '']]) {
    load({ orgCode: code });
    assert.equal(vm.runInContext('orgCode.value', context), expected);
    assert.equal(vm.runInContext('invalidLinkedOrgCode.value', context), Boolean(code) && !expected);
  }
  load({ orgCode: 'acme' });
  vm.runInContext("userCode.value = 'learner'; password.value = 'secret'", context);
  await vm.runInContext('submitLogin()', context);
  assert.deepEqual(store.credentials, ['ACME', 'learner', 'secret']);
  redirects.length = 0;
  store.token = 'existing';
  show();
  assert.equal(redirects.length, 0);
  await vm.runInContext('switchAccount()', context);
  assert.equal(store.token, undefined);
  vm.runInContext('switchOrganization()', context);
  assert.equal(redirects.pop(), '/pages/auth/LoginPage');
  load({});
  store.token = 'existing';
  show();
  assert.equal(redirects.pop(), '/pages/index/index');
});
