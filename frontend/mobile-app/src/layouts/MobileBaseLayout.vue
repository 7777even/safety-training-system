<template>
  <view class="mobile-base-layout">
    <slot />
  </view>
</template>

<style scoped lang="less">
.mobile-base-layout {
  min-height: 100vh;
  padding: 0 32rpx;
  box-sizing: border-box;
  background: var(--color-bg-page);
}
</style>
