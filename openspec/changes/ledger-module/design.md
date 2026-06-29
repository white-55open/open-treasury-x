# 设计：Ledger 模块

## 上下文

Open Treasury X（OTX）是一个开源的 Web3 资金管理中间件。当前代码库已沉淀 account / fundflow / deposit / withdraw 等模块，但 Ledger 模块仅有半成品的 `LedgerEntryEntity` 和预建的 `ledger_entry_t` 表。`V2__ledger.sql` 的 `biz_no + account_code + entry_type` 联合唯一索引是天然的双分录 + 幂等键组合，但表结构与表名都不能直接承载"Journal 做聚合根"的企业级总账语义。

OTX 的架构约束：DDD + 六边形 + 多模块，domain 零框架，仓储只操作聚合根，应用服务管事务，跨聚合用事件。

本期要把 ledger 从"半成品表"升级为"可承载企业级总账 + 后续 web3j 接入的完整骨架"。

## 目标 / 非目标

**目标：**

- 建立以 `LedgerJournal` 为聚合根、`LedgerEntry` 为不可变值对象的总账领域模型。
- 实现复式记账不变量（借贷必平 + 至少各一 + 同户单向唯一）。
- 提供幂等的过账（postJournal）用例与按业务号查询（findByBizNo）用例。
- 在数据库层用联合唯一索引保证幂等性，在应用层用事务 + 重试保证并发安全。
- 在 Journal 主表预留 Web3 链上追溯字段，并在 domain 层定义 `ChainQueryPort` 出站端口供后续 web3j 适配器实现。
- 保持与既有 account / fundflow 模块的弱依赖，不破坏现有行为。

**非目标：**

- 不实现 `ChainQueryPort` 的 web3j 适配器（接口仅预留）。
- 不实现账户主数据 `ledger_account_t`（系统账户编码通过枚举 hardcode）。
- 不实现多币种汇率换算、期末结转、对账引擎、领域事件发布（详见 `proposal.md`）。

## 决策

### 决策 1：聚合根 = `LedgerJournal`（不是 `LedgerEntry`）

**理由**：企业级总账以"凭证"为事务单元，一笔业务事件（一次充值、一次提现、一次冲销）= 一个 Journal + N 条借贷 Entry。强一致、状态机、冲销都在 Journal 粒度进行。Entry 退化为"不可变值对象"，由 Journal 持有，对外不暴露 Entry 单独的仓储写操作。

**替代方案**：

- 让 `LedgerEntry` 做聚合根、`biz_no` 关联多条 entry：一致性靠应用层补偿，弱保证。
- 引入 `JournalAggregate` 双层聚合：表达力强但实现复杂度上升，本期不必要。

### 决策 2：数据库结构 = `ledger_journal_t` 主表 + `ledger_entry_t` 明细表

**理由**：聚合根 = Journal，对应主表 `ledger_journal_t`（V2 改造）；明细 Entry 对应 `ledger_entry_t`（V3 新建）。这是关系数据库承载"聚合 + 不可变值对象"的标准范式。

**表名选择**：

- 不复用 V2 的 `ledger_entry_t`：原表名与新聚合根语义错配，且 V2 表结构是单层"伪分录"，改造后字段完全不同，重命名比补字段更彻底。
- V2 改为 `ledger_journal_t`（主表），V3 新建 `ledger_entry_t`（明细表），跟代码层 `LedgerJournalEntity` / `LedgerEntryEntity` 一一对应。

### 决策 3：Web3 链上字段全部挂 `ledger_journal_t`

**理由**：一笔业务事件（一次链上充值）= 一个链上 tx，链上字段语义上属于"业务事件"而非"分录"。Entry 是借贷平衡的明细，本身不感知链上来源。

**挂 Journal 的字段**：`chain_id` / `chain_tx_hash` / `block_number` / `token_address` / `confirmations`。均允许 NULL，链下业务（内部转账、手续费调整）不填。

**替代方案**：

- 挂 Entry：同一笔 tx 的多条 entry 重复存 txHash，容易不一致。
- 独立 `chain_event_t` 表：彻底解耦但增加 join 开销。

### 决策 4：业务幂等键 = `(biz_no, account_code, entry_type)` 联合唯一索引

**理由**：V2 已建此索引，是天然的"幂等 + 双分录"组合。重复过账相同 `(biz_no, account_code, entry_type)` 会触发 `DuplicateKeyException`，由 `LedgerAppServiceImpl` 转换为 `BizIdempotentException` 并短路返回。

**Journal 主表额外加 `uk_biz_no`**：作为"整笔业务"的幂等屏障，避免"先有一条 DEBIT、再来一条 DEBIT"也被允许。Entry 层的联合唯一索引是"分录级"幂等，Journal 层的 `uk_biz_no` 是"凭证级"幂等。

### 决策 5：借贷平衡校验放在 domain 层 + DB CHECK 约束双保险

**理由**：

- domain 层 `LedgerJournalEntity.assertBalanced()` 抛 `BizException(LEDGER_NOT_BALANCED)`，给出可读错误消息。
- DB 层（MySQL 8）可加 CHECK 约束（可选，本期不强制）作为最后防线。

**为什么不全靠 DB**：错误消息不够友好，且 MySQL 5.7 之前不支持 CHECK。本期仅 domain 校验，后续可加 DB 约束。

### 决策 6：状态机在 domain 层 `LedgerJournalEntity` 实现

**理由**：

- 状态机是核心业务规则，归属 domain 层。
- 状态转换方法（`post()` / `reverse()`）封装所有合法性校验，外部不能直接 `setStatus(POSTED)`。
- 反向 Journal 是一个新的 Journal 实体（不是原 Journal 改状态），便于保留完整审计轨迹。

**状态机**：

```
DRAFT ──post()──▶ POSTED
DRAFT ──reverse(reason)──▶ REVERSED
POSTED ──reverse(reason)──▶ REVERSED
```

### 决策 7：应用服务双层（postJournal 入口 + postJournalAtomic 事务块）

**理由**：沿用 `AccountAppService.changeAmountWithFundFlow` 的样板——外层入口做幂等提前返回，内层方法用 `REQUIRES_NEW` 开启独立事务并加 `@Retryable`。两个方法都通过 `@Lazy` 注入 `self` 引用，触发 Spring AOP 代理。

**流程**：

```
LedgerAppService.postJournal(req)
  ├─ existsByBizNo(req.bizNo) → 命中: 直接返回已存在 Journal
  └─ self.postJournalAtomic(req) [REQUIRES_NEW + @Retryable]
        ├─ 构造 Entity + 校验不变量
        ├─ journalRepo.save(journal)
        ├─ entryRepo.saveBatch(entries)
        ├─ journal.post()
        ├─ journalRepo.update(journal)
        └─ catch DuplicateKeyException → BizIdempotentException
```

### 决策 8：`ChainQueryPort` 接口粒度

**接口方法**（仅定义，本期不实现）：

```java
public interface ChainQueryPort {
    Optional<ChainTxReceipt> queryTxReceipt(String chainId, String txHash);
    Long currentBlockNumber(String chainId);
    boolean isConfirmed(String chainId, String txHash, int requiredConfirmations);
}
```

**理由**：

- `queryTxReceipt` 是一次性拉取交易回执，链上确认/状态查询都从它派生。
- `currentBlockNumber` 用于本地计算 confirmations（避免每次 RPC 调用）。
- `isConfirmed` 是高频热路径调用，封装确认数判断的阈值逻辑。

**返回值 `Optional`**：表示"链上查不到"（txHash 错误或链未同步），业务上区别于"查到但失败"。

### 决策 9：Entry 不可变（值对象）

**理由**：

- 借贷平衡校验在 Journal 创建时一次性完成，事后修改 Entry 等于绕过校验。
- POSTED 后所有 Entry 不可改（只能通过 reverse 产生新 Journal），符合审计要求。
- Entry 字段全部 final，构造时 self-validate（amount > 0、accountCode 非空、entryType 合法）。

### 决策 10：错误码扩展 = 追加 12 个到 `BizErrorEnum`

**理由**：复用现有异常层次 `RuntimeException → BizException`，不引入新异常类型。错误码命名遵循 `LEDGER_<UPPER_SNAKE>` 模式，与现有 `FUND_FLOW_TYPE_NOT_SUPPORT` 等保持风格一致。

| 错误码 | 触发场景 |
|---|---|
| `LEDGER_ENTRIES_EMPTY` | entries 为空 |
| `LEDGER_NOT_BALANCED` | Σ DEBIT ≠ Σ CREDIT |
| `LEDGER_DUPLICATE_ACCOUNT` | 同 accountCode 同 entryType 重复 |
| `LEDGER_BIZ_NO_EMPTY` | bizNo 为空 |
| `LEDGER_CURRENCY_EMPTY` | currency 为空 |
| `LEDGER_AMOUNT_INVALID` | amount ≤ 0 |
| `LEDGER_ENTRY_TYPE_INVALID` | entryType 不在 DEBIT/CREDIT 集合 |
| `LEDGER_ACCOUNT_CODE_INVALID` | accountCode 不在系统编码集合 |
| `LEDGER_JOURNAL_NOT_FOUND` | bizNo 查询不到 |
| `LEDGER_JOURNAL_NOT_DRAFT` | 非 DRAFT 状态调用 post() |
| `LEDGER_JOURNAL_NOT_POSTED` | 非 POSTED 状态调用 reverse() |
| `LEDGER_REVERSAL_NOT_FOUND` | 反向 Journal 关联缺失 |

## 风险 / 权衡

| 风险 | 影响 | 缓解 |
|---|---|---|
| 借贷平衡校验遗漏 | 账目不平，企业级合规问题 | T04 单元测试覆盖所有违反场景（金额不等 / 缺 DEBIT / 缺 CREDIT / 重复 account）；DomainEntity 构造时即校验 |
| 幂等性不足 | 重复过账导致账目翻倍 | DB 联合唯一索引 + Journal 主表 `uk_biz_no`；应用层 `DuplicateKeyException → BizIdempotentException` 短路 |
| 乐观锁并发冲突 | 高并发场景更新失败 | `@Retryable(retryFor=OptimisticLockException, maxAttempts=5)` 沿用 AccountAppService 配置；重试上限 5 次 + 指数退避 |
| 状态机被外部绕过 | 直接 setStatus 破坏不变量 | 状态字段 setStatus 私有化（包内可见但文档化禁止外部调用），状态转换只走 post/reverse 方法 |
| Entry 误改 | POSTED 后被修改，审计失效 | Entry 字段 final；Entity 暴露的 setter 全部删除；构造后无法修改 |
| Web3 端口粒度过粗/过细 | 影响后续适配器实现成本 | T09 单独 task 评审接口粒度；如不合适可调整但不影响本期功能 |
| 跨聚合一致性 | Journal 与 Account 余额的一致性 | 本期不强约束——Account 走原 `changeAmountWithFundFlow` 路径，Journal 独立过账；后续"业务编排"阶段用领域事件打通 |
| 反向 Journal 的链上关联 | reverse 操作的链上回滚语义不清 | 反向 Journal 共享原 Journal 的 chain_tx_hash 字段；增加 `reversed_by` 字段记录反向 Journal 自身的 bizNo |

## 数据库 schema 变更

### V2__ledger.sql（改造为 journal 主表）

```sql
DROP TABLE IF EXISTS ledger_journal_t;
CREATE TABLE ledger_journal_t (
    id              BIGINT          NOT NULL,
    biz_no          VARCHAR(64)     NOT NULL,
    biz_type        VARCHAR(32)     NOT NULL,
    posting_date    DATE            NOT NULL,
    currency        VARCHAR(16)     NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',
    total_amount    DECIMAL(38, 18) NOT NULL,
    description     VARCHAR(256)    DEFAULT NULL,
    chain_id        VARCHAR(16)     DEFAULT NULL,
    chain_tx_hash   VARCHAR(128)    DEFAULT NULL,
    block_number    BIGINT          DEFAULT NULL,
    token_address   VARCHAR(128)    DEFAULT NULL,
    confirmations   INT             DEFAULT NULL,
    reversed_by     BIGINT          DEFAULT NULL,
    version         BIGINT          DEFAULT 0,
    delete_flag     NVARCHAR(1)     NOT NULL DEFAULT 'N',
    created_by      INT,
    last_updated_by INT,
    create_time     DATETIME        NOT NULL,
    last_update_time DATETIME       NOT NULL,
    tenant_id       NVARCHAR(100),
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_no (biz_no),
    KEY idx_posting_date (posting_date),
    KEY idx_biz_type (biz_type),
    KEY idx_chain_tx (chain_tx_hash),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='总账凭证主表（Journal 聚合根）';
```

### V3__ledger_entry.sql（新建明细表）

```sql
DROP TABLE IF EXISTS ledger_entry_t;
CREATE TABLE ledger_entry_t (
    id              BIGINT          NOT NULL,
    journal_id      BIGINT          NOT NULL,
    biz_no          VARCHAR(64)     NOT NULL,
    account_code    VARCHAR(64)     NOT NULL,
    entry_type      VARCHAR(16)     NOT NULL,
    amount          DECIMAL(38, 18) NOT NULL,
    uid             BIGINT          DEFAULT NULL,
    counterparty    VARCHAR(128)    DEFAULT NULL,
    balance_after   DECIMAL(38, 18) DEFAULT NULL,
    remark          VARCHAR(256)    DEFAULT NULL,
    version         BIGINT          DEFAULT 0,
    delete_flag     NVARCHAR(1)     NOT NULL DEFAULT 'N',
    created_by      INT,
    last_updated_by INT,
    create_time     DATETIME        NOT NULL,
    last_update_time DATETIME       NOT NULL,
    tenant_id       NVARCHAR(100),
    PRIMARY KEY (id),
    UNIQUE KEY uk_journal_account_entry (biz_no, account_code, entry_type),
    KEY idx_journal (journal_id),
    KEY idx_biz_no (biz_no),
    KEY idx_uid (uid),
    KEY idx_account_code (account_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='总账分录明细表（Entry）';
```

### V4__account_accountcode.sql（account_t 加字段）

```sql
ALTER TABLE account_t
    ADD COLUMN account_code VARCHAR(64) NOT NULL DEFAULT 'USER_AVAILABLE' AFTER uid,
    ADD KEY idx_account_code (account_code);
```

## API 变更

### 新增端点

```
POST /ledger/journals
  Content-Type: application/json
  Request:
    {
      "bizNo": "DEP-20260101-0001",
      "bizType": "DEPOSIT_ONCHAIN",
      "currency": "USDT",
      "postingDate": "2026-01-01",
      "description": "User deposit 100 USDT",
      "chainId": "1",
      "chainTxHash": "0xabc...",
      "blockNumber": 18000000,
      "tokenAddress": "0xdac...",
      "entries": [
        { "accountCode": "PLATFORM_HOT",   "entryType": "DEBIT",  "amount": 100, "uid": null,             "counterparty": null },
        { "accountCode": "DEPOSIT_IN_TRANSIT", "entryType": "CREDIT", "amount": 100, "uid": 12345, "counterparty": "0xabc..." }
      ]
    }
  Response: Result<JournalDetailResponse>

GET /ledger/journals/{bizNo}
  Response: Result<JournalDetailResponse>
```

## 迁移计划

| 步骤 | 操作 | 回滚 |
|---|---|---|
| 1 | 部署 V2 / V3 / V4 SQL（Flyway 自动执行） | 备份 DDL，反向 DROP |
| 2 | 部署 otx-domain（含 `LedgerJournal` / `LedgerEntry` / `LedgerPostingService` / 3 个端口） | 删除新增文件 |
| 3 | 部署 otx-infrastructure（2 个 PO / 2 个 Mapper / 2 个 Converter / 2 个 Repo） | 同上 |
| 4 | 部署 otx-application（4 DTO / 1 Assembler / 1 AppService + 12 个错误码） | 同上 |
| 5 | 部署 otx-interface（`LedgerController`） | 同上 |
| 6 | 删除旧半成品 `LedgerEntryEntity.java` | 恢复旧文件 |
| 7 | 业务验证：手动 postJournal + 查询 + 幂等 + 借贷平衡异常 | N/A（功能开关） |

**回滚策略**：本期 Ledger 端点为新增，零下游依赖，可独立回滚。Flyway 迁移通过反向 DDL 回滚，旧半成品 `LedgerEntryEntity` 在 git 中可恢复。

## 待定问题

1. **Account ↔ Ledger 同步**：DepositAppService / WithdrawAppService 未来是否串联到 `LedgerAppService.postJournal`？本期不动，后续在"业务编排"变更中处理。
2. **`chain_tx_hash` 唯一性**：是否在 Journal 主表加 `uk_chain_tx`（当 chain_id + chain_tx_hash 都不为空时唯一）？本期不加，后续根据"链上重放保护"需求决定。
3. **DB CHECK 约束**：MySQL 8.0.16+ 支持 CHECK 约束，本期仅在 domain 层校验，后续可加 DB 层双重保险。
4. **领域事件**：`JournalPosted` / `JournalReversed` 事件是否在 `postJournalAtomic` 末尾发布？本期不发布，简化事务边界；后续在引入事件总线时统一处理。
