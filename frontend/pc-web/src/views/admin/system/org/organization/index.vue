<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { sha256 } from '@shared/libs/sha256';
import {
  createOrganization,
  getOrganizationDetail,
  getOrganizationPage,
  updateOrganization,
  type Organization,
  type OrganizationCreatePayload,
  type OrganizationType
} from '@/api/organization';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { LOGIN_ROUTE_PATH } from '@/constants/routes';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const userStore = useUserStore();
/** 机构分类与契约枚举保持一致，监管平台租户不通过此页面开户。 */
const REGULATED_ORGANIZATION_TYPES = [
  { label: '企业', value: 'ENTERPRISE' },
  { label: '培训机构', value: 'TRAINING_INSTITUTION' }
] as const;
const canCreate = hasPermission(userStore.userInfo, 'system:tenant:add');
const canEdit = hasPermission(userStore.userInfo, 'system:tenant:edit');
const records = ref<Organization[]>([]);
const detail = ref<Organization>();
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const loading = ref(false);
const dialogVisible = ref(false);
const detailVisible = ref(false);
const formRef = ref<FormInstance>();
const editingId = ref<number>();
const filters = reactive<{ keyword: string; orgType?: OrganizationType; status?: 0 | 1 }>({ keyword: '' });
const INVISIBLE_UNICODE = /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}\p{Default_Ignorable_Code_Point}]/u;

/** 校验账号和密码中是否包含控制、格式或其他不可见 Unicode 字符。 */
const invisibleCharacterRule = {
  validator: (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
    if (typeof value === 'string' && INVISIBLE_UNICODE.test(value)) {
      callback(new Error('不能包含不可见字符'));
      return;
    }
    callback();
  },
  trigger: 'blur' as const
};

const form = reactive<OrganizationCreatePayload & { version?: number }>({
  orgCode: '',
  orgName: '',
  orgType: 'ENTERPRISE',
  status: 1,
  description: '',
  adminUserCode: 'admin',
  adminUserName: '管理员',
  adminPassword: ''
});
const rules: FormRules = {
  orgCode: [{ required: true, message: '请输入企业编码', trigger: 'blur' }],
  orgName: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
  orgType: [{ required: true, message: '请选择机构类型', trigger: 'change' }],
  adminUserCode: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }, invisibleCharacterRule],
  adminUserName: [{ required: true, message: '请输入管理员名称', trigger: 'blur' }],
  adminPassword: [{ required: true, message: '请输入管理员初始密码', trigger: 'blur' }, invisibleCharacterRule]
};

/** 加载机构分页。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const result = await getOrganizationPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      'query.keyword': filters.keyword || undefined,
      'query.orgType': filters.orgType,
      'query.status': filters.status
    });
    records.value = result.list;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

/** 打开机构开户表单。 */
function openCreate(): void {
  editingId.value = undefined;
  Object.assign(form, {
    orgCode: '',
    orgName: '',
    orgType: 'ENTERPRISE',
    status: 1,
    description: '',
    adminUserCode: 'admin',
    adminUserName: '管理员',
    adminPassword: ''
  });
  dialogVisible.value = true;
}

/** 打开机构编辑表单。 */
async function openEdit(organizationId: number): Promise<void> {
  const organization = await getOrganizationDetail(organizationId);
  editingId.value = organizationId;
  Object.assign(form, { ...organization, adminPassword: '', version: organization.version });
  dialogVisible.value = true;
}

/** 保存机构开户或编辑。 */
async function save(): Promise<void> {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  let createdId: number | undefined;
  if (editingId.value === undefined) {
    createdId = await createOrganization({ ...form, adminPassword: await sha256(form.adminPassword) });
  } else {
    await updateOrganization(editingId.value, {
      orgName: form.orgName,
      orgType: form.orgType === 'REGULATOR' ? undefined : form.orgType,
      contactUserId: form.contactUserId,
      status: form.status ?? 1,
      description: form.description,
      version: form.version ?? 0
    });
  }
  ElMessage.success(editingId.value === undefined ? '机构创建成功' : '机构更新成功');
  dialogVisible.value = false;
  await load();
  if (createdId !== undefined) await openDetail(createdId);
}

/** 机构编码只是登录入口标识；分享链接不包含账号、密码或令牌。 */
function loginLink(orgCode: string): string {
  const url = new globalThis.URL(LOGIN_ROUTE_PATH, globalThis.location.origin);
  url.searchParams.set('orgCode', orgCode);
  return url.toString();
}

/** 复制机构专属登录入口，复制不可用时仍可从详情手动复制。 */
async function copyLoginLink(orgCode: string): Promise<void> {
  try {
    await globalThis.navigator.clipboard.writeText(loginLink(orgCode));
    ElMessage.success('登录链接已复制');
  } catch {
    ElMessage.warning('复制失败，请在机构详情中手动复制链接');
  }
}

/** 查看机构详情。 */
async function openDetail(organizationId: number): Promise<void> {
  detail.value = await getOrganizationDetail(organizationId);
  detailVisible.value = true;
}

function typeLabel(type: OrganizationType): string {
  return type === 'REGULATOR' ? '监管平台' : type === 'TRAINING_INSTITUTION' ? '培训机构' : '企业';
}

onMounted(load);
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline>
        <ElFormItem label="关键词">
          <ElInput
            v-model="filters.keyword"
            clearable
            placeholder="企业编码或机构名称"
            @keyup.enter="
              pageNum = 1;
              load();
            "
          />
        </ElFormItem>
        <ElFormItem label="机构类型">
          <ElSelect v-model="filters.orgType" clearable placeholder="全部类型">
            <ElOption v-for="option in REGULATED_ORGANIZATION_TYPES" :key="option.value" :label="option.label" :value="option.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="filters.status" clearable placeholder="全部状态">
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
              Object.assign(filters, { keyword: '', orgType: undefined, status: undefined });
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
          <strong>机构列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canCreate" type="primary" @click="openCreate">新增机构</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="records">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="orgCode" label="企业编码" min-width="150" />
        <ElTableColumn prop="orgName" label="机构名称" min-width="180" />
        <ElTableColumn label="机构类型" min-width="120">
          <template #default="{ row }">{{ typeLabel(row.orgType) }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="260">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openDetail(row.organizationId)">详情</ElButton>
            <ElButton v-if="row.orgType !== 'REGULATOR'" link type="primary" @click="copyLoginLink(row.orgCode)">复制登录链接</ElButton>
            <ElButton v-if="canEdit && row.orgType !== 'REGULATOR'" link type="primary" @click="openEdit(row.organizationId)">编辑</ElButton>
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
    <ElDialog v-model="dialogVisible" :title="editingId === undefined ? '新增机构' : '编辑机构'" width="600px" :lock-scroll="false">
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="130px">
        <ElFormItem label="企业编码" prop="orgCode">
          <ElInput v-model="form.orgCode" :disabled="editingId !== undefined" placeholder="字母、数字、下划线或短横线" />
        </ElFormItem>
        <ElFormItem label="机构名称" prop="orgName">
          <ElInput v-model="form.orgName" placeholder="请输入机构名称" />
        </ElFormItem>
        <ElFormItem label="机构类型" prop="orgType">
          <ElSelect v-model="form.orgType" placeholder="请选择机构类型">
            <ElOption v-for="option in REGULATED_ORGANIZATION_TYPES" :key="option.value" :label="option.label" :value="option.value" />
          </ElSelect>
        </ElFormItem>
        <template v-if="editingId === undefined">
          <ElFormItem label="管理员账号" prop="adminUserCode"><ElInput v-model="form.adminUserCode" /></ElFormItem>
          <ElFormItem label="管理员名称" prop="adminUserName"><ElInput v-model="form.adminUserName" /></ElFormItem>
          <ElFormItem label="管理员初始密码" prop="adminPassword"><ElInput v-model="form.adminPassword" type="password" show-password placeholder="请输入初始密码" /></ElFormItem>
        </template>
        <ElFormItem label="状态">
          <ElRadioGroup v-model="form.status">
            <ElRadio :value="1">正常</ElRadio>
            <ElRadio :value="0">停用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="说明"><ElInput v-model="form.description" type="textarea" :rows="3" /></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="save">保存</ElButton>
      </template>
    </ElDialog>
    <ElDrawer v-model="detailVisible" title="机构详情" size="440px" :lock-scroll="false">
      <ElDescriptions v-if="detail" :column="1" border>
        <ElDescriptionsItem label="企业编码">{{ detail.orgCode }}</ElDescriptionsItem>
        <ElDescriptionsItem label="机构名称">{{ detail.orgName }}</ElDescriptionsItem>
        <ElDescriptionsItem v-if="detail.orgType !== 'REGULATOR'" label="专属登录链接">
          <ElInput :model-value="loginLink(detail.orgCode)" readonly />
          <ElButton link type="primary" @click="copyLoginLink(detail.orgCode)">复制链接</ElButton>
          <ElText v-if="detail.status === 0">机构已停用，暂不能登录。</ElText>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="机构类型">{{ typeLabel(detail.orgType) }}</ElDescriptionsItem>
        <ElDescriptionsItem label="状态">{{ detail.status === 1 ? '正常' : '停用' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="说明">{{ detail.description || '-' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="版本">{{ detail.version }}</ElDescriptionsItem>
      </ElDescriptions>
    </ElDrawer>
  </section>
</template>

<style scoped lang="less">
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
