<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { Histogram, TrendCharts } from '@element-plus/icons-vue';
import { deleteTrainingPlan, getTrainingPlanPage, type TrainingNature, type TrainingPlan, type TrainingPlanPublishStatus } from '@/api/training-plan';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const rows = ref<TrainingPlan[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const filters = reactive({
  planName: '',
  trainingNature: undefined as TrainingNature | undefined,
  publishStatus: undefined as TrainingPlanPublishStatus | undefined
});

/** 把契约枚举转换为用户可读文案；历史值不允许作为新建选项。 */
function natureText(nature: TrainingNature): string {
  if (nature === 'INITIAL') return '初培';
  if (nature === 'REVIEW') return '复审';
  return '历史未标明';
}

/** 计划状态转换为中文，避免直接暴露接口枚举。 */
function statusText(status: TrainingPlanPublishStatus): string {
  return { DRAFT: '草稿', PUBLISHED: '已发布', RUNNING: '执行中', ENDED: '已结束', CANCELLED: '已取消', ARCHIVED: '已归档' }[status] ?? status;
}

/** 保持后端排期的本地日期时间语义，列表仅显示到分钟。 */
function formatPlanDateTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

/** 起止时间缺失时不拼接残缺的时间范围。 */
function formatPlanTimeRange(startTime?: string, endTime?: string): string {
  if (!startTime || !endTime) return '-';
  return `${formatPlanDateTime(startTime)} 至 ${formatPlanDateTime(endTime)}`;
}

/** 地点为空时使用统一占位，避免将空白单元格误解为加载失败。 */
function formatPlanLocation(location?: string): string {
  return location?.trim() || '-';
}

function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

async function loadRows(): Promise<void> {
  loading.value = true;
  try {
    const page = await getTrainingPlanPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(filters.planName ? { 'query.planName': filters.planName } : {}),
      ...(filters.trainingNature ? { 'query.trainingNature': filters.trainingNature } : {}),
      ...(filters.publishStatus ? { 'query.publishStatus': filters.publishStatus } : {})
    });
    rows.value = page.list;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

async function removePlan(plan: TrainingPlan): Promise<void> {
  await ElMessageBox.confirm(`确认删除培训计划“${plan.planName}”吗？`, '删除确认', { type: 'warning' });
  await deleteTrainingPlan(plan.trainingPlanId);
  await loadRows();
}

function openWorkspace(trainingPlanId?: number): void {
  void router.push(trainingPlanId ? `/admin/business-center/training/plan/${trainingPlanId}` : '/admin/business-center/training/plan/create');
}

/** 收敛表格插槽的宽泛行类型。 */
function asPlan(row: unknown): TrainingPlan {
  return row as TrainingPlan;
}

onMounted(loadRows);
</script>

<template>
  <section class="admin-business-page training-plan-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline @submit.prevent>
        <ElFormItem label="计划名称"><ElInput v-model="filters.planName" clearable placeholder="请输入计划名称" /></ElFormItem>
        <ElFormItem label="培训性质">
          <ElSelect v-model="filters.trainingNature" clearable placeholder="请选择培训性质">
            <ElOption label="初培" value="INITIAL" />
            <ElOption label="复审" value="REVIEW" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="计划状态">
          <ElSelect v-model="filters.publishStatus" clearable placeholder="请选择计划状态">
            <ElOption label="草稿" value="DRAFT" />
            <ElOption label="已发布" value="PUBLISHED" />
            <ElOption label="执行中" value="RUNNING" />
            <ElOption label="已结束" value="ENDED" />
            <ElOption label="已取消" value="CANCELLED" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton
            type="primary"
            @click="
              pageNum = 1;
              loadRows();
            "
          >
            查询
          </ElButton>
          <ElButton
            @click="
              filters.planName = '';
              filters.trainingNature = undefined;
              filters.publishStatus = undefined;
              loadRows();
            "
          >
            重置
          </ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>培训计划列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton
              v-if="canOperate('training:plan:list')"
              :icon="Histogram"
              @click="router.push('/admin/business-center/training/plan/class-statistics')"
            >
              班级统计
            </ElButton>
            <ElButton
              v-if="canOperate('training:result:list') && canOperate('training:plan:list')"
              :icon="TrendCharts"
              @click="router.push('/admin/business-center/training/plan/statistics')"
            >
              培训统计与记录
            </ElButton>
            <ElButton v-if="canOperate('training:plan:add')" type="primary" @click="openWorkspace()">新增计划</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="rows" row-key="trainingPlanId">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="planCode" label="计划编码" min-width="130" />
        <ElTableColumn prop="planName" label="计划名称" min-width="180" />
        <ElTableColumn label="培训时间" min-width="250">
          <template #default="{ row }">{{ formatPlanTimeRange(row.startTime, row.endTime) }}</template>
        </ElTableColumn>
        <ElTableColumn label="培训地点" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ formatPlanLocation(row.location) }}</template>
        </ElTableColumn>
        <ElTableColumn label="培训性质" width="110">
          <template #default="{ row }">{{ natureText(row.trainingNature) }}</template>
        </ElTableColumn>
        <ElTableColumn label="适用最低学时" width="130" align="center">
          <template #default="{ row }">{{ row.applicableRequiredHours ?? '发布后计算' }}</template>
        </ElTableColumn>
        <ElTableColumn label="计划状态" width="110">
          <template #default="{ row }">
            <ElTag>{{ statusText(row.publishStatus) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="创建时间" width="150">
          <template #default="{ row }">{{ formatPlanDateTime(row.createTime) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <ElButton v-if="canOperate('training:plan:list')" link type="primary" @click="openWorkspace(row.trainingPlanId)">查看</ElButton>
            <ElButton v-if="row.publishStatus === 'DRAFT' && canOperate('training:plan:delete')" link type="danger" @click="removePlan(asPlan(row))">
              删除
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty><ElEmpty description="暂无培训计划" /></template>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadRows"
        />
      </div>
    </ElCard>
  </section>
</template>
