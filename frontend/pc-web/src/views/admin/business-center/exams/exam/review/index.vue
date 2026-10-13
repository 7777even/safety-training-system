<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getExamReviewTaskPage, type ExamReviewTask, type ExamReviewTaskStatus } from '@/api/exam-grading';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';

const router = useRouter();
/** 当前教师有权处理的批阅任务。 */
const rows = ref<ExamReviewTask[]>([]);
const total = ref(0);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const reviewStatus = ref<ExamReviewTaskStatus>('PENDING');
const learnerKeyword = ref('');

/** 读取当前登录教师的批阅任务，权限与数据范围由后端 Contract 保证。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const page = await getExamReviewTaskPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      'query.reviewStatus': reviewStatus.value,
      'query.learnerKeyword': learnerKeyword.value.trim() || undefined
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

/** 进入当前作答的独立批阅页。 */
function openReview(row: ExamReviewTask): void {
  void router.push(`/admin/business-center/exams/exam/${row.examId}/grading/${row.examAttemptId}`);
}

onMounted(() => void load());
</script>

<template>
  <section class="admin-business-page page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline>
        <ElFormItem label="批阅状态">
          <ElSelect v-model="reviewStatus" clearable placeholder="全部状态">
            <ElOption label="待批阅" value="PENDING" />
            <ElOption label="已批阅" value="REVIEWED" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="学员">
          <ElInput v-model="learnerKeyword" clearable placeholder="姓名或工号" @keyup.enter="query" />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="query">查询</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>批阅列表</strong>
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
        <ElTableColumn prop="examName" label="考试" width="190" />
        <ElTableColumn label="学员" width="160">
          <template #default="{ row }">
            <ElTag>{{ (row as ExamReviewTask).learnerName }}</ElTag>
            &nbsp;
            <ElTag>{{ (row as ExamReviewTask).learnerCode || '无工号' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="stemSnapshot" label="待阅题目" min-width="260" show-overflow-tooltip />
        <ElTableColumn prop="score" label="满分" width="80">
          <template #default="{ row }">
            <ElTag>
              {{ (row as ExamReviewTask).score }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="submittedTime" label="交卷时间" width="170" />
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="(row as ExamReviewTask).reviewStatus === 'REVIEWED' ? 'success' : 'warning'">
              {{ (row as ExamReviewTask).reviewStatus === 'REVIEWED' ? '已批阅' : '待批阅' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openReview(row as ExamReviewTask)">
              {{ (row as ExamReviewTask).reviewStatus === 'REVIEWED' ? '查看批阅' : '批阅试卷' }}
            </ElButton>
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
small {
  color: var(--color-text-secondary);
}

.pagination {
  justify-content: flex-end;
  margin-top: var(--space-2);
}

.el-input,
.el-select {
  width: 220px;
}
</style>
