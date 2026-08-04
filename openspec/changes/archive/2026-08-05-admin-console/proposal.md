# 管理控制台（admin-console）变更提案

## Why（动机）

OTX 是一个 Web3 财务中间件，其价值体现在"被上游业务系统调用后，在账本上留下正确的痕迹"。但中间件天然没有用户界面——所有能力只以 REST API + 数据库表的形式存在，开发者与运维无法直观看到：

- 用户的资金在账户、流水、凭证之间如何流转；
- 每笔提现单据当前处于哪个状态（PENDING → BROADCASTED → SETTLED / FAILED / CANCELLED）；
- 复式记账的凭证长什么样（借贷分录如何构成）。

对不熟悉 Web3 金融业务的开发者而言，这种"不可见性"是理解项目最大的障碍。本变更引入 **Thymeleaf 管理控制台**（`/admin`），把资金流转、单据状态、凭证结构以页面形式显性化；同时补齐控制台所需的三项查询能力（账户列表 / 提现单据列表 / 凭证列表），这些能力也是 README 中"按业务号查询流水""凭证列表查询"等规划能力的落地基础。

## What Changes（设计方案要点）

### 新增：/admin 管理控制台（Thymeleaf 服务端渲染）

| 页面 | 路由 | 内容 | 数据源 |
|------|------|------|--------|
| 首页/导航 | `GET /admin` | 控制台导航入口 | - |
| 账户总览 | `GET /admin/accounts` | 全部账户：uid / 可用余额 / 冻结余额 | `AccountAppService.listAccounts()`（新增） |
| 提现单据 | `GET /admin/withdraws` | 提现单据列表：bizNo / uid / 金额 / 状态机 / txHash；提供结算、取消操作按钮 | `WithdrawAppService.listRequests()`（新增） |
| 流水时间线 | `GET /admin/accounts/{uid}/flows` | 单个用户的资金流水（充值/冻结/结算/解冻），含变动前后余额 | `FundFlowAppService.findByUid()`（已存在） |
| 凭证查看 | `GET /admin/journals` 与 `GET /admin/journals/{bizNo}` | 凭证列表 + 单张凭证的借贷分录 | `LedgerAppService.listJournals()`（新增）+ `findByBizNo()`（已存在） |

- 控制台操作（结算 / 取消提现）**复用既有应用服务用例**（`confirmAndSettle` / `cancelWithdraw`），不在控制台层重复实现业务逻辑；操作后重定向回单据列表。
- 路由隔离：控制台页面统一 `/admin/**`，既有 REST API（`/accounts`、`/deposit`、`/withdraw`、`/ledger/journals`）完全不动。

### 新增：三项查询能力（控制台数据源）

| 查询 | 位置 | 说明 |
|------|------|------|
| 账户列表 | `AccountRepo.findAll()` + `AccountAppService.listAccounts()` | 返回全部账户（或按 uid 模糊过滤），只读 |
| 提现单据列表 | `WithdrawRequestRepo.findByUid/findAll` + `WithdrawAppService.listRequests()` | 返回提现请求及其状态机、txHash |
| 凭证列表 | `LedgerAppService.listJournals()` + 仓储分页查询 | 返回凭证列表（含状态、业务类型、金额），详情复用 findByBizNo |

### 新增：Thymeleaf 依赖

- `otx-starter` 增加 `spring-boot-starter-thymeleaf` 依赖；模板放在 `otx-interface/src/main/resources/templates/admin/`。
- 视图解析由 Spring Boot 自动配置，页面 Controller 位于 `otx-interface/.../admin/` 包。

## Capabilities（能力）

### New Capabilities

- `admin-console`：OTX 管理控制台能力——账户/提现单据/流水/凭证的只读可视化，以及提现单据的结算/取消操作入口；控制台作为 interface 层的第二个入站适配器，与 REST API 共存。

### Modified Capabilities

无（既有 REST API 契约与行为不变）。

## Impact（影响）

- **otx-interface**：新增 `admin` 包（页面 Controller）+ `resources/templates/admin/`（Thymeleaf 模板）。
- **otx-application**：`AccountAppService.listAccounts()`、`WithdrawAppService.listRequests()`、`LedgerAppService.listJournals()` 三个只读用例 + 对应 DTO。
- **otx-domain**：`AccountRepo.findAll()`、`WithdrawRequestRepo.findByUid/findAll`、`LedgerJournalRepo` 列表查询（分页或全量）；纯接口扩展，聚合根零改动。
- **otx-infrastructure**：三个 RepoImpl 增加列表查询实现（LambdaQueryWrapper）。
- **otx-starter**：增加 `spring-boot-starter-thymeleaf` 依赖。
- **数据库**：无 schema 变更（所有数据源字段已存在）。

## 非目标（Non-Goals）

- **不做模拟上游模块**：独立变更 `mock-upstream`（模拟链上事件与业务系统调用），本变更只做"看"与"操作"。
- **不引入认证体系**：控制台沿用项目无认证现状（技术债 DEVT-007：上线前须网关认证/内网隔离），本变更仅在文档注明控制台为内部运维工具。
- **不改动既有 REST API**：`/accounts`、`/deposit`、`/withdraw`、`/ledger/journals` 的请求/响应契约一律不变。
- **不做多租户、不做权限分级**：控制台为单角色内部视图。
- **不实现复杂前端交互**：仅服务端渲染表格/表单/状态标签，不做图表、不做前端框架。
