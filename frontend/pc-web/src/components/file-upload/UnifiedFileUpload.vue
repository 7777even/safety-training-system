<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { ElDialog, ElMessage, ElOption, ElSelect } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import AssetViewer from '@/components/asset-viewer/AssetViewer.vue';
import { releaseUploadQueueItems, removeUploadQueueItem, uploadQueueItem as runUploadQueueItem, type UploadQueueState } from './upload-queue';

export interface UnifiedUploadedFile {
  fileInfoId: number;
  fileName: string;
  fileSize: number;
  fileSuffix?: string;
  fileUrl?: string;
  /** 保留本地 File，供业务层读取 MIME、页数和时长等元数据。 */
  sourceFile?: globalThis.File;
}

interface UnifiedFileUploadProps {
  modelValue?: boolean;
  mode: 'dialog' | 'panel';
  multiple?: boolean;
  accept?: string;
  limit?: number;
  /** 原始文件字节上限；不传则不按体积拦截。 */
  maxFileSize?: number;
  /** 超过体积上限时的中文提示。 */
  sizeLimitHint?: string;
  /** 可选的业务类型确认项；不传时保持原有上传组件行为。 */
  typeOptions?: ReadonlyArray<{ label: string; value: string }>;
  initialType?: string;
  typeUploadConfig?: Record<string, {
    accept?: string;
    maxFileSize?: number;
    sizeLimitHint?: string;
  }>;
  uploadRequest: (file: globalThis.File, onProgress: (percent: number) => void, signal: globalThis.AbortSignal) => Promise<UnifiedUploadedFile>;
  cancelRequest?: (file: globalThis.File) => Promise<void>;
}

type UploadQueueItem = UploadQueueState<globalThis.File, UnifiedUploadedFile>;

const props = withDefaults(defineProps<UnifiedFileUploadProps>(), {
  modelValue: false,
  multiple: false,
  accept: '',
  limit: 10,
  maxFileSize: undefined,
  sizeLimitHint: undefined,
  typeOptions: undefined,
  initialType: undefined,
  typeUploadConfig: undefined,
  cancelRequest: undefined
});
const emit = defineEmits<{
  'update:modelValue': [visible: boolean];
  success: [files: UnifiedUploadedFile[], type?: string];
  'type-change': [type: string];
  back: [];
}>();

const fileInput = ref<globalThis.HTMLInputElement>();
const queue = ref<UploadQueueItem[]>([]);
const activeItemId = ref('');
const dragging = ref(false);
const isUploading = ref(false);
const isUploaded = ref(false);
const uploadControllers = new Map<string, globalThis.AbortController>();
let uploadSession = 0;
let pendingUploadCount = 0;
const containerComponent = computed(() => (props.mode === 'dialog' ? ElDialog : 'section'));
const selectedType = ref(props.initialType ?? '');
const selectedTypeConfig = computed(() => (selectedType.value ? props.typeUploadConfig?.[selectedType.value] : undefined));
const effectiveAccept = computed(() => selectedTypeConfig.value?.accept ?? props.accept);
const effectiveMaxFileSize = computed(() => selectedTypeConfig.value?.maxFileSize ?? props.maxFileSize);
const effectiveSizeLimitHint = computed(() => selectedTypeConfig.value?.sizeLimitHint ?? props.sizeLimitHint);
const typeSelectionEnabled = computed(() => (props.typeOptions?.length ?? 0) > 0);
const canUploadSelectedType = computed(() => !typeSelectionEnabled.value || Boolean(selectedTypeConfig.value?.accept));
const activeItem = computed(() => queue.value.find((item) => item.id === activeItemId.value) ?? queue.value[0]);
const successfulFiles = computed(() => queue.value.flatMap((item) => (item.result ? [item.result] : [])));

watch(() => props.modelValue, (visible) => {
  if (visible && props.mode === 'dialog') selectedType.value = props.initialType ?? '';
});

function handleTypeChange(type: string): void {
  if (isUploading.value || isUploaded.value) return;
  selectedType.value = type;
  emit('type-change', type);
}

function openFilePicker(): void {
  if (!canUploadSelectedType.value) return;
  fileInput.value?.click();
}

/** 为浏览器支持的本地文件创建临时预览地址。 */
function createQueueItem(file: globalThis.File): UploadQueueItem {
  return {
    id: `${file.name}-${file.size}-${file.lastModified}-${Math.random()}`,
    file,
    status: 'waiting',
    percent: 0,
    previewUrl: globalThis.URL.createObjectURL(file)
  };
}

function isOversized(file: globalThis.File): boolean {
  return effectiveMaxFileSize.value !== undefined && file.size > effectiveMaxFileSize.value;
}

async function addFiles(files: globalThis.File[]): Promise<void> {
  if (files.length === 0) return;
  // 单文件模式先清空再入队，避免上次成功项占满 limit 后无法重选。
  if (!props.multiple) {
    const file = files[0];
    if (isOversized(file)) {
      ElMessage.warning(effectiveSizeLimitHint.value || '原始文件超过允许大小');
      return;
    }
    resetQueue(true);
    queue.value.push(createQueueItem(file));
    const item = queue.value[0];
    if (!item) return;
    activeItemId.value = item.id;
    await uploadItem(item);
    return;
  }
  const available = Math.max(0, props.limit - queue.value.length);
  if (available === 0) {
    ElMessage.warning(`最多上传 ${props.limit} 个文件`);
    return;
  }
  const acceptedFiles = files.slice(0, available).filter((file) => {
    if (!isOversized(file)) return true;
    ElMessage.warning(effectiveSizeLimitHint.value || '原始文件超过允许大小');
    return false;
  });
  if (acceptedFiles.length === 0) return;
  const items = acceptedFiles.map(createQueueItem);
  queue.value.push(...items);
  activeItemId.value = items[0]?.id ?? activeItemId.value;
  // 入队后必须操作 Vue 已代理的对象，确保最终成功或失败状态能立即刷新到界面。
  const queuedItems = queue.value.filter((item) => items.some((candidate) => candidate.id === item.id));
  await Promise.all(queuedItems.map(uploadItem));
}

async function uploadItem(item: UploadQueueItem): Promise<void> {
  const session = uploadSession;
  pendingUploadCount += 1;
  isUploading.value = true;
  const controller = new globalThis.AbortController();
  uploadControllers.set(item.id, controller);
  try {
    await runUploadQueueItem(item, props.uploadRequest, controller.signal);
    if (item.status === 'success' && item.result) {
      item.result.sourceFile = item.file;
      if (session === uploadSession) isUploaded.value = true;
    }
  } finally {
    uploadControllers.delete(item.id);
    if (session === uploadSession) {
      pendingUploadCount -= 1;
      isUploading.value = pendingUploadCount > 0;
    }
  }
}

/** 暂停当前上传请求，multipart 会话保留在服务端，重试时可恢复。 */
function pauseItem(item: UploadQueueItem): void {
  uploadControllers.get(item.id)?.abort();
}

/** 取消当前上传并清理 multipart 会话；小文件请求则直接中止。 */
async function removeItem(item: UploadQueueItem): Promise<void> {
  uploadControllers.get(item.id)?.abort();
  await props.cancelRequest?.(item.file);
  queue.value = removeUploadQueueItem(queue.value, item.id, globalThis.URL.revokeObjectURL);
  if (activeItemId.value === item.id) activeItemId.value = queue.value[0]?.id ?? '';
  if (queue.value.length === 0) {
    uploadSession += 1;
    isUploading.value = false;
    isUploaded.value = false;
    pendingUploadCount = 0;
  }
}

/** 清空本地队列；cancelUploads 时只取消尚未成功的会话，避免误删已完成文件。 */
function resetQueue(cancelUploads: boolean): void {
  uploadSession += 1;
  pendingUploadCount = 0;
  isUploading.value = false;
  isUploaded.value = false;
  queue.value.forEach((item) => {
    uploadControllers.get(item.id)?.abort();
    if (cancelUploads && item.status !== 'success') void props.cancelRequest?.(item.file);
  });
  releaseUploadQueueItems(queue.value, globalThis.URL.revokeObjectURL);
  queue.value = [];
  activeItemId.value = '';
}

function clearQueue(): void {
  resetQueue(true);
}

function onInputChange(event: globalThis.Event): void {
  const input = event.target as globalThis.HTMLInputElement;
  void addFiles(Array.from(input.files ?? []));
  input.value = '';
}

function onDrop(event: globalThis.DragEvent): void {
  dragging.value = false;
  void addFiles(Array.from(event.dataTransfer?.files ?? []));
}

/** 关闭公共上传弹窗时清理队列，避免下次打开残留上一次文件。 */
function closeDialog(): void {
  clearQueue();
  emit('update:modelValue', false);
}

function finish(): void {
  if (successfulFiles.value.length === 0) {
    ElMessage.warning('请先完成文件上传');
    return;
  }
  emit('success', successfulFiles.value, selectedType.value || undefined);
  if (props.mode === 'dialog') {
    resetQueue(false);
    emit('update:modelValue', false);
  } else emit('back');
}

onBeforeUnmount(() => resetQueue(false));
</script>

<template>
  <component
    :is="containerComponent"
    :model-value="mode === 'dialog' ? modelValue : true"
    :title="mode === 'dialog' ? '上传文件' : undefined"
    :width="mode === 'dialog' ? 'min(1040px, calc(100vw - 32px))' : undefined"
    :top="mode === 'dialog' ? '8vh' : undefined"
    :lock-scroll="mode === 'dialog' ? false : undefined"
    :close-on-click-modal="false"
    :class="['unified-file-upload', `is-${mode}`]"
    @update:model-value="$event ? emit('update:modelValue', true) : closeDialog()"
  >
    <div v-if="typeSelectionEnabled" class="upload-type-field">
      <label for="unified-upload-type">素材类型</label>
      <ElSelect id="unified-upload-type" :model-value="selectedType" :disabled="isUploading || isUploaded" placeholder="请选择素材类型" @update:model-value="handleTypeChange">
        <ElOption v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
      </ElSelect>
      <span v-if="effectiveSizeLimitHint" class="upload-type-hint">{{ effectiveSizeLimitHint }}</span>
      <span v-else class="upload-type-hint">当前类型不涉及文件上传</span>
    </div>
    <header v-if="mode === 'panel'" class="panel-header">
      <div>
        <h3>上传文件</h3>
        <p>完成后返回素材信息，不会打开第二层弹窗。</p>
      </div>
      <ElButton @click="emit('back')">返回素材信息</ElButton>
    </header>

    <button
      type="button"
      class="drop-zone"
      :class="{ 'is-dragging': dragging }"
      @click="openFilePicker"
      @dragenter.prevent="dragging = true"
      @dragover.prevent
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <ElIcon :size="40"><UploadFilled /></ElIcon>
      <strong>将文件拖到此处，或点击选择文件</strong>
      <span>{{ multiple ? `一次最多选择 ${limit} 个文件` : '请选择一个文件' }}</span>
      <span v-if="effectiveSizeLimitHint">{{ effectiveSizeLimitHint }}</span>
    </button>
    <input ref="fileInput" class="visually-hidden" type="file" :multiple="multiple" :accept="effectiveAccept" @change="onInputChange">

    <div v-if="multiple || activeItem" class="upload-body">
      <section :class="multiple ? 'queue-panel' : 'single-file-panel'">
        <div v-if="multiple" class="queue-heading">
          <strong>上传队列</strong>
          <span>{{ queue.length }} 个文件</span>
        </div>
        <strong v-else class="single-file-heading">文件信息</strong>
        <ElEmpty v-if="multiple && queue.length === 0" description="暂无文件" :image-size="56" />
        <div v-for="item in queue" :key="item.id" class="queue-item" :class="{ active: activeItem?.id === item.id }">
          <button type="button" class="queue-select" @click="activeItemId = item.id">
            <span class="queue-file">
              <ElTooltip :content="item.file.name" placement="top" :show-after="300" :hide-after="0">
                <strong class="queue-file__name">{{ item.file.name }}</strong>
              </ElTooltip>
              <span>
                {{ Math.ceil(item.file.size / 1024) }} KB ·
                {{
                  item.status === 'success'
                    ? '上传成功'
                    : item.status === 'failed'
                      ? '上传失败'
                      : item.status === 'paused'
                        ? '已暂停'
                        : item.status === 'waiting'
                          ? '等待上传'
                          : '上传中'
                }}
              </span>
            </span>
            <ElProgress
              :percentage="item.percent"
              :status="item.status === 'failed' ? 'exception' : item.status === 'success' ? 'success' : undefined"
            />
          </button>
          <div class="queue-actions">
            <ElButton v-if="item.status === 'uploading'" link type="warning" @click.stop="pauseItem(item)">暂停</ElButton>
            <ElButton v-if="item.status === 'paused'" link type="primary" @click.stop="uploadItem(item)">继续</ElButton>
            <ElButton v-if="item.status === 'failed'" link type="primary" @click.stop="uploadItem(item)">重试</ElButton>
            <ElButton link type="danger" @click.stop="void removeItem(item)">取消</ElButton>
          </div>
        </div>
      </section>

      <aside class="local-preview" aria-label="上传文件预览">
        <strong>文件预览</strong>
        <ElEmpty v-if="!activeItem" description="选择文件后预览" :image-size="56" />
        <template v-else>
          <AssetViewer
            v-if="activeItem.file"
            :local-file="activeItem.file"
            preview-height="360px"
          />
        </template>
      </aside>
    </div>

    <footer class="upload-footer">
      <ElButton v-if="mode === 'dialog'" @click="closeDialog">取消</ElButton>
      <ElButton v-else @click="emit('back')">返回</ElButton>
      <ElButton type="primary" @click="finish">完成</ElButton>
    </footer>
  </component>
</template>

<style scoped lang="less">
.unified-file-upload.is-panel {
  display: block;
}

.unified-file-upload.is-dialog {
  display: flex;
  max-height: calc(100vh - 32px);
  flex-direction: column;
}

.unified-file-upload.is-dialog :deep(.el-dialog__body) {
  min-height: 0;
  overflow-y: auto;
}
.upload-type-field {
  display: grid;
  grid-template-columns: auto minmax(180px, 280px) 1fr;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.upload-type-field label {
  color: var(--color-text-primary);
  font-size: 14px;
}
.upload-type-hint {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.panel-header,
.queue-heading,
.upload-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-header {
  margin-bottom: 16px;
}
.panel-header h3 {
  margin: 0;
}
.panel-header p {
  margin: 5px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.drop-zone {
  display: grid;
  width: 100%;
  min-height: 170px;
  place-items: center;
  align-content: center;
  gap: 8px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-subtle);
  color: var(--color-text-regular);
  cursor: pointer;
}
.drop-zone:hover,
.drop-zone.is-dragging {
  border-color: var(--color-primary);
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}
.drop-zone span,
.queue-heading span,
.queue-item span {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
.upload-body {
  display: grid;
  height: 420px;
  min-height: 0;
  margin-top: 14px;
  grid-template-columns: minmax(320px, 2fr) minmax(520px, 3fr);
  grid-template-rows: minmax(0, 1fr);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  overflow: hidden;
}
.queue-panel,
.single-file-panel,
.local-preview {
  min-width: 0;
  min-height: 0;
  padding: 14px;
}

.queue-panel,
.single-file-panel {
  overflow-x: hidden;
  overflow-y: auto;
}

.single-file-heading {
  display: block;
}
.queue-item {
  display: grid;
  width: 100%;
  min-width: 0;
  gap: 8px;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: #fff;
  cursor: pointer;
  text-align: left;
}
.queue-select {
  display: grid;
  width: 100%;
  min-width: 0;
  gap: 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}
/*
 * grid/flex 子项默认 `min-width: auto`，会被 `white-space: nowrap` 的长文件名按全文宽度撑开，
 * 把面板顶出横向滚动条；显式归零后才能让省略号真正生效。
 */
.queue-item > *,
.queue-select > * {
  min-width: 0;
}
.queue-item.active {
  border-color: var(--color-primary);
}
.queue-file {
  display: block;
  min-width: 0;
}
.queue-file strong,
.queue-file span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.queue-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.local-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-left: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
  overflow: hidden;
}

.local-preview :deep(.asset-viewer) {
  width: 100%;
  height: 360px;
  min-height: 0;
  margin-top: 0;
  padding: 0;
  border-radius: var(--radius-control);
  background: #fff;
  overflow: hidden;
}

.local-preview :deep(.viewer-state) {
  box-sizing: border-box;
  min-height: 0;
  height: 100%;
  padding: 20px;
}

.local-preview :deep(.media-video) {
  height: 100%;
  max-height: 360px;
}
.upload-footer {
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}

@media (max-width: 1100px) {
  .upload-body {
    height: auto;
    min-height: 840px;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  }

  .local-preview {
    border-top: 1px solid var(--color-border);
    border-left: 0;
  }
}
</style>
