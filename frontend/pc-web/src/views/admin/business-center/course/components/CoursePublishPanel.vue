<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getCourseDetail, type CourseDetail } from '@/api/course-foundation';
import { getAssetDetail, type AssetDetail } from '@/api/asset';
import {
  getDownloadAssets,
  getCourseChapters,
  getCourseItems,
  getCourseVersionDetail,
  getCourseVersions,
  offlineCourse,
  publishCourse,
  type CourseVersion,
  type CourseVersionSummary,
  type CourseChapter,
  type CourseItem
} from '@/api/course-operations';
import { COURSE_CONTENT_MODE_TEXT, COURSE_POINT_MODE_TEXT, COURSE_PUBLISH_STATUS_TEXT, SOURCE_DOWNLOAD_MODE_TEXT } from '@/constants/course';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import { formatDuration } from '@/utils/duration';

const props = defineProps<{ courseId: number }>();
const emit = defineEmits<{
  next: [];
  courseChanged: [detail: CourseDetail];
}>();

const userStore = useUserStore();
const loading = ref(false);
const publishing = ref(false);
const course = ref<CourseDetail>();
const versions = ref<CourseVersionSummary[]>([]);
const versionNote = ref('');
const chapterCount = ref(0);
const itemCount = ref(0);
const selectedSnapshot = ref<CourseVersion>();
/** 发布前核查默认全部折叠，避免首次进入遮挡发布操作。 */
const precheckActiveNames = ref<string[]>([]);
/** 发布前核查使用的草稿章节与按章节归类的内容项。 */
const previewChapters = ref<CourseChapter[]>([]);
const previewItemsByChapter = ref<Record<number, CourseItem[]>>({});
const previewChapterId = ref<number>();
const downloadAssets = ref<AssetDetail[]>([]);

const canPublish = computed(() => hasPermission(userStore.userInfo, 'course:course:publish'));
const hasPublishedVersion = computed(() => Boolean(course.value?.currentVersionId));
const activePreviewItems = computed(() => (previewChapterId.value ? (previewItemsByChapter.value[previewChapterId.value] ?? []) : []));
const itemTypeLabel = (itemType: CourseItem['itemType']) => ({ ASSET: '素材', LIVE: '直播', QUESTION_BANK: '题库练习', QUIZ: '课程测验' })[itemType];
const snapshotOverview = computed(() => {
  if (!selectedSnapshot.value) return undefined;
  try {
    const value = JSON.parse(selectedSnapshot.value.contentSnapshotJson) as {
      courseId?: number;
      chapters?: Array<{ chapterId?: number; chapterName?: string; items?: unknown[] }>;
      items?: Array<{ chapterId?: number }>;
      downloadAssets?: Array<{ assetId?: number; assetName?: string; sortOrder?: number; status?: number }>;
    };
    return {
      courseId: value.courseId,
      chapters: (value.chapters ?? []).map((chapter) => ({
        ...chapter,
        items: chapter.items ?? value.items?.filter((item) => item.chapterId === chapter.chapterId) ?? []
      })),
      downloadAssets: value.downloadAssets ?? []
    };
  } catch {
    return { courseId: props.courseId, chapters: [] };
  }
});

/** 同时刷新课程状态、发布历史和页面提示性完整度检查。 */
async function loadPublishPanel(): Promise<void> {
  loading.value = true;
  try {
    const [detail, history, chapters, assets] = await Promise.all([
      getCourseDetail(props.courseId),
      getCourseVersions(props.courseId),
      getCourseChapters(props.courseId),
      getDownloadAssets(props.courseId)
    ]);
    const [itemGroups, assetDetails] = await Promise.all([
      Promise.all(chapters.map((chapter) => getCourseItems(chapter.chapterId))),
      Promise.all(assets.map((asset) => getAssetDetail(asset.assetId)))
    ]);
    course.value = detail;
    versions.value = history;
    previewChapters.value = chapters;
    previewItemsByChapter.value = Object.fromEntries(chapters.map((chapter, index) => [chapter.chapterId, itemGroups[index]]));
    previewChapterId.value = chapters[0]?.chapterId;
    chapterCount.value = chapters.length;
    itemCount.value = itemGroups.reduce((total, items) => total + items.length, 0);
    downloadAssets.value = assetDetails;
    emit('courseChanged', detail);
  } finally {
    loading.value = false;
  }
}

async function publish(): Promise<void> {
  if (!course.value) return;
  try {
    await ElMessageBox.confirm('确认发布当前课程草稿吗？发布后将生成不可变版本快照。', '二次确认', { type: 'warning' });
    publishing.value = true;
    await publishCourse(props.courseId, {
      version: course.value.version,
      ...(versionNote.value.trim() ? { versionNote: versionNote.value.trim() } : {})
    });
    versionNote.value = '';
    ElMessage.success('课程发布成功');
    await loadPublishPanel();
  } finally {
    publishing.value = false;
  }
}

async function offline(): Promise<void> {
  if (!course.value) return;
  await ElMessageBox.confirm('确认下线当前课程吗？已有历史版本仍会保留。', '二次确认', { type: 'warning' });
  await offlineCourse(props.courseId, { version: course.value.version });
  ElMessage.success('课程已下线');
  await loadPublishPanel();
}

async function viewSnapshot(version: CourseVersionSummary): Promise<void> {
  selectedSnapshot.value = await getCourseVersionDetail(props.courseId, version.courseVersionId);
}

onMounted(loadPublishPanel);
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>
      <div class="content-header">
        <p>发布版本</p>
        <ElTag v-if="course" :type="course.publishStatus === 'PUBLISHED' ? 'success' : 'warning'">
          {{ COURSE_PUBLISH_STATUS_TEXT[course.publishStatus] }}
        </ElTag>
      </div>
    </template>

    <div class="publish-grid admin-detail-page__form-scroll">
      <section class="publish-main">
        <ElCard shadow="never">
          <template #header><strong>发布前检查</strong></template>
          <ElCollapse v-model="precheckActiveNames" class="precheck-details">
            <ElCollapseItem name="basic">
              <template #title>
                <div class="precheck-title">
                  <strong>课程基础信息</strong>
                  <div>
                    <span>展开查看当前草稿设置</span>
                    <ElTag type="success" style="color: var(--el-tag-text-color)">已保存</ElTag>
                  </div>
                </div>
              </template>
              <ElDescriptions v-if="course" :column="2" border size="small">
                <ElDescriptionsItem label="课程编码">{{ course.courseCode }}</ElDescriptionsItem>
                <ElDescriptionsItem label="课程名称">{{ course.courseName }}</ElDescriptionsItem>
                <ElDescriptionsItem label="内容模式">{{ COURSE_CONTENT_MODE_TEXT[course.contentMode] }}</ElDescriptionsItem>
                <ElDescriptionsItem label="学分">{{ course.credit }}</ElDescriptionsItem>
                <ElDescriptionsItem label="积分规则">
                  {{ COURSE_POINT_MODE_TEXT[course.pointMode] }}{{ course.pointValue ? `（${course.pointValue}）` : '' }}
                </ElDescriptionsItem>
                <ElDescriptionsItem label="资料下载">{{ SOURCE_DOWNLOAD_MODE_TEXT[course.sourceDownloadMode] }}</ElDescriptionsItem>
                <ElDescriptionsItem label="课程简介" :span="2">{{ course.introduction || '未填写' }}</ElDescriptionsItem>
                <ElDescriptionsItem label="教学目标" :span="2">{{ course.teachingObjective || '未填写' }}</ElDescriptionsItem>
                <ElDescriptionsItem label="课程大纲" :span="2">{{ course.syllabus || '未填写' }}</ElDescriptionsItem>
                <ElDescriptionsItem label="考核方式" :span="2">{{ course.assessmentMethod || '未填写' }}</ElDescriptionsItem>
              </ElDescriptions>
            </ElCollapseItem>
            <ElCollapseItem name="content">
              <template #title>
                <div class="precheck-title">
                  <strong>课程内容</strong>
                  <div>
                    <span>{{ chapterCount }} 个章节 · {{ itemCount }} 项内容</span>
                    <ElTag style="color: var(--el-tag-text-color)" :type="chapterCount > 0 ? 'success' : 'danger'">
                      {{ chapterCount > 0 ? `${chapterCount} 个章节` : '尚未配置' }}
                    </ElTag>
                  </div>
                </div>
              </template>
              <div class="preview-content-layout">
                <aside class="preview-chapter-list">
                  <button
                    v-for="chapter in previewChapters"
                    :key="chapter.chapterId"
                    type="button"
                    :class="{ active: previewChapterId === chapter.chapterId }"
                    @click="previewChapterId = chapter.chapterId"
                  >
                    <span>{{ chapter.chapterName }}</span>
                    <small>{{ previewItemsByChapter[chapter.chapterId]?.length ?? 0 }} 项</small>
                  </button>
                  <ElEmpty v-if="previewChapters.length === 0" description="暂无章节" :image-size="40" />
                </aside>
                <section class="preview-item-list">
                  <ElEmpty v-if="!previewChapterId" description="请选择章节" :image-size="46" />
                  <ElEmpty v-else-if="activePreviewItems.length === 0" description="当前章节暂无内容" :image-size="46" />
                  <div v-for="item in activePreviewItems" :key="item.courseItemId" class="preview-item">
                    <ElTag size="small" style="display: flex">{{ itemTypeLabel(item.itemType) }}</ElTag>
                    <div>
                      <strong>{{ item.itemTitle }}</strong>
                      <span>
                        {{ item.isRequired ? '必修' : '选修' }}<template v-if="item.itemType === 'ASSET'"> · 要求学习 {{ formatDuration(item.minimumStudySeconds) }}</template>
                      </span>
                    </div>
                  </div>
                </section>
              </div>
            </ElCollapseItem>
            <ElCollapseItem name="downloads">
              <template #title>
                <div class="precheck-title">
                  <strong>课程附件</strong>
                  <div>
                    <span>{{ downloadAssets.length }} 个附件</span>
                    <ElTag style="color: var(--el-tag-text-color)" :type="downloadAssets.length > 0 ? 'success' : 'warning'">
                      {{ downloadAssets.length > 0 ? `${downloadAssets.length} 个附件` : '未配置附件' }}
                    </ElTag>
                  </div>
                </div>
              </template>
              <ElEmpty v-if="downloadAssets.length === 0" description="尚未配置课程附件" :image-size="46" />
              <div v-else class="download-asset-list">
                <div v-for="(asset, index) in downloadAssets" :key="asset.assetId" class="download-asset-item">
                  <div>
                    <strong>{{ asset.assetName }}</strong>
                    <span>素材 ID：{{ asset.assetId }} · 排序：{{ index + 1 }}</span>
                  </div>
                  <ElTag size="small" :type="asset.status === 1 ? 'success' : 'info'">
                    {{ asset.status === 1 ? '启用' : '停用' }}
                  </ElTag>
                </div>
              </div>
            </ElCollapseItem>
          </ElCollapse>
          <ElAlert class="publish-alert" title="以上检查仅用于操作提示，最终发布条件和错误信息以后端校验为准。" type="info" :closable="false" />
          <ElForm v-if="canPublish" label-width="90px" class="publish-form">
            <ElFormItem label="版本说明">
              <ElInput v-model="versionNote" maxlength="500" show-word-limit placeholder="请输入本次版本说明（可选）" />
            </ElFormItem>
            <ElFormItem>
              <div style="width: 100%; display: flex; justify-content: flex-end">
                <ElButton v-if="course?.publishStatus === 'PUBLISHED'" type="danger" plain @click="offline">下线课程</ElButton>
                <ElButton type="primary" :loading="publishing" :disabled="chapterCount === 0 || itemCount === 0" @click="publish">
                  发布当前草稿
                </ElButton>
              </div>
            </ElFormItem>
          </ElForm>
        </ElCard>

        <ElCard shadow="never">
          <template #header><strong>历史版本</strong></template>
          <ElTable :data="versions" row-key="courseVersionId">
            <ElTableColumn prop="versionNo" label="版本号" width="90" />
            <ElTableColumn prop="versionNote" label="版本说明" min-width="180" show-overflow-tooltip />
            <ElTableColumn prop="publishedTime" label="发布时间" width="190" />
            <ElTableColumn label="操作" width="100">
              <template #default="{ row }">
                <ElButton link type="primary" @click="viewSnapshot(row as CourseVersionSummary)">查看快照</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </section>

      <aside class="snapshot-panel">
        <div class="snapshot-heading">
          <strong>版本快照</strong>
          <span v-if="selectedSnapshot">版本 {{ selectedSnapshot.versionNo }}</span>
        </div>
        <ElEmpty v-if="!selectedSnapshot" description="选择历史版本查看只读快照" />
        <template v-else>
          <ElDescriptions :column="1" border size="small">
            <ElDescriptionsItem label="课程 ID">{{ snapshotOverview?.courseId ?? courseId }}</ElDescriptionsItem>
            <ElDescriptionsItem label="发布时间">{{ selectedSnapshot.publishedTime }}</ElDescriptionsItem>
            <ElDescriptionsItem label="章节数量">{{ snapshotOverview?.chapters.length ?? 0 }}</ElDescriptionsItem>
            <ElDescriptionsItem label="课程附件">{{ snapshotOverview?.downloadAssets?.length ?? 0 }}</ElDescriptionsItem>
          </ElDescriptions>
          <div class="snapshot-chapters">
            <div v-for="(chapter, index) in snapshotOverview?.chapters" :key="chapter.chapterId ?? index">
              <strong>{{ chapter.chapterName || `章节 ${index + 1}` }}</strong>
              <span>{{ chapter.items?.length ?? 0 }} 项内容</span>
            </div>
          </div>
          <div class="snapshot-download-assets">
            <strong>课程附件</strong>
            <ElEmpty v-if="!snapshotOverview?.downloadAssets?.length" description="该版本未保存课程附件" :image-size="40" />
            <div v-else v-for="(asset, index) in snapshotOverview.downloadAssets" :key="asset.assetId ?? index" class="snapshot-download-asset">
              <strong>{{ asset.assetName || '未保存名称' }}</strong>
              <span>素材 ID：{{ asset.assetId ?? '—' }} · 排序：{{ asset.sortOrder ?? index + 1 }} · {{ asset.status === 1 ? '启用' : '停用' }}</span>
            </div>
          </div>
        </template>
      </aside>
    </div>

    <div class="sticky-actions">
      <ElButton type="primary" :disabled="!hasPublishedVersion" @click="emit('next')">下一步：创建开课</ElButton>
    </div>
  </ElCard>
</template>

<style scoped lang="less">
.course-publish-panel {
  width: 100%;
}
.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
}

.check-list > div,
.snapshot-heading,
.snapshot-chapters > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 54px;
}

.publish-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
}
.publish-main {
  display: grid;
  align-content: start;
  gap: 16px;
  padding: 20px;
}
.check-list {
  display: grid;
  gap: 12px;
}
.check-list > div {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
}
.publish-alert,
.publish-form {
  margin-top: 16px;
}
.precheck-details {
  margin-top: 16px;
}
.precheck-title {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
}
.precheck-title div {
  display: flex;
  gap: 8px;
  align-items: center;
}
.precheck-title span,
.preview-item span,
.preview-chapter-list small {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.preview-content-layout {
  display: grid;
  min-height: 230px;
  grid-template-columns: 190px minmax(0, 1fr);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  overflow: hidden;
}
.preview-chapter-list {
  padding: 8px;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
}
.preview-chapter-list button {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding: 9px 10px;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-text-regular);
  cursor: pointer;
  text-align: left;
}
.preview-chapter-list button:hover,
.preview-chapter-list button.active {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}
.preview-item-list {
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 12px;
}
.preview-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
}
.preview-item div,
.preview-item strong,
.preview-item span {
  display: block;
}
.preview-item strong {
  margin-bottom: 3px;
}
.download-asset-list,
.snapshot-download-assets {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}
.download-asset-item,
.snapshot-download-asset {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
}
.download-asset-item strong,
.download-asset-item span,
.snapshot-download-asset strong,
.snapshot-download-asset span {
  display: block;
}
.download-asset-item span,
.snapshot-download-asset span {
  margin-top: 3px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.snapshot-panel {
  padding: 20px;
  border-left: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
}
.snapshot-heading {
  margin-bottom: 16px;
}
.snapshot-heading span,
.snapshot-chapters span {
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.snapshot-chapters {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}
.snapshot-chapters > div {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: #fff;
}
</style>
