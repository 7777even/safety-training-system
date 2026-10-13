import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const compositionUrl = new URL('../src/libs/training-composition.ts', import.meta.url);
const trainingTypePageUrl = new URL('../src/views/admin/business-center/training/type/index.vue', import.meta.url);
const standardWorkspaceUrl = new URL('../src/views/admin/business-center/training/standard/workspace/index.vue', import.meta.url);
const standardRulesPanelUrl = new URL('../src/views/admin/business-center/training/standard/components/StandardRulesPanel.vue', import.meta.url);

/** 通过比例工具的导出边界验证页面百分数与接口比例值的转换。 */
async function loadTrainingRatioTools() {
  const source = await readFile(compositionUrl, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('培训比例在页面百分数与接口比例值之间双向转换', async () => {
  const tools = await loadTrainingRatioTools();

  assert.equal(tools.toTrainingRatio(30), 0.3);
  assert.equal(tools.toTrainingRatio(0), 0);
  assert.equal(tools.toTrainingRatioPercent(0.3), 30);
  assert.equal(tools.toTrainingRatioPercent(1), 100);
});

test('培训编排学时仅统计课程与线下内容，线上学时仅统计课程', async () => {
  const tools = await loadTrainingRatioTools();
  const stages = [{
    contents: [
      { contentType: 'COURSE', requiredHours: 4 },
      { contentType: 'OFFLINE', requiredHours: 6 },
      { contentType: 'EXAM', requiredHours: 2 },
      { contentType: 'QUESTION_BANK', requiredHours: 3 }
    ]
  }];

  assert.deepEqual(tools.summarizeTrainingComposition(stages), { totalHours: 10, onlineHours: 4, onlineRatio: 0.4 });
  tools.normalizeAssessmentRequiredHours(stages);
  assert.equal(stages[0].contents[2].requiredHours, 0);
  assert.equal(stages[0].contents[3].requiredHours, 0);
});

test('培训类型和标准页面统一展示百分数并在接口边界转换', async () => {
  const [trainingTypePage, standardWorkspace, standardRulesPanel] = await Promise.all([
    readFile(trainingTypePageUrl, 'utf8'),
    readFile(standardWorkspaceUrl, 'utf8'),
    readFile(standardRulesPanelUrl, 'utf8')
  ]);

  assert.match(trainingTypePage, /onlineMaxRatio: toTrainingRatio\(form\.onlineMaxRatio\)/);
  assert.match(trainingTypePage, /onlineMaxRatio: toTrainingRatioPercent\(detail\.onlineMaxRatio\)/);
  assert.match(trainingTypePage, /:max="100"/);
  assert.match(standardWorkspace, /requiredValue: rule\.ruleType === 'ONLINE_RATIO' \? toTrainingRatioPercent\(rule\.requiredValue\)/);
  assert.match(standardWorkspace, /requiredValue: rule\.ruleType === 'ONLINE_RATIO' \? toTrainingRatio\(rule\.requiredValue\)/);
  assert.match(standardWorkspace, /error\.code === 8018/);
  assert.match(standardRulesPanel, /onlineRatioMaxPercent/);
  assert.match(standardRulesPanel, /培训类型最高/);
});
