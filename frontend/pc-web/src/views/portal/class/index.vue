<script setup lang="ts">
import type {
  LearnerCourseCategory,
  LearnerCourseEnrollment,
  LearnerCourseOffering,
  LearnerCourseTaskItem,
  LearnerCourseTasks
} from '@shared/types';
import { orderCourseChapters } from '@shared/libs/course-chapters';
import { getAssessmentExecutionErrorMessage } from '@shared/libs/assessment-execution';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowRight, Reading, Star } from '@element-plus/icons-vue';
import {
  participateCourseLive,
  getLearnerCourseCategories,
  getLearnerCoursePage,
  getLearnerCourseCover,
  getLearnerCourseTasks,
  getLearnerOfferingPage,
  reportStudySession,
  selfEnrollCourse,
  startCourseExamAttempt,
  withdrawLearnerCourse
} from '@/api/learner-course';
import { getOrCreateLearningDeviceId } from '@/utils/learning-device';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';

const router = useRouter();
/** 管理员维护的公开分区/岗位分类，学员手动选择。 */
const courseCategories = ref<LearnerCourseCategory[]>([]);
const selectedCategoryId = ref<number>();
/** 选课大厅复用既有关键词查询，与分类组合筛选。 */
const offeringKeyword = ref('');
async function changeOfferingCategory(): Promise<void> {
  offeringPageNum.value = 1;
  await loadOfferings();
}

/** 当前用户课程列表。 */
const courses = ref<LearnerCourseEnrollment[]>([]);
/** 我的课程分页状态，筛选和翻页均以此状态查询。 */
const coursePageNum = ref(1);
const coursePageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const courseTotal = ref(0);
/** 当前用户可报名课程。 */
const offerings = ref<Awaited<ReturnType<typeof getLearnerOfferingPage>>['list']>([]);
/** 可报名课程分页状态，避免列表请求固定在首页。 */
const offeringPageNum = ref(1);
const offeringPageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const offeringTotal = ref(0);
/** 当前打开的学习任务详情。 */
const courseTasks = ref<LearnerCourseTasks | null>(null);
const loading = ref(false);
const detailLoading = ref(false);
const taskDrawerVisible = ref(false);
/** 当前选中的学习状态；空值表示展示全部课程。 */
const activeLearningStatus = ref<LearnerCourseEnrollment['learningStatus'] | ''>('');
/** 课程封面对象地址，使用受控封面接口获取而非暴露文件 ID。 */
const courseCoverUrls = ref<Record<number, string>>({});

/** 学习状态筛选项，与 Contract 枚举保持一致。 */
const learningStatusTabs: Array<{ label: string; value: LearnerCourseEnrollment['learningStatus'] | '' }> = [
  { label: '全部', value: '' },
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '学习中', value: 'LEARNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已过期', value: 'EXPIRED' }
];

/** 是否存在课程数据，用于渲染清晰空状态。 */
const hasCourses = computed(() => courses.value.length > 0);

/** 将 Contract 学习状态转换为中文。 */
function getStatusText(status: LearnerCourseEnrollment['learningStatus']): string {
  return { NOT_STARTED: '未开始', LEARNING: '学习中', COMPLETED: '已完成', EXPIRED: '已过期' }[status];
}

/** 学习状态标签配色；与考试中心任务状态共用同一套语义色，不另造第二套状态色。 */
function getStatusTagType(status: LearnerCourseEnrollment['learningStatus']): 'primary' | 'success' | 'warning' | 'info' {
  if (status === 'COMPLETED') return 'success';
  if (status === 'LEARNING') return 'primary';
  if (status === 'EXPIRED') return 'warning';
  return 'info';
}

/** 课程类型只在封面角标呈现，正文不再重复同一信息。 */
function getCourseTypeText(courseType: LearnerCourseEnrollment['courseType']): string {
  return courseType === 'REQUIRED' ? '必修课' : '选修课';
}

/** 选课方式直接显示中文，不把 OPEN/LINK 原始枚举暴露给学员。 */
function getEnrollmentTypeText(enrollmentType: LearnerCourseOffering['enrollmentType']): string {
  return enrollmentType === 'OPEN' ? '公开选课' : '链接选课';
}

/** 最近学习时间收敛到分钟；没有记录时显式说明，避免字段行留空。 */
function formatStudyTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '暂无学习记录';
}

/** 获取课程卡片的主操作文案，避免已完成课程仍显示“开始学习”。 */
function getCourseActionText(course: LearnerCourseEnrollment): string {
  return course.learningStatus === 'COMPLETED' ? '查看课程' : course.learningStatus === 'EXPIRED' ? '查看详情' : '进入学习';
}

/** 卡片内日期压缩为「月-日」，仅跨年时保留年份，避免一行塞进两个完整日期。 */
function compactDate(value?: string | null): string {
  if (!value) return '';
  const date = value.slice(0, 10);
  const [year, month, day] = date.split('-');
  return year === String(new Date().getFullYear()) ? `${month}-${day}` : date;
}

/** 开课期间收敛为一条文案，与考试中心卡片使用同一日期压缩口径。 */
function getPeriodText(start?: string | null, end?: string | null): string {
  const from = compactDate(start);
  const to = compactDate(end);
  if (!from && !to) return '长期开放';
  if (!from) return `截至 ${to}`;
  if (!to) return `${from} 起`;
  return `${from} ~ ${to}`;
}

/** 清理已创建的临时封面 URL，防止切换筛选后占用浏览器内存。 */
function clearCourseCoverUrls(): void {
  Object.values(courseCoverUrls.value).forEach((url) => globalThis.URL.revokeObjectURL(url));
  courseCoverUrls.value = {};
}

/** 加载当前卡片所需的受控课程封面；无封面或读取失败时保留统一占位。 */
async function loadCourseCovers(items: Array<Pick<LearnerCourseEnrollment, 'courseId' | 'coverUrl'>>): Promise<void> {
  clearCourseCoverUrls();
  const coverEntries = await Promise.all(
    [...new Map(items.filter((item) => item.coverUrl).map((item) => [item.courseId, item])).values()].map(async (item) => {
      try {
        return [item.courseId, globalThis.URL.createObjectURL(await getLearnerCourseCover(item.coverUrl!))] as const;
      } catch {
        return null;
      }
    })
  );
  courseCoverUrls.value = Object.fromEntries(coverEntries.filter((entry): entry is readonly [number, string] => entry !== null));
}

/** 同步读取本人课程与可报名课程。 */
async function loadCourses(): Promise<void> {
  loading.value = true;
  try {
    const [coursePage, offeringPage] = await Promise.all([
      getLearnerCoursePage({
        pageNum: coursePageNum.value,
        pageSize: coursePageSize.value,
        ...(activeLearningStatus.value ? { 'query.learningStatus': activeLearningStatus.value } : {})
      }),
      getLearnerOfferingPage({ pageNum: offeringPageNum.value, pageSize: offeringPageSize.value, 'query.categoryId': selectedCategoryId.value, 'query.keyword': offeringKeyword.value.trim() || undefined })
    ]);
    courses.value = coursePage.list;
    courseTotal.value = coursePage.total;
    offerings.value = offeringPage.list;
    offeringTotal.value = offeringPage.total;
    await loadCourseCovers([...coursePage.list, ...offeringPage.list]);
  } finally {
    loading.value = false;
  }
}

/** 仅翻页查询我的课程，保留可报名课程当前页。 */
async function loadEnrolledCourses(): Promise<void> {
  loading.value = true;
  try {
    const coursePage = await getLearnerCoursePage({
      pageNum: coursePageNum.value,
      pageSize: coursePageSize.value,
      ...(activeLearningStatus.value ? { 'query.learningStatus': activeLearningStatus.value } : {})
    });
    courses.value = coursePage.list;
    courseTotal.value = coursePage.total;
    await loadCourseCovers([...coursePage.list, ...offerings.value]);
  } finally {
    loading.value = false;
  }
}

/** 每页条数变化时回到首页，避免当前页超出新的总页数。 */
function changeCoursePageSize(): void {
  coursePageNum.value = 1;
  void loadEnrolledCourses();
}

/** 仅翻页查询可报名课程，避免重复请求本人课程列表。 */
async function loadOfferings(): Promise<void> {
  loading.value = true;
  try {
    const offeringPage = await getLearnerOfferingPage({ pageNum: offeringPageNum.value, pageSize: offeringPageSize.value, 'query.categoryId': selectedCategoryId.value, 'query.keyword': offeringKeyword.value.trim() || undefined });
    offerings.value = offeringPage.list;
    offeringTotal.value = offeringPage.total;
    await loadCourseCovers([...courses.value, ...offeringPage.list]);
  } finally {
    loading.value = false;
  }
}

/** 每页条数变化时回到首页，避免当前页超出新的总页数。 */
function changeOfferingPageSize(): void {
  offeringPageNum.value = 1;
  void loadOfferings();
}

/** 打开本人课程任务，不伪造资源预览或播放。 */
async function openTasks(course: LearnerCourseEnrollment): Promise<void> {
  if (course.enrollmentId > 0) {
    await router.push(`/portal/class/learning/${course.enrollmentId}`);
    return;
  }
  detailLoading.value = true;
  taskDrawerVisible.value = true;
  try {
    courseTasks.value = await getLearnerCourseTasks(course.enrollmentId);
  } catch {
    taskDrawerVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
}

/** 以学习任务为单位上报一次客户端会话，不提供媒体播放能力。 */
async function startTask(courseItemId: number): Promise<void> {
  if (!courseTasks.value) return;
  const item = courseTasks.value.chapters.flatMap(ch => ch.items).find(row => row.courseItemId === courseItemId);
  if (item?.itemType === 'LIVE') {
    const target = globalThis.window.open('', '_blank');
    if (target) target.opener = null;
    try {
      const result = await participateCourseLive(courseTasks.value.enrollmentId, courseItemId);
      if (target) target.location.href = result.liveUrl;
      else globalThis.window.location.assign(result.liveUrl);
      courseTasks.value = await getLearnerCourseTasks(courseTasks.value.enrollmentId);
    } catch (error) { target?.close(); ElMessage.error(error instanceof Error ? error.message : '直播参与失败'); }
    return;
  }
  const now = new Date().toISOString();
  await reportStudySession({
    enrollmentId: courseTasks.value.enrollmentId,
    courseItemId,
    clientTraceId: `pc-${courseTasks.value.enrollmentId}-${courseItemId}-${Date.now()}`,
    deviceType: 'PC',
    deviceId: getOrCreateLearningDeviceId(),
    startedTime: now,
    endedTime: now,
    validStudySeconds: 0,
    endReason: 'USER_EXIT'
  });
  ElMessage.success('已记录学习进入，请按任务要求完成学习。');
}

/** 从课程题库练习或课程测验内容项创建作答快照。 */
async function startExamTask(item: LearnerCourseTaskItem, retry = false): Promise<void> {
  if (!courseTasks.value || !item.learnable) return;
  if (!retry && item.scorePublishStatus === 'EFFECTIVE' && item.publishedExamAttemptId) {
    await router.push({
      name: 'PortalCourseAssessmentResult',
      params: { enrollmentId: courseTasks.value.enrollmentId, itemId: item.courseItemId, attemptId: item.publishedExamAttemptId },
      query: { itemTitle: item.itemTitle }
    });
    return;
  }
  if (!retry && item.activeExamAttemptId && item.examTaskStatus === 'WAITING_RESULT') {
    await router.push({
      name: 'PortalCourseAssessmentResult',
      params: { enrollmentId: courseTasks.value.enrollmentId, itemId: item.courseItemId, attemptId: item.activeExamAttemptId },
      query: { itemTitle: item.itemTitle }
    });
    return;
  }
  if (!retry && item.activeExamAttemptId && item.examTaskStatus !== 'WAITING_RESULT') {
    await router.push({
      name: 'PortalCourseAssessmentAttempt',
      params: { enrollmentId: courseTasks.value.enrollmentId, itemId: item.courseItemId, attemptId: item.activeExamAttemptId },
      query: { itemTitle: item.itemTitle }
    });
    return;
  }
  try {
    const attempt = await startCourseExamAttempt(courseTasks.value.enrollmentId, item.courseItemId, {
      clientAttemptToken: `pc-course-${courseTasks.value.enrollmentId}-${item.courseItemId}-${Date.now()}`,
      platform: 'PC'
    });
    await router.push({
      name: 'PortalCourseAssessmentAttempt',
      params: { enrollmentId: courseTasks.value.enrollmentId, itemId: item.courseItemId, attemptId: attempt.examAttemptId },
      query: { itemTitle: item.itemTitle }
    });
  } catch (error) {
    ElMessage.error(getAssessmentExecutionErrorMessage(error, '课程考核启动失败。'));
  }
}

/** 课程中心抽屉与学习详情使用同一套课程考核主操作，避免状态分支不一致。 */
function getExamTaskActionText(item: LearnerCourseTaskItem): string {
  if (item.scorePublishStatus === 'EFFECTIVE' && item.publishedExamAttemptId) return '查看结果';
  if (item.activeExamAttemptId && item.examTaskStatus === 'WAITING_RESULT') return '查看已交卷';
  if (item.activeExamAttemptId) return '继续作答';
  return item.itemType === 'QUIZ' ? '开始测验' : '开始练习';
}

/** 将课程考试项的后端任务事实转换为学员可理解的状态文案。 */
function getTaskStatusText(item: LearnerCourseTaskItem): string {
  if (item.itemType !== 'QUESTION_BANK' && item.itemType !== 'QUIZ') return item.progressStatus === 'COMPLETED' ? '已完成' : '待学习';
  if (item.scorePublishStatus === 'EFFECTIVE') return `结果已生效：${item.finalScore ?? '-'} 分，${item.isPassed === 1 ? '已及格' : '未及格'}`;
  if (item.examTaskStatus === 'WAITING_RESULT') return '已交卷，等待阅卷完成';
  if (item.examTaskStatus === 'IN_PROGRESS') return '正在作答';
  return '待参加考试';
}

/** 按 Contract 允许条件确认退选。 */
async function withdrawCourse(course: LearnerCourseEnrollment): Promise<void> {
  await ElMessageBox.confirm('退选后将无法继续该课程学习，是否确认？', '退选课程', { type: 'warning' });
  await withdrawLearnerCourse(course.enrollmentId);
  ElMessage.success('已退选课程。');
  await loadCourses();
}

/** 自主报名当前可报名开课。 */
async function enrollCourse(offeringId: number): Promise<void> {
  await selfEnrollCourse(offeringId);
  ElMessage.success('选课成功，已加入我的课程。');
  await loadCourses();
}

/** 切换学习状态后按 Contract 重新查询本人课程。 */
async function changeLearningStatus(status: LearnerCourseEnrollment['learningStatus'] | ''): Promise<void> {
  if (activeLearningStatus.value === status) return;
  activeLearningStatus.value = status;
  coursePageNum.value = 1;
  await loadEnrolledCourses();
}

onMounted(async () => {
  courseCategories.value = await getLearnerCourseCategories();
  await loadCourses();
});
onBeforeUnmount(clearCourseCoverUrls);
</script>

<template>
  <section v-loading="loading" class="portal-list-page learner-course-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY LEARNING</p>
        <h1>我的课程</h1>
        <p>查看本人报名课程、学习任务和完成进度。</p>
      </div>
      <div class="portal-heading__actions">
        <RouterLink class="portal-entry-link" to="/user-center/courses">
          <ElIcon><Star /></ElIcon>
          管理关注课程
          <ElIcon><ArrowRight /></ElIcon>
        </RouterLink>
        <ElButton @click="loadCourses">刷新</ElButton>
      </div>
    </header>

    <nav class="portal-list-page__tabs learning-status-tabs" aria-label="课程学习状态筛选">
      <button
        v-for="tab in learningStatusTabs"
        :key="tab.value || 'ALL'"
        type="button"
        class="portal-list-page__tab learning-status-tabs__item"
        :class="{ 'portal-list-page__tab--active learning-status-tabs__item--active': activeLearningStatus === tab.value }"
        @click="changeLearningStatus(tab.value)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <section v-if="!hasCourses && !loading" class="portal-list-state course-empty-state">
      <ElEmpty description="暂无已报名课程，可在下方选择课程" />
    </section>
    <div v-else class="portal-list-grid course-grid">
      <ElCard v-for="course in courses" :key="course.enrollmentId" class="portal-list-card course-card" shadow="never">
        <div class="portal-list-card__cover">
          <img v-if="courseCoverUrls[course.courseId]" :src="courseCoverUrls[course.courseId]" :alt="`${course.courseName}课程封面`" />
          <div v-else class="portal-list-card__cover-placeholder">
            <ElIcon><Reading /></ElIcon>
          </div>
          <span class="course-card__badge">{{ getCourseTypeText(course.courseType) }}</span>
        </div>
        <div class="portal-list-card__content course-card__content">
          <div class="portal-list-card__header course-card__header">
            <div class="course-card__identity">
              <h2 :title="course.courseName">{{ course.courseName }}</h2>
              <p :title="course.offeringName">{{ course.offeringName }}</p>
            </div>
            <ElTag :type="getStatusTagType(course.learningStatus)" size="small">
              {{ getStatusText(course.learningStatus) }}
            </ElTag>
          </div>
          <dl class="course-card__facts">
            <div>
              <dt>开放时间</dt>
              <dd>{{ getPeriodText(course.startTime, course.endTime) }}</dd>
            </div>
            <div>
              <dt>最近学习</dt>
              <dd>{{ formatStudyTime(course.lastStudyTime) }}</dd>
            </div>
          </dl>
          <div class="course-card__progress">
            <div class="course-card__progress-head">
              <span>学习进度</span>
              <strong>{{ course.progressPercent }}%</strong>
            </div>
            <ElProgress :percentage="course.progressPercent" :stroke-width="6" :show-text="false" />
          </div>
          <div class="portal-list-card__actions course-card__actions">
            <ElButton type="primary" @click="openTasks(course)">{{ getCourseActionText(course) }}</ElButton>
            <ElButton
              v-if="course.courseType === 'ELECTIVE' && ['NOT_STARTED', 'LEARNING'].includes(course.learningStatus)"
              @click="withdrawCourse(course)"
            >
              退选课程
            </ElButton>
          </div>
        </div>
      </ElCard>
    </div>
    <div v-if="courseTotal > 0" class="portal-list-page__pagination course-pagination">
      <ElPagination
        v-model:current-page="coursePageNum"
        v-model:page-size="coursePageSize"
        :page-sizes="LIST_PAGE_SIZES"
        :total="courseTotal"
        layout="total, sizes, prev, pager, next"
        @current-change="loadEnrolledCourses"
        @size-change="changeCoursePageSize"
      />
    </div>

    <section class="offering-section">
      <div class="offering-head">
        <div class="portal-section-head">
          <h2 class="portal-section-title">可报名课程</h2>
          <span class="portal-section-hint">仅展示当前用户可自主报名的开课</span>
        </div>
        <ElTreeSelect
          v-model="selectedCategoryId"
          class="offering-filter"
          :data="courseCategories"
          :props="{ label: 'categoryName', children: 'children' }"
          node-key="courseCategoryId"
          value-key="courseCategoryId"
          check-strictly
          clearable
          placeholder="全部分区／岗位分类"
          @change="changeOfferingCategory"
        />
      </div>
      <div class="portal-toolbar">
        <ElInput v-model="offeringKeyword" class="offering-filter" placeholder="请输入课程名称" clearable @keyup.enter="changeOfferingCategory" @clear="changeOfferingCategory" />
        <ElButton type="primary" @click="changeOfferingCategory">查询</ElButton>
      </div>
      <p v-if="offerings.length === 0 && !loading" class="offering-empty">当前暂无可报名课程</p>
      <div v-else class="portal-list-grid course-grid">
        <ElCard v-for="offering in offerings" :key="offering.offeringId" class="portal-list-card course-card" shadow="never">
          <div class="portal-list-card__cover">
            <img v-if="courseCoverUrls[offering.courseId]" :src="courseCoverUrls[offering.courseId]" :alt="`${offering.courseName}课程封面`" />
            <div v-else class="portal-list-card__cover-placeholder">
              <ElIcon><Reading /></ElIcon>
            </div>
            <span class="course-card__badge course-card__badge--enroll">可报名</span>
          </div>
          <div class="portal-list-card__content course-card__content">
            <div class="portal-list-card__header course-card__header">
              <div class="course-card__identity">
                <h3 :title="offering.courseName">{{ offering.courseName }}</h3>
                <p :title="offering.offeringName">{{ offering.offeringName }}</p>
              </div>
            </div>
            <dl class="course-card__facts">
              <div>
                <dt>课程类型</dt>
                <dd>{{ getCourseTypeText(offering.courseType) }}</dd>
              </div>
              <div>
                <dt>选课方式</dt>
                <dd>{{ getEnrollmentTypeText(offering.enrollmentType) }}</dd>
              </div>
              <div>
                <dt>开放时间</dt>
                <dd>{{ getPeriodText(offering.startTime, offering.endTime) }}</dd>
              </div>
            </dl>
            <div class="portal-list-card__actions course-card__actions">
              <ElButton type="primary" @click="enrollCourse(offering.offeringId)">立即选课</ElButton>
            </div>
          </div>
        </ElCard>
      </div>
      <div v-if="offeringTotal > 0" class="portal-list-page__pagination offering-pagination">
        <ElPagination
          v-model:current-page="offeringPageNum"
          v-model:page-size="offeringPageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="offeringTotal"
          layout="total, sizes, prev, pager, next"
          @current-change="loadOfferings"
          @size-change="changeOfferingPageSize"
        />
      </div>
    </section>

    <ElDrawer v-model="taskDrawerVisible" title="学习任务" size="560px" :lock-scroll="false">
      <div v-loading="detailLoading">
        <template v-if="courseTasks">
          <h2>{{ courseTasks.courseName }}</h2>
          <p class="task-summary">{{ courseTasks.offeringName }} · 总进度 {{ courseTasks.progressPercent }}%</p>
          <section v-for="chapter in orderCourseChapters(courseTasks.chapters)" :style="{ marginLeft: chapter.depth * 16 + 'px' }" :key="chapter.chapterId" class="chapter-panel">
            <h3>{{ chapter.sortOrder }}. {{ chapter.chapterName }}</h3>
            <div v-for="item in chapter.items" :key="item.courseItemId" class="task-item">
              <div>
                <strong>{{ item.itemTitle }}</strong>
                <p>{{ item.isRequired === 1 ? '必修' : '选修' }} · {{ getTaskStatusText(item) }}</p>
              </div>
              <ElButton v-if="item.canRetake" type="primary" @click="startExamTask(item, true)">补测一次</ElButton>
              <ElButton
                :disabled="!item.learnable"
                @click="item.itemType === 'QUESTION_BANK' || item.itemType === 'QUIZ' ? startExamTask(item) : startTask(item.courseItemId)"
              >
                {{
                  item.learnable
                    ? item.itemType === 'QUESTION_BANK' || item.itemType === 'QUIZ'
                      ? getExamTaskActionText(item)
                      : '进入任务'
                    : '暂不可学习'
                }}
              </ElButton>
            </div>
          </section>
          <ElAlert title="当前版本不提供在线预览或媒体播放，请按任务要求在已支持的学习终端完成。" type="info" :closable="false" />
        </template>
      </div>
    </ElDrawer>
  </section>
</template>

<style scoped lang="less">
.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.task-summary,
.task-item p,
.portal-section-hint {
  color: var(--color-text-secondary);
  font-size: 13px;
}
/* 课程卡片与考试中心保持同一套信息呈现结构：封面状态角标、对齐标签行、独立进度块。 */
.course-card__content {
  gap: 14px;
}
.course-card .portal-list-card__cover img {
  object-fit: cover;
}
.course-card .portal-list-card__cover-placeholder {
  background: linear-gradient(135deg, #eef4ff, #dbeafe);
}
.course-card .portal-list-card__cover-placeholder .el-icon {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 50%;
  background: rgb(255 255 255 / 82%);
  box-shadow: 0 2px 10px rgb(37 99 235 / 14%);
  font-size: 22px;
}
/* 封面类型角标与考试中心同款：白底胶囊 + 主色文字，浮在封面左上角。 */
.course-card__badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 2px 10px;
  border: 1px solid rgb(255 255 255 / 76%);
  border-radius: 999px;
  background: rgb(255 255 255 / 88%);
  box-shadow: 0 1px 4px rgb(15 23 42 / 12%);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}
.course-card__badge--enroll {
  font-weight: 600;
}
/* 状态标签回到标题行右侧：与考试中心一致，标签不收缩，课程名用剩余宽度两行截断。 */
.course-card__header {
  align-items: flex-start;
  gap: 10px;
}
.course-card__header :deep(.el-tag) {
  flex: 0 0 auto;
  border: 0;
}
/* 标题块固定两行高度，使同一行卡片的标签行与操作区垂直对齐。 */
.course-card__identity {
  min-width: 0;
}
.course-card__identity h2,
.course-card__identity h3 {
  display: -webkit-box;
  // height: 48px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  text-overflow: ellipsis;
}
.course-card__identity p {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
/* 字段按「标签列 + 取值列」对齐成行，文字规格与考试中心卡片完全一致。 */
.course-card__facts {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--color-bg-subtle);
}
.course-card__facts div {
  display: grid;
  align-items: baseline;
  gap: 12px;
  grid-template-columns: 60px minmax(0, 1fr);
}
.course-card__facts dt {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
.course-card__facts dd {
  margin: 0;
  color: var(--color-text-regular);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  overflow-wrap: anywhere;
}
/* 进度区与考试中心得分条同构：上方发丝虚线分隔，结论行在上、进度条在下。 */
.course-card__progress {
  display: grid;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
}
.course-card__progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.course-card__progress-head strong {
  color: var(--color-primary);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  line-height: 22px;
}
/* 操作区补一条发丝分隔线，让按钮与信息区形成明确分组。 */
.course-card__actions {
  border-top: 1px solid var(--color-border);
}
.offering-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
/* 分区头左右分栏：标题与说明左对齐，筛选器固定宽度靠右，避免标题被长占位符挤成两行。 */
.offering-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.offering-head .portal-section-title {
  font-size: 20px;
  white-space: nowrap;
}
.offering-filter {
  width: 240px;
  flex: none;
}
.task-summary {
  margin: 8px 0 20px;
}
.chapter-panel {
  padding: 16px 0;
  border-top: 1px solid var(--color-border);
}
.chapter-panel h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.task-item {
  padding: 12px;
  border-radius: 8px;
  background: var(--color-bg-page);
}
.task-item p {
  margin: 5px 0 0;
}
.offering-empty {
  margin: 0;
  padding: 14px 16px;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
.offering-pagination {
  display: flex;
  justify-content: flex-end;
}
.course-pagination {
  display: flex;
  justify-content: flex-end;
}
</style>
