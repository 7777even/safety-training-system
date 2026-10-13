# PDF 渐进预览与 Page Tree 优化

日期：2026-08-18
状态：生产生成链已实施；查看器已按 256 KB 切片做有界 Range 并行；HTTP/2 需网关另行编译启用
对应 OpenSpec：`openspec/changes/2026-08-14-optimize-pdf-progressive-viewer/`

## 1. 问题结论

大 PDF 的预览性能包含两个彼此独立的问题：

1. 前端渲染问题：通用查看器初始化全部页面、创建全部页面 DOM，PC 还在滚动时扫描所有页面位置。
2. PDF 文件结构问题：qpdf 线性化只能重排对象字节位置，不能保证 `/Pages` 树具有适合 PDF.js 按区间解析的层级结构。

前端改为单页 Canvas 后，第一个问题已经消除；但 648 页真实样本仅执行 `qpdf --linearize` 后，PDF.js 文档初始化仍读取约 34
MB，网络渐进加载指标没有通过。

## 2. 根因

样本的根 Page Tree 为：

```text
/Pages
  /Count 648
  /Kids [Page1 Page2 Page3 ... Page648]
```

即 648 个页面引用全部直接挂在根 `/Kids` 下，没有中间 `/Pages` 节点。

当前使用的 PDF.js 6.2.108 在 worker 初始化中执行：

```text
initDocument
  → checkHeader
  → parseStartXRef
  → parse
  → checkFirstPage
  → checkLastPage
```

`checkLastPage` 在返回 `loadingTask.promise` 之前校验最后一页。面对扁平 Page Tree，PDF.js 为解析目录、xref 和最后页链路读取大量分散区间；
`disableAutoFetch=true` 只能禁止空闲预取，不能跳过初始化必需的 `checkLastPage`。

PDF.js 官方也记录了扁平 Page Tree 导致 Range
初始化读取大量数据的同类问题：[mozilla/pdf.js#17128](https://github.com/mozilla/pdf.js/issues/17128)。官方 FAQ
同时说明，实际所需区间取决于 PDF 内部结构，而不仅取决于服务器是否支持
Range：[PDF.js FAQ](https://github.com/mozilla/pdf.js/wiki/Frequently-Asked-Questions)。

## 3. 已排除方案

### 3.1 只调整前端参数

已验证以下参数：

```ts
disableRange: false
disableStream: true
disableAutoFetch: true
```

它们能够保留 Range 并停止空闲预取，但不能避免 PDF.js 初始化时校验最后一页。

### 3.2 自定义 PDFDataRangeTransport

显式 RangeTransport 排除了浏览器初始 GET、响应头和请求封装因素，但文档初始化仍读取约 34.34 MB。因此问题不是默认 Fetch/Range
封装。

### 3.3 调整 rangeChunkSize

|  Chunk |    文档初始化读取 | 请求数 | 结论             |
|-------:|-----------:|----:|----------------|
|   8 KB |  约 6.56 MB | 809 | 流量下降，但请求数量不可接受 |
|  16 KB | 约 11.50 MB | 709 | 仍存在大量 RTT      |
|  32 KB | 约 20.51 MB | 632 | 无法根治           |
|  64 KB | 约 34.34 MB | 529 | 当前默认量级         |
| 256 KB | 约 57.41 MB | 220 | 请求减少但接近全量读取    |
|   1 MB | 约 60.29 MB |  57 | 基本失去渐进加载意义     |

Chunk 大小只能在传输量和请求数之间交换，不能解决扁平 Page Tree。

### 3.4 只增加 qpdf 参数

- `qpdf --object-streams=disable --linearize`：结果没有改善。
- `qpdf --pages . 1-z` 后再线性化：Page Tree 仍然扁平。
- Ghostscript 重写后再线性化：Page Tree 仍然扁平，且样本文件增大到约 91 MB。

因此不采用 PDF.js worker 补丁、第三方 dist patch、全量 Blob 下载或单纯调整 Chunk 的方案。

## 4. 已验证的根治方案

生成 PREVIEW 时增加 Page Tree 规范化：

```text
原始 PDF / OnlyOffice PDF
          ↓
PDFBox Page Tree Normalizer
  - 每个 /Pages 节点最多 32 个 /Kids
  - 修正 Parent 和 Count
  - 页面内容与资源对象保持不变
          ↓
qpdf --linearize
          ↓
qpdf --check
qpdf --check-linearization
PDFBox 页数/结构校验
          ↓
上传最终 PREVIEW
```

648 页样本重建后为：

```text
/Pages (Count 648)
  /Kids [PagesGroup1 ... PagesGroup21]

PagesGroupN
  /Count <= 32
  /Kids [Page...]
```

算法采用最大扇出 32。页面超过 1024 页时继续递归创建上一层 `/Pages` 节点，直到根节点的 `/Kids` 也不超过 32，不能只针对 648
页写死两层结构。

## 5. 同口径真实样本对比

对比均使用同一份 648 页输入、PDF.js 6.2.108、64 KB RangeTransport，并以 `loadingTask.promise` 完成作为“文档初始化”，以第一页
`getOperatorList()` 完成近似“第一页渲染数据就绪”。

| 指标         |       仅 qpdf 线性化 | Page Tree 分层后再线性化 |
|------------|-----------------:|------------------:|
| 最终文件大小     | 65,536,910 bytes |  59,895,194 bytes |
| 页数         |              648 |               648 |
| 文档初始化读取    | 34,341,774 bytes |     781,722 bytes |
| 第一页渲染数据就绪  | 34,866,062 bytes |   1,371,546 bytes |
| Range 请求数  |              529 |                17 |
| qpdf 线性化校验 |               通过 |                通过 |

结果表明 Page Tree 分层是决定性变量：

- 文档初始化减少 33,560,052 bytes，即 33.56 MB（32.01 MiB），下降 97.72%。
- 第一页渲染数据就绪减少 33,494,516 bytes，即 33.49 MB（31.94 MiB），下降 96.07%。
- Range 请求减少 512 次，从 529 次降至 17 次，下降 96.79%。

这里的 MB 使用十进制 `1 MB = 1,000,000 bytes`；括号内同时给出二进制 MiB，避免统计口径混淆。

## 6. 官方资料与替代方案评估

### 6.1 PDF.js 官方结论

PDF.js 官方问题 [#17128](https://github.com/mozilla/pdf.js/issues/17128) 描述了同类现象：Range 参数已经启用，但根 `/Pages`
平铺大量页面引用时，解析 xref 会持续请求区间；维护者确认问题在 PDF 的 Page Tree 结构，并指向修复 PDF 文档结构的方案，而不是新增前端开关。

PDF.js [FAQ](https://github.com/mozilla/pdf.js/wiki/Frequently-Asked-Questions) 也说明，Range 只能读取渲染所需区间，实际读取量仍取决于关键对象位于
PDF 的什么位置。因此“服务端返回 206”与“首屏读取量足够小”是两项不同验收条件。

### 6.2 qpdf 官方能力边界

qpdf 的[线性化文档](https://qpdf.readthedocs.io/en/11.3/linearization.html)说明，其线性化准备主要包括传播页面继承属性、分析对象归属、创建
Hint Table 和重排对象。官方没有提供“将任意扁平 Page Tree 自动重建为固定扇出的平衡树”的 CLI 保证；本项目实际执行
`--linearize`、`--pages` 和 `--object-streams=disable` 后，648 个根 `/Kids` 仍然存在。

因此 qpdf 仍然适合承担最终线性化和一致性校验，但不能单独承担本样本的 Page Tree 分层。

### 6.3 PDFBox 分层重建

Apache PDFBox 将 [PDPageTree](https://pdfbox.apache.org/docs/2.0.1/javadocs/org/apache/pdfbox/pdmodel/PDPageTree.html)
定义为负责高效页面顺序的页面树抽象。项目后端已经使用 PDFBox 3.0.5，基于其 COS 层显式创建中间 `/Pages` 节点，可以同时控制最大扇出、
`Parent`、`Count` 和页面顺序，不需要新增生产依赖。

### 6.4 其他候选方案

| 方案                                  | 评价                                            |
|-------------------------------------|-----------------------------------------------|
| 继续调 `disableAutoFetch`/Chunk        | 已实测不能根治，只能交换流量与请求数量                           |
| 自定义 PDFDataRangeTransport           | 适合特殊鉴权/数据来源，但不能改变 PDF.js 必须解析的对象集合            |
| 修改 PDF.js worker 跳过 `checkLastPage` | 会形成私有 worker fork，并绕过损坏页树校验；升级和正确性风险高，不采用     |
| 浏览器原生 PDF viewer                    | 难以稳定注入 Bearer、页码事件和课程学习断点，不满足现有契约             |
| 服务端拆成单页 PDF/图片                      | 能降低客户端解析量，但会新增派生文件、存储、分页协议和清晰度问题，明显扩大架构范围     |
| PDFBox Page Tree 分层 + qpdf          | 已在真实样本上将首屏数据降至 1.37 MB，复用现有依赖且不修改 API，是当前最佳方案 |

综合官方资料、项目约束和真实样本结果，目前没有比“标准 Page Tree 分层后再 qpdf 线性化”更低风险、效果更确定的方案。

## 7. 实施约束

1. 仅修改派生 PREVIEW，不覆盖用户上传的 SOURCE。
2. 复用后端已有 PDFBox 3.0.5 和 qpdf，不新增生产依赖。
3. 不修改受控文件 URL、HTTP Range、权限、API Contract 或数据库结构。
4. 不对 PDF.js worker 或第三方 npm 产物打补丁。
5. 失败时不得上传半成品 PREVIEW；中间文件和对象存储目标按现有失败清理规则处理。
6. 最终 PREVIEW 必须同时通过页数一致、qpdf 语法检查和线性化检查。
7. 当前处于开发阶段，不实现非线性化或旧生成链兼容分支；已有开发数据需要受益时重新生成 PREVIEW。

## 8. 风险

- 页面树重写必须保留页面顺序、MediaBox、Rotate、Resources、Contents、注释与可继承页面属性。
- PDFBox 保存派生预览会使原 PDF 数字签名失效；PREVIEW 本身不是签名原件，SOURCE 必须保持不变。
- 表单、书签、标签结构和页面引用需要真实样本回归，不能只验证页数。
- 超大页数文件需要限制内存与临时磁盘，并复用现有失败清理机制。
- Page Tree 正常并不保证所有 PDF 都具有相同首屏字节数；性能阈值先以固定 648 页样本作为可重复基准。

## 9. 计划测试

### 后端零 DB TDD

1. 构造 65 页扁平 Page Tree，先观察“根节点 65 个 Kids”失败。
2. 规范化后验证最大扇出不超过 32、Count 与 Parent 正确、页序和页数不变。
3. 构造超过 1024 页文档，验证递归生成多层 Page Tree。
4. 验证页面可继承属性、书签页面引用和注释引用不丢失。
5. 验证规范化失败时不执行 qpdf、不上传 PREVIEW，并清理临时文件。

### 真实进程与跨端验收

1. 648 页样本依次通过 PDFBox 重开、`qpdf --check`、`qpdf --check-linearization` 和页数一致性检查。
2. 执行 `frontend/shared/tests/pdf-range-transport-diagnostic.mjs`，记录文档初始化、第一页和请求数。
3. 执行 `frontend/shared/tests/pdf-linearized-range-smoke.mjs`，验证 Bearer、Range/206、空闲不预取和第 300 页。
4. PC Chrome/H5 真实页面验证单 Canvas、页码恢复、快速跳页和 Network。

固定 648 页样本目标：文档初始化不超过 2.5 MB、第一页渲染数据就绪不超过 3.5 MB、256 KB Range 请求不超过 16
次。该阈值用于本项目固定样本回归，不作为所有第三方 PDF 的统一业务承诺。查看器 `rangeChunkSize` 为 256
KB，优先减少往返；Page Tree 分层后加大切片不会再把整文件拖下来。64 KB 对照表仅保留为分层前的历史测量。

## 10. 生产实现结果

2026-08-17 已完成生产接入：

- `PdfPageTreeNormalizer` 在 qpdf 前将页面树按最大扇出 32 递归分层，并固化 `Resources`、`MediaBox`、`CropBox`、`Rotate`
  可继承属性。
- 页面 COS 字典保持原对象，页面顺序、内容、注释和书签目标不重建；仅更新页面树父子关系。
- PDFBox 中间文件使用非压缩 xref 保存，避免压缩保存产生 xref stream 自引用警告；最终压缩与线性化仍由 qpdf 完成。
- qpdf 的语法和线性化检查后，再用 PDFBox 校验页数、最大扇出、节点类型、`Parent`、`Count` 和循环引用。
- 任一阶段失败均不会进入 PREVIEW 上传回调，任务目录最终统一清理。
- 对无需输入密码即可读取、但带 PDF 内部修改/组装限制的文档，仅在内存中的派生文档保存前移除保护；对象存储 SOURCE
  保持原字节和加密状态，PREVIEW 仍只通过既有 Bearer、素材权限和租户隔离文件代理访问。需要非空打开密码的 PDF 继续失败。
- 规范化异常保留底层 cause；任务处理器在日志中记录经 Token、密码、Authorization 和 URL 查询参数清洗的异常类型与堆栈，
  持久化任务原因继续使用稳定业务消息。

固定 648 页样本已在真实生产链中通过 PDFBox 重开、两项 qpdf 校验、RangeTransport 阈值与 Bearer/206 烟测；性能指标按查看器
256 KB 切片保持“文档初始化不超过 2.5 MB、第一页不超过 3.5 MB、Range 请求不超过 16 次”的自动化门槛。

另使用 63.86 MiB、2517 页、空用户密码且禁止修改/组装的真实 PDF 验证完整生成链：SOURCE 前后仍为加密状态，派生
PREVIEW 可无密码重开且不再加密，页数保持 2517，并通过 `qpdf --check` 与 `qpdf --check-linearization`。

## 11. 前端 Range 并行

PDF.js worker 解析对象仍是串行 `MissingDataException` 循环，默认 Fetch 因此表现为一条 Range 瀑布。查看器保持
`disableStream=true`、`disableAutoFetch=true`，并用自定义 `PDFDataRangeTransport` 并行拉取：

1. PDF.js 当前请求的区间
2. 文件最后一个 256 KB 切片（线性化 xref）
3. 随后最多 3 个对齐切片

同一文件并发上限为 4，预取窗口保持 3 个切片。这只重叠往返时间，不改变必须解析的对象集合，也不在空闲时持续预取未读页。

## 11.1 查看器最终口径（2026-08-18）

PC `PdfViewer` 与 H5 `PdfPreview` 通过 `createPdfjsConcurrentRangeAdapter` 接入共享加载器
`PdfConcurrentRangeLoader`。生产参数：

| 参数                           |              值 | 含义                        |
|------------------------------|---------------:|---------------------------|
| `PDF_RANGE_CHUNK_SIZE`       | 262144（256 KB） | 单次 Range 对齐切片             |
| `PDF_RANGE_MAX_CONCURRENT`   |              4 | 同一文件同时在途的 Range 上限        |
| `PDF_RANGE_LOOKAHEAD_CHUNKS` |              3 | 在 PDF.js 当前区间之后额外预取的对齐切片数 |
| `disableRange`               |          false | 保留 HTTP Range / 206       |
| `disableStream`              |           true | 不把整文件当流下载                 |
| `disableAutoFetch`           |           true | 空闲后不持续预取未读页               |

探测到 `Content-Range` 总长度后，加载器会并行拉文件最后一个切片（线性化 xref 通常在此）以及随后最多 3 个切片。PDF.js
随后要已缓存切片时不再发重复请求；会话销毁会 `AbortController` 取消在途 Range。

## 11.2 已排除的前端调参

在 Page Tree 分层之后，再调切片和并发只能交换「条数 / 浪费流量 / 网关压力」，不能继续把必须解析的对象变少：

| 尝试                | 结果                                   |
|-------------------|--------------------------------------|
| 并发 16 + 预取 15 个切片 | Network 出现大量额外 206，请求明显变多            |
| 切片改为 2 MB         | 文件尾和预取都按 2 MB 放大，空区间浪费大，观感仍是「请求很多」   |
| 只开 16 路、预取仍为 3    | 并发槽填不满，但上限已高于浏览器 HTTP/1.1 约 6 路和网关承受 |

因此固定为 **256 KB + 最多 4 路 + 提前 3 刀**。648 页分层样本在 256 KB 下的回归门槛仍是：文档初始化不超过 2.5 MB、第一页不超过
3.5 MB、Range 不超过 16 次（诊断脚本口径，不含查看器有界预取）。

Page Tree + 线性化已经把约 529 次 / 34 MB 降到约 17 次 / 首屏 1.4 MB。剩下十几次 206 来自 PDF.js 必读的头、xref、页面树、第一页资源和
`checkLastPage`，不是 qpdf 参数没开够。下一步若还要加快打开，应做 PREVIEW 字体子集/预览图压缩，或让浏览器对网关走
HTTP/2，而不是继续加大切片。

## 12. 网关 HTTP/2

Range 并行的收益取决于浏览器到网关是否能多路复用。

- 浏览器 HTTP/2 **必须 HTTPS**。明文 `http://host:20080` 或 Vite 开发代理不会出现 `h2`。
- Nginx 1.25.1 及以上：`listen 443 ssl;` 且 `http2 on;`。更早版本用 `listen 443 ssl http2;`，不要写 `http2 on;`。
- 二进制必须编入 `--with-http_ssl_module` 和 `--with-http_v2_module`。仅写配置不够。
- 反代上游可继续 HTTP/1.1 + keepalive，并透传 `Authorization`、`Range`；PDF 不要 gzip，以免破坏 206。

2026-08-18 运维机 `nginx -V`：`nginx/1.30.1`，`configure arguments` 只有 prefix/日志路径和
`--add-module=/root/nginx-plugin-master`，**没有** `http_ssl_module` / `http_v2_module`。该构建当前不能启用
HTTP/2，需要按原参数重新编译并补上上述两个模块后再配 443 证书。

开发环境 `pnpm --filter @safety-training/pc-web dev` 经 Vite 代理到 HTTP 上游时，Network 协议列保持 `http/1.1` 是预期行为。验证
`h2` 必须用浏览器直连 `https://` 网关。

## 13. 测试

### 13.1 共享 Range 并行（Node 内置测试）

```bash
node --test frontend/shared/tests/pdf-concurrent-range.test.mjs
node --test frontend/shared/tests/pdf-page-session.test.mjs
```

`pdf-concurrent-range.test.mjs` 覆盖：

1. `Content-Range` 总长度解析、尾块与后续切片规划。
2. 生产常量固定为 256 KB / 4 路 / 提前 3 刀。
3. 探测首块后并行预取尾块和后续切片，空闲后请求数不再增长。
4. PDF.js 再要已预取切片时不重复发 Range，并立即交付。
5. `abort()` 取消未完成请求。
6. `createPdfjsConcurrentRangeAdapter` 把 URL 换成 `PDFDataRangeTransport`，后续区间交给加载器。

`pdf-page-session.test.mjs` 继续覆盖只读目标页、跳页取消、销毁释放，以及 `getDocument` 仍带 Bearer、`disableRange: false`、
`disableStream: true`、`disableAutoFetch: true`、`rangeChunkSize: 262144`。

2026-08-18 上述两个文件合计 14 个用例全部通过。

### 13.2 组件字符串与类型

```bash
node --test frontend/pc-web/tests/pdf-progressive-viewer.test.mjs
node --test frontend/mobile-app/tests/learner-course-learning.test.mjs
pnpm --filter @safety/shared typecheck
pnpm --filter @safety-training/pc-web typecheck
```

PC / H5 定向测试断言查看器接入 `createPdfjsConcurrentRangeAdapter`。2026-08-18 shared 与 PC typecheck 通过。移动端标准
`typecheck` 仍受仓库既有 `ignoreDeprecations` 配置问题阻塞，与本次改动无关。

### 13.3 真实样本烟测与后端门槛

```bash
node frontend/shared/tests/pdf-linearized-range-smoke.mjs <linearized-pdf>
node frontend/shared/tests/pdf-range-transport-diagnostic.mjs <linearized-pdf>
```

烟测默认 `rangeChunkSize=262144`，使用并发适配器，断言峰值在途请求 ≥ 2、Bearer、206、空闲不再增长、跳转第 300 页不读完整文件。诊断脚本供
`QpdfRealProcessTest` 调用，门槛仍为初始化 ≤ 2.5 MB、第一页 ≤ 3.5 MB、请求 ≤ 16 次，显式传入 `262144`。
