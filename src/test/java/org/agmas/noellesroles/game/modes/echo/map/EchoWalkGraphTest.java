package org.agmas.noellesroles.game.modes.echo.map;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoWalkGraphTest {
    private EchoWalkGraph.Cell c(int x,int y,int z) { return new EchoWalkGraph.Cell(x,y,z); }
    private EchoWalkGraph graph(Set<EchoWalkGraph.Cell> ground,Set<EchoWalkGraph.Cell> blocked,boolean jump,boolean reverse,EchoWalkGraph.Cell root) {
        return new EchoWalkGraph(new EchoWalkGraph.Terrain() {
            public boolean stand(EchoWalkGraph.Cell p) { return ground.contains(p); }
            public boolean clear(EchoWalkGraph.Cell p) { return !blocked.contains(p); }
        },root,jump,reverse,1000,10000);
    }
    private void finish(EchoWalkGraph graph) { while(graph.status()==EchoWalkGraph.Status.RUNNING) graph.advance(2); }
    @Test void cardinalWalkFindsDetourButCannotCrossWallsOrDiagonalGaps() {
        var root=c(0,64,0); var target=c(2,64,0); var ground=new HashSet<>(Set.of(root,target,c(0,64,1),c(1,64,1),c(2,64,1)));
        var graph=graph(ground,Set.of(),false,false,root); finish(graph); assertTrue(graph.reached(target));
        ground.remove(c(1,64,1)); graph=graph(ground,Set.of(),false,false,root); finish(graph); assertFalse(graph.reached(target));
    }
    @Test void jumpingIsRequiredForOneBlockRiseAndLowCeilingPreventsIt() {
        var root=c(0,64,0); var high=c(1,65,0); var ground=Set.of(root,high);
        var noJump=graph(ground,Set.of(),false,false,root); finish(noJump); assertFalse(noJump.reached(high));
        var jump=graph(ground,Set.of(),true,false,root); finish(jump); assertTrue(jump.reached(high));
        var ceiling=graph(ground,Set.of(root.add(0,1,0)),true,false,root); finish(ceiling); assertFalse(ceiling.reached(high));
    }
    @Test void reverseGraphRejectsOneWayDropAndNoTwoBlockJumpOrFall() {
        var high=c(0,65,0); var low=c(1,64,0); var ground=Set.of(high,low,c(2,62,0));
        var forward=graph(ground,Set.of(),false,false,high); finish(forward); assertTrue(forward.reached(low)); assertFalse(forward.reached(c(2,62,0)));
        var reverse=graph(ground,Set.of(),false,true,high); finish(reverse); assertFalse(reverse.reached(low));
        var jump=graph(ground,Set.of(),true,true,high); finish(jump); assertTrue(jump.reached(low));
    }
    @Test void aDropRequiresClearanceAtOriginalHeadHeight() {
        var high=c(0,65,0); var low=c(1,64,0);
        var graph=graph(Set.of(high,low),Set.of(low.add(0,1,0)),false,false,high); finish(graph); assertFalse(graph.reached(low));
    }
    @Test void budgetsBoundUnboundedTerrainAndYieldBetweenFrames() {
        var graph=new EchoWalkGraph(new EchoWalkGraph.Terrain() { public boolean stand(EchoWalkGraph.Cell p) { return p.y()==64; } public boolean clear(EchoWalkGraph.Cell p) { return true; } },c(0,64,0),false,false,20,2000);
        assertEquals(EchoWalkGraph.Status.RUNNING,graph.advance(1)); assertTrue(graph.visitedCount()<=5); finish(graph);
        assertEquals(EchoWalkGraph.Status.LIMIT,graph.status()); assertEquals(20,graph.visitedCount());
        var probes=new EchoWalkGraph(new EchoWalkGraph.Terrain() { public boolean stand(EchoWalkGraph.Cell p) { return true; } public boolean clear(EchoWalkGraph.Cell p) { return true; } },c(0,0,0),true,false,1000,4);
        finish(probes); assertEquals(EchoWalkGraph.Status.LIMIT,probes.status()); assertTrue(probes.visitedCount()<=4);
    }
    @Test void invalidRootDoesNotInventReachabilityAndViewsCannotMutateGraph() {
        var graph=graph(Set.of(),Set.of(),true,false,c(0,0,0)); assertEquals(EchoWalkGraph.Status.COMPLETE,graph.status()); assertEquals(0,graph.visitedCount());
        assertThrows(UnsupportedOperationException.class,() -> graph.reached().add(c(0,0,0)));
    }
}
