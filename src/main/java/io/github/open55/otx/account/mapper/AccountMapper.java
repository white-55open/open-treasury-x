package io.github.open55.otx.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.open55.otx.account.entity.Account;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {
}