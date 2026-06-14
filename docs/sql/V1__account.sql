DROP TABLE IF EXISTS account_t;
CREATE TABLE IF NOT EXISTS account_t
(
    id                BIGINT PRIMARY KEY COMMENT '主键ID（数据库自增，仅内部使用）',
    uid               BIGINT          NOT NULL COMMENT '用户ID（业务唯一标识）',
    available_balance DECIMAL(38, 18) NOT NULL DEFAULT 0 COMMENT '可用余额（可用于提现/支付）',
    frozen_balance    DECIMAL(38, 18) NOT NULL DEFAULT 0 COMMENT '冻结余额（提现中/锁定资金）',
    version           BIGINT                   DEFAULT 0 COMMENT '乐观锁版本号（防并发更新）',
    delete_flag       NVARCHAR(1)     NOT NULL DEFAULT 'N' COMMENT '软删除标识',
    created_by        INT COMMENT '创建人（系统/管理员ID）',
    last_updated_by        INT COMMENT '更新人（系统/管理员ID）',
    create_time       DATETIME        NOT NULL COMMENT '创建时间',
    last_update_time       DATETIME        NOT NULL COMMENT '更新时间',
    tenant_id         nvarchar(100) COMMENT '租户id',
    UNIQUE KEY uk_uid (uid) COMMENT '用户维度唯一账户索引'
) COMMENT ='用户资金账户表（核心资金账户）';
DROP TABLE IF EXISTS fund_flow_t;
CREATE TABLE IF NOT EXISTS fund_flow_t
(
    id          BIGINT PRIMARY KEY COMMENT '主键ID（数据库自增）',
    flow_no     VARCHAR(64)     NOT NULL COMMENT '流水号（全局唯一，雪花ID生成）',
    uid         BIGINT          NOT NULL COMMENT '用户ID',
    biz_no      VARCHAR(64)     NOT NULL COMMENT '业务幂等号（防重复入账，例如充值订单号/提现单号）',
    amount      DECIMAL(38, 18) NOT NULL COMMENT '变动金额',
    balance_before    DECIMAL(38,18) NOT NULL COMMENT '变更前可用余额',
    balance_after     DECIMAL(38,18) NOT NULL COMMENT '变更后可用余额',
    direction   VARCHAR(16)     NOT NULL COMMENT '资金方向：IN=入账 / OUT=出账',
    type        VARCHAR(32)     NOT NULL COMMENT '资金类型：DEPOSIT/WITHDRAW/FREEZE/UNFREEZE/SETTLE',
    remark            VARCHAR(256)             DEFAULT NULL COMMENT '备注',
    version     BIGINT                   DEFAULT 0 COMMENT '乐观锁版本号（防并发更新）',
    delete_flag NVARCHAR(1)     NOT NULL DEFAULT 'N' COMMENT '软删除标识',
    created_by  INT COMMENT '创建人',
    last_updated_by  INT COMMENT '更新人',
    create_time DATETIME        NOT NULL COMMENT '创建时间',
    last_update_time DATETIME        NOT NULL COMMENT '更新时间',
    tenant_id         nvarchar(100) COMMENT '租户id',
    UNIQUE KEY uk_flow_no (flow_no) COMMENT '流水唯一索引（防重复）',
    UNIQUE KEY uk_biz_no (biz_no) COMMENT '业务幂等索引（防重复扣款/重复入账）'
) COMMENT ='资金流水表（账本核心，用于对账）';