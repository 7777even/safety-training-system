<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { ArrowLeft, Refresh } from '@element-plus/icons-vue';
import {
  assignCourseEnrollments,
  cancelCourseOffering,
  createCourseOffering,
  deleteCourseOffering,
  endCourseOffering,
  getAdminEnrollmentDetail,
  getCourseChapters,
  getCourseEnrollmentPage,
  getCourseItems,
  getCourseOfferingDetail,
  getCourseOfferingPage,
  publishCourseOffering,
  removeCourseEnrollment,
  type CourseEnrollment,
  type CourseItem,
  type CourseOffering,
  type CoursePublishStatus,
  type EnrollmentDetail,
  type LearningStatus,
  type OfferingPageQuery
} from '@/api/course-operations';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import {
  COMPLETION_RULE_OPTIONS,
  COURSE_PUBLISH_STATUS_OPTIONS,
  COURSE_TYPE_OPTIONS,
  ENROLLMENT_TYPE_OPTIONS,
  getEnumLabel,
  LEARNING_STATUS_OPTIONS
} from '@/constants/enum-options';
import type { EnumOption } from '@/constants/enum-options';
import type { PersonSelectionRequest } from '@/api/person-selection';
import PersonSelectionDialog from '@/components/organization/PersonSelectionDialog.vue';
import CourseSelect from '@/components/organization/CourseSelect.vue';
import CourseAssessmentPanel from './CourseAssessmentPanel.vue';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const props = withDefaults(
  defineProps<{
    courseId?: number;
    embedded?: boolean;
    coursePublished?: boolean;
    /** 从培训安排失败处理入口直达的开课 ID。 */
    openingOfferingId?: number;
  }>(),
  {
    courseId: undefined,
    embedded: false,
    coursePublished: true,
    openingOfferingId: undefined
  }
);

const userStore = useUserStore();
const canOperate = (permission: string) => hasPermission(userStore.userInfo, permission);
/** 一键流程需同时具备发布开课与指派报名权限。 */
const canPublishOffering = computed(() => canOperate('course:offering:publish'));
const canAssignEnrollments = computed(() => canOperate('course:enrollment:assign'));
const canRunAssignmentWorkflow = computed(() => canPublishOffering.value && canAssignEnrollments.value);
/** 权限不完整时，准确说明本次创建后的实际可执行范围。 */
const assignmentWorkflowHint = computed(() =>
  canPublishOffering.value
    ? '当前账号缺少报名指派权限：开课会创建并发布，随后可由具备权限的管理员在“报名名单”中指派学员。'
    : '当前账号缺少开课发布权限，仅可保存草稿；发布后由具备权限的管理员指派学员。'
);
const errorText = (error: unknown) => (error instanceof Error ? error.message : '请求失败，请稍后重试');
const assessmentOffering = ref<CourseOffering>();
const assessmentVisible = ref(false);
/** 弹窗内的课程测验配置，用于在发布前提示尚未保存配置的测验。 */
const assessmentPanelRef = ref<{ getUnconfiguredQuizNames: () => string[] }>();
const asOffering = (row: unknown) => row as CourseOffering;
const asEnrollment = (row: unknown) => row as CourseEnrollment;
/** 开课发布状态的显示文案与 Contract 值映射。 */

/** 开课列表的筛选、分页和加载状态。 */
const filters = reactive({
  offeringName: '',
  publishStatus: undefined as CoursePublishStatus | undefined,
  courseId: undefined as number | undefined
});
const offerings = ref<CourseOffering[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const loading = ref(false);

/** 右侧页面内查看器状态，避免新增、查看再打开业务弹窗。 */
const editorVisible = ref(false);
const saving = ref(false);
/** 开课表单实例，用于在提交前集中展示 Contract 字段校验结果。 */
const offeringFormRef = ref<FormInstance>();
const form = reactive({
  courseOfferingId: undefined as number | undefined,
  offeringCode: '',
  courseId: 0,
  offeringName: '',
  startTime: '',
  endTime: '',
  courseType: 'REQUIRED' as 'REQUIRED' | 'ELECTIVE',
  enrollmentType: 'ASSIGNED' as 'ASSIGNED' | 'OPEN',
  eligibilityType: 'ALL' as 'ALL' | 'SELECTED',
  eligibilitySelection: undefined as PersonSelectionRequest | undefined,
  initialEnrollmentSelection: undefined as PersonSelectionRequest | undefined,
  completionRule: 'ALL_REQUIRED_ITEMS' as 'ALL_REQUIRED_ITEMS' | 'ALL_ITEMS',
  publishStatus: 'DRAFT' as CoursePublishStatus,
  status: 1 as 0 | 1,
  version: 0,
  mode:''
});
/** 将开课时间范围映射回既有保存接口所需的两个时间字段。 */
const offeringTimeRange = computed({
  get: (): [string, string] => [form.startTime, form.endTime],
  set: (value: [string, string] | null) => {
    const [startTime = '', endTime = ''] = value ?? [];
    form.startTime = startTime;
    form.endTime = endTime;
  }
});
/** 已有开课仅展示详情，新增开课才允许填写基础信息。 */
const isViewingOffering = computed(() => form.courseOfferingId !== undefined);
/** 开课编码、课程、名称及报名资格的字段级校验。 */
const offeringFormRules: FormRules = {
  offeringCode: [
    { required: true, message: '请输入开课编码', trigger: 'blur' },
    { max: 64, message: '开课编码不能超过 64 个字符', trigger: 'blur' }
  ],
  courseId: [{ required: true, type: 'number', min: 1, message: '请选择课程', trigger: 'change' }],
  offeringName: [
    { required: true, message: '请输入开课名称', trigger: 'blur' },
    { max: 150, message: '开课名称不能超过 150 个字符', trigger: 'blur' }
  ],
  courseType: [{ required: true, message: '请选择开课类型', trigger: 'change' }],
  enrollmentType: [{ required: true, message: '请选择报名方式', trigger: 'change' }],
  eligibilityType: [{ required: true, message: '请选择报名资格', trigger: 'change' }],
  eligibilitySelection: [
    {
      validator: (_rule, value: PersonSelectionRequest | undefined, callback) =>
        form.enrollmentType !== 'OPEN' || form.eligibilityType !== 'SELECTED' || value?.selectionType
          ? callback()
          : callback(new Error('请选择开放报名的指定人员')),
      trigger: 'change'
    }
  ],
  initialEnrollmentSelection: [
    {
      validator: (_rule, value: PersonSelectionRequest | undefined, callback) =>
        form.courseOfferingId !== undefined || !canRunAssignmentWorkflow.value || form.enrollmentType !== 'ASSIGNED' || value?.selectionType
          ? callback()
          : callback(new Error('请选择需要指派的学员')),
      trigger: 'change'
    }
  ],
  endTime: [
    {
      validator: (_rule, value: string, callback) =>
        (!value && !form.startTime) || (value && form.startTime && new Date(value).getTime() > new Date(form.startTime).getTime())
          ? callback()
          : callback(new Error('请同时选择开始、结束时间，且结束时间必须晚于开始时间')),
      trigger: 'change'
    }
  ]
};

/** 报名名单使用单层抽屉，学习详情在抽屉内部切换，避免弹窗嵌套。 */
const enrollmentVisible = ref(false);
const selectedOffering = ref<CourseOffering>();
const enrollments = ref<CourseEnrollment[]>([]);
const enrollmentTotal = ref(0);
const enrollmentPage = ref(1);
const enrollmentStatus = ref<LearningStatus>();
const enrollmentSource = ref<CourseEnrollment['enrollmentSource']>();
const enrollmentUserId = ref<number>();
const enrollmentSelectionVisible = ref(false);
const eligibilityDialogVisible = ref(false);
const initialEnrollmentDialogVisible = ref(false);
const learningDetail = ref<EnrollmentDetail>();
const selectedEnrollment = ref<CourseEnrollment>();
const enrollmentView = ref<'list' | 'detail'>('list');
const courseItemLabels = ref<Record<number, string>>({});

const enrollmentUserOptions = computed(() => {
  const options = new Map<number, Pick<CourseEnrollment, 'userId' | 'userName' | 'userCode'>>();
  enrollments.value.forEach((item) => options.set(item.userId, item));
  return [...options.values()];
});

const PROGRESS_STATUS_OPTIONS: readonly EnumOption<string>[] = [
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '学习中', value: 'LEARNING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' }
];
const DEVICE_TYPE_OPTIONS: readonly EnumOption<string>[] = [
  { label: '电脑', value: 'PC' },
  { label: 'App', value: 'APP' },
  { label: 'H5', value: 'H5' }
];
const ENROLLMENT_SOURCE_OPTIONS: readonly EnumOption<string>[] = [
  { label: '管理员指派', value: 'ASSIGN' },
  { label: '自主报名', value: 'SELF' },
  { label: '导入', value: 'IMPORT' },
  { label: '培训计划', value: 'TRAINING' }
];
const SESSION_END_REASON_OPTIONS: readonly EnumOption<string>[] = [
  { label: '完成', value: 'COMPLETED' },
  { label: '主动退出', value: 'USER_EXIT' },
  { label: '离开页面', value: 'SCREEN_LEAVE' },
  { label: '超时', value: 'TIMEOUT' },
  { label: '异常结束', value: 'ERROR' }
];

/** 将接口时间统一转换为便于阅读的中文界面格式。 */
function formatDateTime(value?: string | null): string {
  return value ? value.replace('T', ' ') : '—';
}

/** 课程内容项在学习记录中只返回 ID，页面按课程内容配置翻译为内容项标题。 */
async function loadCourseItemLabels(courseId: number): Promise<void> {
  try {
    const chapters = await getCourseChapters(courseId);
    const groups = await Promise.all(chapters.map((chapter) => getCourseItems(chapter.chapterId)));
    courseItemLabels.value = groups.flat().reduce<Record<number, string>>((labels, item: CourseItem) => {
      labels[item.courseItemId] = item.itemTitle;
      return labels;
    }, {});
  } catch {
    courseItemLabels.value = {};
  }
}

/** 内容项标题找不到时保留编号，便于排查接口数据不一致。 */
function courseItemLabel(courseItemId?: number): string {
  if (!courseItemId) return '—';
  return courseItemLabels.value[courseItemId] ?? `内容项 #${courseItemId}`;
}

/** 学习状态标签配色：进行中为主色，已完成成功色，已过期警示色。 */
function learningStatusTag(status?: LearningStatus): 'primary' | 'success' | 'warning' | 'info' {
  if (status === 'LEARNING') return 'primary';
  if (status === 'COMPLETED') return 'success';
  if (status === 'EXPIRED') return 'warning';
  return 'info';
}

/** 分项进度沿用学习状态同一套语义配色，避免同一抽屉出现两套颜色规则。 */
function progressStatusTag(status?: string): 'primary' | 'success' | 'danger' | 'info' {
  if (status === 'LEARNING') return 'primary';
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED') return 'danger';
  return 'info';
}

/** 会话结束原因：正常结束成功色，超时警示色，异常结束危险色。 */
function sessionEndReasonTag(reason?: string): 'success' | 'info' | 'warning' | 'danger' {
  if (reason === 'COMPLETED') return 'success';
  if (reason === 'TIMEOUT') return 'warning';
  if (reason === 'ERROR') return 'danger';
  return 'info';
}

/** 学习时长按「时/分/秒」拆分，纯秒数无法快速判断学习投入量。 */
function formatDuration(seconds?: number | null): string {
  if (seconds === undefined || seconds === null) return '—';
  if (seconds < 60) return `${seconds} 秒`;
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const restSeconds = seconds % 60;
  if (hours) return `${hours} 小时 ${minutes} 分`;
  return restSeconds ? `${minutes} 分 ${restSeconds} 秒` : `${minutes} 分`;
}

/**
 * 学员概览卡的派生展示值。
 * 进度接口可能返回小数：文字保留原值，进度环取整以免圆环标签出现噪声；
 * 同时把异常值收敛到 0~100，防止进度环画出界。
 */
const learnerProgress = computed(() => {
  const raw = Number(learningDetail.value?.progressPercent ?? 0);
  const safe = Number.isFinite(raw) ? Math.min(100, Math.max(0, raw)) : 0;
  return { text: safe, circle: Math.round(safe) };
});

/** 姓名首字作为头像占位，便于在名单中快速定位学员。 */
const learnerInitial = computed(() => selectedEnrollment.value?.userName?.trim().charAt(0) || '学');

const createDisabledReason = computed(() => {
  if (!props.embedded || props.coursePublished) return '';
  return '当前课程尚未生成发布版本，暂不能创建开课';
});

/** 将页面条件转换为接口定义的 query.* 参数。 */
function buildQuery(): OfferingPageQuery {
  return {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    ...(props.courseId ? { 'query.courseId': props.courseId } : filters.courseId ? { 'query.courseId': filters.courseId } : {}),
    ...(filters.offeringName ? { 'query.offeringName': filters.offeringName } : {}),
    ...(filters.publishStatus ? { 'query.publishStatus': filters.publishStatus } : {})
  };
}

/** 刷新当前课程或全局范围的开课分页。 */
async function loadOfferings(): Promise<void> {
  loading.value = true;
  try {
    const data = await getCourseOfferingPage(buildQuery());
    offerings.value = data.list;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

async function search(): Promise<void> {
  pageNum.value = 1;
  await loadOfferings();
}

async function reset(): Promise<void> {
  filters.offeringName = '';
  filters.publishStatus = undefined;
  filters.courseId = undefined;
  await search();
}

/** 重置页面内查看器，新建时固定使用工作台传入的课程 ID。 */
function openCreate(): void {
  if (createDisabledReason.value) {
    ElMessage.warning(createDisabledReason.value);
    return;
  }
  Object.assign(form, {
    courseOfferingId: undefined,
    offeringCode: '',
    courseId: props.courseId ?? 0,
    offeringName: '',
    startTime: '',
    endTime: '',
    courseType: 'REQUIRED',
    enrollmentType: 'ASSIGNED',
    completionRule: 'ALL_REQUIRED_ITEMS',
    publishStatus: 'DRAFT',
    eligibilityType: 'ALL',
    eligibilitySelection: undefined,
    initialEnrollmentSelection: undefined,
    status: 1,
    version: 0
  });
  editorVisible.value = true;
}

/** 查看前重新读取详情，确保展示最新的开课信息。 */
async function openEdit(row: CourseOffering): Promise<void> {
  const detail = await getCourseOfferingDetail(row.courseOfferingId);
  showOfferingDetail(detail);
}

/** 统一回填开课详情，供列表查看与培训失败后的直达处理入口复用。 */
function showOfferingDetail(detail: CourseOffering): void {
  Object.assign(form, detail, {
    startTime: detail.startTime ?? '',
    endTime: detail.endTime ?? '',
    initialEnrollmentSelection: undefined,
    mode:'view'
  });
  editorVisible.value = true;
}

/** 仅在当前课程匹配时自动打开指定开课，避免地址参数跨课程误操作。 */
async function openRequestedOffering(offeringId?: number): Promise<void> {
  if (!offeringId) return;
  try {
    const detail = await getCourseOfferingDetail(offeringId);
    if (props.courseId && detail.courseId !== props.courseId) return;
    assessmentOffering.value = detail;
    assessmentVisible.value = true;
  } catch (error) {
    ElMessage.error(`无法打开需要处理的开课：${errorText(error)}`);
  }
}

/** 保存开课草稿；课程测验阅卷人配置与发布在同一弹窗内继续完成。 */
async function saveOffering(): Promise<void> {
  if (isViewingOffering.value) return;
  try {
    const offeringValid = await offeringFormRef.value?.validate();
    if (!offeringValid) return;
  } catch {
    return;
  }
  saving.value = true;
  try {
    if (form.courseOfferingId === undefined) {
      const offeringId = await createCourseOffering({
        offeringCode: form.offeringCode,
        courseId: form.courseId,
        offeringName: form.offeringName,
        startTime: form.startTime || null,
        endTime: form.endTime || null,
        courseType: form.courseType,
        enrollmentType: form.enrollmentType,
        completionRule: form.completionRule,
        eligibilityType: form.eligibilityType,
        ...(form.enrollmentType === 'OPEN' && form.eligibilityType === 'SELECTED' ? { eligibilitySelection: form.eligibilitySelection } : {}),
        status: form.status
      });
      const createdOffering = await getCourseOfferingDetail(offeringId);
      Object.assign(form, createdOffering, {
        startTime: createdOffering.startTime ?? '',
        endTime: createdOffering.endTime ?? ''
      });
    }
    ElMessage.success('开课草稿已保存，请完成课程测验阅卷人配置后发布。');
    await loadOfferings();
  } finally {
    saving.value = false;
  }
}

/** 从查看弹窗发布草稿；后端 5068 会保留弹窗，便于立即补齐阅卷人。 */
async function publishOfferingFromEditor(): Promise<void> {
  if (!canPublishOffering.value) return;
  if (form.courseOfferingId === undefined) {
    await saveOffering();
    if (form.courseOfferingId === undefined) return;
  }
  const unconfiguredQuizNames = assessmentPanelRef.value?.getUnconfiguredQuizNames() ?? [];
  if (unconfiguredQuizNames.length) {
    ElMessage.warning(`请先为课程测验配置阅卷人：${unconfiguredQuizNames.join('、')}`);
    return;
  }
  saving.value = true;
  try {
    const detail = await getCourseOfferingDetail(form.courseOfferingId);
    await publishCourseOffering(form.courseOfferingId, detail.version ?? 0);
    if (form.enrollmentType === 'ASSIGNED' && form.initialEnrollmentSelection) {
      await assignCourseEnrollments(form.courseOfferingId, form.initialEnrollmentSelection);
    }
    ElMessage.success('开课已发布');
    editorVisible.value = false;
    await loadOfferings();
  } catch (error) {
    ElMessage.warning(`发布前请完成课程测验阅卷人配置：${errorText(error)}`);
  } finally {
    saving.value = false;
  }
}

/** 所有状态流转和删除都在二次确认后使用最新详情版本执行。 */
async function statusAction(row: CourseOffering, action: 'publish' | 'cancel' | 'end' | 'delete'): Promise<void> {
  const labels = { publish: '发布', cancel: '取消', end: '结束', delete: '删除' };
  await ElMessageBox.confirm(`确认${labels[action]}开课“${row.offeringName}”吗？`, '二次确认', { type: 'warning' });
  try {
    const detail = await getCourseOfferingDetail(row.courseOfferingId);
    const version = detail.version ?? 0;
    if (action === 'publish') await publishCourseOffering(row.courseOfferingId, version);
    if (action === 'cancel') await cancelCourseOffering(row.courseOfferingId, version);
    if (action === 'end') await endCourseOffering(row.courseOfferingId, version);
    if (action === 'delete') await deleteCourseOffering(row.courseOfferingId);
    ElMessage.success(`开课已${labels[action]}`);
    await loadOfferings();
  } catch (error) {
    if (action === 'publish' && errorText(error).includes('必须配置阅卷人')) {
      assessmentOffering.value = row;
      assessmentVisible.value = true;
      ElMessage.warning('请先在考核管理中保存课程测验阅卷人，再发布开课。');
      return;
    }
    throw error;
  }
}

/** 打开报名名单并从第一页加载当前开课的人员记录。 */
async function openEnrollments(row: CourseOffering): Promise<void> {
  selectedOffering.value = row;
  enrollmentPage.value = 1;
  enrollmentStatus.value = undefined;
  enrollmentSource.value = undefined;
  enrollmentUserId.value = undefined;
  enrollmentView.value = 'list';
  learningDetail.value = undefined;
  selectedEnrollment.value = undefined;
  enrollmentVisible.value = true;
  await loadCourseItemLabels(row.courseId);
  await loadEnrollments();
}

async function loadEnrollments(): Promise<void> {
  if (!selectedOffering.value) return;
  const data = await getCourseEnrollmentPage(selectedOffering.value.courseOfferingId, {
    pageNum: enrollmentPage.value,
    pageSize: DEFAULT_LIST_PAGE_SIZE,
    ...(enrollmentUserId.value ? { 'query.userId': enrollmentUserId.value } : {}),
    ...(enrollmentStatus.value ? { 'query.learningStatus': enrollmentStatus.value } : {}),
    ...(enrollmentSource.value ? { 'query.enrollmentSource': enrollmentSource.value } : {})
  });
  enrollments.value = data.list;
  enrollmentTotal.value = data.total;
}

/** 报名名单筛选统一从第一页重新查询，避免筛选后停留在空白页。 */
async function applyEnrollmentFilter(): Promise<void> {
  enrollmentPage.value = 1;
  await loadEnrollments();
}

/** 管理员报名指派复用统一选择器，完整支持 ALL、DEPT、USER 三种契约选择。 */
function openEnrollmentSelection(): void {
  if (!selectedOffering.value) return;
  if (selectedOffering.value.publishStatus !== 'PUBLISHED' || selectedOffering.value.enrollmentType !== 'ASSIGNED') {
    ElMessage.warning('仅已发布且报名方式为指派报名的开课可以指派学员');
    return;
  }
  enrollmentSelectionVisible.value = true;
}

async function assignEnrollments(selection: PersonSelectionRequest): Promise<void> {
  if (!selectedOffering.value) return;
  if (selectedOffering.value.publishStatus !== 'PUBLISHED' || selectedOffering.value.enrollmentType !== 'ASSIGNED') {
    ElMessage.warning('当前开课不满足指派报名条件，请先确认已发布且报名方式为指派报名');
    return;
  }
  await assignCourseEnrollments(selectedOffering.value.courseOfferingId, selection);
  enrollmentSelectionVisible.value = false;
  ElMessage.success('报名指派完成');
  await loadEnrollments();
}

async function removeEnrollment(row: CourseEnrollment): Promise<void> {
  await ElMessageBox.confirm(`确认移除“${row.userName}”的报名吗？`, '二次确认', { type: 'warning' });
  await removeCourseEnrollment(row.courseEnrollmentId);
  await loadEnrollments();
}

/** 学习详情在报名抽屉内展示，返回时保留报名筛选和分页。 */
async function viewLearning(row: CourseEnrollment): Promise<void> {
  learningDetail.value = await getAdminEnrollmentDetail(row.courseEnrollmentId);
  selectedEnrollment.value = row;
  enrollmentView.value = 'detail';
}

/** 手动重新读取教师当前打开的学习详情，避免学员继续学习后页面显示旧进度。 */
async function refreshLearningDetail(): Promise<void> {
  if (!selectedEnrollment.value) return;
  learningDetail.value = await getAdminEnrollmentDetail(selectedEnrollment.value.courseEnrollmentId);
  await loadEnrollments();
  ElMessage.success('学习进度已刷新');
}

watch(
  () => props.courseId,
  () => {
    pageNum.value = 1;
    editorVisible.value = false;
    void loadOfferings();
  }
);
/** 从培训安排结果进入时自动打开该开课，并展示课程测验阅卷人配置。 */
watch(
  () => props.openingOfferingId,
  (offeringId) => void openRequestedOffering(offeringId),
  { immediate: true }
);
onMounted(loadOfferings);
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card course-offering-panel admin-detail-card-spacing">
    <template #header>
      <div class="content-header">
        <p>开课配置</p>
        <ElButton v-if="canOperate('course:offering:add')" type="primary" :disabled="Boolean(createDisabledReason)" @click="openCreate">
          新增开课
        </ElButton>
      </div>
    </template>
    <ElAlert v-if="createDisabledReason" :title="createDisabledReason" type="warning" :closable="false" show-icon class="publish-required" />

    <div class="offering-layout admin-detail-page__form-scroll">
      <main class="offering-list">
        <ElCard v-if="!embedded" shadow="never">
          <ElForm :model="filters" inline>
            <ElFormItem label="开课名称"><ElInput v-model="filters.offeringName" clearable placeholder="请输入开课名称" /></ElFormItem>
            <ElFormItem label="课程"><CourseSelect v-model="filters.courseId" clearable /></ElFormItem>
            <ElFormItem label="发布状态">
              <ElSelect v-model="filters.publishStatus" clearable placeholder="请选择状态" class="filter-select">
                <ElOption v-for="status in COURSE_PUBLISH_STATUS_OPTIONS" :key="status.value" :label="status.label" :value="status.value" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" @click="search">查询</ElButton>
              <ElButton @click="reset">重置</ElButton>
            </ElFormItem>
          </ElForm>
        </ElCard>

        <div class="publish-grid">
          <ElTable v-loading="loading" :data="offerings" row-key="courseOfferingId">
            <ElTableColumn label="序号" width="55" align="center">
              <template #default="{ $index }">
                {{ (pageNum - 1) * pageSize + $index + 1 }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="offeringCode" label="开课编码" min-width="150" />
            <ElTableColumn prop="offeringName" label="开课名称" min-width="180" show-overflow-tooltip />
            <ElTableColumn label="来源" width="100">
              <template #default="{ row }">
                <ElTag :type="asOffering(row).planContentId == null ? 'info' : 'primary'">
                  {{ asOffering(row).planContentId == null ? '独立开课' : '培训生成' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn v-if="!embedded" prop="courseName" label="课程" min-width="150" show-overflow-tooltip />
            <ElTableColumn prop="courseType" label="类型" width="90">
              <template #default="{ row }">
                <ElTag>{{ getEnumLabel(row.courseType, COURSE_TYPE_OPTIONS) }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="enrollmentType" label="报名方式" width="90">
              <template #default="{ row }">
                <ElTag>{{ getEnumLabel(row.enrollmentType, ENROLLMENT_TYPE_OPTIONS) }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" width="90">
              <template #default="{ row }">
                <ElTag>{{ getEnumLabel(row.publishStatus, COURSE_PUBLISH_STATUS_OPTIONS) }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="开课时间" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.startTime + ' - ' + row.endTime }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="330" fixed="right">
              <template #default="{ row }">
                <ElButton v-if="canOperate('course:enrollment:list')" link type="primary" @click="openEnrollments(asOffering(row))">
                  报名名单
                </ElButton>
                <ElButton
                  v-if="canOperate('course:offering:list')"
                  link
                  type="primary"
                  @click="
                    assessmentOffering = asOffering(row);
                    assessmentVisible = true;
                  "
                >
                  考核管理
                </ElButton>
                <ElButton
                  v-if="canOperate('course:offering:edit') && ['DRAFT', 'PUBLISHED'].includes(row.publishStatus)"
                  link
                  type="primary"
                  @click="openEdit(asOffering(row))"
                >
                  查看
                </ElButton>
                <ElButton
                  v-if="canOperate('course:offering:publish') && row.publishStatus === 'DRAFT'"
                  link
                  type="primary"
                  @click="statusAction(asOffering(row), 'publish')"
                >
                  发布开课
                </ElButton>
                <ElButton
                  v-if="canOperate('course:offering:publish') && row.publishStatus === 'PUBLISHED'"
                  link
                  type="warning"
                  @click="statusAction(asOffering(row), 'cancel')"
                >
                  取消
                </ElButton>
                <ElButton
                  v-if="canOperate('course:offering:publish') && row.publishStatus === 'PUBLISHED'"
                  link
                  type="danger"
                  @click="statusAction(asOffering(row), 'end')"
                >
                  结束
                </ElButton>
                <ElButton
                  v-if="canOperate('course:offering:delete') && row.publishStatus === 'DRAFT'"
                  link
                  type="danger"
                  @click="statusAction(asOffering(row), 'delete')"
                >
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <div class="admin-business-page__pagination">
            <ElPagination
              :current-page="pageNum"
              :page-size="pageSize"
              :page-sizes="LIST_PAGE_SIZES"
              :total="total"
              layout="total, sizes, prev, pager, next"
              @current-change="
                pageNum = $event;
                loadOfferings();
              "
              @size-change="
                pageSize = $event;
                pageNum = 1;
                loadOfferings();
              "
            />
          </div>
        </div>
      </main>

      <ElDialog v-model="editorVisible" :title="form.courseOfferingId ? '查看开课' : '新增开课'" width="920px" destroy-on-close>
        <div class="offering-dialog__content">
          <ElAlert title="开课先保存为草稿；如课程测验含主观题，请在下方配置阅卷人后再发布。" type="info" :closable="false" show-icon />
          <ElForm ref="offeringFormRef" class="offering-form-grid" :model="form" :rules="offeringFormRules" label-position="top">
            <ElFormItem label="开课编码" prop="offeringCode">
              <ElInput v-model="form.offeringCode" :disabled="form.courseOfferingId !== undefined" placeholder="请输入开课编码" />
            </ElFormItem>
            <ElFormItem label="课程" prop="courseId">
              <CourseSelect v-model="form.courseId" :disabled="embedded || form.courseOfferingId !== undefined" />
            </ElFormItem>
            <ElFormItem label="开课名称" prop="offeringName">
              <ElInput v-model="form.offeringName" placeholder="请输入开课名称" :disabled="form.courseOfferingId !== undefined" />
            </ElFormItem>
            <ElFormItem label="开课时间" prop="endTime">
              <ElDatePicker
                v-model="offeringTimeRange"
                type="datetimerange"
                value-format="YYYY-MM-DDTHH:mm:ss"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                :disabled="form.courseOfferingId !== undefined"
              />
            </ElFormItem>
            <ElFormItem label="课程类型" prop="courseType">
              <ElRadioGroup v-model="form.courseType" :disabled="form.courseOfferingId !== undefined">
                <ElRadio v-for="item in COURSE_TYPE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem label="报名方式" prop="enrollmentType">
              <ElSelect v-model="form.enrollmentType" :disabled="form.courseOfferingId !== undefined">
                <ElOption v-for="item in ENROLLMENT_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="完成规则">
              <ElSelect v-model="form.completionRule" :disabled="form.courseOfferingId !== undefined">
                <ElOption v-for="item in COMPLETION_RULE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="form.enrollmentType === 'OPEN'" label="报名资格" prop="eligibilityType" style="justify-content: space-between">
              <ElRadioGroup v-model="form.eligibilityType" :disabled="isViewingOffering">
                <ElRadio value="ALL">全部人员</ElRadio>
                <ElRadio value="SELECTED">指定人员</ElRadio>
              </ElRadioGroup>
              <ElButton v-if="form.eligibilityType === 'SELECTED' && !isViewingOffering" link type="primary" @click="eligibilityDialogVisible = true">
                设置人员
              </ElButton>
            </ElFormItem>
            <ElFormItem
              v-if="form.courseOfferingId === undefined && form.enrollmentType === 'ASSIGNED' && canRunAssignmentWorkflow"
              label="创建后指派学员"
              prop="initialEnrollmentSelection"
            >
              <div class="selection-field">
                <span>{{ form.initialEnrollmentSelection ? '已设置初始学员范围' : '请选择需要指派报名的学员' }}</span>
                <ElButton link type="primary" @click="initialEnrollmentDialogVisible = true">选择学员</ElButton>
              </div>
            </ElFormItem>
            <ElAlert
              v-else-if="form.courseOfferingId === undefined && form.enrollmentType === 'ASSIGNED' && !canRunAssignmentWorkflow"
              :title="assignmentWorkflowHint"
              type="info"
              :closable="false"
              show-icon
            />
          </ElForm>
          <div v-if="form.courseOfferingId" class="offering-dialog__assessment">
            <h3>课程测验考核</h3>
            <CourseAssessmentPanel
              ref="assessmentPanelRef"
              :offering-id="form.courseOfferingId"
              :course-id="form.courseId"
              :publish-status="form.publishStatus"
            />
          </div>
        </div>
        <template #footer>
          <div class="editor-actions">
            <ElButton @click="editorVisible = false">取消</ElButton>
            <template v-if="form.courseOfferingId === undefined">
              <ElButton :loading="saving" @click="saveOffering">保存草稿</ElButton>
              <ElButton v-if="form.courseOfferingId && canPublishOffering" type="primary" :loading="saving" @click="publishOfferingFromEditor">
                发布开课
              </ElButton>
            </template>
            <template v-else-if="form.courseOfferingId && canPublishOffering && !form?.mode">
              <ElButton type="primary" :loading="saving" @click="publishOfferingFromEditor">发布开课</ElButton>
            </template>
          </div>
        </template>
      </ElDialog>
    </div>

    <ElDrawer v-model="enrollmentVisible" :title="`${selectedOffering?.offeringName || ''} · 报名与学习`" size="78%" :lock-scroll="false">
      <template v-if="enrollmentView === 'list'">
        <div class="enrollment-toolbar">
          <ElSelect v-model="enrollmentUserId" clearable filterable placeholder="选择学员" @change="applyEnrollmentFilter">
            <ElOption v-for="user in enrollmentUserOptions" :key="user.userId" :label="`${user.userName}（${user.userCode}）`" :value="user.userId" />
          </ElSelect>
          <ElSelect v-model="enrollmentStatus" clearable placeholder="学习状态" @change="applyEnrollmentFilter">
            <ElOption v-for="status in LEARNING_STATUS_OPTIONS" :key="status.value" :label="status.label" :value="status.value" />
          </ElSelect>
          <ElSelect v-model="enrollmentSource" clearable placeholder="报名来源" @change="applyEnrollmentFilter">
            <ElOption v-for="source in ENROLLMENT_SOURCE_OPTIONS" :key="source.value" :label="source.label" :value="source.value" />
          </ElSelect>
          <div v-if="canAssignEnrollments && selectedOffering?.publishStatus === 'PUBLISHED' && selectedOffering?.enrollmentType === 'ASSIGNED'">
            <ElButton type="primary" @click="openEnrollmentSelection">选择学员并指派</ElButton>
          </div>
        </div>
        <ElAlert
          v-if="selectedOffering && (selectedOffering.publishStatus !== 'PUBLISHED' || selectedOffering.enrollmentType !== 'ASSIGNED')"
          title="当前开课暂不可指派报名：请先发布开课，并确认报名方式为“指派报名”。"
          type="info"
          :closable="false"
          show-icon
          class="enrollment-assignment-hint"
        />
        <ElTable :data="enrollments">
          <ElTableColumn prop="userCode" label="账号" />
          <ElTableColumn prop="userName" label="姓名" />
          <ElTableColumn prop="enrollmentSource" label="来源">
            <template #default="{ row }">{{ getEnumLabel(row.enrollmentSource, ENROLLMENT_SOURCE_OPTIONS) }}</template>
          </ElTableColumn>
          <ElTableColumn label="学习状态">
            <template #default="{ row }">{{ getEnumLabel(row.learningStatus, LEARNING_STATUS_OPTIONS) }}</template>
          </ElTableColumn>
          <ElTableColumn prop="progressPercent" label="进度(%)" />
          <ElTableColumn prop="totalStudySeconds" label="学习秒数" />
          <ElTableColumn label="操作" width="170">
            <template #default="{ row }">
              <ElButton v-if="canOperate('course:learning:view')" link type="primary" @click="viewLearning(asEnrollment(row))">学习详情</ElButton>
              <ElButton
                v-if="canOperate('course:enrollment:remove') && row.learningStatus !== 'COMPLETED'"
                link
                type="danger"
                @click="removeEnrollment(asEnrollment(row))"
              >
                移除
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <div class="admin-business-page__pagination">
          <ElPagination
            :current-page="enrollmentPage"
            :page-size="DEFAULT_LIST_PAGE_SIZE"
            :total="enrollmentTotal"
            :page-sizes="LIST_PAGE_SIZES"
            layout="total, sizes, prev, pager, next"
            @current-change="
              enrollmentPage = $event;
              loadEnrollments();
            "
          />
        </div>
      </template>
      <template v-else>
        <div class="learning-toolbar">
          <ElButton :icon="ArrowLeft" @click="enrollmentView = 'list'">返回报名名单</ElButton>
          <ElButton type="primary" :icon="Refresh" :loading="loading" @click="refreshLearningDetail">刷新进度</ElButton>
        </div>
        <section v-if="learningDetail && selectedEnrollment" class="learner-card">
          <div class="learner-card__identity">
            <span class="learner-card__avatar" aria-hidden="true">{{ learnerInitial }}</span>
            <div class="learner-card__meta">
              <div class="learner-card__title">
                <h3>{{ selectedEnrollment.userName }}</h3>
                <ElTag :type="learningStatusTag(learningDetail.learningStatus)" size="small" effect="light">
                  {{ getEnumLabel(learningDetail.learningStatus, LEARNING_STATUS_OPTIONS) }}
                </ElTag>
              </div>
              <p class="learner-card__subtitle">
                <span>账号 {{ selectedEnrollment.userCode }}</span>
                <span class="learner-card__sep" aria-hidden="true">·</span>
                <span>报名来源 {{ getEnumLabel(selectedEnrollment.enrollmentSource, ENROLLMENT_SOURCE_OPTIONS) }}</span>
              </p>
            </div>
          </div>
          <div class="learner-card__metrics">
            <ElProgress type="circle" :percentage="learnerProgress.circle" :width="86" :stroke-width="7">
              <template #default>
                <span class="learner-card__ring-value">{{ learnerProgress.text }}<em>%</em></span>
                <span class="learner-card__ring-label">总进度</span>
              </template>
            </ElProgress>
            <dl class="learner-card__stats">
              <div>
                <dt>累计学习时长</dt>
                <dd>{{ formatDuration(learningDetail.totalStudySeconds) }}</dd>
              </div>
              <div>
                <dt>获得学分</dt>
                <dd>{{ learningDetail.creditAwarded }}</dd>
              </div>
            </dl>
          </div>
        </section>

        <!-- 时间信息用并列指标卡承载，短值不会被等宽表格单元格拉成整格留白 -->
        <dl v-if="learningDetail && selectedEnrollment" class="learner-facts">
          <div>
            <dt>报名时间</dt>
            <dd>{{ formatDateTime(selectedEnrollment.enrolledTime) }}</dd>
          </div>
          <div>
            <dt>最近学习时间</dt>
            <dd>{{ formatDateTime(selectedEnrollment.lastStudyTime) }}</dd>
          </div>
          <div>
            <dt>完成时间</dt>
            <dd>{{ formatDateTime(selectedEnrollment.completedTime) }}</dd>
          </div>
        </dl>
        <section class="snapshot-section">
          <header class="snapshot-section__header">
            <div>
              <h4>分项进度</h4>
              <p>按课程内容项记录的学习状态、作答与时间轨迹。</p>
            </div>
            <span class="snapshot-section__count">共 {{ learningDetail?.itemProgresses?.length ?? 0 }} 项</span>
          </header>
          <ElTable :data="learningDetail?.itemProgresses || []" class="detail-table">
            <ElTableColumn prop="itemProgressId" label="进度记录" min-width="100" class-name="cell-code" />
            <ElTableColumn label="内容项" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ courseItemLabel(row.courseItemId) }}</template>
            </ElTableColumn>
            <ElTableColumn label="状态" min-width="110">
              <template #default="{ row }">
                <ElTag :type="progressStatusTag(row.progressStatus)" size="small" effect="light">
                  {{ getEnumLabel(row.progressStatus, PROGRESS_STATUS_OPTIONS) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="学习秒数" width="110" align="right" class-name="cell-number">
              <template #default="{ row }">{{ row.studySeconds ?? '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="最近位置(秒)" width="120" align="right" class-name="cell-number">
              <template #default="{ row }">{{ row.lastPositionSeconds ?? '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="最近页码" width="100" align="right" class-name="cell-number">
              <template #default="{ row }">{{ row.lastPageNo ?? '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="作答次数" width="100" align="right" class-name="cell-number">
              <template #default="{ row }">{{ row.attemptCount ?? '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="首次开始时间" min-width="170" class-name="cell-time">
              <template #default="{ row }">{{ formatDateTime(row.firstStartTime) }}</template>
            </ElTableColumn>
            <ElTableColumn label="完成时间" min-width="170" class-name="cell-time">
              <template #default="{ row }">{{ formatDateTime(row.completedTime) }}</template>
            </ElTableColumn>
            <ElTableColumn label="最近学习时间" min-width="170" class-name="cell-time">
              <template #default="{ row }">{{ formatDateTime(row.lastStudyTime) }}</template>
            </ElTableColumn>
            <template #empty>
              <ElEmpty :image-size="72" description="暂无分项进度记录" />
            </template>
          </ElTable>
        </section>
        <section class="snapshot-section">
          <header class="snapshot-section__header">
            <div>
              <h4>最近学习会话</h4>
              <p>最近若干次学习行为的起止时间、设备与结束原因。</p>
            </div>
            <span class="snapshot-section__count">共 {{ learningDetail?.recentSessions?.length ?? 0 }} 条</span>
          </header>
          <ElTable :data="learningDetail?.recentSessions || []" class="detail-table">
            <ElTableColumn prop="studySessionId" label="学习记录" min-width="100" class-name="cell-code" />
            <ElTableColumn label="内容项" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ courseItemLabel(row.courseItemId) }}</template>
            </ElTableColumn>
            <ElTableColumn label="开始时间" min-width="170" class-name="cell-time">
              <template #default="{ row }">{{ formatDateTime(row.startedTime) }}</template>
            </ElTableColumn>
            <ElTableColumn label="结束时间" min-width="170" class-name="cell-time">
              <template #default="{ row }">{{ formatDateTime(row.endedTime) }}</template>
            </ElTableColumn>
            <ElTableColumn label="设备" width="90">
              <template #default="{ row }">
                <ElTag size="small" effect="plain">{{ getEnumLabel(row.deviceType, DEVICE_TYPE_OPTIONS) }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="有效秒数" width="110" align="right" class-name="cell-number">
              <template #default="{ row }">{{ row.validStudySeconds ?? '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="结束位置(秒)" width="120" align="right" class-name="cell-number">
              <template #default="{ row }">{{ row.endPositionSeconds ?? '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="结束页码" width="100" align="right" class-name="cell-number">
              <template #default="{ row }">{{ row.endPageNo ?? '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="结束原因" min-width="110">
              <template #default="{ row }">
                <ElTag :type="sessionEndReasonTag(row.endReason)" size="small" effect="light">
                  {{ getEnumLabel(row.endReason, SESSION_END_REASON_OPTIONS) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="clientTraceId" label="追踪 ID" min-width="200" show-overflow-tooltip class-name="cell-code" />
            <template #empty>
              <ElEmpty :image-size="72" description="暂无学习会话记录" />
            </template>
          </ElTable>
        </section>

        <!-- 课程评价随详情一并返回，有数据时才占用版面 -->
        <section v-if="learningDetail?.evaluation" class="snapshot-section">
          <header class="snapshot-section__header">
            <div>
              <h4>课程评价</h4>
              <p>学员在课程结束后提交的评分与反馈。</p>
            </div>
            <span class="snapshot-section__count">提交于 {{ formatDateTime(learningDetail.evaluation.submittedTime) }}</span>
          </header>
          <div class="evaluation-card">
            <ElRate :model-value="learningDetail.evaluation.rating" disabled />
            <p>{{ learningDetail.evaluation.content || '学员未填写评价内容' }}</p>
          </div>
        </section>

        <ElEmpty v-else-if="!loading" :image-size="96" description="未找到该学员的学习记录" />
      </template>
    </ElDrawer>
    <PersonSelectionDialog v-model:visible="enrollmentSelectionVisible" title="选择报名学员" @confirm="assignEnrollments" />
    <ElDrawer
      v-model="assessmentVisible"
      :title="assessmentOffering ? `${assessmentOffering.offeringName} · 课程测验考核与批阅` : '课程测验考核与批阅'"
      size="65%"
    >
      <CourseAssessmentPanel
        v-if="assessmentOffering"
        :offering-id="assessmentOffering.courseOfferingId"
        :course-id="assessmentOffering.courseId"
        :publish-status="assessmentOffering.publishStatus"
      />
    </ElDrawer>
    <PersonSelectionDialog
      v-model:visible="eligibilityDialogVisible"
      :model-value="form.eligibilitySelection"
      title="设置报名资格"
      @confirm="form.eligibilitySelection = $event"
    />
    <PersonSelectionDialog
      v-model:visible="initialEnrollmentDialogVisible"
      :model-value="form.initialEnrollmentSelection"
      title="选择创建后指派的学员"
      @confirm="form.initialEnrollmentSelection = $event"
    />
  </ElCard>
</template>

<style scoped lang="less">
.course-offering-panel {
  flex: 1;
  width: 100%;
}
.course-offering-panel:deep(> .el-card__body) {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}
.content-header {
  display: flex;
  width: 100%;
  min-height: 40px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.content-header p {
  margin: 0;
}

.card-header,
.editor-header,
.editor-actions,
.enrollment-toolbar,
.enrollment-toolbar > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.selection-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  color: var(--color-text-secondary);
}

.publish-required {
  margin: 0 20px;
}
.offering-layout {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}
.offering-layout.has-editor {
  gap: 0;
}
.offering-list {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}
.publish-grid {
  display: flex;
  width: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  //padding-bottom: var(--admin-detail-card-bottom-space);
}
.publish-grid :deep(.el-table) {
  min-height: 0;
  flex: 1;
}
.publish-grid .admin-business-page__pagination {
  margin-top: 12px;
}
.offering-dialog__content {
  display: grid;
  max-height: 68vh;
  gap: 20px;
  overflow-y: auto;
  padding-right: 4px;
}
.offering-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
}
.offering-form-grid :deep(.el-form-item) {
  min-width: 0;
}
.offering-form-grid :deep(.el-input),
.offering-form-grid :deep(.el-select),
.offering-form-grid :deep(.el-date-editor) {
  width: 100%;
}
.offering-dialog__assessment {
  padding-top: 4px;
  border-top: 1px solid var(--color-border);
}
.offering-dialog__assessment h3 {
  margin: 0 0 12px;
  color: var(--color-text-primary);
  font-size: 16px;
}
.editor-header {
  margin-bottom: 16px;
}
.editor-actions {
  justify-content: flex-end;
}
.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-2);
}
.filter-select {
  width: 160px;
}
.enrollment-toolbar {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}
.enrollment-assignment-hint {
  margin-bottom: 16px;
}
.enrollment-toolbar :deep(.el-select) {
  width: 220px;
}
.enrollment-toolbar :deep(.el-input) {
  width: 260px;
}
.enrollment-toolbar > div:last-child {
  width: auto;
  justify-content: flex-end;
}
/* ── 报名与学习 · 学习详情 ─────────────────────────────── */
.learning-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
  gap: var(--space-3);
}

/* 概览卡复用只读版本页 hero 的视觉语言：浅主色底 + 右上装饰圆。 */
.learner-card {
  display: flex;
  position: relative;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  margin-bottom: var(--space-3);
  padding: var(--space-5) var(--space-6);
  overflow: hidden;
  background: var(--color-primary-subtle);
  border: 1px solid #dbeafe;
  border-radius: var(--radius-card);
}

.learner-card::after {
  position: absolute;
  top: -58px;
  right: 92px;
  width: 148px;
  height: 148px;
  pointer-events: none;
  content: '';
  border: 20px solid rgb(37 99 235 / 8%);
  border-radius: 50%;
}

.learner-card__identity {
  display: flex;
  position: relative;
  z-index: 1;
  align-items: center;
  min-width: 0;
  gap: var(--space-4);
}

.learner-card__avatar {
  display: inline-flex;
  width: 52px;
  height: 52px;
  flex: none;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-surface);
  border: 1px solid #bfdbfe;
  border-radius: 14px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
  color: var(--color-primary);
  font-size: 20px;
  font-weight: 600;
}

.learner-card__meta {
  min-width: 0;
}

.learner-card__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);

  h3 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: 18px;
    font-weight: 600;
    line-height: 26px;
  }
}

.learner-card__subtitle {
  display: flex;
  align-items: center;
  margin: 4px 0 0;
  gap: 6px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 20px;
}

.learner-card__sep {
  color: var(--color-border);
}

.learner-card__metrics {
  display: flex;
  position: relative;
  z-index: 1;
  flex: none;
  align-items: center;
  gap: var(--space-6);
}

.learner-card__ring-value {
  display: block;
  color: var(--color-text-primary);
  font-size: 17px;
  font-weight: 600;
  line-height: 20px;

  em {
    margin-left: 1px;
    font-size: 11px;
    font-style: normal;
    font-weight: 500;
  }
}

.learner-card__ring-label {
  display: block;
  margin-top: 2px;
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 14px;
}

.learner-card__stats {
  display: flex;
  margin: 0;
  gap: var(--space-6);

  dt {
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 18px;
  }

  dd {
    margin: 4px 0 0;
    color: var(--color-text-primary);
    font-size: 18px;
    font-weight: 600;
    line-height: 24px;
    white-space: nowrap;
  }
}

/* 时间信息用并列指标卡承载，短值不再被描述表格拉成整格留白。 */
.learner-facts {
  display: grid;
  margin: 0 0 var(--space-3);
  gap: var(--space-3);
  grid-template-columns: repeat(3, minmax(0, 1fr));

  > div {
    min-width: 0;
    padding: 12px 16px;
    background: var(--color-bg-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-card);
  }

  dt {
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 18px;
  }

  dd {
    margin: 4px 0 0;
    overflow: hidden;
    color: var(--color-text-primary);
    font-size: 14px;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    line-height: 20px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.snapshot-section {
  display: flex;
  flex-direction: column;
  margin-bottom: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-5);
  gap: var(--space-3);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}

.snapshot-section__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);

  h4 {
    margin: 0;
    color: var(--color-text-primary);
    font-size: 15px;
    font-weight: 600;
    line-height: 22px;
  }

  p {
    margin: 2px 0 0;
    color: var(--color-text-secondary);
    font-size: 12px;
    line-height: 18px;
  }
}

.snapshot-section__count {
  flex: none;
  padding: 2px 10px;
  background: var(--color-bg-subtle);
  border-radius: 999px;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}

/* 明细表统一表头底色、等宽数字与圆角容器，避免默认样式在抽屉里显得松散。 */
.detail-table {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);

  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }

  :deep(th.el-table__cell) {
    background: var(--color-bg-subtle);
    color: var(--color-text-regular);
    font-size: 13px;
    font-weight: 600;
  }

  :deep(td.el-table__cell) {
    font-size: 13px;
  }

  :deep(.cell-code) {
    color: var(--color-text-secondary);
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
  }

  :deep(.cell-number) {
    font-variant-numeric: tabular-nums;
    font-weight: 500;
  }

  :deep(.cell-time) {
    color: var(--color-text-regular);
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  :deep(.el-table__empty-block) {
    background: var(--color-bg-surface);
  }
}

.evaluation-card {
  display: flex;
  align-items: center;
  padding: var(--space-4);
  gap: var(--space-4);
  background: var(--color-bg-subtle);
  border-radius: var(--radius-control);

  p {
    min-width: 0;
    flex: 1;
    margin: 0;
    color: var(--color-text-regular);
    font-size: 13px;
    line-height: 20px;
  }
}
@media (max-width: 1180px) {
  .offering-form-grid {
    grid-template-columns: 1fr;
  }
  .enrollment-toolbar {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
  .enrollment-toolbar > div:last-child {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }
  .offering-layout.has-editor {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 960px) {
  .learner-card {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--space-4);
  }
  .learner-facts {
    grid-template-columns: minmax(0, 1fr);
  }
  .evaluation-card {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--space-2);
  }
}
</style>
