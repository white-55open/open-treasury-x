# account 规范

## 目的

账户上下文是资金系统中所有"动账"操作的入口。它维护用户资金账户的可用余额（`availableBalance`）与冻结余额（`frozenBalance`），并保证双余额在并发场景下永远守恒。

不属于本上下文：流水记录（→ fundflow）、分录复式记账（→ ledger）、链上交易（→ eth-adapter）、对账核销（→ reconciliation）。

## 需求
### 需求:账户创建必须全局唯一并支持幂等

系统必须通过入站端口 `AccountAppService.createAccount(uid)` 创建账户。同一 `uid` 在系统中必须全局唯一；重复调用必须幂等返回已存在账户的内部 id，不得抛异常。

#### 场景:首次创建账户
- **当** 调用 `createAccount(uid)` 且系统中不存在该 `uid` 的账户
- **那么** 系统必须创建一个 `availableBalance = 0` 且 `frozenBalance = 0` 的新账户并持久化，返回新建账户的内部 `id`

#### 场景:重复创建同一 uid 的账户
- **当** 调用 `createAccount(uid)` 且系统中已存在该 `uid` 的账户
- **那么** 系统必须返回已存在账户的内部 `id`，不得创建新账户，也不得抛任何异常

### 需求:账户查询必须按 uid 命中唯一账户

系统必须通过入站端口 `AccountAppService.getByUid(uid)` 返回指定账户的 `uid` / `availableBalance` / `frozenBalance`。当账户不存在时必须抛 `BizException(BizErrorEnum.ACCOUNT_NOT_EXIST)`。

#### 场景:查询已存在账户
- **当** 调用 `getByUid(uid)` 且系统中存在该 `uid` 的账户
- **那么** 系统必须返回包含 `uid`、`availableBalance`、`frozenBalance` 的 `GetAccountResponse`

#### 场景:查询不存在的账户
- **当** 调用 `getByUid(uid)` 且系统中不存在该 `uid` 的账户
- **那么** 系统必须抛 `BizException`，业务错误码必须为 `ACCOUNT_NOT_EXIST`

### 需求:增加可用余额必须保持金额守恒

系统必须通过入站端口 `AccountAppService.increaseBalance(uid, amount)` 增加账户的可用余额。`amount` 必须为正数；调用前必须验证账户存在；调用成功后 `availableBalance` 必须增加 `amount`，`frozenBalance` 不得变化。

#### 场景:正常增加可用余额
- **当** 调用 `increaseBalance(uid, amount)`，账户存在，且 `amount > 0`
- **那么** 账户的 `availableBalance` 必须增加 `amount`，`frozenBalance` 保持不变

#### 场景:增加余额时账户不存在
- **当** 调用 `increaseBalance(uid, amount)` 且账户不存在
- **那么** 系统必须抛 `BizException`，业务错误码必须为 `ACCOUNT_NOT_EXIST`

#### 场景:增加余额金额非法
- **当** 调用 `increaseBalance(uid, amount)` 且 `amount` 为 null 或 `<= 0`
- **那么** 系统必须抛 `BizException`，业务错误码必须为参数校验类错误（`AMOUNT_CANT_NULL` / `PARAM_MISS`）

### 需求:冻结余额必须从可用扣减并加到冻结

系统必须通过入站端口 `AccountAppService.freezeBalance(uid, amount)` 把账户可用余额的一部分转为冻结余额。`amount` 必须为正数，且不得大于当前 `availableBalance`；调用前必须验证账户存在。

#### 场景:正常冻结余额
- **当** 调用 `freezeBalance(uid, amount)`，账户存在，`amount > 0` 且 `amount <= availableBalance`
- **那么** 账户的 `availableBalance` 必须减少 `amount`，`frozenBalance` 必须增加 `amount`，总额守恒

#### 场景:冻结金额超过可用余额
- **当** 调用 `freezeBalance(uid, amount)` 且 `amount > availableBalance`
- **那么** 系统必须抛 `BizException`，业务错误码必须为 `INSUFFICIENT_BALANCE`，账户余额不得变化

#### 场景:冻结金额非法
- **当** 调用 `freezeBalance(uid, amount)` 且 `amount` 为 null 或 `<= 0`
- **那么** 系统必须抛 `BizException`，业务错误码必须为 `FREEZE_AMOUNT_INVALID`

### 需求:解冻余额必须从冻结减回可用

系统必须通过入站端口提供解冻能力（待补具体方法名，对应 `AccountEntity#unfreezeBalance`），将冻结余额转回可用余额。`amount` 必须为正数，且不得大于当前 `frozenBalance`。

#### 场景:正常解冻余额
- **当** 调用解冻方法，`amount > 0` 且 `amount <= frozenBalance`
- **那么** 账户的 `frozenBalance` 必须减少 `amount`，`availableBalance` 必须增加 `amount`，总额守恒

#### 场景:解冻金额超过冻结余额
- **当** 调用解冻方法且 `amount > frozenBalance`
- **那么** 系统必须抛 `BizException`，业务错误码必须为 `INSUFFICIENT_FROZEN_BALANCE`

### 需求:提现扣减必须只动冻结余额

系统必须通过 `AccountEntity#withdraw(amount)` 实现提现扣减：仅扣减 `frozenBalance`，不得扣减 `availableBalance`。`amount` 必须为正数，且不得大于当前 `frozenBalance`（`frozenBalance == amount` 为合法边界，允许全额结算）；当 `frozenBalance < amount` 时必须抛 `BizException(INSUFFICIENT_FROZEN_BALANCE)`，不得抛裸 `IllegalArgumentException`。该方法不得校验 `availableBalance`（结算与可用余额无关）。

#### 场景:正常提现扣减
- **当** 调用 `withdraw(amount)`，`amount > 0` 且 `amount <= frozenBalance`
- **那么** 账户的 `frozenBalance` 必须减少 `amount`，`availableBalance` 保持不变

#### 场景:提现金额恰好等于冻结余额
- **当** 调用 `withdraw(amount)` 且 `amount == frozenBalance`
- **那么** 系统必须成功，`frozenBalance` 变为 0，`availableBalance` 保持不变

#### 场景:可用余额为零时仍可扣减
- **当** 调用 `withdraw(amount)`，`availableBalance = 0` 且 `amount <= frozenBalance`
- **那么** 系统必须成功（不得抛 `INSUFFICIENT_BALANCE`），仅扣减 `frozenBalance`

#### 场景:提现金额超过冻结余额
- **当** 调用 `withdraw(amount)` 且 `amount > frozenBalance`
- **那么** 系统必须抛 `BizException`，业务错误码必须为 `INSUFFICIENT_FROZEN_BALANCE`（不得抛 `IllegalArgumentException`）

#### 场景:提现金额非法
- **当** 调用 `withdraw(amount)` 且 `amount` 为 null 或 `<= 0`
- **那么** 系统必须抛 `BizException`，业务错误码必须为 `WITHDRAW_AMOUNT_INVALID`

### 需求:动账并落流水必须满足幂等性与跨聚合落库

系统必须通过入站端口 `AccountAppService.changeAmountWithFundFlow(request)` 一次性完成"修改账户余额 + 在 fundflow 上下文记录一条流水"。该方法必须满足：

- 入参 `bizNo` 为幂等键：同一 `bizNo` 重复调用必须返回相同结果，不得重复扣减或重复落库。
- 入参 `fundFlowType` 仅支持 `DEPOSIT`；`WITHDRAW` / `FREEZE` / `UNFREEZE` / 其他类型必须抛 `FUND_FLOW_TYPE_NOT_SUPPORT`（提现改由 withdraw 上下文的两阶段用例承担，不再复用本方法）。
- `DEPOSIT` 对应入账（`direction = IN`），账户执行 `deposit`。
- 落流水时 `balanceBefore` / `balanceAfter` 必须分别为动账前/后的 `availableBalance`。
- 账户持久化必须配合乐观锁：若乐观锁冲突（`@Version` 失配）必须抛 `OptimisticLockException`，并由 `@Retryable` 重试至多 5 次。

#### 场景:首次动账并落流水（DEPOSIT）
- **当** 调用 `changeAmountWithFundFlow` 且 `bizNo` 未被使用过，`fundFlowType = DEPOSIT`
- **那么** 账户可用余额必须增加入参 `amount`；fundflow 必须落一条 `direction = IN`、`type = DEPOSIT` 的流水，`balanceBefore` / `balanceAfter` 与动账前后一致

#### 场景:重复 bizNo 幂等
- **当** 调用 `changeAmountWithFundFlow` 且 `bizNo` 已被成功使用过
- **那么** 系统必须返回原 `bizNo`，账户余额不得再次变化，fundflow 不得新增重复流水

#### 场景:WITHDRAW 类型被拒绝
- **当** 调用 `changeAmountWithFundFlow` 且 `fundFlowType = WITHDRAW`
- **那么** 系统必须抛 `BizException`，业务错误码必须为 `FUND_FLOW_TYPE_NOT_SUPPORT`，账户余额与 fundflow 均不得变化

#### 场景:不识别的资金类型
- **当** 调用 `changeAmountWithFundFlow` 且 `fundFlowType` 为 `FREEZE` / `UNFREEZE` / 其他非支持值
- **那么** 系统必须抛 `BizException`，业务错误码必须为 `FUND_FLOW_TYPE_NOT_SUPPORT`

#### 场景:入参缺失
- **当** 调用 `changeAmountWithFundFlow` 且任一必填入参（`uid` / `bizNo` / `amount` / `fundFlowType`）为 null 或 `uid <= 0`
- **那么** 系统必须抛 `BizException`，业务错误码必须为对应的参数校验错误

### 需求:并发写入必须通过乐观锁与重试保证最终一致

系统必须使用 `@Version` 乐观锁防止并发覆盖：当 MyBatis `update` 返回影响行数为 0 时，MyBatis 拦截器必须将其转为 `OptimisticLockException`。`changeAmountWithFundFlow` 必须使用 `@Retryable` 在 `OptimisticLockException` 上重试最多 5 次，退避策略为 `delay=100ms`、`multiplier=1.5`、`maxDelay=250ms`、`random=true`。

#### 场景:并发更新导致乐观锁冲突
- **当** 两个并发事务同时读取同一账户并尝试更新，其中一个先提交使版本号递增
- **那么** 后提交的事务必须收到 `OptimisticLockException`，且应用层必须自动重试该用例

### 需求:REST 端点契约

系统必须通过以下 REST 端点暴露账户上下文用例。所有响应必须为 `Result<T>` 包装，业务错误通过 HTTP 200 状态 + 业务错误码表达。

| Method | Path | 入参 | 业务场景 |
|--------|------|------|----------|
| POST | `/accounts/create/{uid}` | path: `uid` | UC-1 账户创建 |
| GET | `/accounts/{uid}` | path: `uid` | UC-2 账户查询 |
| POST | `/accounts/increase` | query: `uid`, `amount` | UC-3 增加可用余额 |
| POST | `/accounts/freeze` | query: `uid`, `amount` | UC-4 冻结余额 |

#### 场景:账户创建端点
- **当** 调用 `POST /accounts/create/{uid}` 且 `uid` 为正整数
- **那么** 系统必须返回 HTTP 200，`Result.data` 为字符串形式的账户内部 id

#### 场景:账户查询端点
- **当** 调用 `GET /accounts/{uid}` 且账户存在
- **那么** 系统必须返回 HTTP 200，`Result.data` 为 `GetAccountResponse`（含 `uid` / `availableBalance` / `frozenBalance`）

#### 场景:账户增加端点错误码
- **当** 调用 `POST /accounts/increase?uid=...&amount=...` 且账户不存在
- **那么** 系统必须返回 HTTP 200，`Result.code` 必须为 `ACCOUNT_NOT_EXIST`

## 领域事件

### 应发布事件（应然）

| 事件名 | 触发时机 | 载荷 |
|--------|----------|------|
| `AccountCreated` | `createAccount(uid)` 持久化成功后 | `uid, availableBalance, frozenBalance, occurredAt` |
| `BalanceIncreased` | `increaseBalance` 持久化成功后 | `uid, amount, balanceAfter, occurredAt` |
| `BalanceFrozen` | `freezeBalance` 持久化成功后 | `uid, amount, availableAfter, frozenAfter, occurredAt` |
| `BalanceUnfrozen` | 解冻持久化成功后 | `uid, amount, availableAfter, frozenAfter, occurredAt` |
| `BalanceWithdrawn` | `withdraw` 持久化成功后 | `uid, amount, frozenAfter, occurredAt` |

### 当前发布事件（实然）

**无。** 领域层不存在 `DomainEvent` 基类，不存在 `EventPublisher` 端口，无任何事件订阅者。事件总线架构尚未引入。详见 `design.md`（DEVT-001）。

## 错误码契约

本规范所有错误码均引自 `io.github.open55.otx.common.exception.BizErrorEnum`，规范不复制枚举值清单（单一事实源在代码）。下表给出每个业务错误码在本上下文中的触发场景。

| 业务错误码 | 在本上下文的触发场景 |
|------------|----------------------|
| `ACCOUNT_NOT_EXIST` | `getByUid` / `increaseBalance` / `freezeBalance` / `changeAmountWithFundFlow` 找不到账户 |
| `INSUFFICIENT_BALANCE` | `freezeBalance` 时可用不足 |
| `INSUFFICIENT_FROZEN_BALANCE` | 解冻 / 提现时冻结不足 |
| `FREEZE_AMOUNT_INVALID` | `freezeBalance` 金额非正 |
| `UNFREEZE_AMOUNT_INVALID` | 解冻金额非正 |
| `WITHDRAW_AMOUNT_INVALID` | `withdraw` 金额非正 |
| `CONCURRENCY_ERROR` | 乐观锁冲突（`OptimisticLockException` 抛出） |
| `PARAM_MISS` / `UID_INVALID` / `UID_CANT_NULL` / `AMOUNT_CANT_NULL` / `BIZ_NO_EMPTY` / `FUND_FLOW_TYPE_CANT_NULL` | `changeAmountWithFundFlow` 入参校验 |
| `FUND_FLOW_TYPE_NOT_SUPPORT` | `changeAmountWithFundFlow` 收到 DEPOSIT 之外类型（含 WITHDRAW） |

## 入站端口（接口契约）

```java
public interface AccountAppService {
    Long createAccount(Long uid);
    GetAccountResponse getByUid(Long uid);
    void increaseBalance(Long uid, BigDecimal amount);
    void freezeBalance(Long uid, BigDecimal amount);
    String changeAmountWithFundFlow(ChangeAmountRequest request);
}
```

## 出站端口（接口契约）

```java
public interface AccountRepo {
    AccountEntity findByUid(Long uid);     // 返回 null 表示账户不存在
    void save(AccountEntity account);      // 新建
    void update(AccountEntity account);    // 更新（依赖 @Version 乐观锁）
}
```

仓储必须只接受/返回 `AccountEntity` 聚合根，不得接受/返回 PO。

