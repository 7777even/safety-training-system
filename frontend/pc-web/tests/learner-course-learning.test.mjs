import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const apiFile = new URL('../src/api/learner-course.ts', import.meta.url);
const assetApiFile = new URL('../src/api/asset.ts', import.meta.url);
const mockFile = new URL('../src/mock/learner-course.ts', import.meta.url);
const pageFile = new URL('../src/views/portal/class/index.vue', import.meta.url);
const learningPageFile = new URL('../src/views/portal/class/learning.vue', import.meta.url);
const assessmentAttemptPageFile = new URL('../src/views/portal/class/CourseAssessmentAttempt.vue', import.meta.url);
const assessmentResultPageFile = new URL('../src/views/portal/class/CourseAssessmentResult.vue', import.meta.url);
const assessmentErrorFile = new URL('../../shared/src/libs/assessment-execution.ts', import.meta.url);

test('学员课程 API 覆盖本人列表、选课、任务、会话、退选和评价 Contract', async () => {
  const source = await readFile(apiFile, 'utf8');

  for (const path of [
    '/api/course/learning/my-enrollments',
    '/api/course/learning/offerings',
    '/api/course/learning/enrollments/${enrollmentId}/tasks',
    '/api/course/learning/enrollments/${enrollmentId}/items/${itemId}/attempts',
    '/api/course/offerings/${offeringId}/self-enroll',
    '/api/course/enrollments/${enrollmentId}/withdraw',
    '/api/course/learning/enrollments/${enrollmentId}/evaluation',
    '/api/course/learning/sessions'
  ]) assert.ok(source.includes(path), `缺少 Contract 地址：${path}`);

  assert.match(source, /VITE_USE_API_MOCK/);
  assert.match(source, /getLearnerCoursePage/);
  assert.match(source, /getLearnerCourseTasks/);
  assert.match(source, /startCourseExamAttempt/);
  assert.match(source, /reportStudySession/);
});

test('学员课程 Contract Mock 覆盖可报名、状态限制与学习会话', async () => {
  const source = await readFile(mockFile, 'utf8');

  for (const behavior of ['mockGetLearnerCoursePage', 'mockGetLearnerOfferingPage', 'mockSelfEnroll', 'mockWithdrawEnrollment', 'mockReportStudySession']) {
    assert.ok(source.includes(behavior), `Mock 缺少 ${behavior}`);
  }
  assert.match(source, /ELECTIVE/);
  assert.match(source, /COMPLETED/);
  assert.match(source, /clientTraceId/);
});

test('PC 门户课程中心提供我的课程、选课、任务、退选和详情评价入口', async () => {
  const [source, learningSource] = await Promise.all([readFile(pageFile, 'utf8'), readFile(learningPageFile, 'utf8')]);

  for (const text of ['我的课程', '可报名课程', '学习任务', '退选课程']) {
    assert.ok(source.includes(text), `页面缺少 ${text}`);
  }
  assert.match(learningSource, /课程评价/);
  assert.match(source, /reportStudySession/);
  assert.match(source, /getLearnerCourseTasks/);
  assert.match(source, /ElEmpty/);
});

test('PC 课程详情同级展示评价，已完成学员通过抽屉添加', async () => {
  const [apiSource, pageSource, learningSource] = await Promise.all([readFile(apiFile, 'utf8'), readFile(pageFile, 'utf8'), readFile(learningPageFile, 'utf8')]);
  assert.match(apiSource, /\/api\/course\/learning\/enrollments\/\$\{enrollmentId\}\/evaluations/);
  assert.match(learningSource, /getPeerCourseEvaluations/);
  assert.match(learningSource, /同次开课学员评价/);
  assert.match(learningSource, /添加课程评价/);
  assert.match(learningSource, /evaluationDrawerVisible/);
  assert.doesNotMatch(pageSource, /@click="openEvaluation\(course\)"/);
});

test('可报名课程列表按分页状态查询并提供分页器', async () => {
  const source = await readFile(pageFile, 'utf8');

  assert.match(source, /const offeringPageNum = ref\(1\);/);
  assert.match(source, /const offeringPageSize = ref\(DEFAULT_LIST_PAGE_SIZE\);/);
  assert.match(source, /const offeringTotal = ref\(0\);/);
  // 只校验分页状态参与查询；分类筛选等附加查询条件由 batch6 用例单独覆盖。
  assert.match(source, /getLearnerOfferingPage\(\{ pageNum: offeringPageNum\.value, pageSize: offeringPageSize\.value/);
  assert.match(source, /v-model:current-page="offeringPageNum"/);
  assert.match(source, /v-model:page-size="offeringPageSize"/);
  assert.match(source, /:total="offeringTotal"/);
  assert.match(source, /@size-change="changeOfferingPageSize"/);
});

test('我的课程列表按学习状态和分页状态查询并提供分页器', async () => {
  const source = await readFile(pageFile, 'utf8');

  assert.match(source, /const coursePageNum = ref\(1\);/);
  assert.match(source, /const coursePageSize = ref\(DEFAULT_LIST_PAGE_SIZE\);/);
  assert.match(source, /const courseTotal = ref\(0\);/);
  assert.match(source, /pageNum: coursePageNum\.value/);
  assert.match(source, /pageSize: coursePageSize\.value/);
  assert.match(source, /v-model:current-page="coursePageNum"/);
  assert.match(source, /v-model:page-size="coursePageSize"/);
  assert.match(source, /:total="courseTotal"/);
  assert.match(source, /@size-change="changeCoursePageSize"/);
});

test('PC 学员学习页通过现有素材播放契约和学习会话契约完成最小闭环', async () => {
  const [source, assetSource, pageSource] = await Promise.all([
    readFile(apiFile, 'utf8'),
    readFile(assetApiFile, 'utf8'),
    readFile(learningPageFile, 'utf8')
  ]);

  assert.match(assetSource, /getAssetPlayInfo/);
  assert.match(source, /downloadLearnerCourseAsset/);
  assert.doesNotMatch(source, /downloadUrl \|\| `\/api\/course\/learning\/enrollments/);
  assert.match(await readFile(mockFile, 'utf8'), /sourceDownloadMode: ['"](?:ALL|PARTIAL|NONE)['"]/);
  assert.match(source, /url: downloadUrl/);
  assert.match(assetSource, /\/api\/asset\/assets\/\$\{assetId\}\/play-info/);
  for (const text of ['AssetViewer', 'getAssetDetail', 'chapter-list', 'asset-player', 'reportStudySession', 'refreshTaskProgress', 'lastPositionSeconds', 'lastPageNo', 'initial-position-seconds', 'initial-page-no', 'pdf-layout="continuous"', 'is-long-document', 'endPageNo', 'pageupdate', 'download-url', 'height: 620px', 'viewerInitialPositionSeconds', 'viewerInitialPageNo', 'Object.assign(activeItem.value, refreshedItem)']) {
    assert.ok(pageSource.includes(text), `学习页缺少 ${text}`);
  }
  assert.doesNotMatch(pageSource, /activeItem\.value = refreshedItem/);
  assert.match(pageSource, /downloadLearnerCourseAsset/);
  assert.match(pageSource, /preventDefault/);
  assert.match(pageSource, /readonly tabindex="-1"/);
  assert.doesNotMatch(pageSource, /@change="goToPdfPage"/);
});

test('PC 学员学习详情路由使用素材预览权限，并保留 PDF 页码上报契约', async () => {
  const routeFile = new URL('../src/router/admin-dynamic-routes.ts', import.meta.url);
  const [routeSource, viewerSource] = await Promise.all([
    readFile(routeFile, 'utf8'),
    readFile(new URL('../src/components/asset-viewer/AssetViewer.vue', import.meta.url), 'utf8')
  ]);

  assert.match(routeSource, /path: '\/portal\/class\/learning\/:enrollmentId'/);
  assert.match(routeSource, /permission: 'asset:asset:preview'/);
  assert.match(viewerSource, /initialPageNo/);
  assert.match(viewerSource, /pageupdate/);
  assert.match(viewerSource, /pagecount/);
});

test('PC course APIs default to real Contract and enable Mock explicitly', async () => {
  const [operationsSource, learnerSource] = await Promise.all([
    readFile(new URL('../src/api/course-operations.ts', import.meta.url), 'utf8'),
    readFile(apiFile, 'utf8')
  ]);

  assert.match(operationsSource, /VITE_USE_API_MOCK === 'true'/);
  assert.match(learnerSource, /VITE_USE_API_MOCK === 'true'/);
});

test('教师学习详情提供手动刷新进度入口，附件使用契约下载地址', async () => {
  const [panelSource, learningSource] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/course/components/CourseOfferingPanel.vue', import.meta.url), 'utf8'),
    readFile(learningPageFile, 'utf8')
  ]);

  assert.match(panelSource, /refreshLearningDetail/);
  assert.match(panelSource, /刷新进度/);
  assert.match(learningSource, /asset\.downloadUrl/);
  assert.match(learningSource, /asset\.downloadable/);
});

test('PC 学习会话时间按 LocalDateTime 契约传本地时间且不带 UTC 后缀', async () => {
  const source = await readFile(learningPageFile, 'utf8');
  assert.match(source, /formatLocalDateTime/);
  assert.match(source, /startedTime: formatLocalDateTime/);
  assert.match(source, /endedTime: formatLocalDateTime/);
  assert.doesNotMatch(source, /startedTime: new Date\(sessionStartedAt\)\.toISOString\(\)/);
  assert.doesNotMatch(source, /endedTime: new Date\(now\)\.toISOString\(\)/);
});

test('PC 学习计时按真实播放或可见停留并使用 30 秒检查点', async () => {
  const source = await readFile(learningPageFile, 'utf8');
  assert.match(source, /30000/);
  assert.match(source, /visibilitychange|document\.hidden/);
  assert.match(source, /isMediaPlaying|mediaPlaying|validStudySeconds/);
  assert.match(source, /timeupdate/);
});

test('PC 课程考试类内容项通过课程上下文进入作答和成绩结果页', async () => {
  const [pageSource, apiSource, mockSource, resultSource] = await Promise.all([
    readFile(learningPageFile, 'utf8'),
    readFile(apiFile, 'utf8'),
    readFile(mockFile, 'utf8'),
    readFile(new URL('../src/views/portal/class/CourseAssessmentResult.vue', import.meta.url), 'utf8')
  ]);

  assert.match(apiSource, /startCourseExamAttempt/);
  assert.match(apiSource, /getCourseAssessmentAttempt/);
  assert.match(pageSource, /startCourseExamAttempt/);
  assert.match(pageSource, /function isExamItem\(item: LearnerCourseTaskItem\): boolean/);
  assert.doesNotMatch(pageSource, /isDeferredExamItem/);
  assert.doesNotMatch(pageSource, /题库、考试和练习功能暂未开放|考试类内容项暂缓接入|延期边界/);
  assert.doesNotMatch(pageSource, /:disabled="!item\.learnable \|\| isDeferredExamItem\(item\)"/);
  assert.match(pageSource, /reportCurrentSession\('USER_EXIT'\)/);
  assert.match(pageSource, /cleanupMedia\(\)/);
  assert.match(pageSource, /resetAssetState\(\)/);
  assert.match(pageSource, /enterCourseExam/);
  assert.match(pageSource, /name: 'PortalCourseAssessmentResult'/);
  assert.match(pageSource, /enrollmentId: tasks\.value\.enrollmentId/);
  assert.match(pageSource, /itemId: item\.courseItemId/);
  assert.doesNotMatch(pageSource, /\/portal\/exams\/0\/result/);
  assert.match(pageSource, /继续作答/);
  assert.match(pageSource, /查看结果/);
  assert.match(pageSource, /开始测验.*开始练习/s);
  assert.match(resultSource, /getCourseAssessmentAttempt/);
  assert.match(resultSource, /成绩概览/);
  assert.match(resultSource, /标准答案/);
  assert.match(resultSource, /analysisSnapshot/);
  assert.match(mockSource, /mockStartCourseExamAttempt/);
  for (const field of ['activeExamAttemptId', 'publishedExamAttemptId', 'examTaskStatus', 'scorePublishStatus', 'finalScore', 'isPassed']) {
    assert.ok(mockSource.includes(field), `课程考试 Mock 缺少 ${field}`);
  }
  assert.match(mockSource, /QUESTION_BANK/);
  assert.match(mockSource, /COURSE_QUIZ/);
});

test('PC 课程考核入口将 7063 映射为可理解提示且保留其他错误原文', async () => {
  const [apiSource, indexSource, learningSource, errorSource, mockSource] = await Promise.all([
    readFile(apiFile, 'utf8'),
    readFile(pageFile, 'utf8'),
    readFile(learningPageFile, 'utf8'),
    readFile(assessmentErrorFile, 'utf8'),
    readFile(mockFile, 'utf8')
  ]);

  assert.match(apiSource, /class LearnerCourseApiError extends Error/);
  assert.match(apiSource, /new LearnerCourseApiError\(response\.code/);
  assert.match(indexSource, /getAssessmentExecutionErrorMessage/);
  assert.match(indexSource, /catch \(error\)/);
  assert.match(learningSource, /getAssessmentExecutionErrorMessage/);
  assert.match(learningSource, /catch \(error\)/);
  assert.match(errorSource, /ASSESSMENT_EXECUTION_SNAPSHOT_ERROR_CODE = 7063/);
  assert.match(errorSource, /考核配置异常，请联系管理员重新生成。/);
  assert.match(errorSource, /return error instanceof Error \? error\.message : fallback/);
  assert.match(mockSource, /mock-assessment-snapshot-invalid/);
  assert.match(mockSource, /failure\(7063, '考核执行快照已损坏'\)/);
});

test('PC 学习页将不可下载附件原因翻译为中文文案', async () => {
  const source = await readFile(learningPageFile, 'utf8');
  assert.match(source, /function getDownloadUnavailableReasonText/);
  assert.match(source, /SOURCE_DOWNLOAD_DISABLED: '课程未开放源文件下载'/);
  assert.match(source, /getDownloadUnavailableReasonText\(asset\.unavailableReason\)/);
  assert.doesNotMatch(source, /asset\.unavailableReason \|\| '暂不可下载'/);
});

test('PC 课程考核页按课程冻结快照同时开启学习防作弊与学习水印', async () => {
  const attemptSource = await readFile(assessmentAttemptPageFile, 'utf8');

  assert.match(attemptSource, /syncAnticheatListeners\(courseDetail\?\.course\?\.isStudyAnticheatEnabled === 1\)/);
  assert.match(attemptSource, /watermarkEnabled\.value = courseDetail\?\.course\?\.isWatermarkEnabled === 1/);
  assert.match(attemptSource, /buildWatermarkTile\(userStore\.userInfo\?\.userName \?\? userStore\.userInfo\?\.userCode \?\? '学员'\)/);
  assert.match(attemptSource, /--course-watermark-image/);
  assert.match(attemptSource, /<section class="attempt-page" :style="watermarkEnabled \? watermarkStyle : undefined">/);
  assert.match(attemptSource, /background-size: var\(--course-watermark-size, auto\)/);
});

test('PC 课程作答在提交或返回前暂存修改，并在作答和结果页显示内容项名称', async () => {
  const [attemptSource, resultSource, learningSource] = await Promise.all([
    readFile(assessmentAttemptPageFile, 'utf8'),
    readFile(assessmentResultPageFile, 'utf8'),
    readFile(learningPageFile, 'utf8')
  ]);

  assert.match(attemptSource, /ensureAnswersSaved\('提交'\)/);
  assert.match(attemptSource, /ensureAnswersSaved\('返回'\)/);
  assert.match(attemptSource, /课程内容项名称/);
  assert.match(resultSource, /课程内容项名称/);
  assert.match(learningSource, /itemTitle: item\.itemTitle/);
  assert.match(await readFile(pageFile, 'utf8'), /getExamTaskActionText/);
});
