<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  exportRegulationLearning, getRegulationLearners, getRegulationLearningStats, getRegulationOfferings,
  saveRegulationCsv, type RegulationLearner, type RegulationLearningStats, type RegulationOffering
} from '@/api/regulation';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const route = useRoute();
const userStore = useUserStore();
const organizationId = ref<number>();
const park = ref<RegulationLearningStats>();
const current = ref<RegulationLearningStats>();
const learners = ref<RegulationLearner[]>([]);
const offerings = ref<RegulationOffering[]>([]);
const loading = ref(false);
const canExport = hasPermission(userStore.userInfo, 'regulation:learning:export');
/** 参与率统一换算成百分数并夹在 0～100，避免后端小数口径直接撑破进度条。 */
const participationRate = computed(() => Math.min(100, Math.max(0, (current.value?.participationRate ?? 0) * 100)));

/** 机构筛选同时作用于六项指标、人员、开课明细和导出。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const selected = organizationId.value;
    const [stats, learnerRows, offeringRows] = await Promise.all([
      getRegulationLearningStats(selected), getRegulationLearners(selected), getRegulationOfferings(selected)
    ]);
    current.value = stats;
    learners.value = learnerRows;
    offerings.value = offeringRows;
  } finally { loading.value = false; }
}
async function download(): Promise<void> {
  saveRegulationCsv(await exportRegulationLearning(organizationId.value), '学习情况统计.csv');
}
/** 机构汇总行整行可点，点击后把统计口径切到该机构；园区汇总行无机构 ID 时忽略。 */
function selectInstitution(nextOrganizationId: number | undefined): void {
  organizationId.value = nextOrganizationId;
  void load();
}
onMounted(async () => {
  park.value = await getRegulationLearningStats();
  const queryId = Number(route.query.organizationId);
  if (Number.isSafeInteger(queryId) && park.value.institutions.some((item) => item.organizationId === queryId)) organizationId.value = queryId;
  await load();
});
</script>

<template>
  <section v-loading="loading" class="admin-detail-page">
    <ElCard class="admin-detail-page__card" shadow="never">
      <section class="ws-hero">
        <div class="ws-hero__main">
          <p class="ws-hero__eyebrow">LEARNING STATISTICS</p>
          <div class="ws-hero__title-row">
            <h2 class="ws-hero__title">学习情况统计</h2>
            <ElTag effect="plain">{{ organizationId ? '单机构口径' : '园区汇总口径' }}</ElTag>
          </div>
          <div class="ws-hero__meta">
            <span class="ws-hero__chip">六项指标同口径</span>
            <span class="ws-hero__chip">含机构、人员与开课明细</span>
            <span>选择机构后指标、人员与开课明细同步切换；清空则回到园区汇总。</span>
          </div>
        </div>
      </section>

      <section class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>统计范围</h2>
            <p>清空机构则展示园区汇总口径。</p>
          </div>
          <ElButton v-if="canExport" @click="download">按当前筛选导出 CSV</ElButton>
        </header>
        <ElSelect v-model="organizationId" clearable placeholder="全部机构（园区汇总）" style="width: 320px" @change="load">
          <ElOption v-for="item in park?.institutions ?? []" :key="item.organizationId" :label="item.orgName" :value="Number(item.organizationId)" />
        </ElSelect>
      </section>

      <section v-if="current" class="ws-metrics">
        <div class="ws-metric">
          <span class="ws-metric__label">员工账号</span>
          <span class="ws-metric__value">{{ current.employeeAccountCount }}<em>个</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">上课次数</span>
          <span class="ws-metric__value">{{ current.classSessionCount }}<em>次</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">上课人数</span>
          <span class="ws-metric__value">{{ current.classLearnerCount }}<em>人</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">参与课程</span>
          <span class="ws-metric__value">{{ current.participatingOfferingCount }}<em>门</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">人均上课次数</span>
          <span class="ws-metric__value">{{ current.avgSessionsPerLearner.toFixed(2) }}<em>次 / 人</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">参与率</span>
          <div class="ws-bar">
            <div class="ws-bar__track">
              <div class="ws-bar__fill" :style="{ width: `${participationRate}%` }" />
            </div>
            <div class="ws-bar__legend">
              <span>{{ participationRate.toFixed(1) }}%</span>
              <span>上课人数 / 员工账号</span>
            </div>
          </div>
        </div>
      </section>

      <section v-if="!organizationId" class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>机构同口径明细</h2>
            <p>整行可点击，选择后上方指标与下方明细同步切换到该机构。</p>
          </div>
          <span class="ws-section__extra">共 {{ park?.institutions?.length ?? 0 }} 家</span>
        </header>
        <ElTable
          :data="park?.institutions ?? []"
          empty-text="暂无机构统计数据"
          row-class-name="is-clickable"
          @row-click="(row: RegulationLearningStats) => selectInstitution(row.organizationId)"
        >
          <ElTableColumn prop="orgName" label="机构" min-width="180" />
          <ElTableColumn prop="employeeAccountCount" label="员工账号" width="110" />
          <ElTableColumn prop="classSessionCount" label="上课次数" width="110" />
          <ElTableColumn prop="classLearnerCount" label="上课人数" width="110" />
          <ElTableColumn prop="participatingOfferingCount" label="参与课程" width="110" />
          <ElTableColumn label="人均上课" width="110">
            <template #default="{ row }">{{ row.avgSessionsPerLearner.toFixed(2) }}</template>
          </ElTableColumn>
          <ElTableColumn label="参与率" width="110">
            <template #default="{ row }">{{ (row.participationRate * 100).toFixed(1) }}%</template>
          </ElTableColumn>
        </ElTable>
      </section>

      <section class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>上课人员明细</h2>
            <p>参加开课数按人去重统计。</p>
          </div>
          <span class="ws-section__extra">共 {{ learners.length }} 人</span>
        </header>
        <ElTable :data="learners" empty-text="暂无上课人员记录">
          <ElTableColumn prop="userName" label="姓名" min-width="140" />
          <ElTableColumn prop="userCode" label="账号" min-width="140" />
          <!-- 机构列展示机构名称，服务端未回填时回退到机构主键，不出现整列空白。 -->
          <ElTableColumn label="所属机构" min-width="180">
            <template #default="{ row }">{{ row.orgName || row.organizationId }}</template>
          </ElTableColumn>
          <ElTableColumn prop="offeringCount" label="参加开课数" min-width="130" />
        </ElTable>
      </section>

      <section class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>开课明细</h2>
            <p>去重学员数按开课统计。</p>
          </div>
          <span class="ws-section__extra">共 {{ offerings.length }} 门</span>
        </header>
        <ElTable :data="offerings" empty-text="暂无开课记录">
          <ElTableColumn prop="offeringName" label="开课名称" min-width="200" />
          <!-- 机构列展示机构名称，服务端未回填时回退到机构主键，不出现整列空白。 -->
          <ElTableColumn label="所属机构" min-width="180">
            <template #default="{ row }">{{ row.orgName || row.organizationId }}</template>
          </ElTableColumn>
          <ElTableColumn prop="learnerCount" label="去重学员数" min-width="130" />
        </ElTable>
      </section>
    </ElCard>
  </section>
</template>

<style scoped lang="less">
/* 概览 hero 之后的白卡段落之间统一留白，指标区不额外加边距。 */
.ws-hero + .ws-section {
  margin-top: var(--space-4);
}

.ws-section + .ws-metrics {
  margin: var(--space-4) 0;
}

/* 可点击的机构汇总行给出指针光标，提示整行可切换统计口径。 */
:deep(.el-table__row.is-clickable) {
  cursor: pointer;
}
</style>
