import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contentPanelUrl = new URL('../src/views/admin/business-center/course/components/CourseContentPanel.vue', import.meta.url);
const itemInspectorUrl = new URL('../src/views/admin/business-center/course/components/CourseItemInspector.vue', import.meta.url);
const pickerUrl = new URL('../src/components/asset-picker/AssetPickerDialog.vue', import.meta.url);
const publishPanelUrl = new URL('../src/views/admin/business-center/course/components/CoursePublishPanel.vue', import.meta.url);
const offeringPanelUrl = new URL('../src/views/admin/business-center/course/components/CourseOfferingPanel.vue', import.meta.url);
const courseOperationsApiUrl = new URL('../src/api/course-operations.ts', import.meta.url);
const enumOptionsUrl = new URL('../src/constants/enum-options.ts', import.meta.url);
const audienceDialogUrl = new URL('../src/views/admin/business-center/course/components/CourseOfferingAudienceDialog.vue', import.meta.url);
const personPickerUrl = new URL('../src/components/organization/PersonPickerDialog.vue', import.meta.url);

test('课程编排通过专用素材选择器选择内容和下载资料', async () => {
  const [contentSource, inspectorSource, pickerSource] = await Promise.all([
    readFile(contentPanelUrl, 'utf8'),
    readFile(itemInspectorUrl, 'utf8'),
    readFile(pickerUrl, 'utf8')
  ]);

  assert.match(contentSource, /selection-mode="multiple"/);
  assert.match(contentSource, /<AssetPickerDialog/);
  assert.match(inspectorSource, /selection-mode="single"/);
  assert.match(pickerSource, /'query\.processStatus': 'SUCCEEDED'/);
  assert.match(pickerSource, /'query\.status': 1/);
});

test('课程素材选择器将类型和处理状态枚举显示为中文标签', async () => {
  const source = await readFile(pickerUrl, 'utf8');

  assert.match(source, /getAssetTypeLabel\(row\.assetType\)/);
  assert.match(source, /getAssetProcessStatusLabel\(row\.processStatus\)/);
  assert.doesNotMatch(source, /<ElTableColumn prop="assetType" label="类型" width="100"\s*\/>/);
  assert.doesNotMatch(source, /<ElTableColumn prop="processStatus" label="处理状态" width="110"\s*\/>/);
});

test('开课在保存后设置参与范围，报名指派通过统一人员选择而不输入 ID', async () => {
  const source = await readFile(offeringPanelUrl, 'utf8');

  assert.match(source, /PersonSelectionDialog/);
  assert.match(source, /assignCourseEnrollments\(selectedOffering\.value\.courseOfferingId, selection/);
  assert.match(source, /selectionType/);
  assert.doesNotMatch(source, /assignedUserIds/);
  assert.doesNotMatch(source, /参与范围 ID/);
});

test('报名指派请求按后端契约包裹 selection', async () => {
  const source = await readFile(courseOperationsApiUrl, 'utf8');

  assert.match(source, /method: 'POST',[\s\S]+data: \{ selection \}/);
});

test('发布面板自动刷新课程乐观锁版本，历史快照在页面内只读展示', async () => {
  const source = await readFile(publishPanelUrl, 'utf8');

  assert.match(source, /getCourseDetail/);
  assert.match(source, /version: course\.value\.version/);
  assert.match(source, /getCourseVersionDetail/);
  assert.match(source, /class="snapshot-panel"/);
  assert.match(source, /getDownloadAssets/);
  assert.match(source, /课程附件/);
  assert.match(source, /assetName \|\| '未保存名称'/);
  assert.doesNotMatch(source, /v-model="courseVersion"/);
  assert.doesNotMatch(source, /<ElDialog/);
});

test('内容项单字段学习规则与直播配置覆盖 Contract 字段', async () => {
  const source = await readFile(itemInspectorUrl, 'utf8');

  for (const field of ['minimumStudySeconds', 'audienceId', 'liveForm.status']) {
    assert.ok(source.includes(field), `页面缺少 ${field}`);
  }
  for (const removedField of ['watermarkColor', 'watermarkOpacity', 'auxAudioAssetId', 'isFastForwardAllowed', 'isSpeedPlayAllowed']) {
    assert.ok(!source.includes(removedField), `页面仍包含已删除字段 ${removedField}`);
  }
  assert.match(source, /v-model="itemForm\.isLockedAfterParticipation"[\s\S]+:disabled="isEditing"/);
  // 参与后锁定只对题库练习和课程测验展示
  assert.match(source, /v-if="supportsLockAfterParticipation"/);
  assert.match(source, /itemForm\.itemType === 'QUESTION_BANK' \|\| itemForm\.itemType === 'QUIZ'/);
  assert.match(source, /supportsLockAfterParticipation\.value \? itemForm\.isLockedAfterParticipation : 0/);
});

test('开课查询与新增编辑均在页面内完成，当前课程模式固定 courseId', async () => {
  const source = await readFile(offeringPanelUrl, 'utf8');

  assert.match(source, /'query\.courseId': props\.courseId/);
  assert.match(source, /placeholder="请输入开课名称"/);
  // 开课新增/编辑在面板内的受控弹窗中完成，不再内联展开编辑区
  assert.match(source, /v-model="editorVisible"/);
  assert.match(source, /class="offering-form-grid"/);
  assert.match(source, /CourseSelect/);
  assert.doesNotMatch(source, /<ElInputNumber v-model="form\.courseId"/);
  assert.match(source, /:disabled="embedded \|\| form\.courseOfferingId !== undefined"/);
  assert.match(source, /当前课程尚未生成发布版本/);
});

test('课程状态类下拉选项以中文 label 与 Contract value 映射展示，不直接显示枚举值', async () => {
  const [offeringSource, inspectorSource, enumOptionsSource] = await Promise.all([
    readFile(offeringPanelUrl, 'utf8'),
    readFile(itemInspectorUrl, 'utf8'),
    readFile(enumOptionsUrl, 'utf8')
  ]);

  for (const source of [offeringSource, inspectorSource]) assert.doesNotMatch(source, /v-for="status in \[/);
  assert.match(offeringSource, /COURSE_PUBLISH_STATUS_OPTIONS/);
  assert.match(offeringSource, /getEnumLabel\(row\.publishStatus, COURSE_PUBLISH_STATUS_OPTIONS\)/);
  assert.match(enumOptionsSource, /label: '未开始', value: 'NOT_STARTED'/);
  assert.match(enumOptionsSource, /label: '已结束', value: 'ENDED'/);
  assert.match(inspectorSource, /label: '直播中', value: 'LIVING'/);
});

test('课程内容项覆盖 Course OpenAPI 的四种开放类型，并按类型选择已发布资源', async () => {
  const [apiSource, inspectorSource] = await Promise.all([
    readFile(new URL('../src/api/course-operations.ts', import.meta.url), 'utf8'),
    readFile(itemInspectorUrl, 'utf8')
  ]);

  assert.match(apiSource, /'ASSET' \| 'LIVE' \| 'QUESTION_BANK' \| 'QUIZ'/);
  for (const itemType of ['ASSET', 'LIVE', 'QUESTION_BANK', 'QUIZ']) {
    assert.ok(inspectorSource.includes(`value="${itemType}"`), `缺少 ${itemType} 内容类型入口`);
  }
  assert.match(inspectorSource, /题库练习/);
  assert.match(inspectorSource, /课程测验/);
  assert.match(inspectorSource, /已发布题库/);
  assert.match(inspectorSource, /已发布试卷版本/);
  assert.match(inspectorSource, /getQuestionBankPage/);
  assert.match(inspectorSource, /getPaperVersions/);
  assert.match(inspectorSource, /publishedPaperVersions/);
  // 考试 / 专项练习已由 course-assessment-source-separation 关闭，不得再出现在编排入口
  assert.doesNotMatch(inspectorSource, /'EXAM'|'PRACTICE'/);
});

test('直播讲师和参与范围通过选择器关联，不允许手输全局 ID', async () => {
  const source = await readFile(itemInspectorUrl, 'utf8');

  assert.match(source, /PersonPickerDialog/);
  assert.match(source, /ReferenceSelect/);
  assert.match(source, /选择讲师/);
  assert.match(source, /选择参与范围/);
  assert.doesNotMatch(source, /辅助音频/);
  assert.doesNotMatch(source, /label="讲师用户 ID"/);
  assert.doesNotMatch(source, /label="参与范围 ID"/);
  assert.doesNotMatch(source, /label="辅助音频素材 ID"/);
});

test('章节新增和编辑在弹窗中完成，下载资料回显已选资源名称', async () => {
  const source = await readFile(contentPanelUrl, 'utf8');

  assert.match(source, /<ElDialog\s+v-model="chapterDialogVisible"/);
  assert.match(source, /课程附件（学员可下载）/);
  assert.match(source, /downloadAssets/);
});

test('课程素材选择列表按需读取详情，不为当前页素材逐条请求', async () => {
  const source = await readFile(pickerUrl, 'utf8');

  assert.doesNotMatch(source, /page\.list\.map\(\(asset\) => getAssetDetail\(asset\.assetId\)\)/);
  assert.match(source, /async function previewAssetDetail/);
  assert.match(source, /@click="previewAssetDetail\(row as AssetListItem\)"/);
});

test('人员与参与范围默认锁定会话机构，仅租户管理员可跨机构', async () => {
  const [personSource, audienceSource, inspectorSource] = await Promise.all([
    readFile(personPickerUrl, 'utf8'),
    readFile(audienceDialogUrl, 'utf8'),
    readFile(itemInspectorUrl, 'utf8')
  ]);

  for (const source of [audienceSource, inspectorSource]) {
    assert.match(source, /canManageTenantScope/);
    assert.match(source, /currentTenantId/);
  }
  assert.match(personSource, /currentTenantId/);
  assert.match(audienceSource, /v-if="canSelectOrganization"/);
});

test('参与范围规则和解析模式使用中文 label/value 选项', async () => {
  const [source, enumOptionsSource] = await Promise.all([
    readFile(audienceDialogUrl, 'utf8'),
    readFile(enumOptionsUrl, 'utf8')
  ]);

  assert.match(source, /AUDIENCE_RESOLVE_MODE_OPTIONS/);
  assert.match(source, /AUDIENCE_SUBJECT_TYPE_OPTIONS/);
  assert.match(source, /AUDIENCE_DECISION_OPTIONS/);
  assert.match(enumOptionsSource, /label: '包含', value: 'INCLUDE'/);
  assert.match(enumOptionsSource, /label: '部门（含子部门）', value: 'DEPT'/);
  assert.doesNotMatch(source, /:label="kind === 'ALL' \? '全部人员' : kind"/);
});
