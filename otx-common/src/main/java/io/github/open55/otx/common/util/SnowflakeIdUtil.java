package io.github.open55.otx.common.util;

import cn.hutool.extra.spring.SpringUtil;
import io.github.open55.otx.common.interfaces.SnowflakeIdGenerator;

public class SnowflakeIdUtil {
    public static long nextId() {
        return SpringUtil.getBean(SnowflakeIdGenerator.class).getNextId();
    }
}
