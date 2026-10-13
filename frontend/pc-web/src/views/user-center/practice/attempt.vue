<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft } from '@element-plus/icons-vue';
import type { PracticeAnswer, PracticeSession } from '@shared/types';
import { filterPracticeQuestions, practiceFeedback, practiceStandardAnswer } from '@shared/utils/learner-practice';
import { getPractice, savePracticeAnswer, submitPractice, clearPractice, setQuestionFavorite, startPractice } from '@/api/learner-practice';
import { PRACTICE_FILTERS, LEARNER_CENTER_ROUTES } from '@/constants/learner-center';

/** 独立练习作答，不复用会写入正式成绩的提交接口。 */
const route = useRoute(); const router = useRouter();
const session = ref<PracticeSession>(); const index = ref(0); const filter = ref('ALL');
const draft = ref<PracticeAnswer>({ selected: [], blanks: {} }); const busy = ref(false); const error = ref('');
const questions = computed(() => filterPracticeQuestions(session.value?.questions ?? [], filter.value));
const current = computed(() => session.value?.questions[index.value]);
const editable = computed(() => session.value?.status === 'ANSWERING');
/** 返回目标优先取进入来源，直链或刷新时按服务端题源事实兜底，避免答完题无路可退。 */
const backTarget = computed(() => {
  const fromQuestion = route.query.from === 'wrongs' || (!route.query.from && Boolean(session.value?.sourceQuestionId));
  return fromQuestion
    ? { path: LEARNER_CENTER_ROUTES.wrongs, label: '返回我的错题集' }
    : { path: LEARNER_CENTER_ROUTES.practices, label: '返回我的练习' };
});
/** 从当前题目的本人答案恢复可编辑草稿。 */
function setDraft(): void { const answer = current.value?.answer; draft.value = { text: answer?.text, selected: [...(answer?.selected ?? [])], blanks: { ...(answer?.blanks ?? {}) } }; }
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> {
  try {
    session.value = await getPractice(Number(route.params.sessionId));
    // 从题目收藏进入时定位对应题目，身份与答案权限仍由恢复接口校验。
    if (route.query.questionId) index.value = Math.max(0, session.value.questions.findIndex(q => q.questionId === Number(route.query.questionId)));
    setDraft();
    // 直链或刷新时按服务端题源补齐来源，保证二级页签高亮与返回入口一致。
    if (!route.query.from) {
      await router.replace({ path: route.path, query: { ...route.query, from: session.value.sourceQuestionId ? 'wrongs' : 'practices' } });
    }
  } catch (e) { error.value = e instanceof Error ? e.message : '加载失败'; }
}
/** 保存当前题目草稿并读取受控反馈。 */
async function save(): Promise<void> {
  if (!current.value || !session.value || !editable.value) return;
  session.value = await savePracticeAnswer(session.value.sessionId, current.value.itemId, draft.value); setDraft();
}
/** 防止重复执行作答操作，并统一显示失败提示。 */
async function operate(action: () => Promise<void>): Promise<void> {
  if (busy.value) return; busy.value = true;
  try { await action(); } catch (e) { ElMessage.error(e instanceof Error ? e.message : '操作失败'); }
  finally { busy.value = false; }
}
/** 先保存当前答案，再跳转答题卡选中的题目。 */
async function jump(itemId: number): Promise<void> { await operate(async () => { await save(); index.value = session.value!.questions.findIndex(q => q.itemId === itemId); setDraft(); }); }
/** 确认后保存草稿并提交个人会话。 */
async function finish(): Promise<void> { await operate(async () => { await save(); await ElMessageBox.confirm('确认提交本次作答？未做客观题将记录为错误。', '提交确认'); session.value = await submitPractice(session.value!.sessionId); setDraft(); }); }
/** 修改当前题目的本人收藏状态。 */
async function favorite(): Promise<void> { await operate(async () => { await setQuestionFavorite(current.value!.questionId, !current.value!.favorite); current.value!.favorite = !current.value!.favorite; }); }
/** 确认后只清除当前个人练习记录。 */
async function clear(): Promise<void> { await operate(async () => { await ElMessageBox.confirm('只清除本次个人练习，不改原考核、错题和收藏。', '清除记录'); await clearPractice(session.value!.sessionId); await router.push(LEARNER_CENTER_ROUTES.practices); }); }
/** 保存当前答案后，以原题源启动另一个模式的独立会话。 */
async function switchMode(): Promise<void> {
  await operate(async () => {
    await save();
    // 原题源引用由恢复接口返回，不允许页面指定正式试卷。
    const source = session.value!;
    const next = await startPractice({ candidateId: source.candidateId ?? undefined, questionId: source.sourceQuestionId ?? undefined, mode: source.mode === 'PRACTICE' ? 'SIMULATION' : 'PRACTICE', clientToken: (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`) });
    // 切换模式后保留进入来源，返回入口与二级页签高亮不漂移。
    await router.replace({ path: `${LEARNER_CENTER_ROUTES.attempt}/${next.sessionId}`, query: route.query });
  });
}
// 同一组件切换会话及浏览器回退时恢复对应记录。
watch(() => route.params.sessionId, () => { index.value = 0; error.value = ''; void load(); });
onMounted(load);
</script>

<template>
  <section v-loading="busy" class="portal-list-page portal-attempt">
    <ElAlert v-if="error" type="error" :title="error" :closable="false" />
    <template v-if="session">
      <header class="portal-list-page__heading">
        <div>
          <RouterLink class="portal-attempt__back" :to="backTarget.path">
            <ElIcon><ArrowLeft /></ElIcon>{{ backTarget.label }}
          </RouterLink>
          <p class="portal-list-page__eyebrow">{{ session.mode === 'PRACTICE' ? 'PRACTICE' : 'SIMULATION' }}</p>
          <h1>{{ session.title }}</h1>
          <p>{{ session.mode === 'PRACTICE' ? '练习模式：保存后立即查看反馈' : '模拟模式：交卷前不提供答案与对错' }}，均不计正式成绩与学时。</p>
        </div>
        <div class="portal-attempt__actions">
          <ElButton @click="switchMode">切换模式</ElButton>
          <ElButton @click="clear">清除记录</ElButton>
          <ElButton v-if="editable" type="primary" @click="finish">提交作答</ElButton>
        </div>
      </header>
      <p v-if="session.status === 'SUBMITTED'" class="portal-attempt__stat">
        正确 {{ session.correctCount }} · 错误 {{ session.wrongCount }} · 未判定 {{ session.unassessedCount }} ·
        正确率 {{ session.accuracyRate === null ? '暂无' : `${session.accuracyRate.toFixed(1)}%` }} · 用时 {{ session.usedSeconds }} 秒
      </p>
      <div class="portal-attempt__workspace">
        <aside class="portal-attempt__aside">
          <h3>答题卡</h3>
          <ElSelect v-model="filter" placeholder="请选择题目范围">
            <ElOption v-for="f in PRACTICE_FILTERS" :key="f.value" :label="f.label" :value="f.value" />
          </ElSelect>
          <div class="answer-card">
            <ElButton v-for="q in questions" :key="q.itemId" :type="q.itemId === current?.itemId ? 'primary' : undefined" @click="jump(q.itemId)">
              {{ session.questions.findIndex(i => i.itemId === q.itemId) + 1 }}{{ q.answer ? ' ✓' : '' }}
            </ElButton>
          </div>
          <p v-if="!questions.length" class="portal-attempt__hint">当前范围没有题目</p>
        </aside>
        <article v-if="current" class="portal-attempt__question">
          <div class="portal-attempt__question-head">
            <h3>{{ index + 1 }}. {{ current.stem }}</h3>
            <ElButton link type="primary" @click="favorite">{{ current.favorite ? '取消收藏' : '收藏题目' }}</ElButton>
          </div>
          <ElCheckboxGroup v-if="current.type === 'MULTIPLE'" v-model="draft.selected" :disabled="!editable">
            <ElCheckbox v-for="op in current.options" :key="op.key" :value="op.key">{{ op.key }}. {{ op.content }}</ElCheckbox>
          </ElCheckboxGroup>
          <ElRadioGroup v-else-if="['SINGLE', 'JUDGE'].includes(current.type)" :model-value="draft.selected[0]" :disabled="!editable" @update:model-value="draft.selected = [String($event)]">
            <ElRadio v-for="op in current.options" :key="op.key" :value="op.key">{{ op.key }}. {{ op.content }}</ElRadio>
          </ElRadioGroup>
          <template v-else-if="current.type === 'FILL'">
            <ElInput v-for="n in current.blankCount || 1" :key="n" v-model="draft.blanks[String(n)]" :disabled="!editable" :placeholder="`请输入第${n}空答案`" />
          </template>
          <ElInput v-else v-model="draft.text" type="textarea" :disabled="!editable" placeholder="请输入答案" />
          <div class="portal-attempt__actions">
            <ElButton v-if="editable" type="primary" @click="operate(save)">保存{{ session.mode === 'PRACTICE' ? '并查看反馈' : '答案' }}</ElButton>
          </div>
          <div v-if="current.evaluationResult || current.correctAnswerJson || current.analysis" class="portal-attempt__feedback">
            <p v-if="current.evaluationResult">{{ practiceFeedback(current.evaluationResult) }}</p>
            <p v-if="current.correctAnswerJson">标准答案：{{ practiceStandardAnswer(current.correctAnswerJson) }}</p>
            <p v-if="current.analysis">解析：{{ current.analysis }}</p>
          </div>
        </article>
      </div>
    </template>
  </section>
</template>

<style scoped lang="less">
/* 返回入口放在页头左上角，避免进入作答后只能靠顶部页签退出。 */
.portal-attempt__back {
  display: inline-flex;
  margin-bottom: var(--space-3);
  padding: 5px 14px 5px 10px;
  align-items: center;
  gap: 4px;
  border-radius: 999px;
  background: rgb(255 255 255 / 16%);
  color: #fff;
  font-size: 13px;
  text-decoration: none;
  transition: background 0.2s ease;
}

.portal-attempt__back:hover {
  background: rgb(255 255 255 / 30%);
}

.portal-attempt__actions {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.portal-attempt__stat {
  margin: 0;
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-primary-subtle);
  color: var(--color-text-regular);
}

.portal-attempt__workspace {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--space-5);
}

.portal-attempt__aside,
.portal-attempt__question {
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-card);
}

.portal-attempt__aside {
  align-self: start;

  h3 {
    margin: 0 0 var(--space-3);
    color: var(--color-text-primary);
    font-size: 15px;
  }
}

.portal-attempt__hint {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.answer-card {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.portal-attempt__question-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);

  h3 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: 15px;
    line-height: 1.6;
    white-space: pre-wrap;
  }
}

.portal-attempt__question {
  .el-radio-group,
  .el-checkbox-group {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
    margin: var(--space-4) 0;
  }

  .el-input {
    margin: var(--space-2) 0;
  }
}

.portal-attempt__actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.portal-attempt__feedback {
  margin-top: var(--space-4);
  padding: var(--space-3);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);

  p {
    margin: 0 0 var(--space-1);
    color: var(--color-text-regular);
    white-space: pre-wrap;
  }
}
</style>
