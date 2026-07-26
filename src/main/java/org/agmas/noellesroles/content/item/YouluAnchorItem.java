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
import org.agmas.noellesroles.content.entity.YouluAnchorEntity;
import org.agmas.noellesroles.game.roles.killer.youlu.YouluPlayerComponent;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 幽露技能物品「不请自来」。
 *
 * <p>第一次使用：在面前放置一个沿地面向前滑行的球形锚点（仅幽露本人可见）。
 * 第二次使用：传送到锚点位置并回收锚点，随后进入 30s（可配置）物品冷却。
 * 商店 80 金币购买一次，可反复使用。
 */
public class YouluAnchorItem extends Item {

    public YouluAnchorItem(Properties properties) {
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
        YouluPlayerComponent component = YouluPlayerComponent.KEY.get(sp);
        YouluAnchorEntity anchor = component.getAnchor();

        if (anchor == null) {
            // 放置锚点：从脚下前方 1 格出发，沿当前朝向滑行
            YouluAnchorEntity fresh = new YouluAnchorEntity(ModEntities.YOULU_ANCHOR, serverLevel);
            float yaw = sp.getYRot();
            double rad = Math.toRadians(yaw);
            Vec3 start = sp.position().add(-Math.sin(rad), 0.1, Math.cos(rad));
            fresh.setPos(start.x, start.y, start.z);
            fresh.setup(sp.getUUID(), yaw, config.youluAnchorSpeed,
                    GameConstants.getInTicks(0, config.youluAnchorLifetimeSeconds));
            serverLevel.addFreshEntity(fresh);
            component.anchorUuid = fresh.getUUID();
            serverLevel.playSound(null, sp.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 0.6f);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.youlu.anchor_placed")
                            .withStyle(ChatFormatting.AQUA), true);
        } else {
            // 传送到锚点并回收，进入冷却
            Vec3 target = anchor.position();
            sp.teleportTo(target.x, target.y, target.z);
            component.discardAnchor();
            sp.getCooldowns().addCooldown(this,
                    GameConstants.getInTicks(0, config.youluAnchorCooldownSeconds));
            serverLevel.playSound(null, target.x, target.y, target.z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.youlu.anchor_teleported")
                            .withStyle(ChatFormatting.AQUA), true);
            itemStack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(itemStack, false);
    }

    /**
     * 技能物品防消耗保护：幽露的技能物品是一次购买、反复使用的，任何情况下都不应在使用后消失。
     * 使用后延迟 2 tick 校验，若物品已不在背包中则补发一个。
     */
    public static void guardSkillItem(ServerPlayer sp, Item item) {
        io.wifi.starrailexpress.util.Scheduler.schedule(() -> {
            if (!sp.isAlive() || sp.isSpectator() || sp.hasDisconnected()) return;
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.YOULU)) return;
            if (io.wifi.starrailexpress.util.SREItemUtils.hasItem(sp, item)) return;
            org.agmas.noellesroles.utils.RoleUtils.insertStackInFreeSlot(sp, item.getDefaultInstance());
        }, 2);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.youlu_anchor.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
