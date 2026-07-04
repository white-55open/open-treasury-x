package io.github.open55.otx.domain.account.repository;

import io.github.open55.otx.domain.account.entity.AccountEntity;

/**
 * 账户仓储接口，定义账户聚合根的持久化契约。
 * <p>
 * 接口定义在领域层（出站端口），实现在基础设施层。
 */
public interface AccountRepo {

    /**
     * 按用户唯一标识查询账户。
     *
     * @param uid 用户唯一标识
     * @return 账户实体，不存在时返回 null
     */
    AccountEntity findByUid(Long uid);

    /**
     * 持久化新账户。
     *
     * @param accountEntity 新建的账户实体
     */
    void save(AccountEntity accountEntity);

    /**
     * 更新已有账户（乐观锁控制）。
     *
     * @param accountEntity 已修改的账户实体
     */
    void update(AccountEntity accountEntity);
}
