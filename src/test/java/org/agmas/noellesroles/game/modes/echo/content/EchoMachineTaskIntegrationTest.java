package org.agmas.noellesroles.game.modes.echo.content;

import com.google.gson.JsonParser;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** 多格机器的整机距离与透视接口回归；不启动 Minecraft 注册表或世界。 */
class EchoMachineTaskIntegrationTest {
    private String source(String relative) throws Exception { return Files.readString(Path.of("src/main/java",relative)); }
    @Test void standingBesideFarCornerUsesHullDistanceNotAnchorDistance() {
        var box=new AABB(100,64,100,103,66,103);
        var eye=new Vec3(103.5,65.6,102.5);
        var closest=EchoMachineInteractions.closest(box,eye);
        assertTrue(eye.distanceToSqr(closest)<1);
        assertTrue(eye.distanceToSqr(new Vec3(100.5,64.5,100.5))>9);
        assertTrue(box.contains(closest));
        assertEquals(65.6,closest.y,1e-6);
    }
    @Test void everyPhysicalMachineHasExactlyOneTaskAnchor() {
        for(var kind:EchoMachines.Kind.values()) {
            long roots=EchoMachineStructure.cells(kind).stream().filter(c -> EchoMachineTasks.anchorPart(c.part())).count();
            assertEquals(1,roots,kind.name());
            assertTrue(EchoMachineTasks.color(kind)>0 && EchoMachineTasks.color(kind)<=0xFFFFFF);
        }
    }
    @Test void scannerAndRendererUseGenericMultiblockInterfaceInsteadOfNewRenderingPipeline() throws Exception {
        var api=source("io/wifi/starrailexpress/content/block/api/TaskInstinctShowableInterface.java");
        assertTrue(api.contains("default boolean isTaskInstinctAnchor"));
        assertTrue(api.contains("default AABB taskInstinctBounds"));
        var scanner=source("org/agmas/noellesroles/utils/MapScanner.java");
        assertTrue(scanner.contains("if (!it.isTaskInstinctAnchor(blockState)) return;"));
        var renderer=source("org/agmas/noellesroles/client/TaskBlockOverlayRenderer.java");
        assertTrue(renderer.contains("task.taskInstinctBounds(world, state, blockPos)"));
        var block=source("org/agmas/noellesroles/game/modes/echo/content/EchoMachineBlock.java");
        assertTrue(block.contains("implements TaskInstinctShowableInterface"));
        assertTrue(block.contains("EchoMachineTasks.TYPE"));
    }
    @Test void interactionAndEditorNormalizePartsAndPreflightChecksCompleteFootprint() throws Exception {
        var objective=source("org/agmas/noellesroles/game/modes/echo/objective/EchoObjectiveService.java");
        assertTrue(objective.contains("targets.get(EchoMachineInteractions.root"));
        assertTrue(objective.contains("EchoMachineInteractions.reachable"));
        assertTrue(objective.contains("new org.agmas.noellesroles.packet.ScanAllTaskPointsPayload(finished?new HashMap<>():taskPoints())"));
        assertTrue(source("org/agmas/noellesroles/game/modes/echo/editor/EchoEditorService.java").contains("EchoMachineInteractions.root(player.serverLevel(),ray(player))"));
        var generator=source("org/agmas/noellesroles/game/modes/echo/map/manor/EchoManorGenerator.java");
        assertTrue(generator.contains("writeMachines(current,current.templateOrigin,false)"));
        assertTrue(generator.contains("writeMachines(current,current.pasteOrigin,true)"));
        assertTrue(generator.contains("EchoMachineStructure.mutate"));
        var preflight=source("org/agmas/noellesroles/game/modes/echo/runtime/EchoPreflight.java");
        assertTrue(preflight.contains("EchoMachineStructure.complete(level,pos)"));
        assertTrue(preflight.contains("invalid/overlapping machine footprint"));
    }
    @Test void filterAndIncompleteStructureMessagesAreTranslated() throws Exception {
        Set<String> keys=null;
        for(var language:new String[]{"zh_cn","zh_tw","en_us"}) {
            var data=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/echo_machine_tasks/lang/"+language+".json"))).getAsJsonObject();
            if(keys==null) keys=Set.copyOf(data.keySet()); else assertEquals(keys,data.keySet());
            assertTrue(data.has("echo.machine.tasks.filter")); assertTrue(data.has("echo.machine.tasks.incomplete"));
        }
        assertEquals(26,EchoMachineTasks.TYPE);
    }
}
