package io.wifi.srepackcompat.render;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** 仅保存 GPU 编号，不强引用 RenderTarget；引用出队后由渲染线程执行删除。 */
public final class AbandonedRenderTargets {
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
    private final Set<Registration> tracked = ConcurrentHashMap.newKeySet();

    public Registration track(Object target, Handles handles) {
        if (!handles.hasResources()) return null;
        Registration registration = new Registration(target, handles);
        tracked.add(registration);
        Reference.reachabilityFence(target);
        return registration;
    }

    /** 每次最多消费 limit 条引用，避免一次 GC 产生无界清理停顿。 */
    public int drain(int limit, Consumer<Handles> delete) {
        int released = 0;
        for (int i = 0; i < limit; i++) {
            Registration registration = (Registration) queue.poll();
            if (registration == null) break;
            tracked.remove(registration);
            Handles handles = registration.handles.getAndSet(null);
            registration.clear();
            if (handles != null) {
                delete.accept(handles);
                released++;
            }
        }
        return released;
    }

    public int trackedCount() { return tracked.size(); }

    public record Handles(int colorTexture, int depthTexture, int framebuffer) {
        public boolean hasResources() { return colorTexture >= 0 || depthTexture >= 0 || framebuffer >= 0; }
        public boolean containsTexture(int id) { return id >= 0 && (id == colorTexture || id == depthTexture); }
    }

    public final class Registration extends PhantomReference<Object> implements AutoCloseable {
        private final AtomicReference<Handles> handles;

        private Registration(Object target, Handles handles) {
            super(target, queue);
            this.handles = new AtomicReference<>(handles);
        }

        /** 正常销毁/重建必须先撤销旧记录，禁止后续按旧编号二次删除。 */
        @Override
        public void close() {
            handles.set(null);
            tracked.remove(this);
            clear();
        }
    }
}
