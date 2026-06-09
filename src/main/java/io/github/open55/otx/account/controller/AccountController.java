package io.github.open55.otx.account.controller;

import io.github.open55.otx.account.entity.AccountEntity;
import io.github.open55.otx.account.service.AccountService;
import io.github.open55.otx.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/create/{uid}")
    public Result<String> create(@PathVariable Long uid) {
        return Result.success(String.valueOf(accountService.createAccount(uid)));
    }

    @PostMapping("/increase")
    public Result<Void> increase(@RequestParam Long uid, @RequestParam BigDecimal amount) {
        accountService.increaseBalance(uid, amount);
        return Result.success();
    }

    @PostMapping("/freeze")
    public Result<Void> freeze(@RequestParam Long uid, @RequestParam BigDecimal amount) {
        accountService.freezeBalance(uid, amount);
        return Result.success();
    }

    @GetMapping("/{uid}")
    public Result<AccountEntity> get(@PathVariable Long uid) {
        return Result.success(accountService.getByUid(uid));
    }
}
