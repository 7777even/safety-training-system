<script setup lang="ts">
import { orderCourseChapters } from '@shared/libs/course-chapters';
import type { CourseEvaluationListItem, LearnerCourseDetail, LearnerCourseTaskItem, LearnerCourseTasks } from '@shared/types';
import { getAssessmentExecutionErrorMessage } from '@shared/libs/assessment-execution';
import { formatAttachmentSize, getDownloadUnavailableReasonText } from '@shared/utils/attachment';
import { getStudyDescriptionText } from '@shared/utils/study';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElAlert, ElButton, ElDialog, ElInput, ElMessage } from 'element-plus';
import { ArrowDown, ArrowUp, Bottom, Top } from '@element-plus/icons-vue';
import { type AssetDetail, type AssetPlayInfo, getAssetDetail, getAssetPlayInfo } from '@/api/asset';
import AssetViewer from '@/components/asset-viewer/AssetViewer.vue';
import { useUserStore } from '@/stores';
import {
  LearnerCourseApiError,
  participateCourseLive,
  downloadLearnerCourseAsset,
  getLearnerCourseDetail,
  getLearnerCourseTasks,
  getPeerCourseEvaluations,
  reportStudySession,
  startCourseExamAttempt,
  submitCourseEvaluation
} from '@/api/learner-course';
import { getOrCreateLearningDeviceId } from '@/utils/learning-device';
import {
  VERIFICATION_ANSWER_TIMEOUT_SECONDS,
  buildArithmeticQuestion,
  nextVerificationDelayMs
} from '@shared/utils/learning-verification';
import { buildRandomWatermarkTile, nextWatermarkDelayMs } from '@shared/utils/watermark';
import { DEFAULT_LIST_PAGE_SIZE } from '@/constants/pagination';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const tasks = ref<LearnerCourseTasks | null>(null);
const enrollmentDetail = ref<LearnerCourseDetail | null>(null);
const downloadAssets = ref<LearnerCourseDetail['downloadAssets']>([]);
const activeInfoTab = ref<'INTRODUCTION' | 'DOWNLOADS' | 'EVALUATIONS' | 'REPORT'>('INTRODUCTION');
/** 课程评价与简介、附件同级展示；添加评价仍使用右侧抽屉。 */
const peerEvaluations = ref<CourseEvaluationListItem[]>([]);
const peerPageNum = ref(1);
const peerTotal = ref(0);
const peerLoading = ref(false);
const peerError = ref('');
const evaluationDrawerVisible = ref(false);
const evaluationForm = ref({ rating: 5, content: '', isAnonymous: 0 as 0 | 1 });
const submittingEvaluation = ref(false);
const evaluationSubmitted = ref(false);
const activeItem = ref<LearnerCourseTaskItem | null>(null);
const assetDetail = ref<AssetDetail | null>(null);
const playInfo = ref<AssetPlayInfo | null>(null);
const loading = ref(true);
const assetLoading = ref(false);
const errorMessage = ref('');
const assetErrorMessage = ref('');
const currentPositionSeconds = ref(0);
const currentPageNo = ref(1);
const pageCount = ref(0);
const viewerInitialPositionSeconds = ref(0);
const viewerInitialPageNo = ref(1);
const downloadingAssetId = ref<number | null>(null);
let sessionStartedAt = 0;
let sessionTraceId = '';
let reportSequence = 0;
let reportTimer: ReturnType<typeof globalThis.setInterval> | undefined;
let assetProcessTimer: ReturnType<typeof globalThis.setInterval> | undefined;
let accumulatedStudySeconds = 0;
let activeTrackingStartedAt = 0;
const pageVisible = ref(true);
const mediaPlaying = ref(false);
/** 本浏览器持久化的终端标识，随学习会话上报，用于同一终端多账户互斥检测。 */
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

const enrollmentId = computed(() => Number(route.params.enrollmentId));
/** 学习侧四项开关来自冻结课程快照；管理端改动课程基础信息后需重新发布才对已报名学员生效。 */
const courseInfo = computed(() => enrollmentDetail.value?.course ?? null);
const isWatermarkEnabled = computed(() => courseInfo.value?.isWatermarkEnabled === 1);
const isStudyDescriptionEnabled = computed(() => courseInfo.value?.isStudyDescriptionEnabled === 1);
const isSpeedPlayAllowed = computed(() => courseInfo.value?.isSpeedPlayAllowed === 1);
/** 水印内容优先取员工编号（账号编码），未取到时回退用户名，避免出现空水印。 */
const watermarkText = computed(() => userStore.userInfo?.userCode ?? userStore.userInfo?.userName ?? '学员');
/**
 * 水印只覆盖页面空白面（素材之外），不下发到素材容器：
 * 图案挂在 `learning-page` 根容器的背景层，出现在页面左右两侧留白、
 * 素材区下方的空白页与卡片缝隙；素材画面本身完全没有水印，不妨碍观看学习。
 * 未开启水印时不下发变量，`var(..., none)` 兜底为空、不渲染任何图案。
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
    '--learning-watermark-size': `${tile.width}px ${tile.height}px`
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
const activeChapterTitle = computed(() => {
  if (!tasks.value || !activeItem.value) return '';
  return (
    tasks.value.chapters.find((chapter) => chapter.items.some((item) => item.courseItemId === activeItem.value?.courseItemId))?.chapterName ?? ''
  );
});
const mediaFormat = computed(() => playInfo.value?.format?.toLowerCase() ?? null);
const isPdfAsset = computed(
  () => mediaFormat.value === 'pdf' || (mediaFormat.value === 'document' && assetDetail.value?.assetName?.toLowerCase().endsWith('.pdf') === true)
);
const isTimedMedia = computed(() => mediaFormat.value === 'hls');
const controlledDownloadUrl = computed(() => {
  if (!assetDetail.value?.assetId || !assetDetail.value.fileInfoId) return null;
  return `/api/asset/files/${assetDetail.value.assetId}/${assetDetail.value.fileInfoId}`;
});

/** 学习位置摘要的标签，右栏统一展示页码、计时或考核入口状态。 */
const positionLabel = computed(() => {
  if (isPdfAsset.value && pageCount.value > 0) return '文档页码';
  if (isTimedMedia.value) return '本次学习';
  if (activeItem.value && isExamItem(activeItem.value)) return getCourseExamTypeText(activeItem.value);
  return '学习记录';
});
const positionValue = computed(() => {
  if (isTimedMedia.value) return `${Math.floor(currentPositionSeconds.value)} 秒`;
  if (activeItem.value && isExamItem(activeItem.value)) return getCourseExamStatusText(activeItem.value);
  return '已保存';
});

/** 长页面阅读辅助：根节点用于定位真正的滚动容器。 */
const pageRoot = ref<globalThis.HTMLElement | null>(null);
/** 长按方向：-1 向上、1 向下、0 表示当前未长按。 */
const holding = ref<-1 | 0 | 1>(0);
/** 内容超出容器时才显示辅助控件，短页面不出现无效按钮。 */
const canScroll = ref(false);
let scrollFrame: number | undefined;
let scrollStep = 0;
let scrollContainer: globalThis.HTMLElement | null = null;
let assistObserver: globalThis.ResizeObserver | undefined;

/** 门户布局把滚动交给内容容器，因此从学习页向上查找真正的滚动祖先。 */
function resolveScrollContainer(): globalThis.HTMLElement | null {
  let node = pageRoot.value?.parentElement ?? null;
  while (node) {
    const { overflowY } = globalThis.getComputedStyle(node);
    if (overflowY === 'auto' || overflowY === 'scroll') return node;
    node = node.parentElement;
  }
  return (globalThis.document.scrollingElement as globalThis.HTMLElement | null) ?? null;
}

function getMaxScroll(container: globalThis.HTMLElement): number {
  return Math.max(0, container.scrollHeight - container.clientHeight);
}

/** 容器或内容尺寸变化后同步辅助控件的显示状态。 */
function syncScrollAssist(): void {
  if (!scrollContainer) scrollContainer = resolveScrollContainer();
  canScroll.value = scrollContainer ? getMaxScroll(scrollContainer) > 8 : false;
}

/** 长按结束、指针移出控件或窗口失焦时立即停止，避免滚动失控。 */
function stopScroll(): void {
  scrollStep = 0;
  holding.value = 0;
  if (scrollFrame !== undefined) {
    globalThis.cancelAnimationFrame(scrollFrame);
    scrollFrame = undefined;
  }
}

/** 每帧推进一次滚动，到达顶端或底端后自动停止。 */
function stepScroll(): void {
  const container = scrollContainer ?? resolveScrollContainer();
  if (!container || scrollStep === 0) {
    stopScroll();
    return;
  }
  const next = Math.min(getMaxScroll(container), Math.max(0, container.scrollTop + scrollStep));
  if (next === container.scrollTop) {
    stopScroll();
    return;
  }
  container.scrollTop = next;
  scrollFrame = globalThis.requestAnimationFrame(stepScroll);
}

/** 长按开始持续滚动，步长按容器高度自适应，长文档也能快速浏览。 */
function startScroll(direction: -1 | 1): void {
  const container = scrollContainer ?? resolveScrollContainer();
  if (!container || getMaxScroll(container) <= 0) return;
  scrollContainer = container;
  stopScroll();
  scrollStep = direction * Math.max(24, Math.round(container.clientHeight * 0.06));
  holding.value = direction;
  stepScroll();
}

/** 一键回到最顶端或直达最低端，平滑滚动保留方向感。 */
function scrollToEdge(edge: 'top' | 'bottom'): void {
  const container = scrollContainer ?? resolveScrollContainer();
  if (!container) return;
  stopScroll();
  container.scrollTo({ top: edge === 'top' ? 0 : getMaxScroll(container), behavior: 'smooth' });
}

/** 判断当前内容项是否为课程题库练习或课程测验。 */
function isExamItem(item: LearnerCourseTaskItem): boolean {
  return item.itemType === 'QUESTION_BANK' || item.itemType === 'QUIZ';
}

/** 将课程考核内容项转换为学员可理解的任务类型。 */
function getCourseExamTypeText(item: LearnerCourseTaskItem): string {
  if (item.itemType === 'QUESTION_BANK') return '题库练习';
  return '课程测验';
}

/** 累计当前有效学习区间，仅在可见、实际播放（或非媒体停留）、无验证题且未被停止学习时运行。 */
function syncStudyClock(now = Date.now()): void {
  const countable = pageVisible.value && !verificationVisible.value
    && !mutexBlockedMessage.value && !verificationStoppedMessage.value
    && (!isTimedMedia.value || mediaPlaying.value);
  if (activeTrackingStartedAt && countable) {
    accumulatedStudySeconds += Math.max(0, Math.floor((now - activeTrackingStartedAt) / 1000));
  }
  activeTrackingStartedAt = countable ? now : 0;
}

/** 停止全部学习定时器：30 秒上报、验证题调度与作答倒计时。 */
function stopLearningTimers(): void {
  if (reportTimer !== undefined) {
    globalThis.clearInterval(reportTimer);
    reportTimer = undefined;
  }
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
  // 先以 TIMEOUT 结束当前会话（此时弹窗仍开着、计时保持暂停，守卫尚未置位），再置停止提示并收尾。
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

/** 关闭验证题弹窗并清理其调度与倒计时定时器。 */
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

/** 页面隐藏时停止有效学习计时，恢复可见后继续。 */
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

/** 根据内容项类型返回学习状态文案。 */
function getProgressText(item: LearnerCourseTaskItem): string {
  if (!item.learnable) return '暂不可学习';
  if (isExamItem(item)) return getCourseExamStatusText(item);
  if (item.progressStatus === 'COMPLETED') return '已完成';
  if (item.progressStatus === 'LEARNING') return '学习中';
  return '未开始';
}

/** 将课程考试任务事实转换为目录和任务面板使用的状态文案。 */
function getCourseExamStatusText(item: LearnerCourseTaskItem): string {
  if (item.scorePublishStatus === 'EFFECTIVE' && item.publishedExamAttemptId) {
    return `结果已生效：${item.finalScore ?? '-'} 分，${item.isPassed === 1 ? '已及格' : '未及格'}`;
  }
  if (item.examTaskStatus === 'IN_PROGRESS' && item.activeExamAttemptId) return '正在作答';
  if (item.examTaskStatus === 'WAITING_RESULT' && item.activeExamAttemptId) return '已交卷，等待阅卷完成';
  return '未开始';
}

/** 根据课程考试任务状态给出唯一主操作，避免学员选择错误入口。 */
function getCourseExamActionText(item: LearnerCourseTaskItem): string {
  if (item.scorePublishStatus === 'EFFECTIVE' && item.publishedExamAttemptId) return '查看结果';
  if (item.activeExamAttemptId && item.examTaskStatus !== 'WAITING_RESULT') return '继续作答';
  if (item.activeExamAttemptId) return '查看已交卷';
  return item.itemType === 'QUIZ' ? '开始测验' : '开始练习';
}

/** 释放当前素材播放器和定时上报。 */
function cleanupMedia(): void {
  if (reportTimer) globalThis.clearInterval(reportTimer);
  reportTimer = undefined;
}

/** 清空素材播放状态；考试类内容项不会创建素材学习会话。 */
function resetAssetState(): void {
  if (assetProcessTimer !== undefined) globalThis.clearInterval(assetProcessTimer);
  assetProcessTimer = undefined;
  assetDetail.value = null;
  playInfo.value = null;
  assetErrorMessage.value = '';
  assetLoading.value = false;
  currentPositionSeconds.value = 0;
  currentPageNo.value = 1;
  viewerInitialPositionSeconds.value = 0;
  viewerInitialPageNo.value = 1;
  pageCount.value = 0;
}

/** 读取课程任务快照，并默认选中第一个可学习内容项。 */
async function loadTasks(): Promise<void> {
  if (!enrollmentId.value) {
    errorMessage.value = '课程参数无效。';
    loading.value = false;
    return;
  }
  try {
    const [detail, taskSnapshot] = await Promise.all([getLearnerCourseDetail(enrollmentId.value), getLearnerCourseTasks(enrollmentId.value)]);
    enrollmentDetail.value = detail;
    tasks.value = taskSnapshot;
    downloadAssets.value = detail.downloadAssets;
    const items = tasks.value.chapters.flatMap((chapter) => chapter.items);
    await selectItem(items.find((item) => item.learnable) ?? items[0] ?? null);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程学习任务加载失败。';
  } finally {
    loading.value = false;
  }
}

/** 从当前报名读取同次开课评价，分页失败时保留重试入口。 */
async function loadPeerEvaluations(): Promise<void> {
  if (!enrollmentId.value) return;
  peerLoading.value = true;
  peerError.value = '';
  try {
    const page = await getPeerCourseEvaluations(enrollmentId.value, { pageNum: peerPageNum.value, pageSize: DEFAULT_LIST_PAGE_SIZE });
    peerEvaluations.value = page.list;
    peerTotal.value = page.total;
  } catch (error) {
    peerError.value = error instanceof Error ? error.message : '评价加载失败，请重试。';
  } finally {
    peerLoading.value = false;
  }
}

/** 切换至课程评价时加载公开评价，本人评价复用课程详情事实。 */
function showEvaluations(): void {
  activeInfoTab.value = 'EVALUATIONS';
  void loadPeerEvaluations();
}

/** 提交已完成课程评价后回读本人详情和同课列表。 */
async function saveEvaluation(): Promise<void> {
  if (submittingEvaluation.value || evaluationSubmitted.value || enrollmentDetail.value?.learningStatus !== 'COMPLETED' || enrollmentDetail.value.evaluation) return;
  submittingEvaluation.value = true;
  try {
    await submitCourseEvaluation(enrollmentId.value, evaluationForm.value);
    evaluationSubmitted.value = true;
    evaluationDrawerVisible.value = false;
    ElMessage.success('课程评价已提交。');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '课程评价失败，请稍后重试。');
    submittingEvaluation.value = false;
    return;
  }
  try {
    enrollmentDetail.value = await getLearnerCourseDetail(enrollmentId.value);
  } catch {
    ElMessage.warning('评价已提交，请刷新页面查看本人评价。');
  } finally {
    peerPageNum.value = 1;
    await loadPeerEvaluations();
    submittingEvaluation.value = false;
  }
}

/** 上报当前内容项的学习会话，使用递增 trace 保持每次检查点幂等。 */
async function reportCurrentSession(
  endReason: 'USER_EXIT' | 'SCREEN_LEAVE' | 'COMPLETED' | 'HEARTBEAT' | 'TIMEOUT' = 'USER_EXIT'
): Promise<void> {
  if (!tasks.value || !activeItem.value || !sessionStartedAt || !sessionTraceId) return;
  // 学习互斥阻塞或验证题超时后不再上报：本终端计时已暂停。
  if (mutexBlockedMessage.value || verificationStoppedMessage.value) return;
  const now = Date.now();
  // 先结算「上次结算点 → 现在」的活跃区间：心跳等时间驱动的检查点不经过可见性变化或媒体事件，
  // 不先结算就会读到尚未累计的秒数，纯文档类内容项的有效学习时长会恒为 0。
  syncStudyClock(now);
  // 后端以 enrollmentId + clientTraceId 做单次上报幂等；每个检查点使用递增 trace，重试同一检查点仍复用该值。
  reportSequence += 1;
  try {
    await reportStudySession({
      enrollmentId: tasks.value.enrollmentId,
      courseItemId: activeItem.value.courseItemId,
      clientTraceId: `${sessionTraceId}-${reportSequence}`,
      deviceType: 'PC',
      deviceId: learningDeviceId,
      startedTime: formatLocalDateTime(new Date(sessionStartedAt)),
      endedTime: formatLocalDateTime(new Date(now)),
      validStudySeconds: accumulatedStudySeconds,
      startPositionSeconds: activeItem.value.lastPositionSeconds,
      endPositionSeconds: isTimedMedia.value ? Math.floor(currentPositionSeconds.value) : null,
      startPageNo: activeItem.value.lastPageNo,
      endPageNo: isPdfAsset.value ? currentPageNo.value : null,
      endReason
    });
  } catch (error) {
    // 5095/5096 为学习互斥：停止本终端计时并提示，不按普通失败重试。
    if (error instanceof LearnerCourseApiError && (error.code === 5095 || error.code === 5096)) {
      blockLearningByMutex(error.message);
      return;
    }
    throw error;
  }
  accumulatedStudySeconds = 0;
  if (endReason !== 'SCREEN_LEAVE') await refreshTaskProgress();
}

/** 加载素材播放信息并恢复后端保存的媒体位置或文档页码。 */
async function loadAsset(item: LearnerCourseTaskItem): Promise<void> {
  if (assetProcessTimer !== undefined) globalThis.clearInterval(assetProcessTimer);
  assetProcessTimer = undefined;
  assetDetail.value = null;
  playInfo.value = null;
  assetErrorMessage.value = '';
  currentPositionSeconds.value = item.lastPositionSeconds ?? 0;
  currentPageNo.value = item.lastPageNo ?? 1;
  viewerInitialPositionSeconds.value = currentPositionSeconds.value;
  viewerInitialPageNo.value = currentPageNo.value;
  pageCount.value = 0;
  if (!item.assetId) return;
  assetLoading.value = true;
  try {
    assetDetail.value = await getAssetDetail(item.assetId);
    if (!['RICH_TEXT', 'LINK', 'LIVE'].includes(assetDetail.value.assetType)) {
      if (assetDetail.value.processStatus === 'UPLOADED' || assetDetail.value.processStatus === 'PROCESSING') {
        assetProcessTimer = globalThis.setInterval(async () => {
          try {
            const detail = await getAssetDetail(item.assetId!);
            if (activeItem.value?.courseItemId !== item.courseItemId) return;
            assetDetail.value = detail;
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
      } else if (assetDetail.value.processStatus === 'SUCCEEDED') {
        try {
          playInfo.value = await getAssetPlayInfo(item.assetId);
        } catch {
          // 保留资源详情，让 AssetViewer 继续提供受控下载入口。
          playInfo.value = null;
        }
      }
    }
  } catch (error) {
    assetErrorMessage.value = error instanceof Error ? error.message : '素材暂时无法打开。';
  } finally {
    assetLoading.value = false;
  }
}

/** 上报成功后重新读取任务快照，让目录和课程总进度立即显示后端最新状态。 */
async function refreshTaskProgress(): Promise<void> {
  if (!tasks.value || !activeItem.value) return;
  try {
    const [refreshedTasks, refreshedDetail] = await Promise.all([
      getLearnerCourseTasks(tasks.value.enrollmentId),
      getLearnerCourseDetail(tasks.value.enrollmentId)
    ]);
    const refreshedItem = refreshedTasks.chapters
      .flatMap((chapter) => chapter.items)
      .find((item) => item.courseItemId === activeItem.value?.courseItemId);
    tasks.value = refreshedTasks;
    enrollmentDetail.value = refreshedDetail;
    if (refreshedItem && activeItem.value) Object.assign(activeItem.value, refreshedItem);
  } catch {
    // 进度刷新失败不影响当前素材继续学习，下一次上报时会重试。
  }
}

/** 切换目录内容，先提交上一个内容项的最终断点。 */
async function selectItem(item: LearnerCourseTaskItem | null): Promise<void> {
  if (!item || !item.learnable) return;
  if (activeItem.value) await reportCurrentSession('USER_EXIT');
  cleanupMedia();
  activeItem.value = item;
  if (isExamItem(item) || item.itemType === 'LIVE') {
    resetAssetState();
    // 考核与直播内容项不走学习会话，取消验证题调度。
    closeVerification();
    sessionStartedAt = 0;
    sessionTraceId = '';
    reportSequence = 0;
    accumulatedStudySeconds = 0;
    activeTrackingStartedAt = 0;
    mediaPlaying.value = false;
    return;
  }
  sessionStartedAt = Date.now();
  sessionTraceId = `pc-course-${enrollmentId.value}-${item.courseItemId}-${sessionStartedAt}`;
  reportSequence = 0;
  accumulatedStudySeconds = 0;
  activeTrackingStartedAt = 0;
  mediaPlaying.value = false;
  // 切换内容项重置防挂机验证题调度，避免沿用上一个内容的定时器。
  closeVerification();
  await loadAsset(item);
  syncStudyClock();
  reportTimer = globalThis.setInterval(() => {
    // 心跳失败由全局错误处理提示，不打断当前学习；互斥拒绝在 reportCurrentSession 内转为阻塞提示。
    reportCurrentSession('HEARTBEAT').catch(() => undefined);
  }, 30000);
  scheduleVerification();
}

/** 从课程上下文创建考核作答，客户端不提交独立考试 ID 或来源字段。 */
/** 先打开空窗口，避免异步接口返回后被浏览器拦截；失败时关闭。 */
async function enterLive(): Promise<void> {
  if (!activeItem.value) return;
  const popup = globalThis.window.open('about:blank', '_blank');
  if (popup) popup.opener = null;
  try {
    const participation = await participateCourseLive(enrollmentId.value, activeItem.value.courseItemId);
    if (popup) popup.location.href = participation.liveUrl;
    else globalThis.window.location.assign(participation.liveUrl);
    await refreshTaskProgress();
  } catch (error) {
    popup?.close();
    ElMessage.error(error instanceof Error ? error.message : '直播参与失败');
  }
}

/** 配题只是现有测验入口，不创建第二份作答。 */
async function openLinkedQuiz(): Promise<void> {
  const quiz = tasks.value?.chapters.flatMap(c => c.items).find(i => i.courseItemId === activeItem.value?.linkedQuizItemId);
  if (quiz) await enterCourseExam(quiz);
}

async function enterCourseExam(item: LearnerCourseTaskItem, retry = false): Promise<void> {
  if (!tasks.value || !item.learnable || !isExamItem(item)) return;
  const publishedAttemptId = !retry && item.scorePublishStatus === 'EFFECTIVE' ? item.publishedExamAttemptId : null;
  if (publishedAttemptId) {
    await router.push({
      name: 'PortalCourseAssessmentResult',
      params: { enrollmentId: tasks.value.enrollmentId, itemId: item.courseItemId, attemptId: publishedAttemptId },
      query: { itemTitle: item.itemTitle }
    });
    return;
  }
  if (!retry && item.activeExamAttemptId) {
    if (item.examTaskStatus !== 'WAITING_RESULT') {
      await router.push({
        name: 'PortalCourseAssessmentAttempt',
        params: { enrollmentId: tasks.value.enrollmentId, itemId: item.courseItemId, attemptId: item.activeExamAttemptId },
        query: { itemTitle: item.itemTitle }
      });
      return;
    }
    await router.push({
      name: 'PortalCourseAssessmentResult',
      params: { enrollmentId: tasks.value.enrollmentId, itemId: item.courseItemId, attemptId: item.activeExamAttemptId },
      query: { itemTitle: item.itemTitle }
    });
    return;
  }
  try {
    const attempt = await startCourseExamAttempt(tasks.value.enrollmentId, item.courseItemId, {
      clientAttemptToken: `pc-course-${tasks.value.enrollmentId}-${item.courseItemId}-${Date.now()}`,
      platform: 'PC'
    });
    await router.push({
      name: 'PortalCourseAssessmentAttempt',
      params: { enrollmentId: tasks.value.enrollmentId, itemId: item.courseItemId, attemptId: attempt.examAttemptId },
      query: { itemTitle: item.itemTitle }
    });
  } catch (error) {
    ElMessage.error(getAssessmentExecutionErrorMessage(error, '课程考核启动失败。'));
  }
}

/** 处理媒体播放位置变化，供断点上报使用。 */
function onMediaTimeUpdate(value: number | { currentTarget: { currentTime: number } }): void {
  currentPositionSeconds.value = typeof value === 'number' ? value : value.currentTarget.currentTime;
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

/** 接收复用素材查看器计算出的真实 PDF 页码。 */
function onPdfPageUpdate(pageNo: number): void {
  currentPageNo.value = pageNo;
}

/** PDF 页码总数变化后同步学习页只读页码展示。 */
function onPdfPageCount(count: number): void {
  pageCount.value = count;
}

/** 通过课程学习受控接口下载附件，避免原生链接请求丢失 Bearer Token。 */
async function downloadCourseAttachment(event: globalThis.Event, asset: LearnerCourseDetail['downloadAssets'][number]): Promise<void> {
  event.preventDefault();
  if (downloadingAssetId.value !== null) return;
  if (!asset.downloadUrl) {
    ElMessage.warning('当前附件暂未提供下载地址');
    return;
  }
  downloadingAssetId.value = asset.assetId;
  try {
    const blob = await downloadLearnerCourseAsset(enrollmentId.value, asset.assetId, asset.downloadUrl);
    const url = globalThis.URL.createObjectURL(blob);
    const link = globalThis.document.createElement('a');
    link.href = url;
    link.download = asset.assetName || `课程附件-${asset.assetId}`;
    link.click();
    globalThis.setTimeout(() => globalThis.URL.revokeObjectURL(url), 0);
  } finally {
    downloadingAssetId.value = null;
  }
}

/** 兼容旧分支原生媒体节点的时间事件，实际素材统一由 AssetViewer 渲染。 */
/** 素材播放器加载元数据后恢复后端保存的断点。 */
function restoreMediaPosition(): void {
  // AssetViewer 内部播放器负责真正设置 currentTime，此事件只保留扩展点。
}

/** 媒体自然播放结束时通知后端完成当前学习会话。 */
async function onMediaEnded(): Promise<void> {
  onMediaPause();
  await reportCurrentSession('COMPLETED');
  if (reportTimer) {
    globalThis.clearInterval(reportTimer);
    reportTimer = undefined;
  }
  ElMessage.success('本项素材学习已提交。');
}

/** 页面退出前保存当前内容项的最终位置，并释放滚动与尺寸监听。 */
onBeforeUnmount(() => {
  if (assetProcessTimer !== undefined) globalThis.clearInterval(assetProcessTimer);
  if (watermarkRefreshTimer !== undefined) {
    globalThis.clearTimeout(watermarkRefreshTimer);
    watermarkRefreshTimer = undefined;
  }
  stopLearningTimers();
  void reportCurrentSession('SCREEN_LEAVE');
  cleanupMedia();
  stopScroll();
  assistObserver?.disconnect();
  scrollContainer?.removeEventListener('scroll', syncScrollAssist);
  globalThis.removeEventListener('resize', syncScrollAssist);
  globalThis.removeEventListener('blur', stopScroll);
  globalThis.document.removeEventListener('pointerup', stopScroll);
  globalThis.document.removeEventListener('visibilitychange', handleVisibilityChange);
});

/** 浏览器切后台时暂停有效学习计时。 */
function handleVisibilityChange(): void {
  setPageVisible(!globalThis.document.hidden);
}

onMounted(() => {
  globalThis.document.addEventListener('visibilitychange', handleVisibilityChange);
  globalThis.addEventListener('resize', syncScrollAssist);
  globalThis.addEventListener('blur', stopScroll);
  globalThis.document.addEventListener('pointerup', stopScroll);
  void loadTasks();
  scrollContainer = resolveScrollContainer();
  scrollContainer?.addEventListener('scroll', syncScrollAssist, { passive: true });
  // 任务载入会显著改变内容高度，用尺寸观察器统一同步辅助控件的显示状态。
  if (pageRoot.value && typeof globalThis.ResizeObserver !== 'undefined') {
    assistObserver = new globalThis.ResizeObserver(syncScrollAssist);
    assistObserver.observe(pageRoot.value);
  }
  syncScrollAssist();
});
</script>

<template>
  <section ref="pageRoot" class="learning-page" :style="isWatermarkEnabled ? watermarkStyle : undefined">
    <div v-if="loading" class="state-panel">正在加载课程学习内容…</div>
    <div v-else-if="errorMessage" class="state-panel error-panel">{{ errorMessage }}</div>
    <template v-else-if="tasks && activeItem">
      <ElAlert
        v-if="mutexBlockedMessage"
        class="learning-mutex-alert"
        type="warning"
        show-icon
        :closable="false"
        :title="mutexBlockedMessage"
      />
      <ElAlert
        v-else-if="verificationStoppedMessage"
        class="learning-mutex-alert"
        type="error"
        show-icon
        :closable="false"
        :title="verificationStoppedMessage"
      />
      <header class="learning-header">
        <div>
          <button class="back-button" type="button" @click="router.back()">
            <span>‹&nbsp;</span>
            返回课程
          </button>
          <div class="breadcrumb">课程中心 / {{ tasks.courseName }} / {{ activeChapterTitle }}</div>
          <h1>{{ tasks.courseName }}</h1>
        </div>
        <div class="header-progress">
          <span>课程进度</span>
          <strong>{{ tasks.progressPercent }}%</strong>
        </div>
      </header>

      <main class="learning-layout">
        <section class="study-column">
          <ElButton v-if="activeItem.linkedQuizItemId" type="primary" @click="openLinkedQuiz">课件试题</ElButton>
          <div
            class="asset-player"
            :class="{ 'is-long-document': isPdfAsset }"
          >
            <div v-if="assetLoading" class="player-state">正在打开素材…</div>
            <div v-else-if="assetErrorMessage" class="player-state error-panel">{{ assetErrorMessage }}</div>
            <div
              v-else-if="assetDetail && (assetDetail.processStatus === 'UPLOADED' || assetDetail.processStatus === 'PROCESSING')"
              class="player-state"
            >
              <strong>PDF 正在处理中</strong>
              <span>处理完成后将自动加载预览。</span>
            </div>
            <AssetViewer
              v-else-if="assetDetail"
              :key="activeItem.courseItemId"
              class="learning-asset-viewer"
              :asset-type="assetDetail.assetType"
              :play-info="playInfo"
              :rich-content="assetDetail.richContent"
              :external-url="assetDetail.externalUrl"
              :file-name="assetDetail.assetName"
              :initial-position-seconds="viewerInitialPositionSeconds"
              :initial-page-no="viewerInitialPageNo"
              :download-url="controlledDownloadUrl"
              :allow-speed-play="isSpeedPlayAllowed"
              pdf-layout="continuous"
              @timeupdate="onMediaTimeUpdate"
              @play="onMediaPlay"
              @pause="onMediaPause"
              @pageupdate="onPdfPageUpdate"
              @pagecount="onPdfPageCount"
              @ended="onMediaEnded"
              @loadedmetadata="restoreMediaPosition"
            />
            <iframe v-else-if="mediaFormat === 'pdf'" class="document-content" :src="playInfo?.url" title="课程文档预览" />
            <img v-else-if="mediaFormat === 'image'" class="image-content" :src="playInfo?.url" alt="课程素材" />
            <div v-else-if="playInfo" class="player-state">
              <p>当前素材暂不支持内嵌预览。</p>
              <a :href="playInfo.url" target="_blank" rel="noreferrer">打开素材</a>
            </div>
            <div v-else-if="activeItem.itemType === 'LIVE'" class="player-state live-state">
              <strong>{{ activeItem.liveSession?.liveName ?? activeItem.itemTitle }}</strong>
              <span>{{ ({ DRAFT: '草稿', SCHEDULED: '已排期', CANCELLED: '已取消', LIVING: '直播中', ENDED: '已结束' } as Record<string, string>)[activeItem.liveSession?.liveStatus ?? ''] ?? '直播信息待发布' }}</span>
              <span>{{ activeItem.liveSession?.startTime?.replace('T', ' ') || '时间待发布' }} 至 {{ activeItem.liveSession?.endTime?.replace('T', ' ') || '时间待发布' }}</span>
              <ElButton type="primary" :disabled="!activeItem.learnable" @click="enterLive">参加直播</ElButton>
            </div>
            <div v-else-if="isExamItem(activeItem)" class="player-state course-exam-state">
              <span class="course-exam-state__type">{{ getCourseExamTypeText(activeItem) }}</span>
              <strong>{{ activeItem.itemTitle }}</strong>
              <span>{{ getCourseExamStatusText(activeItem) }}</span>
              <div v-if="activeItem.scorePublishStatus === 'EFFECTIVE'" class="course-exam-state__score">
                <b>{{ activeItem.finalScore ?? '-' }}</b>
                <small>分</small>
              </div>
              <button v-if="activeItem.canRetake" class="primary-button" type="button" @click="enterCourseExam(activeItem, true)">补测一次</button>
              <button class="primary-button" type="button" @click="enterCourseExam(activeItem)">
                {{ getCourseExamActionText(activeItem) }}
              </button>
              <button class="secondary-button" type="button" @click="refreshTaskProgress">刷新课程进度</button>
            </div>
            <div v-else class="player-state">当前内容暂无可用素材。</div>
          </div>

          <div class="study-info">
            <div class="study-info__main">
              <span class="eyebrow">正在学习</span>
              <h2>{{ activeItem.itemTitle }}</h2>
              <div class="study-chips">
                <span class="study-chip">{{ activeItem.isRequired === 1 ? '必修内容' : '选修内容' }}</span>
                <span class="study-chip">{{ getProgressText(activeItem) }}</span>
                <span v-if="activeChapterTitle" class="study-chip">{{ activeChapterTitle }}</span>
              </div>
              <p v-if="isStudyDescriptionEnabled" class="study-hours-note">{{ studyDescriptionText }}</p>
            </div>
            <div class="study-info__stat">
              <span class="study-info__stat-label">{{ positionLabel }}</span>
              <label v-if="isPdfAsset && pageCount > 0" class="page-input">
                <input :value="currentPageNo" type="number" readonly tabindex="-1" aria-label="当前页码" />
                <span>/ {{ pageCount }}</span>
              </label>
              <strong v-else class="study-info__stat-value">{{ positionValue }}</strong>
            </div>
          </div>
        </section>

        <aside class="chapter-list">
          <div class="chapter-list__header">
            <strong>课程目录</strong>
            <span>{{ tasks.progressPercent }}% 已完成</span>
          </div>
          <div v-for="chapter in orderCourseChapters(tasks.chapters)" :key="chapter.chapterId" class="chapter-group" :style="{ marginLeft: chapter.depth * 16 + 'px' }">
            <h3>{{ chapter.sortOrder }}. {{ chapter.chapterName }}</h3>
            <button
              v-for="item in chapter.items"
              :key="item.courseItemId"
              class="chapter-item"
              :class="{ active: activeItem.courseItemId === item.courseItemId }"
              type="button"
              :disabled="!item.learnable"
              @click="selectItem(item)"
            >
              <span class="chapter-item__status">{{ item.progressStatus === 'COMPLETED' ? '✓' : '○' }}</span>
              <span class="chapter-item__body">
                <strong>{{ item.itemTitle }}</strong>
                <small>{{ item.isRequired === 1 ? '必修' : '选修' }} · {{ getProgressText(item) }}</small>
              </span>
              <span v-if="item.lastPositionSeconds" class="chapter-item__time">{{ item.lastPositionSeconds }}s</span>
            </button>
          </div>
        </aside>
      </main>

      <section class="course-extra">
        <nav class="course-extra__tabs" aria-label="课程信息">
          <button
            class="course-extra__tab"
            :class="{ active: activeInfoTab === 'INTRODUCTION' }"
            type="button"
            @click="activeInfoTab = 'INTRODUCTION'"
          >
            课程简介
          </button>
          <button class="course-extra__tab" :class="{ active: activeInfoTab === 'DOWNLOADS' }" type="button" @click="activeInfoTab = 'DOWNLOADS'">
            课程附件
          </button>
          <button class="course-extra__tab" :class="{ active: activeInfoTab === 'EVALUATIONS' }" type="button" @click="showEvaluations">
            课程评价
          </button>
          <button class="course-extra__tab" :class="{ active: activeInfoTab === 'REPORT' }" type="button" @click="activeInfoTab = 'REPORT'">课程报告</button>
        </nav>
        <div v-if="activeInfoTab === 'INTRODUCTION'" class="course-introduction">
          <h2>{{ enrollmentDetail?.course?.courseName ?? tasks.courseName }}</h2>
          <p>{{ enrollmentDetail?.course?.introduction || '暂无课程简介。' }}</p>
          <dl>
            <div>
              <dt>主讲教师</dt><dd>{{ enrollmentDetail?.course?.teacherName || '未设置' }}</dd>
            </div>
            <div>
              <dt>教学目标</dt>
              <dd class="syllabus-text">{{ enrollmentDetail?.course?.teachingObjective || '暂无' }}</dd>
            </div>
            <div>
              <dt>考核方式</dt>
              <dd class="syllabus-text">{{ enrollmentDetail?.course?.assessmentMethod || '按课程规则判定' }}</dd>
            </div>
            <div>
              <dt>重点内容／教学大纲</dt>
              <dd class="syllabus-text">{{ enrollmentDetail?.course?.syllabus || '请参见右侧课程目录。' }}</dd>
            </div>
          </dl>
        </div>
        <div v-else-if="activeInfoTab === 'DOWNLOADS'" class="course-downloads">
          <div v-if="downloadAssets.length === 0" class="empty-extra">暂无课程附件。</div>
          <button
            v-for="asset in downloadAssets"
            :key="asset.assetId"
            class="download-card"
            type="button"
            :disabled="!asset.downloadable || downloadingAssetId === asset.assetId"
            @click="downloadCourseAttachment($event, asset)"
          >
            <span class="download-card__title">{{ asset.assetName || `课程附件 #${asset.assetId}` }}</span>
            <span class="download-card__meta">素材类型：{{ asset.assetType || '未知类型' }}</span>
            <span class="download-card__meta">文件名称：{{ asset.fileName || '未知文件' }}</span>
            <span class="download-card__meta">MIME 类型：{{ asset.mimeType || '未知' }}</span>
            <span class="download-card__meta">文件大小：{{ formatAttachmentSize(asset.fileSize) }}</span>
            <span class="download-card__action">
              {{
                downloadingAssetId === asset.assetId
                  ? '正在下载…'
                  : asset.downloadable
                    ? '下载附件'
                    : getDownloadUnavailableReasonText(asset.unavailableReason)
              }}
            </span>
          </button>
        </div>
        <div v-else-if="activeInfoTab === 'REPORT'" class="course-introduction">
          <h2>课程报告</h2>
          <p>学习进度：{{ tasks.progressPercent }}% · 有效学习时长：{{ Math.floor((enrollmentDetail?.totalStudySeconds ?? 0) / 60) }} 分钟 · 认可学时 {{ ((enrollmentDetail?.recognizedStudySeconds ?? enrollmentDetail?.totalStudySeconds ?? 0) / 3600).toFixed(2) }} 小时</p>
          <p v-if="!enrollmentDetail?.assessmentResults?.length">暂无生效考核结果。</p>
          <div v-for="result in enrollmentDetail?.assessmentResults" :key="result.courseItemId" class="download-card">
            <strong>{{ tasks.chapters.flatMap(c => c.items).find(i => i.courseItemId === result.courseItemId)?.itemTitle }}</strong>
            <span v-if="result.sourceType === 'COURSE_BANK_PRACTICE'">正确 {{ result.correctCount ?? 0 }} / 错误 {{ result.wrongCount ?? 0 }} · 正确率 {{ result.accuracyPercent == null ? '暂无' : result.accuracyPercent + '%' }}</span>
            <span v-else>成绩 {{ result.finalScore ?? '暂无' }} · {{ result.isPassed === 1 ? '合格' : '未合格' }}</span>
            <ElButton link type="primary" @click="router.push({ name: 'PortalCourseAssessmentResult', params: { enrollmentId, itemId: result.courseItemId, attemptId: result.examAttemptId } })">查看答题与错题详情</ElButton>
          </div>
        </div>
        <div v-else class="course-evaluations">
          <section v-if="enrollmentDetail?.learningStatus === 'COMPLETED'" class="evaluation-section">
            <h2>我的评价</h2>
            <div v-if="enrollmentDetail.evaluation" class="evaluation-entry">
              <ElRate :model-value="enrollmentDetail.evaluation.rating" disabled allow-half />
              <p>{{ enrollmentDetail.evaluation.content || '未填写评价内容' }}</p>
              <small>{{ enrollmentDetail.evaluation.isAnonymous === 1 ? '匿名展示' : '实名展示' }} · {{ enrollmentDetail.evaluation.submittedTime.replace('T', ' ').slice(0, 16) }}</small>
            </div>
            <p v-else-if="evaluationSubmitted">评价已提交，请刷新页面查看。</p>
            <ElButton v-else type="primary" @click="evaluationDrawerVisible = true">添加课程评价</ElButton>
          </section>
          <section class="evaluation-section">
            <h2>同次开课学员评价（{{ peerTotal }}）</h2>
            <p v-if="peerError" class="evaluation-error">{{ peerError }} <ElButton link type="primary" @click="loadPeerEvaluations">重试</ElButton></p>
            <p v-else-if="!peerLoading && peerTotal === 0" class="evaluation-empty">暂无学员评价</p>
            <div v-loading="peerLoading">
              <article v-for="(item, index) in peerEvaluations" :key="`${peerPageNum}-${index}`" class="evaluation-entry">
                <div class="evaluation-entry-heading"><strong>{{ item.displayName }}</strong><small>{{ item.submittedTime.replace('T', ' ').slice(0, 16) }}</small></div>
                <ElRate :model-value="item.rating" disabled allow-half />
                <p>{{ item.content || '未填写评价内容' }}</p>
              </article>
            </div>
            <ElPagination v-if="peerTotal > DEFAULT_LIST_PAGE_SIZE" v-model:current-page="peerPageNum" :page-size="DEFAULT_LIST_PAGE_SIZE" :total="peerTotal" layout="prev, pager, next" @current-change="loadPeerEvaluations" />
          </section>
        </div>
      </section>
    </template>

    <!-- 长页面阅读辅助：长按持续滚动，也可一键回到最顶端或直达最低端。 -->
    <div v-if="canScroll" class="scroll-assist" role="group" aria-label="页面滚动辅助">
      <button
        class="scroll-assist__button"
        type="button"
        title="一键回到最顶端"
        aria-label="一键回到最顶端"
        @click="scrollToEdge('top')"
      >
        <ElIcon><Top /></ElIcon>
      </button>
      <button
        class="scroll-assist__button"
        :class="{ 'scroll-assist__button--holding': holding === -1 }"
        type="button"
        title="长按持续向上"
        aria-label="长按持续向上滚动"
        @pointerdown="startScroll(-1)"
        @pointerup="stopScroll"
        @pointerleave="stopScroll"
        @pointercancel="stopScroll"
        @contextmenu.prevent
      >
        <ElIcon><ArrowUp /></ElIcon>
      </button>
      <button
        class="scroll-assist__button"
        :class="{ 'scroll-assist__button--holding': holding === 1 }"
        type="button"
        title="长按持续向下"
        aria-label="长按持续向下滚动"
        @pointerdown="startScroll(1)"
        @pointerup="stopScroll"
        @pointerleave="stopScroll"
        @pointercancel="stopScroll"
        @contextmenu.prevent
      >
        <ElIcon><ArrowDown /></ElIcon>
      </button>
      <button
        class="scroll-assist__button"
        type="button"
        title="一键到最低端"
        aria-label="一键到最低端"
        @click="scrollToEdge('bottom')"
      >
        <ElIcon><Bottom /></ElIcon>
      </button>
    </div>
    <ElDrawer v-model="evaluationDrawerVisible" title="添加课程评价" size="480px" :lock-scroll="false">
      <ElForm label-position="top">
        <ElFormItem label="评分"><ElRate v-model="evaluationForm.rating" :max="5" allow-half /></ElFormItem>
        <ElFormItem label="评价内容"><ElInput v-model="evaluationForm.content" type="textarea" maxlength="2000" show-word-limit placeholder="写下你的课程评价（选填）" /></ElFormItem>
        <ElFormItem><ElCheckbox v-model="evaluationForm.isAnonymous" :true-value="1" :false-value="0">匿名展示</ElCheckbox></ElFormItem>
        <ElButton type="primary" :loading="submittingEvaluation" @click="saveEvaluation">提交评价</ElButton>
      </ElForm>
    </ElDrawer>

    <!-- 防挂机验证题：随机间隔弹出，作答期间不计有效学习时长，超时结束会话。 -->
    <ElDialog
      v-model="verificationVisible"
      class="learning-verification-dialog"
      title="学习验证"
      width="420px"
      align-center
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <p class="verification-tip">为防止挂机，请在 {{ verificationRemainingSeconds }} 秒内回答下面的问题：</p>
      <p class="verification-question">{{ verificationPrompt }}</p>
      <ElInput v-model="verificationAnswer" placeholder="请输入答案" @keyup.enter="submitVerification" />
      <p v-if="verificationError" class="verification-error">{{ verificationError }}</p>
      <template #footer>
        <ElButton type="primary" @click="submitVerification">提交答案</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.learning-mutex-alert {
  margin: 0 auto 16px;
  max-width: 1280px;
}
.verification-tip {
  margin: 0 0 12px;
  color: #475467;
  font-size: 14px;
}
.verification-question {
  margin: 0 0 16px;
  color: #1d2939;
  font-size: 24px;
  font-weight: 600;
  letter-spacing: 1px;
}
.verification-error {
  margin: 10px 0 0;
  color: #d92d20;
  font-size: 13px;
}
.learning-header,
.learning-layout,
.study-info {
  display: flex;
  margin: 0 auto;
  max-width: 1280px;
}
.learning-header {
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 22px;
}
.back-button {
  padding: 7px 12px;
  border: 0;
  border-radius: 8px;
  background: #eef3ff;
  color: #4e6fae;
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
}
.breadcrumb,
.eyebrow {
  margin-top: 12px;
  color: #98a2b3;
  font-size: 12px;
}
.learning-header h1,
.study-info h2 {
  margin: 8px 0 0;
  color: #1d2939;
}
.learning-header h1 {
  color: #263653;
  font-size: 26px;
}
.header-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #667085;
}
.header-progress strong {
  color: #5575b1;
  font-size: 20px;
}
.learning-layout {
  align-items: stretch;
  gap: 16px;
}
.study-column {
  flex: 1;
  min-width: 0;
}
.asset-player {
  position: relative;
  display: flex;
  height: 600px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid #e4eaf4;
  border-radius: 18px;
  background: #202a3d;
  box-shadow: 0 12px 30px rgb(44 62 96 / 10%);
  .learning-asset-viewer {
    width: 100%;
    height: 100%;
    min-height: 0;
    padding: 0;
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.asset-player.is-long-document {
  height: auto;
  min-height: 600px;
  align-items: stretch;
  overflow: visible;

  .learning-asset-viewer {
    height: auto;
    min-height: 600px;
    overflow: visible;
    align-items: stretch;
  }
}

.asset-player.is-long-document :deep(.asset-viewer) {
  height: auto;
  min-height: 600px;
  overflow: visible;
}
.learning-asset-viewer :deep(.media-audio) {
  width: min(720px, 86%);
}
.learning-asset-viewer :deep(.ofv-image-viewer) {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
}
.learning-asset-viewer :deep(.open-file-viewer) {
  height: 100%;
  min-height: 0;
}
.learning-asset-viewer :deep(.ofv-image-viewer img) {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
.media-content {
  display: block;
  width: 100%;
  max-height: 620px;
}
.audio-content {
  width: 82%;
}
.document-content {
  width: 100%;
  height: 620px;
  border: 0;
  background: #fff;
}
.image-content {
  display: block;
  max-width: 100%;
  max-height: 620px;
  object-fit: contain;
}
.player-state {
  display: flex;
  padding: 32px;
  align-items: center;
  flex-direction: column;
  gap: 14px;
  color: #d0d5dd;
  text-align: center;
}
.player-state a {
  color: #69b1ff;
}
.course-exam-state {
  max-width: 460px;
}
.course-exam-state__type {
  color: #9db7e8;
  font-size: 13px;
  font-weight: 700;
}
.course-exam-state__score {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: #fff;
}
.course-exam-state__score b {
  font-size: 48px;
}
.course-exam-state__score small {
  color: #d0d5dd;
}
.error-panel {
  color: #fda29b;
}
/*
 * 学习水印挂在 learning-page 根容器的背景层：图案只落在页面左右两侧留白、
 * 素材区下方的空白页以及各卡片之间的缝隙，素材画面内（video / pdf / 图片）一律没有水印，
 * 既不遮挡素材预览学习，又能整页明显看到水印。
 * 图案与尺寸由学习页通过 `--learning-watermark-*` 变量下发，未开启水印时为 none。
 */
.learning-page {
  background-image: var(--learning-watermark-image, none);
  background-repeat: repeat;
  background-size: var(--learning-watermark-size, auto);
}
/* 学习信息卡：左侧承载标题与属性标签，右侧固定展示学习位置，替代原先两端散落的说明文字。 */
.study-info {
  margin-top: 14px;
  padding: 18px 22px;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  border: 1px solid #e7ebf3;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(44 62 96 / 5%);
}
.study-info__main {
  display: grid;
  min-width: 0;
  gap: 10px;
}
.study-info h2 {
  margin: 0;
  font-size: 20px;
}
.study-info__main .eyebrow {
  margin-top: 0;
}
.study-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.study-chip {
  padding: 2px 10px;
  border: 1px solid #e4eaf4;
  border-radius: 999px;
  background: #f6f9ff;
  color: #5575b1;
  font-size: 12px;
  line-height: 20px;
}
.study-hours-note {
  max-width: 720px;
  color: #8a94a6;
  line-height: 1.7;
}
.study-info__stat {
  display: grid;
  min-width: 132px;
  flex: 0 0 auto;
  justify-items: end;
  gap: 6px;
  padding-left: 24px;
  border-left: 1px solid #edf0f6;
}
.study-info__stat-label {
  color: #98a2b3;
  font-size: 12px;
}
.study-info__stat-value {
  color: #263653;
  font-size: 15px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.page-input {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #263653;
  font-size: 15px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.page-input input {
  width: 46px;
  padding: 4px 6px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  background: #f8fafc;
  color: #263653;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  text-align: center;
  appearance: textfield;
  pointer-events: none;
  cursor: default;
}

.page-input input::-webkit-inner-spin-button,
.page-input input::-webkit-outer-spin-button {
  margin: 0;
  appearance: none;
}
.chapter-list {
  position: sticky;
  top: 16px;
  align-self: flex-start;
  width: 350px;
  // height: calc(100vh - 32px);
  max-height: calc(100vh - 32px);
  box-sizing: border-box;
  overflow-y: auto;
  padding: 20px;
  border: 1px solid #e4eaf4;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 10px 26px rgb(44 62 96 / 6%);
}
.chapter-list__header {
  display: flex;
  padding-bottom: 16px;
  justify-content: space-between;
  border-bottom: 1px solid #edf0f6;
}
.chapter-list__header span {
  color: #98a2b3;
  font-size: 12px;
}
.chapter-group {
  padding: 14px 0 0;
}
.chapter-group h3 {
  margin: 0 0 10px;
  color: #526078;
  font-size: 14px;
}
.chapter-item {
  display: flex;
  width: 100%;
  min-height: 54px;
  padding: 10px 9px;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #344054;
  cursor: pointer;
  text-align: left;
}
.chapter-item:hover,
.chapter-item.active {
  background: #f0f4ff;
}
.chapter-item:disabled {
  color: #98a2b3;
  cursor: not-allowed;
}
.chapter-item__status {
  width: 18px;
  color: #98a2b3;
  text-align: center;
}
.chapter-item.active .chapter-item__status {
  color: #5575b1;
}
.chapter-item__body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4px;
}
.chapter-item__body strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chapter-item__body small,
.chapter-item__time {
  color: #98a2b3;
  font-size: 11px;
}
.course-extra {
  max-width: 1280px;
  margin: 18px auto 40px;
  padding: 0 22px 24px;
  border: 1px solid #e7ebf3;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(44 62 96 / 5%);
}
.course-extra__tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid #eaecf0;
}
.course-extra__tab {
  padding: 16px 2px 12px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #667085;
  cursor: pointer;
}
.course-extra__tab.active {
  border-bottom-color: #5575b1;
  color: #5575b1;
}
.course-introduction,
.course-downloads,
.course-evaluations {
  padding-top: 18px;
  color: #475467;
  line-height: 1.8;
}
.evaluation-section + .evaluation-section { margin-top: 24px; border-top: 1px solid #eaecf0; }
.evaluation-section h2 { margin: 0 0 12px; color: #1d2939; font-size: 18px; }
.evaluation-entry { padding: 14px 0; border-bottom: 1px solid #f0f3f9; }
.evaluation-entry p { margin: 6px 0; overflow-wrap: anywhere; white-space: pre-wrap; }
.evaluation-entry small, .evaluation-empty { color: #98a2b3; }
.evaluation-entry-heading { display: flex; justify-content: space-between; gap: 16px; }
.evaluation-error { color: var(--el-color-danger); }
.course-introduction h2 {
  margin: 0 0 8px;
  color: #1d2939;
  font-size: 18px;
}
.course-introduction p {
  margin: 0 0 12px;
}
/* 课程简介的字段区改为带分隔线的键值行，长文本左对齐更易扫读。 */
.course-introduction dl {
  display: grid;
  gap: 0;
  margin: 4px 0 0;
  border-top: 1px solid #f0f3f9;
}
.course-introduction dl div {
  display: grid;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f3f9;
  grid-template-columns: 76px minmax(0, 1fr);
}
.course-introduction dt {
  color: #98a2b3;
  font-size: 13px;
}
.course-introduction dd {
  margin: 0;
}
.syllabus-text {
  white-space: pre-line;
}
.download-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  margin-bottom: 12px;
  padding: 16px;
  border: 1px solid #e4e7ec;
  border-radius: 12px;
  background: #fbfcff;
  color: #344054;
  cursor: pointer;
  text-decoration: none;
  text-align: left;
}
.download-card:not(:disabled):hover {
  border-color: #9eb4e1;
  box-shadow: 0 4px 14px rgb(16 24 40 / 8%);
}
.download-card:disabled {
  cursor: wait;
  opacity: 0.6;
}
.download-card__title {
  color: #1d2939;
  font-size: 16px;
  font-weight: 600;
}
.download-card__meta {
  margin-top: 5px;
  color: #667085;
  font-size: 13px;
}
.download-card__action {
  margin-top: 10px;
  color: #5575b1;
  font-size: 13px;
  font-weight: 500;
}
.download-card:disabled .download-card__action,
.empty-extra {
  color: #98a2b3;
}
.state-panel {
  max-width: 1280px;
  margin: 40px auto;
  padding: 60px;
  border-radius: 8px;
  background: #fff;
  color: #667085;
  text-align: center;
}
.primary-button {
  padding: 9px 18px;
  border: 0;
  border-radius: 5px;
  background: #5575b1;
  color: #fff;
  cursor: pointer;
}
.secondary-button {
  padding: 8px 16px;
  border: 1px solid #8fa7d8;
  border-radius: 5px;
  background: transparent;
  color: #dbe7ff;
  cursor: pointer;
}
/* 长页面阅读辅助：固定右下角，长按上下两键持续滚动，首尾两键一键到顶或到底。 */
.scroll-assist {
  position: fixed;
  z-index: 20;
  right: 24px;
  bottom: 26px;
  display: grid;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-surface);
  box-shadow: 0 10px 28px rgb(15 23 42 / 16%);
}
.scroll-assist__button {
  display: grid;
  width: 42px;
  height: 40px;
  place-items: center;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 15px;
  transition: background 0.15s ease, color 0.15s ease;
  touch-action: none;
}
.scroll-assist__button:last-child {
  border-bottom: 0;
}
.scroll-assist__button:hover,
.scroll-assist__button--holding {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}
:deep(.ofv-pdf) {
  min-height: 92%;
}
@media (max-width: 900px) {
  .learning-layout {
    flex-direction: column;
  }
  .chapter-list {
    width: auto;
  }
  .asset-player {
    height: 420px;
  }

  .asset-player.is-long-document {
    height: auto;
  }
  .chapter-list {
    position: static;
    height: 420px;
  }
  .study-info {
    flex-direction: column;
    gap: 16px;
  }
  .study-info__stat {
    width: 100%;
    justify-items: start;
    padding-top: 12px;
    padding-left: 0;
    border-top: 1px solid #edf0f6;
    border-left: 0;
  }
  .scroll-assist {
    right: 14px;
    bottom: 14px;
  }
  .scroll-assist__button {
    width: 38px;
    height: 36px;
  }
}
</style>
