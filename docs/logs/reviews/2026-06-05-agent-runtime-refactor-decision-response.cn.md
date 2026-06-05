---
affects_level: L1
affects_view: logical
proposal_status: decision-response
authors: ["Chao Xing", "Claude"]
responds_to: docs/logs/reviews/2026-06-05-agent-runtime-sdk-five-module-refactor-proposal.cn.md
addressed_to: x00209170 <xiaom2008@foxmail.com>
proposes_retiring_adrs: [ADR-0072, ADR-0073, ADR-0158]
related_proposals:
  - docs/logs/reviews/2026-06-04-agent-examples-a2a-runtime-registry-facade-proposal.cn.md
  - docs/logs/reviews/2026-06-05-agent-sdk-yaml-to-openjiuwen-handler-proposal.cn.md
  - docs/logs/reviews/2026-06-05-agentsdk-scenario-agent-and-deploy-layering-proposal.cn.md
related_rules: [R-D, R-F, R-G, R-K]
affects_artefact: []
---

# 决策回执 — agent-runtime 五层重构提案（致 Doc 2 作者）

> **Date:** 2026-06-05
> **Status:** Decision Response / 决策已锁定，待作者知悉后据此修订 Doc 2
> **致:** `x00209170 <xiaom2008@foxmail.com>`（《agent-runtime 五层重构实施方法》作者）
> **回应:** `docs/logs/reviews/2026-06-05-agent-runtime-sdk-five-module-refactor-proposal.cn.md`（下称 **Doc 2**）
> **关联:** Doc 1（A2A 多 runtime 注册/网关门面，EuphoriaYan/Codex）、Doc 3（YAML→openjiuwen handler，yougq）、scenario-agent-and-deploy-layering（Chao Xing）

---

## 致作者

先说在前面：这份五层重构提案抓住了当前 `agent-runtime` 最真实的痛点——目录概念错位（`dispatch/dispatch` 双层嵌套、`bootstrap` 混装、`access/protocol/async` 伪协议、`engine/runtime` 与 `dispatch` 双 engine），并且明确提出"先做减法再实现""对齐 AgentScope Runtime Java 的能力组合""用一个能跑起来的 `RuntimeApp.run(host)` 入口收口"。这些判断是对的，**打包卫生这一半我们采纳**。

但我们把提案放到三方证据下做了一轮认真的对照评审——(1) 我们已接受的 AgentRuntime 基础定义；(2) **五个目标框架的真实源码**；(3) AgentScope-Runtime-Java 的真实实现——结论是：Doc 2 **作为 runtime 架构有三处不成立**，并牵出两个边界问题（AgentService 职责、异构对接）需要一并裁定。owner 已就这些形成锁定决策（**D1–D10**）。本文把决策、依据、证据、以及对 Doc 2 的**逐节修改要求**一次讲清，便于你直接据此修订。**本文不替你改稿**，等你知悉/确认后再进入修订与 ADR 阶段。

本文是什么、不是什么：
- **是**：一份带证据链的评审裁决 + 重建蓝本 + 逐节改写清单 + 开放问题。
- **不是**：对你工作的否定。三处必改里有两处（双模退役、§9 越界）其实印证了你"先做减法"的直觉，只是减/留的边界要调整。

---

## 0. 摘要（TL;DR）

**总裁决**：Doc 2 的**打包/分层卫生方向成立、值得做**；但 engine 设计、流模型、§9 中立 provider SPI 三处必须重做。

三处必改：
1. **engine 层**：Doc 2 把 engine 收缩为"`EngineWorker`→openjiuwen 派发"，并**隐式丢弃了现有双模引擎(GRAPH/AGENT_LOOP)+EnginePort**。裁定：双模/EnginePort **确为历史残留、明确退役**（见 §4.1 的证据），但 engine 要**参考 AgentScope-Runtime-Java 重建**（`Runner` + `AgentHandler` + `StreamAdapter`/`MessageAdapter`），**不是**收缩成单框架派发器。
2. **流模型**：`java.util.stream.Stream<AgentResponse>` 是阻塞拉取式，**改响应式 `Flux`/`Flow.Publisher`**——对齐参考实现、对齐我们旧 EnginePort 的 `Flow.Publisher`、满足 Rule R-F/R-G 非阻塞 I/O 硬约束（见 §4.2）。
3. **§9 的中立 provider SPI**（`ToolProvider`/`McpToolProvider`/`MemoryProvider`/`StateProvider`/`SandboxProvider`，以及"不用 middleware 作为顶层概念"）：跨五框架验证后确认**越界**，必须删除 / 下沉到各框架 adapter（见 §4.3、§5）。

两个边界裁定：
- **D9 — AgentService = 纯控制面编排器**：中心化部署态用来**启动/注册/路由/治理**独立 runtime 实例，serving 委托给各 runtime。**不要把 AgentScope 的 web/app 搬进 AgentService**（见 §4.8、§8）。
- **D10 — adapter 分两类**：进程内 Java 库适配 + **远程协议适配**（**Dify=REST+SSE**、MCP）；中立 `AgentHandler` seam 对两类都成立（见 §4.9、§9）。

锁定决策清单见 §3，逐条依据见 §4，逐节改写要求见 §10。

---

## 1. 评审方法与证据基线

结论建立在真实源码上，不是空谈。三方对照 + 一次关键 grep 验证：

**(a) 我们已有的 AgentRuntime 基础定义**（代码 + ADR）。要点：
- 活跃执行路径：`A2aJsonRpcController`(POST `/a2a` + SSE) → `AccessSubmissionService` → `dispatch.EngineDispatcher` → `dispatch.spi.AgentHandler`（`agentId/isHealthy/execute(AgentExecutionContext):Stream<?>/resultAdapter`）→ `dispatch.adapter.openjiuwen.*` → 输出经 `A2aOutputRegistry`/`DefaultNotificationPort` SSE 回流。
- 并存的"设计岛"：`agent-bus/spi/engine/*`（`EnginePort`=`Flow.Publisher<AgentEvent> execute(...)`、`AgentEvent` sealed Finished/Failed/InterruptRequest、`RunMode{GRAPH,AGENT_LOOP}`、`ExecutorDefinition` sealed、`SuspendSignal`、`RunContext`/`ExecutionContext`、`Orchestrator`）+ `agent-runtime/engine/*`（`ExecutorAdapter`/`GraphExecutor`/`AgentLoopExecutor`/`EngineRegistry`/`InProcessEnginePort`/`SequentialGraphExecutor`/`IterativeAgentLoopExecutor`），由 ADR-0072/0073/0158 + ADR-0020(RunStatus DFA) 定义。

**(b) 五个目标框架真实源码**（`D:\ai-research\agent-platforms-survey\`）：`agent-core-java`(openjiuwen 0.1.12)、`agentscope-runtime-java`、`agentscope-java`、`spring-ai-alibaba`、`langchain4j`、`langgraph4j`(HEAD 4206519)。

**(c) Doc 2 / Doc 1 / Doc 3 三份提案本身。**

**关键 grep 验证**：`EnginePort`/`ExecutorAdapter`/`GraphExecutor`/`AgentLoopExecutor`/`EngineRegistry`/`RunMode`/`SuspendSignal` 的引用**只出现在** `agent-bus/spi/engine/*`、`agent-runtime/engine/*`、`agent-middleware`、以及测试里——`access/`、`dispatch/`、`agent-service/`、`examples/` **全都不引用**。即双模引擎 + EnginePort 是一块**从未接进活跃 A2A 路径**的设计岛。这恰好支撑"可安全退役"的裁定（D1）。

---

## 2. 对 Doc 2 的总体裁决

**采纳（打包卫生方向）**：
- 删除 `access/protocol/async` 伪协议（确无真实北向 async protocol）。
- 拆掉 `dispatch/dispatch` 双层嵌套、合并 `engine/runtime` 与 `dispatch` 的双 engine 命名错位。
- `queue` 做成无业务类型的能力层，由各模块内部建队列、不跨层传队列实例。
- `RuntimeSession ≠ AgentSession` 的类型隔离（runtime 外部连续会话 vs 框架内部 agent session），且 `RuntimeSession` 只存 ref、不存状态本体。
- `session` 不建 `store/` 子包、接口先行（`RuntimeSessionRepository` + `InMemory` 首版）。
- `app` 运行入口对齐 AgentScope `AgentApp.run(...)`，`RuntimeApp/RuntimeHost` 纯 Java、Spring Boot 依赖只落在具体 host 实现内。
- 把历史设计 md 移出源码目录（本轮已执行：`agent-runtime/.../2026-05-30-...access-layer-design.md` 已删）。

**不成立（须重做）**：engine 收缩成单框架派发器、阻塞流、§9 中立 provider SPI/"无 middleware"。详见 §4。

---

## 3. 锁定决策（D1–D10）

| # | 决策 |
|---|---|
| **D1** | engine 退役双模引擎(GRAPH/AGENT_LOOP)+EnginePort（历史残留、从未接线），**参考 AgentScope-Runtime-Java 重建**。 |
| **D2** | **单产物 + 可换 `RuntimeHost`(≈AgentScope `DeployManager`)**：`LocalA2aRuntimeHost`=源码SDK托管；spring-boot-starter+容器=微服务；二者共用同一 `Runner(AgentHandler)` 内核。 |
| **D3** | **中立 seam 仅在 I/O 边界**：`AgentHandler`(`getFrameworkType`/`start`/`stop`/`isHealthy`/`streamQuery`→框架原生流/`getStreamAdapter`/`getMessageAdapter`) + `StreamAdapter`/`MessageAdapter` + 中立 schema + **中立取消/config**。 |
| **D4** | **流模型响应式** `Flux<Event>`/`Flow.Publisher`；各 adapter 负责把框架原生流桥接到中立流。 |
| **D5** | **多框架**：`getFrameworkType()` + `FrameworkAdapterRegistry` + `adapters/<framework>/`；**v1 仅 openjiuwen 完整实现**，seam 按五框架预留。 |
| **D6** | **tool/skill/memory/MCP/中间件 = 框架内部**，由各 adapter 用框架原生机制配置；**不上 runtime 中立 SPI**（删 Doc 2 §9 五个 provider 中立 SPI）。 |
| **D7** | **基础设施服务**(Sandbox/State/SessionHistory/Memory) v1 保留最小一组，按 AgentScope `ComponentProvider` 模型作**不透明注入**给 adapter，**不是** agent 拉工具的中立 SPI。 |
| **D8** | **治理/可观测**骑在中立 `Event` 流(status/sequence/error)+handler 生命周期上做 tenant/预算/审计；**不**做中立中间件框架、**不**钩各框架内部 rail。 |
| **D9** | **AgentService = 纯控制面编排器**（中心化部署态）：启动/注册/路由/治理独立 runtime 实例，serving 委托各 runtime；**web/app 留在 agent-runtime，不搬进 AgentService**。 |
| **D10** | **adapter 分两类**：(a) 进程内 Java 库适配；(b) **远程协议适配**（Dify=REST+SSE、MCP）。中立 `AgentHandler` seam 对两类都成立。 |

---

## 4. 决策详解（依据 + 避免的失败模式）

### 4.1 engine 重建、退役双模/EnginePort（D1）

**事实**：Doc 2 §9 把 engine 描述为"`EngineExecutionApi.submit`→`EngineWorker` 内部队列→`AgentRuntimeHandlerRegistry.find(agentId)`→`handler.execute`→回写 `TaskControlApi.onEngineXxx`/`AccessOutputApi`"，全文**未提**现有的 `GraphExecutor`/`AgentLoopExecutor`/`ExecutorAdapter`/`EnginePort`/`SuspendSignal`，只笼统说"删除旧设计预设/旧 ADR 约束"。

**问题**：这把"engine"这个词重新指给了当前的 `dispatch/`（openjiuwen 派发），而把当前真正叫 `engine/` 的双模契约**默默丢弃**。一个评审者无法判断双模是被**有意退役**还是**没注意到**；而 ADR-0072/0073/0158 仍是"已接受 L1"，无 superseding ADR 就删其实现是治理缺口。

**裁定**：双模/EnginePort **确为历史残留**（§1 grep 证明从未接进活跃路径，砍掉成本低），**明确退役**，并补 superseding ADR（§11）。但 engine 不是收缩成单框架派发器，而是**参考 AgentScope-Runtime-Java 重建**：`Runner`(包一个 `AgentHandler`，产出 `Flux<Event>`) + `AgentHandler`(带 `getFrameworkType`) + `StreamAdapter`/`MessageAdapter` + `adapters/<framework>/`。蓝本见 §6，目标树见 §7。

**避免的失败模式**：(a) 治理上"未经 ADR 删除已接受契约"；(b) 架构上把 runtime 退化成"openjiuwen 专用派发器"，丧失多框架能力。

### 4.2 流模型改响应式（D4）

**事实**：Doc 2 §5.3/§9 的 `AgentResponse` 流与 `handler.execute` 用 `java.util.stream.Stream`。

**问题**：`java.util.stream.Stream` 是**阻塞、拉取、一次性**的，无法良好建模 SSE 背压、异步 token 流、首 token SLA。这与三件事冲突：(1) 参考实现 AgentScope 全链路用 Reactor `Flux`；(2) 我们旧 EnginePort 本就用 `Flow.Publisher`；(3) **Rule R-F/R-G** 把 client streaming / cursor flow / 非阻塞外部 I/O 列为硬约束（Doc 1 §13 也引用了）。

**裁定**：handler/engine/access 全链路改响应式 `Flux<Event>`/`Flow.Publisher`；各 adapter 把框架原生流桥接到中立流（桥接的必要性见 §4.3 的五种原生流）。

### 4.3 中立 seam 仅在 I/O 边界 + 删 §9 provider SPI（D3/D6）—— 异构证据

这是最重要的一条，直接回应 owner 的红线："**Runtime 不能给 openjiuwen 定制**。"

跨**五框架真实源码**对照——它们的 agent 执行 / tool / 中间件 / memory / 流式模型**互不兼容、却各自完备**：

| 维度 | openjiuwen-core | agentscope-java | spring-ai-alibaba | langchain4j | langgraph4j |
|---|---|---|---|---|---|
| Agent/执行 | `ReActAgent`/`DeepAgent`+`Runner` | `Agent`/`CallableAgent`/`StreamableAgent` | `ChatClient`+Graph(`CompiledGraph`) | `AiServices`+声明式(`Sequence/Loop/Parallel/Supervisor/Planner`) | `StateGraph`/`CompiledGraph` |
| **流式入口** | `Iterator<Object>` | `Flux<Event> stream(Msg)` | `Flux<ChatResponse>` | `TokenStream`（**回调注册** `onPartialResponse/onToolExecuted/...`） | `AsyncGenerator.Cancellable<NodeOutput<State>>` |
| Tool | `Tool` 抽象类+`ToolCard` | provider 格式化 ToolDTO | `ToolCallback` | `@Tool`+`ToolSpecification` | 复用 langchain4j/spring-ai |
| 中间件 | `AgentRail`/`CallbackFramework` | `Hook`/`HookEventType`/`StreamingHook` | `Advisor` 链 | agent listener/invocation handler | graph node |
| MCP | `McpClient`(动态) | — | `McpToolCallback` | `McpClientAgent` | — |
| Memory | `LongTermMemory` 单例 | `InMemoryMemory`/`LongTermMemory` | chat memory | `ChatMemory` | graph state |

**五种原生流式范式**（响应式推 ×2 / 回调注册 / 拉取迭代器 / 自定义异步生成器+取消）就足以定论：任何 runtime 层的中立 `ToolProvider`/`McpToolProvider`/中间件框架，**要么沦为谁都套不上的最小公约数，要么被某一个框架带偏**。

**裁定**：唯一稳健的中立 seam 是 **I/O 边界**——`AgentHandler`(getFrameworkType/streamQuery→框架原生流/getStreamAdapter/getMessageAdapter) + 中立 `Event`/`Message` schema + 中立取消/config。tool/skill/memory/MCP/中间件**一律下沉到 `adapters/<framework>/`，用各框架原生机制配置**。这正是 AgentScope-Runtime-Java 的做法（其 `AgentHandler.getFrameworkType()` 已枚举 "AgentScope/Spring Ai Alibaba/Langchain4j"——它本就是为这种异构设计的）。

**避免的失败模式**：runtime 被 openjiuwen 的 `Tool`/`Rail`/`McpClient`/单例 memory 带偏，导致 agentscope/spring-ai-alibaba/langchain4j/langgraph4j 接不进来。

### 4.4 多框架接缝 + adapter 注册表（D5）

加 `getFrameworkType()`（返回框架类型）+ `FrameworkAdapterRegistry`（按框架类型路由）+ `adapters/<framework>/` 目录约定。**v1 只实现 openjiuwen** 一个完整 adapter，但接缝从一开始就为 agentscope-java / spring-ai-alibaba / langchain4j / langgraph4j / **dify** 五类预留（目录可留空/缓建）。我们现有的 `AgentHandlerRegistry`(按 agentId) 保留，与 framework-type 维度正交。

### 4.5 基础设施服务保留为不透明注入（D7）

注意区分两类东西：(a) **框架自带**的 tool/middleware/memory —— 归 adapter（D6）；(b) **runtime 可提供**的基础设施 —— 沙箱执行环境、状态存储、会话历史持久化、记忆后端。后者 v1 保留**最小一组**（`engine.service.{SandboxService,StateService,SessionHistoryService,MemoryService}`），按 AgentScope `ComponentProvider` 模型作**不透明注入**给 adapter——adapter 决定要不要用、怎么用，runtime 不规定 agent 如何"拉工具"。

### 4.6 治理/可观测骑中立 Event 流（D8）

企业 runtime 需要的 tenant 隔离 / 预算 / 审计 / 限流，**骑在中立 `Event` 流(status/sequence/error)+handler 生命周期上**实现——观测的是 runtime 边界的中立事件，**不**钩入各框架内部 rail（那会再次把 runtime 绑死到某框架）。runtime **不**重造一套中间件框架（各框架自带）。

### 4.7 单产物 + 可换 Host / 两种部署模式（D2）

同一个 `Runner(AgentHandler)` 内核 + 可换 `RuntimeHost`（≈AgentScope `DeployManager.deploy(Runner)`）即同时支撑两种部署模式。端到端见 §8。

### 4.8 AgentService = 纯控制面编排器（D9）

见 §8。要点：web/app 是 runtime 实例固有的自我服务层，**留在 agent-runtime**；AgentService 在中心化态只做控制面（启动/注册/路由/治理），把 serving 委托给各 runtime；**不吸收** web/app。

### 4.9 adapter 两类 + Dify 远程适配（D10）

见 §9。要点：adapter 不止是"进程内 Java 库适配"，还包括"**远程协议适配**"（Dify=REST+SSE、MCP）；这反过来证明中立 seam 必须保持 I/O 边界中立。

---

## 5. openjiuwen-core 0.1.12 可实现性严审（详）

owner 要求结合 openjiuwen 真实源码挑战 Doc 2 §9.4（不用 middleware 顶层概念）与 `ToolProvider`/`McpToolProvider`。**注意定位**：下面这些 openjiuwen 细节**不是**要 runtime 去"对齐/吸收"（那就成了为 openjiuwen 定制，违反红线）——而是用来证明 **§9 的中立 SPI 既对不上 openjiuwen、又会越界绑死框架，所以应当下沉到 openjiuwen adapter 内部、对 runtime 不可见**。文件相对 `agent-core-java/src/main/java/com/openjiuwen/`。

**严审-1 ｜ §9.4「不用 middleware 作为顶层概念」站不住**：openjiuwen 执行模型深度构建在 middleware/rail/callback 上——
- `core/singleagent/rail/AgentRail.java`：抽象 `AgentRail` 把 `tools(List<ToolCard>)` + `skills(List<Object>)` + `beforeInvoke/afterInvoke` 钩子 + `priority` **捆在一起**。即 openjiuwen 里 tool/skill **不是独立 provider，而是挂在 rail/中间件上的**并带生命周期拦截。
- `core/runner/callback/`：`CallbackFramework`(`register`/`registerSync`/`CallbackChain`/`CircuitBreakerFilter`/`EventFilter`/namespace/metrics) + `HookType` 枚举——Runner 级事件/钩子总线，**自带熔断与过滤**。
- `deepagents/middlewares/ContextEngineeringMiddleware.process(context)`、`TaskPlanningMiddleware`——**DeepAgent（Doc 3 第一版目标之一）就是由 middleware 组成的**。
- `core/security/guardrail/*`、`core/context/processor/ContextProcessor`(+压缩/卸载器)、`harness/rails/{BudgetRail,SecurityRail,...}`。

含义：Doc 2 的 service/provider 只覆盖"agent 拉取的资源"，覆盖不了"横切拦截"（模型/工具前后、护栏否决、预算熔断、审计）。**但解法不是给 runtime 加一套中立中间件框架**（那会被 openjiuwen 带偏，也接不进 agentscope 的 `Hook`、spring-ai 的 `Advisor`、langchain4j 的 listener）——而是 D8：横切治理骑中立 Event 流；openjiuwen 的 rail/callback 留在 openjiuwen adapter 内。

**严审-2 ｜ `ToolProvider` 与 openjiuwen `Tool` 阻抗失配**：`core/foundation/tool/Tool.java` 是**抽象类**，绑 `ToolCard`(Map 形 `inputParams`)，`invoke(Map inputs, Map kwargs) throws Exception` + **`stream(...)→Iterator<Object>`（工具可流式）**；注解式 `@ToolDefinition`+`function/AnnotatedToolFactory`；执行在 agent 内 `operator/tool_call/{ToolRegistry,ToolExecutor}`+`runner/resourcemanager/ToolMgr`+`singleagent/AbilityManager`；内建**工具级中断/HITL**(`singleagent/interrupt/{ToolCallInterruptRequest,ToolInterruptException,ToolInterruptionState}`，`ReActAgent` 直接引用)。Doc 2 §9.5 `ToolProvider{describe():List<ToolDescriptor>; invoke(ToolInvocation):ToolResult}` 失配：多工具/单 provider vs 一工具/一实例；**流式工具丢失**；无类型 Map+Exception vs 类型化 ToolResult，第二个 `kwargs` 无处放；**工具级中断无法建模**；旁路注解路径。→ 这些都该是 openjiuwen adapter 内部的事，不上 runtime SPI。

**严审-3 ｜ `McpToolProvider` 形态错误**：`core/foundation/tool/mcp/` 里 `McpTool extends Tool`，构造 `McpTool(McpClient, McpToolCard)`；`McpClient`(接口)+`McpServerConfig`(超时)+`client/AbstractHttpMcpClient`(HTTP)。**MCP 工具靠连接服务器动态发现**，不连不能列。Doc 2 把 `McpToolProvider` 当成与 `ToolProvider` 同形的静态 describe/invoke SPI——`describe()` 不连接根本列不出工具（Doc 3 自己也说"MCP 第一版只解析配置、不主动拉远端 schema"，恰好反证形态错误）。→ MCP 是"远程协议适配"的一类（D10），由 adapter 持 server-config + client 生命周期。

**严审-附加 ｜ Memory 单例**：`ReActAgent` 内 `LongTermMemory.getInstance().setScopeConfig(new MemoryScopeConfig()...)`——openjiuwen 记忆是**进程级全局单例 + scope 配置**。Doc 2 可注入的 per-runtime `MemoryService/MemoryProvider` 与单例冲突，且多租户隔离危险（记忆全局共享）。→ openjiuwen adapter 须每 run 设置 scope 并裁决租户隔离；这正是"框架内部"的典型。

**严审小结**：§9 的五个中立 provider SPI 应全部删除/下沉；runtime 只保留 D7 的最小不透明基础设施服务。

---

## 6. 重建蓝本：AgentScope-Runtime-Java 真实接缝（含源码引用）

| 概念 | 文件（`agentscope-runtime-java/`） | 形态 | 映射到 agent-runtime |
|---|---|---|---|
| 多框架 handler SPI | `engine-core/.../adapters/AgentHandler.java` | `getName/getDescription/getFrameworkType()` + `start/stop/isHealthy` + `Flux<?> streamQuery(AgentRequest, Object)` + `getStreamAdapter()/getMessageAdapter()` + `getSandboxService()` | `engine.spi.AgentHandler` |
| 框架适配基类 | `engine-core/.../adapters/agentscope/AgentScopeAgentHandler.java` | `abstract implements AgentHandler`，`setSessionHistoryService/setStateService/setMemoryService/setSandboxService` 注入 | `engine.spi.AbstractAgentHandler` |
| 流/消息转换 | `adapters/StreamAdapter.java`、`adapters/MessageAdapter.java` | `Flux<Event> adaptFrameworkStream(Object)`；`List<Message> frameworkMsgToMessage(Object)` / `Object messageToFrameworkMsg(Object)` | `engine.spi.{StreamAdapter,MessageAdapter}` |
| 执行编排 | `engine-core/.../engine/Runner.java` | `Runner(AgentHandler)`，`init/start/stop/shutdown`，`Flux<Event> streamQuery(AgentRequest)`，`isHealthy/getSandboxService` | `engine.Runner` |
| 运行入口 | `web/.../app/AgentApp.java` | builder：`AgentApp(handler)`、`.host/.port/.stream/.responseType/.deployManager/.cors/.middleware`、`.query(framework, handler)`、`.run([host,]port)`；`StateServiceProvider/SessionHistoryServiceProvider/MemoryServiceProvider/SandboxServiceProvider` 注入 | `app.RuntimeApp` |
| 部署接缝 | `engine-core/.../engine/DeployManager.java` | `void deploy(Runner); void undeploy();` | `app.RuntimeHost` |
| SDK托管 host | `web/.../LocalDeployManager.java` | `deploy(Runner)` 用 `SpringApplicationBuilder` 起内嵌 Spring Boot(SERVLET)，`registerBean(Runner)`，按 protocol 暴露 A2A | `app.LocalA2aRuntimeHost` |
| 微服务自动装配 | `starters/spring-boot-starter-runtime-a2a/.../A2aAutoConfiguration.java` | 给 `AgentHandler` bean → 自动建 `Runner`/`DeployProperties`/`a2aProtocolConfig` | 独立 `agent-runtime-spring-boot-starter-a2a` |
| A2A 协议层 | `web/.../protocol/a2a/AgentHandlerConfiguration.java` | `JSONRPCHandler`/`RequestHandler`/`AgentExecutor`/`AgentCard` | `access.a2a.*` |
| 容器/微服务打包 | `maven_plugin/.../DeployerMojo.java`(+ Docker/K8s/AgentRun/ModelStudio service) | 把同一服务打成容器并部署 | 部署工具/CI |
| 用户可见模型 | `engine-core/.../engine/schemas/{AgentRequest,AgentResponse,Event,Message,Content,Role,RunStatus,Session}.java` | `AgentRequest`(model/topP/temperature/`input:List<Message>`/sessionId/userId)；`Event`(sequenceNumber/object/status/error + `created/inProgress/completed/failed/rejected/canceled`)；多模态 `Content`(Text/Image/Audio/Data) 已就绪 | `common.*` |

**结论**：同一个 `Runner(AgentHandler)` 内核 + 可换 `DeployManager`/starter → 两种部署模式；多框架由 `getFrameworkType()` + `adapters/<framework>/` 承载；全链路 `Flux` 响应式。

---

## 7. 目标 agent-runtime 模块模型

保留 Doc 2 的打包卫生，**engine 层按 AgentScope 重建**，**删 §9 中立 provider SPI**：

```
com.huawei.ascend.runtime/
  common/      # 用户可见中立模型，对齐 AgentScope 命名
    AgentRequest, AgentResponse, Event, Message, Content(+Text/Image/Audio/Data), Role, RunStatus, ErrorInfo
  access/      # 协议接入；A2A 优先；输出走响应式 OutputChannel(Flux)
    a2a/        A2aController / A2aJsonRpcHandler / A2aAgentCardController / A2aRequestMapper / A2aResponseMapper
    output/     OutputChannel(Flux<Event>) / EngineOutputSink
    api/        AccessRequestApi / AccessOutputApi
  session/     RuntimeSession(≠AgentSession) + RuntimeSessionRepository + InMemory 首版
  engine/      # ← 参考 AgentScope 重建；退役 EnginePort/双模
    Runner                       # 包一个 AgentHandler，产出 Flux<Event>
    spi/
      AgentHandler               # getName/getDescription/getFrameworkType/start/stop/isHealthy/streamQuery():Flux<?>/getStreamAdapter/getMessageAdapter
      AbstractAgentHandler
      StreamAdapter              # adaptFrameworkStream(Object):Flux<Event>
      MessageAdapter
    service/                     # D7：最小不透明注入的基础设施服务
      SandboxService / StateService / SessionHistoryService / MemoryService
    registry/
      AgentHandlerRegistry       # 按 agentId
      FrameworkAdapterRegistry   # 按 getFrameworkType
    adapters/
      openjiuwen/                # v1 唯一完整实现（Tool/Rail/McpClient/LongTermMemory 都在此内部）
      # agentscope/ springai-alibaba/ langchain4j/ langgraph4j/ dify/  ← 预留，缓建
  control/     # task 生命周期 submit/resume/cancel，响应式；RunStatus DFA 精简复用
  queue/       # 无业务类型响应式队列工具（或直接 Reactor Sinks）
  app/
    RuntimeApp                   # ≈ AgentApp（builder：handler + services + host）
    RuntimeHost                  # ≈ DeployManager：deploy(RuntimeComponents)/undeploy
    LocalA2aRuntimeHost          # 源码SDK托管（内嵌 Spring Boot，≈ LocalDeployManager）
  # 微服务模式：独立 agent-runtime-spring-boot-starter-a2a + 容器打包
```

**删除/退役**（写进 Doc 2 的"基础定义退役说明"）：`agent-runtime/engine/{spi,exec,runtime,planner}/*`（ExecutorAdapter/GraphExecutor/AgentLoopExecutor/EngineRegistry/InProcessEnginePort/EngineEnvelope/Sequential·Iterative 执行器）、`agent-bus/spi/engine/*`(EnginePort/AgentEvent/RunMode/ExecutorDefinition/SuspendSignal/Orchestrator)、`dispatch/spi/{AgentResultAdapter,AgentExecutionResult}` 的旧形态。

---

## 8. 两种部署模式端到端（D2 + D9）

**源码SDK托管模式**（客户把 runtime 当库嵌入自己进程）：
```
客户应用 main()
  -> RuntimeApp.create(handler).services(...).run(LocalA2aRuntimeHost.port(8080))
  -> LocalA2aRuntimeHost 内嵌 Spring Boot，暴露本地 /a2a + /.well-known/agent-card.json
  -> 同进程内直接可用；无需 AgentService
```

**微服务调用模式**（runtime 独立部署、远程被调、集群前端编排）：
```
agent-runtime-spring-boot-starter-a2a + 你的 handler bean
  -> 自动装配 Runner + A2A 服务，打成容器/Pod，独立部署
  -> 每个 runtime 实例 = 一个单 Agent 的 A2A server
AgentService（纯控制面）
  -> 启动/注册（Doc 1 registry）/路由（Doc 1 gateway）/治理（tenant/auth/quota/audit）
  -> 把 A2A 调用转发到目标 runtime 实例的 /a2a，SSE 回流
  -> 不持有 agent 执行态、不跑 agent loop、不暴露自身的 agent A2A —— serving 委托给各 runtime
```

**为什么 web/app 不搬进 AgentService**：web/app 是 runtime 实例固有的自我暴露层；搬入会让 SDK 托管模式无法自我服务、并被迫依赖 AgentService，直接破坏"单产物服务两种模式"。AgentScope 同构——`web` 在 runtime，中心化靠 `maven_plugin`/`DeployManager`，而非独立 service 吸收 web。

---

## 9. Dify / 远程 adapter（D10）

存量 Dify 工作流是 Dify 平台里按 API key 引用的 app。Dify 是**部署好的远程平台 + REST/SSE API**（`controllers/service_api/app/` 暴露 `/workflows/run`、`/chat-messages`、`/completion-messages`，`response_mode`=streaming/blocking，`conversation.py` 管会话，`workflow_events.py`/`human_input_form.py` 支持事件流与 HITL，Bearer 按 app 的 API key 鉴权）。

**Dify adapter = 远程 REST+SSE 客户端**实现中立 `AgentHandler`(frameworkType="dify")：
```
streamQuery(AgentRequest)
  -> 映射为 Dify 请求(inputs / query / conversation_id / user / response_mode=streaming)
  -> POST Dify /v1/workflows/run（存量工作流）或 /v1/chat-messages
  -> 消费 SSE 事件流（workflow_started / node_finished / message / workflow_finished / message_end / error ...）
  -> StreamAdapter 映射成中立 Flux<Event>
sessionId  <->  Dify conversation_id（adapter relay）
配置：baseURL + appApiKey + appType（远程框架需连接配置，与 MCP server-config 同理）
```

要点：**存量工作流不重写**，tools/memory/节点全在 Dify 内——**完美印证 D6"框架内部"**。修正"纯 REST 即可"的说法：要 **REST + SSE 流式**（纯 blocking 会丢首 token 流式与 SLA，与 D4 冲突）。这类"远程 adapter"与进程内 Java 库 adapter 并列，证明中立 `AgentHandler` seam 是传输无关的。

---

## 10. 对 Doc 2 的逐节修改要求

| Doc 2 章节 | 修改 |
|---|---|
| §0 结论 / §1.2 删除旧类 | engine 层从"`EngineWorker`→openjiuwen 派发"改为 `Runner`+`AgentHandler`(带 `getFrameworkType`)+`StreamAdapter`/`MessageAdapter`+`adapters/<framework>/`。`dispatch.spi.AgentHandler` 旧四方法签名 → AgentScope 形态新签名。 |
| §1.3 删除旧架构表达 | 在退役清单里**显式列出** `EnginePort`/`ExecutorAdapter`/`GraphExecutor`/`AgentLoopExecutor`/`SuspendSignal`/`RunMode`/`ExecutorDefinition` 及 `agent-runtime/engine/*`、`agent-bus/spi/engine/*`，并写明"经 superseding ADR 退役 ADR-0072/0073/0158"。 |
| §5.3 AgentResponse / §9 | 流模型 `java.util.stream.Stream` → `Flux<Event>`/`Flow.Publisher`。`AgentResponse`/`Event` 对齐 AgentScope schema(sequence/status/lifecycle)。 |
| §9.4 engine service/provider | 删"不用 middleware 顶层概念"的论断改为：横切治理骑中立 Event 流(D8)；框架中间件留 adapter。`service/` 仅保留 D7 的最小基础设施服务(Sandbox/State/SessionHistory/Memory)作不透明注入。 |
| §9.5 ToolProvider / §9.3 SPI 清单 | **删** `ToolProvider`/`McpToolProvider`/`MemoryProvider`/`StateProvider`/`SandboxProvider` 中立 SPI；tool/skill/memory/MCP/中间件下沉各框架 adapter 用原生机制配。 |
| §9.6 类清单 | engine 子包改为 `Runner` + `spi/{AgentHandler,AbstractAgentHandler,StreamAdapter,MessageAdapter}` + `service/*`(基础设施) + `registry/{AgentHandlerRegistry,FrameworkAdapterRegistry}` + `adapters/<framework>/`。 |
| §10 app 运行入口 | 明确 `RuntimeApp`/`RuntimeHost`/`LocalA2aRuntimeHost` 是 runtime 自我服务层、留在 agent-runtime；补微服务 host = spring-boot-starter + 容器；指明 AgentService(纯控制面)+Doc 1 网关为集群前端。 |
| §11 AgentScope 能力映射 | 把"AgentHandler/MessageAdapter/StreamAdapter/Runner/AgentApp/DeployManager/services"映射补全（见本文 §6），并标注 `getFrameworkType` 多框架判别。 |
| §12 openjiuwen 首实现 | 明确 openjiuwen 是 `adapters/openjiuwen/` 内部实现，其 `Tool`/`Rail`/`McpClient`/`LongTermMemory`/工具中断全在 adapter 内消化，**不**上 runtime SPI。 |
| 新增章节 | (a)「基础定义退役说明」；(b)「关联提案」(链 Doc 1/Doc 3/scenario)；(c) frontmatter + `Status: Pending Review`；语气从"实施方法/必须"降为"提案/建议"。 |

---

## 11. 治理动作

- **新增 superseding ADR**（走 `/design-mode`）：正式退役 ADR-0072(Engine Envelope)/ADR-0073(Engine Hooks)/ADR-0158(EnginePort) 为历史残留，记录"AgentScope-aligned `Runner`/`AgentHandler`/`StreamAdapter`"为新的 engine 基础定义。ADR-0159 已归档无需再动。
- 退役落地后**三处 baseline lockstep**：`docs/governance/architecture-status.yaml`(adr_count / graph nodes·edges / workspace elements·relationships) + `architecture/workspace.dsl` + `architecture/facts/generated/*`（由 deterministic extractor 刷新，**勿手写**）。
- gate 在 **WSL** 跑（Rule G-7）；families.yaml 的 G-9.b 内容鲜度按惯例预置。

---

## 12. 对 Doc 3 / Doc 1 的连带影响

- **Doc 3**（yougq，YAML→handler SDK）：其前提"复用现有 handler 契约、不改 `AgentHandler` 签名"**因 D1/D3 失效**——handler 契约将被 AgentScope-aligned 新接口替换。Doc 3 须改为**面向新 `AgentHandler`**（getFrameworkType/streamQuery:Flux/getStreamAdapter），且 YAML 装配出的 tool/skill **不引入 runtime 级中立 ToolResolver/中间件抽象**，交目标框架原生注册。这也化解了先前评审标记的 **Doc2↔Doc3 handler 契约冲突**：解法 = Doc 3 跟随 Doc 2 新契约。
- **Doc 1**（EuphoriaYan/Codex，registry/gateway facade）：天然成为微服务模式集群前端，归 AgentService 控制面(D9)；其 `agent-examples` vs `agent-service` 命名残留仍需清理（正文残留把门面写成 `agent-service`，与 §0 决议矛盾）。

---

## 13. 建议推进分期

- **W1 文档对齐**：你据本文修订 Doc 2（§10 清单）；我们出 superseding ADR 草稿；同步 Doc 3 收窄说明。
- **W2 ADR + 基线**：ADR 落定退役 0072/0073/0158，刷新 facts + baseline lockstep，gate 绿。
- **W3 骨架**：建 `common/access/session/engine/control/queue/app` 目录 + 中立 `AgentHandler`/`Runner`/`StreamAdapter`/`MessageAdapter` + `LocalA2aRuntimeHost`（不含业务实现）。
- **W4 openjiuwen adapter**：`adapters/openjiuwen/` 完整实现（含 Tool/Rail/McpClient/LongTermMemory 的内部消化）。
- **W5 双模式 E2E**：ping/pong + hello 两轮 A2A，**分别经 LocalA2aRuntimeHost(SDK) 与 starter 微服务**跑通，证明同一 `Runner` 内核服务两种模式。
- **W6（可选）远程 adapter 样例**：Dify REST+SSE adapter，验证远程 adapter 类。

> 注：W3+ 属实现阶段，**待 owner 显式 go 再开工**（仓库当前处于 design 阶段）。

---

## 14. 待作者确认 / 开放项

1. 是否接受 **D1**（退役双模/EnginePort）与对应 superseding ADR？
2. engine 重建是否**完全照** AgentScope-Runtime-Java 的 `Runner`/`AgentHandler`/`StreamAdapter` 形态，还是在其上做我们自己的中立 schema 命名收敛（如 `Event` vs `AgentResponse` 的取舍）？
3. **v1 仅 openjiuwen adapter**、其余四框架(+dify)目录留空预留——是否认可？
4. `engine.service.*` 基础设施服务的**最小集**（Sandbox/State/SessionHistory/Memory）取舍，是否还要进一步删减到纯 I/O seam？
5. **Dify adapter** 是否纳入 v1 远程 adapter 样例（验证远程 adapter 类），还是仅留设计预留？
6. 中立**取消/config**的载体形态（对齐 langgraph4j `RunnableConfig`+`Cancellable` / AgentScope 的 cancel 语义）——是否单列一节定义？

---

## 15. Authority / 关联 / 源码引用附录

**Authority**：退役目标 ADR-0072 / ADR-0073 / ADR-0158（待 superseding ADR 落定）；ADR-0159 已归档。约束：Rule R-F/R-G(client streaming / 非阻塞 I/O 硬约束)→D4；Rule R-K(取消再授权/背压)→D3 中立取消/config；[[feedback_saa_competitor]]（spring-ai-alibaba 是竞品，其 artifacts 不得进核心 Maven 依赖）→ SAA 适配只能作独立可选 adapter 模块、适配不依赖核心。

**源码引用附录**（均经本轮真实阅读，可复核；路径相对各 repo 根）：
- 我们的基础定义：`agent-runtime/.../dispatch/spi/AgentHandler.java`、`.../access/protocol/a2a/{A2aJsonRpcController,A2aWellKnownAgentCardController}.java`、`agent-bus/.../spi/engine/{EnginePort,AgentEvent,RunMode,ExecutorDefinition,SuspendSignal}.java`、`agent-runtime/.../engine/{spi/ExecutorAdapter,exec/IterativeAgentLoopExecutor,runtime/EngineRegistry}.java`。
- AgentScope-Runtime-Java：`engine-core/.../adapters/{AgentHandler,StreamAdapter,MessageAdapter}.java`、`.../adapters/agentscope/AgentScopeAgentHandler.java`、`engine-core/.../engine/{Runner,DeployManager}.java`、`web/.../{app/AgentApp,LocalDeployManager,protocol/a2a/AgentHandlerConfiguration}.java`、`starters/spring-boot-starter-runtime-a2a/.../A2aAutoConfiguration.java`、`maven_plugin/.../DeployerMojo.java`、`engine-core/.../engine/schemas/*`。
- openjiuwen-core 0.1.12：`core/foundation/tool/{Tool,ToolCard,mcp/{McpTool,McpClient,McpServerConfig},annotation/ToolDefinition}.java`、`core/singleagent/rail/AgentRail.java`、`core/runner/callback/{CallbackFramework,HookType}.java`、`deepagents/middlewares/ContextEngineeringMiddleware.java`、`core/singleagent/agents/{ReActAgent,ReActAgentConfig}.java`（`LongTermMemory.getInstance()` 单例）、`core/singleagent/interrupt/*`。
- 异构验证：`agentscope-java`(`agentscope-core/.../agent/{Agent,StreamableAgent}.java`、`hook/*`)、`spring-ai-alibaba`(`ToolCallback`/`Advisor`/`workflow/Node`/`CompiledGraph`)、`langchain4j`(`service/{AiServices,TokenStream}.java`、`langchain4j-agentic/*`、`@Tool`)、`langgraph4j`(`langgraph4j-core/.../CompiledGraph.java` `stream(...):AsyncGenerator.Cancellable<NodeOutput>`、`StateGraph`、`langchain4j-agent`/`spring-ai-agent` 的 `AgentExecutor`)。
- Dify：`api/controllers/service_api/app/{workflow,completion,message,conversation,workflow_events,human_input_form}.py`（`/workflows/run`、`/chat-messages`、`/completion-messages`、`response_mode` streaming、SSE）。
