package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.LegendFx;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.FarlandsRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 在身前 3 格撕开一道裂隙。1.5 秒后吞掉还站在里面的人。 */
public class FarlandsRiftItem extends Item {
    public static final int COOLDOWN = 30 * 20;

    public FarlandsRiftItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer self)) {
            return InteractionResultHolder.success(stack);
        }
        if (self.isSpectator() || self.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.FARLANDS)) {
            return InteractionResultHolder.fail(stack);
        }
        FarlandsRoleData data = RoleData.getNullable(FarlandsRoleData.class, self);
        if (data == null) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 look = self.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        Vec3 at = self.position().add(flat.normalize().scale(3.0D));
        if (!data.armRift(at, self.serverLevel().getGameTime())) {
            self.displayClientMessage(Component.translatable("message.noellesroles.farlands.rift_busy")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        // 一个包交给客户端持续绘制整个前摇，范围与服务端判定保持一致。
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(self.serverLevel(), LegendFx.RING, at,
                FarlandsRoleData.RIFT_DELAY, (float) FarlandsRoleData.RIFT_RADIUS, LegendFx.RIFT);
        self.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F,
                0.5F);
        self.displayClientMessage(Component.translatable("message.noellesroles.farlands.rift_open")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        self.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }
}
