<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { deleteExam, getExamPage, type ExamItem, type ExamPublishStatus } from '@/api/exam';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import { getExamTypeLabel } from '@/constants/exam-options';

const router = useRouter();
const userStore = useUserStore();
/** 根据 profile 按钮权限控制考试操作入口。 */
const can = (permission: string): boolean => hasPermission(userStore.userInfo, permission);
const rows = ref<ExamItem[]>([]);
const total = ref(0);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const examName = ref('');
const publishStatus = ref<ExamPublishStatus>();
const STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' }
] as const;

/** 固定 Element Plus 表格插槽的行类型。 */
function asExam(row: unknown): ExamItem {
  return row as ExamItem;
}

/** 按当前筛选条件读取考试定义。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const page = await getExamPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      'query.examName': examName.value || undefined,
      'query.publishStatus': publishStatus.value
    });
    rows.value = page.list;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

/** 查询时回到第一页，避免筛选后仍停留在空页。 */
function query(): void {
  pageNum.value = 1;
  void load();
}

/** 列表资源只显示接口返回的名称，资源未绑定或已删除时不暴露内部 ID。 */
function resourceLabel(exam: ExamItem): string {
  if (exam.paperName) return exam.paperVersionNo == null ? exam.paperName : `${exam.paperName}（版本 ${exam.paperVersionNo}）`;
  return exam.questionBankName || '未设置';
}

/** 从列表进入工作台时携带已加载的名称，页签无需再为标题发起请求。 */
async function openExam(row: ExamItem, stage?: 'assignment' | 'attempts'): Promise<void> {
  await router.push({
    path: `/admin/business-center/exams/exam/${row.examId}`,
    query: { ...(stage ? { stage } : {}), tabTitle: row.examName }
  });
}

/** 删除前保留轻量确认，复杂编辑流程由独立页面承载。 */
async function remove(row: ExamItem): Promise<void> {
  await ElMessageBox.confirm(`确定删除考试“${row.examName}”吗？`, '删除考试', { type: 'warning' });
  await deleteExam(row.examId);
  ElMessage.success('考试草稿已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline>
        <ElFormItem label="考试名称">
          <ElInput v-model="examName" clearable placeholder="请输入考试名称" @keyup.enter="query" />
        </ElFormItem>
        <ElFormItem label="发布状态">
          <ElSelect v-model="publishStatus" clearable placeholder="全部状态">
            <ElOption v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
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
          <strong>考试列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="can('exam:exam:add')" type="primary" @click="router.push('/admin/business-center/exams/exam/create')">新增考试</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="rows">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="examCode" label="考试编码" width="150" />
        <ElTableColumn prop="examName" label="考试名称" min-width="220" />
        <ElTableColumn label="考试类型" width="110"><template #default="{ row }">{{ getExamTypeLabel(asExam(row).examType) }}</template></ElTableColumn>
        <ElTableColumn label="来源" width="100">
          <template #default="{ row }">
            <ElTag :type="asExam(row).planContentId == null ? 'info' : 'primary'">
              {{ asExam(row).planContentId == null ? '独立考试' : '培训生成' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="openStartTime" label="开放开始时间" width="180" />
        <ElTableColumn prop="openEndTime" label="开放结束时间" width="180" />
        <ElTableColumn label="发布状态" width="110">
          <template #default="{ row }">
            <ElTag :type="asExam(row).publishStatus === 'PUBLISHED' ? 'success' : 'info'">
              {{ asExam(row).publishStatus === 'PUBLISHED' ? '已发布' : '草稿' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="试卷版本/题库" min-width="200"><template #default="{ row }">{{ resourceLabel(asExam(row)) }}</template></ElTableColumn>
        <ElTableColumn label="操作" width="320">
          <template #default="{ row }">
            <ElButton v-if="can('exam:exam:list')" link type="primary" @click="router.push(`/admin/business-center/exams/exam/${asExam(row).examId}/statistics`)">考情分析</ElButton>
            <ElButton
              v-if="can('exam:exam:list')"
              link
              type="primary"
              @click="openExam(asExam(row))"
            >
              详情
            </ElButton>
            <ElButton
              v-if="can('exam:exam:edit')"
              link
              type="primary"
              @click="openExam(asExam(row), 'assignment')"
            >
              人员配置
            </ElButton>
            <ElButton
              v-if="can('exam:exam:list') && asExam(row).publishStatus === 'PUBLISHED'"
              link
              type="primary"
              @click="openExam(asExam(row), 'attempts')"
            >
              考试情况
            </ElButton>
            <ElButton v-if="can('exam:exam:delete') && asExam(row).publishStatus === 'DRAFT'" link type="danger" @click="remove(asExam(row))">
              删除
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无考试" />
        </template>
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

<style scoped lang="less"></style>
