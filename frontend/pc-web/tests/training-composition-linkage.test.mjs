import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { URL } from 'node:url';

const read = (path) => readFile(new URL(path, import.meta.url), 'utf8');

test('培训标准和模板工作台展示规则检查提示，并支持手动快捷添加指定内容', async () => {
  const [standardWorkspace, standardRulesPanel, templateWorkspace, templateStagesPanel] = await Promise.all([
    read('../src/views/admin/business-center/training/standard/workspace/index.vue'),
    read('../src/views/admin/business-center/training/standard/components/StandardRulesPanel.vue'),
    read('../src/views/admin/business-center/training/template/workspace/index.vue'),
    read('../src/views/admin/business-center/training/template/components/TemplateStagesPanel.vue')
  ]);

  assert.match(standardWorkspace, /trainingTypeBaseline/);
  assert.match(standardWorkspace, /ruleComplianceSummary/);
  assert.match(standardWorkspace, /applyTrainingTypeDefaultRules/);
  assert.doesNotMatch(standardWorkspace, /<ElAlert v-if="activeStep === 1"/);
  assert.match(standardRulesPanel, /baselineText/);
  assert.match(standardRulesPanel, /summaryText/);
  assert.match(standardRulesPanel, /培训类型/);
  assert.match(templateWorkspace, /trainingCompositionSummary/);
  assert.match(templateWorkspace, /getTrainingStandardDetail/);
  assert.match(templateWorkspace, /selectedStandard\.value = undefined/);
  assert.match(templateWorkspace, /初培最低/);
  assert.match(templateWorkspace, /standardRuleHints/);
  assert.doesNotMatch(templateWorkspace, /applyStandardRuleHint/);
  assert.match(templateWorkspace, /:composition-summary="trainingCompositionSummary"/);
  assert.match(templateStagesPanel, /compositionSummary/);
  assert.match(templateStagesPanel, /规则检查提示/);
  assert.match(templateStagesPanel, /当前为自定义编排/);
  assert.match(templateStagesPanel, /返回关联培训标准/);
  assert.match(templateStagesPanel, /rule-empty-state/);
  assert.match(templateStagesPanel, /添加到阶段/);
  assert.match(templateStagesPanel, /addRequiredContent/);
  assert.match(templateWorkspace, /function addRequiredContent/);
  assert.match(templateWorkspace, /replaceTrainingTemplateStages/);
});

test('考试和题库练习的学时固定归零且不提供编辑入口', async () => {
  const [planWorkspace, planStagesPanel, templateWorkspace, templateStagesPanel, templatePublishPanel] = await Promise.all([
    read('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanStagesPanel.vue'),
    read('../src/views/admin/business-center/training/template/workspace/index.vue'),
    read('../src/views/admin/business-center/training/template/components/TemplateStagesPanel.vue'),
    read('../src/views/admin/business-center/training/template/components/TemplatePublishPanel.vue')
  ]);

  for (const source of [planWorkspace, templateWorkspace]) {
    assert.match(source, /normalizeAssessmentRequiredHours\(stages\.value\)/);
    assert.match(source, /content\.contentType === 'EXAM' \|\| content\.contentType === 'QUESTION_BANK'\) content\.requiredHours = 0/);
  }
  for (const source of [planStagesPanel, templateStagesPanel]) {
    assert.match(source, /isAssessmentContent\(row\.contentType\)/);
    assert.match(source, /0 学时（不计入）/);
    assert.match(source, /<ElInputNumber v-else v-model="row\.requiredHours"/);
  }
  assert.match(templatePublishPanel, /summarizeTrainingComposition\(props\.stages\)/);
});

test('新建培训标准保存后进入规则编排并重新加载标准详情', async () => {
  const standardWorkspace = await read('../src/views/admin/business-center/training/standard/workspace/index.vue');

  assert.match(standardWorkspace, /const id = await createTrainingStandard/);
  assert.match(standardWorkspace, /await router\.replace\(`\/admin\/business-center\/training\/standard\/\$\{id\}`\)/);
  assert.match(standardWorkspace, /await loadDetail\(\)/);
  assert.match(standardWorkspace, /activeStep\.value = 1/);
});

test('培训计划按类型筛选来源，并由服务端复制模板课表', async () => {
  const [workspace, basicPanel, stagesPanel] = await Promise.all([
    read('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanBasicPanel.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanStagesPanel.vue')
  ]);

  assert.match(workspace, /confirmClearStages/);
  assert.match(workspace, /applyTrainingPlanTemplate/);
  assert.doesNotMatch(workspace, /applyTemplateStages/);
  assert.doesNotMatch(workspace, /getTrainingTemplate\(/);
  assert.match(workspace, /getTrainingStandardDetail/);
  assert.match(workspace, /sourceTemplateText/);
  assert.match(workspace, /async function loadPlan\(targetPlanId = planId\.value\)/);
  assert.match(workspace, /let targetPlanId = plan\.value\?\.trainingPlanId/);
  assert.match(workspace, /targetPlanId = id/);
  assert.match(workspace, /await loadPlan\(targetPlanId\)/);
  assert.match(workspace, /已更新草稿模板来源，保存基础信息后生效；不会复制或清空当前课表/);
  assert.match(workspace, /formatPublishIssues/);
  assert.match(basicPanel, /template\.trainingTypeId === props\.model\.trainingTypeId/);
  assert.doesNotMatch(basicPanel, /template\.trainingStandardId === props\.model\.trainingStandardId/);
  assert.match(stagesPanel, /complianceSummary/);
  assert.match(stagesPanel, /composition-board/);
  assert.match(stagesPanel, /metric-grid/);
  assert.match(stagesPanel, /required-content-list/);
  assert.match(stagesPanel, /选择添加阶段/);
  assert.doesNotMatch(stagesPanel, /ElDescriptions/);
});

test('新建培训计划带模板时只读取服务端已复制的阶段内容', async () => {
  const workspace = await read('../src/views/admin/business-center/training/plan/workspace/index.vue');

  assert.doesNotMatch(workspace, /pendingStages/);
  assert.match(workspace, /await loadPlan\(targetPlanId\);/);
});

test('培训计划发布前重新读取最新版本再执行预检和发布', async () => {
  const workspace = await read('../src/views/admin/business-center/training/plan/workspace/index.vue');

  assert.match(workspace, /if \(action === 'publish'\) \{[\s\S]*?await loadPlan\(plan\.value\.trainingPlanId\);[\s\S]*?precheckTrainingPlanPublish\(plan\.value\.trainingPlanId\)[\s\S]*?publishTrainingPlan\(plan\.value\.trainingPlanId, plan\.value\.version\)/);
});

test('培训计划评价表单仅使用用途匹配版本并提示评分来源', async () => {
  const [workspace, evaluationPanel] = await Promise.all([
    read('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanEvaluationPanel.vue')
  ]);

  assert.match(workspace, /getEvaluationFormVersionDetail/);
  assert.match(workspace, /evaluationContextHints/);
  assert.match(workspace, /formType === 'COMPOSITE'/);
  assert.match(workspace, /itemName: item\?\.itemName/);
  assert.match(workspace, /maxScore: item\?\.maxScore/);
  assert.match(evaluationPanel, /contextHints/);
  assert.match(evaluationPanel, /综合成绩来源配置/);
  assert.match(evaluationPanel, /表单层权重/);
  assert.match(evaluationPanel, /评价项目/);
  assert.match(evaluationPanel, /项目要求/);
  assert.match(evaluationPanel, /compositeSourceCards/);
});

test('加载已有评价表单绑定时同步读取冻结评分规则，避免来源可用性空白', async () => {
  const workspace = await read('../src/views/admin/business-center/training/plan/workspace/index.vue');

  assert.match(
    workspace,
    /selectedEvaluationForms\.value = data\.evaluationForms \?\? \[\];\s*(?:\/\/.*\s*)?await loadEvaluationContext\(selectedEvaluationForms\.value\.map\(\(item\) => item\.formVersionId\)\);/
  );
});

test('培训计划四类内容可维护发布所需执行配置', async () => {
  const [api, workspace, stagesPanel, executionDialog] = await Promise.all([
    read('../src/api/training-plan.ts'),
    read('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanStagesPanel.vue'),
    read('../src/views/admin/business-center/training/components/TrainingExecutionConfigDialog.vue')
  ]);

  for (const field of [
    'assessmentMode',
    'executionConfig',
    'completionRule',
    'durationSeconds',
    'passScore',
    'maxAttempts',
    'drawRules',
    'teacherUserId',
    'location',
    'startTime',
    'endTime'
  ]) {
    assert.match(api, new RegExp(field));
    assert.match(stagesPanel, new RegExp(field));
  }
  assert.match(stagesPanel, /执行配置/);
  assert.match(stagesPanel, /已配置/);
  assert.match(stagesPanel, /待配置/);
  assert.match(stagesPanel, /TrainingExecutionConfigDialog/);
  assert.match(stagesPanel, /validateExecutionDraft/);
  assert.match(executionDialog, /value-format="YYYY-MM-DDTHH:mm:ss"/);
  assert.match(executionDialog, /JSON\.parse\(JSON\.stringify/);
  assert.match(workspace, /:teacher-options="userOptions"/);
  assert.match(workspace, /assessmentMode: undefined/);
  assert.match(workspace, /executionConfig: undefined/);
});

test('线下执行配置不再包含签到语义', async () => {
  const sources = await Promise.all([
    read('../src/api/training-plan.ts'),
    read('../src/views/admin/business-center/training/components/TrainingExecutionConfigDialog.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanStagesPanel.vue'),
    read('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    read('../src/views/admin/business-center/training/template/components/TemplateStagesPanel.vue'),
    read('../src/views/admin/business-center/training/template/components/TemplatePublishPanel.vue')
  ]);
  for (const source of sources) {
    assert.doesNotMatch(source, /signInStartTime|signInEndTime|requireSignOut|attendanceRule|签到窗口|是否要求签退/);
  }
});

test('培训计划选择资源时必须按内容类型匹配，避免题库被识别为考试', async () => {
  const workspace = await read('../src/views/admin/business-center/training/plan/workspace/index.vue');

  assert.match(workspace, /resources\.value\.find\(\(item\) => item\.resourceId === resourceId && item\.resourceType === content\.contentType\)/);
});

test('培训模板可保存执行配置默认值，计划复制后仍由计划补齐本期配置', async () => {
  const [templateApi, templateWorkspace, templateStagesPanel, planWorkspace] = await Promise.all([
    read('../src/api/training-template.ts'),
    read('../src/views/admin/business-center/training/template/workspace/index.vue'),
    read('../src/views/admin/business-center/training/template/components/TemplateStagesPanel.vue'),
    read('../src/views/admin/business-center/training/plan/workspace/index.vue')
  ]);

  assert.match(templateApi, /assessmentMode/);
  assert.match(templateApi, /executionConfig/);
  assert.match(templateWorkspace, /executionConfig: content\.executionConfig/);
  assert.match(templateWorkspace, /assessmentMode: content\.assessmentMode/);
  assert.match(templateStagesPanel, /TrainingExecutionConfigDialog/);
  assert.match(templateStagesPanel, /teacherOptions/);
  assert.match(templateStagesPanel, /执行配置/);
  assert.match(planWorkspace, /applyTrainingPlanTemplate/);
  assert.match(templateStagesPanel, /计划中补充/);
});

test('培训计划发布前必须支持批量绑定参训人员并提示前置要求', async () => {
  const [api, workspace, executionPanel] = await Promise.all([
    read('../src/api/training-plan.ts'),
    read('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue')
  ]);

  assert.match(api, /PersonSelectionRequest/);
  assert.match(api, /bindTrainingPlanParticipantSelection/);
  assert.match(api, /participants\/bind-selection/);
  assert.match(workspace, /bindTrainingPlanParticipantSelection/);
  assert.match(workspace, /handleParticipantSelection/);
  assert.match(workspace, /发布前请先配置至少一名参训人员/);
  assert.match(workspace, /participants\.value\.length <= 0/);
  assert.match(executionPanel, /PersonSelectionDialog/);
  assert.match(executionPanel, /选择参训人员范围/);
  assert.match(executionPanel, /发布前必须至少配置一名具体参训人/);
  assert.match(executionPanel, /participantSelection/);
  assert.match(executionPanel, /:model-value="participantSelection"/);
  assert.match(executionPanel, /@confirm="confirmParticipantSelection"/);
});

test('培训标准指定考试只使用可冻结试卷版本资源', async () => {
  const [workspace, api] = await Promise.all([
    read('../src/views/admin/business-center/training/standard/workspace/index.vue'),
    read('../src/api/training-reference.ts')
  ]);

  assert.match(workspace, /getTrainingReferenceResourcePage/);
  assert.match(workspace, /'query\.resourceType': 'EXAM'/);
  assert.match(workspace, /versionId/);
  assert.match(api, /可执行版本 ID/);
});

test('培训模块核心可编辑表单明确标识必填字段', async () => {
  const files = await Promise.all([
    read('../src/views/admin/business-center/training/standard/components/StandardBasicPanel.vue'),
    read('../src/views/admin/business-center/training/standard/components/StandardRulesPanel.vue'),
    read('../src/views/admin/business-center/training/template/components/TemplateBasicPanel.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanBasicPanel.vue'),
    read('../src/views/admin/business-center/training/evaluation-form/components/EvaluationFormBasicPanel.vue'),
    read('../src/views/admin/business-center/training/components/TrainingExecutionConfigDialog.vue'),
    read('../src/views/admin/business-center/training/type/index.vue'),
    read('../src/views/admin/business-center/training/template/index.vue'),
    read('../src/views/admin/business-center/training/evaluation-form/components/EvaluationFormPublishPanel.vue')
  ]);

  for (const source of files) {
    assert.match(source, /required/);
  }
  assert.match(files[0], /label="标准编码"[^>]*required/);
  assert.match(files[1], /必填/);
  assert.match(files[2], /label="模板编码"[^>]*required/);
  assert.match(files[3], /label="计划编码"[^>]*required/);
  assert.match(files[4], /label="表单编码"[^>]*required/);
  assert.match(files[5], /label="授课教师" required/);
  assert.match(files[1], /required-asterisk/);
  assert.match(files[5], /required-asterisk/);
  assert.match(files[6], /label="初培最低学时"[^>]*required/);
  assert.match(files[6], /label="复审最低学时"[^>]*required/);
  assert.match(files[7], /label="分类名称"[^>]*required/);
  assert.match(files[8], /label="版本总分"[^>]*required/);
});

test('培训模板允许保存缺少本期执行配置的课表方案', async () => {
  const workspace = await read('../src/views/admin/business-center/training/template/workspace/index.vue');

  assert.doesNotMatch(workspace, /isTemplateExecutionConfigured/);
  assert.doesNotMatch(workspace, /请先完成第.*项执行配置/);
  assert.match(workspace, /async function saveStages\(\)[\s\S]*?replaceTrainingTemplateStages/);
});

test('培训计划保存阶段内容前阻止不合规编排', async () => {
  const workspace = await read('../src/views/admin/business-center/training/plan/workspace/index.vue');

  assert.match(workspace, /function validatePlanStages\(\)/);
  assert.match(workspace, /complianceSummary\.value\.metrics/);
  assert.match(workspace, /阶段内容不符合要求/);
  assert.match(workspace, /async function saveStages\(\)[\s\S]*?validatePlanStages\(\)[\s\S]*?return;[\s\S]*?replaceTrainingPlanStages/);
});

test('发布预检问题转换为用户可执行的处理提示', async () => {
  const workspace = await read('../src/views/admin/business-center/training/plan/workspace/index.vue');

  assert.match(workspace, /考试没有可用的冻结试卷版本/);
  assert.match(workspace, /线上内容占比超过允许上限/);
  assert.match(workspace, /activeStep\.value = 1/);
  assert.doesNotMatch(workspace, /发布预检未通过：\$\{issues\.map\(\(item\) => `\$\{item\.code/);
});

test('题库练习按不评分语义配置，并展示规则级预检问题', async () => {
  const [api, stagesPanel, workspace, executionDialog] = await Promise.all([
    read('../src/api/training-plan.ts'),
    read('../src/views/admin/business-center/training/plan/components/PlanStagesPanel.vue'),
    read('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    read('../src/views/admin/business-center/training/components/TrainingExecutionConfigDialog.vue')
  ]);

  assert.match(api, /questionType: 'SINGLE' \| 'MULTIPLE' \| 'JUDGE' \| 'FILL' \| 'ESSAY'/);
  assert.match(api, /interface TrainingPublishIssue/);
  assert.doesNotMatch(stagesPanel, /label="及格分数" required>[\s\S]*?executionDraft\.executionConfig!\.passScore[\s\S]*?<\/ElFormItem>[\s\S]*?<ElFormItem label="最大作答次数"/);
  assert.doesNotMatch(stagesPanel, /<ElTableColumn label="每题分值"/);
  assert.match(executionDialog, /简答题只记录作答/);
  assert.match(executionDialog, /QUESTION_TYPE_OPTIONS/);
  assert.match(executionDialog, /ruleConditionsOverlap/);
  assert.match(executionDialog, /已没有可新增的题型和难度组合/);
  assert.match(executionDialog, /题目范围重叠/);
  assert.match(workspace, /预检问题明细/);
  assert.match(workspace, /requiredCount/);
  assert.match(workspace, /availableCount/);
  assert.match(workspace, /前往调整/);
});

test('培训标准总学时和线上占比规则各自只能配置一条', async () => {
  const [workspace, panel] = await Promise.all([
    read('../src/views/admin/business-center/training/standard/workspace/index.vue'),
    read('../src/views/admin/business-center/training/standard/components/StandardRulesPanel.vue')
  ]);

  assert.match(workspace, /唯一规则类型|只能配置一条/);
  assert.match(workspace, /addRule\(\)[\s\S]*?TOTAL_HOURS[\s\S]*?ONLINE_RATIO/);
  assert.match(workspace, /getInvalidRuleIndex\(\)[\s\S]*?重复配置/);
  assert.match(workspace, /总学时规则只能配置一条/);
  assert.match(workspace, /线上学时占比规则只能配置一条/);
  assert.match(workspace, /TrainingStandardApiError/);
  assert.match(workspace, /8016/);
  assert.match(panel, /ruleTypeOptionsFor/);
});

test('新建培训标准由用户选择初培或复审学时带入依据', async () => {
  const [workspace, basicPanel] = await Promise.all([
    read('../src/views/admin/business-center/training/standard/workspace/index.vue'),
    read('../src/views/admin/business-center/training/standard/components/StandardBasicPanel.vue')
  ]);

  assert.match(workspace, /defaultTrainingNature/);
  assert.match(workspace, /getDefaultTrainingHours\(type\)/);
  assert.doesNotMatch(workspace, /standardText\.includes\('复审'\)|standardText\.includes\('REVIEW'\)/);
  assert.match(basicPanel, /按初培带入/);
  assert.match(basicPanel, /按复审带入/);
});

test('评价表单绑定错误使用用户可理解的业务提示', async () => {
  const workspace = await read('../src/views/admin/business-center/training/plan/workspace/index.vue');

  assert.match(workspace, /TrainingPlanApiError/);
  assert.match(workspace, /8133/);
  assert.match(workspace, /同一评价用途只能绑定一个评价表单/);
  assert.match(workspace, /8134/);
  assert.match(workspace, /不属于当前机构或已停用/);
});

test('计划详情回填评价表单绑定并在保存后刷新版本', async () => {
  const [api, workspace, panel, expertPanel] = await Promise.all([
    read('../src/api/training-plan.ts'),
    read('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanEvaluationPanel.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanExpertInspectionPanel.vue')
  ]);

  assert.match(api, /evaluationForms\?: TrainingPlanEvaluationForm\[\]/);
  assert.match(api, /formName\?: string/);
  assert.match(workspace, /selectedEvaluationForms\.value = data\.evaluationForms/);
  assert.match(workspace, /await loadPlan\(plan\.value\.trainingPlanId\);/);
  assert.match(panel, /:multiple="false"/);
  assert.match(workspace, /保存评价表单/);
  assert.match(workspace, /evaluation-binding-layout__footer/);
  assert.match(workspace, /admin-detail-page__form-scroll evaluation-binding-layout__panels/);
  assert.match(workspace, /\.evaluation-binding-layout \{[\s\S]*?flex-direction: column;/);
  assert.match(panel, /\.panel-heading \{[\s\S]*?min-height: 92px;/);
  assert.match(expertPanel, /\.expert-binding__header \{[\s\S]*?min-height: 92px;/);
  assert.match(expertPanel, /expert-binding__body/);
});

test('已结束培训任务只展示结果，不再提供进入内容入口', async () => {
  const [pcApi, pcList, pcDetail, mobileApi, mobileList, mobileDetail] = await Promise.all([
    read('../src/api/training-learner.ts'),
    read('../src/views/portal/training/index.vue'),
    read('../src/views/portal/training/detail.vue'),
    read('../../mobile-app/src/api/training-learner.ts'),
    read('../../mobile-app/src/pages/training/index.vue'),
    read('../../mobile-app/src/pages/training/detail.vue')
  ]);

  for (const source of [pcApi, mobileApi]) assert.match(source, /publishStatus/);
  for (const source of [pcList, pcDetail, mobileList, mobileDetail]) assert.match(source, /ENDED/);
  assert.match(pcDetail, /查看结果/);
  assert.match(mobileDetail, /查看结果/);
});

test('已发布但未开始的培训计划不向学员端开放', async () => {
  const [pcApi, pcList, mobileApi, mobileList] = await Promise.all([
    read('../src/api/training-learner.ts'),
    read('../src/views/portal/training/index.vue'),
    read('../../mobile-app/src/api/training-learner.ts'),
    read('../../mobile-app/src/pages/training/index.vue')
  ]);

  for (const source of [pcApi, mobileApi]) {
    assert.match(source, /TrainingLearnerPlanStatus = 'RUNNING' \| 'ENDED'/);
    assert.doesNotMatch(source, /TrainingLearnerPlanStatus = 'PUBLISHED'/);
  }
  for (const source of [pcList, mobileList]) {
    assert.match(source, /RUNNING: '进行中'/);
    assert.doesNotMatch(source, /PUBLISHED: '已发布'/);
  }
});

test('培训计划内容全部安排后隐藏重复安排按钮并突出开始计划', async () => {
  const [workspace, panel, courseCenter, courseWorkspace, courseOffering] = await Promise.all([
    read('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    read('../src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue'),
    read('../src/views/admin/business-center/course/center/index.vue'),
    read('../src/views/admin/business-center/course/workspace/index.vue'),
    read('../src/views/admin/business-center/course/components/CourseOfferingPanel.vue')
  ]);

  assert.match(workspace, /const contentCount = computed/);
  assert.match(workspace, /const arrangedContentCount = computed/);
  assert.match(workspace, /:content-count="contentCount"/);
  assert.match(workspace, /:arranged-content-count="arrangedContentCount"/);
  assert.match(panel, /const allContentArranged = computed/);
  assert.match(panel, /const arrangementSummaryText = computed/);
  assert.match(panel, /if \(allContentArranged\.value\) return props\.generationResult \? '点击查看安排明细' : '已安排完成'/);
  assert.match(panel, /已安排 \$\{props\.arrangedContentCount\}\/\$\{props\.contentCount\}，请继续处理/);
  assert.match(panel, /const arrangeButtonText = computed/);
  assert.match(panel, /继续安排培训内容/);
  assert.match(panel, /publishStatus === 'PUBLISHED' && !allContentArranged/);
  assert.match(panel, /培训内容已全部安排，请确认后开始培训/);
  assert.match(panel, /查看安排明细/);
  assert.match(workspace, /code: failure\?\.code/);
  assert.match(workspace, /offeringId: failure\?\.offeringId/);
  assert.match(panel, /generationFailureGuide/);
  assert.match(panel, /getCourseOfferingDetail/);
  assert.match(panel, /offering\.offeringName/);
  assert.match(panel, /去配置阅卷人/);
  assert.match(panel, /openingOfferingId/);
  assert.match(panel, /generation-items__detail/);
  assert.match(courseCenter, /openingOfferingId/);
  assert.match(courseWorkspace, /:opening-offering-id="openingOfferingId"/);
  assert.match(courseOffering, /课程测验阅卷人/);
  assert.match(courseOffering, /openRequestedOffering/);
  assert.match(panel, /继续安排培训内容/);
});

test('培训标准发布确认展示指定课程和考试名称', async () => {
  const [workspace, panel] = await Promise.all([
    read('../src/views/admin/business-center/training/standard/workspace/index.vue'),
    read('../src/views/admin/business-center/training/standard/components/StandardPublishPanel.vue')
  ]);

  assert.match(workspace, /:course-options="courseOptions"/);
  assert.match(workspace, /:exam-options="examOptions"/);
  assert.match(panel, /courseOptions/);
  assert.match(panel, /examOptions/);
  assert.match(panel, /options\.find\(\(item\) => item\.value === rule\.resourceId\)\?\.label/);
  assert.doesNotMatch(panel, /'已绑定指定资源'/);
});

test('培训模板发布确认展示资源名称并支持查看执行配置', async () => {
  const [workspace, panel] = await Promise.all([
    read('../src/views/admin/business-center/training/template/workspace/index.vue'),
    read('../src/views/admin/business-center/training/template/components/TemplatePublishPanel.vue')
  ]);

  assert.match(workspace, /:course-options="courseResourceOptions"/);
  assert.match(workspace, /:exam-options="examResourceOptions"/);
  assert.match(panel, /resourceText[\s\S]*?options\.find\(\(item\) => item\.value === content\.resourceId\)\?\.name/);
  assert.match(panel, /ElDrawer/);
  assert.match(panel, /查看配置/);
  assert.match(panel, /openExecutionConfig/);
  assert.match(panel, /executionConfig/);
});

test('新建培训计划携带模板时由创建接口原子复制课表', async () => {
  const workspace = await read('../src/views/admin/business-center/training/plan/workspace/index.vue');

  assert.match(workspace, /const id = matrixIds\.length \? await createTrainingPlanFromMatrix\(payload, matrixIds\) : await createTrainingPlan\(payload\)/);
  assert.match(workspace, /创建接口已在同一事务复制模板课表/);
  assert.doesNotMatch(workspace, /templateSelectionTask|applyTemplateStages/);
});

test('草稿计划只在确认后调用服务端覆盖复制模板课表', async () => {
  const workspace = await read('../src/views/admin/business-center/training/plan/workspace/index.vue');

  assert.match(workspace, /从模板复制会覆盖当前阶段与内容，确认继续吗？/);
  assert.match(workspace, /await applyTrainingPlanTemplate\(plan\.value\.trainingPlanId/);
  assert.match(workspace, /await loadPlan\(plan\.value\.trainingPlanId\)/);
});
