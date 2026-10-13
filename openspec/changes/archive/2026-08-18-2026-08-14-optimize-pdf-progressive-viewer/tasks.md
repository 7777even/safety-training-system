# Tasks：优化 PC/H5 PDF 渐进预览

> 当前状态：Task 1～3 均已完成。Task 2 于 2026-08-18 经人工真实登录态复核，确认 Range/206 请求已减少。

## Task 1：完成 PC/H5 大 PDF 按页预览能力

- 状态：已完成（2026-08-18）
- Owner：AI（前端）
- 输入：已确认 proposal/design；现有 `pdfjs-dist`、受控 play-info/file URL、qpdf 线性化 PREVIEW、PC `AssetViewer` 与移动 H5
  `AssetPreview`
- 输出：PC/H5 专用 PDF.js 分页预览组件、加载/翻页/跳页/缩放/旋转/错误与资源清理能力；非 PDF 格式继续使用 OpenFileViewer
- 前后端影响：仅前端；复用现有 API Contract，不修改后端、权限或数据库
- API 依赖：`GET /api/asset/assets/{assetId}/play-info`、`GET /api/asset/files/{assetId}/{fileInfoId}`
- 数据影响：只读既有线性化 PREVIEW，不新增或修改业务数据
- 验收标准：
  - [x] PDF 直接通过受控 URL 和 Bearer 交给 PDF.js，保留 Range，不预下载完整 Blob
  - [x] 文档初始化只读取 `numPages` 和目标页，不按总页数循环 `getPage`
  - [x] DOM/Canvas 数量保持常量级，不创建全部页面 wrapper，不执行全页滚动坐标扫描
  - [x] PC 与 H5 支持上一页、下一页、页码跳转、缩放、旋转和受控降级入口
  - [x] 快速翻页、切换文件和卸载组件正确取消异步任务并释放资源
  - [x] 加密、损坏、网络、Range 和渲染失败均展示稳定中文状态
  - [x] App/非 H5 继续使用受控打开/下载，不误打包 DOM 预览分支
- 测试方式：按 TDD 先运行 PDF 控制器行为测试并观察失败；实现后运行 PC/H5 定向测试、`pdf-concurrent-range.test.mjs`、目标
  workspace typecheck、lint、PC build、移动 H5 build

## Task 2：完成课程 PDF 断点与大文件性能验收

- 状态：已完成（2026-08-18）
- Owner：AI（前端）
- 输入：Task 1 产物；现有课程 `initialPageNo/pagecount/pageupdate` 与学习会话上报；64.6 MB、648 页真实 PDF
- 输出：PC 课程页码恢复与上报接入、两端真实浏览器网络/渲染验收证据、无效第三方 patch 清理
- 前后端影响：仅前端；复用现有课程学习会话字段，不修改 API Contract
- API 依赖：Task 1 接口及 `POST /api/course/learning/sessions`
- 数据影响：仅按既有规则写入课程学习会话页码，不新增字段或表
- 验收标准：
  - [x] PC 初始页恢复、成功渲染后的 `pageupdate`、总页数和手动跳页保持现有契约
  - [x] 648 页线性化临时样本首屏前不读取全部文件，空闲时不持续获取后续页 Range
  - [x] 验收只使用已线性化 PREVIEW，不增加历史或非线性化 PDF 兼容测试
  - [x] 跳转第 300 页只触发目标页所需读取，快速翻页无旧结果覆盖
  - [x] 在带真实登录态的 PC Chrome/H5 中复核 Range/206、Canvas 数量和页面视觉结果
  - [x] 删除 patch 文件及 pnpm `patchedDependencies`，`pnpm install --frozen-lockfile` 成功
  - [x] 非 PDF 素材预览定向回归通过，未顺带修改素材权限或后端接口
- 测试方式：PC 课程学习定向测试、移动 PDF 分支测试、两端 typecheck/lint/build、Chrome/H5 Network 与 Performance 真实样本检查、双轴
  Review、QA/Retro

## Task 3：规范化 PREVIEW Page Tree 并完成大文件网络验收

- 状态：已完成（2026-08-17）
- Owner：lims
- 输入：Task 1～2 产物；后端已有 PDFBox 3.0.5、qpdf 线性化器；64.6 MB、648 页扁平 Page Tree 样本；
  `docs/architecture/pdf-progressive-preview-optimization.md`
- 输出：有界 Page Tree 规范化器、更新后的 PREVIEW 生成链、真实样本结构/Range 验收证据
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：无新增或变更；继续复用既有受控 PREVIEW/Range 接口
- 数据影响：不修改数据库和 SOURCE；只改变新生成或重新生成的派生 PREVIEW 文件结构
- 验收标准：
  - [x] 最大扇出 32，超过 1024 页时递归形成多层 `/Pages`，每层 `/Parent`、`/Count`、`/Kids` 正确
  - [x] 页面顺序、页数、页面内容、可继承属性、书签/注释页面引用保持一致
  - [x] 最终文件通过 PDFBox 重开、`qpdf --check` 和 `qpdf --check-linearization`
  - [x] 失败不上传半成品 PREVIEW，并清理全部中间文件
  - [x] 固定 648 页样本文档初始化不超过 2.5 MB、第一页渲染数据就绪不超过 3.5 MB、256 KB Range 请求不超过 16 次
  - [x] 不新增生产依赖，不修改 API、权限、数据库、对象存储和 Range 语义
- 测试方式：按 TDD 先构造扁平 Page Tree 失败用例；运行受影响后端零 DB 测试、真实 qpdf/PDFBox 样本测试、前端 Range
  诊断脚本；涉及真实持久化时才运行 IT，本 Task 预期无数据库 IT
