import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

test('课程内容项直接关联已发布题库或试卷版本，不再关联独立考试', async () => {
  const source = await readFile(new URL('../src/api/course-operations.ts', import.meta.url), 'utf8');
  assert.match(source, /CourseItemType = 'ASSET' \| 'LIVE' \| 'QUESTION_BANK' \| 'QUIZ'/);
  assert.match(source, /questionBankId\?: number \| null/);
  assert.match(source, /paperVersionId\?: number \| null/);
  assert.doesNotMatch(source, /examId\?: number \| null/);
});

test('PC 与移动端均使用课程理论考核 attempts Contract，PC 课程入口直接创建课程作答快照', async () => {
  const [pcSource, mobileSource, portalSource] = await Promise.all([
    readFile(new URL('../src/api/learner-course.ts', import.meta.url), 'utf8'),
    readFile(new URL('../../mobile-app/src/api/learner-course.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/portal/class/index.vue', import.meta.url), 'utf8')
  ]);
  for (const source of [pcSource, mobileSource]) {
    assert.match(source, /startCourseExamAttempt/);
    assert.match(source, /\/api\/course\/learning\/enrollments\/\$\{enrollmentId\}\/items\/\$\{itemId\}\/attempts/);
    assert.doesNotMatch(source, /exam-attempts/);
  }
  assert.match(portalSource, /examTaskStatus/);
  assert.match(portalSource, /startCourseExamAttempt/);
  assert.match(portalSource, /courseItemId/);
  assert.doesNotMatch(portalSource, /item\.examId/);
});

test('独立考试学员任务仅使用执行来源字段，不携带已移除的课程上下文', async () => {
  const [sharedSource, portalSource, mockSource] = await Promise.all([
    readFile(new URL('../../shared/src/types/learner-exam.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/portal/exams/index.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/mock/learner-exam.ts', import.meta.url), 'utf8')
  ]);
  for (const source of [sharedSource, portalSource, mockSource]) {
    assert.doesNotMatch(source, /contextType|contextId/);
  }
  assert.match(sharedSource, /sourceType: 'STANDALONE_EXAM' \| 'STANDALONE_PRACTICE'/);
  assert.match(sharedSource, /sourceBusinessId: number/);
  assert.match(sharedSource, /assessmentExecutionId: number/);
  assert.match(sharedSource, /resourceName: string/);
});

test('草稿开课的课程测验平铺配置阅卷人，不读取尚未生成的批阅和结果', async () => {
  const [assessmentSource, offeringSource] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/course/components/CourseAssessmentPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/course/components/CourseOfferingPanel.vue', import.meta.url), 'utf8')
  ]);
  assert.match(assessmentSource, /flatMap\(\(items, chapterIndex\)/);
  assert.match(assessmentSource, /card\.chapterName/);
  assert.match(assessmentSource, /reviewer\.userName.*reviewer\.userCode/);
  assert.match(assessmentSource, /if \(!isDraft\.value\) await Promise\.all\(cards\.map\(loadPublishedAssessmentData\)\)/);
  assert.match(assessmentSource, /每一项含主观题的课程测验配置并保存阅卷人/);
  assert.match(assessmentSource, /replaceCourseAssessmentReviewers/);
  assert.match(assessmentSource, /getUnconfiguredQuizNames/);
  assert.match(assessmentSource, /assessment-overview/);
  assert.match(assessmentSource, /assessment-card__status/);
  assert.match(assessmentSource, /assessment-card__body/);
  assert.match(offeringSource, /<ElDialog v-model="editorVisible"/);
  assert.match(offeringSource, /publishOfferingFromEditor/);
  assert.match(offeringSource, /请先为课程测验配置阅卷人/);
  assert.match(offeringSource, /请先在考核管理中保存课程测验阅卷人，再发布开课/);
});

test('课程测验选择人员后立即保存，试卷选择器仅保留各试卷最新已发布版本', async () => {
  const [assessmentSource, inspectorSource] = await Promise.all([
    readFile(new URL('../src/views/admin/business-center/course/components/CourseAssessmentPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/course/components/CourseItemInspector.vue', import.meta.url), 'utf8')
  ]);
  assert.match(assessmentSource, /async function chooseReviewers\(people: PersonPickerItem\[\]\).*replaceCourseAssessmentReviewers/s);
  assert.doesNotMatch(assessmentSource, />保存配置</);
  assert.match(inspectorSource, /filter\(\(version\) => version\.publishedStatus === 'PUBLISHED'\)\.sort\(\(left, right\) => right\.versionNo - left\.versionNo\)\.slice\(0, 1\)/);
});
