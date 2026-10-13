<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { Check, Warning } from '@element-plus/icons-vue';
import { EXAM_REFERENCE_PAGE_SIZE, getExamCandidates, getExamReviewers, type ExamCandidate, type ExamItem, type ExamReviewer } from '@/api/exam';
import type { PaperVersionOption } from '@/api/paper';
import type { QuestionBank, QuestionBankCategory } from '@/api/question';
import {
  CANDIDATE_STATUS_OPTIONS,
  EXAM_TYPE_OPTIONS,
  MULTI_SELECT_SCORING_OPTIONS,
  QUESTION_ORDER_MODE_OPTIONS,
  REVIEWER_ASSIGNMENT_SOURCE_OPTIONS,
  getExamDrawRuleText,
  getExamEnumLabel,
  sumDrawRules
} from '@/constants/exam-options';
import ExamPolicySummary from './ExamPolicySummary.vue';
import { formatDuration } from '@/utils/duration';

const props = defineProps<{
  exam: ExamItem;
  candidateCount: number;
  reviewerCount: number;
  publishing: boolean;
  published?: boolean;
  paperOptions: PaperVersionOption[];
  categoryOptions: QuestionBankCategory[];
  questionBankOptions: QuestionBank[];
}>();
const emit = defineEmits<{ publish: []; summaryLoaded: [candidateCount: number, reviewerCount: number] }>();
const candidates = ref<ExamCandidate[]>([]);
const reviewers = ref<ExamReviewer[]>([]);
const candidateTotal = ref(0);
const personLoading = ref(false);
/** 仅线上考试和专项练习会产生人工批阅任务。 */
const requiresReviewer = computed(() => props.exam.examType === 'ONLINE' || props.exam.examType === 'SPECIAL_PRACTICE');

/** 发布核查只读取当前已绑定名单，用于让管理员确认姓名与部门，不参与人员配置写入。 */
async function loadPersonnel(): Promise<void> {
  personLoading.value = true;
  try {
    const [candidatePage, reviewerList] = await Promise.all([
      getExamCandidates(props.exam.examId, { pageNum: 1, pageSize: EXAM_REFERENCE_PAGE_SIZE }),
      requiresReviewer.value ? getExamReviewers(props.exam.examId) : Promise.resolve([])
    ]);
    candidates.value = candidatePage.list;
    candidateTotal.value = candidatePage.total;
    reviewers.value = reviewerList;
    emit('summaryLoaded', candidatePage.total, reviewerList.length);
  } finally {
    personLoading.value = false;
  }
}

/** 题库练习必须带抽题规则才能发布，其余类型不参与该校验。 */
const isBankPractice = computed(() => props.exam.examType === 'BANK_PRACTICE');
/** 抽题规则明细，发布前让管理员确认抽题来源与总分口径。 */
const drawRuleLines = computed(() => (isBankPractice.value ? (props.exam.drawRules ?? []) : []));
/** 抽题合计题数与分值，与考试信息页保持同一口径。 */
const drawRuleSummary = computed(() => sumDrawRules(drawRuleLines.value));
/** 缺少抽题规则的题库练习不允许发布，避免冻结全量题库导致及格线不可达。 */
const drawRulesMissing = computed(() => isBankPractice.value && !drawRuleLines.value.length);

/** 考试分类使用题库分类名称展示。 */
function categoryLabel(categoryId?: number): string {
  if (!categoryId) return '未设置';
  return props.categoryOptions.find((item) => item.bankCategoryId === categoryId)?.categoryName ?? `分类 #${categoryId}`;
}

/** 发布核查清单只把现有门禁条件可视化，不参与任何发布判定。 */
const publishChecks = computed<Array<{ key: string; title: string; ready: boolean; detail: string }>>(() => {
  const checks: Array<{ key: string; title: string; ready: boolean; detail: string }> = [];
  if (isBankPractice.value) {
    checks.push({
      key: 'definition',
      title: '考试定义',
      ready: !drawRulesMissing.value,
      detail: drawRulesMissing.value ? '抽题规则未配置，发布后及格线将不可达' : `已配置 ${drawRuleLines.value.length} 条抽题规则`
    });
  } else {
    checks.push({
      key: 'definition',
      title: '考试定义',
      ready: true,
      detail: props.exam.paperName
        ? `已绑定试卷 ${props.exam.paperName}`
        : `未绑定试卷，按本场规则 ${formatDuration(props.exam.durationSeconds, '—')} / ${props.exam.passScore ?? '—'} 分发卷`
    });
  }
  checks.push({
    key: 'candidates',
    title: '考生配置',
    ready: props.candidateCount > 0,
    detail: props.candidateCount > 0 ? `已绑定 ${props.candidateCount} 名考生` : '尚未绑定考生，请返回人员配置完成绑定'
  });
  if (requiresReviewer.value) {
    checks.push({
      key: 'reviewers',
      title: '批阅人配置',
      ready: props.reviewerCount > 0,
      detail: props.reviewerCount > 0 ? `已配置 ${props.reviewerCount} 名批阅人` : '尚未配置批阅人，请返回人员配置选择批阅人'
    });
  }
  return checks;
});

/** 核查页顶部的关键指标，按考试类型收敛及格口径。 */
const checkMetrics = computed<Array<{ label: string; value: string; unit?: string; accent?: boolean }>>(() => {
  const current = props.exam;
  const metrics: Array<{ label: string; value: string; unit?: string; accent?: boolean }> = [
    { label: '考试时长', value: formatDuration(current.durationSeconds) }
  ];
  if (!isBankPractice.value) {
    metrics.push({ label: '及格分数', value: current.passScore == null ? '未设置' : String(current.passScore), unit: '分', accent: true });
  }
  metrics.push({ label: '学分', value: current.credit == null ? '未设置' : String(current.credit) });
  metrics.push({ label: '开放开始时间', value: current.openStartTime || '未设置' });
  metrics.push({ label: '开放结束时间', value: current.openEndTime || '未设置' });
  return metrics;
});

onMounted(() => {
  void loadPersonnel();
});
watch(
  () => props.exam.examId,
  () => {
    void loadPersonnel();
  }
);
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card admin-detail-card-spacing">
    <div class="admin-detail-page__form-scroll">
      <ElAlert v-if="published" title="考试已发布，考试定义和人员配置已锁定，仅可查看。" type="success" :closable="false" show-icon class="published-alert" />
      <!-- 发布核查清单：把分散在页面各处的门禁条件收敛为一眼可读的状态。 -->
      <section class="ws-check-list publish-check-list">
        <div v-for="item in publishChecks" :key="item.key" class="ws-check" :class="item.ready ? 'ws-check--pass' : 'ws-check--fail'">
          <span class="ws-check__icon">
            <ElIcon><Check v-if="item.ready" /><Warning v-else /></ElIcon>
          </span>
          <span class="ws-check__body">
            <strong>{{ item.title }}</strong>
            <span>{{ item.detail }}</span>
          </span>
        </div>
      </section>
      <section class="ws-metrics publish-check__metrics">
        <div v-for="item in checkMetrics" :key="item.label" class="ws-metric" :class="{ 'ws-metric--accent': item.accent }">
          <span class="ws-metric__label">{{ item.label }}</span>
          <span class="ws-metric__value">{{ item.value }}<em v-if="item.unit">{{ item.unit }}</em></span>
        </div>
      </section>
      <section class="ws-section">
        <div class="ws-section__head">
          <div>
            <h3>考试信息</h3>
            <p>发布前请核对考试身份、资源绑定与作答规则。</p>
          </div>
        </div>
        <div class="ws-kv">
          <div class="ws-kv__item">
            <span class="ws-kv__label">考试编码</span>
            <span class="ws-kv__value">{{ exam.examCode }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">考试名称</span>
            <span class="ws-kv__value">{{ exam.examName }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">考试类型</span>
            <span class="ws-kv__value">{{ getExamEnumLabel(exam.examType, EXAM_TYPE_OPTIONS) }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">试卷版本</span>
            <span class="ws-kv__value">{{ exam.paperName || '未设置' }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">考试分类</span>
            <span class="ws-kv__value">{{ categoryLabel(exam.examCategoryId) }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">题库</span>
            <span class="ws-kv__value">{{ exam.questionBankName || '未设置' }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">题目顺序</span>
            <span class="ws-kv__value">{{ getExamEnumLabel(exam.questionOrderMode, QUESTION_ORDER_MODE_OPTIONS) }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">多选题计分</span>
            <span class="ws-kv__value">{{ getExamEnumLabel(exam.multiSelectScoring, MULTI_SELECT_SCORING_OPTIONS) }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">考试须知</span>
            <span class="ws-kv__value">{{ exam.isNoticeEnabled ? '已启用' : '未启用' }}</span>
          </div>
        </div>
      </section>
      <section v-if="isBankPractice" class="ws-section">
        <div class="ws-section__head">
          <div>
            <h3>抽题规则</h3>
            <p>开考时按以下规则从题库抽取并冻结题目；客观题自动判分，简答题仅记录作答，不判定及格。</p>
          </div>
          <span v-if="drawRuleLines.length" class="ws-section__extra">共 {{ drawRuleLines.length }} 条 · 合计 {{ drawRuleSummary.count }} 题</span>
        </div>
        <div v-if="drawRuleLines.length" class="draw-rule-preview">
          <span v-for="(rule, index) in drawRuleLines" :key="index">{{ index + 1 }}. {{ getExamDrawRuleText(rule) }}</span>
        </div>
        <ElTag v-else type="danger">未配置抽题规则</ElTag>
      </section>
      <ExamPolicySummary :exam="exam" class="check-block" />
      <section class="ws-section">
        <div class="ws-section__head">
          <div>
            <h3>考生名单</h3>
            <p>以下为当前已绑定的实际考生。</p>
          </div>
          <span class="ws-section__extra">已显示 {{ candidates.length }} / {{ candidateTotal }} 人</span>
        </div>
        <ElTable v-loading="personLoading" :data="candidates" stripe :max-height="360">
          <ElTableColumn type="index" label="序号" width="72" />
          <ElTableColumn prop="learnerCode" label="学员账号" width="160" show-overflow-tooltip />
          <ElTableColumn prop="learnerName" label="姓名" min-width="160" show-overflow-tooltip />
          <ElTableColumn prop="deptName" label="部门" min-width="220" show-overflow-tooltip />
          <ElTableColumn label="考生状态" min-width="140">
            <template #default="{ row }">{{ getExamEnumLabel(row.candidateStatus, CANDIDATE_STATUS_OPTIONS) }}</template>
          </ElTableColumn>
          <template #empty><ElEmpty description="尚未绑定考生，请返回人员配置步骤完成绑定" :image-size="60" /></template>
        </ElTable>
      </section>
      <section v-if="requiresReviewer" class="ws-section">
        <div class="ws-section__head">
          <div>
            <h3>批阅人名单</h3>
            <p>批阅人将在发布后承担人工批阅或复核工作。</p>
          </div>
          <span class="ws-section__extra">共 {{ reviewers.length }} 人</span>
        </div>
        <ElTable v-loading="personLoading" :data="reviewers" stripe :max-height="360">
          <ElTableColumn type="index" label="序号" width="72" />
          <ElTableColumn prop="reviewerCode" label="用户编码" width="160" show-overflow-tooltip />
          <ElTableColumn prop="reviewerName" label="姓名" min-width="180" show-overflow-tooltip />
          <ElTableColumn prop="reviewerOrganizationName" label="所属机构" min-width="240" show-overflow-tooltip />
          <ElTableColumn label="配置来源" min-width="140">
            <template #default="{ row }">{{ getExamEnumLabel(row.assignmentSource, REVIEWER_ASSIGNMENT_SOURCE_OPTIONS) }}</template>
          </ElTableColumn>
          <template #empty><ElEmpty description="尚未配置批阅人，请返回人员配置步骤选择评卷人" :image-size="60" /></template>
        </ElTable>
      </section>
      <ElAlert v-if="drawRulesMissing" title="题库练习未配置抽题规则，请返回考试信息补充规则后再发布。" type="warning" :closable="false" show-icon class="publish-check__alert" />
      <ElAlert v-if="!candidateCount || (requiresReviewer && !reviewerCount)" :title="requiresReviewer ? '请先完成考生和批阅人配置后再发布考试。' : '请先完成考生配置后再发布考试。'" type="warning" :closable="false" show-icon class="publish-check__alert" />
    </div>
    <footer v-if="!published" class="admin-detail-action-bar">
      <ElButton type="success" :disabled="!candidateCount || (requiresReviewer && !reviewerCount) || drawRulesMissing" :loading="publishing" @click="emit('publish')">
        发布考试
      </ElButton>
    </footer>
  </ElCard>
</template>

<style scoped lang="less">
/* 核查清单、指标条与分组卡在内容区内保持统一节奏。 */
.publish-check-list,
.publish-check__metrics {
  margin-bottom: var(--space-4);
}

/* 考试策略与考试信息之间由页面统一控制间距，避免两层外边距叠加。 */
.check-block {
  margin: var(--space-4) 0;
}

.published-alert {
  margin-bottom: var(--space-4);
}

.publish-check__alert {
  margin-top: var(--space-3);
}

.draw-rule-preview {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--color-text-regular);
  line-height: 1.7;
}
</style>
