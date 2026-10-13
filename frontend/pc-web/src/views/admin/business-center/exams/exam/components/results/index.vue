<script setup lang="ts">
import type { LearnerExamAttempt } from '@shared/types';
import { computed, onMounted, ref } from 'vue';
import { Check, InfoFilled, Warning } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { createRetakeAttempt, getExamScorePublishLogs, getReviewAttempt, publishExamScore, type ExamScorePublishLog } from '@/api/exam-grading';
import { useUserNameResolver } from '@/composables/use-user-name';
import { useUserStore } from '@/stores/modules/user';
import { hasPermission } from '@/utils/permission';
import {
  ATTEMPT_STATUS_OPTIONS,
  getExamEnumLabel,
  GRADING_STATUS_OPTIONS,
  QUESTION_TYPE_OPTIONS,
  SCORE_PUBLISH_STATUS_OPTIONS,
  SCORE_PUBLISH_TYPE_OPTIONS,
  SCORE_RESULT_OPTIONS
} from '@/constants/exam-options';
import {
  canPublishScore,
  getRetakeEligibilityHint,
  hasPendingManualReview,
  shouldShowRetakeAction,
  toScoreDispositionQuestionRows
} from './score-disposition-state';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const { loadUserNames, userLabel } = useUserNameResolver();
const examId = Number(route.params.examId);
const attemptId = Number(route.params.examAttemptId);
const attempt = ref<LearnerExamAttempt>();
/** 优先使用入口携带的考生标识；从批阅页进入时则复用作答详情中的考生标识。 */
const examCandidateId = computed(() => Number(route.query.examCandidateId) || attempt.value?.examCandidateId || 0);
const scorePublishLogs = ref<ExamScorePublishLog[]>([]);
const loading = ref(false);
const operating = ref(false);
const retakeLoading = ref(false);
/** 控制右侧抽屉，仅在教师需要核对作答明细时展开。 */
const detailDrawerVisible = ref(false);
/** 同一次开补考操作失败后复用令牌，避免重复创建作答。 */
const retakeToken = ref('');
/** 待批阅主观题是发布正式成绩的硬性阻断条件。 */
const hasPendingReview = computed(() => (attempt.value ? hasPendingManualReview(attempt.value.questions) : false));
/** 为教师提供逐题答案、分项得分和批阅状态的发布前核对清单。 */
const questionRows = computed(() => (attempt.value ? toScoreDispositionQuestionRows(attempt.value.questions) : []));
/** 已发布时读取正式结果；待发布且无待阅题时读取服务端派生的预计结果。 */
const scoreResultLabel = computed(() => {
  const passed = attempt.value?.isPassed ?? attempt.value?.predictedIsPassed;
  if (passed === null || passed === undefined) return hasPendingReview.value ? '待人工批阅' : '不判定';
  return passed === 1 ? '及格' : '未及格';
});
/** 结论标签的语义色：只有真出分才用及格/不及格色，待批阅与不判定保持中性，避免「待批阅」被渲染成绿色。 */
const scoreResultTone = computed(() => {
  if (scoreResultLabel.value === '及格') return 'success';
  if (scoreResultLabel.value === '未及格') return 'danger';
  return scoreResultLabel.value === '待人工批阅' ? 'warning' : 'info';
});
/** 批阅完整性只看是否还有待批阅主观题；已生效的练习仍要提示批阅未完成，避免与抽屉告警自相矛盾。 */
const reviewCheckPassed = computed(() => !hasPendingReview.value);
const reviewCheckText = computed(() => {
  const status = attempt.value?.scorePublishStatus;
  if (hasPendingReview.value) {
    return status === 'EFFECTIVE'
      ? '题库练习已按自动判分生效，但仍有主观题待批阅，批阅后请重新核对得分。'
      : '仍有主观题待批阅，不能发布成绩。';
  }
  if (status === 'EFFECTIVE') return '题库练习结果已自动生效，无需人工发布成绩。';
  if (status === 'PUBLISHED') return '正式成绩已发布，本作答内容转为只读。';
  return '主观题已全部批阅，可发布正式成绩。';
});
/** 抽屉内的题量概览，帮助教师在逐题核对前先判断工作量。 */
const questionSummaryText = computed(() => {
  const total = questionRows.value.length;
  const pending = questionRows.value.filter((row) => row.gradingStatus !== 'MANUAL_GRADED' && row.gradingStatus !== 'AUTO_GRADED').length;
  if (!total) return '暂无作答题目';
  return pending ? `共 ${total} 题 · 待批阅 ${pending} 题` : `共 ${total} 题 · 已全部批阅`;
});
/** 处置动作区的一句话结论：已发布、已生效、待批阅、可发布四态各自表述，避免已发布仍提示可发布。 */
const releaseHeadline = computed(() => {
  const status = attempt.value?.scorePublishStatus;
  if (status === 'EFFECTIVE') return '题库练习已自动生效，可查看解析';
  if (status === 'PUBLISHED') return '正式成绩已发布，本作答转为只读查看';
  return hasPendingReview.value ? '仍有主观题待批阅' : '评分已完成，请核对后发布成绩，发布后学员可查看成绩';
});
/** 仅具备人工批阅权限的用户显示返回批阅入口。 */
const canReview = computed(() => hasPermission(userStore.userInfo, 'exam:review'));
/** 补考按钮仅以服务端资格和当前用户权限共同决定；创建时仍由服务端二次校验。 */
const canStartRetake = computed(() =>
  shouldShowRetakeAction(attempt.value?.retakeEligibility, hasPermission(userStore.userInfo, 'exam:attempt:start'))
);
/** 补考策略与当前考生事实不匹配时保留原因，帮助教师处理而不展示无效操作。 */
const retakeEligibilityHint = computed(() => getRetakeEligibilityHint(attempt.value?.retakeEligibility));

/** 读取成绩处置所需的作答快照。 */ async function load(): Promise<void> {
  loading.value = true;
  try {
    const [attemptDetail, logs] = await Promise.all([getReviewAttempt(attemptId), getExamScorePublishLogs(attemptId)]);
    attempt.value = attemptDetail;
    scorePublishLogs.value = logs;
    await loadUserNames([...logs.map((item) => item.publishedBy), attemptDetail.userId]);
  } finally {
    loading.value = false;
  }
}

/** 服务端负责最终校验；页面先阻断待批阅作答和已发布成绩。 */ async function publish(): Promise<void> {
  if (!attempt.value || !canPublishScore(attempt.value)) {
    ElMessage.warning('仍有主观题待批阅，不能发布正式成绩。');
    return;
  }
  try {
    await ElMessageBox.confirm('发布后将作为正式成绩对学员可见，确认继续吗？', '发布成绩', { type: 'warning' });
    operating.value = true;
    await publishExamScore(attemptId, userStore.userInfo?.userId);
    ElMessage.success('正式成绩已发布。');
    await load();
  } finally {
    operating.value = false;
  }
}

/** 仅使用契约规定的考生标识和幂等令牌为当前考生开通一次补考。 */
async function startRetake(): Promise<void> {
  if (!canStartRetake.value) return;
  try {
    await ElMessageBox.confirm('将为该学员开通一次补考资格，确认继续吗？', '开补考', { type: 'warning' });
    if (!retakeToken.value) retakeToken.value = globalThis.crypto.randomUUID();
    retakeLoading.value = true;
    await createRetakeAttempt(examCandidateId.value, retakeToken.value);
    retakeToken.value = '';
    ElMessage.success('已开通补考，学员可在补考开放时间内完成作答。');
    await load();
  } finally {
    retakeLoading.value = false;
  }
}

onMounted(() => void load());
</script>
<template>
  <section v-loading="loading" class="page">
    <header class="score-disposition-hero">
      <div class="ws-hero">
        <div class="ws-hero__main">
          <span class="ws-hero__eyebrow">SCORE DISPOSITION</span>
          <div class="ws-hero__title-row">
            <h1 class="ws-hero__title">作答成绩处置</h1>
            <ElTag v-if="attempt" :type="scoreResultTone" size="small">{{ scoreResultLabel }}</ElTag>
            <ElTag v-if="attempt" size="small" effect="plain">
              成绩{{ getExamEnumLabel(attempt.scorePublishStatus, SCORE_PUBLISH_STATUS_OPTIONS) }}
            </ElTag>
          </div>
          <p class="score-disposition-hero__note">完成批阅后发布本次作答的正式成绩；发布成功后，学员可在考试中心查看。</p>
          <div v-if="attempt" class="ws-hero__meta">
            <span class="ws-hero__chip">考生 {{ userLabel(attempt.userId) }}</span>
            <span class="ws-hero__chip">第 {{ attempt.attemptNo }} 次作答</span>
            <span class="ws-hero__chip">交卷 {{ attempt.submittedTime || '尚未交卷' }}</span>
          </div>
        </div>
        <div class="ws-hero__aside score-disposition-hero__actions">
          <ElButton v-if="canReview" @click="router.push(`/admin/business-center/exams/exam/${examId}/grading/${attemptId}`)">返回批阅</ElButton>
          <ElButton @click="router.back()">返回</ElButton>
        </div>
      </div>
    </header>
    <ElCard v-if="attempt" class="detail-card score-disposition-card" shadow="never">
      <section class="score-disposition-summary">
        <div class="score-disposition-summary__score">
          <span>最终成绩</span>
          <strong>
            {{ attempt.finalScore ?? '—' }}
            <small>分</small>
          </strong>
          <ElTag :type="scoreResultTone" size="small" effect="light" class="score-disposition-summary__result">{{ scoreResultLabel }}</ElTag>
        </div>
        <div class="score-disposition-summary__state">
          <span>作答状态</span>
          <strong>{{ getExamEnumLabel(attempt.attemptStatus, ATTEMPT_STATUS_OPTIONS) }}</strong>
          <small>成绩{{ getExamEnumLabel(attempt.scorePublishStatus, SCORE_PUBLISH_STATUS_OPTIONS) }}</small>
        </div>
        <div class="score-disposition-summary__retake">
          <span>补考资格</span>
          <strong>{{ attempt.retakeEligibility?.eligible ? '可开通补考' : '当前不可开通' }}</strong>
          <small>{{ retakeEligibilityHint }}</small>
        </div>
      </section>
      <section class="score-disposition-actions">
        <div>
          <span class="score-disposition-actions__eyebrow">REVIEW &amp; RELEASE</span>
            <strong>{{ releaseHeadline }}</strong>
        </div>
        <ElButton type="primary" @click="detailDrawerVisible = true">查看作答与得分</ElButton>
        <ElButton v-if="canStartRetake" :loading="retakeLoading" @click="startRetake">开补考</ElButton>
      </section>
      <section class="ws-check-list score-disposition-checks">
        <div class="ws-check" :class="reviewCheckPassed ? 'ws-check--pass' : 'ws-check--fail'">
          <span class="ws-check__icon">
            <ElIcon><Check v-if="reviewCheckPassed" /><Warning v-else /></ElIcon>
          </span>
          <span class="ws-check__body">
            <strong>批阅完整性</strong>
            <span>{{ reviewCheckText }}</span>
          </span>
        </div>
        <div class="ws-check score-disposition-check--note">
          <span class="ws-check__icon">
            <ElIcon><InfoFilled /></ElIcon>
          </span>
          <span class="ws-check__body">
            <strong>成绩同步</strong>
            <span>课程考试完成情况将在课程任务中同步更新；发布成绩后请刷新查看最新结果。</span>
          </span>
        </div>
      </section>
      <section class="score-publish-history">
        <div class="ws-section">
          <div class="ws-section__head">
            <div>
              <h3>成绩发布流水</h3>
              <p>记录本次作答每一次正式成绩的发布动作与结果。</p>
            </div>
            <span class="ws-section__extra">{{ scorePublishLogs.length }} 条记录</span>
          </div>
          <ElTable :data="scorePublishLogs" max-height="360" empty-text="暂无成绩发布记录">
            <ElTableColumn prop="publishedTime" label="发布时间" />
            <ElTableColumn label="发布人">
              <template #default="{ row }">{{ userLabel(row.publishedBy) }}</template>
            </ElTableColumn>
            <ElTableColumn label="发布方式">
              <template #default="{ row }">{{ getExamEnumLabel(row.publishType, SCORE_PUBLISH_TYPE_OPTIONS) }}</template>
            </ElTableColumn>
            <ElTableColumn prop="scoreSnapshot" label="发布成绩">
              <template #default="{ row }">
                <ElTag>{{ row.scoreSnapshot }}&nbsp;分</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="发布结果">
              <template #default="{ row }">
                <ElTag :type="row.resultSnapshot == 'FAILED' ? 'danger' : 'success'">
                  {{ getExamEnumLabel(row.resultSnapshot, SCORE_RESULT_OPTIONS, '其他结果') }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </section>
    </ElCard>
    <ElDrawer v-model="detailDrawerVisible" title="逐题作答与得分核对" size="60%" append-to-body :lock-scroll="false">
      <div v-if="attempt" class="drawer-content">
        <section class="ws-metrics drawer-summary">
          <div class="ws-metric ws-metric--accent">
            <span class="ws-metric__label">最终成绩</span>
            <span class="ws-metric__value">{{ attempt.finalScore ?? '-' }}<em>分</em></span>
          </div>
          <div class="ws-metric">
            <span class="ws-metric__label">成绩结果</span>
            <span class="ws-metric__value">{{ scoreResultLabel }}</span>
          </div>
          <div class="ws-metric">
            <span class="ws-metric__label">题目概览</span>
            <span class="ws-metric__value">{{ questionSummaryText }}</span>
          </div>
        </section>
        <div class="drawer-table">
          <ElTable :data="questionRows" height="100%" empty-text="暂无作答题目">
            <ElTableColumn prop="questionNo" label="题号" width="72" />
            <ElTableColumn label="题型" width="120">
              <template #default="{ row }">{{ getExamEnumLabel(row.questionType, QUESTION_TYPE_OPTIONS, '其他题型') }}</template>
            </ElTableColumn>
            <ElTableColumn label="考生答案" min-width="240">
              <template #default="{ row }">
                <div class="answer-text">{{ row.answerText }}</div>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="autoScore" label="自动分" width="100">
              <template #default="{ row }">{{ row.autoScore ?? '-' }}</template>
            </ElTableColumn>
            <ElTableColumn prop="manualScore" label="人工分" width="100">
              <template #default="{ row }">{{ row.manualScore ?? '-' }}</template>
            </ElTableColumn>
            <ElTableColumn label="最终得分" width="130">
              <template #default="{ row }">{{ row.finalScore ?? '-' }} / {{ row.score }}</template>
            </ElTableColumn>
            <ElTableColumn label="批阅状态" width="130">
              <template #default="{ row }">{{ getExamEnumLabel(row.gradingStatus, GRADING_STATUS_OPTIONS, '无需人工批阅') }}</template>
            </ElTableColumn>
          </ElTable>
        </div>
        <ElAlert v-if="hasPendingReview" title="仍有主观题待批阅，不能发布成绩。" type="warning" show-icon :closable="false" />
        <div class="drawer-actions">
              <ElButton v-if="canPublishScore(attempt)" type="primary" :loading="operating" @click="publish">发布正式成绩</ElButton>
          <ElButton @click="detailDrawerVisible = false">关闭</ElButton>
        </div>
      </div>
    </ElDrawer>
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

/* hero 保持既有类名承载标题，视觉交给全局 ws-hero 规格，避免页面各写一套。
   固定不参与伸缩，防止长内容把页头一起压扁。 */
.score-disposition-hero {
  display: block;
  flex: 0 0 auto;
}

.score-disposition-hero__note {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.score-disposition-hero__actions {
  flex-wrap: wrap;
}

/* 卡片按内容自然增高、永不收缩：内容变多时由 .page 滚动，而不是压缩卡内区块。 */
.detail-card {
  flex: 1 0 auto;
}

.score-disposition-card :deep(.el-card__body) {
  display: grid;
  /* 行高按内容计算，避免以剩余空间分配行高时把上方区块往下压。 */
  grid-auto-rows: max-content;
  gap: 16px;
  padding: 18px;
  /* 取消卡内滚动容器，滚动统一交给 .page，防止内部二级滚动裁掉顶部内容。 */
  overflow: visible;
}

/* 摘要三项等宽，成绩块用主题色承重，避免三栏视觉权重混淆。
   高度交给内容决定：写死高度会让换行的资格提示与结论药丸被 overflow 裁掉。 */
.score-disposition-summary {
  display: grid;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-bg-subtle);
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.score-disposition-summary > div {
  display: grid;
  align-content: start;
  gap: 4px;
  min-height: 108px;
  padding: 16px 18px;
}

.score-disposition-summary > div + div {
  border-left: 1px solid var(--color-border);
}

.score-disposition-summary span,
.score-disposition-summary small {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.score-disposition-summary strong {
  color: var(--color-text-primary);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  line-height: 26px;
  overflow-wrap: anywhere;
}

.score-disposition-summary small {
  overflow-wrap: anywhere;
}

.score-disposition-summary__score {
  background: color-mix(in srgb, var(--color-primary) 6%, var(--color-bg-surface));
}

.score-disposition-summary__score > strong {
  color: var(--color-primary);
  font-size: 30px;
  line-height: 34px;
}

.score-disposition-summary__score > strong small {
  margin-left: 4px;
  color: inherit;
  font-size: 13px;
}

/* 结论药丸独占一行左对齐，替代原先写死宽度的内联样式。 */
.score-disposition-summary__result {
  margin-top: 2px;
  justify-self: start;
}

.score-disposition-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, var(--color-primary) 20%, var(--color-border));
  border-radius: 10px;
  background: color-mix(in srgb, var(--color-primary) 4%, var(--color-bg-surface));
}

.score-disposition-actions > div {
  display: grid;
  flex: 1;
  gap: 2px;
}

.score-disposition-actions strong {
  color: var(--color-text-primary);
  font-size: 14px;
}

.score-disposition-actions__eyebrow {
  color: var(--color-primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

/* 发布前状态收敛为核查清单，替代三条整宽告警堆叠。 */
.score-disposition-checks {
  grid-template-columns: repeat(auto-fit, minmax(268px, 1fr));
}

.score-disposition-check--note {
  border-color: var(--color-border);
  background: var(--color-bg-surface);
}

.score-disposition-check--note .ws-check__icon {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}

/* 流水区只按内容增高；表格自身限高滚动，行数再多也不会拉长页面或挤压上方区块。 */
.score-publish-history {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}

.score-publish-history :deep(.ws-section) {
  grid-template-columns: minmax(0, 1fr);
}

.answer-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.drawer-content {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  gap: 16px;
}

.drawer-summary {
  flex: none;
}

.drawer-table {
  flex: 1;
  min-height: 240px;
}

.drawer-actions {
  display: flex;
  flex: none;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 900px) {
  .score-disposition-summary {
    grid-template-columns: minmax(0, 1fr);
  }

  .score-disposition-summary > div + div {
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }
}
</style>
