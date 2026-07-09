## 1. ChangeAmountRequest 新增 currency 字段

- [x] 1.1 在 `ChangeAmountRequest` 新增 `currency` 字段（String 类型，不要求必填），通过 `mvn -pl otx-application compile`

## 2. DepositAppServiceImpl 过账串联

- [x] 2.1 在 `DepositAppServiceImpl` 中注入 `LedgerAppService` 依赖，通过 `mvn -pl otx-application compile`
  - 构造器注入 `private final LedgerAppService ledgerAppService`
  - 包路径在 `io.github.open55.otx.application.ledger.service`

- [x] 2.2 实现 private 方法 `buildDepositJournalRequest(ChangeAmountRequest req)` 构建 `PostJournalRequestDTO`，通过 `mvn -pl otx-application compile`
- [x] 2.3 修改 `deposit()` 方法：在 `accountAppService.changeAmountWithFundFlow()` 成功后调用 `ledgerAppService.postJournal()`，通过 `mvn -pl otx-application compile`

## 3. WithdrawAppServiceImpl 过账串联

- [x] 3.1 在 `WithdrawAppServiceImpl` 中注入 `LedgerAppService` 依赖，通过 `mvn -pl otx-application compile`

- [x] 3.2 实现 private 方法 `buildWithdrawJournalRequest(ChangeAmountRequest req)` 构建 `PostJournalRequestDTO`，通过 `mvn -pl otx-application compile`
  - `bizNo` = `req.getBizNo()`
  - `bizType` = `WITHDRAW_ONCHAIN`
  - `currency` = `req.getCurrency()`
  - `postingDate` = `LocalDate.now()`
  - `description` = `"提现-" + req.getBizNo()`
  - `entries` = 两个 LedgerEntryRequestDTO：
    - DEBIT `USER_AVAILABLE`，金额=req.amount，uid=req.uid
    - CREDIT `WITHDRAW_IN_TRANSIT`，金额=req.amount，uid=null

- [x] 3.3 修改 `withdraw()` 方法：在 `accountAppService.changeAmountWithFundFlow()` 成功后调用 `ledgerAppService.postJournal()`，通过 `mvn -pl otx-application compile`
  - 过账失败时记录 `log.warn("总账过账失败, bizNo={}", bizNo, e)` 不抛异常

## 4. 单元测试

- [x] 4.1 编写 `DepositAppServiceImplTest`，Mock `AccountAppService` 和 `LedgerAppService`，通过 `mvn -pl otx-application test`
- [x] 4.2 编写 `WithdrawAppServiceImplTest`，Mock `AccountAppService` 和 `LedgerAppService`，通过 `mvn -pl otx-application test`

## 5. 集成测试

- [x] 5.1 在 `otx-starter` 集成测试中追加 `BusinessLedgerIntegrationTest`，通过 `mvn -pl otx-starter test -am -Dtest=BusinessLedgerIntegrationTest`
