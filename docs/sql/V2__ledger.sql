DROP TABLE IF EXISTS ledger_journal_t;
CREATE TABLE ledger_journal_t
(
    id               BIGINT           NOT NULL COMMENT '主键ID',
    biz_no           VARCHAR(64)      NOT NULL COMMENT '业务流水号（幂等键，全局唯一）',
    biz_type         VARCHAR(32)      NOT NULL COMMENT '业务类型（DEPOSIT_ONCHAIN / WITHDRAW_ONCHAIN / INTERNAL_TRANSFER / FEE / REVERSAL / ADJUSTMENT / FREEZE / UNFREEZE）',
    posting_date     DATE             NOT NULL COMMENT '记账日期（业务实际发生日期，非系统日期）',
    currency         VARCHAR(16)      NOT NULL COMMENT '币种（如 USDT、ETH）',
    status           VARCHAR(16)      NOT NULL DEFAULT 'DRAFT' COMMENT '凭证状态（DRAFT / POSTED / REVERSED）',
    total_amount     DECIMAL(38, 18)  NOT NULL COMMENT '凭证总金额（所有分录金额之和，DEBIT 总额 = CREDIT 总额）',
    description      VARCHAR(256)     DEFAULT NULL COMMENT '业务描述',
    chain_id         VARCHAR(16)      DEFAULT NULL COMMENT '区块链 ID（链下业务为空）',
    chain_tx_hash    VARCHAR(128)     DEFAULT NULL COMMENT '链上交易哈希（链下业务为空）',
    block_number     BIGINT           DEFAULT NULL COMMENT '区块高度（链下业务为空）',
    token_address    VARCHAR(128)     DEFAULT NULL COMMENT '代币合约地址（链下业务为空）',
    confirmations    INT              DEFAULT NULL COMMENT '链上确认数（链下业务为空）',
    reversed_by     BIGINT           DEFAULT NULL COMMENT '反向凭证业务号，仅 REVERSED 状态时有值',
    version          BIGINT           DEFAULT 0 COMMENT '乐观锁版本号（防并发更新）',
    delete_flag      NVARCHAR(1)      NOT NULL DEFAULT 'N' COMMENT '软删除标识',
    created_by       INT COMMENT '创建人',
    last_updated_by  INT COMMENT '更新人',
    create_time      DATETIME         NOT NULL COMMENT '创建时间',
    last_update_time DATETIME         NOT NULL COMMENT '更新时间',
    tenant_id        NVARCHAR(100) COMMENT '租户 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_no (biz_no),
    KEY              idx_posting_date (posting_date),
    KEY              idx_biz_type (biz_type),
    KEY              idx_chain_tx (chain_tx_hash),
    KEY              idx_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='总账凭证主表（Journal 聚合根）';
