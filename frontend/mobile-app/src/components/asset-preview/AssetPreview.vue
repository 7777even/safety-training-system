<script setup lang="ts">
import type {AssetDetail, AssetPlayInfo} from '@/api/asset';
import {AUTH_TOKEN_STORAGE_KEY} from '@shared/constants';
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue';
import {getAccessToken, request} from '@/api/request';
// #ifdef H5
import PdfPreview from '@/components/pdf-preview/PdfPreview.vue';
// #endif

const props = defineProps<{
  asset: AssetDetail;
  playInfo: AssetPlayInfo | null;
  loading?: boolean;
  errorMessage?: string;
  initialPositionSeconds?: number | null;
  initialPageNo?: number | null;
  /**
   * 是否允许倍速播放的课程开关。
   * true 展示倍速控件并放开速率；false 锁定 1 倍速并回置播放器控制条改动的速率；未传则不干预。
   */
  allowSpeedPlay?: boolean | null;
}>();

const emit = defineEmits<{
  open: [];
  timeupdate: [seconds: number];
  ended: [];
  play: [];
  pause: [];
  pageupdate: [pageNo: number];
  pagecount: [pageCount: number];
}>();

const videoElement = ref<unknown>();
const resolvedPreviewUrl = ref('');
const resolvedCoverUrl = ref('');
const previewLoadError = ref('');
const viewerKey = ref(0);
const pdfExpanded = ref(false);
const richTextExpanded = ref(false);
const playbackRate = ref(1);
/** 学员端可选倍速档位；课程未开放倍速时不渲染控件。 */
const SPEED_RATE_OPTIONS = [0.75, 1, 1.25, 1.5, 2] as const;
let hls: InstanceType<typeof import('hls.js').default> | undefined;
let previewObjectUrl = '';
let coverObjectUrl = '';
let previewLoadSequence = 0;

/** 兼容 uni-app H5 video ref 返回组件代理或内部原生 video 的差异。 */
function resolveVideoElement(): globalThis.HTMLVideoElement | undefined {
  const candidate = videoElement.value as
    | {
        pause?: unknown;
        querySelector?: (selector: string) => unknown;
        $el?: unknown;
      }
    | undefined;
  if (candidate && typeof candidate.pause === 'function') return candidate as globalThis.HTMLVideoElement;
  const root = candidate?.$el as
    | {
        pause?: unknown;
        querySelector?: (selector: string) => unknown;
      }
    | undefined;
  if (root && typeof root.pause === 'function') return root as globalThis.HTMLVideoElement;
  const nested = root?.querySelector?.('video') as { pause?: unknown } | undefined;
  return nested && typeof nested.pause === 'function' ? (nested as globalThis.HTMLVideoElement) : undefined;
}

/** 按素材详情和播放格式选择移动端预览分支。音视频成功后都走 HLS。 */
function previewKind(): 'video' | 'image' | 'pdf' | 'rich-text' | 'office' | 'external' | 'unsupported' {
  if (props.asset.assetType === 'VIDEO' || props.asset.assetType === 'AUDIO') {
    return props.playInfo?.format === 'hls' ? 'video' : 'unsupported';
  }
  if (props.asset.assetType === 'IMAGE') return 'image';
  if (props.asset.assetType === 'RICH_TEXT') return 'rich-text';
  if (props.asset.assetType === 'LINK' || props.asset.assetType === 'LIVE') return 'external';
  if (props.playInfo?.format === 'pdf') return 'pdf';
  if (props.playInfo?.format === 'excel' || props.playInfo?.format === 'original') return 'office';
  return 'unsupported';
}

/** 只有课程开关明确允许且当前是 HLS 视频时才展示倍速控件。 */
const showSpeedBar = computed(() => props.allowSpeedPlay === true && previewKind() === 'video' && !!props.playInfo?.url);

/** 仅在课程允许倍速时应用速率；移动端播放器支持时才生效。 */
function applyPlaybackRate(rate: number): void {
  const video = resolveVideoElement();
  if (props.allowSpeedPlay !== true || !video) return;
  video.playbackRate = rate;
  playbackRate.value = rate;
}

/** 课程禁用倍速时回置播放器控制条改动的速率，使其等效于不可倍速。 */
function lockPlaybackRate(): void {
  if (props.allowSpeedPlay !== false) return;
  const video = resolveVideoElement();
  if (video && video.playbackRate !== 1) video.playbackRate = 1;
  playbackRate.value = 1;
}

/** 预览失败或移动端不支持时，通过父页面提供的受控入口继续访问。 */
function openControlledFile(): void {
  emit('open');
}

/** 切换 PDF 预览区域的放大状态，避免移动端阅读时受卡片高度限制。 */
// function togglePdfExpanded(): void {
//   pdfExpanded.value = !pdfExpanded.value;
// }

/** 切换富文本阅读区域的放大状态，保持内容与目录卡片分离。 */
function toggleRichTextExpanded(): void {
  richTextExpanded.value = !richTextExpanded.value;
}

/** 释放当前媒体上下文，避免切换课程内容后旧媒体继续播放。 */
function clearMedia(): void {
  const video = resolveVideoElement();
  video?.pause();
  video?.removeAttribute('src');
  video?.load();
  hls?.destroy();
  hls = undefined;
  if (previewObjectUrl) globalThis.URL.revokeObjectURL(previewObjectUrl);
  if (coverObjectUrl) globalThis.URL.revokeObjectURL(coverObjectUrl);
  previewObjectUrl = '';
  coverObjectUrl = '';
  resolvedPreviewUrl.value = '';
  resolvedCoverUrl.value = '';
  viewerKey.value += 1;
  previewLoadError.value = '';
  pdfExpanded.value = false;
  richTextExpanded.value = false;
  playbackRate.value = 1;
  previewLoadSequence += 1;
}

/** 将受保护文件读成 Blob URL，避免原生资源请求丢失 Bearer。 */
async function createProtectedObjectUrl(url: string, mimeType = getPreviewMimeType()): Promise<string> {
  const binary = await request<ArrayBuffer>({ url, method: 'GET', responseType: 'arraybuffer' });
  return globalThis.URL.createObjectURL(new globalThis.Blob([binary], { type: mimeType }));
}

/** 为 Blob 补齐 MIME，尤其避免 PDF 被浏览器当作乱码文本展示。 */
function getPreviewMimeType(): string {
  if (props.playInfo?.format === 'pdf') return 'application/pdf';
  if (props.playInfo?.format === 'image') return props.asset.mimeType || 'image/*';
  return props.asset.mimeType || 'application/octet-stream';
}

/** 加载素材正文预览地址，并在切换素材后释放过期的 Blob。 */
async function loadProtectedPreviewUrl(url: string): Promise<void> {
  const sequence = previewLoadSequence;
  // #ifdef H5
  if (props.playInfo?.format === 'pdf') {
    resolvedPreviewUrl.value = url;
    viewerKey.value += 1;
    return;
  }
  const binary = await request<ArrayBuffer>({ url, method: 'GET', responseType: 'arraybuffer' });
  const blob = new globalThis.Blob([binary], { type: getPreviewMimeType() });
  const objectUrl = globalThis.URL.createObjectURL(blob);
  if (sequence !== previewLoadSequence) {
    globalThis.URL.revokeObjectURL(objectUrl);
    return;
  }
  previewObjectUrl = objectUrl;
  resolvedPreviewUrl.value = previewObjectUrl;
  viewerKey.value += 1;
  // #endif
  // #ifndef H5
  resolvedPreviewUrl.value = url;
  // #endif
}

/** 加载受保护的视频封面，避免 coverUrl 直接触发未携带 Token 的 401。 */
async function loadProtectedCoverUrl(url: string): Promise<void> {
  const sequence = previewLoadSequence;
  // #ifdef H5
  const objectUrl = await createProtectedObjectUrl(url, 'image/*');
  if (sequence !== previewLoadSequence) {
    globalThis.URL.revokeObjectURL(objectUrl);
    return;
  }
  coverObjectUrl = objectUrl;
  resolvedCoverUrl.value = objectUrl;
  // #endif
  // #ifndef H5
  resolvedCoverUrl.value = url;
  // #endif
}

/** 使用 HLS.js 播放受控 HLS 地址，音视频共用，并为分片请求注入登录凭证。 */
async function setupHls(): Promise<void> {
  await nextTick();
  const video = resolveVideoElement();
  if (props.playInfo?.format !== 'hls' || !props.playInfo.url || !video) return;
  const restorePosition = (): void => {
    if ((props.initialPositionSeconds ?? 0) > 0) {
      video.currentTime = props.initialPositionSeconds ?? 0;
    }
  };
  video.addEventListener('loadedmetadata', restorePosition, {once: true});
  const { default: Hls } = await import('hls.js');
  if (Hls.isSupported()) {
    hls = new Hls();
    hls.config.xhrSetup = (xhr) => {
      const token = getAccessToken() || uni.getStorageSync(AUTH_TOKEN_STORAGE_KEY);
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    };
    hls.on(Hls.Events.ERROR, (_event, data) => {
      if (!data.fatal) return;
      if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
        hls?.startLoad();
        return;
      }
      if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
        hls?.recoverMediaError();
        return;
      }
      previewLoadError.value = '媒体资源加载失败，请重试或检查素材权限';
    });
    hls.attachMedia(video);
    hls.loadSource(props.playInfo.url);
    return;
  }
  video.src = props.playInfo.url;
  video.load();
}

/** 转发视频播放进度，避免模板事件在不同端被推断为不带 detail 的通用 Event。 */
function onVideoTimeUpdate(event: unknown): void {
  const currentTime = (event as { detail?: { currentTime?: number } }).detail?.currentTime ?? 0;
  emit('timeupdate', currentTime);
}

/** 转发视频结束事件，统一触发课程学习完成上报。 */
function onVideoEnded(): void {
  emit('ended');
}

onBeforeUnmount(() => {
  clearMedia();
});

watch(
  () => props.playInfo?.url,
  () => {
    clearMedia();
    if (props.playInfo?.format === 'hls') {
      void setupHls();
    } else if (props.playInfo?.url && props.asset.assetType !== 'AUDIO' && props.asset.assetType !== 'VIDEO') {
      void loadProtectedPreviewUrl(props.playInfo.url).catch((error: unknown) => {
        previewLoadError.value = error instanceof Error ? error.message : '素材文件加载失败';
      });
    }
    if (props.playInfo?.coverUrl) {
      void loadProtectedCoverUrl(props.playInfo.coverUrl).catch(() => {
        resolvedCoverUrl.value = '';
      });
    }
  },
  { immediate: true }
);
</script>

<template>
  <view class="asset-preview" :class="{ 'is-long-document': previewKind() === 'pdf' }">
    <view v-if="loading" class="preview-state"><text>正在打开素材…</text></view>
    <view v-else-if="errorMessage || previewLoadError" class="preview-state preview-error">
      <text>{{ errorMessage || previewLoadError }}</text>
      <button class="fallback-button" @click="openControlledFile">使用受控入口打开</button>
    </view>
    <view
      v-else-if="asset.assetType === 'DOCUMENT' && (asset.processStatus === 'UPLOADED' || asset.processStatus === 'PROCESSING')"
      class="preview-state"
    >
      <text>PDF 正在处理中</text>
      <text class="preview-hint">处理完成后将自动加载预览。</text>
    </view>
    <view v-else-if="asset.assetType === 'DOCUMENT' && asset.processStatus === 'FAILED'" class="preview-state preview-error">
      <text>PDF 处理失败，请联系管理员重新处理。</text>
    </view>
    <view v-else-if="previewKind() === 'video' && playInfo?.url" class="video-stage">
      <video
        ref="videoElement"
        class="video-preview"
        :poster="resolvedCoverUrl || undefined"
        :initial-time="initialPositionSeconds || 0"
        controls
        playsinline
        @play="emit('play')"
        @pause="emit('pause')"
        @timeupdate="onVideoTimeUpdate"
        @ended="onVideoEnded"
      />
      <view v-if="showSpeedBar" class="video-speed-bar">
        <text class="video-speed-bar__label">倍速</text>
        <button
          v-for="rate in SPEED_RATE_OPTIONS"
          :key="rate"
          class="video-speed-bar__item"
          :class="{ 'video-speed-bar__item--active': playbackRate === rate }"
          @click="applyPlaybackRate(rate)"
        >
          {{ rate }}x
        </button>
      </view>
    </view>
    <!-- #ifdef H5 -->
    <view v-else-if="previewKind() === 'pdf' && playInfo?.url" class="viewer-shell is-long-document" :class="{ expanded: pdfExpanded }">
      <view class="preview-toolbar">
        <text>PDF 文档</text>
        <!-- <button class="zoom-button" @click="togglePdfExpanded">{{ pdfExpanded ? '缩小' : '放大预览' }}</button> -->
      </view>
      <PdfPreview
        :url="playInfo.url"
        :file-name="asset.assetName"
        :initial-page-no="initialPageNo"
        layout="continuous"
        @pageupdate="emit('pageupdate', $event)"
        @pagecount="emit('pagecount', $event)"
        @error="previewLoadError = $event"
        @fallback="openControlledFile"
      />
    </view>
    <view v-else-if="previewKind() === 'image' && resolvedPreviewUrl" class="viewer-shell">
      <view class="preview-toolbar">
        <text>图片预览</text>
      </view>
      <image class="image-preview" :src="resolvedPreviewUrl" mode="widthFix" />
    </view>
    <view v-else-if="previewKind() === 'office'" class="preview-state office-fallback">
      <text>当前移动端不支持内嵌 Office 预览。</text>
      <button class="fallback-button" @click="openControlledFile">受控打开或下载</button>
    </view>
    <!-- #endif -->
    <!-- #ifndef H5 -->
    <view v-else-if="['image', 'pdf', 'office'].includes(previewKind())" class="preview-state native-fallback">
      <text>当前环境使用受控文件接口打开，避免直接访问私有文件地址。</text>
      <button class="fallback-button" @click="openControlledFile">打开或下载素材</button>
    </view>
    <!-- #endif -->
    <view v-else-if="previewKind() === 'rich-text'" class="rich-text-shell" :class="{ expanded: richTextExpanded }">
      <view class="preview-toolbar">
        <text>富文本内容</text>
        <button class="zoom-button" @click="toggleRichTextExpanded">{{ richTextExpanded ? '缩小' : '放大阅读' }}</button>
      </view>
      <rich-text class="rich-text-preview" :nodes="asset.richContent || ''" />
    </view>
    <view v-else-if="previewKind() === 'external'" class="preview-state">
      <text>{{ asset.assetType === 'LIVE' ? '这是一个直播内容。' : '这是一个外部链接。' }}</text>
      <button class="fallback-button" @click="openControlledFile">打开受控链接</button>
    </view>
    <view v-else class="preview-state">
      <text>{{ previewKind() === 'office' ? '当前移动端不支持内嵌 Office 预览。' : '当前素材暂不支持在线预览。' }}</text>
      <button class="fallback-button" @click="openControlledFile">受控打开或下载</button>
    </view>
  </view>
</template>

<style scoped lang="less">
.asset-preview {
  min-height: 260rpx;
  overflow: hidden;
  border-radius: 0;
  background: #f6f7f9;
}

.asset-preview.is-long-document {
  overflow: visible;
}

.video-preview {
  display: block;
  width: 100%;
  height: 500rpx;
  background: #17181c;
}

.video-stage {
  position: relative;
}

.video-speed-bar {
  position: absolute;
  top: 16rpx;
  right: 16rpx;
  display: flex;
  padding: 6rpx 10rpx;
  align-items: center;
  gap: 6rpx;
  border-radius: 12rpx;
  background: rgb(15 23 42 / 72%);
}

.video-speed-bar__label {
  color: #e2e8f0;
  font-size: 22rpx;
}

.video-speed-bar__item {
  margin: 0;
  padding: 0 12rpx;
  border: 0;
  border-radius: 8rpx;
  background: transparent;
  color: #e2e8f0;
  font-size: 22rpx;
  line-height: 44rpx;
}

.video-speed-bar__item--active {
  background: #2563eb;
  color: #fff;
}

.preview-toolbar {
  display: flex;
  min-height: 68rpx;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 0 28rpx;
  border-bottom: 1rpx solid #f1f1f3;
  color: #17181c;
  font-size: 24rpx;
  font-weight: 700;
}

.zoom-button {
  margin: 0;
  padding: 0 22rpx;
  border: 0;
  border-radius: 28rpx;
  background: #2563eb;
  color: #fff;
  font-size: 22rpx;
  line-height: 52rpx;
}

.viewer-shell,
.rich-text-shell {
  display: flex;
  min-height: 420rpx;
  flex-direction: column;
  overflow: auto;
  background: #fff;
}

.viewer-shell.is-long-document {
  overflow: visible;
  height: auto;
}

.viewer-shell.expanded,
.rich-text-shell.expanded {
  position: fixed;
  z-index: 20;
  inset: 0;
  width: 100vw;
  height: 100vh;
  border-radius: 0;
}

.image-preview {
  display: block;
  width: 100%;
}

.rich-text-preview {
  min-height: 260rpx;
  padding: 28rpx;
  box-sizing: border-box;
  background: #fff;
  color: #17181c;
  line-height: 1.7;
}

.rich-text-preview :deep(img) {
  max-width: 100%;
  height: auto;
}

.rich-text-preview :deep(p) {
  margin: 0 0 20rpx;
}

.rich-text-preview :deep(h1),
.rich-text-preview :deep(h2),
.rich-text-preview :deep(h3) {
  margin: 22rpx 0 14rpx;
  color: #17181c;
  font-weight: 700;
}

.rich-text-shell.expanded .rich-text-preview {
  min-height: calc(100vh - 76rpx);
}

.preview-state {
  display: flex;
  min-height: 260rpx;
  padding: 48rpx 28rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 24rpx;
  box-sizing: border-box;
  color: #8d8f96;
  font-size: 25rpx;
  text-align: center;
}

.preview-error {
  color: #2563eb;
}

.fallback-button {
  margin: 0;
  padding: 0 26rpx;
  border: 0;
  border-radius: 32rpx;
  background: #2563eb;
  color: #fff;
  font-size: 24rpx;
  line-height: 60rpx;
}
</style>
