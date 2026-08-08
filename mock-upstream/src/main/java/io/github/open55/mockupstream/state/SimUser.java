package io.github.open55.mockupstream.state;

import lombok.Data;

/**
 * 模拟用户（内存模型）。
 * <p>
 * 等价于现实平台中注册的一个用户：拥有唯一 uid，在 OTX 侧对应一个资金账户。
 * 创建后不可变；多用户通过 {@link RegistryStore} 注册表管理，支持创建与切换。
 */
@Data
public class SimUser {

    /**
     * 用户唯一标识，作为个人中心与 OTX 账户（POST /accounts）的业务键
     */
    private final Long uid;

    /**
     * 用户创建时间戳（毫秒），用于用户列表排序与展示
     */
    private final long createdAt;
}
