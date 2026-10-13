import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = (path) => readFile(new URL(path, import.meta.url), 'utf8');
const adminSourceRoot = new URL('../../../backend/sts-service-admin/src/main/java/', import.meta.url);

/** 读取 init 菜单种子，作为菜单、组件路径与权限三方对齐的唯一来源。 */
async function readSeedMenus() {
  return JSON.parse(
    await readFile(new URL('../../../backend/sts-service-admin-init/src/main/resources/data/menus.json', import.meta.url), 'utf8')
  );
}

/** 按 pid 关系还原菜单完整访问路径，并应用后台 /result -> /admin/result 的本地映射。 */
function resolveFullPath(menu, byId) {
  const segments = [];
  let current = menu;
  while (current) {
    const segment = (current.path ?? '').trim();
    if (segment.startsWith('/')) {
      segments.unshift(segment);
      break;
    }
    if (segment) segments.unshift(segment);
    current = byId.get(current.pid);
  }
  const joined = segments.join('/');
  return joined.startsWith('/result') ? `/admin${joined}` : joined;
}

/** 复用 admin-dynamic-routes 的组件候选顺序，逐个确认页面文件是否存在。 */
async function resolveComponentFile(fullPath, component) {
  const normalized = component.trim().replace(/^\/+/, '').replace(/\.vue$/, '');
  const candidates = [];
  if (fullPath.startsWith('/admin/result/') && !normalized.startsWith('admin/')) {
    candidates.push(`./src/views/admin/result/${normalized}.vue`);
  }
  if (!normalized.startsWith('admin/')) candidates.push(`./src/views/admin/${normalized}.vue`);
  candidates.push(`./src/views/${normalized}.vue`);
  candidates.push(`./src/views${fullPath}/index.vue`);

  for (const candidate of candidates) {
    try {
      await readFile(new URL(candidate, new URL('../', import.meta.url)));
      return candidate;
    } catch {
      // 继续尝试下一个候选路径。
    }
  }
  return undefined;
}

test('档案入口提供一期、企业、个人三种业务选择且不手输主键', async () => {
  const [page, api] = await Promise.all([
    source('../src/views/admin/result/archive/index.vue'),
    source('../src/api/m4-outcome.ts')
  ]);

  assert.match(page, /type ArchivePerspective = 'PLAN' \| 'ENTERPRISE' \| 'PERSON'/);
  assert.match(page, /<ElTabPane label="一期一档" name="PLAN">/);
  assert.match(page, /<ElTabPane label="企业档案" name="ENTERPRISE">/);
  assert.match(page, /<ElTabPane label="个人档案" name="PERSON">/);
  // 一期从已结束计划候选中选择，个人通过人员选择器选择，都不允许直接输入全局主键。
  assert.match(page, /getTrainingPlanPage\(\{ pageNum: 1, pageSize: 100, 'query\.publishStatus': 'ENDED' \}\)/);
  assert.match(page, /PersonPickerDialog/);
  assert.doesNotMatch(page, /v-model="planId"/);
  assert.doesNotMatch(page, /v-model="userId"/);
  assert.match(page, /createArchiveFromPlan/);
  assert.match(page, /getEnterpriseArchives/);
  assert.match(page, /getArchivePerson/);
  assert.match(page, /downloadArchivePerson/);
  assert.match(page, /\/admin\/result\/archive/);
  assert.match(api, /url: '\/api\/archive\/trainings\/create-from-plan'/);
  assert.match(api, /url: '\/api\/archive\/enterprises'/);
});

test('档案文件按真实格式命名，一期与个人都不再使用压缩包后缀', async () => {
  // 一期档案的事实面板已抽成子组件，命名与格式断言分文件核对。
  const [page, panel] = await Promise.all([
    source('../src/views/admin/result/archive/index.vue'),
    source('../src/views/admin/result/archive/components/PlanArchivePanel.vue')
  ]);

  // 2026-09-24 起档案文件是 PDF 正本（AWT 绘页 + PDFBox 装载），不再产出 JSON。
  assert.match(page, /`\$\{archive\.value\?\.planCode \?\? 'training'\}-培训档案\.pdf`/);
  assert.match(page, /-个人培训档案\.pdf`/);
  assert.doesNotMatch(page, /\.zip/);
  assert.doesNotMatch(panel, /\.zip/);
  // 文件格式只由后端事实决定：历史 JSON 档案必须显式提示重新更新档案。
  assert.match(panel, /archiveFileFormat = computed\(\(\) => \(props\.archive\.archiveFileIsPdf \? 'PDF' : 'JSON'\)\)/);
  assert.match(panel, /需重新更新档案以获得 PDF 正本/);
});

test('结果待补全时一期与个人都禁止下载正式档案', async () => {
  const [page, panel] = await Promise.all([
    source('../src/views/admin/result/archive/index.vue'),
    source('../src/views/admin/result/archive/components/PlanArchivePanel.vue')
  ]);

  // 一期：下载可用性由文件与缺失项共同判定，面板按钮必须复用同一判定。
  assert.match(page, /Boolean\(archive\.value\?\.fileInfoId\) && \(archive\.value\?\.missingItems\.length \?\? 1\) === 0/);
  assert.match(panel, /:disabled="!downloadable"/);
  assert.match(page, /const personDownloadable = computed\(\(\) => \(person\.value\?\.missingItems\.length \?\? 1\) === 0\)/);
  assert.match(page, /const archivable = computed\(/);
});

test('管理员与学员都能在线预览并下载授权范围内的证书', async () => {
  const [admin, portal] = await Promise.all([
    source('../src/views/admin/result/certificate/certificates/index.vue'),
    source('../src/views/portal/certificates/index.vue')
  ]);

  for (const view of [admin, portal]) {
    assert.match(view, /downloadCertificate\(/);
    assert.match(view, /globalThis\.URL\.createObjectURL\(/);
    assert.match(view, /certificate-preview/);
    assert.match(view, /<ElDialog v-model="previewVisible"/);
  }
  // 学员端必须走本人端点并按接口分页翻阅全部证书。
  assert.match(portal, /downloadCertificate\(row\.certificateId, true\)/);
  assert.match(portal, /getCertificates\(params, true\)/);
  assert.match(portal, /<ElPagination/);
  assert.match(portal, /LIST_PAGE_SIZES/);
});

test('签发规则列表按契约数组读取，并在页面请求层完成本地分页', async () => {
  const [api, contract] = await Promise.all([
    source('../src/api/m4-outcome.ts'),
    source('../../../docs/api/certificate.openapi.json')
  ]);
  const schemas = JSON.parse(contract).components.schemas;
  const ruleList = schemas.ResultCertificateIssueRuleList.allOf.find((part) => part.properties?.data);

  assert.equal(ruleList.properties.data.type, 'array');
  assert.match(api, /request<ApiResponse<CertificateRule\[\]>>\(\{ url: '\/api\/certificate\/rules', method: 'GET' \}\)/);
  assert.match(api, /rules\.slice\(start, start \+ params\.pageSize\)/);
  assert.match(api, /total: rules\.length/);
});

test('M4 菜单的组件路径、路由前缀和权限都与实现一致', async () => {
  const menus = await readSeedMenus();
  const byId = new Map(menus.map((menu) => [menu.id, menu]));
  const m4Menus = menus.filter((menu) => menu.id >= 9700 && menu.id <= 9750);
  assert.equal(m4Menus.length, 25);

  const resolved = [];
  for (const menu of m4Menus) {
    const fullPath = resolveFullPath(menu, byId);
    assert.ok(fullPath.startsWith('/admin/result'), `M4 菜单必须落在后台成果路由下: ${menu.name} -> ${fullPath}`);
    if (menu.comp === 'Layout') {
      assert.ok(m4Menus.some((child) => child.pid === menu.id), `目录节点必须有子页面: ${menu.name}`);
      continue;
    }
    if (menu.type !== 'C') continue;
    const file = await resolveComponentFile(fullPath, menu.comp);
    assert.ok(file, `菜单组件路径无法解析到页面: ${menu.name} -> ${menu.comp}`);
    resolved.push(fullPath);
  }

  // 问卷、档案、证书三个叶子页面与计划上下文成果页都必须可达。
  for (const expected of [
    '/admin/result/survey',
    '/admin/result/archive',
    '/admin/result/certificate/templates',
    '/admin/result/certificate/rules',
    '/admin/result/certificate/certificates'
  ]) {
    assert.ok(resolved.includes(expected), `缺少 M4 菜单路径: ${expected}`);
  }
});

test('M4 菜单权限都能在 Admin Controller 上找到对应授权点', async () => {
  const { readdir } = await import('node:fs/promises');
  const menus = await readSeedMenus();
  const m4Permissions = new Set(
    menus.filter((menu) => menu.id >= 9700 && menu.id <= 9750 && menu.perms).map((menu) => menu.perms)
  );

  const controllerPermissions = new Set();
  const walk = async (directory) => {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      const target = new URL(`${entry.name}${entry.isDirectory() ? '/' : ''}`, directory);
      if (entry.isDirectory()) {
        await walk(target);
        continue;
      }
      if (!entry.name.endsWith('.java')) continue;
      const content = await readFile(target, 'utf8');
      for (const match of content.matchAll(/@SaCheckPermission\s*\(([\s\S]*?)\)/g)) {
        for (const value of match[1].matchAll(/"([^"]+)"/g)) {
          if (value[1].includes(':')) controllerPermissions.add(value[1]);
        }
      }
    }
  };
  await walk(adminSourceRoot);

  for (const permission of m4Permissions) {
    assert.ok(controllerPermissions.has(permission), `菜单权限缺少 Controller 授权点: ${permission}`);
  }
  // 未实现的证书撤销、重发、手工签发不得出现在 M4 菜单里。
  for (const absent of ['certificate:cert:issue', 'certificate:cert:revoke', 'certificate:cert:reissue']) {
    assert.ok(!m4Permissions.has(absent), `不得为未实现能力预留权限: ${absent}`);
  }
});

test('成果页面归属应用中心模块，进入后侧栏不得整条消失', async () => {
  const [routes, layout, menus] = await Promise.all([
    source('../src/router/admin-dynamic-routes.ts'),
    source('../src/layouts/AdminLayout.vue'),
    readSeedMenus()
  ]);

  // 侧栏由 activeModule 派生的 sidebarNavigation 决定显隐；为空时整条侧栏都不渲染。
  assert.match(layout, /const sidebarNavigation = computed\(\(\) => activeModule\.value\?\.children \?\? \[\]\)/);
  assert.match(layout, /sidebarNavigation\.length > 0/);

  // 成果节点沿用后端遗留绝对路径 /result，被归一为 /admin/result，因而不在其所属模块的 URL 前缀内。
  const byId = new Map(menus.map((menu) => [menu.id, menu]));
  const resultNode = byId.get(9700);
  assert.equal(resolveFullPath(resultNode, byId), '/admin/result');
  assert.equal(byId.get(resultNode.pid).path, '/admin/business-center', '成果菜单必须挂在应用中心模块下');

  // 因此模块判定必须在 URL 前缀未命中时回落按菜单树回溯，否则成果页面会丢掉侧栏。
  assert.match(routes, /function collectNavigationPaths/);
  assert.match(routes, /const prefixMatched = modules/);
  assert.match(routes, /collectNavigationPaths\(module\.children\)\.some/);
});

test('证书模板设计器显示真实底图，元素按画布坐标渲染并可用指针拖拽回写', async () => {
  const [page, api] = await Promise.all([
    source('../src/views/admin/result/certificate/templates/index.vue'),
    source('../src/api/m4-outcome.ts')
  ]);

  // 底图必须取服务端真实底图（草稿优先），而不是只显示「已关联文件」。
  assert.match(api, /export async function getTemplateBackground\(id: number\): Promise<Blob>/);
  assert.match(api, /url: `\/api\/certificate\/templates\/\$\{id\}\/background`/);
  assert.match(page, /getTemplateBackground\(certificateTemplateId\)/);
  assert.match(page, /<img v-if="backgroundPreviewUrl" :src="backgroundPreviewUrl" class="canvas-stage__image"/);

  // 元素必须在底图上按坐标与字号渲染，而不是只画序号点。
  assert.match(page, /:style="elementStyle\(element\)"/);
  assert.match(page, /left: `\$\{\(element\.xPosition \?\? 0\) \* scale\}px`/);
  assert.match(page, /fontSize: `\$\{fontSize \* scale\}px`/);
  assert.ok(!page.includes('sketch__point'), '位置示意必须由真实底图画布取代');

  // 拖拽必须回写坐标，坐标才是可编辑事实而不是只读展示。
  assert.match(page, /@pointerdown="startDrag\(\$event, index\)"/);
  assert.match(page, /@pointermove="moveDrag"/);
  assert.match(page, /@pointerup="endDrag"/);
  assert.match(page, /element\.xPosition = clampPosition\(drag\.startX \+ \(event\.clientX - drag\.startClientX\) \/ scale, design\.widthPx\)/);
  assert.match(page, /element\.yPosition = clampPosition\(drag\.startY \+ \(event\.clientY - drag\.startClientY\) \/ scale, design\.heightPx\)/);

  // 底图端点必须同时存在于 Controller 与机器可读契约，前端不得凭空设计接口。
  const [controller, contract] = await Promise.all([
    readFile(new URL('com/sts/certificate/controller/CertificateController.java', adminSourceRoot), 'utf8'),
    source('../../../docs/api/certificate.openapi.json')
  ]);
  assert.match(controller, /@GetMapping\("\/certificate\/templates\/\{certificateTemplateId\}\/background"\)/);
  assert.match(controller, /@SaCheckPermission\("certificate:template:list"\)/);
  assert.match(contract, /"\/api\/certificate\/templates\/\{certificateTemplateId\}\/background"/);
});

test('设计器坐标被约束在画布内，上传底图后自动按底图原始像素设定画布', async () => {
  const page = await source('../src/views/admin/result/certificate/templates/index.vue');

  // 坐标与后端 9405 校验一致地留在画布内，且输入框不允许越界。
  assert.match(page, /function clampPosition\(value: number, limit: number\): number/);
  assert.match(page, /:max="design\.widthPx - 1"/);
  assert.match(page, /:max="design\.heightPx - 1"/);

  // 底图原始像素即画布尺寸，用户不必手工量取坐标系。
  assert.match(page, /function applyCanvasSizeFromImage\(url: string\): void/);
  assert.match(page, /design\.widthPx = image\.naturalWidth/);
  assert.match(page, /design\.heightPx = image\.naturalHeight/);

  // 缩放比来自画布真实渲染宽度，窗口变化与弹窗打开都要重算。
  assert.match(page, /const stageScale = computed\(\(\) => \(stageWidth\.value > 0 && design\.widthPx > 0 \? stageWidth\.value \/ design\.widthPx : 0\)\)/);
  assert.match(page, /new globalThis\.ResizeObserver\(\(\) => syncStageWidth\(\)\)/);
});

test('设计器用示例变量值预览元素，变量与颜色都可编辑', async () => {
  const page = await source('../src/views/admin/result/certificate/templates/index.vue');

  // 变量必须有示例值，否则画布上看不出真实签发效果。
  assert.match(page, /\{ code: 'HOLDER_NAME', label: '学员姓名', sample: '张三' \}/);
  assert.match(page, /\{ code: 'CERT_NO', label: '证书编号', sample: 'CERT-SAMPLE01' \}/);
  assert.match(page, /function elementText\(element: CertificateElement\): string/);

  // 常用变量一键添加，变量编码与后端 VARIABLE_CODES 对齐。
  assert.match(page, /function addVariableElement\(variableCode: string\): void/);
  assert.match(page, /<ElButton v-for="item in VARIABLES" :key="item\.code" @click="addVariableElement\(item\.code\)">/);
  for (const code of ['HOLDER_NAME', 'CERT_NO', 'ISSUED_TIME', 'SOURCE_TYPE']) {
    assert.ok(page.includes(`'${code}'`), `设计器必须覆盖证书变量 ${code}`);
  }

  // 字号与颜色是签发渲染的真实属性，必须可编辑。
  assert.match(page, /<ElInputNumber v-model="element\.fontSize"/);
  assert.match(page, /<ElColorPicker v-model="element\.fontColor"/);
});

test('设计器两栏各自滚动，编辑下方元素时画布常驻可见且可反向定位', async () => {
  const page = await source('../src/views/admin/result/certificate/templates/index.vue');

  // 弹窗内容固定高度：元素多时由左栏内部滚动消化，画布不再被顶出视口。
  assert.match(page, /\.designer-body \{[\s\S]*?height: min\(72vh, 800px\);/);
  assert.ok(!/\.designer-body \{[\s\S]*?align-items: start;/.test(page), '固定高度两栏不应再用 align-items: start');

  // 左右两栏必须各自可滚动，且左栏内容贴顶排布。
  assert.match(page, /\.designer-pane \{[\s\S]*?align-content: start;[\s\S]*?overflow-y: auto;/);
  assert.match(page, /\.designer-canvas \{[\s\S]*?overflow-y: auto;/);

  // 加元素按钮吸顶，滚动查看下方元素时仍能追加新元素。
  assert.match(page, /\.quick-add \{[\s\S]*?position: sticky;[\s\S]*?top: 0;/);

  // 坐标字段固定两列，左栏变窄或出现滚动条时不会退化成单列把卡片拉长。
  assert.match(page, /grid-template-columns: repeat\(2, minmax\(0, 1fr\)\);/);

  // 反向定位：在画布上点中元素时，左栏对应设置卡片必须滚入可视区。
  assert.match(page, /const paneRef = ref<globalThis\.HTMLDivElement>\(\)/);
  assert.match(page, /:data-element-card="index"/);
  assert.match(page, /paneRef\.value\?\.querySelector<globalThis\.HTMLElement>\(`\[data-element-card="\$\{index\}"\]`\)/);
  assert.match(page, /card\?\.scrollIntoView\(\{ block: 'nearest' \}\)/);
  assert.match(page, /@pointerdown="startDrag\(\$event, index\)"/);
  assert.ok(/function startDrag[\s\S]*?selectElement\(index\);/.test(page), '画布按下元素必须先走 selectElement 以同步左栏');
  assert.match(page, /@click="selectElement\(index\)"/);

  // 窄屏回到上下排列时不应再固定高度，避免两栏挤在窄列里各自滚动。
  assert.match(page, /@media \(max-width: 1180px\) \{[\s\S]*?height: auto;/);
});
