# 如何创建扩展

> 本文档为**在现有代码库中**注册新内容的快速参考。  
> 如果你要创建一个**独立的扩展模组**（新 Gradle 项目），请先阅读 **[docs/创建模组.md](docs/创建模组.md)**。  
> 如果你要**新增或修改职业（角色）**，请先阅读 **[docs/角色开发指南.md](docs/角色开发指南.md)**（职业注册、组件、技能、事件、文案、商店速查）。

## 项目构建

在开始开发前，确保项目可以正常编译和运行：

```bash
# 编译并打包（产物在 build/libs/）
./gradlew build

# 启动测试客户端
./gradlew runClient

# 启动测试服务端
./gradlew runServer

# 清理构建缓存
./gradlew clean
```

> **环境要求：** JDK 21，并能访问 `maven.fabricmc.net`、`maven.terraformersmc.com` 等 Maven 仓库。

## 注册新角色/职业

### 注册方式：

在ModRoles.java文件中

- 添加自定义角色id
- 注册公有静态角色：可以在初始化函数中初始化或直接在声明时初始化

## 注册角色/职业数据（CCA替代品）

本模组使用 `RoleData` 接口及其实现类来管理玩家职业的持久化数据，不再依赖 Cardinal Components API（CCA）。下文将介绍如何为您的职业创建数据类，并将其注册到职业系统中。

---

### 1. 概述

- **`RoleData`** 是玩家职业数据的顶层接口，定义了数据的生命周期（`init`、`clear`）、同步（`writeToSyncNbt`、`readFromSyncNbt`）以及客户端/服务端 Tick 回调。

- **`SimpleRoleData`** 是一个基础抽象实现，提供了 NBT 读写辅助方法，推荐直接继承该类以简化开发。

- 每个职业可以拥有独立的 `RoleData` 实例，当玩家切换职业时，旧实例会被丢弃，新职业会创建新的数据对象。

---

### 2. 创建职业数据类

您需要创建一个类，实现 `RoleData` 接口（或继承 `SimpleRoleData`），并实现所有抽象方法。

#### 示例：继承 `SimpleRoleData`

```java
import io.wifi.starrailexpress.api.data.SimpleRoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

public class YourRoleDataClass extends SimpleRoleData {
    
    private int customValue;

    public YourRoleDataClass(RoleDataContext context) {
        super(context);
        // 初始化自定义字段
        this.customValue = 0;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 写入数据
        tag.putInt("CustomValue", customValue);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 读取数据
        this.customValue = tag.getInt("CustomValue");
    }

    // 可选：重写 init/clear/clientTick/serverTick
    @Override
    public void init() {
        // 职业赋予时触发
    }

    @Override
    public void clear() {
        // 职业移除时触发
    }

    // 自定义业务方法
    public int getCustomValue() {
        return customValue;
    }

    public void setCustomValue(int value) {
        this.customValue = value;
    }
}
```

> **注意**：构造方法必须接受一个 `RoleDataContext` 参数，用于获取玩家对象及其他上下文信息。

---

### 3. 注册职业数据

在定义职业（`Role` 实现类）时，通过 `setRoleData` 方法绑定数据工厂。该方法接受一个函数式接口，用于在玩家获得该职业时创建 `RoleData` 实例。

#### 3.1 简单注册（通过构造方法引用）

如果您的数据类构造方法与 `RoleDataContext` 匹配，可以直接使用方法引用：

```java
role.setRoleData(YourRoleDataClass::new);
```

#### 3.2 自定义初始化逻辑

如果您需要在创建时执行额外操作，可以使用 Lambda 表达式：

```java
role.setRoleData((ctx) -> {
 // 可以在这里根据上下文做额外处理
 YourRoleDataClass data = new YourRoleDataClass(ctx);
 data.setCustomValue(100); // 初始值
 return data;
});
```



> **说明**：`setRoleData` 会在玩家被赋予该职业时调用，返回的 `RoleData` 实例将被关联到玩家的组件中。

---

### 4. 获取职业数据

在游戏逻辑中，您可以使用 `RoleData` 提供的静态工具方法获取当前玩家的职业数据。

#### 4.1 获取任意类型的职业数据

```java
// 返回 Optional<RoleData>
Optional<RoleData> optional = RoleData.getOptional(player);
optional.ifPresent(data -> {
 // 处理数据
});
```



#### 4.2 获取指定类型的数据

```java
// 返回 Optional<YourRoleDataClass>
Optional<YourRoleDataClass> opt = RoleData.getOptional(YourRoleDataClass.class, player);
opt.ifPresent(data -> {
 int val = data.getCustomValue();
});
```



#### 4.3 直接获取或创建

若数据不存在或类型不匹配，会自动创建新实例（但不会自动绑定到玩家，需自行处理）：

```java
YourRoleDataClass data = RoleData.getOrCreate(YourRoleDataClass.class, player);
if (data != null) {
 // 使用数据
}
```



---

### 5. 同步与持久化

- **同步**：`writeToSyncNbt` 和 `readFromSyncNbt` 用于客户端-服务端数据同步，系统会在合适时机调用。
- **不支持持久化**

---

### 6. 生命周期回调

| 方法             | 触发时机            |
| -------------- | --------------- |
| `init()`       | 玩家被赋予该职业时（仅服务端） |
| `clear()`      | 玩家离开该职业时（仅服务端）  |
| `clientTick()` | 客户端每 tick 调用    |
| `serverTick()` | 服务端每 tick 调用    |

您可以根据需要重写这些方法。

---

### 7. 注意事项

- 每个玩家同时只能拥有一个职业的 `RoleData` 实例，切换职业时会自动替换。
- 数据类必须提供 `RoleDataContext` 构造器，否则 `RoleData.create()` 会失败。
- 若使用 `SimpleRoleData`，您可以直接使用其提供的 `getXxxTag` 辅助方法简化 NBT 操作。

---

### 8. 完整示例

```java
// 1. 定义数据类
public class WarriorData extends SimpleRoleData {
 private int rage;
 public WarriorData(RoleDataContext ctx) { super(ctx); }
 @Override
 public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
 tag.putInt("Rage", rage);
 }
 @Override
 public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
 this.rage = tag.getInt("Rage");
 }
 public void addRage(int amount) { this.rage += amount; }
}
// 2. 在职业注册处绑定
Role warriorRole = ...;
warriorRole.setRoleData(WarriorData::new);
// 3. 在逻辑中获取
Optional<WarriorData> data = RoleData.getOptional(WarriorData.class, player);
data.ifPresent(d -> d.addRage(10));
```

---

### 9. 迁移自 CCA

如果您之前使用了 Cardinal Components API，现在只需：

- 移除对 `Component` 和 `ComponentKey` 的依赖。
- 将原有组件类改为实现 `RoleData`（或继承 `SimpleRoleData`）。
- 使用 `role.setRoleData(factory)` 替代原有的 `setComponentKey(KEY)`。
- 使用 `RoleData.getOptional(clazz, player)` 替代原有的 `KEY.get(player)`。

如此即可平滑迁移。

### 10. 无法从CCA迁移的情况

- 玩家职业会变更，需要持续化的数据。
- 非特定职业玩家的数据。
- 保存到世界的全局数据。

## 注册新物品

### 注册方式：

创建物品Java类继承Item类并实现功能

在ModItems.java文件中添加公有静态物品常量对象并赋予id

## 注册实体

### 注册需求：

创建实体类并注册，创建实体渲染器类并为实体注册

### 注册方式：

实体类创建java文件后在ModEntities.java件中进行注册

再在client.renderer中创建EntityRender.java渲染器类用于客户端渲染，
并在NoellesrolesClient.java中的registerEntityRenderers方法中对实体的渲染器进行注册

## 注册新商店

### 注册方式：

在Noellesroles.java文件中

- 声明新的静态商品对象列表
- 在initShops()函数中对列表进行初始化添加新物品
- 在shopRegiester()函数中为角色注册商店

## 注册网络包

### 定义网络包

在packet下创建___C2Packets.java(或S2C)类继承CustomPacketPayload作为网络包包含：

- 网络包的唯一标识符ResourcesLocatiom
- 网络包类型标识符
- 序列化/反序列化编解码
- 定义编解码器写入读取方法
- 需要传输的内容等

### 注册网络包

对于C2S网络包

- 在模组初始化调用registerPackets函数中注册网络包
- 并且对该网络包进行处理

对于S2C网络包

- 在registerPackets1中进行注册
- 在Client主类中进行处理

## 创建GUI

### 创建方式：

client下创建___Screen.java类继承Screen作为新GUI

重写init函数对GUI进行初始化：注意布局以及添加到渲染列表中

## 对列车谋杀案模组修改

### 代码混合：

- 在mixin文件夹内创建java类，使用@Minin注解进行混合
- 在noellesroles.minin.json文件中添加混合配置

## 翻译

在en_us.json 和 zh_cn.json中添加注册的id对应的汉化（根据已有汉化即可）

