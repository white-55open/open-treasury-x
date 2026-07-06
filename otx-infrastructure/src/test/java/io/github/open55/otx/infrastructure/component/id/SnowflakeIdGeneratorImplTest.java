package io.github.open55.otx.infrastructure.component.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SnowflakeIdGeneratorImpl 雪花 ID 生成器单元测试。
 * <p>
 * 覆盖构造参数校验和 nextId 生成逻辑。
 */
@DisplayName("SnowflakeIdGeneratorImpl 雪花 ID 生成器单元测试 | SnowflakeIdGeneratorImpl unit tests")
class SnowflakeIdGeneratorImplTest {

    @Nested
    @DisplayName("构造参数校验 | constructor validation")
    class ConstructorValidation {

        /**
         * workerId 超过最大值时抛 IllegalArgumentException。
         */
        @Test
        @DisplayName("workerId 超限抛异常")
        void construct_withInvalidWorkerId_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SnowflakeIdGeneratorImpl(32, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> new SnowflakeIdGeneratorImpl(-1, 1));
        }

        /**
         * datacenterId 超过最大值时抛 IllegalArgumentException。
         */
        @Test
        @DisplayName("datacenterId 超限抛异常")
        void construct_withInvalidDatacenterId_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SnowflakeIdGeneratorImpl(1, 32));
            assertThrows(IllegalArgumentException.class,
                    () -> new SnowflakeIdGeneratorImpl(1, -1));
        }

        /**
         * 合法参数成功构造。
         */
        @Test
        @DisplayName("合法参数成功构造")
        void construct_withValidArgs_succeeds() {
            assertDoesNotThrow(() -> new SnowflakeIdGeneratorImpl(1, 1));
        }
    }

    @Nested
    @DisplayName("getNextId 生成 ID | generate next id")
    class GetNextId {

        /**
         * 生成的 ID 为正数且大于 0。
         */
        @Test
        @DisplayName("生成的 ID 为正数")
        void nextId_returnsPositiveNumber() {
            SnowflakeIdGeneratorImpl generator = new SnowflakeIdGeneratorImpl(1, 1);
            long id = generator.getNextId();
            assertTrue(id > 0);
        }

        /**
         * 连续生成的两个 ID 不重复。
         */
        @Test
        @DisplayName("连续生成不重复")
        void nextId_sequentialIds_areUnique() {
            SnowflakeIdGeneratorImpl generator = new SnowflakeIdGeneratorImpl(1, 1);
            long id1 = generator.getNextId();
            long id2 = generator.getNextId();
            assertNotEquals(id1, id2);
        }

        /**
         * 批量生成 1000 个 ID 全部唯一。
         */
        @Test
        @DisplayName("批量生成 1000 个 ID 全部唯一")
        void nextId_batchIds_areUnique() {
            SnowflakeIdGeneratorImpl generator = new SnowflakeIdGeneratorImpl(1, 1);
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                assertTrue(ids.add(generator.getNextId()));
            }
            assertEquals(1000, ids.size());
        }

        /**
         * 不同 workderId 生成同一时间点的 ID 也不会重复。
         */
        @Test
        @DisplayName("不同 workerId 生成的 ID 不重复")
        void nextId_differentWorkers_areUnique() {
            SnowflakeIdGeneratorImpl gen1 = new SnowflakeIdGeneratorImpl(1, 1);
            SnowflakeIdGeneratorImpl gen2 = new SnowflakeIdGeneratorImpl(2, 1);
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                ids.add(gen1.getNextId());
                ids.add(gen2.getNextId());
            }
            assertEquals(200, ids.size());
        }
    }
}
