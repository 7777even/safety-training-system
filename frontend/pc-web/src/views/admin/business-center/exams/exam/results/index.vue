<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getExamAttemptRecordPage, type ExamAttemptRecord } from '@/api/exam-grading';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';

const router = useRouter();
/** 当前数据范围内的考试作答与成绩记录。 */
const rows = ref<ExamAttemptRecord[]>([]);
const total = ref(0);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const learnerKeyword = ref('');
const reviewStatus = ref<ExamAttemptRecord['reviewStatus']>();
const scorePublishStatus = ref<ExamAttemptRecord['scorePublishStatus']>();

/** 读取管理员有权查看的考试成绩记录。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const page = await getExamAttemptRecordPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      'query.learnerKeyword': learnerKeyword.value.trim() || undefined,
      'query.reviewStatus': reviewStatus.value,
      'query.scorePublishStatus': scorePublishStatus.value
    });
    rows.value = page.list;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

/** 使用新筛选条件时回到第一页。 */
function query(): void {
  pageNum.value = 1;
  void load();
}

/** 进入单次作答的成绩发布、补考和练习重置页面。 */
function openResult(row: ExamAttemptRecord): void {
  void router.push({
    path: `/admin/business-center/exams/exam/${row.examId}/results/${row.examAttemptId}`,
    query: { examCandidateId: row.examCandidateId }
  });
}

onMounted(() => void load());
</script>

<template>
  <section class="admin-business-page page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline>
        <ElFormItem label="学员">
          <ElInput v-model="learnerKeyword" clearable placeholder="姓名或工号" @keyup.enter="query" />
        </ElFormItem>
        <ElFormItem label="批阅状态">
          <ElSelect v-model="reviewStatus" clearable placeholder="全部状态">
            <ElOption label="无需人工批阅" value="NOT_REQUIRED" />
            <ElOption label="待批阅" value="PENDING" />
            <ElOption label="已完成" value="COMPLETED" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="发布状态">
          <ElSelect v-model="scorePublishStatus" clearable placeholder="全部状态">
            <ElOption label="未发布" value="UNPUBLISHED" />
            <ElOption label="已发布" value="PUBLISHED" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="query">查询</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>成绩列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton :loading="loading" @click="load">刷新</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="rows">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="examName" label="考试" min-width="190" />
        <ElTableColumn label="学员" min-width="150">
          <template #default="{ row }">
            <ElTag>{{ (row as ExamAttemptRecord).learnerName }}</ElTag>
            &nbsp;
            <ElTag>{{ (row as ExamAttemptRecord).learnerCode || '无工号' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="attemptNo" label="次数" width="70" />
        <ElTableColumn prop="submittedTime" label="交卷时间" min-width="170" />
        <ElTableColumn label="批阅状态" width="120">
          <template #default="{ row }">
            <ElTag :type="(row as ExamAttemptRecord).reviewStatus === 'PENDING' ? 'warning' : 'success'">
              {{
                (row as ExamAttemptRecord).reviewStatus === 'PENDING'
                  ? '待批阅'
                  : (row as ExamAttemptRecord).reviewStatus === 'COMPLETED'
                    ? '已完成'
                    : '无需批阅'
              }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="成绩" width="100">
          <template #default="{ row }">
            {{ (row as ExamAttemptRecord).finalScore ?? '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="发布状态" width="110">
          <template #default="{ row }">
            <ElTag :type="['PUBLISHED', 'EFFECTIVE'].includes((row as ExamAttemptRecord).scorePublishStatus) ? 'success' : 'info'">
              {{ (row as ExamAttemptRecord).scorePublishStatus === 'EFFECTIVE' ? '已生效' : (row as ExamAttemptRecord).scorePublishStatus === 'PUBLISHED' ? '已发布' : '未发布' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openResult(row as ExamAttemptRecord)">成绩处置</ElButton>
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
    </ElCard>
  </section>
</template>

<style scoped lang="less">
.pagination {
  justify-content: flex-end;
  margin-top: var(--space-2);
}

.el-input,
.el-select {
  width: 210px;
}
</style>
