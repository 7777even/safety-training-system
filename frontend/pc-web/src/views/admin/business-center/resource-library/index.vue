<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { QuillEditor } from '@vueup/vue-quill';
import '@vueup/vue-quill/dist/vue-quill.snow.css';
import * as pdfjsLib from 'pdfjs-dist';
import pdfWorker from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import {
  type AssetCreateRequest,
  type AssetDetail,
  type AssetListItem,
  type AssetPlayInfo,
  type AssetProcessStatus,
  type AssetSourceType,
  type AssetStatus,
  type AssetTranscodeJob,
  type AssetType,
  cancelAssetUpload,
  createAsset,
  deleteAsset,
  getAssetDetail,
  getAssetPage,
  getAssetPlayInfo,
  getAssetTranscodeJobs,
  retryAssetTranscode,
  updateAsset,
  uploadAssetFile
} from '@/api/asset';
import { formatDuration } from '@/utils/duration';
import {
  ASSET_ORIGINAL_FILE_MAX_BYTES,
  ASSET_PROCESS_STATUS_OPTIONS,
  ASSET_TYPE_OPTIONS,
  ASSET_UPLOAD_ACCEPT,
  ASSET_UPLOAD_TYPE_CONFIG,
  ASSET_UPLOAD_TYPE_OPTIONS,
  canRetryAssetTranscode,
  getAssetOriginalFileSizeError,
  getAssetOriginalFileSizeHint,
  getAssetProcessStatusLabel,
  getAssetStatusLabel,
  getAssetTypeLabel,
  TRANSCODE_JOB_STATUS_LABELS,
  TRANSCODE_JOB_TYPE_LABELS
} from '@/constants/asset';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import AssetViewer from '@/components/asset-viewer/AssetViewer.vue';

const userStore = useUserStore();
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);

/** 素材列表查询条件。 */
const filters = reactive({
  assetName: '',
  assetType: undefined as AssetType | undefined,
  processStatus: undefined as AssetProcessStatus | undefined,
  status: undefined as AssetStatus | undefined
});
const assetList = ref<AssetListItem[]>([]);
const total = ref(0);
const pageNum = ref(1);
const loading = ref(false);

/** 素材创建与编辑表单。 */
interface AssetForm {
  assetId?: number;
  assetCode: string;
  assetName: string;
  assetType: AssetType;
  sourceType: AssetSourceType;
  fileInfoId?: number;
  uploadedFileName: string;
  externalUrl: string;
  richContent: string;
  mimeType: string;
  fileSize?: number;
  pageCount?: number;
  durationSeconds?: number;
  checksum: string;
  status: AssetStatus;
  version?: number;
}

const drawerVisible = ref(false);
const uploadVisible = ref(false);
const uploadAssetType = ref<AssetType>('DOCUMENT');
const formLoading = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<AssetForm>(newAssetForm());
const formRules: FormRules<AssetForm> = {
  assetCode: [{ required: true, message: '请输入素材编码', trigger: 'blur' }],
  assetName: [{ required: true, message: '请输入素材名称', trigger: 'blur' }],
  assetType: [{ required: true, message: '请选择素材类型', trigger: 'change' }],
  sourceType: [{ required: true, message: '请选择素材来源', trigger: 'change' }]
};

const jobsDrawerVisible = ref(false);
const jobsLoading = ref(false);
const currentAssetName = ref('');
const transcodeJobs = ref<AssetTranscodeJob[]>([]);
const previewDrawerVisible = ref(false);
const previewAsset = ref<AssetListItem | null>(null);
const previewInfo = ref<AssetPlayInfo | null>(null);
const previewDetail = ref<AssetDetail | null>(null);
const previewLoading = ref(false);
const previewProcessStatus = ref<AssetProcessStatus | null>(null);
let processStatusTimer: number | undefined;
let previewProcessTimer: number | undefined;

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorker;

const ASSET_SOURCE_OPTIONS: ReadonlyArray<{ label: string; value: AssetSourceType }> = [
  { label: '文件上传', value: 'UPLOAD' },
  { label: '在线创建', value: 'CREATED' },
  { label: '外部地址', value: 'EXTERNAL' }
];

/** 各素材类型允许的来源；来源收敛为单选，避免类型与来源产生无效组合。 */
const ASSET_SOURCE_BY_TYPE: Record<AssetType, AssetSourceType> = {
  DOCUMENT: 'UPLOAD',
  IMAGE: 'UPLOAD',
  AUDIO: 'UPLOAD',
  VIDEO: 'UPLOAD',
  RICH_TEXT: 'CREATED',
  LINK: 'EXTERNAL',
  LIVE: 'EXTERNAL'
};

const RICH_TEXT_TOOLBAR = [
  ['bold', 'italic', 'underline', 'strike'],
  [{ header: [1, 2, 3, false] }],
  [{ list: 'ordered' }, { list: 'bullet' }],
  [{ color: [] }, { background: [] }],
  ['link', 'clean']
];

const sourceOptions = computed(() => ASSET_SOURCE_OPTIONS.filter((item) => item.value === ASSET_SOURCE_BY_TYPE[form.assetType]));
const uploadAccept = computed(() => ASSET_UPLOAD_ACCEPT[form.assetType]);
const originalFileMaxBytes = computed(() => ASSET_ORIGINAL_FILE_MAX_BYTES[form.assetType]);
const originalFileSizeHint = computed(() => getAssetOriginalFileSizeHint(form.assetType));
/** 上传弹窗的类型候选与白名单/体积上限统一取自素材域常量，避免与后端 6025 校验口径不一致。 */
const uploadTypeOptions = ASSET_UPLOAD_TYPE_OPTIONS;
const uploadTypeConfig = ASSET_UPLOAD_TYPE_CONFIG;

function newAssetForm(): AssetForm {
  return {
    assetId: undefined,
    assetCode: '',
    assetName: '',
    assetType: 'DOCUMENT',
    sourceType: 'UPLOAD',
    fileInfoId: undefined,
    uploadedFileName: '',
    externalUrl: '',
    richContent: '',
    mimeType: '',
    fileSize: undefined,
    pageCount: undefined,
    durationSeconds: undefined,
    checksum: '',
    status: 1,
    version: undefined
  };
}

/** 前端按钮仅按 profile 菜单权限显示，真实鉴权仍由后端完成。 */
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

function asAsset(row: unknown): AssetListItem {
  return row as AssetListItem;
}

/** 将上传结果的字节数转换为便于表单回显的文件大小。 */
function formatFileSize(fileSize?: number): string {
  if (fileSize === undefined) return '';
  if (fileSize < 1024 * 1024) return `${Math.ceil(fileSize / 1024)} KB`;
  return `${(fileSize / 1024 / 1024).toFixed(1)} MB`;
}

/** 根据文件扩展名补齐部分浏览器无法识别的 MIME 类型。 */
function inferMimeType(file: globalThis.File): string {
  if (file.type) return file.type;
  const suffix = file.name.split('.').pop()?.toLowerCase();
  const mimeTypes: Record<string, string> = {
    pdf: 'application/pdf',
    doc: 'application/msword',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    xls: 'application/vnd.ms-excel',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    ppt: 'application/vnd.ms-powerpoint',
    pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    mp3: 'audio/mpeg',
    mp4: 'video/mp4'
  };
  return (suffix && mimeTypes[suffix]) || 'application/octet-stream';
}

/** 读取音视频元数据中的播放时长，并释放临时对象地址。 */
async function readMediaDuration(file: globalThis.File, kind: 'audio' | 'video'): Promise<number | undefined> {
  const media = globalThis.document.createElement(kind);
  const objectUrl = globalThis.URL.createObjectURL(file);
  return new Promise((resolve) => {
    const cleanup = (): void => globalThis.URL.revokeObjectURL(objectUrl);
    media.onloadedmetadata = () => {
      cleanup();
      resolve(Number.isFinite(media.duration) ? Math.round(media.duration) : undefined);
    };
    media.onerror = () => {
      cleanup();
      resolve(undefined);
    };
    media.src = objectUrl;
    media.load();
  });
}

/** 根据上传文件自动回填 MIME、文件大小、PDF 页数和音视频时长。 */
async function fillUploadedMetadata(file: globalThis.File): Promise<void> {
  form.mimeType = inferMimeType(file);
  form.fileSize = file.size;
  form.pageCount = undefined;
  form.durationSeconds = undefined;
  try {
    if (form.assetType === 'DOCUMENT' && form.mimeType === 'application/pdf') {
      const pdf = await pdfjsLib.getDocument({ data: await file.arrayBuffer() }).promise;
      form.pageCount = pdf.numPages;
      await pdf.cleanup();
    } else if (form.assetType === 'AUDIO') {
      form.durationSeconds = await readMediaDuration(file, 'audio');
    } else if (form.assetType === 'VIDEO') {
      form.durationSeconds = await readMediaDuration(file, 'video');
    }
  } catch {
    ElMessage.info('文件已上传，但部分元数据暂时无法从浏览器读取，将由后端处理结果补齐。');
  }
}

/** 清除已上传文件，解锁素材类型以便重新选择。 */
function clearUploadedFile(): void {
  form.fileInfoId = undefined;
  form.uploadedFileName = '';
  form.mimeType = '';
  form.fileSize = undefined;
  form.pageCount = undefined;
  form.durationSeconds = undefined;
  form.checksum = '';
}

/** 切换素材类型时自动收敛来源，并清理上一类型残留的文件、地址或正文。 */
function handleAssetTypeChange(assetType: AssetType): void {
  form.sourceType = ASSET_SOURCE_BY_TYPE[assetType];
  clearUploadedFile();
  form.externalUrl = '';
  form.richContent = '';
}

/** 按 OpenAPI 的 query.* 参数名加载素材分页。 */
async function loadAssets(): Promise<void> {
  loading.value = true;
  try {
    const data = await getAssetPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(filters.assetName ? { 'query.assetName': filters.assetName } : {}),
      ...(filters.assetType ? { 'query.assetType': filters.assetType } : {}),
      ...(filters.processStatus ? { 'query.processStatus': filters.processStatus } : {}),
      ...(filters.status !== undefined ? { 'query.status': filters.status } : {})
    });
    assetList.value = data.list;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

/** 处理中的素材自动刷新，避免上传成功后页面仍停留在旧状态。 */
function startProcessStatusPolling(): void {
  processStatusTimer = globalThis.setInterval(() => {
    if (assetList.value.some((item) => item.processStatus === 'UPLOADED' || item.processStatus === 'PROCESSING')) {
      void loadAssets();
    }
  }, 3000);
}

async function searchAssets(): Promise<void> {
  pageNum.value = 1;
  await loadAssets();
}

async function resetFilters(): Promise<void> {
  Object.assign(filters, { assetName: '', assetType: undefined, processStatus: undefined, status: undefined });
  await searchAssets();
}

function openCreate(): void {
  Object.assign(form, newAssetForm());
  uploadVisible.value = false;
  drawerVisible.value = true;
}

function openUpload(): void {
  uploadAssetType.value = form.assetType;
  uploadVisible.value = true;
}

function handleUploadTypeChange(assetType: string): void {
  if (assetType in ASSET_SOURCE_BY_TYPE) uploadAssetType.value = assetType as AssetType;
}

/** 抽屉完全关闭后恢复干净表单，避免上一次上传状态影响下次新增。 */
function handleDrawerClosed(): void {
  uploadVisible.value = false;
  Object.assign(form, newAssetForm());
  formRef.value?.clearValidate();
}

/** 编辑前读取详情，确保提交后端最新乐观锁版本。 */
async function openEdit(item: AssetListItem): Promise<void> {
  formLoading.value = true;
  try {
    const detail = await getAssetDetail(item.assetId);
    Object.assign(form, newAssetForm(), detail, {
      uploadedFileName: detail.fileInfoId ? `文件记录 #${detail.fileInfoId}` : '',
      externalUrl: detail.externalUrl ?? '',
      richContent: detail.richContent ?? '',
      mimeType: detail.mimeType ?? '',
      checksum: detail.checksum ?? ''
    });
    uploadVisible.value = false;
    drawerVisible.value = true;
  } finally {
    formLoading.value = false;
  }
}

/** 将通用上传组件的请求桥接到系统文件 Contract。 */
function requestFileUpload(
  file: globalThis.File,
  onProgress: (percent: number) => void,
  signal: globalThis.AbortSignal
): Promise<UnifiedUploadedFile> {
  const sizeError = getAssetOriginalFileSizeError(uploadAssetType.value, file.size);
  if (sizeError) return Promise.reject(new Error(sizeError));
  return uploadAssetFile(file, onProgress, 'asset', signal);
}

/** 记录本次上传结果并返回素材信息表单。 */
async function handleUploadSuccess(files: UnifiedUploadedFile[], assetType?: string): Promise<void> {
  const fileInfo = files[0];
  if (!fileInfo) return;
  if (assetType && assetType in ASSET_SOURCE_BY_TYPE) {
    form.assetType = assetType as AssetType;
    form.sourceType = ASSET_SOURCE_BY_TYPE[form.assetType];
  }
  form.fileInfoId = fileInfo.fileInfoId;
  form.uploadedFileName = fileInfo.fileName;
  form.fileSize = fileInfo.fileSize;
  ElMessage.success('文件上传成功');
  if (fileInfo.sourceFile) await fillUploadedMetadata(fileInfo.sourceFile);
}

/** 校验来源类型对应的 Contract 必填字段。 */
function validateSourceFields(): boolean {
  if (form.sourceType === 'UPLOAD' && form.fileInfoId === undefined) {
    ElMessage.warning('请先上传素材文件');
    return false;
  }
  if (form.sourceType === 'EXTERNAL' && !/^https?:\/\//i.test(form.externalUrl)) {
    ElMessage.warning('请输入以 http:// 或 https:// 开头的外部地址');
    return false;
  }
  const plainRichContent = form.richContent
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim();
  if (form.sourceType === 'CREATED' && !plainRichContent) {
    ElMessage.warning('请输入富文本内容');
    return false;
  }
  return true;
}

/** 构造创建请求，只发送 Contract 定义且与当前来源相关的字段。 */
function createRequestBody(): AssetCreateRequest {
  return {
    assetCode: form.assetCode,
    assetName: form.assetName,
    assetType: form.assetType,
    sourceType: form.sourceType,
    ...(form.sourceType === 'UPLOAD' ? { fileInfoId: form.fileInfoId } : {}),
    ...(form.sourceType === 'EXTERNAL' ? { externalUrl: form.externalUrl } : {}),
    ...(form.sourceType === 'CREATED' ? { richContent: form.richContent } : {}),
    ...(form.mimeType ? { mimeType: form.mimeType } : {}),
    ...(form.fileSize !== undefined ? { fileSize: form.fileSize } : {}),
    ...(form.pageCount !== undefined ? { pageCount: form.pageCount } : {}),
    ...(form.durationSeconds !== undefined ? { durationSeconds: form.durationSeconds } : {}),
    ...(form.checksum ? { checksum: form.checksum } : {}),
    status: form.status
  };
}

async function saveAsset(): Promise<void> {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  if (form.assetId === undefined && !validateSourceFields()) return;
  formLoading.value = true;
  try {
    if (form.assetId === undefined) {
      await createAsset(createRequestBody());
    } else if (form.version !== undefined) {
      await updateAsset(form.assetId, { assetName: form.assetName, status: form.status, version: form.version });
    }
    drawerVisible.value = false;
    ElMessage.success('素材保存成功');
    await loadAssets();
  } finally {
    formLoading.value = false;
  }
}

async function confirmDelete(item: AssetListItem): Promise<void> {
  await ElMessageBox.confirm(`确认删除素材“${item.assetName}”吗？被课程引用或正在处理的素材无法删除。`, '二次确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  });
  await deleteAsset(item.assetId);
  ElMessage.success('素材已删除');
  await loadAssets();
}

async function retryTranscode(item: AssetListItem): Promise<void> {
  await retryAssetTranscode(item.assetId);
  if (previewAsset.value?.assetId === item.assetId) {
    previewProcessStatus.value = 'PROCESSING';
    previewInfo.value = null;
    startPreviewProcessPolling(item.assetId);
  }
  ElMessage.success('已重新发起转码');
  await loadAssets();
}

/** 清理预览状态轮询，避免关闭抽屉后继续请求。 */
function clearPreviewProcessPolling(): void {
  if (previewProcessTimer !== undefined) globalThis.clearInterval(previewProcessTimer);
  previewProcessTimer = undefined;
}

/** 处理完成后再获取 play-info，避免 PDF 在线性化前被打开。 */
function startPreviewProcessPolling(assetId: number): void {
  clearPreviewProcessPolling();
  previewProcessTimer = globalThis.setInterval(async () => {
    try {
      const detail = await getAssetDetail(assetId);
      if (previewAsset.value?.assetId !== assetId) return;
      previewDetail.value = detail;
      previewProcessStatus.value = detail.processStatus;
      if (detail.processStatus === 'UPLOADED' || detail.processStatus === 'PROCESSING') return;
      clearPreviewProcessPolling();
      if (detail.processStatus === 'SUCCEEDED') previewInfo.value = await getAssetPlayInfo(assetId);
    } catch {
      // 列表页轮询会继续刷新状态，单次预览轮询失败不打断抽屉状态展示。
    }
  }, 3000);
}

async function openTranscodeJobs(item: AssetListItem): Promise<void> {
  currentAssetName.value = item.assetName;
  jobsDrawerVisible.value = true;
  jobsLoading.value = true;
  try {
    transcodeJobs.value = await getAssetTranscodeJobs(item.assetId);
  } finally {
    jobsLoading.value = false;
  }
}

/** 打开素材预览，播放地址由后端 play-info 受控返回。 */
async function openPreview(item: AssetListItem): Promise<void> {
  clearPreviewProcessPolling();
  previewAsset.value = item;
  previewInfo.value = null;
  previewDetail.value = null;
  previewProcessStatus.value = item.processStatus;
  previewDrawerVisible.value = true;
  previewLoading.value = true;
  try {
    previewDetail.value = await getAssetDetail(item.assetId);
    previewProcessStatus.value = previewDetail.value.processStatus;
    if (previewDetail.value.processStatus === 'UPLOADED' || previewDetail.value.processStatus === 'PROCESSING') {
      startPreviewProcessPolling(item.assetId);
      return;
    }
    if (previewDetail.value.processStatus === 'SUCCEEDED' && !['RICH_TEXT', 'LINK', 'LIVE'].includes(item.assetType)) {
      previewInfo.value = await getAssetPlayInfo(item.assetId);
    }
  } finally {
    previewLoading.value = false;
  }
}

onMounted(async () => {
  await loadAssets();
  startProcessStatusPolling();
});
onBeforeUnmount(() => {
  if (processStatusTimer !== undefined) globalThis.clearInterval(processStatusTimer);
  clearPreviewProcessPolling();
});
</script>

<template>
  <section class="admin-business-page asset-library">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm :model="filters" inline>
        <ElFormItem label="素材名称">
          <ElInput v-model="filters.assetName" placeholder="请输入素材名称" clearable @keyup.enter="searchAssets" />
        </ElFormItem>
        <ElFormItem label="素材类型">
          <ElSelect v-model="filters.assetType" placeholder="全部类型" clearable>
            <ElOption v-for="item in ASSET_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="处理状态">
          <ElSelect v-model="filters.processStatus" placeholder="全部处理状态" clearable>
            <ElOption v-for="item in ASSET_PROCESS_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="启停状态">
          <ElSelect v-model="filters.status" placeholder="全部状态" clearable>
            <ElOption label="启用" :value="1" />
            <ElOption label="停用" :value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="searchAssets">查询</ElButton>
          <ElButton @click="resetFilters">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>素材列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="canOperate('asset:asset:add')" type="primary" @click="openCreate">新增素材</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="assetList" height="calc(100vh - 530px)">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="assetCode" label="素材编码" min-width="150" />
        <ElTableColumn prop="assetName" label="素材名称" min-width="200" show-overflow-tooltip />
        <ElTableColumn label="素材类型" width="110">
          <template #default="{ row }">{{ getAssetTypeLabel(asAsset(row).assetType) }}</template>
        </ElTableColumn>
        <ElTableColumn label="处理状态" width="120">
          <template #default="{ row }">
            <ElTag :type="asAsset(row).processStatus === 'FAILED' ? 'danger' : asAsset(row).processStatus === 'SUCCEEDED' ? 'success' : 'warning'">
              {{ getAssetProcessStatusLabel(asAsset(row).processStatus) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="启停状态" width="100">
          <template #default="{ row }">
            <ElTag :type="asAsset(row).status === 1 ? 'success' : 'info'">{{ getAssetStatusLabel(asAsset(row).status) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              :disabled="asAsset(row).processStatus !== 'SUCCEEDED' && !asAsset(row).assetName.toLowerCase().endsWith('.pdf')"
              @click="openPreview(asAsset(row))"
            >
              预览
            </ElButton>
            <ElButton v-if="canOperate('asset:asset:edit')" link type="primary" @click="openEdit(asAsset(row))">编辑</ElButton>
            <ElButton
              v-if="canOperate('asset:asset:edit') && canRetryAssetTranscode(asAsset(row))"
              link
              type="warning"
              @click="retryTranscode(asAsset(row))"
            >
              重试转码
            </ElButton>
            <ElButton v-if="canOperate('asset:job:list')" link type="primary" @click="openTranscodeJobs(asAsset(row))">转码任务</ElButton>
            <ElButton v-if="canOperate('asset:asset:delete')" link type="danger" @click="confirmDelete(asAsset(row))">删除</ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无资源" />
        </template>
      </ElTable>
      <div class="asset-library__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="
            (page: number) => {
              pageNum = page;
              loadAssets();
            }
          "
          @size-change="
            pageNum = 1;
            loadAssets();
          "
        />
      </div>
    </ElCard>

    <ElDrawer
      v-model="drawerVisible"
      :title="form.assetId === undefined ? '新增素材' : '编辑素材'"
      :lock-scroll="false"
      size="720px"
      destroy-on-close
      @closed="handleDrawerClosed"
    >
      <ElForm ref="formRef" v-loading="formLoading" :model="form" :rules="formRules" label-width="100px" class="asset-library__drawer-form">
        <ElFormItem label="素材编码" prop="assetCode">
          <ElInput v-model="form.assetCode" :disabled="form.assetId !== undefined" maxlength="64" placeholder="请输入唯一素材编码" />
        </ElFormItem>
        <ElFormItem label="素材名称" prop="assetName">
          <ElInput v-model="form.assetName" maxlength="255" placeholder="请输入素材名称" />
        </ElFormItem>
        <template v-if="form.assetId === undefined">
          <ElFormItem label="素材类型" prop="assetType">
            <ElSelect v-model="form.assetType" placeholder="请选择素材类型" :disabled="form.fileInfoId !== undefined" @change="handleAssetTypeChange">
              <ElOption v-for="item in ASSET_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
            </ElSelect>
            <div v-if="form.fileInfoId !== undefined" class="asset-library__file-hint">
              已上传文件后不可更换类型；更换请先清除文件。同一类型可直接重新上传。
            </div>
          </ElFormItem>
          <ElFormItem label="素材来源" prop="sourceType">
            <ElRadioGroup v-model="form.sourceType">
              <ElRadio v-for="item in sourceOptions" :key="item.value" :value="item.value">{{ item.label }}</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem v-if="form.sourceType === 'UPLOAD'" label="素材文件" required>
            <div class="asset-library__file-field">
              <div class="asset-library__file-row">
                <ElButton v-if="canOperate('sys:file:upload')" @click="openUpload">
                  {{ form.uploadedFileName ? '重新上传文件' : '选择并上传文件' }}
                </ElButton>
                <ElText v-else type="info">当前账号没有文件上传权限，请选择在线创建或外部地址。</ElText>
                <span v-if="originalFileSizeHint" class="asset-library__file-hint">{{ originalFileSizeHint }}</span>
              </div>
              <div v-if="form.uploadedFileName" class="asset-library__uploaded-file">
                <span class="asset-library__file-name" :title="form.uploadedFileName">
                  {{ form.uploadedFileName }}
                  <template v-if="form.fileSize">（{{ formatFileSize(form.fileSize) }}）</template>
                </span>
                <ElButton link type="danger" @click="clearUploadedFile">清除文件</ElButton>
              </div>
            </div>
          </ElFormItem>
          <ElFormItem v-if="form.sourceType === 'CREATED'" label="富文本内容" required>
            <QuillEditor
              v-model:content="form.richContent"
              content-type="html"
              theme="snow"
              :toolbar="RICH_TEXT_TOOLBAR"
              placeholder="请输入素材正文内容"
              class="asset-library__rich-editor"
            />
          </ElFormItem>

          <ElFormItem v-if="form.sourceType === 'EXTERNAL'" label="外部地址" required>
            <ElInput v-model="form.externalUrl" maxlength="1000" placeholder="请输入 http:// 或 https:// 地址" />
          </ElFormItem>
          <template v-if="form.sourceType === 'UPLOAD'">
            <ElFormItem label="MIME 类型">
              <ElInput v-model="form.mimeType" readonly placeholder="上传后自动识别" />
            </ElFormItem>
            <ElFormItem label="页数">
              <ElInputNumber v-model="form.pageCount" disabled :min="0" placeholder="PDF 上传后自动读取" />
            </ElFormItem>
            <ElFormItem label="时长（自动读取）">
              <ElInputNumber v-model="form.durationSeconds" disabled :min="0" placeholder="音视频上传后自动读取" />
              <p v-if="form.durationSeconds != null" class="form-item__hint">{{ formatDuration(form.durationSeconds) }}（共 {{ form.durationSeconds }} 秒）</p>
            </ElFormItem>
            <ElFormItem label="校验摘要">
              <ElInput v-model="form.checksum" readonly placeholder="由上传适配器和后端校验" />
            </ElFormItem>
          </template>
        </template>
        <ElFormItem label="启停状态">
          <ElRadioGroup v-model="form.status">
            <ElRadio :value="1">启用</ElRadio>
            <ElRadio :value="0">停用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="drawerVisible = false">取消</ElButton>
        <ElButton
          v-if="canOperate(form.assetId === undefined ? 'asset:asset:add' : 'asset:asset:edit')"
          type="primary"
          :loading="formLoading"
          @click="saveAsset"
        >
          保存
        </ElButton>
      </template>
    </ElDrawer>

    <UnifiedFileUpload
      v-model="uploadVisible"
      mode="dialog"
      :multiple="false"
      :limit="1"
      :accept="uploadAccept"
      :max-file-size="originalFileMaxBytes"
      :size-limit-hint="originalFileSizeHint"
      :type-options="uploadTypeOptions"
      :initial-type="form.assetType"
      :type-upload-config="uploadTypeConfig"
      :upload-request="requestFileUpload"
      :cancel-request="cancelAssetUpload"
      @type-change="handleUploadTypeChange"
      @success="handleUploadSuccess"
    />

    <ElDrawer
      v-model="previewDrawerVisible"
      class="asset-preview-drawer"
      :lock-scroll="false"
      header-class="asset-preview-drawer-header"
      body-class="asset-preview-drawer-body"
      :title="`${previewAsset?.assetName ?? ''} · 预览`"
      size="600px"
      destroy-on-close
      @closed="clearPreviewProcessPolling"
    >
      <div v-if="previewDrawerVisible" v-loading="previewLoading" class="asset-preview-body">
        <div v-if="previewProcessStatus === 'UPLOADED' || previewProcessStatus === 'PROCESSING'" class="preview-process-state">
          <strong>PDF 正在处理中</strong>
          <span>处理完成后将自动加载预览。</span>
        </div>
        <div v-else-if="previewProcessStatus === 'FAILED'" class="preview-process-state preview-process-state--error">
          <strong>PDF 处理失败</strong>
          <span>请点击“重试转码”重新生成预览文件。</span>
          <ElButton
            v-if="canOperate('asset:asset:edit') && previewAsset && canRetryAssetTranscode(previewAsset)"
            type="warning"
            @click="retryTranscode(previewAsset)"
          >
            重试转码
          </ElButton>
        </div>
        <AssetViewer
          v-else-if="previewProcessStatus === 'SUCCEEDED' || ['RICH_TEXT', 'LINK', 'LIVE'].includes(previewAsset?.assetType ?? '')"
          class="asset-preview-viewer"
          :asset-type="previewAsset?.assetType"
          :play-info="previewInfo"
          :rich-content="previewDetail?.richContent"
          :external-url="previewDetail?.externalUrl"
          :file-name="previewAsset?.assetName"
        />
      </div>
    </ElDrawer>

    <ElDrawer v-model="jobsDrawerVisible" :title="`${currentAssetName} · 转码任务`" size="800px" :lock-scroll="false">
      <ElTable v-loading="jobsLoading" :data="transcodeJobs" empty-text="暂无转码任务">
        <ElTableColumn prop="transcodeJobId" label="任务 ID" width="90" />
        <ElTableColumn prop="jobType" label="任务类型" width="160">
          <template #default="{ row }">{{ TRANSCODE_JOB_TYPE_LABELS[(row as AssetTranscodeJob).jobType] }}</template>
        </ElTableColumn>
        <ElTableColumn label="任务状态" width="110">
          <template #default="{ row }">{{ TRANSCODE_JOB_STATUS_LABELS[(row as AssetTranscodeJob).jobStatus] }}</template>
        </ElTableColumn>
        <ElTableColumn prop="startedTime" label="开始时间" min-width="170" />
        <ElTableColumn prop="finishedTime" label="完成时间" min-width="170" />
        <ElTableColumn prop="retryCount" label="重试次数" width="90" />
        <ElTableColumn prop="failureReason" label="失败原因" min-width="180" show-overflow-tooltip />
      </ElTable>
    </ElDrawer>
  </section>
</template>

<style scoped lang="less">
.form-item__hint {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.asset-library {
  color: var(--color-text-regular);
}

.asset-preview-body {
  display: flex;
  width: 100%;
  height: auto;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.asset-preview-viewer {
  width: 100%;
  min-height: 0;
}

.preview-process-state {
  display: grid;
  min-height: 240px;
  place-content: center;
  gap: 8px;
  color: var(--color-text-regular);
  text-align: center;
}

.preview-process-state span {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.preview-process-state--error {
  color: var(--color-danger);
}

.asset-library__pagination {
  display: flex;
  justify-content: flex-end;
  margin: 5px 0;
}

.asset-library__file-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.asset-library__file-field {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 10px;
}

.asset-library__uploaded-file {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: var(--color-bg-subtle);
}

.asset-library__file-name {
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-library__file-hint {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.asset-library__drawer-form :deep(.el-select),
.asset-library__drawer-form :deep(.el-input-number) {
  width: 100%;
}
:deep(.ql-toolbar) {
  width: 100%;
}
:deep(.ql-container) {
  width: 100%;
  height: calc(100% - 50px);
}
</style>

<style lang="less">
/*noinspection CssUnusedSymbol*/
.asset-preview-drawer.el-drawer {
  height: auto !important;
  max-height: 100vh;
}

.el-drawer__header.asset-preview-drawer-header {
  display: flex;
  align-items: center;
  margin-bottom: 0;
  padding: 5px 16px;
}

.asset-preview-drawer-header > :first-child,
.asset-preview-drawer-header .el-drawer__title {
  flex: 1;
  min-width: 0;
  overflow: visible;
  margin: 0;
  padding: 0;
}

.asset-preview-drawer-header .el-drawer__close-btn {
  flex-shrink: 0;
  margin-left: 5px;
  color: var(--color-danger);
}

.el-drawer__body.asset-preview-drawer-body {
  display: flex;
  height: auto;
  max-height: calc(100vh - 56px);
  flex: none;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}
</style>
