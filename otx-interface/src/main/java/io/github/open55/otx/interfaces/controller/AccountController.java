package io.github.open55.otx.interfaces.controller;

import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 账户管理 REST 控制器。
 * <p>
 * 提供账户创建、余额增加、余额冻结和账户查询接口。
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountAppService accountAppService;

    /**
     * 为用户创建账户。
     *
     * @param uid 用户唯一标识
     * @return 账户标识
     */
    @PostMapping("/create/{uid}")
    public Result<String> create(@PathVariable Long uid) {
        return Result.success(String.valueOf(accountAppService.createAccount(uid)));
    }

    /**
     * 增加用户可用余额。
     *
     * @param uid    用户唯一标识
     * @param amount 增加金额
     */
    @PostMapping("/increase")
    public Result<Void> increase(@RequestParam Long uid, @RequestParam BigDecimal amount) {
        accountAppService.increaseBalance(uid, amount);
        return Result.success();
    }

    /**
     * 冻结用户指定金额。
     *
     * @param uid    用户唯一标识
     * @param amount 冻结金额
     */
    @PostMapping("/freeze")
    public Result<Void> freeze(@RequestParam Long uid, @RequestParam BigDecimal amount) {
        accountAppService.freezeBalance(uid, amount);
        return Result.success();
    }

    /**
     * 按用户标识查询账户详情。
     *
     * @param uid 用户唯一标识
     * @return 账户详情
     */
    @GetMapping("/{uid}")
    public Result<GetAccountResponse> get(@PathVariable Long uid) {
        return Result.success(accountAppService.getByUid(uid));
    }
}
