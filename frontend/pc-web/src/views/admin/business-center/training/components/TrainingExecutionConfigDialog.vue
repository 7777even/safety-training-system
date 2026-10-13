<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { TrainingPlanContent, TrainingQuestionBankDrawRule } from '@/api/training-plan';
import { getQuestionPage, type Question } from '@/api/question';
import type { SystemUser } from '@/api/system-user';
import { DRAW_DIFFICULTY_OPTIONS, QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';
import { formatDuration } from '@/utils/duration';

const props = defineProps<{
  visible: boolean;
  content: TrainingPlanContent | undefined;
  teacherOptions: SystemUser[];
  readonly: boolean;
}>();
const emit = defineEmits<{
  'update:visible': [visible: boolean];
  confirm: [content: TrainingPlanContent];
}>();
const draft = ref<TrainingPlanContent>();
const questionPool = ref<Question[]>([]);
const questionPoolLoading = ref(false);
const questionPoolError = ref('');
let questionPoolRequestId = 0;
const durationHours = computed({
  get: () => Math.floor((draft.value?.executionConfig?.durationSeconds ?? 0) / 3600),
  set: (hours: number) => updateDuration(hours, durationMinutes.value, durationSeconds.value)
});
const durationMinutes = computed({
  get: () => Math.floor(((draft.value?.executionConfig?.durationSeconds ?? 0) % 3600) / 60),
  set: (minutes: number) => updateDuration(durationHours.value, minutes, durationSeconds.value)
});
const durationSeconds = computed({
  get: () => (draft.value?.executionConfig?.durationSeconds ?? 0) % 60,
  set: (seconds: number) => updateDuration(durationHours.value, durationMinutes.value, seconds)
});
const totalDrawCount = computed(() => (draft.value?.executionConfig?.drawRules ?? []).reduce((total, rule) => total + (rule.drawCount || 0), 0));
const availableQuestionCount = computed(() => new Set(questionPool.value.map((question) => question.questionId)).size);
const availableDrawCount = computed(() => {
  const rules = draft.value?.executionConfig?.drawRules ?? [];
  return new Set(
    rules.flatMap((rule) => questionPool.value.filter((question) => matchesRule(question, rule)).map((question) => question.questionId))
  ).size;
});
const teachingTimeRange = computed<[string, string], [string, string]>({
  get: (): [string, string] => [draft.value?.executionConfig?.startTime ?? '', draft.value?.executionConfig?.endTime ?? ''],
  set: (value: [string, string]) => {
    if (!draft.value) return;
    draft.value.executionConfig ??= {};
    draft.value.executionConfig.startTime = value[0] || undefined;
    draft.value.executionConfig.endTime = value[1] || undefined;
  }
});

watch(
  () => props.visible,
  (visible) => {
    if (!visible || !props.content) return;
    draft.value = JSON.parse(JSON.stringify(props.content)) as TrainingPlanContent;
    if (draft.value.contentType === 'COURSE') draft.value.executionConfig ??= { completionRule: 'ALL_REQUIRED_ITEMS' };
    if (draft.value.contentType === 'QUESTION_BANK') {
      draft.value.executionConfig ??= {};
      draft.value.executionConfig.durationSeconds ??= 1800;
      draft.value.executionConfig.maxAttempts ??= 3;
      draft.value.executionConfig.drawRules ??= [];
    }
    if (draft.value.contentType === 'OFFLINE') {
      draft.value.executionConfig ??= {};
      draft.value.executionConfig.passScore ??= 60;
    }
    if (draft.value.contentType === 'QUESTION_BANK') void loadQuestionPool(draft.value.resourceId);
  },
  { immediate: true }
);

/** 将界面上的时、分、秒合并为接口继续使用的秒数。 */
function updateDuration(hours: number, minutes: number, seconds: number): void {
  if (!draft.value) return;
  draft.value.executionConfig ??= {};
  draft.value.executionConfig.durationSeconds = Math.max(0, Math.floor(hours || 0) * 3600 + Math.floor(minutes || 0) * 60 + Math.floor(seconds || 0));
}

/** 读取当前题库的可用题目，避免凭空允许超过库存的抽题规则。 */
async function loadQuestionPool(questionBankId?: number): Promise<void> {
  const requestId = ++questionPoolRequestId;
  questionPool.value = [];
  questionPoolError.value = '';
  if (!questionBankId) {
    questionPoolError.value = '当前内容未绑定题库，暂时无法核对题量。';
    return;
  }
  questionPoolLoading.value = true;
  try {
    const pageSize = 100;
    const firstPage = await getQuestionPage({ pageNum: 1, pageSize, 'query.questionBankId': questionBankId, 'query.archiveStatus': 'ACTIVE', 'query.status': 1 });
    const pages = await Promise.all(
      Array.from({ length: Math.max(0, Math.ceil(firstPage.total / pageSize) - 1) }, (_, index) =>
        getQuestionPage({ pageNum: index + 2, pageSize, 'query.questionBankId': questionBankId, 'query.archiveStatus': 'ACTIVE', 'query.status': 1 })
      )
    );
    if (requestId !== questionPoolRequestId) return;
    questionPool.value = [firstPage, ...pages].flatMap((page) => page.list);
  } catch {
    if (requestId === questionPoolRequestId) questionPoolError.value = '题量读取失败，请稍后重试；保存时仍会校验抽题规则。';
  } finally {
    if (requestId === questionPoolRequestId) questionPoolLoading.value = false;
  }
}

/** 兼容题库练习难度文案与题目接口难度枚举的历史映射。 */
function normalizedDifficulty(difficulty?: string | null): string | undefined {
  if (!difficulty || difficulty === 'ALL') return undefined;
  if (difficulty === 'NORMAL') return 'MEDIUM';
  if (difficulty === 'VERY_HARD') return 'HARD';
  return difficulty;
}

/** 判断题目是否命中一条抽题规则。 */
function matchesRule(question: Question, rule: TrainingQuestionBankDrawRule): boolean {
  const version = question.currentVersion;
  return Boolean(version && version.questionType === rule.questionType && (!normalizedDifficulty(rule.difficulty) || version.difficulty === normalizedDifficulty(rule.difficulty)));
}

/** 返回某条规则当前能抽取的题数，用于输入上限和库存提示。 */
function availableCountForRule(rule: TrainingQuestionBankDrawRule): number {
  return questionPool.value.filter((question) => matchesRule(question, rule)).length;
}

function questionTypeLabel(questionType: string): string {
  return QUESTION_TYPE_OPTIONS.find((item) => item.value === questionType)?.label ?? questionType;
}

function issuesOf(content: TrainingPlanContent): string[] {
  const config = content.executionConfig;
  if (content.contentType === 'COURSE') return config?.completionRule ? [] : ['请选择课程完成规则'];
  if (content.contentType === 'EXAM') return content.assessmentMode ? [] : ['请选择考试模式'];
  if (content.contentType === 'QUESTION_BANK') {
    const issues: string[] = [];
    if (!config?.durationSeconds || config.durationSeconds <= 0) issues.push('请输入大于 0 的练习时长');
    if (!config?.maxAttempts || config.maxAttempts <= 0) issues.push('请输入大于 0 的最大作答次数');
    if (!config?.drawRules?.length) issues.push('请至少添加一条抽题规则');
    const unavailableRule = (config?.drawRules ?? []).find((rule) => rule.drawCount > availableCountForRule(rule));
    if (unavailableRule && !questionPoolError.value) issues.push(`“${questionTypeLabel(unavailableRule.questionType)}”可用题目不足，请减少抽题数`);
    if (!questionPoolError.value && totalDrawCount.value > availableDrawCount.value) issues.push('抽题总数超过当前题库可用题目数，请调整抽题规则');
    const overlappingRule = (config?.drawRules ?? []).find((rule, index, rules) => rules.some((other, otherIndex) => otherIndex < index && ruleConditionsOverlap(rule, other)));
    if (overlappingRule) issues.push('抽题规则不能配置重叠的题型和难度范围，请调整后再保存');
    return issues;
  }
  const issues: string[] = [];
  if (!config?.teacherUserId) issues.push('请选择授课教师');
  if (!config?.location?.trim()) issues.push('请输入培训地点');
  if (!config?.startTime || !config.endTime) issues.push('请选择授课时间范围');
  if (config?.passScore === undefined || config.passScore === null || config.passScore < 0) issues.push('请输入有效的及格分数');
  return issues;
}

function addDrawRule(): void {
  if (!draft.value) return;
  draft.value.executionConfig ??= {};
  const rules = (draft.value.executionConfig.drawRules ??= []);
  const nextRule = QUESTION_TYPE_OPTIONS.flatMap((questionType) => DRAW_DIFFICULTY_OPTIONS.map((difficulty) => ({ questionType: questionType.value, difficulty: difficulty.value })))
    .find((candidate) => !rules.some((rule) => ruleConditionsOverlap(rule, candidate)));
  if (!nextRule) {
    ElMessage.warning('已没有可新增的题型和难度组合');
    return;
  }
  rules.push({ ...nextRule, drawCount: 1, sortOrder: rules.length + 1 });
}

/** 将空难度与“不限”视为同一规则条件，避免产生重复抽题范围。 */
function normalizeRuleDifficulty(difficulty?: string | null): string {
  return difficulty || 'ALL';
}

/** 判断两条规则的题型和难度范围是否存在交集，避免同一题目被多个规则重复命中。 */
function ruleConditionsOverlap(
  first: Pick<TrainingQuestionBankDrawRule, 'questionType' | 'difficulty'>,
  second: Pick<TrainingQuestionBankDrawRule, 'questionType' | 'difficulty'>
): boolean {
  if (first.questionType !== second.questionType) return false;
  const firstDifficulty = normalizeRuleDifficulty(first.difficulty);
  const secondDifficulty = normalizeRuleDifficulty(second.difficulty);
  return firstDifficulty === 'ALL' || secondDifficulty === 'ALL' || firstDifficulty === secondDifficulty;
}

/** 判断当前行是否与其他行存在重叠，便于在表格内即时提示。 */
function hasOverlappingRule(index: number): boolean {
  const rules = draft.value?.executionConfig?.drawRules ?? [];
  return rules.some((rule, ruleIndex) => ruleIndex !== index && ruleConditionsOverlap(rule, rules[index]));
}

/** 禁用会与其他规则产生交集的题型或难度组合。 */
function isRuleCombinationTaken(index: number, questionType: TrainingQuestionBankDrawRule['questionType'], difficulty?: string | null): boolean {
  const rules = draft.value?.executionConfig?.drawRules ?? [];
  return rules.some(
    (rule, ruleIndex) => ruleIndex !== index && ruleConditionsOverlap(rule, { questionType, difficulty })
  );
}
function removeDrawRule(index: number): void {
  const rules = draft.value?.executionConfig?.drawRules;
  rules?.splice(index, 1);
  rules?.forEach((rule, ruleIndex) => (rule.sortOrder = ruleIndex + 1));
}
function titleOf(content?: TrainingPlanContent): string {
  return `${{ COURSE: '课程', EXAM: '考试', QUESTION_BANK: '题库练习', OFFLINE: '线下培训' }[content?.contentType ?? 'COURSE']}执行配置`;
}
function confirm(): void {
  if (!draft.value) return;
  const issues = issuesOf(draft.value);
  if (issues.length) {
    ElMessage.warning(issues[0]);
    return;
  }
  const result = JSON.parse(JSON.stringify(draft.value)) as TrainingPlanContent;
  if (result.contentType !== 'EXAM') result.assessmentMode = undefined;
  if (result.contentType === 'EXAM') result.executionConfig = undefined;
  if (result.contentType === 'QUESTION_BANK') {
    result.executionConfig = {
      durationSeconds: result.executionConfig?.durationSeconds,
      maxAttempts: result.executionConfig?.maxAttempts,
      drawRules: result.executionConfig?.drawRules?.map(({ scorePerQuestion, ...rule }, index) => {
        void scorePerQuestion;
        return { ...rule, sortOrder: index + 1 };
      })
    };
  }
  emit('confirm', result);
  emit('update:visible', false);
}
</script>

<template>
  <ElDialog
    :model-value="props.visible"
    :title="titleOf(draft)"
    width="760px"
    destroy-on-close
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <ElForm v-if="draft" label-position="top" class="execution-config-form">
      <ElAlert title="确认后还需保存阶段内容。" type="info" :closable="false" class="execution-config-alert" />
      <ElFormItem v-if="draft.contentType === 'COURSE'" label="课程完成规则" required>
        <ElSelect v-model="draft.executionConfig!.completionRule" :disabled="props.readonly" placeholder="请选择课程完成规则">
          <ElOption label="完成全部必修项" value="ALL_REQUIRED_ITEMS" />
          <ElOption label="完成全部内容项" value="ALL_ITEMS" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem v-else-if="draft.contentType === 'EXAM'" label="考试模式" required>
        <ElSelect v-model="draft.assessmentMode" :disabled="props.readonly" placeholder="请选择考试模式">
          <ElOption label="测验" value="QUIZ" />
          <ElOption label="正式考试" value="FORMAL_EXAM" />
        </ElSelect>
      </ElFormItem>
      <template v-else-if="draft.contentType === 'QUESTION_BANK'">
        <div class="config-grid">
          <ElFormItem label="练习时长" required>
            <div class="duration-inputs">
              <ElInputNumber v-model="durationHours" :disabled="props.readonly" :min="0" :precision="0" controls-position="right" />
              <span>时</span>
              <ElInputNumber v-model="durationMinutes" :disabled="props.readonly" :min="0" :max="59" :precision="0" controls-position="right" />
              <span>分</span>
              <ElInputNumber v-model="durationSeconds" :disabled="props.readonly" :min="0" :max="59" :precision="0" controls-position="right" />
              <span>秒</span>
            </div>
            <div class="field-hint">当前时长：{{ formatDuration(draft.executionConfig!.durationSeconds ?? 0) }}（保存后按 {{ draft.executionConfig!.durationSeconds ?? 0 }} 秒提交）</div>
          </ElFormItem>
          <ElFormItem label="最大作答次数" required>
            <ElInputNumber v-model="draft.executionConfig!.maxAttempts" :disabled="props.readonly" :min="1" :precision="0" />
          </ElFormItem>
        </div>
        <div class="draw-heading">
          <div>
            <strong>抽题规则</strong>
            <p v-if="questionPoolLoading">正在读取题库题量…</p>
            <p v-else-if="questionPoolError" class="warning-text">{{ questionPoolError }}</p>
            <p v-else>当前题库可用 {{ availableQuestionCount }} 题，已配置抽取 {{ totalDrawCount }} 题；简答题只记录作答，不评分、不批阅；重叠题型范围不能重复配置。</p>
          </div>
          <ElButton v-if="!props.readonly" type="primary" plain @click="addDrawRule">新增规则</ElButton>
        </div>
        <ElTable :data="draft.executionConfig!.drawRules ?? []" class="draw-table" border>
            <ElTableColumn min-width="120">
              <template #header>题型 <i class="required-asterisk" aria-hidden="true">*</i></template>
            <template #default="{ row, $index }">
              <ElSelect v-model="(row as TrainingQuestionBankDrawRule).questionType" :disabled="props.readonly" placeholder="请选择题型">
                <ElOption v-for="item in QUESTION_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" :disabled="isRuleCombinationTaken($index, item.value, (row as TrainingQuestionBankDrawRule).difficulty)" />
              </ElSelect>
            </template>
          </ElTableColumn>
            <ElTableColumn label="难度（可选）" min-width="120">
            <template #default="{ row, $index }">
              <ElSelect v-model="(row as TrainingQuestionBankDrawRule).difficulty" :disabled="props.readonly" clearable placeholder="请选择难度">
                <ElOption v-for="item in DRAW_DIFFICULTY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" :disabled="isRuleCombinationTaken($index, (row as TrainingQuestionBankDrawRule).questionType, item.value)" />
              </ElSelect>
            </template>
          </ElTableColumn>
          <ElTableColumn width="200">
              <template #header>抽题数 <i class="required-asterisk" aria-hidden="true">*</i></template>
            <template #default="{ row, $index }">
              <div class="draw-count-cell">
                <ElInputNumber v-model="(row as TrainingQuestionBankDrawRule).drawCount" :disabled="props.readonly" :min="1" :max="Math.max(1, availableCountForRule(row as TrainingQuestionBankDrawRule))" :precision="0" />
                <span class="rule-availability" :class="{ 'warning-text': (row as TrainingQuestionBankDrawRule).drawCount > availableCountForRule(row as TrainingQuestionBankDrawRule) || hasOverlappingRule($index) }">
                  可用 {{ availableCountForRule(row as TrainingQuestionBankDrawRule) }} 题<span v-if="hasOverlappingRule($index)"> · 题目范围重叠</span>
                </span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn v-if="!props.readonly" label="操作" width="72">
            <template #default="{ $index }"><ElButton link type="danger" @click="removeDrawRule($index)">删除</ElButton></template>
          </ElTableColumn>
          <template #empty><ElEmpty :image-size="64" description="暂无抽题规则，请点击右上角新增规则" /></template>
        </ElTable>
      </template>
      <template v-else>
        <div class="config-grid">
          <ElFormItem label="授课教师" required>
            <ElSelect v-model="draft.executionConfig!.teacherUserId" :disabled="props.readonly" filterable placeholder="请选择授课教师">
              <ElOption
                v-for="teacher in props.teacherOptions"
                :key="teacher.userId"
                :label="`${teacher.userName}（${teacher.userCode}）`"
                :value="teacher.userId"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="培训地点" required>
            <ElInput v-model="draft.executionConfig!.location" :disabled="props.readonly" placeholder="请输入培训地点" />
          </ElFormItem>
          <ElFormItem label="授课时间范围" required>
            <ElDatePicker
              v-model="teachingTimeRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              :disabled="props.readonly"
            />
          </ElFormItem>
          <ElFormItem label="及格分数" required>
            <ElInputNumber v-model="draft.executionConfig!.passScore" :disabled="props.readonly" :min="0" :precision="1" />
          </ElFormItem>
        </div>
      </template>
    </ElForm>
    <template #footer>
      <ElButton @click="emit('update:visible', false)">{{ props.readonly ? '关闭' : '取消' }}</ElButton>
      <ElButton v-if="!props.readonly" type="primary" @click="confirm">确认配置</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="less">
.required-asterisk {
  color: var(--el-color-danger);
  font-style: normal;
  font-weight: 600;
}
.execution-config-alert {
  margin-bottom: 16px;
}
.execution-config-form :deep(.el-select),
.execution-config-form :deep(.el-input-number),
.execution-config-form :deep(.el-date-editor) {
  width: 100%;
}
.duration-inputs {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.duration-inputs :deep(.el-input-number) {
  flex: 1;
  min-width: 0;
}
.field-hint,
.rule-availability {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}
.warning-text {
  color: var(--el-color-danger) !important;
}
.rule-availability {
  text-align: center;
  white-space: nowrap;
}
.draw-count-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.draw-count-cell :deep(.el-input-number) {
  flex: 1;
  min-width: 0;
}
.draw-table :deep(.el-table__empty-block) {
  min-height: 150px;
}
.draw-table :deep(.el-table__empty-text) {
  width: 100%;
}
.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}
.draw-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin: 4px 0 12px;
}
.draw-heading p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}
@media (max-width: 760px) {
  .config-grid {
    grid-template-columns: 1fr;
  }
  .duration-inputs {
    gap: 4px;
  }
}
</style>
