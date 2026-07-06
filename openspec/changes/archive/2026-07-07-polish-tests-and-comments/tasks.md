# 补全测试与注释 - 任务

## 1. 领域层单元测试（otx-domain）

- [x] 1.1 编写 `AccountEntityTest`：覆盖 freezeBalance（正常/不足/金额非法）、unfreezeBalance（正常/不足/金额非法）、withdraw（正常/不足）、increaseBalance、deposit、create 工厂方法，以及 BaseEntity 继承字段的透传
- [x] 1.2 编写 `FundFlowEntityTest`：覆盖字段正确透传（flowNo/uid/bizNo/amount/direction/type），以及 BaseEntity 继承字段的透传

## 2. 应用层单元测试（otx-application）

- [x] 2.1 编写 `AccountAppServiceImplTest`（Mock AccountRepo + FundFlowAppService）：覆盖 createAccount（新建/幂等返回已有）、increaseBalance（正常/账户不存在）/ freezeBalance（正常/账户不存在）/ getByUid（正常/不存在）、changeAmountWithFundFlow（DEPOSIT/WITHDRAW/幂等早退/唯一键冲突吞掉/类型不支持）
- [x] 2.2 编写 `FundFlowAppServiceImplTest`（Mock FundFlowRepo）：覆盖 record（正常/字段缺失抛异常）、existsBizNo（存在/不存在/空白bizNo）、findByUid（有流水/无流水/uid非法）
- [x] 2.3 编写 `DepositAppServiceImplTest`（Mock AccountAppService）：覆盖 doDeposit 正常路径与异常路径（账户不存在、金额非法）
- [x] 2.4 编写 `WithdrawAppServiceImplTest`（Mock AccountAppService）：覆盖 doWithdraw 正常路径与异常路径（账户不存在、冻结不足、金额非法）

## 3. 基础设施层单元测试——Converter（otx-infrastructure）

- [x] 3.1 编写 `AccountConverterTest`：验证 PO ↔ Entity 全部字段双向映射正确，包括枚举字段 String ↔ Enum 互转
- [x] 3.2 编写 `FundFlowConverterTest`：验证 PO ↔ Entity 双向映射，direction/type 枚举互转
- [x] 3.3 编写 `LedgerJournalConverterTest`：验证 PO ↔ Entity 双向映射
- [x] 3.4 编写 `LedgerEntryConverterTest`：验证 PO ↔ Entity 双向映射

## 4. 基础设施层单元测试——RepoImpl（otx-infrastructure）

- [x] 4.1 编写 `AccountRepoImplTest`（Mock AccountMapper）：验证 findByUid（存在/不存在）、save/update 调用 Mapper 并正确转换
- [x] 4.2 编写 `FundFlowRepoImplTest`（Mock FundFlowMapper）：验证 save、existsByBizNo、findByUid
- [x] 4.3 编写 `LedgerJournalRepoImplTest`（Mock LedgerJournalMapper）：验证 save、findByBizNo、existsByBizNo、update
- [x] 4.4 编写 `LedgerEntryRepoImplTest`（Mock LedgerEntryMapper）：验证 saveBatch、findByJournalId、findByBizNo

## 5. 基础设施层单元测试——技术组件（otx-infrastructure）

- [x] 5.1 编写 `SnowflakeIdGeneratorImplTest`：验证 nextId 生成长度、序列号递增、构造参数校验
- [x] 5.2 编写 `OptimisticLockerExceptionInterceptorTest`：验证 update 影响行数为 0 时抛出 OptimisticLockException
- [x] 5.3 编写 `GlobalExceptionHandlerTest`：验证 BizException / OptimisticLockException / BizIdempotentException / 未预期异常 转换为统一 Result 响应

## 6. 接口层单元测试（otx-interface）

- [x] 6.1 编写 `AccountControllerTest`（MockMvc）：验证 POST /accounts/create/{uid}（成功/uid不合法）、GET /accounts/{uid}（存在/不存在）、POST /accounts/increase（成功/失败）、POST /accounts/freeze（成功/失败）
- [x] 6.2 编写 `DepositControllerTest`（MockMvc）：验证充值端点端到端响应格式
- [x] 6.3 编写 `WithdrawControllerTest`（MockMvc）：验证提现端点端到端响应格式

## 7. 公共层单元测试（otx-common）

- [x] 7.1 编写 `BizExceptionTest`：验证 get(BizErrorEnum) 工厂方法正确设置 code/message，验证继承 RuntimeException
- [x] 7.2 编写 `ResultTest`：验证 success/error 工厂方法、字段正确性、traceId 非空

## 8. Javadoc 注释补全

- [x] 8.1 检查并补全 infrastructure/po/ 下所有 PO 类（BasePO, AccountPO, FundFlowPO, LedgerJournalPO, LedgerEntryPO）的业务字段 Javadoc，确保格式为多行 `/** ... */` 
- [x] 8.2 检查并补全 infrastructure/config/ 下所有 Config 类（MyBatisConfig, DatabaseStartupValidationConfig, SnowflakeIdConfig）的类级与方法级 Javadoc
- [x] 8.3 检查并补全 interface/controller/ 下所有 Controller（AccountController, DepositController, WithdrawController, LedgerController）的公共方法 Javadoc
- [x] 8.4 检查并补全 application 层所有 DTO（CreateAccountRequest, GetAccountResponse, ChangeAmountRequest, CreateFundFlowRequest, PostJournalRequestDTO等）的业务字段 Javadoc
