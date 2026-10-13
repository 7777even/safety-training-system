<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { ExamDrawRulePayload } from '@/api/exam';
import { getKnowledgePointTree, getQuestionPage, type KnowledgePoint, type Question } from '@/api/question';
import { DRAW_DIFFICULTY_OPTIONS, QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';

const props = defineProps<{ visible: boolean; questionBankId?: number; rules: ExamDrawRulePayload[] }>();
const emit = defineEmits<{ 'update:visible': [visible: boolean]; confirm: [rules: ExamDrawRulePayload[]] }>();

const draftRules = ref<ExamDrawRulePayload[]>([]);
const questionPool = ref<Question[]>([]);
const knowledgePoints = ref<KnowledgePoint[]>([]);
const loading = ref(false);
const loadError = ref('');
const questionPoolLoaded = ref(false);
let requestId = 0;

/** 规则编辑使用草稿，取消时不会改写表单内已经确认的抽题规则。 */
watch(
  () => props.visible,
  (visible) => {
    if (!visible) return;
    draftRules.value = JSON.parse(JSON.stringify(props.rules ?? [])) as ExamDrawRulePayload[];
    void loadQuestionPool();
    void loadKnowledgePoints();
  }
);

/** 题库改选后重新读取候选，避免沿用上一题库的题量。 */
watch(
  () => props.questionBankId,
  () => {
    if (props.visible) void loadQuestionPool();
  }
);

/** 读取与真实抽题一致的启用、未归档、已发布题目。 */
async function loadQuestionPool(): Promise<void> {
  const currentRequestId = ++requestId;
  questionPool.value = [];
  loadError.value = '';
  questionPoolLoaded.value = false;
  if (!props.questionBankId) {
    loadError.value = '请先选择题库，再配置抽题规则。';
    return;
  }
  loading.value = true;
  try {
    const pageSize = 100;
    const firstPage = await getQuestionPage({
      pageNum: 1,
      pageSize,
      'query.questionBankId': props.questionBankId,
      'query.archiveStatus': 'ACTIVE',
      'query.publishedStatus': 'PUBLISHED',
      'query.status': 1
    });
    const pages = await Promise.all(
      Array.from({ length: Math.max(0, Math.ceil(firstPage.total / pageSize) - 1) }, (_, index) =>
        getQuestionPage({
          pageNum: index + 2,
          pageSize,
          'query.questionBankId': props.questionBankId,
          'query.archiveStatus': 'ACTIVE',
          'query.publishedStatus': 'PUBLISHED',
          'query.status': 1
        })
      )
    );
    if (currentRequestId === requestId) {
      questionPool.value = [firstPage, ...pages].flatMap((page) => page.list);
      questionPoolLoaded.value = true;
    }
  } catch {
    if (currentRequestId === requestId) loadError.value = '题量读取失败，请稍后重试；保存时服务端仍会校验抽题边界。';
  } finally {
    if (currentRequestId === requestId) loading.value = false;
  }
}

/** 使用既有知识点树选择器，禁止用户手工输入外键 ID。 */
async function loadKnowledgePoints(): Promise<void> {
  if (knowledgePoints.value.length) return;
  try {
    knowledgePoints.value = await getKnowledgePointTree();
  } catch {
    knowledgePoints.value = [];
  }
}

/** 兼容抽题规则历史难度值与题目接口枚举。 */
function normalizedDifficulty(difficulty?: string): string | undefined {
  if (!difficulty || difficulty === 'ALL') return undefined;
  if (difficulty === 'NORMAL') return 'MEDIUM';
  if (difficulty === 'VERY_HARD') return 'HARD';
  return difficulty;
}

/** 判断题目是否会被某条规则命中，口径与公共抽题引擎一致。 */
function matchesRule(question: Question, rule: ExamDrawRulePayload): boolean {
  const version = question.currentVersion;
  return Boolean(
    version &&
    version.questionType === rule.questionType &&
    (!normalizedDifficulty(rule.difficulty) || version.difficulty === normalizedDifficulty(rule.difficulty)) &&
    (!rule.knowledgePointId || question.knowledgePointIds?.includes(rule.knowledgePointId))
  );
}

const totalQuestionCount = computed(() => new Set(questionPool.value.map((question) => question.questionId)).size);
/** 候选池完整加载前不设置数量上限，避免组件将已确认的抽题数自动钳制为 1。 */
const questionPoolReady = computed(() => questionPoolLoaded.value && !loadError.value);
const questionTypeCounts = computed(() =>
  QUESTION_TYPE_OPTIONS.map((option) => ({
    ...option,
    count: questionPool.value.filter((question) => question.currentVersion?.questionType === option.value).length
  }))
);
const totalDrawCount = computed(() => draftRules.value.reduce((total, rule) => total + (rule.drawCount || 0), 0));
const unionAvailableCount = computed(
  () =>
    new Set(
      draftRules.value.flatMap((rule) => questionPool.value.filter((question) => matchesRule(question, rule)).map((question) => question.questionId))
    ).size
);

/** 当前规则可命中的候选数，直接约束单条抽题上限。 */
function availableCountForRule(rule: ExamDrawRulePayload): number {
  return questionPool.value.filter((question) => matchesRule(question, rule)).length;
}

/** 两条规则若命中同一候选题，必须阻止，避免同一题被重复抽取。 */
function hasOverlappingRule(index: number): boolean {
  const currentIds = new Set(
    questionPool.value.filter((question) => matchesRule(question, draftRules.value[index])).map((question) => question.questionId)
  );
  return draftRules.value.some(
    (rule, ruleIndex) =>
      ruleIndex !== index && questionPool.value.some((question) => currentIds.has(question.questionId) && matchesRule(question, rule))
  );
}

/** 为新增规则选取不与当前规则重叠的默认题型/难度组合。 */
function addDrawRule(): void {
  const candidate = QUESTION_TYPE_OPTIONS.flatMap((questionType) =>
    DRAW_DIFFICULTY_OPTIONS.map((difficulty) => ({ questionType: questionType.value, difficulty: difficulty.value }))
  ).find(
    (item) =>
      !draftRules.value.some(
        (rule) =>
          rule.questionType === item.questionType &&
          (!normalizedDifficulty(rule.difficulty) ||
            !normalizedDifficulty(item.difficulty) ||
            normalizedDifficulty(rule.difficulty) === normalizedDifficulty(item.difficulty))
      )
  );
  if (!candidate) {
    ElMessage.warning('已没有可新增且不重叠的题型和难度范围');
    return;
  }
  draftRules.value.push({ ...candidate, drawCount: 1, sortOrder: draftRules.value.length + 1 });
}

function removeDrawRule(index: number): void {
  draftRules.value.splice(index, 1);
  draftRules.value.forEach((rule, ruleIndex) => (rule.sortOrder = ruleIndex + 1));
}

/** 提交前同时校验单条库存、规则交集和全局唯一候选池，阻止超抽与重抽。 */
function confirm(): void {
  if (!draftRules.value.length) {
    ElMessage.warning('请至少添加一条抽题规则');
    return;
  }
  if (!loadError.value && !loading.value) {
    const invalidRule = draftRules.value.find((rule, index) => rule.drawCount > availableCountForRule(rule) || hasOverlappingRule(index));
    if (invalidRule) {
      ElMessage.warning('存在候选不足或范围重叠的抽题规则，请调整后再确认');
      return;
    }
    if (totalDrawCount.value > unionAvailableCount.value) {
      ElMessage.warning('抽题总数超过全部规则可唯一抽取的题目数，请减少抽题数量');
      return;
    }
  }
  emit(
    'confirm',
    draftRules.value.map((rule, index) => ({ ...rule, sortOrder: index + 1 }))
  );
  emit('update:visible', false);
}
</script>

<template>
  <ElDialog
    :model-value="props.visible"
    title="配置抽题规则"
    width="900px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <ElAlert title="按当前题库的启用、未归档、已发布题目核对候选范围；同一题不会被多条规则重复抽取。" type="info" :closable="false" />
    <div class="pool-summary">
      <span v-if="loading">正在读取题库题量…</span>
      <span v-else-if="loadError" class="warning-text">{{ loadError }}</span>
      <template v-else>
        <strong>当前题库可抽 {{ totalQuestionCount }} 题</strong>
        <span v-for="item in questionTypeCounts" :key="item.value">{{ item.label }} {{ item.count }} 题</span>
      </template>
    </div>
    <div class="draw-heading">
      <span>
        {{ draftRules.length }} 条规则 · 抽取 {{ totalDrawCount }} 题
        <template v-if="!loadError && !loading">· 可唯一抽取 {{ unionAvailableCount }} 题</template>
      </span>
      <ElButton type="primary" plain @click="addDrawRule">新增规则</ElButton>
    </div>
    <ElTable :data="draftRules" border class="draw-table">
      <ElTableColumn label="题型" min-width="130">
        <template #default="{ row }">
          <ElSelect v-model="(row as ExamDrawRulePayload).questionType" placeholder="请选择题型">
            <ElOption v-for="item in QUESTION_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </template>
      </ElTableColumn>
      <ElTableColumn label="难度" min-width="130">
        <template #default="{ row }">
          <ElSelect v-model="(row as ExamDrawRulePayload).difficulty" clearable placeholder="不限难度">
            <ElOption v-for="item in DRAW_DIFFICULTY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </template>
      </ElTableColumn>
      <ElTableColumn label="知识点" min-width="150">
        <template #default="{ row }">
          <ElTreeSelect
            v-model="(row as ExamDrawRulePayload).knowledgePointId"
            :data="knowledgePoints"
            node-key="knowledgePointId"
            clearable
            check-strictly
            :render-after-expand="false"
            :props="{ label: 'knowledgeName', children: 'children' }"
            placeholder="不限知识点"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="抽题数" min-width="120">
        <template #default="{ row, $index }">
          <div class="draw-count">
            <ElInputNumber
              v-model="(row as ExamDrawRulePayload).drawCount"
              :min="1"
                  :max="questionPoolReady ? availableCountForRule(row as ExamDrawRulePayload) : undefined"
              :precision="0"
            />
            <span
              :class="{
                'warning-text':
                  (row as ExamDrawRulePayload).drawCount > availableCountForRule(row as ExamDrawRulePayload) || hasOverlappingRule($index)
              }"
            >
              可用 {{ availableCountForRule(row as ExamDrawRulePayload) }} 题
              <span v-if="hasOverlappingRule($index)">· 范围重叠</span>
            </span>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="72">
        <template #default="{ $index }"><ElButton link type="danger" @click="removeDrawRule($index)">删除</ElButton></template>
      </ElTableColumn>
      <template #empty><ElEmpty :image-size="64" description="暂无抽题规则，请点击右上角新增规则" /></template>
    </ElTable>
    <template #footer>
      <ElButton @click="emit('update:visible', false)">取消</ElButton>
      <ElButton type="primary" @click="confirm">确认配置</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="less">
.pool-summary,
.draw-heading,
.draw-count {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pool-summary {
  flex-wrap: wrap;
  margin: 16px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.draw-heading {
  justify-content: space-between;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.draw-count {
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
}
.draw-count span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.warning-text {
  color: var(--el-color-danger) !important;
}
.draw-table :deep(.el-input-number),
.draw-table :deep(.el-select),
.draw-table :deep(.el-tree-select) {
  width: 100%;
}
</style>
