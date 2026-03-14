package com.revampes.AfterTimeFault.gui.screen;

import com.revampes.AfterTimeFault.modules.impl.other.PanelCommand;
import com.revampes.AfterTimeFault.utility.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PanelCommandScreen extends Screen {
    private static final int BASE_ALPHA = 204; // 80%
    private static final int HOVER_ALPHA = 255; // 100%
    private static final float INNER_RADIUS = 44.0f;
    private static final float OUTER_RADIUS = 138.0f;
    private static final float ANGLE_GAP = 4.0f;

    private final PanelCommand module;

    private int hoveredIndex = -1;
    private boolean editMode = false;
    private float[] hoverAnimation = new float[0];

    private EditInputMode inputMode = EditInputMode.NONE;
    private final StringBuilder typingBuffer = new StringBuilder();
    private int editingIndex = -1;
    private String pendingName = "";

    private String statusText = "";
    private int statusTicks = 0;

    private int addBtnX1;
    private int addBtnY1;
    private int addBtnX2;
    private int addBtnY2;
    private int editBtnX1;
    private int editBtnY1;
    private int editBtnX2;
    private int editBtnY2;
    private int removeBtnX1;
    private int removeBtnY1;
    private int removeBtnX2;
    private int removeBtnY2;

    private List<List<int[]>> cachedRegionRuns = new ArrayList<>();
    private int cacheWidth = -1;
    private int cacheHeight = -1;
    private int cacheCount = -1;

    private enum EditInputMode {
        NONE,
        ADD_NAME,
        ADD_COMMAND,
        EDIT_NAME,
        EDIT_COMMAND
    }

    public PanelCommandScreen(PanelCommand module) {
        super(Text.literal("Panel Command"));
        this.module = module;
    }

    public boolean isEditMode() {
        return this.editMode;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x66000000);
        updateEditButtonLayout();

        List<PanelCommand.CommandEntry> entries = this.module.getEntries();
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "No commands. Press E, then A to add one.", this.width / 2, this.height / 2, 0xFFFFFFFF);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        syncAnimationArray(entries.size());

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        rebuildRunCacheIfNeeded(entries.size(), centerX, centerY);

        this.hoveredIndex = computeHoveredIndex(mouseX, mouseY, entries.size(), centerX, centerY);

        float span = 360.0f / entries.size();
        float rotation = getRotationOffset(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            float start = rotation + (i * span) + (entries.size() == 1 ? 0.0f : ANGLE_GAP / 2.0f);
            float end = rotation + ((i + 1) * span) - (entries.size() == 1 ? 0.0f : ANGLE_GAP / 2.0f);
            if (end <= start) {
                continue;
            }

            float target = (this.hoveredIndex == i && this.inputMode == EditInputMode.NONE) ? 1.0f : 0.0f;
            this.hoverAnimation[i] += (target - this.hoverAnimation[i]) * 0.2f;
            int alpha = (int) (BASE_ALPHA + (HOVER_ALPHA - BASE_ALPHA) * this.hoverAnimation[i]);

            List<int[]> runs = i < this.cachedRegionRuns.size() ? this.cachedRegionRuns.get(i) : null;
            if (runs != null) {
                int color = (alpha << 24);
                for (int[] run : runs) {
                    context.fill(run[0], run[1], run[2] + 1, run[1] + 1, color);
                }
            }

            float mid = (start + end) * 0.5f;
            float textRadius = (INNER_RADIUS + OUTER_RADIUS) * 0.5f;
            int textX = centerX + Math.round((float) Math.cos(Math.toRadians(mid)) * textRadius);
            int textY = centerY + Math.round((float) Math.sin(Math.toRadians(mid)) * textRadius);
            String label = entries.get(i).getName();
            if (label == null || label.trim().isEmpty()) {
                label = "Cmd " + (i + 1);
            }
            context.drawCenteredTextWithShadow(this.textRenderer, label, textX, textY - this.textRenderer.fontHeight / 2, 0xFFFFFFFF);
        }

        drawCenterText(context, centerX, centerY);
        drawTopOverlay(context);
        drawEditButtons(context, mouseX, mouseY);
        drawStatus(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void updateEditButtonLayout() {
        int buttonWidth = 82;
        int buttonHeight = 18;
        int gap = 8;
        int totalWidth = buttonWidth * 3 + gap * 2;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height - 72;

        this.addBtnX1 = startX;
        this.addBtnY1 = y;
        this.addBtnX2 = startX + buttonWidth;
        this.addBtnY2 = y + buttonHeight;

        this.editBtnX1 = this.addBtnX2 + gap;
        this.editBtnY1 = y;
        this.editBtnX2 = this.editBtnX1 + buttonWidth;
        this.editBtnY2 = y + buttonHeight;

        this.removeBtnX1 = this.editBtnX2 + gap;
        this.removeBtnY1 = y;
        this.removeBtnX2 = this.removeBtnX1 + buttonWidth;
        this.removeBtnY2 = y + buttonHeight;
    }

    private void drawEditButtons(DrawContext context, int mouseX, int mouseY) {
        if (!this.editMode || this.inputMode != EditInputMode.NONE) {
            return;
        }

        drawButton(context, this.addBtnX1, this.addBtnY1, this.addBtnX2, this.addBtnY2, "Add", isPointIn(mouseX, mouseY, this.addBtnX1, this.addBtnY1, this.addBtnX2, this.addBtnY2));
        drawButton(context, this.editBtnX1, this.editBtnY1, this.editBtnX2, this.editBtnY2, "Edit", isPointIn(mouseX, mouseY, this.editBtnX1, this.editBtnY1, this.editBtnX2, this.editBtnY2));
        drawButton(context, this.removeBtnX1, this.removeBtnY1, this.removeBtnX2, this.removeBtnY2, "Remove", isPointIn(mouseX, mouseY, this.removeBtnX1, this.removeBtnY1, this.removeBtnX2, this.removeBtnY2));
    }

    private void drawButton(DrawContext context, int x1, int y1, int x2, int y2, String text, boolean hovered) {
        int color = hovered ? 0xB0202020 : 0x90101010;
        context.fill(x1, y1, x2, y2, color);
        RenderUtils.drawBorder(context, x1, y1, x2 - x1, y2 - y1, hovered ? 0xFFFFFFFF : 0x80FFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, text, (x1 + x2) / 2, y1 + 5, 0xFFFFFFFF);
    }

    private boolean isPointIn(double px, double py, int x1, int y1, int x2, int y2) {
        return px >= x1 && px <= x2 && py >= y1 && py <= y2;
    }

    private void drawTopOverlay(DrawContext context) {
        String modeText = "Mode: " + this.module.mode.getOption();
        context.drawCenteredTextWithShadow(this.textRenderer, modeText, this.width / 2, 10, 0xFFE0E0E0);

        if (this.editMode) {
            context.drawCenteredTextWithShadow(this.textRenderer, "Edit Mode: LMB edit, RMB remove, A add, E close edit", this.width / 2, 24, 0xFFFFCC66);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, "Press E for edit mode", this.width / 2, 24, 0xFFB0B0B0);
        }

        if (this.inputMode != EditInputMode.NONE) {
            String prompt;
            if (this.inputMode == EditInputMode.ADD_NAME || this.inputMode == EditInputMode.EDIT_NAME) {
                prompt = "Enter name:";
            } else {
                prompt = "Enter command:";
            }

            int boxWidth = Math.max(260, this.textRenderer.getWidth(prompt + " " + this.typingBuffer) + 24);
            int boxX1 = this.width / 2 - boxWidth / 2;
            int boxX2 = this.width / 2 + boxWidth / 2;
            int boxY1 = this.height - 46;
            int boxY2 = this.height - 20;

            context.fill(boxX1, boxY1, boxX2, boxY2, 0xD0000000);
            RenderUtils.drawBorder(context, boxX1, boxY1, boxWidth, boxY2 - boxY1, 0x80FFFFFF);
            context.drawTextWithShadow(this.textRenderer, prompt + " " + this.typingBuffer, boxX1 + 8, boxY1 + 8, 0xFFFFFFFF);
        }
    }

    private void drawCenterText(DrawContext context, int centerX, int centerY) {
        String line1;
        String line2;

        if (this.editMode) {
            line1 = "Editing";
            line2 = this.hoveredIndex >= 0 ? "Selected: " + (this.hoveredIndex + 1) : "Hover a region";
        } else if ("Hold".equals(this.module.mode.getOption())) {
            line1 = "Hold mode";
            line2 = "";
        } else {
            line1 = "Toggle mode";
            line2 = "";
        }

        context.drawCenteredTextWithShadow(this.textRenderer, line1, centerX, centerY - 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, line2, centerX, centerY + 4, 0xFFDDDDDD);
    }

    private void drawStatus(DrawContext context) {
        if (this.statusTicks <= 0) {
            return;
        }

        this.statusTicks--;
        context.drawCenteredTextWithShadow(this.textRenderer, this.statusText, this.width / 2, this.height - 66, 0xFFA0FFA0);
    }

    private void syncAnimationArray(int size) {
        if (this.hoverAnimation.length == size) {
            return;
        }

        float[] old = this.hoverAnimation;
        this.hoverAnimation = new float[size];
        for (int i = 0; i < Math.min(old.length, size); i++) {
            this.hoverAnimation[i] = old[i];
        }
    }

    private float getRotationOffset(int count) {
        if (count == 4) {
            return -45.0f;
        }
        if (count == 2) {
            return -90.0f;
        }
        return -90.0f;
    }

    private int computeHoveredIndex(double mouseX, double mouseY, int count, int centerX, int centerY) {
        if (count <= 0 || this.inputMode != EditInputMode.NONE) {
            return -1;
        }

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < INNER_RADIUS + 2.0f || distance > OUTER_RADIUS - 2.0f) {
            return -1;
        }

        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) {
            angle += 360.0;
        }

        float span = 360.0f / count;
        float rotation = getRotationOffset(count);

        for (int i = 0; i < count; i++) {
            float start = rotation + (i * span) + (count == 1 ? 0.0f : ANGLE_GAP / 2.0f);
            float end = rotation + ((i + 1) * span) - (count == 1 ? 0.0f : ANGLE_GAP / 2.0f);
            if (isAngleInRange((float) angle, start, end)) {
                return i;
            }
        }

        return -1;
    }

    private boolean isAngleInRange(float angle, float start, float end) {
        float a = normalizeAngle(angle);
        float s = normalizeAngle(start);
        float e = normalizeAngle(end);

        if (s <= e) {
            return a >= s && a <= e;
        }

        return a >= s || a <= e;
    }

    private float normalizeAngle(float angle) {
        float normalized = angle % 360.0f;
        return normalized < 0 ? normalized + 360.0f : normalized;
    }

    private void rebuildRunCacheIfNeeded(int count, int centerX, int centerY) {
        if (count == this.cacheCount && this.width == this.cacheWidth && this.height == this.cacheHeight) {
            return;
        }

        this.cacheCount = count;
        this.cacheWidth = this.width;
        this.cacheHeight = this.height;
        this.cachedRegionRuns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            this.cachedRegionRuns.add(new ArrayList<>());
        }

        if (count <= 0) {
            return;
        }

        float rotation = getRotationOffset(count);
        float span = 360.0f / count;
        int minY = Math.max(0, (int) (centerY - OUTER_RADIUS) - 1);
        int maxY = Math.min(this.height - 1, (int) (centerY + OUTER_RADIUS) + 1);
        int minX = Math.max(0, (int) (centerX - OUTER_RADIUS) - 1);
        int maxX = Math.min(this.width - 1, (int) (centerX + OUTER_RADIUS) + 1);

        double innerSq = INNER_RADIUS * INNER_RADIUS;
        double outerSq = OUTER_RADIUS * OUTER_RADIUS;

        for (int y = minY; y <= maxY; y++) {
            int[] runStart = new int[count];
            for (int i = 0; i < count; i++) {
                runStart[i] = -1;
            }

            for (int x = minX; x <= maxX; x++) {
                double dx = x - centerX;
                double dy = y - centerY;
                double distanceSq = dx * dx + dy * dy;

                int region = -1;
                if (distanceSq >= innerSq && distanceSq <= outerSq) {
                    float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                    if (angle < 0) {
                        angle += 360.0f;
                    }
                    region = findRegionForAngle(angle, count, rotation, span);
                }

                for (int r = 0; r < count; r++) {
                    if (r == region) {
                        if (runStart[r] == -1) {
                            runStart[r] = x;
                        }
                    } else if (runStart[r] != -1) {
                        this.cachedRegionRuns.get(r).add(new int[]{runStart[r], y, x - 1});
                        runStart[r] = -1;
                    }
                }
            }

            for (int r = 0; r < count; r++) {
                if (runStart[r] != -1) {
                    this.cachedRegionRuns.get(r).add(new int[]{runStart[r], y, maxX});
                    runStart[r] = -1;
                }
            }
        }
    }

    private int findRegionForAngle(float angle, int count, float rotation, float span) {
        for (int i = 0; i < count; i++) {
            float start = rotation + (i * span) + (count == 1 ? 0.0f : ANGLE_GAP / 2.0f);
            float end = rotation + ((i + 1) * span) - (count == 1 ? 0.0f : ANGLE_GAP / 2.0f);
            if (isAngleInRange(angle, start, end)) {
                return i;
            }
        }
        return -1;
    }

    public void executeHoveredCommand() {
        if (this.editMode || this.inputMode != EditInputMode.NONE) {
            return;
        }
        executeAtIndex(this.hoveredIndex, true);
    }

    private void executeAtIndex(int index, boolean closeAfter) {
        List<PanelCommand.CommandEntry> entries = this.module.getEntries();
        if (index < 0 || index >= entries.size()) {
            return;
        }

        String cmd = entries.get(index).getCommand();
        if (cmd == null || cmd.trim().isEmpty()) {
            setStatus("Command is empty");
            return;
        }

        String clean = cmd.trim();
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }

        if (this.client != null && this.client.player != null) {
            this.client.player.networkHandler.sendChatCommand(clean);
        }

        if (closeAfter) {
            closePanel();
        }
    }

    private void closePanel() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
        if (this.module.isEnabled()) {
            this.module.disable();
        }
    }

    private void setStatus(String text) {
        this.statusText = text;
        this.statusTicks = 80;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (this.inputMode != EditInputMode.NONE) {
            return true;
        }

        if (this.editMode && button == 0) {
            if (isPointIn(mouseX, mouseY, this.addBtnX1, this.addBtnY1, this.addBtnX2, this.addBtnY2)) {
                beginAddEntry();
                return true;
            }
            if (isPointIn(mouseX, mouseY, this.editBtnX1, this.editBtnY1, this.editBtnX2, this.editBtnY2)) {
                if (this.hoveredIndex >= 0) {
                    beginEditEntry(this.hoveredIndex);
                } else {
                    setStatus("Hover a region to edit");
                }
                return true;
            }
            if (isPointIn(mouseX, mouseY, this.removeBtnX1, this.removeBtnY1, this.removeBtnX2, this.removeBtnY2)) {
                if (this.hoveredIndex >= 0) {
                    removeEntry(this.hoveredIndex);
                } else {
                    setStatus("Hover a region to remove");
                }
                return true;
            }
        }

        if (!this.editMode) {
            if (button == 0 && "Toggle".equals(this.module.mode.getOption()) && this.hoveredIndex >= 0) {
                executeAtIndex(this.hoveredIndex, true);
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        if (this.hoveredIndex < 0) {
            return super.mouseClicked(click, doubled);
        }

        if (button == 0) {
            beginEditEntry(this.hoveredIndex);
            return true;
        }

        if (button == 1) {
            removeEntry(this.hoveredIndex);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        if (this.inputMode != EditInputMode.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelTyping();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmTyping();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!this.typingBuffer.isEmpty()) {
                    this.typingBuffer.setLength(this.typingBuffer.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_E) {
            this.editMode = !this.editMode;
            setStatus(this.editMode ? "Edit mode enabled" : "Edit mode disabled");

            if (!this.editMode && "Hold".equals(this.module.mode.getOption()) && this.module.getKeycode() > 0 && this.client != null) {
                boolean stillHolding = GLFW.glfwGetKey(this.client.getWindow().getHandle(), this.module.getKeycode()) == GLFW.GLFW_PRESS;
                if (!stillHolding) {
                    closePanel();
                }
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePanel();
            return true;
        }

        if (this.editMode) {
            if (keyCode == GLFW.GLFW_KEY_A) {
                beginAddEntry();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_R && this.hoveredIndex >= 0) {
                removeEntry(this.hoveredIndex);
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_DELETE && this.hoveredIndex >= 0) {
                removeEntry(this.hoveredIndex);
                return true;
            }
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (this.inputMode == EditInputMode.NONE) {
            return super.charTyped(input);
        }

        char chr = Character.toChars(input.codepoint())[0];
        if (chr >= 32 && chr != 127 && this.typingBuffer.length() < 90) {
            this.typingBuffer.append(chr);
            return true;
        }

        return false;
    }

    private void beginAddEntry() {
        this.pendingName = "";
        this.editingIndex = -1;
        this.typingBuffer.setLength(0);
        this.inputMode = EditInputMode.ADD_NAME;
        setStatus("Adding new command");
    }

    private void beginEditEntry(int index) {
        List<PanelCommand.CommandEntry> entries = this.module.getEntries();
        if (index < 0 || index >= entries.size()) {
            return;
        }

        this.editingIndex = index;
        this.typingBuffer.setLength(0);
        this.typingBuffer.append(entries.get(index).getName());
        this.inputMode = EditInputMode.EDIT_NAME;
        setStatus("Editing command " + (index + 1));
    }

    private void removeEntry(int index) {
        List<PanelCommand.CommandEntry> entries = this.module.getEntries();
        if (index < 0 || index >= entries.size()) {
            return;
        }

        entries.remove(index);
        if (entries.isEmpty()) {
            entries.add(new PanelCommand.CommandEntry("Help", "/help"));
        }
        this.module.saveEntries();
        setStatus("Removed command");
    }

    private void cancelTyping() {
        this.inputMode = EditInputMode.NONE;
        this.typingBuffer.setLength(0);
        this.pendingName = "";
        this.editingIndex = -1;
        setStatus("Edit cancelled");
    }

    private void confirmTyping() {
        List<PanelCommand.CommandEntry> entries = this.module.getEntries();
        String typed = this.typingBuffer.toString().trim();

        if (this.inputMode == EditInputMode.ADD_NAME) {
            this.pendingName = typed.isEmpty() ? "Command" : typed;
            this.typingBuffer.setLength(0);
            this.typingBuffer.append("/");
            this.inputMode = EditInputMode.ADD_COMMAND;
            return;
        }

        if (this.inputMode == EditInputMode.ADD_COMMAND) {
            String cmd = sanitizeCommand(typed);
            entries.add(new PanelCommand.CommandEntry(this.pendingName, cmd));
            this.module.saveEntries();
            this.inputMode = EditInputMode.NONE;
            this.typingBuffer.setLength(0);
            this.pendingName = "";
            setStatus("Added command");
            return;
        }

        if (this.inputMode == EditInputMode.EDIT_NAME) {
            if (this.editingIndex < 0 || this.editingIndex >= entries.size()) {
                cancelTyping();
                return;
            }
            String name = typed.isEmpty() ? "Command" : typed;
            entries.get(this.editingIndex).setName(name);
            this.typingBuffer.setLength(0);
            this.typingBuffer.append(entries.get(this.editingIndex).getCommand());
            this.inputMode = EditInputMode.EDIT_COMMAND;
            return;
        }

        if (this.inputMode == EditInputMode.EDIT_COMMAND) {
            if (this.editingIndex < 0 || this.editingIndex >= entries.size()) {
                cancelTyping();
                return;
            }
            entries.get(this.editingIndex).setCommand(sanitizeCommand(typed));
            this.module.saveEntries();
            this.inputMode = EditInputMode.NONE;
            this.typingBuffer.setLength(0);
            this.editingIndex = -1;
            setStatus("Updated command");
        }
    }

    private String sanitizeCommand(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "/help";
        }

        String trimmed = value.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
