<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { EXAM_REFERENCE_PAGE_SIZE, getExamCandidates, recordOfflineScores, type ExamCandidate } from '@/api/exam';
import { LIST_PAGE_SIZES } from '@/constants/pagination';

const visible = defineModel<boolean>({ required: true });
const props = defineProps<{
  examId: number;
  passScore?: number;
  openStartTime?: string;
}>();
const emit = defineEmits<{ saved: [] }>();
/** 学员姓名或工号筛选，用于在长名单中定位考生。 */
const keyword = ref('');
const rows = ref<ExamCandidate[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(EXAM_REFERENCE_PAGE_SIZE);
const loading = ref(false);
const saving = ref(false);
/** 本次待录入的成绩草稿，仅提交已填写分数的考生。 */
const drafts = reactive<Record<number, { finalScore?: number; remark: string }>>({});

/** 线下考试默认百分制；及格分高于 100 时，录入上限跟随及格分，保证及格分可达。 */
const maxScore = computed(() => Math.max(100, props.passScore ?? 100));

/** 读取当前考试考生名单，并清空上一轮未提交的草稿，避免串场录入。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const page = await getExamCandidates(props.examId, {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      'query.learnerKeyword': keyword.value.trim() || undefined
    });
    rows.value = page.list;
    total.value = page.total;
    resetDrafts();
  } finally {
    loading.value = false;
  }
}

/** 草稿按考生主键建立；已有成绩只回显在「当前成绩」列，录入框保持为空以免误覆盖。 */
function resetDrafts(): void {
  Object.keys(drafts).forEach((key) => delete drafts[Number(key)]);
  rows.value.forEach((row) => {
    drafts[row.examCandidateId] = { finalScore: undefined, remark: '' };
  });
}

/** 查询回到第一页，避免筛选后停留在空页。 */
function query(): void {
  pageNum.value = 1;
  void load();
}

/** 仅提交本次填写了分数的考生，未填写的行不参与覆盖。 */
async function submit(): Promise<void> {
  const scores = rows.value
    .filter((row) => drafts[row.examCandidateId]?.finalScore !== undefined && drafts[row.examCandidateId]?.finalScore !== null)
    .map((row) => ({
      examCandidateId: row.examCandidateId,
      finalScore: drafts[row.examCandidateId]!.finalScore as number,
      remark: drafts[row.examCandidateId]!.remark.trim() || undefined
    }));
  if (!scores.length) {
    ElMessage.warning('请至少为一名考生填写成绩。');
    return;
  }
  const invalid = scores.find((item) => item.finalScore < 0 || item.finalScore > maxScore.value);
  if (invalid) {
    ElMessage.warning(`成绩必须落在 0 到 ${maxScore.value} 之间。`);
    return;
  }
  saving.value = true;
  try {
    const result = await recordOfflineScores(props.examId, { scores });
    ElMessage.success(`已录入 ${result.recordedCount} 名考生的线下成绩，学员即可查看结果。`);
    emit('saved');
    await load();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '线下成绩录入失败，请稍后重试。');
  } finally {
    saving.value = false;
  }
}

watch(visible, (opened) => {
  if (opened) {
    pageNum.value = 1;
    keyword.value = '';
    void load();
  }
});
</script>

<template>
  <ElDialog v-model="visible" title="录入线下成绩" width="880px" :close-on-click-modal="false">
    <ElAlert
      title="线下考试成绩由管理端录入，提交后学员可在「我的考试」查看成绩与合格状态；重复录入同一考生会覆盖上次成绩。"
      type="info"
      :closable="false"
      show-icon
      class="offline-score-tip"
    />
    <ElAlert type="warning" :closable="false" show-icon class="offline-score-rule-tip">
      <template #title>本场成绩范围：0 ～ {{ maxScore }} 分</template>
      <template #default>
        及格分：{{ passScore ?? '未设置' }} 分。线下场次开始时间：{{ openStartTime || '未设置' }}；录入后将回显到学员开始时间。
      </template>
    </ElAlert>
    <ElForm inline class="offline-score-filters">
      <ElFormItem label="学员"><ElInput v-model="keyword" clearable placeholder="姓名或工号" @keyup.enter="query" /></ElFormItem>
      <ElFormItem><ElButton type="primary" @click="query">查询</ElButton></ElFormItem>
    </ElForm>
    <ElTable v-loading="loading" :data="rows" border stripe max-height="380" empty-text="该考试暂无已绑定考生">
      <ElTableColumn label="序号" width="64" align="center">
        <template #default="{ $index }">{{ (pageNum - 1) * pageSize + $index + 1 }}</template>
      </ElTableColumn>
      <ElTableColumn label="学员" min-width="150">
        <template #default="{ row }">{{ row.learnerName || '—' }}{{ row.learnerCode ? `（${row.learnerCode}）` : '' }}</template>
      </ElTableColumn>
      <ElTableColumn prop="deptName" label="部门" min-width="150" show-overflow-tooltip />
      <ElTableColumn label="当前成绩" width="100">
        <template #default="{ row }">{{ row.finalScore ?? '未录入' }}</template>
      </ElTableColumn>
      <ElTableColumn label="录入成绩" width="170">
        <template #default="{ row }">
          <ElInputNumber
            v-model="drafts[row.examCandidateId].finalScore"
            :min="0"
            :max="maxScore"
            :step="0.5"
            controls-position="right"
            :placeholder="`0 ~ ${maxScore}`"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn label="备注" min-width="180">
        <template #default="{ row }">
          <ElInput v-model="drafts[row.examCandidateId].remark" placeholder="如：纸质试卷补录" />
        </template>
      </ElTableColumn>
    </ElTable>
    <div class="offline-score-pagination">
      <ElPagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="LIST_PAGE_SIZES"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @current-change="load"
        @size-change="query"
      />
    </div>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="submit">提交成绩</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="less">
.offline-score-tip {
  margin-bottom: 16px;
}

.offline-score-filters {
  margin-bottom: 8px;
}

.offline-score-rule-tip {
  margin-bottom: 16px;
}

.offline-score-pagination {
  display: flex;
  margin-top: 12px;
  justify-content: flex-end;
}

</style>
