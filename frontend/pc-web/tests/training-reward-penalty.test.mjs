import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('计划过程页使用既有奖惩接口并在执行中提供录入与只读回看', async () => {
  const [api, panel, workspace] = await Promise.all([
    readFile(new URL('../src/api/training-process.ts', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/components/PlanProcessResultsPanel.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8')
  ]);

  assert.match(api, /createTrainingRewardPenalty/);
  assert.match(api, /updateTrainingRewardPenalty/);
  assert.match(api, /deleteTrainingRewardPenalty/);
  assert.match(panel, /<ElTabPane[^>]*奖惩/);
  assert.match(panel, /props\.planStatus === 'RUNNING'/);
  assert.match(panel, /trainingParticipantId/);
  assert.match(panel, /evidenceFileId/);
  assert.match(workspace, /@reward-changed="handleProcessFactsChanged"/);
});

test('过程与结果仅在第五步挂载，不在第一至第四步右侧显示', async () => {
  const workspace = await readFile(
    new URL('../src/views/admin/business-center/training/plan/workspace/index.vue', import.meta.url), 'utf8'
  );
  assert.match(workspace, /<PlanProcessResultsPanel\s+v-if="plan && activeStep === 4"/);
  assert.doesNotMatch(workspace, /<PlanProcessResultsPanel\s+v-if="plan"\s+v-show="activeStep === 4"/);
});
