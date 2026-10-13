<script setup lang="ts">
import { LEARNER_CENTER_ROUTES, LEARNER_LIST_PAGE_SIZE } from '@/constants/learner-center';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import type { PersonalTeacher } from '@shared/types';
import { getPersonalTeachers, getTeacherFollows, getPersonalTeacher, setTeacherFollow } from '@/api/learner-center';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

/** 移动讲师关注直接引用同机构教师，详情只展示公开姓名。 */
const directory = ref(false); const rows = ref<PersonalTeacher[]>([]); const detail = ref<PersonalTeacher>();
const page = ref(1); const pageSize = LEARNER_LIST_PAGE_SIZE; const total = ref(0); const keyword = ref(''); const busy = ref(false); const error = ref(''); let detailId = 0;
/** 按当前筛选加载本人真实数据，失败时保留提示。 */
async function load(): Promise<void> { busy.value = true; error.value = ''; try { const params = { pageNum: page.value, pageSize, 'query.keyword': keyword.value }; const result = await (directory.value ? getPersonalTeachers(params) : getTeacherFollows(params)); rows.value = result.list; total.value = result.total; if (detailId) detail.value = await getPersonalTeacher(detailId); } catch (e) { error.value = e instanceof Error ? e.message : '加载失败'; } finally { busy.value = false; } }
/** 提交本人关系状态，并刷新服务端回显。 */
async function toggle(teacher: PersonalTeacher): Promise<void> { if (busy.value) return; try { await setTeacherFollow(teacher.teacherId, !teacher.followed); await load(); } catch (e) { uni.showToast({ title: e instanceof Error ? e.message : '操作失败', icon: 'none' }); } }
/** 进入受控业务详情或作答页面。 */
function open(id: number): void { uni.navigateTo({ url: `${LEARNER_CENTER_ROUTES.teachers}?teacherId=${id}` }); }
onLoad(query => { detailId = Number(query?.teacherId) || 0; });
onShow(load);
</script>
<template>
  <MobileBaseLayout>
    <view class="learner-page">
      <view class="learner-hero">
        <view class="learner-hero__deco" />
        <text class="learner-hero__eyebrow">{{ detail ? 'TEACHER DETAIL' : 'MY TEACHERS' }}</text>
        <text class="learner-hero__title">{{ detail ? '讲师详情' : '我的关注' }}</text>
        <text class="learner-hero__subtitle">关注同机构讲师，仅展示公开姓名与关系状态</text>
        <view v-if="!detail && !detailId" class="hero-actions">
          <view class="hero-action" @click="directory = !directory; page = 1; load()">{{ directory ? '返回我的关注' : '查看教师目录' }} ›</view>
        </view>
      </view>
      <text v-if="error" class="learner-error">{{ error }}</text>
      <view v-if="detail" class="learner-card">
        <view class="teacher-head">
          <text class="teacher-avatar teacher-avatar--lg">{{ detail.teacherName.slice(0, 1) }}</text>
          <view class="teacher-head__text">
            <text class="learner-title">{{ detail.teacherName }}</text>
            <text class="learner-meta">{{ detail.available ? '本机构在职教师' : '该教师当前不可访问' }}</text>
          </view>
        </view>
        <view class="learner-actions learner-actions--split">
          <view class="learner-actions__group">
            <text class="learner-meta">仅展示公开姓名</text>
          </view>
          <button class="learner-button learner-button--primary" :disabled="busy" @click="toggle(detail)">{{ detail.followed ? '取消关注' : '关注讲师' }}</button>
        </view>
      </view>
      <template v-else-if="!detailId">
        <view class="learner-toolbar">
          <input v-model="keyword" class="learner-input" placeholder="请输入教师姓名" @confirm="page = 1; load()">
          <button class="learner-button learner-button--primary" @click="page = 1; load()">查询</button>
        </view>
        <view v-for="teacher in rows" :key="teacher.teacherId" class="learner-card">
          <text class="learner-meta">{{ teacher.followed ? '已关注讲师' : '未关注讲师' }} · {{ teacher.available ? '本机构在职教师' : '该教师当前不可访问' }}</text>
          <view class="teacher-head">
            <text class="teacher-avatar">{{ teacher.teacherName.slice(0, 1) }}</text>
            <view class="teacher-head__text">
              <text class="learner-title">{{ teacher.teacherName }}</text>
              <text class="learner-meta">{{ teacher.followed ? '已加入我的关注，可随时取消' : '关注后可在此集中查看该讲师' }}</text>
            </view>
          </view>
          <view class="learner-actions learner-actions--split">
            <view class="learner-actions__group">
              <button class="learner-link" :disabled="busy || (!teacher.available && !teacher.followed)" @click="toggle(teacher)">{{ teacher.followed ? '取消关注' : '关注讲师' }}</button>
            </view>
            <button class="learner-button learner-button--primary" :disabled="!teacher.available" @click="open(teacher.teacherId)">讲师详情</button>
          </view>
        </view>
        <view v-if="!busy && !error && !total" class="learner-state">暂无教师</view>
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

/* 人物卡：头像与姓名同行，其余卡序与其它学员页一致。 */
.teacher-head {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.teacher-head__text {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 4rpx;
}

.teacher-avatar {
  display: block;
  width: 96rpx;
  height: 96rpx;
  flex: none;
  border-radius: 50%;
  background: linear-gradient(135deg, #155eef, #4f8bff);
  box-shadow: 0 8rpx 20rpx rgb(37 99 235 / 24%);
  color: #fff;
  font-size: 38rpx;
  font-weight: 600;
  line-height: 96rpx;
  text-align: center;
}

.teacher-avatar--lg {
  width: 120rpx;
  height: 120rpx;
  font-size: 48rpx;
  line-height: 120rpx;
}
</style>
