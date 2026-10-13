<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getPersonGroups,
  previewPersonGroup,
  savePersonGroup,
  type PersonGroupVO,
  type PersonClassificationCondition
} from '@/api/person-classification';
import { getDeptManagementTree, type SystemDept } from '@/api/system-dept';
import { getOrgPositionPage, type OrgPosition } from '@/api/org-position';
import { getOrgTagPage, type OrgTag } from '@/api/org-tag';
import { getTrainingPlanPage, type TrainingPlan } from '@/api/training-plan';
import type { PersonPickerItem } from '@/api/person-picker';
import PersonProfileDialog from '@/components/organization/PersonProfileDialog.vue';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

/** 条件用户组管理：预览和保存条件，不直接下发任务。 */
const userStore = useUserStore();
const records = ref<PersonGroupVO[]>([]);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
/** 用户组按机构全量返回，分页只在本地切片；页码越界时回落到最后一页。 */
const pagedRecords = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value;
  return records.value.slice(start, start + pageSize.value);
});
const loading = ref(false);
const saving = ref(false);
const previewing = ref(false);
const visible = ref(false);
const form = ref<PersonGroupVO>({ groupName: '', status: 1, conditions: {} });
const conditions = ref<PersonClassificationCondition>({});
const preview = ref<PersonPickerItem[]>([]);
const previewed = ref(false);
const departments = ref<SystemDept[]>([]);
const positions = ref<OrgPosition[]>([]);
const tags = ref<OrgTag[]>([]);
const plans = ref<TrainingPlan[]>([]);
const profileVisible = ref(false);
const profileUser = ref<PersonPickerItem>();
const textFields = [
  { key: 'occupation', label: '职业' },
  { key: 'major', label: '专业' },
  { key: 'workExperience', label: '从业经历' },
  { key: 'specialty', label: '专长' },
  { key: 'qualification', label: '职业资格' },
  { key: 'rewardPenaltyNote', label: '人员奖惩说明' },
  { key: 'workContent', label: '工作内容' },
  { key: 'workSkills', label: '工作技能' }
] as const;
const results = [
  { label: '未完成', value: 'INCOMPLETE' },
  { label: '合格', value: 'PASSED' },
  { label: '未通过', value: 'FAILED' }
];
/** 8 个特征关键词属于次要条件，收进折叠区，避免首屏被长表单占满。 */
const advancedOpen = ref<string[]>([]);
/** 已填关键词个数：收起折叠区时也能看出还有几项附加条件在生效。 */
const advancedCount = computed(() => textFields.filter((field) => Boolean(conditions.value[field.key]?.trim())).length);

/** 只查询已有权限允许读取的选项，避免只读组管理触发无关403。 */
async function loadOptions(): Promise<void> {
  if (hasPermission(userStore.userInfo, 'sys:dept:list')) departments.value = await getDeptManagementTree({ status: 1 });
  // 下拉候选按后端分页逐页读取，不能默默截掉第一页以外岗位、标签或计划。
  if (hasPermission(userStore.userInfo, 'org:position:list')) {
    positions.value = [];
    for (let pageNum = 1; ; pageNum++) {
      const page = await getOrgPositionPage({ pageNum, pageSize: 100, 'query.status': 1 });
      positions.value.push(...page.list);
      if (positions.value.length >= page.total || !page.list.length) break;
    }
  }
  if (hasPermission(userStore.userInfo, 'org:tag:list')) {
    tags.value = [];
    for (let pageNum = 1; ; pageNum++) {
      const page = await getOrgTagPage({ pageNum, pageSize: 100, 'query.status': 1 });
      tags.value.push(...page.list);
      if (tags.value.length >= page.total || !page.list.length) break;
    }
  }
  if (hasPermission(userStore.userInfo, 'training:plan:list')) {
    plans.value = [];
    for (let pageNum = 1; ; pageNum++) {
      const page = await getTrainingPlanPage({ pageNum, pageSize: 100 });
      plans.value.push(...page.list);
      if (plans.value.length >= page.total || !page.list.length) break;
    }
  }
}
/** 刷新用户组管理列表。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    records.value = await getPersonGroups();
    const maxPage = Math.max(1, Math.ceil(records.value.length / pageSize.value));
    if (pageNum.value > maxPage) pageNum.value = maxPage;
  } finally {
    loading.value = false;
  }
}
/** 深复制保存条件，取消编辑不污染列表。 */
async function open(group?: PersonGroupVO): Promise<void> {
  form.value = group ? JSON.parse(JSON.stringify(group)) : { groupName: '', status: 1, conditions: {} };
  conditions.value = form.value.conditions || {};
  preview.value = [];
  previewed.value = false;
  visible.value = true;
  await loadOptions();
}
/** 预览返回真实人员名称；条件变化后必须重新预览。 */
async function previewMembers(): Promise<void> {
  previewing.value = true;
  try {
    preview.value = await previewPersonGroup(conditions.value);
    previewed.value = true;
  } finally {
    previewing.value = false;
  }
}
/** 仅保存分类定义，后续选择组时后端重新计算人员。 */
async function save(): Promise<void> {
  if (!form.value.groupName?.trim()) {
    ElMessage.warning('请输入用户组名称');
    return;
  }
  saving.value = true;
  try {
    await savePersonGroup({ ...form.value, groupName: form.value.groupName.trim(), status: form.value.status ?? 1, conditions: conditions.value });
    visible.value = false;
    ElMessage.success('用户组已保存，已有任务名单保持不变');
    await load();
  } finally {
    saving.value = false;
  }
}
/** 从分类人员进入手工特征与培训事实。 */
function openProfile(user: unknown): void {
  profileUser.value = user as PersonPickerItem;
  profileVisible.value = true;
}
watch(
  conditions,
  () => {
    previewed.value = false;
  },
  { deep: true }
);
onMounted(load);
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>人员分类与用户组</strong>
          <div class="admin-business-page__table-actions">
            <ElButton @click="load">刷新</ElButton>
            <ElButton v-if="hasPermission(userStore.userInfo, 'person:group:edit')" type="primary" @click="open()">新增条件组</ElButton>
          </div>
        </div>
      </template>
      <ElAlert
        class="person-group__tip"
        title="用户组按当前人员属性和培训事实计算。部门、岗位、标签等同字段多选取并集，不同条件取交集；下发后不会自动增删人员。"
        type="info"
        :closable="false"
        show-icon
      />
      <ElTable v-loading="loading" :data="pagedRecords" row-key="groupId">
        <ElTableColumn prop="groupName" label="用户组名称" min-width="180" show-overflow-tooltip />
        <ElTableColumn label="状态" width="120">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="160">
          <template #default="{ row }">
            <ElButton link type="primary" @click="open(row)">
              {{ hasPermission(userStore.userInfo, 'person:group:edit') ? '编辑与预览' : '查看与预览' }}
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty><ElEmpty description="暂无用户组，新增后可按条件圈选人员" /></template>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="records.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </ElCard>
    <ElDialog
      v-model="visible"
      class="condition-group-dialog"
      :title="hasPermission(userStore.userInfo, 'person:group:edit') ? '条件用户组' : '查看条件用户组'"
      width="1160px"
      destroy-on-close
    >
      <!-- 左列条件、右列预览：预览常驻可视，不必翻过 20 多个字段才能确认命中范围。 -->
      <div class="condition-group">
        <div class="condition-group__form">
          <ElForm label-width="100px" :disabled="saving || previewing" @change="previewed = false">
            <section class="condition-section">
              <h4 class="condition-section__title">基本信息</h4>
              <ElRow :gutter="16">
                <ElCol :span="12">
                  <ElFormItem label="组名称" required><ElInput v-model="form.groupName" placeholder="请输入用户组名称" maxlength="100" /></ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="状态">
                    <ElSelect v-model="form.status" placeholder="请选择状态">
                      <ElOption label="启用" :value="1" />
                      <ElOption label="停用" :value="0" />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
              </ElRow>
            </section>
            <section class="condition-section">
              <h4 class="condition-section__title">人员属性</h4>
              <ElRow :gutter="16">
                <ElCol :span="12">
                  <ElFormItem label="姓名/工号"><ElInput v-model="conditions.keyword" placeholder="请输入关键词" clearable maxlength="1000" /></ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="部门">
                    <ElTreeSelect
                      v-model="conditions.deptIds"
                      :data="departments"
                      node-key="deptId"
                      :props="{ label: 'deptName', children: 'children' }"
                      multiple
                      check-strictly
                      clearable
                      placeholder="全部部门（所选含下级）"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="岗位">
                    <ElSelect v-model="conditions.positionIds" multiple filterable clearable placeholder="全部岗位">
                      <ElOption v-for="item in positions" :key="item.positionId" :value="item.positionId" :label="item.positionName" />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="标签">
                    <ElSelect v-model="conditions.tagIds" multiple filterable clearable placeholder="全部标签">
                      <ElOption v-for="item in tags" :key="item.tagId" :value="item.tagId" :label="item.tagName" />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="性别">
                    <ElSelect v-model="conditions.genders" multiple clearable placeholder="全部性别">
                      <ElOption label="男" value="MALE" />
                      <ElOption label="女" value="FEMALE" />
                      <ElOption label="其他" value="OTHER" />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="学历">
                    <ElSelect
                      v-model="conditions.educations"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      clearable
                      placeholder="输入与人员特征一致的学历"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="最低年龄"><ElInputNumber v-model="conditions.minAge" :min="0" :max="150" placeholder="不限" /></ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="最高年龄"><ElInputNumber v-model="conditions.maxAge" :min="0" :max="150" placeholder="不限" /></ElFormItem>
                </ElCol>
              </ElRow>
            </section>
            <section class="condition-section">
              <h4 class="condition-section__title">培训事实</h4>
              <ElRow :gutter="16">
                <ElCol :span="12">
                  <ElFormItem label="参加培训">
                    <ElSelect v-model="conditions.trainingPlanId" filterable clearable placeholder="不限培训计划" @change="conditions.resultStatuses = []">
                      <ElOption v-for="item in plans" :key="item.trainingPlanId" :value="item.trainingPlanId" :label="item.planName" />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="培训结果">
                    <ElSelect v-model="conditions.resultStatuses" :disabled="!conditions.trainingPlanId" multiple clearable placeholder="不限结果">
                      <ElOption v-for="item in results" :key="item.value" :value="item.value" :label="item.label" />
                    </ElSelect>
                  </ElFormItem>
                </ElCol>
              </ElRow>
            </section>
            <!-- 8 个特征关键词是次要条件，默认收起；已填项数在标题上回显。 -->
            <ElCollapse v-model="advancedOpen" class="condition-advanced">
              <ElCollapseItem name="advanced">
                <template #title>
                  <span class="condition-advanced__title">更多人员特征关键词</span>
                  <ElTag v-if="advancedCount" size="small" effect="plain">{{ advancedCount }} 项已填</ElTag>
                </template>
                <ElRow :gutter="16">
                  <ElCol v-for="field in textFields" :key="field.key" :span="12">
                    <ElFormItem :label="field.label">
                      <ElInput v-model="conditions[field.key]" :placeholder="`请输入${field.label}关键词`" clearable maxlength="1000" />
                    </ElFormItem>
                  </ElCol>
                </ElRow>
              </ElCollapseItem>
            </ElCollapse>
          </ElForm>
        </div>
        <aside class="condition-group__preview">
          <div class="person-group__preview-head">
            <div>
              <strong>匹配人员预览</strong>
              <span>按当前条件实时计算；实际下发时后端会重新计算，不会沿用本次结果。</span>
            </div>
            <div class="person-group__preview-actions">
              <ElTag v-if="previewed" :type="preview.length ? 'success' : 'info'" effect="plain">匹配 {{ preview.length }} 人</ElTag>
              <ElButton type="primary" plain :loading="previewing" @click="previewMembers">预览当前匹配人员</ElButton>
            </div>
          </div>
          <div class="person-group__preview-body">
            <ElTable v-if="previewed" :data="preview" row-key="userId" height="100%">
              <ElTableColumn label="人员" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="preview-person__name">{{ row.userName }}</span>
                  <span v-if="row.jobTitle" class="preview-person__job">{{ row.jobTitle }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="userCode" label="工号" width="120" show-overflow-tooltip />
              <ElTableColumn label="特征与事实" width="110">
                <template #default="{ row }">
                  <ElButton v-if="hasPermission(userStore.userInfo, 'person:profile:list')" link type="primary" @click="openProfile(row)">
                    查看人员特征
                  </ElButton>
                </template>
              </ElTableColumn>
              <template #empty><ElEmpty description="当前条件未匹配到启用人员" :image-size="52" /></template>
            </ElTable>
            <ElEmpty v-else description="调整条件后点击预览，确认命中范围再保存" :image-size="52" />
          </div>
        </aside>
      </div>
      <template #footer>
        <ElButton @click="visible = false">关闭</ElButton>
        <ElButton v-if="hasPermission(userStore.userInfo, 'person:group:edit')" type="primary" :loading="saving" @click="save">保存用户组</ElButton>
      </template>
    </ElDialog>
    <PersonProfileDialog v-model:visible="profileVisible" :user-id="profileUser?.userId" :user-name="profileUser?.userName" />
  </section>
</template>

<style scoped lang="less">
:deep(.el-select),
:deep(.el-tree-select) {
  width: 100%;
}
/* 弹窗正文改为左右两栏，正文自身不滚动，由左栏条件区与右栏名单各自滚动。 */
:global(.condition-group-dialog .el-dialog__body) {
  padding-top: 8px;
  overflow: hidden;
}
/* 弹窗被 teleport 到 body，内部控件宽度必须用全局选择器才生效。 */
:global(.condition-group-dialog .el-select),
:global(.condition-group-dialog .el-tree-select),
:global(.condition-group-dialog .el-input),
:global(.condition-group-dialog .el-input-number) {
  width: 100%;
}
.condition-group {
  display: grid;
  min-height: 440px;
  max-height: calc(92vh - 190px);
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 20px;
}
.condition-group__form {
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding-right: 12px;
}
.condition-group__preview {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  padding-left: 20px;
  border-left: 1px solid var(--color-border);
}
/* 条件按「基本信息 / 人员属性 / 培训事实」分段，段间用细分隔线收拢。 */
.condition-section {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}
.condition-section__title {
  margin: 0 0 12px;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}
.condition-group__form :deep(.el-form-item) {
  margin-bottom: 0;
}
.condition-section :deep(.el-row) {
  row-gap: 16px;
}
/* 次要条件收进折叠区，展开前不占首屏高度。 */
.condition-advanced :deep(.el-collapse-item__header) {
  height: 42px;
  gap: 8px;
  border-bottom: 1px solid var(--color-border);
  line-height: 42px;
}
.condition-advanced :deep(.el-collapse-item__wrap),
.condition-advanced :deep(.el-collapse-item__content) {
  padding-bottom: 0;
  border-bottom: 0;
}
.condition-advanced :deep(.el-form-item){
  margin:5px 0;
}
.condition-advanced__title {
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
}
/* 条件区提示与表格之间留白，避免贴住表头。 */
.person-group__tip {
  margin-bottom: 12px;
}
/* 预览常驻右栏：头部固定、名单自己滚动。 */
.person-group__preview-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px 16px;
  margin-bottom: 10px;
}
.person-group__preview-head strong {
  display: block;
  color: var(--color-text-primary);
  font-size: 14px;
  line-height: 20px;
}
.person-group__preview-head span {
  display: block;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.person-group__preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.person-group__preview-body {
  min-height: 0;
  flex: 1 1 auto;
}
.preview-person__name {
  color: var(--color-text-primary);
}
.preview-person__job {
  margin-left: 6px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
</style>
