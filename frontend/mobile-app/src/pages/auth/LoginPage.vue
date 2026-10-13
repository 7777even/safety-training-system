<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app';
import { ref } from 'vue';
import { useUserStore } from '@/stores';
import UniIcons from '@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue';

const orgCode = ref('');
const userCode = ref('');
const password = ref('');
const showPassword = ref(false);
const errorMessage = ref('');
const isSubmitting = ref(false);
const userStore = useUserStore();
/** 专属登录链接中的公开机构编码，校验规则与 PC 端一致。 */
const linkedOrgCode = ref('');
const invalidLinkedOrgCode = ref(false);

onLoad((query) => {
  let code = query?.orgCode;
  // H5 兼容企业链接将参数放在 hash 路由之前；页面参数优先。
  // #ifdef H5
  code = code ?? new globalThis.URLSearchParams(globalThis.window.location.search).get('orgCode');
  // #endif
  linkedOrgCode.value = typeof code === 'string' && /^[A-Z0-9_-]{2,64}$/i.test(code) ? code.toUpperCase() : '';
  invalidLinkedOrgCode.value = Boolean(code) && !linkedOrgCode.value;
  orgCode.value = linkedOrgCode.value;
});

/** 已登录时不重复停留在登录页。 */
onShow(() => {
  if (userStore.token && !linkedOrgCode.value && !invalidLinkedOrgCode.value) {
    uni.reLaunch({ url: '/pages/index/index' });
  }
});

/** 放弃专属入口，恢复手动输入企业编码。 */
function switchOrganization(): void {
  // H5 切换机构时同时移除外层参数，避免重新进入后再次自动带入。
  // #ifdef H5
  const url = new globalThis.URL(globalThis.window.location.href);
  url.searchParams.delete('orgCode');
  globalThis.window.history.replaceState(globalThis.window.history.state, '', url.toString());
  // #endif
  uni.reLaunch({ url: '/pages/auth/LoginPage' });
}

/** 先退出当前账号，再使用链接中的机构编码登录。 */
async function switchAccount(): Promise<void> {
  try {
    await userStore.logout();
  } catch {
    // Store 始终清除本地会话，服务端退出失败也允许重新登录。
  }
}

/** 校验基础输入后完成本地账号登录。 */
async function submitLogin(): Promise<void> {
  if (!orgCode.value.trim()) {
    errorMessage.value = '请输入企业编码';
    return;
  }

  if (!userCode.value.trim()) {
    errorMessage.value = '请输入账号或用户编码。';
    return;
  }

  if (!password.value) {
    errorMessage.value = '请输入密码。';
    return;
  }

  errorMessage.value = '';
  isSubmitting.value = true;

  try {
    await userStore.loginWithPassword(orgCode.value.trim(), userCode.value.trim(), password.value);
    uni.reLaunch({ url: '/pages/index/index' });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试。';
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <view class="login-page">
    <view class="brand-area">
      <view class="brand-mark">安</view>
      <text class="brand-en">SAFETY TRAINING SYSTEM</text>
      <text class="brand-title">学员登录</text>
      <text class="brand-description">安全学习，从一次可靠登录开始。</text>
    </view>

    <view v-if="userStore.token" class="form-card">
      <text>当前已有账号登录。若要使用此机构入口，请先退出当前账号。</text>
      <button class="submit-button" @tap="switchAccount">退出并切换账号</button>
    </view>
    <view v-else class="form-card">
      <!-- 专属入口以轻量信息栏展示机构，切换操作保持次级视觉权重。 -->
      <view v-if="linkedOrgCode" class="field-group organization-entry">
        <view class="organization-info">
          <text class="organization-label">当前机构</text>
          <text class="organization-code">{{ linkedOrgCode }}</text>
        </view>
        <button class="organization-switch" hover-class="organization-switch-active" @tap="switchOrganization">切换机构</button>
      </view>
      <view v-else class="field-group">
        <text class="field-label">企业编码</text>
        <input v-model="orgCode" class="field-input" placeholder="请输入企业编码" placeholder-class="placeholder">
      </view>
      <text v-if="invalidLinkedOrgCode" class="error-message">登录链接中的机构编码无效，请手动输入。</text>
      <view class="field-group">
        <text class="field-label">账号</text>
        <input v-model="userCode" class="field-input" placeholder="请输入账号或用户编码" placeholder-class="placeholder">
      </view>
      <view class="field-group">
        <text class="field-label">密码</text>
        <view class="password-input-wrapper">
          <input
            v-model="password"
            class="field-input password-input"
            :password="!showPassword"
            placeholder="请输入密码"
            placeholder-class="placeholder"
            @confirm="submitLogin"
          >
          <button class="password-toggle" @tap="showPassword = !showPassword">
            <UniIcons v-if="showPassword" type="eye-filled" size="20" color="#2563eb" />
            <UniIcons v-else type="eye-slash-filled" size="20" color="#2563eb" />
          </button>
        </view>
      </view>
      <text v-if="errorMessage" class="error-message">
        {{ errorMessage }}
      </text>
      <button class="submit-button" :disabled="isSubmitting" :loading="isSubmitting" @tap="submitLogin">登录</button>
    </view>
  </view>
</template>

<style scoped lang="less">
.login-page {
  min-height: 100vh;
  padding: env(safe-area-inset-top) 32rpx 64rpx;
  box-sizing: border-box;
  background: var(--color-bg-page);
}
.brand-area {
  min-height: 430rpx;
  padding: 72rpx 16rpx 40rpx;
  box-sizing: border-box;
  border-radius: 0 0 var(--radius-card) var(--radius-card);
  background: var(--color-primary-hover);
  color: var(--color-bg-surface);
}
.brand-mark {
  display: grid;
  width: 72rpx;
  height: 72rpx;
  place-items: center;
  border-radius: var(--radius-control);
  background: var(--color-bg-surface);
  color: var(--color-primary);
  font-size: 36rpx;
  font-weight: 700;
}
.brand-en {
  display: block;
  margin-top: 64rpx;
  color: #dbeafe;
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 2rpx;
}
.brand-title {
  display: block;
  margin-top: 18rpx;
  font-size: 60rpx;
  font-weight: 700;
}
.brand-description {
  display: block;
  margin-top: 18rpx;
  color: #dbeafe;
  font-size: 28rpx;
}
.form-card {
  position: relative;
  margin-top: 64rpx;
  padding: 40rpx 32rpx;
  border: 1rpx solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-card);
}
.field-group + .field-group {
  margin-top: 28rpx;
}
.organization-entry {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  border-radius: var(--radius-control);
  background: var(--color-primary-subtle);
}
.organization-info {
  flex: 1;
  min-width: 0;
}
.organization-label {
  display: block;
  color: var(--color-text-secondary);
  font-size: 24rpx;
  line-height: 1.5;
}
.organization-code {
  display: block;
  margin-top: 4rpx;
  color: var(--color-text-primary);
  font-size: 28rpx;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.organization-switch {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  min-height: 88rpx;
  margin: 0;
  padding: 0 8rpx;
  border: 0;
  background: transparent;
  color: var(--color-primary);
  font-size: 26rpx;
  line-height: 1.5;
}
.organization-switch::after {
  border: 0;
}
.organization-switch-active {
  color: var(--color-primary-hover);
}
.field-label {
  display: block;
  margin-bottom: 14rpx;
  color: var(--color-text-regular);
  font-size: 26rpx;
  font-weight: 600;
}
.field-input {
  min-height: 88rpx;
  height: 88rpx;
  padding: 0 24rpx;
  border: 1rpx solid var(--color-border);
  border-radius: var(--radius-control);
  color: var(--color-text-primary);
  font-size: 28rpx;
  box-sizing: border-box;
}
.password-input-wrapper {
  position: relative;
}
.password-input {
  padding-right: 80rpx;
}
.password-toggle {
  position: absolute;
  top: 50%;
  right: 10rpx;
  min-width: 64rpx;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-primary);
  font-size: 24rpx;
  line-height: 1;
  transform: translateY(-50%);
}
.password-toggle::after {
  border: 0;
}
.placeholder {
  color: var(--color-text-secondary);
}
.error-message {
  display: block;
  margin-top: 20rpx;
  color: var(--color-danger);
  font-size: 24rpx;
}
.submit-button {
  min-height: 88rpx;
  margin-top: 40rpx;
  border-radius: var(--radius-control);
  background: var(--color-primary);
  color: var(--color-bg-surface);
  font-size: 30rpx;
  font-weight: 600;
}
.submit-button[disabled] {
  background: #93c5fd;
}
</style>
