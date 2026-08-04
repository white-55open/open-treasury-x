# 管理控制台（admin-console）设计文档

## Context（背景与现状）

OTX 现有 6 模块（common ← domain ← {application, infrastructure} ← interface ← starter），全部能力以 REST API 暴露：

| 模块 | 端点 | 现状 |
|------|------|------|
| account | `POST /accounts/create/{uid}`、`/increase`、`/freeze`、`GET /accounts/{uid}` | 查询仅单账户 |
| deposit | `POST /deposit` | 入账（已含链确认闸门） |
| withdraw | `POST /withdraw/freeze\|settle\|unfreeze\|broadcast`、`/{bizNo}/confirm-settle\|cancel\|status` | 查询仅单单据 |
| ledger | `POST /ledger/journals`、`GET /ledger/journals/{bizNo}` | 查询仅单凭证 |

**查询能力现状（控制台数据源缺口）**：

| 能力 | 现状 | 缺口 |
|------|------|------|
| 账户列表 | `AccountRepo.findByUid` 单查 | ❌ 无列表查询 |
| 提现单据列表 | `WithdrawRequestRepo.save/findByBizNo/existsByBizNo/update` | ❌ 无列表查询 |
| 用户流水 | `FundFlowAppService.findByUid` | ✅ 已存在 |
| 凭证详情 | `LedgerAppService.findByBizNo` | ✅ 已存在 |
| 凭证列表 | 无 | ❌ 无列表查询 |

springdoc（Swagger UI）依赖已存在但不足以呈现业务视图（用户反馈"使用 swagger 也迷惑"），故直接以 Thymeleaf 管理控制台呈现业务。

## Goals / Non-Goals（目标 / 非目标）

**目标：**
- `/admin` 管理控制台：账户总览、提现单据（含状态机与操作）、流水时间线、凭证查看；
- 三项查询能力补齐（账户列表 / 提现列表 / 凭证列表），全部只读、复用既有聚合根；
- 控制台操作（结算/取消）复用既有应用服务用例，零业务逻辑重复；
- 既有 REST API 契约零改动。

**非目标：**
- 不做模拟上游（独立变更 `mock-upstream`）；
- 不引入认证/权限（技术债 DEVT-007，控制台定位内部运维工具）；
- 不做图表与前端框架（纯服务端渲染）；
- 不做多租户、不做单据编辑（仅结算/取消两个既有操作）。

## Decisions（关键决策）

### D1：Thymeleaf 服务端渲染（不引入前端技术栈）

- 管理后台本质是"表格 + 状态标签 + 表单"，服务端渲染完全够用，且对不熟悉前端的开发者零门槛；
- 模板放 `otx-interface/src/main/resources/templates/admin/`，由 Spring Boot 自动配置的 ThymeleafViewResolver 解析；
- 备选（REST + 静态 JS 单页）被否决：需引入前端模板/构建心智，且与服务端渲染相比无业务收益。

### D2：控制台是 interface 层的第二个入站适配器

- 页面 Controller 位于 `otx-interface/.../admin/` 包，与 `controller/`（REST）平级；
- 控制台直接调用 application 层入站端口（`AccountAppService` / `WithdrawAppService` / `LedgerAppService` / `FundFlowAppService`），不直接访问领域层——与 REST 控制器同等约束；
- 路由隔离：页面 `GET /admin/**`，与 REST 路径无冲突（`/accounts` 等前缀不变）。

### D3：查询能力补齐——只读、复用聚合根、仓储接口扩展

| 新增 | 仓储/服务 | 实现要点 |
|------|-----------|----------|
| 账户列表 | `AccountRepo.findAll()` | LambdaQueryWrapper 按 createTime 升序；`AccountAppService.listAccounts()` 返回 List\<AccountSummaryDTO\> |
| 提现单据列表 | `WithdrawRequestRepo.findByUid(Long)` + `findAll()` | 按 createTime 降序（最新在前）；`WithdrawAppService.listRequests()` 返回 List\<WithdrawRequestViewDTO\>（bizNo/uid/amount/currency/chainId/toAddress/txHash/status/requiredConfirmations） |
| 凭证列表 | `LedgerAppService.listJournals()` | 仓储按 createTime 降序查询 Journal 主表（不含分录，列表页不需要）；详情仍走 `findByBizNo`（含分录） |

- 全部只读方法，无事务注解（单条查询）；聚合根零改动；
- 分页：仓库当前无分页对象约定（AGENTS.md 记录），本期列表查询**全量返回**，在 DTO/方法 Javadoc 注明"数据量大时后续引入分页"。

### D4：控制台操作复用既有应用服务（结算/取消）

- 提现单据页的"结算""取消"按钮以 HTML 表单 POST 到 `POST /admin/withdraws/{bizNo}/confirm-settle`、`POST /admin/withdraws/{bizNo}/cancel`；
- 页面 Controller 直接调用 `WithdrawAppService.confirmAndSettle(bizNo)` / `cancelWithdraw(bizNo)`（复用幂等/状态机/事务语义），**不重复实现任何业务逻辑**；
- 操作失败（BizException）：捕获后以错误消息展示在页面（Thymeleaf 错误块），不抛 500。

### D5：安全边界——内部运维工具

- 控制台为内部运维/学习工具，沿用项目无认证现状（DEVT-007 技术债）；
- 文档与页面页脚注明"仅限内网/本地环境使用，上线前须网关认证"；
- 操作端点与 REST 端点同权限模型（无认证），不做额外鉴权（避免在本变更引入半套权限体系）。

### D6：凭证列表不含分录

- 列表页只展示主表字段（bizNo/bizType/status/amount/postingDate/chainTxHash），点击进入详情页走 `findByBizNo` 展示分录；
- 避免列表查询 N+1 加载分录（`ledger_entry_t` 按 journalId 查询），控制台列表保持轻量。

## 页面流程（ASCII）

```
┌──────────────────────────────────────────────────────────┐
│  /admin 导航                                             │
│  ┌───────────┬───────────┬───────────┬───────────┐      │
│  │ 账户总览    │ 提现单据    │ 流水时间线  │ 凭证查看    │      │
│  └───────────┴───────────┴───────────┴───────────┘      │
└──────────┬───────────────────────────────────────────────┘
           │
  ┌────────▼───────────┐        ┌──────────────────────────┐
  │ /admin/accounts     │        │ /admin/withdraws          │
  │ 账户表格：uid/可用/冻结│        │ 单据表格：bizNo/金额/状态/  │
  │ （点击 uid → 流水）   │        │ txHash + [结算][取消]按钮   │
  └────────┬───────────┘        └───────────┬──────────────┘
           │                                │ POST 表单
           ▼                                ▼
  ┌──────────────────┐        ┌──────────────────────────┐
  │ /admin/accounts/ │        │ /admin/withdraws/{bizNo}/ │
  │ {uid}/flows       │        │ confirm-settle | cancel   │
  │ 流水时间线表格     │        │ → 复用 WithdrawAppService │
  │ （方向/类型/前后余额）│        │ → 重定向回单据列表        │
  └──────────────────┘        └──────────────────────────┘

  /admin/journals（凭证列表，主表字段）──点击──▶ /admin/journals/{bizNo}（分录详情）
```

## API / 路由变更（interface 层新增，REST 契约不变）

| 路由 | 方法 | 说明 |
|------|------|------|
| `GET /admin` | 页面 | 控制台导航 |
| `GET /admin/accounts` | 页面 | 账户总览（数据源 `listAccounts`） |
| `GET /admin/accounts/{uid}/flows` | 页面 | 用户流水时间线（数据源 `FundFlowAppService.findByUid`） |
| `GET /admin/withdraws` | 页面 | 提现单据列表（数据源 `listRequests`） |
| `POST /admin/withdraws/{bizNo}/confirm-settle` | 表单提交 | 复用 `WithdrawAppService.confirmAndSettle` |
| `POST /admin/withdraws/{bizNo}/cancel` | 表单提交 | 复用 `WithdrawAppService.cancelWithdraw` |
| `GET /admin/journals` | 页面 | 凭证列表（数据源 `listJournals`） |
| `GET /admin/journals/{bizNo}` | 页面 | 凭证详情 + 分录（数据源 `findByBizNo`） |

## 领域模型变更

- `AccountRepo`：新增 `List<AccountEntity> findAll();`
- `WithdrawRequestRepo`：新增 `List<WithdrawRequestEntity> findByUid(Long uid);` 与 `List<WithdrawRequestEntity> findAll();`
- `LedgerJournalRepo`：新增 `List<LedgerJournalEntity> findAllOrderByCreateTimeDesc();`（只查主表）
- 聚合根（AccountEntity / WithdrawRequestEntity / LedgerJournalEntity）**零改动**。

## 应用层变更

| 新增用例 | 签名 | 说明 |
|----------|------|------|
| 账户列表 | `List<AccountSummaryDTO> listAccounts();` | DTO：uid/availableBalance/frozenBalance |
| 提现列表 | `List<WithdrawRequestViewDTO> listRequests();` | DTO：bizNo/uid/amount/currency/chainId/toAddress/tokenAddress/txHash/status |
| 凭证列表 | `List<JournalSummaryDTO> listJournals();` | DTO：bizNo/bizType/status/totalAmount/postingDate/chainTxHash |

DTO 统一放 `otx-application/.../{domain}/dto/response/`（或 admin 专用包 `application/admin/dto/`，按实现时现有包结构选择，命名遵循 `*DTO` 后缀）。

## 数据库 Schema 变更

**无**。控制台与查询能力全部基于既有表（account_t / fund_flow_t / ledger_journal_t / ledger_entry_t / withdraw_request_t）。

## 错误码

**无新增**。控制台操作复用既有错误码（`WITHDRAW_REQUEST_NOT_FOUND` / `WITHDRAW_REQUEST_STATUS_INVALID` / `TX_NOT_CONFIRMED_YET` 等），页面展示其 message。

## 测试策略

- **仓储单测**（mock Mapper）：`AccountRepoImpl.findAll`、`WithdrawRequestRepoImpl.findByUid/findAll`、`LedgerJournalRepoImpl.findAllOrderByCreateTimeDesc`（查询条件与排序断言）；
- **应用层单测**（Mockito）：`listAccounts` / `listRequests` / `listJournals` 装配与空列表边界；
- **页面路由测试**（otx-interface，standaloneSetup MockMvc）：`/admin/**` 路由可达、Thymeleaf 视图名正确、表单 POST 调应用服务、BizException 错误展示路径；
- **集成测试**（otx-starter，可选）：控制台页面 GET 返回 200 且包含关键数据（连真实 MySQL/Redis）；
- 严格遵循 `openspec/specs/testing/spec.md`：snake_case 命名、`@Nested` 分组、中英双语 `@DisplayName`、具名常量、静态 import。

## Risks / Trade-offs（风险与取舍）

- **[无认证控制台暴露面]** → 定位内部运维工具，文档与页面注明内网/本地使用；DEVT-007 网关认证落地后统一保护（与 REST 同权）。
- **[列表全量返回无分页]** → 当前数据量可控（学习/内部系统）；方法 Javadoc 注明后续引入分页，避免悄然膨胀。
- **[Thymeleaf 与 REST 的 Result 包装共存]** → 页面 Controller 直接读 DTO（不走 Result），仅操作错误捕获 BizException 展示 message；REST 行为零影响。
- **[控制台操作绕过"上游审批"语义]** → 控制台是内部运维工具，操作等价于调用对应 REST 端点；文档注明操作需谨慎（等价于人工调用 API）。

## Migration Plan（迁移与回滚）

- 部署顺序：domain 仓储接口 → infrastructure 实现 → application 用例 → interface 页面 → starter 依赖；纯增量，无数据迁移；
- 回滚：移除 `/admin` 相关 Controller/模板/依赖即可，REST API 与既有功能零影响。
