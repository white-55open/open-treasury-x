package io.github.open55.otx.infrastructure.converter;

import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.infrastructure.po.AccountPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * AccountPO 与 AccountEntity 之间的双向转换器。
 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface AccountConverter {
    AccountConverter INSTANCE = Mappers.getMapper(AccountConverter.class);

    /**
     * AccountPO → AccountEntity，同名字段自动映射。
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    AccountEntity po2Entity(AccountPO po);

    /**
     * List&lt;AccountPO&gt; → List&lt;AccountEntity&gt;，逐条转换。
     *
     * @param poList 持久化对象列表
     * @return 领域实体列表
     */
    List<AccountEntity> po2EntityList(List<AccountPO> poList);

    /**
     * AccountEntity → AccountPO，同名字段自动映射。
     *
     * @param accountEntity 领域实体
     * @return 持久化对象
     */
    AccountPO entity2po(AccountEntity accountEntity);
}
