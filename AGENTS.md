# AGENTS.md

## 原则

- 简洁优先：遵守 KISS/YAGNI/fail-fast，避免无必要抽象
- 注释：编写中文注释，遵循 Javadoc 规范
- 提交信息：Conventional Commits 格式，描述和正文使用简体中文，类型和作用域保留英文
- 提交范围：每次提交只包含一个逻辑变更
- 文档同步：文档需与想法、决策和代码保持同步，只写当前状态：修改时就地改写成最新结论，不留旧值、改动来由或确认过程；内容遵循所在文档的体裁和要求

## 测试

- Do not backfill tests after implementing business code.
- Tautological tests considered harmful.
- Change-detector tests considered harmful.
- Do not create regression tests for bug fixes without a genuine gap in behavior testing.

## Agent skills

### Issue tracker

Issues and specs live as local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
