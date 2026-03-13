package io.wifi.starrailexpress.item;

import java.util.ArrayList;

import io.wifi.starrailexpress.cca.BartenderPlayerComponent;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class DefenseItem extends Item {
    public static StarGameWorldComponent gameWorldComponent = null;
    public static ArrayList<String> canUseByRightClickRolePaths = new ArrayList<>();

    public DefenseItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack itemStack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
        return 20;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);
        if (gameWorldComponent == null) {
            gameWorldComponent = StarGameWorldComponent.KEY.get(level);
        }
        if (gameWorldComponent != null) {
            var role = gameWorldComponent.getRole(player);
            if (role != null) {
                if (canUseByRightClickRolePaths.contains(role.identifier().getPath())) {
                    player.startUsingItem(interactionHand);
                    return InteractionResultHolder.consume(itemStack);
                }
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(interactionHand));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
        if (gameWorldComponent == null) {
            gameWorldComponent = StarGameWorldComponent.KEY.get(level);
        }
        if (gameWorldComponent != null) {
            var role = gameWorldComponent.getRole(livingEntity.getUUID());
            if (role != null) {
                if (canUseByRightClickRolePaths.contains(role.identifier().getPath())) {
                    if (livingEntity instanceof Player player) {
                        var bartenderComponent = BartenderPlayerComponent.KEY.get(player);
                        if (bartenderComponent != null) {
                            bartenderComponent.giveArmor();
                            itemStack.consume(1, livingEntity);
                            return itemStack;
                        }
                    }
                }
            }
        }
        return itemStack;
    }
}
