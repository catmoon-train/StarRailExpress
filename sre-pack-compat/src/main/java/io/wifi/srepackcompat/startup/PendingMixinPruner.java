package io.wifi.srepackcompat.startup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 在任何写入前完成计划；只换声明列表，不修改原列表、服务端声明或插件。 */
final class PendingMixinPruner {
    interface ConfigView {
        String name() throws ReflectiveOperationException;
        String mixinPackage() throws ReflectiveOperationException;
        boolean prepared() throws ReflectiveOperationException;
        List<String> declarations(boolean client) throws ReflectiveOperationException;
        void declarations(boolean client, List<String> values) throws ReflectiveOperationException;
    }

    private record Change(ConfigView config, boolean client, List<String> original,
                          List<String> retained, List<String> removed) { }
    record Result(List<String> removed, int configs) { }

    static Result apply(List<? extends ConfigView> configs, Map<String, Set<String>> verified,
                        SreMixinCancellationPolicy policy) throws ReflectiveOperationException {
        List<Change> changes = new ArrayList<>();
        for (ConfigView config : configs) {
            Set<String> allowed = verified.get(config.name());
            if (allowed == null || config.prepared()) continue;
            String pkg = config.mixinPackage();
            if (pkg == null || pkg.isEmpty()) continue;
            if (!pkg.endsWith(".")) pkg += ".";
            for (boolean client : new boolean[]{false, true}) {
                List<String> original = config.declarations(client);
                if (original == null || original.isEmpty()) continue;
                List<String> retained = new ArrayList<>();
                List<String> removed = new ArrayList<>();
                for (String shortName : original) {
                    String name = shortName == null ? null : pkg + shortName;
                    if (name != null && allowed.contains(name) && policy.cancels(name)) removed.add(name);
                    else retained.add(shortName);
                }
                if (!removed.isEmpty()) changes.add(new Change(config, client, original,
                        Collections.unmodifiableList(retained), removed));
            }
        }
        // 此时 Mixin 自身持有转换锁；仍拒绝覆盖其他插件刚刚替换的列表。
        for (Change change : changes) {
            if (change.config.prepared() || change.config.declarations(change.client) != change.original) {
                throw new IllegalStateException("Mixin declarations changed before commit");
            }
        }
        int written = 0;
        try {
            for (Change change : changes) {
                change.config.declarations(change.client, change.retained);
                written++;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            for (int i = written - 1; i >= 0; i--) {
                Change change = changes.get(i);
                try {
                    change.config.declarations(change.client, change.original);
                } catch (ReflectiveOperationException | RuntimeException rollback) {
                    // 不允许以“安全回退”掩盖无法恢复的半提交状态。
                    error.addSuppressed(rollback);
                    throw new RollbackFailure(error);
                }
            }
            throw error;
        }
        return new Result(changes.stream().flatMap(c -> c.removed.stream()).toList(),
                (int) changes.stream().map(Change::config).distinct().count());
    }

    static final class RollbackFailure extends Error {
        RollbackFailure(Throwable cause) { super("Cannot restore original Mixin declarations", cause); }
    }

    private PendingMixinPruner() { }
}
