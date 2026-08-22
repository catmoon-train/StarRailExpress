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
import net.exmo.sre.repair.component.RepairRolePlayerComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity;
import org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaComponent;
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
 * Cardinal Components API 组件注册。
 *
 * 职业专属状态请用 {@code SRERole.setRoleData}，不要在这里新建职业 CCA。
 * 这里只保留挂在任意玩家 / 世界 / 实体上、或跨职业复用的组件。
 */
public class ModComponents implements EntityComponentInitializer, WorldComponentInitializer {

  public static final ComponentKey<SREAbilityPlayerComponent> ABILITY = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ability"),
      SREAbilityPlayerComponent.class);

  public static final ComponentKey<DreamHealthComponent> DREAM_HEALTH = DreamHealthComponent.KEY;

  public static final ComponentKey<InControlCCA> INCONTROLCCA = InControlCCA.KEY;

  public static final ComponentKey<PandaComponent> panda = ComponentRegistry.getOrCreate(
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

  public static final ComponentKey<ExpeditionComponent> EXPEDITION = ExpeditionComponent.KEY;

  public static final ComponentKey<TemporaryEffectPlayerComponent> TEMPORARY_EFFECT = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "temporary_effect"),
      TemporaryEffectPlayerComponent.class);

  public static final ComponentKey<HeliumBuzzPlayerComponent> HELIUM_BUZZ = HeliumBuzzPlayerComponent.KEY;

  public static final ComponentKey<RepairRolePlayerComponent> REPAIR_ROLES = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "repair_roles"),
      RepairRolePlayerComponent.class);

  public static final ComponentKey<InfectedPlayerComponent> INFECTED = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "infected"),
      InfectedPlayerComponent.class);

  public ModComponents() {
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

    registry.beginRegistration(Player.class, ABILITY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SREAbilityPlayerComponent::new);

    registry.beginRegistration(Player.class, panda)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PandaComponent::new);

    registry.beginRegistration(Player.class, INCONTROLCCA)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(InControlCCA::new);

    registry.beginRegistration(Player.class, DEFIBRILLATOR)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DefibrillatorComponent::new);

    registry.beginRegistration(Player.class, VOLUME)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PlayerVolumeComponent::new);

    registry.beginRegistration(Player.class, DEATH_PENALTY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DeathPenaltyComponent::new);

    registry.beginRegistration(Player.class, EXPEDITION)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ExpeditionComponent::new);

    registry.beginRegistration(Player.class, TEMPORARY_EFFECT)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(TemporaryEffectPlayerComponent::new);

    registry.beginRegistration(Player.class, HELIUM_BUZZ)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(HeliumBuzzPlayerComponent::new);

    registry.beginRegistration(Player.class, GhostStateComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(GhostStateComponent::new);

    registry.beginRegistration(Player.class, REPAIR_ROLES)
        .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
        .end(RepairRolePlayerComponent::new);

    registry.beginRegistration(Player.class, INFECTED)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(InfectedPlayerComponent::new);

    registry.beginRegistration(Player.class, DREAM_HEALTH)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DreamHealthComponent::new);
  }
}
