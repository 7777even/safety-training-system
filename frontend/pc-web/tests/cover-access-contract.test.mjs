import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = (path) => readFile(new URL(path, root), 'utf8');

test('前端封面契约只使用 coverFileId 写入和 coverUrl 读取', async () => {
  const [typeApi, typePage, trainingApi, trainingPage, courseTypes, examTypes, coursePage, examPage, pcCourseMock, mobileCourseMock, mobileCoursePage, mobileExamPage, mobileTrainingApi, mobileTrainingPage] = await Promise.all([
    read('src/api/training-type.ts'),
    read('src/views/admin/business-center/training/type/index.vue'),
    read('src/api/training-learner.ts'),
    read('src/views/portal/training/index.vue'),
    read('../shared/src/types/course-learning.ts'),
    read('../shared/src/types/learner-exam.ts'),
    read('src/views/portal/class/index.vue'),
    read('src/views/portal/exams/index.vue'),
    read('src/mock/learner-course.ts'),
    read('../mobile-app/src/mock/learner-course.ts'),
    read('../mobile-app/src/pages/course/index.vue'),
    read('../mobile-app/src/pages/exam/index.vue'),
    read('../mobile-app/src/api/training-learner.ts'),
    read('../mobile-app/src/pages/training/index.vue')
  ]);

  assert.match(typeApi, /coverFileId\?: number/);
  assert.doesNotMatch(typeApi, /imageFileId/);
  assert.match(typeApi, /\/api\/training\/types\/\$\{trainingTypeId\}\/cover/);
  assert.match(typePage, /form\.coverFileId = fileInfo\.fileInfoId/);
  assert.doesNotMatch(typePage, /imageFileId/);
  assert.match(trainingApi, /trainingTypeId\?: number \| null/);
  assert.match(trainingApi, /coverUrl\?: string \| null/);
  assert.match(trainingPage, /items\s*\.filter\(\(item\) => item\.coverUrl\)/);
  assert.match(trainingPage, /getTrainingLearnerCover\(item\.coverUrl!\)/);
  assert.match(courseTypes, /coverUrl: string \| null/);
  assert.doesNotMatch(courseTypes, /coverFileId/);
  assert.match(examTypes, /coverUrl: string \| null/);
  assert.match(coursePage, /\.filter\(\(item\) => item\.coverUrl\)/);
  assert.match(coursePage, /getLearnerCourseCover\(item\.coverUrl!\)/);
  assert.match(examPage, /\.filter\(\(task\) => task\.coverUrl\)/);
  assert.match(examPage, /getLearnerExamCover\(task\.coverUrl!\)/);
  assert.doesNotMatch(pcCourseMock, /coverFileId/);
  assert.doesNotMatch(mobileCourseMock, /coverFileId/);
  assert.match(mobileCoursePage, /loadProtectedImageUrl\(course\.coverUrl\)/);
  assert.match(mobileExamPage, /loadProtectedImageUrl\(task\.coverUrl\)/);
  assert.match(mobileCoursePage, /Object\.values\(courseCoverUrls\.value\)\.forEach\(revokeProtectedImageUrl\)/);
  assert.match(mobileExamPage, /Object\.values\(examCoverUrls\.value\)\.forEach\(revokeProtectedImageUrl\)/);
  assert.match(mobileTrainingApi, /trainingTypeId\?: number \| null/);
  assert.match(mobileTrainingApi, /coverUrl\?: string \| null/);
  assert.match(mobileTrainingPage, /if \(!task\.coverUrl\)/);
  assert.match(mobileTrainingPage, /loadProtectedImageUrl\(task\.coverUrl\)/);
  assert.match(mobileTrainingPage, /Object\.values\(trainingCoverUrls\.value\)\.forEach\(revokeProtectedImageUrl\)/);
});

test('培训引用考试只信任后端独立资源候选，不在前端推导来源', async () => {
  const [standard, template, plan] = await Promise.all([
    read('src/views/admin/business-center/training/standard/workspace/index.vue'),
    read('src/views/admin/business-center/training/template/workspace/index.vue'),
    read('src/views/admin/business-center/training/plan/workspace/index.vue')
  ]);
  for (const source of [standard, template, plan]) {
    assert.match(source, /getTrainingReferenceResourcePage/);
    assert.doesNotMatch(source, /planContentId\s*[!=]==?\s*null/);
  }
});
