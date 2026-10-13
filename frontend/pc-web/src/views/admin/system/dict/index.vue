<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import {
  createDictData,
  createDictType,
  deleteDictData,
  deleteDictType,
  getDictCatalog,
  getDictDataPage,
  getDictTypePage,
  updateDictData,
  updateDictType,
  type DictCatalog,
  type DictData,
  type DictStatus,
  type DictType
} from '@/api/system-dict';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

interface DictTreeNode extends DictType {
  valueCount: number;
}

/** 字典类型编辑表单。*/
interface TypeForm {
  dictTypeId?: number;
  dictName: string;
  dictType: string;
  status: DictStatus;
  version?: number;
}

/** 字典数据编辑表单。*/
interface DataForm {
  dictDataId?: number;
  dictType: string;
  dictLabel: string;
  dictValue: string;
  sortOrder: number;
  status: DictStatus;
  version?: number;
}

const userStore = useUserStore();
const typeFilters = reactive({ dictName: '', dictType: '', status: undefined as DictStatus | undefined });
const dataFilters = reactive({ dictLabel: '' });
const dictTypes = ref<DictType[]>([]);
const catalog = ref<DictCatalog>({});
const selectedTypeCode = ref('');
const dataList = ref<DictData[]>([]);
const dataTotal = ref(0);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const typeLoading = ref(false);
const dataLoading = ref(false);
const typeDialogVisible = ref(false);
const dataDialogVisible = ref(false);
const typeFormRef = ref<FormInstance>();
const dataFormRef = ref<FormInstance>();
const typeForm = reactive<TypeForm>(newTypeForm());
const dataForm = reactive<DataForm>(newDataForm());

const typeRules: FormRules<TypeForm> = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入类型编码', trigger: 'blur' }]
};
const dataRules: FormRules<DataForm> = {
  dictType: [{ required: true, message: '请先选择字典类型', trigger: 'change' }],
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }]
};

/** 根据筛选条件生成左侧可浏览的字典目录节点。*/
const dictionaryTree = computed<DictTreeNode[]>(() =>
  dictTypes.value
    .filter((item) => !typeFilters.dictName || item.dictName.includes(typeFilters.dictName))
    .filter((item) => !typeFilters.dictType || item.dictType.includes(typeFilters.dictType))
    .filter((item) => typeFilters.status === undefined || item.status === typeFilters.status)
    .map((item) => ({
      ...item,
      valueCount: catalog.value.types?.[item.dictType]?.length ?? 0
    }))
);

/** 当前目录节点对应的字典类型。*/
const selectedType = computed(() => dictTypes.value.find((item) => item.dictType === selectedTypeCode.value));

/** 当前类型在卡片标题中展示的名称。*/
const selectedTypeTitle = computed(() =>
  selectedType.value ? `${selectedType.value.dictName}（${selectedType.value.dictType}）` : '请选择左侧字典类型'
);

/** 校验当前用户是否拥有指定的字典维护权限。*/
function canOperate(permission: string): boolean {
  return hasPermission(userStore.userInfo, permission);
}

/** 创建空的类型表单。*/
function newTypeForm(): TypeForm {
  return { dictName: '', dictType: '', status: 1 };
}

/** 创建空的数据表单。*/
function newDataForm(): DataForm {
  return { dictType: selectedTypeCode.value, dictLabel: '', dictValue: '', sortOrder: 0, status: 1 };
}

/** 将 Element Plus 表格插槽的通用行类型收窄为字典数据。*/
function asDictData(row: unknown): DictData {
  return row as DictData;
}

/** 读取类型列表和目录快照，供左侧树与数量预览复用。*/
async function loadDictionaryDirectory(): Promise<void> {
  typeLoading.value = true;
  try {
    const [types, currentCatalog] = await Promise.all([
      getDictTypePage({ pageNum: 1, pageSize: LIST_PAGE_SIZES[LIST_PAGE_SIZES.length - 1] }),
      getDictCatalog()
    ]);
    dictTypes.value = types.list;
    catalog.value = currentCatalog;

    if (!dictTypes.value.some((item) => item.dictType === selectedTypeCode.value)) {
      selectedTypeCode.value = dictTypes.value[0]?.dictType ?? '';
    }
  } finally {
    typeLoading.value = false;
  }
}

/** 按当前目录节点和筛选词加载右侧字典数据。*/
async function loadData(): Promise<void> {
  if (!selectedTypeCode.value) {
    dataList.value = [];
    dataTotal.value = 0;
    return;
  }

  dataLoading.value = true;
  try {
    const data = await getDictDataPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      'query.dictType': selectedTypeCode.value,
      ...(dataFilters.dictLabel ? { 'query.dictLabel': dataFilters.dictLabel } : {})
    });
    dataList.value = data.list;
    dataTotal.value = data.total;
  } finally {
    dataLoading.value = false;
  }
}

/** 选择目录节点后回到第一页并加载该类型的字典数据。*/
async function selectDictionaryType(dictType: string): Promise<void> {
  selectedTypeCode.value = dictType;
  pageNum.value = 1;
  Object.assign(dataFilters, { dictLabel: '' });
  await loadData();
}

/** 应用目录筛选；筛选后自动选中第一项，避免右侧出现孤立数据。*/
async function searchDirectory(): Promise<void> {
  const firstType = dictionaryTree.value[0]?.dictType ?? '';
  if (firstType !== selectedTypeCode.value) {
    await selectDictionaryType(firstType);
  } else {
    await loadData();
  }
}

/** 清空目录筛选并恢复完整目录。*/
async function resetDirectoryFilters(): Promise<void> {
  Object.assign(typeFilters, { dictName: '', dictType: '', status: undefined });
  await searchDirectory();
}

/** 重置数据筛选后重新加载当前类型。*/
async function resetDataFilters(): Promise<void> {
  Object.assign(dataFilters, { dictLabel: '' });
  pageNum.value = 1;
  await loadData();
}

/** 打开新增类型弹窗。*/
function openCreateType(): void {
  Object.assign(typeForm, newTypeForm());
  typeDialogVisible.value = true;
}

/** 打开当前类型编辑弹窗。*/
function openEditType(): void {
  if (!selectedType.value) return;
  Object.assign(typeForm, selectedType.value);
  typeDialogVisible.value = true;
}

/** 保存类型后刷新目录，并定位到当前编辑或新建的类型。*/
async function saveType(): Promise<void> {
  if (!(await typeFormRef.value?.validate().catch(() => false))) return;

  if (typeForm.dictTypeId === undefined) {
    await createDictType({ dictName: typeForm.dictName, dictType: typeForm.dictType, status: typeForm.status });
    selectedTypeCode.value = typeForm.dictType;
  } else if (typeForm.version !== undefined) {
    await updateDictType(typeForm.dictTypeId, {
      dictName: typeForm.dictName,
      dictType: typeForm.dictType,
      status: typeForm.status,
      version: typeForm.version
    });
    selectedTypeCode.value = typeForm.dictType;
  }
  typeDialogVisible.value = false;
  ElMessage.success('字典类型保存成功');
  await loadDictionaryDirectory();
  await loadData();
}

/** 删除当前字典类型；服务端负责拦截仍存在数据的类型。*/
async function confirmDeleteType(): Promise<void> {
  if (!selectedType.value) return;

  await ElMessageBox.confirm(`确认删除字典类型“${selectedType.value.dictName}”吗？`, '二次确认', { type: 'warning' });
  await deleteDictType(selectedType.value.dictTypeId);
  selectedTypeCode.value = '';
  ElMessage.success('字典类型已删除');
  await loadDictionaryDirectory();
  await loadData();
}

/** 打开新增数据弹窗，并将数据固定绑定到当前目录类型。*/
function openCreateData(): void {
  if (!selectedType.value) return;
  Object.assign(dataForm, newDataForm());
  dataDialogVisible.value = true;
}

/** 打开字典数据编辑弹窗。*/
function openEditData(item: DictData): void {
  Object.assign(dataForm, item);
  dataDialogVisible.value = true;
}

/** 保存数据后刷新目录数量和当前数据页。*/
async function saveData(): Promise<void> {
  if (!(await dataFormRef.value?.validate().catch(() => false))) return;

  if (dataForm.dictDataId === undefined) {
    await createDictData({
      dictType: dataForm.dictType,
      dictLabel: dataForm.dictLabel,
      dictValue: dataForm.dictValue,
      sortOrder: dataForm.sortOrder,
      status: dataForm.status
    });
  } else if (dataForm.version !== undefined) {
    await updateDictData(dataForm.dictDataId, {
      dictLabel: dataForm.dictLabel,
      dictValue: dataForm.dictValue,
      sortOrder: dataForm.sortOrder,
      status: dataForm.status,
      version: dataForm.version
    });
  }
  dataDialogVisible.value = false;
  ElMessage.success('字典数据保存成功');
  await Promise.all([loadDictionaryDirectory(), loadData()]);
}

/** 删除单条字典数据并刷新当前类型数据。*/
async function confirmDeleteData(item: DictData): Promise<void> {
  await ElMessageBox.confirm(`确认删除字典数据“${item.dictLabel}”吗？`, '二次确认', { type: 'warning' });
  await deleteDictData(item.dictDataId);
  ElMessage.success('字典数据已删除');
  await Promise.all([loadDictionaryDirectory(), loadData()]);
}

onMounted(async () => {
  await loadDictionaryDirectory();
  await loadData();
});
</script>

<template>
  <section class="admin-business-page dictionary-management">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm :model="typeFilters" inline>
        <ElFormItem label="字典名称">
          <ElInput v-model="typeFilters.dictName" placeholder="请输入字典名称" clearable @keyup.enter="searchDirectory" />
        </ElFormItem>
        <ElFormItem label="类型编码">
          <ElInput v-model="typeFilters.dictType" placeholder="请输入类型编码" clearable @keyup.enter="searchDirectory" />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="typeFilters.status" placeholder="全部状态" clearable>
            <ElOption label="正常" :value="1" />
            <ElOption label="停用" :value="0" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem class="filter-actions">
          <ElButton type="primary" @click="searchDirectory">查询</ElButton>
          <ElButton @click="resetDirectoryFilters">重置</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="dictionary-management__card-header">
          <div>
            <span class="dictionary-management__card-title">{{ selectedTypeTitle }}</span>
            <span v-if="catalog.version" class="dictionary-management__catalog-version">目录版本：{{ catalog.version }}</span>
          </div>
          <div class="dictionary-management__actions">
            <ElButton v-if="canOperate('sys:dict:add')" @click="openCreateType">新增字典类型</ElButton>
            <ElButton v-if="selectedType && canOperate('sys:dict:edit')" link type="primary" @click="openEditType">编辑类型</ElButton>
            <ElButton v-if="selectedType && canOperate('sys:dict:delete')" link type="danger" @click="confirmDeleteType">删除类型</ElButton>
            <ElButton v-if="selectedType && canOperate('sys:dict:add')" type="primary" @click="openCreateData">新增字典数据</ElButton>
          </div>
        </div>
      </template>

      <div class="dictionary-management__content">
        <aside v-loading="typeLoading" class="dictionary-management__directory">
          <div class="dictionary-management__directory-title">字典目录</div>
          <ElTree
            :data="dictionaryTree"
            node-key="dictType"
            :current-node-key="selectedTypeCode"
            :expand-on-click-node="false"
            highlight-current
            empty-text="暂无字典类型"
            @node-click="(node: DictTreeNode) => selectDictionaryType(node.dictType)"
          >
            <template #default="{ data }">
              <span class="dictionary-management__tree-node">
                <span>{{ data.dictName }}</span>
                <small>{{ data.dictType }} · {{ data.valueCount }} 项</small>
              </span>
            </template>
          </ElTree>
        </aside>

        <section class="dictionary-management__data-panel">
          <ElForm class="dictionary-management__data-filters" :model="dataFilters" inline>
            <ElFormItem label="字典标签">
              <ElInput
                v-model="dataFilters.dictLabel"
                placeholder="请输入字典标签"
                clearable
                @keyup.enter="
                  pageNum = 1;
                  loadData();
                "
              />
            </ElFormItem>
            <ElFormItem class="filter-actions">
              <ElButton
                type="primary"
                :disabled="!selectedType"
                @click="
                  pageNum = 1;
                  loadData();
                "
              >
                查询
              </ElButton>
              <ElButton :disabled="!selectedType" @click="resetDataFilters">重置</ElButton>
            </ElFormItem>
          </ElForm>

          <ElTable v-loading="dataLoading" :data="dataList" empty-text="请选择左侧字典类型">
            <ElTableColumn prop="dictLabel" label="字典标签" min-width="150" />
            <ElTableColumn prop="dictValue" label="字典值" min-width="150" />
            <ElTableColumn prop="sortOrder" label="排序" width="80" />
            <ElTableColumn label="状态" width="90">
              <template #default="{ row }">
                <ElTag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '正常' : '停用' }}</ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="createTime" label="创建时间" min-width="170" />
            <ElTableColumn label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <ElButton v-if="canOperate('sys:dict:edit')" link type="primary" @click="openEditData(asDictData(row))">编辑</ElButton>
                <ElButton v-if="canOperate('sys:dict:delete')" link type="danger" @click="confirmDeleteData(asDictData(row))">删除</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
          <div class="admin-business-page__pagination">
            <ElPagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :page-sizes="LIST_PAGE_SIZES"
              :total="dataTotal"
              layout="total, sizes, prev, pager, next"
              @current-change="loadData"
              @size-change="
                pageNum = 1;
                loadData();
              "
            />
          </div>
        </section>
      </div>
    </ElCard>

    <ElDialog v-model="typeDialogVisible" :title="typeForm.dictTypeId === undefined ? '新增字典类型' : '编辑字典类型'" width="480px" :lock-scroll="false">
      <ElForm ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <ElFormItem label="字典名称" prop="dictName"><ElInput v-model="typeForm.dictName" placeholder="请输入字典名称" /></ElFormItem>
        <ElFormItem label="类型编码" prop="dictType"><ElInput v-model="typeForm.dictType" placeholder="请输入类型编码" /></ElFormItem>
        <ElFormItem label="状态">
          <ElRadioGroup v-model="typeForm.status">
            <ElRadio :value="1">正常</ElRadio>
            <ElRadio :value="0">停用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="typeDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveType">保存</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="dataDialogVisible" :title="dataForm.dictDataId === undefined ? '新增字典数据' : '编辑字典数据'" width="500px" :lock-scroll="false">
      <ElForm ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="90px">
        <ElFormItem label="字典类型" prop="dictType"><ElInput v-model="dataForm.dictType" disabled /></ElFormItem>
        <ElFormItem label="字典标签" prop="dictLabel"><ElInput v-model="dataForm.dictLabel" placeholder="请输入字典标签" /></ElFormItem>
        <ElFormItem label="字典值" prop="dictValue"><ElInput v-model="dataForm.dictValue" placeholder="请输入字典值" /></ElFormItem>
        <ElFormItem label="排序"><ElInputNumber v-model="dataForm.sortOrder" :min="0" /></ElFormItem>
        <ElFormItem label="状态">
          <ElRadioGroup v-model="dataForm.status">
            <ElRadio :value="1">正常</ElRadio>
            <ElRadio :value="0">停用</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dataDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="saveData">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>

<style scoped lang="less">
.dictionary-management__card-header,
.dictionary-management__actions,
.dictionary-management__content,
.dictionary-management__tree-node {
  display: flex;
  align-items: center;
}
.dictionary-management__card-header {
  justify-content: space-between;
  gap: var(--space-3);
  line-height: 40px;
}
.dictionary-management__card-title {
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: 600;
}
.dictionary-management__catalog-version {
  margin-left: var(--space-3);
  color: var(--color-text-secondary);
  font-size: 12px;
}
.dictionary-management__actions {
  gap: var(--space-2);
}
.dictionary-management__content {
  min-height: 0;
  flex: 1;
  overflow: hidden;
}
.dictionary-management__directory {
  width: 264px;
  min-width: 264px;
  height: 100%;
  overflow: auto;
  padding-right: var(--space-4);
  border-right: 1px solid var(--color-border);
}
.dictionary-management__directory-title {
  margin-bottom: var(--space-3);
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}
.dictionary-management__tree-node {
  width: 100%;
  min-width: 0;
  justify-content: space-between;
  gap: var(--space-2);
}
.dictionary-management__tree-node > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dictionary-management__tree-node small {
  flex: 0 0 auto;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.dictionary-management__data-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding-left: var(--space-4);
  height: 100%;
}
.dictionary-management__data-filters {
  flex: 0 0 auto;
}
.dictionary-management__data-panel :deep(.el-table) {
  min-height: 0;
  flex: 1;
}
@media (max-width: 900px) {
  .dictionary-management__directory {
    width: 220px;
    min-width: 220px;
  }
}
</style>
