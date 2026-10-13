<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { CircleCheck, CirclePlus, Delete, Upload } from '@element-plus/icons-vue';
import UnifiedFileUpload, { type UnifiedUploadedFile } from '@/components/file-upload/UnifiedFileUpload.vue';
import { uploadAssetFile } from '@/api/asset';
import { DEFAULT_LIST_PAGE_SIZE, LIST_PAGE_SIZES } from '@/constants/pagination';
import { CERTIFICATE_SOURCE_TYPE_OPTIONS, getEnumLabel } from '@/constants/enum-options';
import {
  createTemplate,
  deleteTemplate,
  getTemplate,
  getTemplateBackground,
  getTemplatePage,
  previewTemplate,
  publishTemplate,
  updateTemplate,
  type CertificateElement,
  type CertificateTemplate
} from '@/api/m4-outcome';

/** 模板分页状态。 */
const templates = ref<CertificateTemplate[]>([]);
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(DEFAULT_LIST_PAGE_SIZE);
const total = ref(0);
const createVisible = ref(false);
const designerVisible = ref(false);
const uploadVisible = ref(false);
const creating = ref(false);
const saving = ref(false);
const previewUrl = ref('');
const previewLoading = ref(false);
const previewName = ref('');
const createForm = reactive({ templateCode: '', templateName: '', description: '' });
/** 设计草稿；保存时完整替换服务端草稿元素。 */
const design = reactive({
  certificateTemplateId: 0,
  version: 0,
  templateName: '',
  description: '',
  backgroundFileId: undefined as number | undefined,
  widthPx: 1024,
  heightPx: 768,
  versionNote: '',
  elements: [] as CertificateElement[]
});
/** 画布底图预览地址：刚上传时来自本地文件，重新打开设计时来自服务端底图。 */
const backgroundPreviewUrl = ref('');
const canvasLoading = ref(false);
const stageRef = ref<globalThis.HTMLDivElement>();
/** 左栏滚动容器：两栏各自滚动，保证编辑下方元素时右侧画布仍在视口内。 */
const paneRef = ref<globalThis.HTMLDivElement>();
const stageWidth = ref(0);
const selectedIndex = ref(-1);
let stageObserver: globalThis.ResizeObserver | undefined;

/** 查询模板分页。 */
async function loadTemplates(): Promise<void> {
  loading.value = true;
  try {
    const page = await getTemplatePage({ pageNum: pageNum.value, pageSize: pageSize.value });
    templates.value = page.list;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}
/** 打开新建模板表单。 */
function openCreate(): void {
  Object.assign(createForm, { templateCode: '', templateName: '', description: '' });
  createVisible.value = true;
}
/** 创建模板基础记录。 */
async function submitCreate(): Promise<void> {
  if (!createForm.templateCode || !createForm.templateName) {
    ElMessage.warning('请填写模板编码和模板名称。');
    return;
  }
  creating.value = true;
  try {
    await createTemplate({ ...createForm, status: 1 });
    createVisible.value = false;
    await loadTemplates();
    ElMessage.success('模板已创建，请继续设计并发布。');
  } finally {
    creating.value = false;
  }
}
/** 读取草稿或已发布版本，作为设计页面的唯一回显来源；底图以服务端为准。 */
async function openDesigner(row: CertificateTemplate): Promise<void> {
  const detail = await getTemplate(row.certificateTemplateId);
  const version = detail.draftVersion ?? detail.currentPublishedVersion;
  Object.assign(design, {
    certificateTemplateId: detail.certificateTemplateId,
    version: detail.version,
    templateName: detail.templateName,
    description: detail.description ?? '',
    backgroundFileId: version?.backgroundFileId,
    widthPx: version?.widthPx ?? 1024,
    heightPx: version?.heightPx ?? 768,
    versionNote: version?.versionNote ?? '',
    elements: (version?.elements ?? []).map((element) => ({ ...element }))
  });
  selectedIndex.value = -1;
  designerVisible.value = true;
  await nextTick();
  attachStageObserver();
  await loadBackgroundPreview(detail.certificateTemplateId);
}
/** 元素默认落点沿画布纵向依次下移，避免新元素重叠在一起。 */
function nextElementPosition(): { xPosition: number; yPosition: number } {
  const width = design.widthPx || 1;
  const height = design.heightPx || 1;
  return { xPosition: Math.round(width * 0.3), yPosition: Math.round(height * (0.35 + 0.08 * design.elements.length)) };
}
/** 新增固定文字元素。 */
function addTextElement(): void {
  design.elements.push({
    elementType: 'TEXT',
    staticContent: '固定文字',
    ...nextElementPosition(),
    fontSize: Math.max(16, Math.round(design.heightPx * 0.04)),
    fontColor: '#333333',
    sortOrder: design.elements.length + 1,
    status: 1
  });
  const addedIndex = design.elements.length - 1;
  selectedIndex.value = addedIndex;
  void nextTick(() => selectElement(addedIndex));
}
/** 一键新增常用证书变量，避免手工输入坐标和编码。 */
function addVariableElement(variableCode: string): void {
  design.elements.push({
    elementType: 'VARIABLE',
    variableCode,
    ...nextElementPosition(),
    fontSize: Math.max(16, Math.round(design.heightPx * 0.045)),
    fontColor: '#333333',
    sortOrder: design.elements.length + 1,
    status: 1
  });
  const addedIndex = design.elements.length - 1;
  selectedIndex.value = addedIndex;
  void nextTick(() => selectElement(addedIndex));
}
/** 删除元素并重排保存顺序。 */
function removeElement(index: number): void {
  design.elements.splice(index, 1);
  design.elements.forEach((element, order) => {
    element.sortOrder = order + 1;
  });
  selectedIndex.value = Math.min(selectedIndex.value, design.elements.length - 1);
}
/** 证书变量展示名与示例值；示例值用于画布预览，真实签发时由服务端替换。
 *  来源类型示例取自来源类型字典，保证画布与真实证书都呈现中文口径。 */
const VARIABLES = [
  { code: 'HOLDER_NAME', label: '学员姓名', sample: '张三' },
  { code: 'CERT_NO', label: '证书编号', sample: 'CERT-SAMPLE01' },
  { code: 'ISSUED_TIME', label: '签发时间', sample: '2026-09-23 15:30' },
  { code: 'SOURCE_TYPE', label: '来源类型', sample: getEnumLabel('TRAINING', CERTIFICATE_SOURCE_TYPE_OPTIONS) }
] as const;
/** 画布上呈现的元素文本，变量按示例值显示。 */
function elementText(element: CertificateElement): string {
  if (element.elementType === 'VARIABLE')
    return VARIABLES.find((item) => item.code === element.variableCode)?.sample ?? element.variableCode ?? '变量';
  return element.staticContent || '固定文字';
}
/** 元素在设计器里的名称，与画布选中提示和元素卡片保持一致。 */
function elementLabel(element: CertificateElement, index: number): string {
  if (element.elementType === 'VARIABLE') return `#${index + 1} ${VARIABLES.find((item) => item.code === element.variableCode)?.label ?? '证书变量'}`;
  return `#${index + 1} ${element.staticContent || '固定文字'}`;
}
/** 画布缩放比：画布像素到屏幕像素的换算基准，坐标与字号都按它缩放。 */
const stageScale = computed(() => (stageWidth.value > 0 && design.widthPx > 0 ? stageWidth.value / design.widthPx : 0));
/** 没有底图时画布按画布尺寸比例占位，保证元素坐标依然看得见。 */
const stageStyle = computed(() => (backgroundPreviewUrl.value ? {} : { aspectRatio: `${design.widthPx || 1} / ${design.heightPx || 1}` }));
/** 元素按真实坐标与字号渲染到底图上。 */
function elementStyle(element: CertificateElement): Record<string, string> {
  const scale = stageScale.value;
  const fontSize = element.fontSize && element.fontSize > 0 ? element.fontSize : 22;
  return {
    left: `${(element.xPosition ?? 0) * scale}px`,
    top: `${(element.yPosition ?? 0) * scale}px`,
    fontSize: `${fontSize * scale}px`,
    color: element.fontColor || '#333333'
  };
}
/** 拖拽状态以画布像素记录起点，避免缩放换算误差累积。 */
const drag = reactive({ index: -1, pointerId: -1, startClientX: 0, startClientY: 0, startX: 0, startY: 0 });
/** 元素坐标必须留在画布内，与后端 9405 校验保持一致。 */
function clampPosition(value: number, limit: number): number {
  const max = Math.max(0, (limit || 1) - 1);
  return Math.min(max, Math.max(0, Math.round(value)));
}
/** 选中元素：左栏卡片高亮，并把对应卡片滚进左栏可视区，避免画布上点中的元素找不到设置项。 */
function selectElement(index: number): void {
  selectedIndex.value = index;
  const card = paneRef.value?.querySelector<globalThis.HTMLElement>(`[data-element-card="${index}"]`);
  card?.scrollIntoView({ block: 'nearest' });
}
/** 按下即选中并开始拖拽。 */
function startDrag(event: globalThis.PointerEvent, index: number): void {
  const element = design.elements[index];
  if (!element || stageScale.value <= 0) return;
  selectElement(index);
  drag.index = index;
  drag.pointerId = event.pointerId;
  drag.startClientX = event.clientX;
  drag.startClientY = event.clientY;
  drag.startX = element.xPosition ?? 0;
  drag.startY = element.yPosition ?? 0;
  try {
    (event.currentTarget as globalThis.HTMLElement).setPointerCapture?.(event.pointerId);
  } catch {
    /* 合成事件没有真实指针时忽略捕获失败 */
  }
  event.preventDefault();
}
/** 拖拽过程中实时回写坐标，画布与右侧坐标输入框同步。 */
function moveDrag(event: globalThis.PointerEvent): void {
  if (drag.index < 0 || event.pointerId !== drag.pointerId) return;
  const element = design.elements[drag.index];
  const scale = stageScale.value;
  if (!element || scale <= 0) return;
  element.xPosition = clampPosition(drag.startX + (event.clientX - drag.startClientX) / scale, design.widthPx);
  element.yPosition = clampPosition(drag.startY + (event.clientY - drag.startClientY) / scale, design.heightPx);
}
/** 结束拖拽。 */
function endDrag(event: globalThis.PointerEvent): void {
  if (drag.index < 0 || event.pointerId !== drag.pointerId) return;
  drag.index = -1;
  drag.pointerId = -1;
  (event.currentTarget as globalThis.HTMLElement).releasePointerCapture?.(event.pointerId);
}
/** 选中元素可用方向键微调 1 像素，按住 Shift 为 10 像素。 */
function handleCanvasKeydown(event: globalThis.KeyboardEvent): void {
  const element = design.elements[selectedIndex.value];
  if (!element) return;
  const step = event.shiftKey ? 10 : 1;
  const moves: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
  const move = moves[event.key];
  if (!move) return;
  element.xPosition = clampPosition((element.xPosition ?? 0) + move[0], design.widthPx);
  element.yPosition = clampPosition((element.yPosition ?? 0) + move[1], design.heightPx);
  event.preventDefault();
}
/** 上传成功后仅使用统一上传返回的 fileInfoId。 */
function handleBackgroundUploaded(files: UnifiedUploadedFile[]): void {
  const file = files[0];
  if (!file) return;
  design.backgroundFileId = file.fileInfoId;
  uploadVisible.value = false;
  const source = file.sourceFile;
  if (source) {
    releaseBackgroundPreview();
    const localUrl = globalThis.URL.createObjectURL(source);
    backgroundPreviewUrl.value = localUrl;
    applyCanvasSizeFromImage(localUrl);
  }
  ElMessage.success('证书底图已上传，画布已显示底图。');
}
/** 以底图原始像素作为画布尺寸，元素坐标才有真实参照。 */
function applyCanvasSizeFromImage(url: string): void {
  const image = new globalThis.Image();
  image.onload = () => {
    if (image.naturalWidth > 0 && image.naturalHeight > 0) {
      design.widthPx = image.naturalWidth;
      design.heightPx = image.naturalHeight;
    }
  };
  image.src = url;
}
/** 统一上传协议。 */
function requestBackgroundUpload(file: globalThis.File, onProgress: (percent: number) => void): Promise<UnifiedUploadedFile> {
  return uploadAssetFile(file, onProgress, 'certificate-templates');
}
/** 释放本地底图对象地址，避免反复打开设计器泄漏。 */
function releaseBackgroundPreview(): void {
  if (backgroundPreviewUrl.value.startsWith('blob:')) globalThis.URL.revokeObjectURL(backgroundPreviewUrl.value);
  backgroundPreviewUrl.value = '';
}
/** 读取服务端底图；从未保存过底图的模板保持空态提示，不伪装成有底图。 */
async function loadBackgroundPreview(certificateTemplateId: number): Promise<void> {
  releaseBackgroundPreview();
  if (!design.backgroundFileId) return;
  canvasLoading.value = true;
  try {
    backgroundPreviewUrl.value = globalThis.URL.createObjectURL(await getTemplateBackground(certificateTemplateId));
  } catch {
    backgroundPreviewUrl.value = '';
  } finally {
    canvasLoading.value = false;
  }
}
/** 画布宽度变化时重新计算缩放比。 */
function syncStageWidth(): void {
  stageWidth.value = stageRef.value ? stageRef.value.clientWidth : 0;
}
/** 监听画布尺寸，弹窗打开和窗口缩放都能保持缩放比正确。 */
function attachStageObserver(): void {
  detachStageObserver();
  syncStageWidth();
  if (typeof globalThis.ResizeObserver !== 'function' || !stageRef.value) return;
  stageObserver = new globalThis.ResizeObserver(() => syncStageWidth());
  stageObserver.observe(stageRef.value);
}
/** 断开画布尺寸监听。 */
function detachStageObserver(): void {
  stageObserver?.disconnect();
  stageObserver = undefined;
}
/** 关闭设计器时释放监听与本地底图地址。 */
function handleDesignerClosed(): void {
  detachStageObserver();
  releaseBackgroundPreview();
  selectedIndex.value = -1;
}
/** 保存草稿；发布前重读乐观锁版本。 */
async function saveDesign(publish: boolean): Promise<void> {
  if (!design.backgroundFileId) {
    ElMessage.warning('请先上传证书底图。');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      version: design.version,
      templateName: design.templateName,
      description: design.description || undefined,
      status: 1,
      backgroundFileId: design.backgroundFileId,
      widthPx: design.widthPx,
      heightPx: design.heightPx,
      versionNote: design.versionNote || undefined,
      elements: design.elements
    };
    await updateTemplate(design.certificateTemplateId, payload);
    if (publish) {
      const refreshed = await getTemplate(design.certificateTemplateId);
      await publishTemplate(design.certificateTemplateId, { ...payload, version: refreshed.version });
    }
    designerVisible.value = false;
    await loadTemplates();
    ElMessage.success(publish ? '模板已发布。' : '模板草稿已保存。');
  } finally {
    saving.value = false;
  }
}
/** 打开服务端生成的预览图。 */
async function showPreview(row: CertificateTemplate): Promise<void> {
  closePreview();
  previewName.value = row.templateName;
  previewLoading.value = true;
  try {
    previewUrl.value = globalThis.URL.createObjectURL(await previewTemplate(row.certificateTemplateId));
  } finally {
    previewLoading.value = false;
  }
}
/** 释放预览文件对象。 */
function closePreview(): void {
  if (previewUrl.value) globalThis.URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
}
/** 删除无签发规则引用的模板。 */
async function removeTemplate(row: CertificateTemplate): Promise<void> {
  await ElMessageBox.confirm(`确认删除模板“${row.templateName}”吗？`, '删除确认', { type: 'warning' });
  await deleteTemplate(row.certificateTemplateId);
  await loadTemplates();
  ElMessage.success('模板已删除。');
}
onMounted(loadTemplates);
onBeforeUnmount(() => {
  detachStageObserver();
  releaseBackgroundPreview();
});
</script>

<template>
  <section class="admin-business-page">
    <ElCard class="admin-business-page__table" shadow="never">
      <template #header>
        <div class="admin-business-page__table-heading">
          <strong>证书模板列表</strong>
          <div class="admin-business-page__table-actions">
            <ElButton type="primary" :icon="CirclePlus" @click="openCreate">新建模板</ElButton>
          </div>
        </div>
      </template>
      <ElTable v-loading="loading" :data="templates" row-key="certificateTemplateId" empty-text="暂无证书模板，先新建一个模板">
        <ElTableColumn prop="templateCode" label="模板编码" width="180" />
        <ElTableColumn prop="templateName" label="模板名称" min-width="200" show-overflow-tooltip />
        <ElTableColumn label="发布状态" width="140">
          <template #default="{ row }">
            <ElTag :type="(row as CertificateTemplate).currentVersionNo > 0 ? 'success' : 'info'">
              {{ (row as CertificateTemplate).currentVersionNo > 0 ? `已发布 v${(row as CertificateTemplate).currentVersionNo}` : '未发布' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="说明" min-width="200">
          <template #default="{ row }">
            <span class="description-cell">{{ (row as CertificateTemplate).description || '—' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="240">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openDesigner(row as CertificateTemplate)">设计与发布</ElButton>
            <ElButton link v-if="row.currentVersionNo > 0" @click="showPreview(row as CertificateTemplate)">预览</ElButton>
            <ElButton link type="danger" @click="removeTemplate(row as CertificateTemplate)">删除</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <div class="admin-business-page__pagination">
        <ElPagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="LIST_PAGE_SIZES"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadTemplates"
          @size-change="loadTemplates"
        />
      </div>
    </ElCard>

    <ElDialog v-model="createVisible" title="新建证书模板" width="560px">
      <ElForm label-width="100px">
        <ElFormItem label="模板编码" required><ElInput v-model="createForm.templateCode" placeholder="例如 CERT-TPL-001" /></ElFormItem>
        <ElFormItem label="模板名称" required><ElInput v-model="createForm.templateName" placeholder="请输入模板名称" /></ElFormItem>
        <ElFormItem label="模板说明">
          <ElInput v-model="createForm.description" type="textarea" :rows="3" placeholder="填写模板适用场景，便于后续选择" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="createVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="creating" @click="submitCreate">创建</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="designerVisible" title="设计证书模板" width="1240px" top="4vh" :close-on-click-modal="false" @close="handleDesignerClosed">
      <div class="designer-body">
        <div ref="paneRef" class="designer-pane">
          <section class="ws-form-section">
            <header class="ws-form-section__head">
              <span class="ws-form-section__index">1</span>
              <div>
                <strong>模板设置</strong>
                <p>底图与画布尺寸是元素坐标的参照，发布时生成新版本。</p>
              </div>
            </header>
            <ElForm label-position="top" class="designer-form">
              <ElFormItem label="模板名称"><ElInput v-model="design.templateName" placeholder="请输入模板名称" /></ElFormItem>
              <ElFormItem label="证书底图" required>
                <div class="background-row">
                  <ElButton :icon="Upload" @click="uploadVisible = true">
                    {{ design.backgroundFileId ? '重新上传底图' : '上传底图' }}
                  </ElButton>
                  <span v-if="design.backgroundFileId" class="file-hint">
                    <ElIcon><CircleCheck /></ElIcon>
                    已关联文件，可保存发布
                  </span>
                  <span v-else class="file-hint file-hint--empty">尚未上传底图，保存前必须上传</span>
                </div>
                <p class="background-hint">上传后画布自动按底图原始像素设定尺寸，无需手工量取。</p>
              </ElFormItem>
              <ElFormItem label="画布尺寸（像素）">
                <div class="dimension-row">
                  <ElInputNumber v-model="design.widthPx" :min="1" :controls="false" />
                  <span class="dimension-separator">×</span>
                  <ElInputNumber v-model="design.heightPx" :min="1" :controls="false" />
                </div>
              </ElFormItem>
              <ElFormItem label="版本说明"><ElInput v-model="design.versionNote" placeholder="例如 首次发布" /></ElFormItem>
            </ElForm>
          </section>

          <section class="ws-form-section">
            <header class="ws-form-section__head">
              <span class="ws-form-section__index">2</span>
              <div>
                <strong>文字与变量元素</strong>
                <p>添加后在右侧画布拖动即可定位，坐标以画布左上角为原点。</p>
              </div>
            </header>
            <div class="quick-add">
              <ElButton :icon="CirclePlus" @click="addTextElement">固定文字</ElButton>
              <ElButton v-for="item in VARIABLES" :key="item.code" @click="addVariableElement(item.code)">{{ item.label }}</ElButton>
            </div>
            <div v-if="!design.elements.length" class="ws-empty ws-empty--dashed">
              <ElEmpty description="还没有模板元素">
                <span class="ws-empty__hint">至少添加一个元素后发布，否则生成的证书只有底图。</span>
                <ElButton type="primary" :icon="CirclePlus" @click="addTextElement">添加元素</ElButton>
              </ElEmpty>
            </div>
            <template v-else>
              <article
                v-for="(element, index) in design.elements"
                :key="index"
                class="ws-item-card element-card"
                :class="{ 'element-card--active': index === selectedIndex }"
                :data-element-card="index"
                @click="selectElement(index)"
              >
                <header class="element-card__head">
                  <span class="element-card__index">#{{ index + 1 }}</span>
                  <ElSelect v-model="element.elementType" class="element-card__type">
                    <ElOption label="固定文字" value="TEXT" />
                    <ElOption label="证书变量" value="VARIABLE" />
                  </ElSelect>
                  <ElButton link type="danger" :icon="Delete" @click.stop="removeElement(index)">删除</ElButton>
                </header>
                <ElInput v-if="element.elementType === 'TEXT'" v-model="element.staticContent" placeholder="请输入固定文字" />
                <ElSelect v-else v-model="element.variableCode" placeholder="请选择变量">
                  <ElOption v-for="item in VARIABLES" :key="item.code" :label="item.label" :value="item.code" />
                </ElSelect>
                <div class="element-card__grid">
                  <label class="element-field">
                    <span>X 坐标</span>
                    <ElInputNumber v-model="element.xPosition" :min="0" :max="design.widthPx - 1" :controls="false" />
                  </label>
                  <label class="element-field">
                    <span>Y 坐标</span>
                    <ElInputNumber v-model="element.yPosition" :min="0" :max="design.heightPx - 1" :controls="false" />
                  </label>
                  <label class="element-field">
                    <span>字号</span>
                    <ElInputNumber v-model="element.fontSize" :min="1" :controls="false" />
                  </label>
                  <label class="element-field">
                    <span>颜色</span>
                    <ElColorPicker v-model="element.fontColor" size="small" />
                  </label>
                </div>
              </article>
            </template>
          </section>
        </div>

        <section class="ws-form-section designer-canvas">
          <header class="ws-form-section__head">
            <span class="ws-form-section__index">3</span>
            <div>
              <strong>画布预览</strong>
              <p>与底图 1:1 等比缩放；拖动元素或用方向键微调，位置即最终签发位置。</p>
            </div>
          </header>
          <div ref="stageRef" v-loading="canvasLoading" class="canvas-stage" :style="stageStyle" tabindex="0" @keydown="handleCanvasKeydown">
            <img v-if="backgroundPreviewUrl" :src="backgroundPreviewUrl" class="canvas-stage__image" alt="证书底图" @load="syncStageWidth" />
            <div v-else class="canvas-stage__empty">
              <p>尚未关联底图</p>
              <span>上传底图后，这里会显示真实底图并按坐标呈现每个元素。</span>
            </div>
            <template v-if="stageScale > 0">
              <span
                v-for="(element, index) in design.elements"
                :key="index"
                class="canvas-element"
                :class="{ 'canvas-element--active': index === selectedIndex }"
                :style="elementStyle(element)"
                @pointerdown="startDrag($event, index)"
                @pointermove="moveDrag"
                @pointerup="endDrag"
                @pointercancel="endDrag"
              >
                {{ elementText(element) }}
              </span>
            </template>
          </div>
          <footer class="canvas-meta">
            <span class="ws-hint">
              画布 {{ design.widthPx }} × {{ design.heightPx }} 像素，元素按示例数据（张三 / CERT-SAMPLE01）预览，签发时替换为真实数据。
            </span>
            <span v-if="design.elements[selectedIndex]" class="canvas-meta__selection">
              选中 {{ elementLabel(design.elements[selectedIndex], selectedIndex) }} · X {{ design.elements[selectedIndex].xPosition }} · Y
              {{ design.elements[selectedIndex].yPosition }}
            </span>
            <span v-else class="ws-hint">未选中元素：在画布或左侧卡片上点一下即可拖动。</span>
          </footer>
        </section>
      </div>
      <template #footer>
        <ElButton @click="designerVisible = false">取消</ElButton>
        <ElButton :loading="saving" @click="saveDesign(false)">保存草稿</ElButton>
        <ElButton type="primary" :loading="saving" @click="saveDesign(true)">发布模板</ElButton>
      </template>
    </ElDialog>

    <ElDialog :model-value="Boolean(previewUrl)" title="模板预览" width="820px" top="6vh" @close="closePreview">
      <div v-loading="previewLoading" class="preview-body">
        <p class="preview-body__name">{{ previewName }}</p>
        <img v-if="previewUrl" :src="previewUrl" alt="证书模板预览" class="preview-image" />
        <p class="ws-hint">预览图由服务端按已发布版本生成，最终证书以签发时的版本为准。</p>
      </div>
    </ElDialog>

    <UnifiedFileUpload
      v-model="uploadVisible"
      mode="dialog"
      accept="image/png,image/jpeg"
      :limit="1"
      :upload-request="requestBackgroundUpload"
      @success="handleBackgroundUploaded"
    />
  </section>
</template>

<style scoped lang="less">
.description-cell {
  color: var(--color-text-secondary);
}

/* 设计弹窗采用两栏：左栏独立滚动编辑元素，右栏画布常驻，滚动左栏时预览不会跟着移出视口。 */
.designer-body {
  display: grid;
  min-width: 0;
  height: min(72vh, 800px);
  gap: var(--space-4);
  grid-template-columns: minmax(0, 400px) minmax(0, 1fr);
}

.designer-pane {
  display: grid;
  min-width: 0;
  align-content: start;
  gap: var(--space-4);
  padding-right: var(--space-2);
  overflow-y: auto;
}

.designer-form :deep(.el-form-item) {
  margin-bottom: var(--space-3);
}

.background-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.background-hint {
  margin: var(--space-2) 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.file-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #15803d;
  font-size: 12px;
}

.file-hint--empty {
  color: var(--color-text-secondary);
}

.dimension-row {
  display: flex;
  align-items: center;
}

.dimension-separator {
  margin: 0 8px;
  color: var(--color-text-secondary);
}

/* 加元素按钮吸顶：左栏滚动编辑下方元素时仍可随时追加新元素。 */
.quick-add {
  position: sticky;
  z-index: 2;
  top: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  padding: var(--space-2) 0;
  background: var(--color-bg-surface);
}

/* 外壳来自全局 ws-item-card，此处只收紧弹窗内的元素间距。 */
.element-card {
  gap: var(--space-2);
  cursor: pointer;
}

.element-card--active {
  border-color: var(--color-primary);
}

.element-card__head {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
}

.element-card__index {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.element-card__type {
  width: 140px;
}

.element-card__head :deep(.el-button) {
  margin-left: auto;
}

/* 固定两列：左栏变窄或出现滚动条时不会退化成单列把卡片拉长。 */
.element-card__grid {
  display: grid;
  gap: var(--space-2);
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.element-field {
  display: grid;
  gap: 4px;
}

.element-field span {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.designer-canvas {
  align-content: start;
  padding-right: var(--space-2);
  overflow-y: auto;
}

/* 画布按底图等比缩放，元素坐标以画布像素换算为屏幕像素。 */
.canvas-stage {
  position: relative;
  overflow: hidden;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  background: var(--color-bg-surface);
}

.canvas-stage:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.canvas-stage__image {
  display: block;
  width: 100%;
}

.canvas-stage__empty {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 4px;
  min-height: 160px;
  padding: var(--space-4);
  color: var(--color-text-secondary);
  text-align: center;
}

.canvas-stage__empty p {
  margin: 0;
  color: var(--color-text-primary);
  font-weight: 600;
}

.canvas-element {
  position: absolute;
  line-height: 1;
  white-space: nowrap;
  cursor: move;
  touch-action: none;
  user-select: none;
  outline: 1px dashed transparent;
}

.canvas-element--active {
  outline-color: var(--color-primary);
  box-shadow: 0 0 0 2px var(--color-primary-subtle);
}

.canvas-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.canvas-meta__selection {
  color: var(--color-primary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.preview-body {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
}

.preview-body__name {
  margin: 0;
  color: var(--color-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.preview-image {
  display: block;
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
}

/* 窄屏改为上下排列：此时不再固定高度，两栏回归整页滚动。 */
@media (max-width: 1180px) {
  .designer-body {
    height: auto;
    grid-template-columns: minmax(0, 1fr);
  }

  .designer-pane,
  .designer-canvas {
    padding-right: 0;
    overflow-y: visible;
  }
}
</style>
