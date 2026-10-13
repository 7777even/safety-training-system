<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getOpsWorkbench, type OpsTodo, type OpsWorkbench } from '@/api/ops-workbench';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const router = useRouter();
const userStore = useUserStore();
const summary = ref<OpsWorkbench>();
const loading = ref(false);

/** 待办来自服务端计数；编码仅用于跳转到已有业务列表。 */
const todoDestinations: Record<string, { path: string; permission: string }> = {
  DRAFT_COURSE: { path: '/admin/business-center/course/center', permission: 'course:course:list' },
  DRAFT_PLAN: { path: '/admin/business-center/training/plan', permission: 'training:plan:list' },
  PENDING_PARTICIPANT: { path: '/admin/business-center/training/plan', permission: 'training:plan:list' }
};

/** 接口授权、当前权限和实际注册路由同时满足才显示可点击入口。 */
const shortcuts = computed(() => summary.value?.shortcuts.filter((item) =>
  hasPermission(userStore.userInfo, item.permission) && router.resolve(item.path).matched.length > 0) ?? []);

/** 待办分类与服务端总数一起展示，仅在有权限且路由已注册时提供跳转。 */
function canOpenTodo(code: OpsTodo['code']): boolean {
  const destination = todoDestinations[code];
  if (!destination) return false;
  return hasPermission(userStore.userInfo, destination.permission) && router.resolve(destination.path).matched.length > 0;
}

function openTodo(code: OpsTodo['code']): void {
  if (canOpenTodo(code)) void router.push(todoDestinations[code].path);
}

/** 可跳转的待办行给出指针光标，只读行不响应点击。 */
function todoRowClass({ row }: { row: OpsTodo }): string {
  return canOpenTodo(row.code) ? 'is-clickable' : '';
}

/** 加载企业自己的真实概览与分类待办。 */
async function load(): Promise<void> {
  loading.value = true;
  try { summary.value = await getOpsWorkbench(); }
  finally { loading.value = false; }
}
onMounted(load);
</script>

<template>
  <section v-loading="loading" class="admin-detail-page">
    <ElCard class="admin-detail-page__card" shadow="never">
      <section v-if="summary" class="ws-hero">
        <div class="ws-hero__main">
          <p class="ws-hero__eyebrow">ENTERPRISE WORKBENCH</p>
          <div class="ws-hero__title-row">
            <h2 class="ws-hero__title">企业业务概览</h2>
            <ElTag v-if="summary.openTodoCount > 0" type="warning" effect="plain">{{ summary.openTodoCount }} 项待办待处理</ElTag>
            <ElTag v-else type="success" effect="plain">待办已清空</ElTag>
          </div>
          <div class="ws-hero__meta">
            <span class="ws-hero__chip">统计范围 本机构</span>
            <span class="ws-hero__chip">数据来源 企业工作台汇总</span>
            <span>仅统计当前登录机构下的课程、考试与培训，待办按业务分类计数。</span>
          </div>
        </div>
        <div class="ws-hero__aside">
          <div class="ws-hero__state">
            <strong class="workbench-todo">{{ summary.openTodoCount }}</strong>
            <small>待办事项</small>
          </div>
        </div>
      </section>

      <section v-if="summary" class="ws-metrics workbench-metrics">
        <div class="ws-metric">
          <span class="ws-metric__label">已发布课程</span>
          <span class="ws-metric__value">{{ summary.publishedCourseCount }}<em>门</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">进行中考试</span>
          <span class="ws-metric__value">{{ summary.runningExamCount }}<em>场</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">进行中培训</span>
          <span class="ws-metric__value">{{ summary.runningPlanCount }}<em>个计划</em></span>
        </div>
        <div class="ws-metric">
          <span class="ws-metric__label">当前参训</span>
          <span class="ws-metric__value">{{ summary.currentParticipantCount }}<em>人</em></span>
        </div>
        <div class="ws-metric ws-metric--pass">
          <span class="ws-metric__label">已结束合格</span>
          <span class="ws-metric__value">{{ summary.endedPassedCount }}<em>人</em></span>
        </div>
        <div class="ws-metric ws-metric--fail">
          <span class="ws-metric__label">已结束不合格</span>
          <span class="ws-metric__value">{{ summary.endedFailedCount }}<em>人</em></span>
        </div>
      </section>

      <section v-if="shortcuts.length" class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>已授权业务入口</h2>
            <p>仅显示当前账号已授权且路由已注册的入口。</p>
          </div>
          <span class="ws-section__extra">共 {{ shortcuts.length }} 个</span>
        </header>
        <ElSpace wrap>
          <ElButton v-for="item in shortcuts" :key="item.code" @click="router.push(item.path)">{{ item.title }}</ElButton>
        </ElSpace>
      </section>

      <section class="ws-section">
        <header class="ws-section__head">
          <div>
            <h2>待办事项</h2>
            <p>待办数量来自服务端计数，可跳转的行点击后进入对应业务列表。</p>
          </div>
          <span class="ws-section__extra">共 {{ summary?.openTodoCount ?? 0 }} 项</span>
        </header>
        <ElTable
          :data="summary?.todos ?? []"
          :row-class-name="todoRowClass"
          empty-text="暂无待办事项"
          @row-click="(row: OpsTodo) => openTodo(row.code)"
        >
          <ElTableColumn prop="title" label="待办分类" />
          <ElTableColumn prop="count" label="数量" width="120" />
          <ElTableColumn label="操作" width="110">
            <template #default="{ row }">
              <ElButton v-if="canOpenTodo(row.code)" link type="primary" @click.stop="openTodo(row.code)">进入列表</ElButton>
              <span v-else class="ws-kv__value--muted">—</span>
            </template>
          </ElTableColumn>
        </ElTable>
      </section>
    </ElCard>
  </section>
</template>

<style scoped lang="less">
.workbench-todo {
  color: var(--color-primary);
  font-size: 26px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 32px;
}

/* 概览 hero 与指标区之间补一段留白，指标区之后由 .ws-section 自带的相邻留白接管。 */
.workbench-metrics {
  margin: var(--space-4) 0;
}

/* 可跳转的待办行给出指针光标，提示整行可进入对应列表。 */
:deep(.el-table__row.is-clickable) {
  cursor: pointer;
}
</style>
