# 提案：Ledger 模块（企业级总账 + Web3 账本）

## 为什么

当前 Open Treasury X 的资金模型存在三处根本性缺口，使其无法承载"企业级 + Web3"的资金管理诉求：

1. **会计语义缺失**：`account_t` / `fund_flow_t` 采用"单式流水"风格（IN/OUT 方向 + 用户视角单一余额），没有借贷平衡、状态机、冲销等企业级总账能力，无法生成符合审计要求的财务报表。
2. **链上追溯缺失**：`fund_flow_t` 没有 `chain_tx_hash` / `block_number` / `chain_id` 等字段，后续接入 web3j 时无法做链上/链下对账，也无法满足 Web3 业务的可追溯性要求。
3. **账户语义缺失**：`account_t` 只承载"用户钱包"单一维度，平台热钱包、冷钱包、手续费账户、待清算账户等运营账户无处安放，多账户主数据缺位。

`V2__ledger.sql` 已建出 `ledger_entry_t` 表（含 `biz_no + account_code + entry_type` 联合唯一索引），domain 层也已存在半成品 `LedgerEntryEntity`——这是一个明确的"未完成信号"。本变更正是要把这条线从"半成品"推进到"完整骨架"。

## 变更内容

### 新增能力

- **LedgerJournal 聚合根**：`LedgerJournalEntity` 作为聚合根，承载一笔业务事件的全部借贷分录 `List<LedgerEntryEntity>`。提供创建、状态转换（DRAFT → POSTED → REVERSED）、借贷平衡校验、冲销等行为。
- **复式记账领域模型**：每笔业务事件产生 ≥2 条分录，必须满足 `SUM(DEBIT.amount) == SUM(CREDIT.amount)`，且同一账户在同一方向上不重复。
- **Web3 字段预埋**：在 Journal 主表预留 `chain_id` / `chain_tx_hash` / `block_number` / `token_address` / `confirmations` 字段（本期不接入 web3j 实现，但所有相关查询与索引一次到位）。
- **Web3 端口预留**：`ChainQueryPort` 出站接口在 domain 层定义，本期仅接口、不写实现，作为后续 web3j 适配器的契约。
- **REST API**：`POST /ledger/journals`（过账）、`GET /ledger/journals/{bizNo}`（查询）两个端点。

### 修改能力

无。现存 `openspec/specs/` 为空，没有旧规范需要修改。本次是首次为 `ledger` 限界上下文沉淀规范。

### 数据库变更

- `V2__ledger.sql` 改写：原 `ledger_entry_t` 拆分为 `ledger_journal_t`（Journal 主表）+ 新建 `V3__ledger_entry.sql`（Entry 明细表）。
- `V4__account_accountcode.sql` 新建：`account_t` 增加 `account_code` 字段（归一用户账户到系统账户编码）。

### 受影响代码

| 层 | 新增/修改 |
|---|---|
| `otx-domain` | 4 个枚举 + 2 个实体 + 1 个领域服务 + 2 个仓储接口 + 1 个端口 + 删除半成品 `LedgerEntryEntity` |
| `otx-application` | 4 个 DTO + 1 个 Assembler + 1 个 `LedgerAppService` 接口与实现 + `BizErrorEnum` 追加 12 个错误码 |
| `otx-infrastructure` | 2 个 PO + 2 个 Mapper + 2 个 Converter + 2 个 Repo 实现 |
| `otx-interface` | 1 个 `LedgerController` |
| `docs/sql` | 重写 V2 + 新建 V3 + 新建 V4 |

## 非目标（明确不做）

本变更聚焦"总账骨架"，以下事项不在本期范围：

- ❌ **web3j 适配器实现**：`ChainQueryPort` 仅为接口，链上查询/确认/广播的真实实现在后续变更。
- ❌ **账户主数据 `ledger_account_t`**：本期通过 `LedgerAccountCodeEnum` hardcode 一组系统账户编码（USER_AVAILABLE / PLATFORM_HOT 等），账户主表（可配置化）留待后续变更。
- ❌ **多币种汇率换算**：仅记录 `currency` 字段（VARCHAR(16)），不进行跨币种换算。
- ❌ **期末结转 / 账期管理**：`posting_date` 字段已留，但月末/年末结转流程不在本期范围。
- ❌ **替代 `fund_flow_t`**：`fund_flow_t` 继续作为用户视角的对外流水视图，与 `ledger_entry_t` 通过 `biz_no` 关联，**不重复造轮子也不删除旧表**。
- ❌ **对账引擎**：`reconciled` 字段未引入，Reconciliation 模块是独立的 roadmap 任务。
- ❌ **多租户隔离查询增强**：`tenant_id` 字段已建，查询级 tenant 过滤留待基础设施增强。
- ❌ **领域事件发布**：`JournalPosted` / `JournalReversed` 等事件本期不发布，跨聚合通信用同步调用即可，事件机制在后续"应用服务编排"阶段统一引入。

## 影响

### 限界上下文

- **新增** `ledger` 限界上下文（domain / application / infrastructure 三层同时建立）。
- **弱依赖** `account` 上下文（通过 `account_code` 字段关联，不修改 Account 实体）。
- **弱依赖** `fundflow` 上下文（共用 `biz_no` 幂等键，DepositAppService 后续可串接 ledger，本期暂不改造）。

### 架构层

- Domain 层：新增聚合 `LedgerJournal`，新增值对象 `LedgerEntry`，新增出站端口 `LedgerJournalRepo` / `LedgerEntryRepo` / `ChainQueryPort`。
- Application 层：新增 `LedgerAppService.postJournal` / `findByBizNo` 两个用例，事务边界与重试策略沿用 `AccountAppService` 的样板（REQUIRES_NEW + @Retryable + DuplicateKey → BizIdempotent）。
- Infrastructure 层：MyBatis-Plus + MapStruct 出站适配器，零侵入既有 ORM 配置。
- Interface 层：Spring MVC 入站适配器，REST 端点遵循 `Result<T>` 统一响应。

### 兼容性

- 不破坏 `AccountAppService` 既有行为。
- 不修改 `account_t` / `fund_flow_t` 既有数据，仅追加 `account_code` 列。
- 旧半成品 `LedgerEntryEntity`（位于 `otx-domain/.../ledger/`，无 .entity 子包）被同包同名的最终版替代，避免重复定义。

### 风险

| 风险 | 缓解 |
|---|---|
| 借贷平衡校验遗漏 | T04 单元测试覆盖所有违反场景（金额不等 / 缺 DEBIT / 缺 CREDIT / 重复 account） |
| 幂等性不足 | 联合唯一索引 `(biz_no, account_code, entry_type)` + `BizErrorEnum.LEDGER_BIZ_NO_EMPTY` 前置校验 |
| 乐观锁并发冲突 | `@Retryable(retryFor=OptimisticLockException, maxAttempts=5)` 沿用现有重试配置 |
| Web3 端口粒度过细/过粗 | T09 接口评审时确认；本期不影响功能 |
