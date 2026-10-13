<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { getAssetDetail, getAssetPage, type AssetDetail, type AssetListItem, type AssetType } from '@/api/asset';
import { ASSET_TYPE_OPTIONS, getAssetProcessStatusLabel, getAssetTypeLabel } from '@/constants/asset';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import AssetPreviewPanel from './AssetPreviewPanel.vue';
import { mergeAssetSelection, removeAssetSelection } from './asset-picker-state';

interface AssetPickerProps {
  modelValue: boolean;
  selectionMode: 'single' | 'multiple';
  selectedIds: number[];
  allowedTypes?: AssetType[];
  /** 弹窗标题；各业务按自身语境覆盖（课程、题目等）。 */
  title?: string;
}

const props = withDefaults(defineProps<AssetPickerProps>(), {
  allowedTypes: () => [],
  title: '选择素材'
});
const emit = defineEmits<{
  confirm: [assets: AssetListItem[]];
  'update:modelValue': [visible: boolean];
}>();

const loading = ref(false);
const assets = ref<AssetListItem[]>([]);
const selectedAssets = ref<AssetListItem[]>([]);
const previewAsset = ref<AssetDetail>();
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const filters = reactive({
  assetName: '',
  assetType: undefined as AssetType | undefined
});
const visibleTypes = computed(() =>
  props.allowedTypes.length > 0 ? ASSET_TYPE_OPTIONS.filter((option) => props.allowedTypes.includes(option.value)) : ASSET_TYPE_OPTIONS
);

function isSelected(assetId: number): boolean {
  return selectedAssets.value.some((asset) => asset.assetId === assetId);
}

/** 课程素材列表直接使用分页 Contract，避免为每一行产生详情请求。 */
async function loadAssets(): Promise<void> {
  loading.value = true;
  try {
    const page = await getAssetPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(filters.assetName ? { 'query.assetName': filters.assetName } : {}),
      ...(filters.assetType ? { 'query.assetType': filters.assetType } : {}),
      'query.processStatus': 'SUCCEEDED',
      'query.status': 1
    });
    assets.value = page.list;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

/** 打开时补取已选素材详情，保证跨分页确认返回完整素材对象。 */
async function initializeSelection(): Promise<void> {
  selectedAssets.value = [];
  if (props.selectedIds.length > 0) {
    const details = await Promise.all(props.selectedIds.map((assetId) => getAssetDetail(assetId)));
    // 历史已选素材也必须重新通过当前可用性校验，停用或处理失败的素材不能继续确认。
    selectedAssets.value = details.filter((asset) => asset.status === 1 && asset.processStatus === 'SUCCEEDED');
    previewAsset.value = details[0];
  } else {
    previewAsset.value = undefined;
  }
  pageNum.value = 1;
  await loadAssets();
}

/** 用户明确点击素材名称时才按需读取详情并展示预览。 */
async function previewAssetDetail(asset: AssetListItem): Promise<void> {
  previewAsset.value = await getAssetDetail(asset.assetId);
}

function toggleAsset(asset: AssetListItem, selected: boolean): void {
  if (selected) {
    selectedAssets.value = mergeAssetSelection(selectedAssets.value, [asset], props.selectionMode);
  } else {
    selectedAssets.value = removeAssetSelection(selectedAssets.value, asset.assetId);
    if (previewAsset.value?.assetId === asset.assetId) previewAsset.value = undefined;
  }
}

async function search(): Promise<void> {
  pageNum.value = 1;
  await loadAssets();
}

async function selectType(assetType?: AssetType): Promise<void> {
  filters.assetType = assetType;
  await search();
}

function confirmSelection(): void {
  emit('confirm', selectedAssets.value);
  emit('update:modelValue', false);
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) void initializeSelection();
  }
);
</script>

<template>
  <ElDialog :model-value="modelValue" :title="title" width="1120px" destroy-on-close :lock-scroll="false" @update:model-value="emit('update:modelValue', $event)">
    <div class="asset-picker">
      <aside class="type-nav" aria-label="素材类型">
        <button type="button" :class="{ active: !filters.assetType }" @click="selectType()">全部素材</button>
        <button
          v-for="option in visibleTypes"
          :key="option.value"
          type="button"
          :class="{ active: filters.assetType === option.value }"
          @click="selectType(option.value)"
        >
          {{ option.label }}
        </button>
      </aside>

      <section class="asset-results">
        <ElForm class="search-bar" inline>
          <ElFormItem label="名称">
            <ElInput v-model="filters.assetName" clearable placeholder="请输入素材名称" @keyup.enter="search" />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="search">查询</ElButton>
          </ElFormItem>
        </ElForm>

        <ElTable v-loading="loading" :data="assets" row-key="assetId" height="430">
          <ElTableColumn width="48">
            <template #default="{ row }">
              <ElCheckbox
                :model-value="isSelected(row.assetId)"
                :aria-label="`选择素材 ${row.assetName}`"
                @change="toggleAsset(row as AssetListItem, Boolean($event))"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn prop="assetName" label="素材名称" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <ElButton link type="primary" @click="previewAssetDetail(row as AssetListItem)">{{ row.assetName }}</ElButton>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="assetType" label="类型" width="100">
            <template #default="{ row }">{{ getAssetTypeLabel(row.assetType) }}</template>
          </ElTableColumn>
          <ElTableColumn prop="processStatus" label="处理状态" width="110">
            <template #default="{ row }">{{ getAssetProcessStatusLabel(row.processStatus) }}</template>
          </ElTableColumn>
        </ElTable>
        <div class="admin-business-page__pagination">
          <ElPagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="LIST_PAGE_SIZES"
            layout="total, sizes, prev, pager, next"
            @current-change="loadAssets"
            @size-change="
              pageNum = 1;
              loadAssets();
            "
          />
        </div>
      </section>

      <AssetPreviewPanel :asset="previewAsset" />
    </div>

    <template #footer>
      <span class="selection-count">已选 {{ selectedAssets.length }} 项</span>
      <ElButton @click="emit('update:modelValue', false)">取消</ElButton>
      <ElButton type="primary" :disabled="selectedAssets.length === 0" @click="confirmSelection">确定</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="less">
.asset-picker {
  display: grid;
  min-height: 520px;
  grid-template-columns: 160px minmax(0, 1fr) 400px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
}
.type-nav {
  padding: 14px 10px;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
}
.type-nav button {
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-text-regular);
  cursor: pointer;
  text-align: left;
}
.type-nav button:hover,
.type-nav button.active {
  background: var(--color-primary-subtle);
  color: var(--color-primary);
}
.asset-results {
  min-width: 0;
  padding: 16px;
}
.search-bar {
  justify-content: flex-end;
}
.search-bar :deep(.el-input) {
  width: 220px;
}
.asset-results :deep(.el-pagination) {
  margin-top: 16px;
  justify-content: flex-end;
}
.selection-count {
  float: left;
  color: var(--color-text-secondary);
}
</style>
