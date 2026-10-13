<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { CircleCheckFilled, InfoFilled, Refresh, WarningFilled } from '@element-plus/icons-vue';
import { getCourseOfferingDetail, type CourseOffering } from '@/api/course-operations';
import type { PersonPickerItem } from '@/api/person-picker';
import type { PersonSelectionRequest } from '@/api/person-selection';
import type { TrainingExecutionGenerationResult, TrainingPlanStage } from '@/api/training-plan';
import PersonSelectionDialog from '@/components/organization/PersonSelectionDialog.vue';

interface Participant {
  trainingParticipantId: number;
  userId: number;
  participantStatus?: string;
  onlineHours?: number;
  offlineHours?: number;
}
interface UserOption {
  userId: number;
  userName: string;
  userCode: string;
  organizationId?: number;
  deptId?: number | null;
  deptName?: string | null;
  memberType?: string;
  jobTitle?: string;
  status?: 0 | 1;
}
interface Result {
  trainingParticipantId: number;
  resultStatus?: string;
  courseScore?: number;
  examScore?: number;
  offlineScore?: number;
  compositeScore?: number;
  resultFinalizedTime?: string;
  onlineHours?: number | null;
  offlineHours?: number | null;
  items?: Array<{
    planContentId?: number;
    contentName: string;
    executionType?: string;
    resultStatus?: string;
    score?: number | null;
    effectiveDurationSeconds?: number | null;
  }>;
}
/** 结束前检查仅展示当前页面已读取到的阻塞事实，后端结束门禁仍是最终依据。 */
interface EndPrecheckBlocker {
  /** 参训人相关项只写相对说明，参训人名称由分组标题承载；计划级项写完整说明。 */
  title: string;
  description: string;
  /** 归属参训人标识，缺省表示计划级检查项，不参与按人折叠。 */
  participantKey?: string;
  /** 归属参训人显示名称。 */
  participantName?: string;
}
/** 按参训人归集的未完成项，附带类型分布，便于收起时也能看出每人要处理什么。 */
interface ParticipantBlockerGroup {
  key: string;
  name: string;
  items: EndPrecheckBlocker[];
  categories: Array<{ label: string; count: number }>;
}
/** 安排结果表格行，保留后端失败码和课程草稿开课 ID 以展示确定的处理路径。 */
interface GenerationResultItem {
  planContentId: number;
  contentName: string;
  contentType: string;
  status: 'SUCCESS' | 'EXISTING' | 'FAILED';
  code?: number;
  offeringId?: number | null;
  message?: string;
}
const props = defineProps<{
  mode: 'lifecycle' | 'participants';
  /** 当前计划版本审批通过才可发布。 */
  canPublish?: boolean;
  publishStatus: string;
  canAssign: boolean;
  selectedUserId?: number;
  userOptions: UserOption[];
  executionCount: number;
  contentCount: number;
  arrangedContentCount: number;
  requiredContentCount: number;
  readyRequiredContentCount: number;
  stages?: TrainingPlanStage[];
  generationResult?: TrainingExecutionGenerationResult & {
    items: GenerationResultItem[];
  };
  generationLoading?: boolean;
  participantLoading?: boolean;
  participants: Participant[];
  results?: Result;
  endPrecheckLoading?: boolean;
  endPrecheckBlockers?: EndPrecheckBlocker[];
  endPrecheckError?: string;
  hasExpertInspection?: boolean;
  canViewExpertInspection?: boolean;
}>();
const emit = defineEmits<{
  action: [action: 'publish' | 'generate' | 'start' | 'end' | 'cancel'];
  assign: [];
  bindSelection: [selection: PersonSelectionRequest];
  refreshParticipants: [];
  refreshEndPrecheck: [];
  'update:selectedUserId': [value: number | undefined];
  viewResult: [id: number];
  viewExpertInspection: [];
}>();
const participantSelectionVisible = ref(false);
const participantSelection = ref<PersonSelectionRequest>();
const resultDrawerVisible = ref(false);
const selectedResultParticipantId = ref<number>();
const generationResultVisible = ref(false);
const endPrecheckDrawerVisible = ref(false);
const router = useRouter();
/** 按开课 ID 缓存已读取的详情，仅用于把内部标识转换为用户可理解的开课名称。 */
const generationOfferings = ref<Record<number, CourseOffering>>({});
const loadingGenerationOfferingIds = ref<number[]>([]);

const STATUS_STEPS = [
  { value: 'DRAFT', label: '草稿', description: '检查并发布' },
  { value: 'PUBLISHED', label: '已发布', description: '安排培训内容' },
  { value: 'RUNNING', label: '执行中', description: '培训进行中' },
  { value: 'ENDED', label: '已结束', description: '查看最终结果' }
];

/** 生命周期顺序用于同时表达当前阶段和已经完成的阶段。 */
const currentStatusIndex = computed(() => STATUS_STEPS.findIndex((step) => step.value === props.publishStatus));
const isResultLoading = computed(() => !props.results || props.results.trainingParticipantId !== selectedResultParticipantId.value);
const hasContent = computed(() => props.contentCount > 0);
const allContentArranged = computed(() => props.contentCount > 0 && props.arrangedContentCount >= props.contentCount);
const canStartPlan = computed(() => allContentArranged.value && props.readyRequiredContentCount >= props.requiredContentCount);
const remainingContentCount = computed(() => Math.max(props.contentCount - props.arrangedContentCount, 0));
/** 卡片提示必须与实际安排数量一致，避免重新进入页面后误显示“尚未安排内容”。 */
const arrangementSummaryText = computed(() => {
  if (allContentArranged.value) return props.generationResult ? '点击查看安排明细' : '已安排完成';
  if (props.arrangedContentCount > 0) return `已安排 ${props.arrangedContentCount}/${props.contentCount}，请继续处理`;
  return '尚未安排内容';
});
/** 部分安排成功时强调继续处理，避免用户误以为本次操作已经完成。 */
const arrangeButtonText = computed(() => (props.arrangedContentCount > 0 ? '继续安排培训内容' : '转为培训任务'));
/** 转任务复用现有内容安排，开始培训后进入学员既有任务详情。 */
const taskDispatchHint = computed(() => {
  if (props.publishStatus === 'RUNNING') return '培训已开始，学员可从系统消息或培训中心打开任务详情。';
  if (props.publishStatus === 'PUBLISHED') return '转为培训任务会创建课程、考试及线下安排；安排完成后点击“开始培训”，学员即可从系统消息或培训中心进入任务。';
  return '请先完成计划审批并下发，再转为培训任务。';
});
/** 执行中且已绑定专家检查表单、且当前账号有查看权限时才给出填写入口。 */
const canFillExpertInspection = computed(() => props.publishStatus === 'RUNNING' && props.hasExpertInspection && props.canViewExpertInspection);
/** 已结束计划不再新增记录，只为已提交内容保留只读回看入口。 */
const canReviewExpertInspection = computed(() => props.publishStatus === 'ENDED' && props.hasExpertInspection && props.canViewExpertInspection);
/** 已知阻塞项或检查尚未完成时不允许误触结束；未识别规则仍由后端最终校验。 */
const canEndPlan = computed(() => props.publishStatus === 'RUNNING' && !props.endPrecheckLoading && !(props.endPrecheckBlockers?.length ?? 0));
/** 读取失败时允许后端继续执行最终门禁，但不能把未知状态渲染成检查通过。 */
const isEndPrecheckReady = computed(() => canEndPlan.value && !props.endPrecheckError);
const endPlanTitle = computed(() => {
  if (props.endPrecheckLoading) return '正在核对结束条件，请稍候。';
  if (props.endPrecheckBlockers?.length) return '请先处理结束前检查中的阻塞项。';
  return '结束培训并确认最终结果';
});
/** 结束前检查的摘要只描述页面可见事实，不覆盖后端最终门禁。 */
const endPrecheckSummary = computed(() => {
  if (props.endPrecheckLoading) return { title: '正在核对结束条件', description: '正在读取执行、线下场次与参训人结果。' };
  if (props.endPrecheckError) return { title: '暂无法完整核对', description: '请刷新检查；结束时仍会由后端完成最终校验。' };
  const count = props.endPrecheckBlockers?.length ?? 0;
  if (count) return { title: `还有 ${count} 项待处理`, description: '处理完成后刷新检查，即可继续结束培训。' };
  return { title: '当前已知条件满足', description: '可以提交结束培训，系统仍会进行最终校验。' };
});
/** 将阻塞项归类为短标签，便于管理员快速判断下一步该找谁处理。 */
function endPrecheckCategory(title: string): string {
  if (title.includes('线下场次')) return '线下场次';
  if (title.includes('仍在进行中')) return '进行中任务';
  if (title.includes('综合成绩')) return '综合成绩';
  if (title.includes('未安排')) return '内容安排';
  return '必修结果';
}
/** 计划级阻塞项与具体参训人无关，单独成组，避免被人员折叠列表淹没。 */
const planLevelBlockers = computed(() => (props.endPrecheckBlockers ?? []).filter((blocker) => !blocker.participantKey));
/**
 * 参训人相关阻塞项按人归集：同一名参训人的未完成项合并为一个可折叠分组，
 * 人数多时先看「每人还差几项 + 差在哪类」，再逐人展开明细。
 */
const participantBlockerGroups = computed<ParticipantBlockerGroup[]>(() => {
  const grouped = new Map<string, { name: string; items: EndPrecheckBlocker[] }>();
  (props.endPrecheckBlockers ?? [])
    .filter((blocker) => Boolean(blocker.participantKey))
    .forEach((blocker) => {
      const key = blocker.participantKey as string;
      const group = grouped.get(key) ?? { name: blocker.participantName ?? '参训人', items: [] };
      if (blocker.participantName) group.name = blocker.participantName;
      group.items.push(blocker);
      grouped.set(key, group);
    });
  return [...grouped.entries()].map(([key, group]) => {
    const counts = new Map<string, number>();
    group.items.forEach((item) => {
      const label = endPrecheckCategory(item.title);
      counts.set(label, (counts.get(label) ?? 0) + 1);
    });
    return { key, name: group.name, items: group.items, categories: [...counts.entries()].map(([label, count]) => ({ label, count })) };
  });
});
const participantBlockerTotal = computed(() => participantBlockerGroups.value.reduce((total, group) => total + group.items.length, 0));
/** 摘要区分单人与多人场景，避免出现「1 名参训人」这类拗口文案。 */
const participantBlockerSummary = computed(() => {
  const groupCount = participantBlockerGroups.value.length;
  if (!groupCount) return '';
  return groupCount === 1 ? `${participantBlockerTotal.value} 项待处理` : `${groupCount} 名参训人共 ${participantBlockerTotal.value} 项待处理`;
});
/** 折叠状态按参训人标识维护，刷新后重新按人数决定默认展开范围。 */
const openedBlockerGroups = ref<string[]>([]);
const allBlockerGroupsOpened = computed(
  () => participantBlockerGroups.value.length > 0 && openedBlockerGroups.value.length === participantBlockerGroups.value.length
);
/** 单个参训人时保持展开（与折叠前观感一致）；多人时默认收起，避免密密麻麻。 */
function syncOpenedBlockerGroups(): void {
  const keys = participantBlockerGroups.value.map((group) => group.key);
  openedBlockerGroups.value = keys.length > 1 ? [] : keys;
}
function toggleAllBlockerGroups(): void {
  openedBlockerGroups.value = allBlockerGroupsOpened.value ? [] : participantBlockerGroups.value.map((group) => group.key);
}
watch(() => props.endPrecheckBlockers, syncOpenedBlockerGroups, { immediate: true, deep: false });

/** 安排完成后自动打开结果反馈，避免用户只看到执行数量变化。 */
watch(
  () => props.generationResult,
  (result) => {
    if (result && props.mode === 'lifecycle') generationResultVisible.value = true;
  }
);

/** 结果弹窗打开前预取可处理失败项的开课名称，避免用户面对无含义的内部编号。 */
watch(
  () => props.generationResult?.items,
  (items) => {
    items?.filter(canHandleGenerationFailure).forEach((item) => void loadGenerationOffering(item).catch(() => undefined));
  },
  { immediate: true }
);

/** 将后端状态枚举翻译为管理端可读文案。 */
function statusText(status: string): string {
  return { DRAFT: '草稿', PUBLISHED: '已发布', RUNNING: '执行中', ENDED: '已结束', CANCELLED: '已取消', ARCHIVED: '已归档' }[status] ?? status;
}

/** 参训状态只读展示，真实状态流转由后端控制。 */
function participantStatusText(status?: string): string {
  return (
    { PENDING: '待开始', LEARNING: '学习中', COMPLETED: '已完成', FAILED: '未通过', EXPIRED: '已过期', WITHDRAWN: '已退出' }[status ?? ''] ?? '待同步'
  );
}

/** 结果状态只展示业务人员能直接理解的中文。 */
function resultStatusText(status?: string): string {
  return (
    { PASSED: '合格', FAILED: '未通过', INCOMPLETE: '结果待补全', COMPLETED: '已完成', IN_PROGRESS: '进行中', PENDING: '待开始' }[status ?? ''] ??
    '待同步'
  );
}
/** 结果明细以秒记录获得学时，页面仅作显示换算。 */
function earnedHoursText(seconds?: number | null): string {
  if (seconds == null) return '-';
  return `${Math.round((seconds / 3600) * 100) / 100} 学时`;
}

/** 成绩为空表示暂未产生正式成绩，不能与真实的 0 分混淆。 */
function scoreText(score?: number | null): string {
  return score == null ? '尚未产生' : String(score);
}

/** 分项成绩的取得条件，和培训结果接口中的正式事实来源保持一致。 */
function scoreSourceHint(executionType?: string): string {
  return (
    {
      COURSE: '课程学习产生正式成绩后汇总',
      EXAM: '考试成绩发布后同步',
      QUESTION_BANK: '题库练习仅作反馈，不计入正式考试成绩',
      OFFLINE: '教师录入线下成绩后同步'
    }[executionType ?? ''] ?? '等待对应培训内容产生正式结果'
  );
}

/** 从已加载的计划快照读取计分资格，结果明细自身不伪造该业务配置。 */
function resultContent(row: NonNullable<Result['items']>[number]): NonNullable<TrainingPlanStage['contents']>[number] | undefined {
  if (!row.planContentId) return undefined;
  return props.stages?.flatMap((stage) => stage.contents ?? []).find((content) => content.planContentId === row.planContentId);
}

/** 题库练习固定不计入，其余内容以计划冻结的 isScored 配置说明。 */
function scoreParticipationText(row: NonNullable<Result['items']>[number]): string {
  if (row.executionType === 'QUESTION_BANK') return '题库练习不参与综合成绩';
  const content = resultContent(row);
  if (!content) return '未读取到计分配置';
  return content.isScored === 1 ? '计入综合成绩' : '不计入（计划配置）';
}

/** Element Plus 表格插槽不保留结果行泛型，在此处收敛为结果明细类型。 */
function asResultItem(row: unknown): NonNullable<Result['items']>[number] {
  return row as NonNullable<Result['items']>[number];
}

/** 安排结果中的内容类型只展示业务人员能直接理解的中文。 */
function generationContentTypeText(contentType?: string): string {
  return { COURSE: '课程', EXAM: '考试', QUESTION_BANK: '题库练习', OFFLINE: '线下活动' }[contentType ?? ''] ?? '培训内容';
}

/** Element Plus 表格插槽不保留行泛型，在此处收敛为安排结果类型。 */
function asGenerationResultItem(row: unknown): GenerationResultItem {
  return row as GenerationResultItem;
}

/**
 * 仅为契约已明确的失败项提供固定处理路径，其他错误保留服务端说明以免前端猜测业务原因。
 */
function generationFailureGuide(item: { status: string; contentType: string; code?: number; offeringId?: number | null }): string | undefined {
  if (item.status !== 'FAILED') return undefined;
  if (item.contentType === 'COURSE' && item.code === 5068) {
    if (!item.offeringId) return '系统未返回可配置的开课信息，请重新安排培训内容。';
    const offering = generationOfferings.value[item.offeringId];
    return offering
      ? `请点击下方按钮找到“${offering.offeringName}”-“考核管理”配置课程测验阅卷人，保存后返回本页继续安排。`
      : '正在读取对应的课程开课信息，请稍候。';
  }
  return undefined;
}

/** 仅 5068 课程失败项存在明确且可直达的阅卷人配置处理入口。 */
function canHandleGenerationFailure(item: GenerationResultItem): boolean {
  return item.status === 'FAILED' && item.contentType === 'COURSE' && item.code === 5068 && Boolean(item.offeringId);
}

function isGenerationOfferingLoading(offeringId?: number | null): boolean {
  return offeringId !== undefined && offeringId !== null && loadingGenerationOfferingIds.value.includes(offeringId);
}

/** 读取开课详情以展示名称，失败时不以内部编号替代。 */
async function loadGenerationOffering(item: GenerationResultItem): Promise<CourseOffering | undefined> {
  if (!item.offeringId) return undefined;
  const cached = generationOfferings.value[item.offeringId];
  if (cached) return cached;
  if (isGenerationOfferingLoading(item.offeringId)) return undefined;
  loadingGenerationOfferingIds.value = [...loadingGenerationOfferingIds.value, item.offeringId];
  try {
    const offering = await getCourseOfferingDetail(item.offeringId);
    generationOfferings.value = { ...generationOfferings.value, [item.offeringId]: offering };
    return offering;
  } finally {
    loadingGenerationOfferingIds.value = loadingGenerationOfferingIds.value.filter((id) => id !== item.offeringId);
  }
}

/** 进入指定培训开课的阅卷人配置页，复用课程工作台的既有地址状态。 */
async function openGenerationFailureHandling(item: GenerationResultItem): Promise<void> {
  try {
    const offering = await loadGenerationOffering(item);
    if (!offering) return;
    generationResultVisible.value = false;
    await router.push({
      path: '/admin/business-center/course/center',
      query: {
        courseId: String(offering.courseId),
        stage: 'offering',
        openingOfferingId: String(offering.courseOfferingId),
        tabTitle: offering.courseName
      }
    });
  } catch (error) {
    ElMessage.warning(error instanceof Error ? `无法读取对应开课信息：${error.message}` : '无法读取对应开课信息，请稍后重试。');
  }
}

/** 安排结果标题仅按已返回的失败数归纳，不推导未在契约中声明的执行状态。 */
const generationResultStatusText = computed(() => {
  const failedCount = props.generationResult?.failedCount ?? 0;
  return failedCount > 0 ? `待处理 ${failedCount} 项` : '全部安排完成';
});

function userLabel(userId: number): string {
  const user = props.userOptions.find((item) => item.userId === userId);
  return user ? `${user.userName}（${user.userCode}）` : String(userId);
}

/** 当前查看结果的参训人员名称，避免弹窗标题只显示内部 ID。 */
const selectedResultParticipantName = computed(() => {
  const participant = props.participants.find((item) => item.trainingParticipantId === selectedResultParticipantId.value);
  return participant ? userLabel(participant.userId) : '参训人员';
});

/** 打开分项结果抽屉并请求对应参训人的最新结果。 */
function openResultDrawer(trainingParticipantId: number): void {
  selectedResultParticipantId.value = trainingParticipantId;
  resultDrawerVisible.value = true;
  emit('viewResult', trainingParticipantId);
}

/** 参训人员选择弹窗复用统一人员选择模型，回显仅用于指定人员模式。 */
const preselectedUsers = computed<PersonPickerItem[]>(() =>
  props.participants
    .map((participant) => props.userOptions.find((user) => user.userId === participant.userId))
    .filter((user): user is UserOption => Boolean(user))
    .map((user) => ({
      userId: user.userId,
      userCode: user.userCode,
      userName: user.userName,
      organizationId: user.organizationId ?? 0,
      deptId: user.deptId ?? undefined,
      deptName: user.deptName ?? undefined,
      memberType: user.memberType,
      jobTitle: user.jobTitle,
      status: user.status ?? 1
    }))
);

function confirmParticipantSelection(selection: PersonSelectionRequest): void {
  participantSelection.value = selection;
  emit('bindSelection', selection);
}
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>
      <div class="card-header">
        <div class="card-header__main">
          <div class="card-header__title">
            <h2>{{ props.mode === 'lifecycle' ? '发布与执行' : '参训人员与结果' }}</h2>
            <ElTooltip v-if="props.mode === 'participants'" content="刷新参训人员的最新状态和获得学时" placement="top">
              <ElButton link :icon="Refresh" :loading="props.participantLoading" aria-label="刷新参训人员数据" @click="emit('refreshParticipants')" />
            </ElTooltip>
          </div>
          <p>{{ props.mode === 'lifecycle' ? '按发布、生成、开始、同步、结束的顺序推进计划。' : '指派参训人员并查看学习进度和培训成绩。' }}</p>
          <!-- 关键口径固定在卡头：名单是这一步的阅读主体，指标不必在正文里再单独占一块高度。 -->
          <div v-if="props.mode === 'participants'" class="card-header__stats">
            <span class="card-header__stat"><i>已安排培训内容</i><b>{{ props.executionCount }}</b></span>
            <span class="card-header__stat"><i>参训人数</i><b>{{ props.participants.length }}</b></span>
            <span class="card-header__stat"><i>培训结果</i><b>{{ props.results ? '已查询' : '未查询' }}</b></span>
          </div>
        </div>
        <ElTag :type="props.publishStatus === 'CANCELLED' ? 'danger' : props.publishStatus === 'ENDED' ? 'success' : 'primary'">
          {{ statusText(props.publishStatus) }}
        </ElTag>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll execution-scroll" :class="{ 'execution-scroll--list': props.mode === 'participants' }">
      <template v-if="props.mode === 'lifecycle'">
        <section class="lifecycle-board">
          <div class="status-flow">
            <div
              v-for="step in STATUS_STEPS"
              :key="step.value"
              class="status-flow__item"
              :title="step.description"
              :class="{
                'is-active': props.publishStatus === step.value,
                'is-complete': currentStatusIndex > STATUS_STEPS.findIndex((currentStep) => currentStep.value === step.value),
                'is-muted': props.publishStatus === 'CANCELLED'
              }"
            >
              <span class="status-flow__index">
                {{
                  currentStatusIndex > STATUS_STEPS.findIndex((currentStep) => currentStep.value === step.value)
                    ? '✓'
                    : STATUS_STEPS.findIndex((currentStep) => currentStep.value === step.value) + 1
                }}
              </span>
              <strong>{{ step.label }}</strong>
              <span>{{ step.description }}</span>
            </div>
          </div>
          <div class="summary-grid">
            <div>
              <span>当前状态</span>
              <strong>{{ statusText(props.publishStatus) }}</strong>
            </div>
            <button
              type="button"
              class="summary-grid__card"
              :class="{ 'summary-grid__card--clickable': Boolean(props.generationResult) }"
              :disabled="!props.generationResult"
              :title="props.generationResult ? '查看培训内容安排结果' : '当前页面暂无安排明细'"
              @click="generationResultVisible = true"
            >
              <span>已安排培训内容</span>
              <strong>{{ props.executionCount }}</strong>
              <small>{{ arrangementSummaryText }}</small>
            </button>
            <div>
              <span>参训人数</span>
              <strong>{{ props.participants.length }}</strong>
            </div>
          </div>
          <div class="action-panel" :class="{ 'action-panel--empty': ['ENDED', 'CANCELLED'].includes(props.publishStatus) }">
            <!-- 标题与说明同基线排布，把“下一步做什么”压成一行，给下方操作和名单留出高度。 -->
            <div class="action-panel__intro">
              <h3>下一步操作</h3>
              <p v-if="props.publishStatus === 'DRAFT'">发布前请完成参训人员和培训内容检查。</p>
              <p v-else-if="props.publishStatus === 'PUBLISHED' && !hasContent">当前计划没有培训内容，请申请完整变更并经审批后再转为培训任务。</p>
              <p v-else-if="props.publishStatus === 'PUBLISHED' && !allContentArranged">
                还有 {{ remainingContentCount }} 项培训内容未安排，请完成安排后再开始培训。
              </p>
              <p v-else-if="props.publishStatus === 'PUBLISHED'">培训内容已全部安排，请确认后开始培训。</p>
              <p v-else-if="props.publishStatus === 'RUNNING'">培训进行中，可更新课程、考试和线下活动的最新结果。</p>
              <p v-else>当前计划已结束，暂无可执行操作。</p>
              <p v-if="['DRAFT','PUBLISHED','RUNNING'].includes(props.publishStatus)">{{ taskDispatchHint }}</p>
            </div>
            <ElAlert
              v-if="props.publishStatus === 'DRAFT' && props.participants.length <= 0"
              title="发布前请先配置至少一名参训人员"
              type="warning"
              :closable="false"
              show-icon
            />
            <div
              v-if="props.publishStatus === 'RUNNING'"
              class="end-precheck-compact"
              :class="{ 'is-ready': isEndPrecheckReady, 'has-error': Boolean(props.endPrecheckError) }"
            >
              <span class="end-precheck-compact__icon" aria-hidden="true">
                <CircleCheckFilled v-if="isEndPrecheckReady" />
                <WarningFilled v-else />
              </span>
              <div>
                <span>结束前检查</span>
                <strong>{{ endPrecheckSummary.title }}</strong>
                <small>{{ endPrecheckSummary.description }}</small>
              </div>
              <ElButton plain :icon="Refresh" :loading="props.endPrecheckLoading" @click="endPrecheckDrawerVisible = true">查看检查</ElButton>
            </div>
            <div class="action-panel__buttons">
              <ElButton v-if="props.publishStatus === 'DRAFT'" :disabled="!props.canPublish" type="primary" @click="emit('action', 'publish')">发布预检并发布</ElButton>
              <ElButton
                v-if="props.publishStatus === 'PUBLISHED' && hasContent && !allContentArranged"
                :loading="props.generationLoading"
                @click="emit('action', 'generate')"
              >
                {{ arrangeButtonText }}
              </ElButton>
              <ElButton v-if="props.publishStatus === 'PUBLISHED' && props.generationResult" @click="generationResultVisible = true">
                查看安排明细
              </ElButton>
              <ElButton
                v-if="props.publishStatus === 'PUBLISHED'"
                type="success"
                :disabled="!canStartPlan"
                :title="canStartPlan ? '开始培训计划' : '请先安排全部培训内容'"
                @click="emit('action', 'start')"
              >
                开始计划
              </ElButton>
              <span v-if="props.publishStatus === 'PUBLISHED' && !canStartPlan" class="action-panel__hint">
                <template v-if="!hasContent">请先申请完整变更并重新审批</template>
                <template v-else-if="!allContentArranged">还需安排 {{ remainingContentCount }} 项培训内容</template>
              </span>
              <ElButton
                v-if="props.publishStatus === 'RUNNING'"
                type="success"
                :disabled="!canEndPlan"
                :title="endPlanTitle"
                @click="emit('action', 'end')"
              >
                结束培训并确认结果
              </ElButton>
              <!-- 执行中可填写，已结束计划继续提供已提交记录的只读回看。 -->
              <ElButton
                v-if="canFillExpertInspection || canReviewExpertInspection"
                plain
                @click="emit('viewExpertInspection')"
              >
                专家查课
              </ElButton>
              <ElEmpty
                v-if="['ENDED', 'CANCELLED'].includes(props.publishStatus) && !canReviewExpertInspection"
                class="action-panel__empty"
                description="当前计划已结束，暂无可执行操作"
                :image-size="56"
              />
              <!-- 取消是破坏性操作，放在主操作行最右并以 title 交代后果，不再单独占一行。 -->
              <template v-if="!['ENDED', 'CANCELLED'].includes(props.publishStatus)">
                <span class="action-panel__spacer" aria-hidden="true" />
                <ElButton type="danger" plain title="取消后保留已有事实且不会产生通过结论" @click="emit('action', 'cancel')">取消计划</ElButton>
              </template>
            </div>
          </div>
        </section>
      </template>
      <template v-else>
        <section class="participant-board">
          <div v-if="props.publishStatus === 'DRAFT' && props.canAssign" class="participant-assign">
            <ElButton type="primary" plain @click="participantSelectionVisible = true">选择参训人员范围</ElButton>
            <ElSelect
              :model-value="props.selectedUserId"
              filterable
              clearable
              placeholder="请选择参训人员"
              @update:model-value="emit('update:selectedUserId', $event)"
            >
              <ElOption v-for="user in props.userOptions" :key="user.userId" :label="`${user.userName}（${user.userCode}）`" :value="user.userId" />
            </ElSelect>
            <ElButton type="primary" :disabled="!props.selectedUserId" @click="emit('assign')">指派参训人</ElButton>
          </div>
          <!-- 待发布提示压成一行浅底说明，不再用整块告警占据名单高度。 -->
          <p v-if="props.publishStatus === 'DRAFT'" class="participant-hint">
            <ElIcon class="participant-hint__icon"><InfoFilled /></ElIcon>
            发布前必须至少配置一名具体参训人，发布后参训名单只读。
          </p>
          <!-- 名单是这一步的阅读主体：表格吃掉剩余高度并在卡内滚动，细节收进「培训结果」弹窗。 -->
          <div class="participant-list">
            <ElTable
              :data="props.participants"
              border
              height="100%"
              class="participant-table"
              @row-click="(row) => openResultDrawer(row.trainingParticipantId)"
            >
              <ElTableColumn label="参训人" min-width="180">
                <template #default="{ row }">{{ userLabel(row.userId) }}</template>
              </ElTableColumn>
              <ElTableColumn label="过程状态" width="120">
                <template #default="{ row }">
                  <ElTag>{{ participantStatusText(row.participantStatus) }}</ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="onlineHours" label="获得线上学时" width="130" />
              <ElTableColumn prop="offlineHours" label="获得线下学时" width="130" />
              <ElTableColumn label="培训结果" width="140">
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="openResultDrawer(row.trainingParticipantId)">查看培训结果</ElButton>
                </template>
              </ElTableColumn>
              <template #empty><ElEmpty description="暂无参训人员" /></template>
            </ElTable>
          </div>
        </section>
      </template>
    </div>
    <!-- 参训名单卡不再用底栏重复「名单来源」说明，把高度全部让给名单。 -->
    <footer v-if="props.mode === 'lifecycle'" class="sticky-actions">
      <span>每一步操作都会根据当前计划进度进行检查。</span>
    </footer>
    <PersonSelectionDialog
      v-model:visible="participantSelectionVisible"
      title="选择参训人员范围"
      :model-value="participantSelection"
      :preselected-users="preselectedUsers"
      @confirm="confirmParticipantSelection"
    />
    <ElDialog v-model="generationResultVisible" class="generation-result-dialog" width="860px">
      <template #header>
        <div class="generation-result-dialog__header">
          <div>
            <h3>培训内容安排结果</h3>
            <p>本次安排已按内容逐项处理；失败项处理后可返回本页继续安排。</p>
          </div>
          <ElTag :type="props.generationResult?.failedCount ? 'warning' : 'success'" effect="light">
            {{ generationResultStatusText }}
          </ElTag>
        </div>
      </template>
      <template v-if="props.generationResult">
        <div class="generation-summary">
          <div class="generation-summary__item generation-summary__item--primary">
            <span>新安排课程/考试</span>
            <strong>{{ props.generationResult.executionLinksCreated }}</strong>
          </div>
          <div class="generation-summary__item generation-summary__item--primary">
            <span>新建线下活动</span>
            <strong>{{ props.generationResult.offlineSessionsCreated }}</strong>
          </div>
          <div class="generation-summary__item">
            <span>已存在并继续使用</span>
            <strong>{{ props.generationResult.skippedExisting }}</strong>
          </div>
          <div class="generation-summary__item" :class="{ 'generation-summary__item--danger': props.generationResult.failedCount > 0 }">
            <span>待处理失败项</span>
            <strong class="is-danger">{{ props.generationResult.failedCount }}</strong>
          </div>
        </div>
        <ElAlert
          v-if="props.generationResult.failedCount"
          title="部分培训内容未安排成功，请根据失败原因处理后再次安排。"
          description="处理完成后点击“继续安排培训内容”，系统会保留已成功创建或复用的安排。"
          type="warning"
          :closable="false"
          show-icon
        />
        <ElTable :data="props.generationResult.items" border stripe max-height="380" class="generation-items">
          <ElTableColumn prop="contentName" label="培训内容" min-width="200" />
          <ElTableColumn label="内容类型" width="100">
            <template #default="{ row }">{{ generationContentTypeText(row.contentType) }}</template>
          </ElTableColumn>
          <ElTableColumn label="安排状态" width="100">
            <template #default="{ row }">
              <ElTag :type="row.status === 'FAILED' ? 'danger' : row.status === 'EXISTING' ? 'info' : 'success'">
                {{ row.status === 'FAILED' ? '安排失败' : row.status === 'EXISTING' ? '已存在' : '已安排' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="处理说明" min-width="360">
            <template #default="{ row }">
              <div class="generation-items__detail">
                <p class="generation-items__message">{{ row.message || '请检查培训内容配置后重新安排。' }}</p>
                <div v-if="generationFailureGuide(asGenerationResultItem(row))" class="generation-items__guide">
                  <span>建议处理</span>
                  <p>{{ generationFailureGuide(asGenerationResultItem(row)) }}</p>
                  <ElButton
                    v-if="canHandleGenerationFailure(asGenerationResultItem(row))"
                    link
                    type="primary"
                    :loading="isGenerationOfferingLoading(asGenerationResultItem(row).offeringId)"
                    @click="openGenerationFailureHandling(asGenerationResultItem(row))"
                  >
                    去配置阅卷人
                  </ElButton>
                </div>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </template>
    </ElDialog>
    <ElDialog
      v-model="resultDrawerVisible"
      :title="`${selectedResultParticipantName}的培训结果`"
      width="90%"
      top="4vh"
      class="training-result-dialog"
    >
      <ElEmpty v-if="isResultLoading" description="正在读取结果，请稍候" />
      <template v-else>
        <div class="result-drawer__summary summary-grid">
          <div class="result-score-card result-score-card--status">
            <span>培训结果</span>
            <strong>{{ resultStatusText(props.results?.resultStatus) }}</strong>
            <small>按参训人全部必修内容汇总</small>
          </div>
          <div class="result-score-card">
            <span>课程成绩</span>
            <strong>{{ scoreText(props.results?.courseScore) }}</strong>
            <small>可评分课程产生正式成绩后汇总</small>
          </div>
          <div class="result-score-card">
            <span>考试成绩</span>
            <strong>{{ scoreText(props.results?.examScore) }}</strong>
            <small>考试成绩发布后同步</small>
          </div>
          <div class="result-score-card">
            <span>线下成绩</span>
            <strong>{{ scoreText(props.results?.offlineScore) }}</strong>
            <small>教师录入线下成绩后同步</small>
          </div>
          <div class="result-score-card result-score-card--hours">
            <span>获得线上学时</span>
            <strong>{{ props.results?.onlineHours ?? 0 }}</strong>
            <small>仅累计课程有效学习时长</small>
          </div>
          <div class="result-score-card result-score-card--hours">
            <span>获得线下学时</span>
            <strong>{{ props.results?.offlineHours ?? 0 }}</strong>
            <small>仅累计线下合格项时长</small>
          </div>
          <div class="result-score-card result-score-card--composite">
            <span>综合成绩</span>
            <strong>{{ props.results?.compositeScore ?? '尚未生成' }}</strong>
            <small>系统按绑定评价表自动汇总</small>
          </div>
          <div class="result-score-card">
            <span>结果确认时间</span>
            <strong>{{ props.results?.resultFinalizedTime ?? '尚未确认' }}</strong>
            <small>计划结束后冻结结果</small>
          </div>
        </div>
        <ElAlert class="result-drawer__notice" title="成绩说明" type="info" :closable="false" show-icon>
          明细出现成绩不代表一定纳入综合成绩：只有计划中标记“计入综合成绩”的课程、考试和线下内容会进入对应汇总。课程未完成只会使课程成绩保持“尚未产生”，不会清空已计入的考试或线下成绩；题库练习固定只提供反馈，不参与成绩计算。
        </ElAlert>
        <ElTable v-if="props.results?.items?.length" :data="props.results?.items" border class="result-detail-table">
          <ElTableColumn prop="contentName" label="培训内容" min-width="220" />
          <ElTableColumn label="内容类型" width="110">
            <template #default="{ row }">{{ generationContentTypeText(row.executionType) }}</template>
          </ElTableColumn>
          <ElTableColumn label="完成情况" width="120">
            <template #default="{ row }">{{ resultStatusText(row.resultStatus) }}</template>
          </ElTableColumn>
          <ElTableColumn prop="score" label="成绩" width="100">
            <template #default="{ row }">{{ scoreText(row.score) }}</template>
          </ElTableColumn>
          <ElTableColumn label="综合成绩纳入" min-width="170">
            <template #default="{ row }">{{ scoreParticipationText(asResultItem(row)) }}</template>
          </ElTableColumn>
          <ElTableColumn label="获得学时" width="120">
            <template #default="{ row }">{{ earnedHoursText(row.effectiveDurationSeconds) }}</template>
          </ElTableColumn>
          <ElTableColumn label="成绩来源说明" min-width="220">
            <template #default="{ row }">{{ scoreSourceHint(row.executionType) }}</template>
          </ElTableColumn>
        </ElTable>
        <ElEmpty v-else description="暂无培训结果明细" />
      </template>
    </ElDialog>
    <ElDrawer
      v-if="props.mode === 'lifecycle'"
      v-model="endPrecheckDrawerVisible"
      title="结束前检查"
      direction="rtl"
      size="560px"
      class="end-precheck-drawer"
    >
      <section class="end-precheck end-precheck--drawer" :class="{ 'is-ready': isEndPrecheckReady, 'has-error': Boolean(props.endPrecheckError) }">
        <div class="end-precheck__header">
          <div class="end-precheck__identity">
            <span class="end-precheck__icon" aria-hidden="true">
              <CircleCheckFilled v-if="isEndPrecheckReady" />
              <WarningFilled v-else />
            </span>
            <div>
              <span class="end-precheck__eyebrow">PLAN CLOSURE</span>
              <h3>{{ endPrecheckSummary.title }}</h3>
              <p>基于已加载的执行、线下场次及逐人结果核对；最终仍以后端结束门禁为准。</p>
            </div>
          </div>
          <ElButton plain :icon="Refresh" :loading="props.endPrecheckLoading" @click="emit('refreshEndPrecheck')">刷新检查</ElButton>
        </div>
        <div v-if="props.endPrecheckLoading" class="end-precheck__loading">
          <span>正在读取最新执行事实，请稍候…</span>
        </div>
        <ElAlert v-else-if="props.endPrecheckError" :title="props.endPrecheckError" type="warning" :closable="false" show-icon />
        <div v-else-if="props.endPrecheckBlockers?.length" class="end-precheck__body">
          <!-- 计划级检查项与具体参训人无关，固定展示在人员分组之前。 -->
          <section v-if="planLevelBlockers.length" class="end-precheck__section">
            <header class="end-precheck__section-head">
              <strong>计划级检查项</strong>
              <span>不针对具体参训人，处理完成后可再次刷新检查。</span>
            </header>
            <div class="end-precheck__grid">
              <article v-for="(blocker, index) in planLevelBlockers" :key="`${blocker.title}-${index}`" class="end-precheck__item">
                <span class="end-precheck__number">{{ String(index + 1).padStart(2, '0') }}</span>
                <div>
                  <span class="end-precheck__category">{{ endPrecheckCategory(blocker.title) }}</span>
                  <strong>{{ blocker.title }}</strong>
                  <p>{{ blocker.description }}</p>
                </div>
              </article>
            </div>
          </section>
          <!-- 参训人未完成项按人折叠：多人时先看每人还差几项，再按需展开明细。 -->
          <section v-if="participantBlockerGroups.length" class="end-precheck__section">
            <header class="end-precheck__section-head">
              <strong>参训人未完成项</strong>
              <span>{{ participantBlockerSummary }}</span>
              <ElButton link type="primary" @click="toggleAllBlockerGroups">
                {{ allBlockerGroupsOpened ? '全部收起' : '全部展开' }}
              </ElButton>
            </header>
            <ElCollapse v-model="openedBlockerGroups" class="end-precheck__groups">
              <ElCollapseItem v-for="group in participantBlockerGroups" :key="group.key" :name="group.key">
                <template #title>
                  <div class="end-precheck__group-title">
                    <span class="end-precheck__group-name">{{ group.name }}</span>
                    <ElTag size="small" type="warning" effect="plain">{{ group.items.length }} 项未完成</ElTag>
                    <span class="end-precheck__group-summary">
                      {{ group.categories.map((category) => `${category.label} ${category.count} 项`).join(' · ') }}
                    </span>
                  </div>
                </template>
                <div class="end-precheck__grid end-precheck__grid--group">
                  <article v-for="(blocker, index) in group.items" :key="`${blocker.title}-${index}`" class="end-precheck__item">
                    <span class="end-precheck__number">{{ String(index + 1).padStart(2, '0') }}</span>
                    <div>
                      <span class="end-precheck__category">{{ endPrecheckCategory(blocker.title) }}</span>
                      <strong>{{ blocker.title }}</strong>
                      <p>{{ blocker.description }}</p>
                    </div>
                  </article>
                </div>
              </ElCollapseItem>
            </ElCollapse>
          </section>
        </div>
        <div v-else class="end-precheck__ready">
          <CircleCheckFilled aria-hidden="true" />
          <div>
            <strong>所有已知前置条件已满足</strong>
            <span>确认无误后，可执行结束培训并冻结最终结果。</span>
          </div>
        </div>
      </section>
      <template #footer>
        <div class="end-precheck-drawer__footer">
          <span>
            {{
              isEndPrecheckReady
                ? '结束后将冻结最终结果，请确认全部数据正确。'
                : props.endPrecheckError
                  ? '页面未能完整读取事实，提交后将由后端返回最终结果。'
                  : '请先处理检查项，再刷新确认。'
            }}
          </span>
          <ElButton type="success" :disabled="!canEndPlan" :title="endPlanTitle" @click="emit('action', 'end')">结束培训并确认结果</ElButton>
        </div>
      </template>
    </ElDrawer>
  </ElCard>
</template>

<style scoped lang="less">
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 56px;
}
/* 卡头左栏：标题、说明与关键指标纵向排列，指标紧贴说明便于一眼读到。 */
.card-header__main {
  min-width: 0;
}
.card-header__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 20px;
  margin-top: 6px;
}
.card-header__stat {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}
.card-header__stat i {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-style: normal;
  line-height: 18px;
}
.card-header__stat b {
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: 600;
  line-height: 20px;
}
.card-header h2,
.action-panel h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 20px;
}
.card-header p,
.action-panel p,
.sticky-actions span {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
/* 面板自身已经是内容卡，内部不再套第二层带边框的白卡，避免“卡中卡”。 */
.execution-scroll {
  padding: 10px 0 14px;
}
.lifecycle-board,
.participant-board {
  padding: 0;
  border: 0;
  background: transparent;
}
/* 参训名单模式：正文不再整体滚动，改由名单表格在卡内滚动，避免整页长滚把名单推走。 */
.execution-scroll--list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
/* 名单卡正文是一列弹性容器：工具栏与提示固定，名单吃掉剩余高度。 */
.participant-board {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
}
/* “下一步操作”是这一步的行动落点，用浅底内嵌块从内容中区分出来。 */
.action-panel {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-subtle);
}
/* 标题与提示同基线成行，避免每行独占造成垂直浪费。 */
.action-panel__intro {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 10px;
}
.action-panel__intro p {
  margin: 0;
}
/* 状态流：四段等宽进度，序号与文案左右分栏，整体压到一行高度。 */
.status-flow {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}
.status-flow__item {
  position: relative;
  display: grid;
  min-width: 0;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: center;
  column-gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-subtle);
}
.status-flow__item:not(:last-child)::after {
  position: absolute;
  top: 50%;
  right: -8px;
  z-index: 1;
  width: 8px;
  height: 2px;
  background: var(--color-border);
  content: '';
  transform: translateY(-50%);
}
.status-flow__index {
  display: grid;
  width: 22px;
  height: 22px;
  grid-row: 1 / span 2;
  place-items: center;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.status-flow__item.is-active {
  border-color: var(--color-primary);
  background: var(--color-primary-subtle);
}
.status-flow__item.is-active .status-flow__index {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}
.status-flow__item.is-complete {
  border-color: color-mix(in srgb, var(--color-success, #67c23a) 45%, var(--color-border));
}
.status-flow__item.is-complete .status-flow__index {
  border-color: var(--color-success, #67c23a);
  color: var(--color-success, #67c23a);
}
.status-flow__item.is-muted {
  opacity: 0.6;
}
.status-flow__item > strong,
.status-flow__item > span:last-child {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status-flow__item > strong {
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 18px;
}
/* 步骤说明从卡片内收起（悬停看 title），把纵向空间让给下方参训名单。 */
.status-flow__item > span:last-child {
  display: none;
}
/* 关键指标：横向一条紧凑指标带，标签与数值同行，首屏内可看全且不喧宾夺主。 */
.summary-grid {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: stretch;
  margin-bottom: 6px;
  padding: 0 2px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-subtle);
}
.summary-grid > div,
.summary-grid__card {
  display: flex;
  min-width: 0;
  flex: 1 1 0;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 2px 8px;
  padding: 7px 14px;
  border: 0;
  border-radius: 0;
  background: transparent;
}
/* 指标之间用竖线分隔，避免再堆一层卡片边框。 */
.summary-grid > * + * {
  border-left: 1px solid var(--color-border);
}
.summary-grid__card {
  width: auto;
  color: inherit;
  font: inherit;
  text-align: left;
}
.summary-grid__card--clickable {
  cursor: pointer;
}
.summary-grid__card--clickable:hover {
  background: color-mix(in srgb, var(--color-primary) 7%, transparent);
}
.summary-grid__card--clickable:disabled {
  cursor: default;
}
.summary-grid__card small {
  margin-top: 0;
  color: var(--color-primary);
  font-size: 11px;
  line-height: 16px;
}
.summary-grid span,
.summary-grid strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.summary-grid span {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.summary-grid strong {
  margin-top: 0;
  color: var(--color-text-primary);
  font-size: 16px;
  line-height: 22px;
}
.action-panel__buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.action-panel--empty {
  display: flex;
  min-height: 156px;
  flex-direction: column;
}
.action-panel--empty .action-panel__buttons {
  flex: 1;
  align-items: center;
  justify-content: center;
  margin-top: 0;
}
.action-panel__empty {
  padding: 4px 0;
}
.participant-assign {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 10px;
}
/* 待发布提示：一行浅底说明，保留必要结论又不占整块高度。 */
.participant-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px;
  padding: 7px 10px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-primary) 7%, transparent);
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.participant-hint__icon {
  flex: 0 0 auto;
  color: var(--color-primary);
}
/* 名单容器是表格的定高父级，表格用 height="100%" 在容器内滚动。 */
.participant-list {
  min-height: 0;
  flex: 1 1 auto;
}
.participant-table {
  margin-top: 0;
  cursor: pointer;
}
.result-drawer__summary {
  margin-bottom: 16px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.end-precheck {
  margin: 0;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--color-warning, #e6a23c) 34%, var(--color-border));
  border-radius: 10px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--color-warning, #e6a23c) 7%, var(--color-bg-surface)), var(--color-bg-surface) 46%);
}
.end-precheck.is-ready {
  border-color: color-mix(in srgb, var(--color-success, #67c23a) 38%, var(--color-border));
  background: linear-gradient(135deg, color-mix(in srgb, var(--color-success, #67c23a) 7%, var(--color-bg-surface)), var(--color-bg-surface) 46%);
}
.end-precheck.has-error {
  border-color: color-mix(in srgb, var(--color-danger, #f56c6c) 34%, var(--color-border));
}
.end-precheck__header {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  padding: 18px 20px;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border) 70%, transparent);
}
.end-precheck__identity {
  display: flex;
  flex: 1 1 0;
  align-items: flex-start;
  gap: 12px;
}
.end-precheck__icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: 10px;
  background: color-mix(in srgb, var(--color-warning, #e6a23c) 14%, transparent);
  color: var(--color-warning, #e6a23c);
  font-size: 20px;
}
.is-ready .end-precheck__icon {
  background: color-mix(in srgb, var(--color-success, #67c23a) 14%, transparent);
  color: var(--color-success, #67c23a);
}
.end-precheck__eyebrow {
  display: block;
  margin-bottom: 2px;
  color: var(--color-text-secondary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.end-precheck__header h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
  line-height: 20px;
}
.end-precheck__header p {
  max-width: 520px;
  margin: 4px 0 0;
}
.end-precheck__loading,
.end-precheck__ready {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 74px;
  padding: 16px 20px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.end-precheck__ready {
  color: var(--color-success, #67c23a);
}
.end-precheck__ready > svg {
  width: 20px;
  flex: 0 0 auto;
  font-size: 24px;
}
.end-precheck__ready div {
  display: grid;
  gap: 2px;
}
.end-precheck__ready strong {
  color: var(--color-text-primary);
}
.end-precheck__ready span {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.end-precheck__body {
  display: grid;
  gap: 14px;
  max-height: calc(100vh - 280px);
  padding: 14px 20px;
  overflow-y: auto;
}
.end-precheck__section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.end-precheck__section-head strong {
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 20px;
}
.end-precheck__section-head > span {
  min-width: 0;
  flex: 1 1 auto;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.end-precheck__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}
.end-precheck__grid--group {
  padding-top: 2px;
}
.end-precheck__group-title {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}
.end-precheck__group-name {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.end-precheck__group-summary {
  overflow: hidden;
  min-width: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 折叠面板内部由 Element Plus 自行渲染，样式需按抽屉类名全局命中。 */
:global(.end-precheck-drawer .end-precheck__groups) {
  border-top: 0;
  border-bottom: 0;
}
:global(.end-precheck-drawer .end-precheck__groups .el-collapse-item) {
  overflow: hidden;
  margin-bottom: 8px;
  border: 1px solid color-mix(in srgb, var(--color-warning, #e6a23c) 18%, var(--color-border));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-bg-surface) 92%, transparent);
}
:global(.end-precheck-drawer .end-precheck__groups .el-collapse-item:last-child) {
  margin-bottom: 0;
}
:global(.end-precheck-drawer .end-precheck__groups .el-collapse-item__header) {
  height: auto;
  min-height: 44px;
  padding: 10px 12px;
  border-bottom: 0;
  background: transparent;
  line-height: 20px;
}
:global(.end-precheck-drawer .end-precheck__groups .el-collapse-item__arrow) {
  margin-right: 8px;
  color: var(--color-text-secondary);
}
:global(.end-precheck-drawer .end-precheck__groups .el-collapse-item__wrap) {
  border-bottom: 0;
  background: transparent;
}
:global(.end-precheck-drawer .end-precheck__groups .el-collapse-item__content) {
  padding: 0 12px 12px;
  font-size: 12px;
}
.end-precheck__item {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  min-width: 0;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--color-warning, #e6a23c) 18%, var(--color-border));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-bg-surface) 92%, transparent);
}
.end-precheck__number {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-warning, #e6a23c) 12%, transparent);
  color: var(--color-warning, #b88230);
  font-size: 11px;
  font-weight: 700;
}
.end-precheck__item > div {
  min-width: 0;
}
.end-precheck__category {
  display: inline-block;
  margin-bottom: 3px;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--color-bg-subtle);
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 18px;
}
.end-precheck__item strong {
  display: block;
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 18px;
}
.end-precheck__item p {
  margin: 4px 0 0;
  color: var(--color-text-regular);
  font-size: 12px;
  line-height: 18px;
}
.end-precheck-compact {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--color-warning, #e6a23c) 24%, var(--color-border));
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-warning, #e6a23c) 6%, var(--color-bg-surface));
}
.end-precheck-compact.is-ready {
  border-color: color-mix(in srgb, var(--color-success, #67c23a) 28%, var(--color-border));
  background: color-mix(in srgb, var(--color-success, #67c23a) 6%, var(--color-bg-surface));
}
.end-precheck-compact__icon {
  color: var(--color-warning, #e6a23c);
  font-size: 18px;
}
.is-ready .end-precheck-compact__icon {
  color: var(--color-success, #67c23a);
}
.end-precheck-compact > div {
  display: grid;
  min-width: 0;
  flex: 1 1 auto;
  gap: 1px;
}
.end-precheck-compact > div > span,
.end-precheck-compact > div > small {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.end-precheck-compact strong {
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 20px;
}
:global(.end-precheck-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--color-border);
}
:global(.end-precheck-drawer .el-drawer__body) {
  padding: 20px;
}
:global(.end-precheck-drawer .el-drawer__footer) {
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
}
.end-precheck-drawer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.end-precheck-drawer__footer span {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
/* 主操作行内的弹性占位，把破坏性操作推到最右又不引入额外左边距。 */
.action-panel__spacer {
  flex: 1 1 auto;
}
.card-header__title {
  display: flex;
  align-items: center;
  gap: 4px;
}
.result-score-card {
  min-height: 78px;
  padding: 10px 12px;
  border-color: color-mix(in srgb, var(--color-primary) 16%, var(--color-border));
  background: linear-gradient(180deg, color-mix(in srgb, var(--color-primary) 5%, var(--color-bg-surface)), var(--color-bg-subtle));
}
.result-score-card--status {
  border-color: color-mix(in srgb, var(--color-success, #67c23a) 28%, var(--color-border));
  background: color-mix(in srgb, var(--color-success, #67c23a) 6%, var(--color-bg-surface));
}
.result-score-card--status strong {
  color: var(--color-success, #529b2e);
}
.result-score-card--hours strong {
  color: var(--color-primary);
}
.result-score-card--composite {
  border-color: color-mix(in srgb, var(--color-warning, #e6a23c) 28%, var(--color-border));
  background: color-mix(in srgb, var(--color-warning, #e6a23c) 6%, var(--color-bg-surface));
}
.result-score-card small {
  display: block;
  margin-top: 4px;
  color: var(--color-text-secondary);
  font-size: 11px;
  line-height: 16px;
}
:global(.training-result-dialog) {
  min-width: 960px;
  margin-bottom: 0;
}
:global(.training-result-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--color-border);
}
:global(.training-result-dialog .el-dialog__body) {
  max-height: calc(92vh - 86px);
  padding: 16px 20px 24px;
  overflow-y: auto;
}
.result-detail-table {
  margin-top: 4px;
}
.result-drawer__notice {
  margin-bottom: 16px;
}
.action-panel__hint {
  align-self: center;
  color: var(--color-warning, #e6a23c);
  font-size: 12px;
}
.result-drawer__summary .is-danger {
  color: var(--color-danger, #f56c6c);
}
.generation-result-dialog__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-right: 28px;
}
.generation-result-dialog__header h3 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 18px;
  line-height: 26px;
}
.generation-result-dialog__header p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
.generation-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.generation-summary__item {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-subtle);
}
.generation-summary__item--primary {
  border-color: color-mix(in srgb, var(--color-primary) 24%, var(--color-border));
  background: color-mix(in srgb, var(--color-primary) 6%, var(--color-bg-subtle));
}
.generation-summary__item--danger {
  border-color: color-mix(in srgb, var(--color-danger, #f56c6c) 32%, var(--color-border));
  background: color-mix(in srgb, var(--color-danger, #f56c6c) 7%, var(--color-bg-subtle));
}
.generation-summary__item span,
.generation-summary__item strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.generation-summary__item span {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.generation-summary__item strong {
  margin-top: 4px;
  color: var(--color-text-primary);
  font-size: 22px;
  line-height: 28px;
}
.generation-summary__item--danger strong {
  color: var(--color-danger, #f56c6c);
}
.generation-items {
  margin-top: 16px;
}
.generation-items__detail {
  display: grid;
  gap: 8px;
}
.generation-items__message,
.generation-items__guide p {
  margin: 0;
  line-height: 20px;
}
.generation-items__message {
  color: var(--color-text-regular);
}
.generation-items__guide {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 4px;
  background: var(--color-bg-subtle);
  font-size: 12px;
}
.generation-items__guide span {
  display: block;
  margin-bottom: 2px;
  color: var(--color-primary);
  font-weight: 600;
  line-height: 18px;
}
.generation-items__guide p {
  color: var(--color-text-regular);
}
/* 面板底部只承载一句口径说明，压到最薄一行，不再空占一条操作栏高度。 */
.sticky-actions {
  align-items: center;
  justify-content: flex-start;
  padding: 6px 0 0;
  border-top: 0;
  background: transparent;
  backdrop-filter: none;
}
@media (max-width: 1000px) {
  .status-flow,
  .summary-grid,
  .generation-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .status-flow,
  .summary-grid,
  .generation-summary,
  .participant-assign {
    grid-template-columns: 1fr;
  }
  .end-precheck__header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
