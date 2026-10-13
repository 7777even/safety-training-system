# PC/H5 PDF 渐进预览 QA

日期：2026-08-14  
Owner：AI（前端）  
OpenSpec：`2026-08-14-optimize-pdf-progressive-viewer`

## 范围与结论

- PC 与移动 H5 的 PDF 已退出 OpenFileViewer PDF 插件链，改为项目自有 PDF.js 单页 Canvas 组件。
- 只加载和渲染当前目标页；保留 Bearer、Range、页码恢复、课程页码上报、快速翻页取消和资源释放。
- 所有线上 PDF 按用户确认视为已线性化，不实现历史或非线性化兼容分支。
- 第三方 patch 与 pnpm `patchedDependencies` 已删除，冻结锁文件安装通过。
- 前端代码验证通过；2026-08-17 已完成 PDFBox Page Tree 生产优化并通过 648 页真实样本网络门槛。2026-08-18 人工确认 Task 2
  真实登录态复核通过，Range/206 请求已减少。Change 已归档。

## TDD 证据

Red 阶段先运行共享控制器测试并观察到 `pdf-page-session.ts` 不存在导致失败；Green 阶段完成最小控制器后覆盖：

1. 648 页只读取初始目标页。
2. 初始页与跳页边界裁剪。
3. 快速跳转 300/301 页取消旧渲染，只提交最后一页。
4. 切换文档与卸载释放 page/document/loading/render 资源。
5. 旧 URL 加载取消不覆盖新文档。

## 已通过验证

- `pnpm install --frozen-lockfile --offline`：通过。
- PDF 控制器、PC PDF、PC 课程、移动课程 4 个定向测试文件：通过，0 failure。
- shared typecheck：通过。
- PC、移动 typecheck 使用 `--ignoreDeprecations 5.0`：通过；标准脚本受仓库既有 TypeScript 5.9 与 `ignoreDeprecations: 6.0`
  配置不匹配影响。
- PC、移动、shared 本次改动文件定向 ESLint：通过，0 warning。
- PC production build：通过；产物包含本地 `pdf.worker.min-*.mjs`。
- `openspec validate 2026-08-14-optimize-pdf-progressive-viewer --strict`：通过。
- `git diff --check`：通过。

## 64.6 MB / 648 页线性化样本

先用本机 qpdf 生成临时线性化副本并执行 `qpdf --check-linearization`，随后通过本地 Bearer + HTTP Range 服务让 PDF.js 加载第
1 页、空闲 500 ms、跳转第 300 页：

- 文件：65,536,910 bytes，648 页。
- 第 1 页阶段传输：约 34.54 MB，小于完整文件；空闲阶段传输量不增长。
- 跳转第 300 页后传输量未增长。
- 观察到 525 个 `206` Range 请求，Bearer 请求头校验通过。
- qpdf 线性化信息显示第一页区段结束于 631,562 bytes；PDF.js 仍因该样本的尾部 xref 与共享对象布局读取约 34.5 MB。前端已消除全页
  DOM 和主动全页 `getPage`，但“已线性化”本身不等于任意 PDF 都能以极小网络量显示首屏。

烟测脚本：`frontend/shared/tests/pdf-linearized-range-smoke.mjs`。

## 未完成验证与既有阻塞

- 移动 H5 build 先因移动 workspace 未显式声明 Uni 已使用的 `@dcloudio/uni-components` 失败；显式声明同版本后，编译器已能启动。
- 随后 Uni/Vite 在 pnpm 符号链接路径中无法解析 Pinia 已声明的 `@vue/devtools-api` 传递依赖。继续把整条传递依赖复制到业务应用不是合理修复，因此未扩大本
  Change。
- 2026-08-18 人工已完成真实登录态 Range/206 复核，确认请求减少；该项不再阻塞。HTTP/2 协议列仍取决于网关重新编译。

## 2026-08-15 Page Tree 根因复测

将传输量拆分到 PDF.js 文档初始化、`getPage(1)` 和第一页 `getOperatorList()` 后确认：

- 仅 qpdf 线性化：文档初始化即读取 34,538,382 bytes；`getPage(1)` 不再增加；第一页渲染数据累计 35,062,670 bytes。
- 第 300 页渲染完成累计 35,718,030 bytes。
- 说明约 34.5 MB 发生在 `loadingTask.promise` 返回前，不是 Canvas 渲染或前端主动遍历页面造成。

排除结果：

- 自定义 PDFDataRangeTransport：文档初始化仍为 34,341,774 bytes、529 次请求。
- `qpdf --object-streams=disable`：无改善。
- qpdf 页面选择重写：根 `/Kids` 仍平铺 648 页。
- Ghostscript 重写：根 `/Kids` 仍平铺，文件增大。
- 8 KB～2 MB Range Chunk：只能交换传输量与请求数，不能根治。

使用后端现有 PDFBox 将 Page Tree 按最大扇出 32 分层，再执行 qpdf 线性化，同一 RangeTransport 得到：

| 指标        |       仅 qpdf 线性化 | Page Tree 分层 + qpdf |
|-----------|-----------------:|--------------------:|
| 文件大小      | 65,536,910 bytes |    59,895,194 bytes |
| 文档初始化读取   | 34,341,774 bytes |       781,722 bytes |
| 第一页渲染数据就绪 | 34,866,062 bytes |     1,371,546 bytes |
| Range 请求数 |              529 |                  17 |

分层输出保持 648 页，并通过 `qpdf --check` 与 `qpdf --check-linearization`。该结果证明 Page Tree
分层是本样本的有效根治方案，但当前只完成技术验证，尚未进入生产代码。

按同口径换算：文档初始化减少 33.56 MB（97.72%），第一页渲染数据就绪减少 33.49 MB（96.07%），Range 请求减少 512 次（96.79%）。

长期技术记录：`docs/architecture/pdf-progressive-preview-optimization.md`。

## 双轴 Review

- 规格符合性：前端已实现范围通过；Task 3 的最大扇出、引用保持、双重结构校验、失败短路和固定样本性能门槛均已实现。
- 代码质量：通过。没有全页循环、全页 wrapper、滚动扫描、Token 查询参数或完整受控 PDF Blob 预下载；后端没有新增依赖、API、权限、数据库、对象存储或
  Range 语义变化。

## 2026-08-17 Task 3 生产实施验证

- TDD Red：新增测试先因 `PdfPageTreeNormalizer` 不存在而编译失败。
- 定向 Green：`PdfPageTreeNormalizerTest`、`PdfLinearizerTest`、`DocPreviewProviderTest`、`FfmpegConfigurationTest`、
  `QpdfRealProcessTest` 共 16 个用例通过。
- 覆盖 65 页扁平树、1025 页递归层级、页序/内容、继承属性、书签/注释、失败不调用 qpdf、最终校验失败不上传。
- 648 页真实链路通过 PDFBox 重开、`qpdf --check`、`qpdf --check-linearization`，并由现有两个前端诊断脚本验证 2 MB/3 MB/32
  次门槛、Bearer、206、空闲不预取和第 300 页跳转。
- RangeTransport 性能门槛已纳入稳定的真实进程回归；本地 HTTP/206 烟测不嵌入 Maven，因为受限执行环境可能禁止监听回环端口，但本次已在允许监听的运行中实际通过。
- Admin 全量零 DB 执行 615 个用例并全部通过。此前 `CourseSnapshotAssemblerTest` 的失败来自测试仍 mock 旧的 `selectOne`
  调用；改为匹配生产代码实际使用的 `selectById` 后，定向及全量回归均通过。
- 本 Task 不涉及 Entity、Mapper、SQL、事务或数据库结构，因此未运行数据库 IT。

## 2026-08-18 Range 并行与切片口径

前端在保持 Page Tree + qpdf 生成链不变的前提下，为 PC/H5 增加有界 Range 并行，避免 PDF.js 串行解析把 206 排成一条瀑布。

最终口径：

- 切片 256 KB（`PDF_RANGE_CHUNK_SIZE = 262144`）
- 同一文件最多 4 路在途
- 额外预取文件尾切片 + 随后 3 个对齐切片
- 仍关闭 stream 与 autoFetch，空闲后不继续拉未读页

过程中排除：

- 并发 16 + 预取 15：请求条数明显增加
- 切片 2 MB：文件尾和预取被放大，浪费流量，观感仍是请求偏多

运维 Nginx `nginx/1.30.1` 自编译未包含 `http_ssl_module` / `http_v2_module`，当前不能只靠 `http2 on;` 启用 HTTP/2。Vite
开发代理下
Network 协议列为 `http/1.1` 是预期。

### 测试命令与结果

在仓库根目录：

```bash
node --test frontend/shared/tests/pdf-concurrent-range.test.mjs
node --test frontend/shared/tests/pdf-page-session.test.mjs
node --test frontend/pc-web/tests/pdf-progressive-viewer.test.mjs
node --test frontend/mobile-app/tests/learner-course-learning.test.mjs
pnpm --filter @safety/shared typecheck
pnpm --filter @safety-training/pc-web typecheck
```

| 命令                                            | 结果                                                       |
|-----------------------------------------------|----------------------------------------------------------|
| `pdf-concurrent-range.test.mjs`               | 5/5 通过                                                   |
| `pdf-page-session.test.mjs`                   | 9/9 通过                                                   |
| 上述两个文件合计                                      | 14/14 通过                                                 |
| `pdf-progressive-viewer.test.mjs`             | 通过，断言 `createPdfjsConcurrentRangeAdapter`                |
| `learner-course-learning.test.mjs` 中 PDF 相关用例 | 通过，断言 H5 接入并发适配器                                         |
| `@safety/shared typecheck`                    | 通过                                                       |
| `@safety-training/pc-web typecheck`           | 通过                                                       |
| 移动端标准 `typecheck`                             | 未作为本次结论；既有 `ignoreDeprecations` 配置问题                     |
| 真实登录态 Chrome/H5 Network 复核                    | 2026-08-18 人工确认通过；Range/206 请求已减少。HTTP/2（`h2`）仍取决于网关重新编译 |

真实样本脚本（需本地线性化 PDF 路径，本次文档化口径，未在本轮重新跑大文件）：

```bash
node frontend/shared/tests/pdf-linearized-range-smoke.mjs <linearized-pdf>
node frontend/shared/tests/pdf-range-transport-diagnostic.mjs <linearized-pdf>
```

后端 `QpdfRealProcessTest` 调用诊断脚本时传入 `262144`，门槛保持初始化 ≤ 2.5 MB、第一页 ≤ 3.5 MB、请求 ≤ 16 次。

长期记录：`docs/architecture/pdf-progressive-preview-optimization.md` §11～13。
