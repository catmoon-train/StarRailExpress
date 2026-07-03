package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.SilenceTotemEntity;
import org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class SilenceTotemItem extends Item {
    public SilenceTotemItem(Properties properties) {
        super(properties);
    }
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 7;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }
    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity user) {


        if (user instanceof ServerPlayer player) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
            if (!gameWorld.isRole(player, ModRoles.SPELLBREAKER)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.spellbreaker.item_only")
                                .withStyle(ChatFormatting.RED),
                        true);
                return itemStack;
            }

            SpellbreakerPlayerComponent component = SpellbreakerPlayerComponent.KEY.get(player);
            component.discardActiveTotem((ServerLevel) level);

            SilenceTotemEntity totem = new SilenceTotemEntity(ModEntities.SILENCE_TOTEM, level);
            totem.setOwner(player);
            totem.setPosRaw(player.getX(), player.getEyeY() - 0.1, player.getZ());
            totem.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.55F, 1.0F);
            level.addFreshEntity(totem);
            component.setActiveTotem(totem);
            itemStack.consume(1, user);
            return itemStack ;
        }

        level.playSound(null, user.getX(), user.getY(), user.getZ(),
                TMMSounds.ITEM_GRENADE_THROW, SoundSource.NEUTRAL,
                0.5F, 0.8F + (level.random.nextFloat() - .5f) / 10f);


        return super.finishUsingItem(itemStack, level, user);

    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, @NotNull InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);

        // 检查游戏是否正在进行
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
        if (!gameWorld.isRunning()) {
            return InteractionResultHolder.pass(itemStack);
        }

        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(user)) {
            return InteractionResultHolder.pass(itemStack);
        }

        // 开始使用（吃）
        user.startUsingItem(hand);

        return InteractionResultHolder.consume(itemStack);
    }
}
