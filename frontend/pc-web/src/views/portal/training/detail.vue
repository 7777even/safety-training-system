<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getAssessmentExecutionErrorMessage } from '@shared/libs/assessment-execution';
import { ArrowRight, CircleCheck, Clock, CollectionTag, Reading, Tickets, View } from '@element-plus/icons-vue';
import AssetViewer from '@/components/asset-viewer/AssetViewer.vue';
import {
  enterTrainingContent,
  getTrainingLearnerMaterialFile,
  getTrainingLearnerMaterials,
  getTrainingLearnerParticipantDetail,
  type TrainingLearnerContent,
  type TrainingLearnerMaterial,
  type TrainingLearnerParticipantDetail
} from '@/api/training-learner';
import { getLearnerContentDisplayStatus } from './learner-training-status';
const route = useRoute();
const router = useRouter();
const detail = ref<TrainingLearnerParticipantDetail>();
const loading = ref(false);
const errorMessage = ref('');
/** 学员可读的一般资料；空数组时整个区块不渲染。 */
const materials = ref<TrainingLearnerMaterial[]>([]);
const materialPreviewVisible = ref(false);
const materialPreviewLoading = ref(false);
const materialPreviewTitle = ref('培训资料预览');
const materialPreviewFile = ref<globalThis.File>();
/** 预览请求序号：关闭或切换时作废未完成的请求，避免旧文件覆盖新选择。 */
let materialRequestId = 0;
const participantId = computed(() => Number(route.params.trainingParticipantId));
const planEnded = computed(() => detail.value?.plan?.publishStatus === 'ENDED');
/** 培训计划概览数据，仅根据已返回的内容结果计算，不推断后端未提供的结论。 */
const completedCount = computed(() => detail.value?.contents.filter((content) => isCompleted(content)).length ?? 0);
const contentCount = computed(() => detail.value?.contents.length ?? 0);
const progressText = computed(() => `${completedCount.value}/${contentCount.value} 项已完成`);
/** 将培训内容类型转换为学员可理解的中文名称。 */
function contentTypeText(type: TrainingLearnerContent['contentType']): string {
  return { COURSE: '课程学习', EXAM: '理论考试', QUESTION_BANK: '题库练习', OFFLINE: '线下场次' }[type];
}
/** 为不同内容类型提供单色识别图标，保持门户卡片的视觉层级一致。 */
function contentTypeIcon(type: TrainingLearnerContent['contentType']) {
  return { COURSE: Reading, EXAM: Tickets, QUESTION_BANK: CollectionTag, OFFLINE: Clock }[type];
}
/** 将接口状态映射为用户可读的中文状态。 */
function statusText(status?: string): string {
  return (
    {
      PENDING: '待开始',
      NOT_STARTED: '待开始',
      SCHEDULED: '已安排',
      IN_PROGRESS: '进行中',
      RUNNING: '进行中',
      LEARNING: '学习中',
      COMPLETED: '已完成',
      PASSED: '已通过',
      FAILED: '未通过',
      WAITING_RESULT: '等待结果',
      PENDING_REVIEW: '待阅卷',
      PENDING_PUBLISH: '待发布成绩',
      EXPIRED: '已到期',
      SKIPPED: '已跳过'
    }[status ?? ''] ??
    status ??
    '待开始'
  );
}
/** 根据状态选择 Element Plus 标签语义色，不使用颜色表达额外业务含义。 */
function statusTagType(status?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (['COMPLETED', 'PASSED'].includes(status ?? '')) return 'success';
  if (['FAILED', 'EXPIRED'].includes(status ?? '')) return 'danger';
  if (['IN_PROGRESS', 'LEARNING', 'WAITING_RESULT', 'PENDING_REVIEW', 'PENDING_PUBLISH'].includes(status ?? '')) return 'warning';
  return 'info';
}
/** 优先展示结果状态，未产生结果时回退到内容当前状态。 */
function contentStatus(content: TrainingLearnerContent): string {
  return resultFor(content.planContentId)?.resultStatus ?? content.contentStatus ?? '';
}
/** 线下卡片优先展示个人完成结果；未形成结果时展示教师推进的场次进度。 */
function displayStatus(content: TrainingLearnerContent): string {
  const result = resultFor(content.planContentId);
  return getLearnerContentDisplayStatus(
    content.contentType,
    result?.resultStatus,
    content.contentStatus,
    result?.sessionStatus
  );
}
/** 已完成统计只采信明确的完成或通过状态。 */
function isCompleted(content: TrainingLearnerContent): boolean {
  return ['COMPLETED', 'PASSED'].includes(contentStatus(content) ?? '');
}
/** 已交卷的考试或题库练习均只读进入结果页，避免再次打开作答界面。 */
function isExamResultAvailable(content: TrainingLearnerContent): boolean {
  return ['EXAM', 'QUESTION_BANK'].includes(content.contentType)
    && ['PENDING_REVIEW', 'PENDING_PUBLISH', 'COMPLETED', 'PASSED', 'FAILED'].includes(contentStatus(content));
}
/** 仅待开始的培训理论考试先阅读须知；题库练习和已开始考试保持统一进入方式。 */
function shouldReadExamNotice(content: TrainingLearnerContent): boolean {
  return content.contentType === 'EXAM' && ['PENDING', 'NOT_STARTED'].includes(contentStatus(content));
}
async function load(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    detail.value = await getTrainingLearnerParticipantDetail(participantId.value);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '培训详情加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
  await loadMaterials();
}

/**
 * 资料区块是附加事实，读取失败只影响该区块，不把整页打成错误态。
 * <p>没有可读资料时保持空数组，模板整块不渲染，不出现空表格或空区块。
 */
async function loadMaterials(): Promise<void> {
  if (errorMessage.value) {
    materials.value = [];
    return;
  }
  try {
    materials.value = await getTrainingLearnerMaterials(participantId.value);
  } catch {
    materials.value = [];
  }
}

/**
 * 学员侧只做在线预览：经受控端点按资料记录取字节，再以 blob 渲染，页面不持有可直连地址。
 * <p>学员侧不提供下载入口，因此这里没有另存为分支。
 */
async function openMaterialPreview(material: TrainingLearnerMaterial): Promise<void> {
  const requestId = ++materialRequestId;
  materialPreviewFile.value = undefined;
  materialPreviewTitle.value = material.attachmentName || '培训资料预览';
  materialPreviewVisible.value = true;
  materialPreviewLoading.value = true;
  try {
    const blob = await getTrainingLearnerMaterialFile(participantId.value, material.trainingAttachmentId);
    if (materialPreviewVisible.value && requestId === materialRequestId) {
      materialPreviewFile.value = new globalThis.File(
        [blob], material.attachmentName || '培训资料', { type: blob.type });
    }
  } catch (error) {
    if (requestId === materialRequestId) {
      materialPreviewVisible.value = false;
      ElMessage.error(error instanceof Error ? error.message : '培训资料预览失败，请稍后重试。');
    }
  } finally {
    if (requestId === materialRequestId) materialPreviewLoading.value = false;
  }
}

/** 关闭时作废未完成的请求，避免旧文件覆盖下一次预览。 */
function closeMaterialPreview(): void {
  materialRequestId += 1;
  materialPreviewFile.value = undefined;
  materialPreviewLoading.value = false;
}
function createAttemptToken(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
}
function resultFor(id: number) {
  return detail.value?.results.find((item) => item.planContentId === id);
}
/** 将后端秒数换算为便于学员理解的获得学时，零值同样需要展示。 */
function earnedHoursText(seconds?: number | null): string {
  if (seconds == null) return '-';
  return `${Math.round((seconds / 3600) * 100) / 100} 学时`;
}
/** 理论考试和题库练习不计培训学时，内容项不展示安排与获得学时。 */
function shouldDisplayTrainingHours(content: TrainingLearnerContent): boolean {
  return !['EXAM', 'QUESTION_BANK'].includes(content.contentType)
    && resultFor(content.planContentId)?.effectiveDurationSeconds != null;
}
/** 题库练习不计分，即使历史结果带有零分也不在培训卡片展示。 */
function shouldDisplayScore(content: TrainingLearnerContent): boolean {
  return content.contentType !== 'QUESTION_BANK' && resultFor(content.planContentId)?.score != null;
}
/** 已完成内容进入结果解析，待阅卷内容只允许回看学员提交的答卷。 */
function contentActionText(content: TrainingLearnerContent): string {
  if (!isExamResultAvailable(content)) return '进入内容';
  if (['PENDING_REVIEW', 'PENDING_PUBLISH'].includes(contentStatus(content))) return '查看答卷';
  return content.contentType === 'QUESTION_BANK' ? '查看反馈' : '查看结果';
}
/** 终态内容直接读取投影绑定的最终答卷，禁止再次进入而产生或命中空白尝试。 */
function resultAttemptId(content: TrainingLearnerContent): number | null {
  return isExamResultAvailable(content) ? resultFor(content.planContentId)?.finalAttemptId ?? null : null;
}
/** 已结束培训只用详情投影中的既有事实进入只读结果，不调用会被 8154 拒绝的统一进入接口。 */
async function viewResult(content: TrainingLearnerContent): Promise<void> {
  const result = resultFor(content.planContentId);
  if (content.contentType === 'COURSE' && result?.sourceRecordType === 'COURSE_ENROLLMENT' && result.sourceRecordId) {
    await router.push(`/portal/class/learning/${result.sourceRecordId}`);
    return;
  }
  if (content.contentType === 'OFFLINE' && result?.executionId) {
    await router.push(`/portal/training/offline-session/${result.executionId}`);
    return;
  }
  if (['EXAM', 'QUESTION_BANK'].includes(content.contentType) && result?.finalAttemptId) {
    await router.push(`/portal/training/attempt/${result.finalAttemptId}/result`);
    return;
  }
  ElMessage.info('当前内容暂无可查看的明细结果。');
}
async function enter(content: TrainingLearnerContent): Promise<void> {
  if (planEnded.value) {
    await viewResult(content);
    return;
  }
  try {
    if (shouldReadExamNotice(content)) {
      await router.push({
        name: 'PortalTrainingExamNotice',
        params: { trainingParticipantId: participantId.value, planContentId: content.planContentId }
      });
      return;
    }
    const finalAttemptId = resultAttemptId(content);
    if (finalAttemptId) {
      await router.push(`/portal/training/attempt/${finalAttemptId}/result`);
      return;
    }
    const data = await enterTrainingContent(
      participantId.value,
      content.planContentId,
      ['EXAM', 'QUESTION_BANK'].includes(content.contentType)
        ? { clientAttemptToken: createAttemptToken(), platform: 'PC' }
        : undefined
    );
    if (data.contentType === 'COURSE' && data.courseEnrollmentId) await router.push(`/portal/class/learning/${data.courseEnrollmentId}`);
    else if ((data.contentType === 'EXAM' || data.contentType === 'QUESTION_BANK') && data.examAttemptId)
      await router.push(isExamResultAvailable(content)
        ? `/portal/training/attempt/${data.examAttemptId}/result`
        : `/portal/training/attempt/${data.examAttemptId}`);
    else if (data.contentType === 'OFFLINE' && data.offlineSessionId) await router.push(`/portal/training/offline-session/${data.offlineSessionId}`);
    else ElMessage.error('后端未返回可进入的内容信息。');
  } catch (error) {
    ElMessage.error(getAssessmentExecutionErrorMessage(error, '进入培训内容失败。'));
  }
}
onMounted(load);
onActivated(load);
</script>
<template>
  <section v-loading="loading" class="learner-training-detail">
    <header class="portal-list-page__heading" style="position: relative">
      <div>
        <p class="portal-list-page__eyebrow">TRAINING PLAN</p>
        <h1>{{ detail?.plan?.planName ?? '培训详情' }}</h1>
        <p>{{ planEnded ? '培训计划已结束，仅可查看分项结果。' : '按顺序完成本次培训计划中的学习与考核任务。' }}</p>
      </div>
      <ElButton class="back-button" link @click="router.push('/portal/training')">返回我的培训</ElButton>

      <div v-if="detail" class="page-heading__summary" aria-label="培训计划概览">
        <div class="summary-item">
          <strong>{{ contentCount }}</strong>
          <span>培训内容</span>
        </div>
        <span class="summary-divider" aria-hidden="true" />
        <div class="summary-item">
          <strong>{{ completedCount }}</strong>
          <span>已完成</span>
        </div>
        <div class="summary-item">
          <strong>{{ detail.participant?.onlineHours ?? 0 }}</strong>
          <span>获得线上学时</span>
        </div>
        <div class="summary-item">
          <strong>{{ detail.participant?.offlineHours ?? 0 }}</strong>
          <span>获得线下学时</span>
        </div>
        <ElTag :type="planEnded ? 'info' : 'success'" effect="dark">{{ planEnded ? '已结束' : '进行中' }}</ElTag>
      </div>
    </header>
    <section v-if="errorMessage" class="state-panel">
      <p>{{ errorMessage }}</p>
      <ElButton type="primary" @click="load">重新加载</ElButton>
    </section>
    <section v-else-if="detail" class="content-section">
      <div class="content-section__heading">
        <div>
          <p class="section-eyebrow">LEARNING CONTENT</p>
          <h2>培训内容</h2>
        </div>
        <span>{{ progressText }}</span>
      </div>
      <div v-if="detail.contents.length" class="content-list">
        <ElCard v-for="(content, index) in detail.contents" :key="content.planContentId" class="content-card" shadow="never">
          <div class="content-card__topline">
            <span class="content-card__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <ElTag size="small" effect="plain">{{ contentTypeText(content.contentType) }}</ElTag>
            <ElTag size="small" :type="statusTagType(displayStatus(content))">
              <ElIcon v-if="isCompleted(content)"><CircleCheck /></ElIcon>
              {{ statusText(displayStatus(content)) }}
            </ElTag>
          </div>
          <div class="content-card__body">
            <div class="content-card__headline">
              <div class="content-card__icon">
                <ElIcon><component :is="contentTypeIcon(content.contentType)" /></ElIcon>
              </div>
              <div class="content-card__info">
                <h3 :title="content.contentName">{{ content.contentName }}</h3>
                <div class="content-card__base-info">
                  <span>{{ content.isRequired === 1 ? '必修内容' : '选修内容' }}</span>
                  <span v-if="shouldDisplayTrainingHours(content) && content.requiredHours != null">安排 {{ content.requiredHours }} 学时</span>
                </div>
              </div>
            </div>
            <div
              v-if="shouldDisplayTrainingHours(content) || shouldDisplayScore(content)"
              class="content-card__results"
              aria-label="内容学习结果"
            >
              <div v-if="shouldDisplayTrainingHours(content)" class="content-card__result-item">
                <span>获得学时</span>
                <strong>{{ earnedHoursText(resultFor(content.planContentId)?.effectiveDurationSeconds) }}</strong>
              </div>
              <div v-if="shouldDisplayScore(content)" class="content-card__result-item">
                <span>成绩</span>
                <strong>{{ resultFor(content.planContentId)?.score }} 分</strong>
              </div>
            </div>
          </div>
          <div class="content-card__actions">
            <ElButton v-if="!planEnded" type="primary" @click="enter(content)">
              {{ contentActionText(content) }}
              <ElIcon><ArrowRight /></ElIcon>
            </ElButton>
            <ElButton v-else plain @click="viewResult(content)">查看结果</ElButton>
          </div>
        </ElCard>
      </div>
      <ElEmpty v-else description="当前培训计划暂无内容" />
    </section>
    <section v-if="!errorMessage && detail && materials.length" class="content-section">
      <div class="content-section__heading">
        <div>
          <p class="section-eyebrow">TRAINING MATERIALS</p>
          <h2>培训资料</h2>
        </div>
        <span>共 {{ materials.length }} 份</span>
      </div>
      <div class="material-list">
        <ElCard
          v-for="material in materials"
          :key="material.trainingAttachmentId"
          class="material-card"
          shadow="never"
        >
          <div class="material-card__info">
            <h3 :title="material.attachmentName">{{ material.attachmentName }}</h3>
            <p class="material-card__meta">
              <span>{{ material.stageName || '未关联阶段' }}</span>
              <span v-if="material.description" :title="material.description">{{ material.description }}</span>
            </p>
          </div>
          <ElButton v-if="material.hasFile" plain :icon="View" @click="openMaterialPreview(material)">在线预览</ElButton>
        </ElCard>
      </div>
    </section>
    <ElDialog
      v-model="materialPreviewVisible"
      :title="materialPreviewTitle"
      width="min(900px, calc(100vw - 32px))"
      append-to-body
      @close="closeMaterialPreview"
    >
      <div v-loading="materialPreviewLoading" class="material-preview">
        <AssetViewer v-if="materialPreviewFile" :local-file="materialPreviewFile" preview-height="min(70vh, 620px)" />
      </div>
    </ElDialog>
  </section>
</template>
<style scoped lang="less">
.learner-training-detail,
.content-section,
.content-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 资料区块：一行一份，窄屏单列；列表自身不承担滚动，避免撑破页面。 */
.material-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 全局 .el-card__body 是纵向 flex，这里必须显式改回横向，否则资料行会竖排居中。 */
.material-card :deep(.el-card__body) {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.material-card__info {
  min-width: 0;
  flex: 1;
}

.material-card__info h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.material-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

/* 预览弹窗在文件到位前保留最小高度，避免加载中高度塌陷造成弹窗跳动。 */
.material-preview {
  min-height: 240px;
}

@media (max-width: 720px) {
  .material-card :deep(.el-card__body) {
    flex-direction: column;
    align-items: flex-start;
  }
}

.back-button {
  position: absolute;
  top: 10px;
  right: 35px;
  padding: 0;
  color: #dceaff;
}
.back-button:hover {
  color: #fff;
}
.page-heading__eyebrow,
.section-eyebrow {
  margin: 0 0 8px;
  color: #b9d5ff;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1.6px;
}

.page-heading__summary {
  display: flex;
  flex: none;
  align-items: center;
  gap: 18px;
  padding: 16px 18px;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 12px;
  background: rgb(255 255 255 / 12%);
}
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 58px;
}
.summary-item strong {
  font-size: 24px;
  line-height: 1;
}
.summary-item span {
  color: #dceaff;
  font-size: 12px;
}
.summary-divider {
  width: 1px;
  height: 32px;
  background: rgb(255 255 255 / 28%);
}
.content-section__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 4px 0;
}
.content-section__heading h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 20px;
}
.content-section__heading > span {
  color: var(--color-text-secondary);
  font-size: 13px;
}
.section-eyebrow {
  margin-bottom: 4px;
  color: var(--color-primary);
}
.content-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  align-items: stretch;
  gap: 16px;
}
.content-card {
  border-color: var(--color-border);
  border-radius: 12px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}
.content-card:hover {
  box-shadow: 0 8px 20px rgb(15 23 42 / 8%);
  transform: translateY(-2px);
}
.content-card :deep(.el-card__body) {
  display: flex;
  min-height: 188px;
  flex-direction: column;
  padding: 18px !important;
}
.content-card__topline,
.content-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.content-card__topline {
  min-height: 24px;
}
.content-card__index {
  margin-right: 2px;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.content-card__topline .el-tag:last-child {
  margin-left: auto;
}
.content-card__topline .el-icon {
  margin-right: 3px;
  vertical-align: -2px;
}
.content-card__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 20px;
}
.content-card__headline {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.content-card__icon {
  display: grid;
  flex: none;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 10px;
  color: var(--color-primary);
  background: var(--color-primary-subtle);
  font-size: 20px;
}
.content-card__info {
  min-width: 0;
  flex: 1;
}
.content-card h3 {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
  line-height: 24px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  /* 移除 white-space: nowrap */
  word-break: break-word; /* 防止长英文/URL 撑破容器 */
}
.content-card__base-info {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.content-card__base-info span {
  color: var(--color-text-secondary);
  font-size: 13px;
}
.content-card__base-info span + span {
  padding-left: 8px;
  border-left: 1px solid var(--color-border);
}
.content-card__results {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
.content-card__result-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.content-card__result-item span {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.content-card__result-item strong {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
}
.content-card__actions {
  justify-content: flex-end;
  margin-top: auto;
}
.content-card__actions .el-icon {
  margin-left: 4px;
}
.state-panel {
  display: grid;
  min-height: 220px;
  place-items: center;
}
@media (max-width: 768px) {
  .page-heading__summary {
    width: 100%;
    box-sizing: border-box;
    justify-content: space-between;
  }
  .content-list {
    grid-template-columns: 1fr;
  }
}
</style>
