# Rhythm 列车音游曲谱制作指南

## 一、文件结构（请放置在模组或资源包的 `assets/<modid>/` 下）
```
<modid>/
  rhythm/
    └── 曲谱名.json          （例如 yaosemountain.json）
  sounds/
    ├── rhythm/music/
    │   └── 曲谱名.ogg       （背景音乐文件）
    └── sounds.json
```
## 二、sounds.json 配置示例
`sounds.json` 用于引入新的音乐。
```json
{
  "rhythm.music.yaosemountain": {
    "sounds": [
      "yourmodid:rhythm/music/yaosemountain"
    ]
  }
}
```
（注意：键名`rhythm.music.xx`需与谱面JSON中的`Src`的path字段完全一致。比如：上文放在命名空间`<modid>`下则应该为`<modid>:rhythm.music.yaosemountain`）

## 三、谱面 JSON 关键字段说明
```json
{
  "MusicDisplayName": "示例曲目",   // 显示名称
  "Src": "yourmodid:rhythm.music.yaosemountain",  // 对应 sounds.json 的键
  "Delayer": 0,                   // 全局延迟(ms)，正数延后，负数提前，默认可为0
  "Level": 1,                     // 难度等级
  "Original": "原曲信息",
  "Mapper": "谱面作者",
  "Notes": [                      // 音符数组
    {
      "startTime": 500,           // 音符起始时间(ms，相对于音乐开始)
      "endTime": 0,               // 结束时间，只有 Hold 类型才 > 0
      "noteType": "Single",       // Single / Hold / HoldSingle
      "positionType": "Left"      // Left(上轨) / Right(下轨)
    },
    {
      "startTime": 1500,
      "endTime": 3000,
      "noteType": "Hold",
      "positionType": "Right"
    }
  ],
  "NoteClick": [                  // 节拍音效（可选）
    {
      "StartTime": 0,
      "Bpm": 120.0,
      "Division": 4,
      "SpecificMidiClick": [0, 480, 960],  // 相对 StartTime 的偏移(ms)
      "Type": "Normal"
    }
  ],
  "CoverPicOffset": {"x": 0.0, "y": 0.0},
  "CoverPicBorderColor": {"r": 0.0, "g": 0.0, "b": 0.0, "a": 1.0}
}
```
## 四、制作方法
1. 在东方夜雀食堂 DLC 2.5 的曲谱制作器中制作曲谱。
2. 打开曲谱保存文件夹，找到对应章节下的 `MapData.json` 获取原始谱面 JSON。
3. 复制 JSON 到 `<modid>/rhythm/` 下，重命名为有意义的英文名（符合资源包文件包命名规范，即只含有英文小写、下划线、数字）。
4. 在 JSON 中添加 `"Src"` 字段（上文已讲）。
5. 如需整体提前/延后所有音符，设置 `"Delayer"` 字段（单位毫秒）。
6. 将对应的 OGG 背景音乐放入资源包并且在 `sounds.json` 中注册（与音乐资源包相同，可参考 `Minecraft Wiki` 上的制作方法
7. 游戏内通过 `/sre:client debug rhythm_game <ID（会自动补全）>` 来打开音游界面测试。
8. 注册翻译键 `rhythm.map.<modid>.rhythm/<文件名>.json` (`<modid>/lang/zh_cn.json`中)，如：
   ```json
   {
     "rhythm.map.wifi_rhythm.rhythm/yaosemountain_1.json": "妖怪之山 PART 1"
   }
   ```

## 五、注意事项

- `"Src"` 必须填写完整的命名空间字符串，如 `<modid>:rhythm.music.xxx`。
- 音乐需要使用 Minecraft 资源包能导入的 Ogg 格式（`Vorbis`）
- `"Delayer"` 影响所有音符的 `startTime` 与 `endTime`，常用于微调音乐与谱面的对齐。
- 谱面 JSON 的文件名可任意，但建议使用音乐文件名以便管理。
