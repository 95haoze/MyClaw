package io.myclaw.core.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于内存、带容量上限的复盘经验存储。
 *
 * <p>当条数超过 {@code maxEntries} 时，从最旧的一端丢弃（滑动窗口）。
 * 所有方法加锁，可在多会话间安全共享。
 */
public final class InMemoryReflectionStore implements ReflectionStore {

    public static final int DEFAULT_MAX_ENTRIES = 1000;

    private final int maxEntries;
    private final List<Reflection> entries = new ArrayList<>();

    public InMemoryReflectionStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryReflectionStore(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries 至少为 1");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized void save(Reflection reflection) {
        entries.add(reflection);
        while (entries.size() > maxEntries) {
            entries.removeFirst();
        }
    }

    @Override
    public synchronized List<Reflection> recent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int from = Math.max(0, entries.size() - limit);
        return List.copyOf(entries.subList(from, entries.size()));
    }

    @Override
    public synchronized List<Reflection> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank() || limit <= 0) {
            return List.of();
        }
        String key = keyword.toLowerCase();
        List<Reflection> matches = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && matches.size() < limit; i--) {
            Reflection r = entries.get(i);
            if (r.task().toLowerCase().contains(key) || r.lesson().toLowerCase().contains(key)) {
                matches.add(r);
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public synchronized List<Reflection> all() {
        return List.copyOf(entries);
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    @Override
    public synchronized int size() {
        return entries.size();
    }

    public int maxEntries() {
        return maxEntries;
    }
}
