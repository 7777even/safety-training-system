<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getPersonProfile,
  getPersonTrainingFacts,
  savePersonProfile,
  type PersonProfileRequest,
  type PersonTrainingFactVO
} from '@/api/person-classification';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

/** 手工特征可维护；培训事实仅展示。 */
const visible = defineModel<boolean>('visible', { required: true });
const props = defineProps<{ userId?: number; userName?: string }>();
const userStore = useUserStore();
const form = ref<PersonProfileRequest>({ version: 0 });
const facts = ref<PersonTrainingFactVO[]>([]);
const loading = ref(false);
const saving = ref(false);
/** 所有迟到响应都必须与当前人员和本次打开代次一致。 */
const loadSequence = ref(0);
const formUserId = ref<number>();
/** 固定字段来自分类契约，不接受任意自定义属性。 */
const textFields = [
  { key: 'occupation', label: '职业' },
  { key: 'education', label: '学历' },
  { key: 'major', label: '专业' },
  { key: 'workExperience', label: '从业经历' },
  { key: 'specialty', label: '专长' },
  { key: 'qualification', label: '职业资格' },
  { key: 'rewardPenaltyNote', label: '人员奖惩说明' },
  { key: 'workContent', label: '工作内容' },
  { key: 'workSkills', label: '工作技能' }
] as const;
/** 培训结果中文标签。 */
const resultLabels: Record<string, string> = { INCOMPLETE: '未完成', PASSED: '合格', FAILED: '未通过', EXPIRED: '已到期' };

/** 日期选择器禁止未来日期；后端仍执行独立校验。 */
function disabledBirthday(date: Date): boolean {
  return date.getTime() > Date.now();
}

/** 保存后重读版本，防止同一弹窗重复保存产生旧版本冲突。 */
async function save(): Promise<void> {
  const userId = formUserId.value;
  const sequence = loadSequence.value;
  if (!userId || userId !== props.userId || loading.value) return;
  saving.value = true;
  try {
    await savePersonProfile(userId, { ...form.value });
    const profile = await getPersonProfile(userId);
    if (sequence !== loadSequence.value || userId !== props.userId || !visible.value) return;
    form.value = profile;
    ElMessage.success('人员特征已保存');
  } finally {
    saving.value = false;
  }
}
/** 捕获目标人员；快速切换时丢弃旧请求的表单、事实与加载态。 */
async function loadProfile(userId: number): Promise<void> {
  const sequence = ++loadSequence.value;
  loading.value = true;
  form.value = { version: 0 };
  facts.value = [];
  formUserId.value = undefined;
  try {
    const [profile, training] = await Promise.all([getPersonProfile(userId), getPersonTrainingFacts(userId)]);
    if (sequence !== loadSequence.value || props.userId !== userId || !visible.value) return;
    form.value = profile;
    facts.value = training;
    formUserId.value = userId;
  } finally {
    if (sequence === loadSequence.value) loading.value = false;
  }
}
watch(
  () => [visible.value, props.userId],
  () => {
    if (visible.value && props.userId) void loadProfile(props.userId);
    else {
      loadSequence.value++;
      formUserId.value = undefined;
    }
  }
);
</script>

<template>
  <ElDialog
    v-model="visible"
    class="person-profile-dialog"
    :title="`${props.userName || '人员'}的特征与培训事实`"
    width="850px"
    destroy-on-close
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :show-close="!saving"
  >
    <div v-loading="loading">
      <ElAlert title="手工特征用于人员分类；下方培训事实来自系统记录，不能在此修改。" type="info" :closable="false" show-icon />
      <ElForm :model="form" label-width="120px" :disabled="loading || !hasPermission(userStore.userInfo, 'person:profile:edit')" class="profile-form">
        <ElRow :gutter="20">
          <ElCol :span="12">
            <ElFormItem label="出生日期">
              <ElDatePicker
                v-model="form.birthDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择出生日期"
                :disabled-date="disabledBirthday"
              />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="性别">
              <ElSelect v-model="form.gender" clearable placeholder="请选择性别">
                <ElOption label="男" value="MALE" />
                <ElOption label="女" value="FEMALE" />
                <ElOption label="其他" value="OTHER" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol v-for="field in textFields" :key="field.key" :span="12">
            <ElFormItem :label="field.label">
              <ElInput v-model="form[field.key]" :placeholder="`请输入${field.label}`" clearable maxlength="1000" />
            </ElFormItem>
          </ElCol>
        </ElRow>
      </ElForm>
      <section class="profile-facts">
        <div class="profile-facts__head">
          <h3>系统培训事实</h3>
          <span>来自培训记录，仅供参照，不能在此修改</span>
          <ElTag v-if="facts.length" type="info" effect="plain" size="small">共 {{ facts.length }} 条</ElTag>
        </div>
        <ElTable :data="facts" row-key="trainingPlanId" max-height="280" class="profile-facts__table">
          <ElTableColumn prop="planName" label="培训计划" min-width="180" show-overflow-tooltip />
          <ElTableColumn label="结果" width="110">
            <template #default="{ row }">
              <ElTag :type="row.resultStatus === 'PASSED' ? 'success' : row.resultStatus === 'FAILED' ? 'danger' : 'info'" size="small" effect="plain">
                {{ resultLabels[row.resultStatus] || '尚未形成结果' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="onlineHours" label="线上学时" width="100" />
          <ElTableColumn prop="offlineHours" label="线下学时" width="100" />
          <ElTableColumn label="培训奖惩记录" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.rewardPenaltyTitles || '—' }}</template>
          </ElTableColumn>
          <ElTableColumn prop="completedTime" label="完成时间" min-width="170" show-overflow-tooltip />
          <template #empty><ElEmpty description="该人员暂无培训记录" :image-size="52" /></template>
        </ElTable>
      </section>
    </div>
    <template #footer>
      <ElButton @click="visible = false">关闭</ElButton>
      <ElButton v-if="hasPermission(userStore.userInfo, 'person:profile:edit')" type="primary" :loading="saving" :disabled="loading" @click="save">
        保存特征
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="less">
/* 特征字段加培训事实较长：正文独立滚动，底部「保存特征」始终可见。 */
:global(.person-profile-dialog .el-dialog__body) {
  max-height: calc(92vh - 140px);
  overflow-y: auto;
}
.profile-form {
  margin-top: 16px;
}
.profile-form :deep(.el-select),
.profile-form :deep(.el-date-editor) {
  width: 100%;
}
/* 培训事实是只读区，与上方可编辑表单用分隔线明确区分。 */
.profile-facts {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.profile-facts__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}
.profile-facts__head h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
}
.profile-facts__head span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.profile-facts__head :deep(.el-tag) {
  margin-left: auto;
}
</style>
