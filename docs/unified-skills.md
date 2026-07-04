# 统一技能与被动系统

## 已实现

- `RoleSkill.Definition`：统一声明技能 ID、名称、冷却、次数、持续释放间隔和自己的施放报幕。
- 多技能槽：同一职业可以注册多个技能，默认按 `V` 循环选择，按 `G` 使用。
- 持续释放：技能可响应 `PRESS`、`HOLD`、`RELEASE`。服务端按定义的间隔限制 `HOLD`。
- `SREAbilityPlayerComponent`：按技能 ID 同步冷却、剩余次数、最大次数、释放次数、当前槽位和持续施放状态。
- `UnifiedSkillHud`：统一显示所有技能的选中状态、冷却、次数和本局释放次数，并显示统一被动名称。
- `RolePassive`：统一注册一名职业的多个服务端被动 tick。
- 兼容旧接口：原有 `RoleSkill.register(role, context -> {})`、`cooldown`、`charges` 字段仍然可用。

## 首批迁移

| 职业/来源 | 状态 | 说明 |
|---|---|---|
| 疫使 | 已迁移 | 感染技能，80 秒冷却，3 次使用，统一 HUD 和自己的施放报幕 |
| 鹈鹕 | 已迁移 | 拆为“吞噬”和“释放”两个技能槽 |
| 自定义职业 | 已迁移 | 指令技能统一处理冷却、HUD、初始冷却和报幕 |
| 幻音师 | 部分迁移 | 每 30 秒 50 金币被动已迁移；传送和商店技能仍使用旧组件 |

## 注册示例

```java
RoleSkill.register(MY_ROLE,
    RoleSkill.skill(id("beam"), "skill.example.beam", context -> {
        if (context.phase() == RoleSkill.Phase.PRESS) {
            startBeam(context.player());
        } else if (context.phase() == RoleSkill.Phase.HOLD) {
            updateBeam(context.player());
        } else {
            stopBeam(context.player());
        }
        return true;
    }).cooldownSeconds(20).charges(3).continuous(2).build(),
    RoleSkill.skill(id("dash"), "skill.example.dash", context -> {
        dash(context.player());
        return true;
    }).cooldownSeconds(8).build()
);

RolePassive.register(MY_ROLE,
    RolePassive.passive(id("income"), "passive.example.income", 20 * 30,
        player -> giveIncome(player, 50))
);
```

处理器返回 `true` 表示技能实际成功，系统才会扣次数、进入冷却、增加释放次数并显示自己的报幕。目标玩家可通过 `context.target()` 获取。

## 尚未迁移

以下内容仍使用职业私有组件、私有网络包或独立 HUD，需要逐职业迁移：

- `AbilityHandler` 中的旧职业分支。
- `RicesRoleRhapsodyClient.onAbilityKeyPressed` 及其 Boxer、Athlete、Ninja、Star、Creeper、Shadow Falcon、Builder 等私有技能包。
- `GKeyRoleSkill` 中除疫使、鹈鹕外的特殊客户端交互。
- `CommonClientHudRenderer` 和 `client/hud/roles` 中的职业专属冷却/次数文本。
- Imitator、Mortician Bodymaker 等已有多模式但自带状态机的职业。
- Singer、Super Star、Shadow Falcon 等持续效果职业；可直接改用 `continuous(intervalTicks)`。
- 各职业组件中自行递减的 `abilityCooldown`、`teleportCooldown`、`skillCooldown` 等字段。
- 物品技能、商店技能和修机模式技能；它们是否纳入职业技能槽需按玩法决定。
- 除幻音师收入外，现有职业被动尚未迁入 `RolePassive`。

迁移时优先保留真正特殊的目标选择或界面打开逻辑，冷却、次数、输入阶段、同步、HUD 和报幕交给统一系统。
