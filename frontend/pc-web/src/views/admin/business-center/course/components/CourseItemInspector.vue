<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { getAssetDetail, type AssetDetail, type AssetListItem } from '@/api/asset';
import type { PersonPickerItem } from '@/api/person-picker';
import {
  createCourseItem,
  getLiveSession,
  updateCourseItem,
  updateLiveSession,
  type CourseItem,
  type LiveSessionPayload
} from '@/api/course-operations';
import { getQuestionBankPage, type QuestionBank } from '@/api/question';
import { getPaperPage, getPaperVersions, type PaperVersionSummary } from '@/api/paper';
import DurationInput from '@/components/duration/DurationInput.vue';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import { canManageTenantScope, currentTenantId } from '@/utils/tenant-context';
import PersonPickerDialog from '@/components/organization/PersonPickerDialog.vue';
import OrganizationSelect from '@/components/organization/OrganizationSelect.vue';
import ReferenceSelect from '@/components/organization/ReferenceSelect.vue';
import AssetPickerDialog from '@/components/asset-picker/AssetPickerDialog.vue';

const props = defineProps<{
  chapterId: number;
  item?: CourseItem;
  defaultSortOrder: number;
  /** 同课程全部测验，供课件显式关联。 */
  quizItems?: CourseItem[];
}>();
const emit = defineEmits<{
  saved: [itemId?: number];
  cancelCreate: [];
}>();

const userStore = useUserStore();
const saving = ref(false);
const assetPickerVisible = ref(false);
const selectedAsset = ref<AssetDetail>();
const teacherPickerVisible = ref(false);
const selectedTeacher = ref<PersonPickerItem>();
const liveOrganizationId = ref<number>();
const hasLoadedLive = ref(false);
const publishedQuestionBanks = ref<QuestionBank[]>([]);
const publishedPaperVersions = ref<Array<PaperVersionSummary & { paperName: string }>>([]);

/** 当前内容项的可编辑字段。 */
const itemForm = reactive({
  itemType: 'ASSET' as 'ASSET' | 'LIVE' | 'QUESTION_BANK' | 'QUIZ',
  itemTitle: '',
  assetId: undefined as number | undefined,
  linkedQuizItemId: null as number | null,
  questionBankId: undefined as number | undefined,
  paperVersionId: undefined as number | undefined,
  isRequired: 1 as 0 | 1,
  isFinalAssessment: 0 as 0 | 1,
  passScore: null as number | null,
  minimumStudySeconds: 15,
  sortOrder: 0,
  status: 1 as 0 | 1,
  isLockedAfterParticipation: 0 as 0 | 1,
  version: 0
});
const liveForm = reactive<LiveSessionPayload>({
  liveName: '',
  teacherUserId: 0,
  liveUrl: '',
  startTime: '',
  endTime: '',
  audienceId: null,
  liveStatus: 'DRAFT',
  status: 1,
  version: 0
});
/** 直播状态的显示文案与 Contract 值映射。 */
const LIVE_STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已排期', value: 'SCHEDULED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '直播中', value: 'LIVING' },
  { label: '已结束', value: 'ENDED' }
] as const;

const isEditing = computed(() => Boolean(props.item?.courseItemId));
const panelTitle = computed(() => (isEditing.value ? '内容设置' : '新增内容'));
const canEdit = computed(() => hasPermission(userStore.userInfo, isEditing.value ? 'course:item:edit' : 'course:item:add'));
/** 参与后锁定只作用于考核类内容项（题库练习、课程测验），其余类型不展示该开关。 */
const supportsLockAfterParticipation = computed(
  () => itemForm.itemType === 'QUESTION_BANK' || itemForm.itemType === 'QUIZ'
);
/** 普通管理员只能从本人机构选择直播讲师和参与范围。 */
const canSelectOrganization = computed(() => canManageTenantScope(userStore.userInfo));
const activeLiveOrganizationId = computed({
  get: () => (canSelectOrganization.value ? liveOrganizationId.value : currentTenantId(userStore.userInfo)),
  set: (organizationId: number | undefined) => {
    if (canSelectOrganization.value) liveOrganizationId.value = organizationId;
  }
});
/** 将后端可空参与范围转换为选择器使用的 undefined。 */
const liveAudienceId = computed({
  get: () => liveForm.audienceId ?? undefined,
  set: (audienceId: number | undefined) => {
    liveForm.audienceId = audienceId ?? null;
  }
});
/** 将直播时间范围映射回直播场次保存模型的开始、结束字段。 */
const liveTimeRange = computed({
  get: (): [string, string] => [liveForm.startTime, liveForm.endTime],
  set: (value: [string, string] | null) => {
    const [startTime = '', endTime = ''] = value ?? [];
    liveForm.startTime = startTime;
    liveForm.endTime = endTime;
  }
});

function itemTypeLabel(itemType: typeof itemForm.itemType): string {
  return { ASSET: '素材', LIVE: '直播', QUESTION_BANK: '题库练习', QUIZ: '课程测验' }[itemType];
}

function resetForms(): void {
  Object.assign(itemForm, {
    itemType: props.item?.itemType ?? 'ASSET',
    itemTitle: props.item?.itemTitle ?? '',
    assetId: props.item?.assetId ?? undefined,
    linkedQuizItemId: props.item?.linkedQuizItemId ?? null,
    questionBankId: props.item?.questionBankId ?? undefined,
    paperVersionId: props.item?.paperVersionId ?? undefined,
    isRequired: props.item?.isRequired ?? 1,
    isFinalAssessment: props.item?.isFinalAssessment ?? 0,
    passScore: props.item?.passScore ?? null,
    minimumStudySeconds: props.item?.minimumStudySeconds ?? 15,
    sortOrder: props.item?.sortOrder ?? props.defaultSortOrder,
    status: props.item?.status ?? 1,
    isLockedAfterParticipation: props.item?.isLockedAfterParticipation ?? 0,
    version: props.item?.version ?? 0
  });
  Object.assign(liveForm, {
    liveName: '',
    teacherUserId: 0,
    liveUrl: '',
    startTime: '',
    endTime: '',
    audienceId: null,
    liveStatus: 'DRAFT',
    status: 1,
    version: 0
  });
  selectedAsset.value = undefined;
  selectedTeacher.value = undefined;
  liveOrganizationId.value = undefined;
  hasLoadedLive.value = false;
}

/** 读取当前内容项关联的素材或直播场次。 */
async function loadItemDetails(): Promise<void> {
  resetForms();
  if (itemForm.itemType === 'QUESTION_BANK') await loadPublishedQuestionBanks();
  if (itemForm.itemType === 'QUIZ') await loadPublishedPaperVersions();
  if (itemForm.assetId) selectedAsset.value = await getAssetDetail(itemForm.assetId);
  if (props.item?.itemType === 'LIVE') {
    Object.assign(liveForm, await getLiveSession(props.item.courseItemId));
    hasLoadedLive.value = true;
  }
}

/** 课程题库练习仅可引用已发布且启用的题库。 */
async function loadPublishedQuestionBanks(): Promise<void> {
  publishedQuestionBanks.value = (await getQuestionBankPage({ pageNum: 1, pageSize: 100, 'query.publishedStatus': 'PUBLISHED', 'query.status': 1 })).list;
}

/** 课程测验必须明确绑定一条已发布试卷版本。 */
async function loadPublishedPaperVersions(): Promise<void> {
  const papers = (await getPaperPage({ pageNum: 1, pageSize: 100, 'query.hasPublishedVersion': true, 'query.status': 1 })).list;
  const versions = await Promise.all(papers.map(async (paper) => (await getPaperVersions(paper.paperId))
    .filter((version) => version.publishedStatus === 'PUBLISHED').sort((left, right) => right.versionNo - left.versionNo).slice(0, 1)
    .map((version) => ({ ...version, paperName: paper.paperName }))));
  publishedPaperVersions.value = versions.flat();
}

function openAssetPicker(): void {
  assetPickerVisible.value = true;
}

/** 直播只允许一个讲师；选择器返回多人时明确保留第一位。 */
function chooseTeacher(people: PersonPickerItem[]): void {
  const teacher = people[0];
  if (!teacher) return;
  if (people.length > 1) ElMessage.warning('直播场次仅支持一名讲师，已保留第一位。');
  selectedTeacher.value = teacher;
  liveForm.teacherUserId = teacher.userId;
  if (canSelectOrganization.value) liveOrganizationId.value = teacher.organizationId;
}

function chooseAsset(assets: AssetListItem[]): void {
  const asset = assets[0] as AssetDetail | undefined;
  selectedAsset.value = asset;
  itemForm.assetId = asset?.assetId;
  if (!itemForm.itemTitle && asset) itemForm.itemTitle = asset.assetName;
}

/** 新建时一次创建内容项；编辑时仅更新 Contract 允许变化的字段。 */
async function saveItem(): Promise<void> {
  if (!itemForm.itemTitle.trim()) {
    ElMessage.warning('请输入内容标题');
    return;
  }
  if (itemForm.itemType === 'ASSET' && !itemForm.assetId) {
    ElMessage.warning('请选择课程素材');
    return;
  }
  if (itemForm.itemType === 'ASSET' && itemForm.minimumStudySeconds <= 0) {
    ElMessage.warning('要求学习时长必须大于 0');
    return;
  }
  if (itemForm.itemType === 'QUESTION_BANK' && !itemForm.questionBankId) {
    ElMessage.warning('请选择已发布题库');
    return;
  }
  if (itemForm.itemType === 'QUIZ' && !itemForm.paperVersionId) {
    ElMessage.warning('请选择已发布试卷版本');
    return;
  }
  if (
    itemForm.itemType === 'LIVE' &&
    !isEditing.value &&
    (!liveForm.liveName || !liveForm.teacherUserId || !liveForm.liveUrl || !liveForm.startTime || !liveForm.endTime)
  ) {
    ElMessage.warning('请完整填写直播场次信息');
    return;
  }

  saving.value = true;
  try {
    let itemId = props.item?.courseItemId;
    if (itemId) {
      await updateCourseItem(itemId, {
        itemTitle: itemForm.itemTitle,
        assetId: itemForm.itemType === 'ASSET' ? itemForm.assetId : null,
        linkedQuizItemId: itemForm.itemType === 'ASSET' ? itemForm.linkedQuizItemId : null,
        questionBankId: itemForm.itemType === 'QUESTION_BANK' ? itemForm.questionBankId : null,
        paperVersionId: itemForm.itemType === 'QUIZ' ? itemForm.paperVersionId : null,
        isRequired: itemForm.isFinalAssessment ? 1 : itemForm.isRequired,
        isFinalAssessment: itemForm.itemType === 'QUIZ' ? itemForm.isFinalAssessment : 0,
        passScore: itemForm.itemType === 'QUIZ' && itemForm.isFinalAssessment ? itemForm.passScore : null,
        minimumStudySeconds: itemForm.itemType === 'ASSET' ? itemForm.minimumStudySeconds : 0,
        sortOrder: itemForm.sortOrder,
        status: itemForm.status,
        version: itemForm.version
      });
    } else {
      itemId = await createCourseItem({
        chapterId: props.chapterId,
        itemType: itemForm.itemType,
        itemTitle: itemForm.itemTitle,
        assetId: itemForm.itemType === 'ASSET' ? itemForm.assetId : null,
        linkedQuizItemId: itemForm.itemType === 'ASSET' ? itemForm.linkedQuizItemId : null,
        questionBankId: itemForm.itemType === 'QUESTION_BANK' ? itemForm.questionBankId : null,
        paperVersionId: itemForm.itemType === 'QUIZ' ? itemForm.paperVersionId : null,
        isRequired: itemForm.isFinalAssessment ? 1 : itemForm.isRequired,
        isFinalAssessment: itemForm.itemType === 'QUIZ' ? itemForm.isFinalAssessment : 0,
        passScore: itemForm.itemType === 'QUIZ' && itemForm.isFinalAssessment ? itemForm.passScore : null,
        minimumStudySeconds: itemForm.itemType === 'ASSET' ? itemForm.minimumStudySeconds : 0,
        sortOrder: itemForm.sortOrder,
        status: itemForm.status,
        isLockedAfterParticipation: supportsLockAfterParticipation.value ? itemForm.isLockedAfterParticipation : 0,
        ...(itemForm.itemType === 'LIVE'
          ? {
              liveSession: {
                liveName: liveForm.liveName,
                teacherUserId: liveForm.teacherUserId,
                liveUrl: liveForm.liveUrl,
                startTime: liveForm.startTime,
                endTime: liveForm.endTime,
                audienceId: liveForm.audienceId
              }
            }
          : {})
      });
    }
    ElMessage.success('课程内容已保存');
    emit('saved', itemId);
  } finally {
    saving.value = false;
  }
}

/** 复用浏览器剪贴板，不增加分享渠道。 */
async function copyLiveUrl(): Promise<void> {
  try { await globalThis.navigator.clipboard.writeText(liveForm.liveUrl); ElMessage.success('直播链接已复制'); }
  catch { ElMessage.warning('复制失败，请从链接输入框复制'); }
}

async function saveLive(): Promise<void> {
  if (!props.item) return;
  await updateLiveSession(props.item.courseItemId, { ...liveForm });
  ElMessage.success('直播场次已保存');
  await loadItemDetails();
  emit('saved', props.item.courseItemId);
}

watch(() => props.item?.courseItemId, loadItemDetails, { immediate: true });
watch(
  () => itemForm.itemType,
  (type) => {
    itemForm.minimumStudySeconds = type === 'ASSET'
      ? Math.max(itemForm.minimumStudySeconds, 15)
      : 0;
    // 参与后锁定仅对考核类内容项生效，切到其他类型时清空，避免提交无意义的锁定配置。
    if (type !== 'QUESTION_BANK' && type !== 'QUIZ') itemForm.isLockedAfterParticipation = 0;
    if (type === 'ASSET' && !isEditing.value) {
      itemForm.questionBankId = undefined;
      itemForm.paperVersionId = undefined;
    }
    if (type === 'QUESTION_BANK') {
      if (!isEditing.value) {
        itemForm.assetId = undefined;
        itemForm.paperVersionId = undefined;
      }
      void loadPublishedQuestionBanks();
    }
    if (type === 'QUIZ') {
      if (!isEditing.value) {
        itemForm.assetId = undefined;
        itemForm.questionBankId = undefined;
      }
      void loadPublishedPaperVersions();
    }
    if (type === 'LIVE' && !isEditing.value) {
      itemForm.assetId = undefined;
      itemForm.questionBankId = undefined;
      itemForm.paperVersionId = undefined;
    }
  }
);
</script>

<template>
  <aside class="course-item-inspector">
    <header class="inspector-header">
      <div>
        <h3>{{ panelTitle }}</h3>
        <!-- <p>{{ isEditing ? '在当前页面维护内容和学习规则' : '选择素材或填写直播信息后保存' }}</p> -->
      </div>
      <ElTag v-if="item" size="small">{{ itemTypeLabel(item.itemType) }}</ElTag>
    </header>

    <ElForm :model="itemForm" label-position="top" class="inspector-form">
      <ElFormItem label="内容类型">
        <ElRadioGroup v-model="itemForm.itemType" :disabled="isEditing">
          <ElRadio value="ASSET">素材</ElRadio>
          <ElRadio value="LIVE">直播</ElRadio>
          <ElRadio value="QUESTION_BANK">题库练习</ElRadio>
          <ElRadio value="QUIZ">课程测验</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem v-if="itemForm.itemType === 'QUESTION_BANK'" label="已发布题库">
        <ElSelect v-model="itemForm.questionBankId" placeholder="请选择已发布题库">
          <ElOption v-for="bank in publishedQuestionBanks" :key="bank.questionBankId" :label="bank.bankName" :value="bank.questionBankId" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem v-if="itemForm.itemType === 'QUIZ'" label="已发布试卷版本">
        <ElSelect v-model="itemForm.paperVersionId" placeholder="请选择已发布试卷版本">
          <ElOption v-for="version in publishedPaperVersions" :key="version.paperVersionId" :label="`${version.paperName}（V${version.versionNo}）`" :value="version.paperVersionId" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="内容标题">
        <ElInput v-model="itemForm.itemTitle" placeholder="请输入内容标题" />
      </ElFormItem>
      <ElFormItem v-if="itemForm.itemType === 'ASSET'" label="课程素材">
        <div class="asset-field">
          <div v-if="selectedAsset" class="selected-asset">
            <strong>{{ selectedAsset.assetName }}</strong>
            <span>{{ selectedAsset.assetType }} · ID {{ selectedAsset.assetId }}</span>
          </div>
          <ElButton @click="openAssetPicker">{{ selectedAsset ? '更换素材' : '选择素材' }}</ElButton>
        </div>
      </ElFormItem>
      <ElFormItem v-if="itemForm.itemType === 'ASSET'" label="课件试题">
        <ElSelect v-model="itemForm.linkedQuizItemId" clearable placeholder="请选择本课程测验，可不配置" @clear="itemForm.linkedQuizItemId = null">
          <ElOption v-for="quiz in quizItems?.filter(q => q.status === 1)" :key="quiz.courseItemId" :value="quiz.courseItemId" :label="quiz.itemTitle" />
        </ElSelect>
        <div class="field-hint">先创建课程测验，再关联到课件；多个课件可复用同一测验。</div>
      </ElFormItem>
      <ElFormItem v-if="itemForm.itemType === 'ASSET'" label="要求学习时长" class="study-duration-field">
        <DurationInput v-model="itemForm.minimumStudySeconds" />
        <div class="field-hint">累计有效学习达到该时长后完成</div>
      </ElFormItem>
      <ElFormItem label="排序" class="sort-field">
        <ElInputNumber v-model="itemForm.sortOrder" :min="0" controls-position="right" />
      </ElFormItem>
      <ElFormItem v-if="itemForm.itemType === 'QUIZ'" label="结业测评">
        <ElCheckbox v-model="itemForm.isFinalAssessment" :true-value="1" :false-value="0">学后测评，失败可补测一次</ElCheckbox>
      </ElFormItem>
      <ElFormItem v-if="itemForm.itemType === 'QUIZ' && itemForm.isFinalAssessment" label="及格线">
        <ElInputNumber v-model="itemForm.passScore" :min="0" :precision="2" placeholder="沿用试卷及格线" />
        <div class="field-hint">留空沿用试卷；结业测评固定为必修，每课程最多启用一个。</div>
      </ElFormItem>
      <div class="switch-row">
        <ElCheckbox v-model="itemForm.isRequired" :disabled="Boolean(itemForm.isFinalAssessment)" :true-value="1" :false-value="0">必修内容</ElCheckbox>
        <ElCheckbox v-model="itemForm.status" :true-value="1" :false-value="0">启用</ElCheckbox>
        <ElCheckbox v-if="supportsLockAfterParticipation" v-model="itemForm.isLockedAfterParticipation" :true-value="1" :false-value="0" :disabled="isEditing">参与后锁定</ElCheckbox>
      </div>

      <template v-if="itemForm.itemType === 'LIVE' && !isEditing">
        <ElDivider content-position="left">直播场次</ElDivider>
        <ElFormItem label="直播名称"><ElInput v-model="liveForm.liveName" placeholder="请输入直播名称" /></ElFormItem>
        <ElFormItem label="讲师">
          <div class="reference-field">
            <span>{{ selectedTeacher?.userName || (liveForm.teacherUserId ? '已配置讲师，请重新选择' : '暂未选择讲师') }}</span>
            <ElButton @click="teacherPickerVisible = true">选择讲师</ElButton>
          </div>
        </ElFormItem>
        <ElFormItem label="直播入口"><ElInput v-model="liveForm.liveUrl" placeholder="请输入直播链接" /><ElButton link @click="copyLiveUrl">复制链接</ElButton></ElFormItem>
        <ElFormItem label="直播时间">
          <ElDatePicker
            v-model="liveTimeRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </ElFormItem>
        <ElFormItem label="选择参与范围">
          <div class="reference-field reference-field--stacked">
            <OrganizationSelect v-if="canSelectOrganization" v-model="activeLiveOrganizationId" placeholder="先选择参与范围所属机构" />
            <ReferenceSelect v-model="liveAudienceId" kind="AUDIENCE" :organization-id="activeLiveOrganizationId" clearable />
          </div>
        </ElFormItem>
      </template>

      <div v-if="canEdit" class="admin-detail-action-bar">
        <ElButton v-if="!isEditing" @click="emit('cancelCreate')">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="saveItem">保存内容</ElButton>
      </div>
    </ElForm>

    <ElCollapse v-if="item?.itemType === 'LIVE' && hasLoadedLive" class="supporting-config" model-value="live">
      <ElCollapseItem name="live" title="直播场次">
        <ElForm :model="liveForm" label-position="top">
          <ElFormItem label="直播名称"><ElInput v-model="liveForm.liveName" /></ElFormItem>
          <ElFormItem label="讲师">
            <div class="reference-field">
              <span>{{ selectedTeacher?.userName || '已配置讲师，请重新选择' }}</span>
              <ElButton @click="teacherPickerVisible = true">选择讲师</ElButton>
            </div>
          </ElFormItem>
          <ElFormItem label="直播入口"><ElInput v-model="liveForm.liveUrl" placeholder="请输入直播链接" /><ElButton link @click="copyLiveUrl">复制链接</ElButton></ElFormItem>
          <ElFormItem label="直播时间">
            <ElDatePicker
              v-model="liveTimeRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
            />
          </ElFormItem>
          <ElFormItem label="选择参与范围">
            <div class="reference-field reference-field--stacked">
              <OrganizationSelect v-if="canSelectOrganization" v-model="activeLiveOrganizationId" placeholder="先选择参与范围所属机构" />
              <ReferenceSelect v-model="liveAudienceId" kind="AUDIENCE" :organization-id="activeLiveOrganizationId" clearable />
            </div>
          </ElFormItem>
          <ElFormItem label="直播状态">
            <ElSelect v-model="liveForm.liveStatus">
              <ElOption v-for="status in LIVE_STATUS_OPTIONS" :key="status.value" :label="status.label" :value="status.value" />
            </ElSelect>
          </ElFormItem>
          <ElCheckbox v-model="liveForm.status" :true-value="1" :false-value="0">启用直播场次</ElCheckbox>
          <div v-if="canEdit" class="primary-actions">
            <ElButton type="primary" @click="saveLive">保存直播场次</ElButton>
          </div>
        </ElForm>
      </ElCollapseItem>
    </ElCollapse>

    <AssetPickerDialog
      v-model="assetPickerVisible"
      title="选择课程素材"
      selection-mode="single"
      :selected-ids="itemForm.assetId ? [itemForm.assetId] : []"
      :allowed-types="['DOCUMENT', 'IMAGE', 'AUDIO', 'VIDEO', 'RICH_TEXT', 'LINK']"
      @confirm="chooseAsset"
    />
    <PersonPickerDialog
      v-model:visible="teacherPickerVisible"
      :organization-id="activeLiveOrganizationId"
      title="选择讲师"
      @confirm="chooseTeacher"
    />
  </aside>
</template>

<style scoped lang="less">
.course-item-inspector {
  min-width: 0;
  background: var(--color-bg-subtle);
}
.inspector-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
}
.inspector-header h3 {
  margin: 0;
}
.inspector-header p {
  margin: 5px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.inspector-form,
.supporting-config {
  padding: 18px;
  background: #fff;
}
.inspector-form :deep(.el-input-number),
.inspector-form :deep(.el-select),
.supporting-config :deep(.el-input-number),
.supporting-config :deep(.el-select) {
  width: 100%;
}
.asset-field {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.study-duration-field :deep(.el-form-item__content) {
  display: block;
}
.field-hint {
  margin-top: 6px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.sort-field :deep(.el-input-number) {
  width: 120px;
}

.reference-field {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.reference-field > span {
  overflow: hidden;
  color: var(--color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-field--stacked {
  align-items: stretch;
  flex-direction: column;
}
.selected-asset {
  min-width: 0;
}
.selected-asset strong,
.selected-asset span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-asset span {
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.switch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
}
.primary-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
.supporting-config {
  border-top: 1px solid var(--color-border);
}
</style>
