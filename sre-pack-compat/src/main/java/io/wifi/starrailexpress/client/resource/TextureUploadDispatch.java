package io.wifi.starrailexpress.client.resource;

import java.util.Objects;
import java.util.function.Consumer;

/** 纹理上传属于渲染任务，不进入转服时会被清空的游戏任务队列。 */
public final class TextureUploadDispatch {
    private TextureUploadDispatch() { }

    /** 渲染线程直接上传；其他线程仅投递到渲染队列，不阻塞或重试。 */
    public static void dispatch(Runnable upload, boolean onRenderThread, Consumer<Runnable> renderQueue) {
        Objects.requireNonNull(upload, "upload");
        if (onRenderThread) {
            upload.run();
        } else {
            Objects.requireNonNull(renderQueue, "renderQueue").accept(upload);
        }
    }
}
