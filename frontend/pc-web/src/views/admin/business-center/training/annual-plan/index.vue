<script setup lang="ts">
import AnnualFilesPanel from '../plan/components/AnnualFilesPanel.vue';
/** 公司年度文件管理入口，上传后由管理员在计划工作台关联。 */
</script>
<template>
  <section class="admin-business-page annual-plan-page">
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>公司年度培训计划</strong>
          <span class="annual-plan-page__note">年度文件与培训计划一一对应，计划只保留关联时的文件快照。</span>
        </div>
      </template>
      <AnnualFilesPanel />
    </ElCard>
  </section>
</template>
<style scoped lang="less">
.annual-plan-page__note {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}
</style>
