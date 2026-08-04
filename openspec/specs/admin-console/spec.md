# admin-console 规范

## 目的

管理控制台（Admin Console）上下文通过 `/admin` 路由提供基于 Thymeleaf 服务端渲染的管理界面，作为 interface 层的第二个入站适配器与 REST API 并存。它把资金流转、提现单据状态机、流水时间线、凭证分录以页面形式显性化，并提供提现结算/取消操作入口（复用既有应用服务用例），支撑开发者与运维直观理解 OTX 业务。

不属于本上下文：业务能力本身（→ account / withdraw / ledger / fundflow）、模拟上游调用（→ mock-upstream）、REST API 契约（interface 层 controller）。

## 需求

### 需求:系统必须提供管理控制台页面

系统必须（MUST）通过 `/admin` 路由提供基于 Thymeleaf 服务端渲染的管理控制台，作为 interface 层的第二个入站适配器，与既有 REST API 并存且互不影响。控制台面向内部运维与学习场景，页面统一在 `/admin/**` 前缀下。

#### 场景:访问控制台导航页
- **当** 用户以 GET 访问 `/admin`
- **那么** 系统必须返回控制台导航页面，包含账户总览、提现单据、流水时间线、凭证查看四个入口

#### 场景:控制台与 REST API 路由互不冲突
- **当** 控制台页面路由与既有 REST 路由（`/accounts`、`/deposit`、`/withdraw`、`/ledger/journals`）同时存在
- **那么** 既有 REST 端点的请求与响应契约必须保持不变

### 需求:控制台必须展示账户总览

系统必须（MUST）提供账户总览页面（`GET /admin/accounts`），列出全部账户及其资金状态，支撑资金分布的直观理解。

- 数据源：`AccountAppService.listAccounts()`（新增，只读）
- 页面每行必须展示：uid、可用余额、冻结余额
- 账户列表必须按创建时间升序返回

#### 场景:账户总览列出全部账户
- **当** 访问 `GET /admin/accounts`
- **那么** 页面必须展示系统中全部账户，每行包含 uid、可用余额、冻结余额

### 需求:控制台必须展示提现单据及状态机

系统必须（MUST）提供提现单据页面（`GET /admin/withdraws`），列出全部提现请求，直观呈现链上广播编排的状态机与交易哈希。

- 数据源：`WithdrawAppService.listRequests()`（新增，只读）
- 页面每行必须展示：bizNo、uid、金额、币种、目标地址、txHash、状态（PENDING/BROADCASTED/SETTLED/FAILED/CANCELLED）
- 提现单据列表必须按创建时间降序返回（最新在前）

#### 场景:提现单据列表展示状态机
- **当** 访问 `GET /admin/withdraws`
- **那么** 页面必须展示全部提现单据，每行包含 bizNo、金额、txHash 与当前状态；不同状态必须以可区分的标签展示

### 需求:控制台必须提供提现结算与取消操作

系统必须（MUST）在提现单据页面提供"结算"与"取消"操作入口，操作必须复用既有应用服务用例（`confirmAndSettle` / `cancelWithdraw`），不得在控制台层重复实现业务逻辑。

#### 场景:结算提现单据
- **当** 用户在提现单据页对 BROADCASTED 单据提交结算操作（`POST /admin/withdraws/{bizNo}/confirm-settle`）
- **那么** 系统必须调用 `WithdrawAppService.confirmAndSettle(bizNo)`，其幂等/状态机/事务语义与 REST 端点完全一致，操作完成后重定向回单据列表

#### 场景:取消提现单据
- **当** 用户在提现单据页对未结算单据提交取消操作（`POST /admin/withdraws/{bizNo}/cancel`）
- **那么** 系统必须调用 `WithdrawAppService.cancelWithdraw(bizNo)`，操作完成后重定向回单据列表

#### 场景:操作失败展示错误消息
- **当** 结算或取消操作抛出 BizException（如已结算不可取消、确认数不足）
- **那么** 页面必须展示业务错误消息，且不抛出 500；操作无任何部分副作用（复用既有事务语义）

### 需求:控制台必须展示用户流水时间线

系统必须（MUST）提供流水时间线页面（`GET /admin/accounts/{uid}/flows`），展示单个用户完整的资金轨迹。

- 数据源：复用 `FundFlowAppService.findByUid(uid)`（已存在）
- 页面每行必须展示：流水号、金额、方向（IN/OUT）、类型（DEPOSIT/WITHDRAW/FREEZE/UNFREEZE）、变动前余额、变动后余额

#### 场景:流水时间线展示资金轨迹
- **当** 访问 `GET /admin/accounts/{uid}/flows`
- **那么** 页面必须展示该用户的全部流水，每行包含金额、方向、类型与变动前后余额，直观呈现充值→冻结→结算/解冻的轨迹

### 需求:控制台必须展示凭证与分录

系统必须（MUST）提供凭证查看能力：凭证列表（`GET /admin/journals`）与凭证详情（`GET /admin/journals/{bizNo}`）。

- 列表数据源：`LedgerAppService.listJournals()`（新增，只读，仅主表字段：bizNo/bizType/status/totalAmount/postingDate/chainTxHash）
- 详情数据源：复用 `LedgerAppService.findByBizNo(bizNo)`（已存在，含分录）
- 凭证列表必须按创建时间降序返回

#### 场景:凭证列表展示凭证主表字段
- **当** 访问 `GET /admin/journals`
- **那么** 页面必须展示凭证列表（bizNo、业务类型、状态、金额、记账日期、链上交易哈希）

#### 场景:凭证详情展示借贷分录
- **当** 从凭证列表点击进入 `GET /admin/journals/{bizNo}`
- **那么** 页面必须展示该凭证的全部分录（科目、DEBIT/CREDIT、金额、uid），且借方合计等于贷方合计

### 需求:系统必须补齐三项查询能力

系统必须（MUST）提供三个只读查询用例，作为控制台数据源，复用既有聚合根，不改变任何聚合根行为：

- `AccountAppService.listAccounts()`：返回全部账户摘要（uid/可用余额/冻结余额），按创建时间升序
- `WithdrawAppService.listRequests()`：返回全部提现请求视图（bizNo/uid/amount/currency/chainId/toAddress/tokenAddress/txHash/status），按创建时间降序
- `LedgerAppService.listJournals()`：返回凭证主表摘要（bizNo/bizType/status/totalAmount/postingDate/chainTxHash），按创建时间降序，**不含分录**

#### 场景:账户列表查询
- **当** 调用 `AccountAppService.listAccounts()`
- **那么** 必须返回全部账户摘要，且按创建时间升序

#### 场景:提现单据列表查询
- **当** 调用 `WithdrawAppService.listRequests()`
- **那么** 必须返回全部提现请求视图，且按创建时间降序

#### 场景:凭证列表查询
- **当** 调用 `LedgerAppService.listJournals()`
- **那么** 必须返回凭证主表摘要（不含分录），且按创建时间降序

## 领域事件

### 应发布事件（应然）

无（控制台为只读视图 + 复用既有用例，不产生新领域事件）。

### 当前发布事件（实然）

**无。** 与各业务上下文一致。

## 错误码契约

本上下文不新增错误码；控制台操作复用既有业务错误码（`WITHDRAW_REQUEST_NOT_FOUND` / `WITHDRAW_REQUEST_STATUS_INVALID` / `TX_NOT_CONFIRMED_YET` 等），页面展示其 message。另按技术债 DEVT-007，控制台为内部运维工具，页面注明仅限内网/本地使用，上线前须网关认证。
