package io.github.open55.otx.converter;

import io.github.open55.otx.entity.AccountEntity;
import io.github.open55.otx.po.AccountPO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(builder = @Builder(disableBuilder = true))
public interface AccountConverter {
    AccountConverter INSTANCE = Mappers.getMapper(AccountConverter.class);

    AccountEntity po2Entity(AccountPO po);

    AccountPO entity2po(AccountEntity accountEntity);
}
