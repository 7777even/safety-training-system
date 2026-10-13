<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { Download, RefreshLeft, Search, View } from '@element-plus/icons-vue';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { CERTIFICATE_SOURCE_TYPE_OPTIONS, getEnumLabel } from '@/constants/enum-options';
import { useUserNameResolver } from '@/composables/use-user-name';
import { downloadCertificate, getCertificates, type Certificate } from '@/api/m4-outcome';

type CertificateSourceType = Certificate['sourceType'];
/** 来源类型与证书 openapi 的 sourceType 枚举一致，界面只展示中文口径；字典统一来自常量。 */
const SOURCE_TYPE_OPTIONS = CERTIFICATE_SOURCE_TYPE_OPTIONS;
/** 管理端已签发证书分页。 */
const certificates = ref<Certificate[]>([]);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const total = ref(0);
const sourceType = ref<CertificateSourceType>();
/** 逐行记录下载中的证书，避免整表 loading 遮挡其它行。 */
const downloadingId = ref<number>();
/** 在线预览复用同一受控证书文件，预览地址在使用后统一释放。 */
const previewVisible = ref(false);
const previewUrl = ref('');
const previewLoadingId = ref<number>();
/** 持证人只回传主键，统一走人员选择器解析为姓名，失败退回编号。 */
const { loadUserNames, userLabel } = useUserNameResolver();

function sourceTypeLabel(type: CertificateSourceType): string {
  return getEnumLabel(type, SOURCE_TYPE_OPTIONS);
}
/** 查询管理员可见的签发记录。 */
async function loadCertificates(): Promise<void> {
  loading.value = true;
  try {
    const params: Record<string, unknown> = { pageNum: pageNum.value, pageSize: pageSize.value };
    if (sourceType.value) params['query.sourceType'] = sourceType.value;
    const page = await getCertificates(params);
    certificates.value = page.list;
    total.value = page.total;
    await loadUserNames(page.list.map((item) => item.holderUserId));
  } finally {
    loading.value = false;
  }
}
function search(): void {
  pageNum.value = 1;
  void loadCertificates();
}
function resetFilter(): void {
  sourceType.value = undefined;
  search();
}
/** 下载服务端已生成的证书文件。 */
async function download(row: Certificate): Promise<void> {
  if (!row.certificateFileId) return;
  downloadingId.value = row.certificateId;
  try {
    const url = globalThis.URL.createObjectURL(await downloadCertificate(row.certificateId));
    const link = globalThis.document.createElement('a');
    link.href = url;
    link.download = `${row.certificateNo}.png`;
    link.click();
    globalThis.URL.revokeObjectURL(url);
  } finally {
    downloadingId.value = undefined;
  }
}
/** 在线查看服务端已生成的证书文件，不额外生成第二份文件。 */
async function preview(row: Certificate): Promise<void> {
  if (!row.certificateFileId) return;
  previewLoadingId.value = row.certificateId;
  try {
    const blob = await downloadCertificate(row.certificateId);
    if (previewUrl.value) globalThis.URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = globalThis.URL.createObjectURL(blob);
    previewVisible.value = true;
  } finally {
    previewLoadingId.value = undefined;
  }
}
onMounted(loadCertificates);
onBeforeUnmount(() => {
  if (previewUrl.value) globalThis.URL.revokeObjectURL(previewUrl.value);
});
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline>
        <ElFormItem label="来源类型">
          <ElSelect v-model="sourceType" clearable placeholder="全部来源" class="filter-select" @change="search">
            <ElOption v-for="item in SOURCE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem class="filter-actions">
          <ElButton type="primary" :icon="Search" @click="search">查询</ElButton>
          <ElButton :icon="RefreshLeft" @click="resetFilter">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>已签发证书列表</strong>
        </div>
      </template>
      <ElTable v-loading="loading" :data="certificates" row-key="certificateId" empty-text="暂无签发记录">
        <ElTableColumn prop="certificateNo" label="证书编号" width="200" />
        <ElTableColumn label="持证人" min-width="150">
          <template #default="{ row }">{{ userLabel((row as Certificate).holderUserId) }}</template>
        </ElTableColumn>
        <ElTableColumn label="来源类型" width="130">
          <template #default="{ row }">
            <ElTag effect="plain">{{ sourceTypeLabel((row as Certificate).sourceType) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="来源编号" width="120">
          <template #default="{ row }">{{ (row as Certificate).sourceId }}</template>
        </ElTableColumn>
        <ElTableColumn label="签发时间" width="180">
          <template #default="{ row }">{{ (row as Certificate).issuedTime || '—' }}</template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="{ row }">
            <ElTag :type="(row as Certificate).certificateStatus === 'ISSUED' ? 'success' : 'info'">
              {{ (row as Certificate).certificateStatus === 'ISSUED' ? '已签发' : (row as Certificate).certificateStatus }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="180">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              :icon="View"
              :loading="previewLoadingId === (row as Certificate).certificateId"
              :disabled="!(row as Certificate).certificateFileId"
              @click="preview(row as Certificate)"
            >预览</ElButton>
            <ElButton
              link
              type="primary"
              :icon="Download"
              :loading="downloadingId === (row as Certificate).certificateId"
              :disabled="!(row as Certificate).certificateFileId"
              @click="download(row as Certificate)"
            >下载</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="LIST_PAGE_SIZES" :total="total" layout="total, sizes, prev, pager, next" @current-change="loadCertificates" @size-change="loadCertificates" />
      </div>
    </ElCard>
    <ElDialog v-model="previewVisible" title="证书在线预览" width="720px" destroy-on-close>
      <img v-if="previewUrl" class="certificate-preview" :src="previewUrl" alt="证书预览" >
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.filter-select {
  width: 180px;
}

.certificate-preview {
  display: block;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
}
</style>
