<script setup lang="ts">
import { orderCourseChapters } from '@shared/libs/course-chapters';
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getAssetDetail, type AssetDetail, type AssetListItem } from '@/api/asset';
import { getAssetTypeLabel } from '@/constants/asset';
import {
  createCourseChapter,
  deleteCourseChapter,
  deleteCourseItem,
  getCourseChapters,
  getCourseItems,
  getDownloadAssets,
  moveCourseItem,
  replaceDownloadAssets,
  updateCourseChapter,
  type CourseChapter,
  type CourseItem
} from '@/api/course-operations';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';
import { formatDuration } from '@/utils/duration';
import AssetPickerDialog from '@/components/asset-picker/AssetPickerDialog.vue';
import CourseItemInspector from './CourseItemInspector.vue';

const props = defineProps<{ courseId: number }>();
const emit = defineEmits<{ next: [] }>();
const userStore = useUserStore();
const loading = ref(false);
const chapters = ref<CourseChapter[]>([]);
const itemsByChapter = reactive<Record<number, CourseItem[]>>({});
const activeChapterId = ref(0);
const selectedItem = ref<CourseItem>();
const creatingItem = ref(false);
const chapterDialogVisible = ref(false);
const downloadsVisible = ref(false);
const downloadPreviewVisible = ref(false);
const downloadAssetIds = ref<number[]>([]);
const downloadAssets = ref<AssetDetail[]>([]);
const chapterForm = reactive({
  chapterId: undefined as number | undefined,
  parentId: 0,
  chapterName: '',
  sortOrder: 1,
  status: 1 as 0 | 1,
  version: 0
});

const orderedChapters = computed(() => orderCourseChapters(chapters.value));
const activeChapter = computed(() => chapters.value.find((chapter) => chapter.chapterId === activeChapterId.value));
const activeItems = computed(() => itemsByChapter[activeChapterId.value] ?? []);
const can = (permission: string) => hasPermission(userStore.userInfo, permission);
const asCourseItem = (row: unknown) => row as CourseItem;
const itemTypeLabel = (itemType: CourseItem['itemType']) =>
  ({
    ASSET: '素材',
    LIVE: '直播',
    QUESTION_BANK: '题库练习',
    QUIZ: '课程测验'
  })[itemType];

/** 加载课程根级章节及每章内容，尽量保留当前选择。 */
async function loadContent(preferredItemId?: number): Promise<void> {
  loading.value = true;
  try {
    const nextChapters = await getCourseChapters(props.courseId);
    const groups = await Promise.all(nextChapters.map(async (chapter) => [chapter.chapterId, await getCourseItems(chapter.chapterId)] as const));
    chapters.value = nextChapters;
    // 清除已删除章节的旧缓存，避免右侧检查器继续显示不属于当前目录的内容项。
    Object.keys(itemsByChapter).forEach((chapterId) => delete itemsByChapter[Number(chapterId)]);
    groups.forEach(([chapterId, items]) => {
      itemsByChapter[chapterId] = items;
    });
    if (!nextChapters.some((chapter) => chapter.chapterId === activeChapterId.value)) {
      activeChapterId.value = nextChapters[0]?.chapterId ?? 0;
    }
    const itemId = preferredItemId ?? selectedItem.value?.courseItemId;
    selectedItem.value = itemId
      ? Object.values(itemsByChapter)
          .flat()
          .find((item) => item.courseItemId === itemId)
      : activeItems.value[0];
    creatingItem.value = false;
  } finally {
    loading.value = false;
  }
}

function selectChapter(chapterId: number): void {
  activeChapterId.value = chapterId;
  selectedItem.value = itemsByChapter[chapterId]?.[0];
  creatingItem.value = false;
  chapterDialogVisible.value = false;
}

function editChapter(chapter?: CourseChapter): void {
  // 章节排序从 1 开始：新增章节默认排在现有章节之后，编辑时沿用已有排序。
  Object.assign(
    chapterForm,
    chapter ? { ...chapter } : { chapterId: undefined, parentId: 0, chapterName: '', sortOrder: chapters.value.length + 1, status: 1, version: 0 }
  );
  chapterDialogVisible.value = true;
}

async function saveChapter(): Promise<void> {
  if (!chapterForm.chapterName.trim()) {
    ElMessage.warning('请输入章节名称');
    return;
  }

  if (chapterForm.chapterId) {
    await updateCourseChapter(chapterForm.chapterId, {
      parentId: chapterForm.parentId,
      chapterName: chapterForm.chapterName,
      sortOrder: chapterForm.sortOrder,
      status: chapterForm.status,
      version: chapterForm.version
    });
  } else {
    activeChapterId.value = await createCourseChapter({
      courseId: props.courseId,
      parentId: chapterForm.parentId,
      chapterName: chapterForm.chapterName,
      sortOrder: chapterForm.sortOrder,
      status: chapterForm.status
    });
  }
  chapterDialogVisible.value = false;
  await loadContent();
}

async function removeChapter(chapter: CourseChapter): Promise<void> {
  await ElMessageBox.confirm(`确认删除章节“${chapter.chapterName}”吗？`, '二次确认', { type: 'warning' });
  await deleteCourseChapter(chapter.chapterId);
  await loadContent();
}

function beginCreateItem(): void {
  if (!activeChapterId.value) {
    ElMessage.warning('请先创建章节');
    return;
  }
  selectedItem.value = undefined;
  creatingItem.value = true;
}

async function removeItem(item: CourseItem): Promise<void> {
  await ElMessageBox.confirm(`确认删除内容“${item.itemTitle}”吗？`, '二次确认', { type: 'warning' });
  await deleteCourseItem(item.courseItemId);
  await loadContent();
}

async function moveItem(item: CourseItem, targetChapterId: number): Promise<void> {
  await moveCourseItem(item.courseItemId, {
    chapterId: targetChapterId,
    sortOrder: itemsByChapter[targetChapterId]?.length ?? 0,
    version: item.version
  });
  activeChapterId.value = targetChapterId;
  await loadContent(item.courseItemId);
}

/** 加载课程附件，首次进入内容编排时也需要展示已配置数据。 */
async function loadDownloadAssets(): Promise<void> {
  const assets = await getDownloadAssets(props.courseId);
  downloadAssetIds.value = assets.map((item) => item.assetId);
  downloadAssets.value = await Promise.all(assets.map((item) => getAssetDetail(item.assetId)));
}

async function openDownloads(): Promise<void> {
  await loadDownloadAssets();
  downloadsVisible.value = true;
}

async function openDownloadPreview(): Promise<void> {
  await loadDownloadAssets();
  downloadPreviewVisible.value = true;
}

async function saveDownloads(assets: AssetListItem[]): Promise<void> {
  await replaceDownloadAssets(
    props.courseId,
    assets.map((asset, sortOrder) => ({ assetId: asset.assetId, sortOrder, status: 1 }))
  );
  downloadAssetIds.value = assets.map((asset) => asset.assetId);
  downloadAssets.value = assets as AssetDetail[];
  ElMessage.success('课程附件已保存');
}

onMounted(() => {
  void Promise.all([loadContent(), loadDownloadAssets()]);
});
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card admin-detail-card-spacing">
    <template #header>
      <div class="content-header">
        <p>内容编排</p>
        <div class="content-header__actions">
          <ElButton v-if="downloadAssets.length > 0" @click="openDownloadPreview">查看课程附件</ElButton>
          <ElButton v-if="can('course:course:edit')" @click="openDownloads">课程附件（学员可下载）</ElButton>
          <ElButton type="primary" @click="emit('next')">下一步：发布版本</ElButton>
        </div>
      </div>
    </template>

    <div class="content-workspace">
      <aside class="chapter-pane">
        <div class="pane-heading">
          <strong>课程章节</strong>
          <ElButton v-if="can('course:chapter:add')" link type="primary" @click="editChapter()">新增</ElButton>
        </div>
        <button
          v-for="chapter in orderedChapters"
          :key="chapter.chapterId"
          type="button"
          class="chapter-item"
          :style="{ paddingLeft: 12 + chapter.depth * 16 + 'px' }"
          :class="{ active: activeChapterId === chapter.chapterId }"
          @click="selectChapter(chapter.chapterId)"
        >
          <span>{{ chapter.chapterName }}</span>
          <small>{{ (itemsByChapter[chapter.chapterId] ?? []).length }} 项</small>
        </button>
        <ElEmpty v-if="chapters.length === 0" description="暂无章节" :image-size="48" />

        <div v-if="activeChapter" class="chapter-actions">
          <ElButton v-if="can('course:chapter:edit')" size="small" link @click="editChapter(activeChapter)">编辑章节</ElButton>
          <ElButton v-if="can('course:chapter:delete')" size="small" link type="danger" @click="removeChapter(activeChapter)">删除章节</ElButton>
        </div>
      </aside>

      <section class="item-pane">
        <div class="pane-heading">
          <div>
            <strong>{{ activeChapter?.chapterName || '课程内容' }}</strong>
            <span v-if="activeChapter">{{ activeItems.length }} 项内容</span>
          </div>
          <ElButton v-if="activeChapter && can('course:item:add')" type="primary" size="small" @click="beginCreateItem">新增内容</ElButton>
        </div>

        <div
          v-for="item in activeItems"
          :key="item.courseItemId"
          class="content-item"
          role="button"
          tabindex="0"
          :class="{ active: selectedItem?.courseItemId === item.courseItemId && !creatingItem }"
          @click="
            selectedItem = item;
            creatingItem = false;
          "
          @keydown.enter="
            selectedItem = item;
            creatingItem = false;
          "
          @keydown.space.prevent="
            selectedItem = item;
            creatingItem = false;
          "
        >
          <div class="content-item__main">
            <ElTag size="small" style="display: flex">{{ itemTypeLabel(item.itemType) }}</ElTag>
            <span>
              <strong>{{ item.itemTitle }}</strong>
              <small>
                <template v-if="item.itemType === 'ASSET'">要求学习 {{ formatDuration(item.minimumStudySeconds) }} · </template>{{ item.isRequired ? '必修' : '选修' }}
              </small>
            </span>
          </div>
          <div class="content-item__actions">
            <ElDropdown v-if="chapters.length > 1 && can('course:item:edit')" @command="moveItem(item, Number($event))">
              <ElButton link type="primary" @click.stop>移动</ElButton>
              <template #dropdown>
                <ElDropdownMenu>
                  <ElDropdownItem
                    v-for="target in chapters.filter((chapter) => chapter.chapterId !== activeChapterId)"
                    :key="target.chapterId"
                    :command="target.chapterId"
                  >
                    {{ target.chapterName }}
                  </ElDropdownItem>
                </ElDropdownMenu>
              </template>
            </ElDropdown>
            <ElButton v-if="can('course:item:delete')" link type="danger" @click.stop="removeItem(asCourseItem(item))">删除</ElButton>
          </div>
        </div>
        <ElEmpty v-if="activeChapter && activeItems.length === 0 && !creatingItem" description="当前章节暂无内容" />
        <ElEmpty v-if="!activeChapter" description="请先创建或选择章节" />
      </section>

      <CourseItemInspector
        v-if="activeChapter && (selectedItem || creatingItem)"
        :key="selectedItem?.courseItemId ?? `new-${activeChapterId}`"
        :chapter-id="activeChapterId"
        :quiz-items="Object.values(itemsByChapter).flat().filter(i => i.itemType === 'QUIZ')"
        :item="selectedItem"
        :default-sort-order="activeItems.length"
        @saved="loadContent"
        @cancel-create="
          creatingItem = false;
          selectedItem = activeItems[0];
        "
      />
      <aside v-else class="inspector-placeholder">
        <ElEmpty description="选择内容后在此编辑" />
      </aside>
    </div>

    <ElDialog
      v-model="chapterDialogVisible"
      :title="chapterForm.chapterId ? '编辑章节' : '新增章节'"
      width="420px"
      destroy-on-close
      :lock-scroll="false"
    >
      <ElForm class="chapter-form" :model="chapterForm" label-position="top">
        <ElFormItem label="父章节">
          <ElSelect v-model="chapterForm.parentId" placeholder="请选择父章节" style="width: 100%">
            <ElOption label="根级目录" :value="0" />
            <ElOption v-for="chapter in orderedChapters.filter(c => c.chapterId !== chapterForm.chapterId)" :key="chapter.chapterId" :value="chapter.chapterId" :label="' '.repeat(chapter.depth) + chapter.chapterName" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="章节名称">
          <ElInput v-model="chapterForm.chapterName" placeholder="请输入章节名称" />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber v-model="chapterForm.sortOrder" :min="1" />
        </ElFormItem>
        <ElCheckbox v-model="chapterForm.status" :true-value="1" :false-value="0">启用章节</ElCheckbox>
      </ElForm>
      <template #footer>
        <ElButton @click="chapterDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveChapter">保存</ElButton>
      </template>
    </ElDialog>

    <AssetPickerDialog
      v-model="downloadsVisible"
      title="选择课程素材"
      selection-mode="multiple"
      :selected-ids="downloadAssetIds"
      :allowed-types="['DOCUMENT', 'IMAGE', 'AUDIO', 'VIDEO', 'RICH_TEXT', 'LINK']"
      @confirm="saveDownloads"
    />

    <ElDrawer v-model="downloadPreviewVisible" title="课程附件" direction="rtl" size="420px" :lock-scroll="false">
      <div class="download-preview">
        <p class="download-preview__description">以下资料可供学员在学习课程时下载。</p>
        <div class="download-preview__items">
          <div v-for="asset in downloadAssets" :key="asset.assetId" class="download-preview__card">
            <div class="download-preview__card-title">
              <strong>{{ asset.assetName }}</strong>
              <ElTag size="small" type="info">{{ getAssetTypeLabel(asset.assetType) }}</ElTag>
            </div>
            <span>素材 ID {{ asset.assetId }}</span>
          </div>
        </div>
      </div>
    </ElDrawer>
  </ElCard>
</template>

<style scoped lang="less">
:deep(.el-card__body) {
  padding: 0;
}
.content-header {
  display: flex;
  width: 100%;
  min-height: 40px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.content-header p {
  margin: 0;
}
.content-header__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
.pane-heading,
.content-item,
.content-item__main,
.content-item__actions,
.inline-actions,
.chapter-actions {
  display: flex;
  align-items: center;
}
.content-workspace {
  display: grid;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  grid-template-columns: 235px minmax(330px, 1fr) minmax(340px, 420px);

  aside,
  section {
    min-height: 0;
    height: 100%;
    overflow: auto;
  }
}
.chapter-pane,
.item-pane,
.inspector-placeholder {
  min-width: 0;
}
.chapter-pane,
.item-pane {
  border-right: 1px solid var(--color-border);
}
.pane-heading {
  min-height: 54px;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--color-border);
}
.pane-heading span {
  margin-left: 8px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.chapter-item,
.content-item {
  width: calc(100% - 20px);
  margin: 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-text-regular);
  cursor: pointer;
  text-align: left;
}
.chapter-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 5px;
}
.chapter-item small {
  white-space: nowrap;
  color: var(--color-text-secondary);
}
.chapter-item:hover,
.chapter-item.active,
.content-item:hover,
.content-item.active {
  border-color: #bfdbfe;
  background: var(--color-primary-subtle);
}
.chapter-form {
  margin: 10px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: #fff;
}
.chapter-form :deep(.el-input-number) {
  width: 100%;
}

.download-preview__description,
.download-preview__card > span {
  color: var(--color-text-secondary);
  font-size: 13px;
}
.download-preview__description {
  margin: 0 0 16px;
}
.download-preview__items {
  display: grid;
  gap: 10px;
}
.download-preview__card {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  background: #fff;
}
.download-preview__card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.download-preview__card-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.inline-actions,
.chapter-actions {
  justify-content: flex-end;
  padding: 0 15px;
}
.content-item {
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
}
.content-item__main {
  min-width: 0;
  gap: 10px;
}
.content-item__main span,
.content-item__main strong,
.content-item__main small {
  display: block;
  min-width: 0;
}
.content-item__main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.content-item__main small {
  margin-top: 4px;
  color: var(--color-text-secondary);
}
.content-item__actions {
  flex: 0 0 auto;
}
.inspector-placeholder {
  display: grid;
  place-items: center;
  background: var(--color-bg-subtle);
}
@media (max-width: 1280px) {
  .content-workspace {
    grid-template-columns: 190px minmax(300px, 1fr) minmax(320px, 380px);
  }
}
</style>
