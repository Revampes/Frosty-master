package com.revampes.Fault.modules.impl.dungeon.DungeonMap;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class DungeonMap extends Module {
    private static final Identifier GREEN_CHECK_ICON = Identifier.of("revampes", "dungeonmap/bloom_map_green_check.png");
    private static final Identifier WHITE_CHECK_ICON = Identifier.of("revampes", "dungeonmap/bloom_map_white_check.png");
    private static final Identifier FAILED_ICON = Identifier.of("revampes", "dungeonmap/bloom_map_failed_room.png");
    private static final Identifier QUESTION_MARK_ICON = Identifier.of("revampes", "dungeonmap/bloom_map_question_mark.png");

    private final SliderSetting xPos = new SliderSetting("X Position", "%", 75, 0, 100, 1);
    private final SliderSetting yPos = new SliderSetting("Y Position", "%", 18, 0, 100, 1);
    private final SliderSetting mapScale = new SliderSetting("Map Scale", 1.0, 0.5, 4.0, 0.1);
    private final SliderSetting backgroundPadding = new SliderSetting("Background Padding", 3, 0, 12, 1);
    private final SliderSetting backgroundBorder = new SliderSetting("Background Border", 1, 0, 5, 1);
    private final SliderSetting doorThickness = new SliderSetting("Door Thickness", 9, 1, 16, 1);
    private final SliderSetting roomTextScale = new SliderSetting("Room Text Scale", 0.60, 0.25, 2.0, 0.05);
    private final SliderSetting teammateNameScale = new SliderSetting("Teammate Name Scale", 0.90, 0.25, 2.0, 0.05);
    private final SliderSetting teammateNameGap = new SliderSetting("Teammate Name Gap", "px", 2, -20, 40, 1);
    private final SliderSetting playerHeadSize = new SliderSetting("Player Head Size", 1.0, 0.5, 3.0, 0.05);
    private final SliderSetting playerHeadBackgroundSize = new SliderSetting("Player Head BG Size", 1, 0, 10, 1);
    private final SliderSetting headBorderWidth = new SliderSetting("Head Border Width", "px", 1, 0, 6, 1);

    private final ButtonSetting onlyInDungeon = new ButtonSetting("Only in Dungeon", true);
    private final ButtonSetting disableInBoss = new ButtonSetting("Disable in Boss", true);
    private final ButtonSetting showRoomNames = new ButtonSetting("Show Room Names", true);
    private final ButtonSetting showCheckmarks = new ButtonSetting("Show Checkmarks", true);
    private final ButtonSetting showTeammateIcons = new ButtonSetting("Show Teammates", true);
    private final ButtonSetting alwaysShowTeammateNames = new ButtonSetting("Always show names", false);
    private final ButtonSetting holdLeapToShowNames = new ButtonSetting("Hold leap to show names", false);
    private final ButtonSetting headBorder = new ButtonSetting("Head Border", false);

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
    private final ColorSetting selfHeadBorderColor = new ColorSetting("My Head Border", new Color(85, 255, 255, 255));
    private final ColorSetting teammateHeadBorderColor = new ColorSetting("Teammate Head Border", new Color(255, 255, 255, 255));

    private final DungeonMapState state = new DungeonMapState();
    private long lastWorldFallbackTryMs = 0L;
    private boolean wasInDungeon = false;
    private int lastWorldIdentityHash = Integer.MIN_VALUE;
    private final Map<Integer, String> markerIndexNameCache = new HashMap<>();
    private final Map<String, SmoothedMarkerPoint> markerSmoothingCache = new HashMap<>();
    private static final float MARKER_SMOOTH_FACTOR = 0.35f;
    private static final int MARKER_SMOOTH_SNAP_DISTANCE_PX = 28;
    private static final long MARKER_SMOOTH_STALE_MS = 3500L;
    private static final Set<String> RANK_TOKENS = Set.of(
        "VIP", "MVP", "ADMIN", "MOD", "HELPER", "GM", "YT", "PIG", "NONE"
    );
    private static final String[] NAME_FILTER = new String[] {
        "the watcher", "bonzo", "scarf", "livid", "sadan", "lost adventurer", "angry archaeologist", "redstone warrior",
        "shadow assassin", "king midas", "frozen adventurer", "crypt lurker", "crypt undead", "crypt dreadlord",
        "tank zombie", "super tank zombie", "zombie grunt", "zombie soldier", "zombie knight", "zombie commander",
        "zombie lord", "undead skeleton", "scared skeleton", "skeleton grunt", "skeleton master", "skeleton lord",
        "sniper", "crypt souleater", "lonely spider", "cellar spider", "withermancer", "skeletor", "skeletor prime",
        "super archer", "fels", "mimic", "deathmite", "blaze", "bat", "prince", "fairy", "revoker", "psycho",
        "reaper", "parasite", "cannibal", "mute", "ooze", "putrid", "freak", "leech", "flamer", "tear", "skull",
        "mr. dead", "vader", "frost", "walker", "wandering soul", "giant", "angry archaeologist"
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
        this.registerSetting(teammateNameGap);
        this.registerSetting(playerHeadSize);
        this.registerSetting(playerHeadBackgroundSize);
        this.registerSetting(headBorderWidth);

        this.registerSetting(onlyInDungeon);
        this.registerSetting(disableInBoss);
        this.registerSetting(showRoomNames);
        this.registerSetting(showCheckmarks);
        this.registerSetting(showTeammateIcons);
        this.registerSetting(alwaysShowTeammateNames);
        this.registerSetting(holdLeapToShowNames);
        this.registerSetting(headBorder);

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
        this.registerSetting(selfHeadBorderColor);
        this.registerSetting(teammateHeadBorderColor);

        playerHeadSize.setVisibilityCondition(showTeammateIcons::isToggled);
        playerHeadBackgroundSize.setVisibilityCondition(showTeammateIcons::isToggled);
        headBorder.setVisibilityCondition(showTeammateIcons::isToggled);
        alwaysShowTeammateNames.setVisibilityCondition(showTeammateIcons::isToggled);
        holdLeapToShowNames.setVisibilityCondition(showTeammateIcons::isToggled);
        teammateNameGap.setVisibilityCondition(() -> showTeammateIcons.isToggled() && (alwaysShowTeammateNames.isToggled() || holdLeapToShowNames.isToggled()));
        headBorderWidth.setVisibilityCondition(() -> showTeammateIcons.isToggled() && headBorder.isToggled());
        selfHeadBorderColor.setVisibilityCondition(() -> showTeammateIcons.isToggled() && headBorder.isToggled());
        teammateHeadBorderColor.setVisibilityCondition(() -> showTeammateIcons.isToggled() && headBorder.isToggled());
    }

    @Override
    public void onDisable() {
        state.clear();
        markerIndexNameCache.clear();
        markerSmoothingCache.clear();
        wasInDungeon = false;
        lastWorldIdentityHash = Integer.MIN_VALUE;
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
        if (shouldHideForPlayerList()) {
            return;
        }

        if (mc.world == null) {
            if (wasInDungeon || state.hasData()) {
                state.clear();
                markerIndexNameCache.clear();
                markerSmoothingCache.clear();
            }
            wasInDungeon = false;
            lastWorldIdentityHash = Integer.MIN_VALUE;
            return;
        }

        int worldIdentityHash = System.identityHashCode(mc.world);
        if (worldIdentityHash != lastWorldIdentityHash) {
            if (state.hasData()) {
                state.clear();
                markerIndexNameCache.clear();
                markerSmoothingCache.clear();
            }
            lastWorldFallbackTryMs = 0L;
            wasInDungeon = false;
            lastWorldIdentityHash = worldIdentityHash;
        }

        boolean inDungeon = DungeonUtils.isInDungeon();
        if (!inDungeon) {
            if (wasInDungeon || state.hasData()) {
                state.clear();
                markerIndexNameCache.clear();
                markerSmoothingCache.clear();
            }
            wasInDungeon = false;
            return;
        }
        wasInDungeon = inDungeon;

        if (disableInBoss.isToggled() && DungeonUtils.inBoss()) {
            return;
        }

        int mapX = (int) ((event.screenWidth * xPos.getInput()) / 100.0);
        int mapY = (int) ((event.screenHeight * yPos.getInput()) / 100.0);

        long now = System.currentTimeMillis();
        if (now - lastWorldFallbackTryMs > 250L) {
            state.updateFromWorld(mc);
            lastWorldFallbackTryMs = now;
        }

        if (!state.hasData()) {
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
                int roomCenterCode = state.getRoomCenterColorValue(rx, rz);
                if (roomCode == 0 && room == null) {
                    continue;
                }

                int x1 = mapX + rx * (roomPx + gapPx);
                int y1 = mapY + rz * (roomPx + gapPx);
                int roomColor = getRoomColor(room, roomCode);
                event.drawContext.fill(x1, y1, x1 + roomPx, y1 + roomPx, roomColor);

                if (showCheckmarks.isToggled()) {
                    drawRoomCheckmark(event, x1, y1, roomPx, roomCenterCode, roomCode, room);
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
        DungeonRoomDatabase.RoomKind kind = room == null ? DungeonRoomDatabase.RoomKind.UNKNOWN : room.kind();

        if (kind == DungeonRoomDatabase.RoomKind.FAIRY) {
            return fairyRoomColor.getRGB();
        }
        if (kind == DungeonRoomDatabase.RoomKind.ENTRANCE) {
            return entranceRoomColor.getRGB();
        }
        if (kind == DungeonRoomDatabase.RoomKind.BLOOD) {
            return bloodRoomColor.getRGB();
        }

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
            case 82, 85 -> {
                DungeonRoomDatabase.RoomKind special = pickSpecialKind(roomA, roomB);
                if (special != DungeonRoomDatabase.RoomKind.NORMAL && special != DungeonRoomDatabase.RoomKind.UNKNOWN) {
                    yield dynamicNormalDoor(roomA, roomB);
                }
                yield unopenedDoorColor.getRGB();
            }
            case 0 -> unopenedDoorColor.getRGB();
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

    private void drawRoomCheckmark(Render2DEvent event, int x1, int y1, int roomPx, int mapCode, int roomCode, DungeonMapState.RoomSnapshot room) {
        Identifier icon = null;
        if (mapCode == 30) {
            icon = GREEN_CHECK_ICON;
        } else if (mapCode == 34) {
            icon = WHITE_CHECK_ICON;
        } else if (mapCode == 18 && (room == null || room.kind() != DungeonRoomDatabase.RoomKind.BLOOD)) {
            icon = FAILED_ICON;
        } else if (isUnopenedRoomCode(mapCode) || isUnopenedRoomCode(roomCode)) {
            icon = QUESTION_MARK_ICON;
        }

        if (icon == null) {
            return;
        }

        int size = Math.max(6, roomPx - 4);
        int ix = x1 + (roomPx - size) / 2;
        int iy = y1 + (roomPx - size) / 2;
        event.drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, icon, ix, iy, 0.0f, 0.0f, size, size, size, size);
    }

    private boolean isUnopenedRoomCode(int mapColorCode) {
        return mapColorCode == 82 || mapColorCode == 85 || mapColorCode == 119;
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

        cleanupSmoothedMarkerCache(System.currentTimeMillis());

        int startX = state.getMapStartX();
        int startZ = state.getMapStartZ();
        int mapPixelW = Math.max(1, state.getMapPixelWidth());
        int mapPixelH = Math.max(1, state.getMapPixelHeight());

        List<DungeonMapState.PlayerMarker> markers = state.getMarkers();
        List<? extends PlayerEntity> players = mc.world.getPlayers();
        int dotColor = teammateColor.getRGB();
        Map<String, PlayerListEntry> playerEntryLookup = buildPlayerEntryLookup();
        boolean showTeammateNames = shouldRenderTeammateNames();
        boolean smallFloorLayout = state.getRoomsX() < 6 || state.getRoomsZ() < 6;

        Set<Integer> usedMarkerIndices = new HashSet<>();
        List<DungeonMapState.PlayerMarker> validMarkers = new ArrayList<>(markers.size());
        for (DungeonMapState.PlayerMarker marker : markers) {
            if (marker == null) {
                continue;
            }
            validMarkers.add(marker);
        }

        markerIndexNameCache.keySet().removeIf(index -> index < 0 || index >= validMarkers.size());
        if (validMarkers.isEmpty()) {
            markerIndexNameCache.clear();
        }

        for (PlayerEntity player : players) {
            if (!isRenderablePlayer(player)) {
                continue;
            }

            double lerpedX = MathHelper.lerp(event.tickDelta, player.lastRenderX, player.getX());
            double lerpedZ = MathHelper.lerp(event.tickDelta, player.lastRenderZ, player.getZ());
            double worldNx = state.worldToMapNormalizedX(lerpedX);
            double worldNz = state.worldToMapNormalizedZ(lerpedZ);
            double nx = worldNx;
            double nz = worldNz;
            // Don't discard players just because they're a bit outside the map bounds.
            // We'll clamp them later so they appear at the edge instead of disappearing.

            DungeonMapState.PlayerMarker bestMarker = null;
            int bestIndex = -1;
            double bestDist = Double.MAX_VALUE;
            boolean bestLabelMatch = false;
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

                String label = sanitizeMarkerLabel(marker.label()).toLowerCase();
                boolean labelMatch = !playerName.isBlank() && label.contains(playerName);

                double score = labelMatch ? dist - 1.0 : dist;
                if (score < bestDist) {
                    bestDist = score;
                    bestMarker = marker;
                    bestIndex = i;
                    bestLabelMatch = labelMatch;
                }
            }

            if (bestIndex >= 0) {
                usedMarkerIndices.add(bestIndex);
                if (player.getName() != null) {
                    String normalizedPlayerName = sanitizeMarkerLabel(player.getName().getString());
                    if (!normalizedPlayerName.isBlank()) {
                        markerIndexNameCache.put(bestIndex, normalizedPlayerName);
                    }
                }
            }

            boolean worldLooksValid = worldNx > -0.20 && worldNx < 1.20 && worldNz > -0.20 && worldNz < 1.20;
            if (bestMarker != null) {
                double markerNx = (bestMarker.mapX() - startX) / (double) mapPixelW;
                double markerNz = (bestMarker.mapZ() - startZ) / (double) mapPixelH;
                boolean markerCloseEnough = bestDist <= 0.05;
                boolean shouldUseMarker = player == mc.player || bestLabelMatch || (smallFloorLayout && markerCloseEnough) || !worldLooksValid;
                if (shouldUseMarker) {
                    nx = markerNx;
                    nz = markerNz;
                }
            }

            nx = Math.max(0.0, Math.min(1.0, nx));
            nz = Math.max(0.0, Math.min(1.0, nz));

            int px = mapX + MathHelper.floor((float) nx * mapWidth);
            int py = mapY + MathHelper.floor((float) nz * mapHeight);
            int[] smoothed = smoothMarkerScreenPosition("entity:" + player.getUuid(), px, py);
            px = smoothed[0];
            py = smoothed[1];

            int color = player == mc.player ? 0xFF55FFFF : dotColor;
            int bg = player == mc.player ? 0xFF2B4D4D : 0xFF202020;
            drawMarkerHeadOrDot(event, player, px, py, color, bg, player == mc.player);

            if (showTeammateNames && player.getName() != null) {
                drawTeammateName(event, player.getName().getString(), px, getTeammateNameY(py));
            }
        }

        for (int i = 0; i < validMarkers.size(); i++) {
            if (usedMarkerIndices.contains(i)) {
                continue;
            }

            DungeonMapState.PlayerMarker marker = validMarkers.get(i);
            double nx = Math.max(0.0, Math.min(1.0, (marker.mapX() - startX) / (double) mapPixelW));
            double nz = Math.max(0.0, Math.min(1.0, (marker.mapZ() - startZ) / (double) mapPixelH));
            int px = mapX + MathHelper.floor((float) nx * mapWidth);
            int py = mapY + MathHelper.floor((float) nz * mapHeight);

            String label = sanitizeMarkerLabel(marker.label());
            String resolvedName = label;
            if (resolvedName.isBlank()) {
                resolvedName = markerIndexNameCache.getOrDefault(i, "");
            }

            if (mc.player != null && !resolvedName.isBlank() && resolvedName.equalsIgnoreCase(mc.player.getName().getString())) {
                continue;
            }

            PlayerListEntry matchedEntry = lookupEntryByName(playerEntryLookup, resolvedName);
            if (matchedEntry != null && matchedEntry.getProfile() != null) {
                String entryName = matchedEntry.getProfile().name();
                if (entryName != null && !entryName.isBlank()) {
                    if (mc.player != null && entryName.equalsIgnoreCase(mc.player.getName().getString())) {
                        continue;
                    }
                    markerIndexNameCache.put(i, entryName);

                    int[] smoothed = smoothMarkerScreenPosition("entry:" + entryName.toLowerCase(), px, py);
                    px = smoothed[0];
                    py = smoothed[1];

                    drawMarkerHeadOrDot(event, matchedEntry, marker.rotation(), px, py, dotColor, 0xFF202020, false);
                    if (showTeammateNames) {
                        drawTeammateName(event, entryName, px, getTeammateNameY(py));
                    }
                    continue;
                }
            }

            String smoothingKey = resolvedName.isBlank() ? ("marker-index:" + i) : ("marker:" + resolvedName.toLowerCase());
            int[] smoothed = smoothMarkerScreenPosition(smoothingKey, px, py);
            px = smoothed[0];
            py = smoothed[1];

            drawFallbackMarker(event, px, py, dotColor, 0xFF202020, false);
            if (showTeammateNames && !resolvedName.isBlank()) {
                drawTeammateName(event, resolvedName, px, getTeammateNameY(py));
            }
        }
    }

    private boolean shouldRenderTeammateNames() {
        if (alwaysShowTeammateNames.isToggled()) {
            return true;
        }
        if (!holdLeapToShowNames.isToggled() || mc == null || mc.player == null) {
            return false;
        }

        return isLeapItem(mc.player.getMainHandStack()) || isLeapItem(mc.player.getOffHandStack());
    }

    private boolean isLeapItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String itemName = stack.getName() == null ? "" : stack.getName().getString();
        if (itemName.isBlank()) {
            return false;
        }

        String lowerName = itemName.toLowerCase();
        return lowerName.contains("spirit leap") || lowerName.contains("infinileap") || lowerName.contains("infinite leap");
    }

    private Map<String, PlayerListEntry> buildPlayerEntryLookup() {
        Map<String, PlayerListEntry> out = new HashMap<>();
        if (mc == null || mc.getNetworkHandler() == null) {
            return out;
        }

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry == null || entry.getProfile() == null) {
                continue;
            }
            String profileName = entry.getProfile().name();
            if (profileName == null || profileName.isBlank()) {
                continue;
            }

            String normalized = sanitizeMarkerLabel(profileName);
            if (normalized.isBlank()) {
                continue;
            }

            out.putIfAbsent(normalized.toLowerCase(), entry);
        }

        return out;
    }

    private PlayerListEntry lookupEntryByName(Map<String, PlayerListEntry> lookup, String name) {
        if (name == null || name.isBlank() || lookup.isEmpty()) {
            return null;
        }

        String key = sanitizeMarkerLabel(name).toLowerCase();
        if (key.isBlank()) {
            return null;
        }

        return lookup.get(key);
    }

    private void drawMarkerHeadOrDot(Render2DEvent event, PlayerListEntry entry, int markerRotation, int px, int py, int color, int bg, boolean isSelf) {
        int half = getHeadBackgroundHalfSize();
        if (half > 2) {
            event.drawContext.fill(px - half, py - half, px + half, py + half, bg);
        }

        boolean drewHead = false;
        try {
            if (entry != null) {
                SkinTextures textures = entry.getSkinTextures();
                if (textures != null) {
                    int headSize = getConfiguredHeadSize();
                    float yawDegrees = markerRotationToYaw(markerRotation);
                    float rotationRad = (float) Math.toRadians(yawDegrees + 180.0f);

                    event.drawContext.getMatrices().pushMatrix();
                    try {
                        event.drawContext.getMatrices().translate(px, py);
                        event.drawContext.getMatrices().rotate(rotationRad);
                        event.drawContext.getMatrices().translate(-headSize / 2.0f, -headSize / 2.0f);
                        PlayerSkinDrawer.draw(event.drawContext, textures, 0, 0, headSize, -1);
                    } finally {
                        event.drawContext.getMatrices().popMatrix();
                    }
                    drewHead = true;
                    drawHeadBorder(event, px, py, headSize, isSelf);
                }
            }
        } catch (Throwable ignored) {
        }

        if (drewHead) {
            return;
        }

        drawFallbackMarker(event, px, py, color, bg, isSelf);
    }

    private float markerRotationToYaw(int markerRotation) {
        int rot = markerRotation;
        if (rot < 0) {
            rot += 256;
        }

        // Typical map marker rotation uses 0-15, which maps directly to yaw degrees.
        if (rot <= 15) {
            return rot * 22.5f;
        }

        // Some mappings expose full-byte angle encoding.
        rot &= 255;
        return rot * (360.0f / 256.0f);
    }

    private void drawMarkerHeadOrDot(Render2DEvent event, PlayerEntity player, int px, int py, int color, int bg, boolean isSelf) {
        int half = getHeadBackgroundHalfSize();
        if (half > 2) {
            event.drawContext.fill(px - half, py - half, px + half, py + half, bg);
        }

        boolean drewHead = false;
        try {
            if (mc != null && mc.getNetworkHandler() != null) {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
                if (entry != null) {
                    SkinTextures textures = entry.getSkinTextures();
                    if (textures != null) {
                        int headSize = getConfiguredHeadSize();
                        float rotationRad = (float) Math.toRadians(player.getYaw() + 180.0f);

                        event.drawContext.getMatrices().pushMatrix();
                        try {
                            event.drawContext.getMatrices().translate(px, py);
                            event.drawContext.getMatrices().rotate(rotationRad);
                            event.drawContext.getMatrices().translate(-headSize / 2.0f, -headSize / 2.0f);
                            PlayerSkinDrawer.draw(event.drawContext, textures, 0, 0, headSize, -1);
                        } finally {
                            event.drawContext.getMatrices().popMatrix();
                        }
                        drewHead = true;
                        drawHeadBorder(event, px, py, headSize, isSelf);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (drewHead) {
            return;
        }

        drawFallbackMarker(event, px, py, color, bg, isSelf);
        try {
            float yaw = player.getYaw();
            double rad = Math.toRadians(yaw);
            int directionOffset = Math.max(4, getConfiguredHeadSize() / 2);
            int offX = MathHelper.floor(-Math.sin(rad) * directionOffset);
            int offY = MathHelper.floor(Math.cos(rad) * directionOffset);
            int dirX = px + offX;
            int dirY = py + offY;
            event.drawContext.fill(dirX - 1, dirY - 1, dirX + 1, dirY + 1, color);
        } catch (Throwable ignored) {
        }
    }

    private void drawFallbackMarker(Render2DEvent event, int px, int py, int color, int bg, boolean isSelf) {
        int half = getHeadBackgroundHalfSize();
        if (half > 2) {
            event.drawContext.fill(px - half, py - half, px + half, py + half, bg);
        }

        int dotHalf = Math.max(2, getConfiguredHeadSize() / 4);
        event.drawContext.fill(px - dotHalf, py - dotHalf, px + dotHalf, py + dotHalf, color);
        drawHeadBorder(event, px, py, dotHalf * 2, isSelf);
    }

    private int getConfiguredHeadSize() {
        return Math.max(4, (int) Math.round(8 * mapScale.getInput() * playerHeadSize.getInput()));
    }

    private int getHeadBackgroundHalfSize() {
        return 2 + (int) Math.round(playerHeadBackgroundSize.getInput());
    }

    private void drawHeadBorder(Render2DEvent event, int centerX, int centerY, int markerSize, boolean isSelf) {
        if (!headBorder.isToggled()) {
            return;
        }

        int width = Math.max(0, (int) Math.round(headBorderWidth.getInput()));
        if (width <= 0) {
            return;
        }

        int color = isSelf ? selfHeadBorderColor.getRGB() : teammateHeadBorderColor.getRGB();
        int half = markerSize / 2;
        int x1 = centerX - half;
        int y1 = centerY - half;
        int x2 = x1 + markerSize;
        int y2 = y1 + markerSize;

        event.drawContext.fill(x1 - width, y1 - width, x2 + width, y1, color);
        event.drawContext.fill(x1 - width, y2, x2 + width, y2 + width, color);
        event.drawContext.fill(x1 - width, y1, x1, y2, color);
        event.drawContext.fill(x2, y1, x2 + width, y2, color);
    }

    private int[] smoothMarkerScreenPosition(String key, int targetX, int targetY) {
        long now = System.currentTimeMillis();
        SmoothedMarkerPoint point = markerSmoothingCache.get(key);
        if (point == null || now - point.lastUpdateMs > MARKER_SMOOTH_STALE_MS) {
            markerSmoothingCache.put(key, new SmoothedMarkerPoint(targetX, targetY, now));
            return new int[] { targetX, targetY };
        }

        float distance = Math.abs(point.x - targetX) + Math.abs(point.y - targetY);
        if (distance > MARKER_SMOOTH_SNAP_DISTANCE_PX) {
            point.x = targetX;
            point.y = targetY;
        } else {
            point.x += (targetX - point.x) * MARKER_SMOOTH_FACTOR;
            point.y += (targetY - point.y) * MARKER_SMOOTH_FACTOR;
        }
        point.lastUpdateMs = now;

        return new int[] { MathHelper.floor(point.x), MathHelper.floor(point.y) };
    }

    private void cleanupSmoothedMarkerCache(long now) {
        markerSmoothingCache.values().removeIf(point -> now - point.lastUpdateMs > MARKER_SMOOTH_STALE_MS);
    }

    private String sanitizeMarkerLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("(?i)\\u00A7[0-9A-FK-OR]", "");
        cleaned = cleaned.replaceAll("[^A-Za-z0-9_ ]", " ").trim();
        if (cleaned.isBlank()) {
            return "";
        }
        String[] tokens = cleaned.split("\\s+");
        if (tokens.length == 0) {
            return "";
        }

        for (int i = tokens.length - 1; i >= 0; i--) {
            String token = tokens[i];
            if (token == null || token.isBlank()) {
                continue;
            }
            if (token.length() < 3 || token.length() > 16) {
                continue;
            }
            if (RANK_TOKENS.contains(token.toUpperCase())) {
                continue;
            }
            return token;
        }

        return tokens[tokens.length - 1];
    }

    private boolean isRenderablePlayer(PlayerEntity player) {
        if (player == null || mc == null || mc.player == null) {
            return false;
        }
        if (player == mc.player) {
            return true;
        }

        // Only consider real tab-list players to avoid skin/name NPC swaps (e.g., dungeon shop NPCs).
        try {
            if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) == null) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
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

    private boolean shouldHideForPlayerList() {
        if (mc == null) {
            return false;
        }

        try {
            if (mc.options != null && mc.options.playerListKey != null && mc.options.playerListKey.isPressed()) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            return mc.currentScreen != null
                && mc.currentScreen.getClass().getSimpleName().toLowerCase().contains("playerlist");
        } catch (Throwable ignored) {
            return false;
        }
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

    private int getTeammateNameY(int markerY) {
        int headSize = getConfiguredHeadSize();
        int gap = (int) Math.round(teammateNameGap.getInput());
        return markerY + (headSize / 2) + gap;
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

    private static final class SmoothedMarkerPoint {
        private float x;
        private float y;
        private long lastUpdateMs;

        private SmoothedMarkerPoint(float x, float y, long lastUpdateMs) {
            this.x = x;
            this.y = y;
            this.lastUpdateMs = lastUpdateMs;
        }
    }

}
