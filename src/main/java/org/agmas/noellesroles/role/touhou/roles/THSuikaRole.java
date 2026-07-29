package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.HandCuffsItem;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class THSuikaRole extends TouhouRole {
    public static final ResourceLocation SUIKA_BIG_SCALE_ID = Noellesroles.id("suika_big");
    public static final ResourceLocation SUIKA_SMALL_SCALE_ID = Noellesroles.id("suika_small");
    public static final AttributeModifier bigScale = new AttributeModifier(
            SUIKA_BIG_SCALE_ID, 1f,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    public static final AttributeModifier smallScale = new AttributeModifier(
            SUIKA_SMALL_SCALE_ID, -0.33f,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    public THSuikaRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Nullable
    public List<ShopEntry> getShopEntries() {
        var shop = new ArrayList<ShopEntry>();
        shop.add(new ShopEntry(FunnyItems.SUIKA_GOURD.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(FunnyItems.SUIKA_PILL.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(Items.MELON_SLICE.getDefaultInstance(), 25, ShopEntry.Type.TOOL));
        {
            var it = Items.COMMAND_BLOCK.getDefaultInstance();
            it.set(DataComponents.ITEM_NAME, Component.translatable("itemstack.suika.cooldown.push"));
            shop.add(new ShopEntry(it, 0, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    return false;
                }
            });
        }

        {
            var it = Items.REPEATING_COMMAND_BLOCK.getDefaultInstance();
            it.set(DataComponents.ITEM_NAME, Component.translatable("itemstack.suika.cooldown.kill"));
            shop.add(new ShopEntry(it, 0, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(Player player) {
                    return false;
                }
            });
        }
        shop.addAll(ShopContent.getDefaultKnifeEntries());
        return shop;
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        final ServerLevel level = player.serverLevel();
        if (isBigScale(player)) {
            if (HandCuffsItem.hasHandCuff(player)) {
                HandCuffsItem.breakHandCuff(player);
            }
            {
                if (player.isSprinting()) {
                    if ((!player.getCooldowns().isOnCooldown(Items.REPEATING_COMMAND_BLOCK)
                            || !player.getCooldowns().isOnCooldown(Items.COMMAND_BLOCK))
                            && !player.getCooldowns().isOnCooldown(Items.CHAIN_COMMAND_BLOCK)) {
                        Vec3 playerPos = player.position();
                        // 获取当前玩家的碰撞箱
                        AABB playerBox = player.getBoundingBox();
                        int triggeredPush = 0, triggeredKill = 0;
                        // 遍历服务器中所有玩家（包括自己，但跳过）
                        for (ServerPlayer other : player.serverLevel().players()) {

                            if (other.getUUID() == player.getUUID())
                                continue;
                            if (!GameUtils.isPlayerAliveAndSurvival(other))
                                continue;
                            // 检测碰撞箱是否相交（三维空间）
                            if (playerBox.intersects(other.getBoundingBox())) {
                                // 疾跑冲撞人会击退玩家，CD 30s。此形态免疫手铐，其他处于控制状态的玩家若此时被撞到会直接死亡（包括杀手）CD 30s。
                                if (other.hasEffect(ModEffects.MOVE_BANED) && triggeredKill < 3 && triggeredPush <= 0) {
                                    if (!player.getCooldowns().isOnCooldown(Items.REPEATING_COMMAND_BLOCK)) {
                                        GameUtils.killPlayer(other, true, player,
                                                GameConstants.DeathReasons.SUIKA_RUSH);
                                        triggeredKill++;
                                    }
                                } else {
                                    if (!player.getCooldowns().isOnCooldown(Items.COMMAND_BLOCK)) {
                                        Vec3 knockback = other.position()
                                                .subtract(playerPos)
                                                .multiply(1, 0, 1)
                                                .normalize()
                                                .scale(0.5);
                                        other.push(knockback.x, 0, knockback.z);
                                        other.hurtMarked = true;
                                        // 被击退实体产生冲击波粒子
                                        if (level instanceof ServerLevel serverLevel) {
                                            serverLevel.sendParticles(
                                                    net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                                                    other.getX(), other.getY() + 1, other.getZ(),
                                                    1, 0, 0, 0, 0);
                                        }
                                        other.addEffect(ModEffects.of(ModEffects.MOVE_BANED, 20, 0, false, true, true));
                                        triggeredPush++;
                                        other.setLastHurtByPlayer(player);
                                    }
                                }
                            }
                        }
                        if (triggeredKill > 0) {
                            player.getCooldowns().addCooldown(Items.REPEATING_COMMAND_BLOCK, 20 * 30);
                        }
                        if (triggeredPush > 0) {
                            player.getCooldowns().addCooldown(Items.COMMAND_BLOCK, 20 * 5);
                        }
                        if (triggeredKill > 0 || triggeredPush > 0) {
                            player.getCooldowns().addCooldown(Items.CHAIN_COMMAND_BLOCK, 20);
                        }
                    }
                }
            }
            {

                boolean needAdd = false;
                if (!player.hasEffect(ModEffects.NO_COLLIDE)) {
                    needAdd = true;
                } else {
                    var eff = player.getEffect(ModEffects.NO_COLLIDE);
                    if (eff.getDuration() <= 2 * 20) {
                        needAdd = true;
                    }
                }
                if (needAdd) {
                    player.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, 10 * 20, 0, true, false, true));
                }
            }
            {
                boolean needAdd = false;
                if (!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    needAdd = true;
                } else {
                    var eff = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    if (eff.getDuration() <= 2 * 20) {
                        needAdd = true;
                    }
                }
                if (needAdd) {
                    player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, 10 * 20, 0, true, false, true));
                }
            }
        } else if (isSmallScale(player)) {
            if (!player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                restore(player);
            }
        }
    }

    public static void restore(Player player) {
        player.getAttribute(Attributes.SCALE).removeModifiers();
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(ModEffects.NO_COLLIDE);
    }

    public static boolean isBigScale(Player player) {
        return player.getAttribute(Attributes.SCALE).hasModifier(SUIKA_BIG_SCALE_ID);
    }

    public static boolean isSmallScale(Player player) {
        return player.getAttribute(Attributes.SCALE).hasModifier(SUIKA_SMALL_SCALE_ID);
    }

    public static void addSmallScale(Player player) {
        restore(player);
        player.getAttribute(Attributes.SCALE).addOrReplacePermanentModifier(smallScale);
        player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 10 * 20, 0, false, false, true));
    }

    public static void addBigScale(Player player) {
        restore(player);
        player.getAttribute(Attributes.SCALE).addOrReplacePermanentModifier(bigScale);
    }

    public static boolean handleSkillBig(RoleSkillContext context) {
        final var player = context.player();
        if (isBigScale(player)) {
            return false;
        }
        addBigScale(player);
        return true;
    }

    public static boolean handleSkillSmall(RoleSkillContext context) {
        final var player = context.player();
        if (isSmallScale(player)) {
            return false;
        }
        addSmallScale(player);
        return true;
    }

    @Override
    public void onDrink(Player player, ItemStack item) {
        if (item.getItem() instanceof CocktailItem) {
            player.addEffect(ModEffects.of(MobEffects.CONFUSION, 5 * 20, 1, false, false, true));
            if (isSmallScale(player)) {
                player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, 10 * 20, 1, false, false, true));
            }
        }
    }
}
