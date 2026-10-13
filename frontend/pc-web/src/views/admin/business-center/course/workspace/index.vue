<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { getCourseDetail, type CourseDetail } from '@/api/course-foundation';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import CourseBasicPanel from '../components/CourseBasicPanel.vue';
import CourseContentPanel from '../components/CourseContentPanel.vue';
import CourseOfferingPanel from '../components/CourseOfferingPanel.vue';
import CoursePublishPanel from '../components/CoursePublishPanel.vue';
import type { CourseWorkspaceStage } from './course-workspace-state.js';

const props = defineProps<{
  courseId: number;
  stage: CourseWorkspaceStage;
  /** 培训失败处理入口指定的开课，进入开课阶段后自动打开。 */
  openingOfferingId?: number;
}>();
const emit = defineEmits<{
  back: [];
  navigate: [courseId: number, stage: CourseWorkspaceStage];
  replaceNavigate: [courseId: number, stage: CourseWorkspaceStage];
}>();

/** 四阶段名称固定用于页面导航和地址恢复。 */
const stages: Array<{ name: CourseWorkspaceStage; title: string; description: string; permission?: string }> = [
  { name: 'basic', title: '基础信息', description: '课程资料与学习设置' },
  { name: 'content', title: '内容编排', description: '章节、素材与直播', permission: 'course:item:list' },
  { name: 'publish', title: '发布版本', description: '版本检查与发布历史', permission: 'course:course:publish' },
  { name: 'offering', title: '开课配置', description: '创建开课并发布', permission: 'course:offering:list' }
];

const userStore = useUserStore();
const currentCourse = ref<CourseDetail>();
const courseTitle = computed(() => currentCourse.value?.courseName || (props.courseId > 0 ? `课程 #${props.courseId}` : '新增课程'));
const canAccessStage = (stage: CourseWorkspaceStage): boolean => {
  const permission = stages.find((item) => item.name === stage)?.permission;
  return !permission || hasPermission(userStore.userInfo, permission);
};

function navigate(stage: CourseWorkspaceStage): void {
  if ((props.courseId === 0 && stage !== 'basic') || !canAccessStage(stage)) return;
  emit('navigate', props.courseId, stage);
}

/** 基础信息保存后由课程中心替换查询参数，避免内部维护第二套路由状态。 */
function handleBasicSaved(courseId: number, goNext: boolean, detail: CourseDetail): void {
  currentCourse.value = detail;
  const targetStage = goNext && canAccessStage('content') ? 'content' : 'basic';
  if (props.courseId === 0) {
    emit('replaceNavigate', courseId, targetStage);
    return;
  }
  emit('navigate', courseId, targetStage);
}

/** 直接刷新工作台任一阶段时补载课程信息，保证标题、发布状态和开课前置条件正确。 */
async function loadCourse(): Promise<void> {
  if (!props.courseId) {
    currentCourse.value = undefined;
    return;
  }
  currentCourse.value = await getCourseDetail(props.courseId);
}

watch(() => props.courseId, loadCourse, { immediate: true });
</script>

<template>
  <section class="admin-detail-page">
    <header class="admin-detail-page__header">
      <div class="title-row">
        <div>
          <h1>{{ courseTitle }}</h1>
          <div style="display: flex; align-items: center; gap: 12px">
            <p>{{ courseId > 0 ? `课程 ID：${courseId}` : '保存基础信息后即可继续编排与发布' }}</p>
            <ElTag v-if="currentCourse" :type="currentCourse.publishStatus === 'PUBLISHED' ? 'success' : 'warning'" style="margin-top: 6px">
              {{ currentCourse.publishStatus === 'PUBLISHED' ? '已发布' : '草稿' }}
            </ElTag>
          </div>
        </div>
      </div>
      <ElButton plain @click="emit('back')">返回课程中心</ElButton>
    </header>

    <nav class="stage-nav admin-detail-page__steps course-workspace__steps" aria-label="课程制作阶段">
      <button
        v-for="(item, index) in stages"
        :key="item.name"
        type="button"
        class="stage-item"
        :class="{ 'is-active': stage === item.name }"
        :disabled="(!courseId && item.name !== 'basic') || !canAccessStage(item.name)"
        @click="navigate(item.name)"
      >
        <span class="stage-index">{{ index + 1 }}</span>
        <span>
          <b>{{ item.title }}</b>
          <small>{{ item.description }}</small>
        </span>
      </button>
    </nav>

    <main class="admin-detail-page__surface">
      <CourseBasicPanel v-if="stage === 'basic'" :course-id="courseId" @saved="handleBasicSaved" />
      <CourseContentPanel v-else-if="stage === 'content' && canAccessStage('content')" :course-id="courseId" @next="navigate('publish')" />
      <CoursePublishPanel
        v-else-if="stage === 'publish' && canAccessStage('publish')"
        :course-id="courseId"
        @course-changed="currentCourse = $event"
        @next="navigate('offering')"
      />
      <CourseOfferingPanel
        v-else-if="stage === 'offering' && canAccessStage('offering')"
        :course-id="courseId"
        embedded
        :course-published="Boolean(currentCourse?.currentVersionId)"
        :opening-offering-id="openingOfferingId"
      />
      <ElResult v-else icon="warning" title="暂无权限" sub-title="当前账号无权访问该课程阶段，请返回有权限的阶段。" />
    </main>
  </section>
</template>

<style scoped lang="less">
.course-workspace__steps :deep(.stage-item) {
  min-height: 64px;
}
</style>
