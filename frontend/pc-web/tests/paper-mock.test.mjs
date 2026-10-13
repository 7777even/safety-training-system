import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const mockUrl = new URL('../src/mock/paper.ts', import.meta.url);

/** 加载仅保留会话内状态的 Paper Contract Mock。 */
async function loadMock() {
  const source = await readFile(mockUrl, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022
    }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('随机试卷缺少抽题规则返回 Contract 6506', async () => {
  const mock = await loadMock();
  mock.resetPaperMock();
  const created = await mock.mockCreatePaper({
    organizationId: 1,
    paperCode: 'RANDOM-001',
    paperName: '随机卷',
    paperType: 'RANDOM',
    status: 1
  });
  const result = await mock.mockComposePaper(created.data, {
    durationSeconds: 3600,
    passRatio: 0.6,
    questionOrderMode: 'IMPORT',
    multiSelectScoring: 'ALL_OR_NOTHING',
    drawRules: []
  });
  assert.equal(result.code, 6506);
});

test('保存响应式组卷数据时不会调用 structuredClone 克隆 Vue Proxy', async () => {
  const mock = await loadMock();
  mock.resetPaperMock();
  const payload = new Proxy(
    {
      durationSeconds: 3600,
      passRatio: 0.6,
      questionOrderMode: 'IMPORT',
      multiSelectScoring: 'ALL_OR_NOTHING',
      questions: new Proxy([{questionId: 1, questionVersionId: 1, score: 10, sortOrder: 1}], {})
    },
    {}
  );
  const result = await mock.mockComposePaper(1, payload);
  assert.equal(result.code, 0);
  assert.equal((await mock.mockPreviewPaper(1)).code, 0);
});

test('试卷预览后发布固定版本快照，currentVersionNo 保持版本序号', async () => {
  const mock = await loadMock();
  mock.resetPaperMock();
  const payload = {
    durationSeconds: 3600,
    passRatio: 0.6,
    questionOrderMode: 'IMPORT',
    multiSelectScoring: 'ALL_OR_NOTHING',
    questions: [{questionId: 1, questionVersionId: 1, score: 10, sortOrder: 1}]
  };
  await mock.mockComposePaper(1, payload);
  const preview = await mock.mockPreviewPaper(1);
  const published = await mock.mockPublishPaper(1, payload);
  const paper = await mock.mockGetPaper(1);
  assert.equal(preview.code, 0);
  assert.equal(published.data.versionNo, paper.data.currentVersionNo);
  assert.equal(published.data.questions[0].paperQuestionId, 1);
});
