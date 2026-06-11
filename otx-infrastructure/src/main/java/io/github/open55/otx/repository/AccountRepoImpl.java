package io.github.open55.otx.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.converter.AccountConverter;
import io.github.open55.otx.entity.AccountEntity;
import io.github.open55.otx.mapper.AccountMapper;
import io.github.open55.otx.po.AccountPO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepoImpl implements AccountRepo {
    @Autowired
    private AccountMapper accountMapper;

    @Override
    public AccountEntity getByUid(Long uid) {
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
