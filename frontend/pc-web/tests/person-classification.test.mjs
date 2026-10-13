import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
import vm from 'node:vm';

// 执行真实 API 方法，验证写入与预览不误用 Mock 或下发接口。
async function api(request) {
  const source = await readFile(new URL('../src/api/person-classification.ts', import.meta.url), 'utf8');
  const ast = ts.createSourceFile('api.ts', source, ts.ScriptTarget.Latest, true);
  const methods = ast.statements.filter(node => ts.isFunctionDeclaration(node));
  const js = ts.transpileModule(methods.map(node => node.getText(ast).replace('export ', '')).join('\n'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return vm.runInNewContext(js + '\n({previewPersonGroup, savePersonGroup, savePersonProfile})', { request });
}

test('分类预览只提交条件，不调用人员下发', async () => {
  const calls = [];
  const client = await api(async options => { calls.push(options); return { code: 0, data: [7, 8] }; });
  assert.deepEqual(Array.from(await client.previewPersonGroup({ minAge: 18 })), [7, 8]);
  assert.equal(calls[0].url, '/api/person/groups/preview');
  assert.equal(calls[0].data.minAge, 18);
});

test('编辑用户组透传版本和条件，新建不误发更新', async () => {
  const calls = [];
  const client = await api(async options => { calls.push(options); return { code: 0, data: 4 }; });
  await client.savePersonGroup({ groupName: '电工', status: 1, conditions: { occupation: '电工' } });
  await client.savePersonGroup({ groupId: 4, groupName: '电工', status: 0, version: 2, conditions: {} });
  assert.equal(calls[0].method, 'POST'); assert.equal(calls[1].method, 'PUT');
  assert.equal(calls[1].url, '/api/person/groups/4'); assert.equal(calls[1].data.version, 2);
});

test('人员特征清空值透传，服务端失败不得显示成功', async () => {
  const calls = [];
  const client = await api(async options => { calls.push(options); return { code: 4122, message: '版本冲突' }; });
  await assert.rejects(client.savePersonProfile(7, { version: 1, birthDate: null, occupation: '' }), /版本冲突/);
  assert.equal(calls[0].data.birthDate, null);
});

test('人员A的迟到加载不能覆盖当前人员B，也不能提前解除B加载态', async () => {
  const source = await readFile(new URL('../src/components/organization/PersonProfileDialog.vue', import.meta.url), 'utf8');
  const script = source.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile('dialog.ts', script, ts.ScriptTarget.Latest, true);
  const methods = ast.statements.filter(node => ts.isFunctionDeclaration(node) && node.name?.text === 'loadProfile');
  assert.equal(methods.length, 1);
  let resolveA;
  const pendingA = new Promise(resolve => { resolveA = resolve; });
  const state = { props: { userId: 7 }, visible: { value: true }, loading: { value: false }, loadSequence: { value: 0 },
    formUserId: { value: undefined }, form: { value: {} }, facts: { value: [] },
    getPersonProfile: id => id === 7 ? pendingA : Promise.resolve({ userId: 8, version: 0, occupation: '焊工' }),
    getPersonTrainingFacts: async () => [] };
  const js = ts.transpileModule(methods[0].getText(ast), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
  const loadProfile = vm.runInNewContext(js + '\nloadProfile', state);
  const first = loadProfile(7); state.props.userId = 8;
  await loadProfile(8); resolveA({ userId: 7, version: 0, occupation: '电工' }); await first;
  assert.equal(state.form.value.occupation, '焊工'); assert.equal(state.formUserId.value, 8);
});
