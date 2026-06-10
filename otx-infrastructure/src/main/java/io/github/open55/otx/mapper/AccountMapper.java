package io.github.open55.otx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.open55.otx.account.entity.AccountDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper extends BaseMapper<AccountDO> {
}