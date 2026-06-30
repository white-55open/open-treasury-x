## 1. 准备工作

- [x] 1.1 在 `openspec/specs/` 下创建 `account/` 与 `fundflow/` 目录
- [x] 1.2 从 `openspec/changes/add-account-fundflow-specs/specs/` 复制 `account/spec.md` 与 `fundflow/spec.md` 到 `openspec/specs/`（archive 步骤会自动执行，但提前验证内容可读）

## 2. account 上下文规范

- [x] 2.1 写 `openspec/changes/add-account-fundflow-specs/specs/account/spec.md` 的"账户创建"需求（包含幂等场景）—— 验证：通过 `#### 场景` 验证创建与重复创建两种行为
- [x] 2.2 补充"账户查询"需求（包含"账户不存在"抛 `ACCOUNT_NOT_EXIST` 场景）—— 验证：场景覆盖 happy path + 异常路径
- [x] 2.3 补充"增加可用余额"需求（含账户不存在与金额非法两个失败场景）—— 验证：3 个场景齐全
- [x] 2.4 补充"冻结余额"需求（含金额超可用与金额非法两个失败场景）—— 验证：不变量 `available + frozen` 守恒在场景中显式表达
- [x] 2.5 补充"解冻余额"需求（金额超冻结的失败场景）—— 验证：场景覆盖 happy + insufficient
- [x] 2.6 补充"提现扣减"需求（金额超冻结、金额非法两个失败场景）—— 验证：场景显式表达"只动 frozen，不动 available"
- [x] 2.7 补充"动账并落流水"需求（DEPOSIT / WITHDRAW / 重复 bizNo / 不支持类型 / 入参缺失 5 个场景）—— 验证：场景覆盖幂等 + 跨聚合语义
- [x] 2.8 补充"并发与乐观锁"需求（含并发冲突 + 重试场景）—— 验证：场景与 `OptimisticLockTest` 行为一致
- [x] 2.9 补充"REST 端点契约"需求（含 3 个端点场景）—— 验证：端点表与 `AccountController` 端点列表一致
- [x] 2.10 写"领域事件"章节（应然 + 实然两个清单）—— 验证：实然清单只声明"无"并指向 DEVT-001
- [x] 2.11 写"错误码契约"章节（不复制枚举值，仅描述触发场景）—— 验证：与 `BizErrorEnum` 一致，无新增/删除
- [x] 2.12 写"入站端口 / 出站端口"接口契约（Java 代码片段）—— 验证：方法签名与 `AccountAppService` / `AccountRepo` 一致

## 3. fundflow 上下文规范

- [x] 3.1 写"流水记录 append-only"需求（DEPOSIT / WITHDRAW / 字段缺失 3 个场景）—— 验证：不变量 `balanceAfter = balanceBefore ± amount` 在场景中显式表达
- [x] 3.2 写"流水幂等"需求（应用层早退 + DB 唯一约束两个场景）—— 验证：场景覆盖 `existsBizNo` 早退和 `DuplicateKeyException` 转换两个分支
- [x] 3.3 写"幂等探测"需求（存在 / 不存在 / 参数非法 3 个场景）—— 验证：场景与 `existsBizNo(bizNo)` 行为一致
- [x] 3.4 写"按用户查询"需求（有流水 / 无流水 / 参数非法 3 个场景）—— 验证：返回 `List<FundFlowEntity>` 而非 `Page`（DEVF-005 标记为待办）
- [x] 3.5 写"枚举与表 type 一致性"需求（含已声明值 / 未声明值两个场景）—— 验证：DEVF-003 在场景中显式标注
- [x] 3.6 写"领域事件"章节（应然 `FundFlowRecorded` + 实然"无"）—— 验证：实然清单只声明"无"并指向 DEVT-001
- [x] 3.7 写"错误码契约"章节（不抛业务错误码，仅 `IllegalArgumentException` / `BizIdempotentException`）—— 验证：明确"本上下文不直接抛 `BizException`"
- [x] 3.8 写"入站端口 / 出站端口"接口契约（Java 代码片段）—— 验证：方法签名与 `FundFlowAppService` / `FundFlowRepo` 一致

## 4. 提案与设计

- [x] 4.1 写 `proposal.md`（Why / What Changes / Capabilities / Impact）—— 验证：Capabilities 章节列出的 capability 与 `specs/` 目录一一对应
- [x] 4.2 写 `design.md` 的"上下文 / 目标与非目标"章节 —— 验证：明确"不修改代码、不修复异味"的边界
- [x] 4.3 写 `design.md` 的"决策"章节（D1 ~ D7 共 7 个决策）—— 验证：每个决策含"理由 + 考虑过的替代"
- [x] 4.4 写 `design.md` 的"风险 / 权衡"章节（5+ 项）—— 验证：每项含 `[风险] → 缓解` 格式
- [x] 4.5 写 `design.md` 的 `Open Items`（DEVT-001 ~ DEVT-007 + DEVF-001 ~ DEVF-005 共 12 项）—— 验证：DEVT 跨上下文共享编号，DEVF 是 fundflow 私有

## 5. 格式与一致性验证

- [x] 5.1 验证 `proposal.md` 通过 `openspec-cn validate` —— 验证：CLI 无错误（"✓ 变更/add-account-fundflow-specs"）
- [x] 5.2 验证 `design.md` 通过 `openspec-cn validate` —— 验证：CLI 无错误
- [x] 5.3 验证 `specs/account/spec.md` 的每个 `### 需求:` 都至少有一个 `#### 场景:`（恰好 4 个井号）—— 验证：9 需求 / 24 场景，场景数 ≥ 需求数
- [x] 5.4 验证 `specs/fundflow/spec.md` 同样满足场景数量约束 —— 验证：5 需求 / 13 场景，场景数 ≥ 需求数
- [x] 5.5 验证 `tasks.md` 复选框格式（`- [ ] X.Y ...`）—— 验证：所有任务行以 `- [ ] ` 开头
- [x] 5.6 交叉核对：spec 中所有"业务错误码"都在 `BizErrorEnum` 中存在 —— 验证：account spec 错误码契约涵盖 14 个枚举（ACCOUNT_NOT_EXIST / AMOUNT_CANT_NULL / BIZ_NO_EMPTY / CONCURRENCY_ERROR / FREEZE_AMOUNT_INVALID / FUND_FLOW_TYPE_CANT_NULL / FUND_FLOW_TYPE_NOT_SUPPORT / INSUFFICIENT_BALANCE / INSUFFICIENT_FROZEN_BALANCE / PARAM_MISS / UID_CANT_NULL / UID_INVALID / UNFREEZE_AMOUNT_INVALID / WITHDRAW_AMOUNT_INVALID）
- [x] 5.7 交叉核对：spec 中提到的所有 Java 类名都存在于存量代码 —— 验证：AccountEntity / FundFlowEntity / AccountRepo / FundFlowRepo / AccountAppService / FundFlowAppService 全部存在

## 6. 归档

- [x] 6.1 确认 `openspec-cn status --change add-account-fundflow-specs` 输出所有产出物 `status: done`
- [x] 6.2 运行 `openspec-cn archive add-account-fundflow-specs --yes` —— 验证：变更从 `changes/` 移到 `changes/archive/2026-06-30-add-account-fundflow-specs/`，`specs/` 目录出现 `account/spec.md` (9332 B) 与 `fundflow/spec.md` (4748 B)
- [x] 6.3 验证 `openspec/specs/account/spec.md` 与 `openspec/specs/fundflow/spec.md` 内容与变更中的 delta spec 一致 —— 验证：归档工具对 `## 需求` 块做了规范化（添加 `## 目的` 与 `## 需求` wrapper、剥除非规范文档章节）；事后手动补回 `## 目的` / 领域事件 / 错误码契约 / 端口契约 章节，与变更中的 delta spec 文档部分完全一致
- [ ] 6.4 提交 git：`git add openspec/ && git commit -m "docs(openspec): add account and fundflow bounded-context main specs"` —— 验证：`git log -1` 显示新提交（需用户明确批准后执行）
