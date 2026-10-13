import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
import vm from 'node:vm';
// 执行真实组件方法验证组、岗位下发参数，不靠模板字符串判定行为。
async function method(file, name, state) {
  const source = await readFile(new URL(file, import.meta.url), 'utf8');
  const script = source.match(/<script setup[^>]*>([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile('component.ts', script, ts.ScriptTarget.Latest, true);
  const node = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name.text === name);
  const js = ts.transpileModule(node.getText(ast), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
  return vm.runInNewContext(js + '\n' + name, state);
}
test('按用户组确认传组ID，不夹带旧指定人员或按部门ID', async () => {
  const calls = [];
  const state = { hasSessionOrganization: {value: true}, selectionType: {value:'GROUP'}, checkedDeptIds: {value:[5]},
    checkedPositionIds: {value:[6]}, checkedGroupIds: {value:[7,8]}, selectedUsers:{value:[{userId:9}]}, visible:{value:true}, emit: (...args) => calls.push(args), ElMessage:{warning: assert.fail} };
  (await method('../src/components/organization/PersonSelectionDialog.vue', 'confirm', state))();
  assert.equal(calls[0][1].selectionType, 'GROUP'); assert.deepEqual(calls[0][1].subjectIds, [7,8]);
  assert.equal(calls[0][2].length, 0); assert.equal(state.visible.value, false);
});
test('空岗位选择明确提示且不发出下发请求', async () => {
  const messages = [];
  const state = { hasSessionOrganization: {value: true}, selectionType: {value:'POSITION'}, checkedDeptIds: {value:[]},
    checkedPositionIds: {value:[]}, checkedGroupIds: {value:[]}, selectedUsers:{value:[]}, visible:{value:true}, emit:assert.fail, ElMessage:{warning: text => messages.push(text)} };
  (await method('../src/components/organization/PersonSelectionDialog.vue', 'confirm', state))();
  assert.equal(messages[0], '请至少选择一个岗位'); assert.equal(state.visible.value, true);
});
test('候选列表姓名、部门、岗位及用户组条件同时提交', async () => {
  const calls = [];
  const state = { currentTenantId: () => 1, userStore:{userInfo:{}}, query:{value:{keyword:'张',deptId:2,positionId:3,groupId:4}},
    pageNum:{value:1},pageSize:{value:20},loadSequence:{value:0},loading:{value:false},list:{value:[]},total:{value:0},getPersonPickerPage: async query => {calls.push(query);return {list:[],total:0};} };
  await (await method('../src/components/organization/PersonPickerDialog.vue', 'load', state))();
  assert.equal(calls[0]['query.positionId'], 3); assert.equal(calls[0]['query.groupId'], 4);
  assert.equal(calls[0]['query.keyword'], '张'); assert.equal(calls[0]['query.deptId'], 2);
});

test('筛空组失败时清空旧全员候选，不能继续勾选错误名单', async () => {
  const warnings = [];
  const state = { currentTenantId: () => 1, userStore:{userInfo:{}}, query:{value:{groupId:4}},
    pageNum:{value:1},pageSize:{value:20},loadSequence:{value:0},loading:{value:false},list:{value:[{userId:7}]},total:{value:1},
    ElMessage:{error: message => warnings.push(message)}, getPersonPickerPage: async () => { throw new Error('用户组当前没有符合条件的启用人员'); }, Error };
  await (await method('../src/components/organization/PersonPickerDialog.vue', 'load', state))();
  assert.equal(state.list.value.length,0); assert.equal(state.total.value,0); assert.match(warnings[0], /用户组/);
});
test('旧岗位筛选迟到响应不能覆盖新用户组筛选结果', async () => {
  let resolveOld; const old = new Promise(resolve => { resolveOld = resolve; });
  let count = 0;
  const state = { currentTenantId: () => 1, userStore:{userInfo:{}}, query:{value:{positionId:3}},
    pageNum:{value:1},pageSize:{value:20},loadSequence:{value:0},loading:{value:false},list:{value:[]},total:{value:0},
    ElMessage:{error: assert.fail},getPersonPickerPage: () => ++count === 1 ? old : Promise.resolve({list:[{userId:8}],total:1}) };
  const load = await method('../src/components/organization/PersonPickerDialog.vue', 'load', state);
  const first = load(); state.query.value = {groupId:4}; await load(); resolveOld({list:[{userId:7}],total:1}); await first;
  assert.equal(state.list.value[0].userId,8);
});
