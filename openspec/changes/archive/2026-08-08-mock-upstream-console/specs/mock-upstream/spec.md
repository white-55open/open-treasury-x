# mock-upstream 规范（增量变更）

> 本文件为 mock-upstream-console 变更的 delta spec。变更完成后合入 `openspec/specs/mock-upstream/spec.md` 主规范。

## ADDED Requirements

### Requirement: 系统必须提供模拟用户个人中心页面

系统必须（MUST）提供基于 Thymeleaf 的模拟用户个人中心页面（`GET /`），作为 mock-upstream 的主交互界面，替代原控制台日志演绎。页面必须包含：当前用户资产卡片（可用/冻结余额）、充值表单、提现表单、一键处理按钮、我的记录列表（充值/提现记录及状态）、操作日志区、用户切换入口。

#### Scenario: 页面展示当前用户资产与记录

- **WHEN** 用户访问 `GET /`
- **THEN** 页面必须展示当前选中用户的可用/冻结余额、全部充值/提现记录（含状态标签）与操作日志（倒序）

#### Scenario: 页面操作全部映射具体用户操作

- **WHEN** 用户在页面执行创建用户、发起充值、申请提现、取消提现
- **THEN** 每个操作必须对应一个现实用户动作，操作日志必须记录业务身份说明、HTTP 端点与 OTX 响应摘要

### Requirement: 系统必须支持多用户创建与切换

系统必须（MUST）支持创建多个模拟用户并在个人中心间切换，各用户的资产与记录相互独立。

#### Scenario: 创建并切换用户

- **WHEN** 用户提交 `POST /users`
- **THEN** 系统必须幂等创建用户（重复创建返回已有用户）并切换为当前用户，资产初始为 0

#### Scenario: 切换已有用户

- **WHEN** 用户提交 `POST /users/{uid}/select`
- **THEN** 系统必须切换当前用户，页面展示该用户的资产与记录

### Requirement: 系统必须将内部处理收敛为一键处理按钮

系统必须（MUST）提供 `POST /internal/process` 一键处理入口：批量推进所有挂起事项（确认中的充值 → 推块达标 → POST /deposit 入账；已冻结提现 → 广播 → 确认 → 结算；取消处理中提现 → 解冻），并在页面展示处理统计。除用户操作（创建/充值/提现/取消）与一键处理外，系统不得自动调用 OTX 接口。

#### Scenario: 一键处理推进全部挂起事项

- **WHEN** 用户点击一键处理且有挂起事项
- **THEN** 系统必须依序处理全部"确认中"充值、"已冻结"提现与"取消处理中"提现，每步调用 OTX 并记录日志

#### Scenario: 启动后不自动调用 OTX

- **WHEN** mock-upstream 应用启动且无任何用户操作
- **THEN** 系统不得发起任何对 OTX 的 HTTP 调用（移除启动自动演绎）

#### Scenario: 一键处理失败可重试

- **WHEN** 某笔挂起事项因 OTX 不可达处理失败
- **THEN** 该事项保持原状态并记录失败日志，其余事项继续处理，下次一键处理可重试

## MODIFIED Requirements

### Requirement: 系统必须模拟链上事件与确认数演化

系统必须（MUST）提供链上事件模拟器：内存维护区块高度与模拟交易，交易确认数随区块推进递增；**区块推进仅在「一键处理」时按需执行（advanceTo），移除定时自动推块**。当交易确认数达到配置阈值时，自动触发 OTX 充值入账（POST /deposit）。

#### Scenario: 充值交易确认达标后自动入账

- **WHEN** 一键处理推进区块使某笔充值交易确认数达到阈值（默认 12）
- **THEN** 模拟器必须自动调用 `POST /deposit`（携带 uid/amount/bizNo/currency/chainId/chainTxHash）
- **THEN** 操作日志必须输出该步骤的业务身份、调用端点与 OTX 响应结果

#### Scenario: 确认数未达标不入账

- **WHEN** 一笔充值交易的确认数尚未达到阈值
- **THEN** 模拟器不得调用充值入账接口，页面展示当前确认数/所需确认数

#### Scenario: 区块仅在处理时推进

- **WHEN** 应用空闲（无一键处理触发）
- **THEN** 模拟链区块高度不得自动增长

### Requirement: 系统必须支持提现业务动作

系统必须（MUST）支持通过个人中心手动触发的提现动作，覆盖"申请提现（冻结）→ 一键处理（广播 → 确认 → 结算）"与"申请提现 → 取消提现（标记）→ 一键处理（解冻）"两条业务路径；每个动作必须输出业务身份说明、调用端点与响应结果到操作日志。

#### Scenario: 申请提现

- **WHEN** 用户在页面提交 `POST /withdrawals`（uid/金额）
- **THEN** 系统必须调用 `POST /withdraw/freeze` 完成冻结，创建提现记录并置状态为已冻结（FROZEN）

#### Scenario: 一键处理完成提现结算

- **WHEN** 用户点击一键处理且存在已冻结提现
- **THEN** 系统必须依次调用 `POST /withdraw/broadcast`、`POST /withdraw/{bizNo}/confirm-settle`
- **THEN** 提现记录置为已结算（SETTLED），操作日志记录每个步骤

#### Scenario: 取消提现标记与解冻

- **WHEN** 用户在记录列表对未结算提现提交 `POST /withdrawals/{bizNo}/cancel`
- **THEN** 系统必须仅置记录为取消处理中（CANCEL_PENDING），不直接调用 OTX
- **WHEN** 再次点击一键处理
- **THEN** 系统必须调用 `POST /withdraw/{bizNo}/cancel` 完成解冻，记录置为已取消（CANCELLED）

#### Scenario: 已结算提现不可取消

- **WHEN** 用户对已结算（SETTLED）提现提交取消
- **THEN** 系统必须拒绝并记录错误日志，不产生任何 OTX 调用

## REMOVED Requirements

### Requirement: 系统必须提供一键演示模式

系统必须（MUST）提供演示模式：应用启动后自动顺序执行充值故事线、提现结算故事线、提现取消故事线，步骤间有可配置延时，便于配合管理控制台实时观察 OTX 落账效果。

该需求由"UI 手动触发 + 一键处理"替代：启动零调用，所有业务动作由用户在个人中心触发，故整体移除。

---

## 领域事件

无变化（模拟器为演示工具，不产生领域事件）。

## 错误码契约

无变化（模拟器只消费 OTX 响应中的业务码用于日志与页面展示）。
