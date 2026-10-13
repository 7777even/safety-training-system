<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createOrgTag, deleteOrgTag, getOrgTagDetail, getOrgTagPage, updateOrgTag, type OrgTag, type OrgTagStatus } from '@/api/org-tag';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import OrganizationSelect from '@/components/organization/OrganizationSelect.vue';
import { useUserStore } from '@/stores';
import { canManageTenantScope, currentTenantId, currentTenantLabel } from '@/utils/tenant-context';

const userStore = useUserStore();
const canSelectTenant = canManageTenantScope(userStore.userInfo);
const filters = reactive({ organizationId: undefined as number | undefined, tagName: '', status: undefined as OrgTagStatus | undefined });
const records = ref<OrgTag[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const loading = ref(false);
const dialogVisible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({
  tagId: undefined as number | undefined,
  organizationId: undefined as number | undefined,
  tagName: '',
  description: '',
  status: 1 as OrgTagStatus,
  version: undefined as number | undefined
});
const rules: FormRules = {
  organizationId: [{ required: true, message: '请选择所属机构', trigger: 'change' }],
  tagName: [{ required: true, message: '请输入标签名称', trigger: 'blur' }]
};
/** Element Plus 表格插槽未保留行泛型，在边界处收窄为标签记录。 */
function asOrgTag(row: unknown): OrgTag {
  return row as OrgTag;
}
/** 加载当前筛选条件下的标签分页。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await getOrgTagPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(canSelectTenant && filters.organizationId ? { 'query.organizationId': filters.organizationId } : {}),
      ...(filters.tagName ? { 'query.tagName': filters.tagName } : {}),
      ...(filters.status !== undefined ? { 'query.status': filters.status } : {})
    });
    records.value = data.list;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}
/** 打开新增表单。 */
function create(): void {
  Object.assign(form, {
    tagId: undefined,
    organizationId: canSelectTenant ? (filters.organizationId ?? undefined) : currentTenantId(userStore.userInfo),
    tagName: '',
    description: '',
    status: 1,
    version: undefined
  });
  dialogVisible.value = true;
}
/** 读取详情后编辑，机构归属不可修改。 */
async function edit(row: OrgTag): Promise<void> {
  const data = await getOrgTagDetail(row.tagId);
  Object.assign(form, data);
  dialogVisible.value = true;
}
/** 提交新增或携带版本更新。 */
async function save(): Promise<void> {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  if (form.tagId === undefined)
    await createOrgTag({
      organizationId: form.organizationId!,
      tagName: form.tagName,
      description: form.description || undefined,
      status: form.status
    });
  else if (form.version !== undefined)
    await updateOrgTag(form.tagId, {
      tagName: form.tagName,
      description: form.description || undefined,
      status: form.status,
      version: form.version
    });
  ElMessage.success('保存成功');
  dialogVisible.value = false;
  await load();
}
/** 二次确认后删除标签。 */
async function remove(row: OrgTag): Promise<void> {
  await ElMessageBox.confirm(`确认删除标签“${row.tagName}”吗？`, '确认删除', { type: 'warning' });
  await deleteOrgTag(row.tagId);
  ElMessage.success('标签已删除');
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
        <ElFormItem label="标签名称"><ElInput v-model="filters.tagName" placeholder="请输入标签名称" clearable /></ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="filters.status" placeholder="全部状态" clearable>
            <ElOption label="正常" :value="1" />
            <ElOption label="停用" :value="0" />
          </ElSelect>
        </ElFormItem>
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
          <ElButton
            @click="
              Object.assign(filters, { organizationId: undefined, tagName: '', status: undefined });
              pageNum = 1;
              load();
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
          <strong>标签列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton type="primary" @click="create">新增标签</ElButton>
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
        <ElTableColumn label="所属机构" width="200">
          <template #default="{ row }">{{ row.orgName || row.organizationId }}</template>
        </ElTableColumn>
        <ElTableColumn prop="tagName" label="标签名称" min-width="160" />
        <ElTableColumn prop="description" label="描述" min-width="200" />
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="140">
          <template #default="{ row }">
            <ElButton link type="primary" @click="edit(asOrgTag(row))">编辑</ElButton>
            <ElButton link type="danger" @click="remove(asOrgTag(row))">删除</ElButton>
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
    <ElDialog v-model="dialogVisible" :title="form.tagId === undefined ? '新增标签' : '编辑标签'" width="600px" :lock-scroll="false">
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="96px">
        <ElFormItem label="所属机构" prop="organizationId">
          <OrganizationSelect v-if="canSelectTenant" v-model="form.organizationId" :disabled="form.tagId !== undefined" />
          <ElText v-else>{{ currentTenantLabel(userStore.userInfo) }}</ElText>
        </ElFormItem>
        <ElFormItem label="标签名称" prop="tagName"><ElInput v-model="form.tagName" placeholder="请输入标签名称" /></ElFormItem>
        <ElFormItem label="描述"><ElInput v-model="form.description" type="textarea" placeholder="请输入标签描述" /></ElFormItem>
        <ElFormItem label="状态">
          <ElRadioGroup v-model="form.status">
            <ElRadio :value="1">正常</ElRadio>
            <ElRadio :value="0">停用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
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
