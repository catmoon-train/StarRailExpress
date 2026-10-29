# 万圣节小镇：橙色天空与可替换音乐

## 接线（由主入口维护者完成）

- 公共初始化调用 `org.agmas.noellesroles.game.halloween.ambience.HalloweenAmbience.register()`，注册 `halloween:music.town`。必须在注册表冻结之前；不要放在客户端初始化或每次开局里。
- 客户端初始化调用同包 `HalloweenAmbienceClient.register()`，仅注册一次 tick / 断线回调；无需修改 NRSounds。
- `src/main/resources/noellesroles.mixins.json` 的 **client** 列表添加以下六项（公共 mixins 列表不可添加客户端类）：

```json
"halloween.ambience.HalloweenSkyColorMixin",
"halloween.ambience.HalloweenSkyFogMixin",
"halloween.ambience.HalloweenSkyMoonMixin",
"halloween.ambience.HalloweenSkyLightMixin",
"halloween.ambience.HalloweenMusicManagerMixin",
"halloween.ambience.HalloweenSoundReloadMixin"
```

本模块不改动以上现有入口或配置文件。入口接好前，资源与 mixin 文件本身不会自动启用功能。

## 门控和优先级

以实际 `HalloweenRuntime.isMapEnabled(Level)` / `isEnabled(Level)` 为准：地图 `settings.halloweenEventEnabled` 开启、谋杀模式、对局 ACTIVE，且本地玩家已经连接，才启用。大厅、结算、普通地图、断线时不染色或持有音乐；旁观玩家可听见活动音乐。会议沿用 Runtime 状态，不额外停歌。

天空只作用于 NORMAL 天空维度：`ClientLevel.getSkyColor` 穹顶向暖橙 RGB `(1, .35, .08)` 混合 60%；清屏色和真正的 shader 雾色一起暖染，不修改雾距离。月亮保留原 alpha；lightmap 只暖染最初的天光向量，不改方块光或后续真实黑暗/直觉亮度/亡魂夜像素缩放，因此关灯和闪烁仍执行。不增加穹顶模型、HUD 色罩、深度遮挡或后处理。

颜色控制优先级：命令方块事件紫色 > 万圣节橙色；液体、失明、黑暗、幽露黑雾 > 橙色雾/月亮。对命令方块 `CommandBlockSkyLight.active()` 和幽露 `YouluFreeCamClient.shouldBlackenFog()` 只做可选客户端反射探测，缺类视为无事件，存在但 API 不兼容则让出控制。不导入或链接 CommandBlockDirector，也不要求 ltsc-master 带有命令方块事件源码。

复用原版 `getSkyColor` / fog / lightmap 路径，Iris 常见的颜色读取也可受益，没有新增 Iris 依赖或强制卸载光影。自行计算天空/光照的 shader pack 可能忽略原版色值，需要实际光影包验收，不能保证强制覆盖所有包。

## 替换背景音乐

唯一需要替换的源资源：

`src/main/resources/assets/halloween/sounds/music/town.ogg`

- **必须是 Ogg 容器 + Vorbis 音频编码**，不是改后缀的 MP3/WAV，也不要用 Ogg Opus。
- 建议 44100 Hz 或 48000 Hz；单声道、双声道均可。背景音乐无位置衰减、相对听者播放，可用立体声。
- 当前文件是本地 ffmpeg 生成的 **2 秒 / 44100 Hz / 单声道 / 3397 字节有效静音 Vorbis**，不是空文件、不引用在线音频。它会正常流式循环，但听不见音乐是预期行为。
- 不需改 SoundEvent ID 或 `sounds.json`。后者的 `music.town -> halloween:music/town` 设置了 `stream: true`。
- 音轨由声音引擎原生循环（looping=true、delay=0），不是每 tick 重播。正式音频最好提前做好首尾衔接；Vorbis 编解码边界不保证任何素材都无缝。
- 音量由原版声音设置的 **音乐 MUSIC** 和主音量控制。调至零会释放本模块音轨，恢复音量后局内只新开一次。
- 只暂停原版 MusicManager 的随机选曲，入场时停止它持有的原版 BGM；不按声音分类批量停声，不影响用户唱片（RECORDS）或语音。退出活动后恢复原版选曲行为。
- 退出活动地图、结束对局、切换世界实例、断线时释放旧音轨。资源/声音引擎重载后丢弃旧流，下一有效 tick 从头开新流；不会保存播放进度。

转换示例（输入文件应当由你拥有或获准使用）：

```bash
ffmpeg -i "your-music.wav" -vn -ar 44100 -ac 2 -c:a libvorbis -q:a 5 "town.ogg"
ffprobe -v error -show_entries stream=codec_name,sample_rate,channels -show_entries format=duration "town.ogg"
```

### F3+T 与打包

1. 开发运行时替换上述源文件，再把它复制到运行客户端实际加载的资源输出（通常 `build/resources/main/assets/halloween/sounds/music/town.ogg`），或运行资源处理任务；**F3+T 不会自行把源码目录复制到运行输出**。
2. 按 F3+T 重载资源；仍在活动局中则新音轨自动从头播放，已离开活动则保持停止。F3+S 的声音设备重载也会重建音轨。
3. 已打包客户端：可用资源包覆盖 `assets/halloween/sounds/music/town.ogg`，启用后 F3+T；不需要覆盖 sounds.json。不要在游戏运行时直接修改打开的 jar。
4. 发布时替换源文件后沿用项目现有打包流程，检查最终 jar 包含 `assets/halloween/sounds.json` 和 `assets/halloween/sounds/music/town.ogg`。本次实现没有运行全 Gradle 或更改版本号。

音乐不加字幕，所以无需语言文件接线。

## 测试和待验收

`src/test/java/org/agmas/noellesroles/game/halloween/ambience/` 中的 JUnit 不启动 MC：覆盖地图/模式门控输入、ACTIVE/连接条件、单次进入、反复 tick 不重播、局末、换世界、断线重连、静音恢复、重载状态，以及 sounds.json 引用的真实 Ogg/Vorbis 资源存在性。

Mixin 的完整方法描述符、`getMoonPhase()` 注入点和 lightmap 第一个 `Vector3f.lerp` 已按缓存的 Minecraft 1.21.1 Mojang mappings/字节码核对。接线后仍需游戏内验收：进入/离开地图、结束/重连、音量开关、替换非静音文件后 F3+T、普通关灯与亡魂夜闪烁、紫色事件优先、雨天月亮、液体/黑雾，以及启用/禁用实际 Iris 光影包。不以纯逻辑单测替代注入应用或听感验收。
