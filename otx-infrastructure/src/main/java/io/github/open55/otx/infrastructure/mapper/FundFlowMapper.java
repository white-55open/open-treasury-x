package io.github.open55.otx.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.open55.otx.infrastructure.po.FundFlowPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资金流水 MyBatis-Plus 映射器，提供 fund_flow_t 表的基础 CRUD 操作。
 */
@Mapper
public interface FundFlowMapper extends BaseMapper<FundFlowPO> {
}