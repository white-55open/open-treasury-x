# 任务清单：提现两阶段改造（冻结 → 结算 → 解冻）

按依赖顺序执行：领域层 → 应用层 → 接口层 → 集成测试与最终验证。每个任务均包含测试要求（遵循 `openspec/specs/testing/spec.md`：snake_case 命名、@Nested 分组、中英双语 @DisplayName、具名常量、Javadoc 场景说明）。

## 1. 领域层（otx-domain）

- [x] 1.1 `LedgerBizTypeEnum` 新增 `FREEZE("FREEZE")` 与 `UNFREEZE("UNFREEZE")` 两个枚举值（`otx-domain/.../ledger/enums/LedgerBizTypeEnum.java`），供冻结/解冻凭证标记业务类型。
  **测试要求**：在 `LedgerBizTypeEnumTest`（新增）中验证枚举包含 FREEZE/UNFREEZE 且 code 与字面量一致；回归断言既有 6 个值不受影响。

- [x] 1.2 修复 `AccountEntity.withdraw(amount)`（`otx-domain/.../account/entity/AccountEntity.java`）：① 删除多余的 `availableBalance` 校验分支；② 冻结不足判断改为 `getFrozenBalance().compareTo(amount) < 0` 时抛 `BizException(BizErrorEnum.INSUFFICIENT_FROZEN_BALANCE)`（替换裸 `Assert.isTrue(... > 0)`）；③ `frozenBalance == amount` 允许全额结算；④ 中文 Javadoc 同步修正 @throws 描述。
  **测试要求**：扩展 `AccountEntityTest`（`otx-domain/src/test/.../AccountEntityTest.java`），@Nested 新增 withdraw 分组，覆盖 5 个场景：`withdraw_availableZeroAndFrozenEnough_deductsFrozenOnly`（可用为 0 仍成功）、`withdraw_amountEqualsFrozenBalance_deductsAll`（== 边界）、`withdraw_amountExceedsFrozenBalance_throwsInsufficientFrozenBalance`（断言异常类型为 BizException 且业务码 INSUFFICIENT_FROZEN_BALANCE，不得为 IllegalArgumentException）、`withdraw_nullAmount_throwsWithdrawAmountInvalid`、`withdraw_nonPositiveAmount_throwsWithdrawAmountInvalid`。

## 2. 应用层（otx-application）

- [x] 2.1 新增 `WithdrawRequestDTO`（`otx-application/.../withdraw/dto/request/WithdrawRequestDTO.java`）：字段 `uid` / `bizNo` / `amount` / `currency`，每个字段带多行中文 Javadoc（遵循 config.yaml documentation 铁律），`@Data` 风格。
  **测试要求**：无独立测试（纯 DTO），由 2.3–2.5 的用例单测隐式覆盖；若项目对 DTO 有构造校验约定则补充对应校验测试。

- [x] 2.2 重写 `WithdrawAppService` 入站端口（`otx-application/.../withdraw/service/WithdrawAppService.java`）：移除旧 `withdraw(ChangeAmountRequest)` 签名，新增 `String freeze(WithdrawRequestDTO)` / `String withdraw(WithdrawRequestDTO)` / `String unfreeze(WithdrawRequestDTO)` 三个用例方法，全部返回 bizNo，中文 Javadoc 说明各自业务语义。
  **测试要求**：编译期契约验证（接口方法签名与实现类一致），随 2.3–2.5 用例测试一起验证。

- [x] 2.3 实现 `WithdrawAppServiceImpl.freeze()`（`otx-application/.../withdraw/service/impl/WithdrawAppServiceImpl.java`）：入口校验 + `existsBizNo` 幂等前置检查 → `self.freezeAtomic`（`REQUIRES_NEW` + `@Retryable(5)`）：`accountRepo.findByUid` → `account.freezeBalance(amount)`（不足抛 INSUFFICIENT_BALANCE）→ `accountRepo.update` → 落 FREEZE 流水（direction=OUT，快照=可用余额变动前后）→ 捕获 `DuplicateKeyException` 转 `BizIdempotentException`；随后 `postJournal`（bizType=FREEZE，DEBIT USER_AVAILABLE / CREDIT USER_FROZEN，uid=用户），过账失败仅 WARN 不回滚；日志英文 + 中文注释。
  **测试要求**：重写 `WithdrawAppServiceImplTest`（@Nested 按用例分组），freeze 分组覆盖：成功（断言账户余额变化、`fundFlowAppService.record` 收到 FREEZE/OUT/正确快照、`ledgerAppService.postJournal` 收到 FREEZE 凭证且分录为 DEBIT USER_AVAILABLE / CREDIT USER_FROZEN）、余额不足（INSUFFICIENT_BALANCE 且无流水无过账）、金额非法（FREEZE_AMOUNT_INVALID）、同 bizNo 幂等（前置短路 + BizIdempotentException 兜底两条路径）、过账失败容错（postJournal 抛异常时用例不抛出且余额已变）。

- [x] 2.4 实现 `WithdrawAppServiceImpl.withdraw()` 结算用例：`self.withdrawAtomic` 中 `account.withdraw(amount)`（冻结不足抛 INSUFFICIENT_FROZEN_BALANCE）→ 落 WITHDRAW 流水（direction=OUT，快照=冻结余额变动前后）→ `postJournal`（bizType=WITHDRAW_ONCHAIN，**DEBIT USER_FROZEN** / CREDIT WITHDRAW_IN_TRANSIT，借记侧 uid=用户、贷记侧 uid=null），过账失败 WARN 不回滚；幂等同 2.3。
  **测试要求**：`WithdrawAppServiceImplTest` withdraw 分组覆盖：成功（冻结扣减、可用不变、流水 WITHDRAW/OUT/冻结快照、凭证映射断言 DEBIT 科目为 USER_FROZEN）、冻结不足（INSUFFICIENT_FROZEN_BALANCE）、== 边界成功、可用余额为 0 仍成功、金额非法（WITHDRAW_AMOUNT_INVALID）、幂等、过账失败容错。

- [x] 2.5 实现 `WithdrawAppServiceImpl.unfreeze()` 解冻用例：`self.unfreezeAtomic` 中 `account.unfreezeBalance(amount)`（冻结不足抛 INSUFFICIENT_FROZEN_BALANCE）→ 落 UNFREEZE 流水（direction=IN，快照=可用余额变动前后）→ `postJournal`（bizType=UNFREEZE，DEBIT USER_FROZEN / CREDIT USER_AVAILABLE，uid=用户；独立凭证不冲销原冻结凭证），过账失败 WARN 不回滚；幂等同 2.3。
  **测试要求**：`WithdrawAppServiceImplTest` unfreeze 分组覆盖：成功（冻结释放、可用增加、流水 UNFREEZE/IN、凭证 DEBIT USER_FROZEN / CREDIT USER_AVAILABLE）、冻结不足（INSUFFICIENT_FROZEN_BALANCE）、金额非法（UNFREEZE_AMOUNT_INVALID）、幂等、过账失败容错。

- [x] 2.6 清理死路径：`AccountAppServiceImpl.changeAmountWithFundFlowAtomic` 删除 WITHDRAW case（default 抛 `FUND_FLOW_TYPE_NOT_SUPPORT`）；同步更新 `AccountAppService` 接口及实现的中文 Javadoc（"仅支持充值 DEPOSIT"）。
  **测试要求**：更新 `AccountAppServiceImplTest`：新增/调整 `changeAmountWithFundFlow_withWithdrawType_throwsFundFlowTypeNotSupport`（断言业务码 FUND_FLOW_TYPE_NOT_SUPPORT 且账户/流水无副作用）；回归 DEPOSIT 成功、同 bizNo 幂等、乐观锁重试场景。

## 3. 接口层（otx-interface）

- [x] 3.1 新增 `WithdrawController`（`otx-interface/.../controller/WithdrawController.java`）：`POST /withdraw/freeze`、`POST /withdraw/settle`、`POST /withdraw/unfreeze`，body 为 `WithdrawRequestDTO`，返回 `Result<String>`（data=bizNo）；SpringDoc @Tag/@Operation 中文描述。
  **测试要求**：新增 `WithdrawControllerTest`（@WebMvcTest + MockMvc）：三端点路由可达、请求体映射、返回 Result 包装；错误码透传（如 `Result.code == INSUFFICIENT_FROZEN_BALANCE`）。

## 4. 集成测试与最终验证（otx-starter）

- [x] 4.1 新增 `WithdrawTwoPhaseIntegrationTest`（otx-starter，@SpringBootTest 连真实 MySQL/Redis）：① 冻结→结算全流程：断言账户余额（可用-50/冻结归零）、FREEZE 与 WITHDRAW 流水各一条、两张凭证 POSTED 且分录方向正确（DEBIT USER_FROZEN 结算侧）；② 冻结→解冻全流程：余额复原、UNFREEZE 流水与凭证存在、冻结凭证保持 POSTED；③ 同 bizNo 重复调用幂等（流水与凭证各一条、余额不二次变动）；④ 并发同 bizNo 双线程提交（唯一索引兜底，最终各一条）；⑤ 冻结余额不足拒绝路径。
  **测试要求**：每个 @Test 遵循 testing 规范（snake_case 命名、中英双语 @DisplayName、Javadoc 场景说明）；数据准备使用命名常量。

- [x] 4.2 最终验证：执行 `mvnw.cmd -pl otx-domain,otx-application,otx-interface -am test`（单测全绿）→ `mvnw.cmd -pl otx-starter test`（集成测试全绿，需本地 MySQL:3307/Redis:6380 + Flyway 已迁移）→ `mvnw.cmd package -DskipTests` 全模块打包通过。
  **验收标准**：三处命令均 BUILD SUCCESS，无新增错误码/枚举遗漏编译错误，提现全链路（冻结→结算 / 冻结→解冻）端到端测试通过。
