import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';
import vm from 'node:vm';
// 执行真实API方法：来源选择不会绕过服务端合并或触发自动发布。
async function api(request) {
  const source = await readFile(new URL('../src/api/training-matrix.ts', import.meta.url), 'utf8');
  const ast = ts.createSourceFile('api.ts', source, ts.ScriptTarget.Latest, true);
  const js = ts.transpileModule(ast.statements.filter(ts.isFunctionDeclaration).map(node => node.getText(ast).replace('export ', '')).join('\n'), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
  return vm.runInNewContext(js + '\n({saveTrainingMatrix, createTrainingPlanFromMatrix, getTrainingMatrixSource})', { request });
}
test('矩阵创建提交选中要求和基础信息，仅调用草稿事务入口', async () => {
  const calls = []; const client = await api(async request => { calls.push(request); return { code: 0, data: 7 }; });
  assert.equal(await client.createTrainingPlanFromMatrix({ planName: '电工培训' }, [1,2]), 7);
  assert.equal(calls.length, 1); assert.equal(calls[0].url, '/api/training/matrices/create-plan');
  assert.deepEqual(calls[0].data.matrixIds, [1,2]);
});
test('矩阵编辑透传原版本，失败不能视为成功', async () => {
  const calls = []; const client = await api(async request => { calls.push(request); return { code: 8161, message: '版本冲突' }; });
  await assert.rejects(client.saveTrainingMatrix({ version: 2, requiredHours: 3 }, 9), /版本冲突/);
  assert.equal(calls[0].method, 'PUT'); assert.equal(calls[0].data.version, 2);
});
