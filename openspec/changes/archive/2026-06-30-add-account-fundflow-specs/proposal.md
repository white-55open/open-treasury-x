## 为什么

`openspec/specs/` 目录当前为空，项目缺乏可被 OpenSpec 工具链消费的主规范（main specs）。`config.yaml` 已经定义了"按限界上下文组织在 `specs/<bounded-context>/spec.md` 下"的规则，但无任何已落地的规范文件，导致：

- 后续的变更提案没有基线可以 delta；
- 代码评审缺少机器可读的行为契约层；
- 已实现的核心上下文（account、fundflow）有 47 个 Java 文件、1.4k 行实现，但契约只散落在 JavaDoc 和 Javadoc 注释里。

**为什么是现在**：account 和 fundflow 两个上下文是项目里实现最完整、且承担"动账 + 落流水"核心域的限界上下文。先把它们的规范建立起来，可作为后续 deposit/withdraw/ledger/reconciliation/eth-adapter 等上下文规范化时的模板和参照系。

## 变更内容

在 `openspec/specs/` 下新增两份主规范：

- **`specs/account/spec.md`**：账户限界上下文的完整行为契约（聚合根、领域方法、端口、事件、用例、错误码、REST API、幂等、并发）。
- **`specs/fundflow/spec.md`**：资金流水限界上下文的完整行为契约（同上结构）。

两份规范均以**应然（理想 DDD）**为主视角，**实然（当前代码）**的偏离不在本变更修复，而是记录在 `design.md` 的 "Open Items" 中作为后续变更的待办。

**非目标**（明确排除）：

- 不为 deposit、withdraw、ledger、reconciliation、eth-adapter、common 添加主规范。
- 不修复 account/fundflow 实然代码中的任何异味（withdraw 流程残缺、贫血 FundFlowEntity、零领域事件、跨聚合单事务双写等）。
- 不修改任何 Java 代码、SQL 迁移、配置文件、pom.xml。
- 不引入新依赖、不调整模块结构。

## 功能 (Capabilities)

### 新增功能

- `account`: 账户限界上下文主规范。定义 `AccountEntity` 聚合根的领域方法（create / increaseBalance / freezeBalance / unfreezeBalance / withdraw / deposit）、出站端口 `AccountRepo`、入站端口 `AccountAppService`、应然领域事件（`AccountCreated` / `BalanceIncreased` / `BalanceFrozen` / `BalanceUnfrozen` / `BalanceWithdrawn`）、5 个用例（UC-1 ~ UC-5）、14 个错误码契约、4 个 REST 端点、幂等与并发策略。
- `fundflow`: 资金流水限界上下文主规范。定义 `FundFlowEntity` 聚合根的 append-only 不变量、出站端口 `FundFlowRepo`、入站端口 `FundFlowAppService`、应然领域事件（`FundFlowRecorded`）、3 个用例（FC-1 ~ FC-3）、幂等策略（`uk_biz_no` 唯一索引 + `existsBizNo` 早退）。

### 修改功能

无（项目无既存规范可修改）。

## 影响

- **新增文件**：
  - `openspec/specs/account/spec.md`
  - `openspec/specs/fundflow/spec.md`
- **修改文件**：无。
- **代码影响**：无。本变更纯文档（OpenSpec 主规范），不触发任何 Java/Maven/SQL 改动。
- **API 影响**：无。
- **依赖影响**：无。
- **测试影响**：无（不新增自动化测试；规范的 `#### 场景` 即潜在测试用例清单，验证动作由后续"补测试覆盖"变更承担）。
- **运行时影响**：无。
- **团队影响**：引入"按规范驱动变更"的工作流基线——后续任何能力变更都需先有 spec，再有 proposal/design/tasks，最后 apply。
