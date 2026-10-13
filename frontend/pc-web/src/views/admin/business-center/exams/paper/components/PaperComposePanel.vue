<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { KnowledgePoint, Question, QuestionBank, QuestionType } from '@/api/question';
import type { PaperDrawRulePayload, PaperPreview, PaperQuestionPayload, PaperType } from '@/api/paper';
import {
  DRAW_DIFFICULTY_OPTIONS,
  getExamEnumLabel,
  MULTI_SELECT_SCORING_OPTIONS,
  PAPER_TYPE_OPTIONS,
  QUESTION_ORDER_MODE_OPTIONS,
  QUESTION_TYPE_OPTIONS
} from '@/constants/exam-options';
import DurationInput from '@/components/duration/DurationInput.vue';
import QuestionBankPickerDialog from './QuestionBankPickerDialog.vue';
import PaperPreviewDialog from './PaperPreviewDialog.vue';

type PaperComposeForm = {
  durationSeconds: number;
  passRatio: number;
  questionOrderMode: string;
  multiSelectScoring: string;
  versionNote: string;
  randomSeed?: number;
  questions: PaperQuestionPayload[];
  drawRules: PaperDrawRulePayload[];
};
const props = defineProps<{
  paperType: PaperType;
  paperCategoryId?: number;
  composing: boolean;
  totalScore: number;
  passScore: number;
  questionBanks: QuestionBank[];
  knowledgePoints: KnowledgePoint[];
  questionDetails: Record<number, Question>;
  preview?: PaperPreview;
}>();
const compose = defineModel<PaperComposeForm>('compose', { required: true });
const previewVisible = defineModel<boolean>('previewVisible', { required: true });
const emit = defineEmits<{
  invalidatePreview: [];
  questionsSelected: [questions: Question[]];
  addRule: [];
  save: [];
  preview: [];
  previewAndPublish: [];
}>();
const questionPickerVisible = ref(false);
const selectedQuestionIds = computed(() => compose.value.questions.map((item) => item.questionId));
const paperTypeLabel = computed(() => getExamEnumLabel(props.paperType, PAPER_TYPE_OPTIONS));
/** 抽题规则合计抽题数与合计分值，便于管理员核对总分来源。 */
const drawRuleSummary = computed(() =>
  compose.value.drawRules.reduce(
    (summary, rule) => ({
      count: summary.count + (rule.drawCount || 0),
      score: summary.score + (rule.drawCount || 0) * (rule.scorePerQuestion || 0)
    }),
    { count: 0, score: 0 }
  )
);

/** 随机种子说明与 Contract 一致：相同种子配合相同抽题规则可复现同一套题目。 */
const RANDOM_SEED_TIP = '随机卷抽题种子：相同种子配合相同抽题规则可以复现同一套题目，用于复核与追溯；固定试卷无需填写。';
const BANK_WEIGHT_TIP = '题库抽题权重：同一题型存在多条抽题规则时，按权重比例分配抽题数量。';

/** 由已选题目快照提供编码、题干和题型，避免在展示层重复发起题目请求。 */
function selectedQuestion(questionId: number): Question | undefined {
  return props.questionDetails[questionId];
}

/** 选项题与主观题只影响列表展示提示，不改动组卷数据结构。 */
function questionTypeLabel(questionId: number): string {
  return getExamEnumLabel(selectedQuestion(questionId)?.currentVersion?.questionType as QuestionType, QUESTION_TYPE_OPTIONS);
}

function ruleBankName(questionBankId?: number): string {
  return props.questionBanks.find((bank) => bank.questionBankId === questionBankId)?.bankName ?? '未选择题库';
}

/** 交换相邻题目后重排序号，保证 sortOrder 始终从 1 连续排列。 */
function moveQuestion(index: number, offset: number): void {
  const target = index + offset;
  if (target < 0 || target >= compose.value.questions.length) return;
  const list = compose.value.questions;
  [list[index], list[target]] = [list[target], list[index]];
  list.forEach((item, itemIndex) => (item.sortOrder = itemIndex + 1));
  emit('invalidatePreview');
}

function removeQuestion(index: number): void {
  compose.value.questions.splice(index, 1);
  compose.value.questions.forEach((item, itemIndex) => (item.sortOrder = itemIndex + 1));
  emit('invalidatePreview');
}

/** 交换相邻抽题规则后重排序号，避免出现跳号导致的抽题顺序歧义。 */
function moveRule(index: number, offset: number): void {
  const target = index + offset;
  if (target < 0 || target >= compose.value.drawRules.length) return;
  const list = compose.value.drawRules;
  [list[index], list[target]] = [list[target], list[index]];
  list.forEach((item, itemIndex) => (item.sortOrder = itemIndex + 1));
  emit('invalidatePreview');
}

function removeRule(index: number): void {
  compose.value.drawRules.splice(index, 1);
  compose.value.drawRules.forEach((item, itemIndex) => (item.sortOrder = itemIndex + 1));
  emit('invalidatePreview');
}

/** 抽题规则必须选择题库，避免提交未定义字段导致服务端校验失败。 */
function ensureRulesReady(): boolean {
  const invalidIndex = compose.value.drawRules.findIndex((rule) => !rule.questionBankId);
  if (invalidIndex >= 0) {
    ElMessage.warning(`请为抽题规则 ${invalidIndex + 1} 选择题库`);
    return false;
  }
  return true;
}

function handleSave(): void {
  if (!ensureRulesReady()) return;
  emit('save');
}

/** 预览与发布前要求固定题目或抽题规则至少有一项，避免生成空试卷版本。 */
function ensurePreviewable(): boolean {
  if (!ensureRulesReady()) return false;
  if (props.paperType !== 'RANDOM' && !compose.value.questions.length) {
    ElMessage.warning('请先添加试卷题目');
    return false;
  }
  if (props.paperType !== 'FIXED' && !compose.value.drawRules.length) {
    ElMessage.warning('请先新增随机抽题规则');
    return false;
  }
  return true;
}

function handlePreview(): void {
  if (!ensurePreviewable()) return;
  emit('preview');
}

function handlePreviewAndPublish(): void {
  if (!ensurePreviewable()) return;
  emit('previewAndPublish');
}
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card admin-detail-card-spacing compose-panel">
    <template #header>
      <div class="compose-header">
        <div>
          <strong>组卷配置</strong>
          <span>按题型与分值组织题目，保存后可在预览中确认最终试卷</span>
        </div>
        <ElTag type="info" effect="plain">{{ paperTypeLabel }}</ElTag>
      </div>
    </template>

    <div class="admin-detail-page__form-scroll compose-body">
      <div class="score-board">
        <div class="score-card">
          <span>题目数</span>
          <b>
            {{ compose.questions.length }}
            <small v-if="paperType !== 'FIXED'">+ {{ drawRuleSummary.count }} 抽</small>
          </b>
        </div>
        <div class="score-card">
          <span>总分</span>
          <b>{{ totalScore }}</b>
        </div>
        <div class="score-card">
          <span>及格比例</span>
          <b>{{ Math.round(compose.passRatio * 100) }}%</b>
        </div>
        <div class="score-card score-card--highlight">
          <span>及格分</span>
          <b>{{ passScore.toFixed(1) }}</b>
        </div>
      </div>

      <section class="form-section">
        <header class="form-section__header">
          <strong>考试设置</strong>
          <span>考试时长、题目顺序与计分规则</span>
        </header>
        <div class="form-section__body">
          <ElForm label-position="top" class="setting-grid">
            <ElFormItem label="考试时长">
              <DurationInput v-model="compose.durationSeconds" @change="emit('invalidatePreview')" />
            </ElFormItem>
            <ElFormItem label="及格比例">
              <ElInputNumber v-model="compose.passRatio" :min="0" :max="1" :step="0.05" @change="emit('invalidatePreview')" />
              <p class="field-hint">按总分比例计算及格分</p>
            </ElFormItem>
            <ElFormItem label="题目顺序">
              <ElSelect v-model="compose.questionOrderMode" placeholder="请选择题目顺序" @change="emit('invalidatePreview')">
                <ElOption v-for="item in QUESTION_ORDER_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="多选题计分">
              <ElSelect v-model="compose.multiSelectScoring" placeholder="请选择多选题计分方式" @change="emit('invalidatePreview')">
                <ElOption v-for="item in MULTI_SELECT_SCORING_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="paperType !== 'FIXED'">
              <template #label>
                <span class="label-with-help">
                  随机种子
                  <ElTooltip :content="RANDOM_SEED_TIP" placement="top">
                    <span class="field-help">?</span>
                  </ElTooltip>
                </span>
              </template>
              <ElInputNumber v-model="compose.randomSeed" :min="0" placeholder="留空则每次抽题随机" @change="emit('invalidatePreview')" />
            </ElFormItem>
            <ElFormItem label="版本说明" class="setting-grid__wide">
              <ElInput v-model="compose.versionNote" placeholder="请输入版本说明，便于区分多次组卷结果" @input="emit('invalidatePreview')" />
            </ElFormItem>
          </ElForm>
        </div>
      </section>

      <section v-if="paperType !== 'RANDOM'" class="form-section">
        <header class="form-section__header">
          <strong>试卷题目</strong>
          <div class="section-actions">
            <span>共 {{ compose.questions.length }} 题 · 合计 {{ compose.questions.reduce((total, item) => total + item.score, 0) }} 分</span>
            <ElButton type="primary" plain @click="questionPickerVisible = true">从题库添加题目</ElButton>
          </div>
        </header>
        <div class="form-section__body">
          <ElTable :data="compose.questions" class="selected-table" empty-text="尚未添加题目">
            <ElTableColumn label="序号" width="64" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </ElTableColumn>
            <ElTableColumn label="题目编码" width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ selectedQuestion(row.questionId)?.questionCode ?? `#${row.questionId}` }}</template>
            </ElTableColumn>
            <ElTableColumn label="题干" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ selectedQuestion(row.questionId)?.currentVersion?.stem ?? '加载中' }}</template>
            </ElTableColumn>
            <ElTableColumn label="题型" width="100">
              <template #default="{ row }">{{ questionTypeLabel(row.questionId) }}</template>
            </ElTableColumn>
            <ElTableColumn label="分值" width="140">
              <template #default="{ row }">
                <ElInputNumber v-model="row.score" :min="0.5" :step="0.5" size="small" @change="emit('invalidatePreview')" />
              </template>
            </ElTableColumn>
            <ElTableColumn label="排序" width="150" align="center">
              <template #default="{ $index }">
                <ElButton link :disabled="$index === 0" @click="moveQuestion($index, -1)">上移</ElButton>
                <ElButton link :disabled="$index === compose.questions.length - 1" @click="moveQuestion($index, 1)">下移</ElButton>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="80" align="center">
              <template #default="{ $index }">
                <ElButton link type="danger" @click="removeQuestion($index)">删除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
      </section>

      <section v-if="paperType !== 'FIXED'" class="form-section">
        <header class="form-section__header">
          <strong>随机抽题规则</strong>
          <span>{{ compose.drawRules.length }} 条规则 · 合计抽取 {{ drawRuleSummary.count }} 题 / {{ drawRuleSummary.score }} 分</span>
        </header>
        <div class="form-section__body">
          <div class="section-actions">
            <ElButton type="primary" plain @click="emit('addRule')">新增抽题规则</ElButton>
          </div>
          <div class="rule-list">
            <article v-for="(rule, index) in compose.drawRules" :key="rule.sortOrder" class="rule-card">
              <header class="rule-card__head">
                <div class="rule-card__title">
                  <span class="rule-card__index">{{ index + 1 }}</span>
                  <strong>{{ ruleBankName(rule.questionBankId) }}</strong>
                  <small>
                    {{ rule.drawCount || 0 }} 题 × {{ rule.scorePerQuestion || 0 }} 分 = {{ (rule.drawCount || 0) * (rule.scorePerQuestion || 0) }} 分
                  </small>
                </div>
                <div class="rule-card__ops">
                  <ElButton link :disabled="index === 0" @click="moveRule(index, -1)">上移</ElButton>
                  <ElButton link :disabled="index === compose.drawRules.length - 1" @click="moveRule(index, 1)">下移</ElButton>
                  <ElButton link type="danger" @click="removeRule(index)">删除</ElButton>
                </div>
              </header>
              <ElForm label-position="top" class="rule-grid">
                <ElFormItem label="题库" required>
                  <ElSelect v-model="rule.questionBankId" placeholder="请选择题库" filterable @change="emit('invalidatePreview')">
                    <ElOption v-for="bank in questionBanks" :key="bank.questionBankId" :label="bank.bankName" :value="bank.questionBankId" />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="题型" required>
                  <ElSelect v-model="rule.questionType" placeholder="请选择题型" @change="emit('invalidatePreview')">
                    <ElOption v-for="item in QUESTION_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="难度">
                  <ElSelect v-model="rule.difficulty" clearable placeholder="不限难度" @change="emit('invalidatePreview')">
                    <ElOption v-for="item in DRAW_DIFFICULTY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="知识点">
                  <ElTreeSelect
                    v-model="rule.knowledgePointId"
                    :data="knowledgePoints"
                    node-key="knowledgePointId"
                    clearable
                    check-strictly
                    :render-after-expand="false"
                    :props="{ label: 'knowledgeName', children: 'children' }"
                    placeholder="不限知识点"
                    @change="emit('invalidatePreview')"
                  />
                </ElFormItem>
                <ElFormItem label="抽题数量" required>
                  <ElInputNumber v-model="rule.drawCount" :min="1" @change="emit('invalidatePreview')" />
                </ElFormItem>
                <ElFormItem label="每题分值" required>
                  <ElInputNumber v-model="rule.scorePerQuestion" :min="0.5" :step="0.5" @change="emit('invalidatePreview')" />
                </ElFormItem>
                <ElFormItem>
                  <template #label>
                    <span class="label-with-help">
                      题库权重
                      <ElTooltip :content="BANK_WEIGHT_TIP" placement="top">
                        <span class="field-help">?</span>
                      </ElTooltip>
                    </span>
                  </template>
                  <ElInputNumber v-model="rule.bankWeight" :min="0" :step="0.5" @change="emit('invalidatePreview')" />
                </ElFormItem>
              </ElForm>
            </article>
            <ElEmpty v-if="!compose.drawRules.length" description="尚未配置抽题规则，随机试卷需要在发布前至少配置一条" :image-size="72" />
          </div>
        </div>
      </section>
    </div>

    <footer class="admin-detail-action-bar">
      <ElButton :loading="composing" @click="handleSave">保存组卷草稿</ElButton>
      <ElButton @click="handlePreview">预览试卷</ElButton>
      <ElButton type="primary" @click="handlePreviewAndPublish">预览并进入发布</ElButton>
    </footer>

    <QuestionBankPickerDialog
      v-model="questionPickerVisible"
      :selected-question-ids="selectedQuestionIds"
      :default-bank-category-id="paperCategoryId"
      @confirm="emit('questionsSelected', $event)"
    />
    <PaperPreviewDialog
      v-model="previewVisible"
      :preview="preview"
      :draw-rules="compose.drawRules"
      :question-details="questionDetails"
      :question-banks="questionBanks"
    />
  </ElCard>
</template>

<style scoped lang="less">
.compose-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.compose-header strong {
  display: block;
  margin-bottom: 4px;
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: 600;
}

.compose-header span {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.compose-body {
  padding: var(--space-5) var(--space-6);
}

/* 分数总览用等宽数据卡呈现，避免总分与及格分埋在长表单里。 */
.score-board {
  display: grid;
  margin-bottom: var(--space-5);
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.score-card {
  display: grid;
  gap: 6px;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-subtle);
}

.score-card span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.score-card b {
  color: var(--color-text-primary);
  font-size: 22px;
  font-weight: 700;
  line-height: 26px;
}

.score-card b small {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 400;
}

.score-card--highlight {
  border-color: #bfdbfe;
  background: var(--color-primary-subtle);
}

.score-card--highlight b {
  color: var(--color-primary);
}

.form-section {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
}

.form-section + .form-section {
  margin-top: var(--space-4);
}

.form-section__header {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
  border-radius: var(--radius-card) var(--radius-card) 0 0;
  background: var(--color-bg-subtle);
}

.form-section__header strong {
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.form-section__header span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.form-section__body {
  padding: var(--space-4);
}

/* 考试设置按卡片栅格排布，替代原先每个配置项独占一整行的长条布局。 */
.setting-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0 var(--space-4);
}

.setting-grid :deep(.el-form-item) {
  margin-bottom: var(--space-3);
}

.setting-grid :deep(.el-input-number),
.setting-grid :deep(.el-select) {
  width: 100%;
}

.setting-grid__wide {
  grid-column: 1 / -1;
}

.label-with-help {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.field-help {
  display: inline-grid;
  width: 15px;
  height: 15px;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  cursor: help;
  font-size: 11px;
  line-height: 1;
}

.field-hint {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.selected-table {
  margin-top: var(--space-2);
}

.selected-table :deep(.el-input-number) {
  width: 112px;
}

/* 抽题规则改为卡片编排：一条规则一张卡，字段按栅格排布，避免横向超宽表格。 */
.rule-list {
  display: grid;
  margin-top: var(--space-3);
  gap: var(--space-3);
}

.rule-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  overflow: hidden;
}

.rule-card__head {
  display: flex;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
}

.rule-card__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-3);
}

.rule-card__index {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.rule-card__title strong {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rule-card__title small {
  flex: 0 0 auto;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.rule-card__ops {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.rule-grid {
  display: grid;
  padding: var(--space-4) var(--space-4) var(--space-2);
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 0 var(--space-4);
}

.rule-grid :deep(.el-form-item) {
  margin-bottom: var(--space-3);
}

.rule-grid :deep(.el-input-number),
.rule-grid :deep(.el-select),
.rule-grid :deep(.el-tree-select) {
  width: 100%;
}

@media (max-width: 1100px) {
  .score-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
