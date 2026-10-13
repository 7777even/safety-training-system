<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import AnnualFilesPanel from './AnnualFilesPanel.vue';
import type { TrainingPlan } from '@/api/training-plan';
import {
  getPlanApprovals,
  getPlanApprovers,
  submitPlanApproval,
  decidePlanApproval,
  getAnnualPlans,
  getLinkedAnnualPlan,
  bindAnnualPlan,
  getAnnualPlanFile,
  type PlanApproval,
  type PlanApprover,
  type AnnualPlan
} from '@/api/training-approval';
/** 年度依据及审批面板复用工作台、统一上传和当前RBAC。 */
const props = defineProps<{ plan: TrainingPlan }>();
const emit = defineEmits<{ changed: []; state: [pending: boolean, approved: boolean] }>();
const user = useUserStore();
const history = ref<PlanApproval[]>([]);
const approvers = ref<PlanApprover[]>([]);
const annuals = ref<AnnualPlan[]>([]);
const linked = ref<AnnualPlan | null>(null);
const approverId = ref<number>();
const annualId = ref<number>();
const busy = ref(false);
const loading = ref(true);
const error = ref('');
/** 最新记录必须与当前计划版本一致，旧批准仅作为历史。 */
const latest = computed(() => history.value[0]);
const pending = computed(() => latest.value?.approvalStatus === 'PENDING');
const approved = computed(() => latest.value?.approvalStatus === 'APPROVED' && latest.value.planVersion === props.plan.version);
const canEdit = computed(() => hasPermission(user.userInfo, 'training:plan:edit'));
const canAnnualList = computed(() => hasPermission(user.userInfo, 'training:annual:list'));
const canDecide = computed(
  () => hasPermission(user.userInfo, 'training:plan:approve') && latest.value?.approverUserId === user.userInfo?.userId && pending.value
);
let sequence = 0;
/** 请求乱序或读取失败时保持只读，避免错误展示允许发布。 */
async function load(): Promise<void> {
  const requestId = ++sequence;
  loading.value = true;
  error.value = '';
  emit('state', true, false);
  try {
    const [records, file, choices, people] = await Promise.all([
      getPlanApprovals(props.plan.trainingPlanId),
      getLinkedAnnualPlan(props.plan.trainingPlanId),
      canAnnualList.value ? getAnnualPlans() : Promise.resolve([]),
      canEdit.value ? getPlanApprovers() : Promise.resolve([])
    ]);
    if (requestId !== sequence) return;
    history.value = records;
    linked.value = file;
    annuals.value = choices;
    approvers.value = people;
    annualId.value = file?.annualPlanId;
    emit('state', pending.value, approved.value);
  } catch (e) {
    if (requestId === sequence) error.value = e instanceof Error ? e.message : '审批信息读取失败';
  } finally {
    if (requestId === sequence) loading.value = false;
  }
}
/** 每次操作提交当前服务端版本，成功后刷新父计划。 */
async function perform(action: () => Promise<void>): Promise<void> {
  busy.value = true;
  try {
    await action();
    ElMessage.success('操作成功');
    emit('changed');
    await load();
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e instanceof Error ? e.message : '操作失败');
  } finally {
    busy.value = false;
  }
}
async function submit(): Promise<void> {
  if (!approverId.value) {
    ElMessage.warning('请选择其他审批人');
    return;
  }
  await perform(() => submitPlanApproval(props.plan.trainingPlanId, props.plan.version, approverId.value!));
}
async function decide(allow: boolean): Promise<void> {
  try {
    const result = await ElMessageBox.prompt(allow ? '可填写批准意见' : '请填写驳回原因', allow ? '批准计划' : '驳回计划', {
      inputValidator: (value) => allow || Boolean(value?.trim()) || '驳回原因不能为空'
    });
    await perform(() => decidePlanApproval(props.plan.trainingPlanId, allow, result.value || ''));
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('审批操作失败');
  }
}
/** 业务端点返回原始Blob，使用浏览器下载能力。 */
async function download(): Promise<void> {
  if (!linked.value) return;
  try {
    const blob = await getAnnualPlanFile(props.plan.trainingPlanId, true);
    const url = globalThis.URL.createObjectURL(blob);
    const a = globalThis.document.createElement('a');
    a.href = url;
    a.download = linked.value.fileName;
    a.click();
    globalThis.URL.revokeObjectURL(url);
  } catch {
    ElMessage.error('年度文件下载失败，请刷新后重试');
  }
}
watch(
  () => [props.plan.trainingPlanId, props.plan.version],
  () => void load(),
  { immediate: true }
);
/** 审批状态枚举只在面板内映射，避免把后端原值直接渲染给用户。 */
const APPROVAL_STATUS_TEXT: Record<PlanApproval['approvalStatus'], string> = { PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回' };
function approvalStatusText(status: string): string {
  return APPROVAL_STATUS_TEXT[status as PlanApproval['approvalStatus']] ?? '未知状态';
}
function approvalStatusTagType(status: string): 'warning' | 'success' | 'danger' {
  return status === 'APPROVED' ? 'success' : status === 'REJECTED' ? 'danger' : 'warning';
}
</script>
<template>
  <ElCard v-loading="loading || busy" shadow="never" class="admin-detail-page__card approval-panel">
    <template #header>
      <div class="card-header">
        <div>
          <h2>年度依据与计划审批</h2>
          <p>关联公司年度文件，并提交当前版本审批。</p>
        </div>
        <ElTag :type="approved ? 'success' : pending ? 'warning' : 'info'" effect="plain" size="small">
          {{ approved ? '已批准' : pending ? '待审批' : '未提交' }}
        </ElTag>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll approval-panel__body">
      <ElAlert v-if="error" :title="error" type="error" :closable="false" />
      <ElButton v-if="error" @click="load">重新读取</ElButton>
      <template v-else>
        <ElAlert
          :title="
            pending
              ? '待审批：计划及参训名单已锁定'
              : approved
                ? '当前版本审批通过，可由有发布权限的人员下发'
                : props.plan.publishStatus === 'DRAFT'
                  ? '当前版本尚未批准，修改后须重新审批'
                  : '历史发布计划保持有效；审批记录单独保存'
          "
          :type="approved ? 'success' : 'info'"
          :closable="false"
          show-icon
        />
        <!-- 年度依据：窄栏里用“标签 + 值”的两行结构，避免长文件名撑破布局。 -->
        <section class="approval-panel__annual">
          <div>
            <span class="approval-panel__label">年度依据</span>
            <template v-if="linked">
              <strong>{{ linked.planYear }} · {{ linked.planName }}</strong>
              <span class="approval-panel__file">{{ linked.fileName }}</span>
            </template>
            <span v-else class="approval-panel__muted">尚未关联年度文件</span>
          </div>
          <ElButton v-if="linked" link type="primary" @click="download">下载文件</ElButton>
        </section>
        <div v-if="props.plan.publishStatus === 'DRAFT' && canEdit && canAnnualList && !pending" class="approval-panel__actions">
          <ElSelect v-model="annualId" clearable filterable placeholder="选择年度文件（可解除关联）">
            <ElOption v-for="item in annuals" :key="item.annualPlanId" :value="item.annualPlanId" :label="`${item.planYear} · ${item.planName}`" />
          </ElSelect>
          <ElButton @click="perform(() => bindAnnualPlan(props.plan.trainingPlanId, props.plan.version, annualId))">保存年度关联</ElButton>
        </div>
        <ElCollapse v-if="canAnnualList" class="approval-panel__collapse">
          <ElCollapseItem title="管理公司年度文件" name="annual"><AnnualFilesPanel @changed="load" /></ElCollapseItem>
        </ElCollapse>
        <div v-if="props.plan.publishStatus === 'DRAFT' && canEdit && !pending" class="approval-panel__actions">
          <ElSelect v-model="approverId" filterable placeholder="选择指定审批人">
            <ElOption v-for="person in approvers" :key="person.userId" :value="person.userId" :label="person.userName" />
          </ElSelect>
          <ElButton type="primary" :disabled="!approverId || approved" @click="submit">提交当前版本审批</ElButton>
        </div>
        <p v-if="props.plan.publishStatus === 'DRAFT' && canEdit && !pending && !approvers.length" class="approval-panel__hint">
          暂无其他有审批权限的人员，请管理员配置角色权限。
        </p>
        <div v-if="canDecide" class="approval-panel__actions">
          <ElButton type="success" @click="decide(true)">批准</ElButton>
          <ElButton type="danger" plain @click="decide(false)">驳回</ElButton>
        </div>
        <!-- 审批记录用列表承载：窄栏里不再出现横向滚动的六列表格。 -->
        <section class="approval-panel__history">
          <header class="approval-panel__section-head">
            <strong>审批记录</strong>
            <span>{{ history.length ? `共 ${history.length} 条` : '暂无审批记录' }}</span>
          </header>
          <ul v-if="history.length" class="plan-records">
            <li v-for="(row, index) in history" :key="`${row.planVersion}-${index}`" class="plan-record">
              <div class="plan-record__head">
                <strong>版本 V{{ row.planVersion }}</strong>
                <ElTag :type="approvalStatusTagType(row.approvalStatus)" effect="plain" size="small">{{ approvalStatusText(row.approvalStatus) }}</ElTag>
              </div>
              <div class="plan-record__meta">
                <span>提交人 {{ row.submitterUserId }}</span>
                <span>审批人 {{ row.approverUserId }}</span>
              </div>
              <p v-if="row.decisionComment" class="plan-record__comment">{{ row.decisionComment }}</p>
              <span class="plan-record__time">{{ row.createTime }}</span>
            </li>
          </ul>
          <ElEmpty v-else description="暂无审批记录" :image-size="56" />
        </section>
      </template>
    </div>
  </ElCard>
</template>
<style scoped lang="less">
.approval-panel__body {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: 10px;
  padding: 12px 0 16px;
}
.approval-panel__annual {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-subtle);
}
.approval-panel__annual > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}
.approval-panel__label {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.approval-panel__annual strong,
.approval-panel__file,
.approval-panel__muted {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.approval-panel__annual strong {
  color: var(--color-text-primary);
  font-size: 14px;
  line-height: 20px;
}
.approval-panel__file,
.approval-panel__muted {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.approval-panel__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
/* 窄栏里下拉独占一行，避免与按钮挤压换行参差。 */
.approval-panel__actions :deep(.el-select) {
  width: 100%;
}
.approval-panel__hint {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.approval-panel__collapse {
  border-top: 1px solid var(--color-border);
  border-bottom: 0;
}
.approval-panel__history {
  display: grid;
  gap: 8px;
}
.approval-panel__section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.approval-panel__section-head strong {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}
.approval-panel__section-head span {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
/* 审批/变更记录统一用同一种列表行，宽窄栏都不产生横向滚动。 */
.plan-records {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.plan-record {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-subtle);
}
.plan-record__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.plan-record__head strong {
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 18px;
}
.plan-record__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 12px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.plan-record__comment {
  margin: 0;
  color: var(--color-text-regular);
  font-size: 12px;
  line-height: 18px;
}
.plan-record__time {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
/* 侧栏里的空状态只需一句提示，收回 Element Plus 默认的 40px 上下留白。 */
.approval-panel__body :deep(.el-empty) {
  padding: 8px 0 2px;
}
</style>
