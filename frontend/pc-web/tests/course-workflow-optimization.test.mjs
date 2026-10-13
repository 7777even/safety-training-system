import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const WORKSPACE_STATE_URL = new URL('../src/views/admin/business-center/course/workspace/course-workspace-state.ts', import.meta.url);
const COURSE_CENTER_URL = new URL('../src/views/admin/business-center/course/center/index.vue', import.meta.url);
const COURSE_WORKBENCH_URL = new URL('../src/views/admin/business-center/course/workspace/index.vue', import.meta.url);
const COURSE_BASIC_PANEL_URL = new URL('../src/views/admin/business-center/course/components/CourseBasicPanel.vue', import.meta.url);
const FILE_PREVIEW_URL = new URL('../src/components/file-upload/file-preview.ts', import.meta.url);
const UPLOAD_QUEUE_URL = new URL('../src/components/file-upload/upload-queue.ts', import.meta.url);
const ASSET_PICKER_STATE_URL = new URL('../src/components/asset-picker/asset-picker-state.ts', import.meta.url);
const ASSET_PICKER_URL = new URL('../src/components/asset-picker/AssetPickerDialog.vue', import.meta.url);
const FILE_UPLOAD_URL = new URL('../src/components/file-upload/UnifiedFileUpload.vue', import.meta.url);
const PDF_VIEWER_URL = new URL('../src/components/pdf-viewer/PdfViewer.vue', import.meta.url);
const COURSE_CONTENT_PANEL_URL = new URL('../src/views/admin/business-center/course/components/CourseContentPanel.vue', import.meta.url);
const COURSE_ITEM_INSPECTOR_URL = new URL('../src/views/admin/business-center/course/components/CourseItemInspector.vue', import.meta.url);
const ASSET_LIBRARY_URL = new URL('../src/views/admin/business-center/resource-library/index.vue', import.meta.url);
const COURSE_PUBLISH_PANEL_URL = new URL('../src/views/admin/business-center/course/components/CoursePublishPanel.vue', import.meta.url);
const COURSE_OFFERING_PANEL_URL = new URL('../src/views/admin/business-center/course/components/CourseOfferingPanel.vue', import.meta.url);

/** 加载无 Vue 运行时依赖的 TypeScript 公共状态模块。 */
async function importTypeScriptModule(url) {
  const source = await readFile(url, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Math.random()}`);
}

test('课程工作台从查询参数恢复列表、新增和详情阶段', async () => {
  const state = await importTypeScriptModule(WORKSPACE_STATE_URL);

  assert.deepEqual(state.resolveCourseWorkspace({}), { mode: 'list', courseId: 0, stage: 'basic' });
  assert.deepEqual(state.resolveCourseWorkspace({ mode: 'create', stage: 'publish' }), {
    mode: 'create',
    courseId: 0,
    stage: 'basic'
  });
  assert.deepEqual(state.resolveCourseWorkspace({ courseId: '42', stage: 'content' }), {
    mode: 'detail',
    courseId: 42,
    stage: 'content'
  });
  assert.deepEqual(state.resolveCourseWorkspace({ courseId: '42', stage: 'unknown' }), {
    mode: 'detail',
    courseId: 42,
    stage: 'basic'
  });
});

test('课程保存后生成可刷新和可返回的工作台查询参数', async () => {
  const state = await importTypeScriptModule(WORKSPACE_STATE_URL);

  assert.deepEqual(state.buildCourseWorkspaceQuery(42, 'content'), { courseId: '42', stage: 'content' });
  assert.deepEqual(state.buildCourseWorkspaceQuery(42, 'offering'), { courseId: '42', stage: 'offering' });
});

test('课程中心使用同一路由切换列表和全页详情工作台', async () => {
  const [centerSource, workbenchSource, basicSource] = await Promise.all([
    readFile(COURSE_CENTER_URL, 'utf8'),
    readFile(COURSE_WORKBENCH_URL, 'utf8'),
    readFile(COURSE_BASIC_PANEL_URL, 'utf8')
  ]);

  assert.match(centerSource, /resolveCourseWorkspace\(route\.query\)/);
  assert.match(centerSource, /<CourseWorkbench/);
  assert.match(centerSource, /mode: 'create', stage: 'basic'/);
  assert.match(centerSource, /router\.push\(\{ query: buildCourseWorkspaceQuery\(courseId, stage, getCurrentTabTitle\(\)\) \}\)/);
  assert.match(centerSource, /router\.replace\(\{ query: buildCourseWorkspaceQuery\(courseId, stage, getCurrentTabTitle\(\)\) \}\)/);
  assert.doesNotMatch(centerSource, /<ElDialog/);

  for (const stage of ['basic', 'content', 'publish', 'offering']) {
    assert.equal(workbenchSource.includes(`name: '${stage}'`), true, `工作台应包含 ${stage} 阶段`);
  }
  assert.match(workbenchSource, /:disabled="[\s\S]*!courseId/);
  assert.match(workbenchSource, /course:item:list/);
  assert.match(workbenchSource, /course:course:publish/);
  assert.match(workbenchSource, /course:offering:list/);
  assert.match(workbenchSource, /props\.courseId === 0[\s\S]*emit\('replaceNavigate'[\s\S]*emit\('navigate'/);
  assert.match(basicSource, /createCourse/);
  assert.match(basicSource, /updateCourse/);
  assert.match(basicSource, /assignCourseCategories/);
  assert.match(basicSource, /version: courseForm\.version/);
  assert.match(basicSource, /emit\('saved'/);
});

test('统一文件预览按浏览器真实能力分类，不伪造 Office 和压缩包预览', async () => {
  const preview = await importTypeScriptModule(FILE_PREVIEW_URL);

  assert.equal(preview.resolveFilePreviewKind({ fileName: 'cover.png', mimeType: 'image/png' }), 'image');
  assert.equal(preview.resolveFilePreviewKind({ fileName: 'lesson.mp3', mimeType: 'audio/mpeg' }), 'audio');
  assert.equal(preview.resolveFilePreviewKind({ fileName: 'lesson.mp4', mimeType: 'video/mp4' }), 'video');
  assert.equal(preview.resolveFilePreviewKind({ fileName: 'manual.pdf', mimeType: 'application/pdf' }), 'pdf');
  assert.equal(preview.resolveFilePreviewKind({ fileName: 'readme.txt', mimeType: 'text/plain' }), 'text');
  assert.equal(preview.resolveFilePreviewKind({ fileName: 'slides.pptx' }), 'unsupported');
  assert.equal(preview.resolveFilePreviewKind({ fileName: 'archive.zip' }), 'unsupported');
});

test('统一上传队列覆盖成功、失败重试、移除和预览资源释放', async () => {
  const uploadQueue = await importTypeScriptModule(UPLOAD_QUEUE_URL);
  const item = {
    id: 'file-1',
    file: { name: '课程封面.png' },
    status: 'waiting',
    percent: 0,
    previewUrl: 'blob:preview'
  };
  let attempts = 0;

  await uploadQueue.uploadQueueItem(item, async (_file, onProgress) => {
    attempts += 1;
    onProgress(45.4);
    if (attempts === 1) throw new Error('网络中断');
    return { fileInfoId: 1, fileName: '课程封面.png', fileSize: 128 };
  });
  assert.equal(item.status, 'failed');
  assert.equal(item.percent, 45);
  assert.equal(item.error, '网络中断');

  await uploadQueue.uploadQueueItem(item, async (_file, onProgress) => {
    attempts += 1;
    onProgress(120);
    return { fileInfoId: 1, fileName: '课程封面.png', fileSize: 128 };
  });
  assert.equal(item.status, 'success');
  assert.equal(item.percent, 100);
  assert.equal(item.error, undefined);

  const revoked = [];
  assert.deepEqual(
    uploadQueue.removeUploadQueueItem([item], item.id, (url) => revoked.push(url)),
    []
  );
  assert.deepEqual(revoked, ['blob:preview']);

  uploadQueue.releaseUploadQueueItems(
    [{ ...item, id: 'file-2', previewUrl: 'blob:second' }],
    (url) => revoked.push(url)
  );
  assert.deepEqual(revoked, ['blob:preview', 'blob:second']);
});

test('素材选择状态跨分页保留，并区分单选和多选', async () => {
  const picker = await importTypeScriptModule(ASSET_PICKER_STATE_URL);
  const video = { assetId: 10, assetName: '视频' };
  const document = { assetId: 20, assetName: '文档' };

  assert.deepEqual(picker.mergeAssetSelection([video], [document], 'multiple'), [video, document]);
  assert.deepEqual(picker.mergeAssetSelection([video], [document], 'single'), [document]);
  assert.deepEqual(picker.removeAssetSelection([video, document], 10), [document]);
});

test('素材选择器与统一上传组件提供计划约定的公开能力', async () => {
  const [pickerSource, uploadSource] = await Promise.all([
    readFile(ASSET_PICKER_URL, 'utf8'),
    readFile(FILE_UPLOAD_URL, 'utf8')
  ]);

  assert.match(pickerSource, /selectionMode: 'single' \| 'multiple'/);
  assert.match(pickerSource, /selectedIds: number\[\]/);
  assert.match(pickerSource, /'query\.processStatus': 'SUCCEEDED'/);
  assert.match(pickerSource, /'query\.status': 1/);
  assert.match(pickerSource, /asset\.status === 1 && asset\.processStatus === 'SUCCEEDED'/);
  assert.match(pickerSource, /<AssetPreviewPanel/);
  assert.equal((pickerSource.match(/<ElDialog/g) ?? []).length, 1);

  assert.match(uploadSource, /mode: 'dialog' \| 'panel'/);
  assert.match(uploadSource, /uploadRequest:/);
  assert.match(uploadSource, /URL\.createObjectURL/);
  assert.match(uploadSource, /URL\.revokeObjectURL/);
  assert.match(uploadSource, /const queuedItems = queue\.value\.filter/);
  assert.match(uploadSource, /function closeDialog/);
  assert.match(uploadSource, /clearQueue\(\)/);
  assert.match(uploadSource, /emit\('update:modelValue', false\)/);
  assert.doesNotMatch(uploadSource, /<button\s+[^>]*class="queue-item"/);
  assert.doesNotMatch(uploadSource, /@\/api\/asset/);
});

test('统一上传组件在单文件模式隐藏队列文案，仅选中文件后展示文件信息', async () => {
  const uploadSource = await readFile(FILE_UPLOAD_URL, 'utf8');

  assert.match(uploadSource, /v-if="multiple" class="queue-heading"/);
  assert.match(uploadSource, /v-else class="single-file-heading">文件信息/);
  assert.match(uploadSource, /v-if="multiple \|\| activeItem"/);
});

test('统一上传弹窗在高缩放窄视口下改为上下布局并允许预览工具栏重排', async () => {
  const [uploadSource, pdfViewerSource] = await Promise.all([
    readFile(FILE_UPLOAD_URL, 'utf8'),
    readFile(PDF_VIEWER_URL, 'utf8')
  ]);

  assert.match(uploadSource, /min\(1040px, calc\(100vw - 32px\)\)/);
  assert.match(uploadSource, /max-height: calc\(100vh - 32px\)/);
  assert.match(uploadSource, /@media \(max-width: 1100px\)/);
  assert.match(uploadSource, /grid-template-columns: minmax\(320px, 2fr\) minmax\(520px, 3fr\)/);
  assert.match(uploadSource, /grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(uploadSource, /border-top: 1px solid var\(--color-border\)/);
  assert.match(pdfViewerSource, /\.pdf-toolbar \{[\s\S]*flex-wrap: wrap/);
  assert.match(pdfViewerSource, /\.pdf-toolbar \{[\s\S]*overflow-x: auto/);
});

test('课程内容编排使用三栏页面内编辑，仅素材选择器保留弹窗', async () => {
  const [contentSource, inspectorSource] = await Promise.all([
    readFile(COURSE_CONTENT_PANEL_URL, 'utf8'),
    readFile(COURSE_ITEM_INSPECTOR_URL, 'utf8')
  ]);

  assert.match(contentSource, /class="content-workspace"/);
  assert.match(contentSource, /class="chapter-pane"/);
  assert.match(contentSource, /class="item-pane"/);
  assert.match(contentSource, /Object\.keys\(itemsByChapter\)/);
  assert.match(contentSource, /<CourseItemInspector/);
  assert.match(contentSource, /<AssetPickerDialog/);
  // 三栏内编辑：内容面板唯一保留的弹窗是章节表单，内容项不再内嵌业务弹窗
  assert.match(contentSource, /<ElDialog\s+v-model="chapterDialogVisible"/);
  assert.equal((contentSource.match(/<ElDialog\b/g) ?? []).length, 1);

  assert.match(inspectorSource, /<AssetPickerDialog/);
  assert.match(inspectorSource, /要求学习时长/);
  assert.match(inspectorSource, /累计有效学习达到该时长后完成/);
  assert.doesNotMatch(inspectorSource, /MaterialRule|material-rule|材料学习规则/);
  assert.match(inspectorSource, /getLiveSession/);
  assert.match(inspectorSource, /updateLiveSession/);
  assert.doesNotMatch(inspectorSource, /<ElDialog/);
});

test('素材库从编辑抽屉打开统一文件上传选择弹窗并回显文件信息', async () => {
  const source = await readFile(ASSET_LIBRARY_URL, 'utf8');

  assert.match(source, /uploadVisible = ref\(false\)/);
  assert.match(source, /<ElDrawer[\s\S]*v-model="drawerVisible"/);
  assert.match(source, /<UnifiedFileUpload/);
  assert.match(source, /v-model="uploadVisible"/);
  assert.match(source, /mode="dialog"/);
  assert.match(source, /form\.fileInfoId = fileInfo\.fileInfoId/);
  assert.match(source, /form\.uploadedFileName = fileInfo\.fileName/);
  assert.match(source, /form\.fileSize = fileInfo\.fileSize/);
  assert.match(source, /formatFileSize\(form\.fileSize\)/);
  assert.doesNotMatch(source, /mode="panel"/);
  assert.doesNotMatch(source, /drawerMode = ref<'form' \| 'upload'>/);
});

test('发布版本与当前课程开课均在工作台页面内完成', async () => {
  const [publishSource, offeringSource, workbenchSource] = await Promise.all([
    readFile(COURSE_PUBLISH_PANEL_URL, 'utf8'),
    readFile(COURSE_OFFERING_PANEL_URL, 'utf8'),
    readFile(COURSE_WORKBENCH_URL, 'utf8')
  ]);

  assert.match(publishSource, /publishCourse/);
  assert.match(publishSource, /getCourseVersions/);
  assert.match(publishSource, /getCourseVersionDetail/);
  assert.match(publishSource, /class="snapshot-panel"/);
  assert.match(publishSource, /value\.items/);
  assert.doesNotMatch(publishSource, /<ElDialog/);

  assert.match(offeringSource, /'query\.courseId'/);
  assert.match(offeringSource, /coursePublished/);
  // 开课页已并入工作台面板：列表与新增/编辑都在面板内，编辑器用受控弹窗承载
  assert.match(offeringSource, /v-model="editorVisible"/);
  assert.match(offeringSource, /class="offering-form-grid"/);
  assert.match(workbenchSource, /<CoursePublishPanel/);
  assert.match(workbenchSource, /<CourseOfferingPanel/);
});
