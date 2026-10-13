<script setup lang="ts">
import type { LearnerExamAttempt } from '@shared/types';
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { getExamAnticheatEvents, getReviewAttempt, reviewExamAnswer, type ExamAnticheatEvent } from '@/api/exam-grading';
import { useUserStore } from '@/stores/modules/user';
import { hasPermission } from '@/utils/permission';
import { ANTICHEAT_ACTION_OPTIONS, ANTICHEAT_EVENT_TYPE_OPTIONS, getExamEnumLabel, GRADING_STATUS_OPTIONS } from '@/constants/exam-options';
import { isScorePublished as isScorePublishedStatus } from '../exam-review-state';

/** 本页只处理作答快照中的题目结构，复用契约类型避免重复定义。 */
type AttemptQuestion = LearnerExamAttempt['questions'][number];

/** 作答端上报的来源编码转为教师可直接判断的行为说明。 */
const ANTICHEAT_EVIDENCE_SOURCE_LABELS: Record<string, string> = {
  'window-blur': '窗口失焦',
  'page-hidden': '页面隐藏',
  'page-hide': '页面进入后台',
  'clipboard-copy': '复制操作',
  'clipboard-cut': '剪切操作',
  'clipboard-paste': '粘贴操作',
  'clipboard-contextmenu': '打开右键菜单'
};

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const attemptId = Number(route.params.examAttemptId);
const examId = Number(route.params.examId);
const attempt = ref<LearnerExamAttempt>();
const anticheatEvents = ref<ExamAnticheatEvent[]>([]);
const loading = ref(false);
const savingAll = ref(false);
/** 仅具备正式成绩发布权限的用户显示成绩处置入口。 */
const canPublishScore = computed(() => hasPermission(userStore.userInfo, 'exam:score:publish'));
/** 正式成绩发布后，评分事实由服务端锁定，页面仅提供只读查看。 */
const isScorePublished = computed(() => isScorePublishedStatus(attempt.value?.scorePublishStatus));
/** 只呈现 Contract 快照中标记为人工评分的题目。 */
const manualQuestions = computed(() => attempt.value?.questions.filter((item) => item.gradingMode === 'MANUAL') ?? []);
const scores = ref<Record<number, number>>({});
const comments = ref<Record<number, string>>({});
/** 未评分题目必须在调用任何写入接口前一次性提示，0 分属于有效评分。 */
const pendingQuestionNumbers = computed(() => manualQuestions.value
  .map((question, index) => ({ question, number: index + 1 }))
  .filter(({ question }) => {
    const answerId = question.answer?.examAnswerId;
    const score = answerId ? scores.value[answerId] : undefined;
    return typeof score !== 'number' || !Number.isFinite(score) || score < 0 || score > question.score;
  })
  .map(({ number }) => number));
const scoredQuestionCount = computed(() => manualQuestions.value.length - pendingQuestionNumbers.value.length);
/** 概览标签直接暴露待评分题量，避免教师逐题查找哪一道还没打分。 */
const pendingSummaryText = computed(() => (pendingQuestionNumbers.value.length ? `待评分 ${pendingQuestionNumbers.value.length} 题` : '主观题已全部评分'));
/** 人工评分分值上限，让教师在概览处就看到本次批阅的总分口径。 */
const manualTotalScore = computed(() => manualQuestions.value.reduce((total, question) => total + (question.score || 0), 0));
/** 批阅进度只用于展示，不参与任何提交判定；提交前校验仍以 pendingQuestionNumbers 为准。 */
const gradingPercent = computed(() => (manualQuestions.value.length ? Math.round((scoredQuestionCount.value / manualQuestions.value.length) * 100) : 0));

/** 服务端已保存该题人工分，用于卡片状态与「已批阅」底色。 */
function isQuestionReviewed(question: AttemptQuestion): boolean {
  return question.answer?.gradingStatus === 'MANUAL_GRADED';
}

/** 已批阅完成的主观题用成功色，待批阅用警示色，其余状态保持中性，便于教师扫读。 */
function gradingTone(question: AttemptQuestion): 'success' | 'warning' | 'info' {
  if (isQuestionReviewed(question)) return 'success';
  const status = question.answer?.gradingStatus;
  return status === 'PENDING_MANUAL' || status === 'PENDING_REVIEW' ? 'warning' : 'info';
}

/** 已保存的人工分必须区分「未评分」与「0 分」，避免把有效评分显示成缺省值。 */
function manualScoreText(score: number | null | undefined): string {
  return score === null || score === undefined ? '未评分' : `${score} 分`;
}

/** 已给分用成功色、未评分保持中性，教师扫读卡片头即可看出哪几题还没打分。 */
function manualScoreTone(question: AttemptQuestion): 'success' | 'info' {
  const score = question.answer?.manualScore;
  return score === null || score === undefined ? 'info' : 'success';
}

/** 安全解析事件附带的客户端载荷；历史脏数据或非对象载荷按未提供处理。 */
function parseClientPayload(event: ExamAnticheatEvent): Record<string, unknown> | undefined {
  if (!event.clientPayloadJson) return undefined;
  try {
    const payload: unknown = JSON.parse(event.clientPayloadJson);
    return payload && typeof payload === 'object' && !Array.isArray(payload) ? payload as Record<string, unknown> : undefined;
  } catch {
    return undefined;
  }
}

/** 返回教师列表中显示的行为证据摘要，优先使用作答端明确上报的来源。 */
function getEvidenceSummary(event: ExamAnticheatEvent): string {
  const source = parseClientPayload(event)?.source;
  if (typeof source === 'string') return ANTICHEAT_EVIDENCE_SOURCE_LABELS[source] ?? `客户端事件：${source}`;
  return getExamEnumLabel(event.eventType, ANTICHEAT_EVENT_TYPE_OPTIONS);
}

/** 汇总详情弹窗内容，明确事件载荷与可选附件是两类不同证据。 */
function getEvidenceDetail(event: ExamAnticheatEvent): { source: string; payload: string; attachment: string } {
  const source = parseClientPayload(event)?.source;
  return {
    source: typeof source === 'string' ? (ANTICHEAT_EVIDENCE_SOURCE_LABELS[source] ?? source) : '未提供来源',
    payload: event.clientPayloadJson || '无附加客户端数据',
    attachment: event.evidenceFileId ? `已关联附件 ID：${event.evidenceFileId}` : '未关联附件（系统事件记录）'
  };
}

/** 快捷给分：把分数限制在 0 到满分之间，减少批阅时的手工输入。 */
function applyQuickScore(question: AttemptQuestion, value: number): void {
  const answerId = question.answer?.examAnswerId;
  if (!answerId) return;
  const max = question.score || 0;
  scores.value[answerId] = Math.min(Math.max(Number(value.toFixed(1)), 0), max);
}

/** 加载有权限的作答详情，并以当前已保存人工分数初始化表单。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const [attemptDetail, events] = await Promise.all([getReviewAttempt(attemptId), getExamAnticheatEvents(attemptId)]);
    attempt.value = attemptDetail;
    anticheatEvents.value = events;
    scores.value = {};
    comments.value = {};
    attempt.value.questions.forEach((item) => {
      if (item.answer?.examAnswerId) {
        if (item.answer.manualScore !== null && item.answer.manualScore !== undefined) {
          scores.value[item.answer.examAnswerId] = item.answer.manualScore;
        }
        comments.value[item.answer.examAnswerId] = item.answer.reviewComment ?? '';
      }
    });
  } finally {
    loading.value = false;
  }
}

/** 复用既有单题接口顺序保存；接口无事务语义，失败后明确显示部分保存事实。 */
async function saveAllScores(): Promise<void> {
  if (isScorePublished.value) {
    ElMessage.warning('正式成绩已发布，当前作答不可再批阅或改分。');
    return;
  }
  if (pendingQuestionNumbers.value.length) {
    ElMessage.warning(`请先完成第 ${pendingQuestionNumbers.value.join('、')} 题评分。`);
    return;
  }
  const savedNumbers: number[] = [];
  let failedNumber: number | undefined;
  let failureMessage = '';
  savingAll.value = true;
  try {
    for (const question of manualQuestions.value) {
      const answerId = question.answer?.examAnswerId;
      if (!answerId) continue;
      try {
        await reviewExamAnswer(answerId, {
          afterScore: scores.value[answerId],
          reviewComment: comments.value[answerId],
          reviewAction: 'SCORE'
        });
        savedNumbers.push(savedNumbers.length + 1);
      } catch (error) {
        failedNumber = savedNumbers.length + 1;
        failureMessage = error instanceof Error ? error.message : '请求失败，请稍后重试。';
        break;
      }
    }
    if (failedNumber) {
      const remaining = manualQuestions.value.length - savedNumbers.length;
      ElMessage.error(`部分评分保存失败：第 ${failedNumber} 题未保存，另有 ${remaining - 1} 题未提交。${failureMessage}`);
    } else {
      ElMessage.success('全部评分已保存。');
    }
    await load();
  } finally {
    savingAll.value = false;
  }
}

onMounted(() => void load());
</script>
<template>
  <section v-loading="loading" class="page">
    <header class="ws-hero">
      <div class="ws-hero__main">
        <span class="ws-hero__eyebrow">TEACHER REVIEW</span>
        <div class="ws-hero__title-row">
          <h1 class="ws-hero__title">教师批阅</h1>
          <ElTag v-if="isScorePublished" type="warning" size="small">成绩已发布 · 只读</ElTag>
          <ElTag v-else-if="manualQuestions.length" :type="pendingQuestionNumbers.length ? 'warning' : 'success'" size="small" effect="plain">
            {{ pendingSummaryText }}
          </ElTag>
        </div>
        <p class="grading-hero__note">仅展示本次作答中的主观题；评分保存后将更新为最新评分结果。</p>
        <div class="ws-hero__meta">
          <span class="ws-hero__chip">主观题 {{ manualQuestions.length }} 题</span>
          <span class="ws-hero__chip">满分合计 {{ manualTotalScore }} 分</span>
          <span class="ws-hero__chip">已评分 {{ scoredQuestionCount }} 题</span>
        </div>
      </div>
      <div class="ws-hero__aside grading-hero__aside">
        <div v-if="manualQuestions.length" class="grading-score">
          <span class="grading-score__label">简答题评分</span>
          <strong class="grading-score__value">{{ scoredQuestionCount }}<em>/ {{ manualQuestions.length }}</em></strong>
          <ElProgress :percentage="gradingPercent" :show-text="false" :stroke-width="4" />
        </div>
        <div class="grading-hero__actions">
          <ElButton
            v-if="manualQuestions.length"
            type="primary"
            :disabled="isScorePublished"
            :loading="savingAll"
            @click="saveAllScores"
          >
            保存全部评分
          </ElButton>
          <ElButton v-if="canPublishScore" @click="router.push(`/admin/business-center/exams/exam/${examId}/results/${attemptId}`)">
            进入成绩处置
          </ElButton>
          <ElButton @click="router.back()">返回</ElButton>
        </div>
      </div>
    </header>
    <ElAlert v-if="isScorePublished" title="正式成绩已发布，当前作答不可再批阅或改分" type="warning" show-icon :closable="false" />
    <div v-else-if="pendingQuestionNumbers.length" class="ws-tip grading-tip">
      待完成评分：第 {{ pendingQuestionNumbers.join('、') }} 题。0 分同样属于有效评分，保存后会覆盖已保存的人工分。
    </div>
    <ElEmpty v-if="attempt && !manualQuestions.length" description="该作答没有需要人工批阅的题目" />
    <section class="ws-section anticheat-card">
      <div class="ws-section__head">
        <div>
          <h2>反作弊事件</h2>
          <p>记录本次作答期间触发的监控事件与对应处置动作。</p>
        </div>
        <span class="ws-section__extra">共 {{ anticheatEvents.length }} 条</span>
      </div>
      <ElTable :data="anticheatEvents" max-height="320" empty-text="暂无反作弊事件">
        <ElTableColumn type="index" label="序号" width="60" />
        <ElTableColumn prop="eventTime" label="发生时间" min-width="180" />
        <ElTableColumn label="事件类型" min-width="140">
          <template #default="{ row }">{{ getExamEnumLabel(row.eventType, ANTICHEAT_EVENT_TYPE_OPTIONS) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="eventCount" label="累计次数" width="100" />
        <ElTableColumn label="处置动作" min-width="120">
          <template #default="{ row }">{{ getExamEnumLabel(row.actionTaken, ANTICHEAT_ACTION_OPTIONS) }}</template>
        </ElTableColumn>
        <ElTableColumn label="行为证据" min-width="220">
          <template #default="{ row }">
            <div class="evidence-cell">
              <span>{{ getEvidenceSummary(row as ExamAnticheatEvent) }}</span>
              <ElPopover trigger="click" placement="left" :width="360">
                <template #reference><ElButton link type="primary" size="small">查看详情</ElButton></template>
                <div class="evidence-detail">
                  <p><strong>事件来源：</strong>{{ getEvidenceDetail(row as ExamAnticheatEvent).source }}</p>
                  <p><strong>附件状态：</strong>{{ getEvidenceDetail(row as ExamAnticheatEvent).attachment }}</p>
                  <strong>原始载荷：</strong>
                  <pre>{{ getEvidenceDetail(row as ExamAnticheatEvent).payload }}</pre>
                </div>
              </ElPopover>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
    </section>
    <section v-if="manualQuestions.length" class="review-board">
      <div class="ws-section__head review-board__head">
        <div>
          <h2>主观题批阅</h2>
          <p>对照学员答案与参考答案逐题评分；保存后会覆盖该题已保存的人工分。</p>
        </div>
        <span class="ws-section__extra">共 {{ manualQuestions.length }} 题</span>
      </div>
      <div class="review-list">
        <article
          v-for="(question, index) in manualQuestions"
          :key="question.attemptQuestionId"
          class="review-card"
          :class="{ 'review-card--reviewed': isQuestionReviewed(question) }"
        >
          <header class="review-card__head">
            <span class="review-card__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="review-card__title">
              <strong>第 {{ index + 1 }} 题</strong>
              <small>本题满分 {{ question.score }} 分</small>
            </div>
            <div class="review-card__tags">
              <ElTag size="small" effect="light" :type="gradingTone(question)">
                {{ getExamEnumLabel(question.answer?.gradingStatus, GRADING_STATUS_OPTIONS) }}
              </ElTag>
              <ElTag size="small" effect="plain" :type="manualScoreTone(question)">
                {{ manualScoreText(question.answer?.manualScore) }}
              </ElTag>
            </div>
          </header>
          <p class="review-card__stem">{{ question.stemSnapshot }}</p>
          <div class="review-card__answers">
            <section class="answer-block" :class="{ 'answer-block--empty': !question.answer?.answerText }">
              <header class="answer-block__head">学员答案</header>
              <p class="answer-block__text">{{ question.answer?.answerText || '学员未作答' }}</p>
            </section>
            <section class="answer-block answer-block--reference">
              <header class="answer-block__head">参考答案</header>
              <p class="answer-block__text">{{ question.standardAnswer || '未提供参考答案' }}</p>
            </section>
          </div>
          <footer v-if="question.answer?.examAnswerId" class="review-card__scoring">
            <div class="score-field">
              <span class="field-label">本次得分</span>
              <div class="score-field__row">
                <ElInputNumber
                  v-model="scores[question.answer.examAnswerId]"
                  class="score-field__input"
                  :min="0"
                  :max="question.score"
                  controls-position="right"
                  :disabled="isScorePublished"
                />
                <span class="score-field__total">/ {{ question.score }} 分</span>
                <div class="score-shortcuts">
                  <ElButton size="small" plain :disabled="isScorePublished" @click="applyQuickScore(question, question.score)">
                    满分
                  </ElButton>
                  <ElButton size="small" plain :disabled="isScorePublished" @click="applyQuickScore(question, question.score / 2)">
                    半分
                  </ElButton>
                  <ElButton size="small" plain :disabled="isScorePublished" @click="applyQuickScore(question, 0)">0 分</ElButton>
                </div>
              </div>
            </div>
            <div class="comment-field">
              <span class="field-label">批阅意见（选填）</span>
              <ElInput
                v-model="comments[question.answer.examAnswerId]"
                type="textarea"
                :rows="2"
                resize="none"
                maxlength="200"
                placeholder="内部批阅备注，仅管理端回显，不会展示给学员"
                :disabled="isScorePublished"
              />
            </div>
          </footer>
        </article>
      </div>
    </section>
  </section>
</template>
<style scoped lang="less">
.page {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  gap: 16px;
  overflow: auto;
}

/* 页面为纵向 flex 容器：各区块按内容高度排布，滚动统一由 .page 承担，避免区块被压缩。 */
.page > * {
  flex: 0 0 auto;
}

.grading-hero__note {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.grading-hero__aside {
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* 评分进度从标题旁的小字块升为独立指标，数字与进度条给出可扫读的完成度。 */
.grading-score {
  display: grid;
  min-width: 136px;
  gap: 4px;
  padding: 10px 14px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: var(--color-bg-surface);
  text-align: right;
}

.grading-score__label {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.grading-score__value {
  color: var(--color-primary);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 26px;
}

.grading-score__value em {
  margin-left: 4px;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
}

.grading-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.grading-tip {
  align-items: center;
}

/* 表格必须能压缩，否则长内容会把网格列撑出容器。 */
.anticheat-card {
  grid-template-columns: minmax(0, 1fr);
}

/* 行为摘要用于快速扫读，详情保留原始载荷以便复核，不把事件数据伪装成附件。 */
.evidence-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.evidence-detail {
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 20px;
}

.evidence-detail p {
  margin: 0 0 6px;
}

.evidence-detail pre {
  max-height: 160px;
  margin: 6px 0 0;
  padding: 8px;
  overflow: auto;
  border-radius: 6px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-family: var(--font-family-mono, monospace);
  font-size: 12px;
  line-height: 18px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 批阅看板：标题与题目列表同处一个区块，卡片直接落在页面底色上，避免白卡套白卡。 */
.review-board,
.review-list {
  display: grid;
  min-width: 0;
  gap: 12px;
}

.review-board__head {
  padding: 0 2px;
}

/* 单题批阅卡：题干、双栏答案、评分与批阅意见集中在一屏内，不需要展开收起。 */
.review-card {
  display: grid;
  min-width: 0;
  gap: 12px;
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.review-card:hover {
  border-color: #bfdbfe;
  box-shadow: var(--shadow-card-hover);
}

.review-card--reviewed {
  background: var(--color-bg-subtle);
}

.review-card__head {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.review-card__index {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 999px;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.review-card__title {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}

.review-card__title strong {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.review-card__title small {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.review-card__tags {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 6px;
}

/* 题干独立成条：左侧品牌色竖条替代加粗大字，长题干同样能换行不挤压。 */
.review-card__stem {
  margin: 0;
  padding: 10px 14px;
  border-left: 3px solid var(--color-primary);
  border-radius: 0 8px 8px 0;
  background: var(--color-bg-subtle);
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 22px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 双栏答案：学员作答用品牌色承重，参考答案保持中性，两栏左右等高便于逐点对照。 */
.review-card__answers {
  display: grid;
  min-width: 0;
  gap: 12px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.answer-block {
  display: grid;
  min-width: 0;
  align-content: start;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: var(--color-primary-subtle);
}

.answer-block--reference,
.answer-block--empty {
  border-color: var(--color-border);
  background: var(--color-bg-subtle);
}

.answer-block__head {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.answer-block__text {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 22px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.answer-block--empty .answer-block__text {
  color: var(--color-text-secondary);
}

.review-card__scoring {
  display: grid;
  min-width: 0;
  align-items: start;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
  grid-template-columns: minmax(0, 300px) minmax(0, 1fr);
}

.score-field,
.comment-field {
  display: grid;
  min-width: 0;
  gap: 8px;
}

.field-label {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.score-field__row {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.score-field__input {
  width: 120px;
}

.score-field__total {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.score-shortcuts {
  display: flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 1024px) {
  .review-card__scoring {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .review-card__answers {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
