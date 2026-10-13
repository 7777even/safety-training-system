<script setup lang="ts">
defineProps<{
  description: string;
}>();
</script>

<template>
  <section class="page-placeholder">
    <el-empty :description="description" />
  </section>
</template>

<style scoped lang="less">
.page-placeholder {
  display: grid;
  min-height: 100%;
  place-items: center;
}
</style>
