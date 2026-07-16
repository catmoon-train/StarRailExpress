package net.exmo.sre.sixtyseconds.content.item;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsEventSystem;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsTechTree;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 功能类道具（按 {@link Type} 区分）：
 * <ul>
 *   <li><b>RADIO 收音机</b>：播报当前环境事件与队伍状况（不消耗）。</li>
 *   <li><b>COMPASS 罗盘</b>：指示回本队避难所的方向与距离（不消耗）。</li>
 *   <li><b>TOOLBOX 工具箱</b>：拆开获得 3 件随机材料/工具（消耗）。</li>
 *   <li><b>BLUEPRINT 图纸</b>：为队伍免费解锁一个可解锁的随机科技（消耗）。</li>
 *   <li><b>ALARM 警报器</b>：布防今晚——夜袭者数量 -1（消耗，每晚一次）。</li>
 *   <li><b>LURE 诱饵</b>：把今晚本队一半夜袭者引向随机别队（消耗，每晚一次）。</li>
 * </ul>
 */
public class SixtySecondsUtilityItem extends Item {
    public enum Type {
        RADIO, COMPASS, TOOLBOX, BLUEPRINT, ALARM, LURE, MAGNET
    }

    private final Type type;

    public SixtySecondsUtilityItem(Properties properties, Type type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        if (!SixtySecondsMod.isActive(level)) {
            return InteractionResultHolder.pass(stack);
        }
        SixtySecondsState.Data data = SixtySecondsState.get(serverLevel);
        SixtySecondsState.TeamData team = data.teams.get(SixtySecondsStatsComponent.KEY.get(serverPlayer).teamId);
        boolean consumed = switch (type) {
            case RADIO -> radio(serverLevel, serverPlayer);
            case COMPASS -> compass(serverPlayer, team);
            case TOOLBOX -> toolbox(serverLevel, serverPlayer);
            case BLUEPRINT -> blueprint(serverLevel, serverPlayer, team);
            case ALARM -> alarm(serverPlayer, team);
            case LURE -> lure(serverPlayer, team);
            case MAGNET -> magnet(serverLevel, serverPlayer, stack);
        };
        if (consumed && !serverPlayer.isCreative() && type != Type.MAGNET) {
            stack.shrink(1);
        }
        return InteractionResultHolder.success(stack);
    }

    /** 磁铁：把周围 12 格的掉落物吸到脚下（耗 1 点耐久，不消耗物品本体）。 */
    private static boolean magnet(ServerLevel level, ServerPlayer player, ItemStack stack) {
        List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                player.getBoundingBox().inflate(12.0D), e -> e.isAlive());
        if (items.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.magnet_nothing"), true);
            return false;
        }
        for (net.minecraft.world.entity.item.ItemEntity item : items) {
            item.teleportTo(player.getX(), player.getY() + 0.5, player.getZ());
            item.setNoPickUpDelay();
        }
        stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
        return false;
    }

    /**
     * 每秒对手持罗盘（主/副手）的玩家在动作栏显示当前坐标 <b>(x, z)</b>（不显示 y 轴）
     * 及回避难所的方位+距离。在 {@code SixtySecondsManager.tick} 中调用（服务端低频，无需同步）。
     */
    public static void tickHeldCompass(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (ServerPlayer player : level.players()) {
            boolean holding = isCompass(player.getMainHandItem()) || isCompass(player.getOffhandItem());
            if (!holding) {
                continue;
            }
            Component text = Component.translatable("message.noellesroles.sixty_seconds.compass_pos",
                    player.getBlockX(), player.getBlockZ()).withStyle(ChatFormatting.AQUA);
            SixtySecondsState.TeamData team =
                    data.teams.get(SixtySecondsStatsComponent.KEY.get(player).teamId);
            if (team != null) {
                BlockPos home = team.returnDoorPos;
                if (home == null) {
                    home = team.shelterSpawn;
                }
                if (home != null) {
                    double dx = home.getX() + 0.5 - player.getX();
                    double dz = home.getZ() + 0.5 - player.getZ();
                    int distance = (int) Math.sqrt(dx * dx + dz * dz);
                    text = Component.empty().append(text).append(Component.literal("  "))
                            .append(Component.translatable("message.noellesroles.sixty_seconds.compass_home",
                                    distance).withStyle(ChatFormatting.GRAY));
                }
            }
            player.displayClientMessage(text, true);
        }
    }

    private static boolean isCompass(ItemStack stack) {
        return stack.getItem() instanceof SixtySecondsUtilityItem utility && utility.type == Type.COMPASS;
    }

    private static boolean radio(ServerLevel level, ServerPlayer player) {
        String eventKey = SixtySecondsEventSystem.activeEventKey(level);
        Component event = eventKey == null
                ? Component.translatable("message.noellesroles.sixty_seconds.radio_no_event")
                : Component.translatable(eventKey);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.radio_report", event).withStyle(ChatFormatting.YELLOW), false);
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
        return false;
    }

    private static boolean compass(ServerPlayer player, SixtySecondsState.TeamData team) {
        if (team == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.info_no_team"), true);
            return false;
        }
        BlockPos home = team.returnDoorPos;
        if (home == null) {
            home = team.shelterSpawn;
        }
        if (home == null) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.info_no_team"), true);
            return false;
        }
        double dx = home.getX() + 0.5 - player.getX();
        double dz = home.getZ() + 0.5 - player.getZ();
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        // 以玩家视角计算相对方位（前/后/左/右）
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90 - player.getYRot();
        angle = ((angle % 360) + 360) % 360;
        String dir = angle < 45 || angle >= 315 ? "front" : angle < 135 ? "left" : angle < 225 ? "back" : "right";
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.compass_report",
                Component.translatable("message.noellesroles.sixty_seconds.compass_" + dir), distance)
                .withStyle(ChatFormatting.AQUA), true);
        return false;
    }

    private static boolean toolbox(ServerLevel level, ServerPlayer player) {
        SixtySecondsLootTable loot = SixtySecondsLootStore.get(level);
        List<String> pool = new ArrayList<>();
        for (String category : loot.categoryNames()) {
            if ("material".equals(category) || "tool".equals(category)) {
                pool.add(category);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(loot.categoryNames());
        }
        for (int i = 0; i < 3; i++) {
            ItemStack roll = loot.roll(pool.get(level.getRandom().nextInt(pool.size())), level.getRandom());
            if (!roll.isEmpty() && !player.getInventory().add(roll)) {
                player.drop(roll, false);
            }
        }
        player.playNotifySound(SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.8F, 1.2F);
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.toolbox_opened"), true);
        return true;
    }

    private static boolean blueprint(ServerLevel level, ServerPlayer player, SixtySecondsState.TeamData team) {
        if (team == null) {
            return false;
        }
        List<SixtySecondsTechTree.TechNode> candidates = new ArrayList<>();
        for (SixtySecondsTechTree.TechNode node : SixtySecondsTechTree.NODES) {
            if (!team.unlockedTech.contains(node.id())
                    && (node.parentId() == null || team.unlockedTech.contains(node.parentId()))
                    && SixtySecondsTechTree.gateSatisfied(node, team.unlockedTech)) {
                candidates.add(node);
            }
        }
        if (candidates.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.blueprint_all_unlocked"), true);
            return false;
        }
        SixtySecondsTechTree.TechNode node = candidates.get(level.getRandom().nextInt(candidates.size()));
        team.unlockedTech.add(node.id());
        SixtySecondsTechTree.applyUnlockSideEffects(team, node.id());
        Component name = Component.translatable("tech.noellesroles.sixty_seconds." + node.id());
        for (UUID uuid : team.members) {
            if (level.getPlayerByUUID(uuid) instanceof ServerPlayer member) {
                member.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.blueprint_unlocked", name)
                        .withStyle(ChatFormatting.GREEN), false);
            }
        }
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        return true;
    }

    private static boolean alarm(ServerPlayer player, SixtySecondsState.TeamData team) {
        if (team == null || team.alarmTonight) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.alarm_already"), true);
            return false;
        }
        team.alarmTonight = true;
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.alarm_set"), false);
        player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.4F);
        return true;
    }

    private static boolean lure(ServerPlayer player, SixtySecondsState.TeamData team) {
        if (team == null || team.lureTonight) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.lure_already"), true);
            return false;
        }
        team.lureTonight = true;
        player.displayClientMessage(
                Component.translatable("message.noellesroles.sixty_seconds.lure_set"), false);
        return true;
    }
}
