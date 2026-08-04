package io.github.open55.mockupstream.scenario;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 故事线步骤追踪器（包内工具）。
 * <p>
 * 统计单个故事线内各步骤的成功/失败次数，用于演示结束时的总结输出。
 * 单步失败不中断故事线：追踪器只计数，由调用方决定是否继续后续步骤。
 */
class StoryTracker {

    /**
     * 故事线名称（用于总结输出）
     */
    private final String storyName;

    /**
     * 成功步骤计数
     */
    private final AtomicInteger successSteps = new AtomicInteger();

    /**
     * 失败步骤计数
     */
    private final AtomicInteger failedSteps = new AtomicInteger();

    /**
     * 构造追踪器。
     *
     * @param storyName 故事线名称
     */
    StoryTracker(String storyName) {
        this.storyName = storyName;
    }

    /**
     * 记录一个步骤的执行结果。
     *
     * @param success true 表示步骤成功，false 表示失败（业务失败或调用异常）
     */
    void track(boolean success) {
        if (success) {
            successSteps.incrementAndGet();
        } else {
            failedSteps.incrementAndGet();
        }
    }

    /**
     * 汇总为故事线执行结果。
     *
     * @return 故事线结果（故事名/总步骤/成功步骤）
     */
    StoryResult result() {
        int success = successSteps.get();
        return new StoryResult(storyName, success + failedSteps.get(), success);
    }
}
