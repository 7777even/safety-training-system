import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const apiUrl = new URL('../src/api/training-learner.ts', import.meta.url);
const indexUrl = new URL('../src/pages/training/index.vue', import.meta.url);
const detailUrl = new URL('../src/pages/training/detail.vue', import.meta.url);
const offlineSessionUrl = new URL('../src/pages/training/offline-session.vue', import.meta.url);
const pagesUrl = new URL('../src/pages.json', import.meta.url);
const assessmentErrorUrl = new URL('../../shared/src/libs/assessment-execution.ts', import.meta.url);
const materialPreviewUrl = new URL('../src/libs/material-preview.ts', import.meta.url);
const pdfPreviewUrl = new URL('../src/components/pdf-preview/PdfPreview.vue', import.meta.url);

test('移动端培训 API 与页面路由覆盖本人任务和统一进入内容', async () => {
  const [source, index, detail, pages] = await Promise.all([readFile(apiUrl, 'utf8'), readFile(indexUrl, 'utf8'), readFile(detailUrl, 'utf8'), readFile(pagesUrl, 'utf8')]);
  assert.match(source, /\/api\/training\/learner\/tasks/);
  assert.match(source, /\/contents\/\$\{planContentId\}\/enter/);
  assert.match(index, /我的培训/);
  assert.match(detail, /enterTrainingContent/);
  assert.match(detail, /examAttemptId/);
  assert.match(source, /finalAttemptId\?: number \| null/);
  assert.match(detail, /resultFor\(content\.planContentId\)\?\.finalAttemptId/);
  assert.match(detail, /\/pages\/exam\/result\?examAttemptId=\$\{finalAttemptId\}/);
  assert.match(source, /sourceRecordId\?: number \| null/);
  assert.match(source, /executionId\?: number \| null/);
  assert.match(detail, /\/pages\/course\/learning\?enrollmentId=\$\{result\.sourceRecordId\}/);
  assert.match(detail, /\/pages\/training\/offline-session\?offlineSessionId=\$\{result\.executionId\}/);
  assert.match(detail, /@click="viewResult\(content\)"/);
  assert.match(detail, /PENDING_REVIEW.*查看答卷/);
  assert.match(detail, /contentType === 'QUESTION_BANK' \? '查看反馈' : '查看结果'/);
  assert.match(pages, /pages\/training\/index/);
  assert.match(pages, /pages\/training\/detail/);
  assert.match(pages, /pages\/training\/offline-session/);
  assert.match(pages, /"pagePath": "pages\/training\/index"[\s\S]*"text": "培训"/);
});

test('移动端培训严格使用后端结果投影与本人线下场次接口', async () => {
  const [source, detail, offlineSession] = await Promise.all([
    readFile(apiUrl, 'utf8'),
    readFile(detailUrl, 'utf8'),
    readFile(offlineSessionUrl, 'utf8')
  ]);
  assert.doesNotMatch(source, /attemptType\??:/);
  assert.doesNotMatch(detail, /attemptType:\s*'FIRST'/);
  assert.match(detail, /onShow/);
  assert.match(detail, /PENDING_REVIEW/);
  assert.match(detail, /PENDING_PUBLISH/);
  assert.match(detail, /effectiveDurationSeconds/);
  assert.match(detail, /requiredHours/);
  assert.match(source, /getTrainingLearnerOfflineSession/);
  assert.match(source, /\/api\/training\/learner\/offline-sessions\/\$\{offlineSessionId\}/);
  assert.match(offlineSession, /getTrainingLearnerOfflineSession/);
  assert.match(offlineSession, /effectiveDurationSeconds/);
  assert.match(offlineSession, /resultStatus/);
  assert.match(offlineSession, /function resultStatusText/);
  assert.match(offlineSession, /COMPLETED:\s*'已完成'/);
  assert.match(offlineSession, /offline-hero/);
  assert.match(offlineSession, /offline-metric-grid/);
  assert.match(offlineSession, /statusTone/);
  assert.doesNotMatch(offlineSession, /签到/);
  assert.doesNotMatch(detail, /sessionName=.*location=.*startTime=/);
});

test('移动端培训详情将线下场次状态与个人完成状态分开', async () => {
  const [source, detail] = await Promise.all([readFile(apiUrl, 'utf8'), readFile(detailUrl, 'utf8')]);
  assert.match(source, /sessionStatus\?: string \| null/);
  assert.match(detail, /function displayStatus\(content: TrainingLearnerContent\): string \| undefined/);
  assert.match(detail, /content\.contentType === 'OFFLINE' && result\?\.sessionStatus/);
  assert.match(detail, /\['COMPLETED', 'PASSED', 'FAILED'\]\.includes\(result\?\.resultStatus \?\? ''\)/);
  assert.match(detail, /statusTone\(displayStatus\(content\)\)/);
  assert.match(detail, /statusText\(displayStatus\(content\)\)/);
});

test('移动端培训入口将 7063 映射为统一提示且保留其他后端错误', async () => {
  const [api, detail, errorHelper] = await Promise.all([
    readFile(apiUrl, 'utf8'),
    readFile(detailUrl, 'utf8'),
    readFile(assessmentErrorUrl, 'utf8')
  ]);
  assert.match(api, /class TrainingLearnerApiError extends Error/);
  assert.match(api, /new TrainingLearnerApiError\(response\.code/);
  assert.match(detail, /getAssessmentExecutionErrorMessage/);
  assert.match(detail, /getAssessmentExecutionErrorMessage\(error, '进入失败'\)/);
  assert.match(errorHelper, /code === ASSESSMENT_EXECUTION_SNAPSHOT_ERROR_CODE/);
  assert.match(errorHelper, /return error instanceof Error \? error\.message : fallback/);
});

test('移动端培训详情按本人参训记录读取一般资料且不提供下载入口', async () => {
  const [api, detail] = await Promise.all([readFile(apiUrl, 'utf8'), readFile(detailUrl, 'utf8')]);
  assert.match(api, /\/api\/training\/learner\/participants\/\$\{trainingParticipantId\}\/materials/);
  assert.match(api, /materials\/\$\{trainingAttachmentId\}\/file/);
  assert.match(api, /export const getTrainingLearnerMaterials/);
  assert.match(api, /export const getTrainingLearnerMaterialFile\b/);
  assert.match(api, /export const getTrainingLearnerMaterialFileUrl/);
  assert.match(api, /responseType: 'arraybuffer'/);
  assert.match(api, /hasFile\?: boolean/);
  // 学员侧资料项只暴露展示事实，不下发文件主键，避免把存储地址交给前端。
  const materialInterface = api.slice(
    api.indexOf('export interface TrainingLearnerMaterial'),
    api.indexOf('export class TrainingLearnerApiError')
  );
  assert.ok(materialInterface.length > 0);
  assert.doesNotMatch(materialInterface, /fileInfoId/);
  assert.match(detail, /getTrainingLearnerMaterials/);
  assert.match(detail, /openMaterialPreview\(material\)/);
  assert.match(detail, /v-if="materials\.length"/);
  // 空列表不渲染区块；只有 hasFile 的资料才给出预览入口。
  assert.match(detail, /v-if="material\.hasFile"/);
  assert.match(detail, /:key="material\.trainingAttachmentId"/);
  // 模板层不得出现下载或另存入口，学员侧只做在线预览。
  assert.doesNotMatch(detail.slice(detail.indexOf('<template>')), /下载|另存/);
});

test('移动端资料预览按字节判定格式并复用既有承载组件', async () => {
  const [materialPreview, pdfPreview, detail] = await Promise.all([
    readFile(materialPreviewUrl, 'utf8'),
    readFile(pdfPreviewUrl, 'utf8'),
    readFile(detailUrl, 'utf8')
  ]);
  assert.match(materialPreview, /export function detectMaterialPreviewKind/);
  assert.match(materialPreview, /'%PDF'/);
  assert.match(materialPreview, /export type MaterialPreviewKind = 'image' \| 'pdf' \| 'unsupported'/);
  assert.match(materialPreview, /export function buildMaterialPreviewSource/);
  assert.match(materialPreview, /export function releaseMaterialPreviewSource/);
  assert.match(materialPreview, /URL\.createObjectURL/);
  assert.match(materialPreview, /URL\.revokeObjectURL/);
  assert.match(materialPreview, /#ifdef H5/);
  assert.match(materialPreview, /#ifndef H5/);
  // PDF 交给既有组件按字节渲染，不再发第二次受控请求，也不引新播放器。
  assert.match(pdfPreview, /data\?: Uint8Array \| null/);
  assert.match(pdfPreview, /props\.data \? \{ data: props\.data \} : \{ url: props\.url, httpHeaders: authHeaders\(\) \}/);
  assert.match(detail, /buildMaterialPreviewSource/);
  assert.match(detail, /releaseMaterialPreviewSource/);
  assert.match(detail, /:data="materialPreviewSource\.pdfBytes"/);
  assert.match(detail, /closeMaterialPreview/);
});
