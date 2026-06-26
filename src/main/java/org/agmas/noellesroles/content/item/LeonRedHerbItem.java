package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 红色草药（里昂专属）。
 *
 * <p>长按右键使用：为自己套上一层护盾（{@link SREArmorPlayerComponent#giveArmor()}，不可叠加）。
 * 一局发放一次，里昂死亡后不随手枪一起掉落（自定义物品不在 {@code GameUtils.shouldDropOnDeath}
 * 名单内，因此死亡时不会掉落）。
 */
public class LeonRedHerbItem extends Item {

    /** 长按蓄力时长（tick），约 1.5 秒。 */
    private static final int USE_DURATION = 30;

    public LeonRedHerbItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (gameWorld == null || !gameWorld.isRunning()
                || !gameWorld.isRole(user, ModRoles.LEON)
                || !GameUtils.isPlayerAliveAndSurvival(user)) {
            return InteractionResultHolder.pass(stack);
        }

        // 护盾不可叠加：已有护盾时不消耗草药
        if (!world.isClientSide && SREArmorPlayerComponent.KEY.get(user).getArmor() > 0) {
            user.displayClientMessage(
                    Component.translatable("message.noellesroles.leon.shield_exists")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        user.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level world,
            @NotNull LivingEntity user) {
        if (user instanceof Player player && !world.isClientSide) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld != null && gameWorld.isRunning()
                    && gameWorld.isRole(player, ModRoles.LEON)
                    && GameUtils.isPlayerAliveAndSurvival(player)
                    && SREArmorPlayerComponent.KEY.get(player).getArmor() <= 0) {
                SREArmorPlayerComponent.KEY.get(player).giveArmor();
                world.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(),
                        SoundSource.PLAYERS, 0.9f, 1.0f);
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.leon.shield_gained")
                                .withStyle(ChatFormatting.RED),
                        true);
                stack.shrink(1);
            }
        }
        return stack;
    }
}
