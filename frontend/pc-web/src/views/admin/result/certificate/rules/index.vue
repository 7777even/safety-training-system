<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { CirclePlus } from '@element-plus/icons-vue';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { CERTIFICATE_RULE_SOURCE_TYPE_OPTIONS, getEnumLabel } from '@/constants/enum-options';
import {
  createRule,
  deleteRule,
  getRule,
  getRulePage,
  getRuleSourceCandidates,
  getTemplatePage,
  updateRule,
  type CertificateRule,
  type CertificateRuleSourceCandidate,
  type CertificateTemplate
} from '@/api/m4-outcome';

type SourceType = CertificateRule['sourceType'];
/** 规则页数据与表单状态。 */
const rules = ref<CertificateRule[]>([]);
const templates = ref<CertificateTemplate[]>([]);
const candidates = ref<CertificateRuleSourceCandidate[]>([]);
/** 来源候选缓存，用于列表回显来源名称；加载失败时列表退回来源编号。 */
const candidateMap = ref<Record<string, CertificateRuleSourceCandidate>>({});
const sourceType = ref<SourceType>('COURSE');
const loading = ref(false);
const saving = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const total = ref(0);
const dialogVisible = ref(false);
const editingRuleId = ref<number>();
const form = reactive({ ruleName: '', certificateTemplateId: undefined as number | undefined, sourceKey: '', status: 1 as 0 | 1, version: 0 });
const publishedTemplates = computed(() => templates.value.filter((item) => item.currentVersionNo > 0));
/** 来源类型枚举与「签发规则」接口的 sourceType 一致；规则页用「独立」口径，避免与培训内引用对象混淆。 */
const SOURCE_TYPE_OPTIONS = CERTIFICATE_RULE_SOURCE_TYPE_OPTIONS;
/** 来源类型切换后的候选提示，明确该类型选的是哪一类对象。 */
const SOURCE_PLACEHOLDER: Record<SourceType, string> = {
  COURSE: '请选择已发布的独立课程',
  EXAM: '请选择已发布的独立理论考试',
  TRAINING: '请选择已发布或进行中的培训计划'
};
/** 非已发布状态在候选里显式标注，便于识别培训计划的「进行中」等状态。 */
const SOURCE_STATUS_LABELS: Record<string, string> = { RUNNING: '进行中', ENDED: '已结束', CANCELED: '已取消', DRAFT: '草稿' };
/** 候选来源一次取足，避免分页把较早的来源挡在下拉之外。 */
const CANDIDATE_PAGE_SIZE = 100;

/** 条件类型固定为达标，签发方式固定为自动签发，界面只做只读说明。 */
function templateName(certificateTemplateId: number): string {
  return templates.value.find((item) => item.certificateTemplateId === certificateTemplateId)?.templateName ?? `模板 #${certificateTemplateId}`;
}
function sourceTypeLabel(type: SourceType): string {
  return getEnumLabel(type, SOURCE_TYPE_OPTIONS);
}
/** 列表回显来源名称与编码，候选未加载成功时保留编号兜底。 */
function sourceLabel(rule: CertificateRule): string {
  const candidate = candidateMap.value[`${rule.sourceType}:${rule.sourceId}`];
  return candidate ? `${candidate.sourceName}（${candidate.sourceCode}）` : `来源编号 ${rule.sourceId}`;
}
/** 候选下拉标签：名称（编码）并在非已发布状态时追加状态，使同名课程或考试可区分。 */
function candidateLabel(item: CertificateRuleSourceCandidate): string {
  const status = SOURCE_STATUS_LABELS[item.publishStatus];
  return status ? `${item.sourceName}（${item.sourceCode}）· ${status}` : `${item.sourceName}（${item.sourceCode}）`;
}

/** 读取规则分页。 */
async function loadRules(): Promise<void> {
  loading.value = true;
  try {
    const page = await getRulePage({ pageNum: pageNum.value, pageSize: pageSize.value });
    rules.value = page.list;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}
/** 按 OpenAPI 必填的 query.sourceType 读取来源候选。 */
async function loadCandidates(): Promise<void> {
  const page = await getRuleSourceCandidates({ pageNum: 1, pageSize: CANDIDATE_PAGE_SIZE, 'query.sourceType': sourceType.value });
  candidates.value = page.list;
}
/** 预取三类来源候选供列表回显使用，失败时静默保留编号兜底。 */
async function loadCandidateMap(): Promise<void> {
  const pages = await Promise.all(
    SOURCE_TYPE_OPTIONS.map((item) =>
      getRuleSourceCandidates({ pageNum: 1, pageSize: CANDIDATE_PAGE_SIZE, 'query.sourceType': item.value }).catch(() => ({
        list: [] as CertificateRuleSourceCandidate[],
        total: 0
      }))
    )
  );
  const map: Record<string, CertificateRuleSourceCandidate> = {};
  pages.forEach((page) =>
    page.list.forEach((candidate) => {
      map[`${candidate.sourceType}:${candidate.sourceId}`] = candidate;
    })
  );
  candidateMap.value = map;
}
/** 已发布模板是创建规则的前置条件，打开表单时一并刷新。 */
async function loadTemplates(): Promise<void> {
  const page = await getTemplatePage({ pageNum: 1, pageSize: CANDIDATE_PAGE_SIZE });
  templates.value = page.list;
}
/** 打开新增表单。 */
async function openCreate(): Promise<void> {
  editingRuleId.value = undefined;
  Object.assign(form, { ruleName: '', certificateTemplateId: undefined, sourceKey: '', status: 1, version: 0 });
  await loadTemplates();
  await loadCandidates();
  dialogVisible.value = true;
}
/** 回显规则详情和乐观锁版本。 */
async function openEdit(row: CertificateRule): Promise<void> {
  const [detail] = await Promise.all([getRule(row.certificateRuleId), loadTemplates()]);
  editingRuleId.value = detail.certificateRuleId;
  sourceType.value = detail.sourceType;
  await loadCandidates();
  Object.assign(form, {
    ruleName: detail.ruleName,
    certificateTemplateId: detail.certificateTemplateId,
    sourceKey: `${detail.sourceType}:${detail.sourceId}`,
    status: detail.status,
    version: detail.version
  });
  dialogVisible.value = true;
}
/** 来源类型切换后清空旧来源。 */
async function changeSourceType(): Promise<void> {
  form.sourceKey = '';
  await loadCandidates();
}
/** 创建或更新规则。 */
async function submit(): Promise<void> {
  const source = candidates.value.find((item) => `${item.sourceType}:${item.sourceId}` === form.sourceKey);
  if (!form.ruleName || !form.certificateTemplateId || !source) {
    ElMessage.warning('请填写规则名称并选择已发布模板和签发来源。');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      ruleName: form.ruleName,
      certificateTemplateId: form.certificateTemplateId,
      sourceType: source.sourceType,
      sourceId: source.sourceId,
      conditionType: 'PASS' as const,
      isAutoIssue: 1 as const,
      status: form.status
    };
    if (editingRuleId.value) await updateRule(editingRuleId.value, { ...payload, version: form.version });
    else await createRule(payload);
    dialogVisible.value = false;
    await loadRules();
    ElMessage.success(editingRuleId.value ? '规则已更新。' : '规则已创建。');
  } finally {
    saving.value = false;
  }
}
/** 删除规则。 */
async function removeRule(row: CertificateRule): Promise<void> {
  await ElMessageBox.confirm(`确认删除规则“${row.ruleName}”吗？`, '删除确认', { type: 'warning' });
  await deleteRule(row.certificateRuleId);
  await loadRules();
  ElMessage.success('规则已删除。');
}
onMounted(async () => {
  await Promise.all([loadRules(), loadTemplates(), loadCandidateMap()]);
});
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>签发规则列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton type="primary" :icon="CirclePlus" @click="openCreate">新建规则</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="rules" row-key="certificateRuleId" empty-text="暂无签发规则，先新建一条规则">
        <ElTableColumn prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip />
        <ElTableColumn label="证书模板" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ templateName((row as CertificateRule).certificateTemplateId) }}</template>
        </ElTableColumn>
        <ElTableColumn label="来源类型" width="130">
          <template #default="{ row }">
            <ElTag effect="plain">{{ sourceTypeLabel((row as CertificateRule).sourceType) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="签发来源" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ sourceLabel(row as CertificateRule) }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="(row as CertificateRule).status === 1 ? 'success' : 'info'">
              {{ (row as CertificateRule).status === 1 ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="150">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openEdit(row as CertificateRule)">编辑</ElButton>
            <ElButton link type="danger" @click="removeRule(row as CertificateRule)">删除</ElButton>
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
          @current-change="loadRules"
          @size-change="loadRules"
        />
      </div>
    </ElCard>

    <ElDialog v-model="dialogVisible" :title="editingRuleId ? '编辑签发规则' : '新建签发规则'" width="620px">
      <div v-if="!publishedTemplates.length" class="ws-tip dialog-tip">
        <ElIcon><CirclePlus /></ElIcon>
        当前没有已发布模板，请先在「证书模板」中上传底图并发布版本。
      </div>
      <div class="ws-tip dialog-tip">独立课程、独立理论考试、培训计划三类来源彼此独立：培训计划内的课程与考试随培训结果发证，不单独配置规则。</div>
      <ElForm label-width="110px">
        <ElFormItem label="规则名称" required>
          <ElInput v-model="form.ruleName" placeholder="例如 新员工入职培训合格发证" />
        </ElFormItem>
        <ElFormItem label="证书模板" required>
          <ElSelect v-model="form.certificateTemplateId" placeholder="请选择已发布模板" filterable>
            <ElOption
              v-for="item in publishedTemplates"
              :key="item.certificateTemplateId"
              :label="item.templateName"
              :value="item.certificateTemplateId"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="来源类型" required>
          <ElRadioGroup v-model="sourceType" @change="changeSourceType">
            <ElRadioButton v-for="item in SOURCE_TYPE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="签发来源" required>
          <ElSelect v-model="form.sourceKey" :placeholder="SOURCE_PLACEHOLDER[sourceType]" filterable>
            <ElOption
              v-for="item in candidates"
              :key="`${item.sourceType}:${item.sourceId}`"
              :label="candidateLabel(item)"
              :value="`${item.sourceType}:${item.sourceId}`"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="触发条件">
          <div class="condition-row">
            <ElTag type="success" effect="light">达标（PASS）</ElTag>
            <span>来源结果达标时自动签发，无需人工确认。</span>
          </div>
        </ElFormItem>
        <ElFormItem label="状态">
          <div class="status-row">
            <ElSwitch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            <span v-if="form.status === 1">同一来源只能有一条启用规则；更换模板请先停用旧规则再启用新规则。</span>
          </div>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="submit">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.dialog-tip {
  margin-bottom: var(--space-3);
}

.condition-row,
.status-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
</style>
