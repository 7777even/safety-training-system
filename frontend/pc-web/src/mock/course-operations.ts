// TODO replace real api
import { courseFoundationMockStore } from './course-foundation';
import { assertCourseAssetUsable, syncCourseAssetReferences } from './asset';
import type {
  BinaryStatus,
  ChapterCreatePayload,
  ChapterUpdatePayload,
  CourseChapter,
  CourseEnrollment,
  CourseItem,
  CourseOffering,
  CourseOfferingPayload,
  CourseOfferingUpdatePayload,
  CoursePublishStatus,
  CourseVersion,
  CourseVersionSummary,
  DownloadAsset,
  EnrollmentDetail,
  EnrollmentPageQuery,
  ItemCreatePayload,
  ItemMovePayload,
  ItemUpdatePayload,
  LiveSession,
  LiveSessionPayload,
  OfferingPageQuery
} from '@/api/course-operations';
import type { PersonSelectionRequest } from '@/api/person-selection';

interface PageResult<TItem> {
  list: TItem[];
  total: number;
}

const ids = { chapter: 20, item: 120, live: 30, download: 50, version: 60, offering: 220, enrollment: 320 };
const chapters: CourseChapter[] = [
  { chapterId: 11, courseId: 1, chapterName: '第一章 安全生产基础', sortOrder: 1, status: 1, version: 0 },
  { chapterId: 12, courseId: 1, chapterName: '第二章 应急处置', sortOrder: 2, status: 1, version: 0 }
];
const items: CourseItem[] = [
  {
    courseItemId: 101,
    courseId: 1,
    chapterId: 11,
    itemType: 'ASSET',
    itemTitle: '安全生产基础课件',
    assetId: 21,
    isRequired: 1,
    minimumStudySeconds: 600,
    sortOrder: 1,
    isLockedAfterParticipation: 0,
    status: 1,
    version: 0
  },
  {
    courseItemId: 102,
    courseId: 1,
    chapterId: 12,
    itemType: 'LIVE',
    itemTitle: '应急演练直播',
    assetId: null,
    isRequired: 1,
    minimumStudySeconds: 0,
    sortOrder: 1,
    // 参与后锁定仅对题库练习 / 课程测验生效，直播类恒为 0。
    isLockedAfterParticipation: 0,
    status: 1,
    version: 0
  }
];
const liveSessions = new Map<number, LiveSession>([
  [
    102,
    {
      liveSessionId: 21,
      courseItemId: 102,
      liveName: '应急演练直播',
      teacherUserId: 8,
      liveUrl: 'https://live.example.com/mock-room',
      startTime: '2026-07-25T14:00:00',
      endTime: '2026-07-25T16:00:00',
      audienceId: null,
      liveStatus: 'SCHEDULED',
      status: 1,
      version: 0
    }
  ]
]);
const downloads = new Map<number, DownloadAsset[]>([
  [1, [{ courseDownloadAssetId: 41, courseId: 1, assetId: 21, sortOrder: 0, status: 1, version: 0 }]]
]);
const versions: CourseVersion[] = [];
const offerings: CourseOffering[] = [
  {
    courseOfferingId: 201,
    offeringCode: 'OFF-MOCK-001',
    courseId: 1,
    courseName: '新员工三级安全教育',
    courseVersionId: null,
    offeringName: '新员工安全教育开课（草稿）',
    startTime: '2026-07-24T09:00:00',
    endTime: '2026-08-24T18:00:00',
    courseType: 'REQUIRED',
    enrollmentType: 'ASSIGNED',
    eligibilityType: 'ALL',
    completionRule: 'ALL_REQUIRED_ITEMS',
    publishStatus: 'DRAFT',
    publishedTime: null,
    status: 1,
    updateTime: '2026-07-23T09:00:00',
    audienceId: null,
    isSelfEnrollAllowed: 0,
    isExternalUserAllowed: 0,
    version: 0
  },
  {
    courseOfferingId: 202,
    offeringCode: 'OFF-MOCK-002',
    courseId: 1,
    courseName: '新员工三级安全教育',
    courseVersionId: null,
    offeringName: '季度安全复训',
    courseType: 'ELECTIVE',
    enrollmentType: 'OPEN',
    eligibilityType: 'ALL',
    completionRule: 'ALL_REQUIRED_ITEMS',
    publishStatus: 'DRAFT',
    publishedTime: null,
    status: 1,
    updateTime: '2026-07-01T08:00:00',
    audienceId: null,
    isSelfEnrollAllowed: 1,
    isExternalUserAllowed: 0,
    version: 0
  }
];
const enrollments: CourseEnrollment[] = [];

const clone = <T>(value: T): T => structuredClone(value);
const lock = (actual: number | undefined, submitted: number): void => {
  if ((actual ?? 0) !== submitted) throw new Error('数据已被更新，请刷新后重试。');
};
const chapterById = (id: number): CourseChapter => {
  const value = chapters.find((item) => item.chapterId === id);
  if (!value) throw new Error('章节不存在。');
  return value;
};
const itemById = (id: number): CourseItem => {
  const value = items.find((item) => item.courseItemId === id);
  if (!value) throw new Error('课程内容项不存在。');
  return value;
};
const offeringById = (id: number): CourseOffering => {
  const value = offerings.find((item) => item.courseOfferingId === id);
  if (!value) throw new Error('开课不存在。');
  return value;
};
const enrollmentById = (id: number): CourseEnrollment => {
  const value = enrollments.find((item) => item.courseEnrollmentId === id);
  if (!value) throw new Error('报名不存在。');
  return value;
};
const page = <T>(list: T[], pageNum: number, pageSize: number): PageResult<T> => ({
  list: clone(list.slice((pageNum - 1) * pageSize, pageNum * pageSize)),
  total: list.length
});

/** 按内容项和下载资料重算素材引用，保持素材删除保护与课程 Mock 状态一致。 */
function syncAssetReferences(): void {
  const referencedAssetIds = [
    ...items.flatMap((item) => (item.itemType === 'ASSET' && item.assetId != null ? [item.assetId] : [])),
    ...Array.from(downloads.values()).flatMap((assets) => assets.map((asset) => asset.assetId))
  ];
  syncCourseAssetReferences(referencedAssetIds);
}

syncAssetReferences();

/** 模拟后端创建课程时自动生成默认根章节，避免基础 Mock 与编排 Mock 状态脱节。 */
function ensureDefaultChapter(courseId: number): void {
  const courseExists = courseFoundationMockStore.courses.some((course) => course.courseId === courseId);
  if (!courseExists || chapters.some((chapter) => chapter.courseId === courseId)) return;
  chapters.push({
    chapterId: ++ids.chapter,
    courseId,
    chapterName: '默认章节',
    sortOrder: 1,
    status: 1,
    version: 0
  });
}

export async function mockGetCourseChapters(courseId: number): Promise<CourseChapter[]> {
  ensureDefaultChapter(courseId);
  return clone(chapters.filter((item) => item.courseId === courseId).sort((a, b) => a.sortOrder - b.sortOrder));
}
export async function mockCreateCourseChapter(data: ChapterCreatePayload): Promise<number> {
  const chapterId = ++ids.chapter;
  chapters.push({
    chapterId,
    courseId: data.courseId,
    parentId: data.parentId ?? 0,
    chapterName: data.chapterName,
    sortOrder: data.sortOrder ?? 0,
    status: data.status ?? 1,
    version: 0
  });
  return chapterId;
}
export async function mockUpdateCourseChapter(chapterId: number, data: ChapterUpdatePayload): Promise<null> {
  const value = chapterById(chapterId);
  lock(value.version, data.version);
  Object.assign(value, data, { version: value.version + 1 });
  return null;
}
export async function mockDeleteCourseChapter(chapterId: number): Promise<null> {
  const index = chapters.findIndex((item) => item.chapterId === chapterId);
  if (index < 0) throw new Error('章节不存在。');
  chapters.splice(index, 1);
  for (let i = items.length - 1; i >= 0; i -= 1) if (items[i].chapterId === chapterId) items.splice(i, 1);
  syncAssetReferences();
  return null;
}
export async function mockGetCourseItems(chapterId: number): Promise<CourseItem[]> {
  return clone(
    items
      .filter((item) => item.chapterId === chapterId)
      .map((item) => ({ ...item, liveSession: liveSessions.get(item.courseItemId) ?? null }))
      .sort((a, b) => a.sortOrder - b.sortOrder)
  );
}

export async function mockCreateCourseItem(data: ItemCreatePayload): Promise<number> {
  const chapter = chapterById(data.chapterId);
  if (data.itemType === 'ASSET' ? data.minimumStudySeconds <= 0 : data.minimumStudySeconds !== 0) {
    throw new Error(data.itemType === 'ASSET' ? '要求学习时长必须大于 0。' : '非素材内容项的要求学习时长必须为 0。');
  }
  if (data.itemType === 'ASSET') {
    if (data.assetId == null) throw new Error('素材类型内容项必须指定素材。');
    assertCourseAssetUsable(data.assetId);
  }
  if (data.itemType === 'QUESTION_BANK' && !data.questionBankId) throw new Error('题库练习内容项必须选择已发布题库。');
  if (data.itemType === 'QUIZ' && !data.paperVersionId) throw new Error('课程测验内容项必须选择已发布试卷版本。');
  const courseItemId = ++ids.item;
  items.push({
    courseItemId,
    courseId: chapter.courseId,
    chapterId: data.chapterId,
    itemType: data.itemType,
    itemTitle: data.itemTitle,
    assetId: data.assetId ?? null,
    questionBankId: data.questionBankId ?? null,
    paperVersionId: data.paperVersionId ?? null,
    linkedQuizItemId: data.linkedQuizItemId ?? null,
    isFinalAssessment: data.isFinalAssessment ?? 0,
    passScore: data.passScore ?? null,
    isRequired: data.isRequired,
    minimumStudySeconds: data.minimumStudySeconds,
    sortOrder: data.sortOrder,
    isLockedAfterParticipation: data.isLockedAfterParticipation ?? 0,
    status: data.status,
    version: 0
  });
  if (data.itemType === 'LIVE' && data.liveSession)
    liveSessions.set(courseItemId, { liveSessionId: ++ids.live, courseItemId, ...data.liveSession, liveStatus: 'DRAFT', status: 1, version: 0 });
  syncAssetReferences();
  return courseItemId;
}
export async function mockUpdateCourseItem(itemId: number, data: ItemUpdatePayload): Promise<null> {
  const value = itemById(itemId);
  lock(value.version, data.version);
  if (value.itemType === 'ASSET' ? data.minimumStudySeconds <= 0 : data.minimumStudySeconds !== 0) {
    throw new Error(value.itemType === 'ASSET' ? '要求学习时长必须大于 0。' : '非素材内容项的要求学习时长必须为 0。');
  }
  if (value.itemType === 'ASSET') {
    const assetId = data.assetId ?? value.assetId;
    if (assetId == null) throw new Error('素材类型内容项必须指定素材。');
    assertCourseAssetUsable(assetId);
  }
  Object.assign(value, data, { version: value.version + 1 });
  syncAssetReferences();
  return null;
}
export async function mockDeleteCourseItem(itemId: number): Promise<null> {
  const index = items.findIndex((item) => item.courseItemId === itemId);
  if (index < 0) throw new Error('课程内容项不存在。');
  items.splice(index, 1);
  liveSessions.delete(itemId);
  syncAssetReferences();
  return null;
}
export async function mockMoveCourseItem(itemId: number, data: ItemMovePayload): Promise<null> {
  chapterById(data.chapterId);
  const value = itemById(itemId);
  lock(value.version, data.version);
  Object.assign(value, { chapterId: data.chapterId, sortOrder: data.sortOrder, version: value.version + 1 });
  return null;
}
export async function mockGetLiveSession(itemId: number): Promise<LiveSession> {
  const value = liveSessions.get(itemId);
  if (!value) throw new Error('直播场次不存在。');
  return clone(value);
}
export async function mockUpdateLiveSession(itemId: number, data: LiveSessionPayload): Promise<null> {
  const value = liveSessions.get(itemId);
  if (!value) throw new Error('直播场次不存在。');
  lock(value.version, data.version);
  Object.assign(value, data, { version: value.version + 1 });
  return null;
}
export async function mockGetDownloadAssets(courseId: number): Promise<DownloadAsset[]> {
  return clone(downloads.get(courseId) ?? []);
}
export async function mockReplaceDownloadAssets(
  courseId: number,
  assets: Array<{ assetId: number; sortOrder: number; status: BinaryStatus }>
): Promise<null> {
  assets.forEach((asset) => assertCourseAssetUsable(asset.assetId));
  downloads.set(
    courseId,
    assets.map((asset) => ({ courseDownloadAssetId: ++ids.download, courseId, ...asset, version: 0 }))
  );
  syncAssetReferences();
  return null;
}
export async function mockPublishCourse(courseId: number, data: { version: number; versionNote?: string }): Promise<number> {
  const course = courseFoundationMockStore.courses.find((item) => item.courseId === courseId);
  if (!course) throw new Error('课程不存在。');
  lock(course.version, data.version);
  const snapshot = JSON.stringify({
    courseId,
    chapters: chapters.filter((item) => item.courseId === courseId),
    items: items.filter((item) => item.courseId === courseId),
    downloadAssets: (downloads.get(courseId) ?? []).map((asset) => ({
      assetId: asset.assetId,
      assetName: `课程附件-${asset.assetId}`,
      sortOrder: asset.sortOrder,
      status: asset.status
    }))
  });
  const existingVersion = versions.find((version) => version.courseId === courseId && version.contentSnapshotJson === snapshot);
  if (existingVersion) {
    if (course.publishStatus !== 'PUBLISHED' || course.currentVersionId !== existingVersion.courseVersionId) {
      const republishedTime = new Date().toISOString();
      Object.assign(course, {
        publishStatus: 'PUBLISHED',
        currentVersionId: existingVersion.courseVersionId,
        publishedTime: republishedTime,
        updateTime: republishedTime,
        version: course.version + 1
      });
    }
    return existingVersion.courseVersionId;
  }
  const courseVersionId = ++ids.version;
  const publishedTime = new Date().toISOString();
  versions.unshift({
    courseVersionId,
    courseId,
    versionNo: versions.filter((item) => item.courseId === courseId).length + 1,
    snapshotHash: `mock-${courseId}-${courseVersionId}`,
    versionNote: data.versionNote ?? null,
    publishedBy: 1,
    publishedTime,
    contentSnapshotJson: snapshot
  });
  Object.assign(course, {
    publishStatus: 'PUBLISHED',
    currentVersionId: courseVersionId,
    publishedTime,
    updateTime: publishedTime,
    version: course.version + 1
  });
  return courseVersionId;
}
export async function mockOfflineCourse(courseId: number, data: { version: number }): Promise<null> {
  const course = courseFoundationMockStore.courses.find((item) => item.courseId === courseId);
  if (!course) throw new Error('课程不存在。');
  lock(course.version, data.version);
  Object.assign(course, { publishStatus: 'OFFLINE', updateTime: new Date().toISOString(), version: course.version + 1 });
  return null;
}
export async function mockGetCourseVersions(courseId: number): Promise<CourseVersionSummary[]> {
  return clone(
    versions
      .filter((item) => item.courseId === courseId)
      .map((item) => ({
        courseVersionId: item.courseVersionId,
        courseId: item.courseId,
        versionNo: item.versionNo,
        snapshotHash: item.snapshotHash,
        versionNote: item.versionNote,
        publishedBy: item.publishedBy,
        publishedTime: item.publishedTime
      }))
  );
}
export async function mockGetCourseVersionDetail(courseId: number, versionId: number): Promise<CourseVersion> {
  const value = versions.find((item) => item.courseId === courseId && item.courseVersionId === versionId);
  if (!value) throw new Error('课程版本不存在。');
  return clone(value);
}
export async function mockGetCourseOfferingPage(params: OfferingPageQuery): Promise<PageResult<CourseOffering>> {
  const name = params['query.offeringName']?.trim();
  const filtered = offerings.filter(
    (item) =>
      (!name || item.offeringName.includes(name)) &&
      (!params['query.courseId'] || item.courseId === params['query.courseId']) &&
      (!params['query.publishStatus'] || item.publishStatus === params['query.publishStatus']) &&
      (!params['query.courseType'] || item.courseType === params['query.courseType']) &&
      (!params['query.enrollmentType'] || item.enrollmentType === params['query.enrollmentType']) &&
      (params['query.status'] === undefined || item.status === params['query.status'])
  );
  return page(filtered, params.pageNum, params.pageSize);
}
export async function mockCreateCourseOffering(data: CourseOfferingPayload): Promise<number> {
  const courseOfferingId = ++ids.offering;
  offerings.push({
    courseOfferingId,
    ...data,
    courseName: courseFoundationMockStore.courses.find((item) => item.courseId === data.courseId)?.courseName ?? `课程 ${data.courseId}`,
    courseVersionId: null,
    completionRule: data.completionRule ?? 'ALL_REQUIRED_ITEMS',
    publishStatus: 'DRAFT',
    publishedTime: null,
    status: data.status ?? 1,
    updateTime: new Date().toISOString(),
    isSelfEnrollAllowed: data.enrollmentType === 'ASSIGNED' ? 0 : (data.isSelfEnrollAllowed ?? 0),
    isExternalUserAllowed: data.isExternalUserAllowed ?? 0,
    version: 0
  });
  return courseOfferingId;
}
export async function mockGetCourseOfferingDetail(offeringId: number): Promise<CourseOffering> {
  return clone(offeringById(offeringId));
}
export async function mockUpdateCourseOffering(offeringId: number, data: CourseOfferingUpdatePayload): Promise<null> {
  const value = offeringById(offeringId);
  lock(value.version, data.version);
  Object.assign(value, data, {
    isSelfEnrollAllowed: data.enrollmentType === 'ASSIGNED' ? 0 : (data.isSelfEnrollAllowed ?? 0),
    updateTime: new Date().toISOString(),
    version: (value.version ?? 0) + 1
  });
  return null;
}
export async function mockDeleteCourseOffering(offeringId: number): Promise<null> {
  const index = offerings.findIndex((item) => item.courseOfferingId === offeringId);
  if (index < 0) throw new Error('开课不存在。');
  if (offerings[index].publishStatus !== 'DRAFT') throw new Error('仅草稿开课可删除。');
  offerings.splice(index, 1);
  return null;
}
async function changePublishedStatus(offeringId: number, version: number, status: CoursePublishStatus): Promise<null> {
  const value = offeringById(offeringId);
  lock(value.version, version);
  if (value.publishStatus !== 'PUBLISHED') throw new Error('开课状态流转不合法。');
  value.publishStatus = status;
  value.version = (value.version ?? 0) + 1;
  value.updateTime = new Date().toISOString();
  return null;
}
export async function mockPublishCourseOffering(id: number, version: number): Promise<null> {
  const offering = offeringById(id);
  lock(offering.version, version);
  if (offering.publishStatus !== 'DRAFT') throw new Error('开课状态流转不合法。');
  const course = courseFoundationMockStore.courses.find((item) => item.courseId === offering.courseId);
  if (course?.publishStatus !== 'PUBLISHED' || course.currentVersionId == null) {
    throw new Error('关联课程尚未发布，不能发布开课。');
  }
  offering.publishStatus = 'PUBLISHED';
  offering.courseVersionId = course.currentVersionId;
  offering.version = (offering.version ?? 0) + 1;
  offering.updateTime = new Date().toISOString();
  offering.publishedTime = offering.updateTime;
  return null;
}
export const mockCancelCourseOffering = (id: number, version: number) => changePublishedStatus(id, version, 'CANCELLED');
export const mockEndCourseOffering = (id: number, version: number) => changePublishedStatus(id, version, 'ENDED');
export async function mockGetCourseEnrollmentPage(offeringId: number, params: EnrollmentPageQuery): Promise<PageResult<CourseEnrollment>> {
  const filtered = enrollments.filter(
    (item) =>
      item.courseOfferingId === offeringId &&
      (!params['query.userId'] || item.userId === params['query.userId']) &&
      (!params['query.learningStatus'] || item.learningStatus === params['query.learningStatus']) &&
      (!params['query.enrollmentSource'] || item.enrollmentSource === params['query.enrollmentSource'])
  );
  return page(filtered, params.pageNum, params.pageSize);
}
export async function mockAssignCourseEnrollments(offeringId: number, selection: PersonSelectionRequest): Promise<null> {
  const offering = offeringById(offeringId);
  if (offering.publishStatus !== 'PUBLISHED') throw new Error('仅已发布开课可指派报名。');
  const departmentUserMap: Record<number, number[]> = { 10: [1001], 11: [1002], 20: [1003] };
  const userIds = selection.selectionType === 'ALL'
    ? [1001, 1002, 1003]
    : selection.selectionType === 'USER'
      ? selection.subjectIds
      : [...new Set(selection.subjectIds.flatMap((deptId) => departmentUserMap[deptId] ?? []))];
  userIds.forEach((userId) => {
    if (!enrollments.some((item) => item.courseOfferingId === offeringId && item.userId === userId))
      enrollments.push({
        courseEnrollmentId: ++ids.enrollment,
        courseOfferingId: offeringId,
        userId,
        userCode: `user${userId}`,
        userName: `模拟学员${userId}`,
        enrollmentSource: 'ASSIGN',
        enrolledTime: new Date().toISOString(),
        learningStatus: 'NOT_STARTED',
        progressPercent: 0,
        totalStudySeconds: 0,
        creditAwarded: 0,
        completedTime: null,
        lastStudyTime: null
      });
  });
  return null;
}
export async function mockRemoveCourseEnrollment(enrollmentId: number): Promise<null> {
  const index = enrollments.findIndex((item) => item.courseEnrollmentId === enrollmentId);
  if (index < 0) throw new Error('报名不存在。');
  if (enrollments[index].learningStatus === 'COMPLETED') throw new Error('已完成报名不可移除。');
  enrollments.splice(index, 1);
  return null;
}
export async function mockGetAdminEnrollmentDetail(enrollmentId: number): Promise<EnrollmentDetail> {
  const enrollment = enrollmentById(enrollmentId);
  const courseId = offeringById(enrollment.courseOfferingId).courseId;
  return clone({
    courseEnrollmentId: enrollment.courseEnrollmentId,
    courseOfferingId: enrollment.courseOfferingId,
    userId: enrollment.userId,
    learningStatus: enrollment.learningStatus,
    progressPercent: enrollment.progressPercent,
    totalStudySeconds: enrollment.totalStudySeconds,
    creditAwarded: enrollment.creditAwarded,
    itemProgresses: items
      .filter((item) => item.courseId === courseId)
      .map((item) => ({ courseItemId: item.courseItemId, progressStatus: 'NOT_STARTED', studySeconds: 0 })),
    recentSessions: [],
    evaluation: null
  });
}
