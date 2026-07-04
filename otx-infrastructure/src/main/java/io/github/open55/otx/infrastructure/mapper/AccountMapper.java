package io.github.open55.otx.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.open55.otx.infrastructure.po.AccountPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账户 MyBatis-Plus 映射器，提供 account_t 表的基础 CRUD 操作。
 */
@Mapper
public interface AccountMapper extends BaseMapper<AccountPO> {
}