<script setup lang="ts">
import { computed, onMounted, reactive, ref, type Component } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Collection,
  DataAnalysis,
  Document,
  Folder,
  Grid,
  HomeFilled,
  List,
  Management,
  Menu,
  OfficeBuilding,
  Reading,
  Setting,
  Tickets,
  Notebook,
  View,
  Lock,
  User
} from '@element-plus/icons-vue';
import {
  createMenu,
  deleteMenu,
  getMenuTree,
  updateMenu,
  updateMenuBatchVisibility,
  updateMenuVisibility,
  type MenuCreatePayload,
  type MenuType,
  type MenuVisibility,
  type MenuVersionItem,
  type SystemMenu
} from '@/api/system-menu';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const userStore = useUserStore();

/** 判断当前用户是否拥有指定的菜单管理操作权限。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

const MENU_TYPE_OPTIONS: Array<{ label: string; value: MenuType }> = [
  { label: '模块', value: 'D' },
  { label: '目录', value: 'M' },
  { label: '菜单', value: 'C' },
  { label: '按钮', value: 'F' }
];

/** 菜单配置可选择的常用 Element Plus 图标。 */
const MENU_ICON_OPTIONS: Array<{ label: string; value: string; icon: Component }> = [
  { label: '首页', value: 'HomeFilled', icon: HomeFilled },
  { label: '数据概览', value: 'DataAnalysis', icon: DataAnalysis },
  { label: '业务管理', value: 'Management', icon: Management },
  { label: '系统设置', value: 'Setting', icon: Setting },
  { label: '文档资料', value: 'Document', icon: Document },
  { label: '资源目录', value: 'Folder', icon: Folder },
  { label: '课程学习', value: 'Reading', icon: Reading },
  { label: '用户管理', value: 'User', icon: User },
  { label: '组织管理', value: 'OfficeBuilding', icon: OfficeBuilding },
  { label: '应用模块', value: 'Grid', icon: Grid },
  { label: '考试中心', value: 'Tickets', icon: Tickets },
  { label: '菜单管理', value: 'Menu', icon: Menu },
  { label: '资源集合', value: 'Collection', icon: Collection },
  { label: '资源管理', value: 'Folder', icon: Folder },
  { label: '资源列表', value: 'List', icon: List },
  { label: '资源详情', value: 'View', icon: View },
  { label: '角色权限', value: 'Lock', icon: Lock },
  { label: '字典管理', value: 'Notebook', icon: Notebook }
];

/** 根据已选图标标识返回对应组件，历史未知标识回退为菜单图标。 */
function getMenuIcon(iconName: string): Component {
  return MENU_ICON_OPTIONS.find((item) => item.value === iconName)?.icon ?? Menu;
}

/** 菜单树筛选条件。 */
const filters = reactive({ menuName: '', perms: '', isVisible: undefined as MenuVisibility | undefined });
const menuTree = ref<SystemMenu[]>([]);
const loading = ref(false);
const tableTreeProps = { children: 'children' };

/** 菜单新增和编辑共用的表单模型。 */
interface MenuFormModel {
  menuId?: number;
  parentId: number;
  menuName: string;
  path: string;
  component: string;
  perms: string;
  icon: string;
  sortOrder: number;
  isVisible: MenuVisibility;
  menuType: MenuType;
  moduleId?: number;
  version?: number;
}

const menuDialogVisible = ref(false);
const menuFormRef = ref<FormInstance>();
const menuForm = reactive<MenuFormModel>(createMenuForm());
const menuFormRules: FormRules<MenuFormModel> = {
  parentId: [{ required: true, message: '请选择父菜单', trigger: 'change' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }]
};

/** 表单父菜单树始终使用完整数据，不能受当前列表筛选影响。 */
const formParentMenuTree = ref<SystemMenu[]>([]);
/** 表单父菜单树增加虚拟根节点，以支持 Contract 要求的顶级 parentId=0。 */
const parentMenuTree = computed(() => [{ menuId: 0, menuName: '顶级菜单', children: formParentMenuTree.value }]);
const parentTreeProps = { value: 'menuId', label: 'menuName', children: 'children' };

/** 批量可见性窗口使用未筛选菜单树，避免误改筛选范围外的状态。 */
const batchDialogVisible = ref(false);
const batchMenuTree = ref<SystemMenu[]>([]);
const visibilityTreeRef = ref<{
  getCheckedKeys: () => (string | number)[];
}>();
const visibleMenuIds = computed(() =>
  collectMenus(batchMenuTree.value)
    .filter((menu) => menu.isVisible === 1)
    .map((menu) => menu.menuId)
);

/** 生成菜单表单默认值。 */
function createMenuForm(): MenuFormModel {
  return {
    parentId: 0,
    menuId: undefined,
    menuName: '',
    path: '',
    component: '',
    perms: '',
    icon: '',
    sortOrder: 0,
    isVisible: 1,
    menuType: 'M',
    moduleId: undefined
  };
}

/** 递归扁平化菜单树，供批量可见性提交版本映射。 */
function collectMenus(nodes: SystemMenu[]): SystemMenu[] {
  return nodes.flatMap((menu) => [menu, ...collectMenus(menu.children ?? [])]);
}

/** Element Plus 表格插槽不保留泛型行类型，在边界处收窄为菜单记录。 */
function asSystemMenu(row: unknown): SystemMenu {
  return row as SystemMenu;
}

/** 查询当前筛选条件下的菜单树。 */
async function loadMenus(): Promise<void> {
  loading.value = true;

  try {
    menuTree.value = await getMenuTree({
      ...(filters.menuName ? { menuName: filters.menuName } : {}),
      ...(filters.perms ? { perms: filters.perms } : {}),
      ...(filters.isVisible !== undefined ? { isVisible: filters.isVisible } : {})
    });
  } finally {
    loading.value = false;
  }
}

/** 查询菜单树。 */
async function searchMenus(): Promise<void> {
  await loadMenus();
}

/** 清空筛选条件。 */
async function resetFilters(): Promise<void> {
  Object.assign(filters, { menuName: '', perms: '', isVisible: undefined });
  await loadMenus();
}

/** 加载完整菜单树，供新增与编辑菜单时选择父节点。 */
async function loadFormParentMenuTree(): Promise<boolean> {
  try {
    formParentMenuTree.value = await getMenuTree();
    return true;
  } catch {
    return false;
  }
}

/** 打开新增菜单表单。 */
async function openCreateDialog(): Promise<void> {
  Object.assign(menuForm, createMenuForm());

  if (await loadFormParentMenuTree()) {
    menuDialogVisible.value = true;
  }
}

/** 打开新增子级菜单表单，并预选当前菜单为父菜单。 */
async function openCreateChildDialog(parentMenu: SystemMenu): Promise<void> {
  Object.assign(menuForm, createMenuForm(), { parentId: parentMenu.menuId });

  if (await loadFormParentMenuTree()) {
    menuDialogVisible.value = true;
  }
}

/** 打开编辑菜单表单并保留版本号。 */
async function openEditDialog(menu: SystemMenu): Promise<void> {
  Object.assign(menuForm, {
    menuId: menu.menuId,
    parentId: menu.parentId,
    menuName: menu.menuName,
    path: menu.path ?? '',
    component: menu.component ?? '',
    perms: menu.perms ?? '',
    icon: menu.icon ?? '',
    sortOrder: menu.sortOrder ?? 0,
    isVisible: menu.isVisible,
    menuType: menu.menuType,
    moduleId: menu.moduleId,
    version: menu.version
  });
  if (await loadFormParentMenuTree()) {
    menuDialogVisible.value = true;
  }
}

/** 将表单转换为新增和编辑共用的 Contract 字段。 */
function buildMenuPayload(): MenuCreatePayload {
  return {
    parentId: menuForm.parentId,
    menuName: menuForm.menuName,
    ...(menuForm.path ? { path: menuForm.path } : {}),
    ...(menuForm.component ? { component: menuForm.component } : { component: '' }),
    ...(menuForm.perms ? { perms: menuForm.perms } : {}),
    ...(menuForm.icon ? { icon: menuForm.icon } : {}),
    sortOrder: menuForm.sortOrder,
    isVisible: menuForm.isVisible,
    menuType: menuForm.menuType,
    ...(menuForm.moduleId !== undefined ? { moduleId: menuForm.moduleId } : {})
  };
}

/** 提交菜单新增或编辑；编辑始终使用服务端返回的 version。 */
async function saveMenu(): Promise<void> {
  const isValid = await menuFormRef.value?.validate().catch(() => false);

  if (!isValid) {
    return;
  }

  const payload = buildMenuPayload();

  if (menuForm.menuId === undefined) {
    await createMenu(payload);
    ElMessage.success('菜单新增成功');
  } else if (menuForm.version !== undefined) {
    await updateMenu(menuForm.menuId, { ...payload, version: menuForm.version });
    ElMessage.success('菜单修改成功');
  }

  menuDialogVisible.value = false;
  await loadMenus();
}

/** 删除前执行二次确认，服务端冲突错误将以可读消息展示。 */
async function confirmDelete(menu: SystemMenu): Promise<void> {
  await ElMessageBox.confirm(`确认删除菜单“${menu.menuName}”吗？存在子菜单或角色关联时将无法删除。`, '二次确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  });
  await deleteMenu(menu.menuId);
  ElMessage.success('菜单已删除');
  await loadMenus();
}

/** 经确认后调用单项可见性接口，不复用批量接口。 */
async function confirmSingleVisibility(menu: SystemMenu): Promise<void> {
  const nextVisibility: MenuVisibility = menu.isVisible === 1 ? 0 : 1;
  const nextLabel = nextVisibility === 1 ? '显示' : '隐藏';

  await ElMessageBox.confirm(`确认将菜单“${menu.menuName}”设为${nextLabel}吗？`, '二次确认', {
    type: 'warning',
    confirmButtonText: `确认${nextLabel}`,
    cancelButtonText: '取消'
  });
  await updateMenuVisibility(menu.menuId, nextVisibility, menu.version);
  ElMessage.success(`菜单已设为${nextLabel}`);
  await loadMenus();
}

/** 读取完整菜单树后打开批量可见性窗口。 */
async function openBatchVisibilityDialog(): Promise<void> {
  batchMenuTree.value = await getMenuTree();
  batchDialogVisible.value = true;
}

/** 严格勾选模式下直接获取全部可见菜单 ID，避免默认级联改变子节点状态。 */
function getBatchVisibleIds(): number[] {
  return visibilityTreeRef.value?.getCheckedKeys().map(Number) ?? [];
}

/** 经确认后提交批量可见状态和全部菜单版本映射。 */
async function submitBatchVisibility(): Promise<void> {
  const visibleIds = getBatchVisibleIds();
  const items: MenuVersionItem[] = collectMenus(batchMenuTree.value).map((menu) => ({ menuId: menu.menuId, version: menu.version }));

  await ElMessageBox.confirm(`确认提交 ${visibleIds.length} 个可见菜单的批量调整吗？`, '二次确认', {
    type: 'warning',
    confirmButtonText: '确认提交',
    cancelButtonText: '取消'
  });
  await updateMenuBatchVisibility(visibleIds, items);
  batchDialogVisible.value = false;
  ElMessage.success('菜单可见性已批量更新');
  await loadMenus();
}

onMounted(loadMenus);
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm :model="filters" inline>
        <ElFormItem label="菜单名称">
          <ElInput v-model="filters.menuName" placeholder="请输入菜单名称" clearable />
        </ElFormItem>
        <ElFormItem label="权限标识">
          <ElInput v-model="filters.perms" placeholder="请输入权限标识" clearable />
        </ElFormItem>
        <ElFormItem label="可见状态">
          <ElSelect v-model="filters.isVisible" placeholder="全部状态" clearable>
            <ElOption label="可见" :value="1" />
            <ElOption label="隐藏" :value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="searchMenus">查询</ElButton>
          <ElButton @click="resetFilters">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>菜单列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canOperate('sys:menu:edit')" @click="openBatchVisibilityDialog">批量调整可见性</ElButton>
            <ElButton v-if="canOperate('sys:menu:add')" type="primary" @click="openCreateDialog">新增菜单</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="menuTree" row-key="menuId" :tree-props="tableTreeProps">
        <ElTableColumn prop="menuName" label="菜单名称" min-width="180" />
        <ElTableColumn prop="menuType" label="菜单类型" width="80" align="center">
          <template #default="{ row }">
            {{ MENU_TYPE_OPTIONS.find((item) => item.value === row.menuType)?.label }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="path" label="路由路径" min-width="170" />
        <ElTableColumn prop="component" label="组件路径" min-width="170" />
        <ElTableColumn prop="perms" label="权限标识" min-width="170" align="center" />
        <ElTableColumn prop="sortOrder" label="排序" width="80" align="center" />
        <ElTableColumn label="可见性" width="100" align="center">
          <template #default="{ row }">
            <ElTag :type="row.isVisible === 1 ? 'success' : 'info'">
              {{ row.isVisible === 1 ? '可见' : '隐藏' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <ElButton v-if="canOperate('sys:menu:add')" link type="primary" @click="openCreateChildDialog(asSystemMenu(row))">添加子级</ElButton>
            <ElButton v-if="canOperate('sys:menu:edit')" link type="primary" @click="openEditDialog(asSystemMenu(row))">编辑</ElButton>
            <ElButton v-if="canOperate('sys:menu:edit')" link type="primary" @click="confirmSingleVisibility(asSystemMenu(row))">
              {{ row.isVisible === 1 ? '设为隐藏' : '设为可见' }}
            </ElButton>
            <ElButton v-if="canOperate('sys:menu:delete')" link type="danger" @click="confirmDelete(asSystemMenu(row))">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>

    <ElDialog v-model="menuDialogVisible" :title="menuForm.menuId === undefined ? '新增菜单' : '编辑菜单'" width="660px" destroy-on-close :lock-scroll="false">
      <ElForm ref="menuFormRef" :model="menuForm" :rules="menuFormRules" label-width="96px">
        <ElFormItem label="父菜单" prop="parentId">
          <ElTreeSelect v-model="menuForm.parentId" :data="parentMenuTree" :props="parentTreeProps" placeholder="请选择父菜单" check-strictly />
        </ElFormItem>
        <ElFormItem label="菜单名称" prop="menuName">
          <ElInput v-model="menuForm.menuName" placeholder="请输入菜单名称" />
        </ElFormItem>
        <ElFormItem label="菜单类型" prop="menuType">
          <ElSelect v-model="menuForm.menuType" placeholder="请选择菜单类型">
            <ElOption v-for="item in MENU_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="路由路径">
          <ElInput v-model="menuForm.path" placeholder="请输入路由路径" />
        </ElFormItem>
        <ElFormItem label="组件路径">
          <ElInput v-model="menuForm.component" placeholder="请输入组件路径" />
        </ElFormItem>
        <ElFormItem label="权限标识">
          <ElInput v-model="menuForm.perms" placeholder="请输入权限标识" />
        </ElFormItem>
        <ElFormItem label="图标标识">
          <div class="menu-icon-selector">
            <ElSelect v-model="menuForm.icon" placeholder="请选择菜单图标" clearable>
              <ElOption v-for="item in MENU_ICON_OPTIONS" :key="item.value" :label="item.label" :value="item.value">
                <div class="menu-icon-selector__option">
                  <ElIcon><component :is="item.icon" /></ElIcon>
                  <span>{{ item.label }}</span>
                </div>
              </ElOption>
            </ElSelect>
            <div v-if="menuForm.icon" class="menu-icon-selector__preview">
              <ElIcon class="menu-icon-selector__preview-icon"><component :is="getMenuIcon(menuForm.icon)" /></ElIcon>
              <span>当前图标</span>
            </div>
          </div>
        </ElFormItem>
        <ElFormItem label="所属模块">
          <ElInputNumber v-model="menuForm.moduleId" :min="1" placeholder="请输入所属模块编号" />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber v-model="menuForm.sortOrder" :min="0" placeholder="请输入排序序号" />
        </ElFormItem>
        <ElFormItem label="可见性">
          <ElRadioGroup v-model="menuForm.isVisible">
            <ElRadio :value="1">可见</ElRadio>
            <ElRadio :value="0">隐藏</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="menuDialogVisible = false">取消</ElButton>
        <ElButton v-if="canOperate(menuForm.menuId === undefined ? 'sys:menu:add' : 'sys:menu:edit')" type="primary" @click="saveMenu">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="batchDialogVisible" title="批量调整菜单可见性" width="620px" destroy-on-close :lock-scroll="false">
      <p class="batch-tip">勾选表示可见；每个菜单独立勾选，提交时保留完整可见菜单 ID 集合。</p>
      <ElTree
        ref="visibilityTreeRef"
        :data="batchMenuTree"
        :props="parentTreeProps"
        node-key="menuId"
        show-checkbox
        check-strictly
        :default-checked-keys="visibleMenuIds"
      />
      <template #footer>
        <ElButton @click="batchDialogVisible = false">取消</ElButton>
        <ElButton v-if="canOperate('sys:menu:edit')" type="primary" @click="submitBatchVisibility">下一步</ElButton>
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
.card-header div {
  display: flex;
  gap: 12px;
}
.batch-tip {
  margin-top: 0;
  color: #667085;
  font-size: 13px;
}
.menu-icon-selector {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
}
.menu-icon-selector :deep(.el-select) {
  width: 214px;
}
.menu-icon-selector__option,
.menu-icon-selector__preview {
  display: flex;
  align-items: center;
  gap: 8px;
}
.menu-icon-selector__preview {
  color: #667085;
  font-size: 13px;
}
.menu-icon-selector__preview-icon {
  color: #2563eb;
  font-size: 18px;
}
@media (max-width: 760px) {
  .card-header {
    align-items: flex-start;
    gap: 12px;
    flex-direction: column;
  }
}
</style>
