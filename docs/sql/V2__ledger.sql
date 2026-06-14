DROP TABLE IF EXISTS ledger_entry_t;
CREATE TABLE ledger_entry_t
(
    id               BIGINT          NOT NULL COMMENT '主键ID',
    biz_no           VARCHAR(64)     NOT NULL COMMENT '业务流水号(幂等号)',
    uid              BIGINT          NOT NULL COMMENT '用户ID',
    account_code     VARCHAR(64)     NOT NULL COMMENT '账务账户编码',
    entry_type       VARCHAR(16)     NOT NULL COMMENT '分录类型(DEBIT/CREDIT)',
    amount           DECIMAL(38, 18) NOT NULL COMMENT '记账金额',
    version          BIGINT DEFAULT 0 COMMENT '乐观锁版本号（防并发更新）',
    delete_flag      NVARCHAR(1)     NOT NULL DEFAULT 'N' COMMENT '软删除标识',
    created_by       INT COMMENT '创建人',
    last_updated_by  INT COMMENT '更新人',
    create_time      DATETIME        NOT NULL COMMENT '创建时间',
    last_update_time DATETIME        NOT NULL COMMENT '更新时间',
    tenant_id        nvarchar(100) COMMENT '租户id',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_no_account_entry(biz_no,account_code,entry_type),
    KEY              idx_uid (uid),
    KEY              idx_biz_no (biz_no),
    KEY              idx_account_code (account_code)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='总账分录表';