package io.github.open55.otx.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.open55.otx.account.entity.Account;
import io.github.open55.otx.account.mapper.AccountMapper;
import io.github.open55.otx.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountMapper accountMapper;

    @Override
    public Long createAccount(Long uid) {
        Account exist = accountMapper.selectOne(new QueryWrapper<Account>()
                .eq("uid", uid));
        if (exist != null) {
            return exist.getId();
        }

        Account account = new Account();
        account.setUid(uid);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setFrozenBalance(BigDecimal.ZERO);
//        account.setVersion(0L);
        account.setCreateTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());

        accountMapper.insert(account);

        return account.getId();
    }

    @Override
    public void increaseBalance(Long uid, BigDecimal amount) {

        Account account = getOrThrow(uid);

        account.setAvailableBalance(
                account.getAvailableBalance().add(amount)
        );

        account.setUpdateTime(LocalDateTime.now());

        accountMapper.updateById(account);
    }

    @Override
    public void freezeBalance(Long uid, BigDecimal amount) {

        Account account = getOrThrow(uid);

        // 校验余额
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new RuntimeException("余额不足");
        }

        account.setAvailableBalance(
                account.getAvailableBalance().subtract(amount)
        );

        account.setFrozenBalance(
                account.getFrozenBalance().add(amount)
        );

        account.setUpdateTime(LocalDateTime.now());

        accountMapper.updateById(account);
    }

    @Override
    public Account getByUid(Long uid) {
        return getOrThrow(uid);
    }

    private Account getOrThrow(Long uid) {
        Account account = accountMapper.selectOne(
                new QueryWrapper<Account>().eq("uid", uid)
        );

        if (account == null) {
            throw new RuntimeException("账户不存在");
        }

        return account;
    }
}
