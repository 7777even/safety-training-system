<script setup lang="ts">
import { computed, watch } from 'vue';
import { type ExamCreatePayload, type ExamItem } from '@/api/exam';
import { getPaperVersion, type PaperVersionOption } from '@/api/paper';
import type { QuestionBank, QuestionBankCategory } from '@/api/question';
import { useUserNameResolver } from '@/composables/use-user-name';
import { formatDuration } from '@/utils/duration';
import {
  EXAM_TYPE_OPTIONS,
  MULTI_SELECT_SCORING_OPTIONS,
  QUESTION_ORDER_MODE_OPTIONS,
  getExamDrawRuleText,
  getExamEnumLabel,
  sumDrawRules
} from '@/constants/exam-options';
import ExamDefinitionForm from './ExamDefinitionForm.vue';
import ExamPolicySummary from './ExamPolicySummary.vue';

const form = defineModel<ExamCreatePayload>({ required: true });
const props = defineProps<{
  exam?: ExamItem;
  editing: boolean;
  createMode: boolean;
  saving: boolean;
  paperOptions: PaperVersionOption[];
  categoryOptions: QuestionBankCategory[];
  questionBankOptions: QuestionBank[];
}>();
const emit = defineEmits<{
  cancel: [];
  save: [];
  next: [];
  paperVersionSelected: [paperVersionId: number | undefined];
  ruleOverrideChanged: [field: 'durationSeconds' | 'passRatio' | 'passScore' | 'questionOrderMode' | 'multiSelectScoring'];
}>();
const { loadUserNames, userLabel } = useUserNameResolver();
/** 只读详情按负责人主键回读姓名，避免发布后仅保留不可读的内部标识。 */
const responsibleName = computed(() => (props.exam?.responsibleUserId ? userLabel(props.exam.responsibleUserId) : ''));
/** 题库练习的抽题规则在只读详情中逐条列出，说明本场考试的抽题来源与分值口径。 */
const drawRuleLines = computed(() => (props.exam?.examType === 'BANK_PRACTICE' ? (props.exam.drawRules ?? []) : []));
/** 抽题合计题数与分值，与编辑区保持同一口径。 */
const drawRuleSummary = computed(() => sumDrawRules(drawRuleLines.value));

/** 只读详情以考试查询响应的资源名称展示，关联为空或缺失时不回显内部 ID。 */
function paperVersionLabel(exam?: ExamItem): string {
  if (!exam?.paperName) return '未设置';
  return exam.paperVersionNo == null ? exam.paperName : `${exam.paperName}（版本 ${exam.paperVersionNo}）`;
}

/** 概览指标按考试类型收敛及格口径，题库练习不展示无效的及格分数。 */
const overviewMetrics = computed<Array<{ label: string; value: string; unit?: string; accent?: boolean }>>(() => {
  const current = props.exam;
  if (!current) return [];
  const metrics: Array<{ label: string; value: string; unit?: string; accent?: boolean }> = [
    { label: '考试时长', value: formatDuration(current.durationSeconds) }
  ];
  if (current.examType !== 'BANK_PRACTICE') {
    metrics.push({ label: '及格分数', value: current.passScore == null ? '未设置' : String(current.passScore), unit: '分', accent: true });
    metrics.push({ label: '及格比例', value: current.passRatio == null ? '未设置' : String(current.passRatio) });
  }
  metrics.push({ label: '学分', value: current.credit == null ? '未设置' : String(current.credit) });
  metrics.push({ label: '开放开始时间', value: current.openStartTime || '未设置' });
  metrics.push({ label: '开放结束时间', value: current.openEndTime || '未设置' });
  return metrics;
});

/** 考试分类使用题库分类名称展示。 */
function categoryLabel(categoryId?: number): string {
  if (!categoryId) return '未设置';
  return props.categoryOptions.find((item) => item.bankCategoryId === categoryId)?.categoryName ?? `分类 #${categoryId}`;
}

/** 题库字段使用接口返回的名称展示，避免在详情页暴露内部 ID。 */
function questionBankLabel(exam?: ExamItem): string {
  return exam?.questionBankName || '未设置';
}

watch(
  () => props.exam?.responsibleUserId,
  (responsibleUserId) => void loadUserNames([responsibleUserId], { organizationId: props.exam?.organizationId }),
  { immediate: true }
);

/** 选择新试卷版本后，以该版本已发布的规则作为考试表单默认值。 */
async function applyPaperVersionDefaults(paperVersionId?: number): Promise<void> {
  emit('paperVersionSelected', paperVersionId);
  if (!paperVersionId) return;
    const paperVersion = await getPaperVersion(paperVersionId);
    if (form.value.paperVersionId !== paperVersionId) return;
    form.value.durationSeconds = paperVersion.durationSeconds;
    form.value.passRatio = paperVersion.passRatio;
    form.value.passScore = paperVersion.passScore;
    form.value.questionOrderMode = paperVersion.questionOrderMode;
    form.value.multiSelectScoring = paperVersion.multiSelectScoring;

}
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card admin-detail-card-spacing">
    <div class="admin-detail-page__form-scroll">
      <!-- 已有考试（含已发布只读态）：概览卡给出考试身份、负责人与锁定时点。 -->
      <div v-if="exam" class="ws-hero exam-information__hero">
        <div class="ws-hero__main">
          <span class="ws-hero__eyebrow">EXAM DEFINITION · 考试定义</span>
          <div class="ws-hero__title-row">
            <h2 class="ws-hero__title">{{ exam.examName }}</h2>
            <ElTag :type="exam.publishStatus === 'PUBLISHED' ? 'success' : 'warning'" size="small" effect="light">
              {{ exam.publishStatus === 'PUBLISHED' ? '已发布' : '草稿' }}
            </ElTag>
          </div>
          <div class="ws-hero__meta">
            <span class="ws-hero__chip">考试编码 {{ exam.examCode }}</span>
            <span class="ws-hero__chip">{{ getExamEnumLabel(exam.examType, EXAM_TYPE_OPTIONS) }}</span>
            <span class="ws-hero__chip">{{ exam.planContentId == null ? '独立考试' : '培训生成' }}</span>
            <span class="ws-hero__chip">{{ categoryLabel(exam.examCategoryId) }}</span>
          </div>
        </div>
        <div class="ws-hero__aside">
          <div class="ws-hero__state">
            <span class="ws-kv__value">负责人：{{ responsibleName || '未设置' }}</span>
            <small>{{ exam.publishStatus === 'PUBLISHED' ? `发布时间 ${exam.publishedTime || '—'}` : '发布后考试定义与人员配置将锁定' }}</small>
          </div>
        </div>
      </div>
      <!-- 新增态：尚无考试身份，用引导卡说明当前步骤与后续依赖。 -->
      <div v-else-if="createMode" class="ws-hero exam-information__hero">
        <div class="ws-hero__main">
          <span class="ws-hero__eyebrow">NEW EXAM · 新建考试</span>
          <div class="ws-hero__title-row">
            <h2 class="ws-hero__title">填写考试信息</h2>
            <ElTag type="warning" size="small" effect="light">未保存</ElTag>
          </div>
          <div class="ws-hero__meta">
            <span>保存后生成考试草稿，才能继续配置考生范围、批阅人并发布考试。</span>
          </div>
        </div>
      </div>
      <!-- 只读态把关键数字从字段表中提出来，避免阅读时逐格查找。 -->
      <section v-if="!editing && overviewMetrics.length" class="ws-metrics exam-information__metrics">
        <div v-for="item in overviewMetrics" :key="item.label" class="ws-metric" :class="{ 'ws-metric--accent': item.accent }">
          <span class="ws-metric__label">{{ item.label }}</span>
          <span class="ws-metric__value">{{ item.value }}<em v-if="item.unit">{{ item.unit }}</em></span>
        </div>
      </section>
      <ExamDefinitionForm
        v-if="editing"
        v-model="form"
        :editing="!createMode"
        :create-mode="createMode"
        :paper-options="paperOptions"
        :category-options="props.categoryOptions"
        :question-bank-options="props.questionBankOptions"
        @paper-version-selected="applyPaperVersionDefaults"
        @rule-override-changed="emit('ruleOverrideChanged', $event)"
      />
      <ElDescriptions v-else :column="2" border class="exam-information" title="基础信息">
        <ElDescriptionsItem label="考试编码">{{ exam?.examCode }}</ElDescriptionsItem>
        <ElDescriptionsItem label="考试名称">{{ exam?.examName }}</ElDescriptionsItem>
        <ElDescriptionsItem label="考试类型">{{ getExamEnumLabel(exam?.examType, EXAM_TYPE_OPTIONS) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="试卷版本">{{ paperVersionLabel(exam) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="考试分类">{{ categoryLabel(exam?.examCategoryId) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="题库">{{ questionBankLabel(exam) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="考试负责人">{{ responsibleName || '未设置' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="考试来源">{{ exam?.planContentId == null ? '独立考试' : '培训生成' }}</ElDescriptionsItem>
      </ElDescriptions>
      <ElDescriptions v-if="!editing && exam" :column="2" border class="exam-information" title="考试规则与开放时间">
        <ElDescriptionsItem label="考试时长">{{ formatDuration(exam.durationSeconds) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="学分">{{ exam.credit }}</ElDescriptionsItem>
        <template v-if="exam.examType !== 'BANK_PRACTICE'">
          <ElDescriptionsItem label="及格比例">{{ exam.passRatio }}</ElDescriptionsItem>
          <ElDescriptionsItem label="及格分数">{{ exam.passScore }} 分</ElDescriptionsItem>
        </template>
        <ElDescriptionsItem label="题目顺序">{{ getExamEnumLabel(exam.questionOrderMode, QUESTION_ORDER_MODE_OPTIONS) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="多选题计分">{{ getExamEnumLabel(exam.multiSelectScoring, MULTI_SELECT_SCORING_OPTIONS) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="开放开始时间">{{ exam.openStartTime || '未设置' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="开放结束时间">{{ exam.openEndTime || '未设置' }}</ElDescriptionsItem>
      </ElDescriptions>
      <ElDescriptions v-if="!editing && exam" :column="2" border class="exam-information" title="考试须知与状态">
        <ElDescriptionsItem label="考试须知">{{ exam.isNoticeEnabled ? '已启用' : '未启用' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="启用状态">{{ exam.status ? '启用' : '停用' }}</ElDescriptionsItem>
        <ElDescriptionsItem v-if="exam.isNoticeEnabled" label="须知内容" :span="2">
          <div class="exam-information__notice">{{ exam.noticeContent || '未填写' }}</div>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="发布状态">{{ exam.publishStatus === 'PUBLISHED' ? '已发布' : '草稿' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="发布时间">{{ exam.publishedTime || '未发布' }}</ElDescriptionsItem>
      </ElDescriptions>
      <!-- 抽题规则条数不定，单独成卡避免撑破字段表格。 -->
      <section v-if="!editing && exam?.examType === 'BANK_PRACTICE'" class="ws-section">
        <div class="ws-section__head">
          <div>
            <h3>抽题规则</h3>
            <p>开考时按以下规则从题库抽取并冻结题目；客观题自动判分，简答题仅记录作答，不判定及格。</p>
          </div>
          <span v-if="drawRuleLines.length" class="ws-section__extra">共 {{ drawRuleLines.length }} 条 · 合计 {{ drawRuleSummary.count }} 题</span>
        </div>
        <div v-if="drawRuleLines.length" class="exam-information__notice">
          <p v-for="(rule, index) in drawRuleLines" :key="index">{{ index + 1 }}. {{ getExamDrawRuleText(rule) }}</p>
        </div>
        <span v-else class="ws-kv__value ws-kv__value--muted">未配置抽题规则</span>
      </section>
      <ExamPolicySummary v-if="exam" :exam="exam" />
    </div>
    <footer v-if="editing" class="admin-detail-action-bar">
      <ElButton v-if="createMode" @click="emit('cancel')">取消</ElButton>
      <!-- <ElButton :loading="saving" type="primary" @click="emit('save')">{{ createMode ? '创建考试' : '保存草稿' }}</ElButton> -->
      <ElButton @click="emit('next')">下一步：人员配置</ElButton>
    </footer>
  </ElCard>
</template>

<style scoped lang="less">
/* 概览卡与指标条在内容区内保持统一间距，与字段分组卡拉开层级。 */
.exam-information__hero,
.exam-information__metrics {
  margin-bottom: var(--space-4);
}

/* 只读字段按业务段落分组，圆角裁掉表格外框的直角并弱化分隔线。 */
.exam-information {
  border-radius: 10px;
  overflow: hidden;
}

.exam-information + .exam-information,
.exam-information + .ws-section,
.ws-section + .exam-information {
  margin-top: var(--space-4);
}

.exam-information :deep(.el-descriptions__header) {
  margin-bottom: 10px;
}

.exam-information :deep(.el-descriptions__title) {
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.exam-information :deep(.el-descriptions__label) {
  width: 124px;
  color: var(--color-text-secondary);
  font-weight: 600;
  background: var(--color-bg-subtle);
}

.exam-information :deep(.el-descriptions__content) {
  min-width: 180px;
  color: var(--color-text-primary);
}

.exam-information__notice {
  max-width: 100%;
  color: var(--color-text-regular);
  line-height: 1.8;
  white-space: pre-wrap;
}

.exam-information__notice p {
  margin: 0;
}
</style>
