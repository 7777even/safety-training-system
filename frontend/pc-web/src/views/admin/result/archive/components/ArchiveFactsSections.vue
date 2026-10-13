<script setup lang="ts">
import { computed, ref } from 'vue';
import { Download, View } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import AssetViewer from '@/components/asset-viewer/AssetViewer.vue';
import { getTrainingAttachmentFile, getTrainingRewardEvidence } from '@/api/training-process';
import {
  type ArchiveAttendance,
  type ArchiveCertificateItem,
  type ArchiveContent,
  type ArchiveEvaluation,
  type ArchiveMaterial,
  type ArchiveParticipant,
  type ArchiveRewardPenalty,
  type ArchiveScore,
  type ArchiveView
} from '@/api/m4-outcome';
import ArchiveCertificateTable from './ArchiveCertificateTable.vue';
import {
  ARCHIVE_SIGN_IN_STATUS_OPTIONS,
  TRAINING_ATTACHMENT_TYPE_OPTIONS,
  TRAINING_CONTENT_TYPE_OPTIONS,
  TRAINING_RESULT_STATUS_OPTIONS,
  TRAINING_REWARD_RECORD_TYPE_OPTIONS,
  getEnumLabel,
  type EnumOption
} from '@/constants/enum-options';
import { controlledFileName, saveBlobAsFile } from '@/libs/controlled-file';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

/**
 * 一期档案的事实展示块：统计、内容、成绩（含签到）、综合评价、奖惩、影像、附件与证书。
 * <p>企业档案与个人档案复用同一份事实口径，因此只接收 ArchiveView，不自行拼装或补齐字段。
 * <p>证书区由后端按 certificate:cert:list 门控：缺少权限时整个字段不返回，此处整块隐藏，
 * 不用「返回清单但禁用下载」代替。个人档案已有本人证书卡时用 showCertificates 关掉本区块，避免同一批证书出现两次。
 */
const props = withDefaults(defineProps<{ archive: ArchiveView; showCertificates?: boolean }>(), {
  showCertificates: true
});
const userStore = useUserStore();

/** 影像与附件原件走受控资料端点，缺少资料查看权限时不提供预览与下载入口。 */
const canPreviewMaterial = computed(() => hasPermission(userStore.userInfo, 'training:attachment:list'));
/** 奖惩佐证走奖惩记录自身的受控端点，与资料权限分别门控、不互相顶替。 */
const canPreviewReward = computed(() => hasPermission(userStore.userInfo, 'training:reward:list'));

const statistics = computed(() => props.archive.statistics);
const contents = computed((): ArchiveContent[] => props.archive.contents ?? []);
const scores = computed((): ArchiveScore[] => props.archive.scores ?? []);
const evaluations = computed((): ArchiveEvaluation[] => props.archive.evaluations ?? []);
const rewards = computed((): ArchiveRewardPenalty[] => props.archive.rewardPenalties ?? []);
const materials = computed((): ArchiveMaterial[] => props.archive.materials ?? []);
const attachments = computed((): ArchiveMaterial[] => props.archive.attachments ?? []);
const certificates = computed((): ArchiveCertificateItem[] => props.archive.certificates ?? []);
const hasCertificateSection = computed(() => props.showCertificates && props.archive.certificates !== undefined);

/** 签到情况按参训人关联到成绩行，避免同一批人展示两张并列表。 */
const attendanceByParticipant = computed(() => {
  const map = new Map<number, ArchiveAttendance>();
  for (const row of props.archive.attendance ?? []) {
    map.set(row.trainingParticipantId, row);
  }
  return map;
});

/** 成绩与综合评价事实里没有工号，工号统一从参训人清单关联，避免在成绩里重复存一份。 */
const participantByParticipantId = computed(() => {
  const map = new Map<number, ArchiveParticipant>();
  for (const row of props.archive.participants ?? []) {
    map.set(row.trainingParticipantId, row);
  }
  return map;
});

interface MetricItem {
  label: string;
  value: string;
  hint?: string;
}
/** 统计指标一次算好，模板只负责渲染，避免模板里堆叠判断。 */
const metricItems = computed((): MetricItem[] => {
  const stat = statistics.value;
  if (!stat) return [];
  const items: MetricItem[] = [
    { label: '参训人数', value: String(stat.participantCount) },
    { label: '已完成', value: String(stat.completedCount), hint: '结果不为「结果待补全」' },
    { label: '通过', value: String(stat.passedCount) },
    { label: '未通过', value: String(stat.failedCount) },
    { label: '结果待补全', value: String(stat.incompleteCount) },
    { label: '通过率', value: percentText(stat.passRate), hint: '分母为已判定人数' },
    { label: '平均综合分', value: decimalText(stat.averageCompositeScore) },
    { label: '培训内容', value: String(stat.contentCount), hint: `必修 ${stat.requiredContentCount} 项` },
    { label: '线上学时', value: decimalText(stat.onlineHoursTotal) },
    { label: '线下学时', value: decimalText(stat.offlineHoursTotal) },
    { label: '影像记录', value: String(stat.imageCount) },
    { label: '附件', value: String(stat.attachmentCount) },
    { label: '资料合计', value: String(stat.materialCount), hint: '影像与附件合计' },
    { label: '奖励', value: String(stat.rewardCount) },
    { label: '惩罚', value: String(stat.penaltyCount) }
  ];
  // 计划不含线下内容时后端返回空值，此时不展示签到口径，避免出现误导性的 0。
  if (stat.signedCount != null || stat.notRecordedCount != null) {
    items.push({ label: '已到场', value: decimalText(stat.signedCount), hint: '教师已录入线下成绩' });
    items.push({ label: '未记录', value: decimalText(stat.notRecordedCount), hint: '尚未录入线下成绩' });
  }
  return items;
});

const previewVisible = ref(false);
const previewLoading = ref(false);
const previewTitle = ref('培训资料预览');
const previewFile = ref<globalThis.File>();
/** 关闭或切换时作废未完成的请求，避免旧文件覆盖新选择。 */
let previewRequestId = 0;

function toText<T extends string>(value: unknown, options: readonly EnumOption<T>[]): string {
  return getEnumLabel(typeof value === 'string' ? (value as T) : undefined, options);
}
function contentTypeText(value: unknown): string {
  return toText(value, TRAINING_CONTENT_TYPE_OPTIONS);
}
function resultStatusText(value: unknown): string {
  return toText(value, TRAINING_RESULT_STATUS_OPTIONS);
}
function attachmentTypeText(value: unknown): string {
  return toText(value, TRAINING_ATTACHMENT_TYPE_OPTIONS);
}
function rewardRecordTypeText(value: unknown): string {
  return toText(value, TRAINING_REWARD_RECORD_TYPE_OPTIONS);
}
function signInText(value: unknown): string {
  return toText(value, ARCHIVE_SIGN_IN_STATUS_OPTIONS);
}
/** 0/1 契约字段统一转中文，空值不冒充「否」。 */
function yesNoText(value: unknown): string {
  if (value == null) return '—';
  return Number(value) === 1 ? '是' : '否';
}
/** 分数为空表示尚未出分，用占位符而不是 0。 */
function decimalText(value: unknown): string {
  if (value == null) return '—';
  return typeof value === 'number' ? String(Math.round(value * 100) / 100) : String(value);
}
/** 比率为空表示分母为 0，不用 0% 冒充。 */
function percentText(value: unknown): string {
  if (value == null) return '—';
  return typeof value === 'number' ? `${(value * 100).toFixed(2)}%` : String(value);
}
/** 接口返回 ISO 字符串，页面统一转成可读格式。 */
function dateTimeText(value: unknown): string {
  if (!value) return '—';
  return String(value).replace('T', ' ').replace(/:00$/, '');
}
/** 签到三态各自表达「到场 / 不适用 / 待补」，不能用同一种颜色。 */
function signInTagType(value: unknown): 'success' | 'info' | 'warning' {
  if (value === 'SIGNED') return 'success';
  if (value === 'NOT_APPLICABLE') return 'info';
  return 'warning';
}
function scoreRow(row: unknown): ArchiveScore {
  return row as ArchiveScore;
}
function contentRow(row: unknown): ArchiveContent {
  return row as ArchiveContent;
}
function materialRow(row: unknown): ArchiveMaterial {
  return row as ArchiveMaterial;
}
function rewardRow(row: unknown): ArchiveRewardPenalty {
  return row as ArchiveRewardPenalty;
}
/** 成绩行的签到情况；无签到事实时返回空，不猜测状态。 */
function attendanceOf(participantId: number): ArchiveAttendance | undefined {
  return attendanceByParticipant.value.get(participantId);
}
/** 成绩行的工号取自参训人清单。 */
function userCodeOf(participantId: number): string {
  return participantByParticipantId.value.get(participantId)?.userCode ?? '—';
}

/** 影像与附件的原件都按记录读取，页面不持有可直接访问的文件地址。 */
async function openPreview(record: ArchiveMaterial): Promise<void> {
  const requestId = ++previewRequestId;
  previewFile.value = undefined;
  previewTitle.value = '培训资料预览';
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    const blob = await getTrainingAttachmentFile(record.trainingAttachmentId);
    if (previewVisible.value && requestId === previewRequestId) {
      previewFile.value = new globalThis.File([blob], controlledFileName(record.attachmentName, blob.type), { type: blob.type });
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

/**
 * 奖惩佐证按奖惩记录受控读取，与资料预览复用同一个弹窗。
 * <p>入口由 `training:reward:list` 单独门控：缺权限或该条无佐证时整列显示占位符，
 * 不发出必然失败的请求，也不显示「无权限」字样。
 */
async function openRewardEvidence(record: ArchiveRewardPenalty): Promise<void> {
  if (!canPreviewReward.value || !record.evidenceFileId) return;
  const requestId = ++previewRequestId;
  previewFile.value = undefined;
  previewTitle.value = '奖惩佐证预览';
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    const blob = await getTrainingRewardEvidence(record.rewardPenaltyId);
    if (previewVisible.value && requestId === previewRequestId) {
      previewFile.value = new globalThis.File(
        [blob], controlledFileName('奖惩佐证', blob.type), { type: blob.type });
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

async function saveMaterial(record: ArchiveMaterial): Promise<void> {
  try {
    const blob = await getTrainingAttachmentFile(record.trainingAttachmentId);
    saveBlobAsFile(blob, controlledFileName(record.attachmentName, blob.type));
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '受控文件下载失败。');
  }
}

function closePreview(): void {
  previewRequestId += 1;
  previewFile.value = undefined;
  previewLoading.value = false;
}
</script>
<template>
  <section v-if="statistics" class="ws-section">
    <header class="ws-section__head">
      <div>
        <h3>数据统计</h3>
        <p>培训的内容、人数、整体学员完成情况与数据指标；比率分母为 0 时不做 0% 冒充。</p>
      </div>
    </header>
    <div class="ws-metrics">
      <div v-for="item in metricItems" :key="item.label" class="ws-metric">
        <span class="ws-metric__label">{{ item.label }}</span>
        <span class="ws-metric__value">
          {{ item.value }}
          <em v-if="item.hint">{{ item.hint }}</em>
        </span>
      </div>
    </div>
  </section>

  <section class="ws-section">
    <header class="ws-section__head">
      <div>
        <h3>培训内容</h3>
        <p>内容名称、类型、必修与计分属性、权重与要求学时。</p>
      </div>
      <span class="ws-section__extra">共 {{ contents.length }} 项</span>
    </header>
    <ElTable :data="contents" row-key="planContentId" empty-text="该培训计划没有内容项">
      <ElTableColumn label="内容名称" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ contentRow(row).contentName }}</template>
      </ElTableColumn>
      <ElTableColumn label="类型" width="110">
        <template #default="{ row }">{{ contentTypeText(contentRow(row).contentType) }}</template>
      </ElTableColumn>
      <ElTableColumn label="必修" width="80">
        <template #default="{ row }">{{ yesNoText(contentRow(row).isRequired) }}</template>
      </ElTableColumn>
      <ElTableColumn label="计分" width="80">
        <template #default="{ row }">{{ yesNoText(contentRow(row).isScored) }}</template>
      </ElTableColumn>
      <ElTableColumn label="权重" width="90">
        <template #default="{ row }">{{ decimalText(contentRow(row).scoreWeight) }}</template>
      </ElTableColumn>
      <ElTableColumn label="要求学时" width="110">
        <template #default="{ row }">{{ decimalText(contentRow(row).requiredHours) }}</template>
      </ElTableColumn>
      <ElTableColumn label="所属阶段" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ contentRow(row).stageName || '—' }}</template>
      </ElTableColumn>
    </ElTable>
  </section>

  <section class="ws-section">
    <header class="ws-section__head">
      <div>
        <h3>参训人员成绩（含签到情况）</h3>
        <p>签到情况由「教师是否已录入线下成绩」派生：已录入即视为到场，计划不含线下内容时为不适用。</p>
      </div>
      <span class="ws-section__extra">共 {{ scores.length }} 人</span>
    </header>
    <ElTable :data="scores" row-key="trainingParticipantId" empty-text="暂无参训人员成绩">
      <ElTableColumn label="姓名" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ scoreRow(row).userName || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="工号" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ userCodeOf(scoreRow(row).trainingParticipantId) }}</template>
      </ElTableColumn>
      <ElTableColumn label="签到情况">
        <template #default="{ row }">
          <ElTag :type="signInTagType(attendanceOf(scoreRow(row).trainingParticipantId)?.signInStatus)" effect="light">
            {{ signInText(attendanceOf(scoreRow(row).trainingParticipantId)?.signInStatus) }}
          </ElTag>
          <span v-if="attendanceOf(scoreRow(row).trainingParticipantId)?.recordedTime" class="archive-facts__hint">
            {{ dateTimeText(attendanceOf(scoreRow(row).trainingParticipantId)?.recordedTime) }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="课程分" width="100">
        <template #default="{ row }">{{ decimalText(scoreRow(row).courseScore) }}</template>
      </ElTableColumn>
      <ElTableColumn label="考试分" width="100">
        <template #default="{ row }">{{ decimalText(scoreRow(row).examScore) }}</template>
      </ElTableColumn>
      <ElTableColumn label="线下分" width="100">
        <template #default="{ row }">{{ decimalText(scoreRow(row).offlineScore) }}</template>
      </ElTableColumn>
      <ElTableColumn label="综合分" width="100">
        <template #default="{ row }">{{ decimalText(scoreRow(row).compositeScore) }}</template>
      </ElTableColumn>
      <ElTableColumn label="结果" width="120">
        <template #default="{ row }">{{ resultStatusText(scoreRow(row).resultStatus) }}</template>
      </ElTableColumn>
    </ElTable>
  </section>

  <section class="ws-section">
    <header class="ws-section__head">
      <div>
        <h3>综合评价</h3>
        <p>综合分与结果状态；结果待补全的人员不得当作已完成。</p>
      </div>
      <span class="ws-section__extra">共 {{ evaluations.length }} 人</span>
    </header>
    <ElTable :data="evaluations" row-key="trainingParticipantId" empty-text="暂无综合评价">
      <ElTableColumn label="姓名" show-overflow-tooltip>
        <template #default="{ row }">{{ scoreRow(row).userName || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="综合分">
        <template #default="{ row }">{{ decimalText(scoreRow(row).compositeScore) }}</template>
      </ElTableColumn>
      <ElTableColumn label="结果状态">
        <template #default="{ row }">{{ resultStatusText(scoreRow(row).resultStatus) }}</template>
      </ElTableColumn>
    </ElTable>
  </section>

  <section class="ws-section">
    <header class="ws-section__head">
      <div>
        <h3>奖惩记录</h3>
        <p>与正式结果口径一致，只展示启用（status=1）的记录；佐证由奖惩读取权限门控，档案文件不内嵌佐证明细。</p>
      </div>
      <span class="ws-section__extra">共 {{ rewards.length }} 条</span>
    </header>
    <ElTable :data="rewards" row-key="rewardPenaltyId" empty-text="暂无奖惩记录">
      <ElTableColumn label="类型" width="90">
        <template #default="{ row }">{{ rewardRecordTypeText(rewardRow(row).recordType) }}</template>
      </ElTableColumn>
      <ElTableColumn label="标题" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ rewardRow(row).title }}</template>
      </ElTableColumn>
      <ElTableColumn label="分数变动" width="110">
        <template #default="{ row }">{{ decimalText(rewardRow(row).scoreChange) }}</template>
      </ElTableColumn>
      <ElTableColumn label="发生日期" width="150">
        <template #default="{ row }">{{ dateTimeText(rewardRow(row).occurredDate) }}</template>
      </ElTableColumn>
      <ElTableColumn label="说明" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ rewardRow(row).description || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="佐证" width="110">
        <template #default="{ row }">
          <ElButton
            v-if="canPreviewReward && rewardRow(row).evidenceFileId"
            :icon="View"
            link
            type="primary"
            @click="openRewardEvidence(rewardRow(row))"
          >预览</ElButton>
          <span v-else>—</span>
        </template>
      </ElTableColumn>
    </ElTable>
  </section>

  <section class="ws-section">
    <header class="ws-section__head">
      <div>
        <h3>影像记录</h3>
        <p>现场影像只出清单，原件经受控资料端点预览或下载，不内嵌进档案文件。</p>
      </div>
      <span class="ws-section__extra">共 {{ materials.length }} 条</span>
    </header>
    <ElTable :data="materials" row-key="trainingAttachmentId" empty-text="暂无影像记录">
      <ElTableColumn label="资料名称" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ materialRow(row).attachmentName }}</template>
      </ElTableColumn>
      <ElTableColumn label="资料类型" width="120">
        <template #default="{ row }">{{ attachmentTypeText(materialRow(row).attachmentType) }}</template>
      </ElTableColumn>
      <ElTableColumn label="关联阶段" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ materialRow(row).stageName || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="说明" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ materialRow(row).description || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="文件" width="150">
        <template #default="{ row }">
          <template v-if="canPreviewMaterial && materialRow(row).fileInfoId">
            <ElButton :icon="View" link type="primary" @click="openPreview(materialRow(row))">预览</ElButton>
            <ElButton :icon="Download" link type="primary" @click="saveMaterial(materialRow(row))">下载</ElButton>
          </template>
          <span v-else>—</span>
        </template>
      </ElTableColumn>
    </ElTable>
  </section>

  <section class="ws-section">
    <header class="ws-section__head">
      <div>
        <h3>附件清单</h3>
        <p>一般资料与佐证附件同样只出清单，原件经受控资料端点读取。</p>
      </div>
      <span class="ws-section__extra">共 {{ attachments.length }} 条</span>
    </header>
    <ElTable :data="attachments" row-key="trainingAttachmentId" empty-text="暂无附件">
      <ElTableColumn label="资料名称" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ materialRow(row).attachmentName }}</template>
      </ElTableColumn>
      <ElTableColumn label="资料类型" width="120">
        <template #default="{ row }">{{ attachmentTypeText(materialRow(row).attachmentType) }}</template>
      </ElTableColumn>
      <ElTableColumn label="关联阶段" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ materialRow(row).stageName || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="说明" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ materialRow(row).description || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn label="文件" width="150">
        <template #default="{ row }">
          <template v-if="canPreviewMaterial && materialRow(row).fileInfoId">
            <ElButton :icon="View" link type="primary" @click="openPreview(materialRow(row))">预览</ElButton>
            <ElButton :icon="Download" link type="primary" @click="saveMaterial(materialRow(row))">下载</ElButton>
          </template>
          <span v-else>—</span>
        </template>
      </ElTableColumn>
    </ElTable>
  </section>

  <section v-if="hasCertificateSection" class="ws-section">
    <header class="ws-section__head">
      <div>
        <h3>证书清单</h3>
        <p>证书只读聚合；原件与下载走既有证书端点，不在档案中复制。</p>
      </div>
      <span class="ws-section__extra">共 {{ certificates.length }} 张</span>
    </header>
    <ArchiveCertificateTable :certificates="certificates" />
  </section>

  <ElDialog
    v-model="previewVisible"
    :title="previewTitle"
    width="min(900px, calc(100vw - 32px))"
    append-to-body
    @close="closePreview"
  >
    <div v-loading="previewLoading" class="archive-facts__preview">
      <AssetViewer v-if="previewFile" :local-file="previewFile" preview-height="min(70vh, 620px)" />
    </div>
  </ElDialog>
</template>
<style scoped lang="less">
/* 预览弹窗在文件到位前保留最小高度，避免加载中高度塌陷造成弹窗跳动。 */
.archive-facts__preview {
  min-height: 240px;
}

/* 签到情况单元格内的时间提示与标签同列，字号收小避免撑高表格行。 */
.archive-facts__hint {
  display: block;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
  margin-left:10px;
}
</style>
