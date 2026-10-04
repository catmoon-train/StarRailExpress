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

package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(Player.class)
public class RiptideTridentMixin {

    /**
     * 本 tick 起点位置（位移前），用于把撞击判定从「当前点膨胀」升级为「扫掠整段位移」。
     */
    @Unique
    private Vec3 riptideTickStartPos;

    /**
     * 本 tick 开始时（位移前）是否处于激流冲刺状态。必须在 tick 之前取值：
     * 原版在玩家互相推挤时会调用 {@code stopAutoSpinAttack()} 打断激流，
     * 等到位移之后再读 {@code isAutoSpinAttack()} 只会读到 false，判定就永远漏掉了。
     */
    @Unique
    private boolean riptideActiveBeforeTick = false;

    /**
     * 本次激流已经撞死的目标，避免同一目标因连续多个 tick 的扫掠被反复结算死亡。
     */
    @Unique
    private final Set<UUID> riptideHitTargets = new HashSet<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private void noellesroles$checkRiptideCollision(CallbackInfo ci) {
        if (SRE.isLobby)
            return;

        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        ServerLevel serverLevel = serverPlayer.serverLevel();

        // 记录激流冲刺的起点与起始状态，供 TAIL 的扫掠判定使用。
        // 放在所有提前 return 之前，保证后续分支无论走哪条路都不会留下过期的状态。
        this.riptideTickStartPos = player.position();
        this.riptideActiveBeforeTick = player.isAutoSpinAttack();

        // 检查是否能用三叉戟杀人（海王/水鬼等）
        var role = SREGameWorldComponent.KEY.get(serverLevel).getRole(player.getUUID());
        boolean canTridentKill = role != null && role.canKillWithTrident();
        boolean isSeaKing = SREGameWorldComponent.KEY.get(serverLevel).isRole(player.getUUID(), ModRoles.SEA_KING);
        boolean isWaterGhost = SREGameWorldComponent.KEY.get(serverLevel).isRole(player.getUUID(), ModRoles.WATER_GHOST);

        boolean isUsingRiptide = this.riptideActiveBeforeTick;

        // 海王：进入水中自动获得海豚的恩惠
        if (isSeaKing && (player.isInWater() || player.isUnderWater())) {
            player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, true, false, true));
        }

        // 潜水靴深海探索者3附魔 - 所有玩家检查脚部装备
        ItemStack feetItem = player.getItemBySlot(EquipmentSlot.FEET);
        if (feetItem.is(ModItems.DIVING_BOOTS)) {
            boolean hasDepthStrider = false;
            for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : feetItem
                    .getEnchantments().entrySet()) {
                String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
                if (enchantmentId.contains("minecraft:depth_strider")) {
                    hasDepthStrider = true;
                    // 检查等级是否为3，如果不是则更新
                    if (entry.getValue() != 3) {
                        feetItem.remove(DataComponents.ENCHANTMENTS);
                        hasDepthStrider = false;
                    }
                    break;
                }
            }
            if (!hasDepthStrider) {
                // 没有深海探索者附魔，或者等级不对，添加深海探索者3
                feetItem.enchant(serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                        .filter(holder -> {
                            return holder.is((Enchantments.DEPTH_STRIDER));
                        }).findFirst().get(), 3);
            }
        }

        if (!canTridentKill)
            return;

        // 检查主手是否持有三叉戟
        ItemStack mainHandItem = player.getMainHandItem();
        if (!mainHandItem.is(Items.TRIDENT))
            return;

        // 统一处理海王和水鬼的附魔
        if (isSeaKing) {
            // 海王：忠诚3
            boolean hasLoyalty = false;
            for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : mainHandItem
                    .getEnchantments().entrySet()) {
                String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
                if (enchantmentId.contains("minecraft:loyalty")) {
                    hasLoyalty = true;
                    break;
                }
            }
            if (!hasLoyalty) {
                mainHandItem.enchant(serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                        .filter(holder -> {
                            return holder.is((Enchantments.LOYALTY));
                        }).findFirst().get(), 3);
            }
        } else if (isWaterGhost) {
            // 水鬼：激流2（先检查是否已有激流附魔）
            boolean hasRiptide = false;
            for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : mainHandItem
                    .getEnchantments().entrySet()) {
                String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
                if (enchantmentId.contains("minecraft:riptide")) {
                    hasRiptide = true;
                    // 检查等级是否为2，如果不是则更新
                    if (entry.getValue() != 2) {
                        mainHandItem.remove(DataComponents.ENCHANTMENTS);
                        hasRiptide = false;
                    }
                    break;
                }
            }
            if (!hasRiptide) {
                // 没有激流附魔，或者等级不对，添加激流2
                mainHandItem.enchant(serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders()
                        .filter(holder -> {
                            return holder.is((Enchantments.RIPTIDE));
                        }).findFirst().get(), 2);
            }
        }
        // 检查三叉戟是否有激流附魔
        boolean hasRiptide = false;
        for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : mainHandItem
                .getEnchantments().entrySet()) {
            String enchantmentId = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
            if (enchantmentId.contains("minecraft:riptide")) {
                hasRiptide = true;
                break;
            }
        }

        if (!hasRiptide)
            return;

        // 激流状态：在水中/雨中
        boolean isInWaterOrRain = player.isInWaterOrRain();
        if (!isInWaterOrRain)
            return;

        // 检查玩家是否正在使用激流技能
        // 只有在使用激流技能时才进行碰撞检测

        if (!isUsingRiptide)
            return;

        // 撞击判定已迁移到 noellesroles$resolveRiptideHits（tick 的 TAIL）：
        // 原版激流位移发生在 tick 中途，HEAD 上判定的位置还是「冲出去之前」，
        // 目标往往是在本 tick 位移之后才进入范围的，加上撞到人时原版会
        // stopAutoSpinAttack() 打断激流，于是表现为「撞到了、停下了、却没伤害也没死因」。
    }

    /**
     * 激流撞击结算：扫掠本 tick 的整段位移，覆盖「一帧高速穿过判定体积」的情况。
     *
     * <p>
     * 判定放在 tick 末尾（位移、推挤、stopAutoSpinAttack 都已发生之后），并且用 tick 起点作为激流状态与视线的基准，
     * 因此即使本 tick 冲撞被原版打断，也仍能判定出撞到的人。
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void noellesroles$resolveRiptideHits(CallbackInfo ci) {
        if (SRE.isLobby)
            return;

        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        // 本 tick 开始时就没有在激流：不结算。
        // 注意不能用当前的 isAutoSpinAttack()，撞人时原版会把它置为 false。
        if (!this.riptideActiveBeforeTick || this.riptideTickStartPos == null)
            return;

        ServerLevel serverLevel = serverPlayer.serverLevel();
        var role = SREGameWorldComponent.KEY.get(serverLevel).getRole(player.getUUID());
        if (role == null || !role.canKillWithTrident())
            return;
        if (!player.isInWaterOrRain())
            return;
        if (!player.getMainHandItem().is(Items.TRIDENT))
            return;

        boolean isWaterGhost = SREGameWorldComponent.KEY.get(serverLevel).isRole(player.getUUID(), ModRoles.WATER_GHOST);

        Vec3 from = this.riptideTickStartPos;
        Vec3 to = player.position();

        // 对水鬼使用更小的判定箱以避免穿墙击杀；命中与否另用视线检查确认
        double inflateAmount = isWaterGhost ? 1.0 : 1.5;
        // 覆盖整段位移的包围盒，而不是只取终点：激流 II 单 tick 位移可以超过原来的 2 格判定距离
        AABB hitBox = new AABB(from, to).inflate(inflateAmount);

        for (ServerPlayer target : serverLevel.getEntitiesOfClass(ServerPlayer.class, hitBox)) {
            if (target == player || target.isSpectator() || target.isCreative())
                continue;
            if (this.riptideHitTargets.contains(target.getUUID()))
                continue;
            if (!GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            // 视线检查从「冲出去之前」的位置出发，避免被墙挡住却把人撞死
            Vec3 eye = from.add(0.0, player.getEyeHeight(), 0.0);
            if (serverLevel.clip(new ClipContext(eye, target.getEyePosition(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS)
                continue;

            this.riptideHitTargets.add(target.getUUID());
            GameUtils.killPlayer(target, true, serverPlayer, SRE.id("trident"));
            if (isWaterGhost) {
                // 水鬼：激流三叉戟击杀后进入30秒冷却
                player.getCooldowns().addCooldown(Items.TRIDENT, 20 * 30);
            }
        }
    }
}
