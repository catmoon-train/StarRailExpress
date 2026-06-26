package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 蓝色草药（里昂专属）。
 *
 * <p>右键立即使用：刷新里昂的格斗体术冷却。一局发放一次，里昂死亡后不随手枪一起掉落
 * （自定义物品不在 {@code GameUtils.shouldDropOnDeath} 名单内，因此死亡时不会掉落）。
 */
public class LeonBlueHerbItem extends Item {

    public LeonBlueHerbItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (gameWorld == null || !gameWorld.isRunning()
                || !gameWorld.isRole(user, ModRoles.LEON)
                || !GameUtils.isPlayerAliveAndSurvival(user)) {
            return InteractionResultHolder.pass(stack);
        }

        // 刷新格斗体术冷却（通用技能冷却归零）
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(user);
        ability.cooldown = 0;
        SREAbilityPlayerComponent.KEY.sync(user);

        stack.shrink(1);
        world.playSound(null, user.blockPosition(), SoundEvents.GENERIC_DRINK,
                SoundSource.PLAYERS, 0.8f, 1.4f);
        user.displayClientMessage(
                Component.translatable("message.noellesroles.leon.blue_herb_used")
                        .withStyle(ChatFormatting.AQUA),
                true);
        return InteractionResultHolder.consume(stack);
    }
}
