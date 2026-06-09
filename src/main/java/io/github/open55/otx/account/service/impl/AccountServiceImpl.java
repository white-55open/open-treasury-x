package io.github.open55.otx.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.open55.otx.account.entity.AccountEntity;
import io.github.open55.otx.account.mapper.AccountMapper;
import io.github.open55.otx.account.service.AccountService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountMapper accountMapper;

    @Override
    public Long createAccount(Long uid) {
        AccountEntity exist = accountMapper.selectOne(new QueryWrapper<AccountEntity>()
                .eq("uid", uid));
        if (exist != null) {
            return exist.getId();
        }

        AccountEntity accountEntity = new AccountEntity();
        accountEntity.setUid(uid);
        accountEntity.setAvailableBalance(BigDecimal.ZERO);
        accountEntity.setFrozenBalance(BigDecimal.ZERO);

        accountMapper.insert(accountEntity);

        return accountEntity.getId();
    }

    @Override
    public void increaseBalance(Long uid, BigDecimal amount) {

        AccountEntity accountEntity = getOrThrow(uid);
        accountEntity.setAvailableBalance(
                accountEntity.getAvailableBalance().add(amount)
        );

        accountMapper.updateById(accountEntity);
    }

    @Override
    public void freezeBalance(Long uid, BigDecimal amount) {

        AccountEntity accountEntity = getOrThrow(uid);

        // 校验余额
        if (accountEntity.getAvailableBalance().compareTo(amount) < 0) {
            throw new RuntimeException("余额不足");
        }

        accountEntity.setAvailableBalance(
                accountEntity.getAvailableBalance().subtract(amount)
        );

        accountEntity.setFrozenBalance(
                accountEntity.getFrozenBalance().add(amount)
        );

        accountMapper.updateById(accountEntity);
    }

    @Override
    public AccountEntity getByUid(Long uid) {
        return getOrThrow(uid);
    }

    private AccountEntity getOrThrow(Long uid) {
        AccountEntity accountEntity = accountMapper.selectOne(
                new QueryWrapper<AccountEntity>().eq("uid", uid)
        );

        if (accountEntity == null) {
            throw BizException.get(BizErrorEnum.ACCOUNT_NOT_EXIST);
        }

        return accountEntity;
    }
}
