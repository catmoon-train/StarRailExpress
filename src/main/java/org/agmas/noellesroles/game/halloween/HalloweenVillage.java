package org.agmas.noellesroles.game.halloween;

import java.util.*;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.halloween.village.*;
import org.agmas.noellesroles.init.ModEffects;

/** 万圣村服务端入口。仅由活动总运行器每世界 tick 一次，事件只注册一次。 */
public final class HalloweenVillage {
    public static final EntityType<VillageNpc> NPC = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("noellesroles", "halloween_village_npc"),
            EntityType.Builder.of(VillageNpc::new, MobCategory.MISC).sized(.6F, 1.8F)
                    .clientTrackingRange(8).updateInterval(2).noSave().build("halloween_village_npc"));
    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Long> NEXT_KNOCK = new WeakHashMap<>();
    private static boolean registered;
    private static Level clientNightLevel;
    private static boolean clientNight;
    private static int candyReward = 1;

    private HalloweenVillage() {}

    /** 可由总配置加载器设置；缺省每位糖果 NPC 一枚糖，领取后立即消失。 */
    public static void configureCandyReward(int candy) { candyReward = Math.clamp(candy, 1, 64); }

    public static void register() {
        if (registered) return;
        registered = true;
        FabricDefaultAttributeRegistry.register(NPC, VillageNpc.attributes());
        PayloadTypeRegistry.playS2C().register(VillagePackets.Open.ID, VillagePackets.Open.CODEC);
        PayloadTypeRegistry.playS2C().register(VillagePackets.Night.ID, VillagePackets.Night.CODEC);
        PayloadTypeRegistry.playC2S().register(VillagePackets.Buy.ID, VillagePackets.Buy.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(VillagePackets.Buy.ID,
                (packet, context) -> context.server().execute(() -> buy(context.player(), packet)));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level instanceof ServerLevel world && player instanceof ServerPlayer serverPlayer
                    && hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
                    && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
                knock(world, serverPlayer, hit.getBlockPos());
            }
            // 奖励是附加行为，不拦截开门，也不让非活动地图改变原有行为。
            return InteractionResult.PASS;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            for (State state : STATES.values()) {
                state.sessions.remove(handler.player.getUUID());
                state.lastInteract.remove(handler.player.getUUID());
            }
            VillageGhostVoice.removePlayer(handler.player.getUUID());
            ServerLevel level = handler.player.serverLevel();
            if (level.players().size() <= 1) clear(level);
        });
        ServerWorldEvents.UNLOAD.register((server, level) -> clear(level));
        io.wifi.starrailexpress.event.OnGameEnd.EVENT.register((level, game) -> clear(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (ServerLevel level : List.copyOf(STATES.keySet())) clear(level);
            NEXT_KNOCK.clear();
            VillageGhostVoice.clearAll();
        });
    }

    public static void tick(ServerLevel level) {
        if (!HalloweenRuntime.isEnabled(level)) { clear(level); return; }
        var areas = AreasWorldComponent.KEY.get(level);
        AABB bounds = areas.getResetPasteArea();
        State state = STATES.get(level);
        if (state != null && (!Objects.equals(state.map, areas.mapName) || !state.bounds.equals(bounds))) {
            clear(level); state = null;
        }
        if (state == null) {
            state = new State(level, areas.mapName, bounds);
            STATES.put(level, state);
        }
        long now = level.getGameTime();
        if (state.lastTick == now) return;
        state.lastTick = now;
        HalloweenConfig config = HalloweenConfig.get();
        State active = state;
        if (state.reapers.size() < 2) state.scanner.tick(level, config.scanBlocksPerTick, lamp -> {
            if (active.reapers.size() < 2 && !active.reapers.containsKey(lamp)) {
                VillageNpc npc = spawnNpc(level, Vec3.atBottomCenterOf(lamp.above()), false);
                active.reapers.put(lamp.immutable(), npc);
            }
        });
        // NPC 失去区块时不加载区块；重新加载后旧实例会由 owns 检查丢弃。
        for (var entry : state.reapers.entrySet()) {
            VillageNpc npc = entry.getValue();
            if (npc.isRemoved() && level.hasChunkAt(entry.getKey())) {
                entry.setValue(spawnNpc(level, Vec3.atBottomCenterOf(entry.getKey().above()), false));
            }
        }
        state.candy.entrySet().removeIf(entry -> {
            boolean expired = now >= entry.getValue() || entry.getKey().isRemoved();
            if (expired) entry.getKey().discard();
            return expired;
        });
        state.sessions.values().removeIf(session -> now > session.expires);
        boolean night = VillageRules.isNight(now - state.started, config.nightStartTicks,
                config.nightDurationTicks, config.nightIntervalTicks);
        boolean changed = state.night != night;
        state.night = night;
        VillageGhostVoice.publish(level, night);
        if (changed || now % 20 == 0) {
            List<UUID> souls = night ? level.players().stream().filter(Player::isSpectator)
                    .map(Player::getUUID).limit(1024).toList() : List.of();
            int phase = (int) ((now - state.started) % 12000);
            for (ServerPlayer player : level.players()) {
                boolean eligible = night && GameUtils.isPlayerAliveAndSurvival(player);
                sendNight(player, new VillagePackets.Night(eligible, phase, config.nightDarkThreshold,
                        eligible ? souls : List.of()));
                if (changed && eligible) message(player, "night_start");
                else if (changed && !night) message(player, "night_end");
            }
        }
    }

    public static void clear(ServerLevel level) {
        State state = STATES.remove(level);
        if (state == null) return;
        state.reapers.values().forEach(VillageNpc::discard);
        state.candy.keySet().forEach(VillageNpc::discard);
        for (ServerPlayer player : level.players()) sendNight(player, new VillagePackets.Night(false, 0, 5, List.of()));
        VillageGhostVoice.clear(level);
        if (STATES.keySet().stream().noneMatch(world -> world.getServer() == level.getServer())) NEXT_KNOCK.remove(level.getServer());
    }

    public static boolean isNearReaper(ServerPlayer player) {
        if (!HalloweenRuntime.isEnabled(player.level())) return false;
        State state = STATES.get(player.serverLevel());
        if (state == null) return false;
        double radius = HalloweenConfig.get().npcKillRadius;
        return state.reapers.values().stream().anyMatch(npc -> !npc.isRemoved()
                && npc.distanceToSqr(player) <= radius * radius);
    }

    public static boolean isGhostNight(Level level) {
        if (level.isClientSide) return clientNightLevel == level && clientNight;
        State state = STATES.get(level);
        return state != null && state.night && HalloweenRuntime.isEnabled(level);
    }

    /** 客户端只接收服务端授权状态，不能据此改变任何服务端规则。 */
    public static void setClientNight(Level level, boolean active) { clientNightLevel = level; clientNight = active; }

    public static boolean owns(VillageNpc npc) {
        if (!(npc.level() instanceof ServerLevel level) || !HalloweenRuntime.isEnabled(level)) return false;
        State state = STATES.get(level);
        return state != null && (state.reapers.containsValue(npc) || state.candy.containsKey(npc));
    }

    public static InteractionResult interact(VillageNpc npc, Player player, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !canInteract(serverPlayer, npc)) return InteractionResult.FAIL;
        State state = STATES.get(serverPlayer.serverLevel());
        long now = player.level().getGameTime();
        // 同一 tick 的双手包、重复 interactAt/interact 都只能执行一次。
        if (Objects.equals(state.lastInteract.put(player.getUUID(), now), now)) return InteractionResult.CONSUME;
        if (npc.isCandy()) {
            if (now >= state.candy.getOrDefault(npc, 0L)) return InteractionResult.FAIL;
            if (deliver(serverPlayer, 0, 0, new ItemStack(HalloweenItems.CANDY), candyReward)) {
                state.candy.remove(npc); npc.discard(); message(serverPlayer, "candy_received");
            } else message(serverPlayer, "inventory_full");
        } else if (player.getMainHandItem().is(HalloweenItems.SOUL_CONTRACT) || player.getOffhandItem().is(HalloweenItems.SOUL_CONTRACT)) {
            int contractsBefore = player.getInventory().countItem(HalloweenItems.SOUL_CONTRACT);
            if (HalloweenCombat.acceptPact(serverPlayer)) {
                player.getInventory().setChanged();
                serverPlayer.inventoryMenu.broadcastChanges();
                message(serverPlayer, "pact_accepted");
            } else message(serverPlayer, "pact_rejected");
        } else openShop(serverPlayer, npc);
        return InteractionResult.CONSUME;
    }

    private static boolean canInteract(ServerPlayer player, VillageNpc npc) {
        return owns(npc) && GameUtils.isPlayerAliveAndSurvival(player) && player.level() == npc.level()
                && !player.hasEffect(ModEffects.SAFE_TIME) && !player.hasEffect(ModEffects.USED_BANED)
                && !HalloweenCombat.isActionLocked(player) && !HalloweenPactRole.isPact(player)
                && player.distanceToSqr(npc) <= 16
                && player.hasLineOfSight(npc) && player.containerMenu == player.inventoryMenu;
    }

    private static void knock(ServerLevel level, ServerPlayer player, BlockPos position) {
        State state = STATES.get(level);
        if (state == null || !HalloweenRuntime.isEnabled(level) || !GameUtils.isPlayerAliveAndSurvival(player)
                || player.hasEffect(ModEffects.SAFE_TIME) || !level.hasChunkAt(position)
                || !state.bounds.contains(Vec3.atCenterOf(position))
                || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(position)) > 25) return;
        BlockState block = level.getBlockState(position);
        if (!(block.getBlock() instanceof DoorBlock) && !(block.getBlock() instanceof SmallDoorBlock)) return;
        BlockPos door = block.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && block.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
        // 防止伪造隔墙/越界右键包，只认可当前视线命中的同一扇门（上下半统一）。
        var ray = level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(5)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (!ray.getBlockPos().equals(position) && !ray.getBlockPos().equals(door) && !ray.getBlockPos().equals(door.above())) return;
        long now = level.getServer().overworld().getGameTime();
        long next = NEXT_KNOCK.getOrDefault(level.getServer(), 0L);
        if (!VillageRules.cooldownReady(now, next)) {
            player.displayClientMessage(Component.translatable("halloween.village.knock_cooldown", (next - now + 19) / 20), true);
            return;
        }
        HalloweenConfig config = HalloweenConfig.get();
        NEXT_KNOCK.put(level.getServer(), now + Math.max(1, config.knockCooldownTicks));
        level.playSound(null, door, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1, .7F);
        org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler.onDoorKnocked(player);
        switch (VillageRules.knockReward(level.random.nextInt(100))) {
            case 0 -> give(player, new ItemStack(HalloweenItems.PUMPKIN, 1 + level.random.nextInt(2)));
            case 1 -> HalloweenCombat.spawnUndead(level, null, player.getUUID(), player.position(), config.knockUndeadTicks, player.getUUID());
            case 2 -> give(player, new ItemStack(HalloweenItems.CANDY_BAG));
        }
        // 与 60/30/10 的奖励独立抽样，每个临时 NPC 全服只能成功领取一次。
        if (level.random.nextDouble() < config.candyNpcChance) {
            VillageNpc candy = spawnNpc(level, player.position().add(player.getLookAngle().scale(-1.5)), true);
            state.candy.put(candy, level.getGameTime() + config.candyNpcLifetimeTicks);
        }
    }

    private static VillageNpc spawnNpc(ServerLevel level, Vec3 position, boolean candy) {
        VillageNpc npc = NPC.create(level);
        if (npc == null) throw new IllegalStateException("halloween_village_npc");
        npc.setCandy(candy);
        npc.moveTo(position.x, position.y, position.z, 0, 0);
        npc.setCustomName(Component.translatable(candy ? "halloween.village.candy_npc" : "halloween.village.reaper"));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
        return npc;
    }

    private static void openShop(ServerPlayer player, VillageNpc npc) {
        if (!ServerPlayNetworking.canSend(player, VillagePackets.Open.ID) || player.hasEffect(ModEffects.SHOP_BANNED)) return;
        List<VillagePackets.Offer> offers = new ArrayList<>();
        for (HalloweenConfig.Trade trade : HalloweenConfig.get().trades) {
            if (offers.size() >= 128) break;
            ResourceLocation id = trade == null || trade.item == null ? null : ResourceLocation.tryParse(trade.item);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id) && trade.count > 0 && trade.count <= 4096
                    && trade.pumpkins >= 0 && trade.coins >= 0 && (trade.pumpkins > 0 || trade.coins > 0)
                    && !BuiltInRegistries.ITEM.get(id).getDefaultInstance().isEmpty()) {
                offers.add(new VillagePackets.Offer(id, trade.count, trade.pumpkins, trade.coins));
            }
        }
        UUID nonce = UUID.randomUUID();
        STATES.get(player.serverLevel()).sessions.put(player.getUUID(), new Session(npc.getUUID(), nonce,
                player.level().getGameTime() + 400, List.copyOf(offers)));
        ServerPlayNetworking.send(player, new VillagePackets.Open(npc.getUUID(), nonce, offers));
    }

    private static void buy(ServerPlayer player, VillagePackets.Buy packet) {
        State state = STATES.get(player.serverLevel());
        if (state == null) return;
        Session session = state.sessions.get(player.getUUID());
        if (session == null || !session.nonce.equals(packet.nonce()) || !session.npc.equals(packet.npc())) return;
        state.sessions.remove(player.getUUID()); // 匹配的 nonce 在验证/扣费之前作废，重放无效。
        if (player.level().getGameTime() > session.expires || packet.index() < 0 || packet.index() >= session.offers.size()
                || !(player.serverLevel().getEntity(packet.npc()) instanceof VillageNpc npc) || npc.isCandy()
                || !canInteract(player, npc) || player.hasEffect(ModEffects.SHOP_BANNED)) return;
        var offer = session.offers.get(packet.index());
        ItemStack reward = new ItemStack(BuiltInRegistries.ITEM.get(offer.item()));
        boolean success = deliver(player, offer.pumpkins(), offer.coins(), reward, offer.count());
        message(player, success ? "trade_success" : "trade_failed");
        openShop(player, npc);
    }

    private static boolean deliver(ServerPlayer player, int pumpkins, int coins, ItemStack reward, int amount) {
        var shop = SREPlayerShopComponent.KEY.get(player);
        var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        if (coins < 0 || (coins > 0 && (shop.balance < coins || role == null || role.hasNoCoinSystem()))) return false;
        if (reward.is(HalloweenItems.PUMPKIN))
            org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler.markSettled(reward);
        List<ItemStack> plan = VillageInventory.plan(player.getInventory().items, pumpkins, reward, amount, STACKS);
        if (plan == null) return false;
        // 主线程、无回调提交：两个币种和商品作为一个事务；不调用会部分插入的 Inventory.add。
        for (int i = 0; i < plan.size(); i++) player.getInventory().items.set(i, plan.get(i));
        if (coins > 0) shop.setBalance(shop.balance - coins);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (reward.is(HalloweenItems.PUMPKIN))
            org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler.onFreshPickup(player, amount);
        return true;
    }

    private static final VillageInventory.Slots<ItemStack> STACKS = new VillageInventory.Slots<>() {
        public int count(ItemStack stack) { return stack.getCount(); }
        public int limit(ItemStack stack) { return stack.getMaxStackSize(); }
        public boolean currency(ItemStack stack) { return stack.is(HalloweenItems.PUMPKIN); }
        public boolean matches(ItemStack a, ItemStack b) { return ItemStack.isSameItemSameComponents(a, b); }
        public ItemStack withCount(ItemStack stack, int count) { return count <= 0 ? ItemStack.EMPTY : stack.copyWithCount(count); }
    };

    private static void give(ServerPlayer player, ItemStack stack) {
        org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler.onRewardStack(player, stack);
        if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
        player.inventoryMenu.broadcastChanges();
    }
    private static void message(ServerPlayer player, String key) { player.displayClientMessage(Component.translatable("halloween.village." + key), true); }
    private static void sendNight(ServerPlayer player, VillagePackets.Night packet) {
        if (ServerPlayNetworking.canSend(player, VillagePackets.Night.ID)) ServerPlayNetworking.send(player, packet);
    }

    private record Session(UUID npc, UUID nonce, long expires, List<VillagePackets.Offer> offers) {}
    private static final class State {
        final String map;
        final AABB bounds;
        final long started;
        final VillageScanner scanner;
        final Map<BlockPos, VillageNpc> reapers = new LinkedHashMap<>();
        final Map<VillageNpc, Long> candy = new IdentityHashMap<>();
        final Map<UUID, Session> sessions = new HashMap<>();
        final Map<UUID, Long> lastInteract = new HashMap<>();
        long lastTick = Long.MIN_VALUE;
        boolean night;
        State(ServerLevel level, String map, AABB bounds) {
            this.map = map; this.bounds = bounds; started = level.getGameTime(); scanner = new VillageScanner(bounds, level);
        }
    }
}
