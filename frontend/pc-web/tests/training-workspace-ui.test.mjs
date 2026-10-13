import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { URL } from 'node:url';

const workspaces = [
  [
    '培训标准',
    '../src/views/admin/business-center/training/standard/workspace/index.vue',
    ['StandardBasicPanel', 'StandardRulesPanel', 'StandardPublishPanel']
  ],
  [
    '培训模板',
    '../src/views/admin/business-center/training/template/workspace/index.vue',
    ['TemplateBasicPanel', 'TemplateStagesPanel', 'TemplatePublishPanel']
  ],
  [
    '评价表单',
    '../src/views/admin/business-center/training/evaluation-form/workspace/index.vue',
    ['EvaluationFormBasicPanel', 'EvaluationFormItemsPanel', 'EvaluationFormPublishPanel']
  ],
  [
    '培训计划',
    '../src/views/admin/business-center/training/plan/workspace/index.vue',
    ['PlanBasicPanel', 'PlanStagesPanel', 'PlanEvaluationPanel', 'PlanExecutionPanel', 'PlanProcessResultsPanel']
  ]
];

for (const [name, relativePath, panelNames] of workspaces) {
  test(`${name}工作台使用统一详情页步骤和步骤业务组件`, async () => {
    const source = await readFile(new URL(relativePath, import.meta.url), 'utf8');
    const panelSources = await Promise.all(
      panelNames.map((panelName) =>
        readFile(
          new URL(
            `../src/views/admin/business-center/training/${relativePath.split('/training/')[1].split('/workspace')[0]}/components/${panelName}.vue`,
            import.meta.url
          ),
          'utf8'
        )
      )
    );

    assert.match(source, /admin-detail-page__steps/);
    assert.match(source, /<section[^>]*class="admin-detail-page/);
    assert.match(source, /<header class="admin-detail-page__header">/);
    assert.match(source, /<nav class="stage-nav admin-detail-page__steps"/);
    assert.match(source, /<main class="admin-detail-page__surface">/);
    assert.match(source, /class="stage-item"/);
    for (const [index, panelName] of panelNames.entries()) {
      const panelSource = panelSources[index];
      assert.match(source, new RegExp(panelName));
      if (panelName === 'PlanEvaluationPanel') {
        assert.match(panelSource, /composite-binding-card/);
        assert.doesNotMatch(panelSource, /<slot/);
        assert.doesNotMatch(panelSource, /admin-detail-page__surface/);
        continue;
      }
      assert.match(panelSource, /admin-detail-page__card/);
      assert.match(panelSource, /admin-detail-page__form-scroll/);
      assert.match(panelSource, /sticky-actions/);
      assert.doesNotMatch(panelSource, /<slot/);
      assert.doesNotMatch(panelSource, /admin-detail-page__surface/);
    }
  });

  test(`${name}工作台不向步骤组件传递插槽内容`, async () => {
    const source = await readFile(new URL(relativePath, import.meta.url), 'utf8');
    for (const panelName of panelNames) {
      assert.doesNotMatch(source, new RegExp(`<${panelName}\\b[\\s\\S]*?>[\\s\\S]*?<\\/${panelName}>`));
    }
  });
}

test('培训标准工作台按已保存步骤控制导航，并在发布前展示完整配置摘要', async () => {
  const [workspace, rulesPanel, publishPanel] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/training/standard/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/standard/components/StandardRulesPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/standard/components/StandardPublishPanel.vue', import.meta.url), 'utf8')
  ]);

  assert.match(workspace, /basicStepCompleted/);
  assert.match(workspace, /rulesStepCompleted/);
  assert.match(workspace, /savedRulesFingerprint/);
  assert.match(workspace, /refreshRulesStepCompletion/);
  assert.match(workspace, /:disabled="isStepDisabled\(index\)"/);
  assert.match(rulesPanel, /ONLINE_RATIO/);
  assert.match(rulesPanel, /row\.ruleType === 'ONLINE_RATIO' \? \(props\.onlineRatioMaxPercent \?\? 100\)/);
  assert.match(workspace, /toTrainingRatioPercent/);
  assert.match(workspace, /toTrainingRatio\(rule\.requiredValue\)/);
  assert.match(rulesPanel, /无需填写要求值/);
  assert.match(rulesPanel, /固定为 %/);
  assert.match(rulesPanel, /<h2>规则编排<\/h2>/);
  assert.match(rulesPanel, /class="card-header"/);
  assert.match(rulesPanel, /class="admin-detail-page__form-scroll rules-scroll"/);
  assert.match(publishPanel, /ElDescriptions/);
  assert.match(publishPanel, /基础信息/);
  assert.match(publishPanel, /规则明细/);
});

test('评价表单与培训计划按最近确认或保存快照控制后续步骤', async () => {
  const [evaluationWorkspace, planWorkspace] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/training/evaluation-form/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8')
  ]);

  assert.match(evaluationWorkspace, /basicStepCompleted/);
  assert.match(evaluationWorkspace, /savedCompositionFingerprint/);
  assert.match(evaluationWorkspace, /const compositionStepCompleted = computed/);
  assert.match(evaluationWorkspace, /if \(step === 2\) return !basicStepCompleted\.value \|\| !compositionStepCompleted\.value/);
  assert.match(planWorkspace, /basicStepCompleted/);
  assert.match(planWorkspace, /savedStagesFingerprint/);
  assert.match(planWorkspace, /const stagesStepCompleted = computed/);
  assert.match(planWorkspace, /if \(step >= 2\) return !basicStepCompleted\.value \|\| !stagesStepCompleted\.value/);
});

test('培训计划基础信息按契约使用 ISO 本地日期时间并双列排列', async () => {
  const basicPanel = await readFile(
    new URL('../src/views/admin/business-center/training/plan/components/PlanBasicPanel.vue', import.meta.url),
    'utf8'
  );

  assert.match(basicPanel, /value-format="YYYY-MM-DDTHH:mm:ss"/);
  assert.match(basicPanel, /class="form-grid"/);
  assert.match(basicPanel, /grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/);
  assert.match(basicPanel, /@media \(max-width: 768px\)/);
});

test('培训计划阶段内容为空时提供顶部新增入口和明确空态', async () => {
  const stagesPanel = await readFile(
    new URL('../src/views/admin/business-center/training/plan/components/PlanStagesPanel.vue', import.meta.url),
    'utf8'
  );

  assert.match(stagesPanel, /<ElEmpty v-if="props\.stages\.length === 0"/);
  assert.match(stagesPanel, /暂无阶段，请新增后开始编排/);
  assert.match(stagesPanel, /<template #header>[\s\S]*新增阶段/);
  assert.doesNotMatch(stagesPanel, /<footer[\s\S]*添加阶段/);
  assert.match(stagesPanel, /阶段内容编排/);
});

test('培训计划基础信息覆盖全部可选关联契约字段', async () => {
  const [basicPanel, workspace] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanBasicPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8')
  ]);

  for (const field of ['trainingStandardId', 'sourceTemplateId', 'managerUserId']) {
    assert.match(basicPanel, new RegExp(`prop="${field}"`));
    assert.match(workspace, new RegExp(field));
  }
  assert.match(workspace, /getTrainingStandardPage/);
  assert.match(workspace, /getTrainingTemplatePage/);
  assert.match(workspace, /@training-type-change/);
});

test('培训计划评价表单按绑定契约展示版本、必填和启用状态', async () => {
  const [evaluationPanel, workspace, api] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanEvaluationPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/api/training-plan.ts', import.meta.url), 'utf8')
  ]);

  for (const token of ['formVersionId', 'usageType', 'isRequired', 'status']) {
    assert.match(evaluationPanel, new RegExp(token));
    assert.match(workspace, new RegExp(token));
  }
  assert.match(api, /'PARTICIPANT'/);
  assert.match(api, /'COURSE_QUALITY'/);
  assert.match(evaluationPanel, /composite-binding-card/);
  assert.match(evaluationPanel, /当前计划可用/);
  assert.match(evaluationPanel, /formName/);
  assert.doesNotMatch(evaluationPanel, /表单 \$\{item\.evaluationFormId\} · V\$\{item\.versionNo\}/);
  assert.match(workspace, /formNameById/);
  assert.match(evaluationPanel, /ElSwitch/);
  assert.match(evaluationPanel, /请选择已发布综合评价表单版本/);
  assert.match(evaluationPanel, /选择版本后显示课程、考试和线下成绩来源/);
  assert.match(evaluationPanel, /evaluation-hero/);
  assert.match(evaluationPanel, /evaluation-section__step/);
  assert.match(evaluationPanel, /source-card/);
  assert.match(workspace, /selectedEvaluationForms/);
  assert.match(workspace, /handleEvaluationSelection/);
  assert.match(workspace, /forms: selectedEvaluationForms\.value\.map/);
});

test('培训计划发布执行面板按生命周期展示契约动作和结果概览', async () => {
  const executionPanel = await readFile(
    new URL('../src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue', import.meta.url),
    'utf8'
  );

  for (const action of ['publish', 'generate', 'start', 'end', 'cancel']) {
    assert.match(executionPanel, new RegExp(`'${action}'`));
  }
  assert.doesNotMatch(executionPanel, /更新培训结果|emit\('action', 'sync'\)/);
  for (const status of ['DRAFT', 'PUBLISHED', 'RUNNING', 'ENDED', 'CANCELLED']) {
    assert.match(executionPanel, new RegExp(status));
  }
  assert.match(executionPanel, /lifecycle-board/);
  assert.match(executionPanel, /status-flow/);
  assert.match(executionPanel, /action-panel/);
  assert.match(executionPanel, /action-panel--empty/);
  assert.match(executionPanel, /action-panel__empty/);
  assert.match(executionPanel, /summary-grid/);
  assert.match(executionPanel, /ElEmpty/);
  assert.match(executionPanel, /查看培训结果/);
  assert.match(executionPanel, /openResultDrawer\(row\.trainingParticipantId\)/);
  assert.match(executionPanel, /resultDrawerVisible/);
  assert.match(executionPanel, /<ElDialog\s+v-model="resultDrawerVisible"/);
  assert.match(executionPanel, /training-result-dialog/);
  assert.match(executionPanel, /result-detail-table/);
  assert.match(executionPanel, /获得线上学时/);
  assert.match(executionPanel, /获得线下学时/);
  assert.match(executionPanel, /获得学时/);
  assert.match(executionPanel, /effectiveDurationSeconds/);
  assert.match(executionPanel, /系统按绑定评价表自动汇总/);
  assert.doesNotMatch(executionPanel, /综合成绩计算记录|计算综合成绩|确认综合成绩/);
});

test('培训计划执行区在结束前复用既有事实提示阻塞项', async () => {
  const [workspace, executionPanel] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue', import.meta.url), 'utf8')
  ]);

  assert.match(executionPanel, /结束前检查/);
  assert.match(executionPanel, /refreshEndPrecheck/);
  assert.match(executionPanel, /endPrecheckBlockers/);
  assert.match(executionPanel, /:disabled="!canEndPlan"/);
  assert.match(executionPanel, /取消后保留已有事实且不会产生通过结论/);
  assert.match(executionPanel, /end-precheck__grid/);
  assert.match(executionPanel, /end-precheck__item/);
  assert.match(executionPanel, /PLAN CLOSURE/);
  assert.match(executionPanel, /endPrecheckDrawerVisible/);
  assert.match(executionPanel, /查看检查/);
  assert.match(workspace, /loadEndPrecheck/);
  assert.match(workspace, /getTrainingParticipantResults/);
  assert.match(workspace, /IN_PROGRESS/);
  assert.match(workspace, /result\.compositeScore == null/);
  assert.doesNotMatch(workspace, /evaluation:calculation/);
  assert.match(workspace, /hasCompositeEvaluationBinding/);
  assert.match(workspace, /课程学习或考试作答结束/);
  assert.doesNotMatch(workspace, /class="execution-workspace"/);
  assert.match(workspace, /@refresh-end-precheck="loadEndPrecheck"/);
});

test('培训计划工作台按契约完整显示计划发布状态标签', async () => {
  const source = await readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8');

  assert.match(source, /PLAN_STATUS_META/);
  for (const status of ['DRAFT', 'PUBLISHED', 'RUNNING', 'ENDED', 'CANCELLED', 'ARCHIVED']) {
    assert.match(source, new RegExp(status));
  }
  for (const label of ['草稿', '已发布', '执行中', '已结束', '已取消', '已归档']) {
    assert.match(source, new RegExp(label));
  }
  assert.match(source, /plan-title__status/);
});

test('所有培训工作台标题区均显示各自已有的状态标签', async () => {
  const sources = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/training/standard/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/template/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/evaluation-form/workspace/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/evaluation-form/version/index.vue', import.meta.url), 'utf8')
  ]);

  for (const source of sources) {
    const header = source.match(/<header class="admin-detail-page__header">([\s\S]*?)<\/header>/)?.[1] ?? '';
    assert.match(header, /ElTag/);
  }
});
