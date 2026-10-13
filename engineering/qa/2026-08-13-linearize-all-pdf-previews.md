# 所有 PDF 预览线性化 QA

日期：2026-08-13  
Owner：lims  
OpenSpec：`2026-08-13-linearize-all-pdf-previews`

## 范围

- 原生 PDF 异步生成 qpdf 线性化 PREVIEW。
- Office 转换 PDF 在登记前统一执行 qpdf 线性化。
- 历史 PDF 不扫描、不回补、不替换，既有预览事实保持原状。
- qpdf 配置、启动校验、部署说明与 OpenAPI 语义同步。

## TDD 证据

- 原生 PDF 旧同步完成断言先失败，随后改为 `DOCUMENT_PREVIEW/PENDING`。
- 新增 qpdf 命令、线性化器和 Provider 测试后先观察缺类或行为失败，再完成最小实现。
- 自审补充“进程输出不得泄露路径”和“上传失败清理对象”测试，确认 2 个行为用例失败后再修复通过。

## 已通过验证

- `mvn -pl sts-service-admin -am test`：596 个用例通过，0 failure、0 error、0 skipped。
- `QpdfRealProcessTest` 使用 `test/海康-Infovision+iWork-Safety+企业安全生产管理平台用户手册.pdf`：样本 64,617,755
  byte、648 页；真实 `/usr/bin/qpdf` 线性化后 `--check`、`--check-linearization`、页数一致性通过。
- qpdf 非零退出、输出无效、页数变化、缺少可执行绝对路径、临时目录清理、上传失败清理均有零 DB 测试覆盖。
- 原生 PDF 与 Office 两路均只上传最终 PREVIEW；原生 PDF 不调用 OnlyOffice。
- 代码、配置和任务类型中不存在历史 PDF 扫描或回补入口。

## 集成验证

全量回归：

```bash
mvn -pl sts-service-admin -am -Pintegration-test test
```

结果：189/189 通过，0 failure、0 error、0 skipped，包含真实 MySQL 与 MinIO Testcontainers 验证。

## 当前结论

新上传 PDF 线性化实现、零 DB、真实大 PDF qpdf、全量 MySQL/MinIO IT、OpenAPI 与 OpenSpec 校验全部通过；历史回补代码已移除，Change
可以关闭。

## 双轴 Review

- 规格符合性：通过。实现、OpenAPI、配置、部署说明与已确认 Task 1～2 一致，且未实施历史回补。
- 代码质量：通过。自审未发现越界重构、新生产依赖、权限变化或数据库结构变化；失败清理和进程执行边界已覆盖。
