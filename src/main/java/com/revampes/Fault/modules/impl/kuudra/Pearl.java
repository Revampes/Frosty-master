package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.utility.KuudraUtils;
import com.revampes.Fault.utility.RenderUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.*;

public class Pearl extends Module {

    private final ButtonSetting dynamicWaypoints = new ButtonSetting("Dynamic Waypoints", false);
    private final ColorSetting dynamicWaypointsColor = new ColorSetting("Dynamic Color", new Color(170, 0, 170));
    private final ButtonSetting presetWaypoints = new ButtonSetting("Preset Waypoints", true);
    private final ButtonSetting hideFarWaypoints = new ButtonSetting("Hide Far Waypoints", true);

    private static final double GRAV = 0.05;
    private static final double E_VEL = 1.67;

    private final Map<Supply, BlockPos> enumToLineup = new HashMap<>();
    private final Map<Lineup, Color> pearlLineups = new HashMap<>();

    public Pearl() {
        super("Pearl Waypoints", category.Kuudra);
        this.registerSetting(dynamicWaypoints);
        this.registerSetting(dynamicWaypointsColor);
        this.registerSetting(presetWaypoints);
        this.registerSetting(hideFarWaypoints);

        initMaps();
    }

    private void initMaps() {
        enumToLineup.put(Supply.xCannon, new BlockPos(-59, 106, -59));
        enumToLineup.put(Supply.X, new BlockPos(-58, 127, -148));
        enumToLineup.put(Supply.Shop, new BlockPos(-146, 107, -60));
        enumToLineup.put(Supply.Triangle, new BlockPos(-149, 104, -70));
        enumToLineup.put(Supply.Equals, new BlockPos(-168, 124, -118));
        enumToLineup.put(Supply.Slash, new BlockPos(-65, 109, -162));

        pearlLineups.put(new Lineup(Supply.Shop, 
                Arrays.asList(new BlockPos(-71, 79, -135), new BlockPos(-86, 78, -129)),
                Arrays.asList(new BlockPos(-146, 107, -60), new BlockPos(-147, 111, -69))), new Color(255, 85, 85));

        pearlLineups.put(new Lineup(Supply.Triangle, 
                Collections.singletonList(new BlockPos(-68, 77, -123)),
                Collections.singletonList(new BlockPos(-149, 104, -70))), new Color(255, 85, 255));

        pearlLineups.put(new Lineup(Supply.X, 
                Collections.singletonList(new BlockPos(-135, 77, -139)),
                Collections.singletonList(new BlockPos(-59, 115, -71))), new Color(255, 255, 85));

        pearlLineups.put(new Lineup(Supply.xCannon, 
                Collections.singletonList(new BlockPos(-131, 79, -114)),
                Arrays.asList(new BlockPos(-59, 106, -59), new BlockPos(-51, 108, -67), new BlockPos(-39, 93, -76))), Color.WHITE);

        pearlLineups.put(new Lineup(Supply.Square, 
                Collections.singletonList(new BlockPos(-141, 78, -91)),
                Arrays.asList(new BlockPos(-59, 106, -59), new BlockPos(-58, 127, -148), new BlockPos(-146, 107, -60), 
                              new BlockPos(-149, 104, -70), new BlockPos(-168, 124, -118), new BlockPos(-65, 109, -162))), new Color(85, 255, 255));

        pearlLineups.put(new Lineup(Supply.Equals, 
                Collections.singletonList(new BlockPos(-66, 76, -88)),
                Collections.singletonList(new BlockPos(-168, 124, -118))), new Color(85, 255, 85));

        pearlLineups.put(new Lineup(Supply.Slash, 
                Collections.singletonList(new BlockPos(-115, 77, -69)),
                Collections.singletonList(new BlockPos(-65, 109, -162))), new Color(85, 85, 255));
    }

    @Override
    public String getDesc() {
        return "Renders waypoints for pearls in Kuudra.";
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!KuudraUtils.isInKuudra() || KuudraUtils.phase != 1) return;
        if (mc.player == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        boolean closest = true;

        for (Map.Entry<Lineup, Color> entry : getOrderedLineups(playerPos).entrySet()) {
            Lineup lineup = entry.getKey();
            Color color = entry.getValue();

            for (BlockPos startPos : lineup.startPos) {
                if (presetWaypoints.isToggled()) {
                    RenderUtils.drawBox(event.getMatrix(), new Box(startPos), color, 2.0);
                }
            }

            for (BlockPos blockPos : lineup.lineups) {
                Supply missing = ModuleManager.priority != null ? ModuleManager.priority.missing : Supply.None;
                boolean isMissingTarget = missing == Supply.None || missing == Supply.Square;
                boolean isSquareTarget = lineup.supply != Supply.Square || enumToLineup.get(missing) != null && enumToLineup.get(missing).equals(blockPos);

                if ((isMissingTarget || isSquareTarget) && (!hideFarWaypoints.isToggled() || closest)) {
                    if (presetWaypoints.isToggled()) {
                        RenderUtils.drawBoxFilled(event.getMatrix(), new Box(blockPos), new Color(color.getRed(), color.getGreen(), color.getBlue(), 80));
                        RenderUtils.drawBox(event.getMatrix(), new Box(blockPos), color, 2.0);
                    }
                    if (dynamicWaypoints.isToggled()) {
                        Supply destinationSupply = lineup.supply == Supply.Square ? missing : lineup.supply;
                        PearlResult result = calculatePearl(destinationSupply.getDropOffSpot());
                        if (result != null) {
                            Box upBox = new Box(result.upAngle.x - 0.06, result.upAngle.y - 0.06, result.upAngle.z - 0.06,
                                                result.upAngle.x + 0.06, result.upAngle.y + 0.06, result.upAngle.z + 0.06);
                            Box flatBox = new Box(result.flatAngle.x - 0.06, result.flatAngle.y - 0.06, result.flatAngle.z - 0.06,
                                                  result.flatAngle.x + 0.06, result.flatAngle.y + 0.06, result.flatAngle.z + 0.06);

                            RenderUtils.drawBoxFilled(event.getMatrix(), upBox, dynamicWaypointsColor.getColor());
                            RenderUtils.drawBoxFilled(event.getMatrix(), flatBox, dynamicWaypointsColor.getColor());
                        }
                        RenderUtils.drawBox(event.getMatrix(), new Box(lineup.supply.getDropOffSpot().up()), dynamicWaypointsColor.getColor(), 2.0);
                    }
                }
            }
            closest = false;
        }
    }

    private LinkedHashMap<Lineup, Color> getOrderedLineups(BlockPos pos) {
        List<Map.Entry<Lineup, Color>> list = new ArrayList<>(pearlLineups.entrySet());
        list.sort(Comparator.comparingDouble(entry -> {
            double minDist = Double.MAX_VALUE;
            for (BlockPos start : entry.getKey().startPos) {
                double dist = start.getSquaredDistance(pos);
                if (dist < minDist) minDist = dist;
            }
            return minDist;
        }));

        LinkedHashMap<Lineup, Color> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<Lineup, Color> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }

    private PearlResult calculatePearl(BlockPos targetPos) {
        if (mc.player == null) return null;
        
        double posX = mc.player.getX();
        double posY = mc.player.getY();
        double posZ = mc.player.getZ();

        double offX = targetPos.getX() - posX;
        double offY = targetPos.getY() - (posY + 1.62);
        double offZ = targetPos.getZ() - posZ;
        double offHor = Math.hypot(offX, offZ);

        double v2 = E_VEL * E_VEL;
        double v4 = v2 * v2;
        double g = GRAV * (1.0 + (offHor * 0.0012));

        double discriminant = v4 - g * (g * (offHor * offHor) + 2 * offY * v2);

        if (discriminant < 0) return new PearlResult(new Vec3d(0, 9, 0), new Vec3d(0, 9, 0), 0, 0);

        double root = Math.sqrt(discriminant);

        double angle1 = Math.toDegrees(Math.atan((v2 + root) / (g * offHor)));
        double angle2 = Math.toDegrees(Math.atan((v2 - root) / (g * offHor)));

        double uAngle = Math.max(angle1, angle2);
        double fAngle = Math.min(angle1, angle2);

        Vec3d uRes = new Vec3d(0, 10, 0);
        Vec3d fRes = new Vec3d(0, 10, 0);

        int uTiming = 0;
        int fTiming = 0;

        if (uAngle > 0.0) {
            double pitch = -uAngle;
            double radP = Math.toRadians(pitch);
            double radY = -Math.atan2(offX, offZ);

            double vY = E_VEL * Math.sin(Math.toRadians(uAngle));
            double flightTimeFactor = Math.pow(1.0012, Math.max(offHor / 15, 1.0)) * 0.8;
            double fT = (vY + Math.sqrt(vY * vY + 2 * GRAV * (posY + 1.62 - targetPos.getY()))) / GRAV;
            uTiming = (int) Math.floor((fT / Math.pow(0.992, fT)) * flightTimeFactor) - 2;

            double cosRadP = Math.cos(radP);
            double fX = cosRadP * Math.sin(radY);
            double fY = -Math.sin(radP);
            double fZ = cosRadP * Math.cos(radY);

            double targetX = posX - fX * 10;
            double targetY = posY + fY * 10;
            double targetZ = posZ + fZ * 10;

            uRes = new Vec3d(targetX, targetY, targetZ);
        }

        if (fAngle > 0.0) {
            double pitch = -fAngle;
            double radP = Math.toRadians(pitch);
            double radY = -Math.atan2(offX, offZ);

            double vX = E_VEL * Math.cos(Math.toRadians(fAngle));

            double drag = 0.978;
            double ticks = Math.log(1 - (offHor * (1 - drag) / vX)) / Math.log(drag);

            fTiming = Double.isNaN(ticks) ? (int) (offHor / vX) : (int) Math.ceil(ticks);

            double cosRadP = Math.cos(radP);
            double fX = cosRadP * Math.sin(radY);
            double fY = -Math.sin(radP);
            double fZ = cosRadP * Math.cos(radY);

            double targetX = posX - fX * 10;
            double targetY = posY + 1.2 + fY * 10;
            double targetZ = posZ + fZ * 10;

            fRes = new Vec3d(targetX, targetY, targetZ);
        }

        return new PearlResult(uRes, fRes, uTiming, fTiming);
    }

    private static class Lineup {
        final Supply supply;
        final List<BlockPos> startPos;
        final List<BlockPos> lineups;

        Lineup(Supply supply, List<BlockPos> startPos, List<BlockPos> lineups) {
            this.supply = supply;
            this.startPos = startPos;
            this.lineups = lineups;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Lineup lineup = (Lineup) o;
            return supply == lineup.supply;
        }

        @Override
        public int hashCode() {
            return Objects.hash(supply);
        }
    }

    private static class PearlResult {
        final Vec3d upAngle;
        final Vec3d flatAngle;
        final int upTiming;
        final int flatTiming;

        PearlResult(Vec3d upAngle, Vec3d flatAngle, int upTiming, int flatTiming) {
            this.upAngle = upAngle;
            this.flatAngle = flatAngle;
            this.upTiming = upTiming;
            this.flatTiming = flatTiming;
        }
    }
}