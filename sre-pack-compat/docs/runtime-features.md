# 帧节奏、第三方皮肤与遗弃帧缓冲回收

本次只修改独立客户端兼容包，MC 1.21.1 / Java 21，不引入新依赖、不修改版本号、不更换服务器认证。

## 安装

新兼容 JAR 内置这三项功能，可独立使用。若 mods 中仍安装原 `framepacer`、`skin-unlocker`、`gpumemleakfix`，由原模组独占对应功能，本包在 Mixin 选择阶段关闭相应内置实现；GPU tick 回收入口也不注册，避免双重限帧、重复纹理校验和重复显存删除。无需为更新本包强制删除已有模组。未安装原模组时，内置实现正常启用。

## FramePacer

根据用户提供的 FramePacer 1.2（supcherski，MIT）适配：在 `RenderSystem.limitDisplayFPS` 用长余量 `LockSupport.parkNanos` 加最后 1ms 短自旋等待目标时间。相同 FPS 设置继续由 Minecraft 决定；不改 VSync、帧率上限或后台帧率设置，不提高 GPU 的实际渲染能力。

基准使用上次实际完成时间，慢帧不补偿性等待或积累追帧债务；提前唤醒重新检查时钟；保留中断状态，非正 FPS 不进入新限帧路径。短自旋会占用少量 CPU，不承诺提高平均 FPS 或对所有机器减少卡顿。

## MC Skin Unlocker

适配用户提供的 1.0.2（wifi-left，MIT）三个行为：

- Authlib `TextureUrlChecker.isAllowedTextureDomain` 放开皮肤纹理域名限制。
- `Property.isSignatureValid` 仅对名称严格等于 `textures` 的属性放行。
- `YggdrasilServicesKeyInfo.validateProperty` 同样仅放行 `textures`。

这允许客户端加载第三方/未签名的皮肤纹理，等同于用户指定模组的信任边界；皮肤来源不再保证为 Mojang 签名官方来源。其他属性、登录认证、聊天签名和普通下载实现不改。只在 client Mixin 区段启用，不安装在专用服务器。MIT 声明位于资源 `licenses/FramePacer-SkinUnlocker-MIT.txt`。

## GPU 回收

用户提供的 GPU 修复包使用 `RenderTarget.finalize()` 将纹理及帧缓冲编号放入队列，在客户端 tick 最多删除 20 组。原包声明 ARR，本实现独立编写，不嵌入原 JAR 或搬运代码。

此处采用 `PhantomReference` / `ReferenceQueue`，不使用弃用的 finalizer、不主动触发 GC、不启后台 GL 线程：

- 包装基础 `RenderTarget.createBuffers`，记录自有颜色纹理、深度纹理、FBO；分配中途抛错时也记录已生成的编号。
- 正常 `destroyBuffers` 后撤销记录；若只部分销毁，则按字段中仍未删除的编号重新跟踪。
- 重建/resize 撤销旧记录，避免旧编号随后被 GL 复用时遭到二次删除。
- Java 对象真正失去引用并出队后，在客户端 tick 的渲染线程最多消费 20 条记录，删除资源。
- 删除前保存纹理及读/写 FBO 绑定，删除后恢复未被删除的绑定；若原绑定恰是待删除资源，恢复到 0。
- 不清理正常可达对象、不定期销毁主帧缓冲、不降低画面质量。仅跟踪经过基础 RenderTarget 分配/销毁路径的资源，不宣称覆盖所有模组自有纹理、共享附件或直接 GL 分配的泄漏。

所有原有兼容项、Chloride 配置锁、页面隐藏与 preLaunch 配置覆盖继续保留。

## 已完成验证

- `:sre-pack-compat:test :sre-pack-compat:remapJar :sre-pack-compat:remapSourcesJar` 成功；125 项测试通过，0 失败、错误、跳过，本轮新增 16 项。
- MC 1.21.1 / Java 21 / Fabric Loader 0.19.3 的隔离开发映射客户端实际启动，资源重载结束、主界面就绪后完成 31 项检查，输出 `SRE_RUNTIME_FEATURES_PASS checks=31` 并正常退出。只装本包、Fabric API 和 scratch 临时验证器，不连接服务器、不复制真实账号或核心鉴权组件。
- 检查真实 Authlib：第三方纹理域名与无效签名 textures 放行，非 textures 属性在 Property 和 ServicesKey 两条校验路径仍拒绝；限帧 Mixin 实际存在并执行。
- 检查真实 OpenGL：正常 destroy 删除并撤销登记、旧记录不误删新纹理、五次 resize 不积累记录、遗弃颜色/深度纹理及 FBO 删除、原纹理和读写 FBO 绑定恢复、25 组按 20+5 回收，最后无 GL 错误。
- 回收测试手动将实际注册引用入队，验证真实清理管线和编号生命周期，不声称已经重现第三方模组的自然 GC 泄漏或测试所有显卡/光影组合。
- 首次验证器在 CLIENT_STARTED 立即退出，因尚有资源重载产生 TextureManager 关闭并发异常；仅修正 scratch 验证器为就绪后执行，第二轮正常退出，正式实现未因此改动。
- 正式 JAR 核对 GPU 目标/字段已映射为 Fabric 生产命名，保留其他兼容项及内置配置，不含原三个模组类、测试器或额外依赖。未进行完整整包联机、长期显存或帧时间收益基准，未部署用户实例。
