CREATE TABLE account
(
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    uid               BIGINT          NOT NULL,
    available_balance DECIMAL(38, 18) NOT NULL DEFAULT 0,
    frozen_balance    DECIMAL(38, 18) NOT NULL DEFAULT 0,
    create_time       DATETIME        NOT NULL,
    update_time       DATETIME        NOT NULL,
    UNIQUE KEY uk_uid (uid)
);