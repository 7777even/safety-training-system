<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  deleteEvaluationForm,
  getEvaluationFormPage,
  getEvaluationFormVersions,
  type EvaluationForm,
  type EvaluationFormType
} from '@/api/evaluation-form';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const rows = ref<EvaluationForm[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const filters = reactive({
  formName: '',
  formType: undefined as EvaluationFormType | undefined,
  status: undefined as 0 | 1 | undefined
});

const FORM_TYPE_OPTIONS: Array<{ label: string; value: EvaluationFormType }> = [
  { label: '综合评价', value: 'COMPOSITE' },
  { label: '专家检查', value: 'EXPERT_INSPECTION' }
];

function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

function formTypeText(type: EvaluationFormType): string {
  return FORM_TYPE_OPTIONS.find((item) => item.value === type)?.label ?? type;
}

/** 按契约字段分页加载表单主体。 */
async function loadRows(): Promise<void> {
  loading.value = true;
  try {
    const data = await getEvaluationFormPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(filters.formName ? { 'query.formName': filters.formName } : {}),
      ...(filters.formType ? { 'query.formType': filters.formType } : {}),
      ...(filters.status !== undefined ? { 'query.status': filters.status } : {})
    });
    rows.value = data.list;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

function search(): void {
  pageNum.value = 1;
  void loadRows();
}

function resetFilters(): void {
  filters.formName = '';
  filters.formType = undefined;
  filters.status = undefined;
  search();
}

function openWorkspace(evaluationFormId?: number): void {
  void router.push(
    evaluationFormId
      ? `/admin/business-center/training/evaluation-form/workspace/${evaluationFormId}`
      : '/admin/business-center/training/evaluation-form/workspace/create'
  );
}

/** 先解析真实 formVersionId，再进入不可变版本详情。 */
async function openLatestVersion(evaluationFormId: number): Promise<void> {
  const versions = await getEvaluationFormVersions(evaluationFormId);
  if (!versions.length) {
    ElMessage.info('该表单尚未发布版本');
    return;
  }
  await router.push(`/admin/business-center/training/evaluation-form/version/${versions[0].formVersionId}`);
}

async function removeForm(row: EvaluationForm): Promise<void> {
  await ElMessageBox.confirm(`确认删除“${row.formName}”吗？已有发布版本的表单不可删除。`, '删除确认', {
    type: 'warning'
  });
  await deleteEvaluationForm(row.evaluationFormId);
  ElMessage.success('评价表单已删除');
  await loadRows();
}

onMounted(loadRows);
</script>

<template>
  <section class="admin-business-page evaluation-page">
    <ElCard shadow="never" class="admin-business-page__filters">
      <ElForm inline>
        <ElFormItem label="表单名称">
          <ElInput v-model="filters.formName" clearable placeholder="请输入表单名称" @keyup.enter="search" />
        </ElFormItem>
        <ElFormItem label="表单类型">
          <ElSelect v-model="filters.formType" clearable placeholder="全部类型" class="filter-select">
            <ElOption v-for="item in FORM_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="启用状态">
          <ElSelect v-model="filters.status" clearable placeholder="全部状态" class="filter-select">
            <ElOption label="正常" :value="1" />
            <ElOption label="禁用" :value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="search">查询</ElButton>
          <ElButton @click="resetFilters">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>评价表单列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canOperate('evaluation:form:add')" type="primary" @click="openWorkspace()">新建评价表单</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="rows">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="formCode" label="表单编码" min-width="160" />
        <ElTableColumn prop="formName" label="表单名称" min-width="220" />
        <ElTableColumn label="类型" width="120">
          <template #default="{ row }">{{ formTypeText(row.formType as EvaluationFormType) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="currentVersionNo" label="当前版本" width="100">
          <template #default="{ row }">{{ row.currentVersionNo ? `V${row.currentVersionNo}` : '未发布' }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '禁用' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <ElButton v-if="canOperate('evaluation:form:edit')" link type="primary" @click="openWorkspace(row.evaluationFormId)">编辑与发布</ElButton>
            <ElButton v-if="canOperate('evaluation:form:list')" link type="primary" @click="openLatestVersion(row.evaluationFormId)">
              查看版本
            </ElButton>
            <ElButton v-if="canOperate('evaluation:form:delete')" link type="danger" @click="removeForm(row as EvaluationForm)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadRows"
          @size-change="search"
        />
      </div>
    </ElCard>
  </section>
</template>

<style scoped lang="less">
.filter-select {
  width: 180px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-2);
}
</style>
