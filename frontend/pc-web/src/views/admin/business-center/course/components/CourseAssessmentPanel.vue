<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { PersonPickerItem } from '@/api/person-picker';
import PersonPickerDialog from '@/components/organization/PersonPickerDialog.vue';
import {
  getCourseAssessmentResults,
  getCourseAssessmentReviewTasks,
  getCourseAssessmentReviewers,
  getCourseChapters,
  getCourseItems,
  replaceCourseAssessmentReviewers,
  reviewCourseAssessmentAnswer,
  type CourseAssessmentResult,
  type CourseAssessmentReviewTask,
  type CourseAssessmentReviewer,
  type CourseItem
} from '@/api/course-operations';

const props = defineProps<{ offeringId: number; courseId: number; publishStatus: 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'ENDED' }>();

/** 单个课程测验及其阅卷配置、批阅任务和自动生效结果。 */
interface AssessmentQuizCard {
  chapterName: string;
  item: CourseItem;
  reviewers: CourseAssessmentReviewer[];
  reviewerIds: number[];
  selectedReviewers: PersonPickerItem[];
  tasks: CourseAssessmentReviewTask[];
  results: CourseAssessmentResult[];
}

/** 待批阅列表的筛选口径，仅影响展示不影响数据。 */
type ReviewFilter = 'ALL' | 'PENDING' | 'REVIEWED';

/** 批阅筛选的分段按钮配置，计数在模板中按课程测验实时拼装。 */
const REVIEW_FILTER_OPTIONS: ReadonlyArray<{ label: string; value: ReviewFilter }> = [
  { label: '全部', value: 'ALL' },
  { label: '待批阅', value: 'PENDING' },
  { label: '已批阅', value: 'REVIEWED' }
];

const quizCards = ref<AssessmentQuizCard[]>([]);
const loading = ref(false);
const reviewerPickerVisible = ref(false);
const reviewerPickerItemId = ref<number>();
/** 默认停留在待批阅队列，管理员打开抽屉即可继续批阅。 */
const reviewFilter = ref<ReviewFilter>('PENDING');
/**
 * 服务端已保存批阅结果的作答。
 * 该集合只在重新加载后刷新，避免编辑中的分数或评语让题目在「待批阅」队列里提前消失。
 */
const reviewedAnswerIds = ref<Set<number>>(new Set());
/** 正在提交评分的作答，用于按钮级 loading，避免重复提交。 */
const submittingAnswerIds = ref<Set<number>>(new Set());
/** 草稿阶段仅能配置阅卷人；发布后才产生批阅任务与生效结果。 */
const isDraft = computed(() => props.publishStatus === 'DRAFT');
/** 已配置数量仅用于草稿阶段的配置进度展示，不改变发布校验。 */
const configuredQuizCount = computed(() => quizCards.value.filter((card) => card.reviewerIds.length > 0).length);
const activeQuizCard = computed(() => quizCards.value.find((card) => card.item.courseItemId === reviewerPickerItemId.value));
const activePreselectedReviewers = computed(() => activeQuizCard.value?.selectedReviewers ?? []);
/** 全部课程测验的批阅进度，用于概览卡指标。 */
const totalPendingCount = computed(() => quizCards.value.reduce((sum, card) => sum + pendingTaskCount(card), 0));
const totalReviewedCount = computed(() => quizCards.value.reduce((sum, card) => sum + reviewedTaskCount(card), 0));
const totalResultCount = computed(() => quizCards.value.reduce((sum, card) => sum + card.results.length, 0));

/** 将已保存的阅卷人转换为选择器回显数据，避免再次保存时误清空已有配置。 */
function toPersonPickerItem(reviewer: CourseAssessmentReviewer): PersonPickerItem {
  return {
    userId: reviewer.reviewerUserId,
    userCode: reviewer.reviewerCode ?? `USER-${reviewer.reviewerUserId}`,
    userName: reviewer.reviewerName ?? `用户${reviewer.reviewerUserId}`,
    organizationId: 0,
    status: 1
  };
}

/** 加载单项课程测验的阅卷人，并同步选择器回显内容。 */
async function loadReviewers(card: AssessmentQuizCard): Promise<void> {
  const reviewers = await getCourseAssessmentReviewers(props.offeringId, card.item.courseItemId);
  card.reviewers = reviewers;
  card.reviewerIds = reviewers.map((reviewer) => reviewer.reviewerUserId);
  card.selectedReviewers = reviewers.map(toPersonPickerItem);
}

/** 已发布开课才存在作答、批阅与自动生效结果。 */
async function loadPublishedAssessmentData(card: AssessmentQuizCard): Promise<void> {
  const [taskPage, resultPage] = await Promise.all([
    getCourseAssessmentReviewTasks(props.offeringId, card.item.courseItemId),
    getCourseAssessmentResults(props.offeringId, card.item.courseItemId)
  ]);
  card.tasks = taskPage.list;
  card.results = resultPage.list;
}

/**
 * 同步服务端批阅状态快照。
 * 传入单个课程测验时只刷新该测验，保留其他测验尚未提交的编辑内容。
 */
function syncReviewedState(card?: AssessmentQuizCard): void {
  const targetCards = card ? [card] : quizCards.value;
  const next = card ? new Set(reviewedAnswerIds.value) : new Set<number>();
  for (const target of targetCards) {
    for (const task of target.tasks) {
      if (task.latestManualScore == null) {
        next.delete(task.examAnswerId);
      } else {
        next.add(task.examAnswerId);
      }
    }
  }
  reviewedAnswerIds.value = next;
}

/** 按章节构建全部课程测验卡片，避免管理员逐项切换查看。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const chapters = await getCourseChapters(props.courseId);
    const groupedItems = await Promise.all(chapters.map((chapter) => getCourseItems(chapter.chapterId)));
    const cards = groupedItems.flatMap((items, chapterIndex) =>
      items
        .filter((item) => item.itemType === 'QUIZ')
        .map<AssessmentQuizCard>((item) => ({
          chapterName: chapters[chapterIndex].chapterName,
          item,
          reviewers: [],
          reviewerIds: [],
          selectedReviewers: [],
          tasks: [],
          results: []
        }))
    );
    await Promise.all(cards.map(loadReviewers));
    if (!isDraft.value) await Promise.all(cards.map(loadPublishedAssessmentData));
    quizCards.value = cards;
    syncReviewedState();
  } finally {
    loading.value = false;
  }
}

/** 打开当前课程测验的人员选择器。 */
function openReviewerPicker(card: AssessmentQuizCard): void {
  reviewerPickerItemId.value = card.item.courseItemId;
  reviewerPickerVisible.value = true;
}

/** 选择器确认后立即保存当前课程测验的阅卷人，避免重复的保存操作。 */
async function chooseReviewers(people: PersonPickerItem[]): Promise<void> {
  if (!activeQuizCard.value) return;
  const card = activeQuizCard.value;
  card.selectedReviewers = people;
  card.reviewerIds = people.map((person) => person.userId);
  await replaceCourseAssessmentReviewers(props.offeringId, card.item.courseItemId, card.reviewerIds);
  ElMessage.success(`“${card.item.itemTitle}”的阅卷人已保存`);
  await loadReviewers(card);
}

/** 服务端是否已保存该题的批阅结果。 */
function isTaskReviewed(task: CourseAssessmentReviewTask): boolean {
  return reviewedAnswerIds.value.has(task.examAnswerId);
}

/** 当前筛选口径下的题目数量，用于分段按钮上的计数。 */
function filterCount(card: AssessmentQuizCard, filter: ReviewFilter): number {
  if (filter === 'REVIEWED') return reviewedTaskCount(card);
  if (filter === 'PENDING') return pendingTaskCount(card);
  return card.tasks.length;
}

/** 待批阅题目数量。 */
function pendingTaskCount(card: AssessmentQuizCard): number {
  return card.tasks.filter((task) => !isTaskReviewed(task)).length;
}

/** 已批阅题目数量。 */
function reviewedTaskCount(card: AssessmentQuizCard): number {
  return card.tasks.filter((task) => isTaskReviewed(task)).length;
}

/** 当前筛选口径下实际展示的题目。 */
function visibleTasks(card: AssessmentQuizCard): CourseAssessmentReviewTask[] {
  if (reviewFilter.value === 'REVIEWED') return card.tasks.filter((task) => isTaskReviewed(task));
  if (reviewFilter.value === 'PENDING') return card.tasks.filter((task) => !isTaskReviewed(task));
  return card.tasks;
}

/** 单项课程测验的批阅完成度百分比。 */
function quizProgress(card: AssessmentQuizCard): number {
  if (!card.tasks.length) return 0;
  return Math.round((reviewedTaskCount(card) / card.tasks.length) * 100);
}

/** 列表为空时按筛选口径给出更贴近当前状态的说明。 */
function emptyTaskDescription(card: AssessmentQuizCard): string {
  if (!card.tasks.length) return '学员尚未提交可批阅的主观题作答';
  if (reviewFilter.value === 'REVIEWED') return '暂无已批阅的题目';
  return '该课程测验的主观题已全部批阅完成';
}

/** 快捷给分：把分数限制在 0 到满分之间，减少手工输入。 */
function applyQuickScore(task: CourseAssessmentReviewTask, value: number): void {
  task.latestManualScore = Math.min(Math.max(Number(value.toFixed(1)), 0), task.score);
}

/** 评分输入：空值统一落为 null，与后端「未批阅」口径保持一致。 */
function setTaskScore(task: CourseAssessmentReviewTask, value?: number | null): void {
  task.latestManualScore = value ?? null;
}

/** 批阅意见输入：保留原始文本，提交时再收敛空值，避免输入过程中被改写。 */
function setTaskComment(task: CourseAssessmentReviewTask, value?: string | number | null): void {
  task.latestReviewComment = typeof value === 'string' ? value : '';
}

/** 判断某道题是否正在提交评分。 */
function isSubmitting(answerId: number): boolean {
  return submittingAnswerIds.value.has(answerId);
}

/** 维护提交中的作答集合。 */
function setSubmitting(answerId: number, value: boolean): void {
  const next = new Set(submittingAnswerIds.value);
  if (value) {
    next.add(answerId);
  } else {
    next.delete(answerId);
  }
  submittingAnswerIds.value = next;
}

/** 在当前课程测验下提交一道主观题评分。 */
async function review(card: AssessmentQuizCard, task: CourseAssessmentReviewTask): Promise<void> {
  if (task.latestManualScore == null) {
    ElMessage.warning('请先填写评分');
    return;
  }
  const comment = task.latestReviewComment?.trim();
  setSubmitting(task.examAnswerId, true);
  try {
    await reviewCourseAssessmentAnswer(props.offeringId, card.item.courseItemId, task.examAnswerId, {
      afterScore: task.latestManualScore,
      reviewComment: comment ? comment : undefined
    });
    ElMessage.success(`已提交「${task.learnerName || '该学员'}」的评分`);
    await loadPublishedAssessmentData(card);
    syncReviewedState(card);
  } finally {
    setSubmitting(task.examAnswerId, false);
  }
}

/** 接口时间统一转换为便于阅读的中文界面格式。 */
function formatDateTime(value?: string | null): string {
  return value ? value.replace('T', ' ') : '—';
}

/** 供开课弹窗发布操作读取未配置人员的课程测验名称。 */
function getUnconfiguredQuizNames(): string[] {
  return quizCards.value.filter((card) => !card.reviewerIds.length).map((card) => card.item.itemTitle);
}

defineExpose({ getUnconfiguredQuizNames });
watch(() => [props.offeringId, props.courseId, props.publishStatus], load, { immediate: true });
</script>

<template>
  <section class="course-assessment-panel">
    <header class="assessment-hero">
      <div class="assessment-hero__main">
        <span class="assessment-hero__eyebrow">课程评估</span>
        <h3>课程测验考核管理</h3>
        <p>客观题自动判分；全部主观题完成批阅后，课程测验成绩自动生效。</p>
      </div>
      <div class="assessment-hero__aside">
        <div class="assessment-metric">
          <span>课程测验</span>
          <strong>{{ quizCards.length }}</strong>
        </div>
        <div class="assessment-metric">
          <span v-if="isDraft">已配置阅卷人</span>
          <span v-else>待批阅</span>
          <strong v-if="isDraft" :class="{ 'is-success': quizCards.length > 0 && configuredQuizCount === quizCards.length }">
            {{ configuredQuizCount }} / {{ quizCards.length }}
          </strong>
          <strong v-else :class="{ 'is-warning': totalPendingCount > 0 }">{{ totalPendingCount }}</strong>
        </div>
        <template v-if="!isDraft">
          <div class="assessment-metric">
            <span>已批阅</span>
            <strong :class="{ 'is-success': totalReviewedCount > 0 }">{{ totalReviewedCount }}</strong>
          </div>
          <div class="assessment-metric">
            <span>已生效结果</span>
            <strong>{{ totalResultCount }}</strong>
          </div>
        </template>
        <ElButton :loading="loading" @click="load">刷新数据</ElButton>
      </div>
    </header>
    <ElAlert
      v-if="isDraft"
      title="当前开课为草稿：请为每一项含主观题的课程测验配置并保存阅卷人，再发布开课。发布后才会产生批阅任务和自动生效结果。"
      type="warning"
      :closable="false"
      show-icon
    />
    <ElEmpty v-if="!quizCards.length" description="当前课程没有课程测验内容项" />
    <div v-else class="assessment-card-list">
      <article v-for="card in quizCards" :key="card.item.courseItemId" class="quiz-card">
        <header class="quiz-card__head">
          <div class="quiz-card__title">
            <span class="quiz-card__chapter">{{ card.chapterName }}</span>
            <strong :title="card.item.itemTitle">{{ card.item.itemTitle }}</strong>
          </div>
          <ElTag :type="card.reviewerIds.length ? 'success' : 'warning'" effect="light" size="small">
            {{ card.reviewerIds.length ? `已配置 ${card.reviewerIds.length} 名阅卷人` : '尚未配置阅卷人' }}
          </ElTag>
        </header>
        <div class="quiz-card__body">
          <section class="reviewer-bar">
            <div class="reviewer-bar__head">
              <span class="reviewer-bar__label">阅卷人</span>
              <small>{{ isDraft ? '发布开课前需完成配置' : '以下人员可批阅该课程测验' }}</small>
            </div>
            <div class="reviewer-bar__body">
              <div v-if="card.selectedReviewers.length" class="reviewer-tags">
                <ElTag v-for="reviewer in card.selectedReviewers" :key="reviewer.userId" effect="plain" size="small">
                  {{ reviewer.userName }}（{{ reviewer.userCode }}）
                </ElTag>
              </div>
              <span v-else class="reviewer-bar__empty">尚未配置；含主观题的课程测验不能直接发布开课。</span>
              <ElButton size="small" @click="openReviewerPicker(card)">
                {{ card.reviewerIds.length ? '调整阅卷人' : '选择阅卷人' }}
              </ElButton>
            </div>
          </section>

          <template v-if="!isDraft">
            <section class="workbench">
              <header class="workbench__head">
                <div>
                  <h4>批阅工作台</h4>
                  <p>对照学员答案与参考答案逐题评分，提交后系统自动汇总该测验成绩。</p>
                </div>
                <div class="workbench__progress">
                  <span>
                    已完成
                    <strong>{{ reviewedTaskCount(card) }}</strong>
                    / {{ card.tasks.length }} 题
                  </span>
                  <ElProgress :percentage="quizProgress(card)" :stroke-width="6" :show-text="false" />
                </div>
              </header>
              <div class="workbench__filter">
                <ElRadioGroup v-model="reviewFilter" size="small">
                  <ElRadioButton v-for="option in REVIEW_FILTER_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }} {{ filterCount(card, option.value) }}
                  </ElRadioButton>
                </ElRadioGroup>
                <span class="workbench__hint">客观题已自动判分，此处只需处理主观题</span>
              </div>
              <ElEmpty v-if="!visibleTasks(card).length" :image-size="72" :description="emptyTaskDescription(card)" />
              <div v-else class="review-list">
                <article
                  v-for="(task, index) in visibleTasks(card)"
                  :key="task.examAnswerId"
                  class="review-card"
                  :class="{ 'review-card--reviewed': isTaskReviewed(task) }"
                >
                  <header class="review-card__head">
                    <span class="review-card__index">{{ String(index + 1).padStart(2, '0') }}</span>
                    <div class="review-card__learner">
                      <strong>{{ task.learnerName || '未命名学员' }}</strong>
                      <small>本题满分 {{ task.score }} 分</small>
                    </div>
                    <ElTag :type="isTaskReviewed(task) ? 'success' : 'warning'" effect="light" size="small">
                      {{ isTaskReviewed(task) ? '已批阅' : '待批阅' }}
                    </ElTag>
                  </header>
                  <p class="review-card__stem">{{ task.stemSnapshot }}</p>
                  <div class="review-card__answers">
                    <section class="answer-block" :class="{ 'answer-block--empty': !task.answerText }">
                      <header class="answer-block__head">学员答案</header>
                      <p class="answer-block__text">{{ task.answerText || '学员未作答' }}</p>
                    </section>
                    <section class="answer-block answer-block--reference">
                      <header class="answer-block__head">参考答案</header>
                      <p class="answer-block__text">{{ task.standardAnswer || '未提供参考答案' }}</p>
                    </section>
                  </div>
                  <footer class="review-card__scoring">
                    <div class="score-field">
                      <span class="field-label">评分</span>
                      <div class="score-field__row">
                        <ElInputNumber
                          class="score-field__input"
                          :model-value="task.latestManualScore ?? undefined"
                          :min="0"
                          :max="task.score"
                          controls-position="right"
                          @update:model-value="setTaskScore(task, $event)"
                        />
                        <span class="score-field__total">/ {{ task.score }}</span>
                        <div class="score-shortcuts">
                          <ElButton size="small" plain @click="applyQuickScore(task, task.score)">满分</ElButton>
                          <ElButton size="small" plain @click="applyQuickScore(task, task.score / 2)">半分</ElButton>
                          <ElButton size="small" plain @click="applyQuickScore(task, 0)">0 分</ElButton>
                        </div>
                      </div>
                    </div>
                    <div class="comment-field">
                      <span class="field-label">批阅意见（选填）</span>
                      <ElInput
                        :model-value="task.latestReviewComment ?? ''"
                        type="textarea"
                        :rows="2"
                        resize="none"
                        maxlength="200"
                        placeholder="内部批阅备注，仅管理端回显，不会展示给学员"
                        @update:model-value="setTaskComment(task, $event)"
                      />
                    </div>
                    <ElButton type="primary" :loading="isSubmitting(task.examAnswerId)" @click="review(card, task)">
                      {{ isTaskReviewed(task) ? '更新评分' : '提交评分' }}
                    </ElButton>
                  </footer>
                </article>
              </div>
            </section>

            <section class="snapshot-section">
              <header class="snapshot-section__header">
                <div>
                  <h4>自动生效结果</h4>
                  <p>全部主观题批阅完成后，系统自动汇总生成的课程测验成绩。</p>
                </div>
                <span class="snapshot-section__count">共 {{ card.results.length }} 条</span>
              </header>
              <ElTable :data="card.results">
                <ElTableColumn label="学员" min-width="150">
                  <template #default="{ row }">
                    <span class="result-learner">{{ row.learnerName || `学员 ${row.userId}` }}</span>
                    <ElTag v-if="row.attemptNo > 1" size="small" effect="plain">第 {{ row.attemptNo }} 次</ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="成绩" width="90" align="right" class-name="cell-number">
                  <template #default="{ row }">{{ row.finalScore }}</template>
                </ElTableColumn>
                <ElTableColumn label="答对" width="80" align="right" class-name="cell-number">
                  <template #default="{ row }">{{ row.correctCount }}</template>
                </ElTableColumn>
                <ElTableColumn label="答错" width="80" align="right" class-name="cell-number">
                  <template #default="{ row }">{{ row.wrongCount }}</template>
                </ElTableColumn>
                <ElTableColumn label="结果" width="90">
                  <template #default="{ row }">
                    <ElTag :type="row.isPassed === 1 ? 'success' : 'info'" size="small" effect="light">
                      {{ row.isPassed === 1 ? '通过' : '未通过' }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="生效时间" min-width="160" class-name="cell-time">
                  <template #default="{ row }">{{ formatDateTime(row.effectiveTime) }}</template>
                </ElTableColumn>
                <template #empty>
                  <ElEmpty :image-size="72" description="暂无自动生效结果" />
                </template>
              </ElTable>
            </section>
          </template>
        </div>
      </article>
    </div>
  </section>
  <PersonPickerDialog
    v-model:visible="reviewerPickerVisible"
    title="选择课程测验阅卷人"
    :preselected="activePreselectedReviewers"
    @confirm="chooseReviewers"
  />
</template>

<style scoped lang="less">
.course-assessment-panel {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-1) 0 var(--space-5);
}
/* 概览卡：把测验数量与批阅进度前置，避免管理员在长列表里逐项寻找 */
.assessment-hero {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5);
  overflow: hidden;
  background: var(--color-primary-subtle);
  border: 1px solid #dbeafe;
  border-radius: var(--radius-card);

  &::after {
    content: '';
    position: absolute;
    top: -56px;
    right: -56px;
    width: 168px;
    height: 168px;
    background: rgb(37 99 235 / 0.08);
    border-radius: 50%;
    pointer-events: none;
  }

  > * {
    position: relative;
    z-index: 1;
  }

  h3 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: 18px;
    font-weight: 600;
  }

  p {
    max-width: 460px;
    margin: var(--space-1) 0 0;
    color: var(--color-text-secondary);
    font-size: 13px;
    line-height: 20px;
  }
}
.assessment-hero__main {
  flex: 1 1 260px;
}
.assessment-hero__eyebrow {
  display: block;
  margin-bottom: var(--space-1);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1.2px;
}
.assessment-hero__aside {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
}
.assessment-metric {
  display: grid;
  min-width: 88px;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  box-shadow: var(--shadow-card);

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 18px;
  }

  strong {
    color: var(--color-text-primary);
    font-size: 18px;
    font-variant-numeric: tabular-nums;
    font-weight: 600;
    line-height: 22px;
  }

  strong.is-warning {
    color: #d97706;
  }

  strong.is-success {
    color: #16a34a;
  }
}
.assessment-card-list {
  display: grid;
  gap: var(--space-4);
}
.quiz-card {
  overflow: hidden;
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.quiz-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--color-bg-subtle);
  border-bottom: 1px solid var(--color-border);
}
.quiz-card__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);

  strong {
    overflow: hidden;
    color: var(--color-text-primary);
    font-size: 15px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
.quiz-card__chapter {
  flex: none;
  padding: 1px 8px;
  background: var(--color-primary-subtle);
  border-radius: 999px;
  color: var(--color-primary);
  font-size: 12px;
  line-height: 18px;
}
.quiz-card__body {
  display: grid;
  gap: var(--space-4);
  padding: var(--space-4);
}
/* 阅卷人配置：与批阅数据分区，配置结果一眼可见 */
.reviewer-bar {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: linear-gradient(90deg, var(--color-primary-subtle), var(--color-bg-surface));
  border: 1px solid #dbeafe;
  border-radius: var(--radius-control);
}
.reviewer-bar__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);

  small {
    color: var(--color-text-secondary);
    font-size: 12px;
  }
}
.reviewer-bar__label {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
}
.reviewer-bar__body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}
.reviewer-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
.reviewer-bar__empty {
  color: var(--color-text-secondary);
  font-size: 13px;
}
.workbench {
  display: grid;
  gap: var(--space-3);
}
.workbench__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);

  h4 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: 15px;
    font-weight: 600;
  }

  p {
    margin: 2px 0 0;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 18px;
  }
}
.workbench__progress {
  display: grid;
  width: 180px;
  gap: var(--space-1);

  span {
    color: var(--color-text-secondary);
    font-size: 12px;
  }

  strong {
    color: var(--color-primary);
    font-size: 13px;
    font-variant-numeric: tabular-nums;
  }

  :deep(.el-progress-bar__outer) {
    background: #e2e8f0;
  }
}
.workbench__filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  border-bottom: 1px dashed var(--color-border);
}
.workbench__hint {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.review-list {
  display: grid;
  gap: var(--space-3);
}
/* 单题批阅卡：题干、双栏答案、评分与批阅意见集中在一屏内，不需要展开收起 */
.review-card {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: #bfdbfe;
    box-shadow: var(--shadow-card-hover);
  }
}
.review-card--reviewed {
  background: var(--color-bg-subtle);
}
.review-card__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.review-card__index {
  display: flex;
  flex: none;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  background: var(--color-primary-subtle);
  border-radius: 999px;
  color: var(--color-primary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.review-card__learner {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: baseline;
  gap: var(--space-2);

  strong {
    color: var(--color-text-primary);
    font-size: 14px;
    font-weight: 600;
  }

  small {
    color: var(--color-text-secondary);
    font-size: 12px;
  }
}
.review-card__stem {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  background: var(--color-bg-subtle);
  border-left: 3px solid var(--color-primary);
  border-radius: 0 var(--radius-control) var(--radius-control) 0;
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 22px;
  white-space: pre-wrap;
  word-break: break-word;
}
.review-card__answers {
  display: grid;
  gap: var(--space-3);
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}
.answer-block {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-3);
  background: var(--color-primary-subtle);
  border: 1px solid #dbeafe;
  border-radius: var(--radius-control);
}
.answer-block--reference,
.answer-block--empty {
  background: var(--color-bg-subtle);
  border-color: var(--color-border);
}
.answer-block__head {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.4px;
}
.answer-block__text {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 22px;
  white-space: pre-wrap;
  word-break: break-word;
}
.answer-block--empty .answer-block__text {
  color: var(--color-text-secondary);
}
.review-card__scoring {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px dashed var(--color-border);
}
.field-label {
  display: block;
  margin-bottom: var(--space-1);
  color: var(--color-text-secondary);
  font-size: 12px;
}
.score-field {
  flex: none;
}
.score-field__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}
.score-field__input {
  width: 118px;
}
.score-field__total {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.score-shortcuts {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}
.comment-field {
  flex: 1 1 240px;
  min-width: 200px;
}
.snapshot-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--color-bg-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);

  :deep(.el-table__header-wrapper th) {
    background: var(--color-bg-surface);
    color: var(--color-text-regular);
    font-size: 13px;
    font-weight: 600;
  }

  :deep(td.el-table__cell) {
    font-size: 13px;
  }

  :deep(.cell-number) {
    font-variant-numeric: tabular-nums;
    font-weight: 500;
  }

  :deep(.cell-time) {
    color: var(--color-text-regular);
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  :deep(.el-table__empty-block) {
    background: var(--color-bg-surface);
  }
}
.snapshot-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);

  h4 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: 15px;
    font-weight: 600;
  }

  p {
    margin: 2px 0 0;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 18px;
  }
}
.snapshot-section__count {
  flex: none;
  padding: 2px 10px;
  background: var(--color-bg-surface);
  border-radius: 999px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
.result-learner {
  margin-right: var(--space-1);
  color: var(--color-text-primary);
}
@media (max-width: 1024px) {
  .assessment-hero {
    align-items: stretch;
    flex-direction: column;
  }
  .assessment-hero__aside {
    justify-content: flex-start;
  }
  .workbench__progress {
    width: 100%;
  }
}
@media (max-width: 720px) {
  .review-card__scoring {
    align-items: stretch;
    flex-direction: column;
  }
  .reviewer-bar__body {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
