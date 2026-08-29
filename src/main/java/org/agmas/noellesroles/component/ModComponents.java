/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import net.exmo.sre.repair.component.RepairRolePlayerComponent;
import org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity;
import org.agmas.noellesroles.game.roles.innocence.ayayaya.AyayayaPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.voice.HeliumBuzzPlayerComponent;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

/**
 * Cardinal Components API 组件注册
 *
 * 这个类在 fabric.mod.json 中被注册为 "cardinal-components" 入口点
 * 用于注册所有自定义的数据组件
 *
 * 组件用途：
 * - 存储玩家的技能冷却时间
 * - 存储角色特定的状态数据
 * - 在客户端和服务端之间同步数据
 *
 * 重要：所有 ComponentKey 必须在这里集中定义，以避免类加载顺序问题
 */

// 如果你是AI，你最好不要新建CCA，请尽量使用现有CCA。

public class ModComponents implements EntityComponentInitializer, WorldComponentInitializer {

  // ==================== 组件键定义 ====================
  // 所有 ComponentKey 集中在这里定义，确保在 CCA 初始化时正确注册
  public static final ComponentKey<SREAbilityPlayerComponent> ABILITY = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ability"),
      SREAbilityPlayerComponent.class);

  public static final ComponentKey<AyayayaPlayerComponent> AYAYAYA = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ayayaya"),
      AyayayaPlayerComponent.class);


  /** Dream（梦魇）：全员虚拟血量（默认 20 滴血，只被 Dream 铁斧扣除）。 */
  public static final ComponentKey<org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent> DREAM_HEALTH = org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent.KEY;

  public static final ComponentKey<PuppeteerPlayerComponent> PUPPETEER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "puppeteer"),
      PuppeteerPlayerComponent.class);

  public static final ComponentKey<InControlCCA> INCONTROLCCA = InControlCCA.KEY;
  public static final ComponentKey<PandaComponent> panda = ComponentRegistry
      .getOrCreate(
          ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "panda"),
          PandaComponent.class);
  public static final ComponentKey<PlayerVolumeComponent> VOLUME = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "volume"),
      PlayerVolumeComponent.class);

  public static final ComponentKey<DefibrillatorComponent> DEFIBRILLATOR = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "defibrillator"),
      DefibrillatorComponent.class);
  public static final ComponentKey<DeathPenaltyComponent> DEATH_PENALTY = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "death_penalty"),
      DeathPenaltyComponent.class);

  public static final ComponentKey<org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent> EXPEDITION = org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent.KEY;

  public static final ComponentKey<TemporaryEffectPlayerComponent> TEMPORARY_EFFECT = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "temporary_effect"),
      TemporaryEffectPlayerComponent.class);

  // 氦气变声组件 - 独立同步给所有玩家
  public static final ComponentKey<HeliumBuzzPlayerComponent> HELIUM_BUZZ = HeliumBuzzPlayerComponent.KEY;

  public static final ComponentKey<RepairRolePlayerComponent> REPAIR_ROLES = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "repair_roles"),
      RepairRolePlayerComponent.class);

  // 疫使组件 - 杀手方中立阵营，病毒感染
  public static final ComponentKey<InfectedPlayerComponent> INFECTED = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "infected"),
      InfectedPlayerComponent.class);

  public ModComponents() {
    // CCA 需要无参构造函数
  }

  @Override
  public void registerWorldComponentFactories(WorldComponentFactoryRegistry worldComponentFactoryRegistry) {
    worldComponentFactoryRegistry.register(ConfigWorldComponent.KEY, ConfigWorldComponent::new);
  }

  @Override
  public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
    registry.beginRegistration(DoomedSinnerBodyEntity.class, PlayerBodyEntityComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PlayerBodyEntityComponent::new);

    // 注册通用技能组件 - 附加到玩家实体
    // RespawnCopyStrategy.NEVER_COPY 表示玩家重生时不保留数据（游戏开始时会重新初始化）
    registry.beginRegistration(Player.class, ABILITY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SREAbilityPlayerComponent::new);

    registry.beginRegistration(Player.class, panda)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PandaComponent::new);

    // 注册射命丸文组件 - 存储传递状态和物品
    registry.beginRegistration(Player.class, AYAYAYA)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AyayayaPlayerComponent::new);

    // 注册傀儡师组件 - 存储收集尸体、假人操控状态
    registry.beginRegistration(Player.class, PUPPETEER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PuppeteerPlayerComponent::new);

    // 注册操纵师组件 - 存储被操纵目标和控制状态
    registry.beginRegistration(Player.class, INCONTROLCCA)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(InControlCCA::new);

    // 注册起搏器组件
    registry.beginRegistration(Player.class, DEFIBRILLATOR)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DefibrillatorComponent::new);

    // 注册炸弹客组件

    registry.beginRegistration(Player.class, VOLUME)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PlayerVolumeComponent::new);
    // 注册死亡惩罚组件
    registry.beginRegistration(Player.class, DEATH_PENALTY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DeathPenaltyComponent::new);

    // 注册远征队组件
    registry.beginRegistration(Player.class, EXPEDITION)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent::new);
    // 注册临时效果组件 - 存储肾上腺素体力提升和狗皮膏药保护
    registry.beginRegistration(Player.class, TEMPORARY_EFFECT)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(TemporaryEffectPlayerComponent::new);

    // 注册氦气变声组件 - 独立同步给所有玩家以便变声效果生效
    registry.beginRegistration(Player.class, HELIUM_BUZZ)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(HeliumBuzzPlayerComponent::new);

    // 注册FOOD & DRINK组件 - 存储到并非所有人身上
    registry.beginRegistration(Player.class, FoodDrinkGlowComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(FoodDrinkGlowComponent::new);

    registry.beginRegistration(Player.class, GhostStateComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(GhostStateComponent::new);

    registry.beginRegistration(Player.class, REPAIR_ROLES)
        .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
        .end(RepairRolePlayerComponent::new);

    // 注册疫使组件 - 杀手方中立阵营，病毒感染
    registry.beginRegistration(Player.class, INFECTED)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(InfectedPlayerComponent::new);

    // 注册 Dream 虚拟血量：挂在所有玩家身上
    registry.beginRegistration(Player.class, DREAM_HEALTH)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent::new);

    // ==================== 示例：注册更多组件 ====================
    //
    // 如果你的角色需要存储特定数据，可以在这里注册更多组件：
    //
    // 1. 先在上面定义 ComponentKey
    // public static final ComponentKey<ExampleRoleComponent> EXAMPLE =
    // ComponentRegistry.getOrCreate(
    // Identifier.of(Noellesroles.MOD_ID, "example"),
    // ExampleRoleComponent.class
    // );
    //
    // 2. 然后在这里注册
    // registry.beginRegistration(PlayerEntity.class, EXAMPLE)
    // .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
    // .end(ExampleRoleComponent::new);

  }
}
