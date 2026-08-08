package io.github.open55.mockupstream.scenario;

/**
 * 一键处理结果统计（供页面展示处理摘要）。
 * <p>
 * 统计一次一键处理中各类挂起事项的成功/失败数量。
 *
 * @param depositBooked 充值入账成功数
 * @param depositFailed 充值入账失败数
 * @param settled       提现结算成功数
 * @param settleFailed  提现结算失败数
 * @param cancelled     提现取消成功数
 * @param cancelFailed  提现取消失败数
 */
public record ProcessSummary(int depositBooked, int depositFailed, int settled, int settleFailed,
                             int cancelled, int cancelFailed) {

    /**
     * 待处理总数：各类失败数之和（成功项不再挂起）。
     *
     * @return 处理后仍挂起的事项数
     */
    public int remaining() {
        return depositFailed + settleFailed + cancelFailed;
    }

    /**
     * 处理总动作数（成功 + 失败）。
     *
     * @return 本次一键处理执行的动作总数
     */
    public int total() {
        return depositBooked + depositFailed + settled + settleFailed + cancelled + cancelFailed;
    }
}
