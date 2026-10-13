<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { User } from '@element-plus/icons-vue';
import type { PersonalTeacher } from '@shared/types';
import { getPersonalTeachers, getTeacherFollows, getPersonalTeacher, setTeacherFollow } from '@/api/learner-center';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';

/** 教师目录与本人关注列表共用公开字段，详情不展示管理端用户资料。 */
const route = useRoute(); const router = useRouter(); const directory = ref(false); const rows = ref<PersonalTeacher[]>([]);
const page = ref(1); const total = ref(0); const keyword = ref(''); const busy = ref(false); const error = ref(''); const detail = ref<PersonalTeacher>();
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> { busy.value = true; error.value = ''; try { const params = { pageNum: page.value, pageSize: DEFAULT_LIST_PAGE_SIZE, 'query.keyword': keyword.value }; const result = await (directory.value ? getPersonalTeachers(params) : getTeacherFollows(params)); rows.value = result.list; total.value = result.total; } catch (e) { error.value = e instanceof Error ? e.message : '加载失败'; } finally { busy.value = false; } }
/** 提交本人关系状态，并刷新服务端回显。 */
async function toggle(teacher: PersonalTeacher): Promise<void> { try { await setTeacherFollow(teacher.teacherId, !teacher.followed); teacher.followed = !teacher.followed; if (detail.value?.teacherId === teacher.teacherId) detail.value.followed = teacher.followed; await load(); } catch (e) { ElMessage.error(e instanceof Error ? e.message : '操作失败'); } }
/** 根据页面资源引用读取受控详情。 */
async function readDetail(): Promise<void> { const id = Number(route.query.teacherId); detail.value = undefined; if (!id) return; try { detail.value = await getPersonalTeacher(id); } catch (e) { ElMessage.error(e instanceof Error ? e.message : '无权访问教师'); } }
/** 关闭详情并移除页面资源查询参数。 */
function closeDetail(): void { void router.replace({ path: route.path }); }
watch(() => route.query.teacherId, readDetail);
onMounted(async () => { await load(); await readDetail(); });
</script>

<template>
  <section v-loading="busy" class="portal-list-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">MY TEACHERS</p>
        <h1>我的关注</h1>
        <p>关注同机构讲师，查看讲师详情。</p>
      </div>
      <ElButton class="portal-heading__button" @click="directory = !directory; page = 1; load()">{{ directory ? '返回我的关注' : '查看教师目录' }}</ElButton>
    </header>
    <div class="portal-toolbar">
      <ElInput v-model="keyword" placeholder="请输入教师姓名" clearable @keyup.enter="page = 1; load()" />
      <ElButton type="primary" @click="page = 1; load()">查询</ElButton>
    </div>
    <ElAlert v-if="error" type="error" :title="error" :closable="false" />
    <section v-if="!busy && !error && !total" class="portal-list-state">
      <ElEmpty :description="directory ? '暂无有效教师' : '暂无关注讲师'" />
    </section>
    <div v-else class="portal-list-grid">
      <ElCard v-for="teacher in rows" :key="teacher.teacherId" class="portal-list-card" shadow="never">
        <div class="portal-list-card__cover portal-cover">
          <span class="portal-cover__avatar"><em>{{ teacher.teacherName.slice(0, 1) }}</em></span>
          <span class="portal-cover__text">
            <strong>{{ teacher.followed ? '已关注讲师' : '未关注讲师' }}</strong>
            <em>{{ teacher.available ? '本机构在职教师' : '该教师当前不可访问' }}</em>
          </span>
          <ElIcon class="portal-cover__mark"><User /></ElIcon>
        </div>
        <div class="portal-list-card__content">
          <div class="portal-list-card__header"><h3>{{ teacher.teacherName }}</h3></div>
          <p class="portal-list-card__meta">{{ teacher.followed ? '已加入我的关注，可随时取消' : '关注后可在此集中查看该讲师' }}</p>
          <div class="portal-list-card__actions portal-list-card__actions--split">
            <span class="portal-list-card__actions-group">
              <ElButton link :disabled="!teacher.available && !teacher.followed" @click="toggle(teacher)">{{ teacher.followed ? '取消关注' : '关注讲师' }}</ElButton>
            </span>
            <ElButton :disabled="!teacher.available" type="primary" @click="router.push({ path: route.path, query: { teacherId: teacher.teacherId } })">讲师详情</ElButton>
          </div>
        </div>
      </ElCard>
    </div>
    <div class="portal-list-page__pagination">
      <ElPagination v-model:current-page="page" :page-size="DEFAULT_LIST_PAGE_SIZE" :page-sizes="LIST_PAGE_SIZES" layout="total, sizes, prev, pager, next" :total="total" @current-change="load" />
    </div>
    <ElDrawer :model-value="Boolean(detail)" title="讲师详情" size="420px" @close="closeDetail">
      <template v-if="detail">
        <div class="teacher-detail__head">
          <span class="portal-cover__avatar portal-cover__avatar--lg"><em>{{ detail.teacherName.slice(0, 1) }}</em></span>
          <div>
            <h3 class="detail-title">{{ detail.teacherName }}</h3>
            <p class="detail-line">{{ detail.available ? '本机构在职教师' : '该教师当前不可访问' }}</p>
          </div>
        </div>
        <div class="detail-actions"><ElButton type="primary" @click="toggle(detail)">{{ detail.followed ? '取消关注' : '关注讲师' }}</ElButton></div>
      </template>
    </ElDrawer>
  </section>
</template>

<style scoped lang="less">
.portal-heading__button {
  flex: none;
}

.portal-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);

  .el-input {
    width: 240px;
  }
}

/* 详情抽屉头像与列表封面头像同款，仅放大一档。 */
.teacher-detail__head {
  display: flex;
  margin-bottom: var(--space-4);
  align-items: center;
  gap: var(--space-3);
}

.detail-title {
  margin: 0 0 4px;
  color: var(--color-text-primary);
  font-size: 18px;
  font-weight: 600;
}

.detail-line {
  margin: 0;
  color: var(--color-text-regular);
  font-size: 13px;
}

.detail-actions {
  display: flex;
  gap: var(--space-3);
}
</style>
