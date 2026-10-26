package org.agmas.noellesroles.game.modes.echo.combat;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.EchoBootstrap;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.cards.EchoDraftService.PackQuality;
import org.agmas.noellesroles.game.modes.echo.content.EchoContent;
import org.agmas.noellesroles.game.modes.echo.objective.EchoDropService;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;

import java.util.*;
import java.util.function.Predicate;

/** 世界战斗适配：命中、冻结与最终死亡；机会数的唯一权威仍是成员的纯状态。 */
public final class EchoCombatService {
    private static final ResourceLocation IMMOBILIZED=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_immobilized");
    /** 职业被动的感知半径（血腥味等），与旧追猎循环无关。 */
    public static final double PASSIVE_RANGE=24.0D;
    private static final ResourceLocation WEB_SLOW=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_web_slow");
    /** 织网贴脸横甩的半径（格）：近距离不做抛物线，避免「明明贴脸却因为弹道而失手」。 */
    private static final double WEB_SWEEP_RANGE=2.5D;
    /** 横甩扇形的半角余弦（60°，即 120° 扇形）。 */
    private static final double WEB_SWEEP_COS=0.5D;
    /** 裂爪突进距离（格）与撞墙前留出的余量。 */
    private static final double REND_DASH_RANGE=4.0D, REND_DASH_PADDING=0.4D;
    /** 每个追捕者的受害者心跳节流：只按锁定值决定间隔，不额外发提醒文本。 */
    private final EchoMatch match;
    private final ServerLevel level;
    private final Map<UUID,Vec3> anchors=new HashMap<>();
    private final Map<UUID,UUID> downedBy=new HashMap<>();
    private final Set<UUID> prone=new HashSet<>();
    /** 同一追猎者最多同时保留的黏网块数；超出时先丢最早的一块。 */
    private static final int MAX_FIELDS=4;
    /** 织网被动「丝线感知」用的静止追踪：上一 tick 位置与连续静止 tick 数。 */
    private final Map<UUID,Vec3> lastPositions=new HashMap<>();
    private final Map<UUID,Integer> stillTicks=new HashMap<>();
    /** 原版物品冷却只是比赛截止的投影；暂停只平移原版时间轴，不逐 tick 重发冷却包。 */
    private final Map<UUID,Long> cutterProjections=new HashMap<>();
    private final Map<UUID,EchoCutterStrike.Impulse> knockbacks=new HashMap<>();
    private final Map<UUID,Double> originalMaxHealth=new HashMap<>();
    /** 已松手但尚未消费的本局动作；停止使用时不能误清除这次释放。 */
    private final Set<UUID> cutterReleases=new HashSet<>();
    public EchoCombatService(EchoMatch match,ServerLevel level) { this.match=match; this.level=level; }

    /** 开始蓄力只记录起点，不攻击、不消耗冷却。 */
    public boolean beginCutter(ServerPlayer player) {
        if(!canUseCutter(player)) return false;
        long now=match.matchTick();
        var state=match.state(player.getUUID());
        if(state==null || !state.beginCutter(now)) return false;
        return true;
    }
    private boolean canUseCutter(ServerPlayer player) {
        return match.standing(player) && !match.bags().busy(player.getUUID()) && player.getMainHandItem().is(EchoContent.CUTTER)
                && player.getInventory().selected==0 && match.faction(player.getUUID())==EchoFaction.INTERCEPTOR
                && match.phase().acceptsActions() && !SREGameTimeComponent.KEY.get(level).isTimeFrozen();
    }
    public void awaitCutterRelease(ServerPlayer player) { cutterReleases.add(player.getUUID()); }
    public void cancelCutter(ServerPlayer player) {
        cutterReleases.remove(player.getUUID());
        var state=match.state(player.getUUID());
        if(state!=null) state.cancelCharge();
    }
    /** 释放后同次结算，不进入前摇；按收到松手的比赛 tick 校验，队列等待不算蓄力。 */
    public void releaseCutter(ServerPlayer player,long releasedAt) {
        cutterReleases.remove(player.getUUID());
        if(!canUseCutter(player)) { cancelCutter(player); return; }
        long now=match.matchTick();
        var state=match.state(player.getUUID());
        if(state==null || !state.canAct(now) || !EchoCutterStrike.commit(state,now,releasedAt)) { cancelCutter(player); return; }
        player.stopUsingItem();
        match.cards().breakMask(player.getUUID());
        match.objectives().cancelActor(player);
        executeStrike(player);
    }

    /** 松手立即按当前视线判定；没有延迟刀。 */
    private void executeStrike(ServerPlayer player) {
        long now=match.matchTick();
        match.cards().breakMask(player.getUUID());
        match.objectives().cancelActor(player);
        for(ServerPlayer nearby:match.onlinePlayers()) if(nearby.distanceToSqr(player)<=16*16)
            nearby.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1,1F);
        var clazzState=match.classState(player.getUUID());
        Vec3 look=player.getLookAngle();
        float yaw=(float)(Math.toDegrees(Math.atan2(-look.x,look.z)));
        float pitch=(float)Math.toDegrees(Math.asin(-Math.max(-1,Math.min(1,look.y))));
        // 连段只在「追捕·战斗」职业上生效；段数必须在结算前取，命中会推进段数。
        boolean comboClass=clazzState!=null && clazzState.clazz()==org.agmas.noellesroles.game.modes.echo.classes.EchoClass.RAVAGER;
        int stage=comboClass?clazzState.swing(now):0;
        boolean connected=meleeRelease(player,now,stage,yaw,pitch);
        if(comboClass) {
            if(connected) clazzState.settleCombo(now); else clazzState.miss();
        }
        if(connected) org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.strike(match,player,connected);
        else org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.miss(match,player);
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.wipe(match,player,connected);
        if(connected) io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WIPE,
                player.getEyePosition().add(look.scale(0.9)),0,yaw);
        if(connected) {
            player.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,0.9F,1.1F);
            // 擦刀音：布料摩擦 + 轻微金属，复用原版声音，不新增资源。
            player.playNotifySound(SoundEvents.ARMOR_EQUIP_LEATHER.value(),SoundSource.PLAYERS,0.55F,1.35F);
            for(ServerPlayer nearby:match.onlinePlayers()) if(nearby!=player && nearby.distanceToSqr(player)<=12*12)
                nearby.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,0.7F,1.25F);
        } else player.playNotifySound(SoundEvents.PLAYER_ATTACK_NODAMAGE,SoundSource.PLAYERS,0.8F,0.9F);
        project(player,true);
    }

    // ───────────────────────── 职业：战斗 / 牵制 / 循环 ─────────────────────────

    /**
     * 近战结算：判定距离与倒地时长按职业连段段数加成。
     *
     * @param stage 连段段数 1..3；非「追捕·战斗」职业固定传 1
     */
    private boolean meleeRelease(ServerPlayer player, long now, int stage, float yaw, float pitch) {
        double reach = org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.comboReach(stage);
        double range = match.rules().combat().cutterRange() + reach;
        Vec3 look = player.getLookAngle();
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.SLASH,
                player.getEyePosition().add(look.scale(0.6)), 0, yaw, pitch, 1F);
        if (stage > 1) io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.REND_CLAW,
                player.getEyePosition().add(look.scale(0.5)), 0, yaw, pitch, stage);
        var target = firstPlayer(player, range).filter(p -> match.faction(p.getUUID()) != EchoFaction.INTERCEPTOR);
        if (target.isEmpty()) return false;
        ServerPlayer victim = target.get();
        var hit = lethal(victim, player, GameConstants.DeathReasons.KNIFE, false, false);
        if (!EchoCutterStrike.feedbackEligible(hit, false, false)) return false;
        int bleed = org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.comboBleed(stage);
        if (bleed > 0) match.state(victim.getUUID()).extendBleed(now, bleed);
        // 击倒后给「追捕·循环」职业一段追击增益，形成滚雪球。
        var hunterClass = match.classState(player.getUUID());
        if (hunterClass != null && (hit == EchoCombatState.Hit.DOWNED || hit == EchoCombatState.Hit.ELIMINATED))
            hunterClass.onKnockdown(now);
        if (hit == EchoCombatState.Hit.DOWNED || hit == EchoCombatState.Hit.ELIMINATED)
            match.economy().award(player, org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy.Source.DOWN,
                    victim.getUUID().toString());
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.BLOOD,
                victim.getEyePosition().add(0, -0.35, 0), 0, yaw, 1F);
        return true;
    }

    /** 把场地减速写成单一属性源，离开范围或场地过期时归零。 */
    private void applyWebSlow(ServerPlayer player,double factor) {
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed==null) return;
        var existing=speed.getModifier(WEB_SLOW);
        if(Math.abs(factor-1.0D)<0.005D) { if(existing!=null) speed.removeModifier(WEB_SLOW); return; }
        double amount=factor-1.0D;
        if(existing!=null && Math.abs(existing.amount()-amount)<0.002D) return;
        if(existing!=null) speed.removeModifier(WEB_SLOW);
        speed.addTransientModifier(new AttributeModifier(WEB_SLOW,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** 追捕·战斗主动技能「撕裂突进」：沿视线突进并把连段直接推到终结技。 */
    public void rendDash(ServerPlayer player) {
        var clazz = match.classState(player.getUUID());
        if (clazz == null) return;
        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition();
        var block = level.clip(new ClipContext(eye, eye.add(look.scale(REND_DASH_RANGE + REND_DASH_PADDING)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double allowed = REND_DASH_RANGE;
        if (block.getType() != HitResult.Type.MISS)
            allowed = Math.max(0.5D, Math.sqrt(eye.distanceToSqr(block.getLocation())) - REND_DASH_PADDING);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : flat.normalize().scale(allowed);
        if (level.noCollision(player, player.getBoundingBox().move(flat)))
            player.teleportTo(level, player.getX() + flat.x, player.getY(), player.getZ() + flat.z,
                    player.getYRot(), player.getXRot());
        player.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.75F);
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.REND_CLAW,
                player.getEyePosition(), 0, (float) (Math.toDegrees(Math.atan2(-look.x, look.z))),
                (float) Math.toDegrees(Math.asin(-Math.max(-1, Math.min(1, look.y)))), 3F);
    }

    /** 追捕·牵制主动技能「封锁钉」：在准星落点布置一圈减速场，只困逃脱方。 */
    public void placePin(ServerPlayer player) {
        var clazz = match.classState(player.getUUID());
        if (clazz == null) return;
        Vec3 at = player.position();
        var hit = level.clip(new ClipContext(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(6)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK) at = hit.getLocation();
        pins.add(new Pin(player.getUUID(), at, match.matchTick()
                + org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.PIN_TICKS));
        player.playNotifySound(SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 0.6F);
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WEB_FIELD, at, 0,
                (float) org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.PIN_RADIUS);
    }

    /** 逃脱·牵制主动技能「干扰场」：在自己脚下布一圈让追捕者变慢的场。 */
    public void placeJam(ServerPlayer player) {
        var clazz = match.classState(player.getUUID());
        if (clazz == null) return;
        Vec3 at = player.position();
        jams.add(new Pin(player.getUUID(), at, match.matchTick()
                + org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.JAM_TICKS));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.7F);
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WEB_FIELD,
                new Vec3(at.x, at.y + 0.1D, at.z), 0,
                (float) org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.JAM_RADIUS);
    }

    /** 场上的一块场地（封锁钉或干扰场）：归属、中心、截止。 */
    private record Pin(UUID owner, Vec3 at, long until) { }
    private final java.util.List<Pin> pins = new java.util.ArrayList<>();
    private final java.util.List<Pin> jams = new java.util.ArrayList<>();

    /** 每 tick 清理过期场地，并把封锁钉/干扰场的减速写成属性修正。 */
    private void tickClassFields(long now) {
        pins.removeIf(pin -> now >= pin.until());
        jams.removeIf(jam -> now >= jam.until());
        for (ServerPlayer player : match.onlinePlayers()) {
            boolean wolf = match.faction(player.getUUID()) == EchoFaction.INTERCEPTOR;
            double factor = 1.0D;
            if (wolf) {
                for (Pin jam : jams) if (jam.at().distanceToSqr(player.position())
                        <= org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.JAM_RADIUS
                        * org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.JAM_RADIUS)
                    factor = Math.min(factor, 1D - org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.JAM_SLOW);
            } else {
                for (Pin pin : pins) if (pin.at().distanceToSqr(player.position())
                        <= org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.PIN_RADIUS
                        * org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.PIN_RADIUS)
                    factor = Math.min(factor, 1D - org.agmas.noellesroles.game.modes.echo.classes.EchoClassState.PIN_SLOW);
            }
            applyWebSlow(player, factor);
        }
    }

    private boolean solidAt(Vec3 at) {
        BlockPos pos=BlockPos.containing(at);
        var state=level.getBlockState(pos);
        return !state.getCollisionShape(level,pos).isEmpty();
    }
    public boolean shove(ServerPlayer player) {
        if(!match.standing(player) || match.faction(player.getUUID())!=EchoFaction.CARRIER
                || !match.state(player.getUUID()).shoveReady(match.matchTick())) return false;
        var target=firstPlayer(player,match.rules().combat().shoveRange()).filter(p -> match.faction(p.getUUID())==EchoFaction.INTERCEPTOR);
        if(target.isEmpty()) return false;
        ServerPlayer wolf=target.get();
        if(!match.state(wolf.getUUID()).control(match.matchTick(),match.rules().combat().shoveControlTicks())) {
            player.displayClientMessage(Component.translatable("echo.combat.control_immune"),true); return false;
        }
        match.state(player.getUUID()).consumeShove(match.matchTick());
        match.trades().interrupt(player.getUUID()); match.trades().interrupt(wolf.getUUID());
        anchors.put(wolf.getUUID(),wolf.position()); wolf.stopUsingItem();
        match.objectives().cancelActor(player); match.objectives().cancelActor(wolf);
        project(player,true); project(wolf,true);
        return true;
    }

    /** 限制射线先撞方块/普通可点实体；倒地、旁观与模式展示不遮挡攻击。 */
    private Optional<ServerPlayer> firstPlayer(ServerPlayer source,double range) {
        Vec3 eye=source.getEyePosition(),end=eye.add(source.getLookAngle().scale(range));
        var block=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));
        double limit=block.getType()==HitResult.Type.MISS?range*range:eye.distanceToSqr(block.getLocation());
        Entity nearest=null; double distance=limit;
        for(Entity entity:level.getEntities(source,source.getBoundingBox().expandTowards(source.getLookAngle().scale(range)).inflate(0.3),e ->
                e.isPickable() && !e.isSpectator() && !e.getTags().contains(EchoDropService.PARCEL_TAG))) {
            if(entity instanceof ServerPlayer player && match.faction(player.getUUID())!=null && !match.life(player.getUUID()).standing()) continue;
            var box=entity.getBoundingBox().inflate(0.05);
            var hit=box.clip(eye,end);
            double current=box.contains(eye)?0:hit.map(eye::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
            if(current<distance) { nearest=entity; distance=current; }
        }
        return nearest instanceof ServerPlayer player && match.present(player) && match.life(player.getUUID()).standing()?Optional.of(player):Optional.empty();
    }

    /** 返回本次命中的结算结果；IGNORED 表示目标处于保护/无敌窗口，调用方不得当成命中。 */
    public EchoCombatState.Hit lethal(ServerPlayer victim,ServerPlayer killer,ResourceLocation reason,boolean force,boolean environmental) {
        if(match.result()!=null || match.faction(victim.getUUID())==null || victim.level()!=level) return EchoCombatState.Hit.IGNORED;
        return applyDamage(victim,killer,reason,force,environmental,EchoCombatState.CUTTER_DAMAGE);
    }
    public void environmentDamage(ServerPlayer victim,float amount) {
        if(victim.invulnerableTime>0 || !Float.isFinite(amount) || amount<=0) return;
        if(applyDamage(victim,null,GameConstants.DeathReasons.KNIFE,false,true,amount)!=EchoCombatState.Hit.IGNORED)
            victim.invulnerableTime=10;
    }
    private EchoCombatState.Hit applyDamage(ServerPlayer victim,ServerPlayer killer,ResourceLocation reason,boolean force,boolean environmental,float amount) {
        if(match.result()!=null || match.faction(victim.getUUID())==null || victim.level()!=level) return EchoCombatState.Hit.IGNORED;
        if(!force && (!match.phase().acceptsActions() || SREGameTimeComponent.KEY.get(level).isTimeFrozen())) return EchoCombatState.Hit.IGNORED;
        EchoCombatState state=match.state(victim.getUUID());
        EchoCombatState.Hit hit=state.damage(match.matchTick(),force,match.faction(victim.getUUID())==EchoFaction.INTERCEPTOR,amount);
        if(hit==EchoCombatState.Hit.IGNORED) return hit;
        if(!force && !environmental && killer!=null) recordCutterHit(killer.getUUID(),hit);
        react(victim,killer,hit,force,environmental);
        match.cards().breakMask(victim.getUUID());
        match.trades().interrupt(victim.getUUID());
        match.objectives().cancelActor(victim); match.bags().interrupt(victim); victim.stopUsingItem();
        if(hit==EchoCombatState.Hit.DAMAGED) {
            if(environmental && reason.equals(GameConstants.DeathReasons.FELL_OUT_OF_TRAIN)) {
                Vec3 anchor=match.safeAnchor(victim,true);
                victim.teleportTo(level,anchor.x,anchor.y,anchor.z,victim.getYRot(),victim.getXRot());
            }
        } else if(hit==EchoCombatState.Hit.DOWNED) {
            Vec3 anchor=match.safeAnchor(victim,environmental);
            boolean shove=EchoCutterStrike.feedbackEligible(hit,force,environmental);
            if(!shove) victim.teleportTo(level,anchor.x,anchor.y,anchor.z,victim.getYRot(),victim.getXRot());
            anchors.put(victim.getUUID(),shove?victim.position():anchor); prone.add(victim.getUUID());
            downedBy.put(victim.getUUID(),killer==null?null:killer.getUUID());
            dropCargo(victim,anchor);
            if(killer!=null) match.objectives().grantReward(killer,PackQuality.RARE,"down:"+victim.getUUID());
            announce("echo.combat.downed",victim);
        } else if(hit==EchoCombatState.Hit.RESET_INTERCEPTOR) {
            Vec3 spawn=match.spawn(victim.getUUID()); anchors.put(victim.getUUID(),spawn);
            victim.teleportTo(level,spawn.x,spawn.y,spawn.z,victim.getYRot(),victim.getXRot());
            // 生命值由下面的统一投影恢复，不能回到原来的十颗心。
        } else finishElimination(victim,killer,reason);
        project(victim,true);
        return hit;
    }
    /** 实际伤害成功才发一次原版受击包和短击退；环境、友军与强制淘汰不走这条。 */
    private void react(ServerPlayer victim,ServerPlayer killer,EchoCombatState.Hit hit,boolean force,boolean environmental) {
        if(!EchoCutterStrike.feedbackEligible(hit,force,environmental) || killer==null || victim.level()!=killer.level()) return;
        if(victim.hurtTime>0) return;
        victim.hurtDuration=10; victim.hurtTime=10; victim.hurtMarked=true;
        victim.serverLevel().broadcastDamageEvent(victim,victim.damageSources().playerAttack(killer));
        victim.indicateDamage(killer.getX()-victim.getX(),killer.getZ()-victim.getZ());
        var impulse=EchoCutterStrike.impulse(match.matchTick(),killer.getX(),killer.getZ(),victim.getX(),victim.getZ());
        if(impulse==null) return;
        knockbacks.put(victim.getUUID(),impulse);
        victim.setDeltaMovement(impulse.x(),victim.getDeltaMovement().y,impulse.z());
    }

    /** 实际武器命中统一施加6秒冷却与3秒缓慢I，远程与多目标复用此入口。 */
    private void recordCutterHit(UUID owner,EchoCombatState.Hit hit) {
        var state=match.state(owner);
        if(state==null || match.faction(owner)!=EchoFaction.INTERCEPTOR || !state.recordCutterHit(match.matchTick(),hit)) return;
        var player=match.connectedPlayer(owner);
        if(player!=null) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,0,false,false,true));
            project(player,true);
        }
    }

    public boolean canRescue(ServerPlayer actor,UUID target) {
        return match.standing(actor) && match.faction(actor.getUUID())!=EchoFaction.INTERCEPTOR
                && !actor.getUUID().equals(target) && match.state(target)!=null && match.state(target).canBeRescued(match.matchTick());
    }
    public void rescue(ServerPlayer actor,UUID target) {
        ServerPlayer victim=level.getServer().getPlayerList().getPlayer(target);
        if(victim==null || victim.level()!=level || !canRescue(actor,target)) return;
        if(!match.state(target).rescue(match.matchTick())) return;
        releaseAnchor(victim); downedBy.remove(target);
        match.objectives().grantReward(actor,PackQuality.COMMON,"rescue:"+target);
        match.economy().award(actor,org.agmas.noellesroles.game.modes.echo.economy.EchoEconomy.Source.RESCUE,target.toString());
        project(victim,true); announce("echo.combat.rescued",victim);
    }

    /** 暂停只执行限制，不推进流血、控制或冷却。 */
    public void restrict() {
        for(ServerPlayer player:match.onlinePlayers()) { cancelCutter(player); player.stopUsingItem(); restrict(player,match.state(player.getUUID())); projectCutterRemaining(player); }
        match.bags().restrictOpening();
    }
    private void restrict(ServerPlayer player,EchoCombatState state) {
        long now=match.matchTick();
        boolean paused=SREGameTimeComponent.KEY.get(level).isTimeFrozen();
        var shove=knockbacks.get(player.getUUID());
        if(shove!=null && !EchoCutterStrike.pushing(shove,now,paused)) { knockbacks.remove(player.getUUID()); shove=null; }
        if(state.life()==EchoLifeState.DOWNED || state.controlled(now)) {
            Vec3 anchor=anchors.computeIfAbsent(player.getUUID(),id -> player.position());
            player.stopUsingItem(); player.setSprinting(false); player.fallDistance=0;
            if(shove!=null && state.life()==EchoLifeState.DOWNED && !paused) advanceKnockback(player,shove);
            else {
                player.setDeltaMovement(Vec3.ZERO);
                if(player.position().distanceToSqr(anchor)>0.001) player.teleportTo(level,anchor.x,anchor.y,anchor.z,player.getYRot(),player.getXRot());
            }
            var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
            if(speed!=null && speed.getModifier(IMMOBILIZED)==null) speed.addTransientModifier(new AttributeModifier(IMMOBILIZED,-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if(state.life()==EchoLifeState.DOWNED) { player.setPose(Pose.SWIMMING); prone.add(player.getUUID()); }
        } else releaseAnchor(player);
    }
    /** 倒地窗口内只接受被动推力；撞墙立即停，不允许自行走动。 */
    private void advanceKnockback(ServerPlayer player,EchoCutterStrike.Impulse shove) {
        Vec3 step=new Vec3(shove.x(),0,shove.z());
        if(!level.noCollision(player,player.getBoundingBox().move(step))) {
            knockbacks.remove(player.getUUID()); player.setDeltaMovement(Vec3.ZERO); anchors.put(player.getUUID(),player.position()); return;
        }
        player.setDeltaMovement(step); player.move(net.minecraft.world.entity.MoverType.SELF,step);
        anchors.put(player.getUUID(),player.position());
    }
    public void tick() {
        long now=match.matchTick();
        if(SREGameTimeComponent.KEY.get(level).isTimeFrozen()) {
            for(ServerPlayer player:match.onlinePlayers()) player.setDeltaMovement(Vec3.ZERO);
            return;
        }
        for(UUID owner:match.participants()) if(match.connectedPlayer(owner)==null && match.life(owner)==EchoLifeState.DOWNED && now>=match.state(owner).bleedUntil()) match.exit(owner);
        tickClassFields(now);
        for(ServerPlayer player:match.onlinePlayers()) {
            EchoCombatState state=match.state(player.getUUID());
            if(state.expireBleed(now)) {
                UUID killerId=downedBy.get(player.getUUID());
                ServerPlayer killer=killerId==null?null:level.getServer().getPlayerList().getPlayer(killerId);
                finishElimination(player,killer,GameConstants.DeathReasons.KNIFE);
            }
            restrict(player,state);
            if(now%5==0) enforceTool(player,state);
            project(player,false);
            if(state.life()==EchoLifeState.DOWNED && now%10==0) player.displayClientMessage(
                    Component.translatable("echo.combat.bleeding",Math.max(0,(state.bleedUntil()-now+19)/20)),true);
        }
    }
    /** 固定三工具 + 六格唯一库存投影；其它原版槽位不承担模式资源。 */
    private void enforceTool(ServerPlayer player,EchoCombatState state) {
        boolean wolf=match.faction(player.getUUID())==EchoFaction.INTERCEPTOR && state.life().standing();
        boolean standing=state.life().standing();
        boolean changed=false;
        var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize();slot++) {
            if(slot>=3 && slot<9) continue;
            var stack=inventory.getItem(slot);
            boolean allowed=slot==0?(wolf && stack.is(EchoContent.CUTTER))
                    :slot==1?(standing && stack.is(EchoContent.CARD_PROXY))
                    :slot==2?(standing && stack.is(EchoContent.SKILL_TOKEN))
                    :false;
            if(!stack.isEmpty() && !allowed) { inventory.setItem(slot,ItemStack.EMPTY); changed=true; }
        }
        if(wolf && !inventory.getItem(0).is(EchoContent.CUTTER)) { inventory.setItem(0,new ItemStack(EchoContent.CUTTER)); changed=true; }
        if(standing && !inventory.getItem(1).is(EchoContent.CARD_PROXY)) { inventory.setItem(1,new ItemStack(EchoContent.CARD_PROXY)); changed=true; }
        if(standing && !inventory.getItem(2).is(EchoContent.SKILL_TOKEN)) { inventory.setItem(2,new ItemStack(EchoContent.SKILL_TOKEN)); changed=true; }
        org.agmas.noellesroles.game.modes.echo.content.EchoResourceItem.sync(player);
        if(changed) { player.inventoryMenu.broadcastChanges(); if(player.containerMenu!=player.inventoryMenu) player.containerMenu.broadcastChanges(); }
    }
    public void exit(ServerPlayer player) { match.exit(player.getUUID()); }
    public void restore(ServerPlayer player) {
        releaseAnchor(player); restrict(player,match.state(player.getUUID())); enforceTool(player,match.state(player.getUUID())); project(player,true);
    }
    /** 只撤销模式实体限制和工具，不触碰成员生命或倒地截止。 */
    public void detach(ServerPlayer player) {
        releaseAnchor(player); player.stopUsingItem();
        knockbacks.remove(player.getUUID());
        restoreMaxHealth(player);
        cancelCutter(player);
        clearCutterProjection(player);
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) if(EchoContent.readOnly(player.getInventory().getItem(slot))) player.getInventory().setItem(slot,ItemStack.EMPTY);
        player.inventoryMenu.setCarried(ItemStack.EMPTY); player.containerMenu.setCarried(ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges(); if(player.containerMenu!=player.inventoryMenu) player.containerMenu.broadcastChanges();
    }
    private void finishElimination(ServerPlayer player,ServerPlayer killer,ResourceLocation reason) {
        match.trades().interrupt(player.getUUID());
        match.bags().depart(player);
        match.contracts().depart(player.getUUID());
        match.cards().depart(player);
        dropCargo(player,match.safeAnchor(player,true)); releaseAnchor(player); downedBy.remove(player.getUUID());
        enforceTool(player,match.state(player.getUUID()));
        var mode=io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
        if(mode instanceof EchoGameMode echo) echo.finishElimination(player,killer,reason);
        announce("echo.combat.eliminated",player);
    }
    private void dropCargo(ServerPlayer player,Vec3 anchor) {
        while(!match.cargo(player.getUUID()).items().isEmpty()) if(!match.objectives().dropCarried(player,false,anchor)) throw new IllegalStateException("战斗掉货失败");
        if(match.bags().inventory(player.getUUID()).items().stream().anyMatch(org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceInstance::key)
                && !match.objectives().dropCarried(player,true,anchor)) throw new IllegalStateException("战斗掉钥匙失败");
    }
    private void releaseAnchor(ServerPlayer player) {
        anchors.remove(player.getUUID()); knockbacks.remove(player.getUUID());
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed!=null) speed.removeModifier(IMMOBILIZED);
        if(prone.remove(player.getUUID())) player.setPose(Pose.STANDING);
    }
    private void project(ServerPlayer player,boolean force) {
        projectHealth(player);
        RoleData.getOptional(EchoRoleData.class,player).filter(d -> match.id().equals(d.matchId()))
                .ifPresent(data -> data.projectCombat(match.state(player.getUUID()).snapshot(),match.matchTick(),force));
        projectCutterRemaining(player);
    }
    private void projectHealth(ServerPlayer player) {
        var state=match.state(player.getUUID());
        if(state==null || !state.life().potentiallyEscapable()) return;
        var attribute=player.getAttribute(Attributes.MAX_HEALTH);
        if(attribute!=null) {
            originalMaxHealth.putIfAbsent(player.getUUID(),attribute.getBaseValue());
            if(attribute.getBaseValue()!=EchoCombatState.MAX_HEALTH) attribute.setBaseValue(EchoCombatState.MAX_HEALTH);
        }
        // 倒地由模式管理，不能把原版血量写零触发第二条死亡链。
        float displayed=Math.max(0.5F,state.health());
        if(player.getHealth()!=displayed) player.setHealth(displayed);
    }
    private void restoreMaxHealth(ServerPlayer player) {
        var saved=originalMaxHealth.remove(player.getUUID());
        var attribute=player.getAttribute(Attributes.MAX_HEALTH);
        if(saved!=null && attribute!=null) {
            attribute.setBaseValue(saved);
            if(player.isAlive()) player.setHealth(player.getMaxHealth());
        }
    }
    /** 将比赛 tick 的武器截止投影到原版冷却；重连/暂停恢复都不修改权威截止。 */
    /** 供其它服务（如商店治疗）刷新本人生命投影。 */
    public void projectPublic(ServerPlayer player) { project(player,true); }

    public void projectCutterRemaining(ServerPlayer player) {
        var state=match.state(player.getUUID());
        if(state==null || match.faction(player.getUUID())!=EchoFaction.INTERCEPTOR || !state.life().standing()) {
            clearCutterProjection(player); return;
        }
        long remaining=state.cutterRemaining(match.matchTick());
        if(remaining==0) { clearCutterProjection(player); return; }
        long readyAt=state.snapshot().cutterReadyAt();
        var previous=cutterProjections.put(player.getUUID(),readyAt);
        var cooldowns=player.getCooldowns();
        int ticks=(int)Math.min(Integer.MAX_VALUE,remaining);
        if(previous==null || previous!=readyAt || !cooldowns.isOnCooldown(EchoContent.CUTTER)) {
            cooldowns.addCooldown(EchoContent.CUTTER,ticks);
        } else {
            var entry=cooldowns.cooldowns.get(EchoContent.CUTTER);
            int end=cooldowns.tickCount+ticks, shift=end-entry.endTime;
            if(shift!=0) cooldowns.cooldowns.put(EchoContent.CUTTER,
                    new net.minecraft.world.item.ItemCooldowns.CooldownInstance(entry.startTime+shift,end));
        }
    }
    private void clearCutterProjection(ServerPlayer player) {
        cutterProjections.remove(player.getUUID());
        if(player.getCooldowns().isOnCooldown(EchoContent.CUTTER)) player.getCooldowns().removeCooldown(EchoContent.CUTTER);
    }
    private void announce(String key,ServerPlayer player) {
        Component message=Component.translatable(key,player.getGameProfile().getName());
        match.onlinePlayers().forEach(p -> p.sendSystemMessage(message)); SRE.REPLAY_MANAGER.recordCustomEvent(message);
    }
    public void cleanup() {
        for(ServerPlayer player:match.onlinePlayers()) { releaseAnchor(player); player.stopUsingItem(); clearCutterProjection(player); restoreMaxHealth(player); }
        for(ServerPlayer player:match.onlinePlayers()) {
            for(int slot=0;slot<player.getInventory().getContainerSize();slot++)
                if(EchoContent.readOnly(player.getInventory().getItem(slot))) player.getInventory().setItem(slot,ItemStack.EMPTY);
            player.inventoryMenu.setCarried(ItemStack.EMPTY); player.containerMenu.setCarried(ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges(); if(player.containerMenu!=player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
        anchors.clear(); downedBy.clear(); prone.clear();
        lastPositions.clear(); stillTicks.clear();
        cutterProjections.clear();
        knockbacks.clear(); originalMaxHealth.clear(); cutterReleases.clear();
        pins.clear(); jams.clear();
        for(UUID owner:match.participants()) {
            var clazz=match.classState(owner);
            if(clazz!=null) clazz.clear();
        }
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.clearAll();
    }
}
