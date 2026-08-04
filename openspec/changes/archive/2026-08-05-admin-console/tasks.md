# 管理控制台（admin-console）实施任务清单

按依赖顺序执行：仓储查询 → 应用层用例 → 页面路由 → 模板 → 验证。每个任务均包含测试要求（遵循 `openspec/specs/testing/spec.md`：snake_case 命名、@Nested 分组、中英双语 @DisplayName、具名常量、Javadoc 场景说明）。

## 1. 仓储层查询能力（otx-domain + otx-infrastructure）

- [x] 1.1 `AccountRepo` 新增 `List<AccountEntity> findAll();`（中文 Javadoc：按创建时间升序返回全部账户）
  - **测试要求**：扩展 `AccountRepoImplTest`：`findAll_returnsAllAccountsOrderedByCreateTime`（mock Mapper 的 selectList，断言传入的 wrapper 含 createTime 升序排序）
- [x] 1.2 `WithdrawRequestRepo` 新增 `List<WithdrawRequestEntity> findByUid(Long uid);` 与 `List<WithdrawRequestEntity> findAll();`（中文 Javadoc：按创建时间降序，最新在前）
  - **测试要求**：扩展 `WithdrawRequestRepoImplTest`：`findByUid_withMatch_returnsRequests`（断言 wrapper 含 uid 条件 + 降序）、`findAll_returnsAllRequestsOrderedByCreateTimeDesc`（断言降序排序）
- [x] 1.3 `LedgerJournalRepo` 新增 `List<LedgerJournalEntity> findAllOrderByCreateTimeDesc();`（中文 Javadoc：只查主表、按创建时间降序）
  - **测试要求**：扩展 `LedgerJournalRepoImplTest`：`findAllOrderByCreateTimeDesc_returnsJournalsSortedDesc`（断言降序排序）
  - 验收：`mvnw.cmd -pl otx-infrastructure -am test` 通过

## 2. 应用层只读用例（otx-application）

- [x] 2.1 新增 DTO（`otx-application/.../admin/dto/` 或各域 dto/response，按现有包结构选择，统一 `*DTO` 后缀）：
  - `AccountSummaryDTO`（uid/availableBalance/frozenBalance）
  - `WithdrawRequestViewDTO`（bizNo/uid/amount/currency/chainId/toAddress/tokenAddress/txHash/status）
  - `JournalSummaryDTO`（bizNo/bizType/status/totalAmount/postingDate/chainTxHash）
  - 每字段多行中文 Javadoc
- [x] 2.2 `AccountAppService.listAccounts()` 返回 `List<AccountSummaryDTO>`：调用 `accountRepo.findAll()`，逐个装配（MapStruct 或手动，与现有 Assembler 风格一致）；中文 Javadoc 注明"只读查询，数据量大时后续引入分页"
  - **测试要求**：扩展 `AccountAppServiceImplTest`：`listAccounts_withAccounts_returnsSummaries`（断言装配字段）、`listAccounts_empty_returnsEmptyList`
- [x] 2.3 `WithdrawAppService.listRequests()` 返回 `List<WithdrawRequestViewDTO>`：调用 `withdrawRequestRepo.findAll()` 装配；中文 Javadoc 同 2.2
  - **测试要求**：扩展 `WithdrawAppServiceImplTest`：`listRequests_withRequests_returnsViewDtos`（断言 bizNo/amount/txHash/status 透传）、`listRequests_empty_returnsEmptyList`
- [x] 2.4 `LedgerAppService.listJournals()` 返回 `List<JournalSummaryDTO>`：调用 `journalRepo.findAllOrderByCreateTimeDesc()` 装配（**不含分录**）；中文 Javadoc 同 2.2
  - **测试要求**：扩展 `LedgerAppServiceImplTest`：`listJournals_withJournals_returnsSummaries`（断言主表字段透传、不含分录）、`listJournals_empty_returnsEmptyList`
  - 验收：`mvnw.cmd -pl otx-application -am test` 通过

## 3. 页面路由（otx-interface）

- [x] 3.1 `otx-interface` 新增 `admin` 包 + `AdminHomeController`（`GET /admin` 返回导航页视图名 `admin/index`）；页面 Controller 统一 `@Controller`（非 @RestController），返回视图名而非 Result
  - **测试要求**：新增 `AdminControllerTest`（standaloneSetup + MockMvc）：`index_returnsNavigationView`（断言视图名与模型）
- [x] 3.2 `AdminWithdrawController`：`GET /admin/withdraws`（模型含提现列表）+ `POST /admin/withdraws/{bizNo}/confirm-settle` + `POST /admin/withdraws/{bizNo}/cancel`（调用 `WithdrawAppService` 对应用例，捕获 BizException 放入模型错误块，成功后 redirect:/admin/withdraws）
  - **测试要求**：扩展 `AdminControllerTest`：`withdrawsPage_returnsList`、`confirmSettle_submitsUseCase`（mock 服务验证调用 + 重定向）、`confirmSettle_whenBizException_rendersError`（mock 抛 BizException 断言模型含错误消息且状态 200）、`cancel_submitsUseCase`
- [x] 3.3 `AdminAccountController`：`GET /admin/accounts`（账户列表）+ `GET /admin/accounts/{uid}/flows`（复用 `FundFlowAppService.findByUid`）
  - **测试要求**：扩展 `AdminControllerTest`：`accountsPage_returnsList`、`flowsPage_returnsFlowsForUid`
- [x] 3.4 `AdminJournalController`：`GET /admin/journals`（凭证列表）+ `GET /admin/journals/{bizNo}`（复用 `LedgerAppService.findByBizNo`）
  - **测试要求**：扩展 `AdminControllerTest`：`journalsPage_returnsList`、`journalDetailPage_returnsEntries`
  - 验收：`mvnw.cmd -pl otx-interface -am test` 通过

## 4. Thymeleaf 模板（otx-interface/src/main/resources/templates/admin/）

- [x] 4.1 模板骨架：`index.html`（导航四入口，中文页面标题与说明）+ 公共片段（页头注明"内部运维工具，仅限内网/本地使用，上线前须网关认证"——DEVT-007 提示）
- [x] 4.2 `accounts.html`：账户表格（uid/可用余额/冻结余额），uid 链接到流水页；状态/字段展示使用 Thymeleaf 表达式，金额格式化
- [x] 4.3 `withdraws.html`：单据表格（bizNo/uid/金额/币种/toAddress/txHash/状态标签），状态用不同 CSS 类区分（PENDING/BROADCASTED/SETTLED/FAILED/CANCELLED）；每行"结算""取消"表单按钮（POST 到对应路由）；错误消息块展示 BizException message
- [x] 4.4 `flows.html`：流水时间线表格（流水号/金额/方向/类型/变动前/变动后），按时间展示
- [x] 4.5 `journals.html` + `journal-detail.html`：凭证列表 + 详情（分录表 + 借贷合计断言展示）
  - **测试要求**：模板无独立单测，由 3.x 视图名测试 + 集成测试（5.1）覆盖渲染；页面文案中文

## 5. 依赖与最终验证

- [x] 5.1 `otx-starter/pom.xml` 增加 `spring-boot-starter-thymeleaf` 依赖；`otx-starter` 集成测试新增 `AdminConsoleIntegrationTest`（@SpringBootTest 连真实 MySQL/Redis，@MockitoBean 出站端口按需）：`adminPages_renderWithData`（GET /admin/accounts、/admin/withdraws、/admin/journals 返回 200 且响应体含关键数据）
  - **测试要求**：遵循 testing 规范；数据隔离用 nanoTime uid/bizNo
- [x] 5.2 全量验证：`mvnw.cmd clean install -DskipTests`（6 模块编译零错误）+ `mvnw.cmd test` 全绿（单测零网络零 DB；集成测试需本地 MySQL:3307/Redis:6380 已启动）+ 手动启动验证 `/admin` 页面可访问
  - **验收标准**：BUILD SUCCESS；`http://localhost:8003/admin` 打开导航页，四个页面均能渲染真实数据

