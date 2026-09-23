# code-nest 实现票

源规格：[../code-nest/spec.md](../code-nest/spec.md)。决策细节以 `../code-nest/issues/` 下各决策票的 `## Answer` 为准。

阶段：
- **01–09**：基线版业务，不依赖 MQ。
- **10–20**：底座与各亮点的优化档。
- **21–23**：压测。

通用要求（每张票都适用）：
- 行为测试走 HTTP 接口，用 Testcontainers。
- 引入模式开关的票，要让相关的已有 HTTP 测试在每一档下都通过。
- ArchUnit 规则保持为绿。
