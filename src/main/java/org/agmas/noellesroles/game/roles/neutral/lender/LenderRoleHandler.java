package org.agmas.noellesroles.game.roles.neutral.lender;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.LoanContractItem;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.LoanContractOpenS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.agmas.noellesroles.utils.RoleUtils;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative request, loan timer and unpaid-body-part handling. */
public final class LenderRoleHandler {
    public static final net.minecraft.resources.ResourceLocation SKILL_ID = SRE.id("lender_request");
    public static final int SKILL_COOLDOWN_SECONDS = 80;
    public static final int MAX_PRINCIPAL = 175;
    public static final int INTEREST_STEP = 25;
    public static final long INTEREST_INTERVAL_TICKS = 45L * 20L;
    private static final long REMINDER_INTERVAL_TICKS = 60L * 20L;
    private static final long DUE_TICKS = 210L * 20L;
    private static final long REQUEST_TICKS = 10L * 20L;

    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final Map<UUID, Request> PENDING_BY_LENDER = new HashMap<>();
    private static final Map<UUID, Request> ACCEPTED = new HashMap<>();
    private static boolean registered;

    private LenderRoleHandler() {
    }

    public static void register() {
        registerSkill();
        registerEvents();
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.LENDER, RoleSkill.skill(
                SKILL_ID,
                "skill.noellesroles.lender_request",
                context -> {
                    if (context.target() == null) {
                        context.displayNoTargetMessage();
                        return false;
                    }
                    ServerPlayer lender = context.player();
                    Player targetPlayer = lender.serverLevel().getPlayerByUUID(context.target());
                    if (!(targetPlayer instanceof ServerPlayer target)
                            || target == lender || !GameUtils.isPlayerAliveAndSurvival(target)) {
                        return false;
                    }
                    long now = lender.level().getGameTime();
                    Request pending = PENDING_BY_LENDER.get(lender.getUUID());
                    if (pending != null) {
                        if (now - pending.createdAt() < REQUEST_TICKS) {
                            lender.displayClientMessage(Component.translatable("message.noellesroles.loan.request_pending")
                                    .withStyle(ChatFormatting.RED), true);
                            return false;
                        }
                        PENDING_BY_LENDER.remove(lender.getUUID(), pending);
                        REQUESTS.remove(pending.target(), pending);
                    }
                    Request previous = REQUESTS.put(target.getUUID(),
                            new Request(lender.getUUID(), target.getUUID(), now));
                    if (previous != null) {
                        PENDING_BY_LENDER.remove(previous.lender(), previous);
                    }
                    PENDING_BY_LENDER.put(lender.getUUID(), REQUESTS.get(target.getUUID()));
                    target.displayClientMessage(Component.translatable("message.noellesroles.loan.request", lender.getName())
                            .withStyle(ChatFormatting.GOLD), true);
                    lender.displayClientMessage(Component.translatable("message.noellesroles.loan.request_sent", target.getName())
                            .withStyle(ChatFormatting.AQUA), true);
                    return false;
                }).withTarget().cooldownSeconds(SKILL_COOLDOWN_SECONDS).announceToSelf().showOnHud(true).build());
    }

    public static void registerEvents() {
        if (registered) {
            return;
        }
        registered = true;
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer borrower)
                    || !(entity instanceof ServerPlayer lender) || borrower == lender) {
                return InteractionResult.PASS;
            }
            if (!SREGameWorldComponent.KEY.get(level).isRole(lender, ModRoles.LENDER)) {
                return InteractionResult.PASS;
            }
            Request request = REQUESTS.get(borrower.getUUID());
            if (request == null || !request.lender().equals(lender.getUUID())
                    || level.getGameTime() - request.createdAt() >= REQUEST_TICKS) {
                return InteractionResult.PASS;
            }
            REQUESTS.remove(borrower.getUUID(), request);
            PENDING_BY_LENDER.remove(lender.getUUID(), request);
            ACCEPTED.put(borrower.getUUID(), new Request(lender.getUUID(), borrower.getUUID(), level.getGameTime()));
            SREAbilityPlayerComponent.KEY.get(lender)
                    .setSkillCooldown(SKILL_ID, SKILL_COOLDOWN_SECONDS * 20);
            ServerPlayNetworking.send(borrower, new LoanContractOpenS2CPacket(lender.getUUID()));
            borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.accepted")
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        });
        // 进食/饮水禁用统一由 StatusAilmentHandler 通过食欲不振（LOSS_OF_APPETITE）处理
        ServerTickEvents.END_SERVER_TICK.register(LenderRoleHandler::tick);
    }

    public static void submit(ServerPlayer borrower, UUID lenderId, int amount) {
        Request request = ACCEPTED.get(borrower.getUUID());
        if (request == null || !request.lender().equals(lenderId)) {
            borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.expired")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (amount < 1 || amount > MAX_PRINCIPAL) {
            borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.invalid_amount", MAX_PRINCIPAL)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        boolean debugLoan = borrower.getUUID().equals(lenderId) && borrower.getUUID().equals(request.lender());
        ServerPlayer lender = debugLoan ? borrower : borrower.server.getPlayerList().getPlayer(lenderId);
        if (lender == null || !GameUtils.isPlayerAliveAndSurvival(lender)) {
            borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.lender_unavailable")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!debugLoan && !MoneyUtils.cost(lender, amount)) {
            lender.displayClientMessage(Component.translatable("message.noellesroles.loan.lender_insufficient", amount)
                    .withStyle(ChatFormatting.RED), true);
            borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.lender_insufficient", amount)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack contract = ModItems.LOAN_CONTRACT.getDefaultInstance();
        LoanContractItem.initialize(contract, amount, lenderId, borrower.level().getGameTime());
        if (!borrower.getInventory().add(contract)) {
            if (!debugLoan) {
                MoneyUtils.addToBalance(lender, amount);
            }
            borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.inventory_full")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        MoneyUtils.addToBalance(borrower, amount);
        ACCEPTED.remove(borrower.getUUID());
        lender.displayClientMessage(Component.translatable("message.noellesroles.loan.created", borrower.getName(), amount)
                .withStyle(ChatFormatting.GOLD), true);
        borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.received", amount)
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** Deducts the full current debt from the player holding the contract. */
    public static boolean repayContract(ServerPlayer borrower, ItemStack contract) {
        int due = LoanContractItem.totalDue(contract, borrower.level().getGameTime());
        if (due <= 0 || !MoneyUtils.cost(borrower, due)) {
            borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.insufficient", due)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /** Opens the contract form without requiring a real lender request. */
    public static void debugAccept(ServerPlayer borrower) {
        long now = borrower.level().getGameTime();
        ACCEPTED.put(borrower.getUUID(), new Request(borrower.getUUID(), borrower.getUUID(), now));
        ServerPlayNetworking.send(borrower, new LoanContractOpenS2CPacket(borrower.getUUID()));
        borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.accepted")
                .withStyle(ChatFormatting.GREEN), true);
    }

    private static void tick(MinecraftServer server) {
        PENDING_BY_LENDER.entrySet().removeIf(entry -> {
            Request request = entry.getValue();
            ServerPlayer target = server.getPlayerList().getPlayer(request.target());
            if (target == null || target.level().getGameTime() - request.createdAt() >= REQUEST_TICKS) {
                REQUESTS.remove(request.target(), request);
                return true;
            }
            return false;
        });
        REQUESTS.entrySet().removeIf(entry -> {
            ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());
            return target == null || target.level().getGameTime() - entry.getValue().createdAt() >= REQUEST_TICKS;
        });
        ACCEPTED.entrySet().removeIf(entry -> {
            ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());
            return target == null;
        });
        for (ServerPlayer borrower : server.getPlayerList().getPlayers()) {
            if (!GameUtils.isPlayerAliveAndSurvival(borrower)
                    || !SREGameWorldComponent.KEY.get(borrower.level()).isRunning()) {
                continue;
            }
            for (int slot = 0; slot < borrower.getInventory().getContainerSize(); slot++) {
                ItemStack stack = borrower.getInventory().getItem(slot);
                if (!stack.is(ModItems.LOAN_CONTRACT)) {
                    continue;
                }
                long elapsed = Math.max(0L, borrower.level().getGameTime() - LoanContractItem.createdAt(stack));
                LoanContractItem.updateInterest(stack, borrower.level().getGameTime());
                long reminderIndex = elapsed / REMINDER_INTERVAL_TICKS;
                if (reminderIndex > LoanContractItem.lastReminder(stack) && elapsed >= REMINDER_INTERVAL_TICKS
                        && elapsed < DUE_TICKS) {
                    borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.reminder",
                            LoanContractItem.totalDue(stack, borrower.level().getGameTime()))
                            .withStyle(ChatFormatting.RED), true);
                    LoanContractItem.markReminder(stack, reminderIndex);
                }
                if (elapsed >= DUE_TICKS) {
                    forcePayment(borrower, stack);
                    borrower.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    private static void forcePayment(ServerPlayer borrower, ItemStack contract) {
        int due = LoanContractItem.totalDue(contract, borrower.level().getGameTime());
        int available = MoneyUtils.getBalance(borrower);
        int paid = Math.min(available, due);
        if (paid > 0) {
            MoneyUtils.addToBalance(borrower, -paid);
        }
        int remaining = due - paid;
        if (remaining > 0) {
            applyRandomLimbPenalty(borrower);
        }
        borrower.displayClientMessage(Component.translatable("message.noellesroles.loan.forced", due, paid)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    private static void applyRandomLimbPenalty(ServerPlayer player) {
        switch (player.getRandom().nextInt(8)) {
            case 0 -> {
                RoleUtils.removeModifier(player, TraitorAndModifiers.NIGHT_OWL);
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Integer.MAX_VALUE, 1, false, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, Integer.MAX_VALUE, 0, false, false, true));
                notifyPenalty(player, "eye");
            }
            case 1 -> {
                GameUtils.forceKillPlayer(player, true, null, GameConstants.DeathReasons.HEART_ATTACK);
                notifyPenalty(player, "heart");
            }
            case 2 -> {
                player.addEffect(new MobEffectInstance(ModEffects.USED_BANED, Integer.MAX_VALUE, 0, false, false, true));
                notifyPenalty(player, "hand");
            }
            case 3 -> {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, Integer.MAX_VALUE, 1, false, false, true));
                notifyPenalty(player, "liver");
            }
            case 4 -> {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 1, false, false, true));
                notifyPenalty(player, "leg");
            }
            case 5 -> {
                player.addEffect(new MobEffectInstance(ModEffects.LOSS_OF_APPETITE, Integer.MAX_VALUE, 0, false, false, true));
                notifyPenalty(player, "stomach");
            }
            case 6 -> {
                player.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, Integer.MAX_VALUE, 0, false, false, true));
                player.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, Integer.MAX_VALUE, 0, false, false, true));
                notifyPenalty(player, "tongue");
            }
            default -> {
                player.addEffect(new MobEffectInstance(ModEffects.MUFFLED_HEARING, Integer.MAX_VALUE, 1, false, false, true));
                notifyPenalty(player, "ear");
            }
        }
    }

    private static void notifyPenalty(ServerPlayer player, String part) {
        player.displayClientMessage(Component.translatable("message.noellesroles.loan.penalty." + part)
                .withStyle(ChatFormatting.DARK_RED), true);
    }

    private record Request(UUID lender, UUID target, long createdAt) {
    }
}
