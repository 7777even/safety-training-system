<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft, Download, RefreshLeft, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import PersonPickerDialog from '@/components/organization/PersonPickerDialog.vue';
import type { PersonPickerItem } from '@/api/person-picker';
import { getTrainingPlanPage, type TrainingPlan } from '@/api/training-plan';
import { getTrainingTypePage, type TrainingType } from '@/api/training-type';
import {
  createArchiveFromPlan,
  downloadArchive,
  downloadArchivePerson,
  getArchive,
  getArchivePerson,
  getCertificates,
  getEnterpriseArchives,
  type ArchiveCertificateItem,
  type ArchivePerson,
  type ArchiveScore,
  type ArchiveView
} from '@/api/m4-outcome';
import { TRAINING_RESULT_STATUS_OPTIONS, getEnumLabel } from '@/constants/enum-options';
import { saveBlobAsFile } from '@/libs/controlled-file';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import ArchiveCertificateTable from './components/ArchiveCertificateTable.vue';
import ArchiveFactsSections from './components/ArchiveFactsSections.vue';
import ArchiveHistoryPanel from './components/ArchiveHistoryPanel.vue';
import PlanArchivePanel from './components/PlanArchivePanel.vue';

/** 档案业务选择入口按一期、企业、个人三种视角组织，都不允许手输全局主键。 */
type ArchivePerspective = 'PLAN' | 'ENTERPRISE' | 'PERSON';
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
/** 计划上下文路由直达一期一档；缺少计划主键时展示业务选择入口。 */
const routePlanId = computed(() => Number(route.params.trainingPlanId) || undefined);
const perspective = ref<ArchivePerspective>('PLAN');

const planOptions = ref<TrainingPlan[]>([]);
const planOptionsLoading = ref(false);
const selectedPlanId = ref<number>();
const archive = ref<ArchiveView>();
const archiveLoading = ref(false);
const archiving = ref(false);
const downloading = ref(false);
/** 更新档案的请求字段沿用契约必填项，档案编码默认取计划编号，不要求用户查主键。 */
const archiveCode = ref('');
const versionReason = ref('');
const DEFAULT_VERSION_REASON = '培训计划结束后更新档案';

const enterpriseRows = ref<ArchiveView[]>([]);
const enterpriseLoading = ref(false);
const enterpriseQuery = ref({
  planName: '',
  planCode: '',
  trainingTypeId: undefined as number | undefined,
  /** 计划开始时间区间，值格式与契约的 LocalDateTime 参数一致。 */
  startRange: [] as string[],
  /** 计划结束时间区间。 */
  endRange: [] as string[]
});
const trainingTypeOptions = ref<TrainingType[]>([]);
/** 逐行下载需要独立状态，避免一行的加载态禁用整张表。 */
const enterpriseDownloadingId = ref<number>();

const personPickerVisible = ref(false);
const person = ref<ArchivePerson>();
const personLoading = ref(false);
const personDownloading = ref(false);
/** 个人证书清单走证书查询端点，缺 certificate:cert:list 时整块隐藏。 */
const personCertificates = ref<ArchiveCertificateItem[]>([]);
const personCertificatesLoading = ref(false);
const canListCertificates = computed(() => hasPermission(userStore.userInfo, 'certificate:cert:list'));

/** 归档事实完整且文件已生成时才允许下载，页面与服务端校验一致。 */
const downloadable = computed(
  () => Boolean(archive.value?.fileInfoId) && (archive.value?.missingItems.length ?? 1) === 0
);
/** 结果缺失时禁止更新档案，避免把不完整事实固化成正式档案。 */
const archivable = computed(
  () => Boolean(selectedPlanId.value) && Boolean(archive.value) && (archive.value?.missingItems.length ?? 1) === 0
);
const personDownloadable = computed(() => (person.value?.missingItems.length ?? 1) === 0);

/** 加载可归档的已结束计划，作为一期一档的候选来源。 */
async function loadPlanOptions(): Promise<void> {
  planOptionsLoading.value = true;
  try {
    const page = await getTrainingPlanPage({ pageNum: 1, pageSize: 100, 'query.publishStatus': 'ENDED' });
    planOptions.value = page.list;
  } finally {
    planOptionsLoading.value = false;
  }
}
/** 企业档案的培训类型筛选必须从既有类型主数据里选，不手输培训类型主键。 */
async function loadTrainingTypeOptions(): Promise<void> {
  const page = await getTrainingTypePage({ pageNum: 1, pageSize: 200 });
  trainingTypeOptions.value = page.list;
}
/** 查询一期档案事实，同时把档案编码与快照原因预填为可修改的默认值。 */
async function queryArchive(planId: number): Promise<void> {
  archiveLoading.value = true;
  try {
    archive.value = await getArchive(planId);
    archiveCode.value = archive.value.planCode ?? '';
    versionReason.value = DEFAULT_VERSION_REASON;
  } finally {
    archiveLoading.value = false;
  }
}
/** 选择已结束计划后查询对应一期档案；清空选择时同时清掉旧事实。 */
async function selectPlan(value: unknown): Promise<void> {
  const planId = typeof value === 'number' ? value : undefined;
  selectedPlanId.value = planId;
  archive.value = undefined;
  if (planId) await queryArchive(planId);
}
/** 从已结束计划生成或更新一期档案；服务端校验不通过时保留后端消息。 */
async function updateArchive(): Promise<void> {
  if (!selectedPlanId.value || !archivable.value) return;
  archiving.value = true;
  try {
    await createArchiveFromPlan({
      trainingPlanId: selectedPlanId.value,
      archiveCode: archiveCode.value.trim(),
      versionReason: versionReason.value.trim()
    });
    await queryArchive(selectedPlanId.value);
    ElMessage.success('一期档案已按当前培训事实更新。');
  } finally {
    archiving.value = false;
  }
}
/** 下载真实生成的档案文件；一期档案与个人档案均为 PDF 正本。 */
async function downloadPlan(): Promise<void> {
  if (!downloadable.value || !selectedPlanId.value) return;
  downloading.value = true;
  try {
    const blob = await downloadArchive(selectedPlanId.value);
    saveBlobAsFile(blob, `${archive.value?.planCode ?? 'training'}-培训档案.pdf`);
    ElMessage.success('档案文件已开始下载。');
  } finally {
    downloading.value = false;
  }
}
/** 企业档案行内下载：事实不完整或文件未生成时不提供入口。 */
function canDownloadRow(row: ArchiveView): boolean {
  return Boolean(row.fileInfoId) && row.missingItems.length === 0;
}
async function downloadEnterpriseRow(row: ArchiveView): Promise<void> {
  if (!canDownloadRow(row)) return;
  enterpriseDownloadingId.value = row.trainingPlanId;
  try {
    const blob = await downloadArchive(row.trainingPlanId);
    saveBlobAsFile(blob, `${row.planCode}-培训档案.pdf`);
    ElMessage.success('档案文件已开始下载。');
  } finally {
    enterpriseDownloadingId.value = undefined;
  }
}
/** 企业档案沿用一期口径，返回当前机构全部已结束培训。 */
async function loadEnterprise(): Promise<void> {
  enterpriseLoading.value = true;
  try {
    const query = enterpriseQuery.value;
    const params: Record<string, unknown> = {};
    if (query.planName.trim()) params['planName'] = query.planName.trim();
    if (query.planCode.trim()) params['planCode'] = query.planCode.trim();
    if (query.trainingTypeId) params['trainingTypeId'] = query.trainingTypeId;
    if (query.startRange.length === 2) {
      params['startTimeFrom'] = query.startRange[0];
      params['startTimeTo'] = query.startRange[1];
    }
    if (query.endRange.length === 2) {
      params['endTimeFrom'] = query.endRange[0];
      params['endTimeTo'] = query.endRange[1];
    }
    enterpriseRows.value = await getEnterpriseArchives(params);
  } finally {
    enterpriseLoading.value = false;
  }
}
function resetEnterpriseQuery(): void {
  enterpriseQuery.value = { planName: '', planCode: '', trainingTypeId: undefined, startRange: [], endRange: [] };
  void loadEnterprise();
}
/** 从企业档案进入具体一期档案，仍然只使用业务上下文里的计划标识。 */
async function openEnterpriseRow(row: ArchiveView): Promise<void> {
  perspective.value = 'PLAN';
  await loadPlanOptions();
  await selectPlan(row.trainingPlanId);
}
/** 人员选择器只回传业务人员标识，页面据此读取个人档案。 */
async function confirmPerson(items: PersonPickerItem[]): Promise<void> {
  const selected = items[0];
  if (!selected) return;
  personLoading.value = true;
  try {
    person.value = await getArchivePerson(selected.userId);
  } finally {
    personLoading.value = false;
  }
  await loadPersonCertificates(selected.userId);
}
/** 更正只刷新在线历史展示，不更新归档文件和原培训事实。 */
async function reloadPersonHistory(): Promise<void> {
  if (person.value) person.value = await getArchivePerson(person.value.userId);
}
/** 个人证书按持证人查询，权限不足时不请求也不展示空清单。 */
async function loadPersonCertificates(userId: number): Promise<void> {
  personCertificates.value = [];
  if (!canListCertificates.value) return;
  personCertificatesLoading.value = true;
  try {
    const page = await getCertificates({ pageNum: 1, pageSize: 100, 'query.holderUserId': userId });
    personCertificates.value = page.list;
  } finally {
    personCertificatesLoading.value = false;
  }
}
/** 个人档案文件由服务端按可见的已结束培训事实组装，前端只负责触发下载。 */
async function downloadPerson(): Promise<void> {
  if (!person.value || !personDownloadable.value) return;
  personDownloading.value = true;
  try {
    const blob = await downloadArchivePerson(person.value.userId);
    saveBlobAsFile(blob, `${person.value.userCode || person.value.userId}-个人培训档案.pdf`);
    ElMessage.success('个人档案文件已开始下载。');
  } finally {
    personDownloading.value = false;
  }
}
/**
 * 档案事实（统计、内容、成绩、奖惩、影像与附件）改在抽屉里完整阅览。
 * <p>原来挂在表格展开行上，而事实有 1800px 上下、表格体只有 125px 且 overflow:hidden，
 * 展开后只露出第一个区块标题就被裁掉，等于看不到档案内容。
 */
const factsVisible = ref(false);
const factsRow = ref<ArchiveView>();
function openFacts(row: ArchiveView): void {
  factsRow.value = row;
  factsVisible.value = true;
}
/** 抽屉标题带上计划，连续查看多条时不至于分不清正在读哪一期；编号与名称相同时不重复展示。 */
const factsTitle = computed(() => {
  const row = factsRow.value;
  if (!row) return '档案事实';
  const subject = row.planCode && row.planCode !== row.planName ? `${row.planName}（${row.planCode}）` : row.planName;
  return `${subject} · 档案事实`;
});
/** 缺失项后端只产出 RESULTS，页面按统一中文口径展示，未知编码退回原文不吞掉事实。 */
const MISSING_FACT_LABELS: Record<string, string> = { RESULTS: '结果待补全' };
function missingFactText(items: string[]): string {
  return items.map((item) => MISSING_FACT_LABELS[item] ?? item).join('、');
}
/** 个人档案行里的「结算结果」取本人在该期培训的结果状态，其他人不参与展示。 */
function personTrainingResult(row: ArchiveView): string {
  const userId = person.value?.userId;
  if (!userId) return '—';
  const mine = (row.scores ?? []).find((score) => score.userId === userId);
  return getEnumLabel(
    mine?.resultStatus as NonNullable<ArchiveScore['resultStatus']> | undefined,
    TRAINING_RESULT_STATUS_OPTIONS
  );
}
function backToPerspectives(): void {
  void router.push('/admin/result/archive');
}

onMounted(async () => {
  if (routePlanId.value) {
    await selectPlan(routePlanId.value);
    return;
  }
  await Promise.all([loadPlanOptions(), loadTrainingTypeOptions()]);
});
</script>
<template>
  <section v-loading="archiveLoading" :class="routePlanId ? 'admin-detail-page' : 'admin-business-page'">
    <header v-if="routePlanId" class="admin-detail-page__header">
      <div>
        <h1>培训档案</h1>
        <p>一期、企业与个人视角共用同一归档口径；结果待补全或文件未生成时不能下载。</p>
      </div>
      <div class="archive-actions">
        <ElButton :icon="ArrowLeft" @click="backToPerspectives">档案业务选择</ElButton>
        <ElButton :icon="ArrowLeft" @click="$router.back()">返回</ElButton>
      </div>
    </header>

    <ElTabs v-if="!routePlanId" v-model="perspective" class="admin-page-tabs admin-page-tabs--fill">
      <ElTabPane label="一期一档" name="PLAN">
        <ElCard class="admin-business-page__filters" shadow="never">
          <ElForm inline label-width="88px">
            <ElFormItem label="已结束培训">
              <ElSelect
                v-model="selectedPlanId"
                class="archive-plan-select"
                filterable
                clearable
                :loading="planOptionsLoading"
                placeholder="选择已结束的培训计划"
                @change="selectPlan"
              >
                <ElOption
                  v-for="item in planOptions"
                  :key="item.trainingPlanId"
                  :label="`${item.planName}(${item.planCode})`"
                  :value="item.trainingPlanId"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem>
              <ElButton :icon="RefreshLeft" @click="loadPlanOptions">刷新计划</ElButton>
            </ElFormItem>
          </ElForm>
        </ElCard>
        <PlanArchivePanel
          v-if="archive"
          v-model:archive-code="archiveCode"
          v-model:version-reason="versionReason"
          :archive="archive"
          :archiving="archiving"
          :downloading="downloading"
          :archivable="archivable"
          :downloadable="downloadable"
          @update-archive="updateArchive"
          @download="downloadPlan"
        />
        <ElCard v-else class="admin-business-page__table" shadow="never">
          <template #header>
            <div class="admin-business-page__table-heading">
              <strong>一期档案</strong>
            </div>
          </template>
          <div class="archive-empty">
            <ElEmpty description="请选择已结束培训后查看一期一档">
              <span class="ws-empty__hint">一期档案依附具体培训计划，也可从培训计划工作台直接进入。</span>
            </ElEmpty>
          </div>
        </ElCard>
      </ElTabPane>
      <ElTabPane label="企业档案" name="ENTERPRISE">
        <ElCard class="admin-business-page__filters" shadow="never">
          <ElForm inline label-width="88px">
            <ElFormItem label="计划名称">
              <ElInput v-model="enterpriseQuery.planName" clearable placeholder="计划名称关键字" />
            </ElFormItem>
            <ElFormItem label="计划编号">
              <ElInput v-model="enterpriseQuery.planCode" clearable placeholder="计划编号" />
            </ElFormItem>
            <ElFormItem label="培训类型">
              <ElSelect v-model="enterpriseQuery.trainingTypeId" clearable placeholder="全部培训类型" class="archive-filter-select">
                <ElOption
                  v-for="item in trainingTypeOptions"
                  :key="item.trainingTypeId"
                  :label="item.typeName"
                  :value="item.trainingTypeId"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="计划开始">
              <ElDatePicker
                v-model="enterpriseQuery.startRange"
                type="datetimerange"
                value-format="YYYY-MM-DDTHH:mm:ss"
                range-separator="至"
                start-placeholder="开始时间下限"
                end-placeholder="开始时间上限"
              />
            </ElFormItem>
            <ElFormItem label="计划结束">
              <ElDatePicker
                v-model="enterpriseQuery.endRange"
                type="datetimerange"
                value-format="YYYY-MM-DDTHH:mm:ss"
                range-separator="至"
                start-placeholder="结束时间下限"
                end-placeholder="结束时间上限"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :icon="Search" @click="loadEnterprise">查询</ElButton>
              <ElButton :icon="RefreshLeft" @click="resetEnterpriseQuery">重置</ElButton>
            </ElFormItem>
          </ElForm>
        </ElCard>
        <ElCard class="admin-business-page__table" shadow="never">
          <template #header>
            <div class="admin-business-page__table-heading">
              <strong>企业档案列表</strong>
            </div>
          </template>
          <ElTable v-loading="enterpriseLoading" :data="enterpriseRows" row-key="trainingPlanId" empty-text="暂无已结束培训档案">
            <ElTableColumn prop="planCode" label="计划编号" width="160" />
            <ElTableColumn prop="planName" label="计划名称" min-width="180" show-overflow-tooltip />
            <ElTableColumn label="培训类型" width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ (row as ArchiveView).plan?.trainingTypeName || '—' }}</template>
            </ElTableColumn>
            <ElTableColumn prop="organizationName" label="所属企业" min-width="160" show-overflow-tooltip />
            <ElTableColumn label="计划结束" width="170">
              <template #default="{ row }">{{ (row as ArchiveView).plan?.endTime?.replace('T', ' ') ?? '—' }}</template>
            </ElTableColumn>
            <ElTableColumn label="参训人数" width="100">
              <template #default="{ row }">{{ (row as ArchiveView).participantCount }}</template>
            </ElTableColumn>
            <ElTableColumn label="归档事实" width="120">
              <template #default="{ row }">
                <ElTag :type="(row as ArchiveView).missingItems.length ? 'warning' : 'success'" effect="light">
                  {{ (row as ArchiveView).missingItems.length ? '待补全' : '完整' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="文件格式" width="110">
              <template #default="{ row }">{{ (row as ArchiveView).archiveFileIsPdf ? 'PDF' : 'JSON' }}</template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openEnterpriseRow(row as ArchiveView)">查看一期档案</ElButton>
                <ElButton
                  link
                  type="primary"
                  :icon="Download"
                  :disabled="!canDownloadRow(row as ArchiveView)"
                  :loading="enterpriseDownloadingId === (row as ArchiveView).trainingPlanId"
                  @click="downloadEnterpriseRow(row as ArchiveView)"
                >
                  下载档案
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElTabPane>
      <ElTabPane label="个人档案" name="PERSON">
        <!-- 页签内的滚动容器：个人档案信息量远超一屏，页签本身不自理滚动就会被裁掉。 -->
        <div class="archive-person-pane">
          <ElCard class="admin-business-page__filters" shadow="never">
            <ElForm inline label-width="88px">
              <ElFormItem label="归档人员">
                <ElInput
                  class="archive-person-input"
                  :model-value="person ? `${person.userName}（${person.userCode || person.userId}）` : ''"
                  readonly
                  placeholder="通过人员选择器选择"
                />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" @click="personPickerVisible = true">选择人员</ElButton>
              </ElFormItem>
              <ElFormItem v-if="person" label="归档事实">
                <ElTag :type="person.missingItems.length ? 'warning' : 'success'" effect="light">
                  {{ person.missingItems.length ? missingFactText(person.missingItems) : '完整' }}
                </ElTag>
              </ElFormItem>
            </ElForm>
          </ElCard>
          <ElCard class="admin-business-page__table" shadow="never">
            <template #header>
              <div class="admin-business-page__table-heading">
                <strong>个人培训档案</strong>
                <div class="admin-business-page__table-actions">
                  <span v-if="person && !personDownloadable" class="archive-download-hint">结果待补全的已结束培训需补齐后才能下载。</span>
                  <ElButton
                    type="primary"
                    :icon="Download"
                    :loading="personDownloading"
                    :disabled="!personDownloadable"
                    @click="downloadPerson"
                  >
                    下载个人档案
                  </ElButton>
                </div>
              </div>
            </template>
            <div v-if="!person" class="archive-empty">
              <ElEmpty description="请选择人员后查看个人档案">
                <span class="ws-empty__hint">培训归档保留已结束培训；选择人员后可查看统一历史与档案更正。</span>
              </ElEmpty>
            </div>
            <ElTable
              v-else
              v-loading="personLoading"
              :data="person.trainings"
              row-key="trainingPlanId"
              row-class-name="is-clickable"
              empty-text="该人员暂无已结束培训"
              @row-click="openFacts"
            >
              <ElTableColumn prop="planCode" label="计划编号" width="170" />
              <ElTableColumn label="计划名称" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="archive-person-plan">
                    <span class="archive-person-plan__name">{{ (row as ArchiveView).planName }}</span>
                    <span class="archive-person-plan__meta">
                      {{ (row as ArchiveView).organizationName }} · {{ (row as ArchiveView).plan?.endTime?.replace('T', ' ').slice(0, 16) || '结束时间未知' }}
                    </span>
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn label="结算结果" width="120">
                <template #default="{ row }">{{ personTrainingResult(row as ArchiveView) }}</template>
              </ElTableColumn>
              <ElTableColumn label="归档事实" width="120">
                <template #default="{ row }">
                  <ElTag :type="(row as ArchiveView).missingItems.length ? 'warning' : 'success'" effect="light">
                    {{ (row as ArchiveView).missingItems.length ? missingFactText((row as ArchiveView).missingItems) : '完整' }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="140" fixed="right">
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="openFacts(row as ArchiveView)">查看档案事实</ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </ElCard>
          <ElCard v-if="canListCertificates" v-loading="personCertificatesLoading" class="admin-business-page__table archive-person-certificates" shadow="never">
            <template #header>
              <div class="admin-business-page__table-heading">
                <strong>本人证书</strong>
                <span class="ws-section__extra">共 {{ personCertificates.length }} 张</span>
              </div>
            </template>
            <ArchiveCertificateTable :certificates="personCertificates" />
          </ElCard>
          <ArchiveHistoryPanel v-if="person" :user-id="person.userId" :rows="person.history ?? []" @updated="reloadPersonHistory" />
        </div>
      </ElTabPane>
    </ElTabs>

    <template v-else>
      <PlanArchivePanel
        v-if="archive"
        v-model:archive-code="archiveCode"
        v-model:version-reason="versionReason"
        :archive="archive"
        :archiving="archiving"
        :downloading="downloading"
        :archivable="archivable"
        :downloadable="downloadable"
        @update-archive="updateArchive"
        @download="downloadPlan"
      />
      <div v-else-if="selectedPlanId && !archiveLoading" class="ws-empty">
        <ElEmpty description="未读取到该培训的档案事实" />
      </div>
    </template>

    <PersonPickerDialog v-model:visible="personPickerVisible" title="选择归档人员" @confirm="confirmPerson" />
    <!-- 档案事实有 7 个区块、1800px 上下，只有抽屉能给它完整可读的宽高；表格里塞不下。 -->
    <ElDrawer
      v-model="factsVisible"
      class="archive-facts-drawer"
      :title="factsTitle"
      direction="rtl"
      size="min(1180px, 92vw)"
      :lock-scroll="false"
      destroy-on-close
    >
      <ArchiveFactsSections v-if="factsRow" :archive="factsRow" :show-certificates="false" />
    </ElDrawer>
  </section>
</template>
<style scoped lang="less">
.archive-actions {
  display: flex;
  gap: var(--space-2);
}

.archive-plan-select {
  width: 320px;
}

.archive-filter-select {
  width: 180px;
}

/* 个人档案下载说明与主操作同行，避免把不能下载的原因和按钮分开两处。 */
.archive-download-hint {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

/* 页签内的未选择状态居中显示，避免列表卡撑满高度后空态顶在卡片上沿。 */
.archive-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}

/* 本人证书卡不占满高度，与上方培训记录列表区分主次；卡片间距交给页签统一 gap。 */
.archive-person-certificates {
  flex: 0 0 auto;
}

/* 个人档案页签的信息量远超一屏（培训记录 + 证书 + 历史），三张卡不再平分高度：
   平分会把 83 条历史压成 61px 的两行可视区。改为都按内容高度排布，由本容器统一纵向滚动。 */
.archive-person-pane {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: var(--space-3);
  overflow-y: auto;
  padding-right: 4px;
}

.archive-person-pane > .admin-business-page__table {
  flex: 0 0 auto;
}

/* 卡片不再撑满高度后，表格按行数自适应，1 行数据就不会再留出半张卡的空白。 */
.archive-person-pane > .admin-business-page__table .el-table {
  height: auto;
}

/* 计划名称列改成「名称 + 企业 · 结束时间」两行，省掉一列宽表并让上下文挨在一起。 */
.archive-person-plan {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.archive-person-plan__name {
  color: var(--color-text-primary);
}

.archive-person-plan__meta {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.archive-person-input {
  width: 240px;
}

/* 培训记录整行可点开档案事实，给出行悬浮指针提示。 */
.archive-person-pane :deep(.el-table__row.is-clickable) {
  cursor: pointer;
}

/* 抽屉里的事实区块自带白卡边界，只补正文与抽屉边的呼吸空间。 */
.archive-facts-drawer :deep(.el-drawer__body) {
  padding: 16px 20px 24px;
  overflow-y: auto;
}
</style>
