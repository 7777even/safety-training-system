<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { CirclePlus, Download, Link, Search } from '@element-plus/icons-vue';
import QRCode from 'qrcode';
import SurveyStatsContent from '@/components/survey/SurveyStatsContent.vue';
import {
  createSurveyInstance,
  deleteSurvey,
  getSurveyInstances,
  getSurveyPage,
  getSurveyStats,
  type Survey,
  type SurveyInstance,
  type SurveyResultVisibility,
  type SurveyStats
} from '@/api/m4-outcome';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const userStore = useUserStore();
type SurveyPurpose = SurveyInstance['purpose'];
const PURPOSE_OPTIONS: Array<{ label: string; value: SurveyPurpose }> = [
  { label: '线下培训调查', value: 'TRAINING_SURVEY' },
  { label: '培训机构评价', value: 'ORG_EVALUATION' }
];
const VISIBILITY_OPTIONS: Array<{ label: string; value: SurveyResultVisibility }> = [
  { label: '仅管理员可见', value: 'ADMIN_ONLY' },
  { label: '填写后本人可见', value: 'SUBMITTER_AFTER_SUBMIT' }
];
/** 问卷用途与投放用途一一对应，界面只展示中文用途，不暴露编码。 */
const SURVEY_TYPE_LABELS: Record<Survey['surveyType'], string> = {
  TRAINING: '线下培训调查',
  ORGANIZATION_EVALUATION: '培训机构评价'
};
const PURPOSE_LABELS: Record<SurveyPurpose, string> = {
  TRAINING_SURVEY: '线下培训调查',
  ORG_EVALUATION: '培训机构评价'
};
const VISIBILITY_LABELS: Record<SurveyResultVisibility, string> = {
  ADMIN_ONLY: '仅管理员可见',
  SUBMITTER_AFTER_SUBMIT: '填写后本人可见'
};
/** 草稿下拉一次性取足，避免分页把较早的草稿挡在投放入口之外。 */
const DRAFT_PAGE_SIZE = 100;

const surveys = ref<Survey[]>([]),
  instances = ref<SurveyInstance[]>([]),
  drafts = ref<Survey[]>([]),
  total = ref(0),
  loading = ref(false),
  publishing = ref(false),
  pageNum = ref(1),
  pageSize = ref(DEFAULT_LIST_PAGE_SIZE),
  active = ref<'survey' | 'instance'>('survey');
const form = reactive({
  instanceCode: '',
  instanceName: '',
  surveyId: undefined as number | undefined,
  purpose: 'TRAINING_SURVEY' as SurveyPurpose,
  resultVisibility: 'ADMIN_ONLY' as SurveyResultVisibility
});
const stats = ref<SurveyStats>();
const statsVisible = ref(false);
const qrVisible = ref(false);
const qrImage = ref('');
const qrLink = ref('');
const qrInstanceName = ref('');
/** 问卷与投放的新增权限分别控制两个页签的主操作。 */
const canAddSurvey = computed(() => hasPermission(userStore.userInfo, 'survey:survey:add'));
const canAddInstance = computed(() => hasPermission(userStore.userInfo, 'survey:instance:add'));
/** 已选问卷用于投放前二次确认，避免投错用途。 */
const selectedSurvey = computed(() => drafts.value.find((survey) => survey.surveyId === form.surveyId));
/** 根据后端返回的填写地址生成可供其他设备扫描的绝对链接。 */
function absoluteFillUrl(path: string): string {
  return new globalThis.URL(path, globalThis.location.origin).href;
}
/** 用问卷类型约束投放用途，避免将机构评价误投为培训调查。 */
function selectableSurveys(): Survey[] {
  return drafts.value.filter(
    (survey) => survey.contentStatus === 'DRAFT' && survey.surveyType === (form.purpose === 'ORG_EVALUATION' ? 'ORGANIZATION_EVALUATION' : 'TRAINING')
  );
}
function selectPurpose(): void {
  form.surveyId = undefined;
}
function startPublish(row: Survey): void {
  form.purpose = row.surveyType === 'ORGANIZATION_EVALUATION' ? 'ORG_EVALUATION' : 'TRAINING_SURVEY';
  form.surveyId = row.surveyId;
  if (!form.instanceName) form.instanceName = `${row.surveyName}投放`;
  active.value = 'instance';
  pageNum.value = 1;
}
/** 草稿问卷列表只在存在投放权限时加载，避免无权限用户多打一次接口。 */
async function loadDrafts(): Promise<void> {
  if (!canAddInstance.value) return;
  const data = await getSurveyPage({ pageNum: 1, pageSize: DRAFT_PAGE_SIZE });
  drafts.value = data.list;
}
async function loadSurveys(): Promise<{ total: number }> {
  const data = await getSurveyPage({ pageNum: pageNum.value, pageSize: pageSize.value });
  surveys.value = data.list;
  return { total: data.total };
}
async function loadInstances(): Promise<{ total: number }> {
  const data = await getSurveyInstances({ pageNum: pageNum.value, pageSize: pageSize.value });
  instances.value = data.list;
  return { total: data.total };
}
async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = active.value === 'survey' ? await loadSurveys() : await loadInstances();
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}
/** 投放会同时改变问卷状态和投放列表，两处都要刷新。 */
async function refreshAll(): Promise<void> {
  loading.value = true;
  try {
    const [, instanceData] = await Promise.all([loadSurveys(), loadInstances()]);
    total.value = instanceData.total;
  } finally {
    loading.value = false;
  }
}
async function publish(): Promise<void> {
  if (!form.surveyId || !form.instanceCode || !form.instanceName) {
    ElMessage.warning('请完整填写投放信息。');
    return;
  }
  publishing.value = true;
  try {
    await createSurveyInstance(form);
    ElMessage.success('问卷已投放，可在投放管理中复制填写链接或展示二维码。');
    active.value = 'instance';
    pageNum.value = 1;
    Object.assign(form, { instanceCode: '', instanceName: '', surveyId: undefined });
    await Promise.all([refreshAll(), loadDrafts()]);
  } finally {
    publishing.value = false;
  }
}
/** 草稿删除由服务端再次校验是否已投放。 */
async function removeSurvey(row: Survey): Promise<void> {
  await ElMessageBox.confirm(`确认删除未投放问卷“${row.surveyName}”吗？`, '删除问卷', { type: 'warning' });
  await deleteSurvey(row.surveyId);
  ElMessage.success('问卷已删除。');
  await Promise.all([load(), loadDrafts()]);
}
/** 二维码仅在查看时生成，避免列表每次加载都生成图片。 */
async function showQr(row: SurveyInstance): Promise<void> {
  qrLink.value = absoluteFillUrl(row.qrContent);
  qrImage.value = await QRCode.toDataURL(qrLink.value, { width: 280, margin: 2 });
  qrInstanceName.value = row.instanceName;
  qrVisible.value = true;
}
async function showStats(id: number): Promise<void> {
  stats.value = await getSurveyStats(id);
  statsVisible.value = true;
}
async function copy(url: string): Promise<void> {
  await globalThis.navigator.clipboard.writeText(absoluteFillUrl(url));
  ElMessage.success('填写链接已复制。');
}
/** 二维码图片在本地生成，下载不经过任何外部服务。 */
function downloadQr(): void {
  if (!qrImage.value) return;
  const link = globalThis.document.createElement('a');
  link.href = qrImage.value;
  link.download = `${qrInstanceName.value || '问卷'}-二维码.png`;
  link.click();
}
onMounted(async () => {
  await Promise.all([load(), loadDrafts()]);
});
</script>
<template>
  <section class="admin-business-page">
    <ElTabs v-model="active" class="admin-page-tabs admin-page-tabs--fill" @tab-change="load">
      <ElTabPane label="问卷" name="survey">
        <ElCard class="admin-business-page__table" shadow="never">
          <template #header>
            <div class="admin-business-page__table-heading">
              <strong>问卷列表</strong>
              <div class="admin-business-page__table-actions">
                <ElButton v-if="canAddSurvey" type="primary" :icon="CirclePlus" @click="$router.push('/admin/result/survey/workspace')">新建问卷</ElButton>
              </div>
            </div>
          </template>
          <ElTable v-loading="loading" :data="surveys" row-key="surveyId" empty-text="暂无问卷，先新建一份草稿">
            <ElTableColumn prop="surveyCode" label="问卷编码" width="180" />
            <ElTableColumn prop="surveyName" label="问卷名称" min-width="220" show-overflow-tooltip />
            <ElTableColumn label="问卷用途" width="150">
              <template #default="{ row }">
                <ElTag effect="plain">{{ SURVEY_TYPE_LABELS[(row as Survey).surveyType] }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="内容状态" width="120">
              <template #default="{ row }">
                <ElTag :type="(row as Survey).contentStatus === 'DRAFT' ? 'warning' : 'success'">
                  {{ (row as Survey).contentStatus === 'DRAFT' ? '未投放' : '已投放' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="220">
              <template #default="{ row }">
                <ElButton link type="primary" @click="$router.push(`/admin/result/survey/workspace/${row.surveyId}`)">编辑与预览</ElButton>
                <ElButton v-if="row.contentStatus === 'DRAFT' && canAddInstance" link type="primary" @click="startPublish(row as Survey)">
                  投放
                </ElButton>
                <ElButton
                  v-if="row.contentStatus === 'DRAFT' && hasPermission(userStore.userInfo, 'survey:survey:delete')"
                  link
                  type="danger"
                  @click="removeSurvey(row as Survey)"
                >
                  删除
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
              @size-change="load"
            />
          </div>
        </ElCard>
      </ElTabPane>
      <ElTabPane label="投放管理" name="instance">
        <ElCard v-if="canAddInstance" class="publish-card" shadow="never">
          <header class="ws-section__head">
            <div>
              <h2>发起投放</h2>
              <p>投放用途决定了可选问卷范围，投放后填写入口与二维码才会生成。</p>
            </div>
            <span class="ws-section__extra">仅未投放问卷可投放</span>
          </header>
          <ElForm label-position="top" class="publish-form">
            <div class="publish-form__grid">
              <ElFormItem label="投放编码" required>
                <ElInput v-model="form.instanceCode" placeholder="例如 SURVEY-2026-001" />
              </ElFormItem>
              <ElFormItem label="投放名称" required>
                <ElInput v-model="form.instanceName" placeholder="例如 2026 年三季度线下培训调查" />
              </ElFormItem>
              <ElFormItem label="投放用途" required>
                <ElSelect v-model="form.purpose" placeholder="请选择投放用途" @change="selectPurpose">
                  <ElOption v-for="item in PURPOSE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="结果可见" required>
                <ElSelect v-model="form.resultVisibility" placeholder="请选择可见范围">
                  <ElOption v-for="item in VISIBILITY_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="问卷" required class="publish-form__field--wide">
                <ElSelect v-model="form.surveyId" placeholder="请选择与用途一致的草稿问卷" filterable>
                  <ElOption v-for="s in selectableSurveys()" :key="s.surveyId" :label="`${s.surveyName}（${s.surveyCode}）`" :value="s.surveyId" />
                </ElSelect>
              </ElFormItem>
            </div>
            <footer class="publish-form__footer">
              <span v-if="selectedSurvey" class="ws-tip">
                <ElIcon><Search /></ElIcon>
                已选择“{{ selectedSurvey.surveyName }}”，投放后该问卷将冻结为只读。
              </span>
              <span v-else class="ws-tip">草稿问卷按用途过滤，切换用途后需要重新选择问卷。</span>
              <ElButton type="primary" :loading="publishing" @click="publish">发起投放</ElButton>
            </footer>
          </ElForm>
        </ElCard>
        <ElCard class="admin-business-page__table" shadow="never">
          <template #header>
            <div class="admin-business-page__table-heading">
              <strong>投放记录</strong>
            </div>
          </template>
          <ElTable v-loading="loading" :data="instances" row-key="surveyInstanceId" empty-text="暂无投放记录">
            <ElTableColumn prop="instanceCode" label="投放编码" width="170" />
            <ElTableColumn prop="instanceName" label="投放名称" min-width="180" show-overflow-tooltip />
            <ElTableColumn label="投放用途" width="150">
              <template #default="{ row }">
                <ElTag effect="plain">{{ PURPOSE_LABELS[(row as SurveyInstance).purpose] }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="结果可见" width="150">
              <template #default="{ row }">{{ VISIBILITY_LABELS[(row as SurveyInstance).resultVisibility] }}</template>
            </ElTableColumn>
            <ElTableColumn label="填写入口" min-width="260">
              <template #default="{ row }">
                <div class="fill-entry">
                  <a class="fill-entry__link" :href="absoluteFillUrl(row.fillUrl)" target="_blank" rel="noopener">
                    {{ absoluteFillUrl(row.fillUrl) }}
                  </a>
                  <ElButton link :icon="Link" @click="copy(row.fillUrl)">复制</ElButton>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="180">
              <template #default="{ row }">
                <ElButton link type="primary" @click="showQr(row as SurveyInstance)">二维码</ElButton>
                <ElButton link type="primary" @click="showStats(row.surveyInstanceId)">查看统计</ElButton>
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
              @size-change="load"
            />
          </div>
        </ElCard>
      </ElTabPane>
    </ElTabs>
    <ElDialog v-model="statsVisible" title="问卷统计" width="760px" top="6vh">
      <SurveyStatsContent :stats="stats" />
    </ElDialog>
    <ElDialog v-model="qrVisible" title="扫码填写问卷" width="400px">
      <div class="qr-preview">
        <p class="qr-preview__name">{{ qrInstanceName }}</p>
        <img :src="qrImage" alt="问卷填写二维码" >
        <p class="qr-preview__link">{{ qrLink }}</p>
        <p class="qr-preview__tip">二维码在本地浏览器生成，不会上传到任何第三方服务。</p>
      </div>
      <template #footer>
        <ElButton :icon="Download" @click="downloadQr">下载二维码</ElButton>
        <ElButton :icon="Link" @click="copy(qrLink)">复制填写链接</ElButton>
        <ElButton type="primary" @click="qrVisible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
<style scoped lang="less">
/* 投放表单卡与列表卡之间的间距由页签内容区统一控制，这里只固定表单卡不参与高度伸缩。 */
.publish-card {
  flex: 0 0 auto;
}

.publish-card :deep(.el-card__body) {
  display: grid;
  gap: var(--space-3);
}

/* 字段落在固定列栅格上：宽屏 3 列正好两行填满，不再出现半行列宽不齐。 */
.publish-form__grid {
  display: grid;
  gap: var(--space-4) var(--space-5);
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

/* 问卷是投放的主选择项，跨两列收尾；窄屏下退回整列。 */
.publish-form__field--wide {
  grid-column: span 2;
}

/* 行距交给栅格 gap，label 与控件紧贴，保证每格上下节奏一致。 */
.publish-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.publish-form :deep(.el-form-item__label) {
  padding-bottom: var(--space-1);
  line-height: 20px;
}

.publish-form :deep(.el-select),
.publish-form :deep(.el-input) {
  width: 100%;
}

/* 提示与主操作与字段区分隔开，视觉上收口。 */
.publish-form__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--color-border);
}

.fill-entry {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.fill-entry__link {
  overflow: hidden;
  min-width: 0;
  color: var(--color-primary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qr-preview {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  text-align: center;
}

.qr-preview__name {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.qr-preview img {
  width: 260px;
  height: 260px;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
}

.qr-preview__link {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
  overflow-wrap: anywhere;
}

.qr-preview__tip {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
/* 中屏两列、窄屏单列，列宽始终等分。 */
@media (max-width: 1180px) {
  .publish-form__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .publish-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .publish-form__field--wide {
    grid-column: auto;
  }
}
</style>
