import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';

const learningFile = new URL('../src/pages/course/learning.vue', import.meta.url);
const assessmentAttemptFile = new URL('../src/pages/course/assessment-attempt.vue', import.meta.url);
const sharedDeviceFile = new URL('../../shared/src/utils/device-id.ts', import.meta.url);
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

test('移动端学习会话心跳使用 HEARTBEAT 并携带持久化终端标识', async () => {
  const source = await readFile(learningFile, 'utf8');
  // 定时检查点改为 HEARTBEAT：保持学习会话活跃，USER_EXIT 只用于显式收尾。
  assert.match(source, /reportCurrentSession\('HEARTBEAT'\)/);
  assert.match(source, /setInterval/);
  // 终端标识随每次上报下发，供服务端做同一终端多账户互斥检测。
  assert.match(source, /deviceId: learningDeviceId/);
  assert.match(source, /getOrCreateLearningDeviceId/);
});

test('移动端学习页消费 5095/5096 互斥拒绝并暂停计时与上报', async () => {
  const source = await readFile(learningFile, 'utf8');
  assert.match(source, /error\.code === 5095 \|\| error\.code === 5096/);
  assert.match(source, /LearnerCourseApiError/);
  assert.match(source, /blockLearningByMutex/);
  // 计时门控与上报守卫都以互斥阻塞为前提，避免持续报错与暗中累计。
  assert.match(source, /!mutexBlockedMessage\.value/);
  assert.match(source, /if \(mutexBlockedMessage\.value \|\| verificationStoppedMessage\.value\) return;/);
  assert.match(source, /mutex-notice/);
});

test('移动端学习页随机弹出验证题：作答暂停计时、答对恢复、超时结束会话', async () => {
  const source = await readFile(learningFile, 'utf8');
  // 随机间隔与题目由 shared 工具统一生成，两端同口径。
  assert.match(source, /buildArithmeticQuestion/);
  assert.match(source, /nextVerificationDelayMs/);
  assert.match(source, /VERIFICATION_ANSWER_TIMEOUT_SECONDS/);
  // 验证题弹层期间不计有效学习时长。
  assert.match(source, /!verificationVisible\.value/);
  // 答对后恢复计时并按新的随机间隔继续调度。
  assert.match(source, /scheduleVerification\(\)/);
  // 超时未答对以 TIMEOUT 结束当前会话并停止计时与上报。
  assert.match(source, /reportCurrentSession\('TIMEOUT'\)/);
  // 切换内容项重置验证题调度，页面卸载清理全部定时器。
  assert.match(source, /closeVerification\(\)/);
  assert.match(source, /verify-mask/);
});

test('移动端学习页水印不定时不定位置随机重建，文字优先员工编号', async () => {
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

test('移动端终端标识读取或生成后持久化到 uni storage', async () => {
  const [source, deviceModule] = await Promise.all([readFile(deviceUtilFile, 'utf8'), loadSharedModule(sharedDeviceFile)]);
  assert.match(source, /uni\.getStorageSync/);
  assert.match(source, /uni\.setStorageSync/);
  assert.match(source, /LEARNING_DEVICE_ID_KEY/);
  assert.equal(deviceModule.LEARNING_DEVICE_ID_KEY, 'sts:learning:device-id');
  const first = deviceModule.generateDeviceId();
  const second = deviceModule.generateDeviceId();
  assert.notEqual(first, second);
  assert.match(first, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
});
