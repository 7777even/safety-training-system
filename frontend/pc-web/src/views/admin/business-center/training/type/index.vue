<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import { uploadAssetFile } from '@/api/asset';
import { createTrainingType, deleteTrainingType, getTrainingTypeCover, getTrainingTypeDetail, getTrainingTypePage, updateTrainingType } from '@/api/training-type';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { toTrainingRatio, toTrainingRatioPercent } from '@/libs/training-composition';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

/** 培训类型列表行，字段与 training.openapi.json 的 TrainingTypeVO 保持一致。 */
interface TrainingTypeRow {
  trainingTypeId: number;
  organizationId: number;
  typeCode: string;
  typeName: string;
  workType?: string;
  tag?: string;
  coverFileId?: number;
  initialHours: number;
  reviewHours: number;
  onlineMaxRatio?: number;
  status: 0 | 1;
  description: string;
  version: number;
}

/** 培训类型编辑表单；线上占比在页面按百分数填写，提交时转换为接口比例值。 */
interface TrainingTypeForm {
  trainingTypeId?: number;
  typeCode: string;
  typeName: string;
  workType: string;
  tag: string;
  coverFileId: number | undefined;
  imagePreviewUrl: string;
  uploadedImageName: string;
  uploadedImageSize: number | undefined;
  initialHours: number;
  reviewHours: number;
  onlineMaxRatio: number;
  status: 0 | 1;
  description: string;
  version?: number;
}

const userStore = useUserStore();
const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const uploadVisible = ref(false);
const formRef = ref<FormInstance>();
const rows = ref<TrainingTypeRow[]>([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const filters = reactive({
  typeName: '',
  status: undefined as 0 | 1 | undefined
});
const form = reactive<TrainingTypeForm>(emptyForm());
const editing = computed(() => form.trainingTypeId !== undefined);
const dialogTitle = computed(() => (editing.value ? '编辑培训类型' : '新增培训类型'));
const formRules: FormRules<TrainingTypeForm> = {
  typeCode: [{ required: true, message: '请输入类型编码', trigger: 'blur' }],
  typeName: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
  initialHours: [{ type: 'number', min: 1.0, message: '请输入大于 0 的初培最低学时', trigger: 'change' }],
  reviewHours: [{ type: 'number', min: 1.0, message: '请输入大于 0 的复审最低学时', trigger: 'change' }],
  description: [{ required: true, message: '请输入培训类型说明', trigger: 'blur' }]
};

/** 创建可复用的空表单，避免新增与编辑状态相互污染。 */
function emptyForm(): TrainingTypeForm {
  return {
    typeCode: '',
    typeName: '',
    workType: '',
    tag: '',
    coverFileId: undefined,
    imagePreviewUrl: '',
    uploadedImageName: '',
    uploadedImageSize: undefined,
    initialHours: 1,
    reviewHours: 1,
    onlineMaxRatio: 0,
    status: 1,
    description: ''
  };
}

/** 按后端下发的按钮权限控制操作入口。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

/** 收敛 Element Plus 表格插槽的宽泛行类型。 */
function asTrainingTypeRow(row: unknown): TrainingTypeRow {
  return row as TrainingTypeRow;
}

/** 将通用上传组件桥接到系统文件接口，图片使用通用目录。 */
function requestImageUpload(file: globalThis.File, onProgress: (percent: number) => void): Promise<UnifiedUploadedFile> {
  return uploadAssetFile(file, onProgress, 'general');
}

/** 释放上一次上传生成的本地预览地址，避免重复编辑造成 Blob URL 泄漏。 */
function clearImagePreview(): void {
  if (!form.imagePreviewUrl) return;
  globalThis.URL.revokeObjectURL(form.imagePreviewUrl);
  form.imagePreviewUrl = '';
}

/** 将系统文件主键映射为培训类型契约的 coverFileId。 */
function handleImageUploadSuccess(files: UnifiedUploadedFile[]): void {
  const fileInfo = files[0];
  if (!fileInfo) return;
  form.coverFileId = fileInfo.fileInfoId;
  clearImagePreview();
  form.imagePreviewUrl = fileInfo.sourceFile ? globalThis.URL.createObjectURL(fileInfo.sourceFile) : '';
  form.uploadedImageName = fileInfo.fileName;
  form.uploadedImageSize = fileInfo.fileSize;
  uploadVisible.value = false;
  ElMessage.success('培训类型图片上传成功');
}

/** 使用 Contract 的 query.* 参数加载分页列表。 */
async function loadRows(): Promise<void> {
  loading.value = true;
  try {
    const data = await getTrainingTypePage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(filters.typeName ? { 'query.typeName': filters.typeName } : {}),
      ...(filters.status !== undefined ? { 'query.status': filters.status } : {})
    });
    rows.value = data.list as TrainingTypeRow[];
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

/** 重置筛选并返回第一页。 */
function resetFilters(): void {
  filters.typeName = '';
  filters.status = undefined;
  pageNum.value = 1;
  void loadRows();
}

/** 打开新增面板。 */
function openCreate(): void {
  clearImagePreview();
  Object.assign(form, emptyForm());
  uploadVisible.value = false;
  dialogVisible.value = true;
}

/** 读取完整详情后打开编辑面板，避免分页数据缺失可编辑字段。 */
async function openEdit(row: TrainingTypeRow): Promise<void> {
  loading.value = true;
  try {
    const detail = await getTrainingTypeDetail(row.trainingTypeId);
    clearImagePreview();
    Object.assign(form, emptyForm(), detail, { onlineMaxRatio: toTrainingRatioPercent(detail.onlineMaxRatio), imagePreviewUrl: '' });
    if (detail.coverFileId) {
      try {
        form.imagePreviewUrl = globalThis.URL.createObjectURL(await getTrainingTypeCover(detail.trainingTypeId));
      } catch {
        // 无封面或文件不可读时保留上传入口和占位，不阻断类型编辑。
      }
    }
    uploadVisible.value = false;
    dialogVisible.value = true;
  } finally {
    loading.value = false;
  }
}

/** 提交新增或编辑，成功后回到列表上下文。 */
async function submitForm(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  submitting.value = true;
  try {
    const common = {
      typeName: form.typeName,
      workType: form.workType || undefined,
      tag: form.tag || undefined,
      coverFileId: form.coverFileId,
      initialHours: form.initialHours,
      reviewHours: form.reviewHours,
      onlineMaxRatio: toTrainingRatio(form.onlineMaxRatio),
      status: form.status,
      description: form.description
    };
    if (form.trainingTypeId !== undefined && form.version !== undefined) {
      await updateTrainingType(form.trainingTypeId, { ...common, version: form.version });
    } else {
      await createTrainingType({
        ...common,
        typeCode: form.typeCode
      });
    }
    ElMessage.success(editing.value ? '培训类型已更新' : '培训类型已创建');
    dialogVisible.value = false;
    await loadRows();
  } finally {
    submitting.value = false;
  }
}

/** 二次确认后删除培训类型。 */
async function removeRow(row: TrainingTypeRow): Promise<void> {
  await ElMessageBox.confirm(`确认删除培训类型“${row.typeName}”吗？`, '删除确认', { type: 'warning' });
  await deleteTrainingType(row.trainingTypeId);
  ElMessage.success('培训类型已删除');
  await loadRows();
}

onMounted(loadRows);
onBeforeUnmount(clearImagePreview);
</script>

<template>
  <section class="admin-business-page training-type-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline @submit.prevent>
        <ElFormItem label="类型名称">
          <ElInput v-model="filters.typeName" clearable placeholder="请输入类型名称" />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="filters.status" clearable placeholder="请选择状态">
            <ElOption label="启用" :value="1" />
            <ElOption label="停用" :value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem class="filter-actions">
          <ElButton
            type="primary"
            @click="
              pageNum = 1;
              loadRows();
            "
          >
            查询
          </ElButton>
          <ElButton @click="resetFilters">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>培训类型列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canOperate('training:type:add')" type="primary" @click="openCreate">新增类型</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="rows" row-key="trainingTypeId">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="typeCode" label="类型编码" min-width="140" />
        <ElTableColumn prop="typeName" label="类型名称" min-width="160" />
        <ElTableColumn prop="workType" label="适用工种" min-width="140" show-overflow-tooltip />
        <ElTableColumn prop="initialHours" label="初培最低学时" width="120" />
        <ElTableColumn prop="reviewHours" label="复审最低学时" width="120" />
        <ElTableColumn prop="onlineMaxRatio" label="线上占比" width="100">
          <template #default="{ row }">{{ toTrainingRatioPercent(asTrainingTypeRow(row).onlineMaxRatio) }}%</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <ElButton v-if="canOperate('training:type:edit')" link type="primary" @click="openEdit(asTrainingTypeRow(row))">编辑</ElButton>
            <ElButton v-if="canOperate('training:type:delete')" link type="danger" @click="removeRow(asTrainingTypeRow(row))">删除</ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无培训类型" />
        </template>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @change="loadRows"
        />
      </div>
    </ElCard>

    <ElDialog v-model="dialogVisible" :title="dialogTitle" width="750px" class="training-type-dialog" destroy-on-close :lock-scroll="false">
      <ElForm ref="formRef" :model="form" :rules="formRules" label-position="top" class="training-type-form">
        <section class="form-section" aria-label="培训类型基本信息">
          <div class="form-grid">
            <ElFormItem label="类型编码" prop="typeCode" required>
              <ElInput v-model="form.typeCode" :disabled="editing" placeholder="请输入类型编码" />
            </ElFormItem>
            <ElFormItem label="类型名称" prop="typeName" required>
              <ElInput v-model="form.typeName" placeholder="请输入类型名称" />
            </ElFormItem>
            <ElFormItem label="适用工种">
              <ElInput v-model="form.workType" placeholder="请输入适用工种" />
            </ElFormItem>
            <ElFormItem label="类型标签">
              <ElInput v-model="form.tag" placeholder="请输入培训类型标签" />
            </ElFormItem>
            <ElFormItem label="类型图片" class="form-grid__full">
              <div class="image-upload-field">
                <div v-if="form.imagePreviewUrl" class="image-preview" aria-label="培训类型图片预览">
                  <img :src="form.imagePreviewUrl" alt="培训类型图片预览">
                </div>
                <ElButton v-if="canOperate('sys:file:upload')" @click="uploadVisible = true">
                  {{ form.coverFileId ? '重新上传图片' : '选择并上传图片' }}
                </ElButton>
                <ElText v-else type="info">当前账号没有文件上传权限</ElText>
                <span v-if="form.uploadedImageName" class="uploaded-file-info">
                  {{ form.uploadedImageName }}（{{ Math.ceil((form.uploadedImageSize ?? 0) / 1024) }} KB）
                </span>
                <ElTag v-else-if="form.coverFileId" type="success">已关联图片</ElTag>
              </div>
            </ElFormItem>
          </div>
        </section>

        <section class="training-baseline" aria-labelledby="training-baseline-title">
          <div class="training-baseline__heading">
            <div>
              <h3 id="training-baseline-title">培训基线</h3>
            </div>
          </div>
          <div class="training-baseline__grid">
            <ElFormItem label="初培最低学时" prop="initialHours" required>
              <ElInputNumber v-model="form.initialHours" :min="0.1" :precision="1" />
            </ElFormItem>
            <ElFormItem label="复审最低学时" prop="reviewHours" required>
              <ElInputNumber v-model="form.reviewHours" :min="0.1" :precision="1" />
            </ElFormItem>
            <ElFormItem label="线上学时占比上限">
              <ElInputNumber v-model="form.onlineMaxRatio" :min="0" :max="100" :step="1" :precision="1" />
              <p class="field-hint">填写百分数，例如 30 表示线上学时最高占 30%。</p>
            </ElFormItem>
          </div>
        </section>

        <section class="form-section form-section--settings" aria-label="培训类型其他设置">
          <ElFormItem label="状态">
            <ElRadioGroup v-model="form.status">
              <ElRadio :value="1">启用</ElRadio>
              <ElRadio :value="0">停用</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="说明" prop="description" required>
            <ElInput v-model="form.description" type="textarea" :rows="2" placeholder="请输入培训类型说明" />
          </ElFormItem>
        </section>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="submitting" @click="submitForm">保存</ElButton>
      </template>
    </ElDialog>
    <UnifiedFileUpload
      v-model="uploadVisible"
      mode="dialog"
      accept="image/*"
      :multiple="false"
      :limit="1"
      :upload-request="requestImageUpload"
      @success="handleImageUploadSuccess"
    />
  </section>
</template>

<style scoped lang="less">
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 22px;
}

.training-type-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 16px 20px 0;
}

.training-type-dialog :deep(.el-dialog__body) {
  padding: 14px 20px 8px;
}

.training-type-dialog :deep(.el-dialog__footer) {
  padding: 10px 20px 16px;
}

.training-type-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.training-type-form :deep(.el-form-item__label) {
  height: auto;
  padding-bottom: 6px;
  color: var(--color-text-regular);
  font-size: 13px;
  line-height: 20px;
}

.form-section {
  padding-bottom: 6px;
}

.form-grid__full {
  grid-column: 1 / -1;
}

.training-baseline {
  margin: 2px 0 10px;
  padding: 10px 0 0;
  border-top: 1px solid var(--color-border);
}

.training-baseline__heading {
  margin-bottom: 8px;
}

.training-baseline__heading h3,
.training-baseline__heading p {
  margin: 0;
}

.training-baseline__heading h3 {
  color: var(--color-text-primary);
  font-size: 14px;
}

.training-baseline__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 18px;
}

.training-baseline__grid :deep(.el-form-item) {
  margin-bottom: 8px;
}

.training-baseline__grid :deep(.el-input-number) {
  width: 100%;
}

.form-section--settings {
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
}

.image-upload-field {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.image-preview {
  display: grid;
  width: 72px;
  height: 52px;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
}

.image-preview img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-preview-hint {
  width: 100%;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.uploaded-file-info {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.field-hint {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

@media (max-width: 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .training-baseline__grid {
    grid-template-columns: 1fr;
  }

  .training-baseline__grid :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  .training-baseline__grid :deep(.el-form-item:last-child) {
    margin-bottom: 0;
  }
}
</style>
