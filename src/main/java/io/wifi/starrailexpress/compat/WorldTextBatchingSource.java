package io.wifi.starrailexpress.compat;

/**
 * 标记会在世界渲染阶段统一提交各 RenderType 的缓冲源。
 * 告示牌文字可直接交给它合批，不必绕过第三方渲染器逐面刷新。
 */
public interface WorldTextBatchingSource {
}
