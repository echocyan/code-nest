---
name: codex-subagent
description: "通过 Herdr 把 Codex 当作 subagent 调度：新开 tab 启动 Codex、委派任务、收取报告、关闭 tab。仅在明确要求把任务交给 Codex 时使用。需要 HERDR_ENV=1。"
---

# Codex subagent

把本机 Codex CLI 当作 subagent：每个 Codex 独占一个 Herdr tab，主 agent 委派任务、收取报告，报告取完即关闭 tab。Codex
在这里是审查者、调研者，不改仓库。

先加载 `herdr` skill：命令语法、ID 解析、agent 状态、`blocked` 与超时的处理都以它为准，本 skill 只补充 Codex subagent 专属的约定。
`HERDR_ENV` 不为 1 时，告诉用户当前不在 Herdr 中，然后停止。

## 1. 启动

给每个 subagent 取名 `codex-<话题>`（如 `codex-review-spec`），同时用作 tab label 和 agent 名；须符合 herdr 的命名规则，且不与在跑的
agent 重名。同时在跑的 Codex subagent 最多 3 个。

```bash
herdr tab create --cwd "$PWD" --label codex-<话题> --no-focus
herdr agent start codex-<话题> --kind codex --pane <root-pane-id> -- -s workspace-write -a never --disable multi_agent
```

从 `tab create` 的返回中读取 `.result.root_pane.pane_id` 用于启动，读取 `.result.tab.tab_id` 留作收尾。

`-s workspace-write -a never` 让 Codex 能写工作区和临时目录（用于报告文件），且从不请求审批：命令失败直接返回给
Codex，它不会停在审批框上。`--disable multi_agent` 关闭 Codex 原生的 subagent，让它亲自完成任务。模型、推理强度等其余设置沿用
`~/.codex/config.toml`；用户明确指定时才在 `--` 之后追加参数。

完成标准：`agent start` 成功返回。

## 2. 委派

Codex 看不到主 agent 的对话，prompt 必须自包含：目标、相关文件路径或命令（如 diff 命令）、约束、期望的报告形式与篇幅。每个
prompt 开头表明身份："你是被派出执行此任务的 subagent，请亲自完成并直接回复报告"。
prompt 末尾附上"这是只读任务：保持仓库文件和 Git 状态原样"。

按手上的情况选择等待方式：

- **同步**：`herdr agent prompt <name> "<prompt>" --wait --timeout 600000`。
- **并行**：要派多个 subagent，或主 agent 有独立工作可做时，先对每个 agent 执行不带 `--wait` 的 `agent prompt`，全部提交后再逐个
  `herdr agent wait <name> --timeout 600000`。

超时只说明还没结束：继续 `agent wait` 同一个 agent，已提交的 prompt 只提交一次。

Codex 需要澄清时，会在回复里提问并结束本轮；主 agent 对同一 agent 名再 `agent prompt` 作答。同一话题的追问都复用这个
subagent，保留它的上下文。

## 3. 收取报告

1. `herdr agent read <name> --source recent-unwrapped --lines 400`，报告就是 Codex 在输入框之前的最后一条回复；前面的
   `Ran`、`Explored` 块是它的工具调用过程。
2. 读到的回复不完整时，追问一次：让 Codex 用 `mktemp -d` 新建目录，把完整报告写成其中的 Markdown 文件，只回复文件路径；然后读取该文件。

完成标准：拿到完整的报告正文。

## 4. 收尾

报告取完、不再追问后：删除步骤 3 中 Codex 创建的临时目录（如有），再 `herdr tab close <tab-id>`。

任务失败时同样收尾，然后向用户报告失败原因并停止，由用户决定下一步；调用方指定的是 Codex，换成其他 subagent 须经用户同意。失败包括
`agent start` 报错、`agent_prompt_stalled`、反复超时仍无结果。

唯一的例外是 `blocked`：保留 tab，把 Codex 的提问原样转给用户决定。
