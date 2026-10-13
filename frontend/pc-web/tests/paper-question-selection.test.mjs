import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const selectionUrl = new URL('../src/views/admin/business-center/exams/paper/components/question-selection.ts', import.meta.url);

/** 加载题库弹窗向试卷工作台提交的题目解析边界。 */
async function loadQuestionSelection() {
  const source = await readFile(selectionUrl, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('题库列表未返回当前版本时，添加已选题目仍提交可组卷的题目详情', async () => {
  const selection = await loadQuestionSelection();
  const summaries = [{ questionId: 11, questionCode: 'Q-11' }];
  const detail = {
    questionId: 11,
    questionCode: 'Q-11',
    currentVersion: { questionVersionId: 101, stem: '应急出口的设置要求是什么？' }
  };

  const selected = await selection.resolveSelectedQuestions(summaries, [11], async (questionId) => {
    assert.equal(questionId, 11);
    return detail;
  });

  assert.deepEqual(selected, [detail]);
});
