package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity;
import net.exmo.sre.sixtyseconds.network.OpenNpcDialogueS2CPacket;
import net.exmo.sre.sixtyseconds.network.OpenNpcShopEditS2CPacket;
import net.exmo.sre.sixtyseconds.shop.SixtySecondsShopStore;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 对话菜单（服务端）：右键 NPC → 服务端按<b>该玩家 + 该 NPC 变体</b>的上下文算出可用选项 →
 * 客户端 {@code NpcDialogueScreen} 展示 → 点选后经 {@code NpcDialogueActionC2SPacket} 回传执行。
 * <p>结构照抄 {@link SixtySecondsDoorMenu}：{@code open} 攒 Option 列表下发，
 * {@code handleAction} 对每个动作<b>全量重校验</b>（C2S 可伪造，不能信客户端说的 enabled）。
 */
public final class SixtySecondsNpcMenu {
    // ⚠ 线格式常量：新值只能追加在末尾，不能插队/重排（客户端按 action 号取语言键与图标）
    public static final int ACTION_TALK = 0;
    public static final int ACTION_INTEL = 1;
    public static final int ACTION_TRADE = 2;
    public static final int ACTION_QUEST_LIST = 3;
    public static final int ACTION_QUEST_ACCEPT = 4;
    public static final int ACTION_QUEST_TURNIN = 5;
    public static final int ACTION_HIRE = 6;
    public static final int ACTION_STEAL = 7;
    public static final int ACTION_ROB = 8;
    public static final int ACTION_LEAVE = 9;
    /** 创造模式：编辑该商人的货架（GUI 编辑 + C2S 落盘，见 {@code NpcShopEditScreen}）。 */
    public static final int ACTION_SHOP_EDIT = 10;

    /** 每个变体的闲聊台词条数（message.noellesroles.sixty_seconds.npc.talk.<variant>.1..N）。 */
    private static final int TALK_LINES = 4;
    /** 情报台词条数（message.noellesroles.sixty_seconds.npc.intel.1..N）。 */
    private static final int INTEL_LINES = 8;

    private SixtySecondsNpcMenu() {
    }

    /** 右键 NPC：按上下文构建选项列表并开屏；不可交谈时给动作栏提示。 */
    public static void open(ServerPlayer player, SixtySecondsNpcEntity npc) {
        ServerLevel level = player.serverLevel();
        if (!SixtySecondsMod.isActive(level)) {
            return;
        }
        SixtySecondsNpcEntity.Variant variant = npc.getVariant();
        // 强盗不开菜单（mobInteract 已拦；这里是二重防线，防伪造包直接发 action）
        if (variant == SixtySecondsNpcEntity.Variant.BANDIT) {
            return;
        }
        // 记仇中 / 逃跑中：不搭理
        if (npc.isAngryAt(player.getUUID()) || npc.isFleeing()) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.npc.refuses_to_talk")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        boolean isDay = data.phase == SixtySecondsPhase.DAY;

        List<OpenNpcDialogueS2CPacket.Option> options = new ArrayList<>();
        options.add(new OpenNpcDialogueS2CPacket.Option(ACTION_TALK, true, 0));
        options.add(new OpenNpcDialogueS2CPacket.Option(ACTION_INTEL, true, 0));
        switch (variant) {
            case MERCHANT -> {
                // 准备阶段背包被 SixtySecondsInventoryLimit 的屏障占位，交易只在白天开放
                int rows = SixtySecondsNpcShop.entriesOf(level, npc).size();
                options.add(new OpenNpcDialogueS2CPacket.Option(ACTION_TRADE, isDay && rows > 0, rows));
            }
            case SOLDIER -> {
                boolean canHire = isDay && !npc.isHired()
                        && SixtySecondsNpcShop.totalFunds(player) >= SixtySecondsBalance.NPC_HIRE_COST;
                options.add(new OpenNpcDialogueS2CPacket.Option(
                        ACTION_HIRE, canHire, SixtySecondsBalance.NPC_HIRE_COST));
            }
            case TRAVELER -> {
                // 偷窃只在潜行时出现（param = 服务端算好的成功率%，客户端只负责显示）
                if (isDay && player.isShiftKeyDown()) {
                    options.add(new OpenNpcDialogueS2CPacket.Option(
                            ACTION_STEAL, true, SixtySecondsNpcTheft.successPercent(player, npc)));
                }
                options.add(new OpenNpcDialogueS2CPacket.Option(ACTION_ROB, isDay, 0));
            }
            default -> {
            }
        }
        if (player.isCreative() || player.hasPermissions(2)) {
            if (variant == SixtySecondsNpcEntity.Variant.MERCHANT) {
                options.add(new OpenNpcDialogueS2CPacket.Option(ACTION_SHOP_EDIT, true, 0));
            }
        }
        options.add(new OpenNpcDialogueS2CPacket.Option(ACTION_LEAVE, true, 0));

        ServerPlayNetworking.send(player, new OpenNpcDialogueS2CPacket(
                npc.getId(), variant.id, npc.getDisplayName().getString(),
                SixtySecondsNpcShop.totalFunds(player), options));
    }

    /** C2S 动作入口：全量重校验后执行。 */
    public static void handleAction(ServerPlayer player, int entityId, int action, int param) {
        ServerLevel level = player.serverLevel();
        if (!SixtySecondsMod.isActive(level)) {
            return;
        }
        SixtySecondsNpcEntity npc = resolve(player, entityId);
        if (npc == null) {
            return;
        }
        switch (action) {
            case ACTION_TALK -> speak(player, npc, "talk." + npc.getVariant().textureName
                    .replace("sixty_seconds_npc_", ""), TALK_LINES);
            case ACTION_INTEL -> speak(player, npc, "intel", INTEL_LINES);
            case ACTION_TRADE -> {
                if (npc.getVariant() == SixtySecondsNpcEntity.Variant.MERCHANT) {
                    SixtySecondsNpcShop.openShop(player, npc);
                }
            }
            case ACTION_SHOP_EDIT -> {
                if ((player.isCreative() || player.hasPermissions(2))
                        && npc.getVariant() == SixtySecondsNpcEntity.Variant.MERCHANT) {
                    ServerPlayNetworking.send(player, new OpenNpcShopEditS2CPacket(
                            npc.getId(), npc.getShopProfile(), SixtySecondsShopStore.get(level)));
                }
            }
            case ACTION_HIRE -> SixtySecondsNpcHire.hire(player, npc);
            case ACTION_STEAL -> SixtySecondsNpcTheft.startSteal(player, npc);
            case ACTION_ROB -> SixtySecondsNpcTheft.rob(player, npc);
            case ACTION_LEAVE -> {
                // 客户端自行关屏，服务端无需动作
            }
            default -> {
            }
        }
    }

    /**
     * 解析 C2S 传来的实体 id 并做防伪造校验：必须是 NPC、活着、同世界、在交互距离内。
     * （对照 {@code BreakInExecuteC2SPacket} 那种被伪造包绕过的教训。）
     */
    public static SixtySecondsNpcEntity resolve(ServerPlayer player, int entityId) {
        Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof SixtySecondsNpcEntity npc) || !npc.isAlive()) {
            return null;
        }
        if (npc.distanceToSqr(player) > SixtySecondsBalance.NPC_USE_DISTANCE_SQR) {
            return null;
        }
        return npc;
    }

    /** 随机挑一条台词发到聊天（不开新屏）。 */
    private static void speak(ServerPlayer player, SixtySecondsNpcEntity npc, String key, int lines) {
        int pick = 1 + player.serverLevel().getRandom().nextInt(lines);
        player.sendSystemMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.npc.line",
                npc.getDisplayName(),
                Component.translatable("message.noellesroles.sixty_seconds.npc." + key + "." + pick))
                .withStyle(ChatFormatting.GRAY));
    }
}
