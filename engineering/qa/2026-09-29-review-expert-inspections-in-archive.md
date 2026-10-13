# 专家查课结束后回看与一期档案 QA

## 范围和证据

- OpenSpec：`2026-09-29-review-expert-inspections-in-archive` Task 1～2。
- 已结束培训工作台保留专家查课弹窗，组件本身仍只允许 `RUNNING` 提交。
- 一期档案接口返回已提交课程记录的摘要和逐项答案；企业/个人档案不返回该字段。更新一期档案后，新 PDF 包含同一组专家查课事实，历史文件保持原样。
- `archive.openapi.json`、PC 类型及一期页面同步。

## 自动化

- Red：`ArchiveServiceImplTest` 新增档案专家记录断言后，旧实现因缺少构造参数/字段失败。
- Green：`mvn -pl sts-service-admin -am '-Dtest=ArchiveServiceImplTest,ArchivePdfRendererTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：19/19 通过。
- `pnpm --filter @safety-training/pc-web typecheck`：通过。
- 受影响 PC 文件定向 ESLint、OpenAPI JSON 解析和 `git diff --check`：通过。
- `openspec validate 2026-09-29-review-expert-inspections-in-archive --strict`：通过。

## 双轴 Review

- 规格符合性：复用现有专家提交与查询规则，已结束计划仅回看；一期档案新增字段和 PDF；综合分不读取专家分数；无迁移、无新权限。
- 代码质量：档案事实按当前计划绑定版本的已提交记录组装，项目名称取版本快照；企业与个人视角字段为 null 且不序列化；未改动已有归档事实口径。

## 待真实验收

1. 管理员打开已结束、已绑定且有提交记录的计划，确认入口可见、逐项答案可查看、填写按钮不可见。
2. 一期档案核对课程、版本、评价人、时间、总分、摘要和逐项答案；无提交记录时显示空态。
3. 更新档案后下载新 PDF，核对相同事实；已有旧 PDF 仍按原文件下载。
4. 企业与个人档案响应不含 `expertInspections`，且页面不出现专家意见。

本轮尝试读取浏览器会话，但浏览器清单返回 `nodeRepl.fetch request failed`，未取得可用登录态；因此不宣称浏览器或真实下载验收通过。
