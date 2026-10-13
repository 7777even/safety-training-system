# Tasks：综合结果 / 档案 / 证书后端（M4）

前后端影响：仅后端；前端以 OpenAPI 为准另开。Owner：lims

## Task 1：RESULT-01 问卷定义 / 投放 / 作答 / 统计

- 状态：已完成
- Owner：lims
- 输出：survey migration + `com.sts.survey` + 零 DB/IT
- 验收标准：publish 不可变版本；instance 投放；submit 防重复；stats 最小可用
- [x] migration survey tables
- [x] Survey Service/Controller
- [x] 零 DB + IT

## Task 2：RESULT-02 评价记录

- 状态：已完成
- Owner：lims
- 输出：evaluation_record / record_item migration + API
- 验收标准：基于 form_version 提交；已提交不可覆盖
- [x] migration record tables
- [x] Record Service + 测试

## Task 3：RESULT-03 综合成绩计算批次

- 状态：已完成
- Owner：lims
- 输出：calculation_run / source；calculate/confirm；回写 participant
- 验收标准：不可变批次；正式唯一；替代重算
- [x] migration calculation tables
- [x] Calculation Service + 测试

## Task 4：ARCHIVE-01 三级档案与归档版本

- 状态：已完成
- Owner：lims
- 输出：archive_* tables + API（期次主视角；个人/机构为查询投影）
- 验收标准：从计划归档；版本 hash；不可覆盖
- [x] migration archive
- [x] Archive Service + 测试

## Task 5：CERT-01 / CERT-02 证书模板与签发

- 状态：已完成
- Owner：lims
- 输出：certificate tables + template/rule/issue/revoke/reissue
- 验收标准：幂等签发；撤销留痕；重发新号
- [x] migration certificate
- [x] Certificate Service + 测试

## Task 6：RESULT-04 OpenAPI / 权限 / 进度 / QA

- 状态：已完成
- Owner：lims
- 输出：OpenAPI、menus、进度表 M4 关闭、QA
- [x] survey/evaluation-result/archive/certificate openapi
- [x] menus + docs 索引
- [x] 进度表 + QA
- [x] 目标测试通过
