<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { getExamAttemptRecordPage, type ExamAttemptRecord } from '@/api/exam-grading';
import { useUserStore } from '@/stores';
import { ATTEMPT_STATUS_OPTIONS, SCORE_PUBLISH_STATUS_OPTIONS, getExamEnumLabel } from '@/constants/exam-options';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { hasPermission } from '@/utils/permission';
import OfflineScoreDialog from './OfflineScoreDialog.vue';

const props = defineProps<{ examId: number; examType?: string; passScore?: number; openStartTime?: string }>();
const router = useRouter();
const userStore = useUserStore();
const rows = ref<ExamAttemptRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const learnerKeyword = ref('');
const attemptStatus = ref<string>();
const reviewStatus = ref<ExamAttemptRecord['reviewStatus']>();
const scorePublishStatus = ref<ExamAttemptRecord['scorePublishStatus']>();
const offlineScoreVisible = ref(false);
const canReview = hasPermission(userStore.userInfo, 'exam:review');
const canPublishScore = hasPermission(userStore.userInfo, 'exam:score:publish');
/** 线下考试没有在线作答，成绩只能由管理端录入，因此单独提供录入入口。 */
const canRecordOfflineScore = computed(() => props.examType === 'OFFLINE' && canPublishScore);

/** 当前 Panel 始终限定在工作台所属考试，避免跨考试混合展示作答记录。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const page = await getExamAttemptRecordPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      'query.examId': props.examId,
      'query.learnerKeyword': learnerKeyword.value.trim() || undefined,
      'query.attemptStatus': attemptStatus.value,
      'query.reviewStatus': reviewStatus.value,
      'query.scorePublishStatus': scorePublishStatus.value
    });
    rows.value = page.list;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

function query(): void {
  pageNum.value = 1;
  void load();
}

/** 批阅页负责展示完整作答快照和主观题评分，服务端继续校验批阅授权。 */
function openGrading(row: ExamAttemptRecord): void {
  void router.push(`/admin/business-center/exams/exam/${props.examId}/grading/${row.examAttemptId}`);
}

/** 成绩处置页复用作答快照、发布前校验及成绩发布流水。 */
function openResult(row: ExamAttemptRecord): void {
  void router.push({
    path: `/admin/business-center/exams/exam/${props.examId}/results/${row.examAttemptId}`,
    query: { examCandidateId: row.examCandidateId }
  });
}

watch(
  () => props.examId,
  () => query(),
  { immediate: true }
);
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card admin-detail-card-spacing">
    <template #header>
      <div class="panel-header">
        <span>学员考试情况</span>
        <ElButton v-if="canRecordOfflineScore" type="primary" plain @click="offlineScoreVisible = true">录入线下成绩</ElButton>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll" style="display: flex; flex-direction: column">
      <ElAlert title="可查看当前考试的学员作答、批阅和成绩发布情况；可进行的操作会根据您的权限显示。" type="info" :closable="false" />
      <ElForm inline class="filters">
        <ElFormItem label="学员"><ElInput v-model="learnerKeyword" clearable placeholder="姓名或工号" @keyup.enter="query" /></ElFormItem>
        <ElFormItem label="作答状态">
          <ElSelect v-model="attemptStatus" clearable placeholder="全部状态">
            <ElOption v-for="item in ATTEMPT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="批阅状态">
          <ElSelect v-model="reviewStatus" clearable placeholder="全部状态">
            <ElOption label="无需人工批阅" value="NOT_REQUIRED" />
            <ElOption label="待批阅" value="PENDING" />
            <ElOption label="已完成" value="COMPLETED" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="成绩发布">
          <ElSelect v-model="scorePublishStatus" clearable placeholder="全部状态">
            <ElOption v-for="item in SCORE_PUBLISH_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem><ElButton type="primary" @click="query">查询</ElButton></ElFormItem>
      </ElForm>
      <ElTable v-loading="loading" :data="rows" border stripe style="flex: 1">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="学员" min-width="150">
          <template #default="{ row }">
            <ElTag>{{ row.learnerName }}</ElTag>
            &nbsp;
            <ElTag>{{ row.learnerCode || '无工号' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="attemptNo" label="次数" width="70" />
        <ElTableColumn prop="startedTime" label="开始时间" min-width="170" />
        <ElTableColumn prop="submittedTime" label="交卷时间" min-width="170" />
        <ElTableColumn prop="usedSeconds" label="用时（秒）" width="110" />
        <ElTableColumn label="作答状态" width="120">
          <template #default="{ row }">{{ getExamEnumLabel(row.attemptStatus, ATTEMPT_STATUS_OPTIONS) }}</template>
        </ElTableColumn>
        <ElTableColumn label="批阅状态" width="120">
          <template #default="{ row }">
            {{ row.reviewStatus === 'PENDING' ? '待批阅' : row.reviewStatus === 'COMPLETED' ? '已完成' : '无需批阅' }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="finalScore" label="成绩" width="90" />
        <ElTableColumn label="成绩发布" width="120">
          <template #default="{ row }">{{ getExamEnumLabel(row.scorePublishStatus, SCORE_PUBLISH_STATUS_OPTIONS) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <ElButton v-if="canReview" link type="primary" @click="openGrading(row as ExamAttemptRecord)">批阅试卷</ElButton>
            <ElButton v-if="canPublishScore" link type="primary" @click="openResult(row as ExamAttemptRecord)">查看与处置</ElButton>
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
          @current-change="load"
          @size-change="query"
        />
      </div>
    </div>
    <OfflineScoreDialog v-model="offlineScoreVisible" :exam-id="examId" :pass-score="passScore" :open-start-time="openStartTime" @saved="load" />
  </ElCard>
</template>

<style scoped lang="less">
.filters {
  margin-top: 18px;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.el-input,
.el-select {
  width: 210px;
}
small {
  color: var(--color-text-secondary);
}
</style>
