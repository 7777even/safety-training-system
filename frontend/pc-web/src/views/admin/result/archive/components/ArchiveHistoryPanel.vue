<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import type { LearnerHistory, HistoryChanges, HistoryRecordType } from '@shared/types/learner-history';
import { HISTORY_LABELS, HISTORY_FIELDS, historyStatus } from '@shared/utils/learner-history';
import { getHistoryCorrections, appendHistoryCorrection } from '@/api/learner-history';
import HistoryRecordPanel from '@/components/HistoryRecordPanel.vue';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

/** 管理个人档案更正入口，后端逐记录重新鉴权。 */
const props = defineProps<{ userId: number; rows: LearnerHistory[] }>();
const emit = defineEmits<{ updated: [] }>();
const user = useUserStore(),
  detail = ref<LearnerHistory>(),
  dialog = ref(false),
  editing = ref(false),
  busy = ref(false),
  error = ref('');
const canCorrect = computed(() => hasPermission(user.userInfo, 'archive:history:correct'));
const canList = computed(() => hasPermission(user.userInfo, 'archive:history:correction:list'));
const formRef = ref<FormInstance>();
const form = reactive({ field: 'userName' as keyof HistoryChanges, value: '', reason: '' });
const rules: FormRules = {
  value: [{ required: true, message: '请输入更正后值', trigger: 'blur' }],
  reason: [{ required: true, whitespace: true, message: '请输入更正理由', trigger: 'blur' }]
};
/** 学时只适用于课程/培训，个人练习不允许生成成绩。 */
const fields = computed(() =>
  Object.entries(HISTORY_FIELDS)
    .filter(([field]) => field !== 'hours' || (detail.value && ['COURSE', 'TRAINING'].includes(detail.value.recordType)))
    .filter(([field]) => field !== 'score' || (detail.value && !['PRACTICE', 'SIMULATION'].includes(detail.value.recordType)))
);
/** 无任何更正权限时给出明确说明，不显示不可用的操作列。 */
const hasAnyAction = computed(() => canCorrect.value || canList.value);

/** 历史一次读取本人全部记录（实测单人 80+ 条），在卡内按来源、状态、关键词就地筛选。 */
const filters = reactive({ recordType: undefined as HistoryRecordType | undefined, status: undefined as string | undefined, keyword: '' });
/** 筛选项只列既有行里真实出现的取值，避免选出必然为空的组合。 */
const recordTypeOptions = computed(() => {
  const present = new Set(props.rows.map((row) => row.recordType));
  return (Object.keys(HISTORY_LABELS) as HistoryRecordType[])
    .filter((kind) => present.has(kind))
    .map((kind) => ({ value: kind, label: HISTORY_LABELS[kind] }));
});
const statusOptions = computed(() =>
  [...new Set(props.rows.map((row) => row.current.status))]
    .map((status) => ({ value: status, label: historyStatus(status) }))
);
/** 关键词同时匹配名称与历史姓名、账号，管理员按人核对时不必先知道记录标题。 */
const filteredRows = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  return props.rows.filter((row) => {
    if (filters.recordType && row.recordType !== filters.recordType) return false;
    if (filters.status && row.current.status !== filters.status) return false;
    if (!keyword) return true;
    return [row.title, row.current.userName, row.current.userCode]
      .some((value) => (value ?? '').toLowerCase().includes(keyword));
  });
});
const filterActive = computed(() => Boolean(filters.recordType || filters.status || filters.keyword.trim()));
/** 筛选后为空要和「本来就没有历史」区分开，否则会被当成档案缺失。 */
const emptyText = computed(() =>
  props.rows.length && !filteredRows.value.length ? '没有符合筛选条件的历史' : '暂无具备读取权限的历史'
);
function resetFilters(): void {
  filters.recordType = undefined;
  filters.status = undefined;
  filters.keyword = '';
}
/** 留痕与更正权限独立；仅有更正权限时使用当前摘要。 */
async function open(row: LearnerHistory, edit: boolean): Promise<void> {
  error.value = '';
  try {
    detail.value = canList.value ? await getHistoryCorrections(props.userId, row.recordType, row.recordId) : row;
    editing.value = edit;
    dialog.value = true;
    form.field = 'userName';
    form.reason = '';
    resetValue();
  } catch (e) {
    error.value = e instanceof Error ? e.message : '更正记录读取失败';
  }
}
/** 字段变化以当前展示版本为前值。 */
function resetValue(): void {
  const value = detail.value?.current[form.field];
  form.value = value == null ? 'null' : String(value);
}
/** 只构造允许字段，按当前展示版本追加；领域校验由服务端执行。 */
async function save(): Promise<void> {
  if (!detail.value || busy.value || !(await formRef.value?.validate().catch(() => false))) return;
  busy.value = true;
  error.value = '';
  try {
    const raw = form.value.trim();
    const value = form.field === 'score' || form.field === 'hours' ? (raw === 'null' ? null : Number(raw)) : raw;
    if (typeof value === 'number' && !Number.isFinite(value)) throw new Error('请输入有效数值或null');
    detail.value = await appendHistoryCorrection(props.userId, detail.value.recordType, detail.value.recordId, {
      version: detail.value.version,
      changes: { [form.field]: value },
      reason: form.reason.trim()
    });
    dialog.value = false;
    emit('updated');
    ElMessage.success('已追加档案更正，原始业务结果保持原样');
  } catch (e) {
    error.value = e instanceof Error ? e.message : '档案更正失败';
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <ElCard class="admin-business-page__table" shadow="never">
    <template #header>
      <div class="admin-business-page__table-heading">
        <strong>档案历史与更正</strong>
        <div class="admin-business-page__table-actions history-panel__actions">
          <span class="ws-section__extra">
            共 {{ rows.length }} 条<template v-if="filterActive"> · 筛选后 {{ filteredRows.length }} 条</template>
          </span>
          <ElSelect v-model="filters.recordType" class="history-panel__filter" clearable placeholder="全部来源">
            <ElOption v-for="item in recordTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
          <ElSelect v-model="filters.status" class="history-panel__filter" clearable placeholder="全部状态">
            <ElOption v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
          <ElInput v-model="filters.keyword" class="history-panel__keyword" clearable placeholder="名称、姓名或账号" />
          <ElButton link :disabled="!filterActive" @click="resetFilters">重置</ElButton>
        </div>
      </div>
    </template>
    <ElAlert v-if="error" class="history-panel__alert" type="error" :title="error" :closable="false" />
    <p class="ws-tip">
      统一展示课程、正式考试、随堂测试、个人练习/模拟与培训的既有历史。档案更正只追加展示记录，
      不覆盖正式结果、答卷、冻结资产或证书；更正与留痕权限需单独授予，现有角色不会自动获得。
    </p>
    <ElTable :data="filteredRows" max-height="420" :empty-text="emptyText">
      <ElTableColumn label="来源" width="110">
        <template #default="{ row }">{{ HISTORY_LABELS[row.recordType as keyof typeof HISTORY_LABELS] }}</template>
      </ElTableColumn>
      <ElTableColumn prop="title" label="名称" min-width="180" show-overflow-tooltip />
      <ElTableColumn prop="current.occurredTime" label="发生时间" width="170" />
      <ElTableColumn label="状态" width="110">
        <template #default="{ row }">{{ historyStatus(row.current.status) }}</template>
      </ElTableColumn>
      <ElTableColumn prop="version" label="展示版本" width="100" />
      <ElTableColumn v-if="hasAnyAction" label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <ElButton v-if="canCorrect" link type="primary" @click="open(row as LearnerHistory, true)">追加更正</ElButton>
          <ElButton v-if="canList" link @click="open(row as LearnerHistory, false)">更正留痕</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElDialog v-model="dialog" :title="editing ? '追加档案展示更正' : '原始记录与更正留痕'" width="900px">
      <!-- 提交失败后弹窗保持打开，错误原因需在遮罩前可见。 -->
      <ElAlert v-if="error" class="history-panel__alert" type="error" :title="error" :closable="false" />
      <HistoryRecordPanel v-if="detail" :record="detail" />
      <ElForm v-if="editing" ref="formRef" :model="form" :rules="rules" label-width="120px" @submit.prevent="save">
        <ElFormItem label="更正字段">
          <ElSelect v-model="form.field" placeholder="请选择摘要字段" @change="resetValue">
            <ElOption v-for="[key, label] in fields" :key="key" :value="key" :label="label" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="更正后值" prop="value">
          <ElInput v-model="form.value" placeholder="填写后值；时间为2026-10-08T12:00:00，数值可填null" maxlength="100" />
        </ElFormItem>
        <ElFormItem label="更正理由" prop="reason">
          <ElInput v-model="form.reason" type="textarea" placeholder="请输入核对依据和更正理由" maxlength="500" show-word-limit />
        </ElFormItem>
        <ElFormItem><ElButton type="primary" :loading="busy" @click="save">追加更正</ElButton></ElFormItem>
      </ElForm>
    </ElDialog>
  </ElCard>
</template>
<style scoped lang="less">
.history-panel__alert {
  margin-bottom: 12px;
}

/* 筛选与计数同处卡片标题行：不额外占一行高度，也让「筛了多少条」紧挨着筛选项。 */
.history-panel__actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.history-panel__filter {
  width: 148px;
}

.history-panel__keyword {
  width: 200px;
}
</style>
