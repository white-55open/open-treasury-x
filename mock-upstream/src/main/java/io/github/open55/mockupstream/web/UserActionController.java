package io.github.open55.mockupstream.web;

import io.github.open55.mockupstream.scenario.DepositSimulator;
import io.github.open55.mockupstream.scenario.WithdrawSimulator;
import io.github.open55.mockupstream.state.RegistryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import java.math.BigDecimal;

/**
 * 用户操作控制器：个人中心页面的全部"用户视角"动作。
 * <p>
 * 每个端点对应一个现实用户操作并映射到具体业务调用：
 * <ul>
 *   <li>创建用户：用户注册（幂等开户）；</li>
 *   <li>切换用户：个人中心切换视角；</li>
 *   <li>发起充值：用户向平台充值地址转账；</li>
 *   <li>申请提现：用户提交提现申请（冻结）；</li>
 *   <li>取消提现：用户主动取消提现（标记，解冻由一键处理完成）。</li>
 * </ul>
 * 非法输入（金额非正数/未选中用户）以 flash 错误消息回主页，不抛 500。
 */
@Controller
@RequiredArgsConstructor
public class UserActionController {

    /**
     * 内存状态注册表（创建用户）
     */
    private final RegistryStore store;

    /**
     * 充值业务编排器
     */
    private final DepositSimulator depositSimulator;

    /**
     * 提现业务编排器
     */
    private final WithdrawSimulator withdrawSimulator;

    /**
     * 创建用户（用户操作）：uid 缺省时自动生成，幂等创建并切换为当前用户。
     *
     * @param uid    用户标识（可选，缺省自动生成）
     * @param session 当前会话
     * @return 重定向回个人中心主页
     */
    @PostMapping("/users")
    public String createUser(@RequestParam(required = false) Long uid, HttpSession session) {
        Long resolvedUid = uid != null ? uid : 100000L + (System.currentTimeMillis() % 900000L);
        store.createUser(resolvedUid);
        session.setAttribute(HomeController.SESSION_CURRENT_UID, resolvedUid);
        return "redirect:/";
    }

    /**
     * 切换当前用户（用户操作）：仅切换会话视角，不产生业务调用。
     *
     * @param uid     目标用户标识
     * @param session 当前会话
     * @return 重定向回个人中心主页
     */
    @PostMapping("/users/{uid}/select")
    public String selectUser(@PathVariable Long uid, HttpSession session) {
        session.setAttribute(HomeController.SESSION_CURRENT_UID, uid);
        return "redirect:/";
    }

    /**
     * 发起充值（用户操作）：向平台充值地址转账，生成确认中记录。
     *
     * @param amount  充值金额（必须为正数）
     * @param session 当前会话（需已选中用户）
     * @param ra      重定向属性（错误消息）
     * @return 重定向回个人中心主页
     */
    @PostMapping("/deposits")
    public String initiateDeposit(@RequestParam BigDecimal amount, HttpSession session, RedirectAttributes ra) {
        Long currentUid = currentUid(session);
        if (currentUid == null) {
            // 未选中用户：提示先创建/选择用户
            ra.addFlashAttribute("errorMessage", "请先创建或选择用户");
            return "redirect:/";
        }
        if (amount == null || amount.signum() <= 0) {
            // 金额非法：提示后返回主页
            ra.addFlashAttribute("errorMessage", "充值金额必须为正数");
            return "redirect:/";
        }
        depositSimulator.initiateDeposit(currentUid, amount);
        return "redirect:/";
    }

    /**
     * 申请提现（用户操作）：提交提现申请并冻结资金。
     *
     * @param amount  提现金额（必须为正数）
     * @param session 当前会话（需已选中用户）
     * @param ra      重定向属性（错误消息）
     * @return 重定向回个人中心主页
     */
    @PostMapping("/withdrawals")
    public String applyWithdraw(@RequestParam BigDecimal amount, HttpSession session, RedirectAttributes ra) {
        Long currentUid = currentUid(session);
        if (currentUid == null) {
            ra.addFlashAttribute("errorMessage", "请先创建或选择用户");
            return "redirect:/";
        }
        if (amount == null || amount.signum() <= 0) {
            ra.addFlashAttribute("errorMessage", "提现金额必须为正数");
            return "redirect:/";
        }
        withdrawSimulator.applyWithdraw(currentUid, amount);
        return "redirect:/";
    }

    /**
     * 取消提现（用户操作）：标记取消，解冻由一键处理完成。
     *
     * @param bizNo 提现请求业务号
     * @return 重定向回个人中心主页
     */
    @PostMapping("/withdrawals/{bizNo}/cancel")
    public String cancelWithdraw(@PathVariable String bizNo) {
        withdrawSimulator.markCancel(bizNo);
        return "redirect:/";
    }

    /**
     * 读取会话中当前选中用户（未选中时返回 null）。
     *
     * @param session 当前会话
     * @return 当前用户标识或 null
     */
    private Long currentUid(HttpSession session) {
        return (Long) session.getAttribute(HomeController.SESSION_CURRENT_UID);
    }
}
