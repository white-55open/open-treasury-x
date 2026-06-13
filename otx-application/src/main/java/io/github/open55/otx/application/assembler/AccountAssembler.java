package io.github.open55.otx.application.assembler;

import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(builder = @Builder(disableBuilder = true))
public interface AccountAssembler {
    AccountAssembler INSTANCE = Mappers.getMapper(AccountAssembler.class);

    GetAccountResponse entity2AccountResponse(AccountEntity entity);
}
