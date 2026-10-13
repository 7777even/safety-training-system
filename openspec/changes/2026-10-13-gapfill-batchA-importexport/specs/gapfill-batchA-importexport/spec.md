# 补漏 A 批：导入导出基础

## ADDED Requirements

### Requirement: 用户 CSV 导入导出
系统 SHALL 支持按单组织/多组织通过 CSV（Excel 兼容）批量导入与导出用户账号，导入时逐行校验组织归属、必填与重复并定位错误行，错误行不污染已写入行。

#### Scenario: 单/多组织导出
- WHEN 管理员在当前筛选（单组织或多组织选中）下导出用户
- THEN 生成 UTF-8 BOM CSV 且字段完整，仅含授权组织范围内账号

#### Scenario: 导入与错误反馈
- WHEN 管理员导入用户 CSV
- THEN 合法行写入，非法行（组织越权/必填缺失/登录名或手机号重复）返回可定位错误且不回滚已写入行

### Requirement: 考试名单文件导入
系统 SHALL 在考试安排中支持通过文件导入考生名单，等价于现有班级/人员选择。

#### Scenario: 导入考生名单
- WHEN 管理员上传考生名单文件
- THEN 复用现有考生写入链路生成 candidate；重复与非法行反馈，权限受考试编排范围约束

### Requirement: 培训人员批量导入与导出
系统 SHALL 支持培训成员的批量导入（joinSource=IMPORT）与名单导出；扫码加入列为本期延期子能力。

#### Scenario: 批量导入与导出
- WHEN 管理员批量导入成员或导出当前名单
- THEN 复用现有成员写入；导入重复加入幂等；导出字段完整

#### Scenario: 扫码加入延期
- WHEN 本期范围核验
- THEN 仅实现导入与导出；joinSource=QR 的扫码加入不在本期，不阻塞 38 导入/导出勾选
