package com.revampes.Fault.modules.impl.dungeon.DungeonMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class DungeonRoomDatabase {
    public enum RoomKind {
        BLOOD,
        CHAMPION,
        ENTRANCE,
        FAIRY,
        NORMAL,
        PUZZLE,
        RARE,
        TRAP,
        UNKNOWN
    }

    public record RoomMeta(String name, RoomKind kind) {
    }

    private final Map<Integer, RoomMeta> coreToMeta;

    public DungeonRoomDatabase() {
        coreToMeta = loadCoreMap();
    }

    public RoomKind getKindForCore(int core) {
        return coreToMeta.getOrDefault(core, new RoomMeta("Unknown", RoomKind.UNKNOWN)).kind();
    }

    public RoomMeta getMetaForCore(int core) {
        return coreToMeta.getOrDefault(core, new RoomMeta("Unknown", RoomKind.UNKNOWN));
    }

    private static Map<Integer, RoomMeta> loadCoreMap() {
        Map<Integer, RoomMeta> out = new HashMap<>();
        try (InputStream stream = DungeonRoomDatabase.class.getResourceAsStream("/assets/revampes/room.json")) {
            if (stream == null) {
                return Collections.emptyMap();
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonArray()) {
                    return Collections.emptyMap();
                }

                JsonArray rooms = root.getAsJsonArray();
                for (JsonElement element : rooms) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject room = element.getAsJsonObject();
                    String name = room.has("name") ? room.get("name").getAsString() : "Unknown";
                    RoomKind kind = parseKind(room.get("type"));
                    JsonElement coresElement = room.get("cores");
                    if (coresElement == null || !coresElement.isJsonArray()) {
                        continue;
                    }

                    JsonArray cores = coresElement.getAsJsonArray();
                    for (JsonElement coreElement : cores) {
                        if (coreElement != null && coreElement.isJsonPrimitive() && coreElement.getAsJsonPrimitive().isNumber()) {
                            out.put(coreElement.getAsInt(), new RoomMeta(name, kind));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            return Collections.emptyMap();
        }

        return out;
    }

    private static RoomKind parseKind(JsonElement typeElement) {
        if (typeElement == null || !typeElement.isJsonPrimitive()) {
            return RoomKind.UNKNOWN;
        }

        String value = typeElement.getAsString();
        if (value == null) {
            return RoomKind.UNKNOWN;
        }

        return switch (value.trim().toUpperCase()) {
            case "BLOOD" -> RoomKind.BLOOD;
            case "CHAMPION" -> RoomKind.CHAMPION;
            case "ENTRANCE" -> RoomKind.ENTRANCE;
            case "FAIRY" -> RoomKind.FAIRY;
            case "NORMAL" -> RoomKind.NORMAL;
            case "PUZZLE" -> RoomKind.PUZZLE;
            case "RARE" -> RoomKind.RARE;
            case "TRAP" -> RoomKind.TRAP;
            default -> RoomKind.UNKNOWN;
        };
    }
}
