<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowRight, Bell, Trophy } from '@element-plus/icons-vue';
import type { LearnerHomeTask } from '@shared/types';
import { LEARNER_KIND_LABELS, learnerStatusLabel } from '@shared/utils/learner-home';
import { getAuthorizedPortalNavigation } from '@/router/admin-dynamic-routes';
import { getLearnerHome, getLearnerTarget } from '@/api/learner-home';
import { DEFAULT_LIST_PAGE_SIZE } from '@/constants/pagination';
import { useUserStore } from '@/stores';
import { useLearnerHomeStore } from '@/stores/modules/learner-home';
import { useLearnerMessageStore } from '@/stores/modules/learner-messages';

/** 首页只展示本人真实任务，不计算或伪造概览指标。 */
const user = useUserStore();
const router = useRouter();
const messages = useLearnerMessageStore();
/** 会话内快照：从任务详情返回首页时按原筛选/页码铺回列表，不再重复请求。 */
const home = useLearnerHomeStore();
/** 慢查询期间的安排消息合并补读，首页不会停留在旧任务。 */
let refreshPending = false;
const userName = computed(() => user.userInfo?.userName ?? user.userInfo?.userCode ?? '学员');
/** 快照归属账号：换账号后不复用上一个人的列表。 */
const snapshotOwner = computed(() => user.userInfo?.userCode ?? '');
/**
 * 证书入口按已授权菜单收敛：账号未分配「我的证书」菜单时不渲染入口按钮，
 * 避免按钮把学员带到未授权页面。判定与顶部门户导航同源，不新增权限码。
 */
const canOpenCertificates = computed(() =>
  getAuthorizedPortalNavigation(user.userInfo).some((entry) => entry.path === '/portal/certificates')
);
const rows = ref<LearnerHomeTask[]>([]),
  page = ref(1),
  total = ref(0),
  kind = ref('ALL'),
  error = ref(''),
  busy = ref(false),
  refreshing = ref(false);
const kinds = [
  { value: 'ALL', label: '全部任务' },
  { value: 'COURSE', label: '课程学习' },
  { value: 'FORMAL_EXAM', label: '考试' },
  { value: 'COURSE_QUIZ', label: '随堂测试' },
  { value: 'TRAINING', label: '培训' }
];
/** 状态标签只做展示着色，不据此推断访问权限。 */
function statusChipClass(status: string): string {
  if (status === 'COMPLETED') {
    return 'portal-chip--success';
  }
  if (status === 'UNFINISHED') {
    return 'portal-chip--warning';
  }
  return ['ENDED', 'EXPIRED', 'WITHDRAWN', 'CLEARED'].includes(status) ? 'portal-chip--plain' : '';
}
/** 类型与截止时间合并成一行，卡片只保留「标题 / 状态 / 类型与截止 / 主操作」四段。 */
function taskMeta(task: LearnerHomeTask): string {
  return task.endTime ? `${LEARNER_KIND_LABELS[task.kind]} · 截止 ${task.endTime.replace('T', ' ')}` : LEARNER_KIND_LABELS[task.kind];
}
/** 主操作文案由服务端下发的动作决定，不在前端推断可进入性。 */
function actionLabel(action: string): string {
  return action === 'VIEW_RESULT' ? '查看结果' : action === 'CONTINUE' ? '继续任务' : '进入任务';
}
/** 分类与分页沿用统一分页默认值；`silent` 为在线消息触发的后台补读，不占用加载遮罩。 */
async function load(silent = false): Promise<void> {
  if (busy.value || refreshing.value) {
    refreshPending = true;
    return;
  }
  if (silent) {
    refreshing.value = true;
  } else {
    busy.value = true;
  }
  error.value = '';
  try {
    const result = await getLearnerHome({ pageNum: page.value, pageSize: DEFAULT_LIST_PAGE_SIZE, 'query.kind': kind.value });
    rows.value = result.list;
    total.value = result.total;
    home.snapshot({ owner: snapshotOwner.value, kind: kind.value, page: page.value, rows: result.list, total: result.total });
  } catch (e) {
    error.value = e instanceof Error ? e.message : '学习任务加载失败';
  } finally {
    busy.value = false;
    refreshing.value = false;
    if (refreshPending) {
      refreshPending = false;
      void load(silent);
    }
  }
}
/** 进入原业务链路前重新校验资源，状态失效后不生成新报名。 */
async function open(task: LearnerHomeTask): Promise<void> {
  try {
    await router.push(await getLearnerTarget(task));
  } catch (e) {
    ElMessage.warning(e instanceof Error ? e.message : '当前任务已失效或无权访问');
  }
}
watch(() => messages.revision, () => load(true));
onMounted(() => {
  // 页内往返（任务详情、课程/考试子页）回到首页时按快照原样铺回列表与页签，
  // 不再重复请求；在线消息与手动刷新仍会重新读取真实数据。
  if (home.reusableBy(snapshotOwner.value)) {
    kind.value = home.kind;
    page.value = home.page;
    rows.value = home.rows;
    total.value = home.total;
    return;
  }
  void load();
});
</script>

<template>
  <section v-loading="busy" class="portal-list-page home-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY LEARNING</p>
        <h1>你好，{{ userName }}</h1>
        <p>查看已分配的课程、考试与培训任务。</p>
      </div>
      <div class="portal-heading__actions">
        <RouterLink class="portal-entry-link" to="/user-center/messages">
          <ElIcon><Bell /></ElIcon>
          消息中心
          <ElIcon><ArrowRight /></ElIcon>
        </RouterLink>
        <RouterLink v-if="canOpenCertificates" class="portal-entry-link" to="/portal/certificates">
          <ElIcon><Trophy /></ElIcon>
          我的证书
          <ElIcon><ArrowRight /></ElIcon>
        </RouterLink>
        <ElButton :loading="busy" @click="load()">刷新任务</ElButton>
      </div>
    </header>
    <nav class="portal-list-page__tabs" aria-label="学习任务分类">
      <button
        v-for="item in kinds"
        :key="item.value"
        type="button"
        class="portal-list-page__tab"
        :class="{ 'portal-list-page__tab--active': kind === item.value }"
        @click="
          kind = item.value;
          page = 1;
          load();
        "
      >
        {{ item.label }}
      </button>
    </nav>
    <ElAlert v-if="error" type="error" :title="error" :closable="false" />
    <div class="portal-section-head">
      <h2 class="portal-section-title">学习任务</h2>
      <span class="portal-section-hint">共 {{ total }} 条</span>
    </div>
    <section v-if="!busy && !error && !rows.length" class="portal-list-state">
      <ElEmpty description="暂无已分配的学习任务" />
    </section>
    <div v-else class="portal-list-grid home-task-grid">
      <ElCard v-for="task in rows" :key="task.key" class="portal-list-card home-task-card" shadow="never">
        <div class="portal-list-card__content home-task-card__content">
          <div class="home-task-card__main">
            <div class="portal-list-card__header">
              <h3 :title="task.title">{{ task.title }}</h3>
              <span class="portal-chip" :class="statusChipClass(task.status)">{{ learnerStatusLabel(task.status) }}</span>
            </div>
            <p class="portal-list-card__meta">{{ taskMeta(task) }}</p>
          </div>
          <div v-if="task.action" class="portal-list-card__actions home-task-card__actions">
            <ElButton type="primary" @click="open(task)">{{ actionLabel(task.action) }}</ElButton>
          </div>
          <!-- 无入口的任务在操作位放静态标签，不再另起一行提示：卡片始终保持两行信息。 -->
          <span
            v-else
            class="portal-chip portal-chip--plain home-task-card__idle"
            title="当前尚无可进入的入口，请等待安排或成绩发布。"
          >
            暂无入口
          </span>
        </div>
      </ElCard>
    </div>
    <div v-if="total" class="portal-list-page__pagination">
      <ElPagination
        v-model:current-page="page"
        :page-size="DEFAULT_LIST_PAGE_SIZE"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load()"
      />
    </div>
  </section>
</template>

<style scoped lang="less">
/* 首页是待办清单：页内节奏比目录页更紧，压缩卡片与分区之间的空白。 */
.home-page {
  gap: 20px;
}

.home-page .portal-heading__actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* 任务卡按 3~4 列排布；窄卡会把标题与操作区挤成多行，反而更拥挤。 */
.home-task-grid {
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  /* 目录页网格的 min-height: 220px 配合默认 align-content: normal（=stretch）会把「内容不足一行高」
     的页签整行拉伸到 220px（实测课程学习/培训 220px、全部任务 86~114px，同一页面两种卡面）。
     这里清掉最小高度并按顶对齐，用固定行高把卡片尺寸与页签、条目数、文案长度彻底解耦。 */
  min-height: 0;
  align-content: start;
  grid-auto-rows: 110px;
  gap: 14px;
}

/* 任务卡沿用门户卡片圆角与描边，但取消目录卡的最小高度，让高度只跟内容走。 */
.home-task-card {
  min-height: 0;
  border-radius: var(--radius-card);
}

.home-task-card :deep(.el-card__body),
.home-task-card__content {
  min-height: 0;
}

/* 任务卡是横向待办行：左侧标题/状态/类型与截止，右侧唯一主操作，卡片高度只由文本决定。 */
.home-task-card__content {
  flex-direction: row;
  align-items: center;
  gap: 16px;
  padding: 16px 18px;
}

.home-task-card__main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

/* 状态标签紧贴标题，不与右侧主操作挤在同一收口线上。 */
.home-task-card .portal-list-card__header {
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
}

.home-task-card .portal-list-card__meta {
  margin-top: 0;
}

/* 单条主操作保持自适应宽度并垂直居中，不再拉成整卡宽的实心长条，也不独占一行。 */
.home-task-card__actions {
  flex: none;
  margin-top: 0;
  padding-top: 0;
}

/* 无入口的任务在操作位放静态标签，卡片右侧不会空出半张卡。 */
.home-task-card__idle {
  align-self: center;
}

/* 标题最多两行，超出省略；固定行高下不会出现第三行把卡片撑破。 */
.home-task-card h3 {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow-wrap: anywhere;
  text-overflow: ellipsis;
}
</style>
