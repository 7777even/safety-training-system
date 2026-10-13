<script setup lang="ts">
import { orderCourseChapters } from '@shared/libs/course-chapters';
import type { LearnerCourseDetail, LearnerCourseTaskItem, LearnerCourseTasks } from '@shared/types';
import { onHide, onLoad, onShow } from '@dcloudio/uni-app';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { getAssessmentExecutionErrorMessage } from '@shared/libs/assessment-execution';
import { formatAttachmentSize, getDownloadUnavailableReasonText } from '@shared/utils/attachment';
import { getStudyDescriptionText } from '@shared/utils/study';
import { buildRandomWatermarkTile, nextWatermarkDelayMs } from '@shared/utils/watermark';
import {
  VERIFICATION_ANSWER_TIMEOUT_SECONDS,
  buildArithmeticQuestion,
  nextVerificationDelayMs
} from '@shared/utils/learning-verification';
import AssetPreview from '@/components/asset-preview/AssetPreview.vue';
import { type AssetDetail, type AssetPlayInfo, getAssetDetail, getAssetFileUrl, getAssetPlayInfo } from '@/api/asset';
import {
  LearnerCourseApiError,
  downloadLearnerCourseAsset,
  getLearnerCourseDetail,
  getLearnerCourseTasks,
  participateCourseLive,
  reportStudySession,
  startCourseExamAttempt
} from '@/api/learner-course';
import { getOrCreateLearningDeviceId } from '@/utils/learning-device';
import { COURSE_ASSESSMENT_PAGE, COURSE_ASSESSMENT_TASK_STATUS } from '@/constants';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { useUserStore } from '@/stores';

const userStore = useUserStore();

const tasks = ref<LearnerCourseTasks | null>(null);
const enrollmentDetail = ref<LearnerCourseDetail | null>(null);
const activeInfoTab = ref<'INTRODUCTION' | 'DOWNLOADS'>('INTRODUCTION');
const activeItem = ref<LearnerCourseTaskItem | null>(null);
const asset = ref<AssetDetail | null>(null);
const playInfo = ref<AssetPlayInfo | null>(null);
const assetLoading = ref(false);
const assetErrorMessage = ref('');
const loading = ref(false);
const errorMessage = ref('');
let enrollmentId = 0;
let initialCourseItemId = 0;
let sessionStartedAt = 0;
let sessionTraceId = '';
let reportSequence = 0;
let currentPositionSeconds = 0;
let currentPageNo = 1;
let reportTimer: ReturnType<typeof globalThis.setInterval> | undefined;
let assetProcessTimer: ReturnType<typeof globalThis.setInterval> | undefined;
let accumulatedStudySeconds = 0;
let activeTrackingStartedAt = 0;
const pageVisible = ref(true);
const mediaPlaying = ref(false);
/** 本设备持久化的终端标识，随学习会话上报，用于同一终端多账户互斥检测。 */
const learningDeviceId = getOrCreateLearningDeviceId();
/** 学习互斥被服务端拒绝后的提示；非空时暂停计时与上报，避免持续报错。 */
const mutexBlockedMessage = ref('');
/** 防挂机验证题超时后的提示；非空时同样停止计时与上报，需重新进入内容项。 */
const verificationStoppedMessage = ref('');
const verificationVisible = ref(false);
const verificationPrompt = ref('');
const verificationRemainingSeconds = ref(0);
const verificationAnswer = ref('');
const verificationError = ref('');
let currentVerificationAnswer = 0;
let verificationScheduleTimer: ReturnType<typeof globalThis.setTimeout> | undefined;
let verificationCountdownTimer: ReturnType<typeof globalThis.setInterval> | undefined;
const downloadingAssetId = ref<number | null>(null);
const directoryExpanded = ref(true);
const viewerInitialPositionSeconds = ref(0);
const viewerInitialPageNo = ref(1);

const completedItems = computed(
  () => tasks.value?.chapters.flatMap((chapter) => chapter.items).filter((item) => item.progressStatus === 'COMPLETED').length ?? 0
);
const totalItems = computed(() => tasks.value?.chapters.reduce((total, chapter) => total + chapter.items.length, 0) ?? 0);
const isTimedAsset = computed(() => asset.value?.assetType === 'VIDEO' || asset.value?.assetType === 'AUDIO');
/** 学习侧四项开关来自冻结课程快照；管理端改动课程基础信息后需重新发布才对已报名学员生效。 */
const courseInfo = computed(() => enrollmentDetail.value?.course ?? null);
const isWatermarkEnabled = computed(() => courseInfo.value?.isWatermarkEnabled === 1);
const isStudyDescriptionEnabled = computed(() => courseInfo.value?.isStudyDescriptionEnabled === 1);
const isSpeedPlayAllowed = computed(() => courseInfo.value?.isSpeedPlayAllowed === 1);
/** 水印内容优先取员工编号（账号编码），未取到时回退用户名，避免出现空水印。 */
const watermarkText = computed(() => userStore.userInfo?.userCode ?? userStore.userInfo?.userName ?? '学员');
/**
 * rpx 以 750 设计稿折算到常见机型宽度约 0.55 倍；若把 tile 的 px 数值直接当 rpx 用，
 * 17px 的字号在手机上只剩 9px 左右，会糊成一道灰线。放大到 1.5 倍后单字约 13~14px，
 * 既能看清水印内容，又能在卡片宽度内平铺出足够密度。
 */
const MOBILE_TILE_SCALE = 1.5;
/**
 * 移动端水印不放在素材学习区（素材几乎铺满屏幕，叠上去会遮挡观看），
 * 而是以 CSS 变量下发给下方「课程目录」卡片当背景层：卡片自身留白可见水印，
 * 条目卡与按钮有自己的底色会自然遮住水印，不影响目录文字辨认。
 * 学习页按原文使用随机平铺水印并按 45～90 秒随机间隔重建，实现不定时不定位置。
 */
const watermarkStyle = ref<Record<string, string>>({});
let watermarkRefreshTimer: ReturnType<typeof globalThis.setTimeout> | undefined;

/** 用随机参数重建一次水印单元；未开启水印时清空样式变量。 */
function refreshWatermark(): void {
  if (!isWatermarkEnabled.value) {
    watermarkStyle.value = {};
    return;
  }
  const tile = buildRandomWatermarkTile(watermarkText.value);
  watermarkStyle.value = {
    '--learning-watermark-image': tile.image,
    '--learning-watermark-size': `${Math.round(tile.width * MOBILE_TILE_SCALE)}rpx ${Math.round(tile.height * MOBILE_TILE_SCALE)}rpx`
  };
}

/** 按 45～90 秒随机间隔调度下一次重建；仅在水印开启时运行。 */
function scheduleWatermarkRefresh(): void {
  if (watermarkRefreshTimer !== undefined) globalThis.clearTimeout(watermarkRefreshTimer);
  watermarkRefreshTimer = globalThis.setTimeout(() => {
    watermarkRefreshTimer = undefined;
    refreshWatermark();
    scheduleWatermarkRefresh();
  }, nextWatermarkDelayMs());
}

/** 水印开关变化时立即重建或停止随机刷新；开关来自冻结课程快照。 */
watch(isWatermarkEnabled, (enabled) => {
  if (enabled) {
    refreshWatermark();
    scheduleWatermarkRefresh();
  } else {
    if (watermarkRefreshTimer !== undefined) {
      globalThis.clearTimeout(watermarkRefreshTimer);
      watermarkRefreshTimer = undefined;
    }
    watermarkStyle.value = {};
  }
});
const studyDescriptionText = computed(() => getStudyDescriptionText(activeItem.value?.studySeconds));

/** 将学习页标题同步为当前内容项名称，便于学员确认正在学习的材料。 */
function setLearningTitle(title?: string): void {
  uni.setNavigationBarTitle({ title: title || '素材学习' });
}

/** 判断课程内容项是否应进入课程域考核流程。 */
function isAssessmentItem(item: LearnerCourseTaskItem): boolean {
  return item.itemType === 'QUESTION_BANK' || item.itemType === 'QUIZ';
}

/** 累计当前有效学习区间，仅在可见、实际播放（或非媒体停留）、无验证题且未被停止学习时运行。 */
function syncStudyClock(now = Date.now()): void {
  const countable = pageVisible.value && !verificationVisible.value
    && !mutexBlockedMessage.value && !verificationStoppedMessage.value
    && (!isTimedAsset.value || mediaPlaying.value);
  if (activeTrackingStartedAt && countable) {
    accumulatedStudySeconds += Math.max(0, Math.floor((now - activeTrackingStartedAt) / 1000));
  }
  activeTrackingStartedAt = countable ? now : 0;
}

/** 停止全部学习定时器：30 秒上报、验证题调度与作答倒计时。 */
function stopLearningTimers(): void {
  cleanupSessionTimer();
  if (verificationScheduleTimer !== undefined) {
    globalThis.clearTimeout(verificationScheduleTimer);
    verificationScheduleTimer = undefined;
  }
  if (verificationCountdownTimer !== undefined) {
    globalThis.clearInterval(verificationCountdownTimer);
    verificationCountdownTimer = undefined;
  }
}

/** 学习互斥被拒：停止计时与定时上报，保留提示让学员先结束其他终端或账户的学习。 */
function blockLearningByMutex(message: string): void {
  mutexBlockedMessage.value = message;
  syncStudyClock();
  stopLearningTimers();
  uni.showToast({ title: message, icon: 'none' });
}

/** 按 60～180 秒随机间隔调度下一道验证题；切换内容项或停止学习时取消。 */
function scheduleVerification(): void {
  if (verificationScheduleTimer !== undefined) globalThis.clearTimeout(verificationScheduleTimer);
  verificationScheduleTimer = globalThis.setTimeout(() => {
    verificationScheduleTimer = undefined;
    openVerification();
  }, nextVerificationDelayMs());
}

/** 弹出验证题：先结算到弹出时刻，再暂停有效学时累计并开始作答倒计时。 */
function openVerification(): void {
  if (mutexBlockedMessage.value || verificationStoppedMessage.value || verificationVisible.value) return;
  const question = buildArithmeticQuestion();
  currentVerificationAnswer = question.answer;
  verificationPrompt.value = question.prompt;
  verificationAnswer.value = '';
  verificationError.value = '';
  verificationRemainingSeconds.value = VERIFICATION_ANSWER_TIMEOUT_SECONDS;
  verificationVisible.value = true;
  syncStudyClock();
  verificationCountdownTimer = globalThis.setInterval(() => {
    verificationRemainingSeconds.value -= 1;
    if (verificationRemainingSeconds.value <= 0) void onVerificationTimeout();
  }, 1000);
}

/** 提交验证答案：答对恢复计时并按新的随机间隔调度，答错在时限内可重试。 */
function submitVerification(): void {
  const submitted = verificationAnswer.value.trim();
  if (submitted === '' || Number(submitted) !== currentVerificationAnswer) {
    verificationError.value = '答案不正确，请重新作答。';
    return;
  }
  closeVerification();
  syncStudyClock();
  scheduleVerification();
}

/** 验证题超时未答对：按挂机以 TIMEOUT 结束当前会话，停止计时并不再上报。 */
async function onVerificationTimeout(): Promise<void> {
  // 先以 TIMEOUT 结束当前会话（此时弹层仍开着、计时保持暂停，守卫尚未置位），再置停止提示并收尾。
  try {
    await reportCurrentSession('TIMEOUT');
  } catch {
    // 结束上报失败不覆盖挂机提示，学员重新进入内容项即可。
  }
  verificationStoppedMessage.value = '验证题超时未作答，本次学习会话已结束，请重新进入本内容项。';
  syncStudyClock();
  closeVerification();
  stopLearningTimers();
}

/** 关闭验证题弹层并清理其调度与倒计时定时器。 */
function closeVerification(): void {
  if (verificationCountdownTimer !== undefined) {
    globalThis.clearInterval(verificationCountdownTimer);
    verificationCountdownTimer = undefined;
  }
  if (verificationScheduleTimer !== undefined) {
    globalThis.clearTimeout(verificationScheduleTimer);
    verificationScheduleTimer = undefined;
  }
  verificationVisible.value = false;
  verificationError.value = '';
}

/** 更新页面可见状态，切后台期间不累计有效学习时间。 */
function setPageVisible(visible: boolean): void {
  syncStudyClock();
  pageVisible.value = visible;
  syncStudyClock();
}

/** 按课程接口契约生成不带时区后缀的本地日期时间。 */
function formatLocalDateTime(value: Date): string {
  const pad = (part: number): string => String(part).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
}

/** 加载课程级聚合详情和章节任务，保证页面展示的是本人报名上下文。 */
async function loadCourse(): Promise<void> {
  if (!enrollmentId) {
    errorMessage.value = '课程参数无效。';
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  try {
    const [detail, taskSnapshot] = await Promise.all([getLearnerCourseDetail(enrollmentId), getLearnerCourseTasks(enrollmentId)]);
    enrollmentDetail.value = detail;
    tasks.value = taskSnapshot;
    const availableItems = taskSnapshot.chapters.flatMap((chapter) => chapter.items).filter((item) => item.learnable && !isAssessmentItem(item));
    const initialItem = availableItems.find((item) => item.courseItemId === initialCourseItemId) ?? availableItems[0];
    if (initialItem) await selectItem(initialItem);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程学习内容加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

/** 将任务状态转换为目录中的短文案，与课程详情页保持同一口径。 */
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

/** 记录当前内容项的学习会话，重复请求通过递增 trace 序号保持幂等。 */
async function reportCurrentSession(endReason: 'COMPLETED' | 'USER_EXIT' | 'SCREEN_LEAVE' | 'HEARTBEAT' | 'TIMEOUT'): Promise<void> {
  if (!tasks.value || !activeItem.value || activeItem.value.itemType !== 'ASSET' || !sessionStartedAt || !sessionTraceId) return;
  // 学习互斥阻塞或验证题超时后不再上报：本设备计时已暂停。
  if (mutexBlockedMessage.value || verificationStoppedMessage.value) return;
  const now = Date.now();
  // 先结算「上次结算点 → 现在」的活跃区间：心跳等时间驱动的检查点不经过可见性变化或媒体事件，
  // 不先结算就会读到尚未累计的秒数，纯文档类内容项的有效学习时长会恒为 0。
  syncStudyClock(now);
  reportSequence += 1;
  try {
    await reportStudySession({
      enrollmentId: tasks.value.enrollmentId,
      courseItemId: activeItem.value.courseItemId,
      clientTraceId: `${sessionTraceId}-${reportSequence}`,
      deviceType: 'H5',
      deviceId: learningDeviceId,
      startedTime: formatLocalDateTime(new Date(sessionStartedAt)),
      endedTime: formatLocalDateTime(new Date(now)),
      validStudySeconds: accumulatedStudySeconds,
      startPositionSeconds: activeItem.value.lastPositionSeconds,
      endPositionSeconds: isTimedAsset.value ? Math.floor(currentPositionSeconds) : null,
      startPageNo: activeItem.value.lastPageNo,
      endPageNo: playInfo.value?.format === 'pdf' ? currentPageNo : null,
      endReason: endReason
    });
    accumulatedStudySeconds = 0;
    if (endReason !== 'SCREEN_LEAVE') await refreshTaskProgress();
  } catch (error) {
    // 5095/5096 为学习互斥：停止本设备计时并提示，不按普通失败重试。
    if (error instanceof LearnerCourseApiError && (error.code === 5095 || error.code === 5096)) {
      blockLearningByMutex(error.message);
      return;
    }
    if (endReason !== 'SCREEN_LEAVE') uni.showToast({ title: error instanceof Error ? error.message : '学习进度上报失败', icon: 'none' });
  }
}

/** 进度上报成功后重新读取目录和课程摘要，立即同步完成标记与总进度。 */
async function refreshTaskProgress(): Promise<void> {
  if (!tasks.value) return;
  try {
    const [refreshedTasks, refreshedDetail] = await Promise.all([
      getLearnerCourseTasks(tasks.value.enrollmentId),
      getLearnerCourseDetail(tasks.value.enrollmentId)
    ]);
    const currentItemId = activeItem.value?.courseItemId;
    tasks.value = refreshedTasks;
    enrollmentDetail.value = refreshedDetail;
    if (currentItemId) {
      const refreshedItem = refreshedTasks.chapters.flatMap((chapter) => chapter.items).find((item) => item.courseItemId === currentItemId);
      if (refreshedItem && activeItem.value) Object.assign(activeItem.value, refreshedItem);
    }
  } catch {
    // 进度刷新失败不打断当前素材学习，下一次上报继续尝试。
  }
}

/** 释放定时上报任务。 */
function cleanupSessionTimer(): void {
  if (reportTimer) globalThis.clearInterval(reportTimer);
  reportTimer = undefined;
}

/** 切换学习内容时先保存上一项，再创建新的移动端学习会话。 */
async function selectItem(item: LearnerCourseTaskItem): Promise<void> {
  if (!item.learnable || activeItem.value?.courseItemId === item.courseItemId) return;
  if (activeItem.value) await reportCurrentSession('USER_EXIT');
  cleanupSessionTimer();
  activeItem.value = item;
  // 考核与直播内容项不走学习会话，取消验证题调度。
  closeVerification();
  if (item.itemType !== 'ASSET') { sessionStartedAt = 0; sessionTraceId = ''; return; }
  currentPositionSeconds = item.lastPositionSeconds ?? 0;
  currentPageNo = item.lastPageNo ?? 1;
  viewerInitialPositionSeconds.value = currentPositionSeconds;
  viewerInitialPageNo.value = currentPageNo;
  accumulatedStudySeconds = 0;
  activeTrackingStartedAt = 0;
  mediaPlaying.value = false;
  sessionStartedAt = Date.now();
  sessionTraceId = `h5-course-${enrollmentId}-${item.courseItemId}-${sessionStartedAt}`;
  reportSequence = 0;
  await loadAsset(item);
  syncStudyClock();
  reportTimer = globalThis.setInterval(() => {
    // 心跳失败由统一错误提示承接，不打断当前学习；互斥拒绝在 reportCurrentSession 内转为阻塞提示。
    reportCurrentSession('HEARTBEAT').catch(() => undefined);
  }, 30000);
  scheduleVerification();
}

/** 普通素材进入统一走学习会话；实际媒体事件由预览组件回调。 */
async function startTask(item: LearnerCourseTaskItem): Promise<void> {
  await selectItem(item);
}

/** 加载内容项对应的素材详情和受控预览信息。 */
async function loadAsset(item: LearnerCourseTaskItem): Promise<void> {
  if (assetProcessTimer !== undefined) globalThis.clearInterval(assetProcessTimer);
  assetProcessTimer = undefined;
  asset.value = null;
  playInfo.value = null;
  assetErrorMessage.value = '';
  if (!item.assetId) return;
  assetLoading.value = true;
  try {
    asset.value = await getAssetDetail(item.assetId);
    if (!['RICH_TEXT', 'LINK', 'LIVE'].includes(asset.value.assetType)) {
      if (asset.value.processStatus === 'UPLOADED' || asset.value.processStatus === 'PROCESSING') {
        assetProcessTimer = globalThis.setInterval(async () => {
          try {
            const detail = await getAssetDetail(item.assetId!);
            if (activeItem.value?.courseItemId !== item.courseItemId) return;
            asset.value = detail;
            if (detail.processStatus === 'SUCCEEDED') {
              if (assetProcessTimer !== undefined) globalThis.clearInterval(assetProcessTimer);
              assetProcessTimer = undefined;
              playInfo.value = await getAssetPlayInfo(item.assetId!);
            } else if (detail.processStatus === 'FAILED') {
              if (assetProcessTimer !== undefined) globalThis.clearInterval(assetProcessTimer);
              assetProcessTimer = undefined;
              assetErrorMessage.value = 'PDF 处理失败，请联系管理员重新处理。';
            }
          } catch {
            // 下一轮继续读取状态。
          }
        }, 3000);
      } else if (asset.value.processStatus === 'SUCCEEDED') {
        playInfo.value = await getAssetPlayInfo(item.assetId);
      }
    }
  } catch (error) {
    assetErrorMessage.value = error instanceof Error ? error.message : '素材暂时无法打开。';
  } finally {
    assetLoading.value = false;
  }
}

/** 兼容 uni video/audio 的时间更新事件，供断点上报使用。 */
function onMediaTimeUpdate(value: number): void {
  currentPositionSeconds = value;
}

/** PDF 页成功显示后更新移动端课程断点。 */
function onPdfPageUpdate(pageNo: number): void {
  currentPageNo = pageNo;
}

/** 媒体开始实际播放后才开始累计有效学习时间。 */
function onMediaPlay(): void {
  syncStudyClock();
  mediaPlaying.value = true;
  syncStudyClock();
}

/** 媒体暂停后停止累计有效学习时间。 */
function onMediaPause(): void {
  syncStudyClock();
  mediaPlaying.value = false;
}

/** 媒体自然结束时提交完成会话。 */
async function onMediaEnded(): Promise<void> {
  onMediaPause();
  await reportCurrentSession('COMPLETED');
  cleanupSessionTimer();
  uni.showToast({ title: '本项学习已完成', icon: 'success' });
}

/** 使用受控地址打开素材；H5 新窗口打开，App 先下载再交给系统文档能力。 */
async function openControlledFile(): Promise<void> {
  if (!asset.value) return;
  const url =
    playInfo.value?.url ?? (asset.value.fileInfoId ? getAssetFileUrl(asset.value.assetId, asset.value.fileInfoId) : asset.value.externalUrl);
  if (!url) {
    uni.showToast({ title: '当前素材没有可用访问入口', icon: 'none' });
    return;
  }
  // #ifdef H5
  globalThis.open(url, '_blank', 'noopener,noreferrer');
  // #endif
  // #ifndef H5
  const download = await new Promise<{ tempFilePath?: string }>((resolve, reject) => {
    uni.downloadFile({ url, success: resolve, fail: reject });
  });
  if (download.tempFilePath) {
    await new Promise<void>((resolve, reject) => {
      uni.openDocument({ filePath: download.tempFilePath!, showMenu: true, success: () => resolve(), fail: reject });
    });
  }
  // #endif
}

/** 下载课程配置的附件；不可下载时展示后端返回的明确原因。 */
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
    if (typeof globalThis.open === 'function') {
      globalThis.open(result.tempFilePath, '_blank');
    } else {
      await new Promise<void>((resolve, reject) => {
        uni.openDocument({ filePath: result.tempFilePath!, showMenu: true, success: () => resolve(), fail: reject });
      });
    }
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '课程附件下载失败', icon: 'none' });
  } finally {
    downloadingAssetId.value = null;
  }
}

/** 按内容项类型分流考试和普通学习入口。 */
function getAssessmentActionText(item: LearnerCourseTaskItem): string {
  if (item.scorePublishStatus === 'EFFECTIVE' && item.publishedExamAttemptId) return '查看结果';
  if (item.activeExamAttemptId && item.examTaskStatus === COURSE_ASSESSMENT_TASK_STATUS.WAITING_RESULT) return '查看已交卷';
  if (item.activeExamAttemptId) return '继续作答';
  return item.itemType === 'QUIZ' ? '开始测验' : '开始练习';
}

/** 打开课程域考核页面，路径参数只携带本人课程上下文和作答 ID。 */
function navigateToAssessment(item: LearnerCourseTaskItem, attemptId: number, result: boolean): void {
  if (!tasks.value) return;
  const title = encodeURIComponent(item.itemTitle);
  const page = result ? COURSE_ASSESSMENT_PAGE.RESULT : COURSE_ASSESSMENT_PAGE.ATTEMPT;
  uni.navigateTo({
    url: `/pages/course/${page}?enrollmentId=${tasks.value.enrollmentId}&itemId=${item.courseItemId}&attemptId=${attemptId}&itemTitle=${title}`
  });
}

/** 按课程考核任务状态开始、继续或回看，保持与 PC 端一致的分流。 */
/** 课件试题复用原课程考核入口。 */
function openLinkedQuiz(): void {
  const quiz = tasks.value?.chapters.flatMap(c => c.items).find(i => i.courseItemId === activeItem.value?.linkedQuizItemId);
  if (quiz) openTask(quiz);
}

/** 直播参与只由后端认可完成，不上报第三方观看秒数。 */
async function enterLive(item: LearnerCourseTaskItem): Promise<void> {
  if (!tasks.value || !item.learnable) return;
  try {
    const result = await participateCourseLive(tasks.value.enrollmentId, item.courseItemId);
    await refreshTaskProgress();
    // #ifdef H5
    globalThis.window.location.assign(result.liveUrl);
    // #endif
    // #ifndef H5
    await uni.setClipboardData({ data: result.liveUrl });
    // #endif
  } catch (error) { uni.showToast({ title: error instanceof Error ? error.message : '直播参与失败', icon: 'none' }); }
}

function openTask(item: LearnerCourseTaskItem, retry = false): void {
  if (item.itemType === 'LIVE') { void enterLive(item); return; }
  if (isAssessmentItem(item)) {
    if (!tasks.value || !item.learnable) return;
    if (!retry && item.scorePublishStatus === 'EFFECTIVE' && item.publishedExamAttemptId) {
      navigateToAssessment(item, item.publishedExamAttemptId, true);
      return;
    }
    if (!retry && item.activeExamAttemptId) {
      navigateToAssessment(item, item.activeExamAttemptId, item.examTaskStatus === COURSE_ASSESSMENT_TASK_STATUS.WAITING_RESULT);
      return;
    }
    void startCourseExamAttempt(tasks.value.enrollmentId, item.courseItemId, {
      clientAttemptToken: `h5-course-${tasks.value.enrollmentId}-${item.courseItemId}-${Date.now()}`,
      platform: 'H5'
    })
      .then((attempt) => navigateToAssessment(item, attempt.examAttemptId, false))
      .catch((error: unknown) => uni.showToast({ title: getAssessmentExecutionErrorMessage(error, '课程考核启动失败'), icon: 'none' }));
    return;
  }
  void startTask(item);
}

/** 返回课程列表，避免在模板中直接引用全局 uni 对象造成类型推断问题。 */
function goBack(): void {
  uni.navigateBack();
}

onBeforeUnmount(() => {
  if (assetProcessTimer !== undefined) globalThis.clearInterval(assetProcessTimer);
  if (watermarkRefreshTimer !== undefined) {
    globalThis.clearTimeout(watermarkRefreshTimer);
    watermarkRefreshTimer = undefined;
  }
  stopLearningTimers();
  void reportCurrentSession('SCREEN_LEAVE');
});

onHide(() => setPageVisible(false));
onShow(() => {
  setPageVisible(true);
  if (tasks.value) void refreshTaskProgress();
});

watch(activeItem, (item) => setLearningTitle(item?.itemTitle), { immediate: true });

onLoad((query) => {
  enrollmentId = Number(query?.enrollmentId);
  initialCourseItemId = Number(query?.courseItemId) || 0;
  void loadCourse();
});
</script>

<template>
  <MobileBaseLayout>
    <view class="page learning-page">
      <view v-if="loading" class="state-panel"><text>正在加载课程…</text></view>
      <view v-else-if="errorMessage" class="state-panel">
        <text>{{ errorMessage }}</text>
        <button class="retry-button" @click="loadCourse">重新加载</button>
      </view>
      <template v-else-if="tasks">
        <view v-if="mutexBlockedMessage" class="mutex-notice">
          <text class="mutex-notice-icon">!</text>
          <text class="mutex-notice-text">{{ mutexBlockedMessage }}</text>
        </view>
        <view v-else-if="verificationStoppedMessage" class="mutex-notice mutex-notice--error">
          <text class="mutex-notice-icon">!</text>
          <text class="mutex-notice-text">{{ verificationStoppedMessage }}</text>
        </view>
        <view class="course-hero">
          <view class="hero-top">
            <view class="hero-back" @click="goBack">‹</view>
            <text class="hero-status">{{ tasks.learningStatus === 'COMPLETED' ? '已完成' : '必修课程' }}</text>
          </view>
          <text class="hero-title">{{ tasks.courseName }}</text>
          <text class="hero-subtitle">{{ tasks.offeringName }}</text>
          <view class="hero-progress">
            <view>
              <text class="hero-progress-label">已学完课时</text>
              <text class="hero-progress-number">
                {{ completedItems }}
                <text class="hero-progress-total">/ {{ totalItems }}</text>
              </text>
            </view>
            <view class="hero-ring">
              <text>{{ tasks.progressPercent }}%</text>
            </view>
          </view>
        </view>

        <button v-if="activeItem?.linkedQuizItemId" class="task-button" @tap="openLinkedQuiz">课件试题</button>
        <view
          v-if="activeItem && (asset || assetLoading || assetErrorMessage)"
          class="content-card preview-card"
          :class="{ 'is-pdf-preview': playInfo?.format === 'pdf' }"
        >
          <view class="section-heading">
            <text class="section-title">正在学习</text>
            <text class="section-count">{{ activeItem.itemTitle }}</text>
          </view>
          <view class="preview-stage">
            <AssetPreview
              v-if="asset"
              :key="activeItem.courseItemId"
              :asset="asset"
              :play-info="playInfo"
              :loading="assetLoading"
              :error-message="assetErrorMessage"
              :initial-position-seconds="viewerInitialPositionSeconds"
              :initial-page-no="viewerInitialPageNo"
              :allow-speed-play="isSpeedPlayAllowed"
              @timeupdate="onMediaTimeUpdate"
              @pageupdate="onPdfPageUpdate"
              @play="onMediaPlay"
              @pause="onMediaPause"
              @ended="onMediaEnded"
              @open="openControlledFile"
            />
            <view v-else class="preview-loading">
              <text>{{ assetLoading ? '正在打开素材…' : assetErrorMessage }}</text>
            </view>
          </view>
          <!-- 显示学时说明：按课程开关展示有效学习时长口径与本项已记录时长。 -->
          <text v-if="isStudyDescriptionEnabled" class="study-hours-note">{{ studyDescriptionText }}</text>
        </view>

        <view class="content-card directory-card" :style="isWatermarkEnabled ? watermarkStyle : undefined">
          <view class="section-heading">
            <text class="section-title">课程目录</text>
            <text class="section-count">{{ tasks.progressPercent }}% 已完成</text>
            <button class="directory-toggle" @tap="directoryExpanded = !directoryExpanded">
              {{ directoryExpanded ? '收起目录' : '展开目录' }}
            </button>
          </view>
          <view v-show="directoryExpanded" class="directory-list">
            <view v-for="chapter in orderCourseChapters(tasks.chapters)" :key="chapter.chapterId" class="chapter" :style="{ marginLeft: chapter.depth * 12 + 'px' }">
              <view class="chapter-heading">
                <text class="chapter-name">{{ chapter.sortOrder }}. {{ chapter.chapterName }}</text>
                <text class="chapter-progress">
                  {{ chapter.items.filter((item) => item.progressStatus === 'COMPLETED').length }}/{{ chapter.items.length }}
                </text>
              </view>
              <view
                v-for="item in chapter.items"
                :key="item.courseItemId"
                class="task-card"
                :class="{ disabled: !item.learnable, active: activeItem?.courseItemId === item.courseItemId }"
              >
                <view class="task-icon">{{ item.itemType === 'QUIZ' ? '测' : item.itemType === 'QUESTION_BANK' ? '练' : '学' }}</view>
                <view class="task-body">
                  <text class="task-title">{{ item.itemTitle }}</text>
                  <text v-if="item.itemType === 'LIVE'" class="task-meta">直播 · {{ item.liveSession?.startTime?.replace('T', ' ') || '时间待发布' }} 至 {{ item.liveSession?.endTime?.replace('T', ' ') || '时间待发布' }}</text>
                  <text class="task-meta">{{ item.isRequired === 1 ? '必修' : '选修' }} · {{ getTaskStatusText(item) }}</text>
                </view>
                <button v-if="item.canRetake" class="task-button" @click="openTask(item, true)">补测一次</button>
                <button class="task-button" :disabled="!item.learnable" @click="openTask(item)">
                  {{ item.learnable ? (isAssessmentItem(item) ? getAssessmentActionText(item) : '进入') : '锁定' }}
                </button>
              </view>
            </view>
          </view>
        </view>

        <view class="content-card course-info">
          <view class="info-tabs">
            <text class="info-tab" :class="{ active: activeInfoTab === 'INTRODUCTION' }" @click="activeInfoTab = 'INTRODUCTION'">课程简介</text>
            <text class="info-tab" :class="{ active: activeInfoTab === 'DOWNLOADS' }" @click="activeInfoTab = 'DOWNLOADS'">课程附件</text>
          </view>
          <view v-if="activeInfoTab === 'INTRODUCTION'" class="course-introduction">
            <text class="info-title">{{ enrollmentDetail?.course?.courseName ?? tasks.courseName }}</text>
            <text class="info-copy">{{ enrollmentDetail?.course?.introduction || '暂无课程简介。' }}</text>
            <view class="info-row">
              <text>教学目标</text>
              <text>{{ enrollmentDetail?.course?.teachingObjective || '暂无' }}</text>
            </view>
            <view class="info-row">
              <text>考核方式</text>
              <text>{{ enrollmentDetail?.course?.assessmentMethod || '按课程规则判定' }}</text>
            </view>
          </view>
          <view v-else class="course-downloads">
            <view v-if="!enrollmentDetail?.downloadAssets.length" class="empty-extra">暂无课程附件。</view>
            <view
              v-for="downloadAsset in enrollmentDetail?.downloadAssets"
              :key="downloadAsset.assetId"
              class="download-card"
              :class="{ disabled: !downloadAsset.downloadable }"
              @click="downloadCourseAttachment(downloadAsset)"
            >
              <text class="download-card-title">{{ downloadAsset.assetName || `课程附件 #${downloadAsset.assetId}` }}</text>
              <text class="download-card-meta">素材类型：{{ downloadAsset.assetType || '未知类型' }}</text>
              <text class="download-card-meta">文件名称：{{ downloadAsset.fileName || '未知文件' }}</text>
              <text class="download-card-meta">MIME 类型：{{ downloadAsset.mimeType || '未知' }}</text>
              <text class="download-card-meta">文件大小：{{ formatAttachmentSize(downloadAsset.fileSize) }}</text>
              <text class="download-card-action">
                {{
                  downloadingAssetId === downloadAsset.assetId
                    ? '下载中'
                    : downloadAsset.downloadable
                      ? '点击下载'
                      : getDownloadUnavailableReasonText(downloadAsset.unavailableReason)
                }}
              </text>
            </view>
          </view>
        </view>
      </template>

      <!-- 防挂机验证题：随机间隔弹出，作答期间不计有效学习时长，超时结束会话。 -->
      <view v-if="verificationVisible" class="verify-mask">
        <view class="verify-card">
          <text class="verify-title">学习验证</text>
          <text class="verify-tip">为防止挂机，请在 {{ verificationRemainingSeconds }} 秒内回答下面的问题：</text>
          <text class="verify-question">{{ verificationPrompt }}</text>
          <input
            v-model="verificationAnswer"
            class="verify-input"
            type="number"
            placeholder="请输入答案"
            :focus="true"
            @confirm="submitVerification"
          >
          <text v-if="verificationError" class="verify-error">{{ verificationError }}</text>
          <button class="verify-submit" type="button" @click="submitVerification">提交答案</button>
        </view>
      </view>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.page {
  padding-bottom: 64rpx;
}

.learning-page .course-hero,
.learning-page .course-info {
  display: none;
}

.course-hero {
  margin: 0 -32rpx;
  padding: 32rpx 48rpx 44rpx;
  background: #fff;
  color: #17181c;
}

.hero-top,
.hero-progress,
.section-heading,
.chapter-heading,
.task-card,
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.download-card {
  display: flex;
  flex-direction: column;
  margin-top: 18rpx;
  padding: 24rpx;
  border: 1rpx solid #f1f1f3;
  border-radius: 18rpx;
  background: #fbfcff;
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 6%);
}

.download-card.disabled {
  opacity: 0.62;
}

.download-card-title {
  color: #17181c;
  font-size: 28rpx;
  font-weight: 700;
}

.download-card-meta {
  margin-top: 8rpx;
  color: #8d8f96;
  font-size: 23rpx;
  line-height: 1.5;
}

.download-card-action {
  margin-top: 16rpx;
  color: #2563eb;
  font-size: 24rpx;
  font-weight: 600;
}

.hero-back {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  background: #bfdbfe;
  color: #2563eb;
  font-size: 64rpx;
  line-height: 54rpx;
  text-align: center;
}

.hero-status {
  padding: 8rpx 16rpx;
  border: 0;
  background: #bfdbfe;
  border-radius: 24rpx;
  color: #2563eb;
  font-size: 22rpx;
}

.hero-title {
  display: block;
  margin-top: 44rpx;
  font-size: 44rpx;
  font-weight: 700;
}

.hero-subtitle {
  display: block;
  margin-top: 12rpx;
  color: #8d8f96;
  font-size: 25rpx;
}

.hero-progress {
  margin-top: 54rpx;
}

.hero-progress-label {
  display: block;
  color: #8d8f96;
  font-size: 25rpx;
}

.hero-progress-number {
  display: block;
  margin-top: 8rpx;
  font-size: 56rpx;
  font-weight: 700;
}

.hero-progress-total {
  font-size: 30rpx;
  font-weight: 400;
}

.hero-ring {
  display: flex;
  width: 148rpx;
  height: 148rpx;
  align-items: center;
  justify-content: center;
  border: 12rpx solid rgb(45 104 229 / 80%);
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

.preview-card {
  margin: 0 -32rpx 24rpx;
  padding: 0 0 24rpx;
  overflow: visible;
  border-right: 0;
  border-left: 0;
  border-radius: 0;
  box-shadow: none;
}

.preview-card .section-heading {
  padding: 26rpx 32rpx 16rpx;
}

/* 水印已改挂到下方课程目录卡片，素材预览区不再叠加任何水印。 */
.preview-stage {
  position: relative;
}

.study-hours-note {
  display: block;
  padding: 20rpx 32rpx 0;
  color: #8d8f96;
  font-size: 24rpx;
  line-height: 1.7;
}

.preview-card .section-count {
  max-width: 320rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-card.is-pdf-preview .section-heading {
  min-height: 72rpx;
  padding-top: 16rpx;
  padding-bottom: 12rpx;
}

.preview-card.is-pdf-preview .section-title {
  font-size: 30rpx;
}

.preview-loading {
  display: flex;
  min-height: 260rpx;
  align-items: center;
  justify-content: center;
  background: #bfdbfe;
  color: #2563eb;
  font-size: 25rpx;
}

.section-title {
  color: #17181c;
  font-size: 38rpx;
  font-weight: 700;
}

.directory-toggle {
  min-width: 132rpx;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: #2563eb;
  font-size: 22rpx;
  line-height: 1.5;
}

.directory-toggle::after {
  border: 0;
}

.directory-list {
  margin-top: 12rpx;
}

/*
 * 学习水印挂在课程目录卡片的背景层（白底之上、内容之下）：
 * 卡片留白、章节行与条目间隙处可见水印，条目卡与按钮自带底色会遮住水印，
 * 因此水印不会压在可读文字的关键信息上。
 */
.directory-card {
  background-image: var(--learning-watermark-image, none);
  background-repeat: repeat;
  background-size: var(--learning-watermark-size, auto);
}

.section-count,
.chapter-progress,
.task-meta,
.download-status {
  color: #8d8f96;
  font-size: 22rpx;
}

.section-count {
  padding: 8rpx 14rpx;
  border-radius: 20rpx;
  background: #bfdbfe;
  color: #2563eb;
}

.chapter {
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

.task-card {
  gap: 16rpx;
  margin-top: 18rpx;
  padding: 22rpx 16rpx;
  border: 1rpx solid #f1f1f3;
  border-radius: 16rpx;
  background: #fbfcff;
}

.task-card.disabled {
  opacity: 0.56;
}
.task-card.active {
  border-color: rgb(45 104 229 / 80%);
  background: #bfdbfe;
}
.task-card.active .task-title,
.task-card.active .task-meta,
.task-card.active .task-button {
  color: #2563eb;
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
}

.task-button,
.retry-button {
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

.course-info {
  padding-top: 0;
}

.info-tabs {
  display: flex;
  gap: 36rpx;
  border-bottom: 1rpx solid #f1f1f3;
}

.info-tab {
  padding: 28rpx 0 20rpx;
  border-bottom: 4rpx solid transparent;
  color: #92939a;
  font-size: 27rpx;
}

.info-tab.active {
  border-bottom-color: #2563eb;
  color: #17181c;
  font-weight: 700;
}

.course-introduction,
.course-downloads {
  padding-top: 24rpx;
}

.info-title {
  display: block;
  color: #17181c;
  font-size: 29rpx;
  font-weight: 700;
}

.info-copy {
  display: block;
  margin-top: 14rpx;
  color: #676971;
  font-size: 25rpx;
  line-height: 1.7;
}

.info-row,
.download-item {
  gap: 20rpx;
  margin-top: 18rpx;
  color: #777981;
  font-size: 24rpx;
  line-height: 1.6;
}

.info-row > text:last-child,
.download-status {
  flex: 1;
  text-align: right;
}

.empty-extra,
.state-panel {
  color: #8b8d94;
  font-size: 25rpx;
  line-height: 1.7;
}

.state-panel {
  display: flex;
  margin-top: 28rpx;
  padding: 64rpx 28rpx;
  align-items: center;
  flex-direction: column;
  gap: 24rpx;
  border-radius: 20rpx;
  background: #fff;
  text-align: center;
}

/* 学习互斥提示：与其他终端/账户的学习冲突时置顶展示，引导先结束另一端。 */
.mutex-notice {
  display: flex;
  margin-top: 20rpx;
  padding: 20rpx 24rpx;
  align-items: center;
  gap: 16rpx;
  border-radius: 16rpx;
  background: #fff7e8;
}

.mutex-notice-icon {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: #f5a623;
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 36rpx;
  text-align: center;
  flex-shrink: 0;
}

.mutex-notice-text {
  color: #8a5a00;
  font-size: 24rpx;
  line-height: 1.6;
}

.mutex-notice--error .mutex-notice-icon {
  background: #d5494d;
}

.mutex-notice--error .mutex-notice-text {
  color: #a1252a;
}

/* 防挂机验证题弹层：覆盖全屏，作答期间不计有效学习时长。 */
.verify-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  background: rgba(15, 23, 42, 0.55);
  z-index: 100;
}

.verify-card {
  width: 100%;
  padding: 48rpx 40rpx;
  border-radius: 24rpx;
  background: #fff;
  text-align: center;
}

.verify-title {
  color: #1d2939;
  font-size: 32rpx;
  font-weight: 600;
}

.verify-tip {
  display: block;
  margin-top: 16rpx;
  color: #667085;
  font-size: 24rpx;
  line-height: 1.6;
}

.verify-question {
  display: block;
  margin: 28rpx 0;
  color: #1d2939;
  font-size: 52rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}

.verify-input {
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 16rpx;
  background: #f2f4f7;
  color: #1d2939;
  font-size: 30rpx;
  text-align: center;
}

.verify-error {
  display: block;
  margin-top: 16rpx;
  color: #d92d20;
  font-size: 24rpx;
}

.verify-submit {
  margin-top: 32rpx;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 16rpx;
  background: #2f6bff;
  color: #fff;
  font-size: 30rpx;
}
</style>
