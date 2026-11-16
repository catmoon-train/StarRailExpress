# 精简壳（registry-only shell）下的兼容混入

## 症状

服务器启动即崩，栈顶为 `MixinApplyError` / `InvalidMixinException`：

```
Caused by: java.lang.IllegalStateException: Mega Showdown compatibility target missing:
  com/github/yajatkaul/mega_showdown/networking/server/handler/CrownHandler
  at io.wifi.srepokemoncompat.mega.MegaMixinPlugin.shouldApplyMixin(MegaMixinPlugin.java:32)
```

## 原因

精简壳宝可梦/Mega 只保留模组 ID 与注册项，**故意不含原玩法类**。原插件把“目标类字节缺失”
当成致命错误直接 `throw`，于是 Mixin PREPARE 阶段整体失败，服务器无法启动。

## 处理

`io.wifi.srepokemoncompat.CompatTargets` 统一判定：

- `isRegistryShell(modId)`：读取目标模组 `fabric.mod.json` 的 `train:variant`，值为
  `registry-only-shell` 时跳过全部可选兼容混入（壳内没有这些玩法，兼容本身无意义）。
- `hasTargetClass(target)`：只查字节资源，不提前加载目标类；缺失时
  `warnMissingOnce` 告警一次并跳过该混入，用于“同一模组的不同构建改了类名”这类情况。

接入点：`MegaMixinPlugin`、`AotakeMixinPlugin`、`PokemonMixinPlugin`、`SpawningMixinPlugin`。
不再有任何“目标缺失即抛异常”的路径。

## 验证

`verify_shell_compat_fix.py`（scratch）用同一套精简壳组合对比新旧 jar：

- 旧 jar：`mixinCrash=True started=False`（复现上面的崩溃）
- 新 jar：`mixinCrash=False started=True`，并正常导出注册表快照

尚未验证：真实列车整合包全量模组下的启动（该整合包含 wathe/voicechat 等外部依赖，需在完整
服务器实例上确认）。
