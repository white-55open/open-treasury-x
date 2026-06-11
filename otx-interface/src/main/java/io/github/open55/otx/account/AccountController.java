package io.github.open55.otx.account;


import io.github.open55.otx.account.dto.response.GetAccountResponse;
import io.github.open55.otx.account.service.AccountAppService;
import io.github.open55.otx.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountAppService accountAppService;

    @PostMapping("/create/{uid}")
    public Result<String> create(@PathVariable Long uid) {
        return Result.success(String.valueOf(accountAppService.createAccount(uid)));
    }

    @PostMapping("/increase")
    public Result<Void> increase(@RequestParam Long uid, @RequestParam BigDecimal amount) {
        accountAppService.increaseBalance(uid, amount);
        return Result.success();
    }

    @PostMapping("/freeze")
    public Result<Void> freeze(@RequestParam Long uid, @RequestParam BigDecimal amount) {
        accountAppService.freezeBalance(uid, amount);
        return Result.success();
    }

    @GetMapping("/{uid}")
    public Result<GetAccountResponse> get(@PathVariable Long uid) {
        return Result.success(accountAppService.getByUid(uid));
    }
}
