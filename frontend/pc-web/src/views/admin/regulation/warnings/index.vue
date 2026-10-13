<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  exportRegulationWarnings, getRegulationWarning, getRegulationWarningCount, getRegulationWarnings,
  getRegulationWorkbench, saveRegulationCsv, type RegulationInstitution, type RegulationWarning
} from '@/api/regulation';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const router = useRouter();
const userStore = useUserStore();
const filters = reactive<{ warningStatus?: RegulationWarning['warningStatus']; regulatedOrgId?: number; trainingTypeId?: number }>({});
const statusOptions = [
  { label: '待查看', value: 'UNREAD' }, { label: '已查看', value: 'READ' }, { label: '已结束', value: 'ENDED' }
] as const;
/** 状态在列表中用标签呈现，色板与「待办 / 已知悉 / 已闭环」三态对应。 */
const WARNING_STATUS_TAGS: Record<string, 'warning' | 'info' | 'success'> = { UNREAD: 'warning', READ: 'info', ENDED: 'success' };
const institutions = ref<RegulationInstitution[]>([]);
const trainingTypes = ref<{ trainingTypeId: number; typeName: string }[]>([]);
const rows = ref<RegulationWarning[]>([]);
const detail = ref<RegulationWarning>();
const detailVisible = ref(false);
const unreadCount = ref(0);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const loading = ref(false);
const canExport = hasPermission(userStore.userInfo, 'regulation:warning:export');

function statusLabel(status: RegulationWarning['warningStatus']): string {
  return statusOptions.find((item) => item.value === status)?.label ?? status;
}

function statusTagType(status: RegulationWarning['warningStatus']): 'warning' | 'info' | 'success' {
  return WARNING_STATUS_TAGS[status] ?? 'info';
}

/** 同一筛选对象用于列表和 CSV，默认状态由服务端限定为当前预警。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const [page, unread] = await Promise.all([
      getRegulationWarnings({ pageNum: pageNum.value, pageSize: pageSize.value,
        'query.warningStatus': filters.warningStatus, 'query.regulatedOrgId': filters.regulatedOrgId,
        'query.trainingTypeId': filters.trainingTypeId }),
      getRegulationWarningCount()
    ]);
    rows.value = page.list;
    total.value = page.total;
    unreadCount.value = unread;
  } finally { loading.value = false; }
}

/** 详情 GET 自动留存首次查看时间，刷新列表与角标以展示新状态。 */
async function openDetail(warningId: number): Promise<void> {
  detail.value = await getRegulationWarning(warningId);
  detailVisible.value = true;
  await load();
}
async function download(): Promise<void> {
  saveRegulationCsv(await exportRegulationWarnings({
    'query.warningStatus': filters.warningStatus, 'query.regulatedOrgId': filters.regulatedOrgId,
    'query.trainingTypeId': filters.trainingTypeId
  }), '学时预警.csv');
}
function openSource(warning: RegulationWarning): void {
  detailVisible.value = false;
  void router.push({ path: `/admin/regulation/institutions/${warning.regulatedOrgId}/plans`, query: { planId: warning.trainingPlanId } });
}
onMounted(async () => {
  if (hasPermission(userStore.userInfo, 'regulation:overview:view')) {
    const summary = await getRegulationWorkbench();
    institutions.value = summary.institutions;
    trainingTypes.value = summary.trainingTypeDistribution;
  }
  await load();
});
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline>
        <ElFormItem label="预警状态">
          <ElSelect v-model="filters.warningStatus" clearable placeholder="当前预警" style="width: 160px">
            <ElOption v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="机构">
          <ElSelect v-model="filters.regulatedOrgId" clearable placeholder="全部机构" style="width: 220px">
            <ElOption v-for="item in institutions" :key="item.organizationId" :label="item.orgName" :value="item.organizationId" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="培训类型">
          <ElSelect v-model="filters.trainingTypeId" clearable placeholder="全部培训类型" style="width: 180px">
            <ElOption v-for="item in trainingTypes" :key="item.trainingTypeId" :label="item.typeName" :value="item.trainingTypeId" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton
            type="primary"
            @click="
              pageNum = 1;
              load();
            "
          >
            查询
          </ElButton>
          <ElButton
            @click="
              Object.assign(filters, { warningStatus: undefined, regulatedOrgId: undefined, trainingTypeId: undefined });
              pageNum = 1;
              load();
            "
          >
            重置
          </ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>学时不足预警</strong>
          <div class="admin-business-page__table-actions">
            <ElTag v-if="unreadCount > 0" type="warning" effect="plain">{{ unreadCount }} 条待查看</ElTag>
            <ElTag v-else type="success" effect="plain">已全部查看</ElTag>
            <ElButton v-if="canExport" @click="download">按当前筛选导出 CSV</ElButton>
          </div>
        </div>
      </template>
      <ElTable
        v-loading="loading"
        :data="rows"
        row-class-name="is-clickable"
        empty-text="当前筛选条件下没有预警记录"
        @row-click="(row: RegulationWarning) => openDetail(row.warningId)"
      >
        <ElTableColumn prop="regulatedOrgName" label="机构" min-width="150" />
        <ElTableColumn prop="planName" label="培训计划" min-width="170" />
        <ElTableColumn prop="trainingTypeName" label="培训类型" min-width="130" />
        <ElTableColumn label="培训性质" width="110">
          <template #default="{ row }">{{ row.trainingNature === 'INITIAL' ? '初培' : '复审' }}</template>
        </ElTableColumn>
        <ElTableColumn label="不足学时" width="110">
          <template #default="{ row }">
            <strong class="hours-gap">{{ row.hoursGap }}</strong>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="statusTagType(row.warningStatus)" effect="light">{{ statusLabel(row.warningStatus) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="detectedTime" label="首次发现" min-width="170" />
        <ElTableColumn label="操作" width="110">
          <template #default="{ row }">
            <ElButton link type="primary" @click.stop="openDetail(row.warningId)">查看证据</ElButton>
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
          @size-change="
            pageNum = 1;
            load();
          "
        />
      </div>
    </ElCard>

    <ElDrawer v-model="detailVisible" title="学时预警证据" size="480px" @closed="load">
      <ElDescriptions v-if="detail" :column="1" border>
        <ElDescriptionsItem label="机构">{{ detail.regulatedOrgName }}</ElDescriptionsItem>
        <ElDescriptionsItem label="培训计划">{{ detail.planName }}</ElDescriptionsItem>
        <ElDescriptionsItem label="培训类型">{{ detail.trainingTypeName }}</ElDescriptionsItem>
        <ElDescriptionsItem label="培训性质">{{ detail.trainingNature === 'INITIAL' ? '初培' : '复审' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="计划安排总学时">{{ detail.planTotalHours }}</ElDescriptionsItem>
        <ElDescriptionsItem label="标准要求学时">{{ detail.requiredHours }}</ElDescriptionsItem>
        <ElDescriptionsItem label="不足学时">
          <strong class="hours-gap">{{ detail.hoursGap }}</strong>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="首次发现">{{ detail.detectedTime }}</ElDescriptionsItem>
        <ElDescriptionsItem label="首次查看">{{ detail.firstViewedTime || '尚未查看' }}</ElDescriptionsItem>
        <ElDescriptionsItem label="修正后结束">{{ detail.closedTime || '尚未结束' }}</ElDescriptionsItem>
      </ElDescriptions>
      <ElButton v-if="detail" type="primary" class="source" @click="openSource(detail)">查看来源计划</ElButton>
    </ElDrawer>
  </section>
</template>

<style scoped lang="less">
/* 不足学时是预警的核心结论，统一用危险色与等宽数字，列表与证据抽屉口径一致。 */
.hours-gap {
  color: var(--color-danger);
  font-variant-numeric: tabular-nums;
}

.source {
  margin-top: var(--space-5);
}

/* 可查看证据的行给出指针光标，提示整行可打开证据抽屉。 */
:deep(.el-table__row.is-clickable) {
  cursor: pointer;
}
</style>
