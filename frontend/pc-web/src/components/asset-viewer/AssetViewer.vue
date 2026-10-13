<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue';
import Hls from 'hls.js';
import {QuillEditor} from '@vueup/vue-quill';
import '@vueup/vue-quill/dist/vue-quill.snow.css';
import {audioPlugin, fallbackPlugin, imagePlugin, officePlugin, textPlugin, videoPlugin} from '@open-file-viewer/core';
import {OpenFileViewer} from '@open-file-viewer/vue';
import '@open-file-viewer/core/style.css';
import {getAccessToken} from '@/api/request';
import type {AssetPlayInfo, AssetType} from '@/api/asset';
import PdfViewer from '@/components/pdf-viewer/PdfViewer.vue';

const props = defineProps<{
  assetType?: AssetType;
  playInfo?: AssetPlayInfo | null;
  richContent?: string | null;
  externalUrl?: string | null;
  fileName?: string;
  localFile?: globalThis.File | null;
  initialPositionSeconds?: number | null;
  initialPageNo?: number | null;
  downloadUrl?: string | null;
  pdfLayout?: 'page' | 'continuous';
  /**
   * 是否允许倍速播放的课程开关。
   * true 显示倍速控件并放开速率；false 锁定 1 倍速并回置浏览器控制条改动的速率；未传则不干预（管理端预览）。
   */
  allowSpeedPlay?: boolean | null;
  /** 非 PDF 文件查看器的高度，嵌入弹窗时可按容器可用空间覆盖。 */
  previewHeight?: string;
}>();

const emit = defineEmits<{
  error: [message: string];
  timeupdate: [positionSeconds: number];
  pageupdate: [pageNo: number];
  pagecount: [pageCount: number];
  ended: [];
  play: [];
  pause: [];
  loadedmetadata: [];
}>();
const video = ref<globalThis.HTMLVideoElement>();
const viewerFile = ref<globalThis.Blob | string | null>(null);
const viewerFileName = ref('');
const viewerMimeType = ref('application/octet-stream');
const viewerKey = ref(0);
const pdfViewer = ref<InstanceType<typeof PdfViewer>>();
const coverUrl = ref('');
const loading = ref(false);
const errorMessage = ref('');
const playbackRate = ref(1);
let hls: Hls | undefined;
/** 学员端可选倍速档位；课程未开放倍速时不渲染控件。 */
const SPEED_RATE_OPTIONS = [0.75, 1, 1.25, 1.5, 2] as const;
const isPdfPreview = computed((): boolean => {
  const format = props.playInfo?.format?.toLowerCase();
  return format === 'pdf'
    || (format === 'document' && props.fileName?.toLowerCase().endsWith('.pdf') === true)
    || props.localFile?.type === 'application/pdf'
    || props.localFile?.name.toLowerCase().endsWith('.pdf') === true;
});
const isHls = computed(() => props.playInfo?.format?.toLowerCase() === 'hls');
/** 只有课程开关明确允许且当前是可调速的 HLS 播放器时才展示倍速控件。 */
const showSpeedBar = computed(() => props.allowSpeedPlay === true && isHls.value);
/*
 * 查看器高度默认 800px（独立预览场景），上传弹窗等空间受限的容器按需下调。
 * 默认值走 computed 而不是 withDefaults：本文件其余 prop 都不带默认值，
 * 一旦引入 withDefaults，vue/require-default-prop 会要求所有 prop 都补默认值。
 */
const previewHeight = computed(() => props.previewHeight ?? '800px');

/** 仅在课程允许倍速时应用速率，其余情况保持原生 1 倍速。 */
function applyPlaybackRate(rate: number): void {
  if (props.allowSpeedPlay !== true || !video.value) return;
  video.value.playbackRate = rate;
  playbackRate.value = rate;
}

/** 课程禁用倍速时回置浏览器控制条改动的速率，使其等效于不可倍速。 */
function handleRateChange(): void {
  if (!video.value) return;
  if (props.allowSpeedPlay === false && video.value.playbackRate !== 1) {
    video.value.playbackRate = 1;
  }
  playbackRate.value = video.value.playbackRate;
}

/** 恢复后端保存的 PDF 页码；学习页平铺预览只读展示当前页，不提供输入跳转。 */
function goToPdfPage(pageNo: number): void {
  void pdfViewer.value?.goToPage(pageNo);
}

/** Open File Viewer 负责常见文件渲染，HLS 保留专用播放器处理。 */
function authHeaders(): Record<string, string> {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

const viewerPlugins = [
  imagePlugin(),
  officePlugin(),
  textPlugin(),
  videoPlugin(),
  audioPlugin(),
  fallbackPlugin()
];

/** 通过受控文件代理读取 Blob，避免把私有对象地址直接交给浏览器。 */
async function loadBinary(url: string): Promise<ArrayBuffer> {
  const response = await globalThis.fetch(url, { headers: authHeaders() });
  if (!response.ok) throw new Error('素材读取失败，请重试');
  return response.arrayBuffer();
}

/** 带当前登录凭证读取视频封面，避免原生 poster 请求丢失 Bearer Token。 */
async function loadCover(url?: string | null): Promise<void> {
  if (!url) return;
  try {
    const blob = new globalThis.Blob([await loadBinary(url)]);
    coverUrl.value = globalThis.URL.createObjectURL(blob);
  } catch {
    // 封面只是视频增强信息，读取失败不能阻断 HLS 播放。
    coverUrl.value = '';
  }
}

/** 返回预览器和下载都能识别的 MIME 类型。 */
function fileMimeType(format: string): string {
  const mimeTypes: Record<string, string> = {
    doc: 'application/msword',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    xls: 'application/vnd.ms-excel',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    ppt: 'application/vnd.ms-powerpoint',
    pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    pdf: 'application/pdf',
    rtf: 'application/rtf',
    odt: 'application/vnd.oasis.opendocument.text',
    ods: 'application/vnd.oasis.opendocument.spreadsheet',
    odp: 'application/vnd.oasis.opendocument.presentation',
    mp4: 'video/mp4',
    webm: 'video/webm',
    mov: 'video/quicktime',
    mp3: 'audio/mpeg',
    wav: 'audio/wav',
    png: 'image/png',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    gif: 'image/gif',
    webp: 'image/webp',
    bmp: 'image/bmp',
    jfif: 'image/jpeg',
    txt: 'text/plain',
    md: 'text/markdown',
    markdown: 'text/markdown'
  };
  return mimeTypes[format.toLowerCase()] ?? 'application/octet-stream';
}

/** 将后端的类别值映射为文件扩展名，保证 Blob 能命中对应插件。 */
function effectiveFormat(format: string, fileName?: string): string {
  const normalized = format.toLowerCase();
  if (normalized === 'original') return 'original';
  if (normalized === 'excel') return fileName?.match(/\.([^.]+)$/)?.[1]?.toLowerCase() ?? 'xlsx';
  if (normalized === 'image') return fileName?.match(/\.([^.]+)$/)?.[1]?.toLowerCase() ?? 'png';
  if (!['image', 'audio', 'video', 'document', 'text'].includes(normalized)) return normalized;
  const extension = fileName?.match(/\.([^.]+)$/)?.[1]?.toLowerCase();
  if (extension) return extension;
  return { image: 'png', audio: 'mp3', video: 'mp4', document: 'pdf', text: 'txt' }[normalized] ?? normalized;
}

/** 根据 play-info 格式补齐扩展名，确保 Office 插件能正确识别 Blob。 */
function normalizedFileName(fileName: string | undefined, format: string): string {
  const extension = format.toLowerCase();
  const baseName = (fileName || '素材文件').replace(/\.[^.]+$/, '');
  return `${baseName}.${extension}`;
}

/** 下载时显式设置 MIME 和扩展名，避免 xlsx 被浏览器保存成 text。 */
async function downloadPreview(): Promise<void> {
  const url = props.playInfo?.url ?? props.downloadUrl;
  if (!url) return;
  try {
    const format = effectiveFormat(props.playInfo?.format ?? 'original', props.fileName);
    const blob = new globalThis.Blob([await loadBinary(url)], { type: fileMimeType(format) });
    const link = globalThis.document.createElement('a');
    link.href = globalThis.URL.createObjectURL(blob);
    link.download = normalizedFileName(props.fileName, format);
    link.click();
    globalThis.URL.revokeObjectURL(link.href);
  } catch (error) {
    showError(error instanceof Error ? error.message : '素材下载失败，请重试');
  }
}

function clearMedia(): void {
  // 抽屉关闭或切换素材时先停止原生媒体，再销毁 HLS，避免媒体元素继续输出声音。
  video.value?.pause();
  video.value?.removeAttribute('src');
  video.value?.load();
  hls?.destroy();
  hls = undefined;
  if (coverUrl.value) globalThis.URL.revokeObjectURL(coverUrl.value);
  coverUrl.value = '';
  viewerFile.value = null;
  playbackRate.value = 1;
}

function showError(message: string): void {
  errorMessage.value = message;
  emit('error', message);
}

/** HLS 使用专用播放器，音视频共用；分片请求注入现有登录凭证。 */
async function loadHls(): Promise<void> {
  // HLS 播放器依赖 video 节点，先结束加载状态让节点挂载，再初始化播放器。
  loading.value = false;
  await nextTick();
  if (!video.value || !props.playInfo?.url) throw new Error('播放器初始化失败');
  const restorePosition = (): void => {
    if (video.value && (props.initialPositionSeconds ?? 0) > 0) {
      video.value.currentTime = props.initialPositionSeconds ?? 0;
    }
    emit('loadedmetadata');
  };
  video.value.addEventListener('loadedmetadata', restorePosition, { once: true });
  if (Hls.isSupported()) {
    hls = new Hls();
    hls.config.xhrSetup = (xhr) => {
      const token = getAccessToken();
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    };
    hls.attachMedia(video.value);
    hls.loadSource(props.playInfo.url);
  } else {
    video.value.src = props.playInfo.url;
    video.value.load();
  }
}

/** 非 HLS 文件按格式进入专用 PDF 查看器或通用 Open File Viewer。 */
async function loadPreview(): Promise<void> {
  clearMedia();
  errorMessage.value = '';
  /** 本地上传文件直接交给 Open File Viewer，避免依赖后端文件地址。 */
  if (props.localFile) {
    if (isPdfPreview.value) return;
    viewerFileName.value = props.localFile.name;
    viewerMimeType.value = props.localFile.type || 'application/octet-stream';
    viewerFile.value = props.localFile;
    viewerKey.value += 1;
    return;
  }
  const info = props.playInfo;
  if (!info?.url) return;
  if (info.format.toLowerCase() === 'hls') {
    loading.value = true;
    try {
      await loadCover(info.coverUrl);
      await loadHls();
    } catch (error) {
      showError(error instanceof Error ? error.message : '素材预览失败，请重试');
    } finally {
      loading.value = false;
    }
    return;
  }
  if (props.assetType === 'AUDIO' || props.assetType === 'VIDEO' || info.format.toLowerCase() === 'original') {
    viewerFile.value = null;
    viewerKey.value += 1;
    return;
  }
  loading.value = true;
  try {
    const format = effectiveFormat(info.format, props.fileName);
    viewerFileName.value = normalizedFileName(props.fileName, format);
    viewerMimeType.value = fileMimeType(format);
    if (format !== 'pdf') {
      viewerFile.value = new globalThis.Blob([await loadBinary(info.url)], {type: viewerMimeType.value});
    }
    viewerKey.value += 1;
  } catch (error) {
    showError(error instanceof Error ? error.message : '素材预览失败，请重试');
  } finally {
    loading.value = false;
  }
}

function handleViewerError(error: Error): void {
  showError(error.message || '素材预览失败，请重试');
}

watch(
  () => [
    props.localFile,
    props.assetType,
    props.playInfo?.url,
    props.playInfo?.format,
    props.fileName,
    props.initialPageNo,
    props.richContent,
    props.externalUrl,
    props.downloadUrl
  ],
  () => void loadPreview(),
  { immediate: true }
);
defineExpose({ goToPdfPage });
onBeforeUnmount(clearMedia);
</script>

<template>
  <section class="asset-viewer" aria-label="素材预览">
    <div v-if="loading" class="viewer-state">正在加载素材…</div>
    <div v-else-if="errorMessage" class="viewer-state viewer-error">
      <span>{{ errorMessage }}</span>
      <div class="viewer-actions">
        <button type="button" @click="loadPreview">重试</button>
        <button v-if="playInfo?.url || downloadUrl" type="button" @click="downloadPreview">下载文件</button>
      </div>
    </div>
    <div v-else-if="assetType === 'RICH_TEXT'" class="rich-text-content">
      <QuillEditor :content="richContent ?? ''" content-type="html" theme="snow" read-only :toolbar="false" />
    </div>
    <div v-else-if="assetType === 'LINK' || assetType === 'LIVE'" class="external-link-content">
      <p>{{ assetType === 'LINK' ? '该素材为外部链接，点击下方按钮在新标签页打开。' : '该素材为直播，点击下方按钮在新标签页打开。' }}</p>
      <a v-if="externalUrl" class="external-link" :href="externalUrl" target="_blank" rel="noopener noreferrer">
        {{ assetType === 'LINK' ? '打开外部链接' : '打开直播' }}
      </a>
      <span v-else class="viewer-state">链接地址为空。</span>
    </div>
    <div v-else-if="isHls" class="media-stage">
      <video
        ref="video"
        class="media-video"
        controls
        playsinline
        :poster="coverUrl || undefined"
        @timeupdate="emit('timeupdate', ($event.currentTarget as HTMLVideoElement).currentTime)"
        @ratechange="handleRateChange"
        @play="emit('play')"
        @pause="emit('pause')"
        @ended="emit('ended')"
      />
      <div v-if="showSpeedBar" class="media-speed-bar">
        <span>倍速</span>
        <button
          v-for="rate in SPEED_RATE_OPTIONS"
          :key="rate"
          type="button"
          :class="{ active: playbackRate === rate }"
          @click="applyPlaybackRate(rate)"
        >
          {{ rate }}x
        </button>
      </div>
    </div>
    <div v-else-if="playInfo?.format?.toLowerCase() === 'original' || assetType === 'AUDIO' || assetType === 'VIDEO'"
         class="viewer-state">
      <span>当前格式不支持在线预览。</span>
    </div>
    <PdfViewer
      v-else-if="isPdfPreview && (playInfo?.url || localFile)"
      ref="pdfViewer"
      :url="playInfo?.url"
      :file="localFile"
      :file-name="fileName"
      :initial-page-no="initialPageNo"
      :http-headers="authHeaders()"
      :layout="pdfLayout"
      @pagecount="emit('pagecount', $event)"
      @pageupdate="emit('pageupdate', $event)"
      @loaded="emit('loadedmetadata')"
      @error="showError"
      @fallback="downloadPreview"
    />
    <div v-else-if="viewerFile" class="viewer-host">
      <OpenFileViewer
        :key="viewerKey"
        class="open-file-viewer"
        :file="viewerFile"
        :file-name="viewerFileName"
        :mime-type="viewerMimeType"
        :height="previewHeight"
        locale="zh-CN"
        fallback="download"
        :plugins="viewerPlugins"
        toolbar
        @error="handleViewerError"
      />
    </div>
    <div v-else class="viewer-state">
      <span>当前素材暂不支持在线预览，请下载后查看。</span>
      <button v-if="playInfo?.url || downloadUrl" type="button" @click="downloadPreview">下载文件</button>
    </div>
  </section>
</template>

<style scoped lang="less">
.asset-viewer {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  box-sizing: border-box;
  padding: 0;
  background: #202a3d;
}
.open-file-viewer {
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #fff;
}
.viewer-host {
  width: 100%;
  height: 100%;
  min-height: 0;
}
.viewer-state {
  display: grid;
  min-height: 400px;
  place-items: center;
  gap: 12px;
  color: #aebbd0;
  text-align: center;
}
.viewer-error {
  color: var(--color-danger);
}
.viewer-actions {
  display: flex;
  gap: 8px;
}
.viewer-actions button,
.viewer-state > button {
  padding: 6px 14px;
  border: 1px solid currentColor;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.media-video {
  display: block;
  width: 100%;
  height: 600px;
  margin: auto;
  object-fit: contain;
  background: #202a3d;
}
.media-stage {
  position: relative;
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  align-items: center;
}
.media-speed-bar {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  padding: 4px 6px;
  align-items: center;
  gap: 4px;
  border-radius: 8px;
  background: rgb(15 23 42 / 72%);
  color: #e2e8f0;
  font-size: 12px;
}
.media-speed-bar button {
  padding: 3px 7px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 12px;
}
.media-speed-bar button.active {
  background: #5575b1;
  color: #fff;
}
.rich-text-content,
.external-link-content {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  overflow: auto;
  padding: 28px 34px;
  background: #fff;
}
.rich-text-content :deep(.ql-container) {
  border: 0;
  font-size: 16px;
}
.rich-text-content :deep(.ql-editor) {
  min-height: 100%;
  padding: 0;
  color: #1d2939;
  line-height: 1.8;
}
.rich-text-content :deep(.ql-editor > *:first-child) {
  margin-top: 0;
}
.rich-text-content :deep(.ql-editor img) {
  max-width: 100%;
  height: auto;
}
.external-link-content {
  display: grid;
  place-content: center;
  gap: 16px;
  text-align: center;
}
.external-link {
  color: var(--color-primary);
  text-decoration: none;
}
:deep(.ofv-image-viewer) {
  height: auto;
}
</style>
