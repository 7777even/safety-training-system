<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import type { PracticeSession, PracticeAnswer } from '@shared/types';
import { filterPracticeQuestions, practiceFeedback, practiceStandardAnswer } from '@shared/utils/learner-practice';
import { getPractice, savePracticeAnswer, submitPractice, clearPractice, setQuestionFavorite, startPractice } from '@/api/learner-practice';
import { LEARNER_CENTER_ROUTES, PRACTICE_FILTERS } from '@/constants/learner-center';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

/** 移动自由作答使用本人练习接口，正式考核记录保持隔离。 */
const id = ref(0); const session = ref<PracticeSession>(); const index = ref(0); const filter = ref('ALL');
/** 收藏题目入口携带原题引用，只用于恢复后定位，不用于取题或授权。 */
let initialQuestionId = 0;
const draft = ref<PracticeAnswer>({ selected: [], blanks: {} }); const busy = ref(false); const error = ref('');
const current = computed(() => session.value?.questions[index.value]);
const questions = computed(() => filterPracticeQuestions(session.value?.questions ?? [], filter.value));
const editable = computed(() => session.value?.status === 'ANSWERING');
/** 从当前题目的本人答案恢复可编辑草稿。 */
function setDraft(): void { const a = current.value?.answer; draft.value = { text: a?.text, selected: [...(a?.selected ?? [])], blanks: { ...(a?.blanks ?? {}) } }; }
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> { try { session.value = await getPractice(id.value); if (initialQuestionId) index.value = Math.max(0, session.value.questions.findIndex(q => q.questionId === initialQuestionId)); setDraft(); } catch (e) { error.value = e instanceof Error ? e.message : '加载失败'; } }
/** 保存当前题目草稿并读取受控反馈。 */
async function save(): Promise<void> { if (current.value && editable.value) { session.value = await savePracticeAnswer(id.value, current.value.itemId, draft.value); setDraft(); } }
/** 防止重复执行作答操作，并统一显示失败提示。 */
async function operate(action: () => Promise<void>): Promise<void> { if (busy.value) return; busy.value = true; try { await action(); } catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' }); } finally { busy.value = false; } }
/** 先保存当前答案，再跳转答题卡选中的题目。 */
async function jump(itemId: number): Promise<void> { await operate(async () => { await save(); index.value = session.value!.questions.findIndex(q => q.itemId === itemId); setDraft(); }); }
/** 确认后保存草稿并提交个人会话。 */
async function finish(): Promise<void> { const result = await uni.showModal({ title: '提交作答', content: '确认提交？未做客观题将记录为错误。' }); if (result.confirm) await operate(async () => { await save(); session.value = await submitPractice(id.value); setDraft(); }); }
/** 修改当前题目的本人收藏状态。 */
async function favorite(): Promise<void> { await operate(async () => { await setQuestionFavorite(current.value!.questionId, !current.value!.favorite); current.value!.favorite = !current.value!.favorite; }); }
/** 确认后只清除当前个人练习记录。 */
async function clear(): Promise<void> { const result = await uni.showModal({ title: '清除记录', content: '只清除本次个人练习，不改原考核、错题和收藏。' }); if (result.confirm) await operate(async () => { await clearPractice(id.value); uni.redirectTo({ url: LEARNER_CENTER_ROUTES.practices }); }); }
/** 保存当前答案后，以原题源启动另一个模式的独立会话。 */
async function switchMode(): Promise<void> { await operate(async () => { await save(); const s = session.value!; const next = await startPractice({ candidateId: s.candidateId ?? undefined, questionId: s.sourceQuestionId ?? undefined, mode: s.mode === 'PRACTICE' ? 'SIMULATION' : 'PRACTICE', clientToken: (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`) }); uni.redirectTo({ url: `${LEARNER_CENTER_ROUTES.attempt}?id=${next.sessionId}` }); }); }
/** 将多选控件结果同步到答案草稿。 */
function multipleChanged(event: { detail: { value: string[] } }): void { draft.value.selected = event.detail.value; }
/** 将单选控件结果同步到答案草稿。 */
function singleChanged(event: { detail: { value: string } }): void { draft.value.selected = [event.detail.value]; }
onLoad(query => { id.value = Number(query?.id); initialQuestionId = Number(query?.questionId) || 0; void load(); });
</script>
<template>
  <MobileBaseLayout>
    <view class="learner-page">
      <text v-if="error" class="learner-error">{{ error }}</text>
      <template v-if="session">
        <view class="learner-hero">
          <view class="learner-hero__deco" />
          <text class="learner-hero__eyebrow">{{ session.mode === 'PRACTICE' ? 'PRACTICE' : 'SIMULATION' }}</text>
          <text class="learner-hero__title">{{ session.title }}</text>
          <text class="learner-hero__subtitle">{{ session.mode === 'PRACTICE' ? '练习模式：保存后立即查看反馈' : '模拟模式：交卷前不提供答案与对错' }}</text>
        </view>
        <view class="learner-toolbar">
          <button class="learner-button" :disabled="busy" @click="switchMode">切换模式</button>
          <button class="learner-button" :disabled="busy" @click="clear">清除记录</button>
          <button v-if="editable" class="learner-button learner-button--primary" :disabled="busy" @click="finish">提交</button>
        </view>
        <view v-if="session.status === 'SUBMITTED'" class="learner-card">
          <text class="learner-meta">正确{{ session.correctCount }} · 错误{{ session.wrongCount }} · 未判定{{ session.unassessedCount }} · 正确率{{ session.accuracyRate === null ? '暂无' : session.accuracyRate.toFixed(1) + '%' }} · 用时{{ session.usedSeconds }}秒</text>
        </view>
        <view class="learner-card">
          <text class="learner-title">答题卡</text>
          <view class="segmented-filter">
            <text v-for="f in PRACTICE_FILTERS" :key="f.value" class="segmented-filter__item" :class="{ 'segmented-filter__item--active': filter === f.value }" @click="filter = f.value">{{ f.label }}</text>
          </view>
          <view class="learner-actions">
            <button v-for="q in questions" :key="q.itemId" class="learner-button" :disabled="busy" @click="jump(q.itemId)">{{ session.questions.findIndex(i => i.itemId === q.itemId) + 1 }}{{ q.answer ? '✓' : '' }}</button>
          </view>
          <text v-if="!questions.length" class="learner-meta">当前范围没有题目</text>
        </view>
        <view v-if="current" class="learner-card">
          <text class="learner-card__text">{{ index + 1 }}. {{ current.stem }}</text>
          <button class="learner-button" :disabled="busy" @click="favorite">{{ current.favorite ? '取消收藏' : '收藏题目' }}</button>
          <checkbox-group v-if="current.type === 'MULTIPLE'" @change="multipleChanged">
            <label v-for="op in current.options" :key="op.key" class="learner-option"><checkbox :value="op.key" :checked="draft.selected.includes(op.key)" :disabled="!editable || busy" />{{ op.key }}. {{ op.content }}</label>
          </checkbox-group>
          <radio-group v-else-if="['SINGLE', 'JUDGE'].includes(current.type)" @change="singleChanged">
            <label v-for="op in current.options" :key="op.key" class="learner-option"><radio :value="op.key" :checked="draft.selected[0] === op.key" :disabled="!editable || busy" />{{ op.key }}. {{ op.content }}</label>
          </radio-group>
          <template v-else-if="current.type === 'FILL'">
            <input v-for="n in current.blankCount || 1" :key="n" v-model="draft.blanks[String(n)]" class="learner-input learner-input--block" :disabled="!editable || busy" :placeholder="`请输入第${n}空答案`">
          </template>
          <textarea v-else v-model="draft.text" class="learner-input learner-input--block" :disabled="!editable || busy" placeholder="请输入答案" />
          <button v-if="editable" class="learner-button learner-button--primary" :disabled="busy" @click="operate(save)">保存{{ session.mode === 'PRACTICE' ? '并反馈' : '答案' }}</button>
          <view v-if="current.evaluationResult || current.correctAnswerJson || current.analysis" class="learner-answer">
            <text v-if="current.evaluationResult">{{ practiceFeedback(current.evaluationResult) }}</text>
            <text v-if="current.correctAnswerJson">标准答案：{{ practiceStandardAnswer(current.correctAnswerJson) }}</text>
            <text v-if="current.analysis">解析：{{ current.analysis }}</text>
          </view>
        </view>
      </template>
    </view>
  </MobileBaseLayout>
</template>
<style scoped lang="less">
.learner-input--block {
  flex: none;
  width: 100%;
  margin: 8rpx 0;
  box-sizing: border-box;
}

.learner-option {
  display: flex;
  min-height: 88rpx;
  align-items: center;
  color: var(--color-text-regular, #374151);
  font-size: 28rpx;
}

.learner-answer {
  padding: 24rpx;
  border-radius: 16rpx;
  background: var(--color-bg-subtle, #f9fafb);

  text {
    display: block;
    color: var(--color-text-regular, #374151);
    font-size: 24rpx;
    line-height: 1.6;
    white-space: pre-wrap;
  }
}
</style>
