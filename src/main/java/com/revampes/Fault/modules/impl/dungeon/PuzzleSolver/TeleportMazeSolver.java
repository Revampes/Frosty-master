package com.revampes.Fault.modules.impl.dungeon.PuzzleSolver;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.events.impl.SendPacketEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class TeleportMazeSolver extends Module {
	private static final int ROOM_SIZE = 32;
	private static final int ROOM_WORLD_START = -200;
	private static final int[] SEARCH_DELTAS = new int[] {-ROOM_SIZE * 2, -ROOM_SIZE, 0, ROOM_SIZE, ROOM_SIZE * 2};

	private static final int PAD_Y = 69;
	private static final int FINISH_COMP_X = 15;
	private static final int FINISH_COMP_Z = 20;
	private static final int FINISH_Y = 70;

	private static final int DETECTION_MIN_SCORE = 12;
	private static final double PAD_MATCH_DISTANCE_SQ = 1.9 * 1.9;
	private static final double ROOM_RETAIN_DISTANCE_SQ = 70.0 * 70.0;

	private static final Color CORRECT_COLOR = Color.GREEN;
	private static final Color VISITED_COLOR = Color.RED;
	private static final Color POSSIBLE_COLOR = Color.ORANGE;

	private final List<CompPad> endFramePositions = createEndFramePositions();
	private final List<Pad> pads = new ArrayList<>();

	private boolean inMaze = false;
	private long enteredAtTick = -1L;

	private int roomCornerX = Integer.MIN_VALUE;
	private int roomCornerZ = Integer.MIN_VALUE;
	private int roomRotation = 0;

	private int worldIdentity = Integer.MIN_VALUE;
	private int detectCooldownTicks = 0;

	private static final class CompPad {
		private final int cx;
		private final int cz;
		private final int tx;
		private final int tz;
		private final boolean special;
		private final boolean isEnd;

		private CompPad(int cx, int cz, int tx, int tz) {
			this(cx, cz, tx, tz, false, false);
		}

		private CompPad(int cx, int cz, int tx, int tz, boolean special) {
			this(cx, cz, tx, tz, special, false);
		}

		private CompPad(int cx, int cz, int tx, int tz, boolean special, boolean isEnd) {
			this.cx = cx;
			this.cz = cz;
			this.tx = tx;
			this.tz = tz;
			this.special = special;
			this.isEnd = isEnd;
		}
	}

	private static final class Pad {
		private final int x;
		private final int z;
		private final int tx;
		private final int tz;
		private final boolean special;
		private final boolean isEnd;

		private boolean visited;
		private boolean correct;
		private boolean possible;
		private boolean incorrect;

		private Pad(int x, int z, int tx, int tz, boolean special, boolean isEnd) {
			this.x = x;
			this.z = z;
			this.tx = tx;
			this.tz = tz;
			this.special = special;
			this.isEnd = isEnd;
		}
	}

	private static final class MazeCandidate {
		private final int cornerX;
		private final int cornerZ;
		private final int rotation;
		private final int score;
		private final double distanceSq;

		private MazeCandidate(int cornerX, int cornerZ, int rotation, int score, double distanceSq) {
			this.cornerX = cornerX;
			this.cornerZ = cornerZ;
			this.rotation = rotation;
			this.score = score;
			this.distanceSq = distanceSq;
		}
	}

	private static final class Vec2 {
		private final double u;
		private final double v;

		private Vec2(double u, double v) {
			this.u = u;
			this.v = v;
		}

		private boolean parallel(Vec2 other) {
			if (eq(u, 0.0)) {
				return eq(other.u, 0.0) && sign(v) == sign(other.v);
			}

			if (eq(v, 0.0)) {
				return eq(other.v, 0.0) && sign(u) == sign(other.u);
			}

			return eq(u * other.v, v * other.u) && sign(u) == sign(other.u);
		}

		private static int sign(double value) {
			if (eq(value, 0.0)) {
				return 0;
			}
			return value > 0.0 ? 1 : -1;
		}

		private static boolean eq(double a, double b) {
			return Math.abs(a - b) < 1e-2;
		}
	}

	public TeleportMazeSolver() {
		super("TeleportMazeSolver", category.Dungeon);
	}

	@Override
	public String getDesc() {
		return "Highlights the correct teleport pad in Teleport Maze puzzle.";
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

		if (inMaze) {
			if (isPlayerNearRoom(roomCornerX, roomCornerZ, ROOM_RETAIN_DISTANCE_SQ)) {
				return;
			}

			if (!tryDetectMazeRoom()) {
				reset();
				detectCooldownTicks = 4;
			}
			return;
		}

		if (detectCooldownTicks > 0) {
			detectCooldownTicks--;
			return;
		}

		if (!tryDetectMazeRoom()) {
			detectCooldownTicks = 4;
		}
	}

	@EventHandler
	public void onReceivePacket(ReceivePacketEvent event) {
		if (!inMaze || mc.player == null) {
			return;
		}

		if (!(event.getPacket() instanceof PlayerPositionLookS2CPacket packet)) {
			return;
		}

		if (packet.relatives() != null && !packet.relatives().isEmpty()) {
			return;
		}

		EntityPosition change = packet.change();
		if (change == null) {
			return;
		}

		Vec3d pos = change.position();
		if (pos == null) {
			return;
		}

		double oldX = mc.player.getX();
		double oldZ = mc.player.getZ();
		double newX = pos.x;
		double newZ = pos.z;
		float yaw = change.yaw();

		mc.execute(() -> handleTeleport(oldX, oldZ, newX, newZ, yaw));
	}

	@EventHandler
	public void onRender3D(Render3DEvent event) {
		if (!inMaze || pads.isEmpty()) {
			return;
		}

		MatrixStack stack = event.getMatrix();

		for (Pad pad : pads) {
			Color color;
			if (pad.correct) {
				color = CORRECT_COLOR;
			} else if (pad.visited) {
				color = VISITED_COLOR;
			} else if (pad.possible) {
				color = POSSIBLE_COLOR;
			} else {
				continue;
			}

			Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 80);
			Box box = new Box(pad.x, PAD_Y, pad.z, pad.x + 1.0, PAD_Y + 1.0, pad.z + 1.0);
			RenderUtils.drawBox(stack, box, color, 2.0);
			RenderUtils.drawBoxFilled(stack, box, fill);
		}
	}

	@EventHandler
	public void onSendPacket(SendPacketEvent event) {
		if (!inMaze || enteredAtTick < 0L) {
			return;
		}

		if (!(event.getPacket() instanceof PlayerInteractBlockC2SPacket packet)) {
			return;
		}

		BlockHitResult hitResult = packet.getBlockHitResult();
		if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
			return;
		}

		BlockPos pos = hitResult.getBlockPos();
		int[] comp = toComp(roomCornerX, roomCornerZ, pos.getX(), pos.getZ(), roomRotation);
		if (comp == null) {
			return;
		}

		if (comp[0] != FINISH_COMP_X || pos.getY() != FINISH_Y || comp[1] != FINISH_COMP_Z) {
			return;
		}

		double seconds = (currentTick() - enteredAtTick) * 0.05;
		Utils.addChatMessage(String.format("\u00A7bTeleport Maze took\u00A7f: \u00A76%.2fs", seconds));
		enteredAtTick = -1L;
	}

	private void handleTeleport(double oldX, double oldZ, double newX, double newZ, float yaw) {
		if (!inMaze || pads.isEmpty()) {
			return;
		}

		Pad newPad = nearestPadWithin(newX, newZ, PAD_MATCH_DISTANCE_SQ);
		if (newPad == null) {
			return;
		}

		Pad oldPad = nearestPadWithin(oldX, oldZ, PAD_MATCH_DISTANCE_SQ);
		if (oldPad == null) {
			oldPad = newPad;
		}

		if (newPad.special) {
			clearPadState();
			if (newPad.isEnd) {
				return;
			}
		} else {
			oldPad.visited = true;
			newPad.visited = true;
		}

		Vec2 direction = new Vec2(
			Math.cos((yaw + 90.0) / 180.0 * Math.PI),
			Math.sin((yaw + 90.0) / 180.0 * Math.PI)
		);

		for (Pad pad : pads) {
			if (pad == newPad || pad.special) {
				continue;
			}

			Vec2 offset = new Vec2(pad.tx - newPad.tx, pad.tz - newPad.tz);
			boolean matches = direction.parallel(offset);

			pad.correct = matches && !pad.incorrect;
			pad.possible = pad.possible || matches;
			pad.incorrect = pad.incorrect || !matches;
		}
	}

	private Pad nearestPad(double x, double z) {
		return nearestPadWithin(x, z, Double.MAX_VALUE);
	}

	private Pad nearestPadWithin(double x, double z, double maxDistanceSq) {
		Pad best = null;
		double bestDist = maxDistanceSq;

		for (Pad pad : pads) {
			double dx = (pad.x + 0.5) - x;
			double dz = (pad.z + 0.5) - z;
			double dist = dx * dx + dz * dz;
			if (dist < bestDist) {
				bestDist = dist;
				best = pad;
			}
		}

		return best;
	}

	private boolean tryDetectMazeRoom() {
		if (mc.world == null || mc.player == null) {
			return false;
		}

		int baseCornerX = toRoomCorner((int) Math.floor(mc.player.getX()));
		int baseCornerZ = toRoomCorner((int) Math.floor(mc.player.getZ()));

		MazeCandidate best = null;

		for (int dx : SEARCH_DELTAS) {
			for (int dz : SEARCH_DELTAS) {
				int cornerX = baseCornerX + dx;
				int cornerZ = baseCornerZ + dz;

				for (int rotation = 0; rotation < 4; rotation++) {
					int score = scoreCandidate(cornerX, cornerZ, rotation);
					if (score < DETECTION_MIN_SCORE) {
						continue;
					}

					double distanceSq = playerDistanceSqToRoomCenter(cornerX, cornerZ);
					MazeCandidate candidate = new MazeCandidate(cornerX, cornerZ, rotation, score, distanceSq);

					if (best == null
						|| candidate.score > best.score
						|| (candidate.score == best.score && candidate.distanceSq < best.distanceSq)) {
						best = candidate;
					}
				}
			}
		}

		if (best == null) {
			return false;
		}

		applyCandidate(best);
		detectCooldownTicks = 0;
		return true;
	}

	private int scoreCandidate(int cornerX, int cornerZ, int rotation) {
		int sourceScore = scoreCandidate(cornerX, cornerZ, rotation, false);
		int swappedScore = scoreCandidate(cornerX, cornerZ, rotation, true);
		return Math.max(sourceScore, swappedScore);
	}

	private int scoreCandidate(int cornerX, int cornerZ, int rotation, boolean swapCoordinates) {
		if (mc.world == null) {
			return 0;
		}

		int score = 0;
		for (CompPad comp : endFramePositions) {
			int compX = swapCoordinates ? comp.tx : comp.cx;
			int compZ = swapCoordinates ? comp.tz : comp.cz;
			BlockPos pos = fromComp(cornerX, cornerZ, compX, compZ, PAD_Y, rotation);
			Block block = mc.world.getBlockState(pos).getBlock();
			if (block == Blocks.END_PORTAL_FRAME) {
				score++;
			}
		}

		return score;
	}

	private void applyCandidate(MazeCandidate candidate) {
		boolean changed = !inMaze
			|| roomCornerX != candidate.cornerX
			|| roomCornerZ != candidate.cornerZ
			|| roomRotation != candidate.rotation;

		inMaze = true;
		roomCornerX = candidate.cornerX;
		roomCornerZ = candidate.cornerZ;
		roomRotation = candidate.rotation;

		if (changed) {
			enteredAtTick = currentTick();
			buildPads();
		} else if (pads.isEmpty()) {
			buildPads();
		}
	}

	private void buildPads() {
		pads.clear();
		boolean swapCoordinates = shouldSwapPadCoordinates();

		for (CompPad comp : endFramePositions) {
			int padCompX = swapCoordinates ? comp.tx : comp.cx;
			int padCompZ = swapCoordinates ? comp.tz : comp.cz;
			int targetCompX = swapCoordinates ? comp.cx : comp.tx;
			int targetCompZ = swapCoordinates ? comp.cz : comp.tz;

			BlockPos padPos = fromComp(roomCornerX, roomCornerZ, padCompX, padCompZ, PAD_Y, roomRotation);
			BlockPos targetPos = fromComp(roomCornerX, roomCornerZ, targetCompX, targetCompZ, PAD_Y, roomRotation);
			pads.add(new Pad(
				padPos.getX(),
				padPos.getZ(),
				targetPos.getX(),
				targetPos.getZ(),
				comp.special,
				comp.isEnd
			));
		}
	}

	private boolean shouldSwapPadCoordinates() {
		return scorePadFrameMatches(true) > scorePadFrameMatches(false);
	}

	private int scorePadFrameMatches(boolean swapCoordinates) {
		if (mc.world == null) {
			return 0;
		}

		int matches = 0;
		for (CompPad comp : endFramePositions) {
			int compX = swapCoordinates ? comp.tx : comp.cx;
			int compZ = swapCoordinates ? comp.tz : comp.cz;
			BlockPos pos = fromComp(roomCornerX, roomCornerZ, compX, compZ, PAD_Y, roomRotation);
			if (mc.world.getBlockState(pos).getBlock() == Blocks.END_PORTAL_FRAME) {
				matches++;
			}
		}

		return matches;
	}

	private void clearPadState() {
		for (Pad pad : pads) {
			pad.visited = false;
			pad.correct = false;
			pad.possible = false;
			pad.incorrect = false;
		}
	}

	private boolean isPlayerNearRoom(int cornerX, int cornerZ, double maxDistanceSq) {
		if (mc.player == null || cornerX == Integer.MIN_VALUE || cornerZ == Integer.MIN_VALUE) {
			return false;
		}

		return playerDistanceSqToRoomCenter(cornerX, cornerZ) <= maxDistanceSq;
	}

	private boolean isTeleportPadPosition(double x, double y, double z) {
		return alignedHalf(x) && alignedHalf(z) && eq(y, PAD_Y + 0.5);
	}

	private boolean alignedHalf(double value) {
		double doubled = value * 2.0;
		return eq(doubled, Math.rint(doubled));
	}

	private boolean eq(double a, double b) {
		return Math.abs(a - b) < 1e-3;
	}

	private int toRoomCorner(int coord) {
		return Math.floorDiv(coord - ROOM_WORLD_START, ROOM_SIZE) * ROOM_SIZE + ROOM_WORLD_START;
	}

	private double playerDistanceSqToRoomCenter(int cornerX, int cornerZ) {
		if (mc.player == null) {
			return Double.MAX_VALUE;
		}

		double centerX = cornerX + 15.5;
		double centerZ = cornerZ + 15.5;
		return mc.player.squaredDistanceTo(centerX, mc.player.getY(), centerZ);
	}

	private long currentTick() {
		return mc.world == null ? 0L : mc.world.getTime();
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

	private int[] toComp(int cornerX, int cornerZ, int worldX, int worldZ, int rotation) {
		int relX = worldX - cornerX;
		int relZ = worldZ - cornerZ;

		int compX;
		int compZ;

		switch (rotation & 3) {
			case 1 -> {
				compX = relZ;
				compZ = 31 - relX;
			}
			case 2 -> {
				compX = 31 - relX;
				compZ = 31 - relZ;
			}
			case 3 -> {
				compX = 31 - relZ;
				compZ = relX;
			}
			default -> {
				compX = relX;
				compZ = relZ;
			}
		}

		if (compX < 0 || compX > 31 || compZ < 0 || compZ > 31) {
			return null;
		}

		return new int[] {compX, compZ};
	}

	private void reset() {
		inMaze = false;
		enteredAtTick = -1L;
		pads.clear();
		roomCornerX = Integer.MIN_VALUE;
		roomCornerZ = Integer.MIN_VALUE;
		roomRotation = 0;
		detectCooldownTicks = 0;
	}

	private List<CompPad> createEndFramePositions() {
		return List.of(
			new CompPad(4, 6, 5, 7),
			new CompPad(4, 12, 5, 11),
			new CompPad(4, 14, 5, 15),
			new CompPad(4, 20, 5, 19),
			new CompPad(4, 22, 5, 23),
			new CompPad(4, 28, 5, 27),
			new CompPad(10, 6, 9, 7),
			new CompPad(10, 12, 9, 11),
			new CompPad(10, 14, 9, 15),
			new CompPad(10, 20, 9, 19),
			new CompPad(10, 22, 9, 23),
			new CompPad(10, 28, 9, 27),
			new CompPad(12, 22, 13, 23),
			new CompPad(12, 28, 13, 27),
			new CompPad(18, 22, 17, 23),
			new CompPad(18, 28, 17, 27),
			new CompPad(20, 6, 21, 7),
			new CompPad(20, 12, 21, 11),
			new CompPad(20, 14, 21, 15),
			new CompPad(20, 20, 21, 19),
			new CompPad(20, 22, 21, 23),
			new CompPad(20, 28, 21, 27),
			new CompPad(26, 6, 25, 7),
			new CompPad(26, 12, 25, 11),
			new CompPad(26, 14, 25, 15),
			new CompPad(26, 20, 25, 19),
			new CompPad(26, 22, 25, 23),
			new CompPad(26, 28, 25, 27),
			new CompPad(15, 12, 14, 11, true),
			new CompPad(15, 14, 16, 15, true, true)
		);
	}
}
