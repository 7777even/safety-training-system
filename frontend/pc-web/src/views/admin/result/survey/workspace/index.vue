<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft, Bottom, CirclePlus, Delete, Top } from '@element-plus/icons-vue';
import {
  copySurvey,
  getSurvey,
  previewSurvey,
  replaceSurveyQuestions,
  saveSurvey,
  updateSurvey,
  type Survey,
  type SurveyQuestion
} from '@/api/m4-outcome';

const route = useRoute();
const router = useRouter();
const surveyId = computed(() => Number(route.params.surveyId) || undefined);
const detail = ref<Survey>();
/** 已投放问卷由后端冻结，工作台只允许查看和复制。 */
const frozen = computed(() => detail.value?.contentStatus === 'PUBLISHED');
const active = ref<'edit' | 'preview'>('edit');
const previewClient = ref<'PC' | 'MOBILE'>('PC');
const preview = ref<Pick<Survey, 'surveyId' | 'surveyName' | 'surveyType' | 'questions'>>();
const saving = ref(false);
/** 用途展示与投放端保持同一套中文口径，不向界面暴露英文编码。 */
const SURVEY_TYPE_LABELS: Record<Survey['surveyType'], string> = {
  TRAINING: '线下培训调查',
  ORGANIZATION_EVALUATION: '培训机构评价'
};
const form = reactive({
  surveyCode: '',
  surveyName: '',
  surveyType: 'TRAINING' as 'TRAINING' | 'ORGANIZATION_EVALUATION',
  description: '',
  status: 1 as 0 | 1
});
const questions = ref<SurveyQuestion[]>([]);
/** 单选与多选题至少保留两个选项，与后端保存校验一致。 */
const MIN_OPTION_COUNT = 2;
function addQuestion(): void {
  questions.value.push({
    questionType: 'SINGLE',
    questionText: '',
    isRequired: 1,
    sortOrder: questions.value.length + 1,
    status: 1,
    options: [
      { optionKey: 'A', optionContent: '', sortOrder: 1, status: 1 },
      { optionKey: 'B', optionContent: '', sortOrder: 2, status: 1 }
    ]
  });
}
function addOption(question: SurveyQuestion): void {
  question.options.push({
    optionKey: String.fromCharCode(65 + question.options.length),
    optionContent: '',
    sortOrder: question.options.length + 1,
    status: 1
  });
}
/** 删除草稿题目并保持服务端要求的顺序连续。 */
function removeQuestion(index: number): void {
  questions.value.splice(index, 1);
  questions.value.forEach((question, order) => { question.sortOrder = order + 1; });
}
/** 调整题目顺序后重排 sortOrder，界面顺序即保存顺序。 */
function moveQuestion(index: number, offset: number): void {
  const target = index + offset;
  if (target < 0 || target >= questions.value.length) return;
  const [moved] = questions.value.splice(index, 1);
  if (!moved) return;
  questions.value.splice(target, 0, moved);
  questions.value.forEach((question, order) => { question.sortOrder = order + 1; });
}
/** 删除选项后重排选项键，避免再次添加时出现重复键。 */
function removeOption(question: SurveyQuestion, index: number): void {
  question.options.splice(index, 1);
  question.options.forEach((option, order) => {
    option.sortOrder = order + 1;
    option.optionKey = String.fromCharCode(65 + order);
  });
}
/** 调整选项顺序后同步重排选项键，保持 A、B、C 连续可读。 */
function moveOption(question: SurveyQuestion, index: number, offset: number): void {
  const target = index + offset;
  if (target < 0 || target >= question.options.length) return;
  const [moved] = question.options.splice(index, 1);
  if (!moved) return;
  question.options.splice(target, 0, moved);
  question.options.forEach((option, order) => {
    option.sortOrder = order + 1;
    option.optionKey = String.fromCharCode(65 + order);
  });
}
async function load(): Promise<void> {
  if (!surveyId.value) return;
  detail.value = await getSurvey(surveyId.value);
  Object.assign(form, detail.value);
  questions.value = detail.value.questions ?? [];
  preview.value = undefined;
}
async function save(): Promise<void> {
  if (!form.surveyCode || !form.surveyName) {
    ElMessage.warning('请填写问卷编码和名称。');
    return;
  }
  saving.value = true;
  try {
    const creating = !surveyId.value;
    const id = surveyId.value ?? (await saveSurvey(form));
    if (surveyId.value) {
      if (!detail.value) return;
      await updateSurvey(id, { version: detail.value.version, surveyName: form.surveyName, description: form.description, status: form.status });
    }
    const saved = await getSurvey(id);
    await replaceSurveyQuestions(id, saved.version, questions.value);
    ElMessage.success('问卷已保存。');
    await router.replace(`/admin/result/survey/workspace/${id}`);
    if (!creating) await load();
  } finally {
    saving.value = false;
  }
}
async function openPreview(): Promise<void> {
  if (!surveyId.value) {
    ElMessage.warning('请先保存问卷后预览。');
    return;
  }
  preview.value = await previewSurvey(surveyId.value, previewClient.value);
  active.value = 'preview';
}
async function duplicate(): Promise<void> {
  if (!surveyId.value) return;
  const { value } = await ElMessageBox.prompt('请输入新问卷编码', '复制问卷', {
    inputValue: `${form.surveyCode}-COPY`,
    inputPattern: /\S+/,
    inputErrorMessage: '请输入新问卷编码。'
  });
  const id = await copySurvey(surveyId.value, { surveyCode: value.trim(), surveyName: `${form.surveyName}（副本）` });
  await router.push(`/admin/result/survey/workspace/${id}`);
}
/** 复制问卷会复用当前路由组件，必须按新 ID 重新读取草稿。 */
watch(surveyId, load, { immediate: true });
</script>
<template>
  <section class="admin-detail-page">
    <header class="admin-detail-page__header">
      <div>
        <h1>{{ frozen ? '查看问卷' : surveyId ? '编辑问卷' : '新建问卷' }}</h1>
        <p>仅支持单选、多选和文本题；投放后由后端冻结为只读。</p>
      </div>
      <ElButton :icon="ArrowLeft" @click="router.push('/admin/result/survey')">返回列表</ElButton>
    </header>
    <nav class="stage-nav admin-detail-page__steps" aria-label="问卷工作台步骤">
      <button type="button" class="stage-item" :class="{ 'is-active': active === 'edit' }" @click="active = 'edit'">
        <span class="stage-index">1</span>
        <span>
          <b>设计问卷</b>
          <small>基本信息与题目、选项</small>
        </span>
      </button>
      <button type="button" class="stage-item" :class="{ 'is-active': active === 'preview' }" @click="openPreview">
        <span class="stage-index">2</span>
        <span>
          <b>预览效果</b>
          <small>按电脑或手机查看填写样张</small>
        </span>
      </button>
    </nav>

    <ElCard v-if="active === 'edit'" class="admin-detail-page__card" shadow="never">
      <section class="ws-form-section">
        <header class="ws-form-section__head">
          <span class="ws-form-section__index">1</span>
          <div>
            <strong>基本信息</strong>
            <p>编码创建后不可修改，用途决定这份问卷可以被投放到哪个场景。</p>
          </div>
          <ElTag v-if="frozen" type="info" effect="plain" class="form-section__state">已投放，只读</ElTag>
        </header>
        <ElForm class="admin-detail-form" label-width="100px">
          <ElRow :gutter="16">
            <ElCol :span="12">
              <ElFormItem label="问卷编码">
                <ElInput v-model="form.surveyCode" placeholder="例如 SURVEY-2026-001" :disabled="Boolean(surveyId)" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="问卷名称">
                <ElInput v-model="form.surveyName" placeholder="请输入问卷名称" :disabled="frozen" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="问卷用途">
                <ElSelect v-model="form.surveyType" :disabled="Boolean(surveyId)">
                  <ElOption label="线下培训调查" value="TRAINING" />
                  <ElOption label="培训机构评价" value="ORGANIZATION_EVALUATION" />
                </ElSelect>
              </ElFormItem>
            </ElCol>
            <ElCol :span="24">
              <ElFormItem label="问卷说明">
                <ElInput
                  v-model="form.description"
                  type="textarea"
                  :rows="2"
                  :disabled="frozen"
                  placeholder="填写前向填写人说明调查目的与用途"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
        </ElForm>
      </section>

      <section class="ws-form-section">
        <header class="ws-form-section__head">
          <span class="ws-form-section__index">2</span>
          <div>
            <strong>题目与选项</strong>
            <p>列表顺序即填写顺序；单选与多选题至少保留两个选项。</p>
          </div>
          <span class="ws-section__extra">共 {{ questions.length }} 题</span>
        </header>
        <div v-if="!questions.length" class="ws-empty ws-empty--dashed">
          <ElEmpty description="还没有题目">
            <span class="ws-empty__hint">添加第一题后可以设置题型、必填与选项；保存后才可进入预览。</span>
            <ElButton v-if="!frozen" type="primary" :icon="CirclePlus" @click="addQuestion">添加第一题</ElButton>
          </ElEmpty>
        </div>
        <article v-for="(question, index) in questions" :key="index" class="ws-item-card question-card">
          <header class="question-card__head">
            <span class="question-card__index">第 {{ index + 1 }} 题</span>
            <div class="question-card__tools">
              <ElButton link :icon="Top" :disabled="frozen || index === 0" @click="moveQuestion(index, -1)">上移</ElButton>
              <ElButton link :icon="Bottom" :disabled="frozen || index === questions.length - 1" @click="moveQuestion(index, 1)">下移</ElButton>
              <ElButton v-if="!frozen" link type="danger" :icon="Delete" @click="removeQuestion(index)">删除题目</ElButton>
            </div>
          </header>
          <div class="question-card__body">
            <ElInput v-model="question.questionText" :placeholder="`第 ${index + 1} 题题目`" :disabled="frozen" />
            <div class="question-card__meta">
              <ElSelect v-model="question.questionType" :disabled="frozen" class="question-card__type">
                <ElOption label="单选题" value="SINGLE" />
                <ElOption label="多选题" value="MULTIPLE" />
                <ElOption label="文本题" value="TEXT" />
              </ElSelect>
              <ElSwitch
                v-model="question.isRequired"
                :true-value="1"
                :false-value="0"
                :disabled="frozen"
                active-text="必填"
                inactive-text="选填"
              />
            </div>
            <div v-if="question.questionType !== 'TEXT'" class="options">
              <div
                v-for="(option, optionIndex) in question.options"
                :key="option.surveyOptionId ?? option.optionKey"
                class="option-row"
              >
                <span class="ws-index ws-index--square">{{ option.optionKey }}</span>
                <ElInput v-model="option.optionContent" :placeholder="`选项 ${option.optionKey}`" :disabled="frozen" />
                <div class="option-row__tools">
                  <ElButton link :disabled="frozen || optionIndex === 0" @click="moveOption(question, optionIndex, -1)">上移</ElButton>
                  <ElButton
                    link
                    :disabled="frozen || optionIndex === question.options.length - 1"
                    @click="moveOption(question, optionIndex, 1)"
                  >下移</ElButton>
                  <ElButton
                    v-if="!frozen"
                    link
                    type="danger"
                    :disabled="question.options.length <= MIN_OPTION_COUNT"
                    @click="removeOption(question, optionIndex)"
                  >删除选项</ElButton>
                </div>
              </div>
              <ElButton v-if="!frozen" link type="primary" :icon="CirclePlus" @click="addOption(question)">添加选项</ElButton>
            </div>
            <p v-else class="ws-hint">文本题由填写人自由输入，不产生选项统计。</p>
          </div>
        </article>
        <ElButton v-if="questions.length && !frozen" :icon="CirclePlus" @click="addQuestion">添加题目</ElButton>
      </section>
      <footer class="sticky-actions">
        <ElButton v-if="surveyId" @click="duplicate">复制为新草稿</ElButton>
        <ElButton v-if="!frozen" type="primary" @click="save" :loading="saving">保存</ElButton>
      </footer>
    </ElCard>

    <ElCard v-else class="admin-detail-page__card" shadow="never">
      <header class="preview-bar">
        <ElRadioGroup v-model="previewClient" @change="openPreview">
          <ElRadioButton value="PC">电脑预览</ElRadioButton>
          <ElRadioButton value="MOBILE">手机预览</ElRadioButton>
        </ElRadioGroup>
        <span class="ws-hint">预览读取服务端冻结的题目顺序，与填写端展示一致。</span>
      </header>
      <div v-if="!preview" class="ws-empty ws-empty--dashed">
        <ElEmpty description="暂无可预览的问卷">
          <span class="ws-empty__hint">预览需要服务端已保存的题目快照，请先返回设计问卷并保存。</span>
          <ElButton type="primary" @click="active = 'edit'">返回设计</ElButton>
        </ElEmpty>
      </div>
      <div v-else class="preview-stage" :class="{ 'preview-stage--mobile': previewClient === 'MOBILE' }">
        <div class="preview-sheet">
          <header class="preview-sheet__head">
            <h2>{{ preview.surveyName }}</h2>
            <p>
              {{ SURVEY_TYPE_LABELS[preview.surveyType] }} · 共 {{ preview.questions?.length ?? 0 }} 题
            </p>
          </header>
          <ElForm label-position="top">
            <ElFormItem
              v-for="(question, index) in preview.questions"
              :key="question.surveyQuestionId"
              :required="question.isRequired === 1"
            >
              <template #label>
                <span class="preview-label">
                  <em>Q{{ index + 1 }}</em>
                  {{ question.questionText }}
                </span>
              </template>
              <ElRadioGroup v-if="question.questionType === 'SINGLE'">
                <ElRadio v-for="option in question.options" :key="option.surveyOptionId" :value="option.surveyOptionId">
                  {{ option.optionContent }}
                </ElRadio>
              </ElRadioGroup>
              <ElCheckboxGroup v-else-if="question.questionType === 'MULTIPLE'">
                <ElCheckbox v-for="option in question.options" :key="option.surveyOptionId" :value="option.surveyOptionId">
                  {{ option.optionContent }}
                </ElCheckbox>
              </ElCheckboxGroup>
              <ElInput v-else type="textarea" :rows="3" disabled placeholder="文本题由填写人输入" />
            </ElFormItem>
          </ElForm>
          <footer class="preview-sheet__submit">
            <ElButton type="primary" disabled>提交问卷</ElButton>
            <span>预览态不可提交，提交按钮仅用于确认按钮位置与文案。</span>
          </footer>
        </div>
      </div>
    </ElCard>
  </section>
</template>
<style scoped lang="less">
.form-section__state {
  margin-left: auto;
}

/* 题目卡沿用全局 ws-item-card 外壳，此处只定义卡片内部结构。 */
.question-card__head {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-2);
  border-bottom: 1px solid var(--color-border);
}

.question-card__index {
  color: var(--color-text-primary);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.question-card__tools {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.question-card__body {
  display: grid;
  gap: var(--space-2);
}

.question-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4);
}

.question-card__type {
  width: 140px;
}

.options {
  display: grid;
  gap: var(--space-2);
}

.option-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.option-row__tools {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-1);
}

.preview-bar {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--color-border);
}

.preview-stage {
  display: flex;
  flex: 1;
  min-height: 0;
  justify-content: center;
  padding: var(--space-4) 0;
}

/* 手机预览套用窄屏容器，便于确认填写端换行与点击区域。 */
.preview-sheet {
  display: grid;
  align-content: start;
  gap: var(--space-3);
  width: 100%;
  max-width: 720px;
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
}

.preview-stage--mobile .preview-sheet {
  max-width: 375px;
  padding: var(--space-4);
  border-radius: 24px;
  box-shadow: var(--shadow-card-hover);
}

.preview-sheet__head h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 18px;
  font-weight: 600;
}

.preview-sheet__head p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.preview-label {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  white-space: normal;
}

.preview-label em {
  flex: 0 0 auto;
  color: var(--color-primary);
  font-size: 12px;
  font-style: normal;
  font-weight: 700;
}

.preview-sheet__submit {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-2);
  border-top: 1px solid var(--color-border);
}

.preview-sheet__submit span {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
</style>
