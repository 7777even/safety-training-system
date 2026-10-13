import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { URL } from 'node:url';
import {
  collectCompositeBindingIssues,
  resolvePrecheckTargetStep
} from '../src/views/admin/business-center/training/plan/workspace/composite-binding-precheck.ts';

const workspacePath = '../src/views/admin/business-center/training/plan/workspace/index.vue';
const panelPath = '../src/views/admin/business-center/training/plan/components/PlanEvaluationPanel.vue';

/** 已发布的综合评价来源规则：表单层只用「权重折算」。 */
function weightRule(sourceType, weight) {
  return { sourceType, calculationType: 'WEIGHT', weight };
}

/** 已勾选「计入综合成绩」的计划内容。 */
function scoredContent(contentType, extra = {}) {
  return { contentType, isScored: 1, scoreAggregationMode: 'AVERAGE', ...extra };
}

/** 默认场景：表单只要课程成绩，计划里课程已计入综合成绩。 */
function buildInput(overrides = {}) {
  return {
    stagesSaved: true,
    bindings: [{ formVersionId: 1, usageType: 'COMPOSITE' }],
    versionDetails: { 1: { scoreRules: [weightRule('COURSE', 1)] } },
    stages: [{ contents: [scoredContent('COURSE')] }],
    ...overrides
  };
}

/** 汇总失败原因，便于一次性断言文案与判据。 */
function messages(issues) {
  return issues.map((issue) => issue.message).join(' | ');
}

test('阶段与内容有未保存改动时先要求保存，不进入来源判定', () => {
  const issues = collectCompositeBindingIssues(buildInput({ stagesSaved: false }));
  assert.equal(issues.length, 1);
  assert.equal(issues[0].blocked, true);
  assert.equal(issues[0].targetStep, 1);
  assert.match(issues[0].message, /未保存/);
});

test('未绑定综合评价时不触发来源校验', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({ bindings: [{ formVersionId: 2, usageType: 'EXPERT_INSPECTION' }], versionDetails: {} })
  );
  assert.deepEqual(issues, []);
});

test('表单要求课程成绩但计划未勾选计入综合成绩时，提示可执行的处理动作', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({ stages: [{ contents: [{ contentType: 'COURSE', isScored: 0, scoreAggregationMode: 'AVERAGE' }] }] })
  );
  assert.equal(issues.length, 1);
  assert.equal(issues[0].blocked, true);
  assert.equal(issues[0].sourceType, 'COURSE');
  assert.equal(issues[0].targetStep, 1);
  assert.match(issues[0].message, /课程/);
  assert.match(issues[0].message, /计入综合成绩/);
});

test('题库练习的计分内容不能顶替课程来源', () => {
  const issues = collectCompositeBindingIssues(buildInput({ stages: [{ contents: [scoredContent('QUESTION_BANK')] }] }));
  assert.equal(issues.length, 1);
  assert.equal(issues[0].blocked, true);
  assert.match(issues[0].message, /课程/);
});

test('算术平均下保留内容权重时阻断保存', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({ stages: [{ contents: [scoredContent('COURSE', { scoreWeight: 0.5 })] }] })
  );
  assert.equal(issues.length, 1);
  assert.equal(issues[0].targetStep, 1);
  assert.match(issues[0].message, /算术平均/);
});

test('加权平均权重合计不为 1 时给出当前合计', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({
      stages: [{ contents: [scoredContent('COURSE', { scoreAggregationMode: 'WEIGHTED_AVERAGE', scoreWeight: 0.4 })] }]
    })
  );
  assert.equal(issues.length, 1);
  assert.match(issues[0].message, /加权平均/);
  assert.match(issues[0].message, /0\.40/);
});

test('同类计分内容的汇总方式不一致时阻断保存', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({
      stages: [
        {
          contents: [scoredContent('COURSE'), scoredContent('COURSE', { scoreAggregationMode: 'WEIGHTED_AVERAGE', scoreWeight: 1 })]
        }
      ]
    })
  );
  assert.equal(issues.length, 1);
  assert.match(issues[0].message, /统一选择/);
});

test('表单层来源权重合计不为 1 时阻断，并指向评价表单页', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({
      versionDetails: { 1: { scoreRules: [weightRule('COURSE', 0.6), weightRule('EXAM', 0.2)] } },
      stages: [{ contents: [scoredContent('COURSE'), scoredContent('EXAM')] }]
    })
  );
  assert.equal(issues.length, 1);
  assert.equal(issues[0].targetStep, 0);
  assert.match(issues[0].message, /合计为 1/);
  assert.match(issues[0].message, /0\.80/);
});

test('规则使用加减分调整时判定为不合规', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({
      versionDetails: { 1: { scoreRules: [{ sourceType: 'COURSE', calculationType: 'ADJUSTMENT', weight: 1 }] } }
    })
  );
  assert.equal(issues.length, 1);
  assert.match(issues[0].message, /不合规/);
});

test('同一来源重复配置规则时判定为不合规', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({
      versionDetails: { 1: { scoreRules: [weightRule('COURSE', 0.5), weightRule('COURSE', 0.5)] } }
    })
  );
  assert.equal(issues.length, 1);
  assert.match(messages(issues), /同一来源只能出现一次/);
});

test('同一评价用途重复绑定多个版本时阻断保存', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({
      bindings: [
        { formVersionId: 1, usageType: 'COMPOSITE' },
        { formVersionId: 2, usageType: 'COMPOSITE' }
      ],
      versionDetails: {
        1: { scoreRules: [weightRule('COURSE', 1)] },
        2: { scoreRules: [weightRule('COURSE', 1)] }
      }
    })
  );
  assert.match(messages(issues), /同一用途只能保留一个/);
});

test('版本详情未加载时要求重新选择，而不是误报缺少计分内容', () => {
  const issues = collectCompositeBindingIssues(buildInput({ versionDetails: {} }));
  assert.equal(issues.length, 1);
  assert.equal(issues[0].targetStep, 0);
  assert.match(issues[0].message, /尚未加载/);
});

test('版本详情读取中时提示稍候，而不是让用户重新选择', () => {
  const issues = collectCompositeBindingIssues(buildInput({ versionDetails: {}, loadingVersionIds: [1] }));
  assert.equal(issues.length, 1);
  assert.equal(issues[0].targetStep, 0);
  assert.match(issues[0].message, /正在读取/);
  assert.doesNotMatch(issues[0].message, /重新选择/);
});

test('表单没有计分规则时阻断保存', () => {
  const issues = collectCompositeBindingIssues(buildInput({ versionDetails: { 1: { scoreRules: [] } } }));
  assert.equal(issues.length, 1);
  assert.match(issues[0].message, /至少配置一条计分规则/);
});

test('停用的综合评价绑定仍参与校验，与后端口径一致', () => {
  const issues = collectCompositeBindingIssues(
    buildInput({
      bindings: [{ formVersionId: 1, usageType: 'COMPOSITE', status: 0 }],
      stages: [{ contents: [scoredContent('COURSE', { isScored: 0 })] }]
    })
  );
  assert.equal(issues.length, 1);
  assert.equal(issues[0].blocked, true);
});

test('全部判据通过时返回空列表', () => {
  assert.deepEqual(collectCompositeBindingIssues(buildInput()), []);
});

test('跳转步骤优先回到需要补数据的阶段与内容', () => {
  const stageIssue = { blocked: true, message: '缺少课程计分内容', targetStep: 1 };
  const bindingIssue = { blocked: true, message: '表单权重合计不为 1', targetStep: 0 };
  assert.equal(resolvePrecheckTargetStep([bindingIssue, stageIssue]), 1);
  assert.equal(resolvePrecheckTargetStep([bindingIssue]), 0);
  assert.equal(resolvePrecheckTargetStep([]), 0);
  assert.equal(resolvePrecheckTargetStep([], 1), 1);
});

test('工作台在保存前用已保存状态做预检，未通过则不提交请求', async () => {
  const workspace = await readFile(new URL(workspacePath, import.meta.url), 'utf8');
  assert.match(workspace, /collectCompositeBindingIssues/);
  assert.match(workspace, /stagesSaved: stagesStepCompleted\.value/);
  assert.match(workspace, /const blockingBindingIssues = computed/);
  assert.match(workspace, /async function confirmBindingPrecheck/);
  assert.match(workspace, /if \(!\(await confirmBindingPrecheck\(\)\)\) return;/);
  assert.match(workspace, /activeStep\.value = resolvePrecheckTargetStep\(issues\)/);
  assert.match(workspace, /error\.code === 8128/);
  assert.match(workspace, /:precheck-issues="blockingBindingIssues"/);
});

test('绑定面板在保存前展示全部阻断项', async () => {
  const panel = await readFile(new URL(panelPath, import.meta.url), 'utf8');
  assert.match(panel, /precheckIssues: CompositeBindingIssue\[\]/);
  assert.match(panel, /class="precheck-panel"/);
  assert.match(panel, /v-for="\(issue, index\) in props\.precheckIssues"/);
  assert.match(panel, /绑定前检查未通过/);
});
