package io.github.open55.otx.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.open55.otx.infrastructure.po.FundFlowPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FundFlowMapper extends BaseMapper<FundFlowPO> {
}