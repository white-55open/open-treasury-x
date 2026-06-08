package io.github.open55.otx.account.controller;

import io.github.open55.otx.account.service.AccountService;
import io.github.open55.otx.common.result.Result;
import io.github.open55.otx.common.result.Results;
import io.github.open55.otx.account.dto.CreateAccountRequest;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {
    @Resource
    private AccountService accountService;

    @PostMapping
    public Result<Long> create(
            @RequestBody CreateAccountRequest request
    ) {
        return Results.success(
                accountService.createAccount(
                        request.uid()
                )
        );
    }
}
