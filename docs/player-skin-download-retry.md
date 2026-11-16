# 玩家皮肤下载失败恢复

## 原因

Minecraft 1.21.1 的 `PlayerInfo` 保留首次皮肤请求，`SkinManager.TextureCache` 也按纹理 hash 保留结果。
`HttpTexture` 遇到 HTTP 非 2xx、网络异常或图片解码失败时不会触发 `onDownloaded`，
结果一直未完成；下载任务 `future` 却已经结束且不为空，后续 `load` 会直接返回，不再下载。
因此瞬时失败可能导致本次游戏期间一直显示默认皮肤。此结论来自本机实际 Mojang 映射游戏类字节码。

## 修复

- 客户端 `HttpTextureRetryMixin` 监听下载任务结束和成功解码回调。
- 下载任务结束但尚未成功时，分别等待 5、15、45 秒，最多重试三次。
- 在渲染线程清除已结束的下载任务，重新调用同一纹理实例的原版加载流程。
  不替换玩家、纹理缓存或皮肤结果引用，原本等待结果的玩家可正常收到恢复后的结果。
- 延迟后再次检查成功状态及任务身份，成功、重复加载和过期任务不会重复下载。
- 网络操作仍在原版后台执行器执行，延迟不阻塞渲染线程；恢复任务通过渲染队列投递，
  不依赖转服时可能清空的游戏任务队列。已有兼容包上传补丁保持不变。
- 不修改皮肤来源、签名验证、主播模式、职业伪装或模组版本，不新增依赖。

## 验证边界

`SkinDownloadRetryTest` 使用可控任务和调度器覆盖等待中、吞掉异常、异常结束、重复加载、
解码先成功、延迟解码成功、三次退避上限、过期任务及本地缓存命中。

实际执行 `./gradlew :test --tests io.wifi.starrailexpress.client.util.SkinDownloadRetryTest :remapJar --offline --console=plain`
成功，9 项测试全部通过（0 失败、错误、跳过）。已核对 `build/libs/starrailexpress.jar`
包含重试策略、Mixin 类及客户端注册，不包含测试类。未启动游戏进行联机验收。

下载重试针对下载任务已经结束的瞬时故障；不会绕过皮肤源鉴权或修复永久不可用的 URL，
也不强制重启尚未结束的网络连接。真实联机弱网与转服场景仍需游戏内验收。

## 本人皮肤在重载／切服后回退到自带默认皮肤

用户进一步明确问题仅指本人：资源重载或进入另一台服务器后，皮肤有时变成游戏自带默认外观。
下载重试不覆盖所有情况，尤其是新服务器的本人档案没有皮肤属性、原版已经正常返回默认皮肤时。
原版每份 `PlayerInfo` 保留独立的皮肤查询，原有 `ClientSkinCache` 断线时虽然保留本人管理器缓存，
却没有在新 `PlayerInfo` 返回默认皮肤时复用此前成功的本人皮肤。

补充 `LocalPlayerSkinMixin` 和 `LocalPlayerSkinCache`：

- 仅匹配当前本地玩家 UUID，不改变其他玩家的皮肤。
- 有效的服务器皮肤始终优先；自带默认皮肤不覆盖会话中最近成功的本人皮肤。
- 切服和资源重载不清除该兜底结果；更换登录账号时清除缓存及旧的账号皮肤请求。
- 没有成功缓存时，读取已完成的登录档案并通过原版 SkinManager 异步加载账号皮肤。
  `MinecraftProfileAccessor` 只读 `profileFuture`，不调用内部会 join 的 `Minecraft.getGameProfile()`。
  档案暂未完成时下次查询再检查，皮肤尚在下载时直接返回原版结果，完成后自动恢复。
- 主播随机皮肤模式跳过；职业换肤仍在更上层的 `AbstractClientPlayer.getSkin` 处理，不被这里覆盖。
- 不修改服务器档案、皮肤签名、下载 URL、资源重载状态，也不把登录凭证或纹理属性落盘。

单元测试模拟重载／切服后返回默认皮肤的结果选择，不等同于真实 GPU 资源重载。
真实双服切换、F3+T 重载和第一／第三人称显示仍需游戏内验收。
如果本次启动从未加载到本人皮肤，且登录档案也没有皮肤资料，则保持原版默认皮肤，不猜测来源。

### 本人皮肤补充修复的验证

执行 `./gradlew :test --tests io.wifi.starrailexpress.client.util.LocalPlayerSkinCacheTest --tests io.wifi.starrailexpress.client.LocalPlayerSkinMixinTest --tests io.wifi.starrailexpress.client.util.SkinDownloadRetryTest :remapJar --offline --console=plain` 成功。
本人缓存测试 12 项、补丁字节码约束 2 项、下载重试回归 9 项，共 23 项，0 失败、错误或跳过。
已核对正式 JAR 包含本人缓存、两个客户端 Mixin 及配置注册，不包含测试类。
构建存在与本修复无关的 Cobblemon `cobblemon$fetchBrewingRecipe` 映射警告；构建状态为成功。
未部署到运行实例，未执行真实双服切换或 GPU 重载验收。
