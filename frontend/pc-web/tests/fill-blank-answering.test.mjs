import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const pcAttempt = new URL('../src/views/portal/exams/attempt.vue', import.meta.url);
const mobileAttempt = new URL('../../mobile-app/src/pages/exam/attempt.vue', import.meta.url);
const mobileCourseAttempt = new URL('../../mobile-app/src/pages/course/assessment-attempt.vue', import.meta.url);
const learnerTypes = new URL('../../shared/src/types/learner-exam.ts', import.meta.url);

test('填空题契约提供安全空位数量', async () => {
  const types = await readFile(learnerTypes, 'utf8');
  assert.match(types, /blankCount\??: number/);
});

for (const [name, url] of [['PC', pcAttempt], ['移动考试', mobileAttempt], ['移动课程考核', mobileCourseAttempt]]) {
  test(`${name} 填空题按空位保存 BLANK 答案项`, async () => {
    const source = await readFile(url, 'utf8');
    assert.match(source, /blankCount/);
    assert.match(source, /itemType:\s*'BLANK'/);
    assert.match(source, /getFillBlankValue|updateFillBlankAnswer/);
    assert.match(source, /第\s*\{\{.*空/);
  });
}
