import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiFile = new URL('../src/api/course-foundation.ts', import.meta.url);
const categoryPage = new URL('../src/views/admin/business-center/course/classify/index.vue', import.meta.url);
const coursePage = new URL('../src/views/admin/business-center/course/center/index.vue', import.meta.url);
const basicPanel = new URL('../src/views/admin/business-center/course/components/CourseBasicPanel.vue', import.meta.url);
const workbench = new URL('../src/views/admin/business-center/course/workspace/index.vue', import.meta.url);
const courseConstants = new URL('../src/constants/course.ts', import.meta.url);

test('课程基础 API 严格使用分类和课程 Contract', async () => {
  const source = await readFile(apiFile, 'utf8');

  assert.match(source, /url: '\/api\/course\/categories\/tree'/);
  assert.match(source, /url: `\/api\/course\/categories\/\$\{categoryId\}`/);
  assert.doesNotMatch(source, /course\/settings/);
  assert.match(source, /url: '\/api\/course\/courses'/);
  assert.match(source, /url: `\/api\/course\/courses\/\$\{courseId\}\/categories`/);
  assert.match(source, /url: `\/api\/course\/courses\/\$\{courseId\}\/content-mode`/);
  assert.match(source, /'query\.courseName'/);
  assert.match(source, /'query\.publishStatus'/);
  assert.match(source, /'query\.categoryId'/);
});

test('课程分类支持权限入口、移动排序和携带版本保存', async () => {
  const categorySource = await readFile(categoryPage, 'utf8');

  assert.match(categorySource, /course:category:add/);
  assert.match(categorySource, /course:category:edit/);
  assert.match(categorySource, /course:category:delete/);
  assert.doesNotMatch(categorySource, /course:setting/);
  assert.match(categorySource, /parentId: categoryForm\.parentId/);
  assert.match(categorySource, /sortOrder: categoryForm\.sortOrder/);
  assert.match(categorySource, /version: categoryForm\.version/);
  assert.match(categorySource, /ElMessageBox\.confirm/);
});

test('课程中心保留分页筛选和删除能力，基础面板负责创建、编辑与乐观锁版本回传', async () => {
  const [centerSource, basicSource, apiSource, constantsSource] = await Promise.all([
    readFile(coursePage, 'utf8'),
    readFile(basicPanel, 'utf8'),
    readFile(apiFile, 'utf8'),
    readFile(courseConstants, 'utf8')
  ]);

  assert.match(centerSource, /DEFAULT_LIST_PAGE_SIZE/);
  assert.match(centerSource, /'query\.courseName'/);
  assert.match(centerSource, /'query\.publishStatus'/);
  assert.match(centerSource, /'query\.categoryId'/);
  assert.match(centerSource, /course:course:add/);
  assert.match(centerSource, /course:course:delete/);
  assert.match(centerSource, /ElMessageBox\.confirm/);
  assert.match(basicSource, /course:course:edit/);
  assert.match(basicSource, /version: courseForm\.version/);
  assert.match(constantsSource, /数据已被更新，请刷新后重试/);
  // 乐观锁冲突由请求层保留业务码、全局错误处理展示服务端文案，面板只回传 version
  assert.match(apiSource, /OPTIMISTIC_LOCK_ERROR_CODE/);
  assert.match(apiSource, /isOptimisticLockError/);
});

test('课程中心通过查询参数切换全页四阶段工作台，不再内嵌大型业务弹窗', async () => {
  const [centerSource, workbenchSource] = await Promise.all([
    readFile(coursePage, 'utf8'),
    readFile(workbench, 'utf8')
  ]);

  assert.match(centerSource, /<CourseWorkbench/);
  assert.match(centerSource, /router\.replace/);
  assert.match(workbenchSource, /<CourseBasicPanel/);
  assert.match(workbenchSource, /<CourseContentPanel/);
  assert.match(workbenchSource, /<CoursePublishPanel/);
  assert.match(workbenchSource, /<CourseOfferingPanel/);
  assert.doesNotMatch(centerSource, /<ElDialog/);
  assert.doesNotMatch(workbenchSource, /<ElDialog/);
});
