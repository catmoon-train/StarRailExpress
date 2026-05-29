# 自定义直播随机皮肤
您可以在客户端设置中启用直播随机皮肤，以防止自定义皮肤对您的直播造成影响。

您可以通过资源包进行自定义修改。

模组会在资源包重置时读取 `assets/<namespace>/player_skins.json`

文件格式示例：

```json
[
    {
        "path": "minecraft:textures/entity/player/slim/alex.png",
        "is_slim": true
    },
    {
        "path": "minecraft:textures/entity/player/slim/ari.png",
        "is_slim": true
    }
]
```

- `path` 指向资源包中存有皮肤的文件路径
- `is_slim` 皮肤为宽皮肤还是纤细皮肤