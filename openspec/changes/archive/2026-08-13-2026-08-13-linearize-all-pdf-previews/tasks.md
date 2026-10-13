# Tasks：所有 PDF 预览文件线性化

> 状态：已确认（2026-08-13）。
>
> Owner：lims

## Task 1：完成新 PDF 的统一线性化预览链

- 状态：已完成
- Owner：lims
- 输入：已确认 proposal/design；既有 DOCUMENT_PREVIEW、OnlyOffice、PDFBox、MinIO、任务调度和 Range 文件代理
- 输出：qpdf 命令与执行封装、原生 PDF 异步任务、Office PDF 二次线性化、PREVIEW 派生事实、配置与部署说明
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：不新增 URL；同步 `docs/api/asset.openapi.json` 中原生 PDF 异步处理语义和示例
- 数据影响：复用素材、转码任务、派生文件和文件信息表；不新增 migration
- 验收标准：
  - [x] 原生 PDF 与 Office 转换 PDF 均无条件执行 qpdf 线性化，不按文件大小跳过
  - [x] 原生 PDF 创建后保持 `UPLOADED` 并创建 `DOCUMENT_PREVIEW/PENDING`，成功后才进入 `SUCCEEDED`
  - [x] qpdf 使用绝对路径、参数列表和受控超时，不经过 shell，输入输出只位于任务临时目录
  - [x] 最终 PDF 可读取、线性化校验通过且页数与输入一致，只登记最终 PREVIEW
  - [x] qpdf 缺失、非零退出、超时、无效输入和存储失败均进入稳定失败状态，不残留临时目录或未登记对象
  - [x] play-info/Range 权限和 URL 不变，新原生 PDF 不再直接回退源文件作为预览
  - [x] application profile、部署文档和 OpenAPI 同步，明确后端需安装 qpdf
  - [x] 按 TDD 完成零 DB 测试和受影响模块验证
- 测试方式：
  - 先修改/新增自动化测试并观察预期失败，再以最少实现通过
  - `mvn -pl sts-service-admin -am test`
  - 执行 `mvn -pl sts-service-admin -am -Pintegration-test test` 回归既有持久化与 MinIO 行为
  - 使用真实 qpdf 对普通 PDF 和大 PDF 执行 `--check-linearization` 冒烟
  - 校验 OpenAPI JSON/内部 `$ref`、`openspec validate --strict` 和 `git diff --check`

## Task 2：完成验收、Review 与记录同步

- 状态：已完成
- Owner：lims
- 输入：Task 1 的实现、测试与真机证据
- 输出：OpenSpec 验收状态、QA/Retro、进度表、部署证据和双轴 Review
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：复核 `docs/api/asset.openapi.json`
- 数据影响：无新增数据结构
- 验收标准：
  - [x] 新上传 PDF 有真实 qpdf 线性化证据，PDF.js/Range 访问链可复核
  - [x] 不存在历史扫描、回补任务或 PREVIEW 替换逻辑
  - [x] Admin 零 DB、相关 MySQL/MinIO IT、OpenAPI 和 OpenSpec 校验全部通过
  - [x] 规格符合性 Review 与代码质量 Review 结论均为“通过”
  - [x] QA、Retro、部署文档、tasks.md 和进度表证据一致
- 测试方式：执行 Task 1 的全部验证命令并记录实际结果；未运行项不得标记完成
