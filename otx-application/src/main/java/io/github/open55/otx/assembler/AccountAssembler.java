package io.github.open55.otx.assembler;

import io.github.open55.otx.account.dto.response.GetAccountResponse;
import io.github.open55.otx.entity.AccountEntity;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(builder = @Builder(disableBuilder = true))
public interface AccountAssembler {
    AccountAssembler INSTANCE = Mappers.getMapper(AccountAssembler.class);

    GetAccountResponse entity2AccountResponse(AccountEntity entity);
}
