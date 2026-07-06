package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.infrastructure.mapper.FundFlowMapper;
import io.github.open55.otx.infrastructure.po.FundFlowPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FundFlowRepoImpl 资金流水仓储实现单元测试。
 * <p>
 * 覆盖保存、幂等校验、按用户查询路径，验证 Mapper 调用和 PO ↔ Entity 转换。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FundFlowRepoImpl 资金流水仓储单元测试 | FundFlowRepoImpl unit tests")
class FundFlowRepoImplTest {

    private static final long TEST_UID = 12345L;
    private static final String BIZ_NO = "BIZ-20260101-0001";

    @Mock
    private FundFlowMapper fundFlowMapper;

    @InjectMocks
    private FundFlowRepoImpl fundFlowRepo;

    @Captor
    private ArgumentCaptor<FundFlowPO> poCaptor;

    @Nested
    @DisplayName("save 保存流水 | save fund flow")
    class Save {

        /**
         * 保存流水时调用 Mapper.insert 并正确转换 Entity → PO。
         */
        @Test
        @DisplayName("保存调用 Mapper.insert")
        void save_insertsPo() {
            FundFlowEntity entity = new FundFlowEntity();
            entity.setUid(TEST_UID);
            entity.setBizNo(BIZ_NO);
            entity.setAmount(new BigDecimal("100"));
            entity.setDirection(FundFlowDirectionEnum.IN);
            entity.setType(FundFlowTypeEnum.DEPOSIT);

            fundFlowRepo.save(entity);

            verify(fundFlowMapper).insert(poCaptor.capture());
            assertEquals(TEST_UID, poCaptor.getValue().getUid());
            assertEquals(BIZ_NO, poCaptor.getValue().getBizNo());
        }
    }

    @Nested
    @DisplayName("existsByBizNo 幂等校验 | exists by bizNo")
    class ExistsByBizNo {

        /**
         * bizNo 存在时返回 true。
         */
        @Test
        @DisplayName("bizNo 存在返回 true")
        void existsByBizNo_whenExists_returnsTrue() {
            when(fundFlowMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

            assertTrue(fundFlowRepo.existsByBizNo(BIZ_NO));
        }

        /**
         * bizNo 不存在时返回 false。
         */
        @Test
        @DisplayName("bizNo 不存在返回 false")
        void existsByBizNo_whenNotExists_returnsFalse() {
            when(fundFlowMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);

            assertFalse(fundFlowRepo.existsByBizNo(BIZ_NO));
        }
    }

    @Nested
    @DisplayName("findByUid 按用户查询 | find by uid")
    class FindByUid {

        /**
         * 该用户有流水时返回 Entity 列表。
         */
        @Test
        @DisplayName("有流水时返回列表")
        void findByUid_whenHasFlows_returnsList() {
            FundFlowPO po = new FundFlowPO();
            po.setId(1L);
            po.setUid(TEST_UID);
            po.setDirection("IN");
            po.setType("DEPOSIT");
            when(fundFlowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(po));

            List<FundFlowEntity> result = fundFlowRepo.findByUid(TEST_UID);

            assertEquals(1, result.size());
            assertEquals(TEST_UID, result.get(0).getUid());
            assertEquals(FundFlowDirectionEnum.IN, result.get(0).getDirection());
        }

        /**
         * 该用户无流水时返回空列表。
         */
        @Test
        @DisplayName("无流水时返回空列表")
        void findByUid_whenNoFlows_returnsEmpty() {
            when(fundFlowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<FundFlowEntity> result = fundFlowRepo.findByUid(TEST_UID);

            assertTrue(result.isEmpty());
        }
    }
}
