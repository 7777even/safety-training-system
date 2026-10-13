import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiUrl = new URL('../src/api/evaluation-result.ts', import.meta.url);
const workspaceUrl = new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url);
const executionPanelUrl = new URL('../src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue', import.meta.url);
const evaluationPanelUrl = new URL('../src/views/admin/business-center/training/plan/components/PlanEvaluationPanel.vue', import.meta.url);

test('培训计划结果只读取当前自动综合结果，不保留已删除的计算批次契约', async () => {
  const [workspace, panel, evaluationPanel] = await Promise.all([
    readFile(workspaceUrl, 'utf8'),
    readFile(executionPanelUrl, 'utf8'),
    readFile(evaluationPanelUrl, 'utf8')
  ]);
  const api = await readFile(apiUrl, 'utf8');
  assert.doesNotMatch(api, /calculation-runs/);
  assert.doesNotMatch(api, /CalculationRun/);
  assert.doesNotMatch(workspace, /calculation-runs|CalculationRun|calculateComposite|confirmComposite|evaluation:calculation/);
  assert.doesNotMatch(panel, /calculation-runs|CalculationRun|calculateComposite|confirmComposite|综合成绩计算记录|计算综合成绩|确认综合成绩/);
  assert.match(workspace, /getTrainingParticipantResults/);
  assert.match(panel, /props\.results\?\.compositeScore/);
  assert.match(workspace, /usageType === 'COMPOSITE'/);
  assert.doesNotMatch(panel, /v-model=".*trainingPlanId|v-model=".*trainingParticipantId|v-model=".*formVersionId/);
  assert.match(evaluationPanel, /计划内同类内容先汇总，再按绑定表单冻结的来源权重计算综合成绩/);
  assert.doesNotMatch(evaluationPanel, /M4 再生成正式评价结果和综合计算批次/);
});

test('培训计划清楚区分计分资格、空来源和题库练习反馈', async () => {
  const [workspace, stagesPanel, executionPanel] = await Promise.all([
    readFile(workspaceUrl, 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanStagesPanel.vue', import.meta.url), 'utf8'),
    readFile(executionPanelUrl, 'utf8')
  ]);

  assert.match(workspace, /isScored: 1/);
  assert.match(workspace, /content\.contentType === 'QUESTION_BANK' \? 0 : 1/);
  assert.match(stagesPanel, /计入综合成绩/);
  assert.match(stagesPanel, /题库练习不计分/);
  assert.match(executionPanel, /scoreParticipationText/);
  assert.match(executionPanel, /题库练习不参与综合成绩/);
});

test('阶段保存校验使用业务中文说明成绩汇总配置问题', async () => {
  const workspace = await readFile(workspaceUrl, 'utf8');

  assert.match(workspace, /COURSE: '课程', EXAM: '考试', OFFLINE: '线下培训'/);
  assert.match(workspace, /scoreContentTypeText\(contentType\)/);
  assert.match(workspace, /类加权平均的内容权重当前合计为 \$\{total\.toFixed\(2\)\}/);
  assert.match(workspace, /请为每一项计分内容填写大于 0 的内容权重/);
  assert.doesNotMatch(workspace, /return `\$\{contentType\} 加权平均的内容权重/);
});
