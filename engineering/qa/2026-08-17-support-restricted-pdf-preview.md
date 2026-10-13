# QA：支持权限受限 PDF 的派生预览

日期：2026-08-17

OpenSpec：`2026-08-17-support-restricted-pdf-preview`

## 范围

- 空用户密码、禁止修改/组装的 PDF 生成派生 PREVIEW。
- SOURCE 保持加密，PREVIEW 仅在派生链中移除内部保护。
- 非空打开密码安全失败。
- PDF 规范化底层异常保留及日志敏感信息清洗。
- 2517 页真实 PDF 的完整 PDFBox/qpdf 生成链。

## TDD 记录

Red：

- `PdfPageTreeNormalizerTest` 新增权限受限 PDF 和 cause 断言后，6 个用例中 1 个错误、2 个失败：空密码加密 PDF 在保存时得到“PDF
  页面树规范化失败”，异常无 cause。
- `TranscodeJobProcessorTest` 新增安全诊断测试后，因三参数异常构造器和 `diagnostic` 不存在而编译失败。
- 增加 `Authorization: Bearer` 样本后，测试观察到 `auth-secret` 未被清洗。

Green：

- PDFBox 成功打开加密文档后，仅为派生保存调用安全保护移除能力。
- `TranscodeFailureException` 保留底层 cause；任务处理器记录带任务/素材/失败码的清洗后异常栈。
- 清洗覆盖 Token、Secret、Password、Access Key、Authorization 和 URL 查询参数。

## 自动化结果

1. 定向零 DB：

   ```text
   mvn -pl sts-service-admin -am \
     -Dtest=PdfPageTreeNormalizerTest,PdfLinearizerTest,DocPreviewProviderTest,TranscodeJobProcessorTest,FfmpegConfigurationTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   结果：23 个用例，0 失败，0 错误。

2. Admin 全量零 DB：

   ```text
   mvn -pl sts-service-admin -am test
   ```

   结果：620 个用例，0 失败，0 错误，1 跳过。跳过项是未配置外部样本时的可选真实进程测试。

3. 真实权限受限 PDF：

   使用 `restrictedPdfSample` 指定用户提供的 63.86 MiB、2517 页样本，并设置
   `restrictedPdfExpectedPages=2517` 运行
   `QpdfRealProcessTest#linearizesConfiguredRestrictedPdfWithoutChangingSourceProtection`。

   结果：1 个用例通过，约 55.97 秒；SOURCE 的 SHA-256、大小和加密状态保持不变且仍为 2517 页，PREVIEW 不加密且为 2517 页；
   `qpdf --check` 和 `qpdf --check-linearization` 均通过。

## 未运行项

- 未运行数据库 IT：本次不涉及 Entity、Mapper、自定义 SQL、schema、索引、事务或数据库结构。
- 未修改 API Contract、系统权限、对象存储访问策略或前端，因此无对应契约、权限或前端构建项。

## 结论

通过。Task 1 的自动化、真实样本、SOURCE/PREVIEW 保护边界及安全诊断验收均满足 OpenSpec。
