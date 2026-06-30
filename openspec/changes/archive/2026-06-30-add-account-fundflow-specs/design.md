# Design: add-account-fundflow-specs

## 上下文

### 项目状态

Open Treasury X 是一个采用 DDD + 六边形架构的 Web3 财务管理中间件，6 个 Maven 模块，47 个 Java 文件。`openspec/config.yaml` 已经写好了详细的架构上下文和技术栈清单，但 `openspec/specs/` 目录完全空白。

### 已实现的存量代码

- **account 上下文**（完整）：`AccountEntity` 6 个领域方法 + `AccountRepo` 出站端口 + `AccountAppService` 5 个用例 + `AccountController` 4 个 REST 端点 + `AccountPO` / `AccountMapper` / `AccountConverter` / `AccountRepoImpl` 持久化层。
- **fundflow 上下文**（完整）：`FundFlowEntity`（贫血）+ `FundFlowRepo` 出站端口 + `FundFlowAppService` 3 个用例 + `FundFlowPO` / `FundFlowMapper` / `FundFlowConverter` / `FundFlowRepoImpl` 持久化层。**无独立 Controller**（被 account 上下文内部调用）。
- **deposit / withdraw 上下文**（极薄）：`DepositAppService` / `WithdrawAppService` 各自仅 1 个委托方法，逻辑上侵入 account 上下文的 `changeAmountWithFundFlow`。
- **ledger 上下文**（仅空壳）：`LedgerEntryEntity` + `V2__ledger.sql`，无 Repo/AppService/Controller。
- **common / 横切**（完整）：异常体系、雪花 ID、乐观锁拦截器、审计、链路追踪、雪花 ID。

### 约束

- 规范必须**可被 OpenSpec 工具链消费**：每个需求用 `### 需求:` 标题，每个场景用 `#### 场景:`（恰好 4 个井号），使用 `当/那么` 格式。
- 规范以**应然（理想 DDD）**为主视角。
- 规范**不修改任何 Java 代码**，不引入新依赖。
- 存量代码中的"偏离 DDD"问题不修复，仅在 `Open Items` 章节中立记录。

## 目标 / 非目标

**目标**：

- 为 account / fundflow 两个上下文提供完整、可测试、可被 OpenSpec 归档的工作流消费的**主规范**。
- 建立"按限界上下文组织规范"的模板，为后续 deposit/withdraw/ledger 等上下文的规范补全提供范例。
- 把存量代码的实然行为**以"场景"形式**沉淀为可测试的验收条件。
- 把"应然但未实现"的差距**集中在 `Open Items`** 中，避免规范中出现无法兑现的需求。

**非目标**：

- 不修复 `Open Items` 中列出的任何代码异味。
- 不为 deposit/withdraw/ledger 写规范。
- 不改任何代码、配置、SQL、依赖。
- 不写自动化测试（`#### 场景` 是潜在测试清单，不在本变更落地为 JUnit）。
- 不调整 `config.yaml`。

## 决策

### D1: 规范组织粒度 = 按限界上下文

**决定**：每上下文一份 `spec.md`，目录为 `specs/<bounded-context>/spec.md`。

**理由**：
- 与 `config.yaml` 的 `rules.specs` 完全对齐；
- 限界上下文是 DDD 一等公民，每个上下文可独立演进；
- 未来 OpenSpec 工具链的 capability 匹配天然按目录分。

**考虑过的替代**：
- 按层（domain/application/interface）—— 跨上下文重复，但每个上下文的"层"被切片。失败：丢了业务边界。
- 按用例（use case）—— 粒度过细；同一聚合的多个用例无法一起读。失败：失去连贯性。

### D2: 规范视角 = 应然（理想 DDD）+ 实然差距在 design.md

**决定**：
- spec.md 中只写"应该是什么"。例如 `BalanceFrozen` 事件、应然聚合行为。
- 实然（当前代码）的偏离在 spec 中**不显式记录**（避免 spec 被 "什么不是" 的句子污染），而是在 `design.md` 的 `Open Items` 集中记录。

**理由**：
- spec 是契约 / 行为基线，混入"实然偏离"会让规范有歧义；
- "应然" 是 spec-driven 的自然选择（`OpenSpec` 期望 spec 是 desired state 而非 current state）；
- 偏离统一在 design.md 管理，便于后续变更提案"逐项填坑"。

**考虑过的替代**：
- spec 写实然、change 写偏离 → 偏离项没显式位置列出，文件散落。
- spec 同时写应然 + 实然 → 信息重复、维护成本高、易过期。

### D3: 领域事件章节写"应然 + 实然两个清单"

**决定**：spec.md 中 `## 领域事件` 章节下分两个子小节：
- `### 应发布事件（应然）` — 完整的命名、触发时机、载荷清单。
- `### 当前发布事件（实然）` — 一句话声明"无"，并指向 `design.md` 的 `DEVT-001`。

**理由**：
- 与决策 D2 不冲突（应然有"理想事件清单"，实然只声明"无"作为事实）；
- 防止后续读者误以为 spec 漏写；
- 为 `DEVT-001`（事件基础设施）提供清晰的范围描述。

**考虑过的替代**：
- 跳过整个领域事件章节 → 让"零事件"这一关键事实被埋没。
- 写单一应然清单 → 与 D2 冲突，无法让读者一眼看到"现状 = 0"。

### D4: Open Items 编号体系 = DEVT-*（跨上下文通用）/ DEVF-*（fundflow 私有）

**决定**：
- `DEVT-001` ~ `DEVT-007` 是**横切 / 跨上下文**的待办（事件基础设施、跨聚合解耦、端口隔离、提现流程、Money 值对象、多租户、deposit/withdraw 端点归属）。
- `DEVF-001` ~ `DEVF-005` 是 **fundflow 私有**的待办（应用层→应用层直调、贫血 FundFlowEntity、SETTLE 枚举、缺按 bizNo 查询、缺分页）。

**理由**：
- ID 编码区分"通用 / 私有"，后续跨多个上下文的变更可按 `DEVT-*` 重组任务。
- 当前 12 项远未超载，但分级已为未来扩展留好空间。

**考虑过的替代**：
- 加 P0/P1/P2 优先级 → 需要主观判断、维护成本高。
- 用 `TODO-1` 等纯顺序编号 → 失去"通用 / 私有"信号。

### D5: 错误码契约 = 引用 `BizErrorEnum`，不在 spec 中复制 code 列表

**决定**：
- spec 中只列每个错误码的**含义**和**触发场景**；
- 不在 spec 中复制 14 个枚举值的清单（避免和 `BizErrorEnum.java` 双源不一致）。

**理由**：
- 单一事实源：`BizErrorEnum` 是错误码的权威；
- spec 应描述"什么时候抛出什么错误"，而非"有哪些错误码"。

**考虑过的替代**：
- spec 完整枚举所有 code + message → 信息冗余，枚举增删时双源需同步。

### D6: 规范语言 = 中文（与 config.yaml 一致）

**决定**：spec 主体中文，类名/字段名/错误码/路径保留英文原样。

**理由**：
- `config.yaml` 的 `context` 段落是中文；
- 团队用语中文；
- 业务术语（动账、可用余额、冻结余额）中文更精准。

**考虑过的替代**：
- 全英文 → 与 config.yaml 风格不一致。
- 中英双语 → 维护成本加倍。

### D7: 规范不含 SQL DDL，引用 `docs/sql/`

**决定**：spec 不包含 `CREATE TABLE` 语句。涉及表结构的不变量（如 `uid` 唯一索引、`biz_no` 唯一索引）在 spec 中以"业务约束"措辞表达，DDL 在 `docs/sql/V1__account.sql` 和 `V2__ledger.sql` 中。

**理由**：
- spec 是行为契约，不是存储实现；
- 单一事实源在 SQL 迁移脚本；
- 避免 DDL 与 Hibernate/MyBatis-Plus 注解双源。

## 风险 / 权衡

- **[风险] 规范与代码很快失同步** → 缓解：每次 `openspec-cn archive` 前会触发 spec 同步；后续"动账代码"变更必须先有 spec delta。
- **[风险] 12 个 Open Items 散落在两份 spec 中** → 缓解：两份 spec 共享同一个 `DEVT-*` 编号空间，且都指向 design.md 的统一列表。
- **[风险] `#### 场景` 写法被 OpenSpec 严格解析，4 个井号缺一不可** → 缓解：写完后手工核对；tasks 中可加一个"格式 lint"任务。
- **[权衡] 不在本变更补测试** → spec 留有完整场景清单，但实际 JUnit 用例是后续变更。短期 spec 无法被 CI 自动验证。
- **[权衡] 不修复异味** → 让本变更保持"纯文档"，但异味长期存在会持续产生代码评审时的认知负担。
- **[风险] ledger 等其他上下文没规范，未来跨上下文变更可能没有 spec 基线** → 缓解：本次建立模板，下一轮单独 proposal 给 ledger 等。

## Open Items

> **本表是中立记录，不在本变更修复。** 每一项都是后续独立变更提案的种子。

### 跨上下文（DEVT-*）

- **DEVT-001 引入领域事件基础设施** — `DomainEvent` 基类 + `EventPublisher` 端口 + EventBus 实现（同步/异步可配）。无此基础设施，account 应发布的 5 个事件和 fundflow 应发布的 1 个事件均无法落地。
- **DEVT-002 `/deposit` / `/withdraw` 端点归属** — 当前由 deposit/withdraw 控制器调用 `AccountAppService#changeAmountWithFundFlow`，`ChangeAmountRequest` DTO 物理位置在 `deposit` 包下被两边共用。需在补 deposit/withdraw 规范时统一归属（建议：将端点和用例逻辑下沉到 deposit/withdraw 上下文，account 上下文只暴露通用入站端口 `changeAmount`）。
- **DEVT-003 跨聚合双写应事件化解耦** — `changeAmountWithFundFlow` 在 `REQUIRES_NEW` 事务中同时改 account 和 fundflow。目标：account 改完即提交并发布 `BalanceChanged` 事件，fundflow 订阅事件异步落流水（最终一致性）。流水落库失败需死信队列 / 补偿机制。
- **DEVT-004 withdraw 流程残缺** — `AccountEntity#withdraw` 要求 `frozen >= amount`，但 `WithdrawAppService` 实际未触发前置 freeze 步骤，行为退化为直接扣减 `available`。需补 freeze 前置，或重命名 `withdraw` 为 `deductFromFrozen` 等。
- **DEVT-005 提取 `Money` 值对象** — `BigDecimal` 当前代表金额但未封装为不可变值对象（缺货币单位、缺算术安全性）。应提取 `record Money(BigDecimal amount, Currency currency)` 或至少 `Money(BigDecimal amount)`。
- **DEVT-006 多租户查询隔离** — `BaseEntity/BasePO.tenantId` 已声明但仓储与查询未启用。需在 `AccountRepoImpl` / `FundFlowRepoImpl` 中按当前用户租户过滤。
- **DEVT-007 入站端口（Inbound Port）未隔离在独立包** — `AccountAppService` / `FundFlowAppService` 等接口直接放 `service` 包下，被入站适配器 `@Resource` 注入。DDD 严格做法：迁移到 `application.<bc>.port.in` 子包。

### fundflow 私有（DEVF-*）

- **DEVF-001 应用层 → 应用层直调** — `AccountAppServiceImpl` 直接 `@Resource` 注入 `FundFlowAppService` 并调用其 `record()`。应用层之间应不直接通信，应通过领域事件或引入应用层编排服务（`FundsChangeOrchestrator`）解耦。
- **DEVF-002 `FundFlowEntity` 是贫血模型** — 参数校验全在 `FundFlowAppServiceImpl#record` 的 `Assert.*` 中。需下沉到实体方法，例如 `FundFlowEntity.create(...)` 工厂 + `void validate()` 自检；不变量 `balanceAfter == balanceBefore ± amount` 应在 `record()` 时校验。
- **DEVF-003 `SETTLE` 枚举待定** — `fund_flow_t` 表的 `type` 字段注释中含 `SETTLE`，但 `FundFlowTypeEnum` 中无 `SETTLE` 枚举值。需确认：(a) 是否纳入枚举；(b) 若是，对应的领域用例是什么。
- **DEVF-004 缺按 `bizNo` 查询端点** — `FundFlowAppService.existsBizNo(bizNo)` 只能返回 boolean，外部系统无法获取已有流水的完整信息（如 `flowNo`、`balanceAfter`）。应新增 `Optional<FundFlowEntity> findByBizNo(String bizNo)` 入站端口和对应 Controller。
- **DEVF-005 `findByUid` 无分页** — 当前 `findByUid(Long uid)` 返回完整 `List<FundFlowEntity>`，全表扫描风险。需引入 `Page<FundFlowEntity> findByUid(Long uid, Pageable pageable)`。

## 迁移计划

本变更**不涉及运行时迁移**，仅文档变更：

1. `git add openspec/changes/add-account-fundflow-specs/`
2. `git commit -m "docs(openspec): add account and fundflow bounded-context specs"`
3. `openspec-cn archive add-account-fundflow-specs` 将变更归档，spec 同步到 `openspec/specs/account/spec.md` 和 `openspec/specs/fundflow/spec.md`。

**回滚策略**：`git revert <commit>` 即可，无副作用。
