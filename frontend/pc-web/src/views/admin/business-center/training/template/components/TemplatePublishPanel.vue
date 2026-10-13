<script setup lang="ts">
import { computed, ref } from 'vue';
import type { TrainingTemplateStagePayload } from '@/api/training-template';
import type { TrainingContentExecutionConfig } from '@/api/training-plan';
import { summarizeTrainingComposition } from '@/libs/training-composition';
import { formatDuration } from '@/utils/duration';
type TemplateContent = NonNullable<TrainingTemplateStagePayload['contents']>[number];
interface PublishContent extends TemplateContent {
  /** 考试内容模式，仅考试类内容使用。 */
  assessmentMode?: 'QUIZ' | 'FORMAL_EXAM' | null;
  /** 内容执行配置，仅用于发布确认只读查看。 */
  executionConfig?: TrainingContentExecutionConfig | null;
}
interface ResourceOption { value: number; name: string; }
interface Stage extends Omit<TrainingTemplateStagePayload, 'contents'> {
  contents: PublishContent[];
}
const props = defineProps<{
  templateName: string;
  templateCode: string;
  categoryName?: string;
  trainingTypeName?: string;
  trainingStandardName?: string;
  description: string;
  status: 0 | 1;
  stages: Stage[];
  stageCount: number;
  contentCount: number;
  readonly: boolean;
  archived: boolean;
  canPublish: boolean;
  saving: boolean;
  created: boolean;
  courseOptions: ResourceOption[];
  examOptions: ResourceOption[];
  teacherOptions: Array<{ userId: number; userName?: string; nickname?: string }>;
}>();
const emit = defineEmits<{ back: []; publish: [] }>();
const executionDrawerVisible = ref(false);
const selectedContent = ref<PublishContent>();
/** 将表格插槽行收窄为模板内容，避免发布确认列访问字段时丢失类型。 */
function asContent(row: unknown): PublishContent {
  return row as PublishContent;
}
/** 发布前汇总模板阶段和内容学时，便于检查是否明显漏配。 */
const publishSummary = computed(() => {
  const { totalHours, onlineHours } = summarizeTrainingComposition(props.stages);
  return {
    totalHours,
    onlineHours,
    offlineHours: totalHours - onlineHours
  };
});
/** 将契约枚举转换为发布前检查使用的中文语义。 */
function stageTypeText(type: Stage['stageType']): string {
  return { ONLINE: '线上', OFFLINE: '线下', MIXED: '混合' }[type];
}
/** 将内容类型转换为发布前检查使用的中文语义。 */
function contentTypeText(type: Stage['contents'][number]['contentType']): string {
  return { COURSE: '课程', EXAM: '考试', QUESTION_BANK: '题库练习', OFFLINE: '线下活动' }[type];
}
/** 内容资源显示具体课程或考试名称，资源失效时明确提示。 */
function resourceText(content: PublishContent): string {
  if (content.contentType === 'OFFLINE') return '线下内容无需绑定资源';
  if (!content.resourceId) return '未绑定资源';
  const options = content.contentType === 'COURSE' ? props.courseOptions : props.examOptions;
  return options.find((item) => item.value === content.resourceId)?.name ?? '绑定资源已失效，请重新选择';
}
/** 考试模式转换为发布确认可读文案。 */
function assessmentModeText(content: PublishContent): string {
  if (content.contentType !== 'EXAM') return '-';
  if (content.assessmentMode === 'QUIZ') return '测验';
  if (content.assessmentMode === 'FORMAL_EXAM') return '理论考试';
  return content.assessmentMode || '未配置';
}
/** 打开内容执行配置抽屉，发布确认阶段只读展示，不修改模板数据。 */
function openExecutionConfig(content: PublishContent): void {
  selectedContent.value = content;
  executionDrawerVisible.value = true;
}
/** 将执行配置中的教师 ID 转换为发布确认页可读名称。 */
function teacherName(userId?: number | null): string {
  if (!userId) return '未配置';
  const teacher = props.teacherOptions.find((item) => item.userId === userId);
  return teacher?.userName || teacher?.nickname || `用户 ${userId}`;
}
function completionRuleText(value?: string | null): string {
  return value === 'ALL_REQUIRED_ITEMS' ? '完成全部必修内容' : value === 'ALL_ITEMS' ? '完成全部内容' : '未配置';
}
</script>
<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>
      <div class="panel-heading">
        <div>
          <h2>发布确认</h2>
          <p>请逐项检查模板配置，发布后模板、阶段与内容将转为只读。</p>
        </div>
        <ElTag :type="props.archived ? 'info' : props.readonly ? 'success' : 'warning'">
          {{ props.archived ? '已归档' : props.readonly ? '已发布' : '待发布' }}
        </ElTag>
      </div>
    </template>
    <div class="admin-detail-page__form-scroll publish-scroll">
      <section class="summary-section">
        <h3>模板基础信息</h3>
        <ElDescriptions :column="2" border>
          <ElDescriptionsItem label="模板编码">{{ props.templateCode || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="模板名称">{{ props.templateName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="模板分类">{{ props.categoryName || '未分类' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="培训类型">{{ props.trainingTypeName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="培训标准">{{ props.trainingStandardName || '未关联' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="启用状态">
            <ElTag :type="props.status === 1 ? 'success' : 'info'">{{ props.status === 1 ? '启用' : '停用' }}</ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="模板说明" :span="2">{{ props.description || '未填写' }}</ElDescriptionsItem>
        </ElDescriptions>
      </section>
      <section class="summary-section">
        <div class="section-title">
          <div>
            <h3>阶段与内容清单</h3>
            <p>共 {{ props.stageCount }} 个阶段，{{ props.contentCount }} 项内容。</p>
          </div>
        </div>
        <div class="publish-metrics">
          <div class="publish-metric">
            <span>阶段数量</span>
            <strong>{{ props.stageCount }}</strong>
            <small>发布后只读</small>
          </div>
          <div class="publish-metric">
            <span>内容数量</span>
            <strong>{{ props.contentCount }}</strong>
            <small>课程/考试/线下</small>
          </div>
          <div class="publish-metric">
            <span>总学时</span>
            <strong>{{ publishSummary.totalHours }}</strong>
            <small>全部内容合计</small>
          </div>
          <div class="publish-metric">
            <span>线上/线下</span>
            <strong>{{ publishSummary.onlineHours }} / {{ publishSummary.offlineHours }}</strong>
            <small>按内容类型汇总</small>
          </div>
        </div>
        <ElEmpty v-if="props.stages.length === 0" description="暂未编排阶段内容" :image-size="72" />
        <div v-else class="stage-summary-list">
          <article v-for="(stage, index) in props.stages" :key="index" class="stage-summary">
            <header>
              <span class="stage-order">{{ index + 1 }}</span>
              <div>
                <h4>{{ stage.stageName || `未命名阶段 ${index + 1}` }}</h4>
                <p>阶段排序 {{ stage.sortOrder }} · 内容 {{ stage.contents.length }} 项</p>
              </div>
              <div class="stage-fields">
                <span>
                  <b>阶段类型</b>
                  {{ stageTypeText(stage.stageType) }}
                </span>
                <span>
                  <b>要求学时</b>
                  {{ stage.requiredHours ?? 0 }}
                </span>
                <span>
                  <b>启用状态</b>
                  {{ stage.status === 1 ? '启用' : '停用' }}
                </span>
              </div>
            </header>
            <ElTable :data="stage.contents" size="small" class="summary-table" border>
              <ElTableColumn label="内容类型" width="120">
                <template #default="{ row }">{{ contentTypeText(row.contentType) }}</template>
              </ElTableColumn>
              <ElTableColumn label="内容名称" min-width="240">
                <template #default="{ row }">{{ row.contentName || '-' }}</template>
              </ElTableColumn>
              <ElTableColumn label="内容资源" min-width="220">
                <template #default="{ row }">{{ resourceText(asContent(row)) }}</template>
              </ElTableColumn>
              <ElTableColumn label="考试模式" width="110">
                <template #default="{ row }">{{ assessmentModeText(asContent(row)) }}</template>
              </ElTableColumn>
              <ElTableColumn label="必修" width="90">
                <template #default="{ row }">{{ row.isRequired === 1 ? '是' : '否' }}</template>
              </ElTableColumn>
              <ElTableColumn label="学时" width="90">
                <template #default="{ row }">{{ row.requiredHours ?? 0 }}</template>
              </ElTableColumn>
              <ElTableColumn label="状态" width="90">
                <template #default="{ row }">{{ row.status === 1 ? '启用' : '停用' }}</template>
              </ElTableColumn>
              <ElTableColumn label="执行配置" width="110" fixed="right">
                <template #default="{ row }">
                  <ElButton link type="primary" @click="openExecutionConfig(asContent(row))">查看配置</ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </article>
        </div>
      </section>
    </div>
    <ElDrawer v-model="executionDrawerVisible" title="执行配置" direction="rtl" size="460px">
      <template v-if="selectedContent">
        <ElDescriptions :column="1" border>
          <ElDescriptionsItem label="内容名称">{{ selectedContent.contentName || '-' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="内容类型">{{ contentTypeText(selectedContent.contentType) }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="selectedContent.contentType === 'COURSE'" label="完成规则">{{ completionRuleText(selectedContent.executionConfig?.completionRule) }}</ElDescriptionsItem>
          <ElDescriptionsItem v-if="selectedContent.contentType === 'EXAM'" label="考试模式">{{ assessmentModeText(selectedContent) }}</ElDescriptionsItem>
          <template v-if="selectedContent.contentType === 'QUESTION_BANK'">
            <ElDescriptionsItem label="练习时长">{{ formatDuration(selectedContent.executionConfig?.durationSeconds) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="及格分数">{{ selectedContent.executionConfig?.passScore ?? '未配置' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="最大作答次数">{{ selectedContent.executionConfig?.maxAttempts ?? '未配置' }}</ElDescriptionsItem>
          </template>
          <template v-if="selectedContent.contentType === 'OFFLINE'">
            <ElDescriptionsItem label="授课教师">{{ teacherName(selectedContent.executionConfig?.teacherUserId) }}</ElDescriptionsItem>
            <ElDescriptionsItem label="培训地点">{{ selectedContent.executionConfig?.location || '未配置' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="授课时间">{{ selectedContent.executionConfig?.startTime || '未配置' }} 至 {{ selectedContent.executionConfig?.endTime || '未配置' }}</ElDescriptionsItem>
            <ElDescriptionsItem label="及格分数">{{ selectedContent.executionConfig?.passScore ?? '未配置' }}</ElDescriptionsItem>
          </template>
        </ElDescriptions>
        <ElTable v-if="selectedContent.contentType === 'QUESTION_BANK'" :data="selectedContent.executionConfig?.drawRules ?? []" border size="small" class="drawer-table">
          <ElTableColumn prop="questionType" label="题型" />
          <ElTableColumn prop="difficulty" label="难度" />
          <ElTableColumn prop="drawCount" label="抽题数" />
          <ElTableColumn prop="scorePerQuestion" label="每题分值" />
        </ElTable>
      </template>
    </ElDrawer>
    <footer class="sticky-actions">
      <ElButton @click="emit('back')">上一步：检查阶段内容</ElButton>
      <ElButton
        v-if="props.canPublish"
        type="primary"
        :loading="props.saving"
        :disabled="!props.created || props.stageCount === 0"
        @click="emit('publish')"
      >
        确认发布
      </ElButton>
    </footer>
  </ElCard>
</template>
<style scoped lang="less">
.panel-heading,
.section-title,
.stage-summary header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.panel-heading {
  align-items: center;
}
.panel-heading h2,
.summary-section h3,
.stage-summary h4 {
  margin: 0;
  color: var(--color-text-primary);
  font-weight: 600;
}
.panel-heading h2 {
  font-size: 16px;
  line-height: 24px;
}
.panel-heading p,
.section-title p,
.stage-summary p {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 20px;
}
.publish-scroll {
  padding: 20px 24px;
  background: var(--color-bg-subtle);
}
.summary-section {
  margin-bottom: 18px;
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
}
.summary-section:last-child {
  margin-bottom: 0;
}
.summary-section h3 {
  margin-bottom: 14px;
  font-size: 15px;
}
.publish-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0;
}
.publish-metric {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-subtle);
}
.publish-metric span,
.publish-metric small {
  display: block;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.publish-metric strong {
  display: block;
  margin: 4px 0 2px;
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 18px;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-summary-list {
  display: grid;
  gap: 12px;
}
.stage-summary {
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}
.stage-summary header {
  align-items: center;
  justify-content: flex-start;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
}
.stage-order {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary-subtle);
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}
.stage-summary h4 {
  font-size: 14px;
  line-height: 20px;
}
.stage-summary p {
  margin-top: 2px;
}
.stage-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(96px, 1fr));
  gap: 8px;
  margin-left: auto;
}
.stage-fields span {
  min-width: 0;
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #fff;
  color: var(--color-text-primary);
  font-size: 13px;
  line-height: 18px;
}
.stage-fields b {
  display: block;
  margin-bottom: 2px;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 400;
}
.summary-table {
  width: 100%;
}
.drawer-table {
  margin-top: 16px;
}
@media (max-width: 1100px) {
  .publish-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .stage-fields {
    grid-column: 1 / -1;
    width: 100%;
    margin-left: 0;
  }
}
@media (max-width: 720px) {
  .publish-metrics,
  .stage-fields {
    grid-template-columns: 1fr;
  }
}
</style>
