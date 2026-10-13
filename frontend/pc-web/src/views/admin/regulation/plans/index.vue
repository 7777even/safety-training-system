<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  enterRegulationPlanView, leaveRegulationPlanView, getRegulationPlan, getRegulationPlans,
  getRegulationParticipants, getRegulationParticipantResults,
  type RegulationPlan, type RegulationParticipant, type RegulationParticipantResults
} from '@/api/regulation';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';

const route = useRoute();
const plans = ref<RegulationPlan[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const selectedPlan = ref<RegulationPlan>();
const participants = ref<RegulationParticipant[]>([]);
const result = ref<RegulationParticipantResults>();
const loading = ref(false);
/** 按已确认状态枚举展示中文，保留未知历史值供排查。 */
const planStatusLabels: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', RUNNING: '进行中', ENDED: '已结束', CANCELLED: '已取消', ARCHIVED: '已归档' };
const participantStatusLabels: Record<string, string> = { PENDING: '待开始', LEARNING: '学习中', COMPLETED: '已完成', FAILED: '未通过', EXPIRED: '已过期', WITHDRAWN: '已退出' };
const resultStatusLabels: Record<string, string> = { PASSED: '合格', FAILED: '不合格', INCOMPLETE: '待补全' };
/** 状态标签色板与既有业务页保持一致：草稿/归档中性、进行中告警、结束通过色。 */
const planStatusTags: Record<string, 'primary' | 'success' | 'info' | 'warning' | 'danger'> = {
  DRAFT: 'info', PUBLISHED: 'primary', RUNNING: 'warning', ENDED: 'success', CANCELLED: 'danger', ARCHIVED: 'info'
};
const participantStatusTags: Record<string, 'primary' | 'success' | 'info' | 'warning' | 'danger'> = {
  PENDING: 'info', LEARNING: 'warning', COMPLETED: 'success', FAILED: 'danger', EXPIRED: 'info', WITHDRAWN: 'info'
};
const resultStatusTags: Record<string, 'primary' | 'success' | 'info' | 'warning' | 'danger'> = {
  PASSED: 'success', FAILED: 'danger', INCOMPLETE: 'warning'
};

/** 只读请求在当前机构视角执行，离开页面立即清除请求头。 */
async function loadPlans(): Promise<void> {
  loading.value = true;
  try {
    const data = await getRegulationPlans(pageNum.value, pageSize.value);
    plans.value = data.list;
    total.value = data.total;
  } finally { loading.value = false; }
}

/** 查看计划及参训人，不调用任何业务写接口。 */
async function openPlan(trainingPlanId: number): Promise<void> {
  selectedPlan.value = await getRegulationPlan(trainingPlanId);
  participants.value = await getRegulationParticipants(trainingPlanId);
  result.value = undefined;
}

/** 参训结果只显示合同已确认的正式分与学时。 */
async function openResult(participantId: number): Promise<void> {
  if (!selectedPlan.value) return;
  result.value = await getRegulationParticipantResults(selectedPlan.value.trainingPlanId, participantId);
}

/** Element Plus 表格插槽未保留行泛型，在边界处收窄为参训人记录。 */
function asRegulationParticipant(row: unknown): RegulationParticipant {
  return row as RegulationParticipant;
}

/** 参训人展示姓名；服务端未回填时回退到用户主键，避免整列空白。 */
function participantLabel(row: unknown): string {
  const participant = asRegulationParticipant(row);
  return participant.userName || `用户 #${participant.userId}`;
}

onMounted(async () => {
  const organizationId = Number(route.params.organizationId);
  if (!Number.isSafeInteger(organizationId) || organizationId <= 0) return;
  enterRegulationPlanView(organizationId);
  await loadPlans();
  const planId = Number(route.query.planId);
  if (Number.isSafeInteger(planId) && planId > 0) await openPlan(planId);
});
onBeforeUnmount(leaveRegulationPlanView);
</script>

<template>
  <section class="admin-detail-page">
    <ElCard class="admin-detail-page__card" shadow="never">
      <section class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>机构培训计划</h2>
            <p>监管侧只读下钻，整行可点击查看计划及参训人，不提供任何写操作。</p>
          </div>
          <span class="ws-section__extra">共 {{ total }} 个计划</span>
        </header>
        <ElTable
          v-loading="loading"
          :data="plans"
          row-class-name="is-clickable"
          empty-text="该机构暂无培训计划"
          @row-click="(row: RegulationPlan) => openPlan(row.trainingPlanId)"
        >
          <ElTableColumn prop="planCode" label="计划编码" min-width="150" />
          <ElTableColumn prop="planName" label="计划名称" min-width="200" />
          <ElTableColumn label="状态" width="110">
            <template #default="{ row }">
              <ElTag :type="planStatusTags[row.publishStatus]" effect="light">{{ planStatusLabels[row.publishStatus] ?? row.publishStatus }}</ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="applicableRequiredHours" label="适用最低总学时" width="150" />
          <ElTableColumn label="操作" width="110">
            <template #default="{ row }">
              <ElButton link type="primary" @click.stop="openPlan(row.trainingPlanId)">查看</ElButton>
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
            @current-change="loadPlans"
            @size-change="
              pageNum = 1;
              loadPlans();
            "
          />
        </div>
      </section>

      <section v-if="selectedPlan" class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>参训人</h2>
            <p>{{ selectedPlan.planName }} 的参训人明细，整行可点击查看参训结果。</p>
          </div>
          <span class="ws-section__extra">共 {{ participants.length }} 人</span>
        </header>
        <ElTable
          :data="participants"
          row-class-name="is-clickable"
          empty-text="该计划暂无参训人"
          @row-click="(row: RegulationParticipant) => openResult(row.trainingParticipantId)"
        >
          <!-- 列宽统一用 min-width：固定 width 会让表格右侧留出大片空白，min-width 由表格均分剩余宽度。 -->
          <ElTableColumn label="参训人" min-width="180">
            <template #default="{ row }">{{ participantLabel(row) }}</template>
          </ElTableColumn>
          <ElTableColumn label="参训状态" min-width="130">
            <template #default="{ row }">
              <ElTag :type="participantStatusTags[row.participantStatus]" effect="light">{{ participantStatusLabels[row.participantStatus] ?? row.participantStatus }}</ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="onlineHours" label="线上学时" min-width="120" />
          <ElTableColumn prop="offlineHours" label="线下学时" min-width="120" />
          <ElTableColumn label="操作" width="120">
            <template #default="{ row }">
              <ElButton link type="primary" @click.stop="openResult(row.trainingParticipantId)">查看结果</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </section>

      <section v-if="result" class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>参训结果</h2>
            <p>只显示合同已确认的正式分与学时。</p>
          </div>
        </header>
        <div class="ws-kv">
          <div class="ws-kv__item">
            <span class="ws-kv__label">结果状态</span>
            <span class="ws-kv__value">
              <ElTag :type="resultStatusTags[result.resultStatus]" effect="light">{{ resultStatusLabels[result.resultStatus] ?? result.resultStatus }}</ElTag>
            </span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">课程成绩</span>
            <span class="ws-kv__value">{{ result.courseScore ?? '—' }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">考试成绩</span>
            <span class="ws-kv__value">{{ result.examScore ?? '—' }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">线下成绩</span>
            <span class="ws-kv__value">{{ result.offlineScore ?? '—' }}</span>
          </div>
          <div class="ws-kv__item">
            <span class="ws-kv__label">综合分</span>
            <span class="ws-kv__value">{{ result.compositeScore ?? '—' }}</span>
          </div>
        </div>
      </section>
    </ElCard>
  </section>
</template>

<style scoped lang="less">
/* 可下钻的表格行给出指针光标，提示整行可展开下一级只读明细。 */
:deep(.el-table__row.is-clickable) {
  cursor: pointer;
}
</style>
