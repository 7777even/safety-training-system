import assert from 'node:assert/strict';
import { access, readFile } from 'node:fs/promises';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const apiFile = new URL('../src/api/learner-course.ts', import.meta.url);
const listFile = new URL('../src/pages/course/index.vue', import.meta.url);
const detailFile = new URL('../src/pages/course/detail.vue', import.meta.url);
const learningFile = new URL('../src/pages/course/learning.vue', import.meta.url);
const pagesFile = new URL('../src/pages.json', import.meta.url);
const assetApiFile = new URL('../src/api/asset.ts', import.meta.url);
const assetPreviewFile = new URL('../src/components/asset-preview/AssetPreview.vue', import.meta.url);
const assessmentErrorFile = new URL('../../shared/src/libs/assessment-execution.ts', import.meta.url);

/** 编译移动端 TypeScript Mock，以真实函数调用验证课程考核状态机。 */
async function loadCourseMock() {
  const bundle = await build({
    entryPoints: [fileURLToPath(new URL('../src/mock/learner-course.ts', import.meta.url))],
    bundle: true,
    format: 'esm',
    platform: 'node',
    tsconfig: fileURLToPath(new URL('../tsconfig.json', import.meta.url)),
    write: false
  });
  const source = Buffer.from(bundle.outputFiles[0].text).toString('base64');
  return import(`data:text/javascript;base64,${source}`);
}

test('移动端学员课程 API 使用同一组学习 Contract', async () => {
  const source = await readFile(apiFile, 'utf8');

  for (const path of [
    '/api/course/learning/my-enrollments',
    '/api/course/learning/enrollments/${enrollmentId}/tasks',
    '/api/course/learning/sessions'
  ]) {
    assert.ok(source.includes(path), `缺少 Contract 地址：${path}`);
  }
  assert.match(source, /VITE_USE_API_MOCK/);
});

test('移动端已完成课程可评价且本人能回看，重复提交由课程事实阻止', async () => {
  const [apiSource, listSource, detailSource] = await Promise.all([readFile(apiFile, 'utf8'), readFile(listFile, 'utf8'), readFile(detailFile, 'utf8')]);
  const mock = await loadCourseMock();
  assert.match(apiSource, /\/api\/course\/learning\/enrollments\/\$\{enrollmentId\}\/evaluation/);
  assert.match(listSource, /查看课程与评价/);
  assert.match(detailSource, /submitCourseEvaluation/);
  assert.match(detailSource, /enrollmentDetail\.evaluation/);

  assert.equal((await mock.mockGetLearnerCourseDetail(1002)).data.evaluation, null);
  assert.equal((await mock.mockSubmitCourseEvaluation(1002, { rating: 4.5, content: '实用', isAnonymous: 1 })).code, 0);
  assert.equal((await mock.mockGetLearnerCourseDetail(1002)).data.evaluation.rating, 4.5);
  assert.equal((await mock.mockSubmitCourseEvaluation(1002, { rating: 5, content: '', isAnonymous: 0 })).code, 5085);
  assert.equal((await mock.mockSubmitCourseEvaluation(1000, { rating: 5, content: '', isAnonymous: 0 })).code, 5082);
});

test('移动端同次开课评价按本人报名分页，匿名记录不暴露身份', async () => {
  const [apiSource, detailSource] = await Promise.all([readFile(apiFile, 'utf8'), readFile(detailFile, 'utf8')]);
  assert.match(apiSource, /\/api\/course\/learning\/enrollments\/\$\{enrollmentId\}\/evaluations/);
  assert.match(detailSource, /getPeerCourseEvaluations/);
  const mock = await loadCourseMock();
  const page = await mock.mockGetPeerCourseEvaluations(1002, { pageNum: 1, pageSize: 20 });
  assert.equal(page.code, 0);
  assert.ok(page.data.list.some((item) => item.displayName === '匿名学员'));
  assert.ok(page.data.list.filter((item) => item.isAnonymous === 1).every((item) => /^\d{4}-\d{2}-\d{2}$/.test(item.submittedTime)));
  assert.ok(page.data.list.every((item) => !('userId' in item) && !('courseEnrollmentId' in item)));
  assert.equal((await mock.mockGetPeerCourseEvaluations(1000, { pageNum: 1, pageSize: 20 })).data.total, 0);
  assert.equal((await mock.mockGetPeerCourseEvaluations(1002, { pageNum: 2, pageSize: 1 })).data.list.length, 1);
  assert.equal((await mock.mockGetPeerCourseEvaluations(9999, { pageNum: 1, pageSize: 20 })).code, 5071);
});

test('mobile asset files use authorized binary loading and safe uni video cleanup', async () => {
  const [previewSource, requestSource] = await Promise.all([
    readFile(assetPreviewFile, 'utf8'),
    readFile(new URL('../src/api/request.ts', import.meta.url), 'utf8')
  ]);

  assert.match(requestSource, /responseType/);
  assert.match(previewSource, /getAccessToken|getAuthorized|Authorization/);
  assert.match(previewSource, /typeof .*pause === 'function'/);
  assert.match(previewSource, /URL\.createObjectURL/);
});

test('mobile material preview protects cover, renders PDF correctly, and provides readable zoom actions', async () => {
  const [source, pdfSource, pdfSessionSource] = await Promise.all([
    readFile(assetPreviewFile, 'utf8'),
    readFile(new URL('../src/components/pdf-preview/PdfPreview.vue', import.meta.url), 'utf8'),
    readFile(new URL('../../shared/src/libs/pdf-page-session.ts', import.meta.url), 'utf8')
  ]);

  assert.match(source, /coverUrl/);
  assert.match(source, /resolvedCoverUrl/);
  assert.doesNotMatch(source, /<web-view/);
  assert.doesNotMatch(source, /OpenFileViewer/);
  assert.doesNotMatch(source, /@open-file-viewer/);
  assert.match(source, /image-preview/);
  assert.match(source, /office-fallback/);
  assert.match(source, /PdfPreview/);
  assert.match(pdfSource, /pdfjs-dist/);
  assert.match(pdfSource, /const canvas = ref<globalThis\.HTMLCanvasElement>\(\)/);
  assert.match(pdfSource, /pdfWheelPageDelta/);
  assert.match(pdfSource, /pdfArrowPageDelta/);
  assert.match(pdfSource, /pdfContinuousVisibleRange/);
  assert.match(pdfSource, /pdfContinuousWindowMetrics/);
  assert.match(pdfSource, /layout\?: 'page' \| 'continuous'/);
  assert.match(source, /layout="continuous"/);
  assert.match(source, /is-long-document/);
  assert.match(pdfSource, /pdfPageFitScale/);
  assert.match(pdfSource, /pdfPageAvailableHeight/);
  assert.match(pdfSource, /pdfStageSizeChanged/);
  assert.match(pdfSource, /addEventListener\('wheel'/);
  assert.match(pdfSource, /onViewerKeydown/);
  assert.match(pdfSource, /tabindex="0"/);
  assert.match(pdfSessionSource, /disableRange|disableAutoFetch|disableStream|rangeChunkSize/);
  assert.match(pdfSource, /createPdfjsConcurrentRangeAdapter/);
  assert.match(pdfSource, /NativePdfCanvas/);
  assert.doesNotMatch(pdfSource, /:ref="\([^)]+?\) => bindContinuousCanvas/);
  assert.match(pdfSource, /const pagesToPaint = forcePaint \? nextVisible : nextVisible\.filter/);
  assert.match(pdfSource, /overflow-x: auto/);
  assert.match(pdfSource, /NativePdfCanvas/);
  assert.match(pdfSource, /h\('canvas'/);
  assert.doesNotMatch(pdfSource, /canvas-id=/);
  assert.doesNotMatch(pdfSource, /dontFlip: true/);
  assert.match(pdfSource, /position: sticky/);
  assert.match(pdfSource, /backToTop/);
  assert.match(source, /PDF|pdf/);
  assert.match(source, /放大|全屏|预览/);
  assert.match(source, /audio-preview|audio-progress/);
  assert.match(source, /preview-toolbar|rich-text-preview/);
  assert.match(source, /hls.on|MANIFEST|FRAG/);
  assert.match(source, /resolveVideoElement|querySelector|\$el/);
});

test('移动学习采用课程卡片与独立任务详情页，不复制 PC 表格', async () => {
  const [listSource, detailSource, learningSource, pagesSource] = await Promise.all([
    readFile(listFile, 'utf8'),
    readFile(detailFile, 'utf8'),
    readFile(learningFile, 'utf8'),
    readFile(pagesFile, 'utf8')
  ]);

  assert.match(listSource, /course-card/);
  assert.match(listSource, /uni\.navigateTo/);
  assert.match(detailSource, /task-card/);
  assert.doesNotMatch(detailSource, /AssetPreview/);
  assert.match(learningSource, /AssetPreview/);
  assert.match(learningSource, /activeItem/);
  assert.match(learningSource, /courseItemId/);
  assert.match(learningSource, /setLearningTitle/);
  assert.match(learningSource, /uni\.setNavigationBarTitle/);
  assert.match(learningSource, /viewerInitialPageNo/);
  assert.match(learningSource, /Object\.assign\(activeItem\.value, refreshedItem\)/);
  assert.match(learningSource, /directoryExpanded/);
  assert.match(learningSource, /v-show="directoryExpanded"/);
  assert.match(learningSource, /learning-page \.course-hero/);
  assert.match(learningSource, /preview-card\.is-pdf-preview/);
  assert.match(learningSource, /overflow: visible/);
  assert.ok(pagesSource.includes('pages/course/detail'));
  assert.ok(pagesSource.includes('pages/course/learning'));
  assert.doesNotMatch(listSource, /ElTable/);
});

test('移动端课程列表真实接口使用 GET 查询参数而不是请求体', async () => {
  const source = await readFile(apiFile, 'utf8');

  assert.match(source, /url: '\/api\/course\/learning\/my-enrollments'[\s\S]*?method: 'GET'[\s\S]*?params/);
  assert.doesNotMatch(source, /url: '\/api\/course\/learning\/my-enrollments'[\s\S]*?method: 'GET'[\s\S]*?data: params/);
});

test('移动端课程详情包含课程摘要、章节目录和信息 Tab', async () => {
  const source = await readFile(detailFile, 'utf8');

  assert.match(source, /getLearnerCourseDetail/);
  assert.match(source, /course-introduction/);
  assert.match(source, /course-downloads/);
  assert.match(source, /activeInfoTab/);
  assert.match(source, /chapter-progress/);
  assert.match(source, /course-hero/);
});

test('移动端课程详情的题库练习和课程测验直接进入课程考核流程', async () => {
  const source = await readFile(detailFile, 'utf8');

  assert.match(source, /isAssessmentItem/);
  assert.match(source, /startCourseExamAttempt/);
  assert.match(source, /COURSE_ASSESSMENT_TASK_STATUS\.WAITING_RESULT/);
  assert.match(source, /COURSE_ASSESSMENT_PAGE\.ATTEMPT/);
  assert.match(source, /COURSE_ASSESSMENT_PAGE\.RESULT/);
  assert.doesNotMatch(source, /题库、考试和练习功能暂未开放/);
});

test('移动端课程考核入口将 7063 映射为统一提示', async () => {
  const [apiSource, detailSource, learningSource, errorSource] = await Promise.all([
    readFile(apiFile, 'utf8'),
    readFile(detailFile, 'utf8'),
    readFile(learningFile, 'utf8'),
    readFile(assessmentErrorFile, 'utf8')
  ]);
  assert.match(apiSource, /class LearnerCourseApiError extends Error/);
  assert.match(apiSource, /new LearnerCourseApiError\(response\.code/);
  assert.match(detailSource, /getAssessmentExecutionErrorMessage\(error, '课程考核启动失败'\)/);
  assert.match(learningSource, /getAssessmentExecutionErrorMessage\(error, '课程考核启动失败'\)/);
  assert.match(errorSource, /ASSESSMENT_EXECUTION_SNAPSHOT_ERROR_MESSAGE/);
});

test('移动端课程卡片展示必修与选修类型及横向进度条', async () => {
  const source = await readFile(listFile, 'utf8');

  assert.match(source, /course-type/);
  assert.match(source, /必修/);
  assert.match(source, /选修/);
  assert.match(source, /course-progress__bar/);
});

test('移动端素材预览覆盖受控 API 和常见格式降级策略', async () => {
  const [apiSource, previewSource, detailSource, learningSource] = await Promise.all([
    readFile(assetApiFile, 'utf8'),
    readFile(assetPreviewFile, 'utf8'),
    readFile(detailFile, 'utf8'),
    readFile(learningFile, 'utf8')
  ]);

  assert.match(apiSource, /\/api\/asset\/assets\/\$\{assetId\}/);
  assert.match(apiSource, /\/api\/asset\/assets\/\$\{assetId\}\/play-info/);
  assert.match(apiSource, /\/api\/asset\/files\/\$\{assetId\}\/\$\{fileInfoId\}/);
  for (const format of ['video', 'audio', 'image', 'pdf', 'rich-text', 'office', 'external']) {
    assert.match(previewSource, new RegExp(format));
  }
  assert.match(previewSource, /受控/);
  assert.match(previewSource, /不支持|预览失败/);
  assert.doesNotMatch(previewSource, /<audio\b/);
  assert.match(previewSource, /createInnerAudioContext/);
  assert.match(learningSource, /AssetPreview/);
  assert.match(learningSource, /getAssetDetail/);
  assert.doesNotMatch(detailSource, /getAssetDetail/);
});

test('移动端学习会话支持切换、离开、完成上报和递增 trace', async () => {
  const [learningSource, previewSource] = await Promise.all([readFile(learningFile, 'utf8'), readFile(assetPreviewFile, 'utf8')]);

  assert.match(learningSource, /clientTraceId:.*reportSequence/);
  assert.match(learningSource, /reportCurrentSession\('SCREEN_LEAVE'\)/);
  assert.match(learningSource, /reportCurrentSession\('COMPLETED'\)/);
  assert.match(learningSource, /validStudySeconds/);
  assert.match(learningSource, /lastPositionSeconds/);
  assert.match(learningSource, /refreshTaskProgress/);
  assert.match(learningSource, /onBeforeUnmount/);
  assert.match(previewSource, /timeupdate/);
  assert.match(previewSource, /ended/);
  assert.match(previewSource, /initialPositionSeconds/);
});

test('mobile course attachments use controlled learner download endpoint', async () => {
  const [apiSource, detailSource, learningSource] = await Promise.all([
    readFile(apiFile, 'utf8'),
    readFile(detailFile, 'utf8'),
    readFile(learningFile, 'utf8')
  ]);

  assert.match(apiSource, /downloadLearnerCourseAsset/);
  assert.doesNotMatch(apiSource, /downloadUrl \|\| `\/api\/course\/learning\/enrollments/);
  assert.match(await readFile(new URL('../src/mock/learner-course.ts', import.meta.url), 'utf8'), /sourceDownloadMode: ['"](?:ALL|PARTIAL|NONE)['"]/);
  assert.match(apiSource, /downloadUrl/);
  assert.match(learningSource, /downloadLearnerCourseAsset/);
  assert.match(learningSource, /downloadCourseAttachment/);
  assert.match(learningSource, /downloadable/);
  assert.match(learningSource, /unavailableReason/);
  assert.match(detailSource, /downloadLearnerCourseAsset/);
  assert.match(detailSource, /downloadCourseAttachment/);
});

test('移动端学习会话时间按 LocalDateTime 契约传本地时间且不带 UTC 后缀', async () => {
  const source = await readFile(learningFile, 'utf8');
  assert.match(source, /formatLocalDateTime/);
  assert.match(source, /startedTime: formatLocalDateTime/);
  assert.match(source, /endedTime: formatLocalDateTime/);
  assert.doesNotMatch(source, /startedTime: new Date\(sessionStartedAt\)\.toISOString\(\)/);
  assert.doesNotMatch(source, /endedTime: new Date\(now\)\.toISOString\(\)/);
});

test('移动端播放器使用 HLS、切换清理媒体并按生命周期计时', async () => {
  const [previewSource, learningSource, packageSource] = await Promise.all([
    readFile(assetPreviewFile, 'utf8'),
    readFile(learningFile, 'utf8'),
    readFile(new URL('../package.json', import.meta.url), 'utf8')
  ]);

  assert.match(packageSource, /"hls\.js"/);
  assert.match(previewSource, /Hls/);
  assert.match(previewSource, /xhrSetup/);
  assert.match(previewSource, /clearMedia|destroy/);
  assert.match(learningSource, /30000/);
  assert.match(learningSource, /visibilitychange|onHide/);
  assert.match(learningSource, /isMediaPlaying|mediaPlaying|validStudySeconds/);
});

test('移动端课程考核使用课程域独立页面完成开始、继续和结果回看', async () => {
  const [apiSource, learningSource, pagesSource] = await Promise.all([
    readFile(apiFile, 'utf8'),
    readFile(learningFile, 'utf8'),
    readFile(pagesFile, 'utf8'),
    access(new URL('../src/pages/course/assessment-attempt.vue', import.meta.url)),
    access(new URL('../src/pages/course/assessment-result.vue', import.meta.url))
  ]);

  for (const suffix of ['/attempts/${attemptId}', '/attempts/${attemptId}/answers', '/attempts/${attemptId}/submit']) {
    assert.ok(apiSource.includes(suffix), `缺少课程考核 Contract 地址：${suffix}`);
  }
  assert.match(apiSource, /mockGetCourseAssessmentAttempt/);
  assert.match(apiSource, /mockSaveCourseAssessmentAnswers/);
  assert.match(apiSource, /mockSubmitCourseAssessmentAttempt/);
  assert.match(learningSource, /scorePublishStatus === 'EFFECTIVE'/);
  assert.match(learningSource, /COURSE_ASSESSMENT_TASK_STATUS\.WAITING_RESULT/);
  assert.match(learningSource, /COURSE_ASSESSMENT_PAGE\.ATTEMPT/);
  assert.match(learningSource, /COURSE_ASSESSMENT_PAGE\.RESULT/);
  assert.doesNotMatch(learningSource, /pages\/exam\/attempt\?examId=0/);
  assert.ok(pagesSource.includes('pages/course/assessment-attempt'));
  assert.ok(pagesSource.includes('pages/course/assessment-result'));
});

test('移动端课程作答仅使用课程域暂存和交卷接口，结果按自动生效事实裁剪', async () => {
  const [attemptSource, resultSource] = await Promise.all([
    readFile(new URL('../src/pages/course/assessment-attempt.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/pages/course/assessment-result.vue', import.meta.url), 'utf8')
  ]);

  assert.match(attemptSource, /getCourseAssessmentAttempt/);
  assert.match(attemptSource, /saveCourseAssessmentAnswers/);
  assert.match(attemptSource, /submitCourseAssessmentAttempt/);
  assert.match(attemptSource, /questionType === 'MULTIPLE'/);
  assert.match(attemptSource, /questionType === 'SINGLE'/);
  assert.match(attemptSource, /ensureAnswersSaved/);
  assert.doesNotMatch(attemptSource, /recordExamAnticheatEvent|timeoutSubmitExamAttempt|learner-exam/);
  assert.match(resultSource, /scoreVisible && attempt\.value\.scorePublishStatus === 'EFFECTIVE'/);
  assert.match(resultSource, /等待教师批阅/);
  assert.doesNotMatch(resultSource, /人工发布|成绩发布/);
});

test('移动端课程作答页按课程冻结快照同时开启学习防作弊与学习水印', async () => {
  const attemptSource = await readFile(new URL('../src/pages/course/assessment-attempt.vue', import.meta.url), 'utf8');

  assert.match(attemptSource, /anticheatEnabled\.value = courseDetail\?\.course\?\.isStudyAnticheatEnabled === 1/);
  assert.match(attemptSource, /watermarkEnabled\.value = courseDetail\?\.course\?\.isWatermarkEnabled === 1/);
  assert.match(attemptSource, /buildWatermarkTile\(userStore\.userInfo\?\.userName \?\? userStore\.userInfo\?\.userCode \?\? '学员'\)/);
  assert.match(attemptSource, /--course-watermark-image/);
  assert.match(attemptSource, /<view class="exam-attempt-page" :style="watermarkEnabled \? watermarkStyle : undefined">/);
  assert.match(attemptSource, /background-size: var\(--course-watermark-size, auto\)/);
});

test('移动端课程作答将判断题按单选题展示', async () => {
  const attemptSource = await readFile(new URL('../src/pages/course/assessment-attempt.vue', import.meta.url), 'utf8');

  assert.match(attemptSource, /JUDGE: '判断题'/);
  assert.match(attemptSource, /questionType === 'SINGLE' \|\| currentQuestion\.questionType === 'JUDGE'/);
});

test('课程考核 Mock 按题型回写任务状态和内容项完成状态', async () => {
  const mock = await loadCourseMock();

  const snapshotFailure = await mock.mockStartCourseExamAttempt(1000, 104, {
    clientAttemptToken: 'mock-assessment-snapshot-invalid',
    platform: 'H5'
  });
  assert.equal(snapshotFailure.code, 7063);

  const practice = await mock.mockStartCourseExamAttempt(1000, 104, { clientAttemptToken: 'practice', platform: 'H5' });
  const rejected = await mock.mockSubmitCourseAssessmentAttempt(1000, 104, practice.data.examAttemptId, { submitReason: 'MANUAL' });
  assert.notEqual(rejected.code, 0);
  await mock.mockSaveCourseAssessmentAnswers(1000, 104, practice.data.examAttemptId, {
    answers: practice.data.questions.map((question) => ({ attemptQuestionId: question.attemptQuestionId, answerText: 'A', items: [] }))
  });
  const practiceResult = await mock.mockSubmitCourseAssessmentAttempt(1000, 104, practice.data.examAttemptId, { submitReason: 'MANUAL' });
  assert.equal(practiceResult.data.scorePublishStatus, 'EFFECTIVE');
  assert.equal(
    (await mock.mockGetLearnerCourseTasks(1000)).data.chapters[0].items.find((item) => item.courseItemId === 104).progressStatus,
    'COMPLETED'
  );

  const objectiveQuiz = await mock.mockStartCourseExamAttempt(1000, 103, { clientAttemptToken: 'objective', platform: 'H5' });
  await mock.mockSaveCourseAssessmentAnswers(1000, 103, objectiveQuiz.data.examAttemptId, {
    answers: objectiveQuiz.data.questions.map((question) => ({ attemptQuestionId: question.attemptQuestionId, answerText: 'A', items: [] }))
  });
  assert.equal(
    (await mock.mockSubmitCourseAssessmentAttempt(1000, 103, objectiveQuiz.data.examAttemptId, { submitReason: 'MANUAL' })).data.attemptStatus,
    'RESULT_EFFECTIVE'
  );

  const subjectiveQuiz = await mock.mockStartCourseExamAttempt(1000, 105, { clientAttemptToken: 'subjective', platform: 'H5' });
  await mock.mockSaveCourseAssessmentAnswers(1000, 105, subjectiveQuiz.data.examAttemptId, {
    answers: subjectiveQuiz.data.questions.map((question) => ({ attemptQuestionId: question.attemptQuestionId, answerText: 'A', items: [] }))
  });
  const waitingResult = await mock.mockSubmitCourseAssessmentAttempt(1000, 105, subjectiveQuiz.data.examAttemptId, { submitReason: 'MANUAL' });
  assert.equal(waitingResult.data.attemptStatus, 'PENDING_REVIEW');
  assert.equal(
    (await mock.mockGetLearnerCourseTasks(1000)).data.chapters[0].items.find((item) => item.courseItemId === 105).examTaskStatus,
    'WAITING_RESULT'
  );
});
