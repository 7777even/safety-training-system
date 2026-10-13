<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import { getAssessmentExecutionErrorMessage } from '@shared/libs/assessment-execution';
import {
  enterTrainingContent,
  getTrainingLearnerMaterialFile,
  getTrainingLearnerMaterialFileUrl,
  getTrainingLearnerMaterials,
  getTrainingLearnerParticipantDetail,
  type TrainingLearnerContent,
  type TrainingLearnerMaterial,
  type TrainingLearnerParticipantDetail
} from '@/api/training-learner';
import { buildMaterialPreviewSource, releaseMaterialPreviewSource, type MaterialPreviewSource } from '@/libs/material-preview';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
// #ifdef H5
import PdfPreview from '@/components/pdf-preview/PdfPreview.vue';
// #endif
const detail = ref<TrainingLearnerParticipantDetail>();
const errorMessage = ref('');
/** 本人参训计划下可读的一般资料；空列表时整个区块不渲染。 */
const materials = ref<TrainingLearnerMaterial[]>([]);
const materialPreviewVisible = ref(false);
const materialPreviewLoading = ref(false);
const materialPreviewTitle = ref('培训资料预览');
const materialPreviewMessage = ref('');
const materialPreviewSource = ref<MaterialPreviewSource | null>(null);
/** 预览请求序号：关闭或切换时作废未完成的请求，避免旧资料覆盖新选择。 */
let materialRequestId = 0;
let participantId = 0;
function planEnded(): boolean {
  return detail.value?.plan?.publishStatus === 'ENDED';
}
function typeText(type: TrainingLearnerContent['contentType']): string {
  return { COURSE: '课程学习', EXAM: '理论考试', QUESTION_BANK: '题库练习', OFFLINE: '线下场次' }[type];
}
function resultFor(id: number) {
  return detail.value?.results.find((item) => item.planContentId === id);
}
/** 结果投影优先于内容快照，避免已恢复作答仍按旧 PENDING 状态进入须知。 */
function contentStatus(content: TrainingLearnerContent): string | undefined {
  return resultFor(content.planContentId)?.resultStatus ?? content.contentStatus;
}
/** 线下未评分时展示教师推进的场次状态；个人成绩形成终态后优先展示个人结果。 */
function displayStatus(content: TrainingLearnerContent): string | undefined {
  const result = resultFor(content.planContentId);
  if (content.contentType === 'OFFLINE' && ['COMPLETED', 'PASSED', 'FAILED'].includes(result?.resultStatus ?? '')) {
    return result?.resultStatus;
  }
  if (content.contentType === 'OFFLINE' && result?.sessionStatus) return result.sessionStatus;
  return contentStatus(content);
}
/** 将后端培训结果状态转换为学员可理解的中文。 */
function statusText(status?: string): string {
  return {
    PENDING: '待开始',
    SCHEDULED: '已安排',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    FAILED: '未通过',
    PENDING_REVIEW: '待阅卷',
    PENDING_PUBLISH: '待发布成绩',
    EXPIRED: '已到期'
  }[status ?? ''] ?? '待开始';
}
/** 待阅卷或待发布的答卷只能查看结果，避免重复创建答卷。 */
function isAwaitingExamResult(content: TrainingLearnerContent): boolean {
  return ['EXAM', 'QUESTION_BANK'].includes(content.contentType)
    && ['COMPLETED', 'FAILED', 'PASSED', 'PENDING_REVIEW', 'PENDING_PUBLISH'].includes(contentStatus(content) ?? '');
}
/** 仅待开始的培训理论考试先读须知；进行中必须通过统一入口恢复原作答。 */
function shouldReadExamNotice(content: TrainingLearnerContent): boolean {
  return content.contentType === 'EXAM' && ['PENDING', 'NOT_STARTED'].includes(contentStatus(content) ?? '');
}
/** 将秒数转换为移动端展示的获得学时，零值同样需要展示。 */
function earnedHoursText(seconds?: number | null): string {
  if (seconds == null) return '-';
  return `${Math.round((seconds / 3600) * 100) / 100} 学时`;
}
/** 为培训内容状态提供统一的语义色，保持状态识别的连续性。 */
function statusTone(status?: string): 'info' | 'primary' | 'success' | 'warning' | 'danger' {
  if (['COMPLETED', 'PASSED'].includes(status ?? '')) return 'success';
  if (['IN_PROGRESS'].includes(status ?? '')) return 'primary';
  if (['FAILED'].includes(status ?? '')) return 'danger';
  if (['PENDING_REVIEW', 'PENDING_PUBLISH'].includes(status ?? '')) return 'warning';
  return 'info';
}
/** 题库练习不产生分数，培训列表不展示历史零分或空成绩。 */
function shouldDisplayScore(content: TrainingLearnerContent): boolean {
  return content.contentType !== 'QUESTION_BANK' && resultFor(content.planContentId)?.score != null;
}
/** 理论考试和题库练习不计培训学时，内容项不展示安排与获得学时。 */
function shouldDisplayTrainingHours(content: TrainingLearnerContent): boolean {
  return !['EXAM', 'QUESTION_BANK'].includes(content.contentType);
}
/** 已完成内容进入结果解析，待阅卷内容只允许回看学员提交的答卷。 */
function contentActionText(content: TrainingLearnerContent): string {
  if (!isAwaitingExamResult(content)) return '进入';
  if (['PENDING_REVIEW', 'PENDING_PUBLISH'].includes(contentStatus(content) ?? '')) return '查看答卷';
  return content.contentType === 'QUESTION_BANK' ? '查看反馈' : '查看结果';
}
/** 终态内容直接读取投影绑定的最终答卷，避免重新进入后看到空白尝试。 */
function resultAttemptId(content: TrainingLearnerContent): number | null {
  return isAwaitingExamResult(content) ? resultFor(content.planContentId)?.finalAttemptId ?? null : null;
}
/** 已结束培训只按详情投影中的既有事实打开只读结果，不能再调用会返回 8154 的统一进入接口。 */
function viewResult(content: TrainingLearnerContent): void {
  const result = resultFor(content.planContentId);
  if (content.contentType === 'COURSE' && result?.sourceRecordType === 'COURSE_ENROLLMENT' && result.sourceRecordId) {
    uni.navigateTo({ url: `/pages/course/learning?enrollmentId=${result.sourceRecordId}` });
    return;
  }
  if (content.contentType === 'OFFLINE' && result?.executionId) {
    uni.navigateTo({ url: `/pages/training/offline-session?offlineSessionId=${result.executionId}` });
    return;
  }
  if (['EXAM', 'QUESTION_BANK'].includes(content.contentType) && result?.finalAttemptId) {
    uni.navigateTo({ url: `/pages/exam/result?examAttemptId=${result.finalAttemptId}` });
    return;
  }
  uni.showToast({ title: '当前内容暂无可查看的明细结果', icon: 'none' });
}
/** 计算概览中的内容总数，避免在模板中堆叠展示逻辑。 */
const contentCount = computed(() => detail.value?.contents.length ?? 0);
/** 以结果投影计算已完成内容数量，确保概览与卡片状态一致。 */
const completedCount = computed(() => detail.value?.contents.filter((content) => ['COMPLETED', 'PASSED'].includes(contentStatus(content) ?? '')).length ?? 0);
/** 汇总已配置的安排学时；没有配置时显示为 0。 */
const requiredHoursTotal = computed(() => detail.value?.contents
  .filter(shouldDisplayTrainingHours)
  .reduce((total, content) => total + (content.requiredHours ?? 0), 0) ?? 0);
async function load(): Promise<void> {
  try {
    detail.value = await getTrainingLearnerParticipantDetail(participantId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '培训详情加载失败';
  }
  await loadMaterials();
}
/**
 * 资料区块是附加事实，读取失败只影响该区块，不把整页打成错误态。
 * 没有可读资料时保持空数组，模板整块不渲染，不出现空区块。
 */
async function loadMaterials(): Promise<void> {
  if (errorMessage.value) {
    materials.value = [];
    return;
  }
  try {
    materials.value = await getTrainingLearnerMaterials(participantId);
  } catch {
    materials.value = [];
  }
}
/** 释放上一份预览持有的本地载体，避免反复预览时保留失效的 Blob。 */
function resetMaterialPreview(): void {
  releaseMaterialPreviewSource(materialPreviewSource.value);
  materialPreviewSource.value = null;
  materialPreviewMessage.value = '';
}
/**
 * 学员侧只做在线预览：经受控端点按资料记录取字节，再按内容判定渲染方式。
 * 学员侧不提供下载入口，因此这里没有另存为或外部打开分支。
 */
async function openMaterialPreview(material: TrainingLearnerMaterial): Promise<void> {
  const requestId = ++materialRequestId;
  resetMaterialPreview();
  materialPreviewTitle.value = material.attachmentName || '培训资料预览';
  materialPreviewVisible.value = true;
  materialPreviewLoading.value = true;
  try {
    const bytes = await getTrainingLearnerMaterialFile(participantId, material.trainingAttachmentId);
    if (!materialPreviewVisible.value || requestId !== materialRequestId) return;
    const source = buildMaterialPreviewSource(
      getTrainingLearnerMaterialFileUrl(participantId, material.trainingAttachmentId),
      bytes
    );
    materialPreviewSource.value = source;
    if (source.kind === 'unsupported') {
      materialPreviewMessage.value = '当前移动端不支持在线预览该格式的资料，请在 PC 端查看。';
    }
  } catch (error) {
    if (requestId === materialRequestId) {
      materialPreviewMessage.value = error instanceof Error ? error.message : '培训资料预览失败，请稍后重试。';
    }
  } finally {
    if (requestId === materialRequestId) materialPreviewLoading.value = false;
  }
}
/** 关闭时作废未完成的请求并释放本地载体。 */
function closeMaterialPreview(): void {
  materialRequestId += 1;
  materialPreviewVisible.value = false;
  materialPreviewLoading.value = false;
  resetMaterialPreview();
}
function token(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
}
async function enter(content: TrainingLearnerContent): Promise<void> {
  if (planEnded()) {
    viewResult(content);
    return;
  }
  try {
    if (shouldReadExamNotice(content)) {
      uni.navigateTo({ url: `/pages/exam/notice?trainingParticipantId=${participantId}&planContentId=${content.planContentId}` });
      return;
    }
    const finalAttemptId = resultAttemptId(content);
    if (finalAttemptId) {
      uni.navigateTo({ url: `/pages/exam/result?examAttemptId=${finalAttemptId}` });
      return;
    }
    const data = await enterTrainingContent(
      participantId,
      content.planContentId,
      ['EXAM', 'QUESTION_BANK'].includes(content.contentType) ? { clientAttemptToken: token(), platform: 'MOBILE' } : undefined
    );
    if (data.contentType === 'COURSE' && data.courseEnrollmentId)
      uni.navigateTo({ url: `/pages/course/detail?enrollmentId=${data.courseEnrollmentId}` });
    else if ((data.contentType === 'EXAM' || data.contentType === 'QUESTION_BANK') && data.examAttemptId)
      uni.navigateTo({ url: isAwaitingExamResult(content) ? `/pages/exam/result?examAttemptId=${data.examAttemptId}` : `/pages/exam/attempt?examAttemptId=${data.examAttemptId}` });
    else if (data.contentType === 'OFFLINE' && data.offlineSessionId)
      uni.navigateTo({ url: `/pages/training/offline-session?offlineSessionId=${data.offlineSessionId}` });
    else uni.showToast({ title: '后端未返回可进入内容', icon: 'none' });
  } catch (error) {
    uni.showToast({ title: getAssessmentExecutionErrorMessage(error, '进入失败'), icon: 'none' });
  }
}
onLoad((query) => {
  participantId = Number(query?.trainingParticipantId);
});
/** 从课程、考试或线下页返回时重读后端结果投影。 */
onShow(() => {
  if (participantId) void load();
});
</script>
<template>
  <MobileBaseLayout>
    <view class="page">
      <view v-if="errorMessage" class="state">
        <text>{{ errorMessage }}</text>
        <button @click="load">重新加载</button>
      </view>
      <template v-else-if="detail">
        <view class="heading">
          <text class="eyebrow">TRAINING SPACE</text>
          <text class="title">培训详情</text>
          <text class="subtitle">{{ planEnded() ? '培训计划已结束，仅可查看结果' : '完成培训内容后查看分项结果' }}</text>
        </view>
        <view class="overview">
          <view class="overview__glow" />
          <view class="overview__content">
            <text class="overview__label">当前培训计划</text>
            <text class="overview__name">{{ detail.plan?.planName ?? '未命名培训计划' }}</text>
            <view class="overview__status" :class="`overview__status--${planEnded() ? 'ended' : 'running'}`">
              <text class="overview__status-dot" />
              <text>{{ planEnded() ? '已结束' : '进行中' }}</text>
            </view>
          </view>
          <view class="overview__stats">
            <view class="overview__stat">
              <text class="overview__stat-value">{{ contentCount }}</text>
              <text class="overview__stat-label">内容总数</text>
            </view>
            <view class="overview__stat">
              <text class="overview__stat-value">{{ completedCount }}</text>
              <text class="overview__stat-label">已完成</text>
            </view>
            <view class="overview__stat">
              <text class="overview__stat-value">{{ requiredHoursTotal }}</text>
              <text class="overview__stat-label">安排学时</text>
            </view>
          </view>
        </view>
        <view class="list">
          <view v-for="(content, index) in detail.contents" :key="content.planContentId" class="card">
            <view class="card__index">{{ String(index + 1).padStart(2, '0') }}</view>
            <view class="card__body">
              <view class="card__header">
                <text class="type">{{ typeText(content.contentType) }}</text>
                <text class="content-status" :class="`content-status--${statusTone(displayStatus(content))}`">{{ statusText(displayStatus(content)) }}</text>
              </view>
              <text class="name">{{ content.contentName }}</text>
              <view class="meta-list">
                <text class="meta-chip">{{ content.isRequired === 1 ? '必修内容' : '选修内容' }}</text>
                <text v-if="shouldDisplayTrainingHours(content) && content.requiredHours != null" class="meta-chip">安排 {{ content.requiredHours }} 学时</text>
                <text v-if="shouldDisplayTrainingHours(content) && resultFor(content.planContentId)?.effectiveDurationSeconds != null" class="meta-chip">获得 {{ earnedHoursText(resultFor(content.planContentId)?.effectiveDurationSeconds) }}</text>
                <text v-if="shouldDisplayScore(content)" class="meta-chip">成绩 {{ resultFor(content.planContentId)?.score }} 分</text>
              </view>
            </view>
            <button v-if="!planEnded()" class="enter-button" @click="enter(content)">{{ contentActionText(content) }} <text class="enter-button__arrow">›</text></button>
            <button v-else class="result" @click="viewResult(content)">查看结果 <text class="result__arrow">›</text></button>
          </view>
        </view>
        <view v-if="materials.length" class="materials">
          <view class="materials__heading">
            <text class="materials__title">培训资料</text>
            <text class="materials__count">共 {{ materials.length }} 份</text>
          </view>
          <view class="material-list">
            <view v-for="material in materials" :key="material.trainingAttachmentId" class="material-card">
              <view class="material-card__body">
                <text class="material-card__name">{{ material.attachmentName }}</text>
                <view class="meta-list">
                  <text class="meta-chip">{{ material.stageName || '未关联阶段' }}</text>
                  <text v-if="material.description" class="meta-chip">{{ material.description }}</text>
                </view>
              </view>
              <button v-if="material.hasFile" class="material-card__button" @click="openMaterialPreview(material)">预览</button>
              <text v-else class="material-card__empty">暂无文件</text>
            </view>
          </view>
        </view>
        <view v-if="materialPreviewVisible" class="material-preview">
          <view class="material-preview__bar">
            <text class="material-preview__title">{{ materialPreviewTitle }}</text>
            <button class="material-preview__close" @click="closeMaterialPreview">关闭</button>
          </view>
          <view v-if="materialPreviewLoading" class="material-preview__state"><text>正在打开资料…</text></view>
          <view v-else-if="materialPreviewMessage" class="material-preview__state"><text>{{ materialPreviewMessage }}</text></view>
          <scroll-view v-else-if="materialPreviewSource?.kind === 'image'" class="material-preview__scroll" scroll-y>
            <image class="material-preview__image" :src="materialPreviewSource.imageUrl" mode="widthFix" />
          </scroll-view>
          <view v-else-if="materialPreviewSource?.kind === 'pdf'" class="material-preview__document">
            <!-- #ifdef H5 -->
            <PdfPreview
              :data="materialPreviewSource.pdfBytes"
              :file-name="materialPreviewTitle"
              @error="materialPreviewMessage = $event"
            />
            <!-- #endif -->
            <!-- #ifndef H5 -->
            <view class="material-preview__state"><text>当前环境请使用 H5 或 PC 端查看 PDF 资料。</text></view>
            <!-- #endif -->
          </view>
          <view v-else class="material-preview__state"><text>资料暂时无法预览。</text></view>
        </view>
      </template>
    </view>
  </MobileBaseLayout>
</template>
<style scoped lang="less">
.page {
  padding: 28rpx 0 72rpx;
}
.heading {
  display: flex;
  flex-direction: column;
  padding: 18rpx 8rpx 26rpx;
}
.eyebrow {
  color: #2563eb;
  font-size: 21rpx;
  font-weight: 700;
  letter-spacing: 3rpx;
}
.title {
  margin-top: 14rpx;
  color: #17181c;
  font-size: 48rpx;
  font-weight: 700;
}
.subtitle {
  margin-top: 12rpx;
  color: #8d8f96;
  font-size: 24rpx;
}
.overview {
  display: flex;
  position: relative;
  min-height: 210rpx;
  margin-bottom: 24rpx;
  padding: 28rpx;
  overflow: hidden;
  align-items: stretch;
  justify-content: space-between;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #1d4ed8 0%, #2563eb 58%, #38bdf8 100%);
  box-shadow: 0 14rpx 30rpx rgb(37 99 235 / 18%);
  color: #fff;
}
.overview__glow {
  position: absolute;
  width: 240rpx;
  height: 240rpx;
  right: -78rpx;
  bottom: -118rpx;
  border: 1rpx solid rgb(255 255 255 / 22%);
  border-radius: 50%;
  box-shadow: 0 0 0 28rpx rgb(255 255 255 / 7%), 0 0 0 58rpx rgb(255 255 255 / 5%);
}
.overview__content,
.overview__stats {
  position: relative;
  z-index: 1;
}
.overview__content {
  display: flex;
  max-width: 58%;
  flex-direction: column;
}
.overview__label {
  color: rgb(255 255 255 / 72%);
  font-size: 22rpx;
}
.overview__name {
  margin-top: 16rpx;
  overflow: hidden;
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.overview__status {
  display: inline-flex;
  width: fit-content;
  margin-top: auto;
  padding: 8rpx 14rpx;
  align-items: center;
  border-radius: 999rpx;
  background: rgb(255 255 255 / 16%);
  color: #fff;
  font-size: 21rpx;
}
.overview__status--ended {
  background: rgb(15 23 42 / 22%);
}
.overview__status-dot {
  width: 10rpx;
  height: 10rpx;
  margin-right: 8rpx;
  border-radius: 50%;
  background: #86efac;
}
.overview__status--ended .overview__status-dot {
  background: #cbd5e1;
}
.overview__stats {
  display: flex;
  align-items: flex-end;
  gap: 24rpx;
}
.overview__stat {
  display: flex;
  min-width: 60rpx;
  flex-direction: column;
  align-items: center;
}
.overview__stat-value {
  font-size: 38rpx;
  font-weight: 700;
  line-height: 1;
}
.overview__stat-label {
  margin-top: 10rpx;
  color: rgb(255 255 255 / 72%);
  font-size: 20rpx;
  white-space: nowrap;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.card {
  display: flex;
  position: relative;
  padding: 28rpx 24rpx 26rpx;
  align-items: flex-start;
  gap: 18rpx;
  border: 1rpx solid #edf2f7;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgb(38 39 45 / 5%);
}
.card__index {
  display: flex;
  width: 54rpx;
  height: 54rpx;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  background: #eff6ff;
  color: #2563eb;
  font-size: 21rpx;
  font-weight: 700;
}
.card__body {
  min-width: 0;
  flex: 1;
}
.card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}
.type,
.name,
.meta-chip,
.result {
  display: block;
}
.type {
  color: #2563eb;
  font-size: 21rpx;
}
.content-status {
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  white-space: nowrap;
}
.content-status--info {
  background: #f1f5f9;
  color: #64748b;
}
.content-status--primary {
  background: #dbeafe;
  color: #2563eb;
}
.content-status--success {
  background: #dcfce7;
  color: #15803d;
}
.content-status--warning {
  background: #fef3c7;
  color: #b45309;
}
.content-status--danger {
  background: #fee2e2;
  color: #dc2626;
}
.name {
  margin-top: 14rpx;
  overflow: hidden;
  color: #17181c;
  font-size: 31rpx;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  // white-space: nowrap;
}
.meta-list {
  display: flex;
  margin-top: 18rpx;
  flex-wrap: wrap;
  gap: 10rpx;
}
.meta-chip {
  padding: 7rpx 12rpx;
  border-radius: 8rpx;
  background: #f8fafc;
  color: #7b8494;
  font-size: 20rpx;
  line-height: 1.2;
}
.enter-button {
  display: flex;
  min-width: 112rpx;
  height: 64rpx;
  margin: 46rpx 0 0;
  padding: 0 16rpx;
  flex: none;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 14rpx;
  background: #2563eb;
  color: #fff;
  font-size: 23rpx;
  line-height: 1;
}
.enter-button__arrow,
.result__arrow {
  margin-left: 6rpx;
  font-size: 30rpx;
  line-height: 1;
}
.result {
  flex: none;
  margin-top: 52rpx;
  padding: 0;
  border: 0;
  background: transparent;
  color: #2563eb;
  font-size: 22rpx;
  white-space: nowrap;
}
.result::after {
  border: 0;
}
.state {
  display: flex;
  min-height: 250rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 24rpx;
  color: #777981;
  font-size: 26rpx;
}
.state button {
  margin: 0;
  background: #2563eb;
  color: #fff;
  font-size: 25rpx;
}
/* 培训资料：一行一份，窄屏单列；列表自身不承担滚动，避免撑破页面。 */
.materials {
  margin-top: 36rpx;
}
.materials__heading {
  display: flex;
  padding: 0 8rpx 20rpx;
  align-items: baseline;
  justify-content: space-between;
}
.materials__title {
  color: #17181c;
  font-size: 32rpx;
  font-weight: 700;
}
.materials__count {
  color: #8d8f96;
  font-size: 22rpx;
}
.material-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.material-card {
  display: flex;
  padding: 26rpx 24rpx;
  align-items: center;
  gap: 18rpx;
  border: 1rpx solid #edf2f7;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgb(38 39 45 / 5%);
}
.material-card__body {
  min-width: 0;
  flex: 1;
}
.material-card__name {
  display: block;
  color: #17181c;
  font-size: 29rpx;
  font-weight: 700;
  line-height: 1.35;
}
.material-card__button {
  display: flex;
  min-width: 104rpx;
  height: 60rpx;
  margin: 0;
  padding: 0 18rpx;
  flex: none;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #bfdbfe;
  border-radius: 14rpx;
  background: #eff6ff;
  color: #2563eb;
  font-size: 23rpx;
  line-height: 1;
}
.material-card__button::after {
  border: 0;
}
.material-card__empty {
  flex: none;
  color: #b6b8bf;
  font-size: 22rpx;
}
/* 预览层覆盖整屏：层级必须高于 uni 页面头部（uni-page-head 为 998），
   否则标题栏与关闭按钮会被原生头部盖住；正文自身滚动，长文档不会把标题栏顶出视口。 */
.material-preview {
  display: flex;
  position: fixed;
  z-index: 1000;
  inset: 0;
  flex-direction: column;
  background: #f6f7f9;
}
.material-preview__bar {
  display: flex;
  min-height: 96rpx;
  padding: 0 28rpx;
  padding-top: constant(safe-area-inset-top);
  padding-top: env(safe-area-inset-top);
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  border-bottom: 1rpx solid #f1f1f3;
  background: #fff;
}
.material-preview__title {
  overflow: hidden;
  color: #17181c;
  font-size: 28rpx;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.material-preview__close {
  margin: 0;
  padding: 0 24rpx;
  border: 0;
  border-radius: 28rpx;
  background: #f1f5f9;
  color: #475569;
  font-size: 23rpx;
  line-height: 56rpx;
}
.material-preview__close::after {
  border: 0;
}
.material-preview__scroll,
.material-preview__document {
  min-height: 0;
  flex: 1;
}
.material-preview__image {
  display: block;
  width: 100%;
}
.material-preview__state {
  display: flex;
  padding: 48rpx 32rpx;
  flex: 1;
  align-items: center;
  justify-content: center;
  color: #8d8f96;
  font-size: 25rpx;
  text-align: center;
}
</style>
