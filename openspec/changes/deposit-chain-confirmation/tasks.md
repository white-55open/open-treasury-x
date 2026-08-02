# 充值入账前链上确认检查（deposit-chain-confirmation）任务清单

## 1. common：新增业务错误码

- [ ] 1.1 在 `otx-common` 的 `BizErrorEnum` 末尾新增 3 个错误码（每个带中文 Javadoc 说明触发场景）：`DEPOSIT_CHAIN_INFO_MISS`（chainId/chainTxHash 缺失或 requiredConfirmations 非法）、`DEPOSIT_TX_NOT_CONFIRMED`（链上交易未达确认数）、`DEPOSIT_CHAIN_QUERY_FAILED`（链上查询失败）
  - **测试要求**：新增/扩展 `BizErrorEnumTest`——`errorEnum_declaresDepositChainErrorCodes_withUniqueCode` 断言 3 个新枚举存在且 code 与枚举名一致、全局无重复 code；`get_unknownCode_returnsNull` 保持既有断言不回归
  - **验收**：`mvnw.cmd -pl otx-common test` 通过

## 2. application：新增 DepositRequestDTO

- [ ] 2.1 新建 `io.github.open55.otx.application.deposit.dto.DepositRequestDTO extends ChangeAmountRequest`，新增字段：`chainId`（String，必填）、`chainTxHash`（String，必填）、`requiredConfirmations`（Integer，可选）、`tokenAddress`（String，可选）；所有字段使用中文多行 Javadoc（遵守 config.yaml documentation 铁律）
  - **测试要求**：新增 `DepositRequestDTOTest`——`depositRequestDTO_inheritsChangeAmountFields_andCarriesChainFields` 断言继承字段（uid/amount/bizNo/currency）透传正常且链字段可读写
  - **验收**：`mvnw.cmd -pl otx-application -am test -Dtest=DepositRequestDTOTest` 通过

## 3. application：确认闸门编排（核心）

- [ ] 3.1 `DepositAppServiceImpl` 注入 `ChainQueryPort`（构造器注入），`deposit()` 签名改为 `deposit(DepositRequestDTO request)`，在 `changeAmountWithFundFlow` 之前新增私有方法 `assertChainEvidence(request)`（chainId/chainTxHash 非空、requiredConfirmations 为空或正数，否则抛 `DEPOSIT_CHAIN_INFO_MISS`）与 `assertChainConfirmed(request, chainQueryPort)`（resolve 确认数：请求覆盖值优先、否则用 `Web3jProperties` 默认 12；`isConfirmed` 返回 false 抛 `DEPOSIT_TX_NOT_CONFIRMED`；捕获 `Web3jRpcException` 转 `DEPOSIT_CHAIN_QUERY_FAILED`）
  - **测试要求**：扩展 `DepositAppServiceImplTest`，`@Nested` 分组「链确认闸门」——`deposit_withConfirmedTx_creditsBalanceAndPostsJournal`（mock isConfirmed=true，断言 changeAmountWithFundFlow 与 postJournal 被调用）；`deposit_withUnconfirmedTx_throwsTxNotConfirmed_andNoSideEffects`（mock false，断言抛 `DEPOSIT_TX_NOT_CONFIRMED` 且 verifyNever 调用 changeAmountWithFundFlow/postJournal）；`deposit_whenChainQueryFails_throwsChainQueryFailed`（mock 抛 Web3jRpcException，断言 `DEPOSIT_CHAIN_QUERY_FAILED`）；`deposit_withMissingChainId_throwsChainInfoMiss`；`deposit_withMissingTxHash_throwsChainInfoMiss`；`deposit_withInvalidRequiredConfirmations_throwsChainInfoMiss`（0/负数）
  - **验收**：`mvnw.cmd -pl otx-application -am test -Dtest=DepositAppServiceImplTest` 通过

- [ ] 3.2 确认数解析：`requiredConfirmations` 请求覆盖值优先，否则使用配置默认值；`@Nested` 分组「确认数解析」测试：`deposit_withoutOverrideUsesDefaultConfirmations`（断言 isConfirmed 收到 12）、`deposit_withOverrideUsesRequestConfirmations`（断言收到 6）
  - **测试要求**：同上扩展 `DepositAppServiceImplTest`，mock `ChainQueryPort` 用 ArgumentCaptor 断言确认数参数
  - **验收**：`mvnw.cmd -pl otx-application -am test -Dtest=DepositAppServiceImplTest` 通过

- [ ] 3.3 `buildDepositJournalRequest` 链字段填充：`chainId`/`chainTxHash` 取自请求；确认通过后调用一次 `queryTxReceipt` 取 `blockNumber` 填充凭证（回执缺失时 blockNumber 置 null 仍允许入账，见 design R3）；`tokenAddress` 取自请求可选字段；私有方法逻辑加中文行内注释
  - **测试要求**：`@Nested` 分组「凭证链字段」——`deposit_withConfirmedTx_journalCarriesChainEvidence`（ArgumentCaptor 捕获 `PostJournalRequestDTO`，断言 chainId/chainTxHash 等于请求值、blockNumber 等于 mock 回执值）；`deposit_whenReceiptMissing_journalKeepsNullBlockNumber`
  - **验收**：`mvnw.cmd -pl otx-application -am test -Dtest=DepositAppServiceImplTest` 通过

- [ ] 3.4 幂等回归：确认闸门执行后进入既有幂等路径；`@Nested` 分组「幂等」——`deposit_withDuplicateBizNo_returnsOriginalBizNoAndNoDoubleCredit`（mock isConfirmed=true、changeAmountWithFundFlow 幂等返回原 bizNo，断言余额与流水不重复）
  - **测试要求**：扩展 `DepositAppServiceImplTest`，测试数据用 `private static final` 具名常量（`TEST_UID`、`TEST_CHAIN_ID`、`TEST_TX_HASH`、`AMOUNT_100`、`DEFAULT_CONFIRMATIONS_12`）
  - **验收**：`mvnw.cmd -pl otx-application -am test` 全量通过（含既有用例不回归）

## 4. interface：控制器契约调整

- [ ] 4.1 `DepositAppService` 接口签名 `deposit(ChangeAmountRequest)` → `deposit(DepositRequestDTO)`；`DepositController.deposit()` 请求体同步改为 `DepositRequestDTO`
  - **测试要求**：更新 `DepositControllerTest`——`deposit_withValidRequest_returnsBizNo` 改用 `DepositRequestDTO` JSON 请求体（含 chainId/chainTxHash），断言返回 `Result<String>` 且 data 为 bizNo；新增 `deposit_withChainEvidenceJson_bindsToDepositRequestDTO` 断言 JSON 绑定链字段
  - **验收**：`mvnw.cmd -pl otx-interface -am test` 通过

## 5. infrastructure + starter：配置项

- [ ] 5.1 `Web3jProperties` 新增 `requiredConfirmations` 字段（int，默认 12，中文 Javadoc：充值入账所需安全确认数）
  - **测试要求**：更新 `Web3jPropertiesTest`——`web3jProperties_requiredConfirmations_defaultsTo12` 断言默认值；`web3jProperties_requiredConfirmations_bindsCustomValue` 断言自定义值绑定
  - **验收**：`mvnw.cmd -pl otx-infrastructure -am test -Dtest=Web3jPropertiesTest` 通过

- [ ] 5.2 `otx-starter/src/main/resources/application-dev.yaml` 的 `web3j:` 段新增 `required-confirmations: 12`（注释说明：充值入账所需安全确认数，请求级 requiredConfirmations 可覆盖）
  - **测试要求**：无独立测试（配置加载由 6.1 验证）
  - **验收**：yaml 语法校验（`mvnw.cmd -pl otx-starter -am package -DskipTests` 可编译通过）

## 6. 全量验证

- [ ] 6.1 运行全量模块测试验证本变更：`mvnw.cmd -pl otx-interface,otx-application,otx-infrastructure -am test`，确认新增用例全部通过、既有用例零回归
  - **测试要求**：所有新增/修改测试类均有中文类 Javadoc、每个 @Test 方法有 `场景：` Javadoc + 中英双语 `@DisplayName`、snake_case 方法名、无魔法数字（遵守 testing/spec.md）
  - **验收**：命令退出码 0，`DepositAppServiceImplTest` / `DepositControllerTest` / `Web3jPropertiesTest` / `BizErrorEnumTest` 全部通过
