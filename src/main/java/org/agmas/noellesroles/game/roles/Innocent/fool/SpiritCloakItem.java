package org.agmas.noellesroles.game.roles.Innocent.fool;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 灵性斗篷
 *
 * 右键使用后玩家获得5秒无敌、无法攻击、移动速度不变（类似"灵体"状态）。
 * 冷却90秒。
 *
 * 价格：200金币
 */
public class SpiritCloakItem extends Item {

    /** 无敌持续时间（tick） */
    public static final int INVULNERABLE_DURATION = 5 * 20; // 5秒
    /** 冷却时间（tick） */
    public static final int COOLDOWN_DURATION = 90 * 20; // 90秒

    public SpiritCloakItem(Properties settings) {
        super(settings);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user,
            @NotNull InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (!world.isClientSide) {
            if (!(user instanceof ServerPlayer serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }

            SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(world);
            if (!gameComponent.isRole(serverPlayer, ModRoles.THE_FOOL)) {
                return InteractionResultHolder.fail(stack);
            }

            FoolPlayerComponent comp = FoolPlayerComponent.KEY.get(serverPlayer);
            long currentTick = world.getGameTime();

            // 检查冷却
            if (currentTick < comp.cloakCooldownEndTick) {
                long remaining = (comp.cloakCooldownEndTick - currentTick) / 20;
                serverPlayer.displayClientMessage(
                        Component.translatable("message.noellesroles.fool.cloak_cooldown", remaining)
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResultHolder.fail(stack);
            }

            // 激活灵体效果
            comp.cloakActive = true;
            comp.cloakEndTick = currentTick + INVULNERABLE_DURATION;
            comp.cloakCooldownEndTick = currentTick + COOLDOWN_DURATION;

            // 施加抗性提升255效果（实际无敌） + 虚弱255（无法攻击） + 发光效果
            serverPlayer.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE,
                    INVULNERABLE_DURATION, 254, false, false, false));
            serverPlayer.addEffect(new MobEffectInstance(ModEffects.SAFE_TIME,
                    INVULNERABLE_DURATION, 254, false, false, false));
            serverPlayer.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                    INVULNERABLE_DURATION, 254, false, false, false));


            comp.sync();

            // 消耗物品
            stack.shrink(1);

            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.fool.cloak_activated")
                            .withStyle(ChatFormatting.AQUA),
                    true);

            return InteractionResultHolder.consume(stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
    }
}
