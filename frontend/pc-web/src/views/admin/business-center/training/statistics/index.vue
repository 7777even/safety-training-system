<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeft } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import {
  getTrainingPlanStatistics,
  getTrainingProgressPeople,
  type TrainingPlanStatistics,
  type TrainingProgressPerson
} from '@/api/training-statistics';
import { getCourseStatisticsPeople, type CourseStatisticsPerson } from '@/api/course-statistics';
/** 培训记录页：计划结果、人员必修进度及全部可见课程记录。 */
const route = useRoute();
const store = useUserStore();
const canReadCourses = computed(() => hasPermission(store.userInfo, 'course:offering:list'));
const tab = ref('plans');
const loading = ref(false);
const planName = ref('');
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const total = ref(0);
const plans = ref<TrainingPlanStatistics[]>([]);
const selected = ref<number>(Number(route.query.trainingPlanId) || 0);
const selectedName = ref('计划进度');
const people = ref<TrainingProgressPerson[]>([]);
const peoplePage = ref(1);
const peoplePageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const peopleTotal = ref(0);
const completedOnly = ref(false);
const keyword = ref('');
const peopleLoading = ref(false);
const courses = ref<CourseStatisticsPerson[]>([]);
const coursePage = ref(1);
const coursePageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const courseTotal = ref(0);
const courseKeyword = ref('');
/** 人员进度以对话框承载，避免计划列表被下钻结果挤压缩短。 */
const peopleDialog = computed(() => selected.value > 0);
/**
 * 将后端状态枚举显示为中文，枚举来源：
 * 计划发布 DRAFT/PUBLISHED/RUNNING/ENDED/CANCELLED/ARCHIVED；
 * 课程学习 NOT_STARTED/LEARNING/COMPLETED/EXPIRED；
 * 参训结果 PENDING/IN_PROGRESS/PENDING_REVIEW/PENDING_PUBLISH/COMPLETED/FAILED/INCOMPLETE/EXPIRED/PASSED/WITHDRAWN。
 * 新增枚举须同步此处，避免页面直接露出英文状态词。
 */
function status(value: string): string {
  return (
    (
      {
        PASSED: '已通过',
        COMPLETED: '已完成',
        FAILED: '未通过',
        INCOMPLETE: '未完成',
        PENDING: '待开始',
        IN_PROGRESS: '进行中',
        PENDING_REVIEW: '待批阅',
        PENDING_PUBLISH: '待发布',
        EXPIRED: '已过期',
        LEARNING: '学习中',
        NOT_STARTED: '未开始',
        WITHDRAWN: '已退课',
        DRAFT: '草稿',
        PUBLISHED: '已发布',
        RUNNING: '执行中',
        ENDED: '已结束',
        CANCELLED: '已取消',
        ARCHIVED: '已归档'
      } as Record<string, string>
    )[value] ?? value
  );
}
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info';
/** 计划发布状态色板：草稿中性、已发布主色、执行中告警、已结束成功、已取消危险。 */
function publishTag(value: string): TagType {
  return ({ DRAFT: 'info', PUBLISHED: 'primary', RUNNING: 'warning', ENDED: 'success', CANCELLED: 'danger', ARCHIVED: 'info' } as Record<string, TagType>)[value] ?? 'info';
}
/** 参训结果色板：通过/完成成功、未通过危险、其余中性或告警。 */
function resultTag(value: string): TagType {
  return ({ PASSED: 'success', COMPLETED: 'success', FAILED: 'danger', INCOMPLETE: 'warning', WITHDRAWN: 'info' } as Record<string, TagType>)[value] ?? 'info';
}
/** 课程学习状态色板：未开始中性、学习中主色、已完成成功、已过期告警，避免与结果状态混用同一种灰。 */
function learningTag(value: string): TagType {
  return ({ NOT_STARTED: 'info', LEARNING: 'primary', COMPLETED: 'success', EXPIRED: 'warning' } as Record<string, TagType>)[value] ?? 'info';
}
/** 计划完成率用于进度条，缺省按 0 处理并夹在 0～100。 */
function clampRate(rate: number): number {
  return Math.max(0, Math.min(100, rate));
}
/** 分页计划统计只读取事实，不调用结果同步。 */
async function loadPlans(): Promise<void> {
  loading.value = true;
  try {
    const page = await getTrainingPlanStatistics({ planName: planName.value, pageNum: pageNum.value, pageSize: pageSize.value });
    plans.value = page.list;
    total.value = page.total;
  } catch {
    ElMessage.error('计划统计加载失败，请重试');
  } finally {
    loading.value = false;
  }
}
/** 人员筛选同时适用于全部进度和完成人员。 */
async function loadPeople(): Promise<void> {
  if (!selected.value) return;
  peopleLoading.value = true;
  try {
    const page = await getTrainingProgressPeople(selected.value, {
      completedOnly: completedOnly.value,
      learnerKeyword: keyword.value,
      pageNum: peoplePage.value,
      pageSize: peoplePageSize.value
    });
    people.value = page.list;
    peopleTotal.value = page.total;
  } catch {
    ElMessage.error('计划进度加载失败，请重试');
  } finally {
    peopleLoading.value = false;
  }
}
/** 切换计划清除上一个计划的筛选。 */
function drill(value: unknown): void {
  const row = value as TrainingPlanStatistics;
  selected.value = row.trainingPlanId;
  selectedName.value = row.planName;
  peoplePage.value = 1;
  keyword.value = '';
  completedOnly.value = false;
  void loadPeople();
}
/** 关闭人员对话框并复位筛选，避免下次打开残留。 */
function closePeople(): void {
  selected.value = 0;
  keyword.value = '';
  completedOnly.value = false;
}
/** 计划名称查询回到第一页。 */
function searchPlans(): void {
  pageNum.value = 1;
  void loadPlans();
}
/** 人员筛选查询回到第一页。 */
function searchPeople(): void {
  peoplePage.value = 1;
  void loadPeople();
}
/** 所有有权查看的课程开班记录保留每次报名，避免只展示本计划课程。 */
async function loadCourses(): Promise<void> {
  if (!canReadCourses.value) return;
  loading.value = true;
  try {
    const page = await getCourseStatisticsPeople({ learnerKeyword: courseKeyword.value, pageNum: coursePage.value, pageSize: coursePageSize.value });
    courses.value = page.list;
    courseTotal.value = page.total;
  } catch {
    ElMessage.error('课程培训记录加载失败，请重试');
  } finally {
    loading.value = false;
  }
}
/** 切到课程页签时才加载，避免首屏多余请求。 */
function onTabChange(name: string | number): void {
  if (name === 'courses') void loadCourses();
}
/** 课程记录查询回到第一页。 */
function searchCourses(): void {
  coursePage.value = 1;
  void loadCourses();
}
onMounted(async () => {
  await loadPlans();
  await loadPeople();
});
</script>
<template>
  <section class="admin-detail-page training-statistics" v-loading="loading">
    <header class="admin-detail-page__header">
      <div>
        <h1>培训统计与记录</h1>
        <p>完成情况以正式结果为准（已通过、已完成）；执行中的计划按实时数据推导，已结束的计划读取冻结结果。必修进度仅供参考，不替代通过结论。</p>
      </div>
      <ElButton :icon="ArrowLeft" @click="$router.back()">返回</ElButton>
    </header>

    <section class="ws-hero">
      <div class="ws-hero__main">
        <p class="ws-hero__eyebrow">TRAINING STATISTICS</p>
        <div class="ws-hero__title-row">
          <h2 class="ws-hero__title">计划完成情况与培训记录</h2>
          <ElTag type="info" effect="plain">共 {{ total }} 个计划</ElTag>
        </div>
        <div class="ws-hero__meta">
          <span class="ws-hero__chip">完成率按参训人数计算</span>
          <span>点击计划行可查看该计划的必修进度与完成人员明细。</span>
        </div>
      </div>
    </section>

    <ElTabs class="admin-page-tabs admin-page-tabs--fill" v-model="tab" @tab-change="onTabChange">
      <ElTabPane label="计划统计与进度" name="plans">
        <ElCard class="admin-business-page__filters" shadow="never">
          <ElForm inline @submit.prevent>
            <ElFormItem label="计划名称"><ElInput v-model="planName" clearable placeholder="请输入培训计划名称" @keyup.enter="searchPlans" /></ElFormItem>
            <ElFormItem><ElButton type="primary" @click="searchPlans">查询</ElButton></ElFormItem>
          </ElForm>
        </ElCard>
        <ElCard class="admin-business-page__table" shadow="never">
          <template #header>
            <div class="admin-business-page__table-heading">
              <strong>培训计划完成情况</strong>
              <span class="ws-section__extra">共 {{ total }} 个计划</span>
            </div>
          </template>
          <ElTable :data="plans" row-class-name="is-clickable" @row-click="drill">
            <ElTableColumn prop="planName" label="培训计划" show-overflow-tooltip />
            <ElTableColumn label="状态" width="110">
              <template #default="{ row }"><ElTag :type="publishTag(row.publishStatus)">{{ status(row.publishStatus) }}</ElTag></template>
            </ElTableColumn>
            <ElTableColumn prop="expectedCount" label="参训人数" width="100" />
            <ElTableColumn prop="completedCount" label="已完成" width="100" />
            <ElTableColumn prop="failedCount" label="未通过" width="100" />
            <ElTableColumn prop="incompleteCount" label="未完成" width="100" />
            <ElTableColumn label="完成率" min-width="160">
              <template #default="{ row }">
                <div class="ws-bar">
                  <div class="ws-bar__track">
                    <div class="ws-bar__fill" :style="{ width: `${clampRate(row.completionRate)}%` }" />
                  </div>
                  <div class="ws-bar__legend">
                    <span>{{ row.completionRate }}%</span>
                  </div>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="130">
              <template #default="{ row }"><ElButton link type="primary" @click.stop="drill(row)">进度与完成人员</ElButton></template>
            </ElTableColumn>
            <template #empty><ElEmpty description="暂无培训计划统计" /></template>
          </ElTable>
          <div class="admin-business-page__pagination">
            <ElPagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="LIST_PAGE_SIZES"
              layout="total, sizes, prev, pager, next"
              @current-change="loadPlans"
              @size-change="searchPlans"
            />
          </div>
        </ElCard>
      </ElTabPane>

      <ElTabPane v-if="canReadCourses" label="全部课程培训记录" name="courses">
        <ElCard class="admin-business-page__filters" shadow="never">
          <ElForm inline @submit.prevent>
            <ElFormItem label="学员"><ElInput v-model="courseKeyword" clearable placeholder="姓名或账号" @keyup.enter="searchCourses" /></ElFormItem>
            <ElFormItem><ElButton type="primary" @click="searchCourses">查询</ElButton></ElFormItem>
          </ElForm>
        </ElCard>
        <ElCard class="admin-business-page__table" shadow="never">
          <template #header>
            <div class="admin-business-page__table-heading">
              <strong>全部课程培训记录</strong>
              <span class="ws-section__extra">共 {{ courseTotal }} 条</span>
            </div>
          </template>
          <p class="table-note">按个人及开班保留每次报名；时长为有效学习秒数，次数为有效学习会话。</p>
          <ElTable :data="courses">
            <ElTableColumn prop="learnerName" label="学员" />
            <ElTableColumn prop="courseName" label="课程" show-overflow-tooltip />
            <ElTableColumn prop="offeringName" label="开班" show-overflow-tooltip />
            <ElTableColumn label="状态" width="110">
              <template #default="{ row }"><ElTag :type="learningTag(row.learningStatus)">{{ status(row.learningStatus) }}</ElTag></template>
            </ElTableColumn>
            <ElTableColumn prop="totalStudySeconds" label="有效秒数" width="100"/>
            <ElTableColumn prop="studyCount" label="学习次数" width="100"/>
            <ElTableColumn label="进度" width="100">
              <template #default="{ row }">{{ row.progressPercent ?? 0 }}%</template>
            </ElTableColumn>
            <template #empty><ElEmpty description="暂无课程培训记录" /></template>
          </ElTable>
          <div class="admin-business-page__pagination">
            <ElPagination
              v-model:current-page="coursePage"
              v-model:page-size="coursePageSize"
              :total="courseTotal"
              :page-sizes="LIST_PAGE_SIZES"
              layout="total, sizes, prev, pager, next"
              @current-change="loadCourses"
              @size-change="searchCourses"
            />
          </div>
        </ElCard>
      </ElTabPane>
    </ElTabs>

    <ElDialog :model-value="peopleDialog" :title="`${selectedName} · 进度与完成人员`" width="1000px" @close="closePeople">
      <ElForm inline @submit.prevent class="people-filter">
        <ElFormItem label="学员"><ElInput v-model="keyword" clearable placeholder="姓名或账号" @keyup.enter="searchPeople" /></ElFormItem>
        <ElFormItem><ElCheckbox v-model="completedOnly">仅查看完成人员</ElCheckbox></ElFormItem>
        <ElFormItem><ElButton type="primary" @click="searchPeople">查询</ElButton></ElFormItem>
      </ElForm>
      <ElTable v-loading="peopleLoading" :data="people">
        <ElTableColumn type="expand">
          <template #default="{ row }">
            <ElTable :data="row.items">
              <ElTableColumn prop="contentName" label="培训内容" />
              <ElTableColumn label="结果">
                <template #default="{ row: item }">
                  <ElTag :type="resultTag(item.resultStatus)" effect="plain">{{ status(item.resultStatus) }}</ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="effectiveDurationSeconds" label="有效学习秒数" />
              <ElTableColumn prop="score" label="成绩" />
              <ElTableColumn prop="completedTime" label="完成时间" />
              <template #empty><ElEmpty description="暂无内容项明细" /></template>
            </ElTable>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="learnerName" label="学员" />
        <ElTableColumn prop="learnerCode" label="账号" />
        <ElTableColumn label="正式结果">
          <template #default="{ row }"><ElTag :type="resultTag(row.resultStatus)">{{ status(row.resultStatus) }}</ElTag></template>
        </ElTableColumn>
        <ElTableColumn label="必修完成" min-width="150">
          <template #default="{ row }">{{ row.completedContentCount }}/{{ row.requiredCount }}（{{ row.progressPercent }}%）</template>
        </ElTableColumn>
        <ElTableColumn prop="onlineHours" label="线上学时" />
        <ElTableColumn prop="offlineHours" label="线下学时" />
        <ElTableColumn label="来源">
          <template #default="{ row }">{{ row.finalized ? '冻结结果' : '实时事实' }}</template>
        </ElTableColumn>
        <template #empty><ElEmpty description="暂无符合条件的人员" /></template>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="peoplePage"
          v-model:page-size="peoplePageSize"
          :total="peopleTotal"
          :page-sizes="LIST_PAGE_SIZES"
          layout="total, sizes, prev, pager, next"
          @current-change="loadPeople"
          @size-change="searchPeople"
        />
      </div>
    </ElDialog>
  </section>
</template>
<style scoped lang="less">
.training-statistics {
  .ws-hero {
    flex: 0 0 auto;
  }
  .ws-bar {
    min-width: 120px;
  }
}
.table-note {
  margin: 0 0 var(--space-2);
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.people-filter {
  margin-bottom: var(--space-3);
}
:deep(.el-table__row.is-clickable) {
  cursor: pointer;
}
</style>
