<script setup lang="ts">
import { reactive, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import MobileBaseLayout from '@/layouts/MobileBaseLayout.vue';

import type { MyProfile } from '@shared/types/my-profile';
import { prepareMyPassword } from '@shared/libs/my-password';
import { memberTypeLabel } from '@shared/utils/member-type';
import { getMyProfile, updateMyProfile, updateMyPassword } from '@/api/my-profile';
import { useUserStore } from '@/stores';

/** 个人资料和改密只处理当前本人，不提供用户选择和权限修改。 */
const user = useUserStore();
const profile = ref<MyProfile>(), busy = ref(false), error = ref('');
const form = reactive({ userName: '', phone: '', email: '' });
const password = reactive({ old: '', next: '', confirm: '' });
/** 每次加载以专用真实资料回填，禁止使用管理UserVO脱敏手机号。 */
async function load(): Promise<void> {
  profile.value = await getMyProfile();
  Object.assign(form, { userName: profile.value.userName, phone: profile.value.phone ?? '', email: profile.value.email ?? '' });
}
/** 显式构造白名单请求；版本冲突保留输入，提示重新读取。 */
async function save(): Promise<void> {
  if (!profile.value || busy.value) return;
  busy.value = true; error.value = '';
  try {
    await updateMyProfile({ userName: form.userName, phone: form.phone || null, email: form.email || null, version: profile.value.version });
    await load();
    if (user.userInfo) user.userInfo.userName = profile.value?.userName ?? form.userName;
    uni.showToast({ title: '资料已保存', icon: 'success' });
  } catch (e) { error.value = e instanceof Error ? e.message : '保存失败'; }
  finally { busy.value = false; }
}
/** 改密成功先清理所有本地账户状态，再返回登录；不记录密码。 */
async function changePassword(): Promise<void> {
  if (busy.value || !profile.value?.passwordChangeAllowed) return;
  busy.value = true; error.value = '';
  try {
    await updateMyPassword(await prepareMyPassword(password.old, password.next, password.confirm));
    password.old = password.next = password.confirm = '';
    user.clearSession();
    uni.showToast({ title: '密码已修改，请重新登录', icon: 'none' });
    uni.reLaunch({ url: '/pages/auth/LoginPage' });
  } catch (e) { error.value = e instanceof Error ? e.message : '改密失败'; }
  finally { busy.value = false; }
}
/** 手动重新读取时同样显示接口错误。 */
async function reload(): Promise<void> { try { await load(); } catch(e) { error.value = e instanceof Error ? e.message : '资料读取失败'; } }
/** 证书入口复用既有本人证书页。 */
function openCertificates(): void { uni.navigateTo({ url: '/pages/certificate/index' }); }
onShow(reload);
</script>
<template>
  <MobileBaseLayout>
    <view class="learner-page">
      <view class="learner-hero">
        <view class="learner-hero__deco" />
        <text class="learner-hero__eyebrow">MY PROFILE</text>
        <text class="learner-hero__title">本人资料与改密</text>
        <text class="learner-hero__subtitle">仅姓名、手机号与邮箱可自行维护</text>
        <view class="hero-actions">
          <view class="hero-action" @click="openCertificates">我的证书 ›</view>
          <view class="hero-action" @click="!busy && reload()">重新读取</view>
        </view>
      </view>

      <text v-if="error" class="learner-error">{{ error }}</text>

      <view v-if="profile" class="learner-card">
        <view class="learner-card__head">
          <text class="learner-title">账号信息</text>
          <text class="learner-badge learner-badge--plain">仅管理员可改</text>
        </view>
        <text class="learner-meta">账号：{{ profile.userCode }}</text>
        <text class="learner-meta">机构：{{ profile.orgName || profile.organizationId }}</text>
        <text class="learner-meta">部门：{{ profile.deptName || '未分配' }}</text>
        <text class="learner-meta">角色：{{ profile.roles.join('、') || '未分配' }}</text>
        <text class="learner-meta">身份：{{ memberTypeLabel(profile.memberType) }}</text>
      </view>

      <view v-if="profile" class="learner-card">
        <text class="learner-title">可修改资料</text>
        <view class="profile-field">
          <text class="learner-meta">姓名</text>
          <view class="learner-toolbar"><input v-model="form.userName" class="learner-input" maxlength="100" placeholder="请输入姓名" ></view>
        </view>
        <view class="profile-field">
          <text class="learner-meta">手机号</text>
          <view class="learner-toolbar"><input v-model="form.phone" class="learner-input" maxlength="20" placeholder="请输入手机号，可留空" ></view>
        </view>
        <view class="profile-field">
          <text class="learner-meta">邮箱</text>
          <view class="learner-toolbar"><input v-model="form.email" class="learner-input" maxlength="128" placeholder="请输入邮箱，可留空" ></view>
        </view>
        <view class="learner-actions">
          <button class="learner-button learner-button--primary" :disabled="busy" @click="save">保存资料</button>
        </view>
      </view>

      <view v-if="profile" class="learner-card">
        <text class="learner-title">修改密码</text>
        <text v-if="!profile.passwordChangeAllowed" class="learner-meta">
          {{ profile.hasLocalCredential ? '该账号受密码保护，不能本人改密。' : '该账号未绑定本地密码凭证，不能本人改密。' }}
        </text>
        <template v-else>
          <text class="learner-meta">成功后当前账号的全部会话立即失效，需重新登录</text>
          <view class="profile-field">
            <text class="learner-meta">旧密码</text>
            <view class="learner-toolbar"><input v-model="password.old" class="learner-input" :password="true" placeholder="请输入旧密码" ></view>
          </view>
          <view class="profile-field">
            <text class="learner-meta">新密码</text>
            <view class="learner-toolbar"><input v-model="password.next" class="learner-input" :password="true" placeholder="请输入新密码" ></view>
          </view>
          <view class="profile-field">
            <text class="learner-meta">确认新密码</text>
            <view class="learner-toolbar"><input v-model="password.confirm" class="learner-input" :password="true" placeholder="请再次输入新密码" ></view>
          </view>
          <view class="learner-actions">
            <button class="learner-button learner-button--primary" :disabled="busy" @click="changePassword">修改并重新登录</button>
          </view>
        </template>
      </view>
    </view>
  </MobileBaseLayout>
</template>
<style scoped lang="less">
/* 字段行：标签在上、输入框占满整行，输入宽度仍由 .learner-toolbar 提供。 */
.profile-field {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
</style>
