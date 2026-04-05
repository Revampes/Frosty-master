package com.revampes.Fault.modules.impl.dungeon.PuzzleSolver;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.ReceiveMessageEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThreeWeirdosSolver extends Module {
	private static final Pattern NPC_CHAT_REGEX = Pattern.compile("^\\[NPC] ([^:]+):\\s*(.*)$");
	private static final Pattern COMPLETED_REGEX = Pattern.compile("^PUZZLE SOLVED! \\w+ wasn't fooled by \\w+! Good job!$");
	private static final Pattern FAILED_REGEX = Pattern.compile("^PUZZLE FAIL! \\w+ was fooled by \\w+! Yikes!$");

	private static final List<Pattern> SOLUTION_PATTERNS = List.of(
		Pattern.compile("^The reward is not in my chest!$"),
		Pattern.compile("^At least one of them is lying, and the reward is not in \\w+'s chest\\.?$"),
		Pattern.compile("^My chest doesn't have the reward\\. We are all telling the truth\\.?$"),
		Pattern.compile("^My chest has the reward and I'm telling the truth!$"),
		Pattern.compile("^The reward isn't in any of our chests\\.?$"),
		Pattern.compile("^Both of them are telling the truth\\. Also, \\w+ has the reward in their chest\\.?$")
	);

	private static final List<Pattern> WRONG_PATTERNS = List.of(
		Pattern.compile("^One of us is telling the truth!$"),
		Pattern.compile("^They are both telling the truth\\. The reward isn't in \\w+'s chest\\.$"),
		Pattern.compile("^We are all telling the truth!$"),
		Pattern.compile("^\\w+ is telling the truth and the reward is in his chest\\.$"),
		Pattern.compile("^My chest doesn't have the reward\\. At least one of the others is telling the truth!$"),
		Pattern.compile("^One of the others is lying\\.$"),
		Pattern.compile("^They are both telling the truth, the reward is in \\w+'s chest\\.$"),
		Pattern.compile("^They are both lying, the reward is in my chest!$"),
		Pattern.compile("^The reward is in my chest\\.$"),
		Pattern.compile("^The reward is not in my chest\\. They are both lying\\.$"),
		Pattern.compile("^\\w+ is telling the truth\\.$"),
		Pattern.compile("^My chest has the reward\\.$")
	);

	private static final int[][] DIRS = new int[][] {
		{1, 0}, {-1, 0}, {0, 1}, {0, -1}
	};

	private static final Color CORRECT_OUTLINE_COLOR = new Color(0, 255, 0, 255);
	private static final Color CORRECT_FILLED_COLOR = new Color(0, 255, 0, 130);
	private static final Color WRONG_OUTLINE_COLOR = new Color(255, 0, 0, 255);
	private static final Color WRONG_FILLED_COLOR = new Color(255, 0, 0, 80);

	private final Map<String, Integer> entityList = new ConcurrentHashMap<>();
	private final Map<String, AnswerData> answers = new ConcurrentHashMap<>();
	private final Map<String, Boolean> pendingAnswers = new ConcurrentHashMap<>();

	private long enteredAtMs = -1L;
	private int worldIdentity = Integer.MIN_VALUE;
	private boolean replayingColoredMessage = false;

	public static final class AnswerData {
		private final Vec3d entityPos;
		private final BlockPos chestPos;
		private final boolean isCorrect;

		public AnswerData(Vec3d entityPos, BlockPos chestPos, boolean isCorrect) {
			this.entityPos = entityPos;
			this.chestPos = chestPos;
			this.isCorrect = isCorrect;
		}
	}

	public ThreeWeirdosSolver() {
		super("ThreeWeirdosSolver", category.Dungeon);
	}

	@Override
	public String getDesc() {
		return "Highlights the correct chest in the Three Weirdos puzzle room.";
	}

	@Override
	public void onDisable() {
		reset();
		worldIdentity = Integer.MIN_VALUE;
	}

	@EventHandler
	public void onPreUpdate(PreUpdateEvent event) {
		if (mc.world == null || mc.player == null || !DungeonUtils.isInDungeon()) {
			reset();
			if (mc.world == null || !DungeonUtils.isInDungeon()) {
				worldIdentity = Integer.MIN_VALUE;
			}
			return;
		}

		int currentWorldIdentity = System.identityHashCode(mc.world);
		if (currentWorldIdentity != worldIdentity) {
			worldIdentity = currentWorldIdentity;
			reset();
		}

		rebuildEntityMap();
		resolvePendingAnswers();
		refreshAnswerEntityPositions();

		if (enteredAtMs < 0L && !entityList.isEmpty()) {
			enteredAtMs = Util.getMeasuringTimeMs();
		}
	}

	@EventHandler
	public void onReceiveMessage(ReceiveMessageEvent event) {
		if (mc.player == null) {
			return;
		}

		if (replayingColoredMessage) {
			return;
		}

		String text = Utils.stripColor(event.getMessage().getString()).trim();
		if (text.isEmpty()) {
			return;
		}

		if (COMPLETED_REGEX.matcher(text).matches()) {
			if (enteredAtMs >= 0L) {
				double seconds = (Util.getMeasuringTimeMs() - enteredAtMs) / 1000.0;
				Utils.addChatMessage(String.format("\u00A7bThree Weirdos took\u00A7f: \u00A76%.2fs", seconds));
			}
			reset();
			return;
		}

		if (FAILED_REGEX.matcher(text).matches()) {
			reset();
			return;
		}

		Matcher npcMatch = NPC_CHAT_REGEX.matcher(text);
		if (!npcMatch.matches()) {
			return;
		}

		String name = npcMatch.group(1).trim();
		String normalizedName = normalizeNpcName(name);
		if (normalizedName == null) {
			return;
		}

		String message = npcMatch.group(2);

		if (enteredAtMs < 0L) {
			enteredAtMs = Util.getMeasuringTimeMs();
		}

		boolean isWrong = matchesAny(message, WRONG_PATTERNS);
		boolean isSolution = matchesAny(message, SOLUTION_PATTERNS);

		if (!isWrong && !isSolution) {
			return;
		}

		if (isSolution) {
			event.cancel();
			replayColoredNpcMessage(name, message);
		}

		pendingAnswers.put(normalizedName, isSolution);

		AnswerData data = createChest(normalizedName, isSolution);
		if (data != null) {
			answers.put(normalizedName, data);
			pendingAnswers.remove(normalizedName);
		}
	}

	@EventHandler
	public void onRender3D(Render3DEvent event) {
		if (answers.isEmpty()) {
			return;
		}

		MatrixStack stack = event.getMatrix();
		List<AnswerData> renderAnswers = buildRenderAnswers();

		if (renderAnswers.isEmpty()) {
			return;
		}

		for (AnswerData answer : renderAnswers) {
			Color outline = answer.isCorrect ? CORRECT_OUTLINE_COLOR : WRONG_OUTLINE_COLOR;
			Color fill = answer.isCorrect ? CORRECT_FILLED_COLOR : WRONG_FILLED_COLOR;

			Box chestBox = new Box(answer.chestPos);
			RenderUtils.drawBox(stack, chestBox, outline, 2.0);
			RenderUtils.drawBoxFilled(stack, chestBox, fill);

			Box entityBox = new Box(
				answer.entityPos.x - 0.4,
				answer.entityPos.y,
				answer.entityPos.z - 0.4,
				answer.entityPos.x + 0.4,
				answer.entityPos.y + 2.0,
				answer.entityPos.z + 0.4
			);
			RenderUtils.drawBox(stack, entityBox, outline, 2.0);
			RenderUtils.drawBoxFilled(stack, entityBox, fill);
		}
	}

	private List<AnswerData> buildRenderAnswers() {
		List<AnswerData> renderAnswers = new ArrayList<>(answers.values());
		boolean hasCorrect = false;

		Set<BlockPos> wrongChests = new HashSet<>();
		for (AnswerData answer : renderAnswers) {
			if (answer.isCorrect) {
				hasCorrect = true;
			} else {
				wrongChests.add(answer.chestPos);
			}
		}

		if (hasCorrect) {
			return renderAnswers;
		}

		Map<BlockPos, Vec3d> allChestCandidates = collectPuzzleChestCandidates();
		List<Map.Entry<BlockPos, Vec3d>> unknownCandidates = new ArrayList<>();
		for (Map.Entry<BlockPos, Vec3d> candidate : allChestCandidates.entrySet()) {
			if (!wrongChests.contains(candidate.getKey())) {
				unknownCandidates.add(candidate);
			}
		}

		if (unknownCandidates.size() == 1) {
			Map.Entry<BlockPos, Vec3d> only = unknownCandidates.get(0);
			renderAnswers.add(new AnswerData(only.getValue(), only.getKey(), true));
		}

		return renderAnswers;
	}

	private Map<BlockPos, Vec3d> collectPuzzleChestCandidates() {
		Map<BlockPos, Vec3d> candidates = new HashMap<>();
		if (mc.world == null) {
			return candidates;
		}

		for (Integer entityId : entityList.values()) {
			Entity entity = mc.world.getEntityById(entityId);
			if (entity == null) {
				continue;
			}

			BlockPos chestPos = findClosestChest(entity.getBlockPos(), 4, 6);
			if (chestPos == null) {
				continue;
			}

			candidates.putIfAbsent(chestPos, new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
		}

		return candidates;
	}

	private void rebuildEntityMap() {
		entityList.clear();

		for (Entity entity : mc.world.getEntities()) {
			if (!(entity instanceof ArmorStandEntity stand)) {
				continue;
			}

			if (stand.getCustomName() == null) {
				continue;
			}

			String name = Utils.stripColor(stand.getCustomName().getString()).trim();
			if (name.isEmpty()) {
				continue;
			}

			String normalized = normalizeNpcName(name);
			if (normalized != null) {
				entityList.put(normalized, stand.getId());
			}
		}
	}

	private void refreshAnswerEntityPositions() {
		if (answers.isEmpty()) {
			return;
		}

		for (Map.Entry<String, AnswerData> entry : answers.entrySet()) {
			AnswerData previous = entry.getValue();
			AnswerData refreshed = createChest(entry.getKey(), previous.isCorrect);
			if (refreshed != null) {
				answers.put(entry.getKey(), refreshed);
			}
		}
	}

	private void resolvePendingAnswers() {
		if (pendingAnswers.isEmpty()) {
			return;
		}

		for (Map.Entry<String, Boolean> entry : pendingAnswers.entrySet()) {
			AnswerData data = createChest(entry.getKey(), entry.getValue());
			if (data != null) {
				answers.put(entry.getKey(), data);
				pendingAnswers.remove(entry.getKey());
			}
		}
	}

	private void replayColoredNpcMessage(String name, String message) {
		if (mc.player == null) {
			return;
		}

		replayingColoredMessage = true;
		try {
			mc.player.sendMessage(Text.literal("\u00A7e[NPC] \u00A7b\u00A7l" + name + ": \u00A7a\u00A7l" + message), false);
		} finally {
			replayingColoredMessage = false;
		}
	}

	private boolean matchesAny(String input, List<Pattern> patterns) {
		for (Pattern pattern : patterns) {
			if (pattern.matcher(input).matches()) {
				return true;
			}
		}
		return false;
	}

	private AnswerData createChest(String name, boolean isCorrect) {
		if (mc.world == null) {
			return null;
		}

		Integer entityId = findEntityId(name);
		if (entityId == null) {
			return null;
		}

		Entity entity = mc.world.getEntityById(entityId);
		if (entity == null) {
			return null;
		}

		Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
		BlockPos basePos = entity.getBlockPos();
		BlockPos chestPos = findClosestChest(basePos, 4, 6);

		if (chestPos == null) {
			return null;
		}

		return new AnswerData(entityPos, chestPos, isCorrect);
	}

	private Integer findEntityId(String normalizedName) {
		Integer direct = entityList.get(normalizedName);
		if (direct != null) {
			return direct;
		}

		for (Map.Entry<String, Integer> entry : entityList.entrySet()) {
			String key = entry.getKey();
			if (key.equals(normalizedName) || key.contains(normalizedName) || normalizedName.contains(key)) {
				return entry.getValue();
			}
		}

		return null;
	}

	private BlockPos findClosestChest(BlockPos basePos, int horizontalRange, int verticalRange) {
		if (mc.world == null) {
			return null;
		}

		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int dx = -horizontalRange; dx <= horizontalRange; dx++) {
			for (int dz = -horizontalRange; dz <= horizontalRange; dz++) {
				for (int dy = -verticalRange; dy <= verticalRange; dy++) {
					BlockPos pos = basePos.add(dx, dy, dz);
					if (mc.world.getBlockState(pos).getBlock() != Blocks.CHEST) {
						continue;
					}

					double distance = basePos.getSquaredDistance(pos);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = pos;
					}
				}
			}
		}

		if (best != null) {
			return best;
		}

		int x0 = basePos.getX();
		int y0 = basePos.getY();
		int z0 = basePos.getZ();

		for (int[] dir : DIRS) {
			int chestX = x0 + dir[0];
			int chestZ = z0 + dir[1];
			for (int yOffset = -4; yOffset <= 3; yOffset++) {
				BlockPos pos = new BlockPos(chestX, y0 + yOffset, chestZ);
				if (mc.world.getBlockState(pos).getBlock() == Blocks.CHEST) {
					return pos;
				}
			}
		}

		return null;
	}

	private String normalizeNpcName(String rawName) {
		if (rawName == null) {
			return null;
		}

		String name = Utils.stripColor(rawName).trim();
		if (name.isEmpty()) {
			return null;
		}

		if (name.startsWith("[NPC]")) {
			name = name.substring(5).trim();
		}

		int colonIndex = name.indexOf(':');
		if (colonIndex >= 0) {
			name = name.substring(0, colonIndex).trim();
		}

		if (name.endsWith(":")) {
			name = name.substring(0, name.length() - 1).trim();
		}

		if (name.isEmpty()) {
			return null;
		}

		return name.toLowerCase(Locale.ROOT);
	}

	private void reset() {
		enteredAtMs = -1L;
		answers.clear();
		pendingAnswers.clear();
		entityList.clear();
	}
}
