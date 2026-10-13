<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import {
  createTrainingTemplateCategory,
  deleteTrainingTemplateCategory,
  deleteTrainingTemplate,
  getTrainingTemplateCategory,
  getTrainingTemplateCategoryTree,
  getTrainingTemplatePage,
  updateTrainingTemplateCategory,
  type TrainingTemplate,
  type TrainingTemplateCategory,
  type TrainingTemplateCategoryTreeNode,
  type TrainingTemplatePublishStatus
} from '@/api/training-template';
import { getTrainingTypePage, type TrainingType } from '@/api/training-type';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

/** 发布状态中文映射，避免界面直接展示 Contract 英文枚举。 */
const PUBLISH_STATUS_OPTIONS: Array<{ label: string; value: TrainingTemplatePublishStatus }> = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已归档', value: 'ARCHIVED' }
];

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const rows = ref<TrainingTemplate[]>([]);
const categoryTree = ref<TrainingTemplateCategoryTreeNode[]>([]);
const categoryDialogVisible = ref(false);
const categorySubmitting = ref(false);
const categoryFormRef = ref<FormInstance>();
const trainingTypeOptions = ref<TrainingType[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const categoryProps = {
  label: 'categoryName',
  value: 'templateCategoryId',
  children: 'children'
};
/** 模板分类新增和编辑表单。 */
interface CategoryForm {
  templateCategoryId?: number;
  parentId: number;
  categoryName: string;
  sortOrder: number;
  status: 0 | 1;
  version?: number;
}
const filters = reactive({
  templateName: '',
  templateCategoryId: undefined as number | undefined,
  trainingTypeId: undefined as number | undefined,
  publishStatus: undefined as TrainingTemplatePublishStatus | undefined,
  status: undefined as 0 | 1 | undefined
});
const categoryForm = reactive<CategoryForm>(emptyCategoryForm());
/** 父级下拉显式展示顶层分类，避免暴露内部值 0。 */
const categoryParentTree = computed(() => [{ templateCategoryId: 0, categoryName: '顶层模板分类', children: categoryTree.value }]);
const categoryFormRules: FormRules<CategoryForm> = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
};

/** 加载当前机构下的启用培训类型供列表筛选。 */
async function loadTrainingTypes(): Promise<void> {
  const data = await getTrainingTypePage({
    pageNum: 1,
    pageSize: 100,
    'query.status': 1
  });
  trainingTypeOptions.value = data.list;
}

/** 按后端权限集合控制页面入口。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

/** 收敛 Element Plus 表格插槽的宽泛行类型。 */
function asTemplate(row: unknown): TrainingTemplate {
  return row as TrainingTemplate;
}

/** 将发布状态转换为中文。 */
function publishStatusText(status: TrainingTemplatePublishStatus): string {
  return PUBLISH_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status;
}

/** 根据发布状态返回 Element Plus 标签语义。 */
function publishTagType(status: TrainingTemplatePublishStatus): 'success' | 'warning' | 'info' {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'DRAFT') return 'warning';
  return 'info';
}

/** 将培训类型主键转换为用户可识别的业务名称。 */
function trainingTypeName(trainingTypeId: number): string {
  return trainingTypeOptions.value.find((item) => item.trainingTypeId === trainingTypeId)?.typeName ?? '未匹配类型';
}

/** 使用 OpenAPI 的 query.* 字段加载模板分页。 */
async function loadRows(): Promise<void> {
  loading.value = true;
  try {
    const data = await getTrainingTemplatePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(filters.templateName ? { 'query.templateName': filters.templateName } : {}),
      ...(filters.templateCategoryId !== undefined ? { 'query.templateCategoryId': filters.templateCategoryId } : {}),
      ...(filters.trainingTypeId !== undefined ? { 'query.trainingTypeId': filters.trainingTypeId } : {}),
      ...(filters.publishStatus ? { 'query.publishStatus': filters.publishStatus } : {}),
      ...(filters.status !== undefined ? { 'query.status': filters.status } : {})
    });
    rows.value = data.list;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

/** 加载完整模板分类树，供左侧管理和新增模板选择使用。 */
async function loadCategoryTree(): Promise<void> {
  categoryTree.value = await getTrainingTemplateCategoryTree();
}

/** 从第一页执行查询。 */
async function search(): Promise<void> {
  pageNum.value = 1;
  await loadRows();
}

/** 清空筛选条件并重新加载列表。 */
async function resetFilters(): Promise<void> {
  Object.assign(filters, {
    templateName: '',
    templateCategoryId: undefined,
    trainingTypeId: undefined,
    publishStatus: undefined,
    status: undefined
  });
  await search();
}

/** 从左侧树选择分类，并用已有分类条件刷新右侧模板列表。 */
async function selectCategory(category: TrainingTemplateCategoryTreeNode): Promise<void> {
  filters.templateCategoryId = category.templateCategoryId;
  pageNum.value = 1;
  await loadRows();
}

/** 清除分类条件，恢复查看全部模板。 */
async function showAllTemplates(): Promise<void> {
  filters.templateCategoryId = undefined;
  pageNum.value = 1;
  await loadRows();
}

/** 创建空的分类编辑表单。 */
function emptyCategoryForm(parentId = 0): CategoryForm {
  return { parentId, categoryName: '', sortOrder: 1, status: 1 };
}

/** 计算同一父级下现有节点数，新增时默认排在最后。 */
function countCategoryChildren(parentId: number): number {
  if (parentId === 0) return categoryTree.value.length;
  const findNode = (nodes: TrainingTemplateCategoryTreeNode[]): TrainingTemplateCategoryTreeNode | undefined => {
    for (const node of nodes) {
      if (node.templateCategoryId === parentId) return node;
      const found = findNode(node.children ?? []);
      if (found) return found;
    }
    return undefined;
  };
  return findNode(categoryTree.value)?.children?.length ?? 0;
}

/** 打开新增分类弹窗，按当前父级子节点数预设排序。 */
function openCategoryCreate(parentId = 0): void {
  Object.assign(categoryForm, emptyCategoryForm(parentId), { sortOrder: countCategoryChildren(parentId) + 1 });
  categoryDialogVisible.value = true;
}

/** 读取最新分类详情后编辑，保留接口要求的乐观锁版本。 */
async function openCategoryEdit(templateCategoryId: number): Promise<void> {
  const category: TrainingTemplateCategory = await getTrainingTemplateCategory(templateCategoryId);
  Object.assign(categoryForm, {
    templateCategoryId: category.templateCategoryId,
    parentId: category.parentId,
    categoryName: category.categoryName,
    sortOrder: category.sortOrder,
    status: category.status,
    version: category.version
  });
  categoryDialogVisible.value = true;
}

/** 使用现有分类接口保存新增或编辑结果，并刷新左树和右表。 */
async function saveCategory(): Promise<void> {
  const valid = await categoryFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  categorySubmitting.value = true;
  try {
    if (categoryForm.templateCategoryId && categoryForm.version !== undefined) {
      await updateTrainingTemplateCategory(categoryForm.templateCategoryId, {
        version: categoryForm.version,
        parentId: categoryForm.parentId,
        categoryName: categoryForm.categoryName,
        sortOrder: categoryForm.sortOrder,
        status: categoryForm.status
      });
      ElMessage.success('模板分类已更新');
    } else {
      await createTrainingTemplateCategory({
        parentId: categoryForm.parentId,
        categoryName: categoryForm.categoryName,
        sortOrder: categoryForm.sortOrder,
        status: categoryForm.status
      });
      ElMessage.success('模板分类已创建');
    }
    categoryDialogVisible.value = false;
    await Promise.all([loadCategoryTree(), loadRows()]);
  } finally {
    categorySubmitting.value = false;
  }
}

/** 删除分类前由后端校验子分类和模板引用，成功后同步刷新页面数据。 */
async function removeCategory(templateCategoryId: number): Promise<void> {
  await ElMessageBox.confirm('确认删除当前模板分类吗？存在子分类或模板引用时将无法删除。', '删除确认', { type: 'warning' });
  await deleteTrainingTemplateCategory(templateCategoryId);
  if (filters.templateCategoryId === templateCategoryId) filters.templateCategoryId = undefined;
  ElMessage.success('模板分类已删除');
  await Promise.all([loadCategoryTree(), loadRows()]);
}

/** 进入新增或现有模板工作台。 */
async function openWorkspace(trainingTemplateId?: number): Promise<void> {
  await router.push(
    trainingTemplateId ? `/admin/business-center/training/template/${trainingTemplateId}` : '/admin/business-center/training/template/create'
  );
}

/** 二次确认后删除草稿模板。 */
async function removeTemplate(template: TrainingTemplate): Promise<void> {
  await ElMessageBox.confirm(`确认删除模板“${template.templateName}”吗？`, '删除确认', {
    type: 'warning'
  });
  await deleteTrainingTemplate(template.trainingTemplateId);
  ElMessage.success('培训模板已删除');
  await loadRows();
}

onMounted(async () => {
  await Promise.all([loadRows(), loadCategoryTree(), loadTrainingTypes()]);
});
</script>

<template>
  <section class="admin-business-page template-page">
    <div class="master-detail-layout">
      <ElCard shadow="never" class="admin-business-page__table master-panel">
        <template #header>
          <div class="admin-business-page__table-heading">
            <strong>模板分类</strong>
            <ElButton v-if="canOperate('training:template:add')" type="primary" size="small" @click="openCategoryCreate(0)">新增分类</ElButton>
          </div>
        </template>
        <ElButton
          class="all-templates-button"
          :type="filters.templateCategoryId === undefined ? 'primary' : 'default'"
          plain
          @click="showAllTemplates"
        >
          全部模板
        </ElButton>
        <ElTree
          :data="categoryTree"
          :props="{ label: 'categoryName', children: 'children' }"
          node-key="templateCategoryId"
          :current-node-key="filters.templateCategoryId"
          highlight-current
          default-expand-all
          empty-text="暂无模板分类"
          class="category-tree"
          @node-click="selectCategory"
        >
          <template #default="{ node, data }">
            <span class="category-node">
              <span class="category-node__label" :title="node.label">{{ node.label }}</span>
              <span class="category-node__actions">
                <ElButton v-if="canOperate('training:template:add')" link type="primary" @click.stop="openCategoryCreate(data.templateCategoryId)">
                  新增子级
                </ElButton>
                <ElButton v-if="canOperate('training:template:edit')" link type="primary" @click.stop="openCategoryEdit(data.templateCategoryId)">
                  编辑
                </ElButton>
                <ElButton v-if="canOperate('training:template:delete')" link type="danger" @click.stop="removeCategory(data.templateCategoryId)">
                  删除
                </ElButton>
              </span>
            </span>
          </template>
        </ElTree>
      </ElCard>

      <ElCard class="admin-business-page__table detail-panel" shadow="never">
        <template #header>
          <div class="admin-business-page__table-heading">
            <strong>模板列表</strong>
            <div class="admin-business-page__table-actions">
              <ElButton v-if="canOperate('training:template:add')" type="primary" @click="openWorkspace()">新增模板</ElButton>
            </div>
          </div>
        </template>
        <ElForm inline class="template-filters" @submit.prevent>
          <ElFormItem label="模板名称">
            <ElInput v-model="filters.templateName" clearable placeholder="请输入模板名称" @keyup.enter="search" />
          </ElFormItem>
          <ElFormItem label="培训类型">
            <ElSelect v-model="filters.trainingTypeId" clearable filterable placeholder="请选择培训类型">
              <ElOption v-for="item in trainingTypeOptions" :key="item.trainingTypeId" :label="item.typeName" :value="item.trainingTypeId" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="发布状态">
            <ElSelect v-model="filters.publishStatus" clearable placeholder="请选择发布状态">
              <ElOption v-for="item in PUBLISH_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="启用状态">
            <ElSelect v-model="filters.status" clearable placeholder="请选择启用状态">
              <ElOption label="启用" :value="1" />
              <ElOption label="停用" :value="0" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="search">查询</ElButton>
            <ElButton @click="resetFilters">重置</ElButton>
          </ElFormItem>
        </ElForm>
        <ElTable v-loading="loading" :data="rows" row-key="trainingTemplateId" height="calc(100% - 105px)">
          <ElTableColumn label="序号" width="55" align="center">
            <template #default="{ $index }">
              {{ (pageNum - 1) * pageSize + $index + 1 }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="templateCode" label="模板编码" min-width="140" />
          <ElTableColumn prop="templateName" label="模板名称" min-width="220" show-overflow-tooltip />
          <ElTableColumn label="培训类型" min-width="150">
            <template #default="{ row }">{{ trainingTypeName(row.trainingTypeId) }}</template>
          </ElTableColumn>
          <ElTableColumn label="发布状态" width="110">
            <template #default="{ row }">
              <ElTag :type="publishTagType(row.publishStatus)">
                {{ publishStatusText(row.publishStatus) }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="启用状态" width="100">
            <template #default="{ row }">
              <ElTag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '停用' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="createTime" label="创建时间" min-width="170" />
          <ElTableColumn label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <ElButton
                v-if="canOperate(row.publishStatus === 'DRAFT' ? 'training:template:edit' : 'training:template:list')"
                link
                type="primary"
                @click="openWorkspace(row.trainingTemplateId)"
              >
                {{ row.publishStatus === 'DRAFT' ? '编辑编排' : '查看详情' }}
              </ElButton>
              <ElButton
                v-if="row.publishStatus === 'DRAFT' && canOperate('training:template:delete')"
                link
                type="danger"
                @click="removeTemplate(asTemplate(row))"
              >
                删除
              </ElButton>
            </template>
          </ElTableColumn>
          <template #empty>
            <ElEmpty description="暂无培训模板" />
          </template>
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
    </div>

    <ElDialog
      v-model="categoryDialogVisible"
      :title="categoryForm.templateCategoryId ? '编辑模板分类' : '新增模板分类'"
      width="520px"
      destroy-on-close
      :lock-scroll="false"
    >
      <ElForm ref="categoryFormRef" :model="categoryForm" :rules="categoryFormRules" label-width="96px">
        <ElFormItem label="父分类">
          <ElTreeSelect
            v-model="categoryForm.parentId"
            :data="categoryParentTree"
            :props="categoryProps"
            check-strictly
            placeholder="请选择父级分类"
          />
        </ElFormItem>
        <ElFormItem label="分类名称" prop="categoryName" required><ElInput v-model="categoryForm.categoryName" placeholder="请输入分类名称" /></ElFormItem>
        <ElFormItem label="排序"><ElInputNumber v-model="categoryForm.sortOrder" :min="0" controls-position="right" /></ElFormItem>
        <ElFormItem label="启用状态">
          <ElRadioGroup v-model="categoryForm.status">
            <ElRadio :value="1">启用</ElRadio>
            <ElRadio :value="0">停用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="categoryDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="categorySubmitting" @click="saveCategory">保存分类</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.template-page {
  min-height: 0;
}
.master-detail-layout {
  display: grid;
  grid-template-columns: minmax(280px, 16%) minmax(0, 1fr);
  gap: 16px;
  height: 100%;
}
.master-panel,
.detail-panel {
  min-width: 0;
}
.all-templates-button {
  width: 100%;
  margin-bottom: 8px;
  justify-content: flex-start;
}
.category-tree {
  background: transparent;
}
.category-node {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.category-node__label {
  overflow: hidden;
  color: var(--color-text-regular);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.category-node__actions {
  display: none;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}
:deep(.el-tree-node__content) {
  height: 36px;
  padding-right: 8px;
  border-radius: var(--radius-control);
}
:deep(.el-tree-node__content:hover) .category-node__actions,
:deep(.el-tree-node:focus > .el-tree-node__content) .category-node__actions,
:deep(.el-tree-node.is-current > .el-tree-node__content) .category-node__actions {
  display: flex;
}
:deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--color-primary-subtle);
}
.template-filters {
  margin-bottom: 12px;
}
@media (max-width: 960px) {
  .master-detail-layout {
    grid-template-columns: 1fr;
  }
}
</style>
