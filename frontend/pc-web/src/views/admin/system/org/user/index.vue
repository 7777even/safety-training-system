<script setup lang="ts">
import { sha256 } from '@shared/libs/sha256';
import { onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ElIcon } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import {
  createUser,
  deleteUser,
  exportUsers,
  getDeptTree,
  getRoleOptions,
  getUserPage,
  importUsers,
  resetUserPassword,
  updateUser,
  type DeptTreeNode,
  type MemberType,
  type RoleOption,
  type SystemStatus,
  type SystemUser,
  type UserImportResult,
  type UserListQuery
} from '@/api/system-user';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import PersonProfileDialog from '@/components/organization/PersonProfileDialog.vue';
import { hasPermission } from '@/utils/permission';
import { getOrgPositionPage, type OrgPosition } from '@/api/org-position';
import { getOrgTagPage, type OrgTag } from '@/api/org-tag';

const userStore = useUserStore();

/** 判断当前用户是否拥有指定的用户管理操作权限。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

/** 用户列表筛选条件。 */
const filters = reactive({
  userCode: '',
  userName: '',
  phone: '',
  status: undefined as SystemStatus | undefined,
  deptId: undefined as number | undefined
});

/** 用户表格与分页状态。 */
const userList = ref<SystemUser[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const loading = ref(false);

/** 部门与角色选项来自已确认的系统管理接口。 */
const deptTree = ref<DeptTreeNode[]>([]);
const roleOptions = ref<RoleOption[]>([]);
const tagOptions = ref<OrgTag[]>([]);
const positionOptions = ref<OrgPosition[]>([]);
const treeSelectProps = { value: 'deptId', label: 'deptName', children: 'children' };

/** 用户新增和编辑共用的表单模型。 */
interface UserFormModel {
  userId?: number;
  userCode: string;
  userName: string;
  phone: string;
  email: string;
  jobTitle: string;
  entryDate: string;
  memberType: MemberType;
  tagIds: number[];
  positionIds: number[];
  primaryPositionId?: number;
  deptId?: number;
  password: string;
  status: SystemStatus;
  roleIds: number[];
  version?: number;
}

const userDialogVisible = ref(false);
const userFormRef = ref<FormInstance>();
const userForm = reactive<UserFormModel>(createUserForm());
const INVISIBLE_UNICODE = /[\p{Cc}\p{Cf}\p{Cs}\p{Zl}\p{Zp}\p{Default_Ignorable_Code_Point}]/u;

/** 校验用户编码和密码中是否包含控制、格式或其他不可见 Unicode 字符。 */
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

const userFormRules: FormRules<UserFormModel> = {
  userCode: [{ required: true, message: '请输入用户编码', trigger: 'blur' }, invisibleCharacterRule],
  userName: [{ required: true, message: '请输入用户名称', trigger: 'blur' }],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }, invisibleCharacterRule]
};

/** 重置密码表单单独维护，避免在用户编辑时误提交密码字段。 */
const resetDialogVisible = ref(false);
const resetFormRef = ref<FormInstance>();
const resetForm = reactive({ userId: 0, userName: '', password: '' });
const resetFormRules = { password: [{ required: true, message: '请输入新密码', trigger: 'blur' }, invisibleCharacterRule] };

/** 新建默认表单，状态默认使用 Contract 约定的正常值。 */
function createUserForm(): UserFormModel {
  return {
    userId: undefined,
    userCode: '',
    userName: '',
    phone: '',
    email: '',
    jobTitle: '',
    entryDate: '',
    memberType: 'INTERNAL',
    tagIds: [],
    positionIds: [],
    primaryPositionId: undefined,
    deptId: undefined,
    password: '',
    status: 1,
    roleIds: []
  };
}

/** Element Plus 表格插槽不保留泛型行类型，在边界处收窄为已绑定的用户列表记录。 */
function asSystemUser(row: unknown): SystemUser {
  return row as SystemUser;
}

/** 组装 Contract 规定的 query.* 分页筛选参数。 */
function buildUserQuery(): UserListQuery {
  return {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    ...(filters.userCode ? { 'query.userCode': filters.userCode } : {}),
    ...(filters.userName ? { 'query.userName': filters.userName } : {}),
    ...(filters.phone ? { 'query.phone': filters.phone } : {}),
    ...(filters.status !== undefined ? { 'query.status': filters.status } : {}),
    ...(filters.deptId !== undefined ? { 'query.deptId': filters.deptId } : {})
  };
}

/** 查询当前筛选条件下的用户分页数据。 */
async function loadUsers(): Promise<void> {
  loading.value = true;

  try {
    const data = await getUserPage(buildUserQuery());
    userList.value = data.list;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

/** 加载新增和编辑用户所需的部门、角色选项。 */
async function loadFormOptions(): Promise<void> {
  const [depts, roles, tags, positions] = await Promise.all([
    getDeptTree(),
    getRoleOptions(),
    getOrgTagPage({ pageNum: 1, pageSize: 100 }),
    getOrgPositionPage({ pageNum: 1, pageSize: 100 })
  ]);
  deptTree.value = depts;
  roleOptions.value = roles;
  tagOptions.value = tags.list;
  positionOptions.value = positions.list;
}

/** 按筛选条件重新从首页查询。 */
async function searchUsers(): Promise<void> {
  pageNum.value = 1;
  await loadUsers();
}

/** 清空筛选条件并恢复默认分页。 */
async function resetFilters(): Promise<void> {
  Object.assign(filters, { userCode: '', userName: '', phone: '', status: undefined, deptId: undefined });
  await searchUsers();
}

/** 打开新增用户表单。 */
function openCreateDialog(): void {
  Object.assign(userForm, createUserForm());
  userDialogVisible.value = true;
}

/** 打开编辑用户表单，并保留服务端返回的乐观锁版本号。 */
function openEditDialog(user: SystemUser): void {
  Object.assign(userForm, {
    userId: user.userId,
    userCode: user.userCode,
    userName: user.userName,
    phone: user.phone ?? '',
    email: user.email ?? '',
    jobTitle: user.jobTitle ?? '',
    entryDate: user.entryDate ?? '',
    memberType: user.memberType ?? 'INTERNAL',
    tagIds: user.tagIds ?? [],
    positionIds: user.positionIds ?? [],
    primaryPositionId: user.primaryPositionId,
    deptId: user.deptId ?? undefined,
    password: '',
    status: user.status,
    roleIds: user.roleIds ?? [],
    version: user.version
  });
  userDialogVisible.value = true;
}

/** 校验并提交新增或编辑请求。 */
async function saveUser(): Promise<void> {
  const isValid = await userFormRef.value?.validate().catch(() => false);

  if (!isValid || userForm.deptId === undefined) {
    return;
  }

  const memberFields = {
    memberType: userForm.memberType,
    email: userForm.email || undefined,
    jobTitle: userForm.jobTitle || undefined,
    entryDate: userForm.entryDate || undefined,
    tagIds: userForm.tagIds,
    positionIds: userForm.positionIds,
    primaryPositionId: userForm.positionIds.includes(userForm.primaryPositionId ?? -1) ? userForm.primaryPositionId : undefined
  };
  if (userForm.userId === undefined) {
    await createUser({
      userCode: userForm.userCode,
      userName: userForm.userName,
      phone: userForm.phone || undefined,
      deptId: userForm.deptId,
      password: await sha256(userForm.password),
      status: userForm.status,
      roleIds: userForm.roleIds,
      ...memberFields
    });
    ElMessage.success('用户新增成功');
  } else if (userForm.version !== undefined) {
    await updateUser(userForm.userId, {
      userName: userForm.userName,
      phone: userForm.phone || undefined,
      deptId: userForm.deptId,
      status: userForm.status,
      roleIds: userForm.roleIds,
      version: userForm.version,
      ...memberFields
    });
    ElMessage.success('用户修改成功');
  }

  userDialogVisible.value = false;
  await loadUsers();
}

/** 删除前弹出二次确认，避免误删用户。 */
async function confirmDelete(user: SystemUser): Promise<void> {
  await ElMessageBox.confirm(`确认删除用户“${user.userName}”吗？该操作不可恢复。`, '二次确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  });
  await deleteUser(user.userId);
  ElMessage.success('用户已删除');
  await loadUsers();
}

/** 打开密码重置表单。 */
function openResetDialog(user: SystemUser): void {
  Object.assign(resetForm, { userId: user.userId, userName: user.userName, password: '' });
  resetDialogVisible.value = true;
}

/** 输入新密码后进行二次确认并调用重置接口。 */
async function submitPasswordReset(): Promise<void> {
  const isValid = await resetFormRef.value?.validate().catch(() => false);

  if (!isValid) {
    return;
  }

  await ElMessageBox.confirm(`确认重置“${resetForm.userName}”的密码吗？`, '二次确认', {
    type: 'warning',
    confirmButtonText: '确认重置',
    cancelButtonText: '取消'
  });
  await resetUserPassword(resetForm.userId, await sha256(resetForm.password));
  resetDialogVisible.value = false;
  ElMessage.success('密码已重置');
}

/** 切换页码后刷新列表。 */
async function changePage(page: number): Promise<void> {
  pageNum.value = page;
  await loadUsers();
}

/** 切换每页记录数后回到首页。 */
async function changePageSize(size: number): Promise<void> {
  pageSize.value = size;
  pageNum.value = 1;
  await loadUsers();
}
/** 人工特征与只读培训事实入口。 */
const profileVisible = ref(false);
const profileUser = ref<{ userId: number; userName: string }>();
function openProfile(user: SystemUser): void { profileUser.value = user; profileVisible.value = true; }
/* 工具方法 */
function getRoleNames(roleIds: number[]): string {
  return roleIds.map((id) => roleOptions.value.find((role) => role.roleId === id)?.roleName).join('、');
}

/** 导出当前筛选条件下的用户为 CSV（UTF-8 BOM，Excel 可直接打开）。 */
async function handleExport(): Promise<void> {
  try {
    const blob = await exportUsers(buildUserQuery());
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `users_${Date.now()}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
    ElMessage.success('导出已开始');
  } catch {
    /* 全局错误提示已覆盖 */
  }
}

/** 用户导入弹窗与文件状态。 */
const importDialogVisible = ref(false);
const importFile = ref<File | null>(null);
const importing = ref(false);
const importResult = ref<UserImportResult | null>(null);

function openImportDialog(): void {
  importFile.value = null;
  importResult.value = null;
  importDialogVisible.value = true;
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement;
  importFile.value = input.files?.[0] ?? null;
}

/** ElUpload 变更回调：仅取首个本地文件用于导入。 */
function onUploadChange(file: { raw?: File }): void {
  importFile.value = file.raw ?? null;
}

/** 下载导入模板，表头与后端解析一致。 */
function downloadTemplate(): void {
  const header = '登录名,姓名,手机号,部门编码,状态\n';
  const row = 'zhangsan,张三,13800000001,D_RD,1\n';
  const blob = new Blob(['﻿' + header + row], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'user_import_template.csv';
  anchor.click();
  URL.revokeObjectURL(url);
}

/** 提交导入并展示逐行结果。 */
async function submitImport(): Promise<void> {
  if (!importFile.value) {
    ElMessage.warning('请先选择 CSV 文件');
    return;
  }
  importing.value = true;
  try {
    const result = await importUsers(importFile.value);
    importResult.value = result;
    if ((result.failedRows?.length ?? 0) === 0 && (result.fileErrors?.length ?? 0) === 0) {
      ElMessage.success(`导入完成：成功 ${result.successCount}，跳过 ${result.skippedCount}`);
      await loadUsers();
    } else {
      ElMessage.warning('导入存在失败行，请查看明细');
    }
  } finally {
    importing.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadFormOptions()]);
});
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm :model="filters" inline>
        <ElFormItem label="用户编码">
          <ElInput v-model="filters.userCode" placeholder="请输入用户编码" clearable />
        </ElFormItem>
        <ElFormItem label="用户名称">
          <ElInput v-model="filters.userName" placeholder="请输入用户名称" clearable />
        </ElFormItem>
        <ElFormItem label="手机号">
          <ElInput v-model="filters.phone" placeholder="请输入手机号" clearable />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="filters.status" placeholder="全部状态" clearable>
            <ElOption label="正常" :value="1" />
            <ElOption label="停用" :value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="所属部门">
          <ElTreeSelect v-model="filters.deptId" :data="deptTree" :props="treeSelectProps" placeholder="全部部门" clearable check-strictly />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="searchUsers">查询</ElButton>
          <ElButton @click="resetFilters">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>用户列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canOperate('sys:user:export')" @click="handleExport">导出</ElButton>
            <ElButton v-if="canOperate('sys:user:import')" type="primary" @click="openImportDialog">导入</ElButton>
            <ElButton v-if="canOperate('sys:user:add')" type="primary" @click="openCreateDialog">新增用户</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="userList" row-key="userId">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="userCode" label="用户编码" min-width="130" show-overflow-tooltip />
        <ElTableColumn prop="userName" label="用户名称" min-width="120" show-overflow-tooltip />
        <ElTableColumn prop="phone" label="手机号" min-width="130" show-overflow-tooltip />
        <ElTableColumn prop="deptName" label="所属部门" min-width="140" show-overflow-tooltip />
        <!-- 机构列展示机构名称，服务端未回填时回退到机构主键，不出现整列空白。 -->
        <ElTableColumn label="所属机构" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orgName || row.organizationId || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn label="角色" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getRoleNames(row.roleIds) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" min-width="170" show-overflow-tooltip />
        <ElTableColumn label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <template v-if="row.userCode && row.userCode !== 'superAdmin'">
              <ElButton v-if="canOperate('person:profile:list')" link type="primary" @click="openProfile(asSystemUser(row))">人员特征</ElButton>
              <ElButton v-if="canOperate('sys:user:edit')" link type="primary" @click="openEditDialog(asSystemUser(row))">编辑</ElButton>
              <ElButton v-if="canOperate('sys:user:resetPwd')" link type="primary" @click="openResetDialog(asSystemUser(row))">重置密码</ElButton>
              <ElButton v-if="canOperate('sys:user:delete')" link type="danger" @click="confirmDelete(asSystemUser(row))">删除</ElButton>
            </template>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="当前筛选条件下没有用户" :image-size="60" />
        </template>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="changePage"
          @size-change="changePageSize"
        />
      </div>
    </ElCard>

    <PersonProfileDialog v-model:visible="profileVisible" :user-id="profileUser?.userId" :user-name="profileUser?.userName" />
    <ElDialog v-model="userDialogVisible" class="user-dialog" :title="userForm.userId === undefined ? '新增用户' : '编辑用户'" width="800px" align-center destroy-on-close :lock-scroll="false">
      <ElForm ref="userFormRef" class="user-dialog-form" :model="userForm" :rules="userFormRules" label-width="92px">
        <ElRow :gutter="16">
          <ElCol :xs="24" :sm="12">
            <ElFormItem v-if="userForm.userId === undefined" label="用户编码" prop="userCode">
              <ElInput v-model="userForm.userCode" placeholder="请输入用户编码" />
            </ElFormItem>
            <ElFormItem v-else label="用户编码">
              <ElInput :model-value="userForm.userCode" disabled />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="用户名称" prop="userName">
              <ElInput v-model="userForm.userName" placeholder="请输入用户名称" />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="手机号">
              <ElInput v-model="userForm.phone" placeholder="请输入手机号" />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="邮箱">
              <ElInput v-model="userForm.email" placeholder="请输入邮箱" />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="成员类型">
              <ElSelect v-model="userForm.memberType" placeholder="请选择成员类型">
                <ElOption label="内部成员" value="INTERNAL" />
                <ElOption label="外部成员" value="EXTERNAL" />
                <ElOption label="监管成员" value="REGULATOR" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="状态">
              <ElRadioGroup v-model="userForm.status">
                <ElRadio :value="1">正常</ElRadio>
                <ElRadio :value="0">停用</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="职务">
              <ElInput v-model="userForm.jobTitle" placeholder="请输入职务" />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="入职日期">
              <ElDatePicker v-model="userForm.entryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择入职日期" />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="所属部门" prop="deptId">
              <ElTreeSelect v-model="userForm.deptId" :data="deptTree" :props="treeSelectProps" placeholder="请选择所属部门" check-strictly />
            </ElFormItem>
          </ElCol>
          <ElCol v-if="userForm.userId === undefined" :xs="24" :sm="12">
            <ElFormItem label="初始密码" prop="password">
              <ElInput v-model="userForm.password" type="password" placeholder="请输入初始密码" show-password />
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="岗位">
              <ElSelect v-model="userForm.positionIds" multiple filterable placeholder="请选择岗位">
                <ElOption v-for="position in positionOptions" :key="position.positionId" :label="position.positionName" :value="position.positionId" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="主岗位">
              <ElSelect v-model="userForm.primaryPositionId" clearable placeholder="请选择主岗位">
                <ElOption
                  v-for="position in positionOptions.filter((item) => userForm.positionIds.includes(item.positionId))"
                  :key="position.positionId"
                  :label="position.positionName"
                  :value="position.positionId"
                />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="标签">
              <ElSelect v-model="userForm.tagIds" multiple filterable placeholder="请选择标签">
                <ElOption v-for="tag in tagOptions" :key="tag.tagId" :label="tag.tagName" :value="tag.tagId" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :xs="24" :sm="12">
            <ElFormItem label="角色">
              <ElSelect v-model="userForm.roleIds" multiple placeholder="请选择角色">
                <ElOption v-for="role in roleOptions" :key="role.roleId" :label="role.roleName" :value="role.roleId" :disabled="role.status === 0" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
        </ElRow>
      </ElForm>
      <template #footer>
        <ElButton @click="userDialogVisible = false">取消</ElButton>
        <ElButton v-if="canOperate(userForm.userId === undefined ? 'sys:user:add' : 'sys:user:edit')" type="primary" @click="saveUser">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="resetDialogVisible" title="重置密码" width="440px" destroy-on-close :lock-scroll="false">
      <ElForm ref="resetFormRef" :model="resetForm" :rules="resetFormRules" label-width="92px">
        <ElFormItem label="用户">
          <span>{{ resetForm.userName }}</span>
        </ElFormItem>
        <ElFormItem label="新密码" prop="password">
          <ElInput v-model="resetForm.password" type="password" placeholder="请输入新密码" show-password />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="resetDialogVisible = false">取消</ElButton>
        <ElButton v-if="canOperate('sys:user:resetPwd')" type="primary" @click="submitPasswordReset">下一步</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="importDialogVisible" title="导入用户" width="560px" destroy-on-close :lock-scroll="false">
      <div class="user-import-tip">
        <p>请上传 CSV 文件，表头必须为：<code>登录名,姓名,手机号,部门编码,状态(可选)</code>。登录名重复的行将跳过（幂等），部门编码须属于当前租户。</p>
        <ElButton link type="primary" @click="downloadTemplate">下载导入模板</ElButton>
      </div>
      <ElUpload
        v-if="!importFile"
        class="user-import-upload"
        drag
        accept=".csv,text/csv"
        :auto-upload="false"
        :show-file-list="false"
        :on-change="onUploadChange"
      >
        <ElIcon class="el-icon--upload"><UploadFilled /></ElIcon>
        <div class="el-upload__text">将 CSV 文件拖到此处，或<em>点击上传</em></div>
      </ElUpload>
      <div v-else class="user-import-file">
        <span>{{ importFile.name }}</span>
        <ElButton link type="danger" @click="importFile = null">移除</ElButton>
      </div>
      <div v-if="importResult" class="user-import-result">
        <ElAlert
          :type="(importResult.failedRows?.length ?? 0) === 0 && (importResult.fileErrors?.length ?? 0) === 0 ? 'success' : 'warning'"
          :closable="false"
          show-icon
        >
          成功 {{ importResult.successCount }}，跳过 {{ importResult.skippedCount }}
        </ElAlert>
        <ul v-if="importResult.fileErrors?.length">
          <li v-for="err in importResult.fileErrors" :key="err" class="user-import-error">{{ err }}</li>
        </ul>
        <ul v-if="importResult.failedRows?.length">
          <li v-for="row in importResult.failedRows" :key="row.line" class="user-import-error">第 {{ row.line }} 行：{{ row.reason }}</li>
        </ul>
      </div>
      <template #footer>
        <ElButton @click="importDialogVisible = false">关闭</ElButton>
        <ElButton type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">开始导入</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.user-dialog-form {
  padding-right: 8px;
}
.user-dialog-form :deep(.el-input),
.user-dialog-form :deep(.el-select),
.user-dialog-form :deep(.el-date-editor),
.user-dialog-form :deep(.el-tree-select) {
  width: 100%;
}
.user-dialog-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

/* 用户表单字段较多：正文独立滚动，保证底部「保存」始终落在视口内。 */
:global(.user-dialog .el-dialog__body) {
  max-height: calc(92vh - 140px);
  overflow-y: auto;
}
</style>
