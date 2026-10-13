<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { onBeforeUnmount, ref } from 'vue';
import { downloadMyCertificateFile, getMyCertificates, type LearnerCertificate } from '@/api/learner-certificate';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { revokeProtectedImageUrl } from '@/libs/protected-image';
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue';

const certificates = ref<LearnerCertificate[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const previewUrl = ref<string | null>(null);
const previewVisible = ref(false);
const downloadingId = ref<number | null>(null);
/** 当前页证书的受控临时图片地址，重新加载时替换。 */
const coverUrls = ref<Record<number, string | null>>({});
let loadVersion = 0;

/** 来源类型转中文，不回显英文枚举码。 */
function sourceTypeText(type: LearnerCertificate['sourceType']): string {
  return { COURSE: '课程', EXAM: '理论考试', TRAINING: '培训' }[type] ?? type;
}

/** 签发时间收敛为日期，缺失时用中性文案。 */
function issuedDateText(issuedTime: string): string {
  return issuedTime ? issuedTime.slice(0, 10) : '签发日期待同步';
}
/** 历史来源名称缺失时仍能凭类型和 ID 找到对应业务。 */
function certificateTitle(certificate: LearnerCertificate): string {
  return certificate.sourceName || `${sourceTypeText(certificate.sourceType)} #${certificate.sourceId}`;
}
/** 双端均带鉴权头下载证书临时图片，交由 image 展示。 */
async function loadCertificateImage(certificateId: number): Promise<string> {
  const download = downloadMyCertificateFile(certificateId);
  const result = await new Promise<{ tempFilePath?: string; statusCode: number }>((resolve, reject) => {
    uni.downloadFile({ url: download.url, header: download.header, success: resolve, fail: reject });
  });
  if (result.statusCode !== 200 || !result.tempFilePath) throw new Error('证书图片加载失败');
  return result.tempFilePath;
}
/** 当前页缩略图复用本人文件端点；读取失败保留文字占位。 */
async function loadCovers(items: LearnerCertificate[], version: number): Promise<void> {
  const entries = await Promise.all(items.map(async (certificate) => {
    try {
      return [certificate.certificateId, await loadCertificateImage(certificate.certificateId)] as const;
    } catch {
      return [certificate.certificateId, null] as const;
    }
  }));
  if (version !== loadVersion) {
    entries.forEach(([, url]) => revokeProtectedImageUrl(url));
    return;
  }
  Object.values(coverUrls.value).forEach(revokeProtectedImageUrl);
  coverUrls.value = Object.fromEntries(entries);
}

async function load(): Promise<void> {
  const version = ++loadVersion;
  loading.value = true;
  errorMessage.value = '';
  try {
    const page = await getMyCertificates(1, 20);
    if (version !== loadVersion) return;
    certificates.value = page.list;
    await loadCovers(page.list, version);
  } catch (error) {
    if (version !== loadVersion) return;
    errorMessage.value = error instanceof Error ? error.message : '证书列表加载失败，请稍后重试。';
  } finally {
    if (version === loadVersion) loading.value = false;
  }
}

/** 在线预览仍从本人受控端点读取图片，不依赖公开文件地址。 */
async function openPreview(certificate: LearnerCertificate): Promise<void> {
  try {
    previewUrl.value = await loadCertificateImage(certificate.certificateId);
    previewVisible.value = true;
  } catch {
    uni.showToast({ title: '证书图片加载失败，请稍后重试', icon: 'none' });
  }
}

function closePreview(): void {
  previewVisible.value = false;
  revokeProtectedImageUrl(previewUrl.value);
  previewUrl.value = null;
}

/** 下载本人证书原件：受控端点取文件，H5 保存 PNG，其它端走原生文件菜单。 */
async function downloadCertificate(certificate: LearnerCertificate): Promise<void> {
  if (downloadingId.value !== null) return;
  downloadingId.value = certificate.certificateId;
  try {
    const filePath = await loadCertificateImage(certificate.certificateId);
    // #ifdef H5
    const link = globalThis.document.createElement('a');
    link.href = filePath;
    link.download = `${certificate.certificateNo}.png`;
    link.click();
    revokeProtectedImageUrl(filePath);
    // #endif
    // #ifndef H5
    await new Promise<void>((resolve, reject) => {
      uni.openDocument({ filePath, showMenu: true, success: () => resolve(), fail: reject });
    });
    // #endif
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '证书下载失败', icon: 'none' });
  } finally {
    downloadingId.value = null;
  }
}

onShow(() => {
  void load();
});

onBeforeUnmount(() => {
  ++loadVersion;
  Object.values(coverUrls.value).forEach(revokeProtectedImageUrl);
  revokeProtectedImageUrl(previewUrl.value);
});
</script>

<template>
  <MobileBaseLayout>
    <view class="page">
      <view v-if="loading" class="state-box"><text class="state-box__text">证书加载中…</text></view>

      <view v-else-if="errorMessage" class="state-box">
        <text class="state-box__text">{{ errorMessage }}</text>
        <button class="state-box__retry" @tap="load">重新加载</button>
      </view>

      <view v-else-if="!certificates.length" class="state-box">
        <UniIcons type="checkbox-filled" size="48" color="#cbd5e1" />
        <text class="state-box__text">暂未获得证书</text>
        <text class="state-box__hint">完成培训、课程或考试达标后自动签发</text>
      </view>

      <view v-else class="cert-list">
        <view v-for="certificate in certificates" :key="certificate.certificateId" class="cert-card">
          <view class="cert-card__cover" @tap="openPreview(certificate)">
            <image v-if="coverUrls[certificate.certificateId]" class="cert-card__image" :src="coverUrls[certificate.certificateId]!" mode="aspectFit" />
            <view v-else class="cert-card__placeholder">
              <UniIcons type="medal" size="44" color="#2563eb" />
              <text>证书图片暂不可用</text>
            </view>
          </view>
          <view class="cert-card__details">
            <view class="cert-card__badge">
              <text class="cert-card__badge-text">{{ sourceTypeText(certificate.sourceType) }}证书</text>
            </view>
            <text class="cert-card__title">{{ certificateTitle(certificate) }}</text>
            <text class="cert-card__time">签发于 {{ issuedDateText(certificate.issuedTime) }}</text>
            <text class="cert-card__no">编号 {{ certificate.certificateNo }}</text>
          </view>
          <view class="cert-card__actions">
            <button class="cert-card__btn" @tap="openPreview(certificate)">在线查看</button>
            <button class="cert-card__btn cert-card__btn--ghost" :loading="downloadingId === certificate.certificateId" @tap="downloadCertificate(certificate)">
              下载
            </button>
          </view>
        </view>
      </view>

      <view v-if="previewVisible && previewUrl" class="preview-layer" @tap="closePreview">
        <view class="preview-layer__body" @tap.stop>
          <image class="preview-layer__image" :src="previewUrl" mode="aspectFit" />
          <button class="preview-layer__close" @tap="closePreview">关闭</button>
        </view>
      </view>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.page {
  padding: 24rpx 0 64rpx;
}

.state-box {
  display: flex;
  margin-top: 120rpx;
  flex-direction: column;
  align-items: center;
  gap: 18rpx;
}

.state-box__text {
  color: var(--color-text-secondary);
  font-size: 28rpx;
}

.state-box__hint {
  color: var(--color-text-tertiary, #94a3b8);
  font-size: 22rpx;
}

.state-box__retry {
  min-height: 72rpx;
  padding: 0 44rpx;
  border-radius: 999rpx;
  background: #2563eb;
  color: #fff;
  font-size: 26rpx;
  line-height: 72rpx;
}

.state-box__retry::after {
  border: 0;
}

.cert-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.cert-card {
  display: flex;
  overflow: hidden;
  flex-direction: column;
  border-radius: 24rpx;
  background: var(--color-bg-surface);
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 6%);
}
.cert-card__cover {
  display: flex;
  height: 290rpx;
  align-items: center;
  justify-content: center;
  background: #f1f5f9;
}
.cert-card__image {
  width: 100%;
  height: 100%;
}
.cert-card__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  color: var(--color-text-secondary);
  font-size: 22rpx;
}
.cert-card__details {
  display: flex;
  padding: 28rpx 32rpx 0;
  flex-direction: column;
  align-items: flex-start;
}

.cert-card__badge {
  display: flex;
  align-self: flex-start;
  padding: 8rpx 18rpx;
  align-items: center;
  gap: 8rpx;
  border-radius: 999rpx;
  background: var(--color-primary-subtle);
}

.cert-card__badge-text {
  color: #2563eb;
  font-size: 22rpx;
  font-weight: 600;
}

.cert-card__no {
  margin-top: 8rpx;
  color: var(--color-text-tertiary, #94a3b8);
  font-size: 22rpx;
  overflow-wrap: anywhere;
}
.cert-card__title {
  margin-top: 18rpx;
  color: var(--color-text-primary);
  font-size: 32rpx;
  font-weight: 700;
}

.cert-card__time {
  margin-top: 10rpx;
  color: var(--color-text-secondary);
  font-size: 23rpx;
}

.cert-card__actions {
  display: flex;
  padding: 24rpx 32rpx 30rpx;
  gap: 18rpx;
}

.cert-card__btn {
  flex: 1;
  min-height: 76rpx;
  margin: 0;
  border-radius: 999rpx;
  background: #2563eb;
  color: #fff;
  font-size: 26rpx;
  line-height: 76rpx;
}

.cert-card__btn::after {
  border: 0;
}

.cert-card__btn--ghost {
  border: 1rpx solid #2563eb;
  background: var(--color-bg-surface);
  color: #2563eb;
}

.preview-layer {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: flex;
  padding: 40rpx;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgb(15 23 42 / 86%);
}

.preview-layer__body {
  display: flex;
  width: 100%;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
}

.preview-layer__image {
  width: 100%;
  flex: 1;
  min-height: 0;
}

.preview-layer__close {
  min-width: 200rpx;
  min-height: 72rpx;
  margin: 0;
  border-radius: 999rpx;
  background: #fff;
  color: #1e293b;
  font-size: 26rpx;
  line-height: 72rpx;
}

.preview-layer__close::after {
  border: 0;
}
</style>
