<script setup lang="ts">
import type { LearnerCourseCategory, LearnerCourseEnrollment, LearnerCourseOffering } from '@shared/types';
import { onShow } from '@dcloudio/uni-app';
import { computed, onBeforeUnmount, ref } from 'vue';
import { getLearnerCourseCategories, getLearnerCoursePage, getLearnerOfferingPage, selfEnrollCourse } from '@/api/learner-course';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { loadProtectedImageUrl, revokeProtectedImageUrl } from '@/libs/protected-image';
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue';

const courses = ref<LearnerCourseEnrollment[]>([]);
/** 按接口分类树生成选择项，名称和层级来自管理端配置。 */
const categories = ref<LearnerCourseCategory[]>([]);
const categoryIndex = ref(0);
/** 课程名称使用现有keyword契约，与手动分类组合筛选。 */
const offeringKeyword = ref('');
const categoryOptions = computed(() => {
  const options: Array<{ id?: number; label: string }> = [{ label: '全部分区／岗位分类' }];
  const pending = categories.value.map(node => ({ node, prefix: '' }));
  while (pending.length) {
    const current = pending.shift()!;
    const label = `${current.prefix}${current.node.categoryName}`;
    options.push({ id: current.node.courseCategoryId, label });
    pending.unshift(...current.node.children.map(node => ({ node, prefix: `${label} / ` })));
  }
  return options;
});
function changeCategory(event: { detail: { value: string | number } }): void {
  categoryIndex.value = Number(event.detail.value);
  void loadOfferings();
}
const loading = ref(false);
const errorMessage = ref('');
const courseCoverUrls = ref<Record<number, string | null>>({});
/** 当前选中的学习状态；空值表示展示全部课程。 */
const activeLearningStatus = ref<LearnerCourseEnrollment['learningStatus'] | ''>('');
/** 可自主报名的开课，与已报名课程分区展示。 */
const offerings = ref<LearnerCourseOffering[]>([]);
const offeringCoverUrls = ref<Record<number, string | null>>({});
const offeringsLoading = ref(false);
/** 正在提交报名的开课 ID，避免重复点击产生并发请求。 */
const enrollingId = ref<number | null>(null);
let courseLoadVersion = 0;
let offeringLoadVersion = 0;

/** 学习状态筛选项，与学员课程 Contract 枚举保持一致。 */
const learningStatusTabs: Array<{ label: string; value: LearnerCourseEnrollment['learningStatus'] | '' }> = [
  { label: '全部', value: '' },
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '学习中', value: 'LEARNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已过期', value: 'EXPIRED' }
];

/** 将后端学习状态转换为学员可读的状态文案。 */
function statusText(status: LearnerCourseEnrollment['learningStatus']): string {
  return { NOT_STARTED: '未开始', LEARNING: '学习中', COMPLETED: '已完成', EXPIRED: '已过期' }[status];
}

/** 将课程类型显示为必修/选修，避免把 Contract 枚举直接暴露给学员。 */
function courseTypeText(courseType: LearnerCourseEnrollment['courseType']): string {
  return courseType === 'REQUIRED' ? '必修课' : '选修课';
}

/** 学习状态标签配色：完成用成功色、过期用中性色、其余用主色，与 PC 学习中心同一口径。 */
function statusTone(status: LearnerCourseEnrollment['learningStatus']): 'primary' | 'success' | 'info' {
  if (status === 'COMPLETED') return 'success';
  if (status === 'EXPIRED') return 'info';
  return 'primary';
}

/** 开课期间收敛为一条文案；两端都未设置时说明长期开放，避免输出「未设置 至 未设置」。 */
function getPeriodText(start: string | null, end: string | null): string {
  const from = start?.slice(0, 10) ?? '';
  const to = end?.slice(0, 10) ?? '';
  if (!from && !to) return '长期开放';
  if (!from) return `截至 ${to}`;
  if (!to) return `${from} 起`;
  return `${from} ~ ${to}`;
}

/** 卡片主操作文案与 PC 学习中心保持一致，避免已完成课程仍提示「开始学习」。 */
function courseActionText(course: LearnerCourseEnrollment): string {
  return course.learningStatus === 'COMPLETED' ? '查看课程与评价' : course.learningStatus === 'EXPIRED' ? '查看详情' : '进入学习';
}

/** 加载当前登录人可访问的课程，状态筛选与 PC 学习中心使用同一查询参数。 */
async function loadCourses(): Promise<void> {
  const loadVersion = ++courseLoadVersion;
  loading.value = true;
  errorMessage.value = '';
  try {
    const page = await getLearnerCoursePage({
      pageNum: 1,
      pageSize: 20,
      ...(activeLearningStatus.value ? { 'query.learningStatus': activeLearningStatus.value } : {})
    });
    if (loadVersion !== courseLoadVersion) return;
    Object.values(courseCoverUrls.value).forEach(revokeProtectedImageUrl);
    courseCoverUrls.value = {};
    courses.value = page.list;
    await loadCourseCovers(courses.value, loadVersion);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

/** 加载已配置的课程封面；失败时使用文字占位，不影响课程入口。 */
async function loadCourseCovers(items: LearnerCourseEnrollment[], loadVersion: number): Promise<void> {
  const results = await Promise.all(
    items.map(async (course) => {
      if (!course.coverUrl) return [course.courseId, null] as const;
      try {
        return [course.courseId, await loadProtectedImageUrl(course.coverUrl)] as const;
      } catch {
        return [course.courseId, null] as const;
      }
    })
  );
  if (loadVersion === courseLoadVersion) courseCoverUrls.value = Object.fromEntries(results);
}

/** 选课方式文案，与 PC 学习中心的字段口径保持一致。 */
function enrollmentTypeText(enrollmentType: LearnerCourseOffering['enrollmentType']): string {
  return enrollmentType === 'OPEN' ? '公开选课' : '链接选课';
}

/** 切换学习状态筛选后重新查询，避免沿用上一状态的列表。 */
async function changeLearningStatus(status: LearnerCourseEnrollment['learningStatus'] | ''): Promise<void> {
  if (activeLearningStatus.value === status) return;
  activeLearningStatus.value = status;
  await loadCourses();
}

/** 加载当前可自主报名的开课；该分区失败只清空自身，不阻断我的课程。 */
async function loadOfferings(): Promise<void> {
  const loadVersion = ++offeringLoadVersion;
  offeringsLoading.value = true;
  try {
    categories.value = await getLearnerCourseCategories();
    const page = await getLearnerOfferingPage({ pageNum: 1, pageSize: 20, 'query.categoryId': categoryOptions.value[categoryIndex.value]?.id, 'query.keyword': offeringKeyword.value.trim() || undefined });
    if (loadVersion !== offeringLoadVersion) return;
    Object.values(offeringCoverUrls.value).forEach(revokeProtectedImageUrl);
    offeringCoverUrls.value = {};
    offerings.value = page.list;
    await loadOfferingCovers(page.list, loadVersion);
  } catch {
    if (loadVersion === offeringLoadVersion) offerings.value = [];
  } finally {
    offeringsLoading.value = false;
  }
}

/** 按开课 ID 读取受控封面，避免同一课程的不同开课互相覆盖。 */
async function loadOfferingCovers(items: LearnerCourseOffering[], loadVersion: number): Promise<void> {
  const results = await Promise.all(
    items.map(async (offering) => {
      if (!offering.coverUrl) return [offering.offeringId, null] as const;
      try {
        return [offering.offeringId, await loadProtectedImageUrl(offering.coverUrl)] as const;
      } catch {
        return [offering.offeringId, null] as const;
      }
    })
  );
  if (loadVersion === offeringLoadVersion) offeringCoverUrls.value = Object.fromEntries(results);
}

/** 自助报名；后端幂等，成功后刷新我的课程让新报名立即可见。 */
async function enrollOffering(offering: LearnerCourseOffering): Promise<void> {
  if (enrollingId.value !== null) return;
  enrollingId.value = offering.offeringId;
  try {
    await selfEnrollCourse(offering.offeringId);
    uni.showToast({ title: '选课成功，已加入我的课程', icon: 'none' });
    await Promise.all([loadCourses(), loadOfferings()]);
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '选课失败，请稍后重试', icon: 'none' });
  } finally {
    enrollingId.value = null;
  }
}

/** 进入课程学习详情页。 */
function openCourse(course: LearnerCourseEnrollment): void {
  uni.navigateTo({ url: `/pages/course/detail?enrollmentId=${course.enrollmentId}` });
}

onShow(() => {
  void loadCourses();
  void loadOfferings();
});
onBeforeUnmount(() => {
  Object.values(courseCoverUrls.value).forEach(revokeProtectedImageUrl);
  Object.values(offeringCoverUrls.value).forEach(revokeProtectedImageUrl);
});
</script>

<template>
  <MobileBaseLayout>
    <view class="page">
      <view class="page-hero">
        <view class="page-hero__deco" />
        <text class="page-hero__eyebrow">MY LEARNING</text>
        <text class="page-hero__title">我的课程</text>
        <text class="page-hero__subtitle">继续完成你的安全教育学习任务</text>
      </view>

      <view class="segmented-filter">
        <text
          v-for="tab in learningStatusTabs"
          :key="tab.value || 'ALL'"
          class="segmented-filter__item"
          :class="{ 'segmented-filter__item--active': activeLearningStatus === tab.value }"
          @click="changeLearningStatus(tab.value)"
        >
          {{ tab.label }}
        </text>
      </view>

      <view v-if="loading" class="state-panel"><text>正在加载课程…</text></view>
      <view v-else-if="errorMessage" class="state-panel">
        <text>{{ errorMessage }}</text>
        <button class="state-panel__button" @click="loadCourses">重新加载</button>
      </view>
      <view v-else-if="courses.length === 0" class="state-panel">
        <text>{{ activeLearningStatus ? '该状态下暂无课程' : '暂无可学习课程' }}</text>
      </view>
      <view v-else class="course-list">
        <view v-for="course in courses" :key="course.enrollmentId" class="course-card" @click="openCourse(course)">
          <view class="course-card__top">
            <image
              v-if="courseCoverUrls[course.courseId]"
              class="course-card__cover"
              :src="courseCoverUrls[course.courseId] || ''"
              mode="aspectFill"
            />
            <view v-else class="course-card__cover course-card__cover--placeholder">
              <UniIcons type="image" size="30" color="#2563eb" />
            </view>
            <view class="course-card__identity">
              <text class="course-name">{{ course.courseName }}</text>
              <text class="offering-name">{{ course.offeringName }}</text>
              <view class="course-card__chips">
                <text class="course-type">{{ courseTypeText(course.courseType) }}</text>
                <text class="course-type course-type--plain">
                  开放期间 {{ getPeriodText(course.startTime, course.endTime) }}
                </text>
                <text class="status-chip" :class="`status-chip--${statusTone(course.learningStatus)}`">
                  {{ statusText(course.learningStatus) }}
                </text>
              </view>
            </view>
          </view>

          <view class="course-progress">
            <view class="course-progress__heading">
              <text>学习进度</text>
              <text :class="['course-progress__value', { complete: course.progressPercent >= 100 }]">
                {{ course.progressPercent }}%
              </text>
            </view>
            <view class="course-progress__track">
              <view
                class="course-progress__bar"
                :class="{ complete: course.progressPercent >= 100 }"
                :style="{ width: `${course.progressPercent}%` }"
              />
            </view>
          </view>

          <view class="course-card__actions">
            <button class="course-card__button" @click.stop="openCourse(course)">{{ courseActionText(course) }}</button>
          </view>
        </view>
      </view>

      <view class="offering-section">
        <view class="offering-head">
          <view class="offering-head__text">
            <text class="section-heading__title">可报名课程</text>
            <text class="section-heading__hint">仅展示当前可自主报名的开课</text>
          </view>
          <picker
            class="offering-filter"
            :range="categoryOptions"
            range-key="label"
            :value="categoryIndex"
            @change="changeCategory"
          >
            <view class="offering-filter__field">
              <text class="offering-filter__label">{{ categoryOptions[categoryIndex]?.label || '全部分区／岗位分类' }}</text>
              <UniIcons type="bottom" size="12" color="#64748b" />
            </view>
          </picker>
        </view>
        <view class="offering-search">
          <input v-model="offeringKeyword" class="offering-search__input" placeholder="请输入课程名称" confirm-type="search" @confirm="loadOfferings" />
          <button class="offering-search__button" @tap="loadOfferings">查询</button>
        </view>
        <view v-if="offeringsLoading" class="state-panel"><text>正在加载可报名课程…</text></view>
        <view v-else-if="!offerings.length" class="state-panel"><text>当前暂无可报名课程</text></view>
        <view v-else class="course-list">
          <view v-for="offering in offerings" :key="offering.offeringId" class="course-card">
            <view class="course-card__top">
              <image
                v-if="offeringCoverUrls[offering.offeringId]"
                class="course-card__cover"
                :src="offeringCoverUrls[offering.offeringId] || ''"
                mode="aspectFill"
              />
              <view v-else class="course-card__cover course-card__cover--placeholder">
                <UniIcons type="image" size="30" color="#2563eb" />
              </view>
              <view class="course-card__identity">
                <text class="course-name">{{ offering.courseName }}</text>
                <text class="offering-name">{{ offering.offeringName }}</text>
                <view class="course-card__chips">
                  <text class="course-type">{{ courseTypeText(offering.courseType) }}</text>
                  <text class="course-type course-type--plain">
                    开课期间 {{ getPeriodText(offering.startTime, offering.endTime) }}
                  </text>
                  <text class="status-chip status-chip--primary">{{ enrollmentTypeText(offering.enrollmentType) }}</text>
                </view>
              </view>
            </view>

            <view class="course-card__actions">
              <button
                class="course-card__button"
                :disabled="enrollingId === offering.offeringId"
                @click="enrollOffering(offering)"
              >
                {{ enrollingId === offering.offeringId ? '选课中…' : '立即选课' }}
              </button>
            </view>
          </view>
        </view>
      </view>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.page {
  padding: 24rpx 0 64rpx;
}

/* 页头 hero：与 PC 门户列表页同源的蓝色渐变横幅，加一层装饰高光提升层次。 */
.page-hero {
  position: relative;
  display: flex;
  overflow: hidden;
  padding: 40rpx 36rpx;
  flex-direction: column;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #155eef 0%, #4f8bff 100%);
  box-shadow: 0 16rpx 32rpx rgb(21 94 239 / 18%);
}

.page-hero__deco {
  position: absolute;
  top: -90rpx;
  right: -70rpx;
  width: 280rpx;
  height: 280rpx;
  border-radius: 50%;
  background: rgb(255 255 255 / 14%);
}

.page-hero__eyebrow,
.page-hero__title,
.page-hero__subtitle {
  position: relative;
  z-index: 1;
}

.page-hero__eyebrow {
  color: rgb(255 255 255 / 82%);
  font-size: 21rpx;
  font-weight: 700;
  letter-spacing: 3rpx;
}

.page-hero__title {
  margin-top: 14rpx;
  color: #fff;
  font-size: 48rpx;
  font-weight: 700;
}

.page-hero__subtitle {
  margin-top: 12rpx;
  color: rgb(255 255 255 / 78%);
  font-size: 25rpx;
  line-height: 1.5;
}

/* 状态筛选复用全局分段控件：与考试页保持同一条不换行的筛选栏。 */
.course-list {
  display: flex;
  margin-top: 26rpx;
  flex-direction: column;
  gap: 22rpx;
}

.course-card {
  display: flex;
  padding: 32rpx;
  flex-direction: column;
  border-radius: 24rpx;
  background: var(--color-bg-surface, #fff);
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 6%);
}

.course-card__top {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 24rpx;
}

.course-card__cover {
  width: 176rpx;
  height: 176rpx;
  flex: none;
  border-radius: 18rpx;
  background: var(--color-primary-subtle, #eff6ff);
}

.course-card__cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.course-card__identity {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.course-name,
.offering-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.course-name {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  color: var(--color-text-primary, #0f172a);
  font-size: 33rpx;
  font-weight: 700;
  line-height: 1.4;
}

.offering-name {
  margin-top: 10rpx;
  color: var(--color-text-secondary, #64748b);
  font-size: 24rpx;
  white-space: nowrap;
}

.course-card__chips {
  display: flex;
  margin-top: 18rpx;
  flex-wrap: wrap;
  gap: 12rpx;
}

.course-type,
.status-chip {
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  font-size: 21rpx;
  line-height: 1.4;
}

/* 期间等元信息用中性胶囊，与蓝色类别标签拉开层次且不再独占一只灰色字段盒。 */
.course-type--plain {
  max-width: 100%;
  overflow: hidden;
  background: var(--color-bg-subtle, #f1f5f9);
  color: var(--color-text-regular, #475569);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-type {
  background: var(--color-primary-subtle, #eff6ff);
  color: var(--color-primary, #2563eb);
}

.status-chip {
  border-radius: 999rpx;
}

.status-chip--primary {
  background: #dbeafe;
  color: #2563eb;
}

.status-chip--success {
  background: #dcfce7;
  color: #15803d;
}

.status-chip--info {
  background: #f1f5f9;
  color: #64748b;
}

.course-progress {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #f1f1f3;
}

.course-progress__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14rpx;
  color: var(--color-text-regular, #475569);
  font-size: 24rpx;
}

.course-progress__value {
  color: var(--color-primary, #2563eb);
  font-size: 27rpx;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.course-progress__value.complete {
  color: #15803d;
}

.course-progress__track {
  height: 12rpx;
  overflow: hidden;
  border-radius: 999rpx;
  background: #e8eef7;
}

.course-progress__bar {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #4f8bff);
}

.course-progress__bar.complete {
  background: linear-gradient(90deg, #15803d, #34d399);
}

.course-card__actions {
  display: flex;
  margin-top: 26rpx;
  justify-content: flex-end;
}

.course-card__button,
.state-panel__button {
  min-width: 180rpx;
  margin: 0;
  padding: 0 34rpx;
  border: 0;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #2563eb 0%, #4f8bff 100%);
  color: #fff;
  font-size: 26rpx;
  line-height: 68rpx;
  text-align: center;
  box-shadow: 0 10rpx 20rpx rgb(37 99 235 / 22%);
}

.course-card__button::after,
.state-panel__button::after {
  border: 0;
}

/* 报名提交中禁用按钮，避免重复点击生成并发报名请求。 */
.course-card__button[disabled] {
  opacity: 0.62;
}

/* 可报名课程分区：用分隔线与标题和我的课程断开，避免两段列表被读成同一组。 */
.offering-section {
  margin-top: 40rpx;
  padding-top: 32rpx;
  border-top: 1rpx solid #eef0f4;
}

.section-heading__title {
  color: var(--color-text-primary, #0f172a);
  font-size: 32rpx;
  font-weight: 700;
}

.section-heading__hint {
  margin-top: 8rpx;
  color: var(--color-text-secondary, #64748b);
  font-size: 22rpx;
}

/* 分区头：标题与说明左对齐成两行，分类筛选收敛为右侧紧凑胶囊，避免整块灰底占满一行。 */
.offering-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.offering-head__text {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.offering-search {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.offering-search__input {
  flex: 1;
  min-width: 0;
  height: 88rpx;
  padding: 0 24rpx;
  border: 1px solid var(--color-border);
  border-radius: 16rpx;
  background: var(--color-bg-surface);
  font-size: 28rpx;
}
.offering-search__button {
  margin: 0;
  padding: 0 24rpx;
  background: var(--color-primary);
  color: #fff;
  font-size: 28rpx;
  line-height: 88rpx;
}

.offering-filter {
  flex: none;
  max-width: 300rpx;
}

.offering-filter__field {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  border: 1rpx solid #e2e8f0;
  border-radius: 999rpx;
  background: var(--color-bg-surface, #fff);
}

.offering-filter__label {
  overflow: hidden;
  max-width: 224rpx;
  color: var(--color-text-regular, #475569);
  font-size: 23rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.state-panel {
  display: flex;
  margin-top: 26rpx;
  padding: 72rpx 28rpx;
  align-items: center;
  flex-direction: column;
  gap: 26rpx;
  border-radius: 24rpx;
  background: var(--color-bg-surface, #fff);
  color: var(--color-text-secondary, #64748b);
  font-size: 26rpx;
}
</style>
