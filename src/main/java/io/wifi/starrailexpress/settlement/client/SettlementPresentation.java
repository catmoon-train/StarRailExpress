package io.wifi.starrailexpress.settlement.client;

import io.wifi.starrailexpress.settlement.SettlementSnapshot;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 漫画分镜的纯计算层：语义、镜头、排版共用同一时钟，不依赖客户端注册表。 */
public final class SettlementPresentation {
    public enum Action { STILL, PURCHASE, INTERACT, STRIKE, THROW, CAST, TRANSFORM }
    public enum Category { PURCHASE, INTERACTION, CONFLICT, CHANGE, STORY }
    public record Cue(int tick, UUID primary, UUID secondary, Category category) { }
    public record Frame(double time, double detail, double hero, double impact, double orbit,
                        double roll, UUID primary, UUID secondary, Category category, boolean closing) { }
    public record Rect(double x, double y, double w, double h) {
        public double right() { return x + w; }
        public double bottom() { return y + h; }
        public double cx() { return x + w / 2; }
        public double cy() { return y + h / 2; }
        public boolean overlaps(Rect other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
        public Rect grow(double pad) { return new Rect(x - pad, y - pad, w + pad * 2, h + pad * 2); }
        public boolean contains(Rect r) { return r.x >= x && r.y >= y && r.right() <= right() && r.bottom() <= bottom(); }
    }
    public record Layout(int width, int height, float ui, Rect safe, Rect caption, Rect data, boolean stacked) { }
    public record HeroLayout(Rect portrait, Rect info, boolean stacked) { }
    public record HeroText(Rect award, Rect name, Rect role, Rect stats, Rect statLabels, Rect victory,
                           float roleScale, float victoryScale) { }
    public record Motion(double windup, double hit, double recover) { }
    public record Bounds(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Bounds union(Bounds b) {
            return new Bounds(Math.min(x0, b.x0), Math.min(y0, b.y0), Math.min(z0, b.z0),
                    Math.max(x1, b.x1), Math.max(y1, b.y1), Math.max(z1, b.z1));
        }
        public Bounds blend(Bounds b, double t) {
            return new Bounds(lerp(t,x0,b.x0),lerp(t,y0,b.y0),lerp(t,z0,b.z0),
                    lerp(t,x1,b.x1),lerp(t,y1,b.y1),lerp(t,z1,b.z1));
        }
        public Vector3d center() { return new Vector3d((x0+x1)/2, (y0+y1)/2, (z0+z1)/2); }
        public List<Vector3d> corners() {
            List<Vector3d> result = new ArrayList<>(8);
            for (int i = 0; i < 8; i++) result.add(new Vector3d((i&1)==0?x0:x1,(i&2)==0?y0:y1,(i&4)==0?z0:z1));
            return result;
        }
    }
    public record Camera(Vector3d eye, Vector3d look, double fov, double roll) { }

    private SettlementPresentation() { }

    public static Action action(String type) {
        if (type == null) return Action.STILL;
        return switch (type) {
            case "STORE_BUY" -> Action.PURCHASE;
            case "TASK_COMPLETE", "DOOR_LOCK", "DOOR_UNLOCK", "DOOR_OPEN", "DOOR_CLOSE", "LOCKPICK_ATTEMPT",
                    "DOOR_PRY", "DOOR_SEAL", "BOMB_DEFUSE", "ITEM_USED" -> Action.INTERACT;
            case "PLAYER_KILL", "ARMOR_BREAK", "TRAP_TRIGGERED", "ROPE_PULL" -> Action.STRIKE;
            case "GRENADE_THROWN", "BOMB_DETONATE" -> Action.THROW;
            case "SKILL_RELEASE", "PLAYER_POISONED" -> Action.CAST;
            case "CHANGE_ROLE", "PLAYER_REVIVAL", "DISGUISE" -> Action.TRANSFORM;
            default -> Action.STILL;
        };
    }

    public static Category category(String type) {
        return switch (action(type)) {
            case PURCHASE -> Category.PURCHASE;
            case INTERACT -> Category.INTERACTION;
            case STRIKE, THROW, CAST -> Category.CONFLICT;
            case TRANSFORM -> Category.CHANGE;
            default -> Category.STORY;
        };
    }

    /** 只预编排关键镜头，所有事件仍按原时间线播放；镜头间隔至少 40 tick。 */
    public static List<Cue> cues(SettlementSnapshot snapshot) {
        List<Cue> result = new ArrayList<>();
        int last = -1000;
        for (int i = 0; i < snapshot.events().size(); i++) {
            var event = snapshot.events().get(i);
            Category category = category(event.type());
            int tick = SettlementSnapshot.INTRO_TICKS + i * snapshot.eventTicks();
            if ((category != Category.CONFLICT && category != Category.CHANGE) || tick - last < 40) continue;
            // 优先聚焦仍站立的胜者，后台目标最多作为第二构图点，不飞向空中的尸体。
            UUID primary = winner(snapshot, event.source()) ? event.source()
                    : winner(snapshot, event.target()) ? event.target() : snapshot.mvp();
            UUID secondary = winner(snapshot, event.target()) ? event.target() : null;
            if (primary == null) continue;
            result.add(new Cue(tick, primary, primary.equals(secondary) ? null : secondary, category));
            last = tick;
        }
        return List.copyOf(result);
    }

    private static boolean winner(SettlementSnapshot snapshot, UUID id) {
        return id != null && snapshot.actors().stream().anyMatch(a -> a.winner() && a.id().equals(id));
    }

    public static Frame frame(SettlementSnapshot snapshot, List<Cue> cues, double time) {
        boolean closing = time >= snapshot.durationTicks() - 80;
        double hero = smooth((time-202)/14) * (1-smooth((time-304)/16));
        double detail = time < 320 ? hero : .94;
        UUID primary = snapshot.mvp(), secondary = null;
        Category category = Category.STORY;
        double impact = pulse(time,132,13)*.45 + pulse(time,220,16)*.90;
        double orbit = -.16*(1-smooth((time-35)/105)) + .12*hero*(1-smooth((time-230)/16));
        double roll = -.018*hero;
        // 二分查找，不在每帧遍历大量回放。
        int low = 0, high = cues.size();
        while (low < high) { int mid = (low+high)>>>1; if (cues.get(mid).tick() <= time) low=mid+1; else high=mid; }
        if (time >= 320 && low > 0) {
            Cue cue = cues.get(low-1);
            double age = time-cue.tick();
            double active = 1-smooth((age-25)/14);
            if (active > 0) {
                primary = cue.primary(); secondary = cue.secondary(); category = cue.category();
                detail = lerp(easeOut((age-4)/6)*active, .94, 1);
                orbit = .15*((low&1)==0?1:-1)*easeOut(age/8)*active;
                impact = category == Category.CONFLICT ? pulse(time,cue.tick()+7,11)*.85 : 0;
                roll = orbit*.22;
            }
        }
        if (closing) { detail *= 1-smooth((time-snapshot.durationTicks()+80)/24); impact=0; roll=0; }
        return new Frame(time, detail, hero, impact, orbit, roll, primary, secondary, category, closing);
    }

    /** 海报使用单独构图，不受阵容人数影响；窄屏把文字移到下方。 */
    public static HeroLayout heroLayout(int width,int height) {
        boolean stacked=width/(double)Math.max(1,height)<1.4 || width<420;
        return stacked ? new HeroLayout(new Rect(width*.14,height*.045,width*.80,height*.60),
                new Rect(width*.07,height*.655,width*.86,height*.31),true)
                : new HeroLayout(new Rect(width*.43,height*.045,width*.56,height*.94),
                new Rect(width*.055,height*.25,width*.36,height*.64),false);
    }

    /** 职业与胜负文案占独立轨道；窄屏也保留获胜文字，字号扩大后不会挤到贡献统计。 */
    public static HeroText heroText(HeroLayout poster, float ui) {
        Rect r=poster.info();
        if (poster.stacked()) {
            Rect role=new Rect(r.x+r.w*.52,r.y+r.h*.15,r.w*.48,r.h*.14);
            Rect victory=new Rect(r.x,r.y+r.h*.76,r.w,r.h*.20);
            return new HeroText(new Rect(r.x,r.y,r.w,r.h*.13),
                    new Rect(r.x,r.y+r.h*.15,r.w*.48,r.h*.14),role,
                    new Rect(r.x,r.y+r.h*.36,r.w,r.h*.22),new Rect(r.x,r.y+r.h*.59,r.w,r.h*.12),victory,
                    (float)Math.min(1.20*ui,role.h/9),(float)Math.min(1.35*ui,victory.h/9));
        }
        Rect role=new Rect(r.x,r.y+r.h*.40,r.w,r.h*.15);
        Rect victory=new Rect(r.x,r.y+r.h*.91,r.w,r.h*.09);
        return new HeroText(new Rect(r.x,r.y,r.w,r.h*.19),
                new Rect(r.x,r.y+r.h*.26,r.w,r.h*.12),role,
                new Rect(r.x,r.y+r.h*.65,r.w,r.h*.15),new Rect(r.x,r.y+r.h*.80,r.w,r.h*.07),victory,
                (float)Math.min(1.55*ui,role.h/9),(float)Math.min(1.55*ui,victory.h/9));
    }

    /** 近景屏风只在纵向升起，位置不依赖当前焦点，切换人物不再拖动整块背板。 */
    public static double backdropRise(double detail) {
        return -3.75*(1-smooth((detail-.40)/.28));
    }

    /** 冲突手势先后拉、再快速到位并停一拍；不会把一个正弦波当成所有动作。 */
    public static Motion motion(Action action,double age) {
        boolean conflict=action==Action.STRIKE||action==Action.THROW||action==Action.CAST;
        if(!conflict) return new Motion(0,smooth(age/6)*(1-smooth((age-13)/9)),smooth((age-14)/8));
        return new Motion(smooth(age/4)*(1-smooth((age-6)/2)),
                easeOut((age-6)/3)*(1-smooth((age-13)/9)),smooth((age-14)/8));
    }

    public static double easeOut(double value) {double t=1-clamp(value,0,1);return 1-t*t*t;}

    /** 正文字号固定，旁白高度按行数增长并封顶，超出部分由调用方裁剪滚动。 */
    public static Layout layout(int width, int height, boolean hero, boolean replay, int lines) {
        float ui = (float) clamp(Math.min(width/720.0, height/405.0), .72, 1.5);
        double margin = Math.max(10, 22*ui), top = (hero||replay?32:104)*ui, footer = 22*ui;
        double captionW = Math.min(width-margin*2, 580*ui);
        double captionH = replay ? Math.min(height*.20, (24+Math.max(1, lines)*12)*ui) : 0;
        Rect caption = new Rect((width-captionW)/2, height-footer-captionH, captionW, captionH);
        boolean stacked = width/(double)Math.max(1,height) < 1.4 || width < 420;
        double dataW = stacked ? Math.min(width-margin*2, 300*ui) : Math.min(width*.30, 220*ui);
        double dataH = (stacked ? 82 : 140)*ui;
        Rect data = new Rect(stacked ? (width-dataW)/2 : margin,
                stacked ? height-footer-dataH : height*.30, dataW, dataH);
        double safeLeft = hero && !stacked ? data.right()+18*ui : margin;
        double safeBottom = hero && stacked ? data.y-12*ui : replay ? caption.y-10*ui : height-footer-8*ui;
        return new Layout(width,height,ui,new Rect(safeLeft,top,width-margin-safeLeft,
                Math.max(30,safeBottom-top)),caption,data,stacked);
    }

    /** 镜头和渲染共用吊线入场高度，避免按落地包围盒拍摄还在上空的人物。 */
    public static double winnerLift(double time, int order) {
        return 5.4 * (1 - smooth((time - 65 - Math.min(12, Math.max(0, order) * 2)) / 65));
    }

    public static Bounds winnerBounds(double x, double z, int order, double time) {
        double y = winnerLift(time, order);
        return new Bounds(x-.82,y,z-.65,x+.82,y+2.5,z+.65);
    }

    /** 以八个包围盒角点在机位基底中的范围拟合取景；道具、皇冠和双臂均由调用方纳入包围盒。 */
    public static Camera camera(Bounds bounds, Layout layout, Frame frame) {
        double aspect = layout.width/(double)layout.height;
        double pitch = lerp(frame.detail, .17, -.018), yaw = frame.orbit;
        Vector3d forward = new Vector3d(Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        Vector3d right = new Vector3d(forward).cross(0,1,0).normalize();
        Vector3d up = new Vector3d(right).cross(forward).normalize();
        Vector3d center = bounds.center();
        double extentX=0,extentY=0,depth=0;
        for (Vector3d corner : bounds.corners()) {
            Vector3d d=corner.sub(center);
            extentX=Math.max(extentX,Math.abs(d.dot(right)));
            extentY=Math.max(extentY,Math.abs(d.dot(up)));
            depth=Math.max(depth,Math.abs(d.dot(forward)));
        }
        // 取景边距给名签和镜头缓动留下空间；特写人物约占安全画格高度的 60%。
        double safeW=layout.safe.w/layout.width*.83, safeH=layout.safe.h/layout.height*.78;
        double fov=lerp(frame.detail,46,32), tan=Math.tan(Math.toRadians(fov/2));
        double distance=Math.max(3.3,depth+Math.max(extentX/(tan*aspect*safeW),extentY/(tan*safeH)));
        double maxDistance=Math.min(14.0,(center.z+13.5)/forward.z);
        if (distance > maxDistance) {
            distance=maxDistance;
            tan=Math.max(extentX/(Math.max(.5,distance-depth)*aspect*safeW),extentY/(Math.max(.5,distance-depth)*safeH));
            fov=Math.min(128,Math.toDegrees(2*Math.atan(tan))); tan=Math.tan(Math.toRadians(fov/2));
        }
        double sx=(layout.safe.cx()/layout.width-.5)*2*distance*tan*aspect;
        double sy=(layout.safe.cy()/layout.height-.5)*2*distance*tan;
        Vector3d look=new Vector3d(center).fma(-sx,right).fma(sy,up);
        Vector3d eye=new Vector3d(look).fma(-distance,forward);
        return new Camera(eye,look,fov,frame.roll);
    }

    /** 在脸部、其他名签及字幕之外寻找侧签位置；没有位置时让低优先级标签退出。 */
    public static Rect placeLabel(double anchorX, double anchorY, double width, double height,
                                  Rect screen, List<Rect> obstacles, double gap, boolean side) {
        for (int row=0;row<10;row++) {
            double dy=(row==0?0:((row+1)/2)*(row%2==1?-1:1))*(height+gap);
            double[] xs=side ? new double[]{anchorX+gap,anchorX-width-gap} : new double[]{anchorX-width/2,anchorX+gap,anchorX-width-gap};
            for (double x:xs) {
                Rect r=new Rect(clamp(x,screen.x,Math.max(screen.x,screen.right()-width)),anchorY-height/2+dy,width,height);
                if (screen.contains(r) && obstacles.stream().noneMatch(o -> r.grow(gap*.35).overlaps(o))) return r;
            }
        }
        return null;
    }

    public static double scroll(double age, double duration, double contentHeight, double visibleHeight) {
        return Math.max(0,contentHeight-visibleHeight)*smooth((age-3)/Math.max(1,duration-7));
    }
    public static double smooth(double t) { t=clamp(t,0,1);return t*t*(3-2*t); }
    public static double pulse(double time,double start,double duration) {return time<start?0:1-smooth((time-start)/duration);}
    public static double clamp(double v,double min,double max) {return Math.max(min,Math.min(max,v));}
    public static double lerp(double t,double a,double b) {return a+(b-a)*t;}
}
