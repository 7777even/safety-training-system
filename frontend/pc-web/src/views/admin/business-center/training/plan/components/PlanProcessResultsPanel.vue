<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import { ElMessage, ElMessageBox } from 'element-plus';
const router = useRouter();
const userStore = useUserStore();
/** 课堂成绩统计与后端计划/成绩双权限保持一致。 */
const canViewOfflineStatistics = computed(() => hasPermission(userStore.userInfo,'training:plan:list') && hasPermission(userStore.userInfo,'training:offline-score:list'));
import { uploadAssetFile } from '@/api/asset';
import {
  createTrainingAttachment,
  createTrainingRewardPenalty,
  deleteTrainingAttachment,
  deleteTrainingRewardPenalty,
  getTrainingAttachmentFile,
  getTrainingRewardEvidence,
  updateTrainingAttachment,
  updateTrainingRewardPenalty,
  type TrainingAttachment,
  type TrainingAttachmentPayload,
  type TrainingAttachmentType,
  type TrainingRewardPenalty,
  type TrainingRewardPenaltyPayload
} from '@/api/training-process';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import AssetViewer from '@/components/asset-viewer/AssetViewer.vue';
import { controlledFileName } from '@/libs/controlled-file';

interface Participant {
  trainingParticipantId: number;
  userId: number;
  participantStatus?: string;
}

/** 关联阶段只展示已真实落库的阶段，避免把草稿阶段写进资料。 */
interface PlanStageOption {
  planStageId?: number;
  stageName: string;
}

const props = defineProps<{
  offlineSessions: Array<Record<string, unknown>>;
  attachments: TrainingAttachment[];
  rewardPenalties: TrainingRewardPenalty[];
  participants: Participant[];
  participantNames: Record<number, string>;
  stages: PlanStageOption[];
  planId: number;
  planStatus: string;
  showAttachments: boolean;
  showRewards: boolean;
  canAddReward: boolean;
  canEditReward: boolean;
  canDeleteReward: boolean;
  canAddMaterial: boolean;
  canEditMaterial: boolean;
  canDeleteMaterial: boolean;
}>();
const emit = defineEmits<{ 'reward-changed': []; 'material-changed': [] }>();

/** 奖惩表单只存当前计划可录入的业务字段，不允许手工填写关联主键。 */
const rewardForm = reactive<TrainingRewardPenaltyPayload & { trainingParticipantId?: number }>({
  recordType: 'REWARD', title: '', description: '', scoreChange: undefined,
  occurredDate: undefined, evidenceFileId: undefined, status: 1
});
const rewardDialogVisible = ref(false);
const uploadVisible = ref(false);
const rewardSubmitting = ref(false);
const editingReward = ref<TrainingRewardPenalty>();
/** 新上传文件在保存前直接预览；已保存佐证按记录从服务端读取。 */
const uploadedEvidence = ref<globalThis.File>();
const previewFile = ref<globalThis.File>();
const previewVisible = ref(false);
const previewLoading = ref(false);
let previewRequestId = 0;
const canWriteReward = computed(() => props.planStatus === 'RUNNING');

/** 培训资料表单只存当前计划可维护的业务字段，关联主键与文件 ID 均由服务端数据决定。 */
const materialForm = reactive<Omit<TrainingAttachmentPayload, 'fileInfoId'> & { fileInfoId?: number }>({
  attachmentType: 'GENERAL',
  attachmentName: '',
  description: '',
  planStageId: undefined,
  fileInfoId: undefined,
  sortOrder: 1,
  status: 1
});
const materialDialogVisible = ref(false);
const materialSubmitting = ref(false);
const materialUploadVisible = ref(false);
const editingMaterial = ref<TrainingAttachment>();
/** 新上传资料在保存前直接预览；已保存资料按记录从受控端点读取。 */
const uploadedMaterial = ref<globalThis.File>();
const canWriteMaterial = computed(() => props.planStatus === 'RUNNING');
/** 关联阶段只能从本计划已落库的阶段中选择，草稿阶段不进入选项。 */
const stageOptions = computed(() => props.stages.filter((stage) => stage.planStageId));
/** 资料类型选项与后端收敛的枚举一致，页面不提供自由输入；奖惩佐证统一走奖惩记录。 */
const materialTypeOptions: Array<{ label: string; value: TrainingAttachmentType }> = [
  { label: '一般资料', value: 'GENERAL' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '评价佐证', value: 'EVALUATION' }
];
/** 预览弹窗标题随来源切换，影像与佐证共用同一个受控预览器。 */
const previewTitle = ref('佐证文件预览');

/** 收敛 Element Plus 表格插槽的宽泛行类型。 */
function asRewardRow(row: unknown): TrainingRewardPenalty {
  return row as TrainingRewardPenalty;
}

/** 从表格行或空白表单进入同一个最小录入弹窗。 */
function openRewardForm(record?: TrainingRewardPenalty): void {
  editingReward.value = record;
  uploadedEvidence.value = undefined;
  Object.assign(rewardForm, {
    trainingParticipantId: record?.trainingParticipantId,
    recordType: record?.recordType ?? 'REWARD',
    title: record?.title ?? '', description: record?.description ?? '',
    scoreChange: record?.scoreChange, occurredDate: record?.occurredDate,
    evidenceFileId: record?.evidenceFileId, status: record?.status ?? 1
  });
  rewardDialogVisible.value = true;
}

/** 上传成功后仅使用服务端返回的文件主键作为可选佐证。 */
function handleRewardUpload(files: UnifiedUploadedFile[]): void {
  rewardForm.evidenceFileId = files[0]?.fileInfoId;
  uploadedEvidence.value = files[0]?.sourceFile;
  uploadVisible.value = false;
}

/** 从列表或表单打开同一预览弹窗；本次未保存的替换文件优先展示本地内容。 */
async function previewBoundFile(kind: 'REWARD' | 'MATERIAL',
                                record?: TrainingRewardPenalty | TrainingAttachment): Promise<void> {
  const requestId = ++previewRequestId;
  const isReward = kind === 'REWARD';
  previewFile.value = undefined;
  previewTitle.value = isReward ? '奖惩佐证预览' : '培训资料预览';
  previewVisible.value = true;
  const local = isReward ? uploadedEvidence.value : uploadedMaterial.value;
  if (!record && local) {
    previewFile.value = local;
    return;
  }
  const id = isReward
    ? (record as TrainingRewardPenalty | undefined)?.rewardPenaltyId ?? editingReward.value?.rewardPenaltyId
    : (record as TrainingAttachment | undefined)?.trainingAttachmentId ?? editingMaterial.value?.trainingAttachmentId;
  if (!id) return;
  previewLoading.value = true;
  try {
    const blob = isReward ? await getTrainingRewardEvidence(id) : await getTrainingAttachmentFile(id);
    if (previewVisible.value && requestId === previewRequestId) {
      previewFile.value = new globalThis.File(
        [blob], controlledFileName(isReward ? '奖惩佐证' : '培训资料', blob.type), { type: blob.type });
    }
  } catch (error) {
    if (requestId === previewRequestId) {
      previewVisible.value = false;
      ElMessage.error(error instanceof Error ? error.message : '受控文件预览失败。');
    }
  } finally {
    if (requestId === previewRequestId) previewLoading.value = false;
  }
}

/** 奖惩列表与表单沿用同一预览入口。 */
async function previewEvidence(record?: TrainingRewardPenalty): Promise<void> {
  await previewBoundFile('REWARD', record);
}

/** 培训资料列表与表单沿用同一预览入口。 */
async function previewMaterial(record?: TrainingAttachment): Promise<void> {
  await previewBoundFile('MATERIAL', record);
}

/** 关闭预览时作废未完成的请求，避免旧文件覆盖下一次选择。 */
function closeEvidencePreview(): void {
  previewRequestId += 1;
  previewFile.value = undefined;
  previewLoading.value = false;
}

/** 保存后通知工作台回读奖惩、计划版本与个人正式结果。 */
async function saveReward(): Promise<void> {
  if (!rewardForm.trainingParticipantId || !rewardForm.title.trim()) {
    ElMessage.warning('请选择本计划参训人并填写标题。');
    return;
  }
  rewardSubmitting.value = true;
  try {
    const payload: TrainingRewardPenaltyPayload = {
      recordType: rewardForm.recordType,
      title: rewardForm.title.trim(),
      description: rewardForm.description,
      scoreChange: rewardForm.scoreChange,
      occurredDate: rewardForm.occurredDate,
      evidenceFileId: rewardForm.evidenceFileId,
      status: rewardForm.status
    };
    if (editingReward.value) {
      await updateTrainingRewardPenalty(editingReward.value.rewardPenaltyId, {
        ...payload, version: editingReward.value.version
      });
    } else {
      await createTrainingRewardPenalty({
        ...payload, trainingPlanId: props.planId,
        trainingParticipantId: rewardForm.trainingParticipantId
      });
    }
    rewardDialogVisible.value = false;
    emit('reward-changed');
    ElMessage.success('奖惩记录已保存。');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '奖惩记录保存失败。');
  } finally {
    rewardSubmitting.value = false;
  }
}

/** 删除前二次确认，成功后由工作台回读正式结果。 */
async function removeReward(record: TrainingRewardPenalty): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除“${record.title}”吗？`, '删除奖惩记录', { type: 'warning' });
    await deleteTrainingRewardPenalty(record.rewardPenaltyId);
    emit('reward-changed');
    ElMessage.success('奖惩记录已删除。');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '奖惩记录删除失败。');
    }
  }
}

/** 从表格行或空白表单进入同一个资料维护弹窗。 */
function openMaterialForm(record?: TrainingAttachment): void {
  editingMaterial.value = record;
  uploadedMaterial.value = undefined;
  Object.assign(materialForm, {
    attachmentType: record?.attachmentType ?? 'GENERAL',
    attachmentName: record?.attachmentName ?? '',
    description: record?.description ?? '',
    planStageId: record?.planStageId,
    fileInfoId: record?.fileInfoId,
    sortOrder: record?.sortOrder ?? props.attachments.length + 1,
    status: record?.status ?? 1
  });
  materialDialogVisible.value = true;
}

/** 上传成功后仅使用服务端返回的文件主键作为资料文件。 */
function handleMaterialUpload(files: UnifiedUploadedFile[]): void {
  materialForm.fileInfoId = files[0]?.fileInfoId;
  uploadedMaterial.value = files[0]?.sourceFile;
  materialUploadVisible.value = false;
}

/** 保存后由工作台重新读取资料列表，不在前端拼装结果。 */
async function saveMaterial(): Promise<void> {
  if (!materialForm.attachmentName.trim()) {
    ElMessage.warning('请填写资料名称。');
    return;
  }
  if (!materialForm.fileInfoId) {
    ElMessage.warning('请先上传资料文件。');
    return;
  }
  materialSubmitting.value = true;
  try {
    const payload: TrainingAttachmentPayload = {
      planStageId: materialForm.planStageId,
      attachmentType: materialForm.attachmentType,
      fileInfoId: materialForm.fileInfoId,
      attachmentName: materialForm.attachmentName.trim(),
      description: materialForm.description,
      sortOrder: materialForm.sortOrder,
      status: materialForm.status
    };
    if (editingMaterial.value) {
      await updateTrainingAttachment(editingMaterial.value.trainingAttachmentId, {
        ...payload, version: editingMaterial.value.version
      });
    } else {
      await createTrainingAttachment({ ...payload, trainingPlanId: props.planId });
    }
    materialDialogVisible.value = false;
    emit('material-changed');
    ElMessage.success('培训资料已保存。');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '培训资料保存失败。');
  } finally {
    materialSubmitting.value = false;
  }
}

/** 删除前二次确认，成功后由工作台回读资料列表。 */
async function removeMaterial(record: TrainingAttachment): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除“${record.attachmentName}”吗？`, '删除培训资料', { type: 'warning' });
    await deleteTrainingAttachment(record.trainingAttachmentId);
    emit('material-changed');
    ElMessage.success('培训资料已删除。');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '培训资料删除失败。');
    }
  }
}

/** 收敛 Element Plus 表格插槽的宽泛行类型。 */
function asMaterialRow(row: unknown): TrainingAttachment {
  return row as TrainingAttachment;
}

/** 关联阶段名称从本计划阶段读取，避免把全局主键展示给管理人员。 */
function stageNameText(planStageId: unknown): string {
  const id = Number(planStageId);
  if (!planStageId || Number.isNaN(id)) {
    return '未关联阶段';
  }
  return props.stages.find((stage) => stage.planStageId === id)?.stageName ?? '未关联阶段';
}

/** 过程页只汇总已有的参训状态，不代替正式成绩计算。 */
const participantSummary = computed(() => {
  const completed = props.participants.filter((item) => item.participantStatus === 'COMPLETED').length;
  const failed = props.participants.filter((item) => item.participantStatus === 'FAILED').length;
  return {
    total: props.participants.length,
    completed,
    failed,
    pending: Math.max(props.participants.length - completed - failed, 0)
  };
});

/** 将过程事实中的状态枚举转换为管理人员可直接理解的中文。 */
function sessionStatusText(status: unknown): string {
  return (
    (
      {
        DRAFT: '草稿',
        SCHEDULED: '已安排',
        IN_PROGRESS: '进行中',
        COMPLETED: '已结束',
        CANCELLED: '已取消'
      } as Record<string, string>
    )[String(status ?? '')] ?? '未知状态'
  );
}

/** 将培训资料类型转换为中文，避免接口枚举值直接暴露到页面。 */
function attachmentTypeText(type: unknown): string {
  const value = type == null ? '' : String(type);
  return materialTypeOptions.find((option) => option.value === value)?.label ?? '其他资料';
}

/** 将奖惩记录类型转换为中文。 */
function recordTypeText(type: unknown): string {
  return ({ REWARD: '奖励', PENALTY: '处罚' } as Record<string, string>)[String(type ?? '')] ?? '其他记录';
}

/** 统一过程记录时间格式，避免直接展示接口的 ISO 字符串。 */
function dateTimeText(value: unknown): string {
  if (!value) return '-';
  return String(value).replace('T', ' ').replace(/:00$/, '');
}
</script>
<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>
      <div class="card-header">
        <div>
          <h2>过程与结果</h2>
          <p>记录培训实际发生的过程，并查看参训结果概览。</p>
        </div>
        <ElTag type="info">{{ canWriteReward ? '执行中' : '只读记录' }}</ElTag>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll">
      <ElAlert title="以下内容均为系统已经记录的培训事实；正式成绩请在“人员与执行”中查看个人分项结果。" type="info" :closable="false" show-icon />

      <section class="result-overview" aria-label="培训结果概览">
        <div class="result-overview__intro">
          <div>
            <span class="section-kicker">结果概览</span>
            <h3>参训完成情况</h3>
          </div>
          <span class="result-overview__hint">此处展示过程状态汇总，不替代正式成绩</span>
        </div>
        <div class="summary-grid">
          <div class="summary-grid__item">
            <span>参训人数</span>
            <strong>{{ participantSummary.total }}</strong>
            <small>已纳入本计划</small>
          </div>
          <div class="summary-grid__item summary-grid__item--success">
            <span>已完成</span>
            <strong>{{ participantSummary.completed }}</strong>
            <small>过程状态为已完成</small>
          </div>
          <div class="summary-grid__item summary-grid__item--warning">
            <span>待完成</span>
            <strong>{{ participantSummary.pending }}</strong>
            <small>仍需继续培训或同步</small>
          </div>
          <div class="summary-grid__item summary-grid__item--danger">
            <span>未通过</span>
            <strong>{{ participantSummary.failed }}</strong>
            <small>请查看个人分项结果</small>
          </div>
        </div>
      </section>

      <section class="facts-section">
        <div class="section-heading">
          <div>
            <span class="section-kicker">过程记录</span>
            <h3>培训过程中实际发生的记录</h3>
          </div>
          <span class="section-heading__count">
            共 {{ props.offlineSessions.length + props.attachments.length + props.rewardPenalties.length }} 条
          </span>
        </div>
        <ElTabs class="facts-tabs">
          <ElTabPane :label="`线下场次（${props.offlineSessions.length}）`">
            <ElTable :data="props.offlineSessions" class="facts-table">
              <ElTableColumn prop="sessionName" label="场次名称" min-width="160" />
              <ElTableColumn prop="location" label="地点" min-width="140" />
              <ElTableColumn label="开始时间" min-width="170">
                <template #default="{ row }">{{ dateTimeText(row.startTime) }}</template>
              </ElTableColumn>
              <ElTableColumn label="结束时间" min-width="170">
                <template #default="{ row }">{{ dateTimeText(row.endTime) }}</template>
              </ElTableColumn>
              <ElTableColumn label="场次状态" width="110">
                <template #default="{ row }">
                  <ElTag size="small">{{ sessionStatusText(row.sessionStatus) }}</ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="120"><template #default="{row}"><ElButton v-if="canViewOfflineStatistics" link type="primary" @click="router.push(`/admin/business-center/training/plan/offline-sessions/${row.offlineSessionId}/statistics`)">课堂考试统计</ElButton></template></ElTableColumn>
              <template #empty><ElEmpty description="暂无线下场次记录" /></template>
            </ElTable>
          </ElTabPane>
          <ElTabPane v-if="props.showAttachments" :label="`培训资料（${props.attachments.length}）`">
            <div v-if="canWriteMaterial && props.canAddMaterial" class="material-actions">
              <ElButton type="primary" @click="openMaterialForm()">上传培训资料</ElButton>
            </div>
            <ElTable :data="props.attachments" class="facts-table">
              <ElTableColumn prop="attachmentName" label="资料名称" min-width="180" show-overflow-tooltip />
              <ElTableColumn label="资料类型" width="120">
                <template #default="{ row }">{{ attachmentTypeText(row.attachmentType) }}</template>
              </ElTableColumn>
              <ElTableColumn label="关联阶段" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ stageNameText(row.planStageId) }}</template>
              </ElTableColumn>
              <ElTableColumn prop="description" label="说明" min-width="180" show-overflow-tooltip />
              <ElTableColumn label="状态" width="90">
                <template #default="{ row }">{{ row.status === 0 ? '停用' : '启用' }}</template>
              </ElTableColumn>
              <ElTableColumn label="文件" width="100">
                <template #default="{ row }">
                  <ElButton v-if="row.fileInfoId" link type="primary" @click="previewMaterial(asMaterialRow(row))">预览</ElButton>
                  <span v-else>无</span>
                </template>
              </ElTableColumn>
              <ElTableColumn v-if="canWriteMaterial && (props.canEditMaterial || props.canDeleteMaterial)" label="操作" width="150">
                <template #default="{ row }">
                  <ElButton v-if="props.canEditMaterial" link type="primary" @click="openMaterialForm(asMaterialRow(row))">编辑</ElButton>
                  <ElButton v-if="props.canDeleteMaterial" link type="danger" @click="removeMaterial(asMaterialRow(row))">删除</ElButton>
                </template>
              </ElTableColumn>
              <template #empty><ElEmpty description="暂无培训资料记录" /></template>
            </ElTable>
          </ElTabPane>
          <ElTabPane v-if="props.showRewards" :label="`奖惩（${props.rewardPenalties.length}）`">
            <div v-if="canWriteReward && props.canAddReward" class="reward-actions">
              <ElButton type="primary" @click="openRewardForm()">新增奖惩</ElButton>
            </div>
            <ElTable :data="props.rewardPenalties" class="facts-table">
              <ElTableColumn label="参训人" min-width="130">
                <template #default="{ row }">{{ props.participantNames[row.trainingParticipantId] || `参训人 ${row.trainingParticipantId}` }}</template>
              </ElTableColumn>
              <ElTableColumn label="记录类型" width="100">
                <template #default="{ row }">{{ recordTypeText(row.recordType) }}</template>
              </ElTableColumn>
              <ElTableColumn prop="title" label="标题" min-width="160" />
              <ElTableColumn prop="scoreChange" label="分值变化" width="110" />
              <ElTableColumn label="状态" width="90">
                <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
              </ElTableColumn>
              <ElTableColumn label="发生日期" width="140">
                <template #default="{ row }">{{ dateTimeText(row.occurredDate) }}</template>
              </ElTableColumn>
              <ElTableColumn prop="description" label="说明" min-width="150" />
              <ElTableColumn label="佐证" width="100">
                <template #default="{ row }">
                  <ElButton v-if="row.evidenceFileId" link type="primary" @click="previewEvidence(asRewardRow(row))">预览</ElButton>
                  <span v-else>无</span>
                </template>
              </ElTableColumn>
              <ElTableColumn v-if="canWriteReward && (props.canEditReward || props.canDeleteReward)" label="操作" width="150">
                <template #default="{ row }">
                  <ElButton v-if="props.canEditReward" link type="primary" @click="openRewardForm(asRewardRow(row))">编辑</ElButton>
                  <ElButton v-if="props.canDeleteReward" link type="danger" @click="removeReward(asRewardRow(row))">删除</ElButton>
                </template>
              </ElTableColumn>
              <template #empty><ElEmpty description="暂无奖惩记录" /></template>
            </ElTable>
          </ElTabPane>
        </ElTabs>
      </section>

      <div class="result-entry">
        <div>
          <strong>查看个人培训结果</strong>
          <span>如需查看课程、考试、线下成绩等分项结果，请返回“人员与执行”，在参训人员列表中点击“查看培训结果”。</span>
        </div>
        <ElTag type="primary" effect="plain">个人分项结果</ElTag>
      </div>
    </div>
    <footer class="sticky-actions" />
  </ElCard>
  <ElDialog v-model="rewardDialogVisible" :title="editingReward ? '编辑奖惩' : '新增奖惩'" width="620px" append-to-body>
    <ElForm label-width="92px">
      <ElFormItem label="参训人" required>
        <ElSelect v-model="rewardForm.trainingParticipantId" placeholder="请选择本计划参训人" filterable :disabled="Boolean(editingReward)">
          <ElOption v-for="item in props.participants" :key="item.trainingParticipantId"
            :label="props.participantNames[item.trainingParticipantId] || `参训人 ${item.trainingParticipantId}`"
            :value="item.trainingParticipantId" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="类型" required>
        <ElRadioGroup v-model="rewardForm.recordType">
          <ElRadio value="REWARD">奖励</ElRadio><ElRadio value="PENALTY">处罚</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="标题" required><ElInput v-model="rewardForm.title" maxlength="200" show-word-limit /></ElFormItem>
      <ElFormItem label="说明"><ElInput v-model="rewardForm.description" type="textarea" :rows="3" /></ElFormItem>
      <ElFormItem label="分值变化">
        <ElInputNumber v-model="rewardForm.scoreChange" :precision="2" :step="1" controls-position="right" />
        <span class="reward-hint">奖励填正数、处罚填负数；留空不改变分数，0 分有效。</span>
      </ElFormItem>
      <ElFormItem label="发生日期"><ElDatePicker v-model="rewardForm.occurredDate" type="date" value-format="YYYY-MM-DD" /></ElFormItem>
      <ElFormItem label="佐证文件">
        <ElButton @click="uploadVisible = true">上传佐证</ElButton>
        <ElButton v-if="rewardForm.evidenceFileId" link type="primary" @click="previewEvidence()">预览佐证</ElButton>
        <ElButton v-if="rewardForm.evidenceFileId" link @click="rewardForm.evidenceFileId = undefined; uploadedEvidence = undefined">移除</ElButton>
      </ElFormItem>
      <ElFormItem label="状态"><ElSwitch v-model="rewardForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="rewardDialogVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="rewardSubmitting" @click="saveReward">保存</ElButton>
    </template>
  </ElDialog>
  <ElDialog v-model="materialDialogVisible" :title="editingMaterial ? '编辑培训资料' : '上传培训资料'" width="620px" append-to-body>
    <ElForm label-width="92px">
      <ElFormItem label="资料类型" required>
        <ElSelect v-model="materialForm.attachmentType">
          <ElOption
            v-for="option in materialTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="资料名称" required>
        <ElInput v-model="materialForm.attachmentName" maxlength="255" show-word-limit />
      </ElFormItem>
      <ElFormItem label="说明"><ElInput v-model="materialForm.description" type="textarea" :rows="3" /></ElFormItem>
      <ElFormItem label="关联阶段">
        <ElSelect v-model="materialForm.planStageId" clearable placeholder="可不关联阶段">
          <ElOption
            v-for="stage in stageOptions"
            :key="stage.planStageId"
            :label="stage.stageName"
            :value="stage.planStageId as number"
          />
        </ElSelect>
        <span class="reward-hint">资料不强制绑定线下阶段，线上培训期间产生的影像与附件同样在此维护。</span>
      </ElFormItem>
      <ElFormItem label="资料文件" required>
        <ElButton @click="materialUploadVisible = true">
          {{ materialForm.fileInfoId ? '重新上传' : '上传文件' }}
        </ElButton>
        <ElButton v-if="materialForm.fileInfoId" link type="primary" @click="previewMaterial()">预览文件</ElButton>
      </ElFormItem>
      <ElFormItem label="排序">
        <ElInputNumber v-model="materialForm.sortOrder" :min="0" :step="1" controls-position="right" />
      </ElFormItem>
      <ElFormItem label="状态">
        <ElSwitch v-model="materialForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="materialDialogVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="materialSubmitting" @click="saveMaterial">保存</ElButton>
    </template>
  </ElDialog>
  <UnifiedFileUpload v-model="materialUploadVisible" mode="dialog" :multiple="false" :limit="1"
    :upload-request="(file, onProgress, signal) => uploadAssetFile(file, onProgress, 'general', signal)"
    @success="handleMaterialUpload" />
  <UnifiedFileUpload v-model="uploadVisible" mode="dialog" :multiple="false" :limit="1"
    :upload-request="(file, onProgress, signal) => uploadAssetFile(file, onProgress, 'general', signal)"
    @success="handleRewardUpload" />
  <ElDialog v-model="previewVisible" :title="previewTitle" width="min(900px, calc(100vw - 32px))" append-to-body
    @close="closeEvidencePreview">
    <div v-loading="previewLoading" class="reward-preview">
      <AssetViewer v-if="previewFile" :local-file="previewFile" preview-height="min(70vh, 620px)" />
    </div>
  </ElDialog>
</template>

<style scoped lang="less">
.card-header,
.result-overview__intro,
.section-heading,
.result-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.card-header h2,
.result-overview h3,
.section-heading h3 {
  margin: 0;
  color: var(--color-text-primary);
}

.card-header p {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.result-overview,
.facts-section {
  margin-top: 16px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-container);
}

.section-kicker {
  display: block;
  margin-bottom: 5px;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.result-overview__hint,
.section-heading__count {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.summary-grid__item {
  min-height: 86px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-page);
}

.summary-grid__item span,
.summary-grid__item small {
  display: block;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.summary-grid__item strong {
  display: block;
  margin: 6px 0 2px;
  color: var(--color-text-primary);
  font-size: 24px;
  line-height: 1;
}

.summary-grid__item--success strong {
  color: var(--color-success, #67c23a);
}
.summary-grid__item--warning strong {
  color: var(--color-warning, #e6a23c);
}
.summary-grid__item--danger strong {
  color: var(--color-danger, #dc2626);
}

.facts-tabs {
  margin-top: 10px;
}

.facts-table {
  width: 100%;
}

.reward-actions,
.material-actions { margin-bottom: 12px; }
.reward-hint { margin-left: 10px; color: var(--color-text-secondary); font-size: 12px; }
.reward-preview { min-height: 240px; }

.result-entry {
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px dashed var(--color-primary, #2563eb);
  border-radius: 10px;
  background: rgba(37, 99, 235, 0.04);
}

.result-entry strong {
  display: block;
}

.result-entry span {
  display: flex;
  margin-top: 5px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
