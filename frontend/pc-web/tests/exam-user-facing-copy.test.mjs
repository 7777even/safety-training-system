import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';

const sourceRoot = new URL('../src/', import.meta.url);

async function readSource(path) {
  return readFile(new URL(path, sourceRoot), 'utf8');
}

test('教师批阅可滚动查看全部内容，考试页面提示使用业务语言', async () => {
  const [grading, results, attempts, selection, paperPreview, paperPublish, paperWorkspace, paperVersion, questionIndex, questionDetail] = await Promise.all([
    readSource('views/admin/business-center/exams/exam/grading/index.vue'),
    readSource('views/admin/business-center/exams/exam/results/index.vue'),
    readSource('views/admin/business-center/exams/exam/components/ExamAttemptManagementPanel.vue'),
    readSource('components/organization/PersonSelectionDialog.vue'),
    readSource('views/admin/business-center/exams/paper/components/PaperPreviewContent.vue'),
    readSource('views/admin/business-center/exams/paper/components/PaperPublishPanel.vue'),
    readSource('views/admin/business-center/exams/paper/workspace/index.vue'),
    readSource('views/admin/business-center/exams/paper/version/index.vue'),
    readSource('views/admin/business-center/exams/question/index.vue'),
    readSource('views/admin/business-center/exams/question/detail/index.vue')
  ]);

  assert.match(grading, /class="[^"]*anticheat-card/);
  assert.match(grading, /max-height="320"/);
  assert.match(grading, /height: 100%;[\s\S]*overflow: auto/);
  assert.doesNotMatch(grading, /仅展示作答快照中的主观题；评分保存后以服务端返回状态为准/);
  assert.doesNotMatch(results, /由服务端校验资格|以服务端刷新结果为准/);
  assert.doesNotMatch(attempts, /具体授权仍由服务端校验/);
  assert.doesNotMatch(selection, /服务端会选取|由服务端从所选部门中选取|后端校验/);
  assert.doesNotMatch(paperPreview, /由服务端在发布时固化/);
  assert.doesNotMatch(paperPublish, /题目版本快照/);
  assert.doesNotMatch(paperWorkspace, /发布后将固化当前题目版本快照/);
  assert.doesNotMatch(paperVersion, /版本快照只读/);
  assert.doesNotMatch(questionIndex, /符合 OpenAPI|由服务端保存/);
  assert.doesNotMatch(questionDetail, /只读快照引用/);
});
