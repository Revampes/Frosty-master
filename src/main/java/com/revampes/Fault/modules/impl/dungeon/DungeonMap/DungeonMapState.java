package com.revampes.Fault.modules.impl.dungeon.DungeonMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DungeonMapState {
    public static final int CONNECTOR_UNKNOWN = -1;
    public static final int CONNECTOR_NONE = 0;
    public static final int CONNECTOR_MERGED = 1;
    public static final int CONNECTOR_DOOR_NORMAL = 2;
    public static final int CONNECTOR_DOOR_BLOOD = 3;
    public static final int CONNECTOR_DOOR_WITHER = 4;

    private static final int MAP_PIXELS = 128 * 128;
    private static final int ROOM_WORLD_START = -185;
    private static final int ROOM_WORLD_CORNER_START = -200;

    private static final BlockPos.Mutable MUTABLE_POS = new BlockPos.Mutable();

    private final DungeonRoomDatabase roomDatabase = new DungeonRoomDatabase();

    private byte[] colors = null;
    private MapVec2i startCoords = null;
    private int roomSize = 0;
    private int roomsX = 6;
    private int roomsZ = 6;
    private List<PlayerMarker> markers = Collections.emptyList();

    private int mapPacketsSeen = 0;
    private int mapPacketsParsed = 0;
    private int worldFallbackHits = 0;

    private RoomSnapshot[][] rooms = new RoomSnapshot[6][6];
    private int[][] horizontalConnectorType = new int[6][5];
    private int[][] verticalConnectorType = new int[5][6];
    private final int[][] cachedRoomColor = new int[6][6];
    private final boolean[][] stickyBloodCell = new boolean[6][6];

    private long lastModelScanMs = 0L;
    private int roomTransformMode = 0;
    private int lastTransformScore = Integer.MIN_VALUE;

    public DungeonMapState() {
        clear();
    }

    public void clear() {
        colors = null;
        startCoords = null;
        roomSize = 0;
        roomsX = 6;
        roomsZ = 6;
        markers = Collections.emptyList();
        rooms = new RoomSnapshot[6][6];
        horizontalConnectorType = new int[6][5];
        verticalConnectorType = new int[5][6];
        for (int z = 0; z < horizontalConnectorType.length; z++) {
            Arrays.fill(horizontalConnectorType[z], CONNECTOR_UNKNOWN);
        }
        for (int z = 0; z < verticalConnectorType.length; z++) {
            Arrays.fill(verticalConnectorType[z], CONNECTOR_UNKNOWN);
        }
        roomTransformMode = 0;
        lastTransformScore = Integer.MIN_VALUE;
        lastModelScanMs = 0L;
        for (int z = 0; z < cachedRoomColor.length; z++) {
            Arrays.fill(cachedRoomColor[z], 0);
            Arrays.fill(stickyBloodCell[z], false);
        }
    }

    public boolean hasData() {
        return colors != null && startCoords != null && roomSize > 0;
    }

    public int getRoomsX() {
        return roomsX;
    }

    public int getRoomsZ() {
        return roomsZ;
    }

    public int getMapStartX() {
        return startCoords == null ? 0 : startCoords.x;
    }

    public int getMapStartZ() {
        return startCoords == null ? 0 : startCoords.z;
    }

    public int getTileSize() {
        return roomSize <= 0 ? 20 : roomSize + 4;
    }

    public int getMapPixelWidth() {
        return roomsX * getTileSize() - 4;
    }

    public int getMapPixelHeight() {
        return roomsZ * getTileSize() - 4;
    }

    public List<PlayerMarker> getMarkers() {
        return markers;
    }

    public int getMapPacketsSeen() {
        return mapPacketsSeen;
    }

    public int getMapPacketsParsed() {
        return mapPacketsParsed;
    }

    public int getWorldFallbackHits() {
        return worldFallbackHits;
    }

    public RoomSnapshot getRoom(int roomX, int roomZ) {
        if (roomX < 0 || roomZ < 0 || roomZ >= rooms.length || roomX >= rooms[roomZ].length) {
            return null;
        }
        return rooms[roomZ][roomX];
    }

    public DungeonRoomDatabase.RoomKind getRoomKind(int roomX, int roomZ) {
        RoomSnapshot room = getRoom(roomX, roomZ);
        return room == null ? DungeonRoomDatabase.RoomKind.UNKNOWN : room.kind();
    }

    public String getRoomName(int roomX, int roomZ) {
        RoomSnapshot room = getRoom(roomX, roomZ);
        return room == null ? "" : room.name();
    }

    public int getHorizontalConnectionType(int betweenX, int rowZ) {
        if (betweenX < 0 || rowZ < 0 || rowZ >= horizontalConnectorType.length || betweenX >= horizontalConnectorType[rowZ].length) {
            return CONNECTOR_UNKNOWN;
        }
        return horizontalConnectorType[rowZ][betweenX];
    }

    public int getVerticalConnectionType(int columnX, int betweenZ) {
        if (columnX < 0 || betweenZ < 0 || betweenZ >= verticalConnectorType.length || columnX >= verticalConnectorType[betweenZ].length) {
            return CONNECTOR_UNKNOWN;
        }
        return verticalConnectorType[betweenZ][columnX];
    }

    public boolean updateFromPacket(Object packet, MinecraftClient mc) {
        mapPacketsSeen++;
        List<PlayerMarker> packetMarkers = extractMarkers(packet);

        byte[] extracted = tryExtractColors(packet, mc);
        if (extracted == null || extracted.length != MAP_PIXELS) {
            return false;
        }
        if (!looksLikeDungeonMap(extracted)) {
            return false;
        }

        mapPacketsParsed++;
        // Some map packets carry no decoration payload (delta updates).
        // Keep the last non-empty marker set so far teammates do not disappear.
        if (!packetMarkers.isEmpty()) {
            markers = packetMarkers;
        }
        this.colors = Arrays.copyOf(extracted, extracted.length);
        calibrateFromColors(extracted);
        scanWorldModel(mc, false);
        return true;
    }

    public boolean updateFromWorld(MinecraftClient mc) {
        if (mc == null || mc.world == null) {
            return false;
        }

        byte[] extracted = tryExtractColorsFromWorld(mc.world);
        if (extracted != null && extracted.length == MAP_PIXELS) {
            worldFallbackHits++;
            this.colors = Arrays.copyOf(extracted, extracted.length);
            calibrateFromColors(extracted);
        }

        scanWorldModel(mc, true);
        return hasData();
    }

    private void calibrateFromColors(byte[] extracted) {
        Pair green = findGreenRoom(extracted);
        if (green.length == 16 || green.length == 18) {
            this.roomSize = green.length;

            int tileSize = roomSize + 4;
            int startX = (green.start & 127) % tileSize;
            int startZ = (green.start >> 7) % tileSize;
            this.startCoords = new MapVec2i(startX, startZ);

            int extraX = startX == 5 ? 1 : 0;
            int extraZ = startZ == 5 ? 1 : 0;
            this.roomsX = clamp(5 + extraX, 4, 6);
            this.roomsZ = clamp(5 + extraZ, 4, 6);
            return;
        }

        if (startCoords == null || roomSize <= 0) {
            this.roomSize = 16;
            this.startCoords = new MapVec2i(11, 11);
            this.roomsX = 6;
            this.roomsZ = 6;
        }
    }

    private void scanWorldModel(MinecraftClient mc, boolean allowThrottle) {
        if (!hasData() || mc == null || mc.world == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (allowThrottle && now - lastModelScanMs < 300L) {
            return;
        }
        lastModelScanMs = now;

        TransformResult transformResult = pickBestRoomTransform(mc);
        if (shouldSwitchTransform(transformResult)) {
            roomTransformMode = transformResult.mode();
            lastTransformScore = transformResult.score();
        }

        RoomSnapshot[][] nextRooms = new RoomSnapshot[6][6];
        for (int z = 0; z < 6; z++) {
            for (int x = 0; x < 6; x++) {
                if (z >= roomsZ || x >= roomsX) {
                    nextRooms[z][x] = null;
                    continue;
                }

                int mapColor = getRoomColorValue(x, z);
                RoomSnapshot previous = rooms[z][x];

                if (mapColor != 0) {
                    cachedRoomColor[z][x] = mapColor;
                }

                if (mapColor == 0) {
                    if (previous != null) {
                        nextRooms[z][x] = new RoomSnapshot(previous.roomId(), previous.kind(), previous.name(), mapColor);
                    }
                    continue;
                }

                int[] worldPos = mapRoomToWorld(x, z, roomTransformMode);
                int worldX = worldPos[0];
                int worldZ = worldPos[1];

                if (!isChunkLoaded(mc, worldX, worldZ)) {
                    RoomSnapshot fallback = buildFallbackRoomSnapshot(previous, mapColor);
                    if (fallback != null) {
                        nextRooms[z][x] = fallback;
                        if (fallback.kind() == DungeonRoomDatabase.RoomKind.BLOOD) {
                            stickyBloodCell[z][x] = true;
                        }
                    }
                    continue;
                }

                int core = getCoreAtRoomCenter(mc, worldX, worldZ);
                DungeonRoomDatabase.RoomMeta meta = roomDatabase.getMetaForCore(core);

                if (meta.kind() != DungeonRoomDatabase.RoomKind.UNKNOWN) {
                    nextRooms[z][x] = new RoomSnapshot(meta.name().hashCode(), meta.kind(), meta.name(), mapColor);
                    if (meta.kind() == DungeonRoomDatabase.RoomKind.BLOOD) {
                        stickyBloodCell[z][x] = true;
                    }
                } else {
                    RoomSnapshot fallback = buildFallbackRoomSnapshot(previous, mapColor);
                    nextRooms[z][x] = fallback == null
                        ? new RoomSnapshot(0, DungeonRoomDatabase.RoomKind.UNKNOWN, "", mapColor)
                        : fallback;
                    if (nextRooms[z][x].kind() == DungeonRoomDatabase.RoomKind.BLOOD) {
                        stickyBloodCell[z][x] = true;
                    }
                }

                if (mapColor == 18 && (stickyBloodCell[z][x] || (previous != null && previous.kind() == DungeonRoomDatabase.RoomKind.BLOOD))) {
                    RoomSnapshot current = nextRooms[z][x];
                    nextRooms[z][x] = new RoomSnapshot(current.roomId(), DungeonRoomDatabase.RoomKind.BLOOD, current.name(), mapColor);
                    stickyBloodCell[z][x] = true;
                }
            }
        }

        int[][] nextH = new int[6][5];
        int[][] nextV = new int[5][6];
        for (int z = 0; z < nextH.length; z++) {
            Arrays.fill(nextH[z], CONNECTOR_UNKNOWN);
        }
        for (int z = 0; z < nextV.length; z++) {
            Arrays.fill(nextV[z], CONNECTOR_UNKNOWN);
        }

        for (int z = 0; z < roomsZ; z++) {
            for (int x = 0; x < roomsX - 1; x++) {
                nextH[z][x] = detectHorizontalConnector(mc, nextRooms, x, z, horizontalConnectorType[z][x]);
            }
        }

        for (int z = 0; z < roomsZ - 1; z++) {
            for (int x = 0; x < roomsX; x++) {
                nextV[z][x] = detectVerticalConnector(mc, nextRooms, x, z, verticalConnectorType[z][x]);
            }
        }

        pruneUnopenedRoomJoins(nextRooms, nextH, nextV);

        // Enforce that an ENTRANCE room only merges with at most one adjacent room.
        for (int z = 0; z < roomsZ; z++) {
            for (int x = 0; x < roomsX; x++) {
                RoomSnapshot room = nextRooms[z][x];
                if (room == null) continue;
                if (room.kind() != DungeonRoomDatabase.RoomKind.ENTRANCE) continue;

                // collect merged connectors around this entrance
                List<int[]> mergedConnectors = new ArrayList<>();
                // right
                if (x < roomsX - 1 && nextH[z][x] == CONNECTOR_MERGED) mergedConnectors.add(new int[]{0, z, x});
                // left
                if (x > 0 && nextH[z][x - 1] == CONNECTOR_MERGED) mergedConnectors.add(new int[]{1, z, x - 1});
                // down
                if (z < roomsZ - 1 && nextV[z][x] == CONNECTOR_MERGED) mergedConnectors.add(new int[]{2, z, x});
                // up
                if (z > 0 && nextV[z - 1][x] == CONNECTOR_MERGED) mergedConnectors.add(new int[]{3, z - 1, x});

                if (mergedConnectors.size() > 1) {
                    // keep only the first merged connector, clear the rest
                    for (int i = 1; i < mergedConnectors.size(); i++) {
                        int[] info = mergedConnectors.get(i);
                        int type = info[0];
                        int cz = info[1];
                        int cx = info[2];
                        if (type == 0) nextH[cz][cx] = CONNECTOR_NONE; // right
                        else if (type == 1) nextH[cz][cx] = CONNECTOR_NONE; // left
                        else if (type == 2) nextV[cz][cx] = CONNECTOR_NONE; // down
                        else if (type == 3) nextV[cz][cx] = CONNECTOR_NONE; // up
                    }
                }
            }
        }

        rooms = nextRooms;
        horizontalConnectorType = nextH;
        verticalConnectorType = nextV;
    }

    private RoomSnapshot buildFallbackRoomSnapshot(RoomSnapshot previous, int mapColor) {
        DungeonRoomDatabase.RoomKind inferredKind = inferRoomKindFromMapColor(mapColor, previous);
        if (previous != null) {
            boolean forceMapKind = shouldForceMapInferredKind(mapColor, inferredKind);
            DungeonRoomDatabase.RoomKind finalKind = forceMapKind
                ? inferredKind
                : (previous.kind() != DungeonRoomDatabase.RoomKind.UNKNOWN ? previous.kind() : inferredKind);
            String name = (previous.name() == null || previous.name().isBlank())
                ? defaultNameForKind(finalKind)
                : previous.name();
            int roomId = previous.roomId() != 0
                ? previous.roomId()
                : (name.isBlank() ? finalKind.name().hashCode() : name.hashCode());
            return new RoomSnapshot(roomId, finalKind, name, mapColor);
        }

        String name = defaultNameForKind(inferredKind);
        int roomId = name.isBlank() ? 0 : name.hashCode();
        return new RoomSnapshot(roomId, inferredKind, name, mapColor);
    }

    private boolean shouldForceMapInferredKind(int mapColor, DungeonRoomDatabase.RoomKind inferredKind) {
        if (inferredKind == DungeonRoomDatabase.RoomKind.UNKNOWN) {
            return false;
        }
        return mapColor == 82 || mapColor == 30 || mapColor == 18 || mapColor == 62 || mapColor == 66 || mapColor == 74;
    }

    private DungeonRoomDatabase.RoomKind inferRoomKindFromMapColor(int mapColor, RoomSnapshot previous) {
        if (previous != null && previous.kind() != DungeonRoomDatabase.RoomKind.UNKNOWN) {
            if (mapColor == 0 || mapColor == 85 || mapColor == 119) {
                return previous.kind();
            }
        }

        return switch (mapColor) {
            case 30 -> DungeonRoomDatabase.RoomKind.ENTRANCE;
            case 18 -> DungeonRoomDatabase.RoomKind.BLOOD;
            case 62 -> DungeonRoomDatabase.RoomKind.TRAP;
            case 66 -> DungeonRoomDatabase.RoomKind.PUZZLE;
            case 74 -> DungeonRoomDatabase.RoomKind.CHAMPION;
            case 82 -> DungeonRoomDatabase.RoomKind.FAIRY;
            case 63 -> DungeonRoomDatabase.RoomKind.NORMAL;
            default -> previous != null ? previous.kind() : DungeonRoomDatabase.RoomKind.UNKNOWN;
        };
    }

    private String defaultNameForKind(DungeonRoomDatabase.RoomKind kind) {
        return switch (kind) {
            case BLOOD -> "Blood";
            case CHAMPION -> "Champion";
            case ENTRANCE -> "Entrance";
            case FAIRY -> "Fairy";
            case NORMAL -> "Normal";
            case PUZZLE -> "Puzzle";
            case RARE -> "Rare";
            case TRAP -> "Trap";
            case UNKNOWN -> "";
        };
    }

    private void pruneUnopenedRoomJoins(RoomSnapshot[][] roomGrid, int[][] nextH, int[][] nextV) {
        for (int z = 0; z < roomsZ; z++) {
            for (int x = 0; x < roomsX; x++) {
                RoomSnapshot room = roomGrid[z][x];
                if (room == null || !isUnopenedLikeColor(room.mapColor())) {
                    continue;
                }

                List<ConnectorRef> joinedToDiscovered = new ArrayList<>(4);
                if (x < roomsX - 1 && nextH[z][x] == CONNECTOR_MERGED && isDiscoveredLikeRoom(roomGrid[z][x + 1])) {
                    joinedToDiscovered.add(new ConnectorRef(true, z, x));
                }
                if (x > 0 && nextH[z][x - 1] == CONNECTOR_MERGED && isDiscoveredLikeRoom(roomGrid[z][x - 1])) {
                    joinedToDiscovered.add(new ConnectorRef(true, z, x - 1));
                }
                if (z < roomsZ - 1 && nextV[z][x] == CONNECTOR_MERGED && isDiscoveredLikeRoom(roomGrid[z + 1][x])) {
                    joinedToDiscovered.add(new ConnectorRef(false, z, x));
                }
                if (z > 0 && nextV[z - 1][x] == CONNECTOR_MERGED && isDiscoveredLikeRoom(roomGrid[z - 1][x])) {
                    joinedToDiscovered.add(new ConnectorRef(false, z - 1, x));
                }

                if (joinedToDiscovered.size() <= 1) {
                    continue;
                }

                for (int i = 1; i < joinedToDiscovered.size(); i++) {
                    ConnectorRef ref = joinedToDiscovered.get(i);
                    if (ref.horizontal()) {
                        nextH[ref.z()][ref.x()] = CONNECTOR_NONE;
                    } else {
                        nextV[ref.z()][ref.x()] = CONNECTOR_NONE;
                    }
                }
            }
        }
    }

    private static boolean isDiscoveredLikeRoom(RoomSnapshot room) {
        return room != null && !isUnopenedLikeColor(room.mapColor());
    }

    private static boolean isUnopenedLikeColor(int color) {
        return color == 0 || color == 82 || color == 85 || color == 119;
    }

    private int detectHorizontalConnector(MinecraftClient mc, RoomSnapshot[][] roomGrid, int betweenX, int rowZ, int previous) {
        RoomSnapshot left = roomGrid[rowZ][betweenX];
        RoomSnapshot right = roomGrid[rowZ][betweenX + 1];
        if (left == null || right == null || left.mapColor() == 0 || right.mapColor() == 0) {
            return CONNECTOR_NONE;
        }

        int joinColor = getHorizontalJoinColorValue(betweenX, rowZ);
        int mapDoorColor = getHorizontalDoorColorValue(betweenX, rowZ);
        if (
            mapDoorColor != 0 &&
            mapDoorColor != 18 &&
            mapDoorColor != 82 &&
            mapDoorColor != 85 &&
            mapDoorColor != 119 &&
            joinColor == mapDoorColor &&
            left.mapColor() == mapDoorColor &&
            right.mapColor() == mapDoorColor
        ) {
            return CONNECTOR_MERGED;
        }
        if (mapDoorColor == 82 || mapDoorColor == 85) {
            return CONNECTOR_UNKNOWN;
        }

        int mapDoorType = inferDoorTypeFromMapColor(mapDoorColor);
        if (mapDoorType != CONNECTOR_UNKNOWN) {
            return mapDoorType;
        }

        int[] leftWorld = mapRoomToWorld(betweenX, rowZ, roomTransformMode);
        int[] rightWorld = mapRoomToWorld(betweenX + 1, rowZ, roomTransformMode);
        int boundaryX = (leftWorld[0] + rightWorld[0]) / 2;
        int boundaryZ = leftWorld[1];

        int door = detectWorldDoorType(mc, boundaryX, boundaryZ, left, right);
        if (door == CONNECTOR_UNKNOWN) {
            return previous == CONNECTOR_UNKNOWN ? CONNECTOR_NONE : previous;
        }
        return door;
    }

    private int detectVerticalConnector(MinecraftClient mc, RoomSnapshot[][] roomGrid, int columnX, int betweenZ, int previous) {
        RoomSnapshot up = roomGrid[betweenZ][columnX];
        RoomSnapshot down = roomGrid[betweenZ + 1][columnX];
        if (up == null || down == null || up.mapColor() == 0 || down.mapColor() == 0) {
            return CONNECTOR_NONE;
        }

        int joinColor = getVerticalJoinColorValue(columnX, betweenZ);
        int mapDoorColor = getVerticalDoorColorValue(columnX, betweenZ);
        if (
            mapDoorColor != 0 &&
            mapDoorColor != 18 &&
            mapDoorColor != 82 &&
            mapDoorColor != 85 &&
            mapDoorColor != 119 &&
            joinColor == mapDoorColor &&
            up.mapColor() == mapDoorColor &&
            down.mapColor() == mapDoorColor
        ) {
            return CONNECTOR_MERGED;
        }
        if (mapDoorColor == 82 || mapDoorColor == 85) {
            return CONNECTOR_UNKNOWN;
        }

        int mapDoorType = inferDoorTypeFromMapColor(mapDoorColor);
        if (mapDoorType != CONNECTOR_UNKNOWN) {
            return mapDoorType;
        }

        int[] upWorld = mapRoomToWorld(columnX, betweenZ, roomTransformMode);
        int[] downWorld = mapRoomToWorld(columnX, betweenZ + 1, roomTransformMode);
        int boundaryX = upWorld[0];
        int boundaryZ = (upWorld[1] + downWorld[1]) / 2;

        int door = detectWorldDoorType(mc, boundaryX, boundaryZ, up, down);
        if (door == CONNECTOR_UNKNOWN) {
            return previous == CONNECTOR_UNKNOWN ? CONNECTOR_NONE : previous;
        }
        return door;
    }

    private int inferDoorTypeFromMapColor(int mapDoorColor) {
        return switch (mapDoorColor) {
            case 119 -> CONNECTOR_DOOR_WITHER;
            case 18 -> CONNECTOR_DOOR_BLOOD;
            case 82, 85 -> CONNECTOR_UNKNOWN;
            case 0 -> CONNECTOR_NONE;
            default -> CONNECTOR_DOOR_NORMAL;
        };
    }

    private boolean isPersistentConnector(int connectorType) {
        return connectorType == CONNECTOR_MERGED
            || connectorType == CONNECTOR_DOOR_NORMAL
            || connectorType == CONNECTOR_DOOR_BLOOD
            || connectorType == CONNECTOR_DOOR_WITHER;
    }

    private int detectWorldDoorType(MinecraftClient mc, int worldX, int worldZ, RoomSnapshot a, RoomSnapshot b) {
        if (mc == null || mc.world == null) {
            return CONNECTOR_UNKNOWN;
        }

        if (!isChunkLoaded(mc, worldX, worldZ)) {
            return CONNECTOR_UNKNOWN;
        }

        BlockPos center = new BlockPos(worldX, 69, worldZ);
        Block centerBlock = mc.world.getBlockState(center).getBlock();
        if (centerBlock == Blocks.AIR || centerBlock == Blocks.BARRIER) {
            return CONNECTOR_DOOR_NORMAL;
        }
        if (centerBlock == Blocks.COAL_BLOCK) {
            return CONNECTOR_DOOR_WITHER;
        }
        if (centerBlock == Blocks.RED_TERRACOTTA) {
            return CONNECTOR_DOOR_BLOOD;
        }

        return CONNECTOR_NONE;
    }

    private TransformResult pickBestRoomTransform(MinecraftClient mc) {
        int bestMode = roomTransformMode;
        int bestScore = Integer.MIN_VALUE;
        int bestKnown = 0;

        for (int mode = 0; mode < 4; mode++) {
            int score = 0;
            int known = 0;
            for (int rz = 0; rz < roomsZ; rz++) {
                for (int rx = 0; rx < roomsX; rx++) {
                    int roomColor = getRoomColorValue(rx, rz);
                    if (roomColor == 0) {
                        continue;
                    }

                    int[] worldPos = mapRoomToWorld(rx, rz, mode);
                    if (!isChunkLoaded(mc, worldPos[0], worldPos[1])) {
                        continue;
                    }

                    int core = getCoreAtRoomCenter(mc, worldPos[0], worldPos[1]);
                    DungeonRoomDatabase.RoomKind kind = roomDatabase.getKindForCore(core);
                    if (kind != DungeonRoomDatabase.RoomKind.UNKNOWN) {
                        score += 3;
                        known++;
                    }
                    if (roomColor == 18 && kind == DungeonRoomDatabase.RoomKind.BLOOD) {
                        score += 5;
                    }
                    if (roomColor == 30 && kind == DungeonRoomDatabase.RoomKind.ENTRANCE) {
                        score += 4;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestMode = mode;
                bestKnown = known;
            }
        }

        return new TransformResult(bestMode, bestScore, bestKnown);
    }

    private boolean shouldSwitchTransform(TransformResult candidate) {
        if (lastTransformScore == Integer.MIN_VALUE) {
            return true;
        }
        if (candidate.mode() == roomTransformMode) {
            return true;
        }

        // Prevent mirror-flip oscillation unless the new mode is meaningfully more confident.
        if (candidate.knownRooms() < 3) {
            return false;
        }
        return candidate.score() >= lastTransformScore + 6;
    }

    private int[] mapRoomToWorld(int roomX, int roomZ, int mode) {
        int tx = ((mode & 1) != 0) ? (roomsX - 1 - roomX) : roomX;
        int tz = ((mode & 2) != 0) ? (roomsZ - 1 - roomZ) : roomZ;
        return new int[] {
            ROOM_WORLD_START + tx * 32,
            ROOM_WORLD_START + tz * 32
        };
    }

    public float worldToMapNormalizedX(double worldX) {
        double n = (worldX - ROOM_WORLD_CORNER_START) / (roomsX * 32.0);
        if ((roomTransformMode & 1) != 0) {
            n = 1.0 - n;
        }
        return (float) n;
    }

    public float worldToMapNormalizedZ(double worldZ) {
        double n = (worldZ - ROOM_WORLD_CORNER_START) / (roomsZ * 32.0);
        if ((roomTransformMode & 2) != 0) {
            n = 1.0 - n;
        }
        return (float) n;
    }

    public int getRoomColorValue(int roomX, int roomZ) {
        if (!hasData()) {
            if (roomX < 0 || roomZ < 0 || roomZ >= cachedRoomColor.length || roomX >= cachedRoomColor[roomZ].length) {
                return 0;
            }
            return cachedRoomColor[roomZ][roomX];
        }
        int tileSize = roomSize + 4;
        int liveColor = getColorAt(startCoords.x + roomX * tileSize, startCoords.z + roomZ * tileSize);
        if (roomX >= 0 && roomZ >= 0 && roomZ < cachedRoomColor.length && roomX < cachedRoomColor[roomZ].length) {
            if (liveColor != 0) {
                cachedRoomColor[roomZ][roomX] = liveColor;
            } else {
                return cachedRoomColor[roomZ][roomX];
            }
        }
        return liveColor;
    }

    public int getRoomCenterColorValue(int roomX, int roomZ) {
        if (!hasData()) {
            return getRoomColorValue(roomX, roomZ);
        }
        int half = roomSize / 2;
        int tileSize = roomSize + 4;
        int centerColor = getColorAt(startCoords.x + roomX * tileSize + half, startCoords.z + roomZ * tileSize + half);
        if (centerColor == 0) {
            return getRoomColorValue(roomX, roomZ);
        }
        return centerColor;
    }

    public int getHorizontalDoorColorValue(int betweenX, int rowZ) {
        if (!hasData()) return 0;
        int tileSize = roomSize + 4;
        int doorX = startCoords.x + betweenX * tileSize + roomSize;
        int doorZ = startCoords.z + rowZ * tileSize + roomSize / 2;
        return getColorAt(doorX, doorZ);
    }

    public int getHorizontalJoinColorValue(int betweenX, int rowZ) {
        if (!hasData()) return 0;
        int tileSize = roomSize + 4;
        int joinX = startCoords.x + betweenX * tileSize + roomSize;
        int joinZ = startCoords.z + rowZ * tileSize;
        return getColorAt(joinX, joinZ);
    }

    public int getVerticalDoorColorValue(int columnX, int betweenZ) {
        if (!hasData()) return 0;
        int tileSize = roomSize + 4;
        int doorX = startCoords.x + columnX * tileSize + roomSize / 2;
        int doorZ = startCoords.z + betweenZ * tileSize + roomSize;
        return getColorAt(doorX, doorZ);
    }

    public int getVerticalJoinColorValue(int columnX, int betweenZ) {
        if (!hasData()) return 0;
        int tileSize = roomSize + 4;
        int joinX = startCoords.x + columnX * tileSize;
        int joinZ = startCoords.z + betweenZ * tileSize + roomSize;
        return getColorAt(joinX, joinZ);
    }

    private int getColorAt(int x, int z) {
        if (colors == null || x < 0 || z < 0 || x >= 128 || z >= 128) {
            return 0;
        }
        return colors[z * 128 + x] & 0xFF;
    }

    private int getCoreAtRoomCenter(MinecraftClient mc, int worldX, int worldZ) {
        if (!isChunkLoaded(mc, worldX, worldZ)) {
            return Integer.MIN_VALUE;
        }
        WorldChunk chunk = mc.world.getChunk(worldX >> 4, worldZ >> 4);
        return getCoreAtHeight(worldX, worldZ, 140, chunk);
    }

    private int getTopLayerAt(MinecraftClient mc, int worldX, int worldZ) {
        if (!isChunkLoaded(mc, worldX, worldZ)) {
            return 0;
        }
        WorldChunk chunk = mc.world.getChunk(worldX >> 4, worldZ >> 4);
        return getTopLayerOfRoom(worldX, worldZ, chunk);
    }

    private boolean isChunkLoaded(MinecraftClient mc, int worldX, int worldZ) {
        if (mc == null || mc.world == null) {
            return false;
        }
        return mc.world.isChunkLoaded(new BlockPos(worldX, 64, worldZ));
    }

    private int getTopLayerOfRoom(int worldX, int worldZ, WorldChunk chunk) {
        for (int y = 160; y >= 12; y--) {
            MUTABLE_POS.set(worldX, y, worldZ);
            Block block = chunk.getBlockState(MUTABLE_POS).getBlock();
            if (block != Blocks.AIR) {
                return block == Blocks.GOLD_BLOCK ? y - 1 : y;
            }
        }
        return 0;
    }

    private int getCoreAtHeight(int worldX, int worldZ, int roomHeight, WorldChunk chunk) {
        StringBuilder sb = new StringBuilder(768);
        for (int y = 140; y >= 12; y--) {
            MUTABLE_POS.set(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(MUTABLE_POS);
            Block block = state.getBlock();

            if (block == Blocks.OAK_PLANKS || block == Blocks.TRAPPED_CHEST || block == Blocks.CHEST || block == Blocks.IRON_BARS) {
                sb.append('0');
                continue;
            }

            int stateId = Block.getRawIdFromState(state);
            sb.append(Math.max(stateId, 0));
        }

        return sb.toString().hashCode();
    }

    private byte[] tryExtractColorsFromWorld(Object world) {
        try {
            for (Field field : world.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(world);
                if (!(value instanceof Map<?, ?> map)) {
                    continue;
                }

                if (map.isEmpty()) {
                    continue;
                }

                for (Object state : map.values()) {
                    if (state == null) continue;
                    byte[] mapColors = findByteArray(state, 0, 4, Collections.newSetFromMap(new IdentityHashMap<>()));
                    if (mapColors == null || mapColors.length != MAP_PIXELS) continue;

                    if (looksLikeDungeonMap(mapColors)) {
                        return mapColors;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private List<PlayerMarker> extractMarkers(Object packet) {
        List<Object> decorations = findDecorations(packet);
        if (decorations.isEmpty()) {
            return Collections.emptyList();
        }

        List<PlayerMarker> result = new ArrayList<>();
        for (Object decoration : decorations) {
            if (decoration == null || isFrameDecoration(decoration) || !isPlayerDecoration(decoration)) {
                continue;
            }

            Integer x = readNumeric(decoration, "x");
            Integer z = readNumeric(decoration, "y");
            if (z == null) {
                z = readNumeric(decoration, "z");
            }
            Integer rot = readNumeric(decoration, "rot");

            if (x == null || z == null) {
                continue;
            }

            int mapX = clamp(Math.round((x + 126) * 0.5f), 0, 127);
            int mapZ = clamp(Math.round((z + 126) * 0.5f), 0, 127);
            String label = readTextLike(decoration, "name");
            result.add(new PlayerMarker(mapX, mapZ, rot == null ? 0 : rot, label == null ? "" : label));
        }

        return result;
    }

    private static List<Object> findDecorations(Object packet) {
        if (packet == null) {
            return Collections.emptyList();
        }

        Class<?> cls = packet.getClass();

        for (Method method : cls.getDeclaredMethods()) {
            try {
                if (method.getParameterCount() != 0) continue;
                if (!method.getName().toLowerCase().contains("decoration")) continue;
                method.setAccessible(true);
                Object value = method.invoke(packet);
                List<Object> out = toObjectList(value);
                if (!out.isEmpty()) {
                    return out;
                }
            } catch (Throwable ignored) {
            }
        }

        for (Field field : cls.getDeclaredFields()) {
            try {
                if (!field.getName().toLowerCase().contains("decoration")) continue;
                field.setAccessible(true);
                Object value = field.get(packet);
                List<Object> out = toObjectList(value);
                if (!out.isEmpty()) {
                    return out;
                }
            } catch (Throwable ignored) {
            }
        }

        return Collections.emptyList();
    }

    private static List<Object> toObjectList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }

        if (value instanceof Optional<?> opt) {
            value = opt.orElse(null);
            if (value == null) {
                return Collections.emptyList();
            }
        }

        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            out.addAll(list);
            return out;
        }

        if (value instanceof Iterable<?> iterable) {
            List<Object> out = new ArrayList<>();
            for (Object o : iterable) {
                out.add(o);
            }
            return out;
        }

        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }

        return Collections.emptyList();
    }

    private static boolean isFrameDecoration(Object decoration) {
        Object type = readMember(decoration, "type");
        if (type == null) {
            return false;
        }
        String text = type.toString().toUpperCase();
        return text.contains("FRAME");
    }

    private static boolean isPlayerDecoration(Object decoration) {
        Object type = readMember(decoration, "type");
        if (containsPlayerLikeToken(type)) {
            return true;
        }

        Object id = type == null ? null : readMember(type, "id");
        if (containsPlayerLikeToken(id)) {
            return true;
        }

        Object assetId = type == null ? null : readMember(type, "asset");
        if (containsPlayerLikeToken(assetId)) {
            return true;
        }

        if (containsPlayerLikeToken(readMember(decoration, "asset"))) {
            return true;
        }
        if (containsPlayerLikeToken(readMember(decoration, "id"))) {
            return true;
        }
        if (containsPlayerLikeToken(readMember(decoration, "name"))) {
            return true;
        }

        return false;
    }

    private static boolean containsPlayerLikeToken(Object value) {
        if (value == null) {
            return false;
        }
        String text = value.toString().toLowerCase();
        return text.contains("player") || text.contains("off_map") || text.contains("off_limits");
    }

    private static Integer readNumeric(Object object, String key) {
        Object value = readMember(object, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static String readTextLike(Object object, String key) {
        Object value = readMember(object, key);
        if (value == null) return null;
        return value.toString();
    }

    private static Object readMember(Object object, String key) {
        Class<?> cls = object.getClass();

        for (Method method : cls.getDeclaredMethods()) {
            try {
                if (method.getParameterCount() != 0) continue;
                String name = method.getName().toLowerCase();
                if (!name.contains(key)) continue;
                method.setAccessible(true);
                Object value = method.invoke(object);
                if (value instanceof Optional<?> opt) {
                    value = opt.orElse(null);
                }
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }

        for (Field field : cls.getDeclaredFields()) {
            try {
                String name = field.getName().toLowerCase();
                if (!name.contains(key)) continue;
                field.setAccessible(true);
                Object value = field.get(object);
                if (value instanceof Optional<?> opt) {
                    value = opt.orElse(null);
                }
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Pair findGreenRoom(byte[] mapData) {
        int start = -1;
        int length = 0;

        for (int i = 0; i < mapData.length; i++) {
            int color = mapData[i] & 0xFF;
            if (color == 30) {
                if (length == 0) {
                    start = i;
                }
                length++;
            } else {
                if (length >= 16) {
                    return new Pair(start, length);
                }
                length = 0;
            }
        }

        return new Pair(start, length);
    }

    private static boolean looksLikeDungeonMap(byte[] mapData) {
        int unopenedOrWither = 0;
        int cleared = 0;
        int green = 0;

        for (byte value : mapData) {
            int color = value & 0xFF;
            if (color == 85 || color == 119) unopenedOrWither++;
            if (color == 34) cleared++;
            if (color == 30) green++;
        }

        return green >= 16 && (unopenedOrWither > 8 || cleared > 8);
    }

    private byte[] tryExtractColors(Object packet, MinecraftClient mc) {
        if (packet == null) {
            return null;
        }

        String simple = packet.getClass().getSimpleName().toLowerCase();
        if (!simple.contains("map")) {
            return null;
        }

        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        byte[] direct = findByteArray(packet, 0, 4, visited);
        if (direct != null && direct.length == MAP_PIXELS) {
            return direct;
        }

        Object state = resolveMapState(packet, mc);
        if (state == null) {
            return null;
        }

        visited.clear();
        byte[] fromState = findByteArray(state, 0, 4, visited);
        return fromState != null && fromState.length == MAP_PIXELS ? fromState : null;
    }

    private static byte[] findByteArray(Object root, int depth, int maxDepth, Set<Object> visited) {
        if (root == null || depth > maxDepth || visited.contains(root)) {
            return null;
        }
        visited.add(root);

        if (root instanceof byte[] arr) {
            return arr;
        }

        Class<?> cls = root.getClass();
        if (cls.isPrimitive() || cls == String.class || Number.class.isAssignableFrom(cls) || cls.isEnum()) {
            return null;
        }

        for (Field field : cls.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(root);
                if (value instanceof byte[] arr && arr.length == MAP_PIXELS) {
                    return arr;
                }
            } catch (Throwable ignored) {
            }
        }

        for (Field field : cls.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(root);
                if (value == null) continue;

                if (value instanceof Optional<?> opt) {
                    value = opt.orElse(null);
                    if (value == null) continue;
                }

                byte[] nested = findByteArray(value, depth + 1, maxDepth, visited);
                if (nested != null) {
                    return nested;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Object resolveMapState(Object packet, MinecraftClient mc) {
        if (mc == null || mc.world == null) {
            return null;
        }

        Object mapId = findMapId(packet);
        if (mapId == null) {
            return null;
        }

        for (Method method : mc.world.getClass().getMethods()) {
            try {
                if (method.getParameterCount() != 1) continue;
                if (!method.getReturnType().getSimpleName().toLowerCase().contains("mapstate")) continue;

                Class<?> param = method.getParameterTypes()[0];
                if (!param.isAssignableFrom(mapId.getClass()) && !mapId.getClass().isAssignableFrom(param)) {
                    continue;
                }

                Object state = method.invoke(mc.world, mapId);
                if (state instanceof Optional<?> opt) {
                    state = opt.orElse(null);
                }
                if (state != null) {
                    return state;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Object findMapId(Object packet) {
        Class<?> cls = packet.getClass();

        for (Method method : cls.getDeclaredMethods()) {
            try {
                if (method.getParameterCount() != 0) continue;
                String methodName = method.getName().toLowerCase();
                String returnName = method.getReturnType().getSimpleName().toLowerCase();
                if (!methodName.contains("id") && !returnName.contains("mapid")) continue;

                method.setAccessible(true);
                Object value = method.invoke(packet);
                if (value != null && value.getClass().getSimpleName().toLowerCase().contains("mapid")) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }

        for (Field field : cls.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(packet);
                if (value != null && value.getClass().getSimpleName().toLowerCase().contains("mapid")) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private record Pair(int start, int length) {
    }

    public record RoomSnapshot(int roomId, DungeonRoomDatabase.RoomKind kind, String name, int mapColor) {
    }

    private record TransformResult(int mode, int score, int knownRooms) {
    }

    private record ConnectorRef(boolean horizontal, int z, int x) {
    }

    public record PlayerMarker(int mapX, int mapZ, int rotation, String label) {
    }
}
