<script setup lang="ts">
import { orderCourseChapters } from '@shared/libs/course-chapters';
import type { CourseEvaluationListItem, LearnerCourseDetail, LearnerCourseTaskItem, LearnerCourseTasks } from '@shared/types';
import { getAssessmentExecutionErrorMessage } from '@shared/libs/assessment-execution';
import { formatAttachmentSize, getDownloadUnavailableReasonText } from '@shared/utils/attachment';
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { participateCourseLive, downloadLearnerCourseAsset, getLearnerCourseDetail, getLearnerCourseTasks, getPeerCourseEvaluations, startCourseExamAttempt, submitCourseEvaluation } from '@/api/learner-course';
import { COURSE_ASSESSMENT_PAGE, COURSE_ASSESSMENT_TASK_STATUS } from '@/constants';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

const tasks = ref<LearnerCourseTasks | null>(null);
const enrollmentDetail = ref<LearnerCourseDetail | null>(null);
const activeInfoTab = ref<'INTRODUCTION' | 'DOWNLOADS' | 'EVALUATIONS' | 'REPORT'>('INTRODUCTION');
const loading = ref(false);
const errorMessage = ref('');
const downloadingAssetId = ref<number | null>(null);
const openingAssessmentItemId = ref<number | null>(null);
/** 课程评价仅针对已完成且尚未评价的本人报名。 */
const evaluationForm = ref({ rating: 5, content: '', isAnonymous: 0 as 0 | 1 });
const submittingEvaluation = ref(false);
const evaluationSubmitted = ref(false);
/** 移动端评价表单在弹窗中完成，课程详情页仅展示入口与评价结果。 */
const evaluationDialogVisible = ref(false);
/** 同次开课评价单独加载，失败时不影响课程目录。 */
const peerEvaluations = ref<CourseEvaluationListItem[]>([]);
const peerPageNum = ref(1);
const peerTotal = ref(0);
const peerLoading = ref(false);
const peerError = ref('');
const peerRefreshPending = ref(false);
const PEER_PAGE_SIZE = 10;
let enrollmentId = 0;

/** 判断课程目录项是否应跳过素材学习，进入课程题库练习或课程测验。 */
function isAssessmentItem(item: LearnerCourseTaskItem): boolean {
  return item.itemType === 'QUESTION_BANK' || item.itemType === 'QUIZ';
}

/** 将目录项状态转换为移动端可读文案；课程考核按作答与批阅状态收敛，不沿用内容项学习进度。 */
function getTaskStatusText(item: LearnerCourseTaskItem): string {
  if (!item.learnable) return '暂不可学习';
  if (item.itemType === 'QUIZ') {
    if (item.scorePublishStatus === 'EFFECTIVE') return `结果已生效：${item.finalScore ?? '-'} 分`;
    if (item.examTaskStatus === COURSE_ASSESSMENT_TASK_STATUS.WAITING_RESULT) return '已交卷，等待批阅完成';
    if (item.examTaskStatus === COURSE_ASSESSMENT_TASK_STATUS.IN_PROGRESS) return '正在作答';
    return '待参加';
  }
  // 题库练习不产生分数，仍用内容项进度；但存在在答轮次时必须反映作答中。
  if (item.examTaskStatus === COURSE_ASSESSMENT_TASK_STATUS.IN_PROGRESS) return '正在作答';
  if (item.progressStatus === 'COMPLETED') return '已完成';
  if (item.progressStatus === 'LEARNING') return '学习中';
  return '待学习';
}

/** 返回课程考核项当前可执行的操作文案。 */
function getAssessmentActionText(item: LearnerCourseTaskItem): string {
  if (item.scorePublishStatus === 'EFFECTIVE' && item.publishedExamAttemptId) return '查看结果';
  if (item.activeExamAttemptId && item.examTaskStatus === COURSE_ASSESSMENT_TASK_STATUS.WAITING_RESULT) return '查看已交卷';
  if (item.activeExamAttemptId) return '继续作答';
  return item.itemType === 'QUIZ' ? '开始测验' : '开始练习';
}

/** 跳转课程域作答或结果页，路径只保留本人的报名、内容项和答卷上下文。 */
function navigateToAssessment(item: LearnerCourseTaskItem, attemptId: number, result: boolean): void {
  const title = encodeURIComponent(item.itemTitle);
  const page = result ? COURSE_ASSESSMENT_PAGE.RESULT : COURSE_ASSESSMENT_PAGE.ATTEMPT;
  uni.navigateTo({
    url: `/pages/course/${page}?enrollmentId=${enrollmentId}&itemId=${item.courseItemId}&attemptId=${attemptId}&itemTitle=${title}`
  });
}

/** 按现有作答状态开始、恢复或回看课程考核，不进入素材学习页。 */
async function openAssessment(item: LearnerCourseTaskItem, retry = false): Promise<void> {
  if (openingAssessmentItemId.value !== null) return;
  openingAssessmentItemId.value = item.courseItemId;
  try {
    if (!retry && item.scorePublishStatus === 'EFFECTIVE' && item.publishedExamAttemptId) {
      navigateToAssessment(item, item.publishedExamAttemptId, true);
      return;
    }
    if (!retry && item.activeExamAttemptId) {
      navigateToAssessment(item, item.activeExamAttemptId, item.examTaskStatus === COURSE_ASSESSMENT_TASK_STATUS.WAITING_RESULT);
      return;
    }
    const attempt = await startCourseExamAttempt(enrollmentId, item.courseItemId, {
      clientAttemptToken: `mobile-course-${enrollmentId}-${item.courseItemId}-${Date.now()}`,
      platform: 'MOBILE'
    });
    navigateToAssessment(item, attempt.examAttemptId, false);
  } catch (error) {
    uni.showToast({ title: getAssessmentExecutionErrorMessage(error, '课程考核启动失败'), icon: 'none' });
  } finally {
    openingAssessmentItemId.value = null;
  }
}

/** 普通内容项才进入独立素材学习页，并传递当前目录项作为初始内容。 */
/** H5沿现有链接能力跳转，非H5复制链接供对应终端打开。 */
async function enterLive(item: LearnerCourseTaskItem): Promise<void> {
  try {
    const result = await participateCourseLive(enrollmentId, item.courseItemId);
    // #ifdef H5
    globalThis.window.location.assign(result.liveUrl);
    // #endif
    // #ifndef H5
    uni.setClipboardData({ data: result.liveUrl });
    // #endif
    await loadCourse();
  } catch (error) { uni.showToast({ title: error instanceof Error ? error.message : '直播参与失败', icon: 'none' }); }
}

function openTask(item: LearnerCourseTaskItem): void {
  if (!item.learnable) {
    uni.showToast({ title: '当前内容暂不可学习', icon: 'none' });
    return;
  }
  if (item.itemType === 'LIVE') { void enterLive(item); return; }
  if (isAssessmentItem(item)) {
    void openAssessment(item);
    return;
  }
  uni.navigateTo({ url: `/pages/course/learning?enrollmentId=${enrollmentId}&courseItemId=${item.courseItemId}` });
}

/** 返回课程列表，避免在模板中直接依赖全局 uni 对象。 */
/** 报告始终回看该生效答卷，不触发新作答。 */
function openReportResult(itemId: number, attemptId: number): void {
  uni.navigateTo({ url: `/pages/course/assessment-result?enrollmentId=${enrollmentId}&itemId=${itemId}&attemptId=${attemptId}` });
}

function goBack(): void {
  uni.navigateBack();
}

/** 下载课程附件，统一通过后端返回的受控地址访问文件。 */
async function downloadCourseAttachment(asset: LearnerCourseDetail['downloadAssets'][number]): Promise<void> {
  if (!asset.downloadable || !asset.downloadUrl || downloadingAssetId.value !== null) {
    uni.showToast({ title: getDownloadUnavailableReasonText(asset.unavailableReason), icon: 'none' });
    return;
  }
  downloadingAssetId.value = asset.assetId;
  try {
    const request = downloadLearnerCourseAsset(enrollmentId, asset.assetId, asset.downloadUrl);
    const result = await new Promise<{ tempFilePath?: string }>((resolve, reject) => {
      uni.downloadFile({ url: request.url, header: request.header, success: resolve, fail: reject });
    });
    if (!result.tempFilePath) throw new Error('附件下载失败');
    await new Promise<void>((resolve, reject) => {
      uni.openDocument({ filePath: result.tempFilePath!, showMenu: true, success: () => resolve(), fail: reject });
    });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '课程附件下载失败', icon: 'none' });
  } finally {
    downloadingAssetId.value = null;
  }
}

/** 加载课程摘要和目录，课程详情页不再加载素材预览数据。 */
async function loadCourse(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    const [detail, taskSnapshot] = await Promise.all([getLearnerCourseDetail(enrollmentId), getLearnerCourseTasks(enrollmentId)]);
    enrollmentDetail.value = detail;
    tasks.value = taskSnapshot;
    void loadPeerEvaluations(true);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程内容加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

/** 分页读取当前报名所属开课的学员评价。 */
async function loadPeerEvaluations(reset = false): Promise<void> {
  if (peerLoading.value) {
    if (reset) peerRefreshPending.value = true;
    return;
  }
  if (reset) {
    peerPageNum.value = 1;
    peerEvaluations.value = [];
  }
  peerLoading.value = true;
  peerError.value = '';
  try {
    const page = await getPeerCourseEvaluations(enrollmentId, { pageNum: peerPageNum.value, pageSize: PEER_PAGE_SIZE });
    peerEvaluations.value = reset ? page.list : [...peerEvaluations.value, ...page.list];
    peerTotal.value = page.total;
  } catch (error) {
    peerError.value = error instanceof Error ? error.message : '评价加载失败，请重试';
  } finally {
    peerLoading.value = false;
    if (peerRefreshPending.value) {
      peerRefreshPending.value = false;
      void loadPeerEvaluations(true);
    }
  }
}

/** 继续读取下一页公开评价。 */
async function loadMorePeerEvaluations(): Promise<void> {
  if (peerLoading.value || peerEvaluations.value.length >= peerTotal.value) return;
  peerPageNum.value += 1;
  await loadPeerEvaluations();
  if (peerError.value) peerPageNum.value -= 1;
}

/** 提交后回读本人课程详情，确认评价已持久化并切换为回看状态。 */
async function saveEvaluation(): Promise<void> {
  if (submittingEvaluation.value || evaluationSubmitted.value || enrollmentDetail.value?.learningStatus !== 'COMPLETED' || enrollmentDetail.value.evaluation) return;
  submittingEvaluation.value = true;
  try {
    await submitCourseEvaluation(enrollmentId, evaluationForm.value);
    evaluationSubmitted.value = true;
    evaluationDialogVisible.value = false;
    uni.showToast({ title: '课程评价已提交', icon: 'none' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '评价提交失败，请稍后重试', icon: 'none' });
    submittingEvaluation.value = false;
    return;
  }
  try {
    enrollmentDetail.value = await getLearnerCourseDetail(enrollmentId);
    await loadPeerEvaluations(true);
  } catch {
    uni.showToast({ title: '评价已提交，请刷新查看', icon: 'none' });
  } finally {
    submittingEvaluation.value = false;
  }
}

/** 关闭评价弹窗时保留草稿，提交过程中阻止误关。 */
function closeEvaluationDialog(): void {
  if (!submittingEvaluation.value) evaluationDialogVisible.value = false;
}

onLoad((query) => {
  enrollmentId = Number(query?.enrollmentId);
  if (!enrollmentId) {
    errorMessage.value = '课程参数无效。';
    return;
  }
  void loadCourse();
});
</script>

<template>
  <MobileBaseLayout>
    <view class="page">
      <view v-if="loading" class="state-panel"><text>正在加载课程…</text></view>
      <view v-else-if="errorMessage" class="state-panel">
        <text>{{ errorMessage }}</text>
        <button class="retry-button" @tap="loadCourse">重新加载</button>
      </view>
      <template v-else-if="tasks">
        <view class="course-hero">
          <view class="hero-top">
            <view class="hero-back" @tap="goBack">‹</view>
            <text class="hero-status">{{ tasks.learningStatus === 'COMPLETED' ? '已完成' : '必修课程' }}</text>
          </view>
          <text class="hero-title">{{ tasks.courseName }}</text>
          <text class="hero-subtitle">{{ tasks.offeringName }}</text>
          <view class="hero-progress">
            <view>
              <text class="hero-progress-label">已学完课程项</text>
              <text class="hero-progress-number">
                {{ tasks.chapters.flatMap((chapter) => chapter.items).filter((item) => item.progressStatus === 'COMPLETED').length }}
                <text class="hero-progress-total">/ {{ tasks.chapters.reduce((total, chapter) => total + chapter.items.length, 0) }}</text>
              </text>
            </view>
            <view class="hero-ring">
              <text>{{ tasks.progressPercent }}%</text>
            </view>
          </view>
        </view>

        <view class="content-card">
          <view class="section-heading">
            <text class="section-title">课程目录</text>
            <text class="section-count">{{ tasks.progressPercent }}% 已完成</text>
          </view>
          <view v-for="chapter in orderCourseChapters(tasks.chapters)" :key="chapter.chapterId" class="chapter" :style="{ marginLeft: chapter.depth * 12 + 'px' }">
            <view class="chapter-heading">
              <text class="chapter-name">{{ chapter.sortOrder }}. {{ chapter.chapterName }}</text>
              <text class="chapter-progress">
                {{ chapter.items.filter((item) => item.progressStatus === 'COMPLETED').length }}/{{ chapter.items.length }}
              </text>
            </view>
            <view v-for="item in chapter.items" :key="item.courseItemId" class="task-card" :class="{ disabled: !item.learnable }">
              <view class="task-icon">{{ item.itemType === 'QUIZ' ? '测' : item.itemType === 'QUESTION_BANK' ? '练' : '学' }}</view>
              <view class="task-body">
                <text class="task-title">{{ item.itemTitle }}</text>
                  <text v-if="item.itemType === 'LIVE'" class="task-meta">直播 · {{ item.liveSession?.startTime?.replace('T', ' ') || '时间待发布' }} 至 {{ item.liveSession?.endTime?.replace('T', ' ') || '时间待发布' }}</text>
                <text class="task-meta">{{ item.isRequired === 1 ? '必修' : '选修' }} · {{ getTaskStatusText(item) }}</text>
              </view>
              <button v-if="item.canRetake" class="task-button" @click.stop="openAssessment(item, true)">补测一次</button>
              <button class="task-button" :disabled="!item.learnable || openingAssessmentItemId === item.courseItemId" @tap="openTask(item)">
                {{ !item.learnable ? '锁定' : isAssessmentItem(item) ? getAssessmentActionText(item) : '进入' }}
              </button>
            </view>
          </view>
        </view>

        <view class="content-card course-info">
          <view class="info-tabs">
            <text class="info-tab" :class="{ active: activeInfoTab === 'INTRODUCTION' }" @tap="activeInfoTab = 'INTRODUCTION'">课程简介</text>
            <text class="info-tab" :class="{ active: activeInfoTab === 'DOWNLOADS' }" @tap="activeInfoTab = 'DOWNLOADS'">课程附件</text>
            <text class="info-tab" :class="{ active: activeInfoTab === 'EVALUATIONS' }" @tap="activeInfoTab = 'EVALUATIONS'">课程评价</text>
            <text class="info-tab" :class="{ active: activeInfoTab === 'REPORT' }" @tap="activeInfoTab = 'REPORT'">课程报告</text>
          </view>
          <view v-if="activeInfoTab === 'INTRODUCTION'" class="course-introduction">
            <text class="info-title">{{ enrollmentDetail?.course?.courseName ?? tasks.courseName }}</text>
            <text class="info-copy">{{ enrollmentDetail?.course?.introduction || '暂无课程简介。' }}</text>
            <view class="info-row syllabus-text">
              <text>主讲教师</text><text>{{ enrollmentDetail?.course?.teacherName || '未设置' }}</text>
            </view>
            <view class="info-row syllabus-text">
              <text>教学目标</text>
              <text>{{ enrollmentDetail?.course?.teachingObjective || '暂无' }}</text>
            </view>
            <view class="info-row syllabus-text">
              <text>考核方式</text>
              <text>{{ enrollmentDetail?.course?.assessmentMethod || '按课程规则判定' }}</text>
            </view>
            <view class="info-row syllabus-text">
              <text>重点内容／教学大纲</text>
              <text>{{ enrollmentDetail?.course?.syllabus || '请参见课程目录。' }}</text>
            </view>
          </view>
          <view v-else-if="activeInfoTab === 'DOWNLOADS'" class="course-downloads">
            <view v-if="!enrollmentDetail?.downloadAssets.length" class="empty-extra">暂无课程附件。</view>
            <view
              v-for="downloadAsset in enrollmentDetail?.downloadAssets"
              :key="downloadAsset.assetId"
              class="download-card"
              :class="{ disabled: !downloadAsset.downloadable }"
              @tap="downloadCourseAttachment(downloadAsset)"
            >
              <text class="download-card-title">{{ downloadAsset.assetName || `课程附件 #${downloadAsset.assetId}` }}</text>
              <text class="download-card-meta">素材类型：{{ downloadAsset.assetType || '未知类型' }}</text>
              <text class="download-card-meta">文件名称：{{ downloadAsset.fileName || '未提供文件名' }}</text>
              <text class="download-card-meta">MIME 类型：{{ downloadAsset.mimeType || '未知' }}</text>
              <text class="download-card-meta">文件大小：{{ formatAttachmentSize(downloadAsset.fileSize) }}</text>
              <text class="download-card-action">
                {{ downloadAsset.downloadable ? '点击下载' : getDownloadUnavailableReasonText(downloadAsset.unavailableReason) }}
              </text>
            </view>
          </view>
          <view v-else-if="activeInfoTab === 'REPORT'" class="course-introduction">
            <text class="info-title">课程报告</text>
            <text class="info-copy">进度 {{ tasks.progressPercent }}% · 有效学习 {{ Math.floor((enrollmentDetail?.totalStudySeconds ?? 0) / 60) }} 分钟 · 认可学时 {{ ((enrollmentDetail?.recognizedStudySeconds ?? enrollmentDetail?.totalStudySeconds ?? 0) / 3600).toFixed(2) }} 小时</text>
            <text v-if="!enrollmentDetail?.assessmentResults?.length" class="info-copy">暂无生效考核结果</text>
            <view v-for="result in enrollmentDetail?.assessmentResults" :key="result.courseItemId" class="download-card">
              <text class="download-card-title">{{ tasks.chapters.flatMap(c => c.items).find(i => i.courseItemId === result.courseItemId)?.itemTitle }}</text>
              <text v-if="result.sourceType === 'COURSE_BANK_PRACTICE'" class="info-copy">正确 {{ result.correctCount ?? 0 }} / 错误 {{ result.wrongCount ?? 0 }} · 正确率 {{ result.accuracyPercent == null ? '暂无' : result.accuracyPercent + '%' }}</text>
              <text v-else class="info-copy">成绩 {{ result.finalScore ?? '暂无' }} · {{ result.isPassed === 1 ? '合格' : '未合格' }}</text>
              <button class="task-button" @tap="openReportResult(result.courseItemId, result.examAttemptId)">查看答题与错题</button>
            </view>
          </view>
          <view v-else class="course-evaluations">
            <view v-if="enrollmentDetail?.learningStatus === 'COMPLETED' && enrollmentDetail.evaluation" class="evaluation-result">
              <text class="evaluation-subtitle">我的评价</text>
              <text>我的评分：{{ enrollmentDetail.evaluation.rating }} / 5</text>
              <text>{{ enrollmentDetail.evaluation.content || '未填写评价内容' }}</text>
              <text>展示方式：{{ enrollmentDetail.evaluation.isAnonymous === 1 ? '匿名' : '实名' }}</text>
              <text>提交时间：{{ enrollmentDetail.evaluation.submittedTime.replace('T', ' ').slice(0, 16) }}</text>
            </view>
            <text v-else-if="evaluationSubmitted" class="evaluation-empty">评价已提交，请刷新查看。</text>
            <button v-else-if="enrollmentDetail?.learningStatus === 'COMPLETED'" class="evaluation-add" @tap="evaluationDialogVisible = true">添加课程评价</button>
            <view class="peer-evaluation-list">
              <text class="evaluation-subtitle">同次开课学员评价（{{ peerTotal }}）</text>
              <text v-if="peerError" class="evaluation-error">{{ peerError }}</text>
              <button v-if="peerError" class="evaluation-more" @tap="loadPeerEvaluations(true)">重试</button>
              <text v-else-if="!peerLoading && peerTotal === 0" class="evaluation-empty">暂无学员评价</text>
              <view v-for="(item, index) in peerEvaluations" :key="`${peerPageNum}-${index}`" class="peer-evaluation-item">
                <view class="peer-evaluation-heading"><text>{{ item.displayName }}</text><text>{{ item.submittedTime.replace('T', ' ').slice(0, 16) }}</text></view>
                <text>评分：{{ item.rating }} / 5</text>
                <text class="peer-evaluation-content">{{ item.content || '未填写评价内容' }}</text>
              </view>
              <button v-if="peerEvaluations.length < peerTotal" class="evaluation-more" :disabled="peerLoading" @tap="loadMorePeerEvaluations">
                {{ peerLoading ? '加载中…' : '加载更多评价' }}
              </button>
            </view>
          </view>
        </view>
      </template>
    </view>
    <view v-if="evaluationDialogVisible" class="evaluation-dialog-mask" @tap="closeEvaluationDialog">
      <view class="evaluation-dialog" @tap.stop>
        <view class="evaluation-dialog-heading">
          <text class="evaluation-subtitle">添加课程评价</text>
          <text class="evaluation-dialog-close" @tap="closeEvaluationDialog">关闭</text>
        </view>
        <view class="evaluation-form">
          <text>评分：{{ evaluationForm.rating }} / 5</text>
          <slider :value="evaluationForm.rating * 2" :min="0" :max="10" :step="1" active-color="#2563eb" @change="evaluationForm.rating = Number($event.detail.value) / 2" />
          <textarea v-model="evaluationForm.content" maxlength="2000" placeholder="写下你的课程评价（选填）" />
          <view class="evaluation-anonymity">
            <text>展示方式</text>
            <button :class="{ active: evaluationForm.isAnonymous === 0 }" @tap="evaluationForm.isAnonymous = 0">实名</button>
            <button :class="{ active: evaluationForm.isAnonymous === 1 }" @tap="evaluationForm.isAnonymous = 1">匿名展示</button>
          </view>
          <button class="evaluation-submit" :disabled="submittingEvaluation" @tap="saveEvaluation">
            {{ submittingEvaluation ? '提交中…' : '提交评价' }}
          </button>
        </view>
      </view>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.evaluation-dialog-mask {
  position: fixed;
  z-index: 1000;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx;
  background: rgb(0 0 0 / 50%);
}
.evaluation-dialog {
  box-sizing: border-box;
  width: 100%;
  max-height: 85vh;
  overflow-y: auto;
  padding: 32rpx;
  border-radius: 24rpx;
  background: #fff;
}
.evaluation-dialog-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.evaluation-dialog-close {
  padding: 12rpx;
  color: #667085;
  font-size: 25rpx;
}
.evaluation-add {
  width: 100%;
  margin-top: 24rpx;
  background: #2563eb;
  color: #fff;
  font-size: 26rpx;
}
.evaluation-subtitle {
  color: #17181c;
  font-size: 28rpx;
  font-weight: 600;
}
.peer-evaluation-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  margin-top: 32rpx;
  padding-top: 28rpx;
  border-top: 1rpx solid #e5e7eb;
}
.peer-evaluation-item {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 22rpx 0;
  border-top: 1rpx solid #f1f1f3;
  font-size: 25rpx;
}
.peer-evaluation-heading {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
  color: #667085;
  font-size: 23rpx;
}
.peer-evaluation-content {
  color: #17181c;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.evaluation-error { color: #d92d20; }
.evaluation-empty { color: #8d8f96; font-size: 25rpx; }
.evaluation-more { width: 100%; margin: 0; background: #f1f4fa; color: #2563eb; font-size: 25rpx; }
.evaluation-form,
.evaluation-result {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 24rpx;
  color: #17181c;
  font-size: 26rpx;
}
.evaluation-form textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 180rpx;
  padding: 20rpx;
  border: 1rpx solid #d9dde5;
  border-radius: 16rpx;
}
.evaluation-anonymity {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.evaluation-anonymity button {
  margin: 0;
  background: #f1f4fa;
  color: #345;
  font-size: 24rpx;
}
.evaluation-anonymity button.active,
.evaluation-submit {
  background: #2563eb;
  color: #fff;
}
.page {
  padding: 28rpx 0 64rpx;
}

/* 学习任务页遵循移动端列表卡片规范：浅底、白卡、红色行动点、低饱和辅助信息。 */
.course-hero {
  padding: 30rpx;
  border: 1rpx solid #f1f1f3;
  border-radius: 24rpx;
  background: #fff;
  color: #17181c;
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 6%);
}
.hero-top,
.hero-progress,
.section-heading,
.chapter-heading,
.task-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}
.hero-back {
  width: 64rpx;
  height: 64rpx;
  border-radius: 20rpx;
  background: #bfdbfe;
  color: #2563eb;
  font-size: 64rpx;
  line-height: 54rpx;
  text-align: center;
}
.hero-status {
  padding: 8rpx 16rpx;
  border-radius: 24rpx;
  background: #bfdbfe;
  color: #2563eb;
  font-size: 22rpx;
}
.hero-title {
  display: block;
  margin-top: 40rpx;
  color: #17181c;
  font-size: 42rpx;
  font-weight: 700;
}
.hero-subtitle {
  display: block;
  margin-top: 12rpx;
  color: #8d8f96;
  font-size: 25rpx;
}
.hero-progress {
  margin-top: 48rpx;
}
.hero-progress-label {
  display: block;
  color: #8d8f96;
  font-size: 25rpx;
}
.hero-progress-number {
  display: block;
  margin-top: 8rpx;
  color: #17181c;
  font-size: 52rpx;
  font-weight: 700;
}
.hero-progress-total {
  color: #8d8f96;
  font-size: 30rpx;
  font-weight: 400;
}
.hero-ring {
  display: flex;
  width: 148rpx;
  height: 148rpx;
  align-items: center;
  justify-content: center;
  border: 12rpx solid rgb(45 104 229 / 80%) ;
  border-radius: 50%;
  color: #2563eb;
  font-size: 34rpx;
  font-weight: 700;
}
.content-card {
  margin-top: 24rpx;
  padding: 32rpx 28rpx;
  border: 1rpx solid #f1f1f3;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 6%);
}
.section-title {
  color: #17181c;
  font-size: 36rpx;
  font-weight: 700;
}
.section-count {
  padding: 8rpx 14rpx;
  border-radius: 20rpx;
  background: #bfdbfe;
  color: #2563eb;
  font-size: 21rpx;
}
.chapter + .chapter {
  margin-top: 32rpx;
}
.chapter-heading {
  padding-bottom: 14rpx;
  border-bottom: 1rpx solid #f1f1f3;
}
.chapter-name {
  color: #2f3747;
  font-size: 27rpx;
  font-weight: 700;
}
.chapter-progress {
  color: #8d8f96;
  font-size: 22rpx;
}
.task-card {
  min-height: 104rpx;
  gap: 16rpx;
  margin-top: 18rpx;
  padding: 22rpx 16rpx;
  border: 1rpx solid #f1f1f3;
  border-radius: 16rpx;
  background: #fbfcff;
}
.task-card:last-child {
  border-bottom: 1rpx solid #f0f2f7;
}
.task-card.disabled {
  opacity: 0.58;
}
.task-icon {
  display: flex;
  width: 56rpx;
  height: 56rpx;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 18rpx;
  background: #bfdbfe;
  color: #2563eb;
  font-size: 22rpx;
  font-weight: 600;
}
.task-body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}
.task-title {
  overflow: hidden;
  color: #17181c;
  font-size: 28rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-meta {
  margin-top: 8rpx;
  color: #8d8f96;
  font-size: 22rpx;
}
.task-button {
  min-width: 92rpx;
  margin: 0;
  padding: 0 16rpx;
  border: 0;
  border-radius: 28rpx;
  background: #bfdbfe;
  color: #2563eb;
  font-size: 22rpx;
  line-height: 52rpx;
}
.task-button[disabled] {
  background: #f0f2f6;
  color: #9ca6b6;
}
.info-tabs {
  display: flex;
  gap: 36rpx;
  border-bottom: 1rpx solid #f1f1f3;
}
.info-tab {
  padding: 28rpx 0 20rpx;
  border-bottom: 4rpx solid transparent;
  color: #8d8f96;
  font-size: 27rpx;
}
.info-tab.active {
  border-bottom-color: #2563eb;
  color: #17181c;
  font-weight: 700;
}
.course-introduction,
.course-downloads,
.course-evaluations {
  padding-top: 24rpx;
}
.info-title {
  display: block;
  color: #17181c;
  font-size: 28rpx;
  font-weight: 700;
}
.info-copy {
  display: block;
  margin-top: 14rpx;
  color: #676971;
  font-size: 24rpx;
  line-height: 1.7;
}
.info-row {
  display: flex;
  gap: 16rpx;
  margin-top: 18rpx;
  color: #676971;
  font-size: 23rpx;
  line-height: 1.6;
}
.info-row > text:first-child {
  width: 132rpx;
  flex: none;
  color: #8d8f96;
}
.syllabus-text > text:last-child {
  white-space: pre-line;
}
.empty-extra {
  padding: 28rpx 0;
  color: #909bad;
  font-size: 24rpx;
}
.download-card {
  display: flex;
  margin-top: 18rpx;
  padding: 24rpx;
  flex-direction: column;
  gap: 10rpx;
  border: 1rpx solid #f1f1f3;
  border-radius: 18rpx;
  background: #fbfcff;
}
.download-card.disabled {
  opacity: 0.6;
}
.download-card-title {
  color: #17181c;
  font-size: 26rpx;
  font-weight: 600;
}
.download-card-meta {
  color: #8d8f96;
  font-size: 22rpx;
}
.download-card-action {
  margin-top: 4rpx;
  color: #2563eb;
  font-size: 23rpx;
}
.state-panel {
  display: flex;
  margin-top: 48rpx;
  padding: 64rpx 28rpx;
  align-items: center;
  flex-direction: column;
  gap: 24rpx;
  border-radius: 20rpx;
  background: #fff;
  color: #777981;
  font-size: 26rpx;
}
.retry-button {
  min-width: 180rpx;
  margin: 0;
  border: 0;
  border-radius: 40rpx;
  background: #5271ae;
  color: #fff;
  font-size: 26rpx;
}
</style>
