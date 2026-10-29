package org.agmas.noellesroles.game.modes.echo.map;

import java.util.*;

/** 有界整数地面图；只走四邻接、最多一格高差，反向图校验回程，不把直线距离当作路径。 */
public final class EchoWalkGraph {
    public record Cell(int x,int y,int z) {
        public Cell add(int dx,int dy,int dz) { return new Cell(x+dx,y+dy,z+dz); }
    }
    public interface Terrain {
        boolean stand(Cell cell);
        boolean clear(Cell cell);
    }
    public enum Status { RUNNING, COMPLETE, LIMIT }
    private static final int[][] DIRECTIONS={{1,0},{-1,0},{0,1},{0,-1}};
    private final Terrain terrain;
    private final boolean jumping,reverse;
    private final int maxVisited,maxProbes;
    private final Set<Cell> visited=new HashSet<>();
    private final Map<Cell,Boolean> stands=new HashMap<>();
    private final ArrayDeque<Cell> queue=new ArrayDeque<>();
    private int probes;
    private Status status=Status.RUNNING;
    public EchoWalkGraph(Terrain terrain,Cell start,boolean jumping,boolean reverse,int maxVisited,int maxProbes) {
        if(maxVisited<1 || maxProbes<1) throw new IllegalArgumentException("路线预算必须大于0");
        this.terrain=terrain; this.jumping=jumping; this.reverse=reverse; this.maxVisited=maxVisited; this.maxProbes=maxProbes;
        if(stand(start)) { visited.add(start); queue.add(start); } else if(status!=Status.LIMIT) status=Status.COMPLETE;
    }
    private boolean stand(Cell cell) {
        Boolean cached=stands.get(cell); if(cached!=null) return cached;
        if(++probes>maxProbes) { status=Status.LIMIT; return false; }
        boolean value=terrain.stand(cell); stands.put(cell,value); return value;
    }
    private boolean clear(Cell cell) {
        if(++probes>maxProbes) { status=Status.LIMIT; return false; }
        return terrain.clear(cell);
    }
    private boolean move(Cell from,Cell to) {
        int dy=to.y()-from.y();
        if(dy>0) return jumping && clear(from.add(0,1,0));
        if(dy<0) return clear(to.add(0,1,0));
        return true;
    }
    public Status advance(int nodes) {
        if(nodes<1) throw new IllegalArgumentException("步进预算必须大于0");
        for(int n=0;n<nodes && status==Status.RUNNING;n++) {
            Cell current=queue.poll(); if(current==null) { status=Status.COMPLETE; break; }
            for(int[] d:DIRECTIONS) for(int dy=-1;dy<=1;dy++) {
                Cell next=current.add(d[0],dy,d[1]);
                if(visited.contains(next) || !stand(next)) continue;
                if(!(reverse?move(next,current):move(current,next))) continue;
                if(visited.size()>=maxVisited) { status=Status.LIMIT; break; }
                visited.add(next); queue.add(next);
            }
        }
        if(status==Status.RUNNING && queue.isEmpty()) status=Status.COMPLETE;
        return status;
    }
    public Status status() { return status; }
    public int visitedCount() { return visited.size(); }
    public int probes() { return probes; }
    public boolean reached(Cell cell) { return visited.contains(cell); }
    public Set<Cell> reached() { return Collections.unmodifiableSet(visited); }
}
