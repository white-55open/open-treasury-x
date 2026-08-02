DROP TABLE IF EXISTS withdraw_request_t;
CREATE TABLE withdraw_request_t
(
    id               BIGINT           NOT NULL COMMENT '主键ID',
    uid              BIGINT           NOT NULL COMMENT '用户唯一标识（提现发起人）',
    biz_no           VARCHAR(64)      NOT NULL COMMENT '业务流水号（幂等键，全局唯一）',
    amount           DECIMAL(38, 18)  NOT NULL COMMENT '提现金额（与冻结金额一致）',
    currency         VARCHAR(16)      NOT NULL COMMENT '币种（如 USDT、ETH）',
    chain_id         VARCHAR(32)      NOT NULL COMMENT '区块链 ID（如 1 以太坊主网、11155111 Sepolia 测试网）',
    to_address       VARCHAR(64)      NOT NULL COMMENT '接收方地址（用户提现目标链上地址）',
    token_address    VARCHAR(64)      DEFAULT NULL COMMENT '代币合约地址（ERC-20 提现必填，原生币提现为空）',
    tx_hash          VARCHAR(66)      DEFAULT NULL COMMENT '链上交易哈希（广播成功后回填，未广播时为空）',
    status           VARCHAR(32)      NOT NULL COMMENT '提现请求状态（PENDING / BROADCASTED / SETTLED / FAILED / CANCELLED）',
    version          BIGINT           DEFAULT 0 COMMENT '乐观锁版本号（防并发更新）',
    delete_flag      NVARCHAR(1)      NOT NULL DEFAULT 'N' COMMENT '软删除标识',
    created_by       INT COMMENT '创建人',
    last_updated_by  INT COMMENT '更新人',
    create_time      DATETIME         NOT NULL COMMENT '创建时间',
    last_update_time DATETIME         NOT NULL COMMENT '更新时间',
    tenant_id        NVARCHAR(100) COMMENT '租户 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_no (biz_no),
    KEY              idx_uid (uid),
    KEY              idx_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='提现请求（链上广播编排）';
