package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.Entity303Role;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.Entity303RoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 只能撕开偷过的脸，伪装期间也可使用。 */
public class PeelBladeItem extends Item {
    public static final int COOLDOWN = 20 * 20;

    public PeelBladeItem(Properties properties) {
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
        if (!SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.ENTITY_303)) {
            return InteractionResultHolder.fail(stack);
        }
        List<ServerPlayer> hit = GazeRays.playersAlong(self, 4.0D, 0.45D);
        if (hit.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        ServerPlayer target = hit.get(0);
        Entity303RoleData data = RoleData.getNullable(Entity303RoleData.class, self);
        if (data == null || !data.knows(target.getUUID())) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_303.unknown")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
            return InteractionResultHolder.fail(stack);
        }
        boolean killed = CommandBlockCombat.tryKill(self, target, Entity303Role.DEATH_PEEL, false,
                Component.translatable("message.noellesroles.entity_303.blocked"));
        self.getCooldowns().addCooldown(this, COOLDOWN);
        if (killed) {
            data.onKill(self, self.serverLevel().getGameTime());
        }
        org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.9F);
        return InteractionResultHolder.consume(stack);
    }
}
