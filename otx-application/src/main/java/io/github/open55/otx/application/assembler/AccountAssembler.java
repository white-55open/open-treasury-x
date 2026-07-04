package io.github.open55.otx.application.assembler;

import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 账户实体与响应 DTO 之间的转换器。
 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface AccountAssembler {
    AccountAssembler INSTANCE = Mappers.getMapper(AccountAssembler.class);

    /**
     * AccountEntity → GetAccountResponse，字段同名字段自动映射。
     *
     * @param entity 账户实体
     * @return 账户响应 DTO
     */
    GetAccountResponse entity2AccountResponse(AccountEntity entity);
}
