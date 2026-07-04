ALTER TABLE account_t
    ADD COLUMN account_code VARCHAR(64) NOT NULL DEFAULT 'USER_AVAILABLE' COMMENT '系统账户编码（默认 USER_AVAILABLE，兼容历史数据）' AFTER uid,
    ADD KEY idx_account_code (account_code);
