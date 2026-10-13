<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Collection, Star } from '@element-plus/icons-vue';
import type { FavoriteQuestion, PersonalFavoriteCourse } from '@shared/types';
import { getFavoriteCourseCatalog, getFavoriteCourses, getFavoriteCourseDetail, setCourseFavorite } from '@/api/learner-center';
import { getFavoriteQuestions, setQuestionFavorite } from '@/api/learner-practice';
import { LEARNER_CENTER_ROUTES } from '@/constants/learner-center';
import { getExamEnumLabel, QUESTION_TYPE_OPTIONS } from '@/constants/exam-options';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';

/** 54与58复用本人收藏关系，课程详情访问不创建报名。 */
const route = useRoute(); const router = useRouter(); const catalog = ref(false); const rows = ref<PersonalFavoriteCourse[]>([]);
const page = ref(1); const total = ref(0); const busy = ref(false); const keyword = ref(''); const error = ref(''); const detail = ref<PersonalFavoriteCourse>();
const title = computed(() => route.name === 'LearnerCourseFavorites' ? '我的收藏' : '关注课程');
/** 关注课程专属课程管理；我的收藏汇总课程与题目。 */
const favoritesView = computed(() => route.name === 'LearnerCourseFavorites');
const favoriteTab = ref<'COURSES' | 'QUESTIONS'>('COURSES');
const questionTab = computed(() => favoritesView.value && favoriteTab.value === 'QUESTIONS');
const questions = ref<FavoriteQuestion[]>([]);
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> {
  busy.value = true; error.value = '';
  try {
    const params = { pageNum: page.value, pageSize: DEFAULT_LIST_PAGE_SIZE, 'query.keyword': keyword.value };
    if (questionTab.value) { const result = await getFavoriteQuestions(params); questions.value = result.list; total.value = result.total; }
    else { const result = await (catalog.value ? getFavoriteCourseCatalog(params) : getFavoriteCourses(params)); rows.value = result.list; total.value = result.total; }
  } catch (e) { error.value = e instanceof Error ? e.message : '加载失败'; }
  finally { busy.value = false; }
}
/** 取消本人题目收藏，原练习和错题记录不受影响。 */
async function removeQuestion(questionId: number): Promise<void> {
  if (busy.value) return;
  try { await setQuestionFavorite(questionId, false); await load(); }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '取消收藏失败'); }
}
/** 提交本人关系状态，并刷新服务端回显。 */
async function toggle(course: PersonalFavoriteCourse): Promise<void> { try { await setCourseFavorite(course.courseId, !course.favorite); course.favorite = !course.favorite; if (detail.value?.courseId === course.courseId) detail.value.favorite = course.favorite; await load(); } catch (e) { ElMessage.error(e instanceof Error ? e.message : '操作失败'); } }
/** 根据页面资源引用读取受控详情。 */
async function readDetail(): Promise<void> { const id = Number(route.query.courseId); detail.value = undefined; if (!id) return; try { detail.value = await getFavoriteCourseDetail(id); } catch (e) { ElMessage.error(e instanceof Error ? e.message : '无权查看详情'); } }
/** 关闭详情并移除页面资源查询参数。 */
function closeDetail(): void { void router.replace({ path: route.path }); }
watch(() => route.query.courseId, readDetail); watch(() => route.name, () => { catalog.value = false; favoriteTab.value = 'COURSES'; page.value = 1; keyword.value = ''; void load(); });
watch(favoriteTab, () => { catalog.value = false; page.value = 1; keyword.value = ''; void load(); });
onMounted(async () => { await load(); await readDetail(); });
</script>

<template>
  <section v-loading="busy" class="portal-list-page">
    <header class="portal-list-page__heading">
      <div>
        <p class="portal-list-page__eyebrow">{{ title === '我的收藏' ? 'MY FAVOURITES' : 'FOLLOWED COURSES' }}</p>
        <h1>{{ title }}</h1>
        <p>{{ favoritesView ? '分类管理收藏的课程和题目。' : '查找可关注课程，管理已关注课程；关注不会自动报名。' }}</p>
      </div>
      <ElButton v-if="!favoritesView" class="portal-heading__button" @click="catalog = !catalog; page = 1; load()">{{ catalog ? '返回已关注课程' : '查看可关注课程' }}</ElButton>
    </header>
    <div class="portal-toolbar">
      <ElRadioGroup v-if="favoritesView" v-model="favoriteTab" :disabled="busy" aria-label="收藏分类">
        <ElRadioButton value="COURSES">收藏课程</ElRadioButton>
        <ElRadioButton value="QUESTIONS">收藏题目</ElRadioButton>
      </ElRadioGroup>
      <ElInput v-model="keyword" :placeholder="questionTab ? '请输入题干' : '请输入课程名称'" clearable @keyup.enter="page = 1; load()" />
      <ElButton type="primary" @click="page = 1; load()">查询</ElButton>
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <section v-if="!busy && !error && !total" class="portal-list-state">
      <ElEmpty :description="questionTab ? '暂无收藏题目' : catalog ? '暂无可关注课程' : favoritesView ? '暂无收藏课程' : '暂无关注课程'" />
    </section>
    <div v-else-if="questionTab" class="portal-list-grid">
      <ElCard v-for="question in questions" :key="question.questionId" class="portal-list-card" shadow="never">
        <div class="portal-list-card__content">
          <div class="portal-list-card__header"><h3>{{ question.stem }}</h3></div>
          <p class="portal-list-card__meta">{{ getExamEnumLabel(question.type, QUESTION_TYPE_OPTIONS) }} · {{ question.sourceName || '来源当前不可访问' }}</p>
          <p v-if="!question.available" class="portal-list-card__meta">原练习已清除或来源不可访问，仍可取消收藏。</p>
          <div class="portal-list-card__actions portal-list-card__actions--split">
            <ElButton link :disabled="busy" @click="removeQuestion(question.questionId)">取消收藏</ElButton>
            <ElButton v-if="question.available && question.sessionId" type="primary" @click="router.push({ path: `${LEARNER_CENTER_ROUTES.attempt}/${question.sessionId}`, query: { questionId: question.questionId } })">查看作答</ElButton>
          </div>
        </div>
      </ElCard>
    </div>
    <div v-else class="portal-list-grid">
      <ElCard v-for="course in rows" :key="course.courseId" class="portal-list-card" shadow="never">
        <div class="portal-list-card__cover portal-cover">
          <span class="portal-cover__icon"><ElIcon><Collection v-if="course.favorite" /><Star v-else /></ElIcon></span>
          <span class="portal-cover__text">
            <strong>{{ course.favorite ? '已收藏课程' : '未收藏课程' }}</strong>
            <em>{{ course.available ? '课程当前可访问' : '该课程当前不可访问' }}</em>
          </span>
          <ElIcon class="portal-cover__mark"><Collection /></ElIcon>
        </div>
        <div class="portal-list-card__content">
          <div class="portal-list-card__header"><h3>{{ course.courseName }}</h3></div>
          <p class="portal-list-card__meta">{{ course.introduction || '暂无简介' }}</p>
          <div class="portal-list-card__actions portal-list-card__actions--split">
            <span class="portal-list-card__actions-group">
              <ElButton link :disabled="!course.available && !course.favorite" @click="toggle(course)">{{ course.favorite ? '取消收藏' : '收藏课程' }}</ElButton>
            </span>
            <ElButton :disabled="!course.available" type="primary" @click="router.push({ path: route.path, query: { courseId: course.courseId } })">课程详情</ElButton>
          </div>
        </div>
      </ElCard>
    </div>
    <div class="portal-list-page__pagination">
      <ElPagination v-model:current-page="page" :page-size="DEFAULT_LIST_PAGE_SIZE" :page-sizes="LIST_PAGE_SIZES" layout="total, sizes, prev, pager, next" :total="total" @current-change="load" />
    </div>
    <ElDrawer :model-value="Boolean(detail)" title="课程详情" size="420px" @close="closeDetail">
      <template v-if="detail">
        <h3 class="detail-title">{{ detail.courseName }}</h3>
        <p class="detail-line">课程简介：{{ detail.introduction || '暂无' }}</p>
        <p class="detail-line">教学目标：{{ detail.teachingObjective || '暂无' }}</p>
        <div class="detail-actions">
          <ElButton @click="toggle(detail)">{{ detail.favorite ? '取消收藏' : '收藏课程' }}</ElButton>
          <ElButton v-if="detail.enrollmentId" type="primary" @click="router.push(`/portal/class/learning/${detail.enrollmentId}`)">进入已有课程学习</ElButton>
        </div>
        <p v-if="!detail.enrollmentId" class="detail-hint">此页为只读课程详情，收藏不会自动报名。</p>
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

.detail-title {
  margin: 0 0 var(--space-3);
  color: var(--color-text-primary);
  font-size: 16px;
  font-weight: 600;
}

.detail-line {
  margin: 0 0 var(--space-2);
  color: var(--color-text-regular);
  line-height: 1.7;
  white-space: pre-wrap;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin: var(--space-4) 0;
}

.detail-hint {
  color: var(--color-text-secondary);
  font-size: 13px;
}
</style>
