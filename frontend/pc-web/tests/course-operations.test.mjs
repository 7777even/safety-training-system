import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { URL } from 'node:url';
import ts from 'typescript';

/** 每个用例加载一份独立的课程 Mock 状态，避免用例相互污染。 */
async function loadApi({
  mockEnabled = true,
  requester = async () => {
    throw new Error('不应调用真实接口');
  }
} = {}) {
  const foundationSource = await readFile(new URL('../src/mock/course-foundation.ts', import.meta.url), 'utf8');
  const foundationOutput = ts.transpileModule(foundationSource, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  const foundationMock = await import(
    `data:text/javascript;base64,${Buffer.from(foundationOutput).toString('base64')}#foundation-${Date.now()}-${Math.random()}`
  );

  const assetSource = await readFile(new URL('../src/mock/asset.ts', import.meta.url), 'utf8');
  const testableAssetSource = assetSource.replace(/import type \{[\s\S]*?\} from '@\/api\/asset';/, '');
  const assetOutput = ts.transpileModule(testableAssetSource, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  const assetMock = await import(`data:text/javascript;base64,${Buffer.from(assetOutput).toString('base64')}#asset-${Date.now()}-${Math.random()}`);

  const mockSource = await readFile(new URL('../src/mock/course-operations.ts', import.meta.url), 'utf8');
  const testableMockSource = mockSource
    .replace(
      "import { assertCourseAssetUsable, syncCourseAssetReferences } from './asset';",
      'const { assertCourseAssetUsable, syncCourseAssetReferences } = globalThis.__assetMockBindings;'
    )
    .replace(
      "import { courseFoundationMockStore } from './course-foundation';",
      'const courseFoundationMockStore = globalThis.__courseFoundationMockBindings.courseFoundationMockStore;'
    )
    .replace(/import type \{[\s\S]*?\} from '@\/api\/course-operations';/, '');
  const mockOutput = ts.transpileModule(testableMockSource, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  globalThis.__courseFoundationMockBindings = foundationMock;
  globalThis.__assetMockBindings = assetMock;
  globalThis.__courseOperationsMockBindings = await import(
    `data:text/javascript;base64,${Buffer.from(mockOutput).toString('base64')}#mock-${Date.now()}-${Math.random()}`
  );

  const source = await readFile(new URL('../src/api/course-operations.ts', import.meta.url), 'utf8');
  const testableSource = source
    .replace("import type { ApiResponse } from '@shared/types';", '')
    .replace(
      "import * as courseOperationsMock from '@/mock/course-operations';",
      'const courseOperationsMock = globalThis.__courseOperationsMockBindings;'
    )
    .replace("import { request } from './request';", 'const request = globalThis.__courseOperationsRequester;');
  const output = ts.transpileModule(testableSource, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
  }).outputText;
  globalThis.__COURSE_OPERATIONS_MOCK_ENABLED__ = mockEnabled;
  globalThis.__courseOperationsRequester = requester;
  const api = await import(`data:text/javascript;base64,${Buffer.from(output).toString('base64')}#${Date.now()}-${Math.random()}`);
  return { ...api, foundationMock, assetMock };
}

test('基础 Mock 创建的新课程首次读取目录时自动生成默认根章节', async () => {
  const api = await loadApi();
  const response = await api.foundationMock.mockCreateCourse({
    courseCode: 'CRS-MOCK-NEW',
    courseName: '新建课程',
    contentMode: 'MULTI_CHAPTER',
    credit: 0,
    pointMode: 'NONE',
    pointValue: 0,
    sourceDownloadMode: 'NONE',
    isSpeedPlayAllowed: 0,
    isStudyAnticheatEnabled: 0,
    isStudyDescriptionEnabled: 0,
    isWatermarkEnabled: 0
  });

  const chapters = await api.getCourseChapters(response.data);

  assert.equal(chapters.length, 1);
  assert.equal(chapters[0].courseId, response.data);
  assert.equal(chapters[0].sortOrder, 1);
});

test('课程编排 Mock 返回页面所需结构并持久化新增内容', async () => {
  const api = await loadApi();
  const chapters = await api.getCourseChapters(1);

  assert.ok(chapters.length > 0);
  assert.equal(typeof chapters[0].version, 'number');

  const itemId = await api.createCourseItem({
    chapterId: chapters[0].chapterId,
    itemType: 'ASSET',
    itemTitle: '新增消防课件',
    assetId: 21,
    isRequired: 1,
    minimumStudySeconds: 300,
    sortOrder: 9,
    status: 1,
    isLockedAfterParticipation: 0
  });
  const items = await api.getCourseItems(chapters[0].chapterId);

  assert.equal(items.find((item) => item.courseItemId === itemId)?.itemTitle, '新增消防课件');
});

test('课程内容 Mock 执行乐观锁并支持下载资料替换', async () => {
  const api = await loadApi();
  const [chapter] = await api.getCourseChapters(1);
  const [item] = await api.getCourseItems(chapter.chapterId);

  await assert.rejects(
    () =>
      api.updateCourseItem(item.courseItemId, {
        itemTitle: item.itemTitle,
        isRequired: item.isRequired,
        minimumStudySeconds: item.minimumStudySeconds,
        sortOrder: item.sortOrder,
        status: item.status,
        version: item.version + 1,
        assetId: item.assetId
      }),
    /数据已被更新，请刷新后重试/
  );

  await api.replaceDownloadAssets(1, [{ assetId: 21, sortOrder: 0, status: 1 }]);
  assert.deepEqual(
    (await api.getDownloadAssets(1)).map((asset) => asset.assetId),
    [21]
  );
});

test('课程内容 Mock 拒绝不存在、停用或处理未成功的素材引用', async () => {
  const api = await loadApi();
  const [chapter] = await api.getCourseChapters(1);
  const [item] = await api.getCourseItems(chapter.chapterId);

  await assert.rejects(() =>
    api.createCourseItem({
      chapterId: chapter.chapterId,
      itemType: 'ASSET',
      itemTitle: '无效素材内容',
      assetId: 9999,
      isRequired: 1,
      minimumStudySeconds: 60,
      sortOrder: 9,
      status: 1
    })
  );
  await assert.rejects(() =>
    api.updateCourseItem(item.courseItemId, {
      itemTitle: item.itemTitle,
      isRequired: item.isRequired,
      minimumStudySeconds: item.minimumStudySeconds,
      sortOrder: item.sortOrder,
      status: item.status,
      version: item.version,
      assetId: 20
    })
  );
  await assert.rejects(() => api.replaceDownloadAssets(1, [{ assetId: 22, sortOrder: 0, status: 1 }]));
  await api.assetMock.mockUpdateAsset(21, { assetName: '停用素材', status: 0, version: 1 });
  await assert.rejects(() => api.replaceDownloadAssets(1, [{ assetId: 21, sortOrder: 0, status: 1 }]));
});

test('素材删除保护跟随课程内容项和下载资料的实际引用变化', async () => {
  const api = await loadApi();
  const [firstChapter] = await api.getCourseChapters(1);
  const [seededItem] = await api.getCourseItems(firstChapter.chapterId);

  await assert.rejects(() => api.assetMock.mockDeleteAsset(21));

  const assetId = await api.assetMock.mockCreateAsset({
    assetCode: 'AST-RICH-REFERENCE',
    assetName: '动态引用素材',
    assetType: 'RICH_TEXT',
    sourceType: 'CREATED',
    richContent: '<p>课程内容</p>',
    status: 1
  });
  const itemId = await api.createCourseItem({
    chapterId: firstChapter.chapterId,
    itemType: 'ASSET',
    itemTitle: '动态引用内容项',
    assetId,
    isRequired: 1,
    minimumStudySeconds: 60,
    sortOrder: 9,
    status: 1
  });

  await assert.rejects(() => api.assetMock.mockDeleteAsset(assetId));
  await api.deleteCourseItem(itemId);
  await api.assetMock.mockDeleteAsset(assetId);
  await assert.rejects(() => api.assetMock.mockGetAssetDetail(assetId));

  await api.replaceDownloadAssets(1, []);
  await api.updateCourseItem(seededItem.courseItemId, {
    itemTitle: seededItem.itemTitle,
    isRequired: seededItem.isRequired,
    minimumStudySeconds: seededItem.minimumStudySeconds,
    sortOrder: seededItem.sortOrder,
    status: seededItem.status,
    version: seededItem.version
  });
  await assert.rejects(() => api.assetMock.mockDeleteAsset(21));
  await api.deleteCourseItem(seededItem.courseItemId);
  await api.assetMock.mockDeleteAsset(21);
  await assert.rejects(() => api.assetMock.mockGetAssetDetail(21));
});

test('课程发布 Mock 生成可查看的不可变版本快照', async () => {
  const api = await loadApi();
  const versionId = await api.publishCourse(1, { version: 0, versionNote: '前端联调版本' });
  const duplicateVersionId = await api.publishCourse(1, { version: 1, versionNote: '重复发布' });
  const versionsBeforeChange = await api.getCourseVersions(1);
  const detail = await api.getCourseVersionDetail(1, versionId);

  assert.equal(duplicateVersionId, versionId);
  assert.equal(versionsBeforeChange.length, 1);
  assert.equal(versionsBeforeChange[0].versionNote, '前端联调版本');
  assert.match(detail.contentSnapshotJson, /courseId/);

  const [chapter] = await api.getCourseChapters(1);
  await api.updateCourseChapter(chapter.chapterId, {
    chapterName: `${chapter.chapterName}（更新）`,
    sortOrder: chapter.sortOrder,
    status: chapter.status,
    version: chapter.version
  });
  const changedVersionId = await api.publishCourse(1, { version: 1, versionNote: '内容更新版本' });

  assert.notEqual(changedVersionId, versionId);
  assert.equal(changedVersionId, versionId + 1);
  assert.equal((await api.getCourseVersions(1)).length, 2);
});

test('课程下线后重复发布相同快照会复用版本并恢复发布状态', async () => {
  const api = await loadApi();
  const versionId = await api.publishCourse(1, { version: 0 });
  await api.offlineCourse(1, { version: 1 });

  const republishedVersionId = await api.publishCourse(1, { version: 2 });
  const course = api.foundationMock.courseFoundationMockStore.courses.find((item) => item.courseId === 1);

  assert.equal(republishedVersionId, versionId);
  assert.equal((await api.getCourseVersions(1)).length, 1);
  assert.equal(course.publishStatus, 'PUBLISHED');
  assert.equal(course.currentVersionId, versionId);
  assert.equal(course.version, 3);
  assert.ok(course.publishedTime);
  assert.equal(course.updateTime, course.publishedTime);
});

test('开课 Mock 支持筛选、状态流转、报名和管理员学习详情', async () => {
  const api = await loadApi();
  const courseVersionId = await api.publishCourse(1, { version: 0 });
  const draftPage = await api.getCourseOfferingPage({
    pageNum: 1,
    pageSize: 20,
    'query.publishStatus': 'DRAFT'
  });
  const draft = draftPage.list[0];

  assert.ok(draft);
  const detail = await api.getCourseOfferingDetail(draft.courseOfferingId);
  await api.publishCourseOffering(draft.courseOfferingId, detail.version);
  const published = await api.getCourseOfferingDetail(draft.courseOfferingId);
  assert.equal(published.publishStatus, 'PUBLISHED');
  assert.equal(published.courseVersionId, courseVersionId);

  await api.assignCourseEnrollments(draft.courseOfferingId, { selectionType: 'USER', subjectIds: [1001, 1002] });
  const enrollmentPage = await api.getCourseEnrollmentPage(draft.courseOfferingId, {
    pageNum: 1,
    pageSize: 20,
    'query.learningStatus': 'NOT_STARTED'
  });
  assert.equal(enrollmentPage.total, 2);
  const learning = await api.getAdminEnrollmentDetail(enrollmentPage.list[0].courseEnrollmentId);
  assert.equal(learning.courseOfferingId, draft.courseOfferingId);
  assert.ok(Array.isArray(learning.itemProgresses));
  assert.ok(Array.isArray(learning.recentSessions));
});

test('开课 Mock 仅允许合法发布、取消和结束状态流转', async () => {
  const api = await loadApi();
  const draftPage = await api.getCourseOfferingPage({ pageNum: 1, pageSize: 20, 'query.publishStatus': 'DRAFT' });
  const firstDraft = draftPage.list[0];

  await assert.rejects(() => api.publishCourseOffering(firstDraft.courseOfferingId, firstDraft.version));
  await assert.rejects(() => api.cancelCourseOffering(firstDraft.courseOfferingId, firstDraft.version));
  await assert.rejects(() => api.endCourseOffering(firstDraft.courseOfferingId, firstDraft.version));

  await api.publishCourse(1, { version: 0 });
  await api.publishCourseOffering(firstDraft.courseOfferingId, firstDraft.version);
  const published = await api.getCourseOfferingDetail(firstDraft.courseOfferingId);
  await api.cancelCourseOffering(firstDraft.courseOfferingId, published.version);
  const cancelled = await api.getCourseOfferingDetail(firstDraft.courseOfferingId);
  await assert.rejects(() => api.endCourseOffering(firstDraft.courseOfferingId, cancelled.version));
});

test('关闭 Mock 后保留原有真实 Contract 请求分支', async () => {
  const calls = [];
  const api = await loadApi({
    mockEnabled: false,
    requester: async (config) => {
      calls.push(config);
      return { code: 0, message: 'success', data: [] };
    }
  });

  await api.getCourseChapters(8);
  assert.deepEqual(calls, [{ url: '/api/course/chapters', method: 'GET', params: { courseId: 8 } }]);
});
