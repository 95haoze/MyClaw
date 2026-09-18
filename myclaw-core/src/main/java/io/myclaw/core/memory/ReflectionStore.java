package io.myclaw.core.memory;

import java.util.List;

/**
 * 复盘经验的持久化存储。
 *
 * <p>实现可以是内存、文件、数据库等。方法都应当线程安全，因为 {@code Agent} 无状态、
 * 可被多线程共享，同一个 store 可能被多个会话并发写入。
 */
public interface ReflectionStore {

    /** 保存一条经验。 */
    void save(Reflection reflection);

    /** 最近 {@code limit} 条经验，按时间正序返回（最旧在前、最新在后）。 */
    List<Reflection> recent(int limit);

    /**
     * 关键词检索经验，对任务与教训做大小写不敏感的包含匹配，
     * 按时间倒序返回（最新在前），最多 {@code limit} 条。
     */
    List<Reflection> search(String keyword, int limit);

    /** 全部经验（不可变快照）。 */
    List<Reflection> all();

    /** 清空。 */
    void clear();

    /** 当前条数。 */
    int size();
}
