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

public final class ReplayScreenService {
    private static final int LINE_PAUSE_TICKS = 2;
    private static final int SCROLL_TICKS = 8;
    private static final float DISPLAY_VIEW_RANGE = 0.6F;
    private static final double TEXT_OFFSET = 0.58D;
    private static final String NAME_PREFIX = "SRE Replay Screen:";
    private static final String LEGACY_NAME_PREFIX = "SRE Replay Screen: ";
    private static final Map<String, ScrollAnimation> ACTIVE_ANIMATIONS = new HashMap<>();

    private ReplayScreenService() {
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
        List<Component> lines = manager.generateScreenReplayLines(Math.max(6, entry.height() - 2));
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

    private static final class ScrollAnimation {
        private final ReplayScreenSavedData.ReplayScreenEntry screen;
        private final List<Component> lines;
        private final List<Display.TextDisplay> displays = new ArrayList<>();
        private int nextLine;
        private int idleTicks;
        private int pauseTicks;
        private int scrollTicks;
        private boolean scrolling;
        private double yOffset;

        private ScrollAnimation(ReplayScreenSavedData.ReplayScreenEntry screen, List<Component> lines) {
            this.screen = screen;
            this.lines = lines;
        }

        private void tick(ServerLevel level) {
            displays.removeIf(display -> display == null || !display.isAlive());
            int maxRows = visibleRows(screen);
            if (scrolling) {
                scrollTicks++;
                yOffset = Math.min(1.0D, scrollTicks / (double) SCROLL_TICKS);
                updatePositions(maxRows);
                if (yOffset >= 1.0D) {
                    finishScroll();
                    pauseTicks = LINE_PAUSE_TICKS;
                }
                idleTicks = 0;
                return;
            }
            if (nextLine < lines.size()) {
                if (pauseTicks > 0) {
                    pauseTicks--;
                    return;
                }
                if (displays.size() < maxRows) {
                    addLineInstant(level, lines.get(nextLine++), maxRows);
                    pauseTicks = LINE_PAUSE_TICKS;
                } else {
                    startScroll(level, lines.get(nextLine++), maxRows);
                }
                idleTicks = 0;
            } else {
                idleTicks++;
            }
        }

        private void addLineInstant(ServerLevel level, Component line, int maxRows) {
            Display.TextDisplay display = spawnLine(level, screen, line, displays.size(), maxRows);
            displays.add(display);
            updatePositions(maxRows);
            ReplayScreenSavedData.get(level).updateLastTextDisplay(screen.id(), display.getUUID());
        }

        private void startScroll(ServerLevel level, Component line, int maxRows) {
            Display.TextDisplay display = spawnLine(level, screen, line, maxRows, maxRows);
            displays.add(display);
            yOffset = 0.0D;
            scrollTicks = 0;
            scrolling = true;
            updatePositions(maxRows);
            ReplayScreenSavedData.get(level).updateLastTextDisplay(screen.id(), display.getUUID());
        }

        private void finishScroll() {
            if (displays.size() > visibleRows(screen)) {
                Display.TextDisplay oldest = displays.remove(0);
                if (oldest != null) {
                    oldest.discard();
                }
            }
            yOffset = 0.0D;
            scrollTicks = 0;
            scrolling = false;
            updatePositions(visibleRows(screen));
        }

        private void updatePositions(int maxRows) {
            for (int i = 0; i < displays.size(); i++) {
                positionLine(displays.get(i), screen, i - yOffset, maxRows);
            }
        }

        private boolean isDone() {
            return nextLine >= lines.size() && !scrolling && idleTicks > LINE_PAUSE_TICKS;
        }
    }

    private static final class ReplayTextDisplay extends Display.TextDisplay {
        private ReplayTextDisplay(EntityType<?> entityType, Level level) {
            super(entityType, level);
        }
    }
}
