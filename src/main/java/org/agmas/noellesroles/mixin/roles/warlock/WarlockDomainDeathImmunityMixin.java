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

package org.agmas.noellesroles.mixin.roles.warlock;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.roles.killer.warlock.WarlockDomainManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * 咒术师「灰髓之境」领域内的环境致死免疫。
 *
 * <p>领域位于高空虚空（{@link WarlockDomainManager#DOMAIN_Y} = 200，Y 超出地图 playArea.maxY），
 * 而 {@code SREGameWorldComponent#isPlayerOutGameAreas} 每 tick 会把「超出 Y 边界」的玩家判为出界，
 * 用 {@code FELL_OUT_OF_TRAIN}（列车碾死）强制处死——于是被拉入领域的人一进去就被秒杀。
 *
 * <p>领域本应是一处独立祭场，域内只应死于战斗（咒术师 / 彼此）。这里在所有死亡的唯一入口
 * {@link GameUtils#killPlayer} HEAD 处兜底：若受害者正处于领域内、且死因属于「出界 / 落水 / 岩浆 / 黑暗」
 * 等环境判定，则取消这次死亡（软硬死亡都拦，强制死亡会绕过否决事件故必须走 mixin）。
 * 战斗死因（枪 / 刀等）不在名单内，照常结算。
 *
 * <p>镜像 {@code AdventurerDeathImmunityMixin} / {@code ManipulatorControlledDeathImmunityMixin} 的做法。
 */
@Mixin(GameUtils.class)
public abstract class WarlockDomainDeathImmunityMixin {

    /** 领域内应被免疫的「环境 / 区域判定」死因。 */
    private static final Set<ResourceLocation> DOMAIN_IMMUNE = Set.of(
            GameConstants.DeathReasons.FELL_OUT_OF_TRAIN,
            GameConstants.DeathReasons.CANNOT_SWIM,
            GameConstants.DeathReasons.LAVA,
            GameConstants.DeathReasons.DEATH_IN_DARKNESS);

    @Inject(method = "killPlayer(Lnet/minecraft/world/entity/player/Player;Z"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/resources/ResourceLocation;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void noellesroles$warlockDomainEnvironmentImmunity(
            Player victim, boolean spawnBody, Player killer,
            ResourceLocation deathReason, boolean forceDeath, CallbackInfo ci) {
        if (deathReason == null || victim == null) {
            return;
        }
        if (!DOMAIN_IMMUNE.contains(deathReason)) {
            return; // 战斗死因照常结算
        }
        if (WarlockDomainManager.isInDomain(victim.getUUID())) {
            ci.cancel();
        }
    }
}
