package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.PaperDollRole;
import org.agmas.noellesroles.game.command_block.StandSpots;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** 右键蓄力至少 0.05 秒后松手，只能从无遮挡目标背后出刀，失败不进冷却。 */
public class PaperCutItem extends Item {
    public static final int CHARGE_TICKS = 1;
    public static final int COOLDOWN = 12 * 20;
    public static final double RANGE = 5.0D;

    public PaperCutItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUse(player)) {
            return InteractionResultHolder.fail(stack);
        }
        player.playSound(TMMSounds.ITEM_KNIFE_PREPARE, 1.0F, 1.0F);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private boolean canUse(Player player) {
        return !player.isSpectator() && GameUtils.isPlayerAliveAndSurvival(player)
                && !player.getCooldowns().isOnCooldown(this)
                && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.PAPER_DOLL);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof ServerPlayer self) || !canUse(self)
                || getUseDuration(stack, user) - remainingUseTicks < CHARGE_TICKS) {
            return;
        }
        ServerPlayer target = GazeRays.firstVisiblePlayer(self, RANGE, 0.4D);
        if (target == null) {
            self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.missed")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // 只看目标的水平朝向，避免抬头/低头改变背面；正侧面及重叠位置不算背后。
        if (!(StandSpots.horizontal(target, 1.0D).dot(self.position().subtract(target.position())) < -1.0E-6D)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.behind_only")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean killed = CommandBlockCombat.tryKill(self, target, PaperDollRole.DEATH_CUT, false,
                Component.translatable("message.noellesroles.paper_doll.blocked"));
        if (killed) {
            self.getCooldowns().addCooldown(this, COOLDOWN);
            org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.55F, 1.6F);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        releaseUsing(stack, level, user, 0);
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 110;
    }
}
