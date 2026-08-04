package io.github.open55.otx.interfaces.admin;

import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 管理控制台提现单据控制器，提供提现列表与结算/取消操作入口。
 * <p>
 * 操作仅复用应用服务用例（confirmAndSettle / cancelWithdraw），
 * 不重复实现任何业务逻辑；失败时在页面错误块展示 BizException 消息。
 */
@Controller
@RequestMapping("/admin/withdraws")
@RequiredArgsConstructor
public class AdminWithdrawController {

    private final WithdrawAppService withdrawAppService;

    /**
     * 提现单据列表页，展示全部提现请求及其状态机与链上交易哈希。
     *
     * @param model 视图模型
     * @return 提现单据列表页视图名
     */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("withdraws", withdrawAppService.listRequests());
        return "admin/withdraws";
    }

    /**
     * 确认结算操作：链上确认达标后完成冻结资金扣减与凭证过账。
     * <p>
     * 成功重定向回单据列表；失败（BizException）渲染列表页并在错误块展示消息。
     *
     * @param bizNo 业务流水号
     * @param model 视图模型
     * @return 重定向或列表页视图名
     */
    @PostMapping("/{bizNo}/confirm-settle")
    public String confirmSettle(@PathVariable String bizNo, Model model) {
        try {
            withdrawAppService.confirmAndSettle(bizNo);
            return "redirect:/admin/withdraws";
        } catch (BizException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("withdraws", withdrawAppService.listRequests());
            return "admin/withdraws";
        }
    }

    /**
     * 取消提现操作：解冻已冻结资金并置请求为 CANCELLED。
     * <p>
     * 成功重定向回单据列表；失败（BizException）渲染列表页并在错误块展示消息。
     *
     * @param bizNo 业务流水号
     * @param model 视图模型
     * @return 重定向或列表页视图名
     */
    @PostMapping("/{bizNo}/cancel")
    public String cancel(@PathVariable String bizNo, Model model) {
        try {
            withdrawAppService.cancelWithdraw(bizNo);
            return "redirect:/admin/withdraws";
        } catch (BizException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("withdraws", withdrawAppService.listRequests());
            return "admin/withdraws";
        }
    }
}
