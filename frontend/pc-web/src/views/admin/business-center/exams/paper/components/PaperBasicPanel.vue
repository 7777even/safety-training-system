<script setup lang="ts">
import { computed, ref } from 'vue';
import { uploadAssetFile } from '@/api/asset';
import type { QuestionBankCategory } from '@/api/question';
import type { PaperType } from '@/api/paper';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import { PAPER_TYPE_OPTIONS } from '@/constants/exam-options';

type PaperBasicForm = {
  paperCode: string;
  paperName: string;
  paperType: PaperType;
  paperCategoryId?: number;
  coverFileId?: number;
  status: 0 | 1;
};

defineProps<{
  paperId: number;
  bankCategories: QuestionBankCategory[];
  saving: boolean;
  coverFileName: string;
}>();
const basic = defineModel<PaperBasicForm>('basic', { required: true });
const emit = defineEmits<{
  invalidatePreview: [];
  save: [];
  next: [];
  coverUploaded: [files: UnifiedUploadedFile[]];
}>();
const coverUploadVisible = ref(false);
const currentType = computed(() => PAPER_TYPE_OPTIONS.find((item) => item.value === basic.value.paperType)!);

/** 复用素材上传契约，将上传完成事件交给工作台同步试卷状态。 */
function requestCoverUpload(file: globalThis.File, onProgress: (percent: number) => void): Promise<UnifiedUploadedFile> {
  return uploadAssetFile(file, onProgress, 'exam-covers');
}
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>基础信息</template>
    <div class="admin-detail-page__form-scroll">
      <ElForm label-width="120px" class="admin-detail-form">
        <ElRow :gutter="24">
          <ElCol :span="12">
            <ElFormItem label="试卷编码" required>
              <ElInput v-model="basic.paperCode" placeholder="请输入试卷编码" :disabled="Boolean(paperId)" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="试卷名称" required><ElInput v-model="basic.paperName" placeholder="请输入试卷名称" /></ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="组卷类型">
              <ElRadioGroup v-model="basic.paperType" :disabled="Boolean(paperId)" @change="emit('invalidatePreview')">
                <ElRadioButton v-for="item in PAPER_TYPE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</ElRadioButton>
              </ElRadioGroup>
              <ElText class="hint">{{ currentType.description }}</ElText>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="试卷分类">
              <ElSelect v-model="basic.paperCategoryId" clearable placeholder="请选择题库分类" class="bank-select">
                <ElOption v-for="item in bankCategories" :key="item.bankCategoryId" :label="item.categoryName" :value="item.bankCategoryId" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="封面文件">
              <ElButton @click="coverUploadVisible = true">{{ basic.coverFileId ? '重新上传封面' : '选择并上传封面' }}</ElButton>
              <ElText v-if="basic.coverFileId" class="hint">{{ coverFileName || `已选择文件 #${basic.coverFileId}` }}</ElText>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="启用状态"><ElSwitch v-model="basic.status" :active-value="1" :inactive-value="0" /></ElFormItem>
          </ElCol>
        </ElRow>
      </ElForm>
    </div>
    <footer class="admin-detail-action-bar">
      <ElButton :loading="saving" @click="emit('save')">{{ paperId ? '保存基础信息' : '创建试卷' }}</ElButton>
      <ElButton type="primary" :loading="saving" @click="emit('next')">
        {{ paperId ? '下一步：组卷配置' : '创建并进入组卷配置' }}
      </ElButton>
    </footer>
    <UnifiedFileUpload
      v-model="coverUploadVisible"
      mode="dialog"
      accept="image/*"
      :multiple="false"
      :limit="1"
      :upload-request="requestCoverUpload"
      @success="emit('coverUploaded', $event)"
    />
  </ElCard>
</template>

<style scoped lang="less">
.hint {
  margin-left: 12px;
  color: var(--color-text-secondary);
}
.bank-select {
  width: 280px;
}
</style>
