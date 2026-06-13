package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.infrastructure.converter.AccountConverter;
import io.github.open55.otx.infrastructure.mapper.AccountMapper;
import io.github.open55.otx.infrastructure.po.AccountPO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepoImpl implements AccountRepo {
    @Resource
    private AccountMapper accountMapper;

    @Override
    public AccountEntity findByUid(Long uid) {
        AccountPO po = accountMapper.selectOne(new LambdaQueryWrapper<AccountPO>()
                .eq(AccountPO::getUid, uid));
        return AccountConverter.INSTANCE.po2Entity(po);
    }

    @Override
    public void save(AccountEntity accountEntity) {
        AccountPO po = AccountConverter.INSTANCE.entity2po(accountEntity);
        accountMapper.insert(po);
    }

    @Override
    public void update(AccountEntity accountEntity) {
        AccountPO po = AccountConverter.INSTANCE.entity2po(accountEntity);
        accountMapper.updateById(po);
    }
}
