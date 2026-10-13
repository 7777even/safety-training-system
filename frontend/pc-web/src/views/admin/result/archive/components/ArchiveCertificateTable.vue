<script setup lang="ts">
import { ref } from 'vue';
import { Download, View } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import AssetViewer from '@/components/asset-viewer/AssetViewer.vue';
import { downloadCertificate, type ArchiveCertificateItem } from '@/api/m4-outcome';
import { CERTIFICATE_SOURCE_TYPE_OPTIONS, getEnumLabel } from '@/constants/enum-options';
import { controlledFileName, saveBlobAsFile } from '@/libs/controlled-file';

/**
 * 档案内的证书清单表格：一期档案与个人档案共用同一种查看/下载交互。
 * <p>证书文件端点的权限就是 certificate:cert:list，与「是否拿到证书清单」同一把门，
 * 因此调用方只在有权限时渲染本组件，组件内不再重复判权。
 */
defineProps<{ certificates: ArchiveCertificateItem[] }>();

const previewVisible = ref(false);
const previewLoading = ref(false);
const previewFile = ref<globalThis.File>();
/** 关闭或切换时作废未完成的请求，避免旧文件覆盖新选择。 */
let previewRequestId = 0;

function certificateRow(row: unknown): ArchiveCertificateItem {
  return row as ArchiveCertificateItem;
}
function certificateSourceText(value: unknown): string {
  return getEnumLabel(
    typeof value === 'string' ? (value as ArchiveCertificateItem['sourceType']) : undefined,
    CERTIFICATE_SOURCE_TYPE_OPTIONS
  );
}
function dateTimeText(value: unknown): string {
  if (!value) return '—';
  return String(value).replace('T', ' ').replace(/:00$/, '');
}

async function openPreview(record: ArchiveCertificateItem): Promise<void> {
  const requestId = ++previewRequestId;
  previewFile.value = undefined;
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    const blob = await downloadCertificate(record.certificateId);
    if (previewVisible.value && requestId === previewRequestId) {
      previewFile.value = new globalThis.File([blob], controlledFileName(record.certificateNo, blob.type), { type: blob.type });
    }
  } catch (error) {
    if (requestId === previewRequestId) {
      previewVisible.value = false;
      ElMessage.error(error instanceof Error ? error.message : '证书预览失败。');
    }
  } finally {
    if (requestId === previewRequestId) previewLoading.value = false;
  }
}

async function saveCertificate(record: ArchiveCertificateItem): Promise<void> {
  try {
    const blob = await downloadCertificate(record.certificateId);
    saveBlobAsFile(blob, controlledFileName(record.certificateNo, blob.type));
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '证书下载失败。');
  }
}

function closePreview(): void {
  previewRequestId += 1;
  previewFile.value = undefined;
  previewLoading.value = false;
}
</script>
<template>
  <ElTable :data="certificates" row-key="certificateId" empty-text="暂无已签发证书">
    <ElTableColumn label="证书编号" min-width="200" show-overflow-tooltip>
      <template #default="{ row }">{{ certificateRow(row).certificateNo }}</template>
    </ElTableColumn>
    <ElTableColumn label="来源类型" width="120">
      <template #default="{ row }">{{ certificateSourceText(certificateRow(row).sourceType) }}</template>
    </ElTableColumn>
    <ElTableColumn label="来源ID" width="110">
      <template #default="{ row }">{{ certificateRow(row).sourceId }}</template>
    </ElTableColumn>
    <ElTableColumn label="签发时间" width="170">
      <template #default="{ row }">{{ dateTimeText(certificateRow(row).issuedTime) }}</template>
    </ElTableColumn>
    <ElTableColumn label="状态" width="110">
      <template #default="{ row }">
        <ElTag :type="certificateRow(row).certificateStatus === 'ISSUED' ? 'success' : 'info'" effect="light">
          {{ certificateRow(row).certificateStatus === 'ISSUED' ? '已签发' : certificateRow(row).certificateStatus }}
        </ElTag>
      </template>
    </ElTableColumn>
    <ElTableColumn label="证书文件" width="150">
      <template #default="{ row }">
        <ElButton :icon="View" link type="primary" @click="openPreview(certificateRow(row))">查看</ElButton>
        <ElButton :icon="Download" link type="primary" @click="saveCertificate(certificateRow(row))">下载</ElButton>
      </template>
    </ElTableColumn>
  </ElTable>

  <ElDialog
    v-model="previewVisible"
    title="证书预览"
    width="min(900px, calc(100vw - 32px))"
    append-to-body
    @close="closePreview"
  >
    <div v-loading="previewLoading" class="archive-certificates__preview">
      <AssetViewer v-if="previewFile" :local-file="previewFile" preview-height="min(70vh, 620px)" />
    </div>
  </ElDialog>
</template>
<style scoped lang="less">
/* 预览弹窗在文件到位前保留最小高度，避免加载中高度塌陷造成弹窗跳动。 */
.archive-certificates__preview {
  min-height: 240px;
}
</style>
