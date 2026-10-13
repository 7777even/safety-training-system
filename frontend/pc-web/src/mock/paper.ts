// TODO replace real api

import type {
  Paper,
  PaperComposePayload,
  PaperCreatePayload,
  PaperItem,
  PaperPageQuery,
  PaperPreview,
  PaperUpdatePayload,
  PaperVersion,
  PaperVersionSummary,
  PaperVersionQuestion
} from '@/api/paper';

type Response<T> = { code: number; message: string; data: T };
const success = <T>(data: T): Response<T> => ({code: 0, message: 'success', data});
const failure = <T>(code: number, message: string, data: T): Response<T> => ({code, message, data});

const store = {
  papers: [] as PaperItem[],
  drafts: new Map<number, PaperComposePayload>(),
  versions: new Map<number, PaperVersion>(),
  nextPaperId: 1,
  nextPaperVersionId: 1
};

/** 重置浏览器会话 Mock 种子数据。 */
export function resetPaperMock(): void {
  store.papers.splice(0, store.papers.length, {
    paperId: 1,
    paperCode: 'P-SAFE-001',
    paperName: '安全生产基础试卷',
    paperType: 'FIXED',
    archiveStatus: 'ACTIVE',
    status: 1,
    version: 0
  });
  store.drafts.clear();
  store.versions.clear();
  store.nextPaperId = 2;
  store.nextPaperVersionId = 1;
}

export async function mockGetPaperPage(params: PaperPageQuery): Promise<Response<Paper>> {
  const keyword = params['query.paperName']?.toLocaleLowerCase();
  const list = store.papers.filter(
    (item) =>
      (!keyword || item.paperName.toLocaleLowerCase().includes(keyword)) &&
      (!params['query.paperType'] || item.paperType === params['query.paperType']) &&
      (params['query.hasPublishedVersion'] === undefined ||
        hasPublishedVersion(item.paperId) === params['query.hasPublishedVersion'])
  );
  const start = (params.pageNum - 1) * params.pageSize;
  return success({list: list.slice(start, start + params.pageSize).map((item) => ({...item})), total: list.length});
}

export async function mockCreatePaper(data: PaperCreatePayload): Promise<Response<number>> {
  if (store.papers.some((item) => item.paperCode === data.paperCode))
    return failure(6502, '同机构试卷编码已存在。', 0);
  const paperId = store.nextPaperId++;
  store.papers.push({...data, paperId, archiveStatus: 'ACTIVE', version: 0});
  return success(paperId);
}

/** 模拟读取试卷基础信息。 */
export async function mockGetPaper(paperId: number): Promise<Response<PaperItem>> {
  const paper = getPaper(paperId);
  return paper ? success(structuredClone(paper)) : failure(6501, '试卷不存在或已删除。', null as never);
}

/** 模拟更新试卷基础信息及乐观锁。 */
export async function mockUpdatePaper(paperId: number, data: PaperUpdatePayload): Promise<Response<void>> {
  const paper = getPaper(paperId);
  if (!paper) return failure(6501, '试卷不存在或已删除。', undefined as never);
  if (paper.version !== data.version) return failure(1001, '试卷版本已变化，请刷新后重试。', undefined as never);
  Object.assign(paper, data, {version: data.version + 1});
  return success(undefined);
}

/** 模拟删除尚未发布的试卷。 */
export async function mockDeletePaper(paperId: number): Promise<Response<void>> {
  const index = store.papers.findIndex((item) => item.paperId === paperId);
  if (index < 0) return failure(6501, '试卷不存在或已删除。', undefined as never);
  store.papers.splice(index, 1);
  store.drafts.delete(paperId);
  for (const [paperVersionId, version] of store.versions) if (version.paperId === paperId) store.versions.delete(paperVersionId);
  return success(undefined);
}

export async function mockComposePaper(paperId: number, data: PaperComposePayload): Promise<Response<PaperVersion>> {
  const paper = getPaper(paperId);
  if (!paper) return failure(6501, '试卷不存在或已删除。', null as never);
  if (paper.paperType === 'FIXED' && !data.questions?.length) return failure(6505, '组卷结果为空或未指定题目。', null as never);
  if (paper.paperType === 'RANDOM' && !data.drawRules?.length) return failure(6506, '随机试卷未提供抽题规则。', null as never);
  if (data.drawRules?.some((rule) => !rule.drawCount || !rule.sortOrder)) return failure(6507, '随机抽题数量不足。', null as never);
  store.drafts.set(paperId, cloneComposePayload(data));
  const paperVersionId = store.nextPaperVersionId++;
  const version = toVersion(paperId, data, 'DRAFT', paperVersionId, getNextVersionNo(paperId));
  store.versions.set(paperVersionId, version);
  paper.currentVersionNo = version.versionNo;
  return success(structuredClone(version));
}

export async function mockPreviewPaper(paperId: number): Promise<Response<PaperPreview>> {
  const draft = store.drafts.get(paperId);
  if (!draft) return failure(6504, '试卷尚无版本可预览。', null as never);
  const currentVersionNo = getPaper(paperId)?.currentVersionNo;
  const version = [...store.versions.values()].find(
    (item) => item.paperId === paperId && item.versionNo === currentVersionNo
  );
  if (!version) return failure(6504, '试卷尚无版本可预览。', null as never);
  return success({
    paperId,
    paperType: getPaper(paperId)?.paperType ?? 'FIXED',
    totalScore: version.totalScore,
    passScore: version.passScore,
    questionCount: version.questionCount,
    questions: version.questions
  });
}

export async function mockPublishPaper(paperId: number, data: PaperComposePayload): Promise<Response<PaperVersion>> {
  const paper = getPaper(paperId);
  if (!paper) return failure(6501, '试卷不存在或已删除。', null as never);
  if (paper.paperType === 'FIXED' && !data.questions?.length)
    return failure(6505, '组卷结果为空或未指定题目。', null as never);
  if (paper.paperType === 'RANDOM' && !data.drawRules?.length)
    return failure(6506, '随机试卷未提供抽题规则。', null as never);
  const paperVersionId = store.nextPaperVersionId++;
  const version = toVersion(paperId, data, 'PUBLISHED', paperVersionId, getNextVersionNo(paperId));
  store.versions.set(paperVersionId, version);
  paper.currentVersionNo = version.versionNo;
  return success(structuredClone(version));
}

export async function mockGetPaperVersion(paperVersionId: number): Promise<Response<PaperVersion>> {
  const version = store.versions.get(paperVersionId);
  return version ? success(structuredClone(version)) : failure(6503, '试卷版本不存在或尚未发布。', null as never);
}

/** 模拟按试卷读取全部版本摘要，顺序与真实接口一致为版本号倒序。 */
export async function mockGetPaperVersions(paperId: number): Promise<Response<PaperVersionSummary[]>> {
  if (!getPaper(paperId)) return failure(6501, '试卷不存在或已删除。', null as never);
  const versions = [...store.versions.values()]
    .filter((item) => item.paperId === paperId)
    .sort((left, right) => right.versionNo - left.versionNo)
    .map(({paperVersionId, versionNo, publishedStatus, publishedBy, publishedTime}) => ({
      paperVersionId,
      paperId,
      versionNo,
      publishedStatus: publishedStatus as PaperVersionSummary['publishedStatus'],
      publishedBy,
      publishedTime
    }));
  return success(versions);
}

function getPaper(paperId: number): PaperItem | undefined {
  return store.papers.find((item) => item.paperId === paperId);
}

function hasPublishedVersion(paperId: number): boolean {
  return [...store.versions.values()].some(
    (item) => item.paperId === paperId && item.publishedStatus === 'PUBLISHED'
  );
}

/** 将 Vue 响应式 Proxy 转为普通 Contract 请求对象，避免 structuredClone 无法克隆 Proxy。 */
function cloneComposePayload(data: PaperComposePayload): PaperComposePayload {
  return {
    ...data,
    questions: data.questions?.map((question) => ({...question})),
    drawRules: data.drawRules?.map((rule) => ({...rule}))
  };
}

function getNextVersionNo(paperId: number): number {
  return Math.max(0, ...[...store.versions.values()].filter((item) => item.paperId === paperId).map((item) => item.versionNo)) + 1;
}

function toVersion(paperId: number, data: PaperComposePayload, publishedStatus: string, paperVersionId: number, versionNo: number): PaperVersion {
  const questions: PaperVersionQuestion[] = (data.questions ?? []).map((question, index) => ({
    ...question,
    paperQuestionId: index + 1,
    questionType: 'SINGLE',
    sourceBankId: 1
  }));
  const totalScore =
    questions.reduce((sum, question) => sum + question.score, 0) +
    (data.drawRules ?? []).reduce((sum, rule) => sum + rule.drawCount * rule.scorePerQuestion, 0);
  return {
    paperVersionId,
    paperId,
    versionNo,
    durationSeconds: data.durationSeconds,
    totalScore,
    passRatio: data.passRatio,
    passScore: totalScore * data.passRatio,
    questionCount: questions.length + (data.drawRules ?? []).reduce((sum, rule) => sum + rule.drawCount, 0),
    questionOrderMode: data.questionOrderMode,
    multiSelectScoring: data.multiSelectScoring,
    drawSettingMode: data.drawSettingMode,
    publishedStatus,
    publishedBy: publishedStatus === 'PUBLISHED' ? 1 : undefined,
    publishedTime: publishedStatus === 'PUBLISHED' ? '2026-07-28T10:00:00' : undefined,
    versionNote: data.versionNote,
    questions,
    randomSeed: data.randomSeed,
    drawRules: (data.drawRules ?? []).map((rule, index) => ({
      ...rule,
      drawRuleId: index + 1,
      paperVersionId
    }))
  };
}

resetPaperMock();
