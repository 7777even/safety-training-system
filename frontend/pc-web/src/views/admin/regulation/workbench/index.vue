<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { exportRegulationInstitutions, getRegulationWorkbench, saveRegulationCsv, type RegulationWorkbench } from '@/api/regulation';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const router = useRouter();
const userStore = useUserStore();
const summary = ref<RegulationWorkbench>();
const loading = ref(false);
/** 快捷入口同时受接口授权标志与当前用户权限约束。 */
const shortcuts = computed(() => summary.value?.shortcuts.filter((item) => item.granted && hasPermission(userStore.userInfo, item.permission)) ?? []);
const canExport = hasPermission(userStore.userInfo, 'regulation:learning:export');

/** 加载监管工作台的正式汇总与机构摘要。 */
async function load(): Promise<void> {
  loading.value = true;
  try { summary.value = await getRegulationWorkbench(); }
  finally { loading.value = false; }
}
async function exportInstitutions(): Promise<void> {
  saveRegulationCsv(await exportRegulationInstitutions(), '监管机构统计.csv');
}
/** 机构行整行可点，与「计划及参训人」按钮进入同一只读下钻页。 */
function openInstitution(organizationId: number): void {
  void router.push(`/admin/regulation/institutions/${organizationId}/plans`);
}
onMounted(load);
</script>

<template>
  <section v-loading="loading" class="admin-detail-page">
    <ElCard class="admin-detail-page__card" shadow="never">
      <section v-if="summary" class="ws-hero">
        <div class="ws-hero__main">
          <p class="ws-hero__eyebrow">REGULATION WORKBENCH</p>
          <div class="ws-hero__title-row">
            <h2 class="ws-hero__title">园区监管概览</h2>
            <ElTag v-if="summary.unreadWarningCount > 0" type="warning" effect="plain">{{ summary.unreadWarningCount }} 条预警待查看</ElTag>
            <ElTag v-else type="success" effect="plain">预警已查看</ElTag>
          </div>
          <div class="ws-hero__meta">
            <span class="ws-hero__chip">统计范围 园区全部企业</span>
            <span class="ws-hero__chip">数据来源 监管工作台汇总</span>
            <span>汇总园区内企业与培训机构的计划、参训与学时预警，明细可只读下钻。</span>
          </div>
        </div>
        <div class="ws-hero__aside">
          <div class="ws-hero__state">
            <strong class="workbench-warning">{{ summary.unreadWarningCount }}</strong>
            <small>待查看预警</small>
          </div>
        </div>
      </section>

      <section v-if="summary" class="ws-metrics workbench-metrics">
        <div class="ws-metric">
          <span class="ws-metric__label">企业</span>
          <span class="ws-metric__value">{{ summary.enterpriseCount }}<em>家</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">培训机构</span>
          <span class="ws-metric__value">{{ summary.trainingInstitutionCount }}<em>家</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">培训计划</span>
          <span class="ws-metric__value">{{ summary.planTotalCount }}<em>个</em></span>
        </div>
        <div class="ws-metric ws-metric--accent">
          <span class="ws-metric__label">进行中</span>
          <span class="ws-metric__value">{{ summary.runningPlanCount }}<em>个计划</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">已结束</span>
          <span class="ws-metric__value">{{ summary.endedPlanCount }}<em>个计划</em></span>
        </div>
        <div class="ws-metric" :class="{ 'ws-metric--pending': summary.unreadWarningCount > 0 }">
          <span class="ws-metric__label">待查看预警</span>
          <span class="ws-metric__value">{{ summary.unreadWarningCount }}<em>条</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">参训人数</span>
          <span class="ws-metric__value">{{ summary.participantCount }}<em>人</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">已完成参训</span>
          <span class="ws-metric__value">{{ summary.completedParticipantCount }}<em>人</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">特种作业计划</span>
          <span class="ws-metric__value">{{ summary.specialOperation.planCount }}<em>个</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">特种作业参训</span>
          <span class="ws-metric__value">{{ summary.specialOperation.participantCount }}<em>人</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">特种作业已完成</span>
          <span class="ws-metric__value">{{ summary.specialOperation.completedParticipantCount }}<em>人</em></span>
        </div>
      </section>

      <section v-if="summary" class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>培训类型分布</h2>
            <p>按培训类型统计园区内的计划数量。</p>
          </div>
          <span class="ws-section__extra">共 {{ summary.trainingTypeDistribution.length }} 类</span>
        </header>
        <ElTable :data="summary.trainingTypeDistribution" empty-text="暂无培训类型分布数据">
          <ElTableColumn prop="typeName" label="培训类型" min-width="180" />
          <ElTableColumn prop="planCount" label="计划数量" width="140" />
        </ElTable>
      </section>

      <section v-if="summary && shortcuts.length" class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>已授权快捷入口</h2>
            <p>仅显示当前账号已授权且路由已注册的监管入口。</p>
          </div>
          <span class="ws-section__extra">共 {{ shortcuts.length }} 个</span>
        </header>
        <ElSpace wrap>
          <ElButton v-for="item in shortcuts" :key="item.code" @click="router.push(item.path)">{{ item.title }}</ElButton>
        </ElSpace>
      </section>

      <section class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>机构统计</h2>
            <p>按机构汇总培训计划与参训人数，整行可点击进入只读下钻。</p>
          </div>
          <div class="section-actions">
            <span class="section-actions__count">共 {{ summary?.institutions?.length ?? 0 }} 家</span>
            <ElButton v-if="canExport" @click="exportInstitutions">导出 CSV</ElButton>
          </div>
        </header>
        <ElTable
          :data="summary?.institutions ?? []"
          empty-text="暂无机构统计数据"
          row-class-name="is-clickable"
          @row-click="(row: RegulationWorkbench['institutions'][number]) => openInstitution(row.organizationId)"
        >
          <ElTableColumn prop="orgCode" label="机构编码" min-width="150" />
          <ElTableColumn prop="orgName" label="机构名称" min-width="180" />
          <ElTableColumn label="分类" width="120">
            <template #default="{ row }">{{ row.orgType === 'TRAINING_INSTITUTION' ? '培训机构' : '企业' }}</template>
          </ElTableColumn>
          <ElTableColumn prop="planCount" label="培训计划" width="120" />
          <ElTableColumn prop="participantCount" label="参训人数" width="120" />
          <ElTableColumn label="只读下钻" width="170">
            <template #default="{ row }">
              <ElButton link type="primary" @click.stop="openInstitution(row.organizationId)">计划及参训人</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </section>
    </ElCard>
  </section>
</template>

<style scoped lang="less">
.workbench-warning {
  color: var(--color-primary);
  font-size: 26px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 32px;
}

/* 概览 hero 与指标区之间补一段留白，指标区之后由 .ws-section 自带的相邻留白接管。 */
.workbench-metrics {
  margin: var(--space-4) 0;
}

/* 区块标题右侧可同时放计数与操作按钮，按钮不再挤占正文。 */
.section-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
}

.section-actions__count {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 18px;
}

/* 可下钻的机构行给出指针光标，提示整行可进入计划及参训人。 */
:deep(.el-table__row.is-clickable) {
  cursor: pointer;
}
</style>
