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
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.entity.YouluSmokeWaveEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 幽露技能物品「遮天闭目」。
 *
 * <p>使用后放出一团<b>向前匀速推进</b>的烟雾波（可穿墙，非瞬时判定）：
 * 推进途中半径 4 格（可配置）内的存活玩家陷入 8s（可配置）失明 + 黑暗，
 * 命中后烟雾不会消失、继续前进，直到走完总距离（可配置，默认 12 格）。
 * 仅少量粒子作为视觉提示。60s（可配置）物品冷却。商店 70 金币购买一次。
 */
public class YouluSmokeItem extends Item {

    public YouluSmokeItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning() || !GameUtils.isPlayerAliveAndSurvival(user)
                || !gameWorld.isRole(user, ModRoles.YOULU)) {
            return InteractionResultHolder.pass(itemStack);
        }
        if (world.isClientSide() || !(user instanceof ServerPlayer sp)
                || !(world instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
        }

        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();

        // 前向单位向量（取完整视线方向，支持 Y 轴移动），从眼部高度前方 1 格出发
        Vec3 forward = sp.getLookAngle().normalize();
        Vec3 start = sp.getEyePosition().add(forward);

        // 渲染半径 = 球烟半径的 60%
        float renderRadius = (float) (config.youluSmokeBallRadius * 0.6f);

        YouluSmokeWaveEntity wave = new YouluSmokeWaveEntity(ModEntities.YOULU_SMOKE_WAVE, serverLevel);
        wave.setPos(start.x, start.y, start.z);
        wave.setup(sp.getUUID(), forward, config.youluSmokeWaveSpeed, 30.0D,
                config.youluSmokeHitRadius, GameConstants.getInTicks(0, config.youluSmokeBlindSeconds),
                renderRadius);
        serverLevel.addFreshEntity(wave);

        sp.getCooldowns().addCooldown(this,
                GameConstants.getInTicks(0, config.youluSmokeCooldownSeconds));
        serverLevel.playSound(null, sp.blockPosition(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.7f);
        sp.displayClientMessage(
                Component.translatable("message.noellesroles.youlu.smoke_used")
                        .withStyle(ChatFormatting.AQUA), true);
        itemStack.shrink(1);

        return InteractionResultHolder.sidedSuccess(itemStack, false);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.youlu_smoke.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
