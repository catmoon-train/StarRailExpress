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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.TrapperNetEntity;
import org.agmas.noellesroles.game.roles.killer.trapper.TrapperPlayerComponent;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 设陷者大招物品「捕网枪」。
 *
 * <p>通过 G 键（选中「捕网」类型）花费 200 金币购得，到手即进入
 * {@link TrapperPlayerComponent#NET_GUN_COOLDOWN_SECONDS}s 初始冷却。
 * 右键发射一张捕网（{@link TrapperNetEntity}，蜘蛛网方块外观）：命中玩家或落地后
 * 禁锢半径 5 格内的玩家 8s（无法移动/使用物品/使用技能）。每次发射后进入 150s 冷却。
 */
public class TrapperNetGunItem extends Item {

    public TrapperNetGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
                                                           @NotNull InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(user)
                || !gameWorld.isRole(user, ModRoles.TRAPPER)) {
            return InteractionResultHolder.pass(itemStack);
        }
        TrapperPlayerComponent comp = TrapperPlayerComponent.KEY.get(user);
        if (comp.netGunCooldownTicks > 0) {
            if (!world.isClientSide()) {
                user.displayClientMessage(
                        Component.translatable("message.noellesroles.trapper.on_cooldown",
                                        Math.max(1, (comp.netGunCooldownTicks + 19) / 20))
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(itemStack);
        }
        if (world.isClientSide() || !(user instanceof ServerPlayer sp)
                || !(world instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
        }

        // ---- 发射捕网 ----
        TrapperNetEntity net = new TrapperNetEntity(ModEntities.TRAPPER_NET, serverLevel);
        net.setOwner(sp);
        Vec3 eye = sp.getEyePosition();
        net.setPos(eye.x, eye.y - 0.2, eye.z);
        net.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0.0f, 1.3f, 0.5f);
        serverLevel.addFreshEntity(net);

        serverLevel.playSound(null, sp.blockPosition(),
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.2f, 0.7f);
        serverLevel.playSound(null, sp.blockPosition(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2f, 0.7f);
        serverLevel.sendParticles(ParticleTypes.CLOUD,
                eye.x, eye.y, eye.z, 14, 0.2, 0.2, 0.2, 0.24);

        // ---- 后坐力（视角 + 物理推力） ----
        RandomSource random = sp.getRandom();
        // 视角上抬 6~9 度（更猛）
        float pitchRecoil = -6.0f - random.nextFloat() * 3.0f;
        float yawRecoil = (random.nextFloat() - 0.5f) * 5.0f;  // ±2.5 度
        sp.turn(yawRecoil, pitchRecoil);

        // 向后推力：沿视线反方向，力度 1.8（可调高）
        Vec3 lookVec = sp.getLookAngle();
        double force = 1.8;  // 数值越大后坐越强
        sp.setDeltaMovement(sp.getDeltaMovement().add(lookVec.scale(-force)));

        // 强制同步速度到客户端（确保立即生效）
        sp.connection.send(new ClientboundSetEntityMotionPacket(sp));

        // ---- 进入冷却 ----
        comp.netGunCooldownTicks = TrapperPlayerComponent.NET_GUN_COOLDOWN_TICKS;
        comp.sync();
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.trapper.net_fired")
                        .withStyle(ChatFormatting.GREEN), true);
        return InteractionResultHolder.sidedSuccess(itemStack, false);
    }
    @Override
    public void appendHoverText(@NotNull ItemStack stack, TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.trapper_net_gun.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
