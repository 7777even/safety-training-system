<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  deleteTrainingStandard,
  getTrainingStandardPage,
  publishTrainingStandard,
  type TrainingStandardPublishStatus
} from '@/api/training-standard';
import { getTrainingTypePage, type TrainingType } from '@/api/training-type';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

/** 培训标准列表行，保持与 TrainingStandardVO 一致。 */
interface StandardRow {
  trainingStandardId: number;
  standardCode: string;
  standardName: string;
  standardVersion: string;
  publishStatus: TrainingStandardPublishStatus;
  status: 0 | 1;
  version: number;
}

/** 将标准发布状态转换为中文。 */
function publishStatusText(status: StandardRow['publishStatus']): string {
  if (status === 'PUBLISHED') return '已发布';
  if (status === 'ARCHIVED') return '已归档';
  return '草稿';
}

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const rows = ref<StandardRow[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const trainingTypeOptions = ref<TrainingType[]>([]);
const filters = reactive({
  trainingTypeId: undefined as number | undefined,
  standardName: '',
  publishStatus: undefined as TrainingStandardPublishStatus | undefined,
  status: undefined as 0 | 1 | undefined
});

/** 加载当前机构下可用的培训类型供筛选选择。 */
async function loadTrainingTypes(): Promise<void> {
  const data = await getTrainingTypePage({
    pageNum: 1,
    pageSize: 100,
    'query.status': 1
  });
  trainingTypeOptions.value = data.list;
}

/** 按后端权限集合控制入口显示。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

/** 收敛 Element Plus 表格插槽的宽泛行类型。 */
function asStandardRow(row: unknown): StandardRow {
  return row as StandardRow;
}

/** 查询培训标准分页列表。 */
async function loadRows(): Promise<void> {
  loading.value = true;
  try {
    const data = await getTrainingStandardPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(filters.trainingTypeId ? { 'query.trainingTypeId': filters.trainingTypeId } : {}),
      ...(filters.standardName ? { 'query.standardName': filters.standardName } : {}),
      ...(filters.publishStatus ? { 'query.publishStatus': filters.publishStatus } : {}),
      ...(filters.status !== undefined ? { 'query.status': filters.status } : {})
    });
    rows.value = data.list as StandardRow[];
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

/** 跳转到独立标准工作台，避免复杂规则散落在弹窗中。 */
function openWorkspace(trainingStandardId?: number): void {
  void router.push(
    trainingStandardId ? `/admin/business-center/training/standard/${trainingStandardId}` : '/admin/business-center/training/standard/create'
  );
}

/** 发布前进行明确确认并携带乐观锁版本。 */
async function publishRow(row: StandardRow): Promise<void> {
  await ElMessageBox.confirm(`发布后“${row.standardName}”的规则将只读，确认继续吗？`, '发布确认', {
    type: 'warning'
  });
  await publishTrainingStandard(row.trainingStandardId, row.version);
  ElMessage.success('培训标准已发布');
  await loadRows();
}

/** 删除草稿标准。 */
async function removeRow(row: StandardRow): Promise<void> {
  await ElMessageBox.confirm(`确认删除“${row.standardName}”吗？`, '删除确认', { type: 'warning' });
  await deleteTrainingStandard(row.trainingStandardId);
  ElMessage.success('培训标准已删除');
  await loadRows();
}

onMounted(async () => {
  await Promise.all([loadRows(), loadTrainingTypes()]);
});
</script>

<template>
  <section class="admin-business-page standard-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline @submit.prevent>
        <ElFormItem label="培训类型">
          <ElSelect v-model="filters.trainingTypeId" clearable filterable placeholder="请选择培训类型">
            <ElOption v-for="item in trainingTypeOptions" :key="item.trainingTypeId" :label="item.typeName" :value="item.trainingTypeId" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="标准名称">
          <ElInput v-model="filters.standardName" clearable placeholder="请输入标准名称" />
        </ElFormItem>
        <ElFormItem label="发布状态">
          <ElSelect v-model="filters.publishStatus" clearable placeholder="请选择发布状态">
            <ElOption label="草稿" value="DRAFT" />
            <ElOption label="已发布" value="PUBLISHED" />
            <ElOption label="已归档" value="ARCHIVED" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="启用状态">
          <ElSelect v-model="filters.status" clearable placeholder="请选择启用状态">
            <ElOption label="启用" :value="1" />
            <ElOption label="停用" :value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem class="filter-actions">
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
              filters.trainingTypeId = undefined;
              filters.standardName = '';
              filters.publishStatus = undefined;
              filters.status = undefined;
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
          <strong>培训标准列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canOperate('training:standard:add')" type="primary" @click="openWorkspace()">新增标准</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="rows" row-key="trainingStandardId">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="standardCode" label="标准编码" min-width="140" />
        <ElTableColumn prop="standardName" label="标准名称" min-width="220" />
        <ElTableColumn prop="standardVersion" label="标准版本" width="120" />
        <ElTableColumn label="发布状态" width="110">
          <template #default="{ row }">
            <ElTag :type="row.publishStatus === 'PUBLISHED' ? 'success' : row.publishStatus === 'ARCHIVED' ? 'info' : 'warning'">
              {{ publishStatusText(row.publishStatus) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="启用状态" width="100">
          <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <ElButton
              v-if="canOperate(row.publishStatus === 'DRAFT' ? 'training:standard:edit' : 'training:standard:list')"
              link
              type="primary"
              @click="openWorkspace(row.trainingStandardId)"
            >
              {{ row.publishStatus === 'DRAFT' ? '编辑' : '查看' }}
            </ElButton>
            <ElButton
              v-if="row.publishStatus === 'DRAFT' && canOperate('training:standard:publish')"
              link
              type="success"
              @click="publishRow(asStandardRow(row))"
            >
              发布
            </ElButton>
            <ElButton
              v-if="row.publishStatus === 'DRAFT' && canOperate('training:standard:delete')"
              link
              type="danger"
              @click="removeRow(asStandardRow(row))"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty><ElEmpty description="暂无培训标准" /></template>
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

<style scoped lang="less">
.pagination {
  justify-content: flex-end;
  margin-top: var(--space-2);
}
</style>
