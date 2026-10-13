# Tasks：开考类型由服务端派生

> 状态：已完成。Owner：lims。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成开考写路径状态派生

- [x] 1.1 完成开考写路径状态派生

- **状态**：已完成
- **Owner**：lims
- **输入**：design D1、D2。
- **输出**：`start()` 忽略请求类型；终态复用；仅内部补考创建 `RETAKE`；可重复练习派生 `PRACTICE`。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：无新路径。
- **数据影响**：无 DDL。
- **验收标准**：
  1. 正式考试/培训考试/培训题库终态后再 start 不 insert。
  2. 请求 `FIRST` 不能创建第二份首考。
  3. `/retake` 仍可创建 `RETAKE`。
  4. 课程题库无进行中时可再开 `PRACTICE`。
- **测试方式**：Red → Green 扩展 `ExamAttemptServiceImplTest`。

## Task 2：完成培训与课程入口及契约说明

- [x] 2.1 完成培训与课程入口及契约说明

- **状态**：已完成
- **Owner**：lims
- **输入**：design D3、D4。
- **输出**：培训 enter/兼容开考与 Adapter 不再把客户端类型当授权；课程考核不再 setAttemptType；OpenAPI 标明忽略。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：既有 start/enter/retake，仅改 description。
- **数据影响**：无。
- **验收标准**：
  1. 端口 `startAttempt` 不再接收 `attemptType`。
  2. 课程开考请求不声明类型。
  3. exam/training OpenAPI 写明服务端派生。
- **测试方式**：Red → Green 扩展培训/课程零 DB；`git diff --check`。

## 完成门槛

1. 规格符合性：状态机在后端；无新 API/权限/DDL。
2. 代码质量：无前端改动；补考接口行为保持。
