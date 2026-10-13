<script setup lang="ts">
import { reactive, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import type { LearnerHistory, HistoryRecordType, HistorySummary } from '@shared/types/learner-history';
import { HISTORY_LABELS, HISTORY_FIELDS, historyTarget, historyStatus } from '@shared/utils/learner-history';
import { LEARNER_LIST_PAGE_SIZE } from '@/constants/learner-center';
import { getMyHistory, getMyHistoryDetail } from '@/api/learner-history';

/** 本人统一历史及只读轨迹，移动端不提供更正写入。 */
const rows = ref<LearnerHistory[]>([]), total = ref(0), page = ref(1), busy = ref(false), error = ref(''), detail = ref<LearnerHistory>(), detailBusy = ref(false);
const query = reactive({ recordType: 'ALL', keyword: '', fromDate: '', toDate: '' });
const kinds = ['ALL', ...Object.keys(HISTORY_LABELS)] as const;
/** 按来源、名称和发生日期分页，结束与退出记录仍保留。 */
async function load(): Promise<void> {
  busy.value = true; error.value = '';
  try { const result = await getMyHistory({ pageNum: page.value, pageSize: LEARNER_LIST_PAGE_SIZE, 'query.recordType': query.recordType,
    'query.keyword': query.keyword, 'query.fromDate': query.fromDate || undefined, 'query.toDate': query.toDate || undefined });
    rows.value = result.list; total.value = result.total;
  } catch (e) { error.value = e instanceof Error ? e.message : '历史读取失败'; }
  finally { busy.value = false; }
}
/** 筛选重置页码。 */
function search(): void { page.value = 1; void load(); }
/**
 * 日期区间两端倒置时以本次选择的端点为准，把另一端清空，
 * 避免静默发出必然为空的区间让学员以为是「没有历史」。
 */
function changeDate(bound: 'fromDate' | 'toDate', value: string): void {
  query[bound] = value;
  if (query.fromDate && query.toDate && query.fromDate > query.toDate) {
    if (bound === 'fromDate') query.toDate = ''; else query.fromDate = '';
  }
  search();
}
/** 清空区间同样回到第一页重新查询。 */
function clearDates(): void { query.fromDate = ''; query.toDate = ''; search(); }
/**
 * 原始摘要与更正轨迹重新鉴权。
 * 详情走底部弹层：原先渲染在列表末尾，点击后当前视口毫无变化，表现为「点了没反应」。
 * 失败提示改用轻提示，保证在任意滚动位置都能被看到。
 */
async function inspect(row: LearnerHistory): Promise<void> {
  if (detailBusy.value) return;
  detailBusy.value = true;
  try { detail.value = await getMyHistoryDetail(row.recordType, row.recordId); }
  catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '详情读取失败', icon: 'none' }); }
  finally { detailBusy.value = false; }
}
/** 原入口沿用既有受控页面，失效原因由目标接口说明。 */
async function openOriginal(row: LearnerHistory): Promise<void> {
  try { const current = await getMyHistoryDetail(row.recordType, row.recordId); uni.navigateTo({ url: historyTarget(current.reference, 'H5') }); }
  catch (e) { error.value = e instanceof Error ? e.message : '原始记录不可访问'; }
}
/** 摘要字段明确区分未公开与不适用。 */
function value(s: HistorySummary, field: keyof typeof HISTORY_FIELDS): string {
  if (field === 'status') return historyStatus(s.status);
  return field === 'score' && !s.scoreVisible ? '未公开或不评分' : String(s[field] ?? '不适用/无记录');
}
/** 留痕前后值使用中文字段名称。 */
function changes(data: Record<string, unknown>): string { return Object.entries(data).map(([key, v]) => `${HISTORY_FIELDS[key as keyof typeof HISTORY_FIELDS]}：${v ?? '空'}`).join('；') || '无可公开字段'; }
/** 分页避免覆盖筛选输入。 */
function turn(delta: number): void { page.value += delta; void load(); }
onShow(load);
</script>
<template>
  <MobileBaseLayout>
    <view class="learner-page">
      <view class="learner-hero">
        <view class="learner-hero__deco" />
        <text class="learner-hero__eyebrow">MY HISTORY</text>
        <text class="learner-hero__title">统一学习档案</text>
        <text class="learner-hero__subtitle">全部已有历史；原始记录与档案更正分开展示</text>
      </view>

      <text v-if="error" class="learner-error">{{ error }}</text>

      <view class="learner-toolbar">
        <input v-model="query.keyword" class="learner-input" placeholder="请输入来源名称" @confirm="search">
        <button class="learner-button learner-button--primary" @click="search">查询</button>
      </view>

      <view class="history-kinds">
        <button
          v-for="kind in kinds"
          :key="kind"
          class="learner-button"
          :class="{ 'learner-button--primary': query.recordType === kind }"
          @click="query.recordType = kind; search()"
        >
          {{ kind === 'ALL' ? '全部' : HISTORY_LABELS[kind as HistoryRecordType] }}
        </button>
      </view>

      <view class="learner-toolbar">
        <view class="learner-daterange">
          <picker class="learner-daterange__picker" mode="date" :value="query.fromDate" @change="changeDate('fromDate', $event.detail.value)">
            <view class="learner-daterange__value" :class="{ 'learner-daterange__value--empty': !query.fromDate }">{{ query.fromDate || '开始日期' }}</view>
          </picker>
          <text class="learner-daterange__sep">~</text>
          <picker class="learner-daterange__picker" mode="date" :value="query.toDate" @change="changeDate('toDate', $event.detail.value)">
            <view class="learner-daterange__value" :class="{ 'learner-daterange__value--empty': !query.toDate }">{{ query.toDate || '结束日期' }}</view>
          </picker>
          <text v-if="query.fromDate || query.toDate" class="learner-daterange__clear" @click="clearDates">清空</text>
        </view>
      </view>

      <view v-if="!busy && !rows.length" class="learner-state">暂无符合条件的历史</view>

      <view v-for="row in rows" :key="`${row.recordType}-${row.recordId}`" class="learner-card">
        <view class="history-card__head">
          <text class="learner-title">{{ row.title }}</text>
          <text class="history-badge" :class="{ 'history-badge--done': ['COMPLETED', 'PASSED'].includes(row.current.status) }">{{ historyStatus(row.current.status) }}</text>
        </view>
        <text class="learner-meta">{{ HISTORY_LABELS[row.recordType] }} · {{ row.current.occurredTime }}</text>
        <text class="learner-meta">成绩：{{ value(row.current, 'score') }} · 学时：{{ value(row.current, 'hours') }}</text>
        <text class="learner-meta">{{ row.version ? `档案更正 v${row.version}` : '原始记录' }}</text>
        <view class="learner-actions learner-actions--split">
          <view class="learner-actions__group">
            <button class="learner-link" @click="openOriginal(row)">原始记录</button>
          </view>
          <button class="learner-button learner-button--primary" :disabled="detailBusy" @click="inspect(row)">{{ detailBusy ? '读取中…' : '档案与留痕' }}</button>
        </view>
      </view>

      <view class="learner-actions">
        <button class="learner-button" :disabled="page === 1 || busy" @click="turn(-1)">上一页</button>
        <text class="learner-meta">{{ page }} 页 · 共 {{ total }} 条</text>
        <button class="learner-button" :disabled="page * LEARNER_LIST_PAGE_SIZE >= total || busy" @click="turn(1)">下一页</button>
      </view>

      <!-- 「档案与留痕」用底部弹层承载：详情与列表滚动位置解耦，点击后立刻出现在当前视口。 -->
      <view v-if="detail" class="archive-sheet-mask" @click="detail = undefined">
        <view class="archive-sheet" @click.stop>
          <view class="archive-sheet__head">
            <text class="archive-sheet__title">原始记录与档案更正 v{{ detail.version }}</text>
            <text class="archive-sheet__close" @click="detail = undefined">关闭</text>
          </view>
          <text class="learner-meta">更正仅改变档案展示，正式结果、答卷、文件和证书保持原始事实。</text>
          <view class="history-facts">
            <view v-for="(label, field) in HISTORY_FIELDS" :key="field" class="history-facts__row">
              <text class="history-facts__label">{{ label }}</text>
              <text class="history-facts__value">原始 {{ value(detail.original, field) }}</text>
              <text class="history-facts__value history-facts__value--changed">更正展示 {{ value(detail.current, field) }}</text>
            </view>
          </view>
          <text class="learner-title">更正轨迹</text>
          <text v-if="!detail.corrections.length" class="learner-meta">暂无更正记录</text>
          <view v-for="c in detail.corrections" :key="c.correctionId" class="history-trace">
            <text class="learner-meta">v{{ c.version }} · {{ c.operatorName }} · {{ c.operatedTime }}</text>
            <text class="learner-meta">前值：{{ changes(c.before) }}</text>
            <text class="learner-meta">后值：{{ changes(c.after) }}</text>
            <text class="learner-meta">理由：{{ c.reason }}</text>
          </view>
        </view>
      </view>
    </view>
  </MobileBaseLayout>
</template>
<style scoped lang="less">
/* 来源筛选项数量不定，横向换行承载，颜色仍由 learner-button 提供。 */
.history-kinds {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.history-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

/* 状态徽标沿用胶囊语言，仅用语义色区分完成态。 */
.history-badge {
  flex: none;
  padding: 4rpx 18rpx;
  border-radius: 999rpx;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 22rpx;
  line-height: 1.6;
}

.history-badge--done {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}

/* 摘要对比用浅灰字段区，原始值与确认值分行避免误读。 */
.history-facts {
  margin-top: 16rpx;
  padding: 8rpx 24rpx;
  border-radius: 16rpx;
  background: var(--color-bg-subtle);
}

.history-facts__row {
  display: flex;
  padding: 12rpx 0;
  flex-direction: column;
  gap: 6rpx;
}

.history-facts__row + .history-facts__row {
  border-top: 1rpx solid var(--color-bg-surface);
}

.history-facts__label {
  color: var(--color-text-secondary);
  font-size: 22rpx;
}

.history-facts__value {
  color: var(--color-text-primary);
  font-size: 24rpx;
  line-height: 1.6;
}

.history-facts__value--changed {
  color: var(--color-primary);
}

.history-trace {
  display: flex;
  margin-top: 16rpx;
  padding: 20rpx 24rpx;
  flex-direction: column;
  gap: 8rpx;
  border-radius: 16rpx;
  background: var(--color-bg-subtle);
}

/* 「档案与留痕」底部弹层：与课程评价弹层同一交互语言（半透明遮罩 + 白底面板）。 */
.archive-sheet-mask {
  position: fixed;
  z-index: 1000;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: flex-end;
  background: rgb(15 23 42 / 45%);
}

/* 面板自身滚动：详情长度不可控，不让它把整页顶长。 */
.archive-sheet {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  max-height: 82vh;
  overflow-y: auto;
  padding: 32rpx;
  flex-direction: column;
  gap: 16rpx;
  border-radius: 28rpx 28rpx 0 0;
  background: var(--color-bg-surface);
}

.archive-sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.archive-sheet__title {
  flex: 1;
  min-width: 0;
  color: var(--color-text-primary);
  font-size: 30rpx;
  font-weight: 700;
}

.archive-sheet__close {
  flex: none;
  color: var(--color-primary);
  font-size: 26rpx;
}
</style>
