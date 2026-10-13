<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import PersonPickerDialog from './PersonPickerDialog.vue';
import { getDeptManagementTree, type SystemDept } from '@/api/system-dept';
import type { PersonPickerItem } from '@/api/person-picker';
import type { PersonSelectionRequest, PersonSelectionType } from '@/api/person-selection';
import { useUserStore } from '@/stores';
import { getOrgPositionPage, type OrgPosition } from '@/api/org-position';
import { getPersonGroups, type PersonGroupVO } from '@/api/person-classification';
import { hasPermission } from '@/utils/permission';
import { currentTenantId } from '@/utils/tenant-context';

const visible = defineModel<boolean>('visible', { required: true });
const props = withDefaults(defineProps<{ title?: string; modelValue?: PersonSelectionRequest; preselectedUsers?: PersonPickerItem[] }>(), {
  title: '选择人员范围',
  modelValue: undefined,
  preselectedUsers: () => []
});
const emit = defineEmits<{ confirm: [selection: PersonSelectionRequest, selectedUsers: PersonPickerItem[]] }>();
const userStore = useUserStore();
const selectionType = ref<PersonSelectionType>('ALL');
const deptTree = ref<SystemDept[]>([]);
const checkedDeptIds = ref<number[]>([]);
/** 岗位/组仅保存选择条件，真正下发由后端重新展开。 */
const positions = ref<OrgPosition[]>([]);
const groups = ref<Array<PersonGroupVO & { groupId: number }>>([]);
const checkedPositionIds = ref<number[]>([]);
const checkedGroupIds = ref<number[]>([]);
const OPTION_PAGE_SIZE = 100;
/** 按已有读取权限加载当前启用岗位与用户组。 */
async function loadClassifications(): Promise<void> {
  positions.value = []; groups.value = [];
  if (hasPermission(userStore.userInfo, 'org:position:list')) {
    for (let pageNum = 1; ; pageNum++) {
      const page = await getOrgPositionPage({ pageNum, pageSize: OPTION_PAGE_SIZE, 'query.status': 1 });
      positions.value.push(...page.list); if (positions.value.length >= page.total || !page.list.length) break;
    }
  }
  if (hasPermission(userStore.userInfo, 'person:group:list')) groups.value = (await getPersonGroups()).filter((group): group is PersonGroupVO & { groupId: number } => group.status === 1 && typeof group.groupId === 'number');
}
const selectedUsers = ref<PersonPickerItem[]>([]);
const personPickerVisible = ref(false);
const loadingDepartments = ref(false);
const hasSessionOrganization = computed(() => Boolean(currentTenantId(userStore.userInfo)));

/** 打开指定人员候选列表；候选范围始终由当前会话机构决定。 */
function openPersonPicker(): void {
  if (!hasSessionOrganization.value) {
    ElMessage.warning('当前会话缺少所属机构，请重新登录后再选择人员');
    return;
  }
  personPickerVisible.value = true;
}

async function loadDepartments(): Promise<void> {
  if (!hasSessionOrganization.value) return;
  loadingDepartments.value = true;
  try {
    deptTree.value = await getDeptManagementTree({ status: 1 });
  } finally {
    loadingDepartments.value = false;
  }
}

function selectUsers(items: PersonPickerItem[]): void {
  selectedUsers.value = items;
}

function confirm(): void {
  if (!hasSessionOrganization.value) {
    ElMessage.warning('当前会话缺少所属机构，请重新登录后再选择人员');
    return;
  }
  const subjectIds =
    selectionType.value === 'DEPT' ? checkedDeptIds.value : selectionType.value === 'POSITION' ? checkedPositionIds.value : selectionType.value === 'GROUP' ? checkedGroupIds.value : selectionType.value === 'USER' ? selectedUsers.value.map((item) => item.userId) : [];
  if (selectionType.value !== 'ALL' && !subjectIds.length) {
    ElMessage.warning({ DEPT: '请至少选择一个部门', POSITION: '请至少选择一个岗位', GROUP: '请至少选择一个用户组', USER: '请至少选择一名人员', ALL: '' }[selectionType.value]);
    return;
  }
  emit('confirm', { selectionType: selectionType.value, subjectIds }, selectionType.value === 'USER' ? [...selectedUsers.value] : []);
  visible.value = false;
}

watch(visible, async (opened) => {
  if (!opened) return;
  selectionType.value = props.modelValue?.selectionType ?? 'ALL';
  checkedDeptIds.value = props.modelValue?.selectionType === 'DEPT' ? [...props.modelValue.subjectIds] : [];
  selectedUsers.value = props.modelValue?.selectionType === 'USER' ? [...props.preselectedUsers] : [];
  checkedPositionIds.value = props.modelValue?.selectionType === 'POSITION' ? [...props.modelValue.subjectIds] : [];
  checkedGroupIds.value = props.modelValue?.selectionType === 'GROUP' ? [...props.modelValue.subjectIds] : [];
  await Promise.all([loadDepartments(), loadClassifications()]);
});
</script>

<template>
  <ElDialog v-model="visible" class="person-selection-dialog" :title="title" width="760px" destroy-on-close :lock-scroll="false">
    <ElAlert v-if="!hasSessionOrganization" title="当前会话缺少所属机构，请重新登录后再选择人员" type="warning" :closable="false" show-icon />
    <ElRadioGroup v-model="selectionType" class="selection-type">
      <ElRadioButton value="ALL">全部人员</ElRadioButton>
      <ElRadioButton value="DEPT">按部门</ElRadioButton>
      <ElRadioButton value="USER">指定人员</ElRadioButton>
      <ElRadioButton v-if="hasPermission(userStore.userInfo, 'org:position:list')" value="POSITION">按岗位</ElRadioButton>
      <ElRadioButton v-if="hasPermission(userStore.userInfo, 'person:group:list')" value="GROUP">按用户组</ElRadioButton>
    </ElRadioGroup>
    <div v-if="selectionType === 'ALL'" class="selection-hint">
      <strong>全部人员：</strong>
      保存后，系统会按当前机构和已选条件确定最终人员名单。
    </div>
    <template v-else-if="selectionType === 'DEPT'">
      <div class="selection-hint">
        <strong>按部门：</strong>
        保存后，系统会从所选部门中确定符合条件且启用的人员。
      </div>
      <ElTree
        v-loading="loadingDepartments"
        class="dept-tree"
        :data="deptTree"
        node-key="deptId"
        show-checkbox
        default-expand-all
        :props="{ label: 'deptName', children: 'children' }"
        :default-checked-keys="checkedDeptIds"
        @check="(_, checked) => (checkedDeptIds = checked.checkedKeys as number[])"
      />
    </template>
    <div v-else-if="selectionType === 'POSITION'" class="selection-body">
      <div class="selection-hint"><strong>按岗位：</strong>按岗位当前启用成员下发，多岗位取并集；已下发名单不会自动变化。</div>
      <ElSelect v-model="checkedPositionIds" multiple filterable placeholder="请选择岗位" class="selection-input">
        <ElOption v-for="position in positions" :key="position.positionId" :value="position.positionId" :label="position.positionName" />
      </ElSelect>
    </div>
    <div v-else-if="selectionType === 'GROUP'" class="selection-body">
      <div class="selection-hint"><strong>按用户组：</strong>下发时重新计算用户组当前成员并保存名单；空组或停用组不能下发，可在用户组管理中预览人员。</div>
      <ElSelect v-model="checkedGroupIds" multiple filterable placeholder="请选择用户组" class="selection-input">
        <ElOption v-for="group in groups" :key="group.groupId" :value="group.groupId" :label="group.groupName" />
      </ElSelect>
    </div>
    <div v-else class="specified-users-panel">
      <div class="specified-users-header">
        <div>
          <strong>指定人员</strong>
          <p>仅提交当前显式选择的人员，已选择 {{ selectedUsers.length }} 人。</p>
        </div>
        <ElButton type="primary" plain @click="openPersonPicker">选择人员</ElButton>
      </div>
      <div v-if="selectedUsers.length" class="selected-users-list">
        <div v-for="item in selectedUsers" :key="item.userId" class="selected-user-card">
          <div class="selected-user-card__avatar">{{ item.userName.slice(0, 1) }}</div>
          <div class="selected-user-card__info">
            <strong>{{ item.userName }}</strong>
            <span>{{ item.userCode }}</span>
          </div>
          <ElButton
            text
            type="danger"
            aria-label="移除人员"
            @click="selectedUsers = selectedUsers.filter((user) => user.userId !== item.userId)"
          >
            移除
          </ElButton>
        </div>
      </div>
      <ElEmpty v-else description="尚未选择人员，请点击右上角选择人员" :image-size="64" />
    </div>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :disabled="!hasSessionOrganization" @click="confirm">确认选择</ElButton>
    </template>
  </ElDialog>
  <PersonPickerDialog v-model:visible="personPickerVisible" :preselected="selectedUsers" @confirm="selectUsers" />
</template>

<style scoped lang="less">
/* 部门树或已选人员列表较长时正文独立滚动，底部「确认选择」始终可见。 */
:global(.person-selection-dialog .el-dialog__body) {
  max-height: calc(92vh - 140px);
  overflow-y: auto;
}
.selection-type {
  display: flex;
  width: 100%;
  margin: 18px 0;
}
.selection-type :deep(.el-radio-button) {
  flex: 1;
}
.selection-type :deep(.el-radio-button__inner) {
  width: 100%;
  box-sizing: border-box;
}
.selection-hint {
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}
.selection-hint strong {
  color: var(--el-text-color-regular);
}
/* 岗位/用户组只在下方给一个多选框，统一占满弹窗宽度。 */
.selection-input {
  width: 100%;
}
/* 部门树在弹窗内独立滚动，避免大树把对话框撑出视口。 */
.dept-tree {
  max-height: 320px;
  padding: 4px 8px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: auto;
}
.specified-users-panel {
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-fill-color-lighter);
}
.specified-users-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.specified-users-header strong {
  color: var(--el-text-color-primary);
  font-size: 15px;
}
.specified-users-header p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.selected-users-list {
  display: grid;
  max-height: 220px;
  margin-top: 14px;
  gap: 8px;
  overflow: auto;
}
.selected-user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
}
.selected-user-card__avatar {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-weight: 600;
}
.selected-user-card__info {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 2px;
}
.selected-user-card__info strong,
.selected-user-card__info span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-user-card__info span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
