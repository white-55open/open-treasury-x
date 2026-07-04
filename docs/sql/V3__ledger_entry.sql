DROP TABLE IF EXISTS ledger_entry_t;
CREATE TABLE ledger_entry_t
(
    id               BIGINT           NOT NULL COMMENT '主键ID',
    journal_id       BIGINT           NOT NULL COMMENT '关联凭证 ID',
    biz_no           VARCHAR(64)      NOT NULL COMMENT '业务流水号（冗余，用于按 bizNo 查询时避免 join）',
    account_code     VARCHAR(64)      NOT NULL COMMENT '会计科目编码（PLATFORM_HOT / DEPOSIT_IN_TRANSIT 等）',
    entry_type       VARCHAR(16)      NOT NULL COMMENT '分录类型（DEBIT 借方 / CREDIT 贷方）',
    amount           DECIMAL(38, 18)  NOT NULL COMMENT '分录金额，必须为正数',
    uid              BIGINT           DEFAULT NULL COMMENT '用户唯一标识（平台类账户此字段可为空）',
    counterparty     VARCHAR(128)     DEFAULT NULL COMMENT '交易对手地址或标识',
    balance_after    DECIMAL(38, 18)  DEFAULT NULL COMMENT '记账后该账户余额',
    remark           VARCHAR(256)     DEFAULT NULL COMMENT '备注说明',
    version          BIGINT           DEFAULT 0 COMMENT '乐观锁版本号（防并发更新）',
    delete_flag      NVARCHAR(1)      NOT NULL DEFAULT 'N' COMMENT '软删除标识',
    created_by       INT COMMENT '创建人',
    last_updated_by  INT COMMENT '更新人',
    create_time      DATETIME         NOT NULL COMMENT '创建时间',
    last_update_time DATETIME         NOT NULL COMMENT '更新时间',
    tenant_id        NVARCHAR(100) COMMENT '租户 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_journal_account_entry (biz_no, account_code, entry_type) COMMENT '幂等键：同业务号同账户同方向不可重复',
    KEY              idx_journal (journal_id),
    KEY              idx_biz_no (biz_no),
    KEY              idx_uid (uid),
    KEY              idx_account_code (account_code)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='总账分录明细表（Entry）';
