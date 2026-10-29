package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 灵魂链接：服务端邀请、单一共享容器、共享盾池及同生共死。 */
public final class HalloweenSoulLinks {
    private static final Map<UUID, Link> LINKS = new HashMap<>();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Map<UUID, Long> INVITE_COOLDOWNS = new HashMap<>();
    private static final ThreadLocal<Boolean> CONSUMING = ThreadLocal.withInitial(() -> false);
    private static final ResourceLocation LINK_DEATH = Noellesroles.id("halloween_soul_link");
    private static boolean registered;

    private HalloweenSoulLinks() {}

    private record Invite(UUID sender, UUID recipient, ServerLevel level, long until, InteractionHand hand) {}
    private static final class Link {
        final ServerLevel level;
        final UUID first, second;
        final SimpleContainer inventory = new SimpleContainer(27);
        Vec3 lastFirst, lastSecond;
        Link(ServerPlayer a, ServerPlayer b) {
            level = a.serverLevel();
            first = a.getUUID(); second = b.getUUID();
            lastFirst = a.position(); lastSecond = b.position();
        }
        UUID other(UUID self) { return first.equals(self) ? second : first; }
    }

    public static void register() {
        if (registered) return;
        registered = true;
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Player target)
                    || !HalloweenRuntime.isEnabled(level)) return InteractionResult.PASS;
            boolean proposing = player.getItemInHand(hand).is(HalloweenItems.SOUL_LINK);
            boolean opening = player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()
                    && target.getUUID().equals(partnerId(player));
            if (!proposing && !opening) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer sender) || !(target instanceof ServerPlayer recipient)
                    || sender == recipient || !canAct(sender) || !canAct(recipient) || !sender.hasLineOfSight(recipient)
                    || sender.distanceToSqr(recipient) > 9) return InteractionResult.FAIL;
            if (recipient.getUUID().equals(partnerId(sender))) {
                openShared(sender, LINKS.get(sender.getUUID()));
            } else if (proposing) invite(sender, recipient, hand);
            return InteractionResult.SUCCESS;
        });
        OnPlayerDeathWithKiller.FINAL_EVENT.register((victim, killer, reason) -> dieTogether(victim));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            Link link = LINKS.get(player.getUUID());
            if (link != null) remove(link, true);
            INVITES.entrySet().removeIf(e -> e.getKey().equals(player.getUUID())
                    || e.getValue().sender.equals(player.getUUID()));
            INVITE_COOLDOWNS.remove(player.getUUID());
        });
    }

    private static boolean canAct(ServerPlayer player) {
        return HalloweenRuntime.isEnabled(player.level()) && GameUtils.isPlayerAliveAndSurvival(player)
                && !player.hasEffect(ModEffects.USED_BANED) && !player.hasEffect(ModEffects.SAFE_TIME);
    }

    private static void invite(ServerPlayer sender, ServerPlayer recipient, InteractionHand hand) {
        long now = GameUtils.getTicksFromGameStart(sender.level());
        if (LINKS.containsKey(sender.getUUID()) || LINKS.containsKey(recipient.getUUID())
                || INVITE_COOLDOWNS.getOrDefault(sender.getUUID(), 0L) > now
                || INVITES.containsKey(recipient.getUUID())) {
            sender.displayClientMessage(Component.translatable("halloween.link.unavailable"), true);
            return;
        }
        Invite invite = new Invite(sender.getUUID(), recipient.getUUID(), sender.serverLevel(),
                now + HalloweenConfig.get().linkInviteTicks, hand);
        INVITES.put(recipient.getUUID(), invite);
        INVITE_COOLDOWNS.put(sender.getUUID(), now + 200);
        recipient.openMenu(new SimpleMenuProvider((id, inv, p) -> new ConsentMenu(id, inv, invite),
                Component.translatable("halloween.link.invite", sender.getDisplayName())));
        sender.displayClientMessage(Component.translatable("halloween.link.sent"), true);
    }

    private static void accept(ServerPlayer recipient, Invite invite) {
        INVITES.remove(recipient.getUUID(), invite);
        ServerPlayer sender = invite.level.getServer().getPlayerList().getPlayer(invite.sender);
        if (sender == null || !recipient.getUUID().equals(invite.recipient)
                || recipient.serverLevel() != invite.level || sender.serverLevel() != invite.level
                || GameUtils.getTicksFromGameStart(invite.level) >= invite.until
                || !canAct(sender) || !canAct(recipient) || sender.distanceToSqr(recipient) > 9
                || !sender.hasLineOfSight(recipient) || LINKS.containsKey(sender.getUUID())
                || LINKS.containsKey(recipient.getUUID())
                || !sender.getItemInHand(invite.hand).is(HalloweenItems.SOUL_LINK)) return;
        Link link = new Link(sender, recipient);
        LINKS.put(link.first, link); LINKS.put(link.second, link);
        sender.getItemInHand(invite.hand).shrink(1);
        sender.getInventory().setChanged();
        sender.containerMenu.broadcastChanges();
        HalloweenNetwork.sync(invite.level);
        SREArmorPlayerComponent.KEY.get(sender).sync();
        SREArmorPlayerComponent.KEY.get(recipient).sync();
        sender.displayClientMessage(Component.translatable("halloween.link.established"), false);
        recipient.displayClientMessage(Component.translatable("halloween.link.established"), false);
    }

    private static void openShared(ServerPlayer player, Link link) {
        if (link == null) return;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new SharedMenu(id, inv, link),
                Component.translatable("halloween.link.inventory")));
    }

    public static UUID partnerId(Player player) {
        if (!HalloweenRuntime.isEnabled(player.level())) return null;
        if (player.level().isClientSide) return HalloweenNetwork.clientPartner(player.getUUID());
        Link link = LINKS.get(player.getUUID());
        return link == null || link.level != player.level() ? null : link.other(player.getUUID());
    }

    public static Player partner(Player player) {
        UUID id = partnerId(player);
        return id == null ? null : player.level().getPlayerByUUID(id);
    }

    public static boolean arePartners(Player first, Player second) {
        return first.level() == second.level() && second.getUUID().equals(partnerId(first));
    }

    public static int rawArmor(Player player) {
        var armor = SREArmorPlayerComponent.KEY.get(player);
        long now = GameUtils.getTicksFromGameStart(player.level());
        long count = Math.max(0, armor.armor);
        for (var entry : armor.timedArmor.entrySet()) {
            if (entry.getKey() > now) count += Math.max(0, entry.getValue());
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /** 非链接返回 null 走原逻辑；递归保护只覆盖一次同步消费。 */
    public static Boolean consumeArmor(Player player, int amount) {
        if (CONSUMING.get()) return null;
        Player other = partner(player);
        if (other == null || player.level().isClientSide) return null;
        int partnerArmor = HalloweenRuntime.isShieldSuppressed(other) ? 0 : rawArmor(other);
        int total = HalloweenLinkRules.sharedArmor(rawArmor(player), partnerArmor);
        int own = HalloweenLinkRules.consumeOwn(rawArmor(player), amount);
        int remaining = Math.min(partnerArmor, Math.max(0, amount - own));
        CONSUMING.set(true);
        try {
            if (own > 0) SREArmorPlayerComponent.KEY.get(player).triggerAndRemoveArmor(own);
            if (remaining > 0) SREArmorPlayerComponent.KEY.get(other).triggerAndRemoveArmor(remaining);
            return total >= amount;
        } finally { CONSUMING.remove(); }
    }

    private static void dieTogether(Player victim) {
        if (!(victim instanceof ServerPlayer player) || !HalloweenRuntime.isEnabled(player.level())) return;
        Link link = LINKS.get(player.getUUID());
        if (link == null) return;
        ServerPlayer other = link.level.getServer().getPlayerList().getPlayer(link.other(player.getUUID()));
        remove(link, true);
        // 一方已真实死亡，另一方不能再用共享盾/免死规避同生共死；先解除链接以避免递归。
        if (other != null && other.level() == player.level() && GameUtils.isPlayerAliveAndSurvival(other))
            GameUtils.forceKillPlayer(other, true, null, LINK_DEATH);
    }

    public static void tick(ServerLevel level) {
        long now = GameUtils.getTicksFromGameStart(level);
        INVITES.entrySet().removeIf(e -> e.getValue().level == level && e.getValue().until <= now);
        for (Link link : new HashSet<>(LINKS.values())) {
            if (link.level != level) continue;
            ServerPlayer a = level.getServer().getPlayerList().getPlayer(link.first);
            ServerPlayer b = level.getServer().getPlayerList().getPlayer(link.second);
            if (a == null || b == null || a.serverLevel() != level || b.serverLevel() != level
                    || !GameUtils.isPlayerAliveAndSurvival(a) || !GameUtils.isPlayerAliveAndSurvival(b)) {
                remove(link, true);
                continue;
            }
            double limit = HalloweenConfig.get().linkDistance;
            if (!HalloweenLinkRules.within(a.distanceToSqr(b), limit)) {
                boolean firstMoved = a.position().distanceToSqr(link.lastFirst)
                        >= b.position().distanceToSqr(link.lastSecond);
                ServerPlayer moved = firstMoved ? a : b, anchor = firstMoved ? b : a;
                Vec3 delta = moved.position().subtract(anchor.position()).normalize();
                Vec3 end = anchor.position().add(delta.scale(Math.max(0, limit - .15)));
                if (!level.noCollision(moved, moved.getBoundingBox().move(end.subtract(moved.position()))))
                    end = anchor.position();
                moved.teleportTo(level, end.x, end.y, end.z, moved.getYRot(), moved.getXRot());
                moved.setDeltaMovement(Vec3.ZERO);
                moved.fallDistance = 0;
            }
            link.lastFirst = a.position(); link.lastSecond = b.position();
        }
    }

    private static void remove(Link link, boolean drop) {
        if (!LINKS.remove(link.first, link)) return;
        LINKS.remove(link.second, link);
        Vec3 pos = link.lastFirst;
        for (UUID id : Set.of(link.first, link.second)) {
            ServerPlayer player = link.level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.containerMenu instanceof SharedMenu menu && menu.link == link)
                player.closeContainer();
        }
        for (int i = 0; i < link.inventory.getContainerSize(); i++) {
            ItemStack stack = link.inventory.removeItemNoUpdate(i);
            if (drop && !stack.isEmpty()) link.level.addFreshEntity(new ItemEntity(link.level,
                    pos.x, pos.y + .3, pos.z, stack));
        }
        HalloweenNetwork.sync(link.level);
    }

    public static void clear(ServerLevel level) {
        for (Link link : new HashSet<>(LINKS.values())) if (link.level == level) remove(link, false);
        INVITES.entrySet().removeIf(e -> e.getValue().level == level);
        INVITE_COOLDOWNS.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null
                || level.getServer().getPlayerList().getPlayer(id).level() == level);
    }

    private static final class SharedMenu extends ChestMenu {
        final Link link;
        SharedMenu(int id, Inventory inventory, Link link) {
            super(MenuType.GENERIC_9x3, id, inventory, link.inventory, 3);
            this.link = link;
        }
        @Override public boolean stillValid(Player player) {
            Player other = partner(player);
            return LINKS.get(player.getUUID()) == link && GameUtils.isPlayerAliveAndSurvival(player)
                    && other != null && GameUtils.isPlayerAliveAndSurvival(other)
                    && HalloweenLinkRules.within(player.distanceToSqr(other), HalloweenConfig.get().linkDistance);
        }
    }

    private static final class ConsentMenu extends ChestMenu {
        private final Invite invite;
        ConsentMenu(int id, Inventory inventory, Invite invite) {
            super(MenuType.GENERIC_9x3, id, inventory, new SimpleContainer(27), 3);
            this.invite = invite;
            ItemStack yes = new ItemStack(Items.LIME_WOOL);
            yes.set(DataComponents.CUSTOM_NAME, Component.translatable("halloween.link.accept"));
            ItemStack no = new ItemStack(Items.RED_WOOL);
            no.set(DataComponents.CUSTOM_NAME, Component.translatable("halloween.link.decline"));
            getSlot(11).set(yes); getSlot(15).set(no);
        }
        @Override public void clicked(int slot, int button, ClickType type, Player player) {
            if (!(player instanceof ServerPlayer recipient) || !stillValid(player)) return;
            if (type != ClickType.PICKUP || button != 0 || (slot != 11 && slot != 15)) return;
            boolean valid = INVITES.get(recipient.getUUID()) == invite;
            recipient.closeContainer();
            if (valid && slot == 11) accept(recipient, invite);
            else INVITES.remove(recipient.getUUID(), invite);
        }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) {
            return player.getUUID().equals(invite.recipient) && player.level() == invite.level
                    && INVITES.get(player.getUUID()) == invite && HalloweenRuntime.isEnabled(player.level())
                    && GameUtils.getTicksFromGameStart(player.level()) < invite.until;
        }
    }
}
