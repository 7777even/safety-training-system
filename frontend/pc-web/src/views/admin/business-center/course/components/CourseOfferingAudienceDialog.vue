<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { createAudience, getAudienceDetail, replaceAudienceRules, updateAudience, type AudienceRule } from '@/api/audience';
import type { CourseOffering } from '@/api/course-operations';
import type { PersonPickerItem } from '@/api/person-picker';
import { AUDIENCE_DECISION_OPTIONS, AUDIENCE_RESOLVE_MODE_OPTIONS, AUDIENCE_SUBJECT_TYPE_OPTIONS } from '@/constants/enum-options';
import { useUserStore } from '@/stores';
import { canManageTenantScope, currentTenantId } from '@/utils/tenant-context';
import PersonPickerDialog from '@/components/organization/PersonPickerDialog.vue';
import OrganizationSelect from '@/components/organization/OrganizationSelect.vue';
import ReferenceSelect from '@/components/organization/ReferenceSelect.vue';

const visible = defineModel<boolean>({ required: true });
const props = defineProps<{ offering?: CourseOffering }>();
const emit = defineEmits<{ saved: [audienceId: number] }>();
const userStore = useUserStore();

const saving = ref(false);
const personPickerVisible = ref(false);
const editingRuleIndex = ref<number>();
const form = reactive({
  audienceId: undefined as number | undefined,
  organizationId: undefined as number | undefined,
  audienceName: '',
  resolveMode: 'DYNAMIC' as 'DYNAMIC' | 'FROZEN',
  isInternalLinkAllowed: 0 as 0 | 1,
  isExternalLinkAllowed: 0 as 0 | 1,
  status: 1 as 0 | 1,
  version: 0,
  rules: [{ decision: 'INCLUDE', subjectType: 'ALL', subjectId: 0 } as AudienceRule]
});

/** 普通管理员固定会话机构，只有租户管理员可以跨机构创建参与范围。 */
const canSelectOrganization = computed(() => canManageTenantScope(userStore.userInfo));
const canSave = computed(() => Boolean(props.offering && form.organizationId && form.audienceName.trim() && form.rules.length));

function reset(): void {
  Object.assign(form, {
    audienceId: undefined,
    organizationId: currentTenantId(userStore.userInfo),
    audienceName: props.offering ? `${props.offering.offeringName} - 参与范围` : '',
    resolveMode: 'DYNAMIC',
    isInternalLinkAllowed: 0,
    isExternalLinkAllowed: 0,
    status: 1,
    version: 0,
    rules: [{ decision: 'INCLUDE', subjectType: 'ALL', subjectId: 0 }]
  });
}

async function initialize(): Promise<void> {
  reset();
  if (!props.offering?.audienceId) return;
  try {
    const audience = await getAudienceDetail(props.offering.audienceId);
    Object.assign(form, {
      audienceId: audience.audienceId,
      organizationId: audience.organizationId,
      audienceName: audience.audienceName,
      resolveMode: audience.resolveMode,
      isInternalLinkAllowed: audience.isInternalLinkAllowed,
      isExternalLinkAllowed: audience.isExternalLinkAllowed,
      status: audience.status,
      version: audience.version,
      rules: audience.rules?.map((item) => ({ decision: item.decision, subjectType: item.subjectType, subjectId: item.subjectId })) ?? []
    });
  } catch {
    visible.value = false;
  }
}

function addRule(): void {
  form.rules.push({ decision: 'INCLUDE', subjectType: 'ALL', subjectId: 0 });
}

function changeSubject(rule: AudienceRule): void {
  rule.subjectId = rule.subjectType === 'ALL' ? 0 : (undefined as unknown as number);
}

/** 跨机构切换后清空规则，避免引用旧机构的部门、标签、岗位或人员。 */
function changeOrganization(): void {
  form.rules = [{ decision: 'INCLUDE', subjectType: 'ALL', subjectId: 0 }];
}

function referenceKind(rule: AudienceRule): 'DEPT' | 'TAG' | 'POSITION' | 'AUDIENCE' {
  return rule.subjectType as 'DEPT' | 'TAG' | 'POSITION' | 'AUDIENCE';
}

function openPeople(index: number): void {
  editingRuleIndex.value = index;
  personPickerVisible.value = true;
}

function selectPeople(items: PersonPickerItem[]): void {
  const index = editingRuleIndex.value;
  if (index === undefined) return;
  const rule = form.rules[index];
  const sameDecision = rule.decision;
  form.rules.splice(index, 1, ...items.map((item) => ({ decision: sameDecision, subjectType: 'USER' as const, subjectId: item.userId })));
}

async function save(): Promise<void> {
  if (!props.offering || !canSave.value) return;
  if (!form.organizationId) {
    ElMessage.warning('当前会话缺少所属机构，请重新登录后再设置参与范围');
    return;
  }
  if (form.rules.some((rule) => rule.subjectType !== 'ALL' && !rule.subjectId)) {
    ElMessage.warning('请为每条参与规则选择关联对象');
    return;
  }
  saving.value = true;
  try {
    let audienceId = form.audienceId;
    if (audienceId) {
      await updateAudience(audienceId, {
        version: form.version,
        audienceName: form.audienceName,
        isInternalLinkAllowed: form.isInternalLinkAllowed,
        isExternalLinkAllowed: form.isExternalLinkAllowed,
        status: form.status
      });
    } else {
      audienceId = await createAudience({
        organizationId: form.organizationId,
        audienceName: form.audienceName,
        audienceKind: 'BOUND',
        businessType: 'OFFERING',
        businessId: props.offering.courseOfferingId,
        purpose: 'PARTICIPANT',
        resolveMode: form.resolveMode,
        isInternalLinkAllowed: form.isInternalLinkAllowed,
        isExternalLinkAllowed: form.isExternalLinkAllowed,
        status: form.status
      });
    }
    await replaceAudienceRules(audienceId, form.rules);
    emit('saved', audienceId);
    visible.value = false;
  } finally {
    saving.value = false;
  }
}

watch(visible, (opened) => {
  if (opened) void initialize();
});
</script>

<template>
  <ElDialog v-model="visible" title="参与范围设置" width="780px" destroy-on-close :lock-scroll="false">
    <ElAlert title="此设置仅控制开课的参与/报名准入；课程可见范围须等待后端补充多用途绑定契约。" type="info" :closable="false" class="notice" />
    <ElForm label-width="112px">
      <ElFormItem v-if="canSelectOrganization" label="所属机构">
        <OrganizationSelect v-model="form.organizationId" :disabled="Boolean(form.audienceId)" @change="changeOrganization" />
      </ElFormItem>
      <ElFormItem label="范围名称"><ElInput v-model="form.audienceName" maxlength="100" /></ElFormItem>
      <ElFormItem label="解析模式">
        <ElRadioGroup v-model="form.resolveMode" :disabled="Boolean(form.audienceId)">
          <ElRadio v-for="option in AUDIENCE_RESOLVE_MODE_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="链接准入">
        <ElCheckbox v-model="form.isInternalLinkAllowed" :true-value="1" :false-value="0">允许内部链接参加</ElCheckbox>
        <ElCheckbox v-model="form.isExternalLinkAllowed" :true-value="1" :false-value="0">允许外部链接参加</ElCheckbox>
      </ElFormItem>
      <ElFormItem label="状态">
        <ElSwitch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
      </ElFormItem>
      <ElDivider content-position="left">参与规则（排除优先）</ElDivider>
      <div v-for="(rule, index) in form.rules" :key="`${index}-${rule.subjectType}-${rule.subjectId}`" class="rule-row">
        <ElSelect v-model="rule.decision">
          <ElOption v-for="option in AUDIENCE_DECISION_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </ElSelect>
        <ElSelect v-model="rule.subjectType" @change="changeSubject(rule)">
          <ElOption v-for="option in AUDIENCE_SUBJECT_TYPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </ElSelect>
        <span v-if="rule.subjectType === 'ALL'" class="all-tip">全体人员</span>
        <ElButton v-else-if="rule.subjectType === 'USER'" @click="openPeople(index)">选择人员</ElButton>
        <ReferenceSelect v-else v-model="rule.subjectId" :kind="referenceKind(rule)" :organization-id="form.organizationId" />
        <ElButton link type="danger" :disabled="form.rules.length === 1" @click="form.rules.splice(index, 1)">删除</ElButton>
      </div>
      <ElButton plain @click="addRule">新增规则</ElButton>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :disabled="!canSave" :loading="saving" @click="save">保存范围</ElButton>
    </template>
  </ElDialog>
  <PersonPickerDialog v-model:visible="personPickerVisible" :organization-id="form.organizationId" title="选择参与人员" @confirm="selectPeople" />
</template>

<style scoped lang="less">
.notice {
  margin-bottom: 18px;
}

.rule-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
}

.rule-row :deep(.el-select) {
  width: 130px;
}

.all-tip {
  min-width: 120px;
  color: var(--color-text-secondary);
}
</style>
