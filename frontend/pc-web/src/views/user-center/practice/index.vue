<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { EditPen, Notebook } from '@element-plus/icons-vue';
import type { PracticeQuestion, PracticeTask, PracticeSession, PracticeMode } from '@shared/types';
import { practiceStandardAnswer, practiceRecordTime, practiceRecordDuration, practiceSourceLabel } from '@shared/utils/learner-practice';
import { getPracticeTasks, getWrongQuestions, getPracticeSessions, startPractice, setWrongDisposition } from '@/api/learner-practice';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { getExamEnumLabel, QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';
import { LEARNER_CENTER_ROUTES, PRACTICE_MODES } from '@/constants/learner-center';

/** 分配练习与本人错题共用列表操作，页面不读取管理端题目接口。 */
const route = useRoute();
const router = useRouter();
const wrongView = computed(() => route.name === 'LearnerWrongQuestions');
const tasks = ref<PracticeTask[]>([]);
const wrongs = ref<PracticeQuestion[]>([]);
const sessions = ref<PracticeSession[]>([]);
/** 历史会话独立分页，避免只能回看最近一页。 */
const sessionPage = ref(1);
const sessionTotal = ref(0);
const pageNum = ref(1);
const total = ref(0);
const keyword = ref('');
const mode = ref<PracticeMode>('PRACTICE');
const busy = ref(false);
const error = ref('');
const expanded = ref<number>();
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> {
  busy.value = true;
  error.value = '';
  try {
    const params = { pageNum: pageNum.value, pageSize: DEFAULT_LIST_PAGE_SIZE, 'query.keyword': keyword.value };
    if (wrongView.value) {
      const page = await getWrongQuestions(params);
      wrongs.value = page.list;
      total.value = page.total;
    } else {
      const page = await getPracticeTasks(params);
      tasks.value = page.list;
      total.value = page.total;
      await loadSessions();
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    busy.value = false;
  }
}
/** 读取本人全部历史记录的当前页。 */
async function loadSessions(): Promise<void> {
  const result = await getPracticeSessions({ pageNum: sessionPage.value, pageSize: DEFAULT_LIST_PAGE_SIZE });
  sessions.value = result.list;
  sessionTotal.value = result.total;
}
/** 校验当前操作状态后启动独立练习会话。 */
async function start(candidateId?: number, questionId?: number): Promise<void> {
  if (busy.value) return;
  busy.value = true;
  try {
    const value = await startPractice({
      candidateId,
      questionId,
      mode: mode.value,
      clientToken: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
    });
    await router.push({ path: `${LEARNER_CENTER_ROUTES.attempt}/${value.sessionId}`, query: { from: wrongView.value ? 'wrongs' : 'practices' } });
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '启动失败');
  } finally {
    busy.value = false;
  }
}
/** 提交错题处理状态后刷新列表。 */
async function handleWrong(id: number, disposition: 'DELETED' | 'MASTERED'): Promise<void> {
  try {
    await setWrongDisposition(id, disposition);
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
  }
}
watch(wrongView, () => {
  pageNum.value = 1;
  void load();
});
onMounted(load);
</script>

<template>
  <section v-loading="busy" class="portal-list-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">{{ wrongView ? 'WRONG QUESTIONS' : 'MY PRACTICE' }}</p>
        <h1>{{ wrongView ? '我的错题集' : '我的练习' }}</h1>
        <p>
          {{ wrongView ? '正式考试和课程测试的可见错题，标记已掌握或删除不会修改原答卷。' : '练习逐题反馈，模拟交卷后反馈；均不改变正式考核结果。' }}
        </p>
      </div>
      <ElSelect v-model="mode" class="portal-heading__select" placeholder="请选择作答模式">
        <ElOption v-for="item in PRACTICE_MODES" :key="item.value" :label="item.label" :value="item.value" />
      </ElSelect>
    </header>
    <div class="portal-toolbar">
      <ElInput
        v-model="keyword"
        placeholder="请输入名称或题干"
        clearable
        @keyup.enter="
          pageNum = 1;
          load();
        "
      />
      <ElButton
        type="primary"
        @click="
          pageNum = 1;
          load();
        "
      >
        查询
      </ElButton>
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <section v-if="!busy && !error && total === 0" class="portal-list-state">
      <ElEmpty description="暂无记录" />
    </section>
    <template v-else>
      <div v-if="wrongView" class="portal-list-grid">
        <ElCard v-for="item in wrongs" :key="item.questionId" class="portal-list-card" shadow="never">
          <div class="portal-list-card__cover portal-cover">
            <span class="portal-cover__icon">
              <ElIcon><Notebook /></ElIcon>
            </span>
            <span class="portal-cover__text">
              <strong>{{ item.sourceType === 'COURSE_QUIZ' ? '课程测试错题' : '正式考试错题' }}</strong>
              <em>{{ item.sourceName || '未标注来源' }}</em>
            </span>
            <ElIcon class="portal-cover__mark"><Notebook /></ElIcon>
          </div>
          <div class="portal-list-card__content">
            <div class="portal-list-card__header">
              <h3>{{ item.stem }}</h3>
            </div>
            <p class="portal-list-card__meta">{{ getExamEnumLabel(item.type, QUESTION_TYPE_OPTIONS) }}</p>
            <div class="portal-list-card__actions portal-list-card__actions--split">
              <span class="portal-list-card__actions-group">
                <ElButton link type="primary" @click="expanded = expanded === item.questionId ? undefined : item.questionId">
                  {{ expanded === item.questionId ? '收起解析' : '查看解析' }}
                </ElButton>
                <ElButton link @click="handleWrong(item.questionId, 'MASTERED')">已掌握</ElButton>
                <ElButton link type="danger" @click="handleWrong(item.questionId, 'DELETED')">删除</ElButton>
              </span>
              <ElButton type="primary" @click="start(undefined, item.questionId)">再练</ElButton>
            </div>
            <div v-if="expanded === item.questionId" class="practice-answer">
              <p>标准答案：{{ practiceStandardAnswer(item.correctAnswerJson) }}</p>
              <p>解析：{{ item.analysis || '当前未提供或不允许查看解析' }}</p>
            </div>
          </div>
        </ElCard>
      </div>
      <div v-else class="portal-list-grid">
        <ElCard v-for="task in tasks" :key="task.candidateId" class="portal-list-card" shadow="never">
          <div class="portal-list-card__cover portal-cover">
            <span class="portal-cover__icon">
              <ElIcon><EditPen /></ElIcon>
            </span>
            <span class="portal-cover__text">
              <strong>{{ task.kind === 'BANK_PRACTICE' ? '题库练习' : '专项训练' }}</strong>
              <em>管理员分配</em>
            </span>
            <ElIcon class="portal-cover__mark"><EditPen /></ElIcon>
          </div>
          <div class="portal-list-card__content">
            <div class="portal-list-card__header">
              <h3>{{ task.title }}</h3>
            </div>
            <div class="portal-list-card__actions">
              <ElButton type="primary" @click="start(task.candidateId)">开始作答</ElButton>
            </div>
          </div>
        </ElCard>
      </div>
    </template>
    <div class="portal-list-page__pagination">
      <ElPagination
        v-model:current-page="pageNum"
        :page-size="DEFAULT_LIST_PAGE_SIZE"
        :page-sizes="LIST_PAGE_SIZES"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </div>
    <template v-if="!wrongView && sessions.length">
      <div class="portal-section-head">
        <h2 class="portal-section-title">练习与模拟记录</h2>
        <span class="portal-section-hint">共 {{ sessionTotal }} 条，含全部历史作答</span>
      </div>
      <div class="portal-list-grid">
        <ElCard v-for="s in sessions" :key="s.sessionId" class="portal-list-card record-card" shadow="never">
          <div class="portal-list-card__content record-card__content">
            <div class="portal-list-card__header">
              <h3>{{ s.title }}</h3>
              <span class="portal-chip" :class="s.mode === 'SIMULATION' ? 'portal-chip--warning' : ''">
                {{ s.mode === 'PRACTICE' ? '练习模式' : '模拟模式' }}
              </span>
            </div>
            <div class="portal-list-card__tags">
              <span class="portal-chip" :class="s.status === 'ANSWERING' ? 'portal-chip--warning' : 'portal-chip--success'">
                {{ s.status === 'ANSWERING' ? '作答中' : '已提交' }}
              </span>
              <span class="portal-chip portal-chip--plain">{{ practiceSourceLabel(s.sourceType) }}</span>
            </div>
            <p class="record-card__stem">{{ s.questionStem ? `题目：${s.questionStem}` : s.sourceName || '原来源未记录' }}</p>
            <dl class="record-card__stats">
              <div>
                <dt>已答</dt>
                <dd>{{ s.answeredCount }} / {{ s.totalCount }}</dd>
              </div>
              <div v-if="s.status === 'SUBMITTED'">
                <dt>正确</dt>
                <dd>{{ s.correctCount }}</dd>
              </div>
              <div v-if="s.status === 'SUBMITTED'">
                <dt>错误</dt>
                <dd>{{ s.wrongCount }}</dd>
              </div>
              <div v-if="s.status === 'SUBMITTED'">
                <dt>正确率</dt>
                <dd>{{ s.accuracyRate === null ? '暂无' : `${s.accuracyRate.toFixed(1)}%` }}</dd>
              </div>
              <div>
                <dt>用时</dt>
                <dd>{{ practiceRecordDuration(s.usedSeconds) }}</dd>
              </div>
            </dl>
            <p class="record-card__time">
              开始 {{ practiceRecordTime(s.startedTime) }}
              <br>
              <template v-if="s.submittedTime">提交 {{ practiceRecordTime(s.submittedTime) }}</template>
            </p>
            <div class="portal-list-card__actions portal-list-card__actions--split">
              <span class="portal-list-card__actions-group">
                <span v-if="s.status === 'SUBMITTED' && s.unassessedCount" class="record-card__unassessed">待判定 {{ s.unassessedCount }} 题</span>
              </span>
              <ElButton type="primary" @click="router.push(`${LEARNER_CENTER_ROUTES.attempt}/${s.sessionId}`)">
                {{ s.status === 'ANSWERING' ? '继续作答' : '查看结果' }}
              </ElButton>
            </div>
          </div>
        </ElCard>
      </div>
      <div class="portal-list-page__pagination">
        <ElPagination
          v-model:current-page="sessionPage"
          :page-size="DEFAULT_LIST_PAGE_SIZE"
          layout="total, prev, pager, next"
          :total="sessionTotal"
          @current-change="load"
        />
      </div>
    </template>
  </section>
</template>

<style scoped lang="less">
.portal-heading__select {
  flex: none;
  width: 160px;
}

.portal-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);

  .el-input {
    width: 260px;
  }
}

.record-card__content {
  gap: var(--space-3);
}

.record-card__stem {
  margin: 0;
  color: var(--color-text-regular);
  font-size: 13px;
  line-height: 20px;
}

/* 记录统计条：把原先七行流水账压成一行可扫视的数字。 */
.record-card__stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-5);
  margin: 0;
  padding: var(--space-3);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
}

.record-card__stats dt {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.record-card__stats dd {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 22px;
}

.record-card__time,
.record-card__unassessed {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.practice-answer {
  margin-top: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
  color: var(--color-text-regular);
  font-size: 13px;

  p {
    margin: 0 0 var(--space-1);
    white-space: pre-wrap;
  }
}
</style>
