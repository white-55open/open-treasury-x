package io.github.open55.otx.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.open55.otx.infrastructure.po.LedgerJournalPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LedgerJournalMapper extends BaseMapper<LedgerJournalPO> {
}
