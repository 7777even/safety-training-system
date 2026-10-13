<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { deletePaper, getPaperPage, getPaperVersions, type PaperItem, type PaperType } from '@/api/paper';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const router = useRouter();
const userStore = useUserStore();
/** 根据 profile 按钮权限控制试卷操作入口。 */
const can = (permission: string): boolean => hasPermission(userStore.userInfo, permission);
const rows = ref<PaperItem[]>([]);
const total = ref(0);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const paperName = ref('');
const TYPES: Array<{ label: string; value: PaperType }> = [
  { label: '固定试卷', value: 'FIXED' },
  {
    label: '随机试卷',
    value: 'RANDOM'
  },
  { label: '自定义试卷', value: 'CUSTOM' }
];
const typeText = (type: PaperType): string => TYPES.find((item) => item.value === type)?.label ?? type;

/** 加载试卷分页列表。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const page = await getPaperPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...(paperName.value ? { 'query.paperName': paperName.value } : {})
    });
    rows.value = page.list;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

function query(): void {
  pageNum.value = 1;
  void load();
}

/** 删除未再使用的试卷，并刷新当前列表。 */
async function remove(row: unknown): Promise<void> {
  const value = row as Partial<PaperItem>;
  const paperId = Number(value.paperId);
  const paperName = String(value.paperName ?? '');
  if (!paperId) return;
  await ElMessageBox.confirm(`确定删除试卷“${paperName}”吗？`, '删除试卷', { type: 'warning' });
  await deletePaper(paperId);
  ElMessage.success('试卷已删除');
  await load();
}

/** 根据 currentVersionNo 查找真实版本 ID 后进入独立版本详情页。 */
async function viewCurrentVersion(row: unknown): Promise<void> {
  const paper = row as PaperItem;
  if (!paper.currentVersionNo) return;
  const versions = await getPaperVersions(paper.paperId);
  const current = versions.find((item) => item.versionNo === paper.currentVersionNo);
  if (!current) {
    ElMessage.warning('当前试卷版本不存在');
    return;
  }
  await router.push(`/admin/business-center/exams/paper/${paper.paperId}/version/${current.paperVersionId}`);
}
/** 将试卷归档状态转换为中文，避免直接向用户展示 Contract 枚举值。 */
function paperArchiveStatusText(status?: string): string {
  if (!status) return '—';
  if (status === 'ACTIVE') return '正常';
  if (status === 'ARCHIVED') return '已归档';
  return status;
}

onMounted(load);
</script>

<template>
  <section class="admin-business-page paper-page">
    <ElCard class="admin-business-page__filters" shadow="never">
      <ElForm inline>
        <ElFormItem label="试卷名称">
          <ElInput v-model="paperName" placeholder="请输入试卷名称" clearable @keyup.enter="query" />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="query">查询</ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>试卷列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton v-if="can('paper:paper:add')" type="primary" @click="router.push('/admin/business-center/exams/paper/workspace')">
              新增试卷
            </ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="rows">
        <ElTableColumn label="序号" width="55" align="center">
          <template #default="{ $index }">
            {{ (pageNum - 1) * pageSize + $index + 1 }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="paperCode" label="试卷编码" width="150" />
        <ElTableColumn prop="paperName" label="试卷名称" min-width="220" />
        <ElTableColumn label="类型" width="120">
          <template #default="{ row }">{{ typeText(row.paperType) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="currentVersionNo" label="当前版本" width="100" align="center">
          <template #default="{ row }">
            <ElTag>{{ row.currentVersionNo }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="archiveStatus" label="归档状态" width="100" align="center">
          <template #default="{ row }">
            <ElTag type="success">{{ paperArchiveStatusText(row.archiveStatus) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="270">
          <template #default="{ row }">
            <ElButton
              v-if="can('paper:paper:edit')"
              link
              type="primary"
              @click="router.push(`/admin/business-center/exams/paper/workspace/${row.paperId}`)"
            >
              编辑与组卷
            </ElButton>
            <ElButton v-if="can('paper:paper:list')" link type="primary" :disabled="!row.currentVersionNo" @click="viewCurrentVersion(row)">
              查看版本
            </ElButton>
            <ElButton v-if="can('paper:paper:delete')" link type="danger" @click="remove(row)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="query"
        />
      </div>
    </ElCard>
  </section>
</template>

<style scoped lang="less"></style>
