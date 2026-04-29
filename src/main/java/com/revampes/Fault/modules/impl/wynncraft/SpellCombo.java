package com.revampes.Fault.modules.impl.wynncraft;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.mixin.accessor.KeyBindingAccessor;
import com.revampes.Fault.mixin.accessor.KeyboardAccessor;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.InputSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.Utils;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class SpellCombo extends Module {
	private static class PendingKeyRelease {
		private final InputUtil.Key key;
		private final List<KeyBinding> bindings;
		private final int keyboardKeyCode;
		private final boolean releaseStaticState;
		private final boolean releaseKeyboardEvent;
		private final long releaseAtMs;

		private PendingKeyRelease(InputUtil.Key key, List<KeyBinding> bindings, int keyboardKeyCode,
								  boolean releaseStaticState, boolean releaseKeyboardEvent, long releaseAtMs) {
			this.key = key;
			this.bindings = bindings;
			this.keyboardKeyCode = keyboardKeyCode;
			this.releaseStaticState = releaseStaticState;
			this.releaseKeyboardEvent = releaseKeyboardEvent;
			this.releaseAtMs = releaseAtMs;
		}
	}

	private enum ComboAction {
		SPELL_1,
		SPELL_2,
		SPELL_3,
		SPELL_4,
		RIGHT_CLICK,
		MIDDLE_CLICK,
		BUTTON_4,
		BUTTON_5
	}

	private static final long SPELL_KEY_HOLD_MS = 45L;
	private static final long SPELL_RELEASE_TIMEOUT_MS = 120L;

	private static final Method ON_KEY_PRESSED_METHOD = findKeyBindingMethod("onKeyPressed", InputUtil.Key.class);
	private static final Method SET_KEY_PRESSED_METHOD = findKeyBindingMethod("setKeyPressed", InputUtil.Key.class, boolean.class);

	private final InputSetting cast1SpellKey = new InputSetting("Cast 1st Spell", 32, "Z", "Wynntils quick-cast key");
	private final InputSetting cast2SpellKey = new InputSetting("Cast 2nd Spell", 32, "X", "Wynntils quick-cast key");
	private final InputSetting cast3SpellKey = new InputSetting("Cast 3rd Spell", 32, "C", "Wynntils quick-cast key");
	private final InputSetting cast4SpellKey = new InputSetting("Cast 4th Spell", 32, "V", "Wynntils quick-cast key");

	private final SliderSetting leftClickDelay = new SliderSetting("Left Click Delay", "ms", 100, 0, 400, 1);
	private final SliderSetting rightClickDelay = new SliderSetting("Right Click Delay", "ms", 100, 0, 400, 1);

	private final InputSetting combo1Trigger = new InputSetting("Combo 1 Trigger", 32, "", "e.g. I");
	private final InputSetting combo1Actions = new InputSetting("Combo 1 Actions", 150, "", "e.g. V -> Z -> C -> RIGHT CLICK");
	private final InputSetting combo2Trigger = new InputSetting("Combo 2 Trigger", 32, "", "e.g. O");
	private final InputSetting combo2Actions = new InputSetting("Combo 2 Actions", 150, "", "e.g. V -> Z -> RIGHT CLICK");
	private final InputSetting combo3Trigger = new InputSetting("Combo 3 Trigger", 32, "", "optional");
	private final InputSetting combo3Actions = new InputSetting("Combo 3 Actions", 150, "", "optional");

	private final ArrayDeque<ComboAction> queuedActions = new ArrayDeque<>();
	private long nextActionAtMs;
	private boolean triedWynntilsAutofill;
	private boolean combo1TriggerHeld;
	private boolean combo2TriggerHeld;
	private boolean combo3TriggerHeld;
	private KeyBinding detectedSpell1Binding;
	private KeyBinding detectedSpell2Binding;
	private KeyBinding detectedSpell3Binding;
	private KeyBinding detectedSpell4Binding;
	private final ArrayDeque<PendingKeyRelease> pendingReleases = new ArrayDeque<>();

	public SpellCombo() {
		super("SpellCombo", "Queue Wynntils quick-cast spell chains onto one key.", category.Wynncraft);
		this.registerSetting(cast1SpellKey);
		this.registerSetting(cast2SpellKey);
		this.registerSetting(cast3SpellKey);
		this.registerSetting(cast4SpellKey);
		this.registerSetting(leftClickDelay);
		this.registerSetting(rightClickDelay);
		this.registerSetting(combo1Trigger);
		this.registerSetting(combo1Actions);
		this.registerSetting(combo2Trigger);
		this.registerSetting(combo2Actions);
		this.registerSetting(combo3Trigger);
		this.registerSetting(combo3Actions);
	}

	@Override
	public String getDesc() {
		return "Triggers: keyboard keys or mouse buttons (RC, LC, MC, BUTTON4, BUTTON5). Actions: keyboard keys or mouse buttons. Format with -> (e.g., V -> Z -> C -> RC or RC -> BUTTON4).";
	}

	@Override
	public void onEnable() {
		clearQueue();
		resetTriggerHoldStates();
		clearDetectedSpellBindings();
		tryAutofillWynntilsQuickCast();
	}

	@Override
	public void onDisable() {
		clearQueue();
		forceReleaseAllPending();
		resetTriggerHoldStates();
	}

	@EventHandler
	public void onPreUpdate(PreUpdateEvent event) {
		long now = System.currentTimeMillis();
		processPendingReleases(now);

		if (!Utils.nullCheck() || mc.currentScreen != null) {
			clearQueue();
			forceReleaseAllPending();
			resetTriggerHoldStates();
			return;
		}

		pollTriggerCombos();

		if (queuedActions.isEmpty()) {
			return;
		}

		if (now < nextActionAtMs) {
			return;
		}

		ComboAction action = queuedActions.pollFirst();
		if (action == null) {
			return;
		}

		if (!executeAction(action)) {
			clearQueue();
			return;
		}

		if (queuedActions.isEmpty()) {
			nextActionAtMs = 0L;
			return;
		}

		nextActionAtMs = now + getDelayAfterActionMs(action);
	}

	private void pollTriggerCombos() {
		handleTriggerPress("Combo 1", combo1Trigger, combo1Actions, 1);
		handleTriggerPress("Combo 2", combo2Trigger, combo2Actions, 2);
		handleTriggerPress("Combo 3", combo3Trigger, combo3Actions, 3);
	}

	private void handleTriggerPress(String comboName, InputSetting triggerSetting, InputSetting actionsSetting, int slot) {
		String triggerValue = triggerSetting.getValue();
		boolean isDown = isTriggerInputDown(triggerValue);
		boolean wasDown = getHeldState(slot);
		setHeldState(slot, isDown);

		if (!isDown || wasDown) {
			return;
		}

		tryQueueCombo(comboName, triggerValue, actionsSetting);
	}

	private boolean isTriggerInputDown(String triggerValue) {
		if (triggerValue == null || triggerValue.isBlank()) {
			return false;
		}

		String normalized = normalizeToken(triggerValue);
		
		// Check for mouse button triggers
		Integer mouseButton = parseMouseButton(normalized);
		if (mouseButton != null) {
			return isMouseButtonDown(mouseButton);
		}
		
		// Check for keyboard triggers
		int keyCode = parseKeyCode(triggerValue);
		return isPhysicalKeyDown(keyCode);
	}

	private boolean isPhysicalKeyDown(int keyCode) {
		if (keyCode == GLFW.GLFW_KEY_UNKNOWN || mc.getWindow() == null) {
			return false;
		}

		long window = mc.getWindow().getHandle();
		if (window == 0L) {
			return false;
		}

		return GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
	}

	private boolean isMouseButtonDown(int mouseButton) {
		if (mc.getWindow() == null) {
			return false;
		}

		long window = mc.getWindow().getHandle();
		if (window == 0L) {
			return false;
		}

		return GLFW.glfwGetMouseButton(window, mouseButton) == GLFW.GLFW_PRESS;
	}

	private Integer parseMouseButton(String normalizedToken) {
		return switch (normalizedToken) {
			case "RIGHT", "RC", "RIGHTCLICK", "MOUSERIGHT", "RIGHTMOUSE" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
			case "LEFT", "LC", "LEFTCLICK", "MOUSELEFT", "LEFTMOUSE" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
			case "MIDDLE", "MC", "MIDDLECLICK", "MOUSEMIDDLE" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
			case "BUTTON4", "BTN4", "B4" -> GLFW.GLFW_MOUSE_BUTTON_4;
			case "BUTTON5", "BTN5", "B5" -> GLFW.GLFW_MOUSE_BUTTON_5;
			default -> null;
		};
	}

	private boolean getHeldState(int slot) {
		return switch (slot) {
			case 1 -> combo1TriggerHeld;
			case 2 -> combo2TriggerHeld;
			case 3 -> combo3TriggerHeld;
			default -> false;
		};
	}

	private void setHeldState(int slot, boolean value) {
		switch (slot) {
			case 1 -> combo1TriggerHeld = value;
			case 2 -> combo2TriggerHeld = value;
			case 3 -> combo3TriggerHeld = value;
			default -> {
			}
		}
	}

	private boolean tryQueueCombo(String comboName, String triggerValue, InputSetting actionsSetting) {
		if (isBusy()) {
			Utils.addChatMessage("§eSpellCombo is already running. Wait for the current combo to finish.");
			return true;
		}

		List<ComboAction> actions = parseComboActions(actionsSetting.getValue());
		if (actions.size() < 2) {
			Utils.addChatMessage("§c" + comboName + " must contain at least 2 valid actions.");
			return true;
		}

		queuedActions.addAll(actions);
		nextActionAtMs = 0L;
		Utils.addChatMessage("§a" + comboName + " queued with §f" + actions.size() + "§a actions.");
		return true;
	}

	private boolean executeAction(ComboAction action) {
		return switch (action) {
			case SPELL_1 -> executeSpellAction(1, cast1SpellKey, "Cast 1st Spell");
			case SPELL_2 -> executeSpellAction(2, cast2SpellKey, "Cast 2nd Spell");
			case SPELL_3 -> executeSpellAction(3, cast3SpellKey, "Cast 3rd Spell");
			case SPELL_4 -> executeSpellAction(4, cast4SpellKey, "Cast 4th Spell");
			case RIGHT_CLICK -> executeMouseAction(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			case MIDDLE_CLICK -> executeMouseAction(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
			case BUTTON_4 -> executeMouseAction(GLFW.GLFW_MOUSE_BUTTON_4);
			case BUTTON_5 -> executeMouseAction(GLFW.GLFW_MOUSE_BUTTON_5);
		};
	}

	private boolean executeMouseAction(int mouseButton) {
		InputUtil.Key key = InputUtil.Type.MOUSE.createFromCode(mouseButton);
		return pressAndQueueRelease(key, GLFW.GLFW_KEY_UNKNOWN);
	}

	private boolean executeSpellAction(int spellIndex, InputSetting spellKeySetting, String label) {
		if (triggerDetectedSpellBinding(spellIndex)) {
			return true;
		}

		int keyCode = parseKeyCode(spellKeySetting.getValue());
		if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
			Utils.addChatMessage("§c" + label + " key is invalid. Set it to your Wynntils quick-cast key first.");
			return false;
		}

		InputUtil.Key key = InputUtil.Type.KEYSYM.createFromCode(keyCode);
		boolean success = pressAndQueueRelease(key, keyCode);
		if (!success) {
			Utils.addChatMessage("§cSpellCombo failed to send key for " + label + ".");
		}
		return success;
	}

	private boolean triggerDetectedSpellBinding(int spellIndex) {
		KeyBinding binding = getDetectedSpellBinding(spellIndex);
		if (binding == null) {
			return false;
		}

		InputUtil.Key boundKey = ((KeyBindingAccessor) binding).getKey();
		if (boundKey == null) {
			return false;
		}

		int keyCode = boundKey.getCode();
		int keyboardKeyCode = isLikelyKeyboardKeyCode(keyCode) ? keyCode : GLFW.GLFW_KEY_UNKNOWN;
		return pressAndQueueRelease(boundKey, keyboardKeyCode);
	}

	private KeyBinding getDetectedSpellBinding(int spellIndex) {
		return switch (spellIndex) {
			case 1 -> detectedSpell1Binding;
			case 2 -> detectedSpell2Binding;
			case 3 -> detectedSpell3Binding;
			case 4 -> detectedSpell4Binding;
			default -> null;
		};
	}

	private void setDetectedSpellBinding(int spellIndex, KeyBinding binding) {
		switch (spellIndex) {
			case 1 -> detectedSpell1Binding = binding;
			case 2 -> detectedSpell2Binding = binding;
			case 3 -> detectedSpell3Binding = binding;
			case 4 -> detectedSpell4Binding = binding;
			default -> {
			}
		}
	}

	private boolean isLikelyKeyboardKeyCode(int keyCode) {
		return keyCode >= GLFW.GLFW_KEY_SPACE && keyCode <= GLFW.GLFW_KEY_LAST;
	}

	private boolean pressAndQueueRelease(InputUtil.Key key, int keyboardKeyCode) {
		if (key == null) {
			return false;
		}

		boolean sentAny = false;
		boolean staticPressed = false;

		if (SET_KEY_PRESSED_METHOD != null && invokeStaticKeyBindingMethod(SET_KEY_PRESSED_METHOD, key, true)) {
			sentAny = true;
			staticPressed = true;
		}

		if (ON_KEY_PRESSED_METHOD != null && invokeStaticKeyBindingMethod(ON_KEY_PRESSED_METHOD, key)) {
			sentAny = true;
		}

		List<KeyBinding> touchedBindings = collectMatchingBindings(key);
		if (!touchedBindings.isEmpty()) {
			sentAny = true;
		}

		boolean keyboardPressed = false;
		if (keyboardKeyCode != GLFW.GLFW_KEY_UNKNOWN) {
			keyboardPressed = sendKeyboardEvent(keyboardKeyCode, GLFW.GLFW_PRESS);
			if (keyboardPressed) {
				sentAny = true;
			}
		}

		if (!sentAny) {
			if (staticPressed && SET_KEY_PRESSED_METHOD != null) {
				invokeStaticKeyBindingMethod(SET_KEY_PRESSED_METHOD, key, false);
			}
			for (KeyBinding binding : touchedBindings) {
				binding.setPressed(false);
			}
			return false;
		}

		queuePendingRelease(key, touchedBindings, keyboardKeyCode, staticPressed, keyboardPressed);
		return true;
	}

	private List<KeyBinding> collectMatchingBindings(InputUtil.Key key) {
		List<KeyBinding> touched = new ArrayList<>();
		if (mc.options == null || mc.options.allKeys == null) {
			return touched;
		}

		for (KeyBinding keyBinding : mc.options.allKeys) {
			InputUtil.Key bound = ((KeyBindingAccessor) keyBinding).getKey();
			if (bound == null || !bound.equals(key)) {
				continue;
			}
			keyBinding.setPressed(true);
			touched.add(keyBinding);
		}

		return touched;
	}

	private void queuePendingRelease(InputUtil.Key key, List<KeyBinding> bindings, int keyboardKeyCode,
								 boolean releaseStatic, boolean releaseKeyboard) {
		long now = System.currentTimeMillis();
		long holdMs = Math.max(1L, Math.min(SPELL_KEY_HOLD_MS, SPELL_RELEASE_TIMEOUT_MS));
		pendingReleases.addLast(new PendingKeyRelease(
				key,
				bindings,
				keyboardKeyCode,
				releaseStatic,
				releaseKeyboard,
				now + holdMs
		));
	}

	private void processPendingReleases(long now) {
		while (!pendingReleases.isEmpty()) {
			PendingKeyRelease pending = pendingReleases.peekFirst();
			if (pending == null || now < pending.releaseAtMs) {
				return;
			}
			releasePending(pending);
			pendingReleases.pollFirst();
		}
	}

	private void forceReleaseAllPending() {
		while (!pendingReleases.isEmpty()) {
			PendingKeyRelease pending = pendingReleases.pollFirst();
			if (pending != null) {
				releasePending(pending);
			}
		}
	}

	private void releasePending(PendingKeyRelease pending) {
		if (pending.releaseKeyboardEvent && pending.keyboardKeyCode != GLFW.GLFW_KEY_UNKNOWN) {
			sendKeyboardEvent(pending.keyboardKeyCode, GLFW.GLFW_RELEASE);
		}

		if (pending.releaseStaticState && SET_KEY_PRESSED_METHOD != null) {
			invokeStaticKeyBindingMethod(SET_KEY_PRESSED_METHOD, pending.key, false);
		}

		for (KeyBinding keyBinding : pending.bindings) {
			keyBinding.setPressed(false);
		}
	}

	private boolean sendKeyboardEvent(int keyCode, int action) {
		if (keyCode == GLFW.GLFW_KEY_UNKNOWN || mc.keyboard == null || mc.getWindow() == null) {
			return false;
		}

		long window = mc.getWindow().getHandle();
		if (window == 0L) {
			return false;
		}

		int scancode = GLFW.glfwGetKeyScancode(keyCode);
		KeyInput input = new KeyInput(keyCode, scancode, 0);

		try {
			KeyboardAccessor keyboard = (KeyboardAccessor) mc.keyboard;
			keyboard.revampes$invokeOnKey(window, action, input);
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private long getDelayAfterActionMs(ComboAction action) {
		long leftDelayMs = Math.max(0L, Math.round(leftClickDelay.getInput()));
		long rightDelayMs = Math.max(0L, Math.round(rightClickDelay.getInput()));
		long spellStepDelay = Math.max(leftDelayMs, rightDelayMs);
		
		// Mouse button actions use rightClickDelay + spell step multiplier
		if (action == ComboAction.RIGHT_CLICK || action == ComboAction.MIDDLE_CLICK || 
		    action == ComboAction.BUTTON_4 || action == ComboAction.BUTTON_5) {
			return rightDelayMs + (spellStepDelay * 3L);
		}
		
		// Spell actions use standard delay multiplier
		return spellStepDelay * 3L;
	}

	private List<ComboAction> parseComboActions(String rawActions) {
		List<ComboAction> parsed = new ArrayList<>();
		if (rawActions == null || rawActions.isBlank()) {
			return parsed;
		}

		String[] tokens = rawActions.split("\\s*(?:->|,|\\|)\\s*");
		if (tokens.length == 1 && rawActions.contains(" ")) {
			tokens = rawActions.trim().split("\\s+");
		}

		for (String token : tokens) {
			if (token == null || token.isBlank()) {
				continue;
			}

			ComboAction action = parseComboToken(token);
			if (action == null) {
				Utils.addChatMessage("§cInvalid SpellCombo action: §f" + token.trim());
				return new ArrayList<>();
			}
			parsed.add(action);
		}
		return parsed;
	}

	private ComboAction parseComboToken(String token) {
		String normalized = normalizeToken(token);
		if (normalized.isEmpty()) {
			return null;
		}

		if (matchesSpellToken(normalized, 1, cast1SpellKey.getValue())) {
			return ComboAction.SPELL_1;
		}
		if (matchesSpellToken(normalized, 2, cast2SpellKey.getValue())) {
			return ComboAction.SPELL_2;
		}
		if (matchesSpellToken(normalized, 3, cast3SpellKey.getValue())) {
			return ComboAction.SPELL_3;
		}
		if (matchesSpellToken(normalized, 4, cast4SpellKey.getValue())) {
			return ComboAction.SPELL_4;
		}

		if (normalized.equals("RIGHT") || normalized.equals("RIGHTCLICK") || normalized.equals("MOUSERIGHT") || normalized.equals("RIGHTMOUSE") || normalized.equals("RC")) {
			return ComboAction.RIGHT_CLICK;
		}
		if (normalized.equals("MIDDLE") || normalized.equals("MIDDLECLICK") || normalized.equals("MOUSEMIDDLE") || normalized.equals("MC")) {
			return ComboAction.MIDDLE_CLICK;
		}
		if (normalized.equals("BUTTON4") || normalized.equals("BTN4") || normalized.equals("B4")) {
			return ComboAction.BUTTON_4;
		}
		if (normalized.equals("BUTTON5") || normalized.equals("BTN5") || normalized.equals("B5")) {
			return ComboAction.BUTTON_5;
		}

		return null;
	}

	private boolean matchesSpellToken(String token, int spellIndex, String configuredKeyValue) {
		if (token.equals(String.valueOf(spellIndex))
				|| token.equals("SPELL" + spellIndex)
				|| token.equals("CAST" + spellIndex)
				|| token.equals("S" + spellIndex)
				|| token.equals("C" + spellIndex)) {
			return true;
		}

		String configuredKey = normalizeToken(configuredKeyValue);
		return !configuredKey.isEmpty() && token.equals(configuredKey);
	}

	private boolean tapInputKey(InputUtil.Key key) {
		if (key == null) {
			return false;
		}

		boolean pressedEventSent = false;

		if (SET_KEY_PRESSED_METHOD != null) {
			invokeStaticKeyBindingMethod(SET_KEY_PRESSED_METHOD, key, true);
		}

		if (ON_KEY_PRESSED_METHOD != null && invokeStaticKeyBindingMethod(ON_KEY_PRESSED_METHOD, key)) {
			pressedEventSent = true;
		}

		if (SET_KEY_PRESSED_METHOD != null) {
			invokeStaticKeyBindingMethod(SET_KEY_PRESSED_METHOD, key, false);
		}

		if (!pressedEventSent) {
			pressedEventSent = fallbackTapUsingBoundKeyList(key);
		}

		return pressedEventSent;
	}

	private boolean fallbackTapUsingBoundKeyList(InputUtil.Key target) {
		if (mc.options == null || mc.options.allKeys == null) {
			return false;
		}

		boolean touchedAny = false;
		for (KeyBinding keyBinding : mc.options.allKeys) {
			InputUtil.Key bound = ((KeyBindingAccessor) keyBinding).getKey();
			if (bound == null || !bound.equals(target)) {
				continue;
			}

			keyBinding.setPressed(true);
			keyBinding.setPressed(false);
			touchedAny = true;
		}

		return touchedAny;
	}

	private boolean invokeStaticKeyBindingMethod(Method method, Object... args) {
		try {
			method.invoke(null, args);
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static Method findKeyBindingMethod(String name, Class<?>... parameterTypes) {
		try {
			Method method = KeyBinding.class.getDeclaredMethod(name, parameterTypes);
			method.setAccessible(true);
			return method;
		} catch (Exception ignored) {
			return null;
		}
	}

	private void tryAutofillWynntilsQuickCast() {
		if (triedWynntilsAutofill || !shouldAutofillSpellKeys()) {
			return;
		}
		triedWynntilsAutofill = true;

		int filled = 0;
		Map<String, KeyBinding> keysById = KeyBindingAccessor.getKeysById();
		if (keysById == null || keysById.isEmpty()) {
			return;
		}

		for (Map.Entry<String, KeyBinding> entry : keysById.entrySet()) {
			String translation = normalizeToken(entry.getKey());
			if (!translation.contains("CAST") || !translation.contains("SPELL")) {
				continue;
			}
			if (!translation.contains("WYNN")) {
				continue;
			}

			int spellIndex = findSpellIndex(translation);
			if (spellIndex == -1) {
				continue;
			}

			KeyBinding keyBinding = entry.getValue();
			if (keyBinding == null) {
				continue;
			}

			setDetectedSpellBinding(spellIndex, keyBinding);

			int keyCode = ((KeyBindingAccessor) keyBinding).getKey().getCode();
			String keyName = formatKeyForSetting(keyCode);
			if (keyName.isEmpty()) {
				continue;
			}

			switch (spellIndex) {
				case 1 -> cast1SpellKey.setValue(keyName);
				case 2 -> cast2SpellKey.setValue(keyName);
				case 3 -> cast3SpellKey.setValue(keyName);
				case 4 -> cast4SpellKey.setValue(keyName);
				default -> {
				}
			}
			filled++;
		}

		if (filled > 0) {
			Utils.addChatMessage("§aSpellCombo auto-filled §f" + filled + "§a Wynntils combat keybind(s).");
		}
	}

	private boolean shouldAutofillSpellKeys() {
		return isDefaultOrEmpty(cast1SpellKey.getValue(), "Z")
				&& isDefaultOrEmpty(cast2SpellKey.getValue(), "X")
				&& isDefaultOrEmpty(cast3SpellKey.getValue(), "C")
				&& isDefaultOrEmpty(cast4SpellKey.getValue(), "V");
	}

	private boolean isDefaultOrEmpty(String value, String defaultValue) {
		String normalizedValue = normalizeToken(value);
		return normalizedValue.isEmpty() || normalizedValue.equals(normalizeToken(defaultValue));
	}

	private int findSpellIndex(String normalizedTranslation) {
		if (containsSpellNumber(normalizedTranslation, "1", "1ST", "FIRST")) {
			return 1;
		}
		if (containsSpellNumber(normalizedTranslation, "2", "2ND", "SECOND")) {
			return 2;
		}
		if (containsSpellNumber(normalizedTranslation, "3", "3RD", "THIRD")) {
			return 3;
		}
		if (containsSpellNumber(normalizedTranslation, "4", "4TH", "FOURTH")) {
			return 4;
		}
		return -1;
	}

	private boolean containsSpellNumber(String value, String... patterns) {
		for (String pattern : patterns) {
			if (value.contains("SPELL" + pattern) || value.contains(pattern + "SPELL") || value.endsWith(pattern)) {
				return true;
			}
		}
		return false;
	}

	private int parseKeyCode(String value) {
		String normalized = normalizeToken(value);
		if (normalized.isEmpty() || normalized.equals("NONE") || normalized.equals("UNBOUND")) {
			return GLFW.GLFW_KEY_UNKNOWN;
		}

		if (normalized.length() == 1) {
			char c = normalized.charAt(0);
			if (c >= 'A' && c <= 'Z') {
				return GLFW.GLFW_KEY_A + (c - 'A');
			}
			if (c >= '0' && c <= '9') {
				return GLFW.GLFW_KEY_0 + (c - '0');
			}
		}

		if (normalized.matches("-?\\d+")) {
			try {
				return Integer.parseInt(normalized);
			} catch (NumberFormatException ignored) {
				return GLFW.GLFW_KEY_UNKNOWN;
			}
		}

		if (normalized.startsWith("F") && normalized.length() <= 3 && normalized.length() > 1) {
			try {
				int fKey = Integer.parseInt(normalized.substring(1));
				if (fKey >= 1 && fKey <= 25) {
					return GLFW.GLFW_KEY_F1 + (fKey - 1);
				}
			} catch (NumberFormatException ignored) {
				return GLFW.GLFW_KEY_UNKNOWN;
			}
		}

		return switch (normalized) {
			case "SPACE", "SPACEBAR" -> GLFW.GLFW_KEY_SPACE;
			case "TAB" -> GLFW.GLFW_KEY_TAB;
			case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
			case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
			case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
			case "DELETE", "DEL" -> GLFW.GLFW_KEY_DELETE;
			case "INSERT", "INS" -> GLFW.GLFW_KEY_INSERT;
			case "HOME" -> GLFW.GLFW_KEY_HOME;
			case "END" -> GLFW.GLFW_KEY_END;
			case "PAGEUP", "PGUP" -> GLFW.GLFW_KEY_PAGE_UP;
			case "PAGEDOWN", "PGDN" -> GLFW.GLFW_KEY_PAGE_DOWN;
			case "UP", "UPARROW" -> GLFW.GLFW_KEY_UP;
			case "DOWN", "DOWNARROW" -> GLFW.GLFW_KEY_DOWN;
			case "LEFT", "LEFTARROW" -> GLFW.GLFW_KEY_LEFT;
			case "RIGHT", "RIGHTARROW" -> GLFW.GLFW_KEY_RIGHT;
			case "SHIFT", "LEFTSHIFT", "LSHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
			case "RIGHTSHIFT", "RSHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
			case "CTRL", "CONTROL", "LEFTCONTROL", "LEFTCTRL", "LCTRL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
			case "RIGHTCONTROL", "RIGHTCTRL", "RCTRL" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
			case "ALT", "LEFTALT", "LALT" -> GLFW.GLFW_KEY_LEFT_ALT;
			case "RIGHTALT", "RALT" -> GLFW.GLFW_KEY_RIGHT_ALT;
			case "CAPS", "CAPSLOCK" -> GLFW.GLFW_KEY_CAPS_LOCK;
			case "COMMA" -> GLFW.GLFW_KEY_COMMA;
			case "PERIOD", "DOT" -> GLFW.GLFW_KEY_PERIOD;
			case "MINUS", "DASH" -> GLFW.GLFW_KEY_MINUS;
			case "EQUAL", "EQUALS" -> GLFW.GLFW_KEY_EQUAL;
			case "SEMICOLON" -> GLFW.GLFW_KEY_SEMICOLON;
			case "APOSTROPHE", "QUOTE" -> GLFW.GLFW_KEY_APOSTROPHE;
			case "SLASH", "FORWARDSLASH" -> GLFW.GLFW_KEY_SLASH;
			case "BACKSLASH" -> GLFW.GLFW_KEY_BACKSLASH;
			case "LEFTBRACKET", "LBRACKET" -> GLFW.GLFW_KEY_LEFT_BRACKET;
			case "RIGHTBRACKET", "RBRACKET" -> GLFW.GLFW_KEY_RIGHT_BRACKET;
			case "GRAVE", "TILDE" -> GLFW.GLFW_KEY_GRAVE_ACCENT;
			default -> GLFW.GLFW_KEY_UNKNOWN;
		};
	}

	private String formatKeyForSetting(int keyCode) {
		if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
			return String.valueOf((char) ('A' + (keyCode - GLFW.GLFW_KEY_A)));
		}

		if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
			return String.valueOf((char) ('0' + (keyCode - GLFW.GLFW_KEY_0)));
		}

		if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
			return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
		}

		return switch (keyCode) {
			case GLFW.GLFW_KEY_SPACE -> "SPACE";
			case GLFW.GLFW_KEY_TAB -> "TAB";
			case GLFW.GLFW_KEY_ENTER -> "ENTER";
			case GLFW.GLFW_KEY_ESCAPE -> "ESC";
			case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
			case GLFW.GLFW_KEY_DELETE -> "DELETE";
			case GLFW.GLFW_KEY_INSERT -> "INSERT";
			case GLFW.GLFW_KEY_HOME -> "HOME";
			case GLFW.GLFW_KEY_END -> "END";
			case GLFW.GLFW_KEY_PAGE_UP -> "PAGE_UP";
			case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE_DOWN";
			case GLFW.GLFW_KEY_UP -> "UP";
			case GLFW.GLFW_KEY_DOWN -> "DOWN";
			case GLFW.GLFW_KEY_LEFT -> "LEFT";
			case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
			case GLFW.GLFW_KEY_LEFT_SHIFT -> "LEFT_SHIFT";
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RIGHT_SHIFT";
			case GLFW.GLFW_KEY_LEFT_CONTROL -> "LEFT_CTRL";
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RIGHT_CTRL";
			case GLFW.GLFW_KEY_LEFT_ALT -> "LEFT_ALT";
			case GLFW.GLFW_KEY_RIGHT_ALT -> "RIGHT_ALT";
			case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS_LOCK";
			case GLFW.GLFW_KEY_COMMA -> "COMMA";
			case GLFW.GLFW_KEY_PERIOD -> "PERIOD";
			case GLFW.GLFW_KEY_MINUS -> "MINUS";
			case GLFW.GLFW_KEY_EQUAL -> "EQUAL";
			case GLFW.GLFW_KEY_SEMICOLON -> "SEMICOLON";
			case GLFW.GLFW_KEY_APOSTROPHE -> "APOSTROPHE";
			case GLFW.GLFW_KEY_SLASH -> "SLASH";
			case GLFW.GLFW_KEY_BACKSLASH -> "BACKSLASH";
			case GLFW.GLFW_KEY_LEFT_BRACKET -> "LEFT_BRACKET";
			case GLFW.GLFW_KEY_RIGHT_BRACKET -> "RIGHT_BRACKET";
			case GLFW.GLFW_KEY_GRAVE_ACCENT -> "GRAVE";
			default -> "";
		};
	}

	private String normalizeToken(String value) {
		if (value == null) {
			return "";
		}
		return value.toUpperCase(Locale.ROOT)
				.replace(" ", "")
				.replace("_", "")
				.replace("-", "")
				.trim();
	}

	private boolean isBusy() {
		return !queuedActions.isEmpty() || nextActionAtMs > System.currentTimeMillis();
	}

	private void clearQueue() {
		queuedActions.clear();
		nextActionAtMs = 0L;
	}

	private void resetTriggerHoldStates() {
		combo1TriggerHeld = false;
		combo2TriggerHeld = false;
		combo3TriggerHeld = false;
	}

	private void clearDetectedSpellBindings() {
		detectedSpell1Binding = null;
		detectedSpell2Binding = null;
		detectedSpell3Binding = null;
		detectedSpell4Binding = null;
	}
}
