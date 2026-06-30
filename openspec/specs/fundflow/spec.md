# fundflow 规范

## 目的

资金流水（FundFlow）上下文记录所有引起账户余额变化的业务事件，是账本与对账的事实来源（source of truth）。它不直接修改账户余额，而是被账户上下文的用例**调用**以在余额变更的同一逻辑单元中落下一条业务流水。

不属于本上下文：账户余额本身（→ account）、分录复式记账（→ ledger）、对账核销（→ reconciliation）。

## 需求
### 需求:流水记录必须 append-only 且全局唯一

系统必须通过入站端口 `FundFlowAppService.record(input)` 落一条资金流水。每条流水必须：

- 由雪花 ID 生成的唯一 `flowNo`（DB 唯一索引兜底）；
- 由调用方提供的唯一 `bizNo`（DB 唯一索引兜底，作为幂等键）；
- `amount > 0`；
- `balanceAfter == balanceBefore + amount`（`direction = IN`）或 `balanceAfter == balanceBefore - amount`（`direction = OUT`）；
- 一旦落库不得修改或删除（append-only）。

#### 场景:首次记录 DEPOSIT 流水
- **当** 调用 `record(input)` 且 `bizNo` 未被使用过，`direction = IN`，`balanceAfter = balanceBefore + amount`
- **那么** 系统必须生成唯一 `flowNo` 并持久化一条流水

#### 场景:首次记录 WITHDRAW 流水
- **当** 调用 `record(input)` 且 `bizNo` 未被使用过，`direction = OUT`，`balanceAfter = balanceBefore - amount`
- **那么** 系统必须生成唯一 `flowNo` 并持久化一条流水

#### 场景:流水字段缺失
- **当** 调用 `record(input)` 且任一必填入参（`uid` / `bizNo` / `amount` / `balanceBefore` / `balanceAfter` / `direction` / `type`）为 null 或 `uid <= 0` 或 `bizNo` 为空白字符串
- **那么** 系统必须抛 `IllegalArgumentException`（参数校验在入站端口入口处）

### 需求:流水记录必须基于 bizNo 满足幂等

系统必须通过 `fund_flow_t.uk_biz_no` 唯一索引保证同一 `bizNo` 仅落一条流水。`record` 入口处必须先调用 `existsBizNo(bizNo)` 早退；即便穿透到 DB 唯一约束触发 `DuplicateKeyException`，也必须转换为 `BizIdempotentException`，由调用方吞掉后视为成功。

#### 场景:重复 bizNo 在应用层早退
- **当** 调用 `record(input)` 时 `existsBizNo(bizNo)` 返回 true
- **那么** 系统不得执行任何 DB 写入，必须由调用方按"幂等成功"处理

#### 场景:重复 bizNo 穿透到 DB 唯一约束
- **当** `existsBizNo(bizNo)` 返回 false（探测通过）但同一 `bizNo` 在并发条件下最终触发 `fund_flow_t.uk_biz_no` 唯一约束
- **那么** 系统必须将 `DuplicateKeyException` 转换为 `BizIdempotentException`，调用方必须能识别并吞掉该异常

### 需求:幂等探测必须能独立于 record 被调用

系统必须通过入站端口 `FundFlowAppService.existsBizNo(bizNo)` 提供幂等探测能力。`bizNo` 为空白字符串时必须抛 `IllegalArgumentException`；其他情况返回 boolean。

#### 场景:探测已存在 bizNo
- **当** 调用 `existsBizNo(bizNo)` 且 `bizNo` 在 `fund_flow_t` 中存在
- **那么** 系统必须返回 `true`

#### 场景:探测未存在 bizNo
- **当** 调用 `existsBizNo(bizNo)` 且 `bizNo` 在 `fund_flow_t` 中不存在
- **那么** 系统必须返回 `false`

#### 场景:探测参数非法
- **当** 调用 `existsBizNo(bizNo)` 且 `bizNo` 为 null 或空白字符串
- **那么** 系统必须抛 `IllegalArgumentException`

### 需求:按用户查询流水必须返回该用户全部流水

系统必须通过入站端口 `FundFlowAppService.findByUid(uid)` 返回指定 `uid` 的全部流水（按 DB 默认顺序，物理存储顺序）。`uid` 为 null 或 `<= 0` 必须抛 `IllegalArgumentException`；返回类型为 `List<FundFlowEntity>`。

#### 场景:查询有流水的用户
- **当** 调用 `findByUid(uid)` 且 `uid > 0` 且该用户存在任意条流水
- **那么** 系统必须返回该用户的全部流水列表

#### 场景:查询无流水的用户
- **当** 调用 `findByUid(uid)` 且 `uid > 0` 且该用户无任何流水
- **那么** 系统必须返回空列表

#### 场景:查询参数非法
- **当** 调用 `findByUid(uid)` 且 `uid` 为 null 或 `<= 0`
- **那么** 系统必须抛 `IllegalArgumentException`

### 需求:领域枚举必须与表 type 字段保持一致

`FundFlowTypeEnum` 枚举值（`DEPOSIT` / `WITHDRAW` / `FREEZE` / `UNFREEZE`）必须与 `fund_flow_t.type` 列当前支持的值保持一致。`type = SETTLE` 在表中预留但枚举中未列出——若后续启用 `SETTLE`，必须**同时**更新枚举与表注释。

#### 场景:落库时 type 必须为枚举值之一
- **当** 调用 `record(input)` 且 `type` 字段为 `DEPOSIT` / `WITHDRAW` / `FREEZE` / `UNFREEZE` 之一
- **那么** 系统必须正常落库

#### 场景:落库时 type 为未声明值
- **当** 调用 `record(input)` 且 `type` 为未在枚举中声明的值（如 `SETTLE`）
- **那么** 系统不得报错（由调用方负责枚举约束），但 DB 端将成功落库——这是已知的设计债（见 `design.md` **DEVF-003**）

## 领域事件

### 应发布事件（应然）

| 事件名 | 触发时机 | 载荷 |
|--------|----------|------|
| `FundFlowRecorded` | `record()` 成功持久化后 | `flowNo, uid, bizNo, amount, balanceBefore, balanceAfter, direction, type, occurredAt` |

### 当前发布事件（实然）

**无。** 流水落库后无任何事件发布。详见 `design.md`（DEVT-001）。

## 错误码契约

| 业务错误码 / 异常 | 在本上下文的触发场景 |
|--------------------|----------------------|
| `IllegalArgumentException` | `record` / `existsBizNo` / `findByUid` 入参校验失败 |
| `DuplicateKeyException` → `BizIdempotentException` | `uk_biz_no` 唯一约束冲突（由调用方吞掉） |
| `FUND_FLOW_TYPE_NOT_SUPPORT` | （间接）`AccountAppService#changeAmountWithFundFlow` 收到 DEPOSIT/WITHDRAW 之外类型时抛；fundflow 自身不抛此错 |

注：`fundflow` 上下文**不直接抛任何业务错误码**（无 `BizException`），入参校验走 `IllegalArgumentException`，幂等冲突走 `BizIdempotentException`，由调用方处理。

## 入站端口（接口契约）

```java
public interface FundFlowAppService {
    void record(CreateFundFlowRequest input);
    boolean existsBizNo(String bizNo);
    List<FundFlowEntity> findByUid(Long uid);
}
```

`FundFlowAppService` 是**被 account 上下文内部调用的入站端口**，当前无独立 REST 端点。如未来需要按 `bizNo` 查询流水等对外能力，应新增用例（见 `design.md` **DEVF-004**）。

## 出站端口（接口契约）

```java
public interface FundFlowRepo {
    void save(FundFlowEntity flow);            // 落库（依赖 uk_biz_no 幂等）
    boolean existsByBizNo(String bizNo);       // 提前探测幂等
    List<FundFlowEntity> findByUid(Long uid);  // 按用户查流水
}
```

仓储必须只接受/返回 `FundFlowEntity` 聚合根，不得接受/返回 PO。

