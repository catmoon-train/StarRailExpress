package io.wifi.events.day_night_fight;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DNFTentacleItem extends Item {
    public DNFTentacleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (!(user instanceof ServerPlayer player) || !DNF.isDNFKiller(player)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().expandTowards(look.scale(6)).inflate(1.2);
        Entity nearest = null;
        double dist = 999;
        for (Entity entity : world.getEntities(player, box, e -> e instanceof Player && e.isAlive())) {
            Vec3 delta = entity.position().subtract(player.position());
            double dot = delta.normalize().dot(look);
            if (dot < 0.55) continue;
            double d = delta.lengthSqr();
            if (d < dist) { dist = d; nearest = entity; }
        }
        if (nearest != null) {
            Vec3 pullTo = player.position().add(look.scale(1.2));
            nearest.teleportTo(pullTo.x, pullTo.y, pullTo.z);
            nearest.setDeltaMovement(player.position().subtract(nearest.position()).normalize().scale(0.6));
            player.getCooldowns().addCooldown(this, 200);
            return InteractionResultHolder.success(stack);
        }
        player.displayClientMessage(Component.literal("前方没有可抓取目标").withStyle(ChatFormatting.GRAY), true);
        return InteractionResultHolder.fail(stack);
    }
}
