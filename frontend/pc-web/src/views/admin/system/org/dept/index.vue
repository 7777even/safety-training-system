<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createDept,
  deleteDept,
  getDeptManagementTree,
  updateDept,
  type DeptCreatePayload,
  type DeptStatus,
  type DeptType,
  type SystemDept
} from '@/api/system-dept';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import OrganizationSelect from '@/components/organization/OrganizationSelect.vue';
import { canManageTenantScope, currentTenantId } from '@/utils/tenant-context';

const userStore = useUserStore();
const canSelectTenant = canManageTenantScope(userStore.userInfo);

/** 判断当前用户是否拥有指定的部门管理操作权限。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

/** 部门树筛选条件。 */
const filters = reactive({ deptName: '', status: undefined as DeptStatus | undefined });
const deptTree = ref<SystemDept[]>([]);
const loading = ref(false);
const tableTreeProps = { children: 'children' };

/** 部门新增和编辑共用表单。 */
interface DeptFormModel {
  deptId?: number;
  organizationId?: number;
  parentId?: number;
  deptName: string;
  deptCode: string;
  deptType: DeptType;
  description: string;
  sortOrder: number;
  leader: string;
  phone: string;
  status: DeptStatus;
  version?: number;
}

const deptDialogVisible = ref(false);
const deptFormRef = ref<FormInstance>();
const deptForm = reactive<DeptFormModel>(createDeptForm());
const deptFormRules: FormRules<DeptFormModel> = {
  organizationId: [{ required: true, message: '请选择所属机构', trigger: 'change' }],
  parentId: [{ required: true, message: '请选择父级部门', trigger: 'change' }],
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  deptCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }]
};

/** 表单父级树使用完整数据，避免被当前列表筛选限制。 */
const formParentTree = ref<SystemDept[]>([]);
const parentTreeData = computed(() => [{ deptId: 0, deptName: '顶级部门', children: formParentTree.value }]);
const parentTreeProps = { value: 'deptId', label: 'deptName', children: 'children' };

/** 创建部门表单默认值。 */
function createDeptForm(): DeptFormModel {
  return {
    organizationId: currentTenantId(userStore.userInfo),
    parentId: undefined,
    deptId: undefined,
    deptName: '',
    deptCode: '',
    deptType: 'DEPARTMENT',
    description: '',
    sortOrder: 0,
    leader: '',
    phone: '',
    status: 1
  };
}

/** Element Plus 表格插槽不保留泛型行类型，在边界处收窄为部门记录。 */
function asSystemDept(row: unknown): SystemDept {
  return row as SystemDept;
}

/** 加载当前筛选条件下的部门树。 */
async function loadDeptTree(): Promise<void> {
  loading.value = true;

  try {
    deptTree.value = await getDeptManagementTree({
      ...(filters.deptName ? { deptName: filters.deptName } : {}),
      ...(filters.status !== undefined ? { status: filters.status } : {})
    });
  } finally {
    loading.value = false;
  }
}

/** 清空筛选条件。 */
async function resetFilters(): Promise<void> {
  Object.assign(filters, { deptName: '', status: undefined });
  await loadDeptTree();
}

/** 读取完整部门树，供新增和编辑选择父级。 */
async function loadFormParentTree(): Promise<boolean> {
  try {
    formParentTree.value = await getDeptManagementTree();
    return true;
  } catch {
    return false;
  }
}

/** 打开新增部门表单。 */
async function openCreateDialog(): Promise<void> {
  Object.assign(deptForm, createDeptForm());

  if (await loadFormParentTree()) {
    deptDialogVisible.value = true;
  }
}

/** 打开新增子级部门表单，并预选当前部门为父级。 */
async function openCreateChildDialog(parentDept: SystemDept): Promise<void> {
  Object.assign(deptForm, createDeptForm(), { parentId: parentDept.deptId, organizationId: parentDept.organizationId });

  if (await loadFormParentTree()) {
    deptDialogVisible.value = true;
  }
}

/** 打开编辑部门表单并保留服务端版本号。 */
async function openEditDialog(dept: SystemDept): Promise<void> {
  Object.assign(deptForm, {
    deptId: dept.deptId,
    organizationId: dept.organizationId,
    parentId: dept.parentId,
    deptName: dept.deptName,
    deptCode: dept.deptCode,
    deptType: dept.deptType ?? 'DEPARTMENT',
    description: dept.description ?? '',
    sortOrder: dept.sortOrder ?? 0,
    leader: dept.leader ?? '',
    phone: dept.phone ?? '',
    status: dept.status,
    version: dept.version
  });

  if (await loadFormParentTree()) {
    deptDialogVisible.value = true;
  }
}

/** 从表单构建新增和编辑共用的 Contract 字段。 */
function buildDeptPayload(): DeptCreatePayload {
  return {
    organizationId: deptForm.organizationId,
    parentId: deptForm.parentId,
    deptName: deptForm.deptName,
    deptCode: deptForm.deptCode,
    deptType: deptForm.deptType,
    description: deptForm.description || undefined,
    sortOrder: deptForm.sortOrder,
    ...(deptForm.leader ? { leader: deptForm.leader } : {}),
    ...(deptForm.phone ? { phone: deptForm.phone } : {}),
    status: deptForm.status
  };
}

/** 提交部门新增或编辑。 */
async function saveDept(): Promise<void> {
  const isValid = await deptFormRef.value?.validate().catch(() => false);

  if (!isValid) {
    return;
  }

  const payload = buildDeptPayload();

  if (deptForm.deptId === undefined) {
    await createDept(payload);
    ElMessage.success('部门新增成功');
  } else if (deptForm.version !== undefined) {
    await updateDept(deptForm.deptId, {
      parentId: payload.parentId,
      deptName: payload.deptName,
      deptCode: payload.deptCode,
      deptType: payload.deptType,
      description: payload.description,
      sortOrder: payload.sortOrder,
      leader: payload.leader,
      phone: payload.phone,
      status: payload.status,
      version: deptForm.version
    });
    ElMessage.success('部门修改成功');
  }

  deptDialogVisible.value = false;
  await loadDeptTree();
}

/** 删除前执行二次确认。 */
async function confirmDelete(dept: SystemDept): Promise<void> {
  await ElMessageBox.confirm(`确认删除部门“${dept.deptName}”吗？该操作不可恢复。`, '二次确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  });
  await deleteDept(dept.deptId);
  ElMessage.success('部门已删除');
  await loadDeptTree();
}
// 定义 change 事件处理函数
const handleParentChange = (val: number | undefined) => {
  // 如果清空了选择，同步清空 organizationId
  if (val === undefined || val === null) {
    deptForm.organizationId = undefined;
    return;
  }

  // 递归查找树节点的辅助函数
  const findNode = (tree: SystemDept[], targetId: number): SystemDept | null => {
    for (const node of tree) {
      // 假设你的 value 绑定的是 deptId
      if (node.deptId === targetId) {
        return node;
      }
      // 如果有子节点，继续递归查找
      if (node.children && node.children.length > 0) {
        const found = findNode(node.children, targetId);
        if (found) {
          return found;
        }
      }
    }
    return null;
  };
  const treeData = parentTreeData.value;

  // 查找对应的节点
  const targetNode = findNode(treeData[0].children || [], val);

  // 赋值
  if (targetNode) {
    deptForm.organizationId = targetNode.organizationId;
  } else {
    deptForm.organizationId = undefined;
  }
};

onMounted(loadDeptTree);
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm :model="filters" inline>
        <ElFormItem label="部门名称">
          <ElInput v-model="filters.deptName" placeholder="请输入部门名称" clearable />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="filters.status" placeholder="全部状态" clearable>
            <ElOption label="正常" :value="1" />
            <ElOption label="停用" :value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="loadDeptTree">查询</ElButton>
          <ElButton @click="resetFilters">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>部门列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canOperate('sys:dept:add')" type="primary" @click="openCreateDialog">新增部门</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="deptTree" row-key="deptId" :tree-props="tableTreeProps">
        <ElTableColumn prop="deptName" label="部门名称" min-width="200" />
        <ElTableColumn prop="deptCode" label="部门编码" min-width="140" />
        <!-- 机构列展示机构名称，服务端未回填时回退到机构主键，不出现整列空白。 -->
        <ElTableColumn label="所属机构" min-width="160">
          <template #default="{ row }">{{ row.orgName || row.organizationId }}</template>
        </ElTableColumn>
        <ElTableColumn prop="leader" label="负责人" min-width="120" />
        <ElTableColumn prop="phone" label="负责人电话" min-width="150" />
        <ElTableColumn prop="sortOrder" label="排序" width="80" />
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '正常' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <ElButton v-if="canOperate('sys:dept:add')" link type="primary" @click="openCreateChildDialog(asSystemDept(row))">添加子级</ElButton>
            <ElButton v-if="canOperate('sys:dept:edit')" link type="primary" @click="openEditDialog(asSystemDept(row))">编辑</ElButton>
            <ElButton v-if="canOperate('sys:dept:delete')" link type="danger" @click="confirmDelete(asSystemDept(row))">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElDialog v-model="deptDialogVisible" :title="deptForm.deptId === undefined ? '新增部门' : '编辑部门'" width="580px" destroy-on-close :lock-scroll="false">
      <ElForm ref="deptFormRef" :model="deptForm" :rules="deptFormRules" label-width="96px">
        <ElFormItem label="父级部门" prop="parentId">
          <ElTreeSelect
            v-model="deptForm.parentId"
            :data="parentTreeData"
            :props="parentTreeProps"
            placeholder="请选择父级部门"
            check-strictly
            @change="handleParentChange"
          />
        </ElFormItem>
        <ElFormItem label="所属机构" prop="organizationId">
          <OrganizationSelect v-if="canSelectTenant" v-model="deptForm.organizationId" :disabled="deptForm.deptId !== undefined" />
          <ElText v-else>{{ currentTenantId(userStore.userInfo) }}</ElText>
        </ElFormItem>
        <ElFormItem label="部门名称" prop="deptName">
          <ElInput v-model="deptForm.deptName" placeholder="请输入部门名称" />
        </ElFormItem>
        <ElFormItem label="部门编码" prop="deptCode">
          <ElInput v-model="deptForm.deptCode" placeholder="请输入部门编码" />
        </ElFormItem>
        <ElFormItem label="部门类型">
          <ElSelect v-model="deptForm.deptType" placeholder="请选择部门类型">
            <ElOption label="学校" value="SCHOOL" />
            <ElOption label="学院" value="COLLEGE" />
            <ElOption label="专业" value="MAJOR" />
            <ElOption label="部门" value="DEPARTMENT" />
            <ElOption label="班级" value="CLASS" />
            <ElOption label="其他" value="OTHER" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="部门描述">
          <ElInput v-model="deptForm.description" type="textarea" :rows="2" placeholder="请输入部门描述" />
        </ElFormItem>
        <ElFormItem label="负责人">
          <ElInput v-model="deptForm.leader" placeholder="请输入负责人" />
        </ElFormItem>
        <ElFormItem label="负责人电话">
          <ElInput v-model="deptForm.phone" placeholder="请输入负责人电话" />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber v-model="deptForm.sortOrder" :min="0" placeholder="请输入排序序号" />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElRadioGroup v-model="deptForm.status">
            <ElRadio :value="1">正常</ElRadio>
            <ElRadio :value="0">停用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="deptDialogVisible = false">取消</ElButton>
        <ElButton v-if="canOperate(deptForm.deptId === undefined ? 'sys:dept:add' : 'sys:dept:edit')" type="primary" @click="saveDept">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #102a43;
  font-size: 16px;
  font-weight: 600;
}
</style>
