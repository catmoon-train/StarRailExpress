package org.agmas.noellesroles.game.modes.echo.runtime;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.echo.map.EchoActiveMap;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapGate;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapRepository;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapValidator;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 结构预检与加载后的真实世界安全检查分离；不通过开发开关放宽任何安全检查。 */
public final class EchoPreflight {
    private EchoPreflight() { }

    public record Prepared(EchoMapConfig map,EchoRosterPolicy.Profile profile,org.agmas.noellesroles.game.modes.echo.rules.EchoRuleRepository.Snapshot rules) { }
    public record Check(Prepared prepared, List<Component> errors) {
        public Check { errors = List.copyOf(errors); }
        public boolean valid() { return errors.isEmpty() && prepared != null; }
    }

    /** 保留旧调用签名；开发参数不再限制正式开局。 */
    public static Check check(ServerLevel level, int playerCount, boolean ignoredDevelopment) {
        return check(level,playerCount,ignoredDevelopment,null);
    }

    public static Check check(ServerLevel level,int playerCount,boolean ignoredDevelopment,EchoMapConfig override) {
        return inspect(level,playerCount,override,true);
    }

    /** 重置前/加载前只读配置闸门；真实碰撞检查必须在区块准备完成后再次执行。 */
    public static Check configuration(ServerLevel level,int playerCount) {
        return inspect(level,playerCount,null,false);
    }

    private static Check inspect(ServerLevel level,int playerCount,EchoMapConfig override,boolean terrain) {
        List<Component> errors = new ArrayList<>();
        EchoRosterPolicy.Profile profile;
        try { profile = EchoRosterPolicy.forPlayerCount(playerCount); }
        catch (IllegalArgumentException exception) {
            return new Check(null, List.of(Component.translatable("echo.error.player_count", playerCount)));
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (areas.mapName == null || areas.mapName.isBlank()) {
            return new Check(null, List.of(Component.translatable("echo.error.map_required")));
        }
        if (!EchoMapGate.supported(areas)) {
            errors.add(Component.translatable("echo.error.map_mode"));
        }
        var settings = areas.areasSettings;
        if (!settings.initialItems.isEmpty() || settings.meetingEnabled || settings.bellMeetingEnabled
                || settings.minigameQuestEnabled || settings.planeCrashEventEnabled
                || !settings.enableSceneTask.isEmpty() || !settings.forcedRoles.isEmpty()
                || !settings.forcedModifiers.isEmpty() || !settings.enabledRoles.isEmpty()
                || !settings.enabledModifiers.isEmpty()
                || !org.agmas.harpymodloader.Harpymodloader.FORCED_MODDED_ROLE.isEmpty()
                || !org.agmas.harpymodloader.Harpymodloader.FORCED_MODDED_MODIFIER.isEmpty()) {
            errors.add(Component.translatable("echo.error.map_settings"));
        }
        try {
            var worldRoot=level.getServer().getWorldPath(LevelResource.ROOT);
            var rules=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleRepository.load(worldRoot);
            // 生效地图：switchmap 绑定优先，没有绑定才按 Areas 地图名自动匹配侧文件。
            String mapId=override!=null?override.mapId():EchoActiveMap.effective(worldRoot,areas.mapName);
            EchoMapConfig source = override!=null?override:EchoMapRepository.load(worldRoot, mapId);
            if(!source.mapId().equals(mapId) || !source.coordinateSpace().equals("template")) throw new IOException("侧文件必须对应当前回声地图并使用模板坐标");
            List<String> structural = EchoMapValidator.validate(source, playerCount);
            for (String issue : structural) errors.add(Component.translatable("echo.error.map_detail", issue));
            if(!structural.isEmpty()) return new Check(null,errors);
            EchoMapConfig map = source.translated(offset(areas));
            var core=map.coreArea();
            if(!areas.getPlayArea().contains(core.minX(),core.minY(),core.minZ()) || !areas.getPlayArea().contains(core.maxX()-0.01,core.maxY()-0.01,core.maxZ()-0.01))
                errors.add(Component.translatable("echo.error.map_detail","coreArea outside playArea"));
            List<EchoMapConfig.Spawn> spawns = new ArrayList<>(map.carrierSpawns());
            spawns.addAll(map.interceptorSpawns());
            for (EchoMapConfig.Spawn spawn : spawns) {
                if (!safeSpawn(level, areas, spawn, terrain)) errors.add(Component.translatable("echo.error.spawn", spawn.x(), spawn.y(), spawn.z()));
            }
            if (!areas.getPlayArea().contains(map.spectatorPos().x(), map.spectatorPos().y(), map.spectatorPos().z())) {
                errors.add(Component.translatable("echo.error.map_detail", "spectatorPos outside playArea"));
            }
            validateObjectives(level,areas,map,playerCount,errors,terrain);
            if(org.agmas.noellesroles.game.modes.echo.objective.EchoGateJournal.pending(
                    worldRoot,level.dimension().location().toString(),mapId)) {
                errors.add(Component.translatable("echo.error.recovery_pending"));
            }
            return new Check(errors.isEmpty() ? new Prepared(map,profile,rules) : null, errors);
        } catch (IOException | IllegalArgumentException exception) {
            Noellesroles.LOGGER.warn("回声地图预检失败: {}", areas.mapName, exception);
            errors.add(Component.translatable("echo.error.map_detail", exception.getMessage()));
            return new Check(null, errors);
        }
    }

    public static EchoMapConfig.Vec offset(AreasWorldComponent areas) {
        var template=areas.getResetTemplateArea(); var paste=areas.getResetPasteArea();
        var offset=new EchoMapConfig.Vec(paste.minX-template.minX,paste.minY-template.minY,paste.minZ-template.minZ);
        var from=BlockPos.containing(template.getMinPosition()); var to=BlockPos.containing(paste.getMinPosition());
        if(offset.x()!=to.getX()-from.getX() || offset.y()!=to.getY()-from.getY() || offset.z()!=to.getZ()-from.getZ())
            throw new IllegalArgumentException("模板平移与Areas实际方块复制不一致");
        return offset;
    }

    private static boolean safeSpawn(ServerLevel level, AreasWorldComponent areas, EchoMapConfig.Spawn spawn, boolean terrain) {
        if (!areas.getPlayArea().contains(spawn.x(), spawn.y(), spawn.z())
                || !areas.getPlayArea().contains(spawn.x(), spawn.y() + 1.8, spawn.z())) return false;
        BlockPos feet = BlockPos.containing(spawn.x(), spawn.y(), spawn.z());
        if (!level.getWorldBorder().isWithinBounds(feet)) return false;
        if (!terrain) return true;
        if (!level.hasChunkAt(feet)) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getBlockState(feet.below()).isCollisionShapeFullBlock(level, feet.below())
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty() && level.getFluidState(feet.below()).isEmpty()
                && !hazard(level,feet) && !hazard(level,feet.above()) && !hazard(level,feet.below());
    }

    private static boolean hazard(ServerLevel level,BlockPos pos) {
        var s=level.getBlockState(pos);
        return s.is(net.minecraft.tags.BlockTags.FIRE) || s.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || s.is(net.minecraft.world.level.block.Blocks.CAMPFIRE) || s.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                || s.is(net.minecraft.world.level.block.Blocks.CACTUS) || s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                || s.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE) || s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
    }

    private static void validateObjectives(ServerLevel level,AreasWorldComponent areas,EchoMapConfig map,int count,List<Component> errors,boolean terrain) {
        List<EchoMapConfig.Vec> points=new ArrayList<>();
        map.crystalNodes().stream().filter(p -> p.minPlayers()<=count).forEach(p -> points.add(p.pos()));
        map.beacons().stream().filter(p -> p.minPlayers()<=count).forEach(p -> points.add(p.pos()));
        map.supplyCrates().stream().filter(p -> p.minPlayers()<=count).forEach(p -> points.add(p.pos()));
        map.altars().forEach(p -> points.add(p.pos()));
        map.blackCrystalNodes().forEach(p -> points.add(p.pos()));
        map.blackDropoffs().forEach(p -> points.add(p.pos()));
        points.add(map.brokerExit().pos());
        points.add(map.evacuation().controlPos());
        map.coreGate().doors().forEach(p -> points.add(p.controlPos()));
        points.add(map.coreGate().freeExit().controlPos());
        java.util.Set<BlockPos> occupied=new java.util.HashSet<>();
        for(var point:points) {
            BlockPos pos=BlockPos.containing(point.x(),point.y(),point.z());
            if(!occupied.add(pos) || !areas.getPlayArea().contains(point.x(),point.y(),point.z())
                    || !level.getWorldBorder().isWithinBounds(pos) || (terrain && (!level.hasChunkAt(pos) || level.getBlockState(pos).isAir()))) {
                errors.add(Component.translatable("echo.error.map_detail","invalid/overlapping objective: "+pos));
            }
            if(terrain && level.hasChunkAt(pos)) {
                var state=level.getBlockState(pos);
                var machine=org.agmas.noellesroles.game.modes.echo.content.EchoMachines.kind(state);
                if(machine!=null) {
                    if(state.getValue(org.agmas.noellesroles.game.modes.echo.content.EchoMachineBlock.PART)!=0
                            || !org.agmas.noellesroles.game.modes.echo.content.EchoMachineStructure.complete(level,pos)) {
                        errors.add(Component.translatable("echo.machine.tasks.incomplete",pos.toShortString()));
                        continue;
                    }
                    var facing=state.getValue(org.agmas.noellesroles.game.modes.echo.content.EchoMachineBlock.FACING);
                    boolean core=map.coreArea().contains(point);
                    for(var cell:org.agmas.noellesroles.game.modes.echo.content.EchoMachineStructure.cells(machine)) {
                        var part=pos.offset(org.agmas.noellesroles.game.modes.echo.content.EchoMachineStructure.offset(cell.part(),facing));
                        var center=new EchoMapConfig.Vec(part.getX()+0.5,part.getY()+0.5,part.getZ()+0.5);
                        boolean gate=map.coreGate().doors().stream().anyMatch(d -> d.volume().contains(center))
                                || map.evacuation().doorVolume().contains(center);
                        if((!part.equals(pos) && !occupied.add(part)) || !level.getWorldBorder().isWithinBounds(part)
                                || !areas.getPlayArea().contains(part.getX(),part.getY(),part.getZ())
                                || !areas.getPlayArea().contains(part.getX()+0.999,part.getY()+0.999,part.getZ()+0.999)
                                || map.coreArea().contains(center)!=core
                                || gate || map.evacuation().zone().contains(center)) {
                            errors.add(Component.translatable("echo.error.map_detail","invalid/overlapping machine footprint: "+part));
                            break;
                        }
                    }
                }
            }
        }
        var anchor=map.evacuation().exitDropAnchor();
        if(!safeSpawn(level,areas,map.coreGate().freeExit().outsideSafe(),terrain) || !safeSpawn(level,areas,map.evacuation().outsideSafe(),terrain)
                || !safeSpawn(level,areas,new EchoMapConfig.Spawn(anchor.x(),anchor.y(),anchor.z(),0,0),terrain)) {
            errors.add(Component.translatable("echo.error.map_detail","unsafe free/evacuation exit/drop anchor"));
        }
        var zone=map.evacuation().zone();
        if(!areas.getPlayArea().contains(zone.minX(),zone.minY(),zone.minZ()) || !areas.getPlayArea().contains(zone.maxX()-0.01,zone.maxY()-0.01,zone.maxZ()-0.01))
            errors.add(Component.translatable("echo.error.map_detail","evacuation zone outside playArea"));
        List<EchoMapConfig.Box> volumes=new ArrayList<>();
        map.coreGate().doors().forEach(p -> volumes.add(p.volume()));
        volumes.add(map.evacuation().doorVolume());
        for(var volume:volumes) {
            if(!volume.integral() || !areas.getPlayArea().contains(volume.minX(),volume.minY(),volume.minZ())
                    || !areas.getPlayArea().contains(volume.maxX()-0.01,volume.maxY()-0.01,volume.maxZ()-0.01)) {
                errors.add(Component.translatable("echo.error.map_detail","gate outside playArea or nonintegral"));
                continue;
            }
            for(BlockPos pos:BlockPos.betweenClosed((int)volume.minX(),(int)volume.minY(),(int)volume.minZ(),
                    (int)volume.maxX()-1,(int)volume.maxY()-1,(int)volume.maxZ()-1)) {
                if((terrain && !level.hasChunkAt(pos)) || !level.getWorldBorder().isWithinBounds(pos)) {
                    errors.add(Component.translatable("echo.error.map_detail","unloaded/out-of-border gate: "+pos));
                    break;
                }
                if(!terrain) continue;
                var state=level.getBlockState(pos);
                if(state.isAir() || state.hasBlockEntity() || !state.isCollisionShapeFullBlock(level,pos) || !level.getFluidState(pos).isEmpty()) {
                    errors.add(Component.translatable("echo.error.map_detail","gate must be ordinary closed solid blocks: "+pos));
                    break;
                }
            }
        }
    }
}
