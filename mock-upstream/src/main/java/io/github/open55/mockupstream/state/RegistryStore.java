package io.github.open55.mockupstream.state;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存状态注册表（全局单例，重启即失）。
 * <p>
 * 集中管理模拟用户、充值记录、提现记录与操作日志四类状态：
 * <ul>
 *   <li>用户表：uid → {@link SimUser}，支持多用户创建与切换（幂等创建）；</li>
 *   <li>充值表：bizNo → {@link DepositRecord}，每笔独立业务号；</li>
 *   <li>提现表：bizNo → {@link WithdrawRecord}，每笔独立业务号；</li>
 *   <li>日志缓冲：{@link OperationLogEntry} 环形缓冲（上限 200 条），页面倒序展示。</li>
 * </ul>
 * 所有容器并发安全（ConcurrentHashMap / 同步访问），支撑 Web 层并发请求；
 * 当前选中用户由 Web 层 Session 管理，本类不感知会话。
 */
@Component
public class RegistryStore {

    /**
     * 操作日志环形缓冲上限：超过后丢弃最旧条目，防止内存无限增长
     */
    private static final int MAX_LOG_ENTRIES = 200;

    /**
     * 用户注册表：uid → 模拟用户（ConcurrentHashMap 保证并发安全）
     */
    private final Map<Long, SimUser> users = new ConcurrentHashMap<>();

    /**
     * 充值记录表：bizNo → 充值记录
     */
    private final Map<String, DepositRecord> deposits = new ConcurrentHashMap<>();

    /**
     * 提现记录表：bizNo → 提现记录
     */
    private final Map<String, WithdrawRecord> withdraws = new ConcurrentHashMap<>();

    /**
     * 操作日志环形缓冲：最新条目在尾部（同步访问保证线程安全）
     */
    private final Deque<OperationLogEntry> logBuffer = new ArrayDeque<>();

    /**
     * 创建模拟用户（幂等）：uid 已存在时返回已有用户，不重复创建。
     *
     * @param uid 用户唯一标识
     * @return 新创建或已存在的用户
     */
    public SimUser createUser(Long uid) {
        return users.computeIfAbsent(uid, key -> new SimUser(key, System.currentTimeMillis()));
    }

    /**
     * 查询全部用户，按创建时间升序排列（最早创建在前）。
     *
     * @return 用户列表
     */
    public List<SimUser> listUsers() {
        List<SimUser> result = new ArrayList<>(users.values());
        result.sort(Comparator.comparingLong(SimUser::getCreatedAt));
        return result;
    }

    /**
     * 注册一笔充值记录（发起充值动作调用）。
     *
     * @param bizNo    充值业务号（OTX 幂等键）
     * @param uid      充值用户标识
     * @param amount   充值金额
     * @param currency 充值币种
     * @param txHash   模拟链交易哈希
     * @return 新建的充值记录（状态 CONFIRMING）
     */
    public DepositRecord registerDeposit(String bizNo, Long uid, BigDecimal amount,
                                         String currency, String txHash) {
        DepositRecord record = new DepositRecord(bizNo, uid, amount, currency, txHash,
                System.currentTimeMillis());
        record.setStatus(DepositRecord.Status.CONFIRMING);
        deposits.put(bizNo, record);
        return record;
    }

    /**
     * 查询全部充值记录，按创建时间倒序排列（最新在前）。
     *
     * @return 充值记录列表
     */
    public List<DepositRecord> listDeposits() {
        List<DepositRecord> result = new ArrayList<>(deposits.values());
        result.sort(Comparator.comparingLong(DepositRecord::getCreatedAt).reversed());
        return result;
    }

    /**
     * 注册一笔提现记录（申请提现动作调用）。
     *
     * @param bizNo       提现请求业务号（链上请求号）
     * @param freezeBizNo 链下冻结业务号
     * @param uid         提现用户标识
     * @param amount      提现金额
     * @param currency    提现币种
     * @param toAddress   提现目标地址
     * @return 新建的提现记录（状态 FROZEN）
     */
    public WithdrawRecord registerWithdraw(String bizNo, String freezeBizNo, Long uid,
                                           BigDecimal amount, String currency, String toAddress) {
        WithdrawRecord record = new WithdrawRecord(bizNo, freezeBizNo, uid, amount, currency,
                toAddress, System.currentTimeMillis());
        record.setStatus(WithdrawRecord.Status.FROZEN);
        withdraws.put(bizNo, record);
        return record;
    }

    /**
     * 查询全部提现记录，按创建时间倒序排列（最新在前）。
     *
     * @return 提现记录列表
     */
    public List<WithdrawRecord> listWithdraws() {
        List<WithdrawRecord> result = new ArrayList<>(withdraws.values());
        result.sort(Comparator.comparingLong(WithdrawRecord::getCreatedAt).reversed());
        return result;
    }

    /**
     * 按业务号查询充值记录（不存在时返回 null）。
     *
     * @param bizNo 充值业务号
     * @return 充值记录或 null
     */
    public DepositRecord findDeposit(String bizNo) {
        return deposits.get(bizNo);
    }

    /**
     * 按业务号查询提现记录（不存在时返回 null）。
     *
     * @param bizNo 提现请求业务号
     * @return 提现记录或 null
     */
    public WithdrawRecord findWithdraw(String bizNo) {
        return withdraws.get(bizNo);
    }

    /**
     * 追加一条操作日志（环形裁剪：超过上限丢弃最旧条目）。
     *
     * @param entry 操作日志条目
     */
    public synchronized void appendLog(OperationLogEntry entry) {
        logBuffer.addLast(entry);
        while (logBuffer.size() > MAX_LOG_ENTRIES) {
            logBuffer.removeFirst();
        }
    }

    /**
     * 读取全部操作日志（最新在前），供页面倒序展示。
     *
     * @return 操作日志列表
     */
    public synchronized List<OperationLogEntry> recentLogs() {
        List<OperationLogEntry> result = new ArrayList<>(logBuffer);
        Collections.reverse(result);
        return result;
    }
}
