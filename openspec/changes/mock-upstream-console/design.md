# mock-upstream 用户个人中心 设计文档

## 架构总览

```
┌──────────────────────────────────────────────────────────────┐
│  模拟用户个人中心（mock-upstream，独立应用 :8004）              │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Web 层（新增：spring-boot-starter-web + Thymeleaf）     │  │
│  │  HomeController（渲染页面）                               │  │
│  │  UserActionController（用户操作：创建/切换/充值/提现/取消） │  │
│  │  ProcessController（一键处理 POST /internal/process）     │  │
│  └───────────────┬────────────────────────────────────────┘  │
│                  │ 调用                                      │
│  ┌───────────────▼────────────────────────────────────────┐  │
│  │  场景编排层（重构现有 Simulator）                         │  │
│  │  DepositSimulator：发起充值 / 确认入账                    │  │
│  │  WithdrawSimulator：申请提现 / 标记取消 / 推进结算/解冻    │  │
│  │  ProcessOrchestrator：一键处理编排（遍历挂起事项批量推进）  │  │
│  └───────────────┬────────────────────────────────────────┘  │
│                  │ 读写                                      │
│  ┌───────────────▼────────────────────────────────────────┐  │
│  │  内存状态注册表（新增，ConcurrentHashMap 系）             │  │
│  │  UserRegistry / DepositRecordStore / WithdrawRecordStore │  │
│  │  OperationLogBuffer（环形缓冲）                           │  │
│  │  BlockchainSimulator（复用：区块/交易/确认数，仅按需推进）  │  │
│  └───────────────┬────────────────────────────────────────┘  │
│                  │ HTTP（OtxClient 复用，RestClient）         │
│  ┌───────────────▼────────────────────────────────────────┐  │
│  │  OTX REST API（:8003，主项目零改动）                     │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

## 页面与交互流程

```
┌──────────────────────────────────────────────────────────┐
│  模拟用户中心 · 用户 #100123                     [切换用户 ▼]│
├──────────────────────────────────────────────────────────┤
│  💼 资产                                            │
│  ┌──────────────────────┬──────────────────────┐         │
│  │  可用余额             │  冻结余额             │         │
│  │  100.00 USDT         │  50.00 USDT          │         │
│  └──────────────────────┴──────────────────────┘         │
│                                                          │
│  💰 充值        金额 [ 100 ]          [发起充值]           │
│  💸 提现        金额 [ 50 ]           [申请提现]           │
│                                                          │
│  ⚙ [一键处理 内部流程（2 笔待处理）]                        │
│                                                          │
│  📋 我的记录                                             │
│  ┌────────┬────────┬──────────────┬────────────────┐     │
│  │ 业务    │ 金额    │ 状态          │ 操作             │     │
│  ├────────┼────────┼──────────────┼────────────────┤     │
│  │ 充值    │ 100    │ ⏳ 确认中 5/12 │                │     │
│  │ 提现    │ 50     │ ⏳ 已冻结      │ [取消提现]       │     │
│  └────────┴────────┴──────────────┴────────────────┘     │
└──────────────────────────────────────────────────────────┘
```

### 一键处理状态机（每笔挂起事项）

```
充值：  CONFIRMING ──推块至达标──▶ BOOKED
        （确认数 5/12 页面实时展示）  （POST /deposit 成功）

提现：  FROZEN ──一键处理──▶ SETTLED
        （POST /withdraw/freeze）   （broadcast → 推块 → confirm-settle）

        FROZEN ──[取消提现]──▶ CANCEL_PENDING ──一键处理──▶ CANCELLED
                              （仅改内存状态）        （POST /withdraw/{bizNo}/cancel）
```

## API 变更（mock-upstream 内部端点）

| 端点 | 方法 | 业务含义 | 触发类型 |
|------|------|----------|----------|
| `/` | GET | 个人中心主页（当前用户资产 + 表单 + 记录 + 日志） | 页面 |
| `/users` | POST | 创建新用户并切换（uid 自动生成或输入） | 用户操作 |
| `/users/{uid}/select` | POST | 切换当前用户（会话保存） | 用户操作 |
| `/deposits` | POST | 发起充值：注册模拟链交易，状态 CONFIRMING | 用户操作 |
| `/withdrawals` | POST | 申请提现：调 POST /withdraw/freeze，状态 FROZEN | 用户操作 |
| `/withdrawals/{bizNo}/cancel` | POST | 取消提现：仅置 CANCEL_PENDING | 用户操作 |
| `/internal/process` | POST | 一键处理：批量推进所有挂起事项 | 内部触发 |

**一键处理（POST /internal/process）内部执行顺序**：

```
1. 充值批量：对每笔 CONFIRMING 充值
   → blockchainSimulator.advanceTo(requiredConfirmations) 推块至全部达标
   → 触发确认回调（复用现有 onConfirmationsMet 机制）
   → POST /deposit（复用现有回调体）→ 状态置 BOOKED
2. 提现批量：对每笔 FROZEN 提现
   → POST /withdraw/broadcast（复用现有广播调用）
   → 推块确认（模拟）→ POST /withdraw/{bizNo}/confirm-settle → 置 SETTLED
3. 取消批量：对每笔 CANCEL_PENDING 提现
   → POST /withdraw/broadcast（若尚未广播，创建请求记录）
   → POST /withdraw/{bizNo}/cancel → 置 CANCELLED
4. 每步调用结果追加到 OperationLogBuffer，页面日志区展示
```

## 内存模型（新增，均在 mock-upstream 内）

| 构件 | 关键字段 | 说明 |
|------|----------|------|
| `SimUser` | uid、创建时间 | 用户注册表：`ConcurrentHashMap<Long, SimUser>` + 当前选中 uid（Session） |
| `DepositRecord` | bizNo、uid、amount、currency、txHash、confirmations、status | 充值记录：`CONFIRMING` / `BOOKED`；确认数由模拟链实时计算 |
| `WithdrawRecord` | bizNo、uid、amount、currency、toAddress、freezeBizNo、status | 提现记录：`FROZEN` / `SETTLED` / `CANCEL_PENDING` / `CANCELLED` |
| `OperationLogEntry` | 时间、业务身份前缀、动作描述、HTTP 端点、OTX 响应摘要 | 环形缓冲（如 200 条），页面倒序展示 |
| `BlockchainSimulator`（复用改造） | 区块高度、交易表、确认回调 | 原 `@Scheduled` 定时推块**移除**，改为 `advanceTo(long)` 按需推进 |

## 幂等与并发考虑

- **模拟器为单用户浏览器操作**：一键处理为串行遍历（单线程执行），无需锁；内存状态均用 `ConcurrentHashMap` 防御多请求并发；
- **OTX 侧幂等**：沿用现有契约——充值/提现各阶段独立 bizNo（`fund_flow_t.uk_biz_no` 唯一索引），重复触发由 OTX 幂等兜底（本项目不修改）；
- **每笔独立 bizNo 生成**：`MOCK-DEP-{ts}` / `MOCK-WD-{ts}` 沿用现有模式，避免跨轮数据污染（uid 用时间戳派生，同现有 `nextUid()` 逻辑）；
- **防重复入账**：复用现有 `callbackTriggered` 集合——同一交易确认达标回调仅触发一次；一键处理对已 BOOKED / SETTLED 的记录跳过。

## 领域模型 / 数据库变更

- **OTX 主项目**：无领域模型变更、无数据库 schema 变更（零改动）；
- **mock-upstream**：内存态领域对象（非持久化），不涉及数据库。

## 跨聚合一致性策略

- mock-upstream 各内存注册表之间无强一致性要求：一键处理失败的单笔事项（如 OTX 不可达）保留原状态并记录日志，下次一键处理可重试（天然最终一致）；
- OTX 内部账务一致性由 OTX 既有事务语义保证（余额变更 + 流水同一 REQUIRES_NEW，总账失败不回滚——本项目不改变）。
