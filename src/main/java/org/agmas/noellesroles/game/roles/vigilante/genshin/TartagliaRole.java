package org.agmas.noellesroles.game.roles.vigilante.genshin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.DiscountShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TartagliaRole extends NormalRole {
    public static final int SKILL_KILL_THRESHOLD = 2;
    public static Map<UUID, Integer> tickCounts = new ConcurrentHashMap<>();

    public TartagliaRole(ResourceLocation identifier, int color, RoleType roleType, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, roleType, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        var bow = Items.BOW.getDefaultInstance();
        bow.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        return List.of(bow);
    }

    @Nullable
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();

        // 类似游侠商店
        {
            final var PoisonArrow = Items.TIPPED_ARROW.getDefaultInstance();
            PoisonArrow.set(DataComponents.ITEM_NAME, Component.translatable("item.poison_arrow.name"));
            PoisonArrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.POISON));
            PoisonArrow.set(DataComponents.MAX_STACK_SIZE, 1);
            SHOP.add(new DiscountShopEntry(PoisonArrow, 120, 50) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    int itemCount = SREItemUtils.countItem(player, Items.TIPPED_ARROW);
                    if (itemCount >= 2)
                        return false;
                    return RoleUtils.insertStackInFreeSlot(player, PoisonArrow.copy());
                }
            });

            final var SpectralArrow = Items.SPECTRAL_ARROW.getDefaultInstance();
            SpectralArrow.set(DataComponents.MAX_STACK_SIZE, 1);

            SHOP.add(new ShopEntry(SpectralArrow, 50, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    int itemCount = SREItemUtils.countItem(player, Items.SPECTRAL_ARROW);
                    if (itemCount >= 2)
                        return false;
                    return RoleUtils.insertStackInFreeSlot(player, SpectralArrow.copy());
                }
            });
        }

        SHOP.add(new ShopEntry(Items.CROSSBOW.getDefaultInstance(), 300, ShopEntry.Type.WEAPON) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                int itemCount = SREItemUtils.countItem(player, Items.CROSSBOW);
                if (itemCount > 1)
                    return false;
                ItemStack item = Items.CROSSBOW.getDefaultInstance();
                return RoleUtils.insertStackInFreeSlot(player, item);
            }
        });
        SHOP.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(),
                SREConfig.instance().firecrackerPrice, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(),
                SREConfig.instance().bodyBagPrice, ShopEntry.Type.TOOL));
        SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(),
                SREConfig.instance().blackoutPrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });
        SHOP.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), SREConfig.instance().notePrice,
                ShopEntry.Type.TOOL));

        return SHOP;
    }

    @Override
    public void resetVariables() {
        tickCounts.clear();
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }

        // 只在冲刺旋转期间检测碰撞
        if (!player.isAutoSpinAttack()) {
            return;
        }
        final var level = player.serverLevel();
        Vec3 playerPos = player.position();
        Vec3 movement = player.getDeltaMovement();

        // 以当前移动方向为碰撞检测朝向
        Vec3 dashDir = movement.multiply(1, 0, 1).normalize();
        Vec3 frontCenter = playerPos.add(dashDir.scale(0.8));
        AABB collisionBox = new AABB(frontCenter, frontCenter).inflate(0.6, 0.9, 0.6);

        for (var e : level.getEntities(player, collisionBox)) {
            if (!(e instanceof Player targetPlayer))
                continue;
            if (targetPlayer.isSpectator())
                continue;

            // 撞到目标：
            if (GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                GameUtils.killPlayer(targetPlayer, true, player, GameConstants.DeathReasons.TARTAGLIA);
            }

            tickCounts.merge(player.getUUID(), 1, Integer::sum);
            int count = tickCounts.get(player.getUUID());
            if (count >= SKILL_KILL_THRESHOLD) {
                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);

                // 计算击退向量（从玩家指向目标）
                Vec3 knockbackDir = targetPlayer.position().subtract(playerPos).multiply(1, 0, 1).normalize();
                // 施加击退效果，将目标推开
                targetPlayer.push(knockbackDir.x * 2.5, 0.5, knockbackDir.z * 2.5);
                targetPlayer.hurtMarked = true;
                player.hurtMarked = true;
                tickCounts.remove(player.getUUID());
                return;
            }

            break;
        }
    }

    public static boolean onSkillUsed(ServerPlayer player, RoleSkillContext context) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(Items.BOW) && !stack.is(Items.CROSSBOW)) {
            player.displayClientMessage(
                    Component.translatable("skill.noellesroles.tartaglia.failed.nobow").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        final ServerLevel level = player.serverLevel();
        float f = 1f;
        float g = player.getYRot();
        tickCounts.remove(player.getUUID());
        // 水平朝向向量
        float k = -Mth.sin(g * ((float) Math.PI / 180F));
        float m = Mth.cos(g * ((float) Math.PI / 180F));
        float horizLen = Mth.sqrt(k * k + m * m);
        float kNorm = k / horizLen;
        float mNorm = m / horizLen;
        // ── 启动平飞冲刺与炫酷粒子特效 ──
        player.push(kNorm * f, 0.0, mNorm * f);
        player.addEffect(ModEffects.of(ModEffects.NO_COLLIDE, 20 * 5, 1, false, false, false));
        player.startAutoSpinAttack(20, 8.0F, player.getMainHandItem());
        player.hurtMarked = true;
        player.connection.send(
                new ClientboundSetEntityMotionPacket(player.getId(), player.getDeltaMovement()));
        if (player.onGround()) {
            player.move(MoverType.SELF, new Vec3(0.0, 1.2, 0.0));
        }

        // 生成更炫酷的冲刺粒子效果
        if (level instanceof ServerLevel serverLevel) {
            // 1. 核心爆发粒子 (END_ROD 模拟能量束)
            for (int i = 0; i < 20; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2;
                double radius = 0.5 + level.random.nextDouble() * 0.5;
                double px = player.getX() + Math.cos(angle) * radius;
                double pz = player.getZ() + Math.sin(angle) * radius;
                double py = player.getY() + 1.0 + level.random.nextDouble() * 0.5;

                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        px, py, pz,
                        1,
                        (level.random.nextDouble() - 0.5) * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.2,
                        (level.random.nextDouble() - 0.5) * 0.2,
                        0.05);
            }

            // 2. 环形冲击波 (PORTAL 粒子模拟波纹扩散)
            for (int ring = 0; ring < 3; ring++) {
                int count = 15 + ring * 5;
                for (int i = 0; i < count; i++) {
                    double angle = (i / (double) count) * Math.PI * 2;
                    double r = 1.0 + ring * 0.8;
                    double px = player.getX() + Math.cos(angle) * r;
                    double pz = player.getZ() + Math.sin(angle) * r;
                    double py = player.getY() + 1.0;

                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                            px, py, pz,
                            1,
                            0, 0, 0,
                            0.1);
                }
            }

            // 3. 拖尾火花 (FLAME 或 CRIT)
            for (int i = 0; i < 10; i++) {
                double offsetX = (level.random.nextDouble() - 0.5) * 0.5;
                double offsetZ = (level.random.nextDouble() - 0.5) * 0.5;
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        player.getX() + offsetX, player.getY() + 1.5, player.getZ() + offsetZ,
                        1, 0, 0, 0, 0.2);
            }
        }

        level.playSound(null, player, SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 1.0F,
                0.8F + level.random.nextFloat() * 0.4F);
        return true;
    }

}
