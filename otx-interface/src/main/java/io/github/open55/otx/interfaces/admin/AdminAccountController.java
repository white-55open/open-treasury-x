package io.github.open55.otx.interfaces.admin;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 管理控制台账户控制器，提供账户总览与用户资金流水时间线。
 */
@Controller
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountAppService accountAppService;

    private final FundFlowAppService fundFlowAppService;

    /**
     * 账户总览页，展示全部账户的可用余额与冻结余额，uid 可跳转流水页。
     *
     * @param model 视图模型
     * @return 账户总览页视图名
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("accounts", accountAppService.listAccounts());
        return "admin/accounts";
    }

    /**
     * 用户资金流水页，按用户标识查询该用户的全部流水（时间线表格）。
     *
     * @param uid   用户唯一标识
     * @param model 视图模型
     * @return 流水时间线页视图名
     */
    @GetMapping("/{uid}/flows")
    public String flows(@PathVariable Long uid, Model model) {
        model.addAttribute("uid", uid);
        model.addAttribute("flows", fundFlowAppService.findByUid(uid));
        return "admin/flows";
    }
}
