import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const planBasicPanelUrl = new URL('../src/views/admin/business-center/training/plan/components/PlanBasicPanel.vue', import.meta.url);
const offeringPanelUrl = new URL('../src/views/admin/business-center/course/components/CourseOfferingPanel.vue', import.meta.url);
const itemInspectorUrl = new URL('../src/views/admin/business-center/course/components/CourseItemInspector.vue', import.meta.url);
const examDefinitionUrl = new URL('../src/views/admin/business-center/exams/exam/components/ExamDefinitionForm.vue', import.meta.url);

test('PC 管理端时间段统一通过范围控件映射既有开始和结束字段', async () => {
  const [planBasicPanel, offeringPanel, itemInspector, examDefinition] = await Promise.all([
    readFile(planBasicPanelUrl, 'utf8'),
    readFile(offeringPanelUrl, 'utf8'),
    readFile(itemInspectorUrl, 'utf8'),
    readFile(examDefinitionUrl, 'utf8')
  ]);

  for (const [source, range] of [
    [planBasicPanel, 'planTimeRange'],
    [offeringPanel, 'offeringTimeRange'],
    [itemInspector, 'liveTimeRange'],
    [examDefinition, 'openTimeRange'],
    [examDefinition, 'retakeTimeRange']
  ]) {
    assert.match(source, new RegExp(`const ${range} = computed`));
    assert.match(source, new RegExp(`v-model="${range}"[\\s\\S]*?type="datetimerange"`));
    assert.match(source, /value-format="YYYY-MM-DDTHH:mm:ss"/);
  }

  assert.match(planBasicPanel, /props\.model\.startTime = startTime/);
  assert.match(planBasicPanel, /props\.model\.endTime = endTime/);
  assert.match(offeringPanel, /form\.startTime = startTime/);
  assert.match(offeringPanel, /form\.endTime = endTime/);
  assert.match(itemInspector, /liveForm\.startTime = startTime/);
  assert.match(itemInspector, /liveForm\.endTime = endTime/);
  assert.match(examDefinition, /form\.value\.openStartTime = openStartTime \|\| undefined/);
  assert.match(examDefinition, /ensurePolicy\(\)\.retakeStartTime = retakeStartTime \|\| undefined/);
});

test('培训性质仅在草稿且非历史未标明计划时可编辑', async () => {
  const source = await readFile(planBasicPanelUrl, 'utf8');

  assert.match(source, /:disabled="props\.readonly \|\| props\.historicalNature"/);
});
