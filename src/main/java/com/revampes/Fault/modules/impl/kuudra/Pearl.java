package com.revampes.Fault.modules.impl.kuudra;

import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.ModuleManager;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.KuudraUtils;
import com.revampes.Fault.utility.RenderUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.*;

public class Pearl extends Module {

    private final ButtonSetting dynamicWaypoints = new ButtonSetting("Dynamic Waypoints", false);
    private final ColorSetting dynamicWaypointsColor = new ColorSetting("Dynamic Color", new Color(170, 0, 170));
    private final SliderSetting dynamicMarkerSize = new SliderSetting("Dynamic Marker Size", 0.2, 0.05, 1.0, 0.01);
    private final SliderSetting labelSize = new SliderSetting("Timing Label Size", 1.2, 0.5, 5.0, 0.1);
    private final ColorSetting labelColor = new ColorSetting("Timing Label Color", new Color(255, 255, 255));
    private final ButtonSetting presetWaypoints = new ButtonSetting("Preset Waypoints", true);
    private final SliderSetting lineupWaypointSize = new SliderSetting("Lineup Waypoint Size", 1.6, 0.5, 5.0, 0.1);
    private final SliderSetting lineupWaypointLineWidth = new SliderSetting("Lineup Line Width", 2.5, 1.0, 8.0, 0.1);
    private final ButtonSetting customLineupColor = new ButtonSetting("Custom Lineup Color", false);
    private final ColorSetting lineupWaypointColor = new ColorSetting("Lineup Color", new Color(255, 170, 85));
    private final ButtonSetting hideFarWaypoints = new ButtonSetting("Hide Far Waypoints", true);

    private static final double GRAV = 0.05;
    private static final double E_VEL = 1.67;

    private final Map<Supply, BlockPos> enumToLineup = new HashMap<>();
    private final Map<Lineup, Color> pearlLineups = new LinkedHashMap<>();

    public Pearl() {
        super("Pearl Waypoints", category.Kuudra);
        this.registerSetting(dynamicWaypoints);
        this.registerSetting(dynamicWaypointsColor);
        this.registerSetting(dynamicMarkerSize);
        this.registerSetting(labelSize);
        this.registerSetting(labelColor);
        this.registerSetting(presetWaypoints);
        this.registerSetting(lineupWaypointSize);
        this.registerSetting(lineupWaypointLineWidth);
        this.registerSetting(customLineupColor);
        this.registerSetting(lineupWaypointColor);
        this.registerSetting(hideFarWaypoints);

        dynamicWaypointsColor.setVisibilityCondition(dynamicWaypoints::isToggled);
        dynamicMarkerSize.setVisibilityCondition(dynamicWaypoints::isToggled);
        labelSize.setVisibilityCondition(dynamicWaypoints::isToggled);
        labelColor.setVisibilityCondition(dynamicWaypoints::isToggled);

        lineupWaypointSize.setVisibilityCondition(presetWaypoints::isToggled);
        lineupWaypointLineWidth.setVisibilityCondition(presetWaypoints::isToggled);
        customLineupColor.setVisibilityCondition(presetWaypoints::isToggled);
        lineupWaypointColor.setVisibilityCondition(() -> presetWaypoints.isToggled() && customLineupColor.isToggled());

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
            Color renderColor = getLineupRenderColor(entry.getValue());

            for (BlockPos startPos : lineup.startPos) {
                if (presetWaypoints.isToggled()) {
                    RenderUtils.drawBox(event.getMatrix(), getScaledBlockBox(startPos), renderColor, lineupWaypointLineWidth.getInput());
                }
            }

            for (BlockPos blockPos : lineup.lineups) {
                Supply missing = ModuleManager.priority != null ? ModuleManager.priority.missing : Supply.None;
                boolean shouldRenderLineup =
                    (missing == Supply.None
                        || missing == Supply.Square
                        || lineup.supply != Supply.Square
                        || Objects.equals(enumToLineup.get(missing), blockPos))
                    && (!hideFarWaypoints.isToggled() || closest);

                if (shouldRenderLineup) {
                    if (presetWaypoints.isToggled()) {
                        RenderUtils.drawBoxFilled(event.getMatrix(), getScaledBlockBox(blockPos), withAlpha(renderColor, 110));
                        RenderUtils.drawBox(event.getMatrix(), getScaledBlockBox(blockPos), renderColor, lineupWaypointLineWidth.getInput());
                    }

                    if (dynamicWaypoints.isToggled()) {
                        Supply destinationSupply = resolveDestinationSupply(lineup, blockPos, missing);
                        if (destinationSupply != Supply.None && destinationSupply != Supply.Square) {
                            BlockPos destinationPos = destinationSupply.getDropOffSpot();
                            PearlResult result = calculatePearl(destinationPos, event.getDelta());
                            if (result != null) {
                                double markerHalf = Math.max(0.02, dynamicMarkerSize.getInput() / 2.0);
                                Box upBox = new Box(result.upAngle.x - markerHalf, result.upAngle.y - markerHalf, result.upAngle.z - markerHalf,
                                                    result.upAngle.x + markerHalf, result.upAngle.y + markerHalf, result.upAngle.z + markerHalf);
                                Box flatBox = new Box(result.flatAngle.x - markerHalf, result.flatAngle.y - markerHalf, result.flatAngle.z - markerHalf,
                                                      result.flatAngle.x + markerHalf, result.flatAngle.y + markerHalf, result.flatAngle.z + markerHalf);

                                RenderUtils.drawBoxFilled(event.getMatrix(), upBox, dynamicWaypointsColor.getColor());
                                RenderUtils.drawBoxFilled(event.getMatrix(), flatBox, dynamicWaypointsColor.getColor());
                                renderTimingLabels(event, result);
                            }

                            // Supply dropOffSpot is the intended pearl landing coordinate.
                            RenderUtils.drawBox(event.getMatrix(), getScaledBlockBox(destinationPos), dynamicWaypointsColor.getColor(), lineupWaypointLineWidth.getInput());
                        }
                    }
                }
            }
            closest = false;
        }
    }

    private void renderTimingLabels(Render3DEvent event, PearlResult result) {
        int upTicks = Math.max(0, result.upTiming);
        int flatTicks = Math.max(0, result.flatTiming);

        double yOffset = Math.max(0.12, dynamicMarkerSize.getInput() + 0.06);
        float textScale = (float) Math.max(0.1, labelSize.getInput());
        int textColor = labelColor.getColor().getRGB();

        Vec3d upTextPos = result.upAngle.add(0.0, yOffset, 0.0);
        Vec3d flatTextPos = result.flatAngle.add(0.0, yOffset, 0.0);

        RenderUtils.draw3DText(event.getMatrix(), "Up " + upTicks + "t", upTextPos, textScale, textColor);
        RenderUtils.draw3DText(event.getMatrix(), "Flat " + flatTicks + "t", flatTextPos, textScale, textColor);
    }

    private Color getLineupRenderColor(Color defaultColor) {
        if (customLineupColor.isToggled()) {
            return lineupWaypointColor.getColor();
        }
        return defaultColor;
    }

    private Box getScaledBlockBox(BlockPos blockPos) {
        double scale = Math.max(0.1, lineupWaypointSize.getInput());
        double half = scale / 2.0;

        double centerX = blockPos.getX() + 0.5;
        double centerY = blockPos.getY() + 0.5;
        double centerZ = blockPos.getZ() + 0.5;

        return new Box(
            centerX - half, centerY - half, centerZ - half,
            centerX + half, centerY + half, centerZ + half
        );
    }

    private Color withAlpha(Color color, int alpha) {
        int clampedAlpha = MathHelper.clamp(alpha, 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampedAlpha);
    }

    private Supply resolveDestinationSupply(Lineup lineup, BlockPos lineupPos, Supply missing) {
        if (lineup.supply != Supply.Square) {
            return lineup.supply;
        }

        Supply mappedSupply = getSupplyForLineupPosition(lineupPos);
        if (mappedSupply != Supply.None) {
            return mappedSupply;
        }

        if (missing != Supply.None && missing != Supply.Square) {
            return missing;
        }

        return Supply.None;
    }

    private Supply getSupplyForLineupPosition(BlockPos lineupPos) {
        for (Map.Entry<Supply, BlockPos> entry : enumToLineup.entrySet()) {
            if (Objects.equals(entry.getValue(), lineupPos)) {
                return entry.getKey();
            }
        }
        return Supply.None;
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

    private PearlResult calculatePearl(BlockPos targetPos, float partialTicks) {
        if (mc.player == null) return null;

        double posX = MathHelper.lerp(partialTicks, mc.player.lastRenderX, mc.player.getX());
        double posY = MathHelper.lerp(partialTicks, mc.player.lastRenderY, mc.player.getY());
        double posZ = MathHelper.lerp(partialTicks, mc.player.lastRenderZ, mc.player.getZ());

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
            if (!(o instanceof Lineup lineup)) return false;
            return supply == lineup.supply && Objects.equals(startPos, lineup.startPos) && Objects.equals(lineups, lineup.lineups);
        }

        @Override
        public int hashCode() {
            return Objects.hash(supply, startPos, lineups);
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