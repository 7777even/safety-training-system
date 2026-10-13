<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import {
  createOrgPosition,
  deleteOrgPosition,
  getOrgPositionDetail,
  getOrgPositionPage,
  updateOrgPosition,
  type OrgPosition,
  type OrgPositionStatus
} from '@/api/org-position';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import OrganizationSelect from '@/components/organization/OrganizationSelect.vue';
import { useUserStore } from '@/stores';
import { canManageTenantScope, currentTenantId, currentTenantLabel } from '@/utils/tenant-context';

const userStore = useUserStore();
const canSelectTenant = canManageTenantScope(userStore.userInfo);
const filters = reactive({
  organizationId: undefined as number | undefined,
  positionCode: '',
  positionName: '',
  status: undefined as OrgPositionStatus | undefined
});
const records = ref<OrgPosition[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const loading = ref(false);
const visible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({
  positionId: undefined as number | undefined,
  organizationId: undefined as number | undefined,
  positionCode: '',
  positionName: '',
  description: '',
  status: 1 as OrgPositionStatus,
  version: undefined as number | undefined
});
const rules: FormRules = {
  organizationId: [{ required: true, message: '请选择所属机构', trigger: 'change' }],
  positionCode: [{ required: true, message: '请输入岗位编码', trigger: 'blur' }],
  positionName: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }]
};
function asRow(row: unknown): OrgPosition {
  return row as OrgPosition;
}
async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await getOrgPositionPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(canSelectTenant && filters.organizationId ? { 'query.organizationId': filters.organizationId } : {}),
      ...(filters.positionCode ? { 'query.positionCode': filters.positionCode } : {}),
      ...(filters.positionName ? { 'query.positionName': filters.positionName } : {}),
      ...(filters.status !== undefined ? { 'query.status': filters.status } : {})
    });
    records.value = data.list;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}
function create(): void {
  Object.assign(form, {
    positionId: undefined,
    organizationId: canSelectTenant ? (filters.organizationId ?? undefined) : currentTenantId(userStore.userInfo),
    positionCode: '',
    positionName: '',
    description: '',
    status: 1,
    version: undefined
  });
  visible.value = true;
}
async function edit(row: OrgPosition): Promise<void> {
  Object.assign(form, await getOrgPositionDetail(row.positionId));
  visible.value = true;
}
async function save(): Promise<void> {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  if (form.positionId === undefined)
    await createOrgPosition({
      organizationId: form.organizationId!,
      positionCode: form.positionCode,
      positionName: form.positionName,
      description: form.description || undefined,
      status: form.status
    });
  else if (form.version !== undefined)
    await updateOrgPosition(form.positionId, {
      positionCode: form.positionCode,
      positionName: form.positionName,
      description: form.description || undefined,
      status: form.status,
      version: form.version
    });
  ElMessage.success('保存成功');
  visible.value = false;
  await load();
}
async function remove(row: OrgPosition): Promise<void> {
  await ElMessageBox.confirm(`确认删除岗位“${row.positionName}”吗？`, '确认删除岗位', { type: 'warning' });
  await deleteOrgPosition(row.positionId);
  await load();
}
onMounted(load);
</script>
<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline :model="filters">
        <ElFormItem v-if="canSelectTenant" label="所属机构">
          <OrganizationSelect v-model="filters.organizationId" clearable placeholder="全部机构" />
        </ElFormItem>
        <ElFormItem label="岗位编码"><ElInput v-model="filters.positionCode" placeholder="请输入岗位编码" /></ElFormItem>
        <ElFormItem>
          <ElButton
            type="primary"
            @click="
              pageNum = 1;
              load();
            "
          >
            查询
          </ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>岗位列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton type="primary" @click="create">新增岗位</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="records">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <!-- 机构列展示机构名称，服务端未回填时回退到机构主键，不出现整列空白。 -->
        <ElTableColumn label="所属机构">
          <template #default="{ row }">{{ row.orgName || row.organizationId }}</template>
        </ElTableColumn>
        <ElTableColumn prop="positionCode" label="岗位编码" />
        <ElTableColumn prop="positionName" label="岗位名称" />
        <ElTableColumn prop="description" label="描述" />
        <ElTableColumn label="操作" width="110">
          <template #default="{ row }">
            <ElButton link type="primary" @click="edit(asRow(row))">编辑</ElButton>
            <ElButton link type="danger" @click="remove(asRow(row))">删除</ElButton>
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
          @current-change="load"
          @size-change="
            pageNum = 1;
            load();
          "
        />
      </div>
    </ElCard>
    <ElDialog v-model="visible" :title="form.positionId ? '编辑岗位' : '新增岗位'" width="600px" :lock-scroll="false">
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="100px">
        <ElFormItem label="所属机构" prop="organizationId">
          <OrganizationSelect v-if="canSelectTenant" v-model="form.organizationId" :disabled="Boolean(form.positionId)" />
          <ElText v-else>{{ currentTenantLabel(userStore.userInfo) }}</ElText>
        </ElFormItem>
        <ElFormItem label="岗位编码" prop="positionCode">
          <ElInput v-model="form.positionCode" placeholder="请输入岗位编码" />
        </ElFormItem>
        <ElFormItem label="岗位名称" prop="positionName">
          <ElInput v-model="form.positionName" placeholder="请输入岗位名称" />
        </ElFormItem>
        <ElFormItem label="描述">
          <ElInput v-model="form.description" type="textarea" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="visible = false">取消</ElButton>
        <ElButton type="primary" @click="save">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
<style scoped lang="less">
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
