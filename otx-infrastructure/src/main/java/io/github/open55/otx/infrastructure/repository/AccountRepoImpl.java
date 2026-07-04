package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.infrastructure.converter.AccountConverter;
import io.github.open55.otx.infrastructure.mapper.AccountMapper;
import io.github.open55.otx.infrastructure.po.AccountPO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * 账户仓储实现，通过 MyBatis-Plus 完成账户持久化操作。
 */
@Repository
public class AccountRepoImpl implements AccountRepo {
    @Resource
    private AccountMapper accountMapper;

    /**
     * 按用户标识查询账户，PO 转 Entity 后返回。
     *
     * @param uid 用户唯一标识
     * @return 账户实体，不存在时返回 null
     */
    @Override
    public AccountEntity findByUid(Long uid) {
        AccountPO po = accountMapper.selectOne(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getUid, uid));
        return AccountConverter.INSTANCE.po2Entity(po);
    }

    /**
     * 持久化新账户，Entity 转 PO 后插入。
     *
     * @param accountEntity 新建的账户实体
     */
    @Override
    public void save(AccountEntity accountEntity) {
        AccountPO po = AccountConverter.INSTANCE.entity2po(accountEntity);
        accountMapper.insert(po);
    }

    /**
     * 更新已有账户，Entity 转 PO 后按 ID 更新（乐观锁）。
     *
     * @param accountEntity 已修改的账户实体
     */
    @Override
    public void update(AccountEntity accountEntity) {
        AccountPO po = AccountConverter.INSTANCE.entity2po(accountEntity);
        accountMapper.updateById(po);
    }
}
