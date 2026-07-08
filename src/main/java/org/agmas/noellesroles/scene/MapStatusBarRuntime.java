package org.agmas.noellesroles.scene;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.content.item.CocktailItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import io.wifi.starrailexpress.index.tag.TMMBlockTags;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block.scene.StoveBlock;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.packet.MapStatusBarSyncS2CPacket;
import pro.fazeclan.river.stupid_express.constants.SEItems;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MapStatusBarRuntime {
    public static final int MAX_VALUE = 20;
    private static final TagKey<Item> NOELLES_DRINKS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("noellesroles", "food_drink"));
    private static final Map<UUID, State> STATES = new HashMap<>();

    private MapStatusBarRuntime() {
    }

    public static void tick(ServerLevel level) {
        MapStatusBarType type = currentStatusBar(level);
        if (!isGameRunning(level) || type == MapStatusBarType.NONE) {
            clear(level);
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (!shouldTrack(player)) {
                remove(player);
                continue;
            }
            State state = STATES.computeIfAbsent(player.getUUID(), id -> new State(type));
            if (state.type != type) {
                state.reset(type);
            }
            // 污染值初始为0
            if (type == MapStatusBarType.POLLUTION && state.value == MAX_VALUE && state.lastSyncedValue == -1) {
                state.set(0);
            }
            tickPlayer(level, player, state);
            if (level.getGameTime() % 20 == 0) {
                state.sync(player);
            }
        }

        Iterator<UUID> iterator = STATES.keySet().iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            if (level.getServer().getPlayerList().getPlayer(id) == null) {
                iterator.remove();
            }
        }
    }

    public static void clear(ServerLevel level) {
        if (STATES.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (STATES.remove(player.getUUID()) != null) {
                ServerPlayNetworking.send(player,
                        new MapStatusBarSyncS2CPacket(MapStatusBarType.NONE, MAX_VALUE, MAX_VALUE));
            }
        }
    }

    public static void addWarmth(ServerPlayer player, int delta) {
        add(player, MapStatusBarType.WARMTH, delta);
    }

    public static void addThirst(ServerPlayer player, int delta) {
        add(player, MapStatusBarType.THIRST, delta);
    }

    public static void addHunger(ServerPlayer player, int delta) {
        add(player, MapStatusBarType.HUNGER, delta);
    }

    public static void addPollution(ServerPlayer player, int delta) {
        add(player, MapStatusBarType.POLLUTION, delta);
    }

    public static void onFinishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (level.isClientSide || !(user instanceof ServerPlayer player)) {
            return;
        }
        if (isDrink(stack)) {
            // 一瓶水恢复10点口渴值，其他饮料恢复4点
            int amount = stack.is(ModItems.A_BOTTLE_OF_WATER) ? 10 : 4;
            add(player, MapStatusBarType.THIRST, amount);
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            add(player, MapStatusBarType.HUNGER, Math.max(1, food.nutrition()));
        }
    }

    private static void add(ServerPlayer player, MapStatusBarType type, int delta) {
        ServerLevel level = (ServerLevel) player.level();
        if (!isGameRunning(level) || currentStatusBar(level) != type || !shouldTrack(player)) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), id -> new State(type));
        if (state.type != type) {
            state.reset(type);
        }
        state.change(delta);
        if (state.value <= 0 && type != MapStatusBarType.POLLUTION) {
            killByStatus(player, type);
            STATES.remove(player.getUUID());
            return;
        }
        state.sync(player);
    }

    private static void tickPlayer(ServerLevel level, ServerPlayer player, State state) {
        switch (state.type) {
            case WARMTH -> tickWarmth(level, player, state);
            case THIRST -> tickThirst(player, state);
            case HUNGER -> tickHunger(player, state);
            case POLLUTION -> tickPollution(level, player, state);
            default -> {
            }
        }
        if (state.value <= 0 && state.type != MapStatusBarType.POLLUTION) {
            killByStatus(player, state.type);
            STATES.remove(player.getUUID());
        }
    }

    private static void tickWarmth(ServerLevel level, ServerPlayer player, State state) {
        if (nearLitStove(level, player.blockPosition())) {
            state.set(MAX_VALUE);
            state.clearTimers();
            return;
        }

        int leatherPieces = leatherArmorPieces(player);
        boolean protectedFromCold = leatherPieces >= 3;
        int skyColdInterval = 20 * 20 * Math.max(1, leatherPieces + 1);
        int groundColdInterval = 15 * 20 * Math.max(1, leatherPieces + 1);
        boolean canSeeSky = level.canSeeSky(player.blockPosition());
        boolean inPowderSnow = level.getBlockState(player.blockPosition()).is(Blocks.POWDER_SNOW);

        // 看得见天空: 每20秒下降1点（皮甲会减速）
        if (canSeeSky && !protectedFromCold) {
            if (++state.skyTicks >= skyColdInterval) {
                state.skyTicks = 0;
                state.change(-1);
            }
            state.indoorTicks = 0;
        } else if (!canSeeSky) {
            state.skyTicks = 0;
            if (++state.indoorTicks >= 10 * 20) {
                state.indoorTicks = 0;
                state.change(1);
            }
        }

        if (inPowderSnow && leatherPieces == 0) {
            if (++state.powderSnowTicks >= 2 * 20) {
                state.powderSnowTicks = 0;
                state.change(-1);
            }
        } else {
            state.powderSnowTicks = 0;
        }

        // 站在冰/蓝冰/浮冰/雪/雪块上: 每15秒下降1点（皮甲会减速，3件以上免疫）
        if (isColdGround(level.getBlockState(player.blockPosition().below())) && !protectedFromCold) {
            if (++state.blockTicks >= groundColdInterval) {
                state.blockTicks = 0;
                state.change(-1);
            }
        } else {
            state.blockTicks = 0;
        }
    }

    private static void tickThirst(ServerPlayer player, State state) {
        // 露天下: 每25秒下降1点（会积累）
        if (player.level().canSeeSky(player.blockPosition())) {
            if (++state.skyTicks >= 25 * 20) {
                state.skyTicks = 0;
                state.change(-1);
            }
        } else {
            state.skyTicks = 0;
        }
        // 疾跑消耗: 每30秒下降1点
        if (isSpendingSprint(player)) {
            if (++state.sprintTicks >= 30 * 20) {
                state.sprintTicks = 0;
                state.change(-1);
            }
        } else {
            state.sprintTicks = 0;
        }
        // 在水中恢复口渴值
        if (player.isInWater() || player.isUnderWater()) {
            if (++state.waterTicks >= 20 * 20) {
                state.waterTicks = 0;
                state.change(1);
            }
        } else {
            state.waterTicks = 0;
        }
    }

    private static void tickHunger(ServerPlayer player, State state) {
        if (++state.passiveTicks >= 90 * 20) {
            state.passiveTicks = 0;
            state.change(-2);
        }
        if (isSpendingSprint(player)) {
            if (++state.sprintTicks >= 20 * 20) {
                state.sprintTicks = 0;
                state.change(-2);
            }
        } else {
            state.sprintTicks = 0;
        }
    }

    private static void tickPollution(ServerLevel level, ServerPlayer player, State state) {
        // 污染值从0开始，逐渐增长（与口渴/保暖值方向相反）
        boolean inWater = player.isInWater() || player.isUnderWater();
        boolean inRain = level.isRaining() && level.canSeeSky(player.blockPosition());

        // 泡在水中: 每15秒增加1点
        if (inWater) {
            if (++state.waterTicks >= 15 * 20) {
                state.waterTicks = 0;
                state.change(1);
            }
        } else {
            state.waterTicks = 0;
        }
        // 下雨时: 每8秒增加1点
        if (inRain) {
            if (++state.skyTicks >= 8 * 20) {
                state.skyTicks = 0;
                state.change(1);
            }
        } else {
            state.skyTicks = 0;
        }
        // 站在淋浴头下: 每2.5秒降低1点
        if (isNearSprinkler(level, player)) {
            if (++state.blockTicks >= 50) { // 2.5秒 = 50tick
                state.blockTicks = 0;
                state.change(-1);
            }
        } else {
            state.blockTicks = 0;
        }

        // 污染值满时效果
        if (state.value >= MAX_VALUE) {
            // 每秒降低0.05心情值
            if (++state.indoorTicks >= 20) {
                state.indoorTicks = 0;
                var mood = SREPlayerMoodComponent.KEY.get(player);
                if (mood != null) {
                    mood.setMood(Math.max(0, mood.getMood() - 0.05f));
                }
            }
            // 每5秒检查并施加缓慢1 + 失明2（效果持续6秒，保证不中断）
            if (++state.passiveTicks >= 100) {
                state.passiveTicks = 0;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 0, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 1, false, false, true));
            }
        } else {
            state.indoorTicks = 0;
            state.passiveTicks = 0;
        }
    }

    private static boolean shouldTrack(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)
                || player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
            return false;
        }
        // 污染值：杀手阵营不追踪
        MapStatusBarType type = currentStatusBar((ServerLevel) player.level());
        if (type == MapStatusBarType.POLLUTION) {
            var game = SREGameWorldComponent.KEY.get(player.level());
            if (game != null && game.isKillerTeam(player.getUUID())) {
                return false;
            }
        }
        return true;
    }

    private static void remove(ServerPlayer player) {
        if (STATES.remove(player.getUUID()) != null) {
            ServerPlayNetworking.send(player,
                    new MapStatusBarSyncS2CPacket(MapStatusBarType.NONE, MAX_VALUE, MAX_VALUE));
        }
    }

    private static MapStatusBarType currentStatusBar(ServerLevel level) {
        MapStatusBarType type = AreasWorldComponent.KEY.get(level).areasSettings.mapStatusBar;
        return type == null ? MapStatusBarType.NONE : type;
    }

    private static boolean isGameRunning(ServerLevel level) {
        return SREGameWorldComponent.KEY.get(level).isRunning();
    }

    private static boolean isColdGround(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    private static int leatherArmorPieces(ServerPlayer player) {
        int count = 0;
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET))
            count++;
        if (player.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE))
            count++;
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(Items.LEATHER_LEGGINGS))
            count++;
        if (player.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS))
            count++;
        return count;
    }

    private static boolean nearLitStove(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ModSceneBlocks.STOVE) && state.hasProperty(StoveBlock.LIT)
                            && state.getValue(StoveBlock.LIT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSpendingSprint(ServerPlayer player) {
        Vec3 movement = player.getDeltaMovement();
        return player.isSprinting() && movement.x * movement.x + movement.z * movement.z > 0.01D;
    }

    private static boolean isDrink(ItemStack stack) {
        return stack.getItem() instanceof CocktailItem
                || stack.getItem() instanceof PotionItem
                || stack.getItem() instanceof HoneyBottleItem
                || stack.is(NOELLES_DRINKS)
                || stack.is(SEItems.DRINKS);
    }

    /** 检查玩家是否在淋浴头下方（参考洗澡任务的检测方式） */
    private static boolean isNearSprinkler(ServerLevel level, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        for (int y = 0; y < 4; y++) {
            if (level.getBlockState(playerPos.above(y)).is(TMMBlockTags.SPRINKLERS)) {
                return true;
            }
        }
        return false;
    }

    private static void killByStatus(ServerPlayer player, MapStatusBarType type) {
        switch (type) {
            case WARMTH -> GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.FROZEN);
            case THIRST -> GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.THIRST);
            case HUNGER -> GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.STARVED);
            default -> {
            }
        }
    }

    private static final class State {
        private MapStatusBarType type;
        private int value = MAX_VALUE;
        private int lastSyncedValue = -1;
        private MapStatusBarType lastSyncedType = null;
        private int skyTicks;
        private int blockTicks;
        private int indoorTicks;
        private int powderSnowTicks;
        private int passiveTicks;
        private int sprintTicks;
        private int waterTicks;

        private State(MapStatusBarType type) {
            this.type = type;
        }

        private void reset(MapStatusBarType type) {
            this.type = type;
            this.value = type == MapStatusBarType.POLLUTION ? 0 : MAX_VALUE;
            this.lastSyncedValue = -1;
            this.lastSyncedType = null;
            clearTimers();
        }

        private void clearTimers() {
            skyTicks = 0;
            blockTicks = 0;
            indoorTicks = 0;
            powderSnowTicks = 0;
            passiveTicks = 0;
            sprintTicks = 0;
            waterTicks = 0;
        }

        private void change(int delta) {
            set(value + delta);
        }

        private void set(int value) {
            this.value = Math.max(0, Math.min(MAX_VALUE, value));
        }

        private void sync(ServerPlayer player) {
            if (lastSyncedType == type && lastSyncedValue == value) {
                return;
            }
            lastSyncedType = type;
            lastSyncedValue = value;
            ServerPlayNetworking.send(player, new MapStatusBarSyncS2CPacket(type, value, MAX_VALUE));
        }
    }
}
