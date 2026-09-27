# code-nest 实现票

源规格：[../code-nest/spec.md](../code-nest/spec.md)。决策细节以 `../code-nest/issues/` 下各决策票的 `## Answer` 为准。

阶段：
- **01–04**：工程骨架、认证、MQ 可靠性底座与计数系统。
- **05–17**：业务功能与各技术方案。
- **18–20**：压测。

通用要求（每张票都适用）：
- 行为测试走 HTTP 接口，用 Testcontainers。
- ArchUnit 规则保持为绿。
