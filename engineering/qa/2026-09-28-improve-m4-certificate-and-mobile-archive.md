# M4 双端证书展示与移动档案文案 QA

关联 Change：`2026-09-28-improve-m4-certificate-and-mobile-archive`。记录时间：2026-09-28。

## 开发侧验证

- 后端 `CertificateServiceImplTest,CertificateControllerTest`：10/10 通过，覆盖课程、考试、培训三类来源与缺失来源名称。
- PC `vue-tsc --noEmit` 与目标文件 eslint：通过。
- 移动端 `vue-tsc --noEmit`、目标文件 eslint、`learner-outcome.test.mjs` 3/3、H5 build：通过。
- 新 Change `openspec validate --strict`：通过；`git diff --check`：通过；OpenAPI JSON 可解析。

## 双轴 Review

- 规格符合性：证书列表仅增加可空 `sourceName`，图片仍由本人受控文件端点提供；移动档案未改变结果计算或权限。旧移动端 Change 的三处展示约束已由本 Change 的人工确认目标覆盖。
- 代码质量：来源按本页和类型批量查询，无逐证书查询；双端图片失败保留占位；PC Blob URL、移动端可撤销临时 URL 在换页或卸载时释放；现有未提交工作树变更保留。

## 尚待真实验收

- 当前没有在本轮取得可用的真实学员登录态及运行后端，未声称完成浏览器或原生端的图片、下载、越权与期次跳转验收。
- 需核对截图所用环境是否已部署 `trainingTypeName` 回填。四期均显示旧「未分类」，不能仅据截图断定四期未配置培训类型。
- 来源名称取当前主数据；来源改名会改变列表标题，已签发证书 PNG 原件不变。若业务需要签发时名称永久留存，应另确认历史快照口径。
