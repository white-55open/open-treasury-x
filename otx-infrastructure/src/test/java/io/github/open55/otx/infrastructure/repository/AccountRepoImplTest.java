package io.github.open55.otx.infrastructure.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.infrastructure.converter.AccountConverter;
import io.github.open55.otx.infrastructure.mapper.AccountMapper;
import io.github.open55.otx.infrastructure.po.AccountPO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
 * AccountRepoImpl 账户仓储实现单元测试。
 * <p>
 * 覆盖查询、新增、更新路径，验证 Mapper 调用和 PO ↔ Entity 转换。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountRepoImpl 账户仓储单元测试 | AccountRepoImpl unit tests")
class AccountRepoImplTest {

    private static final long TEST_UID = 12345L;

    @BeforeAll
    static void installLambdaCache() {
        // 预装 MyBatis-Plus 列缓存：纯 JUnit 环境下 LambdaQueryWrapper 解析列名需要 TableInfo
        TableInfo tableInfo = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AccountPO.class);
        LambdaUtils.installCache(tableInfo);
    }

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountRepoImpl accountRepo;

    @Captor
    private ArgumentCaptor<AccountPO> poCaptor;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<AccountPO>> wrapperCaptor;

    @Nested
    @DisplayName("findAll 查询全部账户 | find all accounts")
    class FindAll {

        /**
         * 场景：存在账户时按创建时间升序返回全部账户。
         * Scenario: all accounts are returned ordered by create time ascending.
         * 断言 Mapper.selectList 收到含 createTime 升序排序的 wrapper，且 PO 正确转 Entity。
         */
        @Test
        @DisplayName("全部账户按创建时间升序返回 | all accounts ordered by create time ascending")
        void findAll_returnsAllAccountsOrderedByCreateTime() {
            AccountPO po = new AccountPO();
            po.setId(1L);
            po.setUid(TEST_UID);
            po.setAvailableBalance(new BigDecimal("1000"));
            when(accountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(po));

            List<AccountEntity> result = accountRepo.findAll();

            assertEquals(1, result.size());
            assertEquals(TEST_UID, result.get(0).getUid());
            verify(accountMapper).selectList(wrapperCaptor.capture());
            String orderBy = wrapperCaptor.getValue().getExpression().getOrderBy().getSqlSegment();
            assertTrue(orderBy.contains("create_time"));
            assertTrue(orderBy.contains("ASC"));
        }
    }

    @Nested
    @DisplayName("findByUid 按用户查询 | find by uid")
    class FindByUid {

        /**
         * 账户存在时返回转换后的 Entity。
         */
        @Test
        @DisplayName("账户存在返回 Entity")
        void findByUid_whenExists_returnsEntity() {
            AccountPO po = new AccountPO();
            po.setId(1L);
            po.setUid(TEST_UID);
            po.setAvailableBalance(new BigDecimal("1000"));
            when(accountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(po);

            AccountEntity entity = accountRepo.findByUid(TEST_UID);

            assertNotNull(entity);
            assertEquals(TEST_UID, entity.getUid());
        }

        /**
         * 账户不存在时返回 null。
         */
        @Test
        @DisplayName("账户不存在返回 null")
        void findByUid_whenNotExists_returnsNull() {
            when(accountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            AccountEntity entity = accountRepo.findByUid(TEST_UID);

            assertNull(entity);
        }
    }

    @Nested
    @DisplayName("save 保存账户 | save account")
    class Save {

        /**
         * 保存账户时调用 Mapper.insert 并正确转换 Entity → PO。
         */
        @Test
        @DisplayName("保存调用 Mapper.insert")
        void save_insertsPo() {
            AccountEntity entity = AccountEntity.builder()
                    .uid(TEST_UID)
                    .build();

            accountRepo.save(entity);

            verify(accountMapper).insert(poCaptor.capture());
            assertEquals(TEST_UID, poCaptor.getValue().getUid());
        }
    }

    @Nested
    @DisplayName("update 更新账户 | update account")
    class Update {

        /**
         * 更新账户时调用 Mapper.updateById 并正确转换 Entity → PO。
         */
        @Test
        @DisplayName("更新调用 Mapper.updateById")
        void update_updatesById() {
            AccountEntity entity = AccountEntity.builder()
                    .uid(TEST_UID)
                    .availableBalance(new BigDecimal("500"))
                    .build();
            entity.setId(1L);

            accountRepo.update(entity);

            verify(accountMapper).updateById(poCaptor.capture());
            assertEquals(1L, poCaptor.getValue().getId());
            assertEquals(TEST_UID, poCaptor.getValue().getUid());
        }
    }
}
