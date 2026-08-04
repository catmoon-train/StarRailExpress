# 注意事项
在你写代码之前，请阅读 `ai_doc.md` 以及 `docs/*.md`

`docs/*.md` 中的 API 可能已过时。以实际为准。

文件中的AI提示词与注释十分重要，你不能忽视。

按键绑定原则：优先不使用新的按键绑定，复用旧的（功能类似且不冲突时）。

按键绑定注册是常量，不应该动态注册。

避免与当前的和MC的按键冲突。

冷门按键绑定应该使用冷门的按键，比如小键盘上的按键。

UI打开不一定必须要按键绑定，也可以写客户端命令打开。客户端命令注册位置：`SREClientCommand.java`

功能类似或相同的尽量使用同一按键。

# 有关依赖

尽量不引入新依赖。引入的新依赖请不要直接丢文件。而是使用在线资源下载。

# 版本号

不要随便改模组版本号。版本号应当由人工手动更改。

# 有关玩家职业数据

你可以使用SRERole中的 
```java
.setRoleData(RoleData实例类::new)
```

RoleData实例类：可以extends SimpleRoleData，或是 implements RoleData
因为每次实例都是创建新的，理论上你不需要init和clear。
获取此实例类方法是 `RoleData.getNullable(类.class, 玩家)`
或者 `RoleData.getOptional(类.class, 玩家);`

！！！尽量使用此API，不要使用CCA！！！

# 语言文件
遵循使用翻译键，优先补全 `zh_cn.json`