# tx-broadcast-orchestration 设计文档

## Context（背景与现状）

OTX 当前的链上能力仅有**只读查询**：`ChainQueryPort`（`io.github.open55.otx.domain.ledger.port`，含 `queryTxReceipt` / `currentBlockNumber` / `isConfirmed`）由 `Web3jChainQueryAdapter` 实现（多 RPC 按序故障切换，客户端按 URL 池化于 `Web3jConfig`，配置在 `Web3jProperties`，前缀 `web3j`）。

提现流程在 `withdraw-two-phase` 变更（本变更前置，已完成）后已拆分为三个链下账务用例：`freeze()`（可用→冻结 + FREEZE 流水 + 冻结凭证）、`withdraw()`（结算：扣冻结 + WITHDRAW 流水 + 结算凭证 DEBIT USER_FROZEN / CREDIT WITHDRAW_IN_TRANSIT）、`unfreeze()`（解冻 + UNFREEZE 流水 + 解冻凭证）；三者均不触碰链上。本变更在此基础上补上链上广播编排层。

总账凭证 `LedgerJournalEntity` 已具备：
- 状态机 `LedgerJournalStatusEnum`：DRAFT → POSTED → REVERSED，转换只能通过领域方法 `post()`（仅 DRAFT）/ `reverse()`（仅 POSTED），**当前所有凭证创建即过账，DRAFT 从未被使用**；
- 链上字段：`chainId / chainTxHash / blockNumber / tokenAddress / confirmations`（已建列可存储）；
- `LedgerAppServiceImpl.postJournalAtomic`：REQUIRES_NEW + `@Retryable(5 次)` + DuplicateKeyException → BizIdempotentException 的幂等范式。

约束条件：
- 依赖链严格单向：`common ← domain ← {application, infrastructure} ← interface ← starter`；domain 纯 Java 零框架；
- `withdraw-two-phase` 变更将先行提供**冻结/解冻**能力（`AccountEntity.freezeBalance/unfreezeBalance` 应用层用例 + FREEZE/UNFREEZE 流水），本变更在其之上构建，**不重复实现冻结**；
- 资金流水 append-only、凭证不可删只能冲销、金额一律 BigDecimal；
- 测试规范：单测零网络零 DB 全 mock；测试命名 `methodUnderTest_scenario_expectedBehavior`、`@DisplayName` 中英双语、`@Nested` 分组、Javadoc 中文。

## Goals / Non-Goals

**Goals（本设计达成）：**
- 提现交易广播编排全链路：构造 → 签名 → 广播 → 留痕（DRAFT 凭证 + 提现请求记录）→ 确认结算 → 失败/取消解冻；
- 签名可插拔：`SignerPort` 出站端口 + 配置驱动适配器选择，**私钥永不进入 OTX 核心**；
- 广播可插拔：`TxBroadcastPort` 出站端口 + web3j 实现；
- 激活总账 DRAFT 状态机（凭证先 DRAFT 后过账）；
- 结算幂等、可重试；确认数可配置。

**Non-Goals（明确不做）：**
- OTX 核心不存储/管理任何私钥，不实现生产级 KMS/MPC/Fireblocks/Cobo 签名适配器（仅文档说明）；
- 不做链上事件监听/监控、不做资金归集（归集为未来独立变更）；
- 不改动充值（deposit）流程与既有 `ChainQueryPort`/`Web3jChainQueryAdapter`；
- 不新增领域事件总线（本期无领域事件发布，沿用同步编排 + 最终一致性）；
- 不实现广播后超时自动告警与 DRAFT 凭证残留自动清理（归对账/运维变更）。

## Decisions（关键决策）

### D1：架构方向 Form B —— OTX 编排，私钥外部化

OTX 承担交易**编排**（构造、签名调用、广播、确认结算），签名由外部设施（本地 keystore 仅开发、KMS/MPC 等生产）完成。备选方案 A（OTX 持有私钥直接签名）被否决：私钥入核心带来密钥管理与合规风险，违背 Web3 财务中间件定位；备选方案 C（完全外部编排，OTX 只做账务）被否决：会割裂资金流与链上状态的关联，本设计保留 txHash/确认数等链上字段以支撑审计与对账。

### D2：新端口包放置 —— 新建 `domain/chain` 限界上下文

`SignerPort` / `TxBroadcastPort` 放入新包 `io.github.open55.otx.domain.chain.port`，值对象（`SignRequest` / `SignedTx` / `BroadcastResult`）同包。备选方案（放入 `ledger/port` 与 `ChainQueryPort` 同处）被否决：链上交易广播是**横切领域能力**（提现、未来归集、Gas 费支付都复用），不属于总账会计职责；`ChainQueryPort` 留在 `ledger/port` 是历史放置，**本变更不迁移**（避免破坏现有代码与数据），新端口一律进 `chain` 上下文。领域依赖关系：`withdraw` 应用编排依赖 `chain.port` 与 `ledger` 仓储、`withdraw` 领域实体，各限界上下文在 domain 层互不 import，编排全部在应用层。

### D3：结算确认策略 —— 显式 API 为主契约，轮询为可选扩展

- **主契约**：显式 `confirmAndSettle(bizNo)` API，由上游回调 / 对账任务 / 调度任务调用，OTX 保持被动中间件定位（与充值入账"业务系统通知 OTX 入账"模式一致）；
- **可选扩展**：`@Scheduled` 轮询（`PollingSettlementJob`）扫描 BROADCASTED 状态的提现请求自动结算，配置 `otx.chain-tx.poll.enabled` 默认 **false**；
- 理由：主动轮询引入持续的 RPC 依赖、确认阈值与结算时机不可控、重复结算风险高；显式 API 让上游掌控确认时机，实现简单、可测性高。

### D4：凭证创建时机 —— 广播成功后才创建 DRAFT，广播失败不创建

广播是外部副作用，一旦成功不可撤销，因此：
- **广播失败（未上链）**：不创建任何凭证，提现请求置 FAILED，资金保持冻结（由取消路径解冻）；
- **广播成功**：创建 DRAFT 凭证（携带 chainId/chainTxHash/tokenAddress），**不调用 post()**；
- 理由：若广播前就创建凭证，失败后 DRAFT 凭证将无意义地残留；若广播成功后仍立即过账，则链上失败时需冲销 POSTED 凭证（`reverse()` 支持），但会计上"确认前不过账"更严谨，且激活既有 DRAFT 状态机正是本变更目的之一。备选方案（广播成功后立即过账、失败再冲销）被否决：链上失败概率不低，频繁冲销污染凭证生命周期，且"先记账后确认"在审计上弱于"确认后记账"。

### D5：链上确认失败 —— 保留 DRAFT 凭证留痕 + 解冻，不做冲销

`reverse()` 仅适用于 POSTED 凭证（DRAFT 无冲销语义）。确认阶段发现链上失败（回执 0x0）或交易缺失时：
- 提现请求置 FAILED；
- 冻结资金解冻（复用 phase 1 的 unfreeze 能力，记录 UNFREEZE 流水）；
- DRAFT 凭证**保留**作为审计痕迹（记录 txHash 与失败事实），不删除；
- 残留 DRAFT 凭证由后续"对账/凭证清理"变更统一处理（文档记录，不扩展现有状态机）；
- 理由：与项目"过账失败容错 → 对账修复"的最终一致性哲学一致，最小侵入 ledger 聚合。

### D6：新增提现请求聚合根 WithdrawRequestEntity

广播编排状态（目标地址、txHash、状态）需要独立持久化：凭证（journal）在广播前不存在、账户（account）不承载提现链上信息。新增 `io.github.open55.otx.domain.withdraw` 包（withdraw 首次在 domain 层拥有领域实体）：
- `WithdrawRequestEntity`（聚合根，继承 `BaseEntity`）：状态机 PENDING → BROADCASTED → SETTLED / FAILED / CANCELLED；
- `WithdrawRequestRepo` 仓储接口（save / findByBizNo / existsByBizNo / update）；
- 表 `withdraw_request_t`（Flyway V5），`biz_no` 唯一索引（幂等兜底）。
- 备选方案（在 ledger_journal_t 上扩展字段）被否决：凭证 DRAFT 之前（广播前）无行可存，且违背聚合边界。

### D7：幂等设计 —— bizNo 全程唯一

一个提现 bizNo 贯穿冻结 → 广播 → 结算全流程：
- 提现请求表 `uk_biz_no` 唯一索引 + 入口 `existsByBizNo` 前置检查 + DuplicateKeyException → BizIdempotentException 三重保险（沿用既有范式）；
- `confirmAndSettle`：SETTLED 状态直接返回已有结果（幂等成功）；并发结算由乐观锁（version）+ `@Retryable` 保证仅一次生效；
- 凭证侧复用 `createDraftJournal` / `postJournalByBizNo` 的既有幂等语义。

### D8：事务边界 —— 外部 IO 在事务外，账务变更在事务内

| 步骤 | 事务 | 说明 |
|------|------|------|
| 记录提现请求 PENDING | 事务 T1（REQUIRED） | 广播前先落库，崩溃后可恢复 |
| currentNonce / 签名 / 广播 | **无事务** | 纯外部调用，不碰 DB |
| 广播成功后：请求 → BROADCASTED + 创建 DRAFT 凭证 | 事务 T2（REQUIRED） | 广播成功的记录落库 |
| 广播失败：请求 → FAILED | 事务 T3（REQUIRED） | 失败留痕 |
| 结算：扣冻结 + 流水 + 请求 → SETTLED | 事务 T4（**REQUIRES_NEW** + `@Retryable(5)`） | 复用 withdraw-two-phase 变更的 `withdrawAtomic` 结算原子能力 |
| 结算：凭证 post() | 事务 T5（REQUIRES_NEW + `@Retryable(5)`，postJournalByBizNo） | 失败仅 warn，对账修复（与既有提现一致） |
| 取消：解冻 + 流水 + 请求 → CANCELLED | 事务 T6（REQUIRES_NEW + `@Retryable(5)`） | 复用 phase 1 unfreeze 能力 |

跨聚合一致性策略：**最终一致性**——扣款+流水（account/fundflow 聚合）与凭证过账（ledger 聚合）分属独立事务，过账失败不回滚账务，靠对账修复（与充值/既有提现完全一致）。

### D9：签名适配器选择 —— 配置驱动

`otx.chain-tx.signer-adapter` 选择 SignerPort 实现：`local-keystore`（开发，加载本地 keystore 文件，密码经环境变量注入）本期实现；`kms` / `mpc` / `fireblocks` / `cobo` 仅文档预留，**不实现**。广播适配器 `otx.chain-tx.broadcast-adapter` 本期仅 `web3j` 一个实现。配置缺失或未知值启动即报错（fail-fast），避免生产误用 dev 签名器（`LocalKeystoreSigner` 类级 Javadoc 显著标注 dev-only，并在 `dev`/`demo` profile 才可装配）。

## 提现广播生命周期（ASCII 流程）

```
                        ┌────────────────────────────┐
                        │  用户                       │
                        └──────────────┬─────────────┘
                                       │ withdraw(bizNo, toAddress, ...)
                                       ▼
                        ┌────────────────────────────┐
                        │ 冻结金额（phase1 能力，      │
                        │ withdraw-two-phase 变更）    │
                        └──────────────┬─────────────┘
                                       ▼
                        ┌────────────────────────────┐
                        │ 保存 WithdrawRequest        │
                        │  PENDING（事务T1）          │
                        └──────────────┬─────────────┘
                                       ▼
                        ┌────────────────────────────┐
                        │ TxBroadcastPort.currentNonce│
                        │     → SignerPort.sign       │  ← 私钥在外部设施
                        │     → TxBroadcastPort.      │      OTX 核心不接触
                        │        broadcast            │
                        └──────┬──────────────┬──────┘
                               │ 成功           │ 失败
                               ▼                ▼
              ┌─────────────────────┐   ┌─────────────────────┐
              │ T2: BROADCASTED +   │   │ T3: FAILED          │
              │ txHash 落库         │   │ 资金保持冻结          │
              │ + 创建 DRAFT 凭证   │   └──────────┬──────────┘
              │   (chainTxHash)     │              │
              └──────────┬──────────┘              ▼
                         │                 取消路径(cancelWithdraw)
                         ▼                 解冻 → CANCELLED
              ┌─────────────────────┐
              │ confirmAndSettle    │  ← 上游回调/对账/调度
              │  ChainQueryPort.    │
              │   isConfirmed       │
              └──────┬────────┬─────┘
                     │ 是      │ 否
                     ▼        ▼
        ┌────────────────┐  TX_NOT_CONFIRMED_YET
        │ T4: 扣冻结余额  │  （可重试，状态不变）
        │ + 流水(OUT/    │
        │    WITHDRAW)   │
        │ + SETTLED      │
        │ T5: 凭证 post()│
        │   → POSTED     │
        └────────────────┘
                     │ 链上失败(0x0)/交易缺失
                     ▼
        ┌────────────────┐
        │ 请求 → FAILED   │
        │ 解冻 + UNFREEZE │
        │ 流水            │
        │ DRAFT 凭证保留   │
        │ （审计痕迹）     │
        └────────────────┘
```

## 端口定义（Java 签名，domain 层）

```java
// 包：io.github.open55.otx.domain.chain.port

/** 签名请求值对象：一次链上交易的签名入参 */
public class SignRequest {
    private final String chainId;          // 区块链 ID，如 "11155111"
    private final String fromAddress;      // 发送方（平台热钱包）地址
    private final String toAddress;        // 接收方地址
    private final BigInteger amountWei;    // 转账金额（Wei）
    private final String tokenAddress;     // 代币合约地址，原生币转账为空
    private final BigInteger nonce;        // 可选；为空由签名适配器自行获取
    private final BigInteger gasLimit;     // 可选
    private final String data;             // 可选，合约调用 data
}

/** 已签名交易值对象：广播入参 */
public class SignedTx {
    private final String chainId;          // 区块链 ID
    private final String rawTransaction;   // 十六进制签名字符串（0x 前缀）
    private final String fromAddress;      // 发送方地址
    private final String toAddress;        // 接收方地址
}

/** 签名出站端口：实现可插拔（dev: local-keystore；prod: KMS/MPC 等，本期不实现） */
public interface SignerPort {
    SignedTx sign(SignRequest request);
}

/** 广播结果值对象 */
public class BroadcastResult {
    private final String chainId;          // 区块链 ID
    private final String txHash;           // 链上交易哈希
    private final String fromAddress;      // 发送方地址
    private final String toAddress;        // 接收方地址
}

/** 广播出站端口 */
public interface TxBroadcastPort {
    BroadcastResult broadcast(SignedTx signedTx);
    BigInteger currentNonce(String chainId, String fromAddress);  // 辅助：获取当前 nonce
}
```

## 领域模型变更

### WithdrawRequestEntity（新增聚合根，`domain/withdraw`）

```java
public enum WithdrawRequestStatusEnum { PENDING, BROADCASTED, SETTLED, FAILED, CANCELLED }

public class WithdrawRequestEntity extends BaseEntity {
    // 业务字段：uid、bizNo（唯一）、amount、currency、chainId、
    //          toAddress、tokenAddress、txHash、status
    // 领域方法：
    //   create(...)                          → PENDING（构造校验：bizNo/uid/金额/目标地址非空，金额 > 0）
    //   markBroadcasted(txHash)              → BROADCASTED（允许 PENDING|FAILED 重试）
    //   markSettled()                        → SETTLED（仅 BROADCASTED）
    //   markFailed()                         → FAILED（仅 PENDING|BROADCASTED）
    //   cancel()                             → CANCELLED（仅 PENDING|BROADCASTED|FAILED）
    // 所有非法转移抛 WITHDRAW_REQUEST_STATUS_INVALID
}

public interface WithdrawRequestRepo {
    void save(WithdrawRequestEntity entity);
    Optional<WithdrawRequestEntity> findByBizNo(String bizNo);
    boolean existsByBizNo(String bizNo);
    void update(WithdrawRequestEntity entity);
}
```

### LedgerJournalEntity（使用方式变更，状态机激活）

- `createDraftJournal` 通过 `LedgerAssembler.toEntity` + 保存 + **跳过 post()** 生成 DRAFT 凭证（复用既有字段链上字段与唯一索引幂等）；
- `postJournalByBizNo` 加载 DRAFT 凭证 → `journal.post()`（聚合根校验：借贷必平、同户同向唯一、仅 DRAFT）→ update；
- 聚合根方法与不变量**零改动**，状态机全部走既有 `post()`。

## API 变更

### 提现接口（`WithdrawController`，REST）

| 端点 | 方法 | 变更 | 说明 |
|------|------|------|------|
| `POST /withdraw/broadcast` | `broadcast` | **新增**（原"改造 `POST /withdraw`"修订：该端点已被 withdraw-two-phase 变更拆分为 `/withdraw/freeze|settle|unfreeze`，广播编排另立端点） | 请求体 `WithdrawRequestDTO`（在 withdraw-two-phase 四字段基础上新增 `chainId` / `toAddress` / `tokenAddress` / `requiredConfirmations`，可选默认取配置）；不再扣余额立即过账，改为签名+广播；响应 `Result<WithdrawBroadcastResponseDTO>`（bizNo + txHash + status） |
| `POST /withdraw/{bizNo}/confirm-settle` | `confirmAndSettle` | 新增 | 链上确认后结算，响应 `Result<WithdrawSettleResponseDTO>`（bizNo + status + journalStatus） |
| `POST /withdraw/{bizNo}/cancel` | `cancelWithdraw` | 新增 | 解冻取消，响应 `Result<WithdrawStatusResponseDTO>` |
| `GET /withdraw/{bizNo}/status` | `queryStatus` | 新增 | 查询提现请求状态与 txHash，响应 `Result<WithdrawStatusResponseDTO>` |

DTO 后缀遵循 `*RequestDTO` / `*ResponseDTO` 命名（新增 `WithdrawRequestDTO` / `WithdrawBroadcastResponseDTO` / `WithdrawSettleResponseDTO` / `WithdrawStatusResponseDTO`）。

### 应用服务接口变更

```java
public interface WithdrawAppService {
    // 以下三个用例由 withdraw-two-phase 变更提供并保留（冻结/结算/解冻，链下账务）
    String freeze(WithdrawRequestDTO request);
    String withdraw(WithdrawRequestDTO request);      // 结算：扣冻结+流水+过账（confirmAndSettle 复用其原子能力）
    String unfreeze(WithdrawRequestDTO request);      // 解冻：cancelWithdraw 复用其原子能力
    // 本变更新增（链上广播编排）
    WithdrawBroadcastResponseDTO broadcast(WithdrawRequestDTO request);  // 新增（修订：原 design 改造 withdraw 改为另立 broadcast，避免与结算语义冲突）
    WithdrawSettleResponseDTO confirmAndSettle(String bizNo);            // 新增
    WithdrawStatusResponseDTO cancelWithdraw(String bizNo);              // 新增
    WithdrawStatusResponseDTO queryStatus(String bizNo);                 // 新增
}

public interface LedgerAppService {
    // 新增两个方法（幂等语义与 postJournal 一致）
    JournalDetailResponseDTO createDraftJournal(PostJournalRequestDTO req);
    JournalDetailResponseDTO postJournalByBizNo(String bizNo);
}
```

## 数据库 Schema 变更

Flyway `V5__withdraw_request.sql`（新增表，无既有表变更）：

```sql
CREATE TABLE withdraw_request_t (
    id            BIGINT       NOT NULL COMMENT '主键（雪花）',
    uid           BIGINT       NOT NULL COMMENT '用户唯一标识',
    biz_no        VARCHAR(64)  NOT NULL COMMENT '业务流水号（幂等键）',
    amount        DECIMAL(38,18) NOT NULL COMMENT '提现金额',
    currency      VARCHAR(16)  NOT NULL COMMENT '币种',
    chain_id      VARCHAR(32)  NOT NULL COMMENT '区块链 ID',
    to_address    VARCHAR(64)  NOT NULL COMMENT '接收方地址',
    token_address VARCHAR(64)  NULL     COMMENT '代币合约地址（原生币为空）',
    tx_hash       VARCHAR(66)  NULL     COMMENT '链上交易哈希',
    status        VARCHAR(32)  NOT NULL COMMENT '状态：PENDING/BROADCASTED/SETTLED/FAILED/CANCELLED',
    version       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    delete_flag   TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除标记',
    created_by    BIGINT       NULL COMMENT '创建人',
    last_updated_by BIGINT     NULL COMMENT '更新人',
    create_time   DATETIME     NOT NULL COMMENT '创建时间',
    last_update_time DATETIME  NOT NULL COMMENT '更新时间',
    tenant_id     BIGINT       NULL COMMENT '租户 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_no (biz_no),
    KEY idx_uid (uid)
) COMMENT '提现请求（链上广播编排）';
```

持久化四件套：`WithdrawRequestPO`（继承 `BasePO`，枚举字段 String）+ `WithdrawRequestMapper` + `WithdrawRequestConverter`（MapStruct，String ↔ WithdrawRequestStatusEnum）+ `WithdrawRequestRepoImpl`（主键回写，参照 `LedgerJournalRepoImpl` 范式）。

## 配置变更（`otx-starter` application*.yaml）

```yaml
otx:
  chain-tx:
    required-confirmations: 12        # 结算所需链上确认数
    broadcast-adapter: web3j          # 广播适配器选择（本期仅 web3j）
    signer-adapter: local-keystore    # 签名适配器选择（dev-only；生产对接外部签名设施）
    local-keystore:                   # 仅 signer-adapter=local-keystore 时生效
      key-file: classpath:keystore/dev-withdrawer.json
      password: ${OTX_KEYSTORE_PASSWORD:}   # 经环境变量注入，禁止硬编码
    poll:                             # 可选自动结算轮询（默认关闭）
      enabled: false
      interval-ms: 30000
```

新增 `ChainTxProperties`（`@ConfigurationProperties(prefix = "otx.chain-tx")`），在 starter 注册。

## 错误码新增（`BizErrorEnum`）

| 错误码 | 触发场景 |
|--------|----------|
| `TX_SIGN_FAILED` | SignerPort 签名异常 |
| `TX_BROADCAST_FAILED` | 广播失败（RPC 拒绝/超时/网络异常） |
| `TX_NOT_CONFIRMED_YET` | 结算时链上确认数 < 必填确认数（可重试） |
| `TX_CHAIN_FAILED` | 链上交易失败（回执 0x0）或交易缺失 |
| `WITHDRAW_REQUEST_NOT_FOUND` | 按 bizNo 查不到提现请求 |
| `WITHDRAW_REQUEST_STATUS_INVALID` | 状态非法转移/非法操作（如 SETTLED 后再取消） |

## 领域事件

本期**不发布**任何领域事件（与 ledger 既有"当前发布事件：无"一致）。跨聚合一致性通过应用层同步编排 + 最终一致性（对账修复）实现；`WithdrawSettled` / `WithdrawBroadcastFailed` 等事件留待"应用服务编排"变更统一引入。

## 安全说明

- 私钥**绝不**进入 OTX 核心：`SignerPort` 是唯一签名入口，domain/application/infrastructure 均无密钥存储；`LocalKeystoreSigner` 类 Javadoc 显著标注 **dev-only**，仅 dev/demo profile 装配，启动时输出警告日志；keystore 密码经环境变量注入，禁止写死；
- 生产签名选项（文档说明，不实现）：云 KMS（AWS KMS / 阿里云 KMS）、MPC（多方计算）、托管钱包服务（Fireblocks / Cobo）——均为 `SignerPort` 的适配器实现，密钥存于外部设施；
- 广播/签名/结算日志不得打印 rawTransaction 与私钥材料，txHash 可打印（供排查）。

## 测试策略（无真实链、无真实网络）

- **domain 单测**（纯 JUnit 5，零 Mockito）：`WithdrawRequestEntityTest`（状态机全部合法/非法转移、构造校验）、值对象构造测试；
- **application 单测**（Mockito）：`WithdrawAppServiceImplTest` 扩展——mock `SignerPort`/`TxBroadcastPort`/`ChainQueryPort`/`WithdrawRequestRepo`/`AccountAppService`/`LedgerAppService`，覆盖广播成功/失败、结算确认/未确认/链上失败、幂等、取消；`LedgerAppServiceImplTest` 扩展——`createDraftJournal`（DRAFT 落库、幂等、借贷平衡校验）与 `postJournalByBizNo`（DRAFT→POSTED、幂等、状态非法）；
- **infrastructure 单测**：`Web3jTxBroadcastAdapterTest`（mock `Web3j` 客户端，验证 broadcast 发送与 nonce 查询、RPC 故障切换）、`LocalKeystoreSignerTest`（固定测试私钥确定性验证签名——纯本地计算，无网络）、`WithdrawRequestConverterTest`（String ↔ 枚举互转）；
- **集成测试**（otx-starter，连真实 MySQL/Redis，**端口全部 mock**）：`WithdrawBroadcastIntegrationTest`——广播→确认结算→取消全链路、并发结算幂等仅一次生效；
- 严格遵循 `openspec/specs/testing/spec.md`：命名 `methodUnderTest_scenario_expectedBehavior`、`@DisplayName` 中英双语、`@Nested` 分组、无执行顺序依赖、断言精确到错误码与状态。

## Risks / Trade-offs（风险与缓解）

| 风险 | 缓解 |
|------|------|
| 广播成功但本地记录丢失（崩溃窗口：T1 落库后、T2 提交前） | 广播前先落 PENDING；恢复后按 bizNo 查询链上（ChainQueryPort）比对；同一 nonce 重放广播返回相同 txHash |
| 重复广播 / 双花 | 状态机防护（BROADCASTED 后不允许重复广播）+ bizNo 唯一索引 + 广播幂等（nonce 固定） |
| dev keystore 误入生产 | fail-fast 配置校验 + 仅 dev/demo profile 装配 + 启动警告日志 + 类级 dev-only 标注 |
| 链重组（reorg）导致已结算交易回滚 | 必填确认数可配置（默认 12），确认数不足自动拒绝；极端场景由后续对账变更处理 |
| RPC 单点故障 | 复用 `Web3jChainQueryAdapter` 既有多 RPC 按序切换模式，广播适配器同样实现 |
| DRAFT 凭证残留（链上失败后保留） | 文档记录，由对账/凭证清理变更统一处理；不扩展现有状态机（保持最小侵入） |
| 新增广播端点与结算/取消/查询端点（纯增量，无 BREAKING） | 提现端点已在 withdraw-two-phase 变更中拆分为 `/withdraw/freeze|settle|unfreeze`（上游已同步适配），本变更新增 `/withdraw/broadcast` 与 `/{bizNo}/confirm-settle|cancel|status`，既有端点不变 |

## Migration Plan（迁移方案）

1. 部署顺序：V5 迁移（新增 `withdraw_request_t`）→ 应用发布（新端点与配置）→ 上游调用方接入新增端点（`/withdraw/broadcast` 与 `/{bizNo}/confirm-settle|cancel|status`）；withdraw-two-phase 的 `/withdraw/freeze|settle|unfreeze` 端点保持不变；
2. 配置发布：`otx.chain-tx` 段默认值随应用发布（required-confirmations 按链设置；`signer-adapter` 先配 `local-keystore` 仅限开发环境）；
3. 回滚策略：新表与端点均为增量，回滚仅需恢复旧版应用；**注意**：回滚期间已广播的提现请求无法通过新端点结算，需在回滚前完成存量 SETTLED 或人工处理；
4. 既有提现数据兼容：已 POSTED 的历史凭证不受影响，新流程只对**新发起**的提现生效。

## Open Questions（开放问题）

- 广播后长期未确认（卡链）的超时告警与自动处理策略（本期不做，留待运维/对账变更）；
- FAILED 提现请求的业务处置细则（自动重试广播 vs 人工介入）——本期支持从 FAILED 重试广播，最终策略待业务确认；
- 多链多 token 的 Gas 费支付与记账（`FEE`/Gas 科目）——未来变更；
- 生产签名适配器（KMS/MPC）的具体选型与对接方式——待安全与运维评估后另立变更。
