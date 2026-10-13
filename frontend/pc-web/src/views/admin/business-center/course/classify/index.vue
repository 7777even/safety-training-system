<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createCourseCategory,
  deleteCourseCategory,
  getCourseCategoryTree,
  updateCourseCategory,
  type CourseCategory
} from '@/api/course-foundation';
import type { EnabledStatus } from '@/constants/course';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const userStore = useUserStore();
const loading = ref(false);
const categoryTree = ref<CourseCategory[]>([]);
const categoryTreeSelect = ref<CourseCategory[]>([]);
const filters = reactive({ categoryName: '', status: undefined as EnabledStatus | undefined });
const dialogVisible = ref(false);
const formRef = ref<FormInstance>();

/** 分类编辑表单，更新时完整保留服务端乐观锁版本。 */
interface CategoryForm {
  courseCategoryId?: number;
  parentId: number;
  categoryName: string;
  description: string;
  sortOrder: number;
  status: EnabledStatus;
  version: number;
}

const categoryForm = reactive<CategoryForm>(newCategoryForm());
const rules: FormRules<CategoryForm> = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择上级分类', trigger: 'change' }]
};
const treeProps = { label: 'categoryName', value: 'courseCategoryId', children: 'children' };

function newCategoryForm(parentId = 0): CategoryForm {
  return { parentId, categoryName: '', description: '', sortOrder: 0, status: 1, version: 0 };
}

/** 入口展示由 profile 菜单按钮权限决定，真实鉴权仍由后端负责。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

function asCategory(row: unknown): CourseCategory {
  return row as CourseCategory;
}

/** 按名称与状态重新查询分类树。 */
async function loadCategories(): Promise<void> {
  loading.value = true;
  try {
    categoryTree.value = await getCourseCategoryTree({
      ...(filters.categoryName ? { categoryName: filters.categoryName } : {}),
      ...(filters.status !== undefined ? { status: filters.status } : {})
    });
    categoryTreeSelect.value = [
      ...categoryTree.value,
      { courseCategoryId: 0, categoryName: '顶级分类', children: [], parentId: 0, status: 1, version: 0 }
    ];
  } finally {
    loading.value = false;
  }
}

async function resetFilters(): Promise<void> {
  Object.assign(filters, { categoryName: '', status: undefined });
  await loadCategories();
}

function openCreate(parentId = 0): void {
  Object.assign(categoryForm, newCategoryForm(parentId));
  dialogVisible.value = true;
}

function openEdit(category: CourseCategory): void {
  Object.assign(categoryForm, {
    courseCategoryId: category.courseCategoryId,
    parentId: category.parentId,
    categoryName: category.categoryName,
    description: category.description ?? '',
    sortOrder: category.sortOrder ?? 0,
    status: category.status,
    version: category.version
  });
  dialogVisible.value = true;
}

/** 保存分类；编辑父级与排序即完成移动排序。 */
async function saveCategory(): Promise<void> {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  if (categoryForm.courseCategoryId === undefined) {
    await createCourseCategory({
      parentId: categoryForm.parentId,
      categoryName: categoryForm.categoryName,
      description: categoryForm.description,
      sortOrder: categoryForm.sortOrder,
      status: categoryForm.status
    });
  } else {
    await updateCourseCategory(categoryForm.courseCategoryId, {
      parentId: categoryForm.parentId,
      categoryName: categoryForm.categoryName,
      description: categoryForm.description,
      sortOrder: categoryForm.sortOrder,
      status: categoryForm.status,
      version: categoryForm.version
    });
  }
  dialogVisible.value = false;
  ElMessage.success('课程分类保存成功');
  await loadCategories();
}

async function confirmDelete(category: CourseCategory): Promise<void> {
  await ElMessageBox.confirm(`确认删除课程分类“${category.categoryName}”吗？`, '二次确认', { type: 'warning' });
  await deleteCourseCategory(category.courseCategoryId);
  ElMessage.success('课程分类已删除');
  await loadCategories();
}

onMounted(loadCategories);
</script>

<template>
  <section class="admin-business-page course-foundation">
    <ElCard class="admin-business-page__filters" shadow="never">
          <ElForm class="course-foundation__filters" :model="filters" inline>
            <ElFormItem label="分类名称">
              <ElInput v-model="filters.categoryName" placeholder="请输入分类名称" clearable />
            </ElFormItem>
            <ElFormItem label="状态">
              <ElSelect v-model="filters.status" placeholder="全部状态" clearable>
                <ElOption label="启用" :value="1" />
                <ElOption label="停用" :value="0" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" @click="loadCategories">查询</ElButton>
              <ElButton @click="resetFilters">重置</ElButton>
            </ElFormItem>
          </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table course-foundation__content" shadow="never">
          <template #header>
            <div class="admin-business-page__table-heading">
              <strong>课程分类</strong>
              <div class="admin-business-page__table-actions">
                <ElButton v-if="canOperate('course:category:add')" type="primary" @click="openCreate()">新增课程分类</ElButton>
              </div>
            </div>
          </template>
          <ElTable v-loading="loading" :data="categoryTree" row-key="courseCategoryId" default-expand-all>
            <ElTableColumn prop="categoryName" label="分类名称" min-width="220" />
            <ElTableColumn prop="description" label="描述" min-width="220" show-overflow-tooltip />
            <ElTableColumn prop="sortOrder" label="排序" width="90" />
            <ElTableColumn label="状态" width="90">
              <template #default="{ row }">
                <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <ElButton v-if="canOperate('course:category:add')" link type="primary" @click="openCreate(row.courseCategoryId)">新增下级</ElButton>
                <ElButton v-if="canOperate('course:category:edit')" link type="primary" @click="openEdit(asCategory(row))">编辑/移动</ElButton>
                <ElButton v-if="canOperate('course:category:delete')" link type="danger" @click="confirmDelete(asCategory(row))">删除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
    </ElCard>

    <ElDialog v-model="dialogVisible" :title="categoryForm.courseCategoryId === undefined ? '新增课程分类' : '编辑/移动课程分类'" width="560px" :lock-scroll="false">
      <ElForm ref="formRef" :model="categoryForm" :rules="rules" label-width="96px">
        <ElFormItem label="上级分类" prop="parentId">
          <ElTreeSelect
            v-model="categoryForm.parentId"
            :data="categoryTreeSelect"
            :props="treeProps"
            node-key="courseCategoryId"
            check-strictly
            clearable
            placeholder="请选择上级分类，留空为顶级"
            @clear="categoryForm.parentId = 0"
          />
        </ElFormItem>
        <ElFormItem label="分类名称" prop="categoryName">
          <ElInput v-model="categoryForm.categoryName" placeholder="请输入分类名称" maxlength="100" show-word-limit />
        </ElFormItem>
        <ElFormItem label="分类描述">
          <ElInput v-model="categoryForm.description" type="textarea" :rows="3" placeholder="请输入分类描述" />
        </ElFormItem>
        <ElFormItem label="排序序号">
          <ElInputNumber v-model="categoryForm.sortOrder" :min="0" placeholder="请输入排序序号" />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElRadioGroup v-model="categoryForm.status">
            <ElRadio :value="1">启用</ElRadio>
            <ElRadio :value="0">停用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveCategory">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.course-foundation__filters {
  :deep(.el-input),
  :deep(.el-select) {
    width: 214px;
  }
}
.course-foundation__tabs {
  width: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;

  :deep(.el-tabs__header) {
    flex: 0 0 auto;
  }

  :deep(.el-tabs__content) {
    display: flex;
    width: 100%;
    min-height: 0;
    min-width: 0;
    flex: 1;
    overflow: hidden;
  }

  :deep(.el-tab-pane) {
    display: flex;
    width: 100%;
    min-height: 0;
    min-width: 0;
    flex: 1;
    flex-direction: column;
    overflow: hidden;
  }
}
.course-foundation__content {
  width: 100%;
  min-height: 0;
  min-width: 0;
  flex: 1;
  margin-top: var(--space-2);

  :deep(.el-card__body) {
    display: flex;
    min-height: 0;
    min-width: 0;
    flex: 1;
    flex-direction: column;
  }

  :deep(.el-table) {
    min-height: 0;
    min-width: 0;
    flex: 1;
  }
}
</style>
