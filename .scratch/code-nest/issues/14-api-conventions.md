# API 设计规范

Type: grilling
Status: open
Blocked by:

## Question

（背景：返回体、错误码分段、两种分页结构已在[工程结构与测试基础设施](02-project-structure.md)中定下；各接口的分页方式已由各业务票决定。）

对外 REST 接口的统一约定是什么：
- URL 风格：资源名用复数、路径前缀与版本号（如 `/api/v1`）、嵌套资源的写法（如 `/articles/{id}/comments`）。
- 点赞、收藏、关注这类"开关型"动作用哪种语义：`PUT`/`DELETE` 幂等表达，还是 `POST` 切换？
- 请求与响应的字段命名、时间格式、ID 统一为字符串。
- 分页参数命名。
- 各模块的错误码清单如何登记。
- 以及一份按模块列出的接口清单，作为 `to-spec` 的输入。
