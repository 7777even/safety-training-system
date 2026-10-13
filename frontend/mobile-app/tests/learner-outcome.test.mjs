import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const certApiUrl = new URL('../src/api/learner-certificate.ts', import.meta.url);
const certPageUrl = new URL('../src/pages/certificate/index.vue', import.meta.url);
const archivePageUrl = new URL('../src/pages/archive/index.vue', import.meta.url);
const profileUrl = new URL('../src/pages/profile/index.vue', import.meta.url);
const pagesUrl = new URL('../src/pages.json', import.meta.url);
const trainingApiUrl = new URL('../src/api/training-learner.ts', import.meta.url);

test('移动端我的证书：本人端点、来源中文展示、空态与受控预览', async () => {
  const [source, page, profile, pages] = await Promise.all([
    readFile(certApiUrl, 'utf8'),
    readFile(certPageUrl, 'utf8'),
    readFile(profileUrl, 'utf8'),
    readFile(pagesUrl, 'utf8')
  ]);
  // 端点只查询本人证书，不手输人员主键。
  assert.match(source, /\/api\/certificate\/my-certificates/);
  assert.match(source, /export function getMyCertificateFileUrl\(certificateId: number\): string/);
  assert.match(source, /\/api\/certificate\/my-certificates\/\$\{certificateId\}\/file/);
  // 查询只按登录态派生持有人，请求参数不含人员主键。
  assert.match(source, /params: \{ pageNum, pageSize \}/);
  assert.doesNotMatch(source, /params: \{[\s\S]*userId/);
  assert.doesNotMatch(source, /params: \{[\s\S]*holderUserId/);
  // 来源类型中文映射，不回显英文枚举码。
  assert.match(page, /COURSE: '课程', EXAM: '理论考试', TRAINING: '培训'/);
  // 空态与加载失败可重试。
  assert.match(page, /暂未获得证书/);
  assert.match(page, /重新加载/);
  // 图片和预览走受控下载并携带鉴权，不直接暴露 fileInfoId 拼接地址。
  assert.match(page, /loadCertificateImage/);
  assert.match(page, /downloadMyCertificateFile\(certificateId\)/);
  assert.doesNotMatch(source, /fileInfoId/);
  // 下载走受控端点，携带鉴权头。
  assert.match(source, /downloadMyCertificateFile/);
  assert.match(source, /Authorization: `Bearer \$\{token\}`/);
  assert.match(page, /downloadCertificate\(certificate\)/);
  assert.match(page, /下载/);
  // 个人中心入口与路由注册。
  assert.match(profile, /我的证书/);
  assert.match(profile, /\/pages\/certificate\/index/);
  assert.match(pages, /pages\/certificate\/index/);
  assert.match(pages, /"navigationBarTitleText": "我的证书"/);
});

test('移动端我的培训档案：按类型分组、结果口径与详情跳转', async () => {
  const [trainingApi, archive, profile, pages] = await Promise.all([
    readFile(trainingApiUrl, 'utf8'),
    readFile(archivePageUrl, 'utf8'),
    readFile(profileUrl, 'utf8'),
    readFile(pagesUrl, 'utf8')
  ]);
  // 后端契约新增字段已在类型声明中消费。
  assert.match(trainingApi, /trainingTypeName\?: string \| null/);
  // 档案页按培训类型分组，缺失时准确提示名称待补全。
  assert.match(archive, /task\.trainingTypeName \|\| '类型名称待补全'/);
  assert.match(archive, /共 \{\{ group\.count \}\} 期/);
  // 已结束培训展示正式结果；进行中只看进度，不提前标记结果缺失。
  assert.match(archive, /PASSED: '合格', FAILED: '不合格', INCOMPLETE: '结果待补全'/);
  assert.match(archive, /task\.publishStatus === 'ENDED'/);
  // 点击任务进入该期详情，复用 training/detail 的 trainingParticipantId。
  assert.match(archive, /\/pages\/training\/detail\?trainingParticipantId=\$\{task\.trainingParticipantId\}/);
  // 个人中心入口与路由注册。
  assert.match(profile, /我的培训档案/);
  assert.match(profile, /\/pages\/archive\/index/);
  assert.match(pages, /pages\/archive\/index/);
  assert.match(pages, /"navigationBarTitleText": "我的培训档案"/);
});

test('移动端培训列表解释未形成的结果，档案继续保留正式结果术语', async () => {
  const trainingListUrl = new URL('../src/pages/training/index.vue', import.meta.url);
  const [list, archive] = await Promise.all([readFile(trainingListUrl, 'utf8'), readFile(archivePageUrl, 'utf8')]);
  // 列表展示给学员的原因，进行中的计划不显示最终结果；档案仍保留领域术语。
  assert.match(list, /INCOMPLETE: '有必修内容尚无最终结果'/);
  assert.match(list, /v-if="task.publishStatus === 'ENDED'"/);
  assert.match(list, /isIncompleteEnded\(task\)/);
  assert.match(list, /: '结果待同步'/);
  assert.doesNotMatch(list, /结果不完整/);
  assert.doesNotMatch(list, /INCOMPLETE: '未完成'/);
  // 档案页结果与兜底成对，避免「结果待补全」与「待同步」混用。
  assert.match(archive, /: '结果待同步';/);
});

test('移动端个人中心新增成果入口且不破坏既有 tabBar 导航', async () => {
  const [profile, pages] = await Promise.all([readFile(profileUrl, 'utf8'), readFile(pagesUrl, 'utf8')]);
  // 成果入口用 navigateTo 压栈，任务入口仍用 switchTab。
  assert.match(profile, /function openOutcome\(path: string\): void/);
  assert.match(profile, /uni\.navigateTo\(\{ url: path \}\)/);
  assert.match(profile, /uni\.switchTab\(\{ url: path \}\)/);
  // 证书与档案页不在 tabBar 内（它们只是普通页面）。
  assert.doesNotMatch(pages, /"pagePath": "pages\/certificate\/index"/);
  assert.doesNotMatch(pages, /"pagePath": "pages\/archive\/index"/);
});
