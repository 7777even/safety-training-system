import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const editorPageUrl = new URL('../src/views/admin/business-center/exams/paper/editor/index.vue', import.meta.url);
const workspaceStateUrl = new URL('../src/views/admin/business-center/exams/paper/editor/paper-workspace-state.ts', import.meta.url);
const pickerDialogUrl = new URL('../src/views/admin/business-center/exams/paper/components/QuestionBankPickerDialog.vue', import.meta.url);
const previewDialogUrl = new URL('../src/views/admin/business-center/exams/paper/components/PaperPreviewDialog.vue', import.meta.url);
const previewContentUrl = new URL('../src/views/admin/business-center/exams/paper/components/PaperPreviewContent.vue', import.meta.url);
const versionPageUrl = new URL('../src/views/admin/business-center/exams/paper/version/index.vue', import.meta.url);

test('试卷工作台在同页完成新建、编辑、题库选题、组卷与结构化预览', async () => {
  const [editorPage, versionPage, pickerDialog, previewDialog] = await Promise.all([readFile(editorPageUrl, 'utf8'), readFile(versionPageUrl, 'utf8'), readFile(pickerDialogUrl, 'utf8'), readFile(previewDialogUrl, 'utf8')]);

  assert.match(editorPage, /createPaper/);
  assert.match(editorPage, /updatePaper/);
  assert.match(editorPage, /getPaperVersions/);
  assert.match(editorPage, /getPaperVersion/);
  assert.match(editorPage, /QuestionBankPickerDialog/);
  assert.match(editorPage, /添加题目/);
  assert.match(editorPage, /multiSelectScoring/);
  assert.match(editorPage, /totalScore/);
  assert.match(editorPage, /passScore/);
  assert.match(editorPage, /drawCount/);
  assert.match(editorPage, /sortOrder/);
  assert.match(editorPage, /PaperPreview/);
  assert.match(editorPage, /PaperPreviewDialog/);
  assert.doesNotMatch(editorPage, /JSON\.stringify/);
  assert.match(versionPage, /getPaperVersion/);
  assert.match(versionPage, /paperQuestionId/);
  assert.match(pickerDialog, /getQuestionBankPage/);
  assert.match(pickerDialog, /getQuestionBankCategories/);
  assert.match(pickerDialog, /getQuestionPage/);
  assert.match(pickerDialog, /ElTree/);
  assert.match(pickerDialog, /题库名称/);
  assert.match(previewDialog, /PaperPreview/);
  assert.match(previewDialog, /ElDialog/);
  for (const page of [editorPage, versionPage]) assert.doesNotMatch(page, /ElDialog|ElDrawer/);
});

test('试卷基础信息使用分类选择和封面上传，并把分类联动到题库选题弹窗', async () => {
  const [editorPage, pickerDialog] = await Promise.all([readFile(editorPageUrl, 'utf8'), readFile(pickerDialogUrl, 'utf8')]);

  assert.match(editorPage, /getQuestionBankCategories/);
  assert.match(editorPage, /UnifiedFileUpload/);
  assert.match(editorPage, /uploadAssetFile/);
  assert.match(editorPage, /basic\.coverFileId = fileInfo\.fileInfoId/);
  assert.match(editorPage, /v-model="basic\.paperCategoryId"/);
  assert.match(editorPage, /:default-bank-category-id="basic\.paperCategoryId"/);
  assert.doesNotMatch(editorPage, /<ElInputNumber v-model="basic\.(?:paperCategoryId|coverFileId)"/);
  assert.match(pickerDialog, /defaultBankCategoryId/);
  assert.match(pickerDialog, /v-model="currentCategoryId"/);
  assert.match(pickerDialog, /'query\.bankCategoryId': currentCategoryId\.value/);
  assert.match(pickerDialog, /changeCategory/);
  assert.match(pickerDialog, /changeBank/);
  assert.match(pickerDialog, /ElMessage\.error/);
});

test('试卷组卷字段使用 label-value 字典，并展示已选题目的编码和题干', async () => {
  const [editorPage, pickerDialog] = await Promise.all([readFile(editorPageUrl, 'utf8'), readFile(pickerDialogUrl, 'utf8')]);

  for (const token of ['QUESTION_ORDER_MODE_OPTIONS', 'MULTI_SELECT_SCORING_OPTIONS', 'QUESTION_TYPE_OPTIONS', 'DRAW_DIFFICULTY_OPTIONS']) {
    assert.match(editorPage, new RegExp(token));
  }
  assert.match(editorPage, /selectedQuestionDetails/);
  assert.match(editorPage, /questionCode/);
  assert.match(editorPage, /currentVersion\?\.stem/);
  assert.match(pickerDialog, /getEnumLabel/);
  assert.match(pickerDialog, /QUESTION_TYPE_OPTIONS/);
  assert.match(pickerDialog, /QUESTION_DIFFICULTY_OPTIONS/);
  assert.doesNotMatch(editorPage, /<ElInput v-model="compose\.(?:questionOrderMode|multiSelectScoring)"/);
});

async function loadWorkspaceState() {
  const source = await readFile(workspaceStateUrl, 'utf8');
  const output = (await import('typescript')).transpileModule(source, {
    compilerOptions: {module: 99, target: 9}
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('管理员在单页工作台按基础信息、组卷配置和发布阶段创建试卷', async () => {
  const [editorPage, workspaceState] = await Promise.all([readFile(editorPageUrl, 'utf8'), loadWorkspaceState()]);

  for (const stage of ['基础信息', '组卷配置', '发布']) {
    assert.match(editorPage, new RegExp(stage));
  }
  assert.match(editorPage, /stage-nav/);
  assert.match(editorPage, /stage === 'basic'/);
  assert.match(editorPage, /stage === 'compose'/);
  assert.match(editorPage, /ElCard v-else shadow="never" class="publish-stage(?: [^"]+)?"/);
  assert.match(editorPage, /getPaperWorkspaceNextStage\('basic-saved'\)/);
  assert.match(editorPage, /previewPaperFor\('preview-and-publish'\)/);
  assert.match(editorPage, /canEnterPaperWorkspaceStage\(paperId, item\.name\)/);
  assert.match(editorPage, /当前阶段尚未完成预览/);
  assert.match(editorPage, /<ElButton @click="showPreview">预览试卷<\/ElButton>/);
  assert.match(editorPage, /<ElButton type="primary" @click="previewAndEnterPublish">预览并进入发布<\/ElButton>/);
  assert.match(editorPage, /class="draw-rules-table"/);
  assert.equal(workspaceState.canEnterPaperWorkspaceStage(0, 'basic'), true);
  assert.equal(workspaceState.canEnterPaperWorkspaceStage(0, 'compose'), false);
  assert.equal(workspaceState.canEnterPaperWorkspaceStage(101, 'publish'), true);
  assert.equal(workspaceState.getPaperWorkspaceNextStage('basic-saved'), 'compose');
  assert.equal(workspaceState.getPaperWorkspaceNextStage('preview-and-publish'), 'publish');
  assert.equal(workspaceState.getPaperWorkspaceStageAfterPreview('compose', 'preview-only'), 'compose');
  assert.equal(workspaceState.getPaperWorkspaceStageAfterPreview('compose', 'preview-and-publish'), 'publish');
  assert.doesNotMatch(editorPage, /<ElButton type="primary" @click="publish">发布版本<\/ElButton>/);
});

test('预览并进入发布时将试卷预览内容展示在发布确认中', async () => {
  const [editorPage, previewContent] = await Promise.all([readFile(editorPageUrl, 'utf8'), readFile(previewContentUrl, 'utf8')]);

  assert.match(editorPage, /import PaperPreviewContent/);
  assert.match(editorPage, /<ElDivider>试卷预览<\/ElDivider>/);
  assert.match(editorPage, /<PaperPreviewContent/);
  assert.match(editorPage, /:show-summary="false"/);
  assert.match(previewContent, /showSummary/);
  assert.match(previewContent, /题目预览/);
  assert.match(previewContent, /随机抽题规则/);
});
