package io.github.open55.otx.account.controller;

import io.github.open55.otx.account.entity.Account;
import io.github.open55.otx.account.service.AccountService;
import io.github.open55.otx.common.result.Result;
import io.github.open55.otx.common.result.Results;
import io.github.open55.otx.account.dto.CreateAccountRequest;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/create/{uid}")
    public Long create(@PathVariable Long uid) {
        return accountService.createAccount(uid);
    }

    @PostMapping("/increase")
    public void increase(@RequestParam Long uid,
                         @RequestParam BigDecimal amount) {
        accountService.increaseBalance(uid, amount);
    }

    @PostMapping("/freeze")
    public void freeze(@RequestParam Long uid,
                       @RequestParam BigDecimal amount) {
        accountService.freezeBalance(uid, amount);
    }

    @GetMapping("/{uid}")
    public Account get(@PathVariable Long uid) {
        return accountService.getByUid(uid);
    }
}
