<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { InfoFilled } from '@element-plus/icons-vue';
import { getTrainingMatrices, saveTrainingMatrix, type TrainingMatrix, type TrainingMatrixPayload } from '@/api/training-matrix';
import { getOrgPositionPage, type OrgPosition } from '@/api/org-position';
import { getTrainingReferenceResourcePage, type TrainingReferenceResource } from '@/api/training-reference';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
/** 岗位要求管理，选择后进入现有计划工作台核对基础信息。 */
const userStore = useUserStore();
const router = useRouter();
const records = ref<TrainingMatrix[]>([]);
const selected = ref<TrainingMatrix[]>([]);
const positions = ref<OrgPosition[]>([]);
const courses = ref<TrainingReferenceResource[]>([]);
const filterPosition = ref<number>();
const rows = computed(() => records.value.filter((row) => !filterPosition.value || row.positionId === filterPosition.value));
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
/** 矩阵全量拉取，分页仅在本地切片，页码越界时回落到最后一页。 */
const visibleRows = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value;
  return rows.value.slice(start, start + pageSize.value);
});
const visible = ref(false);
const loading = ref(false);
const saving = ref(false);
const editingId = ref<number>();
const form = ref<Partial<TrainingMatrixPayload>>({ status: 1, requiredHours: 1 });
/** 下拉选项独立分页，读取全部可选记录。 */
const OPTION_PAGE_SIZE = 100;
/** 读取真实矩阵与引用选项。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    records.value = await getTrainingMatrices();
    selected.value = [];
    // 数据量变化后页码可能越界，直接回到可用范围，避免停留在空白分页。
    const maxPage = Math.max(1, Math.ceil(rows.value.length / pageSize.value));
    if (pageNum.value > maxPage) pageNum.value = maxPage;
  } finally {
    loading.value = false;
  }
}
/** 岗位和课程仅调用已有授权列表。 */
async function loadOptions(): Promise<void> {
  positions.value = [];
  courses.value = [];
  if (hasPermission(userStore.userInfo, 'org:position:list')) {
    for (let cursor = 1; ; cursor++) {
      const page = await getOrgPositionPage({ pageNum: cursor, pageSize: OPTION_PAGE_SIZE, 'query.status': 1 });
      positions.value.push(...page.list);
      if (positions.value.length >= page.total || !page.list.length) break;
    }
  }
  if (hasPermission(userStore.userInfo, 'training:plan:list')) {
    for (let cursor = 1; ; cursor++) {
      const page = await getTrainingReferenceResourcePage({ pageNum: cursor, pageSize: OPTION_PAGE_SIZE, 'query.resourceType': 'COURSE' });
      courses.value.push(...page.list);
      if (courses.value.length >= page.total || !page.list.length) break;
    }
  }
}
/** 复制字段，取消不污染已保存矩阵。 */
function open(value?: unknown): void {
  const row = value as TrainingMatrix | undefined;
  editingId.value = row?.matrixId;
  form.value = row
    ? {
        positionId: row.positionId,
        contentName: row.contentName,
        courseId: row.courseId,
        courseVersionId: row.courseVersionId,
        requiredHours: row.requiredHours,
        status: row.status,
        version: row.version
      }
    : { status: 1, requiredHours: 1 };
  visible.value = true;
}
/** 选择课程带入当前可执行版本，旧要求不自动改版。 */
function changeCourse(courseId: number): void {
  const course = courses.value.find((item) => item.resourceId === courseId);
  form.value.courseVersionId = course?.versionId ?? undefined;
  if (!form.value.contentName) form.value.contentName = course?.resourceName;
}
/** 维护要求，空值用中文提示；并发失败保留编辑草稿。 */
async function save(): Promise<void> {
  if (
    !form.value.positionId ||
    !form.value.courseId ||
    !form.value.courseVersionId ||
    !form.value.contentName?.trim() ||
    !form.value.requiredHours ||
    form.value.requiredHours <= 0
  ) {
    ElMessage.warning('请选择岗位和已发布课程，并填写培训内容及大于零的学时');
    return;
  }
  saving.value = true;
  try {
    await saveTrainingMatrix({ ...form.value, contentName: form.value.contentName.trim() } as TrainingMatrixPayload, editingId.value);
    visible.value = false;
    ElMessage.success('岗位培训要求已保存');
    await load();
  } finally {
    saving.value = false;
  }
}
/** 仅传选中要求ID，内容合并与快照由后端事务完成。 */
function createPlan(): void {
  if (!selected.value.length) {
    ElMessage.warning('请先选择岗位培训要求');
    return;
  }
  void router.push({
    path: '/admin/business-center/training/plan/create',
    query: { matrixIds: selected.value.map((row) => row.matrixId).join(',') }
  });
}
/** 表格选择只允许启用项。 */
function selectable(row: TrainingMatrix): boolean {
  return row.status === 1;
}
/** 保留明确的业务类型，不在模板推断未知行。 */
function selectRows(picked: TrainingMatrix[]): void {
  selected.value = picked;
}
function resetFilter(): void {
  filterPosition.value = undefined;
  pageNum.value = 1;
}
onMounted(async () => {
  await Promise.all([load(), loadOptions()]);
});
</script>
<template>
  <section class="admin-business-page training-matrix-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline @submit.prevent>
        <ElFormItem label="岗位">
          <ElSelect v-model="filterPosition" clearable filterable placeholder="全部岗位" @change="pageNum = 1">
            <ElOption v-for="position in positions" :key="position.positionId" :value="position.positionId" :label="position.positionName" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="load">刷新</ElButton>
          <ElButton @click="resetFilter">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>岗位培训矩阵</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="hasPermission(userStore.userInfo, 'training:plan:add')" :disabled="!selected.length" @click="createPlan">
              根据选中要求创建计划
            </ElButton>
            <ElButton v-if="hasPermission(userStore.userInfo, 'training:matrix:edit')" type="primary" @click="open()">新增培训要求</ElButton>
          </div>
        </div>
      </template>
      <ElAlert
        title="按岗位维护课程与要求学时。选中启用中的要求后可批量创建计划草稿，课程以后更新不会自动替换已保存要求。"
        type="info"
        :closable="false"
        class="training-matrix-page__tip"
      />
      <ElTable v-loading="loading" :data="visibleRows" row-key="matrixId" @selection-change="selectRows">
        <ElTableColumn type="selection" :selectable="selectable" width="48" reserve-selection />
        <ElTableColumn prop="positionName" label="岗位" min-width="120" show-overflow-tooltip />
        <ElTableColumn prop="contentName" label="培训内容" min-width="180" show-overflow-tooltip />
        <ElTableColumn prop="courseName" label="课程（课件随发布版本）" min-width="200" show-overflow-tooltip />
        <ElTableColumn prop="requiredHours" label="要求学时" width="100" />
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="hasPermission(userStore.userInfo, 'training:matrix:edit')" label="操作" width="90">
          <template #default="{ row }"><ElButton link type="primary" @click="open(row)">编辑</ElButton></template>
        </ElTableColumn>
        <template #empty><ElEmpty description="暂无岗位培训要求" /></template>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="rows.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </ElCard>
    <ElDialog
      v-model="visible"
      class="matrix-form-dialog"
      :title="editingId ? '编辑岗位培训要求' : '新增岗位培训要求'"
      width="680px"
      :close-on-click-modal="false"
    >
      <!-- 把「版本冻结」这一最关键的口径提到表单之前，替代原先堆在表单末尾的说明字段。 -->
      <p class="matrix-form__lead">
        <ElIcon class="matrix-form__lead-icon"><InfoFilled /></ElIcon>
        <span>选择启用岗位与已发布课程，保存后固定该课程的当前发布版本；课件在课程管理中维护，后续更新不会自动替换本要求。</span>
      </p>
      <ElForm label-width="92px" class="matrix-form">
        <section class="matrix-form__section">
          <h4 class="matrix-form__section-title">培训要求</h4>
          <ElRow :gutter="20">
            <ElCol :span="12">
              <ElFormItem label="岗位" required>
                <ElSelect v-model="form.positionId" filterable placeholder="请选择启用岗位">
                  <ElOption v-for="position in positions" :key="position.positionId" :value="position.positionId" :label="position.positionName" />
                </ElSelect>
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="要求学时" required>
                <ElInputNumber v-model="form.requiredHours" :min="0.01" :max="999999.99" :precision="2" class="matrix-form__hours" />
                <span class="matrix-form__unit">学时</span>
              </ElFormItem>
            </ElCol>
            <ElCol :span="24">
              <ElFormItem label="培训内容" required>
                <ElInput v-model="form.contentName" maxlength="150" placeholder="请输入培训内容" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="24">
              <ElFormItem label="课程" required>
                <ElSelect v-model="form.courseId" filterable placeholder="请选择已发布课程" @change="changeCourse">
                  <ElOption v-for="course in courses" :key="course.resourceId" :value="course.resourceId" :label="course.resourceName" />
                </ElSelect>
              </ElFormItem>
            </ElCol>
          </ElRow>
        </section>
        <section class="matrix-form__section matrix-form__section--last">
          <h4 class="matrix-form__section-title">启用状态</h4>
          <ElFormItem label="状态">
            <ElSwitch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            <span class="matrix-form__hint">停用后不会被再选中用于创建培训计划。</span>
          </ElFormItem>
        </section>
      </ElForm>
      <template #footer>
        <ElButton :disabled="saving" @click="visible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
<style scoped lang="less">
.training-matrix-page__tip {
  margin-bottom: 12px;
}
.training-matrix-page {
  :deep(.el-form-item) {
    margin-bottom: 0;
  }
  :deep(.el-select) {
    width: 100%;
  }
  :deep(.el-input-number) {
    width: 100%;
  }
}
/* 弹窗被 teleport 到 body，内部 Element Plus 规格必须用全局选择器才生效（同用户组弹窗做法）。 */
:global(.matrix-form-dialog .el-form-item) {
  margin-bottom: 0;
}
:global(.matrix-form-dialog .el-select),
:global(.matrix-form-dialog .el-input) {
  width: 100%;
}
/* 数字控件按详情页标准固定 220px，不随列宽拉伸。 */
:global(.matrix-form-dialog .matrix-form__hours) {
  width: 220px;
}
/* 把「版本冻结」口径提到表单前，替代原先堆在末尾的说明字段。 */
.matrix-form__lead {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0 0 16px;
  padding: 8px 10px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-primary) 7%, transparent);
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}
.matrix-form__lead-icon {
  flex: 0 0 auto;
  margin-top: 1px;
  color: var(--color-primary);
}
/* 表单按「培训要求 / 启用状态」分段，用细分隔线收拢字段，避免整屏平铺。 */
.matrix-form__section {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}
.matrix-form__section--last {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: 0;
}
.matrix-form__section-title {
  margin: 0 0 12px;
  color: var(--color-text-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
}
.matrix-form__section :deep(.el-row) {
  row-gap: 16px;
}
.matrix-form__unit {
  margin-left: 8px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.matrix-form__hint {
  margin-left: 12px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
</style>
