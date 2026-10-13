import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const workspaceUrl = new URL('../src/views/admin/business-center/exams/exam/workspace/index.vue', import.meta.url);
const listUrl = new URL('../src/views/admin/business-center/exams/exam/index.vue', import.meta.url);
const assignmentUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamAssignmentPanel.vue', import.meta.url);
const optionsUrl = new URL('../src/constants/exam-options.ts', import.meta.url);

test('仅已发布考试提供考试情况入口，并完整翻译人员配置状态', async () => {
  const [workspace, list, assignment, options] = await Promise.all([readFile(workspaceUrl, 'utf8'), readFile(listUrl, 'utf8'), readFile(assignmentUrl, 'utf8'), readFile(optionsUrl, 'utf8')]);

  assert.match(workspace, /publishStatus === 'PUBLISHED'/);
  assert.match(workspace, /stage === 'attempts'/);
  assert.match(list, /publishStatus === 'PUBLISHED'/);
  assert.match(list, /stage=attempts/);
  const reviewerSection = assignment.slice(assignment.indexOf('<h2>批阅人</h2>'), assignment.indexOf('<h2>已绑定考生</h2>'));
  assert.doesNotMatch(reviewerSection, /prop="deptName" label="部门"/);
  assert.doesNotMatch(reviewerSection, /prop="jobTitle" label="职务"/);
  assert.match(options, /\{label: '待批阅', value: 'PENDING_REVIEW'\}/);
});
