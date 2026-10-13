<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
/** 读取路由元数据，仅用于基础壳层标题展示。 */
const title = computed(() => String(route.meta.title ?? '基础页面'))
</script>

<template>
  <section class="route-placeholder">
    <h1>{{ title }}</h1>
    <p>基础路由已就绪，业务内容将在后续 OpenSpec 变更中实现。</p>
  </section>
</template>

<style scoped lang="less">
.route-placeholder {
  max-width: 960px;
  margin: 0 auto;
  padding: 48px 24px;
}
</style>
