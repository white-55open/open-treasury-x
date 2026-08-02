# tx-broadcast-orchestration 实施任务清单

## 执行说明

- 依赖前置：本变更依赖 `withdraw-two-phase` 变更先行完成（冻结/解冻能力：`AccountEntity.freezeBalance/unfreezeBalance`、应用层冻结/解冻用例、FREEZE/UNFREEZE 流水）。若冻结能力未就绪，先完成该变更。
- 执行流程（默认）：逐个任务执行 → 验证 → 展示变更摘要 → 用户人工 review → git commit → 确认 continue 后进入下一任务。
- 测试规范：所有测试遵循 `openspec/specs/testing/spec.md`（命名 `methodUnderTest_scenario_expectedBehavior`、`@DisplayName` 中英双语、`@Nested` 分组、中文 Javadoc、具名常量、AAA、零网络零 DB）。

## 1. 公共层（otx-common）：错误码

- [x] 1.1 `BizErrorEnum` 新增 6 个错误码：`TX_SIGN_FAILED`、`TX_BROADCAST_FAILED`、`TX_NOT_CONFIRMED_YET`、`TX_CHAIN_FAILED`、`WITHDRAW_REQUEST_NOT_FOUND`、`WITHDRAW_REQUEST_STATUS_INVALID`（英文 message，沿用现有格式）
  - 测试要求：`BizErrorEnumTest`（若不存在则新建，otx-common）：`allCodes_areUniqueAndNonBlank`——遍历枚举断言 code 非空且全局唯一、message 非空，防止误用重复码

## 2. 领域层（otx-domain）：链上端口契约（新限界上下文 chain）

- [x] 2.1 新建包 `io.github.open55.otx.domain.chain.port`：值对象 `SignRequest`（chainId/fromAddress/toAddress/amountWei 必填自验证，tokenAddress/nonce/gasLimit/data 可选）与 `SignedTx`（rawTransaction 非空自验证）
  - 测试要求：`SignRequestTest`：`construct_withMissingChainId_throwsIllegalArgument`、`construct_withNegativeAmountWei_throwsIllegalArgument`、`construct_withValidFields_succeeds`；`SignedTxTest`：`construct_withBlankRawTransaction_throwsIllegalArgument`
- [x] 2.2 `SignerPort` 接口（`SignedTx sign(SignRequest request)`），中文 Javadoc 注明"实现可插拔，私钥绝不进入 OTX 核心"
  - 测试要求：接口无独立单测；由 5.2 实现类测试与 7.2 应用层 mock 测试共同验证
- [x] 2.3 值对象 `BroadcastResult`（chainId/txHash/fromAddress/toAddress，txHash 非空自验证）+ `TxBroadcastPort` 接口（`BroadcastResult broadcast(SignedTx signedTx)`、`BigInteger currentNonce(String chainId, String fromAddress)`）
  - 测试要求：`BroadcastResultTest`：`construct_withBlankTxHash_throwsIllegalArgument`、`construct_withValidFields_succeeds`

## 3. 领域层（otx-domain）：提现请求聚合根（新限界上下文 withdraw）

- [x] 3.1 新建包 `io.github.open55.otx.domain.withdraw`：`WithdrawRequestStatusEnum`（PENDING/BROADCASTED/SETTLED/FAILED/CANCELLED）+ 聚合根 `WithdrawRequestEntity`（继承 BaseEntity；构造校验 bizNo/uid/amount>0/toAddress/chainId/currency 非空；领域方法 `markBroadcasted(txHash)`【允许 PENDING|FAILED 重试】/`markSettled()`【仅 BROADCASTED】/`markFailed()`【仅 PENDING|BROADCASTED】/`cancel()`【仅 PENDING|BROADCASTED|FAILED】，非法转移抛 `WITHDRAW_REQUEST_STATUS_INVALID`）
  - 测试要求：`WithdrawRequestEntityTest`（纯 JUnit 5，零 Mockito）：`construct_withNullBizNo_throwsBizNoEmpty`、`construct_withNonPositiveAmount_throwsAmountInvalid`、`construct_withBlankToAddress_throwsAddressInvalid`、`markBroadcasted_fromPending_setsBroadcastedAndTxHash`、`markBroadcasted_fromFailed_allowsRetry`、`markBroadcasted_fromSettled_throwsStatusInvalid`、`markSettled_fromBroadcasted_setsSettled`、`markSettled_fromPending_throwsStatusInvalid`、`markFailed_fromPending_setsFailed`、`cancel_fromBroadcasted_setsCancelled`、`cancel_fromSettled_throwsStatusInvalid`
- [x] 3.2 `WithdrawRequestRepo` 仓储接口（save/findByBizNo/existsByBizNo/update，只出现 Entity）
  - 测试要求：接口无独立单测；由 4.2 实现类测试与 7.x 应用层 mock 测试验证

## 4. 基础设施层（otx-infrastructure）：提现请求持久化

- [x] 4.1 `docs/sql/` 新增 Flyway 迁移 `V5__withdraw_request.sql`：建表 `withdraw_request_t`（字段与设计一致：id/uid/biz_no/amount/currency/chain_id/to_address/token_address/tx_hash/status/version/delete_flag/审计字段/tenant_id，`uk_biz_no` 唯一索引、`idx_uid` 普通索引）
  - 测试要求：无单测；由 9.x 集成测试启动 Flyway 建表后 CRUD 验证（断言表存在且 `uk_biz_no` 唯一约束生效——重复插入抛 DuplicateKeyException）
- [x] 4.2 持久化四件套：`WithdrawRequestPO`（继承 BasePO，status 为 String）+ `WithdrawRequestMapper`（BaseMapper）+ `WithdrawRequestConverter`（MapStruct，String ↔ WithdrawRequestStatusEnum）+ `WithdrawRequestRepoImpl`（`@Repository`，save 后主键回写，参照 `LedgerJournalRepoImpl` 范式）
  - 测试要求：`WithdrawRequestConverterTest`（otx-infrastructure）：`convert_entityToPo_mapsStatusToEnumCode`、`convert_poToEntity_mapsEnumCodeToStatus`、`convert_roundTrip_preservesAllBusinessFields`；`WithdrawRequestRepoImplTest`（mock Mapper）：`save_insertsAndWritesBackId`、`findByBizNo_withMatch_returnsEntity`、`existsByBizNo_withMatch_returnsTrue`

## 5. 基础设施层（otx-infrastructure）：链上广播与签名适配器

- [x] 5.1 `Web3jTxBroadcastAdapter`（实现 TxBroadcastPort）：复用 `web3jPool` 多 RPC 按序切换；broadcast 调 `ethSendRawTransaction`（成功返回 txHash，RPC 全失败抛 Web3jRpcException → 应用层转换）；`currentNonce` 调 `ethGetTransactionCount`（PENDING 计数）；未配置链 ID 抛 `LEDGER_CHAIN_NOT_CONFIGURED`
  - 测试要求：`Web3jTxBroadcastAdapterTest`（mock Web3j/Web3jProperties，零网络）：`broadcast_withMockClient_returnsTxHashFromResponse`、`broadcast_allRpcNodesFail_throwsWeb3jRpcException`、`currentNonce_withMockClient_returnsPendingNonce`、`broadcast_withUnconfiguredChain_throwsLedgerChainNotConfigured`
- [x] 5.2 `LocalKeystoreSigner`（实现 SignerPort，**dev-only**：类 Javadoc 显著标注、仅 dev/demo profile 装配、启动警告日志、密码经环境变量注入）：加载 keystore 文件构造交易并本地签名（web3j RawTransaction + 签名），构造 `SignRequest` → 返回 `SignedTx`；文件缺失/密码错误/签名失败抛异常（应用层转换 `TX_SIGN_FAILED`）
  - 测试要求：`LocalKeystoreSignerTest`（固定测试私钥，纯本地计算）：`sign_withFixedKey_producesDeterministicRawTx`（两次签名相同输入产生相同 rawTransaction）、`sign_withMissingKeyFile_throws`、`sign_withWrongPassword_throws`
- [x] 5.3 `ChainTxProperties`（`@ConfigurationProperties(prefix = "otx.chain-tx")`：requiredConfirmations/broadcastAdapter/signerAdapter/localKeystore/poll）+ 在 `otx-starter` 的 application*.yaml 增加 `otx.chain-tx` 配置段（含注释），starter 注册该配置；校验：signer-adapter 未知值启动 fail-fast
  - 测试要求：`ChainTxPropertiesTest`（otx-starter 或 infrastructure）：`bind_yamlValues_mapsToProperties`；`validate_unknownSignerAdapter_throwsAtStartup`（上下文加载测试验证 fail-fast）

## 6. 应用层（otx-application）：ledger DRAFT 状态机激活

- [x] 6.1 `LedgerAppService.createDraftJournal(PostJournalRequestDTO)` + 实现：复用 postJournal 幂等范式（existsByBizNo 前置检查 → self 调用 `createDraftJournalAtomic`【REQUIRES_NEW + @Retryable(5)】→ 构造 Entity 保存后**不调用 post()** → 返回 DRAFT 详情；DuplicateKeyException → BizIdempotentException 幂等返回）
  - 测试要求：`LedgerAppServiceImplTest` 扩展（Mockito）：`createDraftJournal_withValidRequest_returnsDraftJournal`（断言 status=DRAFT 且链上字段透传）、`createDraftJournal_duplicateBizNo_returnsExistingDraft`、`createDraftJournal_unbalancedEntries_throwsLedgerNotBalanced`（断言不持久化）
- [x] 6.2 `LedgerAppService.postJournalByBizNo(String bizNo)` + 实现：加载凭证 → `journal.post()`（聚合根校验）→ update 返回 POSTED 详情；已 POSTED 幂等返回；REVERSED 抛 `LEDGER_JOURNAL_NOT_DRAFT`；不存在抛 `LEDGER_JOURNAL_NOT_FOUND`
  - 测试要求：`LedgerAppServiceImplTest` 扩展：`postJournalByBizNo_withDraftJournal_changesStatusToPosted`、`postJournalByBizNo_alreadyPosted_returnsExistingJournal`、`postJournalByBizNo_notFound_throwsJournalNotFound`、`postJournalByBizNo_reversed_throwsJournalNotDraft`

## 7. 应用层（otx-application）：提现广播编排

- [x] 7.1 新增 DTO：`WithdrawRequestDTO`（uid/amount/bizNo/currency/chainId/toAddress/tokenAddress/requiredConfirmations）、`WithdrawBroadcastResponseDTO`（bizNo/txHash/status）、`WithdrawSettleResponseDTO`（bizNo/status/journalStatus）、`WithdrawStatusResponseDTO`（bizNo/status/txHash/amount/currency）
  - 测试要求：无独立单测；由 7.2~7.4 服务测试断言装配结果
- [x] 7.2 新增 `WithdrawAppService.broadcast(WithdrawRequestDTO)` + 实现（**修订说明**：原 design 的"改造 `withdraw(WithdrawRequestDTO)`"在 withdraw-two-phase 变更后不可行——该方法已承载结算语义（扣冻结+流水+过账）且已实现验证；广播编排另立 `broadcast()` 方法，避免同名不同义冲突。`confirmAndSettle` 直接复用既有 `withdrawAtomic` 结算能力）：幂等前置检查 → 事务 T1 保存 PENDING → 事务外 currentNonce/`SignerPort.sign`/`TxBroadcastPort.broadcast` → 成功：事务 T2 更新 BROADCASTED+txHash 并调 `createDraftJournal`（WITHDRAW_ONCHAIN、**DEBIT USER_FROZEN**/CREDIT WITHDRAW_IN_TRANSIT（修正原 DESIGN USER_AVAILABLE 旧映射，与 withdraw-two-phase D3 决策一致）、携带 chainId/chainTxHash/tokenAddress）→ 返回 BroadcastResponse；失败：事务 T3 置 FAILED 并抛 `TX_SIGN_FAILED`/`TX_BROADCAST_FAILED`（不创建凭证）
  - 测试要求：`WithdrawAppServiceImplTest` 扩展（mock SignerPort/TxBroadcastPort/WithdrawRequestRepo/AccountAppService/LedgerAppService）：`withdraw_broadcastSuccess_returnsTxHashAndCreatesDraftJournal`（断言请求 BROADCASTED、DRAFT 凭证携带 chainTxHash、无余额扣减）、`withdraw_broadcastFailure_throwsTxBroadcastFailedAndMarksFailed`（断言无凭证创建）、`withdraw_signFailure_throwsTxSignFailed`、`withdraw_duplicateBizNo_returnsExistingRequest`
- [x] 7.3 `WithdrawAppService.confirmAndSettle(String bizNo)` + 实现：加载请求（不存在抛 `WITHDRAW_REQUEST_NOT_FOUND`；SETTLED 幂等返回）→ `ChainQueryPort.isConfirmed(chainId, txHash, requiredConfirmations)` → 确认达标：self 结算原子方法（REQUIRES_NEW + @Retryable(5)：扣冻结余额 + OUT/WITHDRAW 流水 + SETTLED）→ `postJournalByBizNo` 过账（失败仅 warn 交由对账）；未确认抛 `TX_NOT_CONFIRMED_YET`；链上失败（回执 0x0/交易缺失）→ 请求 FAILED + 解冻（UNFREEZE 流水）+ 抛 `TX_CHAIN_FAILED`（DRAFT 凭证保留）
  - 测试要求：`WithdrawAppServiceImplTest` 扩展：`confirmAndSettle_confirmed_settlesFrozenAndPostsJournal`（断言扣款金额、流水方向类型、凭证 POSTED、状态 SETTLED）、`confirmAndSettle_notConfirmed_throwsTxNotConfirmedYet`（断言无任何状态变化）、`confirmAndSettle_alreadySettled_returnsSameResult`（断言不重复扣款）、`confirmAndSettle_chainFailed_unfreezesAndMarksFailed`（断言解冻+UNFREEZE 流水+FAILED+DRAFT 凭证保留）、`confirmAndSettle_requestNotFound_throwsWithdrawRequestNotFound`
- [x] 7.4 `WithdrawAppService.cancelWithdraw(bizNo)`（解冻复用 phase1 unfreeze 能力 + CANCELLED；SETTLED 抛 `WITHDRAW_REQUEST_STATUS_INVALID`）与 `queryStatus(bizNo)` 实现
  - 测试要求：`WithdrawAppServiceImplTest` 扩展：`cancelWithdraw_broadcasted_unfreezesAndMarksCancelled`（断言 UNFREEZE 流水）、`cancelWithdraw_settled_throwsStatusInvalid`、`cancelWithdraw_duplicateCall_idempotent`、`queryStatus_withTxHash_returnsStatusWithTxHash`

## 8. 接口层（otx-interface）：REST 端点

- [x] 8.1 `WithdrawController`：新增 `POST /withdraw/broadcast`（请求体 `WithdrawRequestDTO`，响应 `Result<WithdrawBroadcastResponseDTO>`；**修订说明**：原 design 的"改造 `POST /withdraw`"对象已不存在——withdraw-two-phase 变更已将提现拆为 `/withdraw/freeze|settle|unfreeze`，广播端点另立）；新增 `POST /withdraw/{bizNo}/confirm-settle`、`POST /withdraw/{bizNo}/cancel`、`GET /withdraw/{bizNo}/status`（响应均为 Result<...ResponseDTO>，业务错误 HTTP 200 + 业务码）
  - 测试要求：无独立单测（接口层薄）；由 9.x 集成测试覆盖端点路由与响应结构

## 9. 集成测试（otx-starter）：广播编排全链路（端口全 mock）

- [x] 9.1 `WithdrawBroadcastIntegrationTest`（`@SpringBootTest` 连真实 MySQL/Redis + Flyway；`@MockitoBean` SignerPort/TxBroadcastPort/ChainQueryPort 三个出站端口，**无真实链**；注：Spring Boot 4.0.6 已移除 `@MockBean`，用官方替代 `@MockitoBean`）：
  - `broadcast_confirmSettle_fullFlow_endsPosted`：冻结（mock phase1 能力或直调）→ broadcast（mock 广播返回 txHash）→ 断言 DRAFT 凭证与 BROADCASTED → confirmAndSettle（mock isConfirmed=true）→ 断言扣款/流水/POSTED/SETTLED
  - `confirmSettle_concurrentCalls_onlyOneSettlement`：并发两次 confirmAndSettle 断言扣款一次、流水一条
  - `confirmSettle_notConfirmed_retryAfterConfirmed_succeeds`：先 mock false 断言 TX_NOT_CONFIRMED_YET，再 mock true 重试成功
  - `cancelAfterBroadcast_unfreezes`：广播后取消断言解冻流水与 CANCELLED
  - `chainFailed_unfreezesAndKeepsDraftJournal`：mock 回执失败断言解冻 + FAILED + DRAFT 凭证存在

## 10. 最终验证

- [x] 10.1 全量构建与测试：`mvnw.cmd clean install` 通过（6 模块编译零错误）+ `mvnw.cmd test` 全绿（单测零网络零 DB；集成测试需本地 MySQL:3307/Redis:6380 已启动）
- [x] 10.2 规范核对：对照 `openspec/changes/tx-broadcast-orchestration/specs/**` 逐条验收（广播留痕/DRAFT/结算幂等/未确认拒绝/失败解冻/确认数可配置/取消解冻），并核对 `design.md` 中端口签名、错误码、配置项与代码一致

