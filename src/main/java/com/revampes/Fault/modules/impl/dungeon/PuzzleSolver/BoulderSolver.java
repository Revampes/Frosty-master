package com.revampes.Fault.modules.impl.dungeon.PuzzleSolver;

import com.revampes.Fault.events.impl.PreUpdateEvent;
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
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class BoulderSolver extends Module {
	private static final int ROOM_SIZE = 32;
	private static final int ROOM_WORLD_START = -200;

	private static final int GRID_ORIGIN_X = 24;
	private static final int GRID_ORIGIN_Y = 65;
	private static final int GRID_ORIGIN_Z = 24;
	private static final int CHEST_SCAN_HORIZONTAL = 22;
	private static final int CHEST_SCAN_VERTICAL = 12;
	private static final double ROOM_RETAIN_DISTANCE_SQ = 60.0 * 60.0;

	private static final int CHEST_COMP_X = 15;
	private static final int CHEST_COMP_Z = 29;
	private static final int CHEST_Y = 66;

	private static final int[] SEARCH_DELTAS = new int[] {-ROOM_SIZE * 2, -ROOM_SIZE, 0, ROOM_SIZE, ROOM_SIZE * 2};
	private static final int[] GRID_SAMPLE_OFFSETS = new int[] {-1, 0, 1};
	private static final int MAX_GRID_MISMATCH = 3;

	private static final Color OUTLINE_COLOR = new Color(0, 255, 255, 255);
	private static final Color FILLED_COLOR = new Color(0, 255, 255, 80);

	private final Map<String, List<CompPos>> solutions = createSolutions();

	private boolean inBoulder = false;
	private long enteredAtTick = -1L;
	private CopyOnWriteArrayList<BlockPos> currentSolution = null;

	private int worldIdentity = Integer.MIN_VALUE;
	private int roomCornerX = Integer.MIN_VALUE;
	private int roomCornerZ = Integer.MIN_VALUE;
	private int roomRotation = 0;
	private int detectCooldownTicks = 0;

	private static final class CompPos {
		private final int x;
		private final int z;

		private CompPos(int x, int z) {
			this.x = x;
			this.z = z;
		}
	}

	private static final class RoomMatch {
		private final int cornerX;
		private final int cornerZ;
		private final int rotation;
		private final String grid;
		private final int mismatch;
		private final double playerDistanceSq;

		private RoomMatch(int cornerX, int cornerZ, int rotation, String grid, int mismatch, double playerDistanceSq) {
			this.cornerX = cornerX;
			this.cornerZ = cornerZ;
			this.rotation = rotation;
			this.grid = grid;
			this.mismatch = mismatch;
			this.playerDistanceSq = playerDistanceSq;
		}
	}

	private static final class GridMatch {
		private final String gridKey;
		private final int mismatch;

		private GridMatch(String gridKey, int mismatch) {
			this.gridKey = gridKey;
			this.mismatch = mismatch;
		}
	}

	public BoulderSolver() {
		super("BoulderSolver", category.Dungeon);
	}

	@Override
	public String getDesc() {
		return "Highlights the blocks to click for Boulder puzzle.";
	}

	@Override
	public void onDisable() {
		resetState();
		worldIdentity = Integer.MIN_VALUE;
	}

	@EventHandler
	public void onPreUpdate(PreUpdateEvent event) {
		if (mc.world == null || mc.player == null || !DungeonUtils.isInDungeon()) {
			if (mc.world == null || !DungeonUtils.isInDungeon()) {
				worldIdentity = Integer.MIN_VALUE;
			}
			resetState();
			return;
		}

		int currentWorldIdentity = System.identityHashCode(mc.world);
		if (currentWorldIdentity != worldIdentity) {
			worldIdentity = currentWorldIdentity;
			resetState();
		}

		if (inBoulder) {
			// Keep current solution while player remains near the detected room,
			// even if the live grid changes after clicking signs/buttons.
			if (isPlayerNearRoom(roomCornerX, roomCornerZ, ROOM_RETAIN_DISTANCE_SQ)) {
				return;
			}

			RoomMatch nearbyRoomMatch = findBoulderRoom();
			if (nearbyRoomMatch == null) {
				leaveBoulderRoom();
				detectCooldownTicks = 4;
				return;
			}

			applyMatch(nearbyRoomMatch);
			return;
		}

		if (detectCooldownTicks > 0) {
			detectCooldownTicks--;
			return;
		}

		RoomMatch roomMatch = findBoulderRoom();
		if (roomMatch == null) {
			detectCooldownTicks = 4;
			return;
		}

		applyMatch(roomMatch);
	}

	@EventHandler
	public void onSendPacket(SendPacketEvent event) {
		if (!(event.getPacket() instanceof PlayerInteractBlockC2SPacket packet)) {
			return;
		}

		if (!inBoulder || currentSolution == null) {
			return;
		}

		BlockHitResult hitResult = packet.getBlockHitResult();
		if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
			return;
		}

		BlockPos clickedPos = hitResult.getBlockPos();
		mc.execute(() -> handleBlockInteract(clickedPos));
	}

	@EventHandler
	public void onRender3D(Render3DEvent event) {
		if (currentSolution == null || currentSolution.isEmpty()) {
			return;
		}

		MatrixStack stack = event.getMatrix();
		for (BlockPos pos : currentSolution) {
			Box box = new Box(pos);
			RenderUtils.drawBox(stack, box, OUTLINE_COLOR, 2.0);
			RenderUtils.drawBoxFilled(stack, box, FILLED_COLOR);
		}
	}

	private void handleBlockInteract(BlockPos pos) {
		if (mc.world == null || mc.player == null || !inBoulder || currentSolution == null) {
			return;
		}

		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		Block block = mc.world.getBlockState(pos).getBlock();

		if (block == Blocks.CHEST) {
			handleChestInteract(x, y, z);
			return;
		}

		if (block != Blocks.STONE_BUTTON && block != Blocks.OAK_WALL_SIGN) {
			return;
		}

		for (BlockPos target : currentSolution) {
			int dist = Math.abs(x - target.getX()) + Math.abs(z - target.getZ());
			if (dist == 1) {
				currentSolution.remove(target);
			}
		}
	}

	private void handleChestInteract(int x, int y, int z) {
		if (enteredAtTick < 0L) {
			return;
		}

		int[] comp = toComp(roomCornerX, roomCornerZ, x, z, roomRotation);
		if (comp == null) {
			return;
		}

		if (comp[0] != CHEST_COMP_X || y != CHEST_Y || comp[1] != CHEST_COMP_Z) {
			return;
		}

		double seconds = (currentTick() - enteredAtTick) * 0.05;
		Utils.addChatMessage(String.format("\u00A7bBoulder took\u00A7f: \u00A76%.2fs", seconds));
		enteredAtTick = -1L;
	}

	private RoomMatch matchCurrentRoom() {
		if (!inBoulder || roomCornerX == Integer.MIN_VALUE || roomCornerZ == Integer.MIN_VALUE) {
			return null;
		}

		GridMatch gridMatch = findGridMatch(roomCornerX, roomCornerZ, roomRotation);
		if (gridMatch == null) {
			return null;
		}

		return new RoomMatch(roomCornerX, roomCornerZ, roomRotation, gridMatch.gridKey, gridMatch.mismatch, playerDistanceSqToRoomCenter(roomCornerX, roomCornerZ));
	}

	private RoomMatch findBoulderRoom() {
		if (mc.world == null || mc.player == null) {
			return null;
		}

		int baseCornerX = toRoomCorner((int) Math.floor(mc.player.getX()));
		int baseCornerZ = toRoomCorner((int) Math.floor(mc.player.getZ()));

		RoomMatch best = null;

		for (int dx : SEARCH_DELTAS) {
			for (int dz : SEARCH_DELTAS) {
				int candidateCornerX = baseCornerX + dx;
				int candidateCornerZ = baseCornerZ + dz;

				for (int rotation = 0; rotation < 4; rotation++) {
					GridMatch gridMatch = findGridMatch(candidateCornerX, candidateCornerZ, rotation);
					if (gridMatch == null) {
						continue;
					}

					double distanceSq = playerDistanceSqToRoomCenter(candidateCornerX, candidateCornerZ);
					RoomMatch candidate = new RoomMatch(candidateCornerX, candidateCornerZ, rotation, gridMatch.gridKey, gridMatch.mismatch, distanceSq);

					if (best == null
						|| candidate.mismatch < best.mismatch
						|| (candidate.mismatch == best.mismatch && candidate.playerDistanceSq < best.playerDistanceSq)) {
						best = candidate;
					}
				}
			}
		}

		RoomMatch chestAnchoredMatch = findRoomMatchFromChestAnchors();
		if (chestAnchoredMatch != null && (best == null || chestAnchoredMatch.playerDistanceSq < best.playerDistanceSq)) {
			best = chestAnchoredMatch;
		}

		return best;
	}

	private RoomMatch findRoomMatchFromChestAnchors() {
		if (mc.world == null || mc.player == null) {
			return null;
		}

		RoomMatch best = null;
		for (BlockPos chestPos : collectNearbyChests(CHEST_SCAN_HORIZONTAL, CHEST_SCAN_VERTICAL)) {
			if (chestPos.getY() != CHEST_Y) {
				continue;
			}

			for (int rotation = 0; rotation < 4; rotation++) {
				int cornerX = inferCornerX(chestPos.getX(), CHEST_COMP_X, CHEST_COMP_Z, rotation);
				int cornerZ = inferCornerZ(chestPos.getZ(), CHEST_COMP_X, CHEST_COMP_Z, rotation);

				if (!isAlignedRoomCorner(cornerX) || !isAlignedRoomCorner(cornerZ)) {
					continue;
				}

				GridMatch gridMatch = findGridMatch(cornerX, cornerZ, rotation);
				if (gridMatch == null) {
					continue;
				}

				double distanceSq = playerDistanceSqToRoomCenter(cornerX, cornerZ);
				RoomMatch candidate = new RoomMatch(cornerX, cornerZ, rotation, gridMatch.gridKey, gridMatch.mismatch, distanceSq);

				if (best == null
					|| candidate.mismatch < best.mismatch
					|| (candidate.mismatch == best.mismatch && candidate.playerDistanceSq < best.playerDistanceSq)) {
					best = candidate;
				}
			}
		}

		return best;
	}

	private java.util.List<BlockPos> collectNearbyChests(int horizontalRange, int verticalRange) {
		java.util.List<BlockPos> chests = new ArrayList<>();
		if (mc.world == null || mc.player == null) {
			return chests;
		}

		int px = (int) Math.floor(mc.player.getX());
		int py = (int) Math.floor(mc.player.getY());
		int pz = (int) Math.floor(mc.player.getZ());

		int minY = py - verticalRange;
		int maxY = py + verticalRange;

		for (int x = px - horizontalRange; x <= px + horizontalRange; x++) {
			for (int z = pz - horizontalRange; z <= pz + horizontalRange; z++) {
				for (int y = minY; y <= maxY; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (mc.world.getBlockState(pos).getBlock() == Blocks.CHEST) {
						chests.add(pos);
					}
				}
			}
		}

		return chests;
	}

	private boolean isPlayerNearRoom(int cornerX, int cornerZ, double maxDistanceSq) {
		if (mc.player == null || cornerX == Integer.MIN_VALUE || cornerZ == Integer.MIN_VALUE) {
			return false;
		}

		return playerDistanceSqToRoomCenter(cornerX, cornerZ) <= maxDistanceSq;
	}

	private boolean isAlignedRoomCorner(int coord) {
		return Math.floorMod(coord - ROOM_WORLD_START, ROOM_SIZE) == 0;
	}

	private int inferCornerX(int worldX, int compX, int compZ, int rotation) {
		return switch (rotation & 3) {
			case 1 -> worldX - (31 - compZ);
			case 2 -> worldX - (31 - compX);
			case 3 -> worldX - compZ;
			default -> worldX - compX;
		};
	}

	private int inferCornerZ(int worldZ, int compX, int compZ, int rotation) {
		return switch (rotation & 3) {
			case 1 -> worldZ - compX;
			case 2 -> worldZ - (31 - compZ);
			case 3 -> worldZ - (31 - compX);
			default -> worldZ - compZ;
		};
	}

	private void applyMatch(RoomMatch match) {
		boolean changed = !inBoulder
			|| roomCornerX != match.cornerX
			|| roomCornerZ != match.cornerZ
			|| roomRotation != match.rotation;

		inBoulder = true;
		roomCornerX = match.cornerX;
		roomCornerZ = match.cornerZ;
		roomRotation = match.rotation;

		if (changed) {
			enteredAtTick = currentTick();
			currentSolution = buildWorldSolution(match.grid, roomCornerX, roomCornerZ, roomRotation);
		} else if (currentSolution == null) {
			currentSolution = buildWorldSolution(match.grid, roomCornerX, roomCornerZ, roomRotation);
		}

		detectCooldownTicks = 0;
	}

	private CopyOnWriteArrayList<BlockPos> buildWorldSolution(String grid, int cornerX, int cornerZ, int rotation) {
		List<CompPos> compPositions = solutions.get(grid);
		if (compPositions == null) {
			return null;
		}

		List<BlockPos> worldPositions = new ArrayList<>(compPositions.size());
		for (CompPos compPos : compPositions) {
			worldPositions.add(fromComp(cornerX, cornerZ, compPos.x, compPos.z, GRID_ORIGIN_Y, rotation));
		}

		return new CopyOnWriteArrayList<>(worldPositions);
	}

	private GridMatch findGridMatch(int cornerX, int cornerZ, int rotation) {
		GridMatch best = null;

		for (int offsetX : GRID_SAMPLE_OFFSETS) {
			for (int offsetZ : GRID_SAMPLE_OFFSETS) {
				String layout = getGridLayout(cornerX, cornerZ, rotation, GRID_ORIGIN_X + offsetX, GRID_ORIGIN_Z + offsetZ);
				if (solutions.containsKey(layout)) {
					return new GridMatch(layout, 0);
				}

				for (String known : solutions.keySet()) {
					int cutoff = best == null ? MAX_GRID_MISMATCH : Math.min(MAX_GRID_MISMATCH, best.mismatch);
					int mismatch = hammingDistance(layout, known, cutoff);
					if (mismatch > MAX_GRID_MISMATCH) {
						continue;
					}

					if (best == null || mismatch < best.mismatch) {
						best = new GridMatch(known, mismatch);
					}
				}
			}
		}

		return best;
	}

	private String getGridLayout(int cornerX, int cornerZ, int rotation) {
		return getGridLayout(cornerX, cornerZ, rotation, GRID_ORIGIN_X, GRID_ORIGIN_Z);
	}

	private String getGridLayout(int cornerX, int cornerZ, int rotation, int originX, int originZ) {
		if (mc.world == null) {
			return "";
		}

		StringBuilder layout = new StringBuilder(42);

		for (int z = 0; z < 16; z += 3) {
			for (int x = 0; x < 19; x += 3) {
				BlockPos pos = fromComp(cornerX, cornerZ, originX - x, originZ - z, GRID_ORIGIN_Y, rotation);
				BlockState blockState = mc.world.getBlockState(pos);
				layout.append(blockState.isAir() ? '0' : '1');
			}
		}

		return layout.toString();
	}

	private int hammingDistance(String lhs, String rhs, int cutoff) {
		if (lhs.length() != rhs.length()) {
			return Integer.MAX_VALUE;
		}

		int mismatch = 0;
		for (int i = 0; i < lhs.length(); i++) {
			if (lhs.charAt(i) == rhs.charAt(i)) {
				continue;
			}

			mismatch++;
			if (mismatch > cutoff) {
				return mismatch;
			}
		}

		return mismatch;
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

	private void leaveBoulderRoom() {
		inBoulder = false;
		enteredAtTick = -1L;
		currentSolution = null;
		roomCornerX = Integer.MIN_VALUE;
		roomCornerZ = Integer.MIN_VALUE;
		roomRotation = 0;
	}

	private void resetState() {
		leaveBoulderRoom();
		detectCooldownTicks = 0;
	}

	private Map<String, List<CompPos>> createSolutions() {
		Map<String, List<CompPos>> map = new HashMap<>();

		addSolution(map, "100101001000000010101001111101010101010101", new CompPos(21, 11), new CompPos(22, 21));
		addSolution(map, "010000010111101001010011100000101110000111", new CompPos(13, 12));
		addSolution(map, "000000011111101001010011100000101110000110", new CompPos(13, 12));
		addSolution(map, "100000111101111011101110001110111010000000", new CompPos(21, 14), new CompPos(15, 17), new CompPos(15, 20), new CompPos(13, 21));
		addSolution(map, "110001110111011010001100111111100011000001", new CompPos(15, 14), new CompPos(19, 21));
		addSolution(map, "100100101000100010100010101000101000100010", new CompPos(22, 21));
		addSolution(map, "000000010101110101010011010000010100000000", new CompPos(22, 18));
		addSolution(map, "000000001111100100010010001011111110000000", new CompPos(24, 11), new CompPos(24, 14), new CompPos(24, 17), new CompPos(24, 20), new CompPos(22, 21));

		return Collections.unmodifiableMap(map);
	}

	private void addSolution(Map<String, List<CompPos>> map, String grid, CompPos... positions) {
		List<CompPos> solution = new ArrayList<>(positions.length);
		Collections.addAll(solution, positions);
		map.put(grid, Collections.unmodifiableList(solution));
	}
}
