<script setup lang="ts">
import { RouterView } from 'vue-router';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
</script>

<template>
  <!-- 为所有 Element Plus 组件提供统一中文文案，例如分页器和空数据提示。 -->
  <ElConfigProvider :locale="zhCn">
    <RouterView />
  </ElConfigProvider>
</template>
