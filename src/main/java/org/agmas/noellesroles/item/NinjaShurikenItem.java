package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.item.KnifeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

public class NinjaShurikenItem extends KnifeItem {

    private static final float SHURIKEN_RANGE = 20.0F;

    public NinjaShurikenItem(Properties properties) {
        super(properties);
    }

    private static HitResult getShurikenTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user,
                entity -> entity instanceof Player player && GameUtils.isPlayerAliveAndSurvival(player),
                SHURIKEN_RANGE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer shooter)) {
            return InteractionResultHolder.pass(stack);
        }

        // 检查物品冷却
        if (shooter.getCooldowns().isOnCooldown(ModItems.NINJA_SHURIKEN)) {
            return InteractionResultHolder.pass(stack);
        }

        HitResult collision = getShurikenTarget(shooter);
        if (collision instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            if (target instanceof Player victim) {
                GameUtils.killPlayer(victim, true, shooter, Noellesroles.id("ninja_shuriken_kill"));
                stack.shrink(1);
                // 添加30秒物品冷却
                shooter.getCooldowns().addCooldown(ModItems.NINJA_SHURIKEN, 30 * 20);
                return InteractionResultHolder.consume(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    @Override
    public String getItemSkinType() {
        return "";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.ninja_shuriken.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}