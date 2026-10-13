import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(currentDirectory, '..', '..', '..');

async function readProjectFile(relativePath) {
  return readFile(path.join(projectRoot, relativePath), 'utf8');
}

test('PC Web UI 标准声明列表高度、分页大小和紧凑间距常量', async () => {
  const source = await readProjectFile('frontend/pc-web/src/constants/ui.ts');

  assert.match(source, /LIST_PAGE_SIZES/);
  assert.doesNotMatch(source, /LIST_TABLE_HEIGHT/);
  assert.doesNotMatch(source, /TREE_TABLE_HEIGHT/);
});

test('后台壳层包含折叠侧栏、面包屑和 tabs-card，并移除旧 page-header', async () => {
  const source = await readProjectFile('frontend/pc-web/src/layouts/AdminLayout.vue');

  assert.match(source, /admin-sidebar--collapsed/);
  assert.match(source, /admin-breadcrumb/);
  assert.match(source, /admin-tabs/);
  assert.match(source, /sessionStorage\.getItem\(ADMIN_TABS_STORAGE_KEY\)/);
  assert.match(source, /sessionStorage\.setItem\(ADMIN_SIDEBAR_COLLAPSED_STORAGE_KEY/);
  assert.match(source, /function clearAdminUiSession\(\): void/);
  assert.match(source, /sessionStorage\.removeItem\(ADMIN_TABS_STORAGE_KEY\)/);
  assert.match(source, /sessionStorage\.removeItem\(ADMIN_SIDEBAR_COLLAPSED_STORAGE_KEY\)/);
  assert.match(source, /if \(currentRoute\.path === LOGIN_ROUTE_PATH\) \{\s*return;/);
  assert.doesNotMatch(source, /admin-page-header/);
});

test('Element Plus 全局组件与程序化确认框使用中文文案', async () => {
  const appSource = await readProjectFile('frontend/pc-web/src/App.vue');
  const bootstrapSource = await readProjectFile('frontend/pc-web/src/main.ts');

  assert.match(appSource, /element-plus\/es\/locale\/lang\/zh-cn/);
  assert.match(appSource, /<ElConfigProvider :locale="zhCn">/);
  assert.match(bootstrapSource, /confirmButtonText: '确认'/);
  assert.match(bootstrapSource, /cancelButtonText: '取消'/);
});

test('折叠侧栏使用独立图标菜单，并隐藏展开态菜单', async () => {
  const source = await readProjectFile('frontend/pc-web/src/layouts/AdminLayout.vue');
  const collapsedMenuSource = await readProjectFile('frontend/pc-web/src/layouts/AdminSidebarCollapsedMenu.vue');

  assert.match(source, /admin-sidebar--collapsed \.admin-sidebar__expanded-menu[\s\S]*visibility: hidden/);
  assert.match(source, /admin-sidebar--collapsed \.admin-sidebar__collapsed-menu[\s\S]*visibility: visible/);
  assert.match(collapsedMenuSource, /justify-content: center/);
  assert.match(collapsedMenuSource, /padding: 0/);
});

test('后台概览页使用轻量壳层，业务页继续使用完整壳层', async () => {
  const source = await readProjectFile('frontend/pc-web/src/layouts/AdminLayout.vue');

  assert.match(source, /isOverviewPage/);
  assert.match(source, /v-if="!isOverviewPage[^\"]*"[^>]*admin-sidebar/);
  assert.match(source, /v-if="!isOverviewPage"[^>]*admin-tabs/);
  assert.match(source, /v-if="!isOverviewPage"[^>]*admin-breadcrumb/);
});

test('tabs 只有一个时不可关闭，多个时关闭按钮通过悬浮显示', async () => {
  const source = await readProjectFile('frontend/pc-web/src/layouts/AdminLayout.vue');

  assert.match(source, /tabs\.value\.forEach\(\(tab\) => \{\s*tab\.closable = tabs\.value\.length > 1/s);
  assert.match(source, /\.admin-tab__close[\s\S]*visibility: hidden/);
  assert.match(source, /\.admin-tab:hover \.admin-tab__close/);
});

test('tabs 过多时使用左右滑动按钮并隐藏原生横向滚动条', async () => {
  const source = await readProjectFile('frontend/pc-web/src/layouts/AdminLayout.vue');

  assert.match(source, /scrollTabs\(direction: 'left' \| 'right'\)/);
  assert.match(source, /ArrowLeft/);
  assert.match(source, /ArrowRight/);
  assert.match(source, /class="admin-tabs__scroll-button"/);
  assert.match(source, /overflow: hidden/);
  assert.match(source, /behavior: 'smooth'/);
});

test('业务列表页复用课程中心的公共页面结构样式', async () => {
  const source = await readProjectFile('frontend/pc-web/src/styles/element-plus.less');

  for (const className of ['admin-business-page', 'admin-business-page__header', 'admin-business-page__filters', 'admin-business-page__table']) {
    assert.match(source, new RegExp(`\\.${className.replaceAll('__', '__')}`));
  }
});

test('筛选操作按钮作为 ElFormItem 参与换行布局', async () => {
  const styleSource = await readProjectFile('frontend/pc-web/src/styles/element-plus.less');
  const formFiles = [
    'frontend/pc-web/src/views/admin/business-center/exams/paper/index.vue',
    'frontend/pc-web/src/views/admin/business-center/exams/exam/index.vue',
    'frontend/pc-web/src/views/admin/business-center/exams/exam/results/index.vue',
    'frontend/pc-web/src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue'
  ];

  assert.match(styleSource, /\.admin-business-page__filters \.el-form--inline[\s\S]*flex-wrap: wrap/);
  assert.match(styleSource, /\.admin-business-page__filters \.el-form--inline \.filter-actions[\s\S]*white-space: nowrap/);

  for (const file of formFiles) {
    const source = await readProjectFile(file);
    assert.doesNotMatch(source, /<ElForm[^>]*>[\s\S]*?<\/ElFormItem>\s*<ElButton/, `${file} 存在脱离 ElFormItem 的筛选按钮`);
  }
});

test('长期 UI 标准覆盖后台、门户和统一列表结构', async () => {
  const source = await readProjectFile('docs/design/pc-web-ui-standard.md');

  for (const phrase of ['门户与后台壳层', '第一个 `ElCard`', 'total, sizes, prev, pager, next', 'admin-business-page__table', 'v-model:page-size']) {
    assert.ok(source.includes(phrase), `UI 标准缺少：${phrase}`);
  }
});

test('详情与工作台页面复用统一的步骤、内容卡和紧凑表单标准', async () => {
  const styleSource = await readProjectFile('frontend/pc-web/src/styles/element-plus.less');
  const documentSource = await readProjectFile('docs/design/pc-web-ui-standard.md');

  for (const className of ['admin-detail-page', 'admin-detail-page__header', 'admin-detail-page__steps', 'admin-detail-page__card', 'admin-detail-form']) {
    assert.match(styleSource, new RegExp(`\\.${className}`));
  }

  for (const phrase of ['详情、编辑与工作台页', '课程中心工作台、试卷工作台和考试工作台', 'admin-detail-form__wide', '320px', 'admin-detail-action-bar']) {
    assert.ok(documentSource.includes(phrase), `详情页标准缺少：${phrase}`);
  }
});

test('现有课程、题库、试卷、考试和培训工作台已接入详情页公共样式', async () => {
  const detailFiles = [
    'frontend/pc-web/src/views/admin/business-center/course/workspace/index.vue',
    'frontend/pc-web/src/views/admin/business-center/exams/paper/workspace/index.vue',
    'frontend/pc-web/src/views/admin/business-center/exams/question/create/index.vue',
    'frontend/pc-web/src/views/admin/business-center/exams/question/detail/index.vue',
    'frontend/pc-web/src/views/admin/business-center/exams/exam/workspace/index.vue',
    'frontend/pc-web/src/views/admin/business-center/training/standard/workspace/index.vue',
    'frontend/pc-web/src/views/admin/business-center/training/template/workspace/index.vue',
    'frontend/pc-web/src/views/admin/business-center/training/evaluation-form/workspace/index.vue',
    'frontend/pc-web/src/views/admin/business-center/training/evaluation-form/version/index.vue'
  ];

  for (const file of detailFiles) {
    const source = await readProjectFile(file);
    assert.match(source, /admin-detail-page/, `${file} 未接入详情页公共布局`);
  }
});

test('详情表单复用课程中心的 ElRow、ElCol 与 sticky-actions 结构', async () => {
  const styleSource = await readProjectFile('frontend/pc-web/src/styles/element-plus.less');
  const formFiles = [
    'frontend/pc-web/src/views/admin/business-center/course/components/CourseBasicPanel.vue',
    'frontend/pc-web/src/views/admin/business-center/exams/question/create/index.vue',
    'frontend/pc-web/src/views/admin/business-center/exams/paper/workspace/index.vue',
    'frontend/pc-web/src/views/admin/business-center/training/evaluation-form/components/EvaluationFormBasicPanel.vue'
  ];

  assert.match(styleSource, /\.sticky-actions,[\s\S]*position: sticky/);
  assert.match(styleSource, /admin-detail-page__form-scroll[\s\S]*overflow: auto/);
  for (const file of formFiles) {
    const source = await readProjectFile(file);
    assert.match(source, /<ElRow :gutter="24">/, `${file} 未使用统一的 ElRow 间距`);
    assert.match(source, /<ElCol :span="12">/, `${file} 未使用统一的两列表单项`);
  }
});

test('考试创建共享表单按详情页标准双列展示常规字段', async () => {
  const source = await readProjectFile('frontend/pc-web/src/views/admin/business-center/exams/exam/components/ExamDefinitionForm.vue');

  assert.match(source, /<ElForm[^>]*admin-detail-form/);
  assert.match(source, /<ElRow :gutter="24">/);
  assert.match(source, /<ElCol :span="12">\s*<ElFormItem label="考试编码"/);
  assert.match(source, /<ElCol :span="12">\s*<ElFormItem label="考试名称"/);
  assert.match(source, /admin-detail-form__wide/);
});

test('字典管理在同一工作区联动目录与当前类型数据', async () => {
  const source = await readProjectFile('frontend/pc-web/src/views/admin/system/dict/index.vue');

  assert.match(source, /getDictCatalog/);
  assert.match(source, /dictionary-management__directory/);
  assert.match(source, /selectedTypeCode/);
  assert.match(source, /openCreateData/);
  assert.doesNotMatch(source, /<ElTabs/);
});

test('后台所有分页器都提供统一的页大小选择和分页布局', async () => {
  const { readdir } = await import('node:fs/promises');
  const adminRoot = path.join(projectRoot, 'frontend/pc-web/src/views/admin');
  const files = [];

  async function collectVueFiles(directory) {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const entryPath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        await collectVueFiles(entryPath);
      } else if (entry.name.endsWith('.vue')) {
        files.push(entryPath);
      }
    }
  }

  await collectVueFiles(adminRoot);

  for (const file of files) {
    const source = await readFile(file, 'utf8');
    const paginationBlocks = source.match(/<ElPagination[\s\S]*?\/>/g) ?? [];
    for (const block of paginationBlocks) {
      assert.match(block, /:page-sizes="LIST_PAGE_SIZES"/, `${file} 分页器缺少统一页大小`);
      assert.match(block, /layout="total, sizes, prev, pager, next"/, `${file} 分页器 layout 不统一`);
    }
  }
});
