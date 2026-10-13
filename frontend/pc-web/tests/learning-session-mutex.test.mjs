import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';

const learningFile = new URL('../src/views/portal/class/learning.vue', import.meta.url);
const assessmentAttemptFile = new URL('../src/views/portal/class/CourseAssessmentAttempt.vue', import.meta.url);
const sharedDeviceFile = new URL('../../shared/src/utils/device-id.ts', import.meta.url);
const sharedVerificationFile = new URL('../../shared/src/utils/learning-verification.ts', import.meta.url);
const sharedWatermarkFile = new URL('../../shared/src/utils/watermark.ts', import.meta.url);
const deviceUtilFile = new URL('../src/utils/learning-device.ts', import.meta.url);

/** 编译 shared 源码，以真实函数验证跨端口径。 */
async function loadSharedModule(entry) {
  const bundle = await build({
    entryPoints: [fileURLToPath(entry)],
    bundle: true,
    format: 'esm',
    platform: 'node',
    write: false
  });
  const source = Buffer.from(bundle.outputFiles[0].text).toString('base64');
  return import(`data:text/javascript;base64,${source}`);
}

test('PC 学习会话心跳使用 HEARTBEAT 并携带持久化终端标识', async () => {
  const source = await readFile(learningFile, 'utf8');
  // 定时检查点改为 HEARTBEAT：保持学习会话活跃，USER_EXIT 只用于显式收尾。
  assert.match(source, /reportCurrentSession\('HEARTBEAT'\)/);
  assert.match(source, /setInterval/);
  // 终端标识随每次上报下发，供服务端做同一终端多账户互斥检测。
  assert.match(source, /deviceId: learningDeviceId/);
  assert.match(source, /getOrCreateLearningDeviceId/);
});

test('PC 学习页消费 5095/5096 互斥拒绝并暂停计时与上报', async () => {
  const source = await readFile(learningFile, 'utf8');
  assert.match(source, /error\.code === 5095 \|\| error\.code === 5096/);
  assert.match(source, /LearnerCourseApiError/);
  assert.match(source, /blockLearningByMutex/);
  // 计时门控与上报守卫都以互斥阻塞为前提，避免持续报错与暗中累计。
  assert.match(source, /!mutexBlockedMessage\.value/);
  assert.match(source, /if \(mutexBlockedMessage\.value \|\| verificationStoppedMessage\.value\) return;/);
  assert.match(source, /<ElAlert/);
});

test('PC 学习页水印不定时不定位置随机重建，文字优先员工编号', async () => {
  const [source, attemptSource, watermark] = await Promise.all([
    readFile(learningFile, 'utf8'),
    readFile(assessmentAttemptFile, 'utf8'),
    loadSharedModule(sharedWatermarkFile)
  ]);

  // 学习页改随机平铺水印并按 45～90 秒随机间隔重建。
  assert.match(source, /buildRandomWatermarkTile/);
  assert.match(source, /nextWatermarkDelayMs/);
  assert.match(source, /userInfo\?\.userCode \?\? userStore\.userInfo\?\.userName/);
  // 课程作答页保持固定平铺水印，不随本批改变。
  assert.match(attemptSource, /buildWatermarkTile\(/);
  assert.doesNotMatch(attemptSource, /buildRandomWatermarkTile/);

  // shared 随机水印：单元尺寸、角度与锚点均随机，文字转义仍然生效。
  assert.equal(watermark.WATERMARK_MIN_REFRESH_INTERVAL_MS, 45000);
  assert.equal(watermark.WATERMARK_MAX_REFRESH_INTERVAL_MS, 90000);
  assert.equal(watermark.nextWatermarkDelayMs(() => 0), 45000);
  assert.equal(watermark.nextWatermarkDelayMs(() => 1), 90000);
  const tile = watermark.buildRandomWatermarkTile('A001');
  assert.match(tile.image, /^url\("data:image\/svg\+xml/);
  assert.ok(tile.width >= 200 && tile.width <= 280, `单元宽度越界：${tile.width}`);
  assert.ok(tile.height >= 150 && tile.height <= 200, `单元高度越界：${tile.height}`);
  assert.notEqual(tile.image, watermark.buildRandomWatermarkTile('A001').image);
  const escaped = decodeURIComponent(watermark.buildRandomWatermarkTile('a&b<c>').image);
  assert.ok(escaped.includes('&lt;c&gt;') && escaped.includes('&amp;'), '随机水印未正确转义文本');
});

test('PC 学习页随机弹出验证题：作答暂停计时、答对恢复、超时结束会话', async () => {
  const source = await readFile(learningFile, 'utf8');
  // 随机间隔与题目由 shared 工具统一生成，两端同口径。
  assert.match(source, /buildArithmeticQuestion/);
  assert.match(source, /nextVerificationDelayMs/);
  assert.match(source, /VERIFICATION_ANSWER_TIMEOUT_SECONDS/);
  // 验证题弹窗期间不计有效学习时长。
  assert.match(source, /!verificationVisible\.value/);
  // 答对后恢复计时并按新的随机间隔继续调度。
  assert.match(source, /scheduleVerification\(\)/);
  // 超时未答对以 TIMEOUT 结束当前会话并停止计时与上报。
  assert.match(source, /reportCurrentSession\('TIMEOUT'\)/);
  // 切换内容项重置验证题调度，页面卸载清理全部定时器。
  assert.match(source, /closeVerification\(\)/);
  assert.match(source, /<ElDialog/);
});

test('PC 终端标识读取或生成后持久化到 localStorage', async () => {
  const [source, deviceModule] = await Promise.all([readFile(deviceUtilFile, 'utf8'), loadSharedModule(sharedDeviceFile)]);
  assert.match(source, /localStorage/);
  assert.match(source, /LEARNING_DEVICE_ID_KEY/);
  assert.equal(deviceModule.LEARNING_DEVICE_ID_KEY, 'sts:learning:device-id');
  const first = deviceModule.generateDeviceId();
  const second = deviceModule.generateDeviceId();
  assert.notEqual(first, second);
  assert.match(first, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
});

test('防挂机验证题为三种算术且答案正确，调度间隔在 60～180 秒', async () => {
  const verification = await loadSharedModule(sharedVerificationFile);

  assert.equal(verification.VERIFICATION_MIN_INTERVAL_MS, 60000);
  assert.equal(verification.VERIFICATION_MAX_INTERVAL_MS, 180000);
  assert.equal(verification.VERIFICATION_ANSWER_TIMEOUT_SECONDS, 60);
  assert.equal(verification.nextVerificationDelayMs(() => 0), 60000);
  assert.equal(verification.nextVerificationDelayMs(() => 1), 180000);
  assert.equal(verification.nextVerificationDelayMs(() => 0.5), 120000);

  for (const [operation, randomValue] of [['+', 0], ['-', 0.5], ['×', 0.99]]) {
    const question = verification.buildArithmeticQuestion(() => randomValue);
    const matched = question.prompt.match(/^(\d+) ([+\-×]) (\d+) = \?$/);
    assert.ok(matched, `题干格式不正确：${question.prompt}`);
    const left = Number(matched[1]);
    const right = Number(matched[3]);
    assert.equal(matched[2], operation);
    const expected = operation === '+' ? left + right : operation === '-' ? left - right : left * right;
    assert.equal(question.answer, expected, `答案与题干不一致：${question.prompt}`);
  }

  for (let index = 0; index < 50; index += 1) {
    const question = verification.buildArithmeticQuestion();
    const [, left, operation, right] = question.prompt.match(/^(\d+) ([+\-×]) (\d+) = \?$/);
    if (operation === '-') assert.ok(Number(left) > Number(right), `减法出现负结果：${question.prompt}`);
    if (operation === '×') {
      assert.ok(Number(left) <= 9 && Number(right) <= 9, `乘法操作数超范围：${question.prompt}`);
    }
    const delay = verification.nextVerificationDelayMs();
    assert.ok(delay >= 60000 && delay <= 180000, `调度间隔越界：${delay}`);
  }
});
