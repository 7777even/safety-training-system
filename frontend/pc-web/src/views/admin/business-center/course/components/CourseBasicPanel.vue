<script setup lang="ts">
import { getPersonalTeachers } from '@/api/learner-center';
import type { PersonalTeacher } from '@shared/types';
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { Picture } from '@element-plus/icons-vue';
import {
  assignCourseCategories,
  createCourse,
  getCourseCategoryTree,
  getCourseCoverPreview,
  getCourseDetail,
  uploadCourseCover,
  updateCourse,
  type CourseCategory,
  type CourseDetail
} from '@/api/course-foundation';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import {
  COURSE_CONTENT_MODE_TEXT,
  COURSE_POINT_MODE_TEXT,
  SOURCE_DOWNLOAD_MODE_TEXT,
  type CourseContentMode,
  type CoursePointMode,
  type EnabledStatus,
  type SourceDownloadMode
} from '@/constants/course';
import { useUserStore } from '@/stores';
import { hasPermission } from '@/utils/permission';

const props = defineProps<{ courseId: number }>();
const emit = defineEmits<{
  saved: [courseId: number, goNext: boolean, detail: CourseDetail];
}>();

/** 工作台基础信息表单，更新时保留服务端乐观锁版本。 */
interface CourseForm {
  courseCode: string;
  courseName: string;
  teacherUserId: number | null;
  coverFileId: number | null;
  introduction: string;
  teachingObjective: string;
  syllabus: string;
  assessmentMethod: string;
  contentMode: CourseContentMode;
  credit: number;
  pointMode: CoursePointMode;
  pointValue: number;
  sourceDownloadMode: SourceDownloadMode;
  isSpeedPlayAllowed: EnabledStatus;
  isStudyAnticheatEnabled: EnabledStatus;
  isStudyDescriptionEnabled: EnabledStatus;
  isWatermarkEnabled: EnabledStatus;
  status: EnabledStatus;
  categoryIds: number[];
  version: number;
}

const userStore = useUserStore();
const formRef = ref<FormInstance>();
const loading = ref(false);
const teachers = ref<PersonalTeacher[]>([]);
/** 复用已有教师目录关键词查询。 */
async function searchTeachers(keyword: string): Promise<void> {
  teachers.value = (await getPersonalTeachers({ pageNum: 1, pageSize: 20, 'query.keyword': keyword })).list;
}
const saving = ref(false);
const categoryTree = ref<CourseCategory[]>([]);
const coverUploadVisible = ref(false);
const coverFileName = ref('');
const coverPreviewUrl = ref('');
const categoryProps = { label: 'categoryName', value: 'courseCategoryId', children: 'children' };
const courseForm = reactive<CourseForm>(createEmptyForm());
const rules: FormRules = {
  courseCode: [{ required: true, message: '请输入课程编码', trigger: 'blur' }],
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  contentMode: [{ required: true, message: '请选择内容模式', trigger: 'change' }],
  credit: [{ required: true, message: '请输入学分', trigger: 'change' }]
};

function createEmptyForm(): CourseForm {
  return {
    teacherUserId: null,
    courseCode: '',
    courseName: '',
    coverFileId: null,
    introduction: '',
    teachingObjective: '',
    syllabus: '',
    assessmentMethod: '',
    contentMode: 'MULTI_CHAPTER',
    credit: 0,
    pointMode: 'NONE',
    pointValue: 0,
    sourceDownloadMode: 'ALL',
    isSpeedPlayAllowed: 1,
    isStudyAnticheatEnabled: 1,
    isStudyDescriptionEnabled: 1,
    isWatermarkEnabled: 1,
    status: 1,
    categoryIds: [],
    version: 0
  };
}

function applyDetail(detail: CourseDetail): void {
  Object.assign(courseForm, {
    ...detail,
    teacherUserId: detail.teacherUserId ?? null,
    introduction: detail.introduction ?? '',
    teachingObjective: detail.teachingObjective ?? '',
    syllabus: detail.syllabus ?? '',
    assessmentMethod: detail.assessmentMethod ?? '',
    categoryIds: detail.categoryIds ?? [],
    coverFileId: detail.coverFileId ?? null
  });
}

/** 释放旧预览地址，避免切换课程或重复上传时保留无效的本地 Blob。 */
function clearCoverPreview(): void {
  if (coverPreviewUrl.value.startsWith('blob:')) globalThis.URL.revokeObjectURL(coverPreviewUrl.value);
  coverPreviewUrl.value = '';
}

/** 通过受控读取接口加载已保存课程的封面，供缩略图和点击放大复用。 */
async function loadCoverPreview(courseId: number, coverFileId: number | null): Promise<void> {
  clearCoverPreview();
  if (!coverFileId) return;
  try {
    coverPreviewUrl.value = globalThis.URL.createObjectURL(await getCourseCoverPreview(courseId));
  } catch {
    ElMessage.warning('课程封面加载失败，请重新上传后保存。');
  }
}

/** 使用课程封面专用契约上传图片，避免把文件 ID 暴露给用户手动填写。 */
async function requestCoverUpload(
  file: globalThis.File,
  onProgress: (percent: number) => void,
  signal: globalThis.AbortSignal
): Promise<UnifiedUploadedFile> {
  const result = await uploadCourseCover(file, onProgress, signal);
  return {
    fileInfoId: result.fileInfoId,
    fileName: result.fileName,
    fileSize: result.fileSize
  };
}

/** 将上传成功的封面文件 ID 写回课程表单。 */
function handleCoverUploadSuccess(files: UnifiedUploadedFile[]): void {
  const [file] = files;
  if (!file) return;
  courseForm.coverFileId = file.fileInfoId;
  coverFileName.value = file.fileName;
  clearCoverPreview();
  if (file.sourceFile) coverPreviewUrl.value = globalThis.URL.createObjectURL(file.sourceFile);
  coverUploadVisible.value = false;
}

/** 加载分类和当前课程详情，新增状态恢复默认表单。 */
async function loadPanel(): Promise<void> {
  loading.value = true;
  try {
    categoryTree.value = await getCourseCategoryTree();
    await searchTeachers('');
    if (props.courseId > 0) {
      const detail = await getCourseDetail(props.courseId);
      applyDetail(detail);
      if (detail.teacherUserId && detail.teacherName && !teachers.value.some(t => t.teacherId === detail.teacherUserId)) {
        teachers.value.push({ teacherId: detail.teacherUserId, teacherName: detail.teacherName, available: true, followed: false });
      }
      await loadCoverPreview(props.courseId, detail.coverFileId ?? null);
    } else {
      Object.assign(courseForm, createEmptyForm());
      clearCoverPreview();
    }
  } finally {
    loading.value = false;
  }
}

/** 先保存当前阶段，再由工作台决定是否进入内容编排。 */
async function saveCourse(goNext: boolean): Promise<void> {
  if (!(await formRef.value?.validate().catch(() => false))) return;
  saving.value = true;
  try {
    let savedCourseId = props.courseId;
    if (savedCourseId === 0) {
      savedCourseId = await createCourse({
        courseCode: courseForm.courseCode,
        courseName: courseForm.courseName,
        coverFileId: courseForm.coverFileId,
        introduction: courseForm.introduction,
        teacherUserId: courseForm.teacherUserId,
        teachingObjective: courseForm.teachingObjective,
        syllabus: courseForm.syllabus,
        assessmentMethod: courseForm.assessmentMethod,
        contentMode: courseForm.contentMode,
        credit: courseForm.credit,
        pointMode: courseForm.pointMode,
        pointValue: courseForm.pointValue,
        sourceDownloadMode: courseForm.sourceDownloadMode,
        isSpeedPlayAllowed: courseForm.isSpeedPlayAllowed,
        isStudyAnticheatEnabled: courseForm.isStudyAnticheatEnabled,
        isStudyDescriptionEnabled: courseForm.isStudyDescriptionEnabled,
        isWatermarkEnabled: courseForm.isWatermarkEnabled,
        status: courseForm.status,
        categoryIds: courseForm.categoryIds
      });
    } else {
      await updateCourse(savedCourseId, {
        courseName: courseForm.courseName,
        coverFileId: courseForm.coverFileId,
        introduction: courseForm.introduction,
        teacherUserId: courseForm.teacherUserId,
        teachingObjective: courseForm.teachingObjective,
        syllabus: courseForm.syllabus,
        assessmentMethod: courseForm.assessmentMethod,
        credit: courseForm.credit,
        pointMode: courseForm.pointMode,
        pointValue: courseForm.pointValue,
        sourceDownloadMode: courseForm.sourceDownloadMode,
        isSpeedPlayAllowed: courseForm.isSpeedPlayAllowed,
        isStudyAnticheatEnabled: courseForm.isStudyAnticheatEnabled,
        isStudyDescriptionEnabled: courseForm.isStudyDescriptionEnabled,
        isWatermarkEnabled: courseForm.isWatermarkEnabled,
        status: courseForm.status,
        version: courseForm.version
      });
      await assignCourseCategories(savedCourseId, courseForm.categoryIds);
    }

    const detail = await getCourseDetail(savedCourseId);
    applyDetail(detail);
    await loadCoverPreview(savedCourseId, detail.coverFileId ?? null);
    ElMessage.success('课程基础信息已保存');
    emit('saved', savedCourseId, goNext, detail);
  } finally {
    saving.value = false;
  }
}

function canSave(): boolean {
  return hasPermission(userStore.userInfo, props.courseId > 0 ? 'course:course:edit' : 'course:course:add');
}

watch(() => props.courseId, loadPanel);
onMounted(loadPanel);
onBeforeUnmount(clearCoverPreview);
</script>

<template>
  <ElCard shadow="never" class="admin-detail-page__card">
    <template #header>课程信息</template>
    <div class="admin-detail-page__form-scroll">
      <ElForm ref="formRef" :model="courseForm" :rules="rules" label-width="128px" class="admin-detail-form">
        <ElRow :gutter="24">
          <ElCol :span="12">
            <ElFormItem label="课程编码" prop="courseCode">
              <ElInput v-model="courseForm.courseCode" :disabled="courseId > 0" placeholder="请输入机构内唯一课程编码" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="课程名称" prop="courseName">
              <ElInput v-model="courseForm.courseName" maxlength="255" placeholder="请输入课程名称" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="课程封面" class="course-cover-form-item">
              <div class="course-cover-field">
                <ElImage
                  v-if="coverPreviewUrl"
                  :src="coverPreviewUrl"
                  :preview-src-list="[coverPreviewUrl]"
                  fit="cover"
                  class="course-cover-preview"
                  preview-teleported
                />
                <div v-else class="course-cover-placeholder">
                  <ElIcon><Picture /></ElIcon>
                  <span>暂无封面</span>
                </div>
                <div class="course-cover-actions">
                  <ElButton type="primary" plain @click="coverUploadVisible = true">
                    {{ courseForm.coverFileId ? '更换封面' : '上传封面' }}
                  </ElButton>
                  <span v-if="courseForm.coverFileId" class="course-cover-name" :title="coverFileName || '已设置课程封面'">
                    {{ coverFileName || '已设置课程封面' }}
                  </span>
                  <span v-else class="course-cover-tip">JPG、PNG、WebP，最大 10 MiB</span>
                  <span v-if="coverPreviewUrl" class="course-cover-tip">点击图片可放大预览</span>
                </div>
              </div>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="内容模式" prop="contentMode">
              <ElSelect v-model="courseForm.contentMode" :disabled="courseId > 0" placeholder="请选择内容模式">
                <ElOption v-for="(label, value) in COURSE_CONTENT_MODE_TEXT" :key="value" :label="label" :value="value" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="课程分类">
              <ElTreeSelect
                v-model="courseForm.categoryIds"
                :data="categoryTree"
                :props="categoryProps"
                node-key="courseCategoryId"
                multiple
                show-checkbox
                check-strictly
                clearable
                placeholder="请选择课程分类"
              />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="学分" prop="credit">
              <ElInputNumber v-model="courseForm.credit" :min="0" :precision="1" placeholder="请输入学分" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="启用状态">
              <ElRadioGroup v-model="courseForm.status">
                <ElRadio :value="1">启用</ElRadio>
                <ElRadio :value="0">停用</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
          </ElCol>
        </ElRow>

        <ElFormItem label="课程简介">
          <ElInput v-model="courseForm.introduction" type="textarea" :rows="3" maxlength="2000" placeholder="请输入课程简介" />
        </ElFormItem>
        <ElFormItem label="主讲教师">
          <ElSelect v-model="courseForm.teacherUserId" filterable remote clearable :remote-method="searchTeachers" placeholder="请选择本机构教师" @clear="courseForm.teacherUserId = null">
            <ElOption v-for="teacher in teachers" :key="teacher.teacherId" :value="teacher.teacherId" :label="teacher.teacherName" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="教学目标">
          <ElInput v-model="courseForm.teachingObjective" type="textarea" :rows="3" placeholder="请输入教学目标" />
        </ElFormItem>
        <ElFormItem label="重点内容／教学大纲">
          <ElInput v-model="courseForm.syllabus" type="textarea" :rows="3" placeholder="请输入重点内容／教学大纲" />
        </ElFormItem>
        <ElFormItem label="考核方式">
          <ElInput v-model="courseForm.assessmentMethod" type="textarea" :rows="3" placeholder="请输入考核方式" />
        </ElFormItem>

        <ElDivider content-position="left">学习与积分</ElDivider>
        <ElRow :gutter="24">
          <ElCol :span="12">
            <ElFormItem label="积分模式">
              <ElSelect v-model="courseForm.pointMode" placeholder="请选择积分模式">
                <ElOption v-for="(label, value) in COURSE_POINT_MODE_TEXT" :key="value" :label="label" :value="value" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="积分值">
              <ElInputNumber v-model="courseForm.pointValue" :min="0" placeholder="请输入积分值" />
            </ElFormItem>
          </ElCol>
          <ElCol :span="12">
            <ElFormItem label="源文件下载">
              <ElSelect v-model="courseForm.sourceDownloadMode" placeholder="请选择下载模式">
                <ElOption v-for="(label, value) in SOURCE_DOWNLOAD_MODE_TEXT" :key="value" :label="label" :value="value" />
              </ElSelect>
            </ElFormItem>
          </ElCol>
        </ElRow>
        <div class="switch-grid">
          <ElCheckbox v-model="courseForm.isSpeedPlayAllowed" :true-value="1" :false-value="0">允许倍速播放</ElCheckbox>
          <ElCheckbox v-model="courseForm.isStudyAnticheatEnabled" :true-value="1" :false-value="0">启用学习防作弊</ElCheckbox>
          <ElCheckbox v-model="courseForm.isStudyDescriptionEnabled" :true-value="1" :false-value="0">显示学时说明</ElCheckbox>
          <ElCheckbox v-model="courseForm.isWatermarkEnabled" :true-value="1" :false-value="0">启用学习水印</ElCheckbox>
        </div>
      </ElForm>
    </div>
    <footer v-if="canSave()" class="admin-detail-action-bar">
      <ElButton :loading="saving" @click="saveCourse(false)">保存</ElButton>
      <ElButton type="primary" :loading="saving" @click="saveCourse(true)">保存并下一步</ElButton>
    </footer>
    <UnifiedFileUpload
      v-model="coverUploadVisible"
      mode="dialog"
      accept="image/jpeg,image/png,image/webp"
      :max-file-size="10 * 1024 * 1024"
      size-limit-hint="课程封面图片不能超过 10 MiB"
      :upload-request="requestCoverUpload"
      @success="handleCoverUploadSuccess"
    />
  </ElCard>
</template>

<style scoped lang="less">
.course-basic-panel {
  min-height: 520px;
}
.course-cover-preview {
  width: 144px;
  height: 81px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  cursor: zoom-in;
}
.course-cover-field {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 81px;
}
.course-cover-placeholder {
  display: flex;
  flex: 0 0 144px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 144px;
  height: 81px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
}
.course-cover-placeholder .el-icon {
  margin-bottom: 6px;
  font-size: 22px;
}
.course-cover-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}
.course-cover-name,
.course-cover-tip {
  max-width: 150px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-cover-tip {
  color: var(--el-text-color-placeholder);
}
.section-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-subtle);
}
.section-title h2 {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 16px;
}
.section-title p {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.course-form {
  height: 48vh;
  overflow-y: auto;
  padding: 28px 32px 96px;
}
.course-form :deep(.el-input),
.course-form :deep(.el-select),
.course-form :deep(.el-tree-select) {
  width: 320px;
}

.course-form :deep(.el-input-number) {
  width: 220px;
}

.course-form :deep(.el-textarea) {
  width: 100%;
}
.switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 24px;
  padding-left: 128px;
}
@media (max-width: 1100px) {
  .course-form {
    padding-inline: 20px;
  }
  .switch-grid {
    grid-template-columns: 1fr;
  }
}

:deep(.el-card__header) {
  line-height: 40px;
}
</style>
