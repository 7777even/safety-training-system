<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { MenuTreeItem } from '@shared/types';
import {
  createRole,
  deleteRole,
  getRoleDetail,
  getRoleMenuTree,
  getRolePage,
  updateRole,
  type RoleLevel,
  type RoleStatus,
  type SystemRole
} from '@/api/system-role';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { getSelectedLeafMenuIds } from '@/utils/menu-tree';
import { hasPermission } from '@/utils/permission';
import OrganizationSelect from '@/components/organization/OrganizationSelect.vue';
import { canManageTenantScope, currentTenantId, currentTenantLabel } from '@/utils/tenant-context';

const userStore = useUserStore();
const canSelectTenant = canManageTenantScope(userStore.userInfo);
const canOperate = (permission: string): boolean => hasPermission(userStore.userInfo, permission);
const filters = reactive({ roleName: '', roleCode: '', status: undefined as RoleStatus | undefined });
const roleList = ref<SystemRole[]>([]);
const menuTree = ref<MenuTreeItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const loading = ref(false);
const roleDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const roleDetail = ref<SystemRole>();
const formRef = ref<FormInstance>();
const menuTreeRef = ref<{ getCheckedKeys: () => (string | number)[]; setCheckedKeys: (keys: number[]) => void }>();

/** 收窄表格插槽行类型，确保角色操作只接收契约定义的角色记录。 */
function asSystemRole(row: unknown): SystemRole {
  return row as SystemRole;
}

interface RoleFormModel {
  roleId?: number;
  organizationId?: number;
  roleName: string;
  roleCode: string;
  roleLevel: RoleLevel;
  description: string;
  status: RoleStatus;
  menuIds: number[];
  version?: number;
}

const form = reactive<RoleFormModel>(createForm());
const rules: FormRules<RoleFormModel> = {
  organizationId: [{ required: true, message: '请选择所属租户', trigger: 'change' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
};

function createForm(): RoleFormModel {
  return {
    roleId: undefined,
    organizationId: currentTenantId(userStore.userInfo),
    roleName: '',
    roleCode: '',
    roleLevel: 'ADMIN',
    description: '',
    status: 1,
    menuIds: []
  };
}

async function loadRoles(): Promise<void> {
  loading.value = true;
  try {
    const result = await getRolePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      'query.roleName': filters.roleName || undefined,
      'query.roleCode': filters.roleCode || undefined,
      'query.status': filters.status
    });
    roleList.value = result.list;
    total.value = result.total;
  } finally {
    loading.value = false;
  }
}

async function loadMenuTree(): Promise<void> {
  menuTree.value = await getRoleMenuTree();
}

function openCreate(): void {
  Object.assign(form, createForm());
  roleDialogVisible.value = true;
}

async function openEdit(role: SystemRole): Promise<void> {
  const detail = await getRoleDetail(role.roleId);
  Object.assign(form, { ...detail, roleLevel: detail.roleLevel ?? 'ADMIN', description: detail.description ?? '', menuIds: detail.menuIds ?? [] });
  roleDialogVisible.value = true;
}

async function save(): Promise<void> {
  if (!(await formRef.value?.validate().catch(() => false)) || form.organizationId === undefined) return;
  const menuIds = menuTreeRef.value?.getCheckedKeys().map(Number) ?? [];
  if (form.roleId === undefined) {
    await createRole({
      organizationId: form.organizationId,
      roleName: form.roleName,
      roleCode: form.roleCode,
      roleLevel: form.roleLevel,
      description: form.description || undefined,
      status: form.status,
      menuIds
    });
  } else {
    await updateRole(form.roleId, {
      roleName: form.roleName,
      roleCode: form.roleCode,
      roleLevel: form.roleLevel,
      description: form.description || undefined,
      status: form.status,
      menuIds,
      version: form.version ?? 0
    });
  }
  ElMessage.success('角色保存成功');
  roleDialogVisible.value = false;
  await loadRoles();
}

async function remove(role: SystemRole): Promise<void> {
  await ElMessageBox.confirm(`确认删除角色“${role.roleName}”吗？`, '删除确认', { type: 'warning' });
  await deleteRole(role.roleId);
  await loadRoles();
}

async function openDetail(role: SystemRole): Promise<void> {
  roleDetail.value = await getRoleDetail(role.roleId);
  detailDialogVisible.value = true;
}

function menuIds(role: Pick<RoleFormModel, 'menuIds'> | undefined): number[] {
  return getSelectedLeafMenuIds(menuTree.value, role?.menuIds ?? []);
}

onMounted(async () => {
  await Promise.all([loadRoles(), loadMenuTree()]);
});
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline>
        <ElFormItem label="角色名称"><ElInput v-model="filters.roleName" clearable placeholder="请输入角色名称" /></ElFormItem>
        <ElFormItem label="角色编码"><ElInput v-model="filters.roleCode" clearable placeholder="请输入角色编码" /></ElFormItem>
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
              loadRoles();
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
          <strong>角色列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canOperate('sys:role:add')" type="primary" @click="openCreate">新增角色</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="roleList" row-key="roleId">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <!-- 机构列展示机构名称，服务端未回填时回退到机构主键，不出现整列空白。 -->
        <ElTableColumn label="所属机构">
          <template #default="{ row }">{{ row.orgName || row.organizationId }}</template>
        </ElTableColumn>
        <ElTableColumn prop="roleName" label="角色名称" />
        <ElTableColumn prop="roleCode" label="角色编码" />
        <ElTableColumn prop="roleLevel" label="角色级别">
          <template #default="{ row }">
            <ElTag>{{ { ADMIN: '管理角色', TEACHER: '教师角色', STUDENT: '学员角色' }[row.roleLevel as string] }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="220">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openDetail(asSystemRole(row))">详情</ElButton>
            <ElButton v-if="canOperate('sys:role:edit')" link type="primary" @click="openEdit(asSystemRole(row))">编辑</ElButton>
            <ElButton v-if="canOperate('sys:role:delete')" link type="danger" @click="remove(asSystemRole(row))">删除</ElButton>
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
          @current-change="loadRoles"
          @size-change="
            pageNum = 1;
            loadRoles();
          "
        />
      </div>
    </ElCard>
    <ElDialog v-model="roleDialogVisible" :title="form.roleId === undefined ? '新增角色' : '编辑角色'" width="620px" destroy-on-close :lock-scroll="false">
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="100px">
        <ElFormItem label="所属租户" prop="organizationId">
          <OrganizationSelect v-if="canSelectTenant" v-model="form.organizationId" :disabled="form.roleId !== undefined" />
          <ElText v-else>{{ currentTenantLabel(userStore.userInfo) }}</ElText>
        </ElFormItem>
        <ElFormItem label="角色名称" prop="roleName"><ElInput v-model="form.roleName" /></ElFormItem>
        <ElFormItem label="角色编码" prop="roleCode"><ElInput v-model="form.roleCode" /></ElFormItem>
        <ElFormItem label="角色级别">
          <ElSelect v-model="form.roleLevel">
            <ElOption label="管理角色" value="ADMIN" />
            <ElOption label="教师角色" value="TEACHER" />
            <ElOption label="学员角色" value="STUDENT" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="角色说明"><ElInput v-model="form.description" type="textarea" /></ElFormItem>
        <ElFormItem label="状态">
          <ElRadioGroup v-model="form.status">
            <ElRadio :value="1">正常</ElRadio>
            <ElRadio :value="0">停用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="菜单权限">
          <ElTree
            ref="menuTreeRef"
            :data="menuTree"
            :props="{ label: 'menuName', children: 'children' }"
            node-key="menuId"
            show-checkbox
            :default-checked-keys="menuIds(form)"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="roleDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="save">保存</ElButton>
      </template>
    </ElDialog>
    <ElDialog v-model="detailDialogVisible" title="角色详情" width="620px" :lock-scroll="false">
      <ElDescriptions v-if="roleDetail" :column="2" border>
        <ElDescriptionsItem label="角色名称">{{ roleDetail.roleName }}</ElDescriptionsItem>
        <ElDescriptionsItem label="角色编码">{{ roleDetail.roleCode }}</ElDescriptionsItem>
        <ElDescriptionsItem label="所属租户">{{ roleDetail.orgName || roleDetail.organizationId }}</ElDescriptionsItem>
        <ElDescriptionsItem label="状态">{{ roleDetail.status === 1 ? '正常' : '停用' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="创建时间">{{ roleDetail.createTime || '-' }}</ElDescriptionsItem>
      </ElDescriptions>
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
