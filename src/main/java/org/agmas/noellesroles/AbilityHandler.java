package org.agmas.noellesroles;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.component.*;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.entity.WheelchairEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.AbilityC2SPacket;
import org.agmas.noellesroles.packet.AbilityWithTargetC2SPacket;
import org.agmas.noellesroles.packet.ProblemScreenOpenC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.commander.CommanderHandler;
import org.agmas.noellesroles.roles.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.roles.noise_maker.NoiseMakerPlayerComponent;
import org.agmas.noellesroles.roles.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.roles.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;

public class AbilityHandler {

    public static void handler(AbilityC2SPacket payload, Context context) {
        // 通用技能服务端处理

        SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                .get(context.player());
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(context.player().level());
        final ServerPlayer player = context.player();
        if (player.hasEffect(ModEffects.TIME_STOP) && !TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
            return;
        }
        if (player.hasEffect(ModEffects.SKILL_BANED)) {
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.HOAN_MEIRIN)) {
            var hmpc = HoanMeirinPlayerComponent.KEY.get(player);
            if (player.hasEffect(MobEffects.LEVITATION)) {
                player.removeEffect(MobEffects.LEVITATION);
                player.displayClientMessage(
                        Component.translatable("hud.hoan_meirin.ability_stop").withStyle(ChatFormatting.AQUA),
                        true);
            } else if (hmpc.cooldown > 0) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.ability_cooldown").withStyle(ChatFormatting.RED),
                        true);
                return;
            } else {
                hmpc.setCooldown(60 * 20);
                player.displayClientMessage(
                        Component.translatable("hud.hoan_meirin.ability_activated").withStyle(ChatFormatting.GREEN),
                        true);
                context.player().addEffect(new MobEffectInstance(MobEffects.LEVITATION,
                        10 * 20, 1, true, false,
                        true));
            }
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.MAID_SAKUYA)) {
            if (abilityPlayerComponent.cooldown > 0 || player.getCooldowns().isOnCooldown(Items.CLOCK)) {
                context.player().displayClientMessage(Component.translatable(
                        "tip.noellesroles.cooldown", abilityPlayerComponent.cooldown / 20)
                        .withStyle(ChatFormatting.RED), true);
            } else {
                TimeStopEffect.tryTriggerStart(player, 20 * 5, Component.translatable("title.maid_sakuya.timestopper")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                abilityPlayerComponent.setCooldown(20 * 240);
            }
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.JOJO)) {
            if (abilityPlayerComponent.cooldown > 0 || player.getCooldowns().isOnCooldown(Items.CLOCK)) {
                context.player().displayClientMessage(Component.translatable(
                        "tip.noellesroles.cooldown", abilityPlayerComponent.cooldown / 20)
                        .withStyle(ChatFormatting.RED), true);
            } else {
                TimeStopEffect.tryTriggerStart(player, 20 * 3, Component.translatable("hud.noellesroles.jojo.the_world")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                abilityPlayerComponent.setCooldown(20 * 240);
            }
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.DIO)) {

            DIOPlayerComponent.KEY.get(player).tryActivateTimeStop();
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.WIND_YAOSE)) {
            if (abilityPlayerComponent.cooldown > 0) {
                context.player().displayClientMessage(Component.translatable(
                        "tip.noellesroles.cooldown", abilityPlayerComponent.cooldown / 20)
                        .withStyle(ChatFormatting.RED), true);
            } else {
                for (var p : player.level().players()) {
                    if (p.distanceTo(player) <= 30.) {
                        // 30s
                        PlayerVolumeComponent.KEY.get(p).setVolume(600, 0.05f);
                    }
                }
                abilityPlayerComponent.setCooldown(20 * 120);
            }
            return;
        }

        if (gameWorldComponent.isRole(context.player(), ModRoles.CLEANER)) {
            if (abilityPlayerComponent.cooldown > 0) {
                context.player().displayClientMessage(Component.translatable(
                        "message.noellesroles.cleaner.cooldown", abilityPlayerComponent.cooldown / 20)
                        .withStyle(ChatFormatting.RED), true);
            } else {
                var items = player.level().getEntitiesOfClass(ItemEntity.class,
                        player.getBoundingBox().inflate(5.), (p) -> true);
                for (var it : items) {
                    it.discard();
                }
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F,
                        1.0F + player.level().random.nextFloat() * 0.1F - 0.05F);
                context.player().displayClientMessage(Component.translatable(
                        "message.noellesroles.cleaner.cleanned", items.size())
                        .withStyle(ChatFormatting.GOLD), true);
                abilityPlayerComponent.setCooldown(20 * 90);
            }
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.GLITCH_ROBOT)) {
            if (!RoleUtils.isPlayerHasFreeSlot(context.player())) {
                context.player().displayClientMessage(
                        Component.translatable("message.hotbar.full").withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!context.player().getSlot(103).get().is(ModItems.NIGHT_VISION_GLASSES)) {
                context.player().displayClientMessage(
                        Component.translatable("info.glitch_robot.noglasses_on_head").withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            RoleUtils.insertStackInFreeSlot(context.player(), context.player().getSlot(103).get().copy());
            // RoleUtils.removeStackItem(context.player(), 103);
            context.player().getInventory().armor.set(3, ItemStack.EMPTY);
            context.player().displayClientMessage(
                    Component.translatable("info.glitch_robot.take_off_glasses.success")
                            .withStyle(ChatFormatting.GREEN),
                    true);
            context.player().removeEffect(MobEffects.NIGHT_VISION);
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.DIVER)) {
            if (!RoleUtils.isPlayerHasFreeSlot(context.player())) {
                context.player().displayClientMessage(
                        Component.translatable("message.hotbar.full").withStyle(ChatFormatting.RED), true);
                return;
            }

            boolean removedAny = false;

            // 检查并移除头盔
            ItemStack headItem = context.player().getSlot(103).get();
            if (!headItem.isEmpty()) {
                RoleUtils.insertStackInFreeSlot(context.player(), headItem.copy());
                context.player().getInventory().armor.set(3, ItemStack.EMPTY);
                removedAny = true;
            }

            // 检查并移除靴子
            ItemStack feetItem = context.player().getSlot(100).get();
            if (!feetItem.isEmpty()) {
                RoleUtils.insertStackInFreeSlot(context.player(), feetItem.copy());
                context.player().getInventory().armor.set(0, ItemStack.EMPTY);
                removedAny = true;
            }

            if (removedAny) {
                context.player().displayClientMessage(
                        Component.translatable("info.diver.remove_equipment.success")
                                .withStyle(ChatFormatting.GREEN),
                        true);
                context.player().removeEffect(MobEffects.WATER_BREATHING);
                context.player().removeEffect(MobEffects.DOLPHINS_GRACE);
            } else {
                context.player().displayClientMessage(
                        Component.translatable("info.diver.no_equipment")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.MA_CHEN_XU)) {
            MaChenXuPlayerComponent.KEY.get(context.player()).tryActiveAbility();
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.COMMANDER)) {
            CommanderHandler.tryActiveAbility(context.player());
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.ATTENDANT)) {
            if (abilityPlayerComponent.cooldown > 0) {
                context.player().displayClientMessage(Component.translatable(
                        "message.noellesroles.attendant.cooldown", abilityPlayerComponent.cooldown / 20)
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!context.player().isCreative())
                abilityPlayerComponent.setCooldown(60 * 20);
            AttendantHandler.openLight(context.player());
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.EXAMPLER)) {
            if (abilityPlayerComponent.cooldown > 0) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.ability_cooldown").withStyle(ChatFormatting.RED),
                        true);
                return;
            } else {
                SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(context.player());
                if (playerShopComponent.balance < 300) {
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.insufficient_funds_money", 300)
                                    .withStyle(ChatFormatting.RED),
                            true);
                    return;
                }
                playerShopComponent.addToBalance(-300);
                abilityPlayerComponent.setCooldown(240 * 20);
                player.serverLevel().players().forEach(sp -> {
                    if (GameUtils.isPlayerAliveAndSurvival(sp)) {
                        ServerPlayNetworking.send(sp, new ProblemScreenOpenC2SPacket(true, 3));
                    }
                });
            }
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.BOMBER)) {
            BomberPlayerComponent bomberPlayerComponent = ModComponents.BOMBER.get(context.player());
            bomberPlayerComponent.buyBomb();
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.NOISEMAKER)) {
            NoiseMakerPlayerComponent noiseMakerPlayerComponent = ModComponents.NOISEMAKER.get(context.player());
            noiseMakerPlayerComponent.useAbility();
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.GHOST)) {
            org.agmas.noellesroles.roles.ghost.GhostPlayerComponent ghostPlayerComponent = org.agmas.noellesroles.roles.ghost.GhostPlayerComponent.KEY
                    .get(context.player());
            ghostPlayerComponent.useAbility();
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.BLOOD_FEUDIST)) {
            BloodFeudistPlayerComponent bfComponent = ModComponents.BLOOD_FEUDIST.get(context.player());
            bfComponent.toggleEffects();
            return;
        }

        if (gameWorldComponent.isRole(context.player(), ModRoles.RECALLER)
                && abilityPlayerComponent.cooldown <= 0) {
            RecallerPlayerComponent recallerPlayerComponent = RecallerPlayerComponent.KEY.get(context.player());
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(context.player());
            if (!recallerPlayerComponent.placed) {
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().recallerMarkCooldown);
                recallerPlayerComponent.setPosition();
            } else if (playerShopComponent.balance >= 100) {
                playerShopComponent.balance -= 100;
                playerShopComponent.sync();
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().recallerTeleportCooldown);
                recallerPlayerComponent.teleport();
            }

        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.OLDMAN)) {
            if (player.getVehicle() != null && player.getVehicle() instanceof WheelchairEntity we) {
                if (player.getCooldowns().isOnCooldown(ModItems.WHEELCHAIR)) {
                    return;
                }
                var chairDurability = we.durability;
                we.discard();
                var it = ModItems.WHEELCHAIR.getDefaultInstance();
                it.setDamageValue(it.getMaxDamage() - chairDurability);
                RoleUtils.insertStackInFreeSlot(player, it);
                player.stopRiding();
                player.getCooldowns().addCooldown(ModItems.WHEELCHAIR, 40);
                player.displayClientMessage(
                        Component.translatable("message.oldman.get_back").withStyle(ChatFormatting.GOLD), true);
            }
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.PHANTOM)) {
            if (abilityPlayerComponent.cooldown <= 0) {
                context.player().addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                        NoellesRolesConfig.HANDLER.instance().phantomInvisibilityDuration * 20, 0, true, false,
                        true));
                abilityPlayerComponent.cooldown = GameConstants.getInTicks(0,
                        NoellesRolesConfig.HANDLER.instance().phantomInvisibilityCooldown);
            } else {
                var effectINVISIBILITY = context.player().getEffect(MobEffects.INVISIBILITY);
                if (effectINVISIBILITY != null) {
                    if (effectINVISIBILITY.getDuration() > 0) {
                        context.player().removeEffect(MobEffects.INVISIBILITY);
                        context.player().displayClientMessage(
                                Component.translatable("tip.phantom.exited").withStyle(ChatFormatting.YELLOW),
                                true);
                    }
                }
            }

        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.NIAN_SHOU)) {
            var sender = context.player();

            NianShouPlayerComponent nianShouComponent = NianShouPlayerComponent.KEY.get(sender);

            // 简单实现：检查准星对准的玩家
            Player target = null;
            // 由于raycastPlayer方法不存在，使用简化逻辑
            // 获取准星对准的玩家
            double minDistance = 5.0;
            for (Player otherPlayer : sender.level().players()) {
                if (otherPlayer.isSpectator())
                    continue;
                if (otherPlayer.getUUID().equals(sender.getUUID())) {
                    continue; // 不能给自己发红包
                }
                double distance = sender.distanceTo(otherPlayer);
                if (distance <= minDistance) {
                    // 检查是否在准星方向
                    net.minecraft.world.phys.Vec3 eyePos = sender.getEyePosition();
                    net.minecraft.world.phys.Vec3 lookVec = sender.getLookAngle().normalize();
                    net.minecraft.world.phys.Vec3 toTarget = otherPlayer.position().subtract(eyePos).normalize();
                    double dotProduct = lookVec.dot(toTarget);
                    if (dotProduct > 0.8) { // 准星方向大致对准目标
                        if (target == null || distance < sender.distanceTo(target)) {
                            target = otherPlayer;
                        }
                    }
                }
            }

            if (target == null) {
                sender.displayClientMessage(
                        Component.translatable("message.noellesroles.nianshou.no_target")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            if (nianShouComponent.getRedPacketCount() <= 0) {
                sender.displayClientMessage(
                        Component.translatable("message.noellesroles.nianshou.no_red_packet")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            // 发放红包
            nianShouComponent.useRedPacket();

            // 添加延迟发放计时器
            if (target instanceof ServerPlayer) {
                ConfigWorldComponent configWorld = ConfigWorldComponent.KEY.get(target.level());
                configWorld.addRedPacketTimer(target.getUUID());

                // 提示年兽
                sender.displayClientMessage(
                        Component.translatable("message.noellesroles.nianshou.red_packet_sent", target.getName())
                                .withStyle(ChatFormatting.GOLD),
                        true);
            }
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.THIEF)) {
            ThiefPlayerComponent thiefComponent = ThiefPlayerComponent.KEY.get(context.player());

            // 检查玩家是否在蹲下
            if (context.player().isShiftKeyDown()) {
                // 蹲下按技能键：切换模式
                thiefComponent.toggleMode();
            } else {
                // 普通按技能键：使用技能
                thiefComponent.useAbility();
            }
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.CLOCKMAKER)) {
            ClockmakerPlayerComponent clockmakerComponent = ModComponents.CLOCKMAKER.get(context.player());
            clockmakerComponent.useSkill();
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.ACCOUNTANT)) {
            org.agmas.noellesroles.component.AccountantPlayerComponent accountantComponent = org.agmas.noellesroles.component.AccountantPlayerComponent.KEY
                    .get(context.player());

            // 检查玩家是否在蹲下
            if (context.player().isShiftKeyDown()) {
                // 蹲下按技能键：切换模式
                accountantComponent.toggleMode();
            } else {
                // 普通按技能键：使用技能
                accountantComponent.useAbility();
            }
            return;
        }
        if (gameWorldComponent.isRole(context.player(), ModRoles.ALCHEMIST)) {
            org.agmas.noellesroles.component.AlchemistPlayerComponent alchemistComponent = org.agmas.noellesroles.component.AlchemistPlayerComponent.KEY
                    .get(context.player());

            // 检查玩家是否在蹲下
            if (context.player().isShiftKeyDown()) {
                // 蹲下按技能键：切换药剂
                alchemistComponent.switchPotion();
            } else {
                // 普通按技能键：调制药剂
                alchemistComponent.craftPotion();
            }
            return;
        }

        if (gameWorldComponent.isRole(context.player(), ModRoles.SEA_KING)) {
            if (abilityPlayerComponent.cooldown > 0) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.sea_king.cooldown",
                                (abilityPlayerComponent.cooldown + 19) / 20)
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            final double radius = 20.0D;
            final int duration = 5 * 20;
            int affected = 0;

            AABB range = player.getBoundingBox().inflate(radius);
            for (ServerPlayer target : player.serverLevel().getEntitiesOfClass(
                    ServerPlayer.class,
                    range,
                    p -> !p.getUUID().equals(player.getUUID()) && GameUtils.isPlayerAliveAndSurvival(p))) {
                if (player.distanceToSqr(target) > radius * radius) {
                    continue;
                }
                if (!(target.isInWater() || target.isUnderWater())) {
                    continue;
                }

                target.addEffect(new MobEffectInstance(ModEffects.GHOST_CURSE, duration, 0, false, true, true));
                affected++;
            }

            abilityPlayerComponent.setCooldown(60 * 20);
                player.level().playSound(null, player.blockPosition(),
                    SoundEvents.TRIDENT_RETURN, SoundSource.MASTER, 5.0F, 1.0F);

            if (affected > 0) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.sea_king.skill_used", affected)
                                .withStyle(ChatFormatting.AQUA),
                        true);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.sea_king.skill_no_target")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return;
        }
    }

    public static void handlerWithTarget(AbilityWithTargetC2SPacket payload, Context context) {
        SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                .get(context.player());

        SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY
                .get(context.player());
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(context.player().level());
        final ServerPlayer player = context.player();
        if (player.hasEffect(ModEffects.TIME_STOP) && !TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
            return;
        }
        if (player.hasEffect(ModEffects.SKILL_BANED)) {
            return;
        }
        var targetPlayer = player.level().getPlayerByUUID(payload.target());

        if (gameWorldComponent.isRole(player, ModRoles.EXAMPLER)) {
            if (abilityPlayerComponent.cooldown > 0) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.ability_cooldown").withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            if (playerShopComponent.balance < 100) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.insufficient_funds").withStyle(ChatFormatting.RED),
                        true);
                return;
            }
            playerShopComponent.addToBalance(-100);
            if (targetPlayer != null && targetPlayer instanceof ServerPlayer sp) {
                abilityPlayerComponent.setCooldown(90 * 20);
                ServerPlayNetworking.send(player, new ProblemScreenOpenC2SPacket(true, 2));
                ServerPlayNetworking.send(sp, new ProblemScreenOpenC2SPacket(true, 2));
            }
            return;
        }
        if (gameWorldComponent.isRole(player, ModRoles.FORTUNETELLER)) {
            if (abilityPlayerComponent.cooldown > 0) {
                player.displayClientMessage(Component.translatable("message.noellesroles.ability_cooldown"), true);
                return;
            }
            if (targetPlayer != null) {
                if (playerShopComponent.balance >= 200) {
                    playerShopComponent.addToBalance(-200);
                    FortunetellerPlayerComponent.KEY.get(player).protectPlayer(targetPlayer);
                    abilityPlayerComponent.setCooldown(120 * 20);
                } else {
                    player.displayClientMessage(Component.translatable("message.noellesroles.insufficient_funds"),
                            true);
                    return;
                }
            }
            return;
        }
    }
}
