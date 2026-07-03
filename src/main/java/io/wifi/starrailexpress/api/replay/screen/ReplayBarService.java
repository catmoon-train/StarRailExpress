package io.wifi.starrailexpress.api.replay.screen;

import io.wifi.starrailexpress.api.replay.GameReplayManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import com.mojang.math.Transformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ReplayBarService {
    /** 回放屏幕一次最多纳入的内容行数（让视口能滚过完整时间线）。 */
    private static final int SCREEN_MAX_REPLAY_LINES = 500;
    /** 开始向下滚动前的停顿 tick 数（先让玩家看到开头）。 */
    private static final int INITIAL_HOLD_TICKS = 20;
    /** 滚到底部后保持静止的 tick 数，随后结束动画（最后画面保留在屏幕上）。 */
    private static final int FINISH_HOLD_TICKS = 60;
    /** 每 tick 视口向下移动的行数（约每行 1 秒，便于阅读）。 */
    private static final double SCROLL_SPEED_ROWS_PER_TICK = 0.05D;
    private static final float DISPLAY_VIEW_RANGE = 0.6F;
    private static final double TEXT_OFFSET = 0.58D;
    private static final String NAME_PREFIX = "SRE Replay Screen:";
    private static final String LEGACY_NAME_PREFIX = "SRE Replay Screen: ";
    private static final Map<String, ScrollAnimation> ACTIVE_ANIMATIONS = new HashMap<>();

    private ReplayBarService() {
    }

    public static ReplayScreenSavedData.ReplayScreenEntry createScreen(ServerLevel level, String id, BlockPos origin,
            int width, int height, Direction direction) {
        Direction horizontal = normalize(direction);
        ReplayScreenSavedData.ReplayScreenEntry entry = new ReplayScreenSavedData.ReplayScreenEntry(id,
                level.dimension(), origin.immutable(), width, height, horizontal, null);
        buildBackground(level, entry);
        ReplayScreenSavedData.get(level).putScreen(entry, false);
        return entry;
    }

    public static boolean removeScreen(ServerLevel level, String id) {
        ReplayScreenSavedData data = ReplayScreenSavedData.get(level);
        Optional<ReplayScreenSavedData.ReplayScreenEntry> removed = data.removeScreen(id);
        removed.ifPresent(entry -> {
            ServerLevel screenLevel = level.getServer().getLevel(entry.dimension());
            if (screenLevel != null) {
                clearTextDisplay(screenLevel, entry);
            }
        });
        return removed.isPresent();
    }

    public static boolean showDefault(ServerLevel currentLevel, GameReplayManager manager) {
        ReplayScreenSavedData data = ReplayScreenSavedData.get(currentLevel);
        return data.getDefaultScreen().map(entry -> show(currentLevel, entry, manager)).orElse(false);
    }

    public static boolean show(ServerLevel currentLevel, String id, GameReplayManager manager) {
        ReplayScreenSavedData data = ReplayScreenSavedData.get(currentLevel);
        return data.getScreen(id).map(entry -> show(currentLevel, entry, manager)).orElse(false);
    }

    public static boolean show(ServerLevel currentLevel, ReplayScreenSavedData.ReplayScreenEntry entry,
            GameReplayManager manager) {
        ServerLevel level = currentLevel.getServer().getLevel(entry.dimension());
        if (level == null) {
            return false;
        }
        buildBackground(level, entry);
        clearTextDisplay(level, entry);
        // 取完整时间线（不再按屏高截断），让视口能从头滚到尾
        List<Component> lines = manager.generateScreenReplayLines(SCREEN_MAX_REPLAY_LINES);
        ScrollAnimation animation = new ScrollAnimation(entry, lines);
        ACTIVE_ANIMATIONS.put(animationKey(level, entry.id()), animation);
        animation.tick(level);
        return true;
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<String, ScrollAnimation>> iterator = ACTIVE_ANIMATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ScrollAnimation> entry = iterator.next();
            ServerLevel level = server.getLevel(entry.getValue().screen.dimension());
            if (level == null || entry.getValue().isDone()) {
                iterator.remove();
                continue;
            }
            entry.getValue().tick(level);
        }
    }

    public static void buildBackground(ServerLevel level, ReplayScreenSavedData.ReplayScreenEntry entry) {
        BlockPos origin = entry.origin();
//        for (int w = 0; w < entry.width(); w++) {
//            for (int h = 0; h < entry.height(); h++) {
//                BlockPos pos = backgroundPos(origin, entry.direction(), w, h);
//                level.setBlock(pos, Blocks.BLACK_WOOL.defaultBlockState(), Block.UPDATE_ALL);
//            }
//        }
    }

    private static BlockPos backgroundPos(BlockPos origin, Direction direction, int widthOffset, int heightOffset) {
        if (direction.getAxis() == Direction.Axis.Z) {
            return origin.offset(widthOffset, heightOffset, 0);
        }
        return origin.offset(0, heightOffset, widthOffset);
    }

    private static void clearTextDisplay(ServerLevel level, ReplayScreenSavedData.ReplayScreenEntry entry) {
        ACTIVE_ANIMATIONS.remove(animationKey(level, entry.id()));
        UUID entityId = entry.lastTextDisplay();
        if (entityId != null) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                entity.discard();
            }
        }
        String screenName = displayName(entry.id());
        String legacyScreenName = legacyDisplayName(entry.id());
        AABB screenBounds = textCleanupBounds(entry);
        List<Entity> oldDisplays = new ArrayList<>();
        level.getAllEntities().forEach(entity -> {
            if (!(entity instanceof Display.TextDisplay)) {
                return;
            }
            String customName = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
            if (screenName.equals(customName) || legacyScreenName.equals(customName)
                    || screenBounds.contains(entity.position())) {
                oldDisplays.add(entity);
            }
        });
        for (Entity entity : oldDisplays) {
            entity.discard();
        }
        ReplayScreenSavedData.get(level).updateLastTextDisplay(entry.id(), null);
    }

    private static Direction normalize(Direction direction) {
        if (direction == null || direction.getAxis().isVertical()) {
            return Direction.NORTH;
        }
        return direction;
    }

    private static float yawFor(Direction direction) {
        return switch (normalize(direction)) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> 180.0F;
        };
    }

    private static float textScale(ReplayScreenSavedData.ReplayScreenEntry entry) {
        return Math.max(0.35F, Math.min(1.25F, entry.width() / 8.0F));
    }

    private static String animationKey(ServerLevel level, String id) {
        return level.dimension().location() + ":" + id;
    }

    private static String displayName(String id) {
        return NAME_PREFIX + id;
    }

    private static String legacyDisplayName(String id) {
        return LEGACY_NAME_PREFIX + id;
    }

    private static AABB textCleanupBounds(ReplayScreenSavedData.ReplayScreenEntry entry) {
        BlockPos origin = entry.origin();
        double minX = origin.getX();
        double minY = origin.getY();
        double minZ = origin.getZ();
        double maxX = origin.getX() + 1.0D;
        double maxY = origin.getY() + entry.height();
        double maxZ = origin.getZ() + 1.0D;
        if (entry.direction().getAxis() == Direction.Axis.Z) {
            maxX = origin.getX() + entry.width();
        } else {
            maxZ = origin.getZ() + entry.width();
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(3.0D);
    }

    private static Display.TextDisplay spawnLine(ServerLevel level, ReplayScreenSavedData.ReplayScreenEntry entry,
            Component text, double row, int visibleRows) {
        Display.TextDisplay display = new ReplayTextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setText(text);
        display.setNoGravity(true);
        display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        display.setYRot(yawFor(entry.direction()));
        display.setXRot(0.0F);
        display.setViewRange(DISPLAY_VIEW_RANGE);
        display.setLineWidth(Math.max(80, entry.width() * 40));
        display.setBackgroundColor(0x00000000);
        display.setTransformation(new Transformation(new Matrix4f().scale(textScale(entry))));
        positionLine(display, entry, row, visibleRows);
        display.setCustomName(Component.literal(displayName(entry.id())).withStyle(ChatFormatting.GRAY));
        display.setCustomNameVisible(false);
        level.addFreshEntity(display);
        return display;
    }

    private static void positionLine(Display.TextDisplay display, ReplayScreenSavedData.ReplayScreenEntry entry, double row,
            int visibleRows) {
        BlockPos origin = entry.origin();
        double x = origin.getX() + 0.5D;
        double y = origin.getY() + entry.height() - 0.65D - row * lineSpacing(entry, visibleRows);
        double z = origin.getZ() + 0.5D;
        if (entry.direction().getAxis() == Direction.Axis.Z) {
            x += (entry.width() - 1) / 2.0D;
            z += entry.direction().getStepZ() * TEXT_OFFSET;
        } else {
            x += entry.direction().getStepX() * TEXT_OFFSET;
            z += (entry.width() - 1) / 2.0D;
        }
        display.moveTo(x, y, z, yawFor(entry.direction()), 0.0F);
    }

    private static double lineSpacing(ReplayScreenSavedData.ReplayScreenEntry entry, int visibleRows) {
        if (visibleRows <= 1) {
            return 0.42D;
        }
        double readableSpacing = 0.24D + textScale(entry) * 0.22D;
        double fittingSpacing = Math.max(0.24D, (entry.height() - 0.8D) / visibleRows);
        return Math.min(readableSpacing, fittingSpacing);
    }

    private static int visibleRows(ReplayScreenSavedData.ReplayScreenEntry entry) {
        return Math.max(2, entry.height() - 1);
    }

    /**
     * 回放屏幕滚动动画：视口从顶部开始，随时间向下滚过完整事件时间线，
     * 直到最后一条内容落到屏幕底部后停止（画面保留，不循环、不移出）。
     */
    private static final class ScrollAnimation {
        private final ReplayScreenSavedData.ReplayScreenEntry screen;
        private final List<Component> lines;
        // 行号 -> 当前活动的文本展示实体（仅维护视口内的少量实体）
        private final Map<Integer, Display.TextDisplay> active = new HashMap<>();
        private double scroll;
        private int initialHold = INITIAL_HOLD_TICKS;
        private int finishHold;
        private boolean reachedBottom;

        private ScrollAnimation(ReplayScreenSavedData.ReplayScreenEntry screen, List<Component> lines) {
            this.screen = screen;
            this.lines = lines;
        }

        private double maxScroll(int visibleRows) {
            return Math.max(0.0D, lines.size() - (double) visibleRows);
        }

        private void tick(ServerLevel level) {
            int visibleRows = visibleRows(screen);
            double maxScroll = maxScroll(visibleRows);
            if (initialHold > 0) {
                initialHold--;
            } else if (scroll < maxScroll) {
                scroll = Math.min(maxScroll, scroll + SCROLL_SPEED_ROWS_PER_TICK);
                if (scroll >= maxScroll) {
                    reachedBottom = true;
                }
            } else {
                reachedBottom = true;
                finishHold++;
            }
            render(level, visibleRows);
        }

        private void render(ServerLevel level, int visibleRows) {
            int lo = Math.max(0, (int) Math.floor(scroll));
            int hi = Math.min(lines.size() - 1, lo + visibleRows);
            // 回收离开视口（或已失效）的展示实体
            Iterator<Map.Entry<Integer, Display.TextDisplay>> iterator = active.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Display.TextDisplay> entry = iterator.next();
                int index = entry.getKey();
                Display.TextDisplay display = entry.getValue();
                if (index < lo || index > hi || display == null || !display.isAlive()) {
                    if (display != null && display.isAlive()) {
                        display.discard();
                    }
                    iterator.remove();
                }
            }
            // 生成 / 更新视口内的展示实体
            for (int index = lo; index <= hi; index++) {
                if (index < 0 || index >= lines.size()) {
                    continue;
                }
                double row = index - scroll;
                Display.TextDisplay display = active.get(index);
                if (display == null || !display.isAlive()) {
                    display = spawnLine(level, screen, lines.get(index), row, visibleRows);
                    active.put(index, display);
                    ReplayScreenSavedData.get(level).updateLastTextDisplay(screen.id(), display.getUUID());
                } else {
                    positionLine(display, screen, row, visibleRows);
                }
            }
        }

        private boolean isDone() {
            return reachedBottom && finishHold > FINISH_HOLD_TICKS;
        }
    }

    private static final class ReplayTextDisplay extends Display.TextDisplay {
        private ReplayTextDisplay(EntityType<?> entityType, Level level) {
            super(entityType, level);
        }
    }
}
