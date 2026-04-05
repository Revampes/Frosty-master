package com.revampes.Fault.modules.impl.dungeon.PuzzleSolver;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.ReceiveMessageEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TriviaSolver extends Module {
	private static final Pattern ANSWER_LINE_REGEX = Pattern.compile("^\\s*([ⓐⓑⓒ])\\s+(.*)$");
	private static final Pattern QUESTION_REGEX = Pattern.compile("^\\s*(.*\\?)$");
	private static final Pattern INTRO_REGEX = Pattern.compile("^\\[STATUE] Oruo the Omniscient: I am Oruo the Omniscient\\. I have lived many lives\\. I have learned all there is to know\\.$");
	private static final Pattern QUESTION_SOLVED_REGEX = Pattern.compile("^\\[STATUE] Oruo the Omniscient: \\w+ answered Question #\\d+ correctly!$");
	private static final Pattern FINAL_SOLVED_REGEX = Pattern.compile("^\\[STATUE] Oruo the Omniscient: \\w+ answered the final question correctly!$");
	private static final Pattern YIKES_REGEX = Pattern.compile("^\\[STATUE] Oruo the Omniscient: Yikes$");

	private static final int ROOM_SIZE = 32;
	private static final int ROOM_WORLD_START = -200;

	private static final Color CORRECT_OUTLINE = new Color(0, 255, 0, 255);
	private static final Color CORRECT_FILL = new Color(0, 255, 0, 80);

	private final Map<String, List<String>> solutions = createSolutions();
	private final Map<String, int[]> typeBlocks = createTypeBlocks();

	private boolean inQuiz = false;
	private List<String> solution = null;
	private String currentAnswer = null;
	private long enteredAtTick = -1L;

	private int worldIdentity = Integer.MIN_VALUE;
	private int roomCornerX = Integer.MIN_VALUE;
	private int roomCornerZ = Integer.MIN_VALUE;
	private int roomRotation = 0;
	private boolean replayingColoredMessage = false;

	public TriviaSolver() {
		super("TriviaSolver", category.Dungeon);
	}

	@Override
	public String getDesc() {
		return "Highlights the correct answer block and chat message for Quiz puzzle.";
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

		if (inQuiz && (roomCornerX == Integer.MIN_VALUE || roomCornerZ == Integer.MIN_VALUE)) {
			detectQuizRoomAnchor();
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

		String stripped = Utils.stripColor(event.getMessage().getString());
		if (stripped == null) {
			return;
		}

		String text = stripped.trim();
		if (text.isEmpty()) {
			return;
		}

		if (INTRO_REGEX.matcher(text).matches()) {
			inQuiz = true;
			enteredAtTick = currentTick();
			return;
		}

		if (QUESTION_SOLVED_REGEX.matcher(text).matches()) {
			resetSolution();
			return;
		}

		if (FINAL_SOLVED_REGEX.matcher(text).matches()) {
			if (enteredAtTick >= 0L && mc.world != null) {
				double seconds = (currentTick() - enteredAtTick) * 0.05;
				Utils.addChatMessage(String.format("\u00A7bQuiz took\u00A7f: \u00A76%.2fs", seconds));
			}
			inQuiz = false;
			resetSolution();
			return;
		}

		if (YIKES_REGEX.matcher(text).matches()) {
			inQuiz = false;
			resetSolution();
			return;
		}

		Matcher answerMatch = ANSWER_LINE_REGEX.matcher(text);
		if (answerMatch.matches()) {
			if (solution == null) {
				return;
			}

			inQuiz = true;

			String type = answerMatch.group(1);
			String message = answerMatch.group(2).trim();
			boolean correct = isCorrectAnswer(message);

			if (correct) {
				currentAnswer = type;
				detectQuizRoomAnchor();
			}

			event.cancel();
			replayColoredAnswerLine(type, message, correct);
			return;
		}

		Matcher questionMatch = QUESTION_REGEX.matcher(text);
		if (!questionMatch.matches()) {
			return;
		}

		String question = questionMatch.group(1).trim();
		String statuePrefix = "[STATUE] Oruo the Omniscient: ";
		if (question.startsWith(statuePrefix)) {
			question = question.substring(statuePrefix.length()).trim();
		}

		List<String> found = resolveQuestionSolution(question);
		if (found == null) {
			return;
		}

		inQuiz = true;
		solution = found;
		currentAnswer = null;

		if (roomCornerX == Integer.MIN_VALUE || roomCornerZ == Integer.MIN_VALUE) {
			detectQuizRoomAnchor();
		}
	}

	@EventHandler
	public void onRender3D(Render3DEvent event) {
		if (!inQuiz || solution == null || currentAnswer == null || mc.world == null) {
			return;
		}

		if (roomCornerX == Integer.MIN_VALUE || roomCornerZ == Integer.MIN_VALUE) {
			if (!detectQuizRoomAnchor()) {
				return;
			}
		}

		int[] compPos = typeBlocks.get(currentAnswer);
		if (compPos == null) {
			return;
		}

		BlockPos worldPos = fromComp(roomCornerX, roomCornerZ, compPos[0], compPos[1], 70, roomRotation);
		MatrixStack stack = event.getMatrix();

		Box box = new Box(worldPos);
		RenderUtils.drawBox(stack, box, CORRECT_OUTLINE, 2.0);
		RenderUtils.drawBoxFilled(stack, box, CORRECT_FILL);
	}

	private boolean detectQuizRoomAnchor() {
		if (mc.player == null || mc.world == null) {
			return false;
		}

		int baseCornerX = toRoomCorner((int) Math.floor(mc.player.getX()));
		int baseCornerZ = toRoomCorner((int) Math.floor(mc.player.getZ()));
		int[] deltas = new int[] {-ROOM_SIZE, 0, ROOM_SIZE};

		int bestScore = Integer.MIN_VALUE;
		int bestCornerX = Integer.MIN_VALUE;
		int bestCornerZ = Integer.MIN_VALUE;
		int bestRotation = 0;
		double bestDistance = Double.MAX_VALUE;

		for (int dx : deltas) {
			for (int dz : deltas) {
				int cornerX = baseCornerX + dx;
				int cornerZ = baseCornerZ + dz;

				for (int rotation = 0; rotation < 4; rotation++) {
					int score = scoreQuizAnchor(cornerX, cornerZ, rotation);
					if (score <= 0) {
						continue;
					}

					double centerX = cornerX + 15.5;
					double centerZ = cornerZ + 15.5;
					double distance = mc.player.squaredDistanceTo(centerX, mc.player.getY(), centerZ);

					if (score > bestScore || (score == bestScore && distance < bestDistance)) {
						bestScore = score;
						bestDistance = distance;
						bestCornerX = cornerX;
						bestCornerZ = cornerZ;
						bestRotation = rotation;
					}
				}
			}
		}

		if (bestScore <= 0) {
			return false;
		}

		roomCornerX = bestCornerX;
		roomCornerZ = bestCornerZ;
		roomRotation = bestRotation;
		return true;
	}

	private int scoreQuizAnchor(int cornerX, int cornerZ, int rotation) {
		if (mc.world == null) {
			return Integer.MIN_VALUE;
		}

		int score = 0;
		for (int[] compPos : typeBlocks.values()) {
			BlockPos pos = fromComp(cornerX, cornerZ, compPos[0], compPos[1], 70, rotation);
			Block block = mc.world.getBlockState(pos).getBlock();
			if (isSolidBlock(block)) {
				score += 3;
			}

			Block below = mc.world.getBlockState(pos.down()).getBlock();
			if (isSolidBlock(below)) {
				score += 1;
			}
		}

		if (currentAnswer != null) {
			int[] answerComp = typeBlocks.get(currentAnswer);
			if (answerComp != null) {
				BlockPos answerPos = fromComp(cornerX, cornerZ, answerComp[0], answerComp[1], 70, rotation);
				if (isSolidBlock(mc.world.getBlockState(answerPos).getBlock())) {
					score += 4;
				} else {
					score -= 2;
				}
			}
		}

		return score;
	}

	private boolean isSolidBlock(Block block) {
		return block != Blocks.AIR && block != Blocks.CAVE_AIR && block != Blocks.VOID_AIR;
	}

	private long currentTick() {
		return mc.world == null ? 0L : mc.world.getTime();
	}

	private boolean isCorrectAnswer(String message) {
		if (solution == null) {
			return false;
		}

		for (String answer : solution) {
			if (answer.equalsIgnoreCase(message)) {
				return true;
			}
		}
		return false;
	}

	private List<String> resolveQuestionSolution(String rawQuestion) {
		String question = rawQuestion == null ? "" : rawQuestion.trim();
		if (question.isEmpty()) {
			return null;
		}

		if ("What SkyBlock year is it?".equals(question)) {
			return List.of(currentYear());
		}

		if ("glass?".equals(question)) {
			question = "What is the name of the vendor in the Hub who sells stained glass?";
		}

		List<String> answer = solutions.get(question);
		return answer == null ? null : answer;
	}

	private void replayColoredAnswerLine(String type, String message, boolean correct) {
		if (mc.player == null) {
			return;
		}

		replayingColoredMessage = true;
		try {
			String color = correct ? "\u00A7a" : "\u00A7c";
			mc.player.sendMessage(Text.literal("    " + color + type + " " + message), false);
		} finally {
			replayingColoredMessage = false;
		}
	}

	private int toRoomCorner(int coord) {
		return Math.floorDiv(coord - ROOM_WORLD_START, ROOM_SIZE) * ROOM_SIZE + ROOM_WORLD_START;
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

	private String currentYear() {
		long year = (((System.currentTimeMillis() / 1000L) - 1560276000L) / 446400L) + 1L;
		return "Year " + year;
	}

	private void resetSolution() {
		enteredAtTick = -1L;
		solution = null;
		currentAnswer = null;
	}

	private void resetState() {
		inQuiz = false;
		roomCornerX = Integer.MIN_VALUE;
		roomCornerZ = Integer.MIN_VALUE;
		roomRotation = 0;
		replayingColoredMessage = false;
		resetSolution();
	}

	private Map<String, int[]> createTypeBlocks() {
		Map<String, int[]> map = new HashMap<>();
		map.put("ⓐ", new int[] {20, 6});
		map.put("ⓑ", new int[] {15, 9});
		map.put("ⓒ", new int[] {10, 6});
		return Collections.unmodifiableMap(map);
	}

	private Map<String, List<String>> createSolutions() {
		Map<String, List<String>> map = new HashMap<>();

		addSolution(map, "What is the status of The Watcher?", "Stalker");
		addSolution(map, "What is the status of Bonzo?", "New Necromancer");
		addSolution(map, "What is the status of Scarf?", "Apprentice Necromancer");
		addSolution(map, "What is the status of The Professor?", "Professor");
		addSolution(map, "What is the status of Thorn?", "Shaman Necromancer");
		addSolution(map, "What is the status of Livid?", "Master Necromancer");
		addSolution(map, "What is the status of Sadan?", "Necromancer Lord");
		addSolution(map, "What is the status of Maxor, Storm, Goldor, and Necron?", "The Wither Lords");
		addSolution(map, "How many total Fairy Souls are there?", "267 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in Spider's Den?", "19 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in Spiders Den?", "19 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in The End?", "12 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in The Farming Islands?", "20 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in Crimson Isle?", "29 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in The Park?", "12 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in Jerry's Workshop?", "5 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in Hub?", "80 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in The Hub?", "80 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in Deep Caverns?", "21 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in Gold Mine?", "12 Fairy Souls");
		addSolution(map, "How many Fairy Souls are there in Dungeon Hub?", "7 Fairy Souls");
		addSolution(map, "Which brother is on the Spider's Den?", "Rick");
		addSolution(map, "Which brother is on the Spiders Den?", "Rick");
		addSolution(map, "What is the name of Rick's brother?", "Pat");
		addSolution(map, "What is the name of the vendor in the Hub who sells stained glass?", "Wool Weaver");
		addSolution(map, "What is the name of the person that upgrades pets?", "Kat");
		addSolution(map, "What is the name of the lady of the Nether?", "Elle");
		addSolution(map, "Which villager in the Village gives you a Rogue Sword?", "Jamie");
		addSolution(map, "How many unique minions are there?", "60 Minions");

		addSolution(
			map,
			"Which of these enemies does not spawn in the Spider's Den?",
			"Zombie Spider",
			"Cave Spider",
			"Wither Skeleton",
			"Dashing Spooder",
			"Broodfather",
			"Night Spider"
		);
		addSolution(
			map,
			"Which of these enemies does not spawn in the Spiders Den?",
			"Zombie Spider",
			"Cave Spider",
			"Wither Skeleton",
			"Dashing Spooder",
			"Broodfather",
			"Night Spider"
		);
		addSolution(
			map,
			"Which of these monsters only spawns at night?",
			"Zombie Villager",
			"Ghast"
		);
		addSolution(
			map,
			"Which of these is not a dragon in The End?",
			"Zoomer Dragon",
			"Weak Dragon",
			"Stonk Dragon",
			"Holy Dragon",
			"Boomer Dragon",
			"Booger Dragon",
			"Older Dragon",
			"Elder Dragon",
			"Stable Dragon",
			"Professor Dragon"
		);

		return Collections.unmodifiableMap(map);
	}

	private void addSolution(Map<String, List<String>> map, String question, String... answers) {
		map.put(question, List.of(answers));
	}
}
