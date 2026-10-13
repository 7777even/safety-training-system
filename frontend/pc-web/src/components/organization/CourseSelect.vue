<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getCoursePage, type CourseListItem } from '@/api/course-foundation';

const model = defineModel<number | undefined>({ required: true });
withDefaults(defineProps<{ disabled?: boolean; clearable?: boolean }>(), { disabled: false, clearable: false });
const options = ref<CourseListItem[]>([]);
const loading = ref(false);

async function load(keyword = ''): Promise<void> {
  loading.value = true;
  try {
    const page = await getCoursePage({ pageNum: 1, pageSize: 100, ...(keyword ? { 'query.courseName': keyword } : {}) });
    options.value = page.list;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>

<template>
  <ElSelect
    v-model="model"
    filterable
    remote
    :remote-method="load"
    :loading="loading"
    :disabled="disabled"
    :clearable="clearable"
    placeholder="请选择课程"
  >
    <ElOption v-for="course in options" :key="course.courseId" :label="`${course.courseName}（${course.courseCode}）`" :value="course.courseId" />
  </ElSelect>
</template>
