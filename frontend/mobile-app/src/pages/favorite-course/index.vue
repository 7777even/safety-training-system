<script setup lang="ts">
import { LEARNER_CENTER_ROUTES, LEARNER_LIST_PAGE_SIZE } from '@/constants/learner-center';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import type { FavoriteQuestion, PersonalFavoriteCourse } from '@shared/types';
import { getFavoriteCourseCatalog, getFavoriteCourses, getFavoriteCourseDetail, setCourseFavorite } from '@/api/learner-center';
import { getFavoriteQuestions, setQuestionFavorite } from '@/api/learner-practice';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

/** 移动课程关注和收藏共用数据；详情只读，学习仍需已有报名。 */
const title = ref('关注课程'); const catalog = ref(false); const rows = ref<PersonalFavoriteCourse[]>([]); const detail = ref<PersonalFavoriteCourse>();
/** 综合收藏按课程/题目切换，关注课程保持课程专属入口。 */
const favoriteTab = ref<'COURSES' | 'QUESTIONS'>('COURSES'); const questions = ref<FavoriteQuestion[]>([]);
const favoritesView = computed(() => title.value === '我的收藏');
const questionTab = computed(() => favoritesView.value && favoriteTab.value === 'QUESTIONS');
const page = ref(1); const pageSize = LEARNER_LIST_PAGE_SIZE; const total = ref(0); const error = ref(''); const busy = ref(false); const keyword = ref(''); let detailId = 0;
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> {
  busy.value = true; error.value = '';
  try {
    const params = { pageNum: page.value, pageSize, 'query.keyword': keyword.value };
    if (questionTab.value) { const value = await getFavoriteQuestions(params); questions.value = value.list; total.value = value.total; }
    else { const value = await (catalog.value ? getFavoriteCourseCatalog(params) : getFavoriteCourses(params)); rows.value = value.list; total.value = value.total; }
    if (detailId) detail.value = await getFavoriteCourseDetail(detailId);
  } catch (e) { error.value = e instanceof Error ? e.message : '加载失败'; }
  finally { busy.value = false; }
}
/** 分类切换重置搜索与分页，避免课程关键词影响题目列表。 */
async function switchTab(tab: 'COURSES' | 'QUESTIONS'): Promise<void> {
  if (busy.value || favoriteTab.value === tab) return;
  favoriteTab.value = tab; page.value = 1; keyword.value = ''; catalog.value = false; await load();
}
/** 取消题目收藏，原作答与错题处理状态保持不变。 */
async function removeQuestion(id: number): Promise<void> {
  if (busy.value) return;
  try { await setQuestionFavorite(id, false); await load(); }
  catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '取消收藏失败', icon: 'none' }); }
}
/** 查看本人收藏题目对应的原练习，不读取管理端题目。 */
function openQuestion(question: FavoriteQuestion): void {
  if (question.available && question.sessionId) uni.navigateTo({ url: `${LEARNER_CENTER_ROUTES.attempt}?id=${question.sessionId}&questionId=${question.questionId}` });
}
/** 提交本人关系状态，并刷新服务端回显。 */
async function toggle(course: PersonalFavoriteCourse): Promise<void> { if (busy.value) return; try { await setCourseFavorite(course.courseId, !course.favorite); await load(); } catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' }); } }
/** 进入受控业务详情或作答页面。 */
function open(id: number): void { uni.navigateTo({ url: `${LEARNER_CENTER_ROUTES.courses}?courseId=${id}` }); }
/** 进入已报名课程，不创建额外报名。 */
function learn(id: number): void { uni.navigateTo({ url: `/pages/course/detail?enrollmentId=${id}` }); }
onLoad(query => { if (query?.tab === 'favorites') title.value = '我的收藏'; detailId = Number(query?.courseId) || 0; });
onShow(load);
</script>
<template>
  <MobileBaseLayout>
    <view class="learner-page">
      <view class="learner-hero">
        <view class="learner-hero__deco" />
        <text class="learner-hero__eyebrow">{{ detail ? 'COURSE DETAIL' : (title === '我的收藏' ? 'MY FAVOURITES' : 'FOLLOWED COURSES') }}</text>
        <text class="learner-hero__title">{{ detail ? '课程详情' : title }}</text>
        <text class="learner-hero__subtitle">{{ favoritesView ? '分类管理收藏的课程和题目' : '查找可关注课程，管理已关注课程；关注不会自动报名' }}</text>
        <view v-if="!detail && !detailId && !favoritesView" class="hero-actions">
          <view class="hero-action" @click="catalog = !catalog; page = 1; load()">{{ catalog ? '返回关注课程' : '查看可关注课程' }} ›</view>
        </view>
      </view>
      <text v-if="error" class="learner-error">{{ error }}</text>
      <view v-if="detail" class="learner-card">
        <text class="learner-title">{{ detail.courseName }}</text>
        <text class="learner-meta">课程简介：{{ detail.introduction || '暂无' }}</text>
        <text class="learner-meta">教学目标：{{ detail.teachingObjective || '暂无' }}</text>
        <view class="learner-actions learner-actions--split">
          <view class="learner-actions__group">
            <button class="learner-link" :disabled="busy" @click="toggle(detail)">{{ detail.favorite ? '取消收藏' : '收藏课程' }}</button>
          </view>
          <button v-if="detail.enrollmentId" class="learner-button learner-button--primary" @click="learn(detail.enrollmentId)">进入已有课程</button>
        </view>
        <text v-if="!detail.enrollmentId" class="learner-meta">只读详情，收藏不会自动报名</text>
      </view>
      <template v-else-if="!detailId">
        <view v-if="favoritesView" class="segmented-filter">
          <button class="segmented-filter__item" :class="{ 'segmented-filter__item--active': favoriteTab === 'COURSES' }" :disabled="busy" @click="switchTab('COURSES')">收藏课程</button>
          <button class="segmented-filter__item" :class="{ 'segmented-filter__item--active': favoriteTab === 'QUESTIONS' }" :disabled="busy" @click="switchTab('QUESTIONS')">收藏题目</button>
        </view>
        <view class="learner-toolbar">
          <input v-model="keyword" class="learner-input" :placeholder="questionTab ? '请输入题干' : '请输入课程名称'" @confirm="page = 1; load()">
          <button class="learner-button learner-button--primary" @click="page = 1; load()">查询</button>
        </view>
        <template v-if="questionTab">
          <view v-for="question in questions" :key="question.questionId" class="learner-card">
            <text class="learner-title">{{ question.stem }}</text>
            <text class="learner-meta">来源：{{ question.sourceName || '当前不可访问' }}</text>
            <text v-if="!question.available" class="learner-meta">原练习已清除或来源不可访问，仍可取消收藏</text>
            <view class="learner-actions learner-actions--split">
              <button class="learner-link" :disabled="busy" @click="removeQuestion(question.questionId)">取消收藏</button>
              <button v-if="question.available && question.sessionId" class="learner-button learner-button--primary" @click="openQuestion(question)">查看作答</button>
            </view>
          </view>
        </template>
        <template v-else>
        <view v-for="course in rows" :key="course.courseId" class="learner-card">
          <text class="learner-meta">{{ course.favorite ? '已收藏课程' : '未收藏课程' }} · {{ course.available ? '课程当前可访问' : '该课程当前不可访问' }}</text>
          <text class="learner-title">{{ course.courseName }}</text>
          <text class="learner-card__text">{{ course.introduction || '暂无简介' }}</text>
          <view class="learner-actions learner-actions--split">
            <view class="learner-actions__group">
              <button class="learner-link" :disabled="busy || (!course.available && !course.favorite)" @click="toggle(course)">{{ course.favorite ? '取消收藏' : '收藏课程' }}</button>
            </view>
            <button class="learner-button learner-button--primary" :disabled="!course.available" @click="open(course.courseId)">课程详情</button>
          </view>
        </view>
        </template>
        <view v-if="!busy && !error && !total" class="learner-state">{{ questionTab ? '暂无收藏题目' : '暂无课程' }}</view>
        <view class="learner-toolbar learner-pager">
          <button class="learner-button" :disabled="page <= 1 || busy" @click="page--; load()">上一页</button>
          <text class="learner-meta">{{ page }}</text>
          <button class="learner-button" :disabled="page * pageSize >= total || busy" @click="page++; load()">下一页</button>
        </view>
      </template>
    </view>
  </MobileBaseLayout>
</template>
<style scoped lang="less">
.learner-pager {
  justify-content: center;
}
</style>
