<script setup lang="ts">
import { getPersonalTeachers } from '@/api/learner-center';
import type { PersonalTeacher } from '@shared/types';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { deleteCourse, getCourseCategoryTree, getCoursePage, type CourseCategory, type CourseListItem } from '@/api/course-foundation';
import {
  COURSE_CONTENT_MODE_TEXT,
  COURSE_PUBLISH_STATUS_TEXT,
  type CourseContentMode,
  type CoursePublishStatus,
  type EnabledStatus
} from '@/constants/course';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import CourseWorkbench from '../workspace/index.vue';
import { buildCourseWorkspaceQuery, resolveCourseWorkspace, type CourseWorkspaceStage } from '../workspace/course-workspace-state.js';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const workspace = computed(() => resolveCourseWorkspace(route.query));
/** 由培训安排失败处理入口传入；只接受正整数，避免无效地址打开任意开课。 */
const openingOfferingId = computed(() => {
  const raw = Array.isArray(route.query.openingOfferingId) ? route.query.openingOfferingId[0] : route.query.openingOfferingId;
  const value = Number(raw);
  return Number.isInteger(value) && value > 0 ? value : undefined;
});
const loading = ref(false);
const teachers = ref<PersonalTeacher[]>([]);
async function searchTeachers(keyword: string): Promise<void> {
  teachers.value = (await getPersonalTeachers({ pageNum: 1, pageSize: 20, 'query.keyword': keyword })).list;
}
const courseList = ref<CourseListItem[]>([]);
const categoryTree = ref<CourseCategory[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const categoryProps = { label: 'categoryName', value: 'courseCategoryId', children: 'children' };
const filters = reactive({
  courseName: '',
  teacherUserId: undefined as number | undefined,
  categoryId: undefined as number | undefined,
  publishStatus: undefined as CoursePublishStatus | undefined,
  status: undefined as EnabledStatus | undefined
});

function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

function asCourse(row: unknown): CourseListItem {
  return row as CourseListItem;
}

function publishStatusText(status: CoursePublishStatus): string {
  return COURSE_PUBLISH_STATUS_TEXT[status] ?? status;
}

function publishTagType(status: CoursePublishStatus): 'success' | 'warning' | 'info' {
  if (status === 'PUBLISHED') return 'success';
  if (status === 'DRAFT') return 'warning';
  return 'info';
}

/** 使用 Contract 的 query.* 字段分页查询课程。 */
async function loadCourses(): Promise<void> {
  loading.value = true;
  try {
    const data = await getCoursePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(filters.teacherUserId ? { 'query.teacherUserId': filters.teacherUserId } : {}),
      ...(filters.courseName ? { 'query.courseName': filters.courseName } : {}),
      ...(filters.publishStatus ? { 'query.publishStatus': filters.publishStatus } : {}),
      ...(filters.status !== undefined ? { 'query.status': filters.status } : {}),
      ...(filters.categoryId !== undefined ? { 'query.categoryId': filters.categoryId } : {})
    });
    courseList.value = data.list;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

async function loadCategories(): Promise<void> {
  categoryTree.value = await getCourseCategoryTree();
}

async function searchCourses(): Promise<void> {
  pageNum.value = 1;
  await loadCourses();
}

async function resetFilters(): Promise<void> {
  Object.assign(filters, { teacherUserId: undefined, courseName: '', categoryId: undefined, publishStatus: undefined, status: undefined });
  await searchCourses();
}

async function openCreate(): Promise<void> {
  await router.push({ query: { mode: 'create', stage: 'basic' } });
}

async function openCourse(item: CourseListItem, stage: CourseWorkspaceStage = 'basic'): Promise<void> {
  await router.push({ query: buildCourseWorkspaceQuery(item.courseId, stage, item.courseName) });
}

/** 工作台阶段切换沿用当前标签名称，避免标签在切换阶段后退化为课程编号。 */
function getCurrentTabTitle(): string | undefined {
  return typeof route.query.tabTitle === 'string' ? route.query.tabTitle : undefined;
}

async function navigateWorkspace(courseId: number, stage: CourseWorkspaceStage): Promise<void> {
  await router.push({ query: buildCourseWorkspaceQuery(courseId, stage, getCurrentTabTitle()) });
}

/** 首次保存用 replace 将 create 地址替换为真实课程地址，避免后退回到失效的新建状态。 */
async function replaceWorkspace(courseId: number, stage: CourseWorkspaceStage): Promise<void> {
  await router.replace({ query: buildCourseWorkspaceQuery(courseId, stage, getCurrentTabTitle()) });
}

async function closeWorkspace(): Promise<void> {
  await router.push({ query: {} });
  await loadCourses();
}

async function confirmDelete(item: CourseListItem): Promise<void> {
  await ElMessageBox.confirm(`确认删除课程“${item.courseName}”吗？`, '二次确认', { type: 'warning' });
  await deleteCourse(item.courseId);
  ElMessage.success('课程已删除');
  await loadCourses();
}

onMounted(async () => {
  await Promise.all([loadCourses(), loadCategories()]);
});
</script>

<template>
  <CourseWorkbench
    v-if="workspace.mode !== 'list'"
    :course-id="workspace.courseId"
    :stage="workspace.stage"
    :opening-offering-id="openingOfferingId"
    @back="closeWorkspace"
    @navigate="navigateWorkspace"
    @replace-navigate="replaceWorkspace"
  />

  <section v-else class="admin-business-page course-center">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm :model="filters" inline>
        <ElFormItem label="名称／重点内容">
          <ElInput v-model="filters.courseName" placeholder="请输入名称或重点内容" clearable @keyup.enter="searchCourses" />
        </ElFormItem>
        <ElFormItem label="主讲教师">
          <ElSelect v-model="filters.teacherUserId" clearable filterable remote :remote-method="searchTeachers" placeholder="搜索主讲教师" style="width: 220px">
            <ElOption v-for="teacher in teachers" :key="teacher.teacherId" :value="teacher.teacherId" :label="teacher.teacherName" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="课程分类">
          <ElTreeSelect v-model="filters.categoryId" :data="categoryTree" :props="categoryProps" check-strictly clearable placeholder="全部分类" />
        </ElFormItem>
        <ElFormItem label="发布状态">
          <ElSelect v-model="filters.publishStatus" placeholder="全部发布状态" clearable>
            <ElOption v-for="(label, value) in COURSE_PUBLISH_STATUS_TEXT" :key="value" :label="label" :value="value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="启用状态">
          <ElSelect v-model="filters.status" placeholder="全部启用状态" clearable>
            <ElOption label="启用" :value="1" />
            <ElOption label="停用" :value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="searchCourses">查询</ElButton>
          <ElButton @click="resetFilters">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>课程列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canOperate('course:course:add')" type="primary" @click="openCreate">新增课程</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="courseList">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="courseCode" label="课程编码" min-width="130" />
        <ElTableColumn prop="courseName" label="名称／重点内容" min-width="220" show-overflow-tooltip />
        <ElTableColumn label="内容模式" width="110">
          <template #default="{ row }">{{ COURSE_CONTENT_MODE_TEXT[row.contentMode as CourseContentMode] }}</template>
        </ElTableColumn>
        <ElTableColumn label="发布状态" width="110">
          <template #default="{ row }">
            <ElTag :type="publishTagType(row.publishStatus)">{{ publishStatusText(row.publishStatus) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="启用状态" width="100">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="updateTime" label="更新时间" width="170" />
        <ElTableColumn label="操作" width="440" fixed="right">
          <template #default="{ row }">
            <ElButton v-if="canOperate('course:offering:list')" link type="primary" @click="router.push(`/admin/business-center/course/center/${asCourse(row).courseId}/statistics`)">学习统计</ElButton>
            <ElButton v-if="canOperate('course:course:edit')" link type="primary" @click="openCourse(asCourse(row))">详情编辑</ElButton>
            <ElButton v-if="canOperate('course:item:list')" link type="primary" @click="openCourse(asCourse(row), 'content')">内容编排</ElButton>
            <ElButton v-if="canOperate('course:course:list')" link type="primary" @click="openCourse(asCourse(row), 'publish')">发布版本</ElButton>
            <ElButton v-if="canOperate('course:offering:list')" link type="primary" @click="openCourse(asCourse(row), 'offering')">开课配置</ElButton>
            <ElButton v-if="canOperate('course:course:delete')" link type="danger" @click="confirmDelete(asCourse(row))">删除</ElButton>
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
          @current-change="loadCourses"
          @size-change="searchCourses"
        />
      </div>
    </ElCard>
  </section>
</template>

<style scoped lang="less"></style>
