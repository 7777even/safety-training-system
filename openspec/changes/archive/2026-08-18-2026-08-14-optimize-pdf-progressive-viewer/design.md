# Design：PC/H5 PDF 渐进预览

## 1. 总体结构

```text
play-info 返回受控 PDF URL
          ↓
项目专用 PdfViewer / PdfPreview
          ↓
pdfjs-dist getDocument（Bearer + Range）
          ↓
仅 getPage(currentPage) → 单 Canvas
          ↓
页码事件 → 课程学习页 → 既有 sessions 上报
```

- PC 新增 `components/pdf-viewer/PdfViewer.vue`，由 `AssetViewer` 在 `playInfo.format=pdf` 时直接使用。
- 移动 H5 新增适配移动布局的 `components/pdf-preview/PdfPreview.vue`，由 `AssetPreview` 直接使用。
- 两端都直接调用本端现有的 `pdfjs-dist` 和本地 worker；不通过 `OpenFileViewer` 的 PDF/Office PDF 配置。
- `OpenFileViewer` 保留给 Excel、图片、文本等非 PDF 格式，避免扩大重构范围。

## 2. 文档加载

`getDocument` 使用受控 URL，不预先读取 ArrayBuffer 或创建完整 Blob。PDF.js 解析对象仍是串行的，但查看器通过自定义
`PDFDataRangeTransport` 把当前所需切片、文件尾 xref 切片和随后最多 3 个对齐切片并行发出（同一文件最多 4 路在途），缩短 Range
瀑布等待；不得为了填满并发槽而把未读区间拆成大量额外请求，空闲后不再继续拉取未读页。

```ts
pdfjsLib.getDocument({
  range: concurrentRangeTransport,
  httpHeaders: { Authorization: `Bearer ${token}` },
  disableRange: false,
  disableStream: true,
  disableAutoFetch: true,
  rangeChunkSize: 262144,
  workerSrc: localWorkerUrl
})
```

约束：

1. Token 只通过请求头发送，不进入 URL、日志或 DOM 属性。
2. PDF worker 使用构建产物内的 `pdf.worker.min.mjs`，不依赖公共 CDN。
3. 字体/CMap 资源使用 `pdfjs-dist` 本地资源或系统字体，生产路径不得新增公共 CDN 依赖。
4. 文档加载完成只读取 `numPages`；不得循环调用 `getPage(1...numPages)`。
5. URL 或组件实例变化时取消旧 `loadingTask`、`renderTask`，使用序列号防止旧异步结果覆盖新素材。
6. Range 不可用或服务端返回错误时展示明确失败和受控打开/下载入口，不自动把整个 PDF 读成 Blob 重试。

## 3. 分页渲染

采用单页分页阅读模式：

- 初始只渲染 `initialPageNo`，默认第 1 页。
- 用户点击上一页、下一页或输入页码后，只请求并渲染目标页。
- 页面切换前取消未完成的渲染任务；渲染完成后释放旧页面引用并复用单一 Canvas。
- DOM 中最多保留一个主 Canvas；如后续增加相邻页预取，最多缓存前后各一页，且不得默认启用网络预取。
- Canvas 像素尺寸结合 `devicePixelRatio`，同时设置 CSS 尺寸，兼顾清晰度与内存。
- 最大输出像素数设置上限；超大页面按比例降采样，避免 Canvas 分配失败。
- 缩放、旋转仅重新渲染当前页，并使用防抖/任务取消避免重复渲染堆积。

## 4. 交互与状态

PC 与 H5 统一提供：

- 当前页 / 总页数。
- 上一页、下一页。
- 合法范围内的页码跳转。
- 放大、缩小、恢复、左右旋转。
- 文档加载、页面加载、密码需要、密码错误、损坏文件、网络失败和渲染失败状态。
- 受控打开或下载兜底。

PC 保持现有组件契约：

- 输入：`url`、`fileName`、`initialPageNo`。
- 输出：`pagecount`、`pageupdate`、`error`、`loaded`。
- 暴露：`goToPage(pageNo)`。

H5 使用触控尺寸按钮和窄屏工具栏；App/非 H5 编译分支不引入 PDF.js DOM 渲染代码。

## 5. 学习进度

- 文档成功加载后发送一次 `pagecount(numPages)`。
- 目标页成功显示后发送 `pageupdate(pageNo)`；加载失败或只输入未确认时不发送。
- 恢复 `initialPageNo` 时先裁剪到 `[1, numPages]`，成功显示后再回传。
- 切换课程内容、退出页面和定时检查点继续使用现有学习会话 API，不改变后端字段或完成判定。
- 移动 H5 本 Change 只完成 PDF 页码组件能力；如果既有移动学习会话 Contract 未发送页码，不自行扩展接口。

## 6. 资源与性能边界

不得出现：

- 初始化时按总页数调用 `getPage()`。
- 为每一页创建 wrapper、Canvas 或 MutationObserver。
- 每次滚动扫描全部页面的 `getBoundingClientRect()`。
- PDF URL 先通过 Axios/uni.request 下载成完整 ArrayBuffer。
- 将鉴权 Token 拼接到查询参数。

资源清理：

- 每次换页取消旧 `RenderTask`。
- 切换文件或卸载组件时依次取消 render、销毁 page、销毁 document、销毁 loadingTask。
- 清理 ResizeObserver、定时器、事件监听和临时 Object URL。
- 对连续快速翻页只保留最后一次目标页渲染结果。

## 7. patch 与依赖处理

- 删除 `patches/@open-file-viewer__core@0.1.33.patch`。
- 删除 `pnpm-workspace.yaml` 和锁文件中的对应 `patchedDependencies`。
- 重新执行 pnpm install，确认 `@open-file-viewer/core` 使用标准包路径。
- 两端继续保留 `@open-file-viewer/core/vue`，因为非 PDF 格式仍在使用；不得因此顺带迁移全部文件预览。
- 不引入新的预览技术依赖。移动 workspace 显式声明 Uni 构建链已使用的同版本 `@dcloudio/uni-components`，避免 pnpm
  严格隔离下依赖解析不稳定；测试使用 Node 24 内置测试能力和可注入 PDF.js adapter。

## 8. TDD 与测试设计

先抽取可注入的 PDF 加载/渲染控制器，使用 mock PDF.js 表达行为，再接入 Vue UI。

Red 阶段至少覆盖：

1. 648 页文档初始化只调用目标页一次，不能调用其余页面。
2. `initialPageNo` 越界时正确裁剪。
3. 快速从第 1 页切到第 300、301 页时取消旧渲染，只提交最后结果。
4. URL 切换和卸载会销毁 loading/document/page/render 资源。
5. 请求参数包含 Bearer、Range 开启、stream/autofetch 关闭；当前页所需 Range 由查看器并行发出。
6. 加密、网络、损坏和渲染错误映射为稳定中文状态。
7. PC 页码事件只在页面成功显示后发送。
8. H5 构建不会把 DOM PDF 分支错误用于非 H5。

组件完成后运行两端定向测试、typecheck、lint 和各自 build；不得只做源代码字符串断言来证明渐进加载。

## 9. 真实样本验收

使用仓库既有 64.6 MB、648 页、经 qpdf 线性化的新 PREVIEW，在 PC Chrome 和移动 H5 浏览器验证：

1. 初次打开只出现目标页 `getPage` 行为，不读取 648 页元信息。
2. Network 中可观察到受控文件 URL、Bearer 请求和后续合法 `206 Content-Range`。
3. 第一页显示前传输量显著小于完整文件；不得完成 64.6 MB 全量下载后才显示，也不验证非线性化 PDF。
4. 未操作时不得持续请求后续页面 Range。
5. 跳转第 300 页只加载目标页所需范围，页面可显示且当前页更新为 300。
6. 预览区 Canvas 数量保持常量级，页面总数增长不增加成百上千 DOM 节点。
7. 连续快速翻页、缩放、切换素材后无旧页面闪回、控制台未处理异常或持续网络请求。
8. 课程退出后上次成功显示页能够按既有会话链恢复。

## 10. 风险与回滚

- 页面尺寸、旋转和字体差异只影响当前页，失败时允许用户重试或受控打开，不回退全量读取或兼容非线性化文件。
- 仍只渲染当前页，不把全部页面排进 DOM。默认缩放按预览区宽高适配完整一页；当前页滚到顶/底后，滚轮继续翻上一页/下一页。
- 回滚以恢复原组件 PDF 分支为单位，但不恢复无效 patch；如必须临时回滚，应记录大 PDF 性能退化。

## 11. Page Tree 根因与后端生成方案（已实施）

真实样本表明，`qpdf --linearize` 后的根 `/Pages` 仍直接包含 648 个页面 `/Kids`。PDF.js 6.2.108 在文档初始化阶段固定执行
`checkLastPage()`，扁平 Page Tree 会迫使它读取大量 xref 和页面链路；更换 RangeTransport、禁用对象流或调整 Chunk
只能改变流量/请求数取舍，不能根治。

后续生成链调整为：

```text
PDF 输入 → PDFBox Page Tree Normalizer（最大扇出 32）
        → qpdf --linearize
        → qpdf 语法/线性化 + PDFBox 页数/结构校验
        → 上传 PREVIEW
```

规范化器递归构造有界 `/Pages` 层级，修正每个中间节点的 `/Parent`、`/Count` 和 `/Kids`，但不得修改页面顺序和页面内容对象。只修改派生
PREVIEW，SOURCE 保持不变。

同口径 648 页样本结果：

| 指标              |             仅线性化 | Page Tree 分层 + 线性化 |
|-----------------|-----------------:|-------------------:|
| 文档初始化读取         | 34,341,774 bytes |      781,722 bytes |
| 第一页渲染数据就绪       | 34,866,062 bytes |    1,371,546 bytes |
| 64 KB Range 请求数 |              529 |                 17 |

详细根因、排除方案、风险和测试计划见 `docs/architecture/pdf-progressive-preview-optimization.md`。

2026-08-17 已在 `PdfLinearizer` 生产链接入 `PdfPageTreeNormalizer`。规范化器先固化页面可继承属性，再按最大扇出 32
递归重建 `/Pages` 层级；PDFBox 中间文件使用非压缩 xref 保存，由 qpdf 负责最终压缩和线性化。qpdf 校验后再次使用
PDFBox 验证页数、扇出、`Parent`、`Count` 和节点类型，全部通过后才允许上传 PREVIEW。

## 12. Range 并行与网关（2026-08-18）

查看器最终口径：256 KB 切片、同一文件最多 4 路在途、额外预取随后 3 个切片和文件尾 xref 切片。已排除并发 16 / 预取 15 和 2 MB
切片，因为都会把未读区间放大成额外 206。实现见 `@safety/shared` 的 `PdfConcurrentRangeLoader` 与
`createPdfjsConcurrentRangeAdapter`，PC/H5 共用。

HTTP/2 可让这 4 路在浏览器到网关之间真正多路复用，但不改变 Range 语义。浏览器需要 `https`；Nginx 1.30 应使用
`listen 443 ssl;`
加 `http2 on;`，且二进制须包含 `http_ssl_module` 与 `http_v2_module`。2026-08-18 运维自编译 Nginx 1.30.1 未包含这两项，只改
conf 无法启用。Vite 开发代理走 HTTP/1.1 时 Network 看不到 `h2` 是预期。

测试：

1. `node --test frontend/shared/tests/pdf-concurrent-range.test.mjs`
2. `node --test frontend/shared/tests/pdf-page-session.test.mjs`
3. `node --test frontend/pc-web/tests/pdf-progressive-viewer.test.mjs`
4. `node --test frontend/mobile-app/tests/learner-course-learning.test.mjs`
5. 真实样本：`pdf-linearized-range-smoke.mjs`、`pdf-range-transport-diagnostic.mjs`（256 KB）
6. 后端门槛：`QpdfRealProcessTest` 调用诊断脚本，传入 `262144`

详细取舍、Nginx 编译参数和命令结果见 `docs/architecture/pdf-progressive-preview-optimization.md` §11～13。
