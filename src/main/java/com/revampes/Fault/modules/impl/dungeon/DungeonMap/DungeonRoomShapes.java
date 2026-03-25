package com.revampes.Fault.modules.impl.dungeon.DungeonMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class DungeonRoomShapes {
    private final Set<String> knownShapes = new HashSet<>();

    public DungeonRoomShapes() {
        load();
        if (knownShapes.isEmpty()) {
            knownShapes.add("1x1");
            knownShapes.add("1x2");
            knownShapes.add("1x3");
            knownShapes.add("1x4");
            knownShapes.add("2x2");
            knownShapes.add("L");
        }
    }

    private void load() {
        try (InputStream stream = DungeonRoomShapes.class.getResourceAsStream("/assets/revampes/room.json")) {
            if (stream == null) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonArray()) {
                    return;
                }
                JsonArray arr = root.getAsJsonArray();
                for (JsonElement element : arr) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = element.getAsJsonObject();
                    JsonElement shapeElement = obj.get("shape");
                    if (shapeElement == null || !shapeElement.isJsonPrimitive()) {
                        continue;
                    }
                    String shape = shapeElement.getAsString();
                    if (shape != null && !shape.isBlank()) {
                        knownShapes.add(shape.trim().toUpperCase());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean supports(String shape) {
        if (shape == null) {
            return false;
        }
        return knownShapes.contains(shape.trim().toUpperCase());
    }
}
