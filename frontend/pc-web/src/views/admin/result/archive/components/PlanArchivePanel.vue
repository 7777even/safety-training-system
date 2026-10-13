<script setup lang="ts">
import { computed } from 'vue';
import { Check, Download, Upload, Warning } from '@element-plus/icons-vue';
import type { ArchiveView } from '@/api/m4-outcome';
import ArchiveFactsSections from './ArchiveFactsSections.vue';

/** 一期档案事实面板：页签态与路由直达共用同一份事实展示与操作，避免两处模板各自维护。 */
const props = defineProps<{
  archive: ArchiveView;
  archiving: boolean;
  downloading: boolean;
  archivable: boolean;
  downloadable: boolean;
}>();
/** 档案编码与归档说明由页面持有，面板只负责编辑，不自行决定默认值。 */
const archiveCode = defineModel<string>('archiveCode', { required: true });
const versionReason = defineModel<string>('versionReason', { required: true });
const emit = defineEmits<{
  updateArchive: [];
  download: [];
}>();

/**
 * 归档文件格式只由后端事实决定：新生成的档案是 PDF 正本，
 * 2026-09-24 之前落库的历史档案仍是 JSON，页面必须提示重新更新档案，不能只看文件是否存在。
 */
const archiveFileFormat = computed(() => (props.archive.archiveFileIsPdf ? 'PDF' : 'JSON'));
const needsPdfRebuild = computed(() => props.archive.archiveFileIsPdf === false);
</script>
<template>
  <ElCard class="admin-business-page__table plan-archive" shadow="never">
    <template #header>
      <div class="admin-business-page__table-heading">
        <strong>一期档案</strong>
        <div class="admin-business-page__table-actions">
          <ElButton type="primary" :icon="Upload" :loading="archiving" :disabled="!archivable" @click="emit('updateArchive')">更新档案</ElButton>
          <ElButton type="primary" plain :icon="Download" :loading="downloading" :disabled="!downloadable" @click="emit('download')">
            下载档案
          </ElButton>
        </div>
      </div>
    </template>

    <section class="ws-hero">
      <div class="ws-hero__main">
        <p class="ws-hero__eyebrow">TRAINING ARCHIVE</p>
        <div class="ws-hero__title-row">
          <h2 class="ws-hero__title">{{ archive.planName }}</h2>
        </div>
        <div class="ws-hero__meta">
          <span class="ws-hero__chip">培训编号 {{ archive.planCode }}</span>
          <span class="ws-hero__chip">参训 {{ archive.participantCount }} 人</span>
          <span>{{ archive.organizationName }}</span>
        </div>
      </div>
      <div class="ws-hero__aside">
        <div class="ws-hero__state">
          <ElTag :type="downloadable ? 'success' : 'warning'" effect="light">
            {{ downloadable ? '可下载' : '待补全' }}
          </ElTag>
          <small>{{ archive.missingItems.length ? `缺少 ${archive.missingItems.length} 项归档事实` : '归档事实全部完整' }}</small>
        </div>
      </div>
    </section>

    <section class="ws-section archive-checks">
      <header class="ws-section__head">
        <div>
          <h2>归档事实核查</h2>
          <p>档案由成绩、学时与证书等事实汇总，任一项缺失都会阻断下载。</p>
        </div>
        <span class="ws-section__extra">
          {{ archive.missingItems.length ? `缺少 ${archive.missingItems.length} 项` : '全部完整' }}
        </span>
      </header>
      <div class="ws-check-list">
        <div v-if="!archive.missingItems.length" class="ws-check ws-check--pass">
          <span class="ws-check__icon">
            <ElIcon><Check /></ElIcon>
          </span>
          <div class="ws-check__body">
            <strong>归档事实完整</strong>
            <span>成绩、学时与证书事实均已生成，可下载一期一档文件。</span>
          </div>
        </div>
        <div v-for="item in archive.missingItems" :key="item" class="ws-check ws-check--fail">
          <span class="ws-check__icon">
            <ElIcon><Warning /></ElIcon>
          </span>
          <div class="ws-check__body">
            <strong>{{ item }}</strong>
            <span>该项事实尚未生成，请先在培训计划中补齐后再重新归档。</span>
          </div>
        </div>
      </div>
    </section>

    <ArchiveFactsSections :archive="archive" />

    <section class="ws-section">
      <header class="ws-section__head">
        <div>
          <h2>专家查课</h2>
          <p>仅展示本期培训当前绑定检查表的已提交记录；意见与分数不参与学员综合分。</p>
        </div>
        <span class="ws-section__extra">共 {{ archive.expertInspections?.length ?? 0 }} 条</span>
      </header>
      <ElTable :data="archive.expertInspections ?? []" empty-text="暂无已提交专家查课记录">
        <ElTableColumn type="expand">
          <template #default="{ row }">
            <div class="plan-archive__inspection-detail">
              <p>查课摘要：{{ row.summary || '—' }}</p>
              <ElTable :data="row.items" border size="small" empty-text="暂无检查项目答案">
                <ElTableColumn prop="itemName" label="检查项目" min-width="180" />
                <ElTableColumn label="答案" min-width="260">
                  <template #default="{ row: item }">{{ item.itemType === 'SCORE' ? `${item.score ?? '—'} 分` : item.textValue || '—' }}</template>
                </ElTableColumn>
              </ElTable>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="contentName" label="课程" min-width="180" />
        <ElTableColumn label="表单版本" width="100"><template #default="{ row }">v{{ row.versionNo }}</template></ElTableColumn>
        <ElTableColumn prop="evaluatorName" label="评价人" width="130" />
        <ElTableColumn label="提交时间" width="180"><template #default="{ row }">{{ row.submittedTime?.replace('T', ' ') || '—' }}</template></ElTableColumn>
        <ElTableColumn label="总分" width="90"><template #default="{ row }">{{ row.totalScore ?? '—' }}</template></ElTableColumn>
      </ElTable>
    </section>

    <section class="ws-section">
      <header class="ws-section__head">
        <div>
          <h2>归档文件</h2>
          <p>文件由服务端生成并保存，页面只负责触发下载，不参与文件组装。</p>
        </div>
      </header>
      <div class="ws-kv">
        <div class="ws-kv__item">
          <span class="ws-kv__label">培训编号</span>
          <span class="ws-kv__value">{{ archive.planCode }}</span>
        </div>
        <div class="ws-kv__item">
          <span class="ws-kv__label">参训人数</span>
          <span class="ws-kv__value">{{ archive.participantCount }}</span>
        </div>
        <div class="ws-kv__item">
          <span class="ws-kv__label">文件格式</span>
          <span class="ws-kv__value" :class="{ 'ws-kv__value--muted': needsPdfRebuild }">
            {{ archiveFileFormat }}
          </span>
        </div>
        <div class="ws-kv__item">
          <span class="ws-kv__label">文件状态</span>
          <span class="ws-kv__value" :class="{ 'ws-kv__value--muted': !archive.fileInfoId }">
            {{ archive.fileInfoId ? '已生成，可下载' : '未生成' }}
          </span>
        </div>
      </div>
      <!-- 历史 JSON 档案不是 PDF 正本，必须显式提示重新更新档案，避免线下交付拿到旧格式。 -->
      <ElAlert v-if="needsPdfRebuild" type="warning" :closable="false" show-icon title="该档案仍是历史 JSON 档案，需重新更新档案以获得 PDF 正本。">
        <template #default>重新更新后会按当前培训事实生成新的 PDF 档案文件。</template>
      </ElAlert>
    </section>

    <section class="ws-section plan-archive__form">
      <header class="ws-section__head">
        <div>
          <h2>更新档案</h2>
          <p>按当前培训事实生成新的归档快照；结果待补全时后端会拒绝并返回缺失项。</p>
        </div>
      </header>
      <ElForm inline label-width="96px">
        <ElFormItem label="档案编码">
          <ElInput v-model="archiveCode" clearable placeholder="默认取培训计划编号" />
        </ElFormItem>
        <ElFormItem label="归档说明">
          <ElInput v-model="versionReason" clearable placeholder="内部快照原因" />
        </ElFormItem>
      </ElForm>
    </section>
  </ElCard>
</template>
<style scoped lang="less">
/* 事实核查区块与上方概览之间单独留白，其余区块间距由 ws-section 的相邻规则统一控制。 */
.archive-checks {
  margin-top: var(--space-4);
}

/* 档案内容高于卡片时在卡片内部滚动，卡头与列表页保持一致，不把整页撑出滚动条。 */
.plan-archive :deep(.el-card__body) {
  overflow: auto;
}

/* 卡片底部内边距为 0（全局约定），末块表单单独留白，避免贴住卡片下沿。 */
.plan-archive__form {
  margin-bottom: var(--space-3);
}

.plan-archive__inspection-detail {
  padding: 4px 16px 12px 48px;
}
</style>
