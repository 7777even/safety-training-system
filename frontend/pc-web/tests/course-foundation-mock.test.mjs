import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

const mockFile = new URL('../src/mock/course-foundation.ts', import.meta.url);
const apiFile = new URL('../src/api/course-foundation.ts', import.meta.url);

/** 将无运行时依赖的 TypeScript Mock 编译为 data URL，通过公开导出验证行为。 */
async function loadMockModule() {
  const source = await readFile(mockFile, 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  return import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Date.now()}-${Math.random()}`);
}

test('课程分类 Mock 支持树查询、增改删和乐观锁冲突', async () => {
  const mock = await loadMockModule();
  mock.resetCourseFoundationMock();

  const created = await mock.mockCreateCourseCategory({
    parentId: 0,
    categoryName: '专项培训',
    status: 1
  });
  assert.equal(created.code, 0);

  const filtered = await mock.mockGetCourseCategoryTree({ categoryName: '专项', status: 1 });
  assert.deepEqual(
    filtered.data.map((item) => item.categoryName),
    ['专项培训']
  );

  const stale = await mock.mockUpdateCourseCategory(created.data, {
    parentId: 0,
    categoryName: '已过期修改',
    status: 1,
    version: 99
  });
  assert.equal(stale.code, 2040);

  const updated = await mock.mockUpdateCourseCategory(created.data, {
    parentId: 0,
    categoryName: '专项安全培训',
    status: 1,
    version: 0
  });
  assert.equal(updated.code, 0);
  assert.equal(mock.courseFoundationMockStore.categories.find((item) => item.courseCategoryId === created.data).version, 1);

  assert.equal((await mock.mockDeleteCourseCategory(created.data)).code, 0);
});

test('课程分类 Mock 拒绝删除存在直接子分类的父分类', async () => {
  const mock = await loadMockModule();
  mock.resetCourseFoundationMock();

  const result = await mock.mockDeleteCourseCategory(1);

  assert.equal(result.code, 5006);
  assert.equal(
    mock.courseFoundationMockStore.categories.some((item) => item.courseCategoryId === 1),
    true
  );
  assert.equal(mock.courseFoundationMockStore.categories.find((item) => item.courseCategoryId === 2)?.parentId, 1);
});

test('课程 Mock 支持分页筛选、CRUD、分类覆盖和目录模式乐观锁', async () => {
  const mock = await loadMockModule();
  mock.resetCourseFoundationMock();

  const created = await mock.mockCreateCourse({
    courseCode: 'CRS-MOCK-NEW',
    courseName: '专项安全操作',
    contentMode: 'MULTI_CHAPTER',
    credit: 2,
    pointMode: 'COURSE',
    pointValue: 10,
    sourceDownloadMode: 'PARTIAL',
    isSpeedPlayAllowed: 0,
    isStudyAnticheatEnabled: 1,
    isStudyDescriptionEnabled: 1,
    isWatermarkEnabled: 1,
    status: 1,
    categoryIds: [2]
  });
  assert.equal(created.code, 0);

  const page = await mock.mockGetCoursePage({
    pageNum: 1,
    pageSize: 1,
    'query.courseName': '专项',
    'query.publishStatus': 'DRAFT',
    'query.status': 1,
    'query.categoryId': 2
  });
  assert.equal(page.data.total, 1);
  assert.deepEqual(
    page.data.list.map((item) => item.courseCode),
    ['CRS-MOCK-NEW']
  );

  const detail = await mock.mockGetCourseDetail(created.data);
  assert.deepEqual(detail.data.categoryIds, [2]);

  const staleUpdate = await mock.mockUpdateCourse(created.data, {
    ...detail.data,
    courseName: '不应保存',
    version: 99
  });
  assert.equal(staleUpdate.code, 2040);

  assert.equal((await mock.mockAssignCourseCategories(created.data, [1])).code, 0);
  assert.deepEqual((await mock.mockGetCourseDetail(created.data)).data.categoryIds, [1]);

  assert.equal((await mock.mockSwitchCourseContentMode(created.data, 'SINGLE', 0)).code, 0);
  const switched = await mock.mockGetCourseDetail(created.data);
  assert.equal(switched.data.contentMode, 'SINGLE');
  assert.equal(switched.data.version, 1);
  assert.equal((await mock.mockSwitchCourseContentMode(created.data, 'MULTI_CHAPTER', 0)).code, 2040);

  assert.equal((await mock.mockDeleteCourse(created.data)).code, 0);
  assert.equal((await mock.mockGetCourseDetail(created.data)).code, 5011);
});

test('课程基础 API 通过统一开关优先使用 Mock 且保留真实请求分支', async () => {
  const source = await readFile(apiFile, 'utf8');

  assert.match(source, /VITE_USE_API_MOCK/);
  assert.match(source, /mockGetCourseCategoryTree/);
  assert.doesNotMatch(source, /mockGetCourseSetting/);
  assert.match(source, /mockGetCoursePage/);
  assert.match(source, /mockCreateCourse/);
  assert.match(source, /mockUpdateCourse/);
  assert.match(source, /mockSwitchCourseContentMode/);
  assert.match(source, /request<ApiResponse<CourseCategory\[\]>>/);
  assert.match(source, /request<ApiResponse<CoursePageResult<CourseListItem>>>/);
});
