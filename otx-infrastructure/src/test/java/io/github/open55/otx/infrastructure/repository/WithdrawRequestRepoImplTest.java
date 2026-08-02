package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.domain.withdraw.WithdrawRequestEntity;
import io.github.open55.otx.infrastructure.mapper.WithdrawRequestMapper;
import io.github.open55.otx.infrastructure.po.WithdrawRequestPO;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WithdrawRequestRepoImpl 提现请求聚合根仓储实现单元测试。
 * <p>
 * 覆盖保存、按 bizNo 查询、幂等校验路径，验证 Mapper 调用和 PO ↔ Entity 转换。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawRequestRepoImpl 提现请求仓储单元测试 | WithdrawRequestRepoImpl unit tests")
class WithdrawRequestRepoImplTest {

    private static final Long UID = 1L;
    private static final String BIZ_NO = "WITHDRAW-20260101-0001";
    private static final BigDecimal AMOUNT = new BigDecimal("100.5");
    private static final String CURRENCY = "USDT";
    private static final String CHAIN_ID = "11155111";
    private static final String TO_ADDRESS = "0xToAddress00000000000000000000000000000001";

    @Mock
    private WithdrawRequestMapper withdrawRequestMapper;

    @InjectMocks
    private WithdrawRequestRepoImpl withdrawRequestRepo;

    @Captor
    private ArgumentCaptor<WithdrawRequestPO> poCaptor;

    @Nested
    @DisplayName("save 保存提现请求 | save withdraw request")
    class Save {

        /**
         * 保存提现请求时调用 Mapper.insert，PO 的 ID 回写到 Entity。
         */
        @Test
        @DisplayName("保存调用 Mapper.insert 并回写 ID")
        void save_insertsAndWritesBackId() {
            WithdrawRequestEntity entity = WithdrawRequestEntity.create(
                    UID, BIZ_NO, AMOUNT, CURRENCY, CHAIN_ID, TO_ADDRESS, null);
            when(withdrawRequestMapper.insert(any(WithdrawRequestPO.class))).thenAnswer(invocation -> {
                WithdrawRequestPO po = invocation.getArgument(0);
                po.setId(100L);
                return 1;
            });

            withdrawRequestRepo.save(entity);

            verify(withdrawRequestMapper).insert(poCaptor.capture());
            assertEquals(100L, entity.getId());
            assertEquals(BIZ_NO, poCaptor.getValue().getBizNo());
        }
    }

    @Nested
    @DisplayName("findByBizNo 按业务号查询 | find by bizNo")
    class FindByBizNo {

        /**
         * 提现请求存在时返回 Optional.of(Entity)，状态码转为枚举。
         */
        @Test
        @DisplayName("提现请求存在返回 Optional.of")
        void findByBizNo_withMatch_returnsEntity() {
            WithdrawRequestPO po = new WithdrawRequestPO();
            po.setId(1L);
            po.setUid(UID);
            po.setBizNo(BIZ_NO);
            po.setAmount(AMOUNT);
            po.setCurrency(CURRENCY);
            po.setChainId(CHAIN_ID);
            po.setToAddress(TO_ADDRESS);
            po.setStatus("PENDING");
            when(withdrawRequestMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(po);

            Optional<WithdrawRequestEntity> result = withdrawRequestRepo.findByBizNo(BIZ_NO);

            assertTrue(result.isPresent());
            assertEquals(BIZ_NO, result.get().getBizNo());
            assertEquals(UID, result.get().getUid());
        }

        /**
         * 提现请求不存在时返回 Optional.empty()。
         */
        @Test
        @DisplayName("提现请求不存在返回 Optional.empty")
        void findByBizNo_withNoMatch_returnsEmpty() {
            when(withdrawRequestMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            Optional<WithdrawRequestEntity> result = withdrawRequestRepo.findByBizNo(BIZ_NO);

            assertFalse(result.isPresent());
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
        void existsByBizNo_withMatch_returnsTrue() {
            when(withdrawRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertTrue(withdrawRequestRepo.existsByBizNo(BIZ_NO));
        }

        /**
         * bizNo 不存在时返回 false。
         */
        @Test
        @DisplayName("bizNo 不存在返回 false")
        void existsByBizNo_withNoMatch_returnsFalse() {
            when(withdrawRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertFalse(withdrawRequestRepo.existsByBizNo(BIZ_NO));
        }
    }
}
