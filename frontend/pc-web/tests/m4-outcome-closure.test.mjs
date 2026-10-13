import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = (path) => readFile(new URL(path, import.meta.url), 'utf8');

test('M4 成果 API 只使用当前 OpenAPI 的档案与证书字段和端点', async () => {
  const api = await source('../src/api/m4-outcome.ts');
  assert.match(api, /certificateFileId/);
  assert.match(api, /issuedTime/);
  assert.match(api, /\/api\/archive\/trainings\/\$\{id\}\/file/);
  assert.match(api, /\/api\/certificate\/templates\/\$\{id\}\/preview/);
  assert.doesNotMatch(api, /calculation-runs/);
});

test('结束培训从计划上下文进入成果和档案，档案缺事实时禁用真实下载', async () => {
  const [workspace, archive, archivePanel, routes] = await Promise.all([
    source('../src/views/admin/business-center/training/plan/workspace/index.vue'),
    source('../src/views/admin/result/archive/index.vue'),
    source('../src/views/admin/result/archive/components/PlanArchivePanel.vue'),
    source('../src/router/admin-dynamic-routes.ts')
  ]);
  assert.match(workspace, /ended-result/);
  assert.match(workspace, /\/archive/);
  assert.match(routes, /TrainingArchive/);
  assert.match(archive, /downloadArchive/);
  // 一期档案的事实与下载动作已抽到面板组件，禁用条件按同一份判定核对。
  assert.match(archive, /Boolean\(archive\.value\?\.fileInfoId\) && \(archive\.value\?\.missingItems\.length \?\? 1\) === 0/);
  assert.match(archivePanel, /:disabled="!downloadable"/);
  assert.doesNotMatch(archive, /v-model="planId"/);
});

test('问卷填写以快照题目校验必填并将单选标准化为 optionIds 数组', async () => {
  const [page, router, bootstrap, login] = await Promise.all([
    source('../src/views/pages/SurveyFillPage.vue'),
    source('../src/router/index.ts'),
    source('../src/main.ts'),
    source('../src/views/pages/LoginPage.vue')
  ]);
  assert.match(page, /请完成所有必填题后再提交/);
  assert.match(page, /question\.questionType === 'SINGLE' \? \[singleAnswers\[question\.surveyQuestionId!\]\]/);
  assert.match(router, /\/survey\/fill\/:surveyInstanceId/);
  assert.match(bootstrap, /record\.meta\.m4PublicFlow === true/);
  assert.match(login, /record\.meta\.m4PublicFlow === true/);
});

test('问卷最小闭环提供草稿删除、题目修正、扫码投放和结果可见性', async () => {
  const [list, workspace, fill] = await Promise.all([
    source('../src/views/admin/result/survey/index.vue'),
    source('../src/views/admin/result/survey/workspace/index.vue'),
    source('../src/views/pages/SurveyFillPage.vue')
  ]);
  assert.match(list, /deleteSurvey/);
  assert.match(list, /QRCode\.toDataURL/);
  assert.match(list, /resultVisibility/);
  assert.match(list, /purpose/);
  assert.match(workspace, /removeQuestion/);
  assert.match(workspace, /removeOption/);
  assert.match(workspace, /watch\(surveyId/);
  assert.match(workspace, /router\.push\('\/admin\/result\/survey'\)/);
  assert.match(workspace, /const frozen = computed\(\(\) => detail\.value\?\.contentStatus === 'PUBLISHED'\)/);
  assert.match(workspace, /v-if="!frozen" type="primary" @click="save"/);
  assert.match(workspace, /<ElSelect v-model="form\.surveyType" :disabled="Boolean\(surveyId\)"/);
  assert.match(fill, /getSurveyStats/);
  assert.match(fill, /SUBMITTER_AFTER_SUBMIT/);
});

test('已结束成果展示计划、学员逐项详情、奖惩和平均分，结束后仍能回看专家记录', async () => {
  const [result, expert] = await Promise.all([
    source('../src/views/admin/result/training-result/index.vue'),
    source('../src/views/admin/business-center/training/plan/components/PlanExpertInspectionExecutionPanel.vue')
  ]);
  assert.match(result, /data\?\.planCode/);
  assert.match(result, /data\.averageCompositeScore/);
  assert.match(result, /data\.rewardPenalties/);
  assert.match(result, /row\.items/);
  assert.match(result, /resultFinalizedTime/);
  assert.doesNotMatch(result, /row\.compositeScore \?\? '结果待补全'/);
  assert.match(expert, /v-if="props\.formVersionId" v-loading="loading" :data="rows"/);
  assert.match(expert, /props\.publishStatus !== 'RUNNING'/);
});

test('证书管理提供模板创建、上传 fileInfoId、发布预览、规则来源选择与管理员下载', async () => {
  const [templates, rules, certificates, enumOptions] = await Promise.all([
    source('../src/views/admin/result/certificate/templates/index.vue'),
    source('../src/views/admin/result/certificate/rules/index.vue'),
    source('../src/views/admin/result/certificate/certificates/index.vue'),
    source('../src/constants/enum-options.ts')
  ]);
  assert.match(templates, /createTemplate/);
  assert.match(templates, /design\.backgroundFileId = file\.fileInfoId/);
  assert.match(templates, /publishTemplate/);
  assert.match(templates, /previewTemplate/);
  // 设计器已改为所见即所得并改用变量契约（code/label/sample），原断言绑定的旧 DOM 细节
  // value="..." 已不存在；此处按同一原意（证书编号 / 签发时间两类变量可用，且不出现错误的
  // CERTIFICATE_NO）改为断言变量编码。
  assert.match(templates, /code: 'CERT_NO'/);
  assert.match(templates, /code: 'ISSUED_TIME'/);
  assert.doesNotMatch(templates, /CERTIFICATE_NO/);
  assert.match(rules, /getRuleSourceCandidates/);
  assert.match(rules, /'query\.sourceType': sourceType\.value/);
  assert.match(certificates, /downloadCertificate/);
  assert.doesNotMatch(certificates, /Certificate\)\.fileInfoId/);
  // 证书变量「来源类型」必须按中文渲染：字典只有一处（constants/enum-options），
  // 设计器画布示例值取自字典，规则页与已签发证书页共用同一份，避免再退回英文编码。
  assert.match(enumOptions, /CERTIFICATE_SOURCE_TYPE_OPTIONS = \[\s*\{ label: '课程', value: 'COURSE' \},\s*\{ label: '理论考试', value: 'EXAM' \},\s*\{ label: '培训计划', value: 'TRAINING' \}\s*\]/);
  assert.doesNotMatch(templates, /sample: 'TRAINING'/);
  assert.match(templates, /code: 'SOURCE_TYPE'[\s\S]{0,80}getEnumLabel\('TRAINING', CERTIFICATE_SOURCE_TYPE_OPTIONS\)/);
  assert.match(rules, /const SOURCE_TYPE_OPTIONS = CERTIFICATE_RULE_SOURCE_TYPE_OPTIONS;/);
  assert.match(certificates, /const SOURCE_TYPE_OPTIONS = CERTIFICATE_SOURCE_TYPE_OPTIONS;/);
  assert.doesNotMatch(rules, /label: '培训计划', value: 'TRAINING'/);
  assert.doesNotMatch(certificates, /label: '培训计划', value: 'TRAINING'/);
});

test('证书管理三个菜单各自解析为独立叶子页面', async () => {
  const [container, templates, rules, certificates] = await Promise.all([
    source('../src/views/admin/result/certificate/index.vue'),
    source('../src/views/admin/result/certificate/templates/index.vue'),
    source('../src/views/admin/result/certificate/rules/index.vue'),
    source('../src/views/admin/result/certificate/certificates/index.vue')
  ]);
  assert.match(container, /RouterView/);
  assert.match(templates, /证书模板/);
  assert.match(rules, /签发规则/);
  assert.match(certificates, /已签发证书/);
});

test('PC 管理端保留结果术语，学员培训列表按计划阶段解释未形成的结果', async () => {
  const [enumOptions, portalList, planPanel, resultPage, archiveFacts, archivePanel, archivePage] = await Promise.all([
    source('../src/constants/enum-options.ts'),
    source('../src/views/portal/training/index.vue'),
    source('../src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue'),
    source('../src/views/admin/result/training-result/index.vue'),
    source('../src/views/admin/result/archive/components/ArchiveFactsSections.vue'),
    source('../src/views/admin/result/archive/components/PlanArchivePanel.vue'),
    source('../src/views/admin/result/archive/index.vue')
  ]);
  // 管理端维持领域术语；学员列表单独解释缺少必修内容结果的含义。
  for (const text of [enumOptions, portalList, planPanel, resultPage, archiveFacts, archivePanel, archivePage]) {
    assert.doesNotMatch(text, /结果不完整/);
    assert.doesNotMatch(text, /INCOMPLETE: '未完成'/);
  }
  assert.match(enumOptions, /\{ label: '结果待补全', value: 'INCOMPLETE' \}/);
  assert.match(resultPage, /INCOMPLETE: \{ label: '结果待补全', type: 'warning' \}/);
  assert.match(archiveFacts, /\{ label: '结果待补全', value: String\(stat\.incompleteCount\) \}/);
  assert.match(portalList, /INCOMPLETE: '有必修内容尚无最终结果'/);
  assert.match(portalList, /v-if="task.publishStatus === 'ENDED'"/);
  assert.match(portalList, /isIncompleteEnded\(task\)/);
  assert.match(portalList, /'结果待同步'/);
  assert.match(planPanel, /INCOMPLETE: '结果待补全'/);
});
