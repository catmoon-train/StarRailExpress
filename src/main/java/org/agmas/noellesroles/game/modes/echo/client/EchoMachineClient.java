package org.agmas.noellesroles.game.modes.echo.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachineBlock;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachines;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachineInteractions;
import org.agmas.noellesroles.game.modes.echo.objective.EchoMachinePayload;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoMachineView;

/** 机器前方单行浮字与紧凑 HUD；只展示服务器事实，不代替交互或预测成功。 */
public final class EchoMachineClient {
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new ByteBufferBuilder(4096));
    private static EchoMachinePayload view;
    private static int age;
    private EchoMachineClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoMachinePayload.ID, (p, c) -> c.client().execute(() -> { view = p; age = 0; }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (view != null && (client.player == null || client.level == null || ++age > 25
                    || !view.dimension().equals(client.level.dimension().location()) || EchoHud.view() == null
                    || !view.match().equals(EchoHud.view().match()))) view = null;
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            var client = Minecraft.getInstance();
            var machine = current();
            if (machine == null || context.matrixStack() == null) return;
            Component text = Component.translatable("echo.machine.world", Component.translatable(machine.type().nameKey()), instruction(machine));
            // 文字锚在机壳正面上缘，不创建实体；NORMAL 深度测试保证不会隔墙显示。
            var box = EchoMachineInteractions.bounds(client.level,new BlockPos(machine.x(),machine.y(),machine.z()));
            Vec3 pos = new Vec3((box.minX+box.maxX)/2,box.maxY+0.16,(box.minZ+box.maxZ)/2);
            var state = client.level.getBlockState(new BlockPos(machine.x(), machine.y(), machine.z()));
            if (state.hasProperty(EchoMachineBlock.FACING)) {
                var front = state.getValue(EchoMachineBlock.FACING);
                double depth = front.getStepX()!=0 ? box.getXsize() : box.getZsize();
                pos = pos.add(front.getStepX()*(depth/2+0.06),0,front.getStepZ()*(depth/2+0.06));
            }
            var camera = context.camera().getPosition(); var pose = context.matrixStack();
            float scale = Math.min(0.019F, 3F / Math.max(1, client.font.width(text)));
            pose.pushPose(); pose.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
            pose.mulPose(client.getEntityRenderDispatcher().cameraOrientation()); pose.scale(-scale, -scale, scale);
            client.font.drawInBatch(text, -client.font.width(text) / 2F, 0, color(machine), false,
                    pose.last().pose(), BUFFER, Font.DisplayMode.NORMAL, 0xC01A1008, LightTexture.FULL_BRIGHT);
            pose.popPose(); BUFFER.endBatch();
        });
    }

    /** 接收快照后继续做本地距离、视线与方向门控，转身或走开无需等下个网络周期。 */
    private static EchoMachineView current() {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.player.isSpectator() || client.options.hideGui || client.screen != null) return null;
        EchoMachineView machine = null;
        var own = EchoBagClient.data();
        if (view != null && age <= 25 && EchoHud.view() != null && view.match().equals(EchoHud.view().match())
                && client.level.dimension().location().equals(view.dimension()) && own != null && view.match().equals(own.matchId())
                && own.life().potentiallyEscapable()) machine = view.machine();
        // 制图时也能辨认机器，但明确说明：放下外壳不会自动注册玩法点位。
        if (machine == null && EchoHud.view() == null && client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var root = EchoMachineInteractions.root(client.level,hit.getBlockPos());
            var kind = EchoMachines.kind(client.level.getBlockState(root));
            if (kind != null) machine = new EchoMachineView(root.getX(),root.getY(),root.getZ(),
                    EchoMachineView.Type.valueOf(kind.name()), "", EchoMachineView.Status.UNCONFIGURED, 0, 0, 0, 0);
        }
        if (machine == null) return null;
        var pos = new BlockPos(machine.x(), machine.y(), machine.z());
        if (!client.level.hasChunkAt(pos) || client.level.getBlockState(pos).isAir()) return null;
        Vec3 eye = client.player.getEyePosition(), center = EchoMachineInteractions.aim(client.level,Vec3.atCenterOf(pos),eye), delta = center.subtract(eye);
        if (delta.lengthSqr() > 4.5 * 4.5 || client.player.getLookAngle().dot(delta.normalize()) < 0.25) return null;
        var hit = client.level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        return hit.getType() == HitResult.Type.MISS || EchoMachineInteractions.root(client.level,hit.getBlockPos()).equals(pos) ? machine : null;
    }

    private static Component instruction(EchoMachineView machine) {
        var client = Minecraft.getInstance();
        if (machine.status() != EchoMachineView.Status.READY) {
            if (machine.status() == EchoMachineView.Status.OVERLOAD_READY)
                return Component.translatable("echo.machine.action.overload", NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
            if (machine.status() == EchoMachineView.Status.NO_RESOURCE) {
                String resource = switch (machine.type()) {
                    case CORE_CONSOLE -> "need_key";
                    case BLACK_DROPOFF -> "need_black";
                    case BEACON, CORE_BEACON -> EchoBagClient.data() != null && EchoBagClient.data().faction() == EchoFaction.INTERCEPTOR ? "need_charge" : "need_crystal";
                    default -> "no_resource";
                };
                return Component.translatable("echo.machine.status." + resource);
            }
            boolean paused = EchoHud.view() != null && EchoHud.view().state().paused();
            if (machine.remaining() > 0 && (machine.status() == EchoMachineView.Status.COOLDOWN || machine.status() == EchoMachineView.Status.STALLED || machine.status() == EchoMachineView.Status.CORE_OPEN))
                return Component.translatable("echo.machine.timer", Component.translatable(machine.status().key()), machine.seconds(age, paused));
            return Component.translatable(machine.status().key());
        }
        String action = switch (machine.type()) {
            case CRYSTAL, BLACK_NODE -> "collect";
            case ALTAR -> "key";
            case SUPPLY, BLACK_MARKET -> "search";
            case CORE_CONSOLE -> "unlock";
            case FREE_EXIT -> "leave";
            case BLACK_DROPOFF -> "deliver_black";
            case BEACON, CORE_BEACON -> EchoBagClient.data() != null && EchoBagClient.data().faction() == EchoFaction.INTERCEPTOR ? "stall" : "submit";
            default -> "interact";
        };
        return Component.translatable("echo.machine.action." + action, client.options.keyUse.getTranslatedKeyMessage());
    }

    /** 在主 HUD 的信息流中占位，空间不够时只留世界浮字，绝不压住底部库存/引导条。 */
    static int renderHud(FakeGuiGraphics graphics, int x, int y, int width, int limit) {
        var machine = current();
        if (machine == null || y + 31 > limit || width < 120) return y;
        // 回调只在脏 tick 执行；必须入帧缓存，直接使用真实 GuiGraphics 会在中间帧消失。
        var g = graphics;
        int accent = color(machine);
        g.fillGradient(x, y, x + width, y + 29, 0xEB1A1008, 0xEB20140A);
        g.renderOutline(x, y, width, 29, 0xFF8B6914);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0x33FFE8C0);
        g.fill(x + 1, y + 2, x + 3, y + 28, accent);
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("noellesroles", machine.type().path()));
        g.renderItem(new ItemStack(item), x + 5, y + 5);
        Component title = Component.translatable(machine.type().nameKey());
        if (machine.required() > 0) title = Component.translatable("echo.machine.beacon", title, machine.amount(), machine.required(), machine.progress() / 10);
        fit(g, title, x + 25, y + 5, width - 30, 0xFFD4AF37);
        fit(g, instruction(machine), x + 25, y + 16, width - 30, accent);
        if (machine.required() > 0) {
            g.fill(x + 4, y + 26, x + width - 3, y + 28, 0xFF49341E);
            g.fill(x + 4, y + 26, x + 4 + (width - 7) * machine.progress() / 1000, y + 28, accent);
        }
        return y + 32;
    }

    private static int color(EchoMachineView machine) {
        return machine.status().actionable() || machine.status() == EchoMachineView.Status.ACTIVE ? 0xFF72C17B
                : switch (machine.status()) {
                    case CHARGING, CORE_OPEN -> 0xFF5EB7D8;
                    case COOLDOWN, EMPTY, EVAC_WAIT, RETIRE_WAIT, UNCONFIGURED -> 0xFFC8B898;
                    default -> 0xFFE0AD5B;
                };
    }
    private static void fit(FakeGuiGraphics g, Component text, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1F, width / (float) Math.max(1, font.width(text)));
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false); g.pose().popPose();
    }
}
