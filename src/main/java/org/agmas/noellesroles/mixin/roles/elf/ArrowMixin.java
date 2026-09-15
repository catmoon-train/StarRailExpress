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

package org.agmas.noellesroles.mixin.roles.elf;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.role_data.neutral.CupidRoleData;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.HunterRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 箭矢命中玩家的统一处理。
 *
 * <ul>
 * <li>光灵箭：只给发光，不造成伤害；落地时若射手职业能用弓/弩杀人，则提示落点附近的玩家数（同一开关）；</li>
 * <li>瞬间伤害药水箭：<b>任何职业</b>命中即死（死因：箭矢）；</li>
 * <li>能用弓/弩击杀的判定<b>只有一个入口</b>：{@code SRERole#canKillWithBowAndCrossbow()}
 * 为 true 的职业（游侠、猎人、达达利亚，以及自定义职业工具里勾选了「可用弓/弩」开关的职业），
 * 其弓 / 弩射出的<b>任何</b>箭矢（含中毒箭）命中即死；</li>
 * <li>死因与冷却按职业分派：达达利亚走自己的 {@code tartaglia_arrow} 与专属冷却，
 * 猎人走 {@code HunterRoleData#onKill}（弓 20 秒冷却 + 杀敌奖励），其余走通用的箭矢死因与 1 秒冷却。</li>
 * </ul>
 *
 * <p>
 * 击杀一律走 {@link GameUtils#killPlayer}，并在命中处取消原版伤害结算、移除箭矢——
 * 与「一击必杀」的玩法一致，也避免同一箭矢重复结算。
 */
@Mixin(AbstractArrow.class)
public class ArrowMixin {

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void noellesroles$onHitEntity(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (!(entityHitResult.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        final var cca = SREGameWorldComponent.KEY.get(player.serverLevel());
        AbstractArrow arrow = (AbstractArrow) (Object) this;

        // 光灵箭：仅发光，不造成伤害
        if (arrow instanceof SpectralArrow) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 20, 0, false, false, true));
            arrow.discard();
            ci.cancel();
            return;
        }
        if (!(arrow instanceof Arrow)) {
            return;
        }

        // 瞬间伤害药水箭：任何职业用弓/弩装填并射出即死，死因是箭矢
        if (isHarmingPotionArrow(arrow)) {
            ServerPlayer shooter = arrow.getOwner() instanceof ServerPlayer sp ? sp : null;
            GameUtils.killPlayer(player, true, shooter, GameConstants.DeathReasons.ARROW);
            arrow.discard();
            ci.cancel();
            return;
        }

        if (!(arrow.getOwner() instanceof ServerPlayer killer)) {
            return;
        }
        if (CupidRoleData.handleArrowHit((Arrow) arrow, killer, player)) {
            ci.cancel();
            return;
        }
        // 「能用弓/弩和箭杀人」的唯一判定入口：游侠、猎人、达达利亚，以及自定义职业里勾选该开关的职业。
        // 这里只管「能不能用箭矢击杀」，死因与冷却由下面的各分支决定
        SRERole killerRole = cca.getRole(killer);
        if (killerRole == null || !killerRole.canKillWithBowAndCrossbow()) {
            return;
        }
        // 达达利亚：击杀资格同样来自上面的职业开关，但死因与冷却走自己的专属分支
        if (cca.isRole(killer, ModRoles.TARTAGLIA)) {
            GameUtils.killPlayer(player, true, killer, GameConstants.DeathReasons.TARTAGLIA_ARROW);
            killer.getCooldowns().addCooldown(Items.BOW, 5 * 20);
            killer.getCooldowns().addCooldown(Items.CROSSBOW, 1 * 20);
            killer.getCooldowns().addCooldown(Items.TIPPED_ARROW, 15 * 20);
            arrow.discard();
            ci.cancel();
            return;
        }
        GameUtils.killPlayer(player, true, killer, GameConstants.DeathReasons.ARROW);
        // 猎人沿用原有的专属处理（弓 20 秒冷却 + 杀敌计数 + 每 3 杀奖励毒箭），其余职业是游侠那套 1 秒冷却
        var hunterData = RoleData.getOptional(HunterRoleData.class, killer);
        if (hunterData.isPresent()) {
            hunterData.get().onKill();
        } else {
            killer.getCooldowns().addCooldown(Items.BOW, 1 * 20);
            killer.getCooldowns().addCooldown(Items.CROSSBOW, 1 * 20);
        }
        arrow.discard();
        ci.cancel();
    }

    /** 是否是「瞬间伤害」药水箭（该类箭任何职业都能一箭致人死亡）。 */
    private static boolean isHarmingPotionArrow(AbstractArrow arrow) {
        ItemStack pickupItem = arrow.getPickupItemStackOrigin();
        PotionContents potionContents = pickupItem.get(DataComponents.POTION_CONTENTS);
        if (potionContents == null) {
            return false;
        }
        for (MobEffectInstance instance : potionContents.getAllEffects()) {
            if (instance.is(MobEffects.HARM)) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void noellesroles$onHitPlayerBody(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (entityHitResult.getEntity() instanceof PlayerBodyEntity) {
            AbstractArrow arrow = (AbstractArrow) (Object) this;
            arrow.discard();
        }
    }

    @Inject(method = "onHitBlock", at = @At("TAIL"))
    private void noellesroles$onHitBlock(BlockHitResult blockHitResult, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (arrow instanceof SpectralArrow arrow1) {
            if (arrow.getOwner() instanceof ServerPlayer serverPlayer) {
                final var cca = SREGameWorldComponent.KEY.get(serverPlayer.serverLevel());
                // 与击杀判定共用同一个职业开关：能用弓/弩杀人的职业（游侠、猎人、达达利亚、自定义开关职业）
                // 才能读到光灵箭落地时的附近人数提示
                SRERole shooterRole = cca.getRole(serverPlayer);
                if (shooterRole != null && shooterRole.canKillWithBowAndCrossbow()) {
                    // 获取箭矢击中的位置
                    BlockPos hitPos = blockHitResult.getBlockPos();
                    // 获取附近玩家列表（例如半径为5格）
                    List<ServerPlayer> nearbyPlayers = serverPlayer.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                            new AABB(hitPos).inflate(8));
                    // 输出附近玩家数量
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.arrow.near_by_players", nearbyPlayers.size())
                                    .withStyle(ChatFormatting.GREEN),
                            true);
                    arrow1.discard();
                }
            }
        } else {
            arrow.discard();
        }
    }
}
