package com.revampes.Fault.modules.impl.other;

import com.revampes.Fault.config.ConfigManager;
import com.revampes.Fault.gui.screen.PanelCommandScreen;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.SelectSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PanelCommand extends Module {
    public final SelectSetting mode = new SelectSetting("Mode", 0, new String[]{"Hold", "Toggle"});
    private PanelCommandScreen screen;
    private List<CommandEntry> entries;

    public static class CommandEntry {
        private String name;
        private String command;

        public CommandEntry(String name, String command) {
            this.name = sanitize(name, "Command");
            this.command = sanitize(command, "/help");
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = sanitize(name, "Command");
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = sanitize(command, "/help");
        }

        private static String sanitize(String value, String fallback) {
            if (value == null) return fallback;
            String trimmed = value.trim();
            return trimmed.isEmpty() ? fallback : trimmed;
        }
    }

    public PanelCommand() {
        super("PanelCommand", category.Other, GLFW.GLFW_KEY_H);
        this.registerSetting(mode);
        this.entries = new ArrayList<>(ConfigManager.loadPanelCommands());
    }

    public List<CommandEntry> getEntries() {
        return this.entries;
    }

    public void saveEntries() {
        ConfigManager.savePanelCommands(this.entries);
    }

    @Override
    public void onEnable() {
        this.entries = new ArrayList<>(ConfigManager.loadPanelCommands());
        if (this.entries.isEmpty()) {
            this.entries.add(new CommandEntry("Help", "/help"));
            saveEntries();
        }

        if (mc.currentScreen == null || !(mc.currentScreen instanceof PanelCommandScreen)) {
            screen = new PanelCommandScreen(this);
            mc.setScreen(screen);
        }
    }

    @Override
    public void onUpdate() {
        if (this.screen == null) {
            return;
        }

        if (mc.currentScreen != this.screen) {
            this.disable();
            return;
        }

        if ("Hold".equals(mode.getOption()) && this.getKeycode() > 0 && !this.screen.isEditMode()) {
            if (GLFW.glfwGetKey(mc.getWindow().getHandle(), this.getKeycode()) != GLFW.GLFW_PRESS) {
                this.screen.executeHoveredCommand();
                if (mc.currentScreen == this.screen) {
                    mc.setScreen(null);
                }
                this.disable();
            }
        }
    }

    @Override
    public void onDisable() {
        if (mc.currentScreen == this.screen) {
            mc.setScreen(null);
        }
        this.screen = null;
    }
}
