package com.revampes.Fault.modules.impl.dungeon.PuzzleSolver;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.events.impl.SendPacketEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaterBoardSolver extends Module {
    private static final int TOP_LEFT_X = 16;
    private static final int TOP_LEFT_Z = 26;
    private static final int TOP_RIGHT_X = 14;
    private static final int TOP_RIGHT_Z = 26;
    private static final int SEA_LANTERN_X = 15;
    private static final int SEA_LANTERN_Z = 27;
    private static final int PURPLE_WOOL_X = 15;
    private static final int PURPLE_WOOL_Z = 19;

    private static final int ROOM_SIZE = 32;
    private static final int ROOM_WORLD_START = -200;
    private static final int[] SEARCH_DELTAS = new int[] {-ROOM_SIZE * 2, -ROOM_SIZE, 0, ROOM_SIZE, ROOM_SIZE * 2};

    private static final Color FIRST_COLOR = new Color(0, 255, 0, 255);
    private static final Color SECOND_COLOR = new Color(255, 165, 0, 255);

    private static final List<Block> WOOL_ORDER = List.of(
        Blocks.PURPLE_WOOL,
        Blocks.ORANGE_WOOL,
        Blocks.BLUE_WOOL,
        Blocks.LIME_WOOL,
        Blocks.RED_WOOL
    );

    private final SelectSetting solutionMode = new SelectSetting("WaterBoardSolver Mode", 0, new String[] {"Desco1", "Efficient"});

    private final Map<String, Map<String, EnumMap<Lever, List<Integer>>>> solutionsData;
    private final Map<String, Map<String, EnumMap<Lever, List<Integer>>>> efficientSolutionsData;

    private boolean inWaterBoard = false;
    private Integer variant = null;
    private String subvariant = null;
    private List<SolutionEntry> solution = null;
    private long openedWaterAtTick = -1L;

    private int roomCornerX = Integer.MIN_VALUE;
    private int roomCornerZ = Integer.MIN_VALUE;
    private int roomRotation = 0;
    private int roomTopY = 77;
    private int roomYOffset = 0;

    private int worldIdentity = Integer.MIN_VALUE;
    private int lastMode = -1;
    private String lastUnknownKey = null;
    private int detectCooldownTicks = 0;

    private final Comparator<LeverTick> solutionSort = Comparator
        .comparingInt((LeverTick pair) -> pair.tick)
        .thenComparingInt(pair -> pair.lever.ordinal());

    private static final class RoomCandidate {
        private final int cornerX;
        private final int cornerZ;
        private final int rotation;
        private final int yOffset;
        private final int topY;
        private final int leverCount;
        private final Integer variant;
        private final String subvariant;
        private final double playerDistanceSq;

        private RoomCandidate(
            int cornerX,
            int cornerZ,
            int rotation,
            int yOffset,
            int topY,
            int leverCount,
            Integer variant,
            String subvariant,
            double playerDistanceSq
        ) {
            this.cornerX = cornerX;
            this.cornerZ = cornerZ;
            this.rotation = rotation;
            this.yOffset = yOffset;
            this.topY = topY;
            this.leverCount = leverCount;
            this.variant = variant;
            this.subvariant = subvariant;
            this.playerDistanceSq = playerDistanceSq;
        }

        private int score() {
            int score = leverCount * 20;
            if (variant != null) {
                score += 80;
            }
            if (subvariant != null && subvariant.length() == 3) {
                score += 40;
            }
            score -= (int) Math.min(playerDistanceSq, 500.0);
            return score;
        }
    }

    private static final class LeverTick {
        private final Lever lever;
        private final int tick;

        private LeverTick(Lever lever, int tick) {
            this.lever = lever;
            this.tick = tick;
        }
    }

    private static final class SolutionEntry {
        private final int x;
        private final int y;
        private final int z;
        private final int time;
        private final Lever lever;
        private final double x1;
        private final double z1;
        private final double wx;
        private final double wz;
        private final double y1;
        private final double h;

        private SolutionEntry(
            int x,
            int y,
            int z,
            int time,
            Lever lever,
            double x1,
            double z1,
            double wx,
            double wz,
            double y1,
            double h
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
            this.lever = lever;
            this.x1 = x1;
            this.z1 = z1;
            this.wx = wx;
            this.wz = wz;
            this.y1 = y1;
            this.h = h;
        }
    }

    private enum Lever {
        Quartz("quartz_block", 20, 61, 20, 20.625, 20.3125, 21.0, 20.6875, 61.25, 0.5),
        Gold("gold_block", 20, 61, 15, 20.625, 15.3125, 21.0, 15.6875, 61.25, 0.5),
        Coal("coal_block", 20, 61, 10, 20.625, 10.3125, 21.0, 10.6875, 61.25, 0.5),
        Diamond("diamond_block", 10, 61, 20, 10.0, 20.3125, 10.375, 20.6875, 61.25, 0.5),
        Emerald("emerald_block", 10, 61, 15, 10.0, 15.3125, 10.375, 15.6875, 61.25, 0.5),
        Terracotta("hardened_clay", 10, 61, 10, 10.0, 10.3125, 10.375, 10.6875, 61.25, 0.5),
        Water("water", 15, 60, 5, 15.25, 5.3125, 15.75, 5.6875, 60.0, 0.375);

        private final String type;
        private final int x;
        private final int y;
        private final int z;
        private final double x1;
        private final double z1;
        private final double x2;
        private final double z2;
        private final double y1;
        private final double h;

        Lever(String type, int x, int y, int z, double x1, double z1, double x2, double z2, double y1, double h) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            this.y1 = y1;
            this.h = h;
        }

        private static Lever from(String type) {
            if (type == null) {
                return null;
            }

            for (Lever value : values()) {
                if (value.type.equalsIgnoreCase(type)) {
                    return value;
                }
            }

            if ("terracotta".equalsIgnoreCase(type) || "white_terracotta".equalsIgnoreCase(type)) {
                return Terracotta;
            }

            return null;
        }
    }

    public WaterBoardSolver() {
        super("WaterBoardSolver", category.Dungeon);

        this.registerSetting(solutionMode);

        this.solutionsData = loadSolutionMap(
            "/assets/revampes/dungeons/WaterBoardSolution.json",
            "/assets/revampes/dungeons/WaterBoardSolutions.json",
            "/assets/devonian/dungeons/WaterBoardSolutions.json"
        );

        Map<String, Map<String, EnumMap<Lever, List<Integer>>>> efficient = loadSolutionMap(
            "/assets/revampes/dungeons/EfficientWaterBoardSolution.json",
            "/assets/revampes/dungeons/EfficientWaterboardSolutions.json",
            "/assets/devonian/dungeons/EfficientWaterboardSolutions.json"
        );
        this.efficientSolutionsData = efficient.isEmpty() ? this.solutionsData : efficient;
    }

    @Override
    public String getDesc() {
        return "Highlights efficient lever timings for Water Board puzzle.";
    }

    @Override
    public void onDisable() {
        resetState();
        worldIdentity = Integer.MIN_VALUE;
    }

    @EventHandler
    public void onPreUpdate(PreUpdateEvent event) {
        if (mc.world == null || mc.player == null || !DungeonUtils.isInDungeon()) {
            resetState();
            if (mc.world == null || !DungeonUtils.isInDungeon()) {
                worldIdentity = Integer.MIN_VALUE;
            }
            return;
        }

        int currentWorldIdentity = System.identityHashCode(mc.world);
        if (currentWorldIdentity != worldIdentity) {
            worldIdentity = currentWorldIdentity;
            resetState();
        }

        if (!inWaterBoard) {
            if (detectCooldownTicks > 0) {
                detectCooldownTicks--;
                return;
            }

            if (!tryDetectWaterBoardRoom()) {
                detectCooldownTicks = 4;
            }
            return;
        }

        if (!isLikelyWaterBoardRoom()) {
            resetState();
            detectCooldownTicks = 4;
            return;
        }

        if (variant == null) {
            variant = detectVariant(roomCornerX, roomCornerZ, roomRotation, roomTopY);
        }

        if (variant != null && (subvariant == null || subvariant.length() != 3)) {
            String detectedSubvariant = detectSubvariant(roomCornerX, roomCornerZ, roomRotation, roomYOffset);
            if (detectedSubvariant != null && detectedSubvariant.length() == 3) {
                subvariant = detectedSubvariant;
                buildSolution();
                System.out.println("Revampes$WaterBoard[variant=\"" + variant + "\", subvariant=\"" + subvariant + "\"]");
            }
        }

        if (solution == null && variant != null && subvariant != null && subvariant.length() == 3) {
            buildSolution();
        }

        int currentMode = (int) solutionMode.getValue();
        if (currentMode != lastMode && variant != null && subvariant != null && subvariant.length() == 3) {
            buildSolution();
        }
    }

    @EventHandler
    public void onSendPacket(SendPacketEvent event) {
        if (!(event.getPacket() instanceof PlayerInteractBlockC2SPacket packet)) {
            return;
        }

        BlockPos clickedPos = packet.getBlockHitResult().getBlockPos();
        mc.execute(() -> handleBlockInteract(clickedPos));
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (mc.world == null || !inWaterBoard || solution == null || solution.isEmpty()) {
            return;
        }

        MatrixStack stack = event.getMatrix();
        EnumMap<Lever, Integer> leverOffsets = new EnumMap<>(Lever.class);

        double lastX = 0.0;
        double lastY = 0.0;
        double lastZ = 0.0;

        long currentTick = mc.world.getTime();

        for (int i = 0; i < solution.size(); i++) {
            SolutionEntry entry = solution.get(i);
            int yOffset = leverOffsets.merge(entry.lever, 1, Integer::sum) - 1;

            Color color = i == 0 ? FIRST_COLOR : SECOND_COLOR;
            Box box = new Box(
                entry.x1,
                entry.y1 + roomYOffset + yOffset,
                entry.z1,
                entry.x1 + entry.wx,
                entry.y1 + roomYOffset + yOffset + entry.h,
                entry.z1 + entry.wz
            );
            RenderUtils.drawBox(stack, box, color, 2.0);
            RenderUtils.drawBoxFilled(stack, box, new Color(color.getRed(), color.getGreen(), color.getBlue(), 85));

            long remaining = openedWaterAtTick < 0 ? entry.time : entry.time - (currentTick - openedWaterAtTick);
            String title = remaining <= 0 ? "Click Now!" : String.format("%.2fs", remaining * 0.05);
            int textColor = remaining <= 0 ? 0xFF55FF55 : 0xFFFFDD55;

            double x = entry.x + 0.5;
            double y = entry.y + 0.5 + yOffset;
            double z = entry.z + 0.5;

            RenderUtils.draw3DText(stack, title, new Vec3d(x, y, z), 1.35f, textColor);

            if (i >= 1 && i <= 2) {
                Color lineColor = i == 1 ? FIRST_COLOR : SECOND_COLOR;
                RenderUtils.drawLine(stack, new Vec3d(lastX, lastY, lastZ), new Vec3d(x, y, z), lineColor, 2.0);
            }

            lastX = x;
            lastY = y;
            lastZ = z;
        }
    }

    private void handleBlockInteract(BlockPos pos) {
        if (mc.world == null || !inWaterBoard) {
            return;
        }

        Block block = mc.world.getBlockState(pos).getBlock();

        if (block == Blocks.LEVER && openedWaterAtTick < 0) {
            BlockPos waterLeverPos = fromComp(roomCornerX, roomCornerZ, Lever.Water.x, Lever.Water.z, toWorldY(Lever.Water.y), roomRotation);
            if (pos.equals(waterLeverPos)) {
                openedWaterAtTick = mc.world.getTime();
            }
        }

        if (block == Blocks.CHEST) {
            BlockPos chestPos = fromComp(roomCornerX, roomCornerZ, 15, 22, toWorldY(56), roomRotation);
            if (openedWaterAtTick >= 0 && pos.equals(chestPos) && solution != null && solution.isEmpty()) {
                double seconds = (mc.world.getTime() - openedWaterAtTick) * 0.05;
                Utils.addChatMessage(String.format("\u00A7bWater Board took\u00A7f: \u00A76%.2fs", seconds));
                openedWaterAtTick = -1L;
            }
            return;
        }

        if (block != Blocks.LEVER || solution == null || solution.isEmpty()) {
            return;
        }

        int index = -1;
        for (int i = 0; i < solution.size(); i++) {
            SolutionEntry entry = solution.get(i);
            if (entry.x == pos.getX() && entry.y == pos.getY() && entry.z == pos.getZ()) {
                index = i;
                break;
            }
        }

        if (index < 0) {
            return;
        }

        SolutionEntry entry = solution.get(index);
        long remaining = openedWaterAtTick < 0 ? entry.time : entry.time - (mc.world.getTime() - openedWaterAtTick);

        if (entry.time <= 0 || remaining < 20) {
            solution.remove(index);
        }
    }

    private boolean tryDetectWaterBoardRoom() {
        if (mc.world == null || mc.player == null) {
            return false;
        }

        int baseCornerX = toRoomCorner((int) Math.floor(mc.player.getX()));
        int baseCornerZ = toRoomCorner((int) Math.floor(mc.player.getZ()));

        RoomCandidate best = null;

        for (int dx : SEARCH_DELTAS) {
            for (int dz : SEARCH_DELTAS) {
                int cornerX = baseCornerX + dx;
                int cornerZ = baseCornerZ + dz;

                for (int rotation = 0; rotation < 4; rotation++) {
                    int yOffset = findBestLeverYOffset(cornerX, cornerZ, rotation);
                    if (yOffset == Integer.MIN_VALUE) {
                        continue;
                    }

                    int leverCount = countLeversAtYOffset(cornerX, cornerZ, rotation, yOffset);
                    if (leverCount < 6) {
                        continue;
                    }

                    int topY = detectTopY(cornerX, cornerZ, rotation, yOffset);
                    if (topY == Integer.MIN_VALUE) {
                        continue;
                    }

                    Integer detectedVariant = detectVariant(cornerX, cornerZ, rotation, topY);
                    if (detectedVariant == null) {
                        continue;
                    }

                    String detectedSubvariant = detectSubvariant(cornerX, cornerZ, rotation, yOffset);
                    double distanceSq = playerDistanceSqToRoomCenter(cornerX, cornerZ);

                    RoomCandidate candidate = new RoomCandidate(
                        cornerX,
                        cornerZ,
                        rotation,
                        yOffset,
                        topY,
                        leverCount,
                        detectedVariant,
                        detectedSubvariant,
                        distanceSq
                    );

                    if (best == null
                        || candidate.score() > best.score()
                        || (candidate.score() == best.score() && candidate.playerDistanceSq < best.playerDistanceSq)) {
                        best = candidate;
                    }
                }
            }
        }

        if (best == null) {
            return false;
        }

        inWaterBoard = true;
        roomCornerX = best.cornerX;
        roomCornerZ = best.cornerZ;
        roomRotation = best.rotation;
        roomYOffset = best.yOffset;
        roomTopY = best.topY;
        variant = best.variant;
        subvariant = best.subvariant;
        solution = null;
        openedWaterAtTick = -1L;
        lastUnknownKey = null;

        if (variant != null && subvariant != null && subvariant.length() == 3) {
            buildSolution();
        }

        detectCooldownTicks = 0;
        return true;
    }

    private boolean isLikelyWaterBoardRoom() {
        if (mc.world == null || roomCornerX == Integer.MIN_VALUE || roomCornerZ == Integer.MIN_VALUE) {
            return false;
        }

        int leverCount = countLeversAtYOffset(roomCornerX, roomCornerZ, roomRotation, roomYOffset);
        if (leverCount < 5) {
            return false;
        }

        int topY = detectTopY(roomCornerX, roomCornerZ, roomRotation, roomYOffset);
        if (topY == Integer.MIN_VALUE) {
            return false;
        }

        Integer detectedVariant = detectVariant(roomCornerX, roomCornerZ, roomRotation, topY);
        if (detectedVariant == null) {
            return false;
        }

        roomTopY = topY;
        if (variant == null) {
            variant = detectedVariant;
        }

        return true;
    }

    private int detectTopY(int cornerX, int cornerZ, int rotation, int yOffset) {
        if (mc.world == null) {
            return Integer.MIN_VALUE;
        }

        int y77 = 77 + yOffset;
        BlockPos lantern77 = fromComp(cornerX, cornerZ, SEA_LANTERN_X, SEA_LANTERN_Z, y77, rotation);
        if (mc.world.getBlockState(lantern77).getBlock() == Blocks.SEA_LANTERN) {
            return y77;
        }

        int y78 = 78 + yOffset;
        BlockPos lantern78 = fromComp(cornerX, cornerZ, SEA_LANTERN_X, SEA_LANTERN_Z, y78, rotation);
        if (mc.world.getBlockState(lantern78).getBlock() == Blocks.SEA_LANTERN) {
            return y78;
        }

        return Integer.MIN_VALUE;
    }

    private int findBestLeverYOffset(int cornerX, int cornerZ, int rotation) {
        int bestOffset = Integer.MIN_VALUE;
        int bestCount = -1;

        for (int yOffset = -20; yOffset <= 20; yOffset++) {
            int count = countLeversAtYOffset(cornerX, cornerZ, rotation, yOffset);
            if (count > bestCount) {
                bestCount = count;
                bestOffset = yOffset;
            }
        }

        return bestCount >= 6 ? bestOffset : Integer.MIN_VALUE;
    }

    private int countLeversAtYOffset(int cornerX, int cornerZ, int rotation, int yOffset) {
        if (mc.world == null) {
            return 0;
        }

        int count = 0;
        for (Lever lever : Lever.values()) {
            BlockPos pos = fromComp(cornerX, cornerZ, lever.x, lever.z, lever.y + yOffset, rotation);
            if (mc.world.getBlockState(pos).getBlock() == Blocks.LEVER) {
                count++;
            }
        }

        return count;
    }

    private Integer detectVariant(int cornerX, int cornerZ, int rotation, int topY) {
        if (mc.world == null) {
            return null;
        }

        BlockPos topLeftPos = fromComp(cornerX, cornerZ, TOP_LEFT_X, TOP_LEFT_Z, topY, rotation);
        BlockPos topRightPos = fromComp(cornerX, cornerZ, TOP_RIGHT_X, TOP_RIGHT_Z, topY, rotation);

        Block left = mc.world.getBlockState(topLeftPos).getBlock();
        Block right = mc.world.getBlockState(topRightPos).getBlock();

        if (left == Blocks.AIR || left == Blocks.STONE) {
            BlockPos shiftedLeft = fromComp(cornerX, cornerZ, TOP_LEFT_X, TOP_LEFT_Z + 1, topY, rotation);
            left = mc.world.getBlockState(shiftedLeft).getBlock();
        }

        if (right == Blocks.AIR || right == Blocks.STONE) {
            BlockPos shiftedRight = fromComp(cornerX, cornerZ, TOP_RIGHT_X, TOP_RIGHT_Z + 1, topY, rotation);
            right = mc.world.getBlockState(shiftedRight).getBlock();
        }

        return mapVariant(left, right);
    }

    private String detectSubvariant(int cornerX, int cornerZ, int rotation, int yOffset) {
        if (mc.world == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        int y = 57 + yOffset;

        for (int i = 0; i < WOOL_ORDER.size(); i++) {
            Block wool = WOOL_ORDER.get(i);
            BlockPos pos = fromComp(cornerX, cornerZ, PURPLE_WOOL_X, PURPLE_WOOL_Z - i, y, rotation);
            if (mc.world.getBlockState(pos).getBlock() == wool) {
                builder.append(i);
            }
        }

        return builder.length() == 3 ? builder.toString() : null;
    }

    private Integer mapVariant(Block left, Block right) {
        if (left == Blocks.GOLD_BLOCK && isTerracotta(right)) {
            return 0;
        }
        if (left == Blocks.EMERALD_BLOCK && right == Blocks.QUARTZ_BLOCK) {
            return 1;
        }
        if (left == Blocks.QUARTZ_BLOCK && right == Blocks.DIAMOND_BLOCK) {
            return 2;
        }
        if (left == Blocks.GOLD_BLOCK && right == Blocks.QUARTZ_BLOCK) {
            return 3;
        }

        return null;
    }

    private boolean isTerracotta(Block block) {
        return block == Blocks.TERRACOTTA || block == Blocks.WHITE_TERRACOTTA;
    }

    private void buildSolution() {
        if (variant == null || subvariant == null || subvariant.length() != 3) {
            return;
        }

        Map<String, Map<String, EnumMap<Lever, List<Integer>>>> source = getCurrentModeSolutions();
        Map<String, EnumMap<Lever, List<Integer>>> variants = source.get(String.valueOf(variant));
        EnumMap<Lever, List<Integer>> leverTimings = variants == null ? null : variants.get(subvariant);

        if (leverTimings == null) {
            String key = variant + "/" + subvariant;
            if (!key.equals(lastUnknownKey)) {
                Utils.addChatMessage("\u00A74Unknown water board variant: " + key);
                lastUnknownKey = key;
            }
            solution = new ArrayList<>();
            lastMode = (int) solutionMode.getValue();
            return;
        }

        List<LeverTick> sorted = new ArrayList<>();
        for (Map.Entry<Lever, List<Integer>> entry : leverTimings.entrySet()) {
            Lever lever = entry.getKey();
            if (entry.getValue() == null) {
                continue;
            }

            for (Integer tick : entry.getValue()) {
                if (tick != null) {
                    sorted.add(new LeverTick(lever, tick));
                }
            }
        }

        sorted.sort(solutionSort);

        List<SolutionEntry> builtSolution = new ArrayList<>();
        for (LeverTick tickEntry : sorted) {
            Lever lever = tickEntry.lever;

            BlockPos worldLeverPos = fromComp(roomCornerX, roomCornerZ, lever.x, lever.z, toWorldY(lever.y), roomRotation);
            Vec3d p1 = fromComp(roomCornerX, roomCornerZ, lever.x1, lever.z1, roomRotation);
            Vec3d p2 = fromComp(roomCornerX, roomCornerZ, lever.x2, lever.z2, roomRotation);

            double minX = Math.min(p1.x, p2.x);
            double minZ = Math.min(p1.z, p2.z);
            double maxX = Math.max(p1.x, p2.x);
            double maxZ = Math.max(p1.z, p2.z);

            builtSolution.add(new SolutionEntry(
                worldLeverPos.getX(),
                worldLeverPos.getY(),
                worldLeverPos.getZ(),
                tickEntry.tick,
                lever,
                minX,
                minZ,
                maxX - minX,
                maxZ - minZ,
                lever.y1,
                lever.h
            ));
        }

        this.solution = builtSolution;
        this.lastMode = (int) solutionMode.getValue();
        this.lastUnknownKey = null;
    }

    private Map<String, Map<String, EnumMap<Lever, List<Integer>>>> getCurrentModeSolutions() {
        return solutionMode.getValue() == 0 ? solutionsData : efficientSolutionsData;
    }

    private Map<String, Map<String, EnumMap<Lever, List<Integer>>>> loadSolutionMap(String... resourcePaths) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Map<String, Map<String, List<Double>>>>>() {}.getType();

        Map<String, Map<String, Map<String, List<Double>>>> raw = null;

        for (String path : resourcePaths) {
            try (InputStream stream = WaterBoardSolver.class.getResourceAsStream(path)) {
                if (stream == null) {
                    continue;
                }

                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    raw = gson.fromJson(reader, type);
                    if (raw != null) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (raw == null) {
            return new HashMap<>();
        }

        Map<String, Map<String, EnumMap<Lever, List<Integer>>>> converted = new HashMap<>();
        for (Map.Entry<String, Map<String, Map<String, List<Double>>>> variantEntry : raw.entrySet()) {
            Map<String, EnumMap<Lever, List<Integer>>> subvariants = new HashMap<>();
            if (variantEntry.getValue() == null) {
                continue;
            }

            for (Map.Entry<String, Map<String, List<Double>>> subEntry : variantEntry.getValue().entrySet()) {
                EnumMap<Lever, List<Integer>> leverMap = new EnumMap<>(Lever.class);
                if (subEntry.getValue() == null) {
                    continue;
                }

                for (Map.Entry<String, List<Double>> leverEntry : subEntry.getValue().entrySet()) {
                    Lever lever = Lever.from(leverEntry.getKey());
                    if (lever == null || leverEntry.getValue() == null) {
                        continue;
                    }

                    List<Integer> ticks = new ArrayList<>();
                    for (Double seconds : leverEntry.getValue()) {
                        if (seconds != null) {
                            ticks.add((int) Math.round(seconds * 20.0));
                        }
                    }
                    leverMap.put(lever, ticks);
                }

                subvariants.put(subEntry.getKey(), leverMap);
            }

            converted.put(variantEntry.getKey(), subvariants);
        }

        return converted;
    }

    private int toRoomCorner(int coord) {
        return Math.floorDiv(coord - ROOM_WORLD_START, ROOM_SIZE) * ROOM_SIZE + ROOM_WORLD_START;
    }

    private double playerDistanceSqToRoomCenter(int cornerX, int cornerZ) {
        if (mc.player == null) {
            return Double.MAX_VALUE;
        }

        double centerX = cornerX + 15.5;
        double centerZ = cornerZ + 15.5;
        return mc.player.squaredDistanceTo(centerX, mc.player.getY(), centerZ);
    }

    private int toWorldY(int roomY) {
        return roomY + roomYOffset;
    }

    private BlockPos fromComp(int cornerX, int cornerZ, int compX, int compZ, int y, int rotation) {
        int x;
        int z;

        switch (rotation & 3) {
            case 1 -> {
                x = cornerX + (31 - compZ);
                z = cornerZ + compX;
            }
            case 2 -> {
                x = cornerX + (31 - compX);
                z = cornerZ + (31 - compZ);
            }
            case 3 -> {
                x = cornerX + compZ;
                z = cornerZ + (31 - compX);
            }
            default -> {
                x = cornerX + compX;
                z = cornerZ + compZ;
            }
        }

        return new BlockPos(x, y, z);
    }

    private Vec3d fromComp(int cornerX, int cornerZ, double compX, double compZ, int rotation) {
        double x;
        double z;

        switch (rotation & 3) {
            case 1 -> {
                x = cornerX + (31.0 - compZ);
                z = cornerZ + compX;
            }
            case 2 -> {
                x = cornerX + (31.0 - compX);
                z = cornerZ + (31.0 - compZ);
            }
            case 3 -> {
                x = cornerX + compZ;
                z = cornerZ + (31.0 - compX);
            }
            default -> {
                x = cornerX + compX;
                z = cornerZ + compZ;
            }
        }

        return new Vec3d(x, 0.0, z);
    }

    private void resetState() {
        inWaterBoard = false;
        variant = null;
        subvariant = null;
        solution = null;
        openedWaterAtTick = -1L;
        roomCornerX = Integer.MIN_VALUE;
        roomCornerZ = Integer.MIN_VALUE;
        roomRotation = 0;
        roomTopY = 77;
        roomYOffset = 0;
        lastMode = -1;
        lastUnknownKey = null;
        detectCooldownTicks = 0;
    }
}
