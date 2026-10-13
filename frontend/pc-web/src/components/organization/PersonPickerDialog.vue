<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { getPersonPickerPage, type PersonPickerItem } from '@/api/person-picker';
import { getDeptManagementTree, type SystemDept } from '@/api/system-dept';
import { useUserStore } from '@/stores';
import { getOrgPositionPage, type OrgPosition } from '@/api/org-position';
import { getPersonGroups, type PersonGroupVO } from '@/api/person-classification';
import { hasPermission } from '@/utils/permission';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { currentTenantId } from '@/utils/tenant-context';

const visible = defineModel<boolean>('visible', { required: true });
const props = withDefaults(defineProps<{ title?: string; preselected?: PersonPickerItem[] }>(), {
  title: '选择人员',
  preselected: () => []
});
const emit = defineEmits<{ confirm: [items: PersonPickerItem[]] }>();
const userStore = useUserStore();
const query = ref({ keyword: '', deptId: undefined as number | undefined, positionId: undefined as number | undefined, groupId: undefined as number | undefined });
const list = ref<PersonPickerItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const loading = ref(false);
/** 查询代次防止较早筛选响应覆盖当前候选。 */
const loadSequence = ref(0);
const selected = ref(new Map<number, PersonPickerItem>());
const restoringSelection = ref(false);
const deptOptions = ref<Array<{ value: number; label: string }>>([]);
/** 分类筛选只影响候选查询，不改变已有选择名单。 */
const positions = ref<OrgPosition[]>([]);
const groups = ref<Array<PersonGroupVO & { groupId: number }>>([]);
const OPTION_PAGE_SIZE = 100;
/** 当前权限允许读取的岗位与用户组。 */
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
const tableRef = ref<{ toggleRowSelection: (row: PersonPickerItem, selected?: boolean) => void }>();
const selectedCount = computed(() => selected.value.size);
const hasSessionOrganization = computed(() => Boolean(currentTenantId(userStore.userInfo)));

/** 将会话机构的部门树展平为候选人员筛选项。 */
function flattenDepartments(nodes: SystemDept[], parentNames: string[] = []): Array<{ value: number; label: string }> {
  return nodes.flatMap((node) => {
    const names = [...parentNames, node.deptName];
    return [{ value: node.deptId, label: names.join(' / ') }, ...flattenDepartments(node.children ?? [], names)];
  });
}

async function loadDepartments(): Promise<void> {
  deptOptions.value = flattenDepartments(await getDeptManagementTree({ status: 1 }));
}

async function load(): Promise<void> {
  const sequence = ++loadSequence.value;
  const organizationId = currentTenantId(userStore.userInfo);
  if (!organizationId) {
    list.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const result = await getPersonPickerPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      'query.organizationId': organizationId,
      'query.keyword': query.value.keyword || undefined,
      'query.deptId': query.value.deptId,
      'query.positionId': query.value.positionId,
      'query.groupId': query.value.groupId
    });
    if (sequence !== loadSequence.value) return;
    list.value = result.list;
    total.value = result.total;
  } catch (error) {
    if (sequence !== loadSequence.value) return;
    // 无成员或无权限筛选失败不能继续展示上一轮全员候选。
    list.value = []; total.value = 0;
    ElMessage.error(error instanceof Error ? error.message : '查询人员失败，请重新选择筛选条件');
  } finally {
    if (sequence === loadSequence.value) loading.value = false;
  }
}

function search(): void {
  pageNum.value = 1;
  void load();
}
function reset(): void {
  query.value = { keyword: '', deptId: undefined, positionId: undefined, groupId: undefined };
  search();
}
function changeSelection(rows: PersonPickerItem[]): void {
  if (restoringSelection.value) return;

  const selectedIds = new Set(rows.map((item) => item.userId));
  list.value.forEach((item) => {
    if (!selectedIds.has(item.userId)) {
      selected.value.delete(item.userId);
    }
  });
  rows.forEach((item) => selected.value.set(item.userId, item));
}
function confirm(): void {
  if (!hasSessionOrganization.value) {
    ElMessage.warning('当前会话缺少所属机构，请重新登录后再选择人员');
    return;
  }
  if (!selected.value.size) {
    ElMessage.warning('请至少选择一名人员');
    return;
  }
  emit('confirm', Array.from(selected.value.values()));
  visible.value = false;
}
async function restoreSelection(): Promise<void> {
  restoringSelection.value = true;
  try {
    await nextTick();
    list.value.forEach((item) => tableRef.value?.toggleRowSelection(item, selected.value.has(item.userId)));
    await nextTick();
  } finally {
    restoringSelection.value = false;
  }
}

watch(visible, async (opened) => {
  if (!opened) return;
  selected.value = new Map(props.preselected.map((item) => [item.userId, item]));

  pageNum.value = 1;
  await Promise.all([loadDepartments(), loadClassifications(), load()]);
});
watch(list, async () => {
  await restoreSelection();
});
</script>

<template>
  <ElDialog v-model="visible" class="person-picker-dialog" :title="title" width="900px" destroy-on-close :lock-scroll="false">
    <ElAlert v-if="!hasSessionOrganization" title="当前会话缺少所属机构，请重新登录后再选择人员" type="warning" :closable="false" show-icon />
    <ElForm inline label-width="72px">
      <ElFormItem label="关键词"><ElInput v-model="query.keyword" clearable placeholder="编码、姓名、手机号" @keyup.enter="search" /></ElFormItem>
      <ElFormItem label="部门">
        <ElSelect v-model="query.deptId" clearable filterable placeholder="全部部门">
          <ElOption v-for="item in deptOptions" :key="item.value" :label="item.label" :value="item.value" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem v-if="hasPermission(userStore.userInfo, 'org:position:list')" label="岗位"><ElSelect v-model="query.positionId" clearable filterable placeholder="全部岗位"><ElOption v-for="position in positions" :key="position.positionId" :value="position.positionId" :label="position.positionName" /></ElSelect></ElFormItem>
      <ElFormItem v-if="hasPermission(userStore.userInfo, 'person:group:list')" label="用户组"><ElSelect v-model="query.groupId" clearable filterable placeholder="全部用户组"><ElOption v-for="group in groups" :key="group.groupId" :value="group.groupId" :label="group.groupName" /></ElSelect></ElFormItem>
      <ElFormItem>
        <ElButton type="primary" @click="search">查询</ElButton>
        <ElButton @click="reset">重置</ElButton>
      </ElFormItem>
    </ElForm>
    <ElTable ref="tableRef" v-loading="loading" :data="list" row-key="userId" @selection-change="changeSelection">
      <ElTableColumn type="selection" reserve-selection width="52" />
      <ElTableColumn prop="userName" label="姓名" min-width="120" show-overflow-tooltip />
      <ElTableColumn prop="userCode" label="用户编码" min-width="130" show-overflow-tooltip />
      <ElTableColumn prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
      <ElTableColumn prop="jobTitle" label="职务" min-width="140" show-overflow-tooltip />
      <ElTableColumn prop="memberType" label="成员类型" min-width="100">
        <template #default="{ row }">
          {{ { INTERNAL: '内部成员', EXTERNAL: '外部成员', REGULATOR: '监管成员' }[row.memberType as string] }}
        </template>
      </ElTableColumn>
      <template #empty><ElEmpty description="当前筛选条件下没有人员" :image-size="52" /></template>
    </ElTable>
    <div class="picker-pagination">
      <ElPagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="LIST_PAGE_SIZES"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="search"
      />
    </div>
    <template #footer>
      <div class="picker-footer">
        <span class="selection-tip">已选择 {{ selectedCount }} 人</span>
        <div class="picker-footer__buttons">
          <ElButton @click="visible = false">取消</ElButton>
          <ElButton type="primary" :disabled="!hasSessionOrganization" @click="confirm">确认选择</ElButton>
        </div>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped lang="less">
/* 候选表较长：正文独立滚动，底部「确认选择」不被顶出视口。 */
:global(.person-picker-dialog .el-dialog__body) {
  max-height: calc(92vh - 140px);
  overflow-y: auto;
}
.el-input,
.el-select {
  width: 160px;
}
/* 分页器贴右下，与表格同宽；用容器对齐而不是给分页器加外边距。 */
.picker-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
/* 页脚左侧交代已选人数，右侧按钮成组，避免用 float 撑开。 */
.picker-footer {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.picker-footer__buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}
.picker-footer__buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}
.selection-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 20px;
}
</style>
