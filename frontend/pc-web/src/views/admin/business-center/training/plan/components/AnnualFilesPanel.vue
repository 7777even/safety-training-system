<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import { uploadAssetFile } from '@/api/asset';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import { getAnnualPlans, saveAnnualPlan, getAnnualPlanFile, type AnnualPlan } from '@/api/training-approval';
/** 年度文件独立维护，计划只保留绑定时文件快照。 */
const emit = defineEmits<{ changed: [] }>();
const user = useUserStore();
const canEdit = computed(() => hasPermission(user.userInfo, 'training:annual:edit'));
const rows = ref<AnnualPlan[]>([]);
const loading = ref(false);
const busy = ref(false);
const uploadVisible = ref(false);
const editing = ref<AnnualPlan>();
const year = ref(new Date().getFullYear());
const name = ref('');
/** 查询当前机构年度文件，失败清空候选。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    rows.value = await getAnnualPlans();
  } catch (e) {
    rows.value = [];
    ElMessage.error(e instanceof Error ? e.message : '年度文件读取失败');
  } finally {
    loading.value = false;
  }
}
/** 编辑沿用已读版本；上传完成才保存年度记录。 */
function edit(row?: AnnualPlan): void {
  editing.value = row;
  year.value = row?.planYear || new Date().getFullYear();
  name.value = row?.planName || '';
}
async function uploaded(files: UnifiedUploadedFile[]): Promise<void> {
  const file = files[0];
  if (!file?.fileInfoId) return;
  busy.value = true;
  try {
    await saveAnnualPlan(
      { planYear: year.value, planName: name.value.trim(), fileInfoId: file.fileInfoId, version: editing.value?.version },
      editing.value?.annualPlanId
    );
    ElMessage.success('年度文件已保存，已有计划关联快照保持有效');
    edit();
    await load();
    emit('changed');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '年度文件保存失败');
  } finally {
    busy.value = false;
  }
}
/** 下载按年度记录授权，使用原文件名。 */
async function download(row: AnnualPlan): Promise<void> {
  try {
    const blob = await getAnnualPlanFile(row.annualPlanId);
    const url = globalThis.URL.createObjectURL(blob);
    const a = globalThis.document.createElement('a');
    a.href = url;
    a.download = row.fileName;
    a.click();
    globalThis.URL.revokeObjectURL(url);
  } catch {
    ElMessage.error('年度文件下载失败，请刷新后重试');
  }
}
/** 表格行来自年度文件列表，收窄Element Plus通用行类型。 */
function asAnnual(row: unknown): AnnualPlan {
  return row as AnnualPlan;
}
onMounted(load);
</script>
<template>
  <div v-loading="loading || busy" class="annual-files">
    <ElForm inline class="annual-files__form" @submit.prevent>
      <ElFormItem label="年度">
        <ElInputNumber v-model="year" :min="1900" :max="9999" controls-position="right" class="annual-files__year" />
      </ElFormItem>
      <ElFormItem label="年度计划名称">
        <ElInput v-model="name" maxlength="150" placeholder="请输入年度计划名称" class="annual-files__name" />
      </ElFormItem>
      <ElFormItem v-if="canEdit">
        <ElButton type="primary" :disabled="!name.trim() || busy" @click="uploadVisible = true">
          {{ editing ? '上传替换文件' : '上传并创建' }}
        </ElButton>
        <ElButton v-if="editing" @click="edit()">取消编辑</ElButton>
      </ElFormItem>
    </ElForm>
    <ElAlert
      class="annual-files__tip"
      :title="editing ? `正在替换「${editing.planName}」的文件，上传后将生成新版本。` : '上传公司年度培训计划文件，可关联多个培训计划；文件更新不会改变已关联计划的原始依据。'"
      :type="editing ? 'warning' : 'info'"
      :closable="false"
      show-icon
    />
    <ElTable :data="rows" row-key="annualPlanId" empty-text="暂无年度文件" class="annual-files__table">
      <ElTableColumn prop="planYear" label="年度" width="88" />
      <ElTableColumn prop="planName" label="名称" min-width="160" show-overflow-tooltip />
      <ElTableColumn prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
      <ElTableColumn label="操作" width="130">
        <template #default="{ row }">
          <div class="annual-files__actions">
            <ElButton link type="primary" @click="download(asAnnual(row))">下载</ElButton>
            <ElButton v-if="canEdit" link type="primary" @click="edit(asAnnual(row))">替换文件</ElButton>
          </div>
        </template>
      </ElTableColumn>
      <template #empty>
        <ElEmpty description="暂无年度文件，上传后即可在计划中关联" :image-size="52" />
      </template>
    </ElTable>
  </div>
  <UnifiedFileUpload
    v-model="uploadVisible"
    mode="dialog"
    :multiple="false"
    :limit="1"
    :upload-request="(file, onProgress, signal) => uploadAssetFile(file, onProgress, 'general', signal)"
    @success="uploaded"
  />
</template>
<style scoped lang="less">
.annual-files__form {
  row-gap: 4px;
}
/* 年度与名称按内容定宽，窄侧栏下自动换行而不是撑破容器。 */
.annual-files__year {
  width: 118px;
}
.annual-files__name {
  width: 220px;
}
.annual-files__tip {
  margin-bottom: 12px;
}
.annual-files__table {
  width: 100%;
}
/* 两个文字链操作并排，避免继承 Element Plus 默认外边距后错行。 */
.annual-files__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.annual-files__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
