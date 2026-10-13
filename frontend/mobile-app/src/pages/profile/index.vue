<script setup lang="ts">
import { LEARNER_CENTER_ROUTES } from '@/constants/learner-center';
import { onShow } from '@dcloudio/uni-app';
import { computed, ref } from 'vue';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';
import { useUserStore } from '@/stores';
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue';

const userStore = useUserStore();
const isLoggingOut = ref(false);

/** 账号信息行：标签固定、取值右对齐，缺失字段直接不生成行。 */
interface ProfileFact {
  label: string;
  value: string;
}

/** 三个业务入口均为 tabBar 页面，点击后切换到对应首页而不是压栈。 */
const taskEntries = [
  { key: 'course', label: '我的课程', hint: '继续安全教育学习任务', icon: 'list', path: '/pages/course/index' },
  { key: 'exam', label: '我的考试', hint: '查看考试与练习结果', icon: 'compose', path: '/pages/exam/index' },
  { key: 'training', label: '我的培训', hint: '跟踪培训进度与结果', icon: 'medal', path: '/pages/training/index' }
] as const;

/** 个人成果入口为普通页面，navigateTo 压栈以便返回个人中心。 */
const outcomeEntries = [
  { key: 'my-profile', label: '资料与改密', hint: '完善本人资料和修改密码', icon: 'person', path: '/pages/profile/edit' },
  { key: 'messages', label: '消息中心', hint: '课程、考试与培训通知', icon: 'chat', path: '/pages/messages/index' },
  { key: 'teacher-follow', label: '我的关注', hint: '关注本机构讲师', icon: 'person', path: LEARNER_CENTER_ROUTES.teachers },
  {
    key: 'course-favorites',
    label: '我的收藏',
    hint: '分类管理收藏课程与题目',
    icon: 'star',
    path: `${LEARNER_CENTER_ROUTES.courses}?tab=favorites`
  },
  { key: 'course-follow', label: '关注课程', hint: '收藏、取消及查看课程详情', icon: 'star', path: LEARNER_CENTER_ROUTES.courses },
  { key: 'personal-exams', label: '考试与随堂测试', hint: '查看两类考核及历史考卷', icon: 'compose', path: LEARNER_CENTER_ROUTES.exams },
  { key: 'practice', label: '我的练习', hint: '专项与题库练习', icon: 'compose', path: LEARNER_CENTER_ROUTES.practices },
  { key: 'wrong', label: '我的错题集', hint: '复习考试与课程错题', icon: 'list', path: `${LEARNER_CENTER_ROUTES.practices}?tab=wrongs` },
  { key: 'certificate', label: '我的证书', hint: '查看已签发的培训证书', icon: 'checkbox-filled', path: '/pages/certificate/index' },
  { key: 'archive', label: '我的培训档案', hint: '按类型汇总培训结果', icon: 'paperclip', path: '/pages/archive/index' },
  { key: 'history', label: '统一学习档案', hint: '全部历史与只读更正轨迹', icon: 'paperclip', path: '/pages/history/index' }
] as const;

/** 头像占位取姓名或账号首字，资料缺失时也保留可辨识标识。 */
const avatarText = computed(() => {
  const source = userStore.userInfo?.userName || userStore.userInfo?.userCode || '学';
  return source.slice(0, 1);
});

/** 主标题优先展示姓名，未取到资料时退回账号，避免出现空白页头。 */
const displayName = computed(() => userStore.userInfo?.userName || userStore.userInfo?.userCode || '未获取账号信息');

/** 手机号仅对 11 位号码做中段脱敏，非手机号形态保持原样，不在个人中心明文暴露联系方式。 */
const maskedPhone = computed(() => {
  const phone = userStore.userInfo?.phone ?? '';
  return /^\d{11}$/.test(phone) ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone;
});

/** 账号信息按固定顺序成行，缺失字段过滤掉，避免出现空值行。 */
const profileFacts = computed<ProfileFact[]>(() =>
  [
    { label: '登录账号', value: userStore.userInfo?.userCode ?? '' },
    { label: '姓名', value: userStore.userInfo?.userName ?? '' },
    { label: '所属部门', value: userStore.userInfo?.deptName ?? '' },
    { label: '手机号', value: maskedPhone.value }
  ].filter((fact) => Boolean(fact.value))
);

/** tabBar 页面必须用 switchTab 打开，用 navigateTo 会破坏返回栈。 */
function openEntry(path: string): void {
  uni.switchTab({ url: path });
}

/** 普通页面用 navigateTo 压栈，返回时回到个人中心。 */
function openOutcome(path: string): void {
  uni.navigateTo({ url: path });
}

/** 未登录用户不能查看个人中心，统一返回登录页。 */
onShow(() => {
  if (!userStore.token) {
    uni.reLaunch({ url: '/pages/auth/LoginPage' });
  }
});

/** 确认退出当前账号，完成本地会话清理后回到登录页。 */
function handleLogout(): void {
  if (isLoggingOut.value) {
    return;
  }

  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    confirmText: '退出',
    cancelText: '取消',
    success: async ({ confirm }) => {
      if (!confirm) {
        return;
      }

      isLoggingOut.value = true;

      try {
        await userStore.logout();
      } catch {
        // 服务端退出失败时，store 仍会清理本地会话，避免账号继续留在当前设备。
        uni.showToast({ title: '已退出本地账号', icon: 'none' });
      } finally {
        isLoggingOut.value = false;
        uni.reLaunch({ url: '/pages/auth/LoginPage' });
      }
    }
  });
}
</script>

<template>
  <MobileBaseLayout>
    <view class="page">
      <!-- 页头 hero：与学习、考试、培训三页同一条蓝色渐变横幅，保证四个 tab 视觉同源。 -->
      <view class="profile-hero">
        <view class="profile-hero__deco" />
        <view class="profile-hero__avatar">
          <text class="profile-hero__avatar-text">{{ avatarText }}</text>
        </view>
        <view class="profile-hero__identity">
          <text class="profile-hero__name">{{ displayName }}</text>
          <text class="profile-hero__account">{{ userStore.userInfo?.userCode ?? '未获取账号信息' }}</text>
        </view>
      </view>

      <view class="profile-card">
        <view class="profile-card__head">
          <text class="profile-card__title">账号信息</text>
          <text class="profile-card__note">来自当前登录会话</text>
        </view>
        <view v-if="profileFacts.length" class="fact-list">
          <view v-for="fact in profileFacts" :key="fact.label" class="fact-row">
            <text class="fact-row__label">{{ fact.label }}</text>
            <text class="fact-row__value">{{ fact.value }}</text>
          </view>
        </view>
        <text v-else class="profile-card__empty">暂无账号信息</text>
      </view>

      <view class="profile-card">
        <view class="profile-card__head">
          <text class="profile-card__title">我的任务</text>
          <text class="profile-card__note">进入对应模块继续办理</text>
        </view>
        <view class="entry-list">
          <view v-for="entry in taskEntries" :key="entry.key" class="entry-row" @click="openEntry(entry.path)">
            <view class="entry-row__icon">
              <UniIcons :type="entry.icon" size="20" color="#2563eb" />
            </view>
            <view class="entry-row__text">
              <text class="entry-row__label">{{ entry.label }}</text>
              <text class="entry-row__hint">{{ entry.hint }}</text>
            </view>
            <view class="entry-row__arrow">
              <UniIcons type="right" size="14" color="#94a3b8" />
            </view>
          </view>
        </view>
      </view>

      <view class="profile-card">
        <view class="profile-card__head">
          <text class="profile-card__title">我的成果</text>
          <text class="profile-card__note">证书与培训档案</text>
        </view>
        <view class="entry-list">
          <view v-for="entry in outcomeEntries" :key="entry.key" class="entry-row" @click="openOutcome(entry.path)">
            <view class="entry-row__icon">
              <UniIcons :type="entry.icon" size="20" color="#2563eb" />
            </view>
            <view class="entry-row__text">
              <text class="entry-row__label">{{ entry.label }}</text>
              <text class="entry-row__hint">{{ entry.hint }}</text>
            </view>
            <view class="entry-row__arrow">
              <UniIcons type="right" size="14" color="#94a3b8" />
            </view>
          </view>
        </view>
      </view>

      <button class="logout-button" :disabled="isLoggingOut" :loading="isLoggingOut" @tap="handleLogout">退出登录</button>
    </view>
  </MobileBaseLayout>
</template>

<style scoped lang="less">
.page {
  padding: 24rpx 0 64rpx;
}

/* 身份 hero：与三个业务页共用同一渐变、同一圆角与阴影，只把内容换成账号身份。 */
.profile-hero {
  position: relative;
  display: flex;
  overflow: hidden;
  padding: 40rpx 36rpx;
  align-items: center;
  gap: 26rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #155eef 0%, #4f8bff 100%);
  box-shadow: 0 16rpx 32rpx rgb(21 94 239 / 18%);
}

.profile-hero__deco {
  position: absolute;
  top: -90rpx;
  right: -70rpx;
  width: 280rpx;
  height: 280rpx;
  border-radius: 50%;
  background: rgb(255 255 255 / 14%);
}

.profile-hero__avatar {
  position: relative;
  z-index: 1;
  display: flex;
  width: 108rpx;
  height: 108rpx;
  flex: none;
  align-items: center;
  justify-content: center;
  border: 2rpx solid rgb(255 255 255 / 38%);
  border-radius: 50%;
  background: rgb(255 255 255 / 20%);
}

.profile-hero__avatar-text {
  color: #fff;
  font-size: 44rpx;
  font-weight: 700;
}

.profile-hero__identity {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.profile-hero__name,
.profile-hero__account {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-hero__name {
  color: #fff;
  font-size: 38rpx;
  font-weight: 700;
}

.profile-hero__account {
  margin-top: 10rpx;
  color: rgb(255 255 255 / 78%);
  font-size: 24rpx;
}

.profile-card {
  margin-top: 26rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: var(--color-bg-surface);
  box-shadow: 0 8rpx 24rpx rgb(38 39 45 / 6%);
}

.profile-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16rpx;
}

.profile-card__title {
  color: var(--color-text-primary);
  font-size: 30rpx;
  font-weight: 700;
}

.profile-card__note {
  flex: 1;
  min-width: 0;
  color: var(--color-text-secondary);
  font-size: 22rpx;
  text-align: right;
}

.profile-card__empty {
  display: block;
  margin-top: 22rpx;
  color: var(--color-text-secondary);
  font-size: 24rpx;
}

/* 账号字段沿用列表卡的白底浅灰字段区，标签左、取值右对齐。 */
.fact-list {
  margin-top: 22rpx;
  padding: 12rpx 24rpx;
  border-radius: 16rpx;
  background: var(--color-bg-subtle);
}

.fact-row {
  display: flex;
  padding: 14rpx 0;
  align-items: center;
  gap: 24rpx;
}

.fact-row__label {
  flex: none;
  color: var(--color-text-secondary);
  font-size: 24rpx;
}

.fact-row__value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: 25rpx;
  font-weight: 500;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.entry-list {
  margin-top: 10rpx;
}

.entry-row {
  display: flex;
  padding: 22rpx 0;
  align-items: center;
  gap: 22rpx;
}

.entry-row + .entry-row {
  border-top: 1rpx solid #f1f1f3;
}

.entry-row__icon {
  display: flex;
  width: 72rpx;
  height: 72rpx;
  flex: none;
  align-items: center;
  justify-content: center;
  border-radius: 20rpx;
  background: var(--color-primary-subtle);
}

.entry-row__text {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.entry-row__label {
  color: var(--color-text-primary);
  font-size: 28rpx;
  font-weight: 600;
}

.entry-row__hint {
  margin-top: 8rpx;
  color: var(--color-text-secondary);
  font-size: 22rpx;
}

.entry-row__arrow {
  display: flex;
  flex: none;
  align-items: center;
}

/* 退出登录沿用列表卡按钮的胶囊语言，仅用危险色描边区分破坏性操作。 */
.logout-button {
  width: 100%;
  min-height: 88rpx;
  margin: 40rpx 0 0;
  border: 1rpx solid #fecaca;
  border-radius: 999rpx;
  background: var(--color-bg-surface);
  color: var(--color-danger);
  font-size: 28rpx;
  font-weight: 600;
  line-height: 88rpx;
  box-shadow: 0 8rpx 20rpx rgb(220 38 38 / 8%);
}

.logout-button::after {
  border: 0;
}

.logout-button[disabled] {
  color: var(--color-text-secondary);
  border-color: var(--color-border);
  box-shadow: none;
}
</style>
