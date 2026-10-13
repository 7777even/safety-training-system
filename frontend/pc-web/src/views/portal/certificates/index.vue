<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Download, Medal, Refresh, View } from '@element-plus/icons-vue';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { downloadCertificate, getCertificates, type Certificate } from '@/api/m4-outcome';

type CertificateSourceType = Certificate['sourceType'];
/** 门户只按来源类型分组，展示名称与后台签发规则保持一致。 */
const SOURCE_TABS: Array<{ label: string; value: CertificateSourceType | '' }> = [
  { label: '全部', value: '' },
  { label: '课程证书', value: 'COURSE' },
  { label: '考试证书', value: 'EXAM' },
  { label: '培训成果证书', value: 'TRAINING' }
];
const SOURCE_TITLES: Record<CertificateSourceType, string> = {
  TRAINING: '培训成果证书',
  COURSE: '课程证书',
  EXAM: '考试证书'
};
const rows = ref<Certificate[]>([]);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const total = ref(0);
const activeSource = ref<CertificateSourceType | ''>('');
/** 逐张记录下载中的证书，避免整页 loading 遮挡其它卡片。 */
const downloadingId = ref<number>();
/** 在线查看与下载使用同一份本人受控证书文件。 */
const previewVisible = ref(false);
const previewUrl = ref('');
const previewLoadingId = ref<number>();
/** 当前页证书缩略图的 Blob URL，换页与卸载时释放。 */
const coverUrls = ref<Record<number, string>>({});
let coverVersion = 0;
/** 无数据时区分「确实没有证书」与「当前筛选没有证书」。 */
const emptyDescription = computed(() => (activeSource.value ? '当前类型下暂无证书' : '暂未获得证书'));

function sourceTitle(type: CertificateSourceType): string {
  return SOURCE_TITLES[type] ?? '证书';
}
/** 来源删除时保留可追溯的类型与 ID，不把证书编号误当业务名称。 */
function certificateTitle(row: Certificate): string {
  return row.sourceName || `${sourceTitle(row.sourceType)} #${row.sourceId}`;
}
/** 证书缩略图读取本人受控文件；换页时释放旧的 Blob URL。 */
async function loadCovers(items: Certificate[], version: number): Promise<void> {
  const entries = await Promise.all(items.map(async (row) => {
    try {
      return [row.certificateId, globalThis.URL.createObjectURL(await downloadCertificate(row.certificateId, true))] as const;
    } catch {
      return [row.certificateId, ''] as const;
    }
  }));
  if (version !== coverVersion) {
    entries.forEach(([, url]) => { if (url) globalThis.URL.revokeObjectURL(url); });
    return;
  }
  Object.values(coverUrls.value).forEach(globalThis.URL.revokeObjectURL);
  coverUrls.value = Object.fromEntries(entries);
}
async function load(): Promise<void> {
  const version = ++coverVersion;
  loading.value = true;
  try {
    const params: Record<string, unknown> = { pageNum: pageNum.value, pageSize: pageSize.value };
    if (activeSource.value) params['query.sourceType'] = activeSource.value;
    const page = await getCertificates(params, true);
    if (version !== coverVersion) return;
    rows.value = page.list;
    total.value = page.total;
    await loadCovers(page.list, version);
  } finally {
    if (version === coverVersion) loading.value = false;
  }
}
/** 切换来源类型时回到第一页，避免停留在越界页码。 */
function selectSource(value: CertificateSourceType | ''): void {
  if (activeSource.value === value) return;
  activeSource.value = value;
  pageNum.value = 1;
  void load();
}
async function download(row: Certificate): Promise<void> {
  downloadingId.value = row.certificateId;
  try {
    const url = globalThis.URL.createObjectURL(await downloadCertificate(row.certificateId, true));
    const link = globalThis.document.createElement('a');
    link.href = url;
    link.download = `${row.certificateNo}.png`;
    link.click();
    globalThis.URL.revokeObjectURL(url);
  } finally {
    downloadingId.value = undefined;
  }
}
/** 在线查看本人证书；仍走本人文件端点，不产生额外授权口径。 */
async function preview(row: Certificate): Promise<void> {
  previewLoadingId.value = row.certificateId;
  try {
    const blob = await downloadCertificate(row.certificateId, true);
    if (previewUrl.value) globalThis.URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = globalThis.URL.createObjectURL(blob);
    previewVisible.value = true;
  } finally {
    previewLoadingId.value = undefined;
  }
}
onMounted(load);
onBeforeUnmount(() => {
  ++coverVersion;
  Object.values(coverUrls.value).forEach(globalThis.URL.revokeObjectURL);
  if (previewUrl.value) globalThis.URL.revokeObjectURL(previewUrl.value);
});
</script>
<template>
  <section class="portal-list-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY CERTIFICATES</p>
        <h1>我的证书</h1>
        <p>查看并下载本人达标后自动签发的证书。</p>
      </div>
      <ElButton :icon="Refresh" @click="load">刷新</ElButton>
    </header>

    <nav class="portal-list-page__tabs" aria-label="证书来源筛选">
      <button
        v-for="item in SOURCE_TABS"
        :key="item.value || 'ALL'"
        type="button"
        class="portal-list-page__tab"
        :class="{ 'portal-list-page__tab--active': activeSource === item.value }"
        @click="selectSource(item.value)"
      >
        {{ item.label }}
      </button>
    </nav>

    <div v-if="!loading && !rows.length" class="portal-list-state">
      <ElEmpty :description="emptyDescription">
        <span class="portal-empty-hint">
          {{ activeSource ? '切换其它类型或查看全部证书。' : '完成培训或课程并达标后，证书会自动出现在这里。' }}
        </span>
      </ElEmpty>
    </div>
    <div v-else class="portal-list-grid">
      <ElCard v-for="row in rows" :key="row.certificateId" class="portal-list-card" shadow="never">
        <div class="portal-list-card__cover">
          <img v-if="coverUrls[row.certificateId]" :src="coverUrls[row.certificateId]" :alt="`${certificateTitle(row)}证书`">
          <div v-else class="portal-list-card__cover-placeholder certificate-cover">
            <ElIcon><Medal /></ElIcon>
          </div>
        </div>
        <div class="portal-list-card__content">
          <div class="portal-list-card__header">
            <h2>{{ certificateTitle(row) }}</h2>
            <ElTag :type="row.certificateStatus === 'ISSUED' ? 'success' : 'info'">
              {{ row.certificateStatus === 'ISSUED' ? '已签发' : row.certificateStatus }}
            </ElTag>
          </div>
          <p class="certificate-line">{{ sourceTitle(row.sourceType) }}</p>
          <p class="certificate-line">证书编号：{{ row.certificateNo }}</p>
          <p class="certificate-line">签发时间：{{ row.issuedTime || '—' }}</p>
          <div class="portal-list-card__actions">
            <ElButton
              :icon="View"
              :loading="previewLoadingId === row.certificateId"
              @click="preview(row)"
            >
              在线查看
            </ElButton>
            <ElButton
              type="primary"
              :icon="Download"
              :loading="downloadingId === row.certificateId"
              @click="download(row)"
            >
              下载证书
            </ElButton>
          </div>
        </div>
      </ElCard>
    </div>

    <div v-if="total > pageSize" class="portal-list-page__pagination">
      <ElPagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="LIST_PAGE_SIZES"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <ElDialog v-model="previewVisible" title="证书在线查看" width="720px" destroy-on-close>
      <img v-if="previewUrl" class="certificate-preview" :src="previewUrl" alt="证书预览" >
    </ElDialog>
  </section>
</template>
<style scoped lang="less">
/* 证书卡沿用门户任务卡骨架，封面区用渐变与奖章图标表达授奖语义。 */
.certificate-cover {
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
}

.certificate-cover .el-icon {
  color: var(--color-primary);
  font-size: 34px;
}

.portal-list-card__cover img {
  object-fit: contain;
}

.certificate-line {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.portal-empty-hint {
  display: block;
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  font-size: 13px;
}

.certificate-preview {
  display: block;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
}
</style>
