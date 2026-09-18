package io.myclaw.core.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InMemoryReflectionStore} 的行为测试 —— 离线、确定性。
 */
class InMemoryReflectionStoreTest {

    @Test
    @DisplayName("按插入顺序保存，all 返回不可变快照")
    void keepsEntriesInInsertionOrder() {
        InMemoryReflectionStore store = new InMemoryReflectionStore(10);
        store.save(ref("a"));
        store.save(ref("b"));

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.all()).extracting(Reflection::task).containsExactly("a", "b");
    }

    @Test
    @DisplayName("超过容量上限时丢弃最旧的条目")
    void evictsOldestBeyondCapacity() {
        InMemoryReflectionStore store = new InMemoryReflectionStore(2);
        store.save(ref("a"));
        store.save(ref("b"));
        store.save(ref("c"));

        assertThat(store.all()).extracting(Reflection::task).containsExactly("b", "c");
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("recent 返回最近 N 条，保持时间正序")
    void recentReturnsLastNInOrder() {
        InMemoryReflectionStore store = new InMemoryReflectionStore(10);
        store.save(ref("a"));
        store.save(ref("b"));
        store.save(ref("c"));

        assertThat(store.recent(2)).extracting(Reflection::task).containsExactly("b", "c");
        assertThat(store.recent(10)).hasSize(3);
        assertThat(store.recent(0)).isEmpty();
    }

    @Test
    @DisplayName("search 大小写不敏感地匹配任务与教训，最新在前")
    void searchMatchesKeywordNewestFirst() {
        InMemoryReflectionStore store = new InMemoryReflectionStore(10);
        store.save(ref("first", "SQL 优化任务"));
        store.save(ref("second", "写诗任务"));
        store.save(ref("third", "SQL 调优二"));

        assertThat(store.search("sql", 5))
                .extracting(Reflection::task)
                .containsExactly("third", "first");
        assertThat(store.search("  ", 5)).isEmpty();
        assertThat(store.search("写诗", 5)).extracting(Reflection::task).containsExactly("second");
    }

    @Test
    @DisplayName("clear 清空全部经验")
    void clearEmptiesStore() {
        InMemoryReflectionStore store = new InMemoryReflectionStore(10);
        store.save(ref("a"));
        store.clear();

        assertThat(store.size()).isZero();
        assertThat(store.all()).isEmpty();
    }

    @Test
    @DisplayName("容量必须为正数")
    void rejectsNonPositiveCapacity() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new InMemoryReflectionStore(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Reflection ref(String task) {
        return ref(task, "lesson");
    }

    private static Reflection ref(String task, String lesson) {
        return new Reflection(task, Reflection.Outcome.SUCCESS, List.of(), List.of(), lesson, 1, 10, null);
    }
}
