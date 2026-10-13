<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { bindExamSelection, getExamCandidates, getExamReviewers, replaceExamReviewers, type ExamCandidate } from '@/api/exam';
import type { PersonPickerItem } from '@/api/person-picker';
import type { PersonSelectionRequest } from '@/api/person-selection';
import { getDeptManagementTree, type SystemDept } from '@/api/system-dept';
import PersonSelectionDialog from '@/components/organization/PersonSelectionDialog.vue';
import { CANDIDATE_STATUS_OPTIONS, getExamEnumLabel, REVIEWER_ASSIGNMENT_SOURCE_OPTIONS } from '@/constants/exam-options';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';

const props = defineProps<{ examId: number; organizationId: number; examType: string; readOnly?: boolean }>();
const emit = defineEmits<{ saved: [candidateCount: number, reviewerCount: number]; next: [] }>();
const candidates = ref<ExamCandidate[]>([]);
/** 受关键字/状态筛选影响的分页总数，只驱动表格分页展示。 */
const candidatePageTotal = ref(0);
/** 不受列表筛选影响的已配置人数，用于发布步骤与保存回执。 */
const candidateTotal = ref(0);
const candidatePageNum = ref(1);
const candidatePageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const candidateStatus = ref('');
const learnerKeyword = ref('');
/** 考生名单加载态，避免绑定后核对时表格短暂空数据造成误解。 */
const candidateLoading = ref(false);
const selection = ref<PersonSelectionRequest>();
const selectedUsers = ref<PersonPickerItem[]>([]);
const selectedReviewers = ref<PersonPickerItem[]>([]);
/** 条件范围保存时由服务端展开，成功后回读具体名单。 */
const reviewerSelection = ref<PersonSelectionRequest>();
/** 统一中文单位，岗位与用户组不能误计为人数。 */
function rangeSummary(value?: PersonSelectionRequest): string {
  if (!value) return '尚未选择';
  if (value.selectionType === 'ALL') return '当前机构全部启用人员';
  const units = {DEPT:'个部门',USER:'名人员',POSITION:'个岗位',GROUP:'个用户组'};
  return `已选 ${value.subjectIds.length} ${units[value.selectionType]}`;
}
/** 批阅人接口返回的所属机构与配置来源，仅用于持久化后回显。 */
const reviewerDetails = ref<Record<number, { organizationName?: string; assignmentSource?: string }>>({});
const departments = ref<SystemDept[]>([]);
const selectionVisible = ref(false);
const reviewerVisible = ref(false);
const saving = ref(false);
/** 以服务端返回的实际绑定考生数决定是否允许进入发布步骤。 */
const configurationSaved = computed(() => candidateTotal.value > 0);
/** 题库练习与线下考试没有人工批阅任务，不展示也不保存批阅人。 */
const requiresReviewer = computed(() => props.examType === 'ONLINE' || props.examType === 'SPECIAL_PRACTICE');
/** 存在未提交的条件范围时给出提示，避免把「已选范围」误当成「已生效名单」。 */
const hasPendingChanges = computed(() => Boolean(selection.value) || Boolean(reviewerSelection.value));
/** 未选择范围时的说明：已发布锁定、已有名单但本次未调整、首次配置分别给不同话术。 */
const rangeEmptyHint = computed(() => {
  if (props.readOnly) return '已发布，范围规则已锁定，请查看下方实际考生名单。';
  if (candidateTotal.value > 0) return '本次未调整考生范围，下方为已生效名单；如需改范围请重新选择后保存。';
  return '尚未选择考生范围，保存后才会生成考生名单。';
});

/** 已配置的批阅人以人员对象保存，提交时再收敛为 Contract 所需的用户 ID。 */
const reviewerIds = computed(() => selectedReviewers.value.map((item) => item.userId));
const selectionSummary = computed(() => rangeSummary(selection.value));
const reviewerSelectionSummary = computed(() => reviewerSelection.value ? rangeSummary(reviewerSelection.value) : `已保存 ${selectedReviewers.value.length} 名评卷人`);

/** 将部门树中的已选节点转换为面向用户的名称回显。 */
function findDepartmentNames(nodes: SystemDept[], ids: number[], names: string[] = []): string[] {
  for (const node of nodes) {
    if (ids.includes(node.deptId)) names.push(node.deptName);
    if (node.children?.length) findDepartmentNames(node.children, ids, names);
  }
  return names;
}
const selectedDepartmentNames = computed(() =>
  selection.value?.selectionType === 'DEPT' ? findDepartmentNames(departments.value, selection.value.subjectIds) : []
);

/** 加载当前考试已绑定考生，用于人员配置后的即时核对。 */
async function loadCandidates(): Promise<void> {
  candidateLoading.value = true;
  try {
    const page = await getExamCandidates(props.examId, {
      pageNum: candidatePageNum.value,
      pageSize: candidatePageSize.value,
      ...(learnerKeyword.value ? { 'query.learnerKeyword': learnerKeyword.value } : {}),
      ...(candidateStatus.value ? { 'query.candidateStatus': candidateStatus.value } : {})
    });
    candidates.value = page.list;
    candidatePageTotal.value = page.total;
  } finally {
    candidateLoading.value = false;
  }
}

function queryCandidates(): void {
  candidatePageNum.value = 1;
  void loadCandidates();
}

/** 清空考生名单筛选条件并回到第一页，不影响已保存的人员配置。 */
function resetCandidateFilter(): void {
  learnerKeyword.value = '';
  candidateStatus.value = '';
  queryCandidates();
}

/** 工作台切换到本面板时，读取可回显的部门、批阅人和当前已绑定考生。 */
async function load(): Promise<void> {
  const [tree, reviewers] = await Promise.all([getDeptManagementTree({ status: 1 }), requiresReviewer.value ? getExamReviewers(props.examId) : Promise.resolve([])]);
  departments.value = tree;
  reviewerDetails.value = Object.fromEntries(
    reviewers.map((item) => [item.reviewerUserId, { organizationName: item.reviewerOrganizationName, assignmentSource: item.assignmentSource }])
  );
  selectedReviewers.value = reviewers.map((item) => ({
    userId: item.reviewerUserId,
    userCode: item.reviewerCode ?? String(item.reviewerUserId),
    userName: item.reviewerName ?? `用户${item.reviewerUserId}`,
    organizationId: item.reviewerOrganizationId,
    jobTitle: '',
    status: 1
  }));
  await loadCandidates();
  const all = await getExamCandidates(props.examId,{pageNum:1,pageSize:1});
  candidateTotal.value = all.total;
}

/** 返回 OpenAPI 已提供的批阅人所属机构，未保存的新选择不虚构部门或职务信息。 */
function getReviewerOrganizationName(userId: number): string {
  return reviewerDetails.value[userId]?.organizationName ?? '—';
}

/** 将批阅人配置来源转换为中文；未保存的本次选择显示为指定人员。 */
function getReviewerAssignmentSource(userId: number): string {
  return getExamEnumLabel(reviewerDetails.value[userId]?.assignmentSource ?? 'USER', REVIEWER_ASSIGNMENT_SOURCE_OPTIONS);
}

function selectCandidates(value: PersonSelectionRequest, users: PersonPickerItem[]): void {
  selection.value = value;
  selectedUsers.value = value.selectionType === 'USER' ? users : [];
}

/** 范围提交交由服务端展开，指定人员保留候选回显。 */
function selectReviewers(value: PersonSelectionRequest, users: PersonPickerItem[]): void {
  reviewerSelection.value = value;
  selectedReviewers.value = value.selectionType === 'USER' ? users : [];
}

/** 清空本次待保存的批阅人选择，保存时按 Contract 提交空数组。 */
function clearReviewers(): void {
  selectedReviewers.value = [];
  reviewerSelection.value = undefined;
  reviewerDetails.value = {};
}

/** 按当前选择整体绑定考生并替换批阅人；空数组表示清空批阅人。 */
async function save(): Promise<void> {
  saving.value = true;
  try {
    if (selection.value) {
      await bindExamSelection(props.examId, { selection: selection.value });
      // 已提交条件不在单改评卷时重新计算，实际考生以服务端快照为准。
      selection.value = undefined; selectedUsers.value = [];
    }
    if (requiresReviewer.value) {
      if (reviewerSelection.value) {
        // 条件范围交由服务端展开，不把岗位/用户组 ID 当作人员 ID 提交。
        await replaceExamReviewers(props.examId, { selection: reviewerSelection.value });
      } else {
        await replaceExamReviewers(props.examId, { reviewerUserIds: reviewerIds.value, assignmentSource: 'USER' });
      }
    }
    await load();
    reviewerSelection.value = undefined;
    emit('saved', candidateTotal.value, reviewerIds.value.length);
    ElMessage.success('人员配置已保存。');
  } finally {
    saving.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <ElCard shadow="never" class="assignment-panel admin-detail-page__card admin-detail-card-spacing">
    <div class="admin-detail-page__form-scroll">
      <!-- 配置概览：范围规则与已生效人数放在同一视线高度，便于保存前后对照。 -->
      <section class="ws-metrics assignment-overview">
        <div class="ws-metric">
          <span class="ws-metric__label">考生范围规则</span>
          <span class="ws-metric__value">{{ selectionSummary }}</span>
        </div>
        <div class="ws-metric ws-metric--accent">
          <span class="ws-metric__label">已绑定考生</span>
          <span class="ws-metric__value">{{ candidateTotal }}<em>人</em></span>
        </div>
        <div v-if="requiresReviewer" class="ws-metric">
          <span class="ws-metric__label">已配置批阅人</span>
          <span class="ws-metric__value">{{ selectedReviewers.length }}<em>人</em></span>
        </div>
      </section>

      <section class="ws-section assignment-section">
        <div class="ws-section__head">
          <div>
            <h2>考生范围</h2>
            <p>按部门、岗位、用户组或姓名选择考生，保存时由服务端展开为当前启用人员。</p>
          </div>
          <ElButton v-if="!readOnly" type="primary" plain @click="selectionVisible = true">选择考生范围</ElButton>
        </div>
        <!-- 规则回显收进同一行「标签 + 内容」，避免裸文本直接贴在卡片里。 -->
        <div class="assignment-range" :class="{ 'is-empty': !selection }">
          <span class="assignment-range__label">范围规则</span>
          <div class="assignment-range__body">
            <span v-if="!selection" class="assignment-range__hint">{{ rangeEmptyHint }}</span>
            <template v-else-if="selection.selectionType === 'ALL'">
              <span class="assignment-range__value">当前机构全部启用人员</span>
              <span class="assignment-range__hint">保存时展开为具体考生名单。</span>
            </template>
            <template v-else-if="selection.selectionType === 'DEPT' && selectedDepartmentNames.length">
              <ElSpace wrap>
                <ElTag v-for="name in selectedDepartmentNames" :key="name" size="small" effect="plain">{{ name }}</ElTag>
              </ElSpace>
              <span class="assignment-range__hint">包含所选部门及其下级部门，保存时展开为具体考生名单。</span>
            </template>
            <ElTable v-else-if="selection.selectionType === 'USER'" :data="selectedUsers" size="small" :max-height="240">
              <ElTableColumn prop="userCode" label="用户编码" width="160" show-overflow-tooltip />
              <ElTableColumn prop="userName" label="姓名" min-width="160" show-overflow-tooltip />
              <ElTableColumn prop="deptName" label="部门" min-width="220" show-overflow-tooltip />
              <template #empty><ElEmpty description="未选择具体人员" :image-size="60" /></template>
            </ElTable>
            <span v-else class="assignment-range__hint">{{ selectionSummary }}；保存时展开为具体考生名单。</span>
          </div>
        </div>
      </section>

      <section v-if="requiresReviewer" class="ws-section assignment-section">
        <div class="ws-section__head">
          <div>
            <h2>批阅人</h2>
            <p>按部门、岗位、用户组或姓名选择评卷人，须具有现有阅卷权限。</p>
          </div>
          <ElSpace v-if="!readOnly">
            <ElButton v-if="selectedReviewers.length || reviewerSelection" plain @click="clearReviewers">清空批阅人</ElButton>
            <ElButton type="primary" plain @click="reviewerVisible = true">选择批阅人</ElButton>
          </ElSpace>
        </div>
        <div class="assignment-range">
          <span class="assignment-range__label">配置来源</span>
          <div class="assignment-range__body">
            <span class="assignment-range__hint">{{ reviewerSelectionSummary }}</span>
          </div>
        </div>
        <ElTable :data="selectedReviewers" stripe :max-height="300" row-key="userId">
          <ElTableColumn type="index" label="序号" width="72" />
          <ElTableColumn prop="userCode" label="用户编码" width="160" show-overflow-tooltip />
          <ElTableColumn prop="userName" label="姓名" min-width="160" show-overflow-tooltip />
          <ElTableColumn label="所属机构" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ getReviewerOrganizationName(row.userId) }}</template>
          </ElTableColumn>
          <ElTableColumn label="配置来源" min-width="140">
            <template #default="{ row }">{{ getReviewerAssignmentSource(row.userId) }}</template>
          </ElTableColumn>
          <template #empty><ElEmpty description="尚未配置批阅人，保存前请选择评卷人" :image-size="60" /></template>
        </ElTable>
      </section>

      <section class="ws-section assignment-section">
        <div class="ws-section__head">
          <div>
            <h2>已绑定考生</h2>
            <p>保存人员配置后，此处显示当前考试实际绑定的考生名单与状态。</p>
          </div>
          <span class="ws-section__extra">共 {{ candidatePageTotal }} 人</span>
        </div>
        <ElForm inline class="candidate-filter">
          <ElFormItem label="学员"><ElInput v-model="learnerKeyword" clearable placeholder="姓名或账号" @keyup.enter="queryCandidates" /></ElFormItem>
          <ElFormItem label="考生状态">
            <ElSelect v-model="candidateStatus" clearable placeholder="全部状态" @change="queryCandidates">
              <ElOption v-for="item in CANDIDATE_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="queryCandidates">查询</ElButton>
            <ElButton @click="resetCandidateFilter">重置</ElButton>
          </ElFormItem>
        </ElForm>
        <ElTable v-loading="candidateLoading" :data="candidates" stripe :max-height="420">
          <ElTableColumn prop="learnerCode" label="学员账号" width="150" show-overflow-tooltip />
          <ElTableColumn prop="learnerName" label="学员姓名" min-width="140" show-overflow-tooltip />
          <ElTableColumn prop="deptName" label="部门" min-width="220" show-overflow-tooltip />
          <ElTableColumn label="考生状态" width="120">
            <template #default="{ row }">{{ getExamEnumLabel(row.candidateStatus, CANDIDATE_STATUS_OPTIONS) }}</template>
          </ElTableColumn>
          <template #empty><ElEmpty description="暂未绑定考生，先在上方选择考生范围并保存" :image-size="60" /></template>
        </ElTable>
        <div class="admin-business-page__pagination">
          <ElPagination
            v-model:current-page="candidatePageNum"
            v-model:page-size="candidatePageSize"
            :page-sizes="LIST_PAGE_SIZES"
            :total="candidatePageTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadCandidates"
            @size-change="queryCandidates"
          />
        </div>
      </section>
    </div>
    <footer v-if="!readOnly" class="admin-detail-action-bar assignment-action-bar">
      <span v-if="hasPendingChanges" class="assignment-action-bar__pending">有未保存的人员配置更改</span>
      <ElButton v-if="configurationSaved" @click="emit('next')">下一步：发布考试</ElButton>
      <ElButton type="primary" :loading="saving" @click="save">保存人员配置</ElButton>
    </footer>
    <PersonSelectionDialog
      v-model:visible="selectionVisible"
      title="选择考生"
      :model-value="selection"
      :preselected-users="selectedUsers"
      @confirm="selectCandidates"
    />
    <PersonSelectionDialog
      v-model:visible="reviewerVisible"
      title="选择考试评卷人范围"
      :model-value="reviewerSelection"
      :preselected-users="selectedReviewers"
      @confirm="selectReviewers"
    />
  </ElCard>
</template>

<style scoped lang="less">
.assignment-panel {
  min-width: 0;
}

.assignment-panel :deep(.el-card__body) {
  padding: var(--space-4);
}

/* 概览指标条与分组卡之间保持统一间距，卡片本身由公共样式定义。 */
.assignment-overview {
  margin-bottom: var(--space-4);
}

.assignment-section + .assignment-section {
  margin-top: var(--space-4);
}

/* 范围回显：标签列定宽，内容列吃满剩余宽度，长名单在容器内换行而不是撑破卡片。 */
.assignment-range {
  display: grid;
  min-width: 0;
  align-items: start;
  gap: 4px var(--space-3);
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
  grid-template-columns: 64px minmax(0, 1fr);
}

/* 未选择时弱化底纹，与已有内容的回显区分开。 */
.assignment-range.is-empty {
  border-style: dashed;
  background: transparent;
}

.assignment-range__label {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}

.assignment-range__body {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.assignment-range__value {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}

.assignment-range__hint {
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.candidate-filter {
  margin-bottom: 0;
}

.candidate-filter :deep(.el-input),
.candidate-filter :deep(.el-select) {
  width: 220px;
}

/* 保存是整张面板的最终动作：固定在底部操作条最右，脱离考生筛选行。 */
.assignment-action-bar__pending {
  display: inline-flex;
  margin-right: auto;
  align-items: center;
  gap: 6px;
  color: #d97706;
  font-size: 12px;
  line-height: 20px;
}

.assignment-action-bar__pending::before {
  content: '';
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  border-radius: 50%;
  background: currentcolor;
}

@media (max-width: 768px) {
  .assignment-panel :deep(.el-card__body) {
    padding: var(--space-3);
  }

  .ws-section__head {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
  }

  .assignment-range {
    grid-template-columns: minmax(0, 1fr);
  }

  .candidate-filter :deep(.el-input),
  .candidate-filter :deep(.el-select) {
    width: 100%;
  }
}
</style>
