# 设计：提现两阶段改造（冻结 → 结算 → 解冻）

## Context（背景与现状）

提现上下文当前只有一个用例 `WithdrawAppServiceImpl.withdraw(ChangeAmountRequest)`，实现为"一步到账"：

1. 强制把 `fundFlowType` 置为 `WITHDRAW` 后调用 `AccountAppService.changeAmountWithFundFlow(request)`；
2. `changeAmountWithFundFlowAtomic` 的 WITHDRAW 分支调用 `AccountEntity.withdraw(amount)`；
3. 该方法从**冻结余额**扣减，但全流程没有任何冻结动作，`frozenBalance` 恒为 0，`Assert.isTrue(getFrozenBalance().compareTo(amount) > 0)` 必然失败并抛裸 `IllegalArgumentException`——**提现任何情况下都无法成功**（设计债 DEVT-004）。
4. 即使冻结存在，`AccountEntity.withdraw` 还错误地先行校验 `availableBalance`（与结算语义无关），并存在 `==` 边界被拒绝（`> 0` 而非 `>= 0`）的错误语义。

另外，`AccountAppService.freezeBalance(uid, amount)` / `unfreezeBalance(uid, amount)` 是裸余额方法：不落资金流水、不过账总账，冻结/解冻在会计上不可见，破坏"流水 vs 总账"对账闭环。

上游真实流程：**申请即冻结，审批通过结算，取消/驳回解冻**。本设计把提现重构为三个独立用例，让余额、流水、总账在每个阶段自洽。

### 现状代码事实

| 符号 | 现状问题 |
|------|----------|
| `AccountEntity.withdraw(amount)` | ① 多余校验 `availableBalance`；② `Assert.isTrue(frozen > amount)` 抛裸异常且 `==` 被拒 |
| `AccountAppServiceImpl.freezeBalance(uid, amount)` | 裸方法，无流水、无凭证 |
| `AccountAppServiceImpl.changeAmountWithFundFlowAtomic` WITHDRAW 分支 | 调 `withdraw` 必炸；流水快照错记可用余额 |
| `WithdrawAppServiceImpl.withdraw` | 复用 deposit 包 `ChangeAmountRequest`；凭证映射 `DEBIT USER_AVAILABLE` 在两阶段语义下会双重借记 |
| `LedgerBizTypeEnum` | 无 `FREEZE` / `UNFREEZE` 业务类型 |

## Goals / Non-Goals（目标 / 非目标）

**目标：**
- 提现拆为冻结 / 结算 / 解冻三个独立用例，各自资金 + 流水 + 凭证三落一体
- 修复 `AccountEntity.withdraw()` 的错误码、边界与多余校验
- 各阶段幂等（bizNo + 唯一索引 + 异常兜底）
- 冻结 / 解冻会计可见（凭证 + 流水），与 account / ledger 主规格保持一致

**非目标：**
- 不做链上广播与确认（另属 `tx-broadcast-orchestration`）
- 不引入 `SETTLE` 流水类型（DEVF-003）
- 不新增 `BizErrorEnum` 错误码、不新增会计科目（现有枚举已覆盖）
- 不引入提现申请单实体 / 阶段状态机（冻结-结算-解冻的顺序约束由调用方保证）
- 不改造 `FundFlowEntity`（DEVF-002）、不引入领域事件总线（DEVT-001）

## Decisions（关键决策）

### D1：三个独立用例，各自自代理原子方法

```
WithdrawAppService（入站端口）
├── freeze(request)    → self.freezeAtomic(request)    [REQUIRES_NEW + @Retryable(5)]
├── withdraw(request)  → self.withdrawAtomic(request)  [REQUIRES_NEW + @Retryable(5)]
└── unfreeze(request)  → self.unfreezeAtomic(request)  [REQUIRES_NEW + @Retryable(5)]
```

沿用 `AccountAppServiceImpl` / `LedgerAppServiceImpl` 已验证的范式：入口方法做参数校验 + `existsBizNo` 幂等前置检查，通过 `self` 代理调用原子方法触发 Spring AOP（`@Transactional` / `@Retryable`），捕获 `BizIdempotentException` 视为幂等成功。三个用例互不嵌套，各自独立事务。

### D2：修复 `AccountEntity.withdraw(amount)`

- **移除** `availableBalance` 校验（结算只与冻结余额相关，现有可用余额检查会在 `available=0 / frozen=50` 时误抛 `INSUFFICIENT_BALANCE`）；
- 冻结不足判断改为 `getFrozenBalance().compareTo(amount) < 0` → 抛 `BizException(INSUFFICIENT_FROZEN_BALANCE)`（**`frozenBalance == amount` 允许**，修正原 `> 0` 边界错误）；
- 不新增错误码：`INSUFFICIENT_FROZEN_BALANCE` / `WITHDRAW_AMOUNT_INVALID` 均已存在于 `BizErrorEnum`。
- 备选：保留 `Assert.isTrue` 但换消息——否决：裸 `IllegalArgumentException` 无法被 `GlobalExceptionHandler` 映射为业务码，必须走 `BizException`。

### D3：结算凭证映射修正为 `DEBIT USER_FROZEN / CREDIT WITHDRAW_IN_TRANSIT`（关键决策）

原 `buildWithdrawJournalRequest` 映射为 `DEBIT USER_AVAILABLE / CREDIT WITHDRAW_IN_TRANSIT`。在两阶段流程下该映射是**错误的**：

- 冻结阶段已借记 `USER_AVAILABLE`（可用 → 冻结）；
- 结算若再借记 `USER_AVAILABLE`，科目借方累计 2×amount，而账户可用余额实际只减少 amount——"账户余额 vs 科目余额"对账必然失配。

结算扣的是**冻结**余额，故凭证必须借记 `USER_FROZEN` 才能与账户状态一一对应、科目归零。两阶段合计借贷平衡验证：

```
冻结：  DEBIT USER_AVAILABLE(50)  / CREDIT USER_FROZEN(50)
结算：  DEBIT USER_FROZEN(50)     / CREDIT WITHDRAW_IN_TRANSIT(50)
净效应：USER_AVAILABLE 借方 50（可用-50）
       USER_FROZEN 借贷相抵（冻结归零）
       WITHDRAW_IN_TRANSIT 贷方 50（在途+50）
```

- 备选：保留 `DEBIT USER_AVAILABLE`——否决：科目余额失真，对账不变量被破坏。

### D4：解冻发布独立凭证，不冲销原冻结凭证

- 解冻凭证：`bizType = UNFREEZE`，`DEBIT USER_FROZEN / CREDIT USER_AVAILABLE`（与冻结凭证方向相反、各自独立）；
- **不调用 `LedgerJournalEntity.reverse()`**：冲销会把原冻结凭证置为 `REVERSED` 且需要 reverse 链路引用，而冻结本身是真实发生的业务事件，冻结凭证应保持 `POSTED` 留痕；独立凭证还让解冻沿用"自己的 bizNo 幂等"，无需跨凭证引用。冻结 + 解冻两张凭证合计借贷为零，总账平衡。

### D5：`LedgerBizTypeEnum` 新增 `FREEZE` / `UNFREEZE`

总账凭证必须按业务类型区分（`ledger_journal_t.biz_type`）。现有枚举只有 `DEPOSIT_ONCHAIN` / `WITHDRAW_ONCHAIN` / `INTERNAL_TRANSFER` / `FEE` / `REVERSAL` / `ADJUSTMENT`，冻结与解冻没有对应值，故新增两个枚举值。此改动**不是** DEVF-003（DEVF-003 指 `FundFlowTypeEnum` 缺 `SETTLE`，本次结算沿用现有 `WITHDRAW` 类型，不触碰 `FundFlowTypeEnum`）。

### D6：`changeAmountWithFundFlow` 移除 WITHDRAW 分支（**BREAKING**）

- WITHDRAW 分支是死路径（永远抛异常），且其"扣冻结"语义与两阶段流程绑定，继续保留会诱使后续调用方误用；
- 移除后 `changeAmountWithFundFlow` 仅支持 `DEPOSIT`，`fundFlowType = WITHDRAW` 走 default 分支抛 `FUND_FLOW_TYPE_NOT_SUPPORT`（现有 default 分支已存在，仅需删 WITHDRAW case + 更新接口 Javadoc 与测试）。
- 同步删除 `AccountAppService.changeAmountWithFundFlow` Javadoc 中"支持充值（DEPOSIT）和提现（WITHDRAW）"的描述。

### D7：新增专用 `WithdrawRequestDTO`（`otx-application/withdraw/dto/request/`）

- 字段：`uid`、`bizNo`、`amount`、`currency`（冻结 / 结算 / 解冻三用例共用，与 deposit 包 `ChangeAmountRequest` 解耦）；
- 否决方案：继续复用 `ChangeAmountRequest`——该 DTO 属 deposit 限界上下文，提现借用是设计异味，且 `fundFlowType` 字段对提现用例无意义（用例即类型）。

### D8：流水快照语义——`balanceBefore/After` 记录"本笔资金变动科目的前后余额"

`fund_flow_t` 结构不变（单一快照字段对），各阶段取变动科目的快照：

| 阶段 | 变动科目 | 快照示例（可用 100 / 冻结 0 → 冻结 50） |
|------|----------|-------------------------------------------|
| 冻结 | 可用余额 | before=100 / after=50 |
| 结算 | 冻结余额 | before=50 / after=0 |
| 解冻 | 可用余额 | before=0 / after=50 |

每笔流水都真实反映所动资金的轨迹，避免现有 `changeAmountWithFundFlowAtomic` 在 WITHDRAW 上"动冻结却记可用快照"的错误。

### D9：幂等策略（每阶段三层 + 凭证自幂等）

```
入口：参数校验 → existsBizNo(bizNo) 已存在则直接返回 bizNo
原子方法：REQUIRES_NEW 事务内 动账 + 落流水（biz_no 唯一索引）
         → DuplicateKeyException 捕获并转 BizIdempotentException
入口：捕获 BizIdempotentException → 视为幂等成功返回
过账：postJournal(bizNo) 自身幂等（existsByBizNo → 返回已有凭证 / 唯一键冲突 → 返回已有凭证）
```

并发重复提交：同一 bizNo 两个事务同时进入原子方法时，后者在唯一索引上失败 → 幂等兜底；账户更新依赖 `@Version` 乐观锁 + `@Retryable(5)`（`delay=100, multiplier=1.5, maxDelay=500, random=true`）。

### D10：事务边界与跨聚合一致性

```
每阶段原子方法：账户(REQUIRES_NEW 事务)
  1. accountRepo.findByUid → 领域方法动账 → accountRepo.update（乐观锁）
  2. fundFlowAppService.record（同事务内，唯一索引幂等）
  3. 事务提交后 → ledgerAppService.postJournal（独立 REQUIRES_NEW 事务，自身幂等）
```

- 余额 + 流水强一致（同一 `REQUIRES_NEW`）；总账过账为**最终一致性**：过账失败不回滚余额与流水，日志 WARN 留痕，由后续对账模块修复（与 `DepositAppServiceImpl` 既有模式一致，满足 ledger 主规格"过账失败禁止回滚"）。
- 一个用例内跨三个聚合（AccountEntity / FundFlow / Journal）：延续项目现状——同步调用编排，**不引入领域事件**（ledger 主规格"当前发布事件（实然）：无"保持成立）。

## 流程总览（ASCII）

```
                       提现两阶段（冻结 → 结算 / 解冻）

 用户提交申请                审核通过                  资金离账
     │                          │                        │
     ▼                          ▼                        ▼
 ┌───────────┐   freeze   ┌───────────┐   withdraw   ┌───────────┐
 │ 可用余额   │ ─────────▶ │ 冻结余额   │ ───────────▶ │ 提现在途   │
 │ available │   可用-50   │  frozen   │   冻结-50    │ in-transit│
 └───────────┘  冻结+50    └───────────┘              └───────────┘
     ▲                          │
     │                          │  unfreeze
     │     取消 / 驳回           ▼
     └────────────────────── 冻结-50 / 可用+50

 账务流（每阶段三落一体）：
   冻结：  FREEZE 流水(OUT) + 凭证 DEBIT USER_AVAILABLE / CREDIT USER_FROZEN
   结算：  WITHDRAW 流水(OUT) + 凭证 DEBIT USER_FROZEN / CREDIT WITHDRAW_IN_TRANSIT
   解冻：  UNFREEZE 流水(IN) + 凭证 DEBIT USER_FROZEN / CREDIT USER_AVAILABLE

 幂等：每个阶段独立 bizNo ──▶ existsBizNo 前置检查
                            ──▶ fund_flow_t.biz_no 唯一索引
                            ──▶ BizIdempotentException 兜底
                            ──▶ postJournal 凭证自幂等
```

## API 变更

### 入站端口 `WithdrawAppService`（重写）

```java
public interface WithdrawAppService {
    String freeze(WithdrawRequestDTO request);    // 冻结：锁定申请金额
    String withdraw(WithdrawRequestDTO request);  // 结算：从冻结余额扣减
    String unfreeze(WithdrawRequestDTO request);  // 解冻：释放冻结余额
}
```

- 返回值统一为 `bizNo`（幂等结果标识），与现有 `changeAmountWithFundFlow` 约定一致。
- 旧 `withdraw(ChangeAmountRequest)` 签名废弃（**BREAKING**，无外部调用方）。

### REST 端点（新增 `WithdrawController`，otx-interface）

| Method | Path | 入参（body） | 业务场景 |
|--------|------|--------------|----------|
| POST | `/withdraw/freeze` | `WithdrawRequestDTO`（uid / bizNo / amount / currency） | 申请提现，锁定资金 |
| POST | `/withdraw/settle` | 同上 | 审批通过，冻结余额结算 |
| POST | `/withdraw/unfreeze` | 同上 | 取消/驳回，释放冻结资金 |

响应统一 `Result<String>`（data = bizNo），业务错误 HTTP 200 + 业务码（`INSUFFICIENT_BALANCE` / `INSUFFICIENT_FROZEN_BALANCE` / `FREEZE_AMOUNT_INVALID` / `UNFREEZE_AMOUNT_INVALID` / `WITHDRAW_AMOUNT_INVALID` / `BIZ_NO_EMPTY` / `UID_CANT_NULL` / `AMOUNT_CANT_NULL` / `PARAM_MISS` 等）。

## 领域模型变更

| 位置 | 变更 |
|------|------|
| `otx-domain` `AccountEntity.withdraw` | 移除可用余额校验；冻结不足抛 `BizException(INSUFFICIENT_FROZEN_BALANCE)`；`frozenBalance >= amount` 通过（== 允许） |
| `otx-domain` `LedgerBizTypeEnum` | 新增 `FREEZE("FREEZE")`、`UNFREEZE("UNFREEZE")` 两个值 |
| `otx-application` `WithdrawAppService(Impl)` | 三用例重写 + `WithdrawRequestDTO` |
| `otx-application` `AccountAppServiceImpl.changeAmountWithFundFlowAtomic` | 删除 WITHDRAW case（default 抛 `FUND_FLOW_TYPE_NOT_SUPPORT`） |
| `otx-interface` | 新增 `WithdrawController` |
| 聚合边界 | 不变：`AccountEntity`（account 聚合根）、`LedgerJournalEntity`（ledger 聚合根）、`FundFlowEntity`（流水实体）各自独立，用例在应用层编排 |

数据库 schema：**无变更**。`fund_flow_t.biz_no` 唯一索引、`ledger_journal_t.biz_no` 唯一、`ledger_entry_t (biz_no, account_code, entry_type)` 联合唯一索引均复用既有设施；Flyway 无新增脚本。

## 测试策略

| 层 | 测试类 | 覆盖 |
|----|--------|------|
| domain | `AccountEntityTest`（扩展） | `withdraw`：== 边界、冻结不足 → `INSUFFICIENT_FROZEN_BALANCE`、可用为 0 仍可扣、金额非法 |
| application | `WithdrawAppServiceImplTest`（重写） | 三用例：成功（资金/流水/凭证三断言）、余额不足拒绝、金额非法、幂等（existsBizNo 短路 + `BizIdempotentException` 兜底）、过账失败容错（WARN 不抛出）、结算凭证映射为 `DEBIT USER_FROZEN` |
| application | `AccountAppServiceImplTest`（更新） | `changeAmountWithFundFlow` WITHDRAW → `FUND_FLOW_TYPE_NOT_SUPPORT`；DEPOSIT 路径回归 |
| interface | `WithdrawControllerTest`（新增） | 三端点路由 + `Result` 包装 + 错误码透传 |
| 集成 | otx-starter 新增 `WithdrawTwoPhaseIntegrationTest` | 端到端：冻结→结算全流程（余额/流水/凭证三方核对）、冻结→解冻全流程、同 bizNo 重复调用幂等、并发同 bizNo 提交唯一索引兜底、乐观锁重试 |

单测遵循 `openspec/specs/testing/spec.md`：snake_case 业务命名、`@Nested` 分组、中英双语 `@DisplayName`、具名常量、Javadoc 场景说明。

## Risks / Trade-offs（风险与取舍）

- **[结算凭证映射变更（USER_AVAILABLE → USER_FROZEN）] 已过账的历史凭证与新映射不一致** → 提现此前因 `Assert` 必然失败、从未有成功过账的 WITHDRAW 凭证（DEVT-004 保证无历史脏数据），映射变更零迁移成本。
- **[`changeAmountWithFundFlow` 移除 WITHDRAW 为破坏性变更]** → 全仓库唯一调用方即 `WithdrawAppServiceImpl`，本次一并重构；接口 Javadoc 同步更新，杜绝外部误用。
- **[过账失败最终一致性窗口]** → 与充值路径一致：失败时日志 WARN + 对账模块兜底修复，不引入新的不一致类型。
- **[阶段顺序不强制（冻结→结算 或 冻结→解冻）]** → 语义由调用方编排（上游业务流程），本期不建申请单状态机；结算/解冻仅校验冻结余额充足性，天然防止超额。
- **[`LedgerBizTypeEnum` 新增枚举值]** → 纯增量，不影响既有凭证查询与冲销逻辑。

## Migration Plan（迁移与回滚）

- **部署顺序**：domain（枚举 + 实体修复）→ application（用例重写）→ interface（控制器）→ 测试；无数据迁移、无 Flyway 脚本。
- **回滚**：本变更为"修复 + 纯新增"，回滚即还原 `AccountEntity.withdraw` 与 `WithdrawAppServiceImpl`；`LedgerBizTypeEnum` 新增值为无副作用增量，保留无害。

## Open Questions（开放问题）

- 提现申请单实体与阶段状态机（冻结 → 结算 / 解冻 的顺序强制、取消后重新冻结）是否在后续变更引入——本期明确不做，依赖调用方编排。
- `currency` 是否在冻结/解冻阶段成为强约束（当前仅透传给凭证，沿用 `ChangeAmountRequest.currency` 的可选语义）。
