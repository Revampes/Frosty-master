package com.revampes.Fault.modules.impl.dungeon.DungeonMap;

import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.events.impl.Render2DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.modules.impl.other.AntiBot;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DungeonMap extends Module {
    private static final Identifier GREEN_CHECK_ICON = Identifier.of("revampes", "dungeonmap/bloom_map_green_check.png");
    private static final Identifier WHITE_CHECK_ICON = Identifier.of("revampes", "dungeonmap/bloom_map_white_check.png");
    private static final Identifier FAILED_ICON = Identifier.of("revampes", "dungeonmap/bloom_map_failed_room.png");

    private final SliderSetting xPos = new SliderSetting("X Position", "%", 75, 0, 100, 1);
    private final SliderSetting yPos = new SliderSetting("Y Position", "%", 18, 0, 100, 1);
    private final SliderSetting mapScale = new SliderSetting("Map Scale", 1.0, 0.5, 4.0, 0.1);
    private final SliderSetting backgroundPadding = new SliderSetting("Background Padding", 3, 0, 12, 1);
    private final SliderSetting backgroundBorder = new SliderSetting("Background Border", 1, 0, 5, 1);
    private final SliderSetting doorThickness = new SliderSetting("Door Thickness", 9, 1, 16, 1);
    private final SliderSetting roomTextScale = new SliderSetting("Room Text Scale", 0.60, 0.25, 2.0, 0.05);
    private final SliderSetting teammateNameScale = new SliderSetting("Teammate Name Scale", 0.90, 0.25, 2.0, 0.05);
    private final SliderSetting playerHeadBackgroundSize = new SliderSetting("Player Head BG Size", 1, 0, 10, 1);

    private final ButtonSetting onlyInDungeon = new ButtonSetting("Only in Dungeon", true);
    private final ButtonSetting showRoomNames = new ButtonSetting("Show Room Names", true);
    private final ButtonSetting showCheckmarks = new ButtonSetting("Show Checkmarks", true);
    private final ButtonSetting showTeammateIcons = new ButtonSetting("Show Teammates", true);
    private final ButtonSetting showTeammateNames = new ButtonSetting("Show Teammate Names", false);

    private final ColorSetting backgroundColor = new ColorSetting("Background", new Color(0, 0, 0, 130));
    private final ColorSetting borderColor = new ColorSetting("Border", new Color(30, 30, 30, 200));

    private final ColorSetting unknownRoomColor = new ColorSetting("Unknown Room", new Color(30, 30, 30, 255));
    private final ColorSetting unopenedRoomColor = new ColorSetting("Unopened Room", new Color(52, 52, 52, 255));
    private final ColorSetting normalRoomColor = new ColorSetting("Normal Room", new Color(107, 58, 17, 255));
    private final ColorSetting bloodRoomColor = new ColorSetting("Blood Room", new Color(255, 0, 0, 255));
    private final ColorSetting puzzleRoomColor = new ColorSetting("Puzzle Room", new Color(117, 0, 133, 255));
    private final ColorSetting championRoomColor = new ColorSetting("Champion Room", new Color(254, 223, 0, 255));
    private final ColorSetting trapRoomColor = new ColorSetting("Trap Room", new Color(216, 127, 51, 255));
    private final ColorSetting entranceRoomColor = new ColorSetting("Entrance Room", new Color(20, 133, 0, 255));
    private final ColorSetting fairyRoomColor = new ColorSetting("Fairy Room", new Color(244, 19, 139, 255));
    private final ColorSetting rareRoomColor = new ColorSetting("Rare Room", new Color(255, 203, 89, 255));

    private final ColorSetting unopenedDoorColor = new ColorSetting("Unopened Door", new Color(30, 30, 30, 255));
    private final ColorSetting normalDoorColor = new ColorSetting("Normal Door", new Color(107, 58, 17, 255));
    private final ColorSetting bloodDoorColor = new ColorSetting("Blood Door", new Color(200, 40, 40, 255));
    private final ColorSetting witherDoorColor = new ColorSetting("Wither Door", new Color(25, 25, 25, 255));
    private final ColorSetting puzzleDoorColor = new ColorSetting("Puzzle Door", new Color(117, 0, 133, 255));
    private final ColorSetting championDoorColor = new ColorSetting("Champion Door", new Color(254, 223, 0, 255));
    private final ColorSetting trapDoorColor = new ColorSetting("Trap Door", new Color(216, 127, 51, 255));
    private final ColorSetting entranceDoorColor = new ColorSetting("Entrance Door", new Color(20, 133, 0, 255));
    private final ColorSetting fairyDoorColor = new ColorSetting("Fairy Door", new Color(244, 19, 139, 255));
    private final ColorSetting rareDoorColor = new ColorSetting("Rare Door", new Color(255, 203, 89, 255));

    private final ColorSetting teammateColor = new ColorSetting("Teammate Dot", new Color(255, 255, 255, 255));
    private final ColorSetting teammateNameColor = new ColorSetting("Teammate Name", new Color(70, 70, 70, 255));
    private final ColorSetting roomNameColor = new ColorSetting("Room Name", new Color(235, 235, 235, 255));

    private final DungeonMapState state = new DungeonMapState();
    private long lastWorldFallbackTryMs = 0L;
    private boolean wasInDungeon = false;
    private static final String[] NAME_FILTER = new String[] {
        "shadow assassin", "lost adventurer", "diamond guy", "crypt", "watcher", "mob", "prime", "skeletor", "skeleton",
        "zombie", "undead"

    };

    public DungeonMap() {
        super("DungeonMap", "Custom dungeon map HUD based on world scan + map packet data.", category.Dungeon);

        this.registerSetting(xPos);
        this.registerSetting(yPos);
        this.registerSetting(mapScale);
        this.registerSetting(backgroundPadding);
        this.registerSetting(backgroundBorder);
        this.registerSetting(doorThickness);
        this.registerSetting(roomTextScale);
        this.registerSetting(teammateNameScale);
        this.registerSetting(playerHeadBackgroundSize);

        this.registerSetting(onlyInDungeon);
        this.registerSetting(showRoomNames);
        this.registerSetting(showCheckmarks);
        this.registerSetting(showTeammateIcons);
        this.registerSetting(showTeammateNames);

        this.registerSetting(backgroundColor);
        this.registerSetting(borderColor);

        this.registerSetting(unknownRoomColor);
        this.registerSetting(unopenedRoomColor);
        this.registerSetting(normalRoomColor);
        this.registerSetting(bloodRoomColor);
        this.registerSetting(puzzleRoomColor);
        this.registerSetting(championRoomColor);
        this.registerSetting(trapRoomColor);
        this.registerSetting(entranceRoomColor);
        this.registerSetting(fairyRoomColor);
        this.registerSetting(rareRoomColor);

        this.registerSetting(unopenedDoorColor);
        this.registerSetting(normalDoorColor);
        this.registerSetting(bloodDoorColor);
        this.registerSetting(witherDoorColor);
        this.registerSetting(puzzleDoorColor);
        this.registerSetting(championDoorColor);
        this.registerSetting(trapDoorColor);
        this.registerSetting(entranceDoorColor);
        this.registerSetting(fairyDoorColor);
        this.registerSetting(rareDoorColor);

        this.registerSetting(teammateColor);
        this.registerSetting(teammateNameColor);
        this.registerSetting(roomNameColor);

        showTeammateNames.setVisibilityCondition(showTeammateIcons::isToggled);
    }

    @Override
    public void onDisable() {
        state.clear();
        wasInDungeon = false;
    }

    @EventHandler
    public void onReceivePacket(ReceivePacketEvent event) {
        Object packet = event.getPacket();
        if (packet == null || !packet.getClass().getSimpleName().toLowerCase().contains("map")) {
            return;
        }

        if (!isEnabled() || mc == null) {
            return;
        }

        mc.execute(() -> {
            if (mc.world == null || !isEnabled()) {
                return;
            }
            if (onlyInDungeon.isToggled() && !DungeonUtils.isInDungeon()) {
                return;
            }
            state.updateFromPacket(packet, mc);
        });
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }

        boolean inDungeon = DungeonUtils.isInDungeon();
        if (mc.world == null || (onlyInDungeon.isToggled() && !inDungeon)) {
            if (wasInDungeon || state.hasData()) {
                state.clear();
            }
            wasInDungeon = false;
            return;
        }
        wasInDungeon = inDungeon;

        int mapX = (int) ((event.screenWidth * xPos.getInput()) / 100.0);
        int mapY = (int) ((event.screenHeight * yPos.getInput()) / 100.0);

        long now = System.currentTimeMillis();
        if (now - lastWorldFallbackTryMs > 250L) {
            state.updateFromWorld(mc);
            lastWorldFallbackTryMs = now;
        }

        if (!state.hasData()) {
            int w = 220;
            int h = 30;
            event.drawContext.fill(mapX, mapY, mapX + w, mapY + h, backgroundColor.getRGB());
            event.drawContext.drawText(mc.textRenderer, "DungeonMap: waiting for map data", mapX + 4, mapY + 4, roomNameColor.getRGB(), true);
            String diag = "packets=" + state.getMapPacketsSeen() + " parsed=" + state.getMapPacketsParsed() + " fallback=" + state.getWorldFallbackHits();
            event.drawContext.drawText(mc.textRenderer, diag, mapX + 4, mapY + 16, roomNameColor.getRGB(), true);
            return;
        }

        int roomsX = state.getRoomsX();
        int roomsZ = state.getRoomsZ();
        int roomPx = Math.max(4, (int) Math.round(16 * mapScale.getInput()));
        int gapPx = Math.max(1, (int) Math.round(4 * mapScale.getInput()));
        int doorPx = Math.max(1, (int) Math.round(doorThickness.getInput() * mapScale.getInput()));

        int mapWidth = roomsX * roomPx + (roomsX - 1) * gapPx;
        int mapHeight = roomsZ * roomPx + (roomsZ - 1) * gapPx;
        int padding = (int) Math.round(backgroundPadding.getInput());
        int border = (int) Math.round(backgroundBorder.getInput());

        drawMapBackground(event, mapX, mapY, mapWidth, mapHeight, padding, border);

        for (int rz = 0; rz < roomsZ; rz++) {
            for (int rx = 0; rx < roomsX; rx++) {
                DungeonMapState.RoomSnapshot room = state.getRoom(rx, rz);
                int roomCode = state.getRoomColorValue(rx, rz);
                if (roomCode == 0 && room == null) {
                    continue;
                }

                int x1 = mapX + rx * (roomPx + gapPx);
                int y1 = mapY + rz * (roomPx + gapPx);
                int roomColor = getRoomColor(room, roomCode);
                event.drawContext.fill(x1, y1, x1 + roomPx, y1 + roomPx, roomColor);

                if (showCheckmarks.isToggled()) {
                    drawRoomCheckmark(event, x1, y1, roomPx, roomCode, room);
                }

                if (showRoomNames.isToggled() && room != null && room.name() != null && !room.name().isBlank() && roomCode != 82 && roomCode != 85 && roomCode != 119) {
                    drawRoomName(event, room.name(), x1, y1, roomPx);
                }

                if (rx < roomsX - 1) {
                    int cx1 = x1 + roomPx;
                    int cy1 = y1 + Math.max(0, (roomPx - doorPx) / 2);
                    int cy2 = y1 + Math.min(roomPx, (roomPx + doorPx) / 2);
                    int type = state.getHorizontalConnectionType(rx, rz);
                    int rightCode = state.getRoomColorValue(rx + 1, rz);
                    if (rightCode != 0) {
                        if (type == DungeonMapState.CONNECTOR_MERGED) {
                            event.drawContext.fill(cx1, y1, cx1 + gapPx, y1 + roomPx, roomColor);
                        } else {
                            int mapDoorCode = state.getHorizontalDoorColorValue(rx, rz);
                            if (shouldRenderDoor(type, mapDoorCode)) {
                                event.drawContext.fill(cx1, cy1, cx1 + gapPx, cy2, getDoorColor(type, mapDoorCode, room, state.getRoom(rx + 1, rz)));
                            }
                        }
                    }
                }

                if (rz < roomsZ - 1) {
                    int cy1 = y1 + roomPx;
                    int cx1 = x1 + Math.max(0, (roomPx - doorPx) / 2);
                    int cx2 = x1 + Math.min(roomPx, (roomPx + doorPx) / 2);
                    int type = state.getVerticalConnectionType(rx, rz);
                    int downCode = state.getRoomColorValue(rx, rz + 1);
                    if (downCode != 0) {
                        if (type == DungeonMapState.CONNECTOR_MERGED) {
                            event.drawContext.fill(x1, cy1, x1 + roomPx, cy1 + gapPx, roomColor);
                        } else {
                            int mapDoorCode = state.getVerticalDoorColorValue(rx, rz);
                            if (shouldRenderDoor(type, mapDoorCode)) {
                                event.drawContext.fill(cx1, cy1, cx2, cy1 + gapPx, getDoorColor(type, mapDoorCode, room, state.getRoom(rx, rz + 1)));
                            }
                        }
                    }
                }
            }
        }

        // Fill tiny center holes for fully merged 2x2 rooms.
        for (int rz = 0; rz < roomsZ - 1; rz++) {
            for (int rx = 0; rx < roomsX - 1; rx++) {
                if (state.getHorizontalConnectionType(rx, rz) != DungeonMapState.CONNECTOR_MERGED) {
                    continue;
                }
                if (state.getHorizontalConnectionType(rx, rz + 1) != DungeonMapState.CONNECTOR_MERGED) {
                    continue;
                }
                if (state.getVerticalConnectionType(rx, rz) != DungeonMapState.CONNECTOR_MERGED) {
                    continue;
                }
                if (state.getVerticalConnectionType(rx + 1, rz) != DungeonMapState.CONNECTOR_MERGED) {
                    continue;
                }

                DungeonMapState.RoomSnapshot topLeft = state.getRoom(rx, rz);
                if (topLeft == null || state.getRoomColorValue(rx, rz) == 0) {
                    continue;
                }

                int x1 = mapX + rx * (roomPx + gapPx) + roomPx;
                int y1 = mapY + rz * (roomPx + gapPx) + roomPx;
                int centerColor = getRoomColor(topLeft, state.getRoomColorValue(rx, rz));
                event.drawContext.fill(x1, y1, x1 + gapPx, y1 + gapPx, centerColor);
            }
        }

        if (showTeammateIcons.isToggled()) {
            renderTeammates(event, mapX, mapY, mapWidth, mapHeight);
        }
    }

    private void drawMapBackground(Render2DEvent event, int mapX, int mapY, int mapWidth, int mapHeight, int padding, int border) {
        int x1 = mapX - padding;
        int y1 = mapY - padding;
        int x2 = mapX + mapWidth + padding;
        int y2 = mapY + mapHeight + padding;

        event.drawContext.fill(x1, y1, x2, y2, backgroundColor.getRGB());
        if (border <= 0) {
            return;
        }

        int c = borderColor.getRGB();
        event.drawContext.fill(x1, y1, x2, y1 + border, c);
        event.drawContext.fill(x1, y2 - border, x2, y2, c);
        event.drawContext.fill(x1, y1, x1 + border, y2, c);
        event.drawContext.fill(x2 - border, y1, x2, y2, c);
    }

    private int getRoomColor(DungeonMapState.RoomSnapshot room, int mapColorCode) {
        if (mapColorCode == 0) {
            return unknownRoomColor.getRGB();
        }
        if (mapColorCode == 82 || mapColorCode == 85 || mapColorCode == 119) {
            return unopenedRoomColor.getRGB();
        }

        if (mapColorCode == 18 && room != null && room.kind() == DungeonRoomDatabase.RoomKind.BLOOD) {
            return bloodRoomColor.getRGB();
        }
        if (mapColorCode == 18 && room == null) {
            return bloodRoomColor.getRGB();
        }

        DungeonRoomDatabase.RoomKind kind = room == null ? DungeonRoomDatabase.RoomKind.UNKNOWN : room.kind();
        return switch (kind) {
            case BLOOD -> bloodRoomColor.getRGB();
            case CHAMPION -> championRoomColor.getRGB();
            case ENTRANCE -> entranceRoomColor.getRGB();
            case FAIRY -> fairyRoomColor.getRGB();
            case PUZZLE -> puzzleRoomColor.getRGB();
            case RARE -> rareRoomColor.getRGB();
            case TRAP -> trapRoomColor.getRGB();
            case NORMAL -> normalRoomColor.getRGB();
            case UNKNOWN -> unknownRoomColor.getRGB();
        };
    }

    private int getDoorColor(int connectorType, int mapDoorCode, DungeonMapState.RoomSnapshot roomA, DungeonMapState.RoomSnapshot roomB) {
        return switch (connectorType) {
            case DungeonMapState.CONNECTOR_DOOR_BLOOD -> bloodDoorColor.getRGB();
            case DungeonMapState.CONNECTOR_DOOR_WITHER -> witherDoorColor.getRGB();
            case DungeonMapState.CONNECTOR_DOOR_NORMAL -> dynamicNormalDoor(roomA, roomB);
            case DungeonMapState.CONNECTOR_UNKNOWN -> getMapDoorFallbackColor(mapDoorCode, roomA, roomB);
            default -> unopenedDoorColor.getRGB();
        };
    }

    private boolean shouldRenderDoor(int connectorType, int mapDoorCode) {
        if (connectorType == DungeonMapState.CONNECTOR_NONE) {
            return false;
        }
        if (connectorType == DungeonMapState.CONNECTOR_DOOR_NORMAL || connectorType == DungeonMapState.CONNECTOR_DOOR_BLOOD || connectorType == DungeonMapState.CONNECTOR_DOOR_WITHER) {
            return true;
        }
        return mapDoorCode == 18 || mapDoorCode == 82 || mapDoorCode == 85 || mapDoorCode == 119;
    }

    private int getMapDoorFallbackColor(int mapDoorCode, DungeonMapState.RoomSnapshot roomA, DungeonMapState.RoomSnapshot roomB) {
        return switch (mapDoorCode) {
            case 119 -> witherDoorColor.getRGB();
            case 18 -> bloodDoorColor.getRGB();
            case 0, 82, 85 -> unopenedDoorColor.getRGB();
            default -> dynamicNormalDoor(roomA, roomB);
        };
    }

    private int dynamicNormalDoor(DungeonMapState.RoomSnapshot roomA, DungeonMapState.RoomSnapshot roomB) {
        DungeonRoomDatabase.RoomKind chosen = pickSpecialKind(roomA, roomB);
        return switch (chosen) {
            case BLOOD -> bloodDoorColor.getRGB();
            case CHAMPION -> championDoorColor.getRGB();
            case ENTRANCE -> entranceDoorColor.getRGB();
            case FAIRY -> fairyDoorColor.getRGB();
            case PUZZLE -> puzzleDoorColor.getRGB();
            case RARE -> rareDoorColor.getRGB();
            case TRAP -> trapDoorColor.getRGB();
            case NORMAL, UNKNOWN -> normalDoorColor.getRGB();
        };
    }

    private DungeonRoomDatabase.RoomKind pickSpecialKind(DungeonMapState.RoomSnapshot a, DungeonMapState.RoomSnapshot b) {
        DungeonRoomDatabase.RoomKind ak = a == null ? DungeonRoomDatabase.RoomKind.UNKNOWN : a.kind();
        DungeonRoomDatabase.RoomKind bk = b == null ? DungeonRoomDatabase.RoomKind.UNKNOWN : b.kind();

        DungeonRoomDatabase.RoomKind[] priority = new DungeonRoomDatabase.RoomKind[] {
            DungeonRoomDatabase.RoomKind.ENTRANCE,
            DungeonRoomDatabase.RoomKind.BLOOD,
            DungeonRoomDatabase.RoomKind.CHAMPION,
            DungeonRoomDatabase.RoomKind.PUZZLE,
            DungeonRoomDatabase.RoomKind.RARE,
            DungeonRoomDatabase.RoomKind.TRAP,
            DungeonRoomDatabase.RoomKind.FAIRY
        };

        for (DungeonRoomDatabase.RoomKind kind : priority) {
            if (ak == kind || bk == kind) {
                return kind;
            }
        }

        if (ak != DungeonRoomDatabase.RoomKind.UNKNOWN) {
            return ak;
        }
        if (bk != DungeonRoomDatabase.RoomKind.UNKNOWN) {
            return bk;
        }
        return DungeonRoomDatabase.RoomKind.NORMAL;
    }

    private void drawRoomCheckmark(Render2DEvent event, int x1, int y1, int roomPx, int mapCode, DungeonMapState.RoomSnapshot room) {
        Identifier icon = null;
        if (mapCode == 30) {
            icon = GREEN_CHECK_ICON;
        } else if (mapCode == 34) {
            icon = WHITE_CHECK_ICON;
        } else if (mapCode == 18 && (room == null || room.kind() != DungeonRoomDatabase.RoomKind.BLOOD)) {
            icon = FAILED_ICON;
        }

        if (icon == null) {
            return;
        }

        int size = Math.max(6, roomPx - 4);
        int ix = x1 + (roomPx - size) / 2;
        int iy = y1 + (roomPx - size) / 2;
        event.drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, icon, ix, iy, 0.0f, 0.0f, size, size, size, size);
    }

    private void drawRoomName(Render2DEvent event, String name, int x1, int y1, int roomPx) {
        String shortName = shortenRoomName(name);
        int textW = mc.textRenderer.getWidth(shortName);
        int scaledTx = x1 + (roomPx - Math.round(textW * (float) roomTextScale.getInput())) / 2;
        int scaledTy = y1 + roomPx - Math.max(6, Math.round(mc.textRenderer.fontHeight * (float) roomTextScale.getInput())) - 1;

        event.drawContext.getMatrices().pushMatrix();
        event.drawContext.getMatrices().translate(scaledTx, scaledTy);
        event.drawContext.getMatrices().scale((float) roomTextScale.getInput(), (float) roomTextScale.getInput());
        event.drawContext.drawText(mc.textRenderer, shortName, 0, 0, roomNameColor.getRGB(), true);
        event.drawContext.getMatrices().popMatrix();
    }

    private String shortenRoomName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] split = name.trim().split("\\s+");
        if (split.length == 1) {
            return split[0].length() > 10 ? split[0].substring(0, 10) : split[0];
        }

        StringBuilder out = new StringBuilder();
        for (String part : split) {
            if (!part.isBlank()) {
                out.append(part.charAt(0));
            }
        }
        return out.toString();
    }

    private void renderTeammates(Render2DEvent event, int mapX, int mapY, int mapWidth, int mapHeight) {
        if (mc.world == null) {
            return;
        }

        int startX = state.getMapStartX();
        int startZ = state.getMapStartZ();
        int mapPixelW = Math.max(1, state.getMapPixelWidth());
        int mapPixelH = Math.max(1, state.getMapPixelHeight());

        List<DungeonMapState.PlayerMarker> markers = state.getMarkers();
        List<? extends PlayerEntity> players = mc.world.getPlayers();
        int dotColor = teammateColor.getRGB();

        Set<Integer> usedMarkerIndices = new HashSet<>();
        List<DungeonMapState.PlayerMarker> validMarkers = new ArrayList<>();
        for (DungeonMapState.PlayerMarker marker : markers) {
            float markerNx = (marker.mapX() - startX) / (float) mapPixelW;
            float markerNz = (marker.mapZ() - startZ) / (float) mapPixelH;
            if (markerNx < -0.50f || markerNx > 1.50f || markerNz < -0.50f || markerNz > 1.50f) {
                continue;
            }
            validMarkers.add(marker);
        }

        for (PlayerEntity player : players) {
            if (!isRenderablePlayer(player)) {
                continue;
            }

            double nx = state.worldToMapNormalizedX(player.getX());
            double nz = state.worldToMapNormalizedZ(player.getZ());

            if (nx < -0.50 || nx > 1.50 || nz < -0.50 || nz > 1.50) {
                continue;
            }

            DungeonMapState.PlayerMarker bestMarker = null;
            int bestIndex = -1;
            double bestDist = Double.MAX_VALUE;
            String playerName = player.getName() == null ? "" : player.getName().getString().toLowerCase();

            for (int i = 0; i < validMarkers.size(); i++) {
                if (usedMarkerIndices.contains(i)) {
                    continue;
                }

                DungeonMapState.PlayerMarker marker = validMarkers.get(i);
                float markerNx = (marker.mapX() - startX) / (float) mapPixelW;
                float markerNz = (marker.mapZ() - startZ) / (float) mapPixelH;
                double dx = markerNx - nx;
                double dz = markerNz - nz;
                double dist = dx * dx + dz * dz;

                String label = marker.label() == null ? "" : marker.label().toLowerCase();
                boolean labelMatch = !playerName.isBlank() && label.contains(playerName);
                if (!labelMatch && dist > 0.0105) {
                    continue;
                }

                double score = labelMatch ? dist - 1.0 : dist;
                if (score < bestDist) {
                    bestDist = score;
                    bestMarker = marker;
                    bestIndex = i;
                }
            }

            if (bestIndex >= 0) {
                usedMarkerIndices.add(bestIndex);
            }

            if (bestMarker != null) {
                nx = Math.max(0.0, Math.min(1.0, (bestMarker.mapX() - startX) / (double) mapPixelW));
                nz = Math.max(0.0, Math.min(1.0, (bestMarker.mapZ() - startZ) / (double) mapPixelH));
            } else {
                nx = Math.max(0.0, Math.min(1.0, nx));
                nz = Math.max(0.0, Math.min(1.0, nz));
            }

            int px = mapX + MathHelper.floor((float) nx * mapWidth);
            int py = mapY + MathHelper.floor((float) nz * mapHeight);
            int color = player == mc.player ? 0xFF55FFFF : dotColor;
            int bg = player == mc.player ? 0xFF2B4D4D : 0xFF202020;
            int half = 2 + (int) Math.round(playerHeadBackgroundSize.getInput());

            if (half > 2) {
                event.drawContext.fill(px - half, py - half, px + half, py + half, bg);
            }
            event.drawContext.fill(px - 2, py - 2, px + 2, py + 2, color);

            if (showTeammateNames.isToggled() && player.getName() != null) {
                drawTeammateName(event, player.getName().getString(), px, py + 4);
            }
        }
    }

    private boolean isRenderablePlayer(PlayerEntity player) {
        if (player == null || mc == null || mc.player == null) {
            return false;
        }
        if (player == mc.player) {
            return true;
        }
        if (AntiBot.isBot(player) || isNpcLike(player)) {
            return false;
        }

        String display = player.getDisplayName() == null ? "" : player.getDisplayName().getString();
        String name = player.getName() == null ? "" : player.getName().getString();
        String displayLower = display.toLowerCase();
        String nameLower = name.toLowerCase();
        for (String keyword : NAME_FILTER) {
            if (displayLower.contains(keyword) || nameLower.contains(keyword)) {
                return false;
            }
        }
        if (displayLower.contains("[lv]") || nameLower.contains("[lv]")) {
            return false;
        }

        if (player.isInvisible()) {
            boolean hasEquipment = false;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (!player.getEquippedStack(slot).isEmpty()) {
                    hasEquipment = true;
                    break;
                }
            }
            if (!hasEquipment) {
                return false;
            }
        }

        return true;
    }

    private boolean isNpcLike(PlayerEntity player) {
        if (!(player instanceof OtherClientPlayerEntity)) {
            return false;
        }

        try {
            return player.getUuid() != null
                && player.getUuid().version() == 2
                && Math.abs(player.getHealth() - 20.0f) < 0.001f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void drawTeammateName(Render2DEvent event, String text, int x, int y) {
        int tw = mc.textRenderer.getWidth(text);
        int tx = x - Math.round(tw * (float) teammateNameScale.getInput() / 2.0f);

        event.drawContext.getMatrices().pushMatrix();
        event.drawContext.getMatrices().translate(tx, y);
        event.drawContext.getMatrices().scale((float) teammateNameScale.getInput(), (float) teammateNameScale.getInput());
        event.drawContext.drawText(mc.textRenderer, text, 0, 0, teammateNameColor.getRGB(), true);
        event.drawContext.getMatrices().popMatrix();
    }

}
