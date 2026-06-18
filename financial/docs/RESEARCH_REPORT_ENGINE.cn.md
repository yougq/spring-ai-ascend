# 研报生成引擎(多智能体)— 设计与说明

> 金融行业智能体 · 归属 `financial/`(银行客户工作区)· 基于 spring-ai-ascend 平台与 `a2a-shared-memory` 共享记忆套件构建。
> 包路径:`com.bank.financial.research`。
>
> **产品范围(2026-06):** 本客户为商业银行,理财产品为**基金/FOF、债券/固收、行业主题/板块策略**,无单一股票二级研究,故已移除「个股」研报类型与对应引擎(DCF/可比/收敛等)。下文涉及个股 DCF/估值的章节为历史方法学说明,实际产品形态以这三类为准;方法对比评测已迁移到基金。共享黑板 / 确定性计算 / 独立复核 / 合规治理 / 跨运行经验等架构原则在三类报告中通用。

本引擎把"研究报告生产"建模为一支**研究小组的多智能体协作**:若干专精子智能体围绕一块**共享黑板(单一事实源)**分工,经"规划→数据→建模→估值→行业→首席收敛→撰写→评审→合规"的流水线,产出**评级、目标价、投资论点与多章节长文一致**的研究报告。核心设计目标是:**生成效果可控、数据接入可插拔、数字可计算可核验、风格与观点一致。**

---

## 1. 方法论依据(借鉴头部投行做法)

设计直接对标卖方研究的真实做法,并采纳已发表的多智能体长文生成架构:

- **报告骨架**:摘要/评级 → 投资论点 → 盈利模型与估计 → 估值(DCF + 可比)→ 情景与风险 → 行业/宏观 → 披露。投资论点是全文脊柱,评级、估计、估值、目标价逐层勾稽([Wall Street Prep](https://www.wallstreetprep.com/knowledge/sample-equity-research-report/);[CFI](https://corporatefinanceinstitute.com/resources/valuation/equity-research-report/))。
- **分工**:首席分析师拥有评级与论点(唯一决策者),副手做建模/数据/行业,**持牌监督分析师(SA)**按 FINRA Rule 2241 复核签发([Mergers&Inquisitions](https://mergersandinquisitions.com/equity-research-analyst/);[FINRA 2241](https://www.finra.org/rules-guidance/rulebooks/finra-rules/2241))。
- **数据四层**:一致预期(I/B/E/S 式)、公司财报、电话会纪要、实时行情;新鲜度窗口 + 最新覆盖去重(SUE 的 ~90 天规则)([Baruch I/B/E/S 指南](https://guides.newman.baruch.cuny.edu/Earnings))。
- **估值收敛靠三角验证**:DCF 与可比无单一权威,**两者重叠区即高置信区间**;显著背离时应**回到首席重新调和**而非简单平均([Ryan O'Connell CFA](https://ryanoconnellfinance.com/dcf-valuation-multiples/))。
- **多智能体长文架构**:STORM(先大纲+检索 grounding,再分节撰写)、FinCon(经理-分析师层级 + 单一决策者 + 信念选择性传播)、AutoGen earnings-call 框架(writer↔critic 限轮反馈循环)([STORM](https://storm-project.stanford.edu/research/storm/);[FinCon](https://openreview.net/forum?id=dG1HwKMYbC);[arXiv 2410.01039](https://arxiv.org/html/2410.01039v1))。
- **诚实定位**:已发表最佳研报生成系统仍有 **83% 的情形被人工报告偏好**,且在前瞻性分析与风险深度上偏弱([arXiv 2410.01039](https://arxiv.org/html/2410.01039v1))。因此本引擎定位为**分析师增强草拟器**,产出须经 SA 复核签发,**不是自主发布者**。

---

## 2. 子智能体(9 个,对标研究小组)

| 角色 | role id | 职责 | 产出(黑板键,owner) |
|---|---|---|---|
| 规划 | `planner` | 固定报告骨架与公司身份(STORM 预写) | `outline`、`company`、`currency` |
| 数据 | `data` | 发布行情锚(现价),数据已经新鲜度校验 | `currentPrice` |
| 建模 | `quant-model` | 多方法收敛的收入趋势→增速假设;FY1 收入/EPS/FCF;SUE | `revenue.FY1`、`eps.FY1`、`fcf.FY1`、`growth.*`、`sue*`、`trend.*` |
| 估值 | `valuation` | DCF(Gordon)+ 可比 + **三角收敛** + 情景 | `dcf.*`、`comps.*`、`convergence.*`、`scenario.*` |
| 行业/宏观 | `sector-macro` | 用驱动因子模型**量化外部信息对收入/EPS 的影响** | `impact.revenuePct`、`impact.eps` |
| 首席 | `lead-manager` | **唯一决策者**:评级、目标价、论点;背离时显式调和 | `rating`、`priceTarget`、`upsidePct`、`thesis` |
| 撰写 | `writer` | 单一笔触,从黑板规范数字撰写各章节 | `section.*` |
| 评审 | `critic` | 数字一致性门 + 触发有界改稿 | `critique.*` |
| 合规 | `compliance` | FINRA 式披露 + 认证 + SA 签发约束 + 数据缺口透明披露 | `compliance.*` |

> 为何 9 个、为何**单一撰写者 + 单一决策者**:长文一致性靠"单声道写作 + 单一定调",专精体只作研究/评审而非各自成文(STORM/FinCon 的核心经验)。

---

## 3. 数据接入方式(`research.data`)

- **SPI**:`ResearchDataSource`,四层(`fundamentals/consensus/market/peers`)+ 文本(`transcripts/news`)+ `macro`;一家实现 = 一个数据提供方。
- **离线**:`StubResearchDataSource`(虚构公司"晨曦科技/DEMO",数字固定 → 测试/演示确定性可复现)。
- **生产**:`HttpResearchDataSource`(瘦客户端,连接/请求**超时**,非 200/解析失败 → `DataUnavailableException`),银行接自有数据网关。
- **韧性**:`DataIngestionService` 逐层**容错降级**——某层失败则该层置空 + 记录告警,而非整次失败;`FreshnessPolicy` 做新鲜度窗口 + 最新覆盖去重,过期**标注而非静默丢弃**。
- **溯源**:每个数据点带 `Provenance`(来源/类型/as-of/引用/置信度),供合规披露与评审核验。

---

## 4. 复杂金融计算(`research.calc`,纯 Java、确定性、全单测)

**所有报告中的数字都来自这里计算,绝不由模型臆造。**

| 模型 | 作用 |
|---|---|
| `DcfModel` | DCF 内在估值,Gordon/退出倍数终值,**EV→股权桥**(净负债/少数股东) |
| `ComparablesModel` | 可比倍数(EV/EBITDA、EV/Sales、P/E),中位/均值,逐法做桥 |
| `ConvergenceCheck` | **三角收敛**:离散度 + 重叠区 → CONVERGENT/PARTIAL/DIVERGENT |
| `ScenarioAnalysis` | 牛/基/熊概率加权期望 |
| `SensitivityAnalysis` | WACC × 永续增长 二维敏感性表 |
| `EarningsSurprise` | 标准化盈利惊喜(SUE) |
| `RevenueImpactModel` | **外部信息→驱动因子→收入/EPS** 一阶分解(收入分析/收益分析核心) |
| `TrendForecast` | CAGR / OLS / 动量 **多方法预测并收敛**为一个观点(趋势预测核心) |

---

## 5. 一致性与收敛机制(生成效果的关键)

1. **黑板=单一事实源**:每个报告数字只由一个 owner 写一次;撰写环节只引用,不二次推算(`a2a-shared-memory` 的 ownership + append-log + provenance 保障)。
2. **论点为脊柱 + 固定大纲**:规划阶段先定论点与章节,撰写/评审都校验各节回链论点。
3. **显式收敛门**:估值算 DCF↔可比重叠;**DIVERGENT 时回到首席调和**(保守中点)而非平均。
4. **有界 writer↔critic 循环**:`NumericConsistencyChecker` 确定性核对正文数字 = 黑板规范值,发现漂移触发改稿,**轮数受预算上限约束**(AutoGen 限轮)。
5. **合规门**:披露 + 认证 + 平衡风险 + SA 签发约束。

**多智能体共享上下文/记忆/知识**:运行内共享 = 黑板;**跨运行知识** = `ExperienceMemoryKit`(经验在 run 末由 `CollaborationMemoryHook` 蒸馏入库、下次同类报告 recall),可插 `MemOptExperienceStore` 做持久语义记忆。

---

## 6. 韧性 / 预算 / 可观测(工程质量)

- **预算上限** `ReportBudget`:最大改稿轮数、最大模型调用次数、墙钟超时——超限即用"当前最优"收尾,绝不无界循环。
- **数据容错**:见 §3;HTTP 超时;经验后端 fail-open + 熔断(MemOpt 套件)。
- **可观测**:`MemoryObserver`(离线 NOOP,生产 Slf4j 例行 DEBUG / 降级 WARN + Micrometer);报告元数据记录模型调用数/改稿轮数/收敛判定/数据缺口/一致性发现。
- **可插拔/规模**:数据源、生成模型、经验后端均为 SPI;引擎无状态、黑板按 run 隔离。

---

## 7. 如何运行

```bash
# 端到端离线演示(无需 API key:桩数据 + 脚本化模型),打印完整 markdown 报告
./financial/play-research.sh DEMO

# 接真实数据/模型(环境变量驱动)
RESEARCH_REPORT_LIVE_MODEL=true BANK_LLM_API_KEY=sk-... BANK_LLM_API_BASE=... \
RESEARCH_DATA_BASE_URL=https://data-gw.intra/bank \
  ./financial/play-research.sh DEMO --real

# 测试(单元 + 集成 + 一致性,共 22 个)
./mvnw -f financial/pom.xml test
```

样例输出见 [`sample-report-DEMO.md`](sample-report-DEMO.md)。引擎也以 `research-report` 注册进 `FinancialAgentRegistry`,可经 A2A 调用 `generate_research_report(ticker)`。

---

## 8. 诚实自检 — 已达成 vs 待办

**已达成(离线可复现、有测试,29/29):** 9 智能体流水线;纯 Java 金融计算全单测;黑板单一事实源 + 一致性门 + 收敛门 + 有界改稿 + 合规披露;数据四层 SPI + 容错降级 + 新鲜度 + 溯源;跨运行经验蒸馏/召回;预算/可观测;facade + 独立 playground。

**经五维深审已加固(2026-06-17):**
- **故障隔离(robustness/resilience)**:引擎对每个 agent contribution 与每次模型调用做异常隔离——失败则该节降级为"事实摘要"、首席论点回退确定性版本,**整篇报告仍完整产出**,降级事件记入 `metadata.degradations` 并打 WARN;经验 recall/distill、合规均 fail-soft。失败路径有测试(注入永远抛错的模型 → 报告仍完整 + 降级被记录)。
- **live LLM 硬超时(robustness)**:`TimeoutReportModel` 装饰器给真实模型每次调用加硬超时(默认 60s,`RESEARCH_MODEL_TIMEOUT_S` 可调),超时即降级,不再无限阻塞;有超时单测。
- **单位一致(correctness)**:外部信息收入影响统一按百分比展示(`Bb.pct`),修正此前分数/百分比混用。
- **编排层可观测(observability)**:引擎按 run/phase 打结构化日志 + Micrometer `research.report.latency`/`research.report.count{outcome}` 指标;`fromEnv` 组合 Slf4j + Micrometer 两个 observer(同一埋点,配置切强度)。

**待办 / 当前边界(明确披露):**
- **真实 LLM 路径**(`OpenJiuwenReportModel`)已编译接通且加了超时/降级,但**离线未实跑**(无 key);需一条 live 集成测试与实测长度/质量。
- 离线报告长度为**示意**(脚本模型,~3.6k 字符);"数万 token"长度需真实模型生成。
- ANALYZE 阶段目前**顺序**执行;可借 `collaboration` 的 `Coordinator` 把建模/估值/行业**并行**(已评估,接口就绪,未接线)。
- WACC 为**固定假设**(未做 CAPM 推导);"情绪→驱动因子冲击"映射为**一阶启发式**——两者均已在代码注释中标注,生产应以校准弹性/CAPM 替换。
- 未跑 A2A 服务端 e2e;默认未用 `BoundedSharedMemoryStore` 包装(背压能力就绪,单 run 黑板小、风险低)。
- 适用档:**生产级方向的可运行垂直切片**,非完整可发布产品;符合"分析师增强、人工/SA 签发"的定位。

---

## 9. 主题 / 宏观板块策略引擎(扩展)

个股引擎之外新增一条**主题/板块策略**线(`research.thematic`),把宏观与地缘事件传导到子板块,产出"板块评级 + 子板块超配/标配/低配 + 配置建议"的策略研报(如中国 TMT)。复用同一套黑板、ReportModel、预算、可观测、经验、降级保护;通用管线抽出 `RunContext` 基类,个股/主题引擎共用。

- **数字骨架(确定性)**:`SectorImpactModel` —— 宏观因子(方向×强度)× 子板块敞口矩阵 → 各子板块**复合影响分** → 评级。**评级是算出来的,不是模型断言的**;LLM 只写散文。
- **数据层**:`ThematicDataSource` SPI + `StubThematicDataSource`(把美伊缓和 / FOMC 鹰派 / 国内宽货币 / 中美股市 4 条主线 × 6 个 TMT 子板块的敞口编码为可复现场景;生产可接实时源/喂入实时事件)。
- **7 个主题智能体**:planner / data(macro-ingestion)/ sector-impact / lead-manager(唯一决策者)/ writer(单声道)/ critic / compliance,经 `ThematicReportEngine` 编排:`PLAN→INGEST→IMPACT→CONVERGE→WRITE→CRITIQUE→COMPLY→ASSEMBLE`。
- **运行**:`./financial/play-research.sh --thematic "中国 TMT"`(离线);加 `--real` 走真实模型。

**✅ 真实 agent-runtime 验证(2026-06-17)**:以 GLM Coding Plan(`glm-4.6`)跑通 `--thematic --real` —— 元数据显示 `模型=openjiuwen:glm-4.6`,即经 `OpenJiuwenReportModel → ReActAgent → agent-runtime → GLM`;**8 次真实模型调用、0 降级、0 一致性问题**;计算出的子板块评级(半导体/AI算力 1.03、CPO 0.77、商业航天 0.69 → 超配;消费电子 0.145、互联网 0.09 → 标配;软件/SaaS −0.18 → 低配;总体 0.4242 → 超配)与人工策略判断一致,论点为真实模型散文且锚定计算分。

测试合计 **38/38**(新增 `SectorImpactModelTest` 4 + `ThematicReportEngineTest` 5,后者含"模型全失败仍完整产出 + 评级因系计算而保留"的降级测试)。样例见 [`sample-thematic-china-tmt.md`](sample-thematic-china-tmt.md)。
