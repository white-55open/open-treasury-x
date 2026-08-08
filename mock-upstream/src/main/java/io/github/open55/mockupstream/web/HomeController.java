package io.github.open55.mockupstream.web;

import io.github.open55.mockupstream.blockchain.BlockchainSimulator;
import io.github.open55.mockupstream.config.SimulatorProperties;
import io.github.open55.mockupstream.state.DepositRecord;
import io.github.open55.mockupstream.state.RegistryStore;
import io.github.open55.mockupstream.state.WithdrawRecord;
import io.github.open55.mockupstream.upstream.ApiResult;
import io.github.open55.mockupstream.upstream.OtxClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

import java.util.List;

/**
 * 个人中心主页控制器：渲染模拟用户个人中心页面。
 * <p>
 * 页面展示当前用户的资产卡片（OTX 实时余额）、充值/提现记录（含动态确认数）、
 * 操作日志与一键处理入口；未选中用户时仅展示用户创建入口。
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    /**
     * 会话中当前选中用户标识的属性名
     */
    public static final String SESSION_CURRENT_UID = "currentUid";

    /**
     * 内存状态注册表（用户/记录/日志）
     */
    private final RegistryStore store;

    /**
     * OTX REST 客户端（资产余额查询）
     */
    private final OtxClient otxClient;

    /**
     * 模拟链（动态确认数与区块高度展示）
     */
    private final BlockchainSimulator blockchainSimulator;

    /**
     * 模拟器配置（所需确认数展示）
     */
    private final SimulatorProperties properties;

    /**
     * 渲染个人中心主页。
     *
     * @param session 当前会话（含选中用户）
     * @param model   视图模型
     * @return 个人中心页视图名
     */
    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        Long currentUid = (Long) session.getAttribute(SESSION_CURRENT_UID);
        model.addAttribute("users", store.listUsers());
        model.addAttribute("currentUid", currentUid);
        model.addAttribute("blockHeight", blockchainSimulator.currentHeight());
        model.addAttribute("requiredConfirmations", properties.getRequiredConfirmations());
        if (currentUid != null) {
            // 当前用户资产卡片与我的记录（按用户过滤）
            model.addAttribute("account", fetchAccount(currentUid));
            model.addAttribute("deposits", depositViews(currentUid));
            model.addAttribute("withdraws", withdrawsOf(currentUid));
            model.addAttribute("pendingCount", pendingCount(currentUid));
        } else {
            // 未创建/选中用户：资产与记录为空，仅展示创建入口
            model.addAttribute("account", null);
            model.addAttribute("deposits", List.of());
            model.addAttribute("withdraws", List.of());
            model.addAttribute("pendingCount", 0);
        }
        model.addAttribute("logs", store.recentLogs());
        return "home";
    }

    /**
     * 查询当前用户资产视图（OTX 不可达时返回 null 由页面展示占位）。
     *
     * @param uid 用户标识
     * @return 资产视图或 null
     */
    private AccountView fetchAccount(Long uid) {
        ApiResult result = otxClient.getAccount(uid);
        if (result.data() == null || !result.data().isObject()) {
            return null;
        }
        String available = result.data().path("availableBalance").asText("-");
        String frozen = result.data().path("frozenBalance").asText("-");
        return new AccountView(available, frozen);
    }

    /**
     * 组装当前用户的充值记录视图（附加模拟链动态确认数）。
     *
     * @param uid 用户标识
     * @return 充值记录视图列表（最新在前）
     */
    private List<DepositView> depositViews(Long uid) {
        return store.listDeposits().stream()
                .filter(d -> d.getUid().equals(uid))
                .map(d -> DepositView.from(d,
                        blockchainSimulator.confirmationsOf(d.getTxHash()),
                        properties.getRequiredConfirmations()))
                .toList();
    }

    /**
     * 查询当前用户的提现记录（最新在前）。
     *
     * @param uid 用户标识
     * @return 提现记录列表
     */
    private List<WithdrawRecord> withdrawsOf(Long uid) {
        return store.listWithdraws().stream()
                .filter(w -> w.getUid().equals(uid))
                .toList();
    }

    /**
     * 统计当前用户的挂起事项数（确认中充值 + 已冻结/取消处理中提现）。
     *
     * @param uid 用户标识
     * @return 挂起事项数
     */
    private long pendingCount(Long uid) {
        long confirmingDeposits = store.listDeposits().stream()
                .filter(d -> d.getUid().equals(uid) && d.getStatus() == DepositRecord.Status.CONFIRMING)
                .count();
        long pendingWithdraws = store.listWithdraws().stream()
                .filter(w -> w.getUid().equals(uid)
                        && (w.getStatus() == WithdrawRecord.Status.FROZEN
                        || w.getStatus() == WithdrawRecord.Status.CANCEL_PENDING))
                .count();
        return confirmingDeposits + pendingWithdraws;
    }
}
