package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class THIbarakiKasenRole extends TouhouRole {
    public static Map<UUID, Long> tickTime = new ConcurrentHashMap<>();
    public static final long WOLF_DISAPEAR_TICKS = 30 * 20;
    public static final long WOLF_CAPTURE_REWARD_TICKS = 10 * 20;
    public static final int WOLF_DETECTION_RANGE = 10;

    public void serverTick(ServerPlayer player) {
        long now = GameUtils.getTicksFromGameStart(player.level());
        var it = tickTime.entrySet().iterator();
        while (it.hasNext()) {
            var t = it.next();
            UUID uid = t.getKey();
            Entity wolf = player.serverLevel().getEntity(uid);
            if (wolf == null) {
                it.remove();
                continue;
            }
            if (now > t.getValue()) {
                if (wolf != null) {
                    if (wolf instanceof Wolf wf) {
                        wf.discard();
                    }
                }
                it.remove();
            } else {
                if (wolf instanceof Wolf wf) {
                    var target = wf.getTarget();
                    if (target != null && target instanceof Player p) {
                        if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                            sitWolf(wf);
                        }
                    } else {
                        if (!wf.isOrderedToSit()) {
                            sitWolf(wf);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void resetVariables() {
        tickTime.clear();
    }

    public THIbarakiKasenRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        final ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.add(new ShopEntry(Items.WOLF_SPAWN_EGG.getDefaultInstance(), 100, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                return buyWolf(player);
            }
        });
        return SHOP;
    }

    public static boolean buyWolf(Player p) {
        if (!(p instanceof ServerPlayer player)) {
            return false;
        }
        final var world = player.serverLevel();
        AtomicInteger count = new AtomicInteger(0);
        world.getAllEntities().forEach((entity) -> {
            if (isWolf(entity)) {
                count.addAndGet(1);
            }
        });
        if (count.get() >= 3) {
            return false;
        }
        spawnWolf(player, player.position());
        p.getCooldowns().addCooldown(Items.WOLF_SPAWN_EGG, 20 * 10);
        return true;
    }

    public static void registerEvents() {
        OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> {
            if (victim == null || killer == null) {
                return;
            }
            if (victim.getUUID().equals(killer.getUUID())) {
                return;
            }
            if (!(victim instanceof ServerPlayer player)) {
                return;
            }
            if (GameUtils.isPlayerAliveAndSurvival(killer)) {
                var it = tickTime.entrySet().iterator();
                while (it.hasNext()) {
                    var t = it.next();
                    UUID uid = t.getKey();
                    Entity wolf = player.serverLevel().getEntity(uid);
                    if (wolf != null) {
                        if (wolf.distanceToSqr(killer) <= WOLF_DETECTION_RANGE * WOLF_DETECTION_RANGE)
                            if (wolf instanceof Wolf wf) {
                                var target = wf.getTarget();
                                if (target == null) {
                                    setWolfTarget(wf, killer);
                                    // 修改map
                                    tickTime.put(uid, t.getValue() + WOLF_CAPTURE_REWARD_TICKS);
                                    break;
                                }
                            }
                    }
                }
            }
        });
    }

    public static void setWolfTarget(Wolf wolf, LivingEntity target) {
        wolf.setOrderedToSit(false);
        wolf.setJumping(false);
        wolf.setGlowingTag(true);
        wolf.setTarget(target);
    }

    public static void sitWolf(Wolf wolf) {
        wolf.setOrderedToSit(true);
        wolf.setGlowingTag(false);

        wolf.setJumping(false);
        wolf.getNavigation().stop();
        wolf.setTarget(null);
    }

    public static Wolf spawnWolf(ServerPlayer owner, Vec3 pos) {
        Wolf wolf = EntityType.WOLF.create(owner.serverLevel());
        wolf.setPos(pos);
        wolf.addTag("sre.kasen");
        owner.level().addFreshEntity(wolf);
        wolf.tame(owner);
        sitWolf(wolf);
        tickTime.put(wolf.getUUID(), GameUtils.getTicksFromGameStart(owner.level()) + WOLF_DISAPEAR_TICKS);
        return wolf;
    }

    public static boolean isWolf(Entity entity) {
        if (entity instanceof Wolf wf) {
            if (wf.getTags() != null && wf.getTags().contains("sre.kasen")) {
                return true;
            }
        }
        return false;
    }

}
