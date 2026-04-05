package com.revampes.Fault.modules.impl.dungeon.PuzzleSolver;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.ReceivePacketEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

public class IceFillSolver extends Module {
	private static final int ROOM_SIZE = 32;
	private static final int ROOM_WORLD_START = -200;
	private static final int[] SEARCH_DELTAS = new int[] {-ROOM_SIZE * 2, -ROOM_SIZE, 0, ROOM_SIZE, ROOM_SIZE * 2};
	private static final double ROOM_RETAIN_DISTANCE_SQ = 28.0 * 28.0;
	private static final double ROOM_DETECT_DISTANCE_SQ = 30.0 * 30.0;

	private final ColorSetting lineColor = new ColorSetting("Line Color", Color.GREEN);
	private final ColorSetting startColor = new ColorSetting("Start Color", Color.BLUE);
	private final ColorSetting endColor = new ColorSetting("End Color", Color.RED);
	private final SliderSetting lineWidth = new SliderSetting("Line Width", 3.0, 1.0, 10.0, 1.0);
	private final ButtonSetting fastSolution = new ButtonSetting("Fast Solution", false);

	private final List<IcePlatform> platforms = List.of(
		new IcePlatform(
			new Coord(15, 69, 10),
			new Coord(15, 69, 7),
			new Coord(14, 69, 7),
			new Coord(16, 69, 9)
		),
		new IcePlatform(
			new Coord(15, 70, 17),
			new Coord(15, 70, 12),
			new Coord(13, 70, 12),
			new Coord(17, 70, 16)
		),
		new IcePlatform(
			new Coord(15, 71, 26),
			new Coord(15, 71, 19),
			new Coord(12, 71, 19),
			new Coord(18, 71, 25)
		)
	);

	private boolean inIce = false;
	private int worldIdentity = Integer.MIN_VALUE;
	private int roomCornerX = Integer.MIN_VALUE;
	private int roomCornerZ = Integer.MIN_VALUE;
	private int roomRotation = 0;
	private int detectCooldownTicks = 0;
	private boolean lastStrictStart = true;

	private static final class Coord {
		private final int x;
		private final int y;
		private final int z;

		private Coord(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private static final class RoomCandidate {
		private final int cornerX;
		private final int cornerZ;
		private final int rotation;
		private final int score;
		private final int validTiles;
		private final double distanceSq;

		private RoomCandidate(int cornerX, int cornerZ, int rotation, int score, int validTiles, double distanceSq) {
			this.cornerX = cornerX;
			this.cornerZ = cornerZ;
			this.rotation = rotation;
			this.score = score;
			this.validTiles = validTiles;
			this.distanceSq = distanceSq;
		}
	}

	private static final class BlockUpdateResult {
		private final IcePlatform platform;
		private final boolean checkPlayer;

		private BlockUpdateResult(IcePlatform platform, boolean checkPlayer) {
			this.platform = platform;
			this.checkPlayer = checkPlayer;
		}
	}

	public IceFillSolver() {
		super("IceFillSolver", category.Dungeon);
		this.registerSetting(lineColor);
		this.registerSetting(startColor);
		this.registerSetting(endColor);
		this.registerSetting(lineWidth);
		this.registerSetting(fastSolution);
	}

	@Override
	public String getDesc() {
		return "Highlights Ice Fill path, start, and end tiles.";
	}

	@Override
	public void onDisable() {
		resetState(true);
	}

	@EventHandler
	public void onPreUpdate(PreUpdateEvent event) {
		if (mc.world == null || mc.player == null || !DungeonUtils.isInDungeon()) {
			if (mc.world == null || !DungeonUtils.isInDungeon()) {
				worldIdentity = Integer.MIN_VALUE;
			}
			resetState(false);
			return;
		}

		int currentWorldIdentity = System.identityHashCode(mc.world);
		if (currentWorldIdentity != worldIdentity) {
			worldIdentity = currentWorldIdentity;
			resetState(false);
		}

		boolean strictStart = !fastSolution.isToggled();
		if (strictStart != lastStrictStart) {
			lastStrictStart = strictStart;
			if (inIce) {
				for (IcePlatform platform : platforms) {
					platform.solve(roomCornerX, roomCornerZ, roomRotation, strictStart, false);
				}
			}
		}

		if (inIce) {
			if (!isPlayerNearRoom(roomCornerX, roomCornerZ, ROOM_RETAIN_DISTANCE_SQ)) {
				resetIceRoom();
				detectCooldownTicks = 4;
				return;
			}
			return;
		}

		if (detectCooldownTicks > 0) {
			detectCooldownTicks--;
			return;
		}

		if (!tryDetectIceRoom()) {
			detectCooldownTicks = 4;
		}
	}

	@EventHandler
	public void onReceivePacket(ReceivePacketEvent event) {
		if (event.getPacket() instanceof BlockUpdateS2CPacket packet) {
			BlockPos pos = packet.getPos();
			BlockState state = packet.getState();

			mc.execute(() -> {
				if (!ensureActiveContext()) {
					return;
				}

				if (!inIce && !tryDetectIceRoom()) {
					return;
				}

				BlockUpdateResult update = onBlock(pos, state);
				if (update != null) {
					update.platform.solve(roomCornerX, roomCornerZ, roomRotation, !fastSolution.isToggled(), update.checkPlayer);
				}
			});
			return;
		}

		if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket packet) {
			mc.execute(() -> {
				if (!ensureActiveContext()) {
					return;
				}

				if (!inIce && !tryDetectIceRoom()) {
					return;
				}

				LinkedHashMap<IcePlatform, Boolean> updates = new LinkedHashMap<>();

				packet.visitUpdates((pos, state) -> {
					BlockUpdateResult update = onBlock(pos, state);
					if (update != null) {
						updates.merge(update.platform, update.checkPlayer, (a, b) -> a && b);
					}
				});

				boolean strictStart = !fastSolution.isToggled();
				for (var entry : updates.entrySet()) {
					entry.getKey().solve(roomCornerX, roomCornerZ, roomRotation, strictStart, entry.getValue());
				}
			});
		}
	}

	@EventHandler
	public void onRender3D(Render3DEvent event) {
		if (!inIce || !isPlayerNearRoom(roomCornerX, roomCornerZ, ROOM_RETAIN_DISTANCE_SQ)) {
			return;
		}

		MatrixStack stack = event.getMatrix();
		Color pathColor = lineColor.getColor();
		Color startBoxColor = startColor.getColor();
		Color endBoxColor = endColor.getColor();
		double width = lineWidth.getInput();

		for (IcePlatform platform : platforms) {
			Deque<Coord> solution = platform.solution;
			if (solution == null || solution.isEmpty()) {
				continue;
			}

			Coord start = solution.peekFirst();
			Coord end = solution.peekLast();

			if (start != null) {
				RenderUtils.drawBox(
					stack,
					new Box(start.x, start.y, start.z, start.x + 1.0, start.y + 1.0, start.z + 1.0),
					startBoxColor,
					width
				);
			}

			if (start != null && end != null && (start.x != end.x || start.y != end.y || start.z != end.z)) {
				RenderUtils.drawBox(
					stack,
					new Box(end.x, end.y, end.z, end.x + 1.0, end.y + 1.0, end.z + 1.0),
					endBoxColor,
					width
				);
			}

			Coord previous = null;
			for (Coord pos : solution) {
				if (previous != null) {
					Vec3d from = new Vec3d(previous.x + 0.5, previous.y + 1.1, previous.z + 0.5);
					Vec3d to = new Vec3d(pos.x + 0.5, pos.y + 1.1, pos.z + 0.5);
					RenderUtils.drawLine(stack, from, to, pathColor, width);
				}
				previous = pos;
			}
		}
	}

	private boolean ensureActiveContext() {
		return mc.world != null && mc.player != null && DungeonUtils.isInDungeon();
	}

	private BlockUpdateResult onBlock(BlockPos pos, BlockState state) {
		for (IcePlatform platform : platforms) {
			if (!platform.contains(roomCornerX, roomCornerZ, roomRotation, pos.getX(), pos.getY(), pos.getZ())) {
				continue;
			}

			if (state.getBlock() == Blocks.PACKED_ICE) {
				if (platform.removeBlock(roomCornerX, roomCornerZ, roomRotation, pos.getX(), pos.getZ())) {
					return new BlockUpdateResult(platform, true);
				}
				return null;
			}

			if (state.isAir()) {
				platform.reset(roomCornerX, roomCornerZ, roomRotation);
				return new BlockUpdateResult(platform, false);
			}
		}

		return null;
	}

	private boolean tryDetectIceRoom() {
		if (mc.world == null || mc.player == null) {
			return false;
		}

		int baseCornerX = toRoomCorner((int) Math.floor(mc.player.getX()));
		int baseCornerZ = toRoomCorner((int) Math.floor(mc.player.getZ()));

		RoomCandidate best = null;

		for (int dx : SEARCH_DELTAS) {
			for (int dz : SEARCH_DELTAS) {
				int cornerX = baseCornerX + dx;
				int cornerZ = baseCornerZ + dz;

				for (int rotation = 0; rotation < 4; rotation++) {
					RoomCandidate candidate = evaluateCandidate(cornerX, cornerZ, rotation);
					if (candidate == null) {
						continue;
					}

					if (best == null
						|| candidate.score > best.score
						|| (candidate.score == best.score && candidate.validTiles > best.validTiles)
						|| (candidate.score == best.score && candidate.validTiles == best.validTiles && candidate.distanceSq < best.distanceSq)) {
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

	private RoomCandidate evaluateCandidate(int cornerX, int cornerZ, int rotation) {
		if (mc.world == null || mc.player == null) {
			return null;
		}

		double distanceSq = playerDistanceSqToRoomCenter(cornerX, cornerZ);
		if (distanceSq > ROOM_DETECT_DISTANCE_SQ) {
			return null;
		}

		int validTiles = 0;
		int score = 0;

		for (IcePlatform platform : platforms) {
			int platformValid = 0;

			for (int cx = platform.corner1.x; cx <= platform.corner2.x; cx++) {
				for (int cz = platform.corner1.z; cz <= platform.corner2.z; cz++) {
					BlockPos pos = fromComp(cornerX, cornerZ, cx, cz, platform.end.y, rotation);
					BlockState base = mc.world.getBlockState(pos);
					if (!isIceLikeBlock(base)) {
						continue;
					}

					platformValid++;
					validTiles++;
					score += 4;

					if (mc.world.getBlockState(pos.up()).isAir()) {
						score += 1;
					}
				}
			}

			if (platformValid < platform.minimumDetectTiles()) {
				return null;
			}

			BlockPos endPos = fromComp(cornerX, cornerZ, platform.end.x, platform.end.z, platform.end.y, rotation);
			if (isWalkBlock(mc.world.getBlockState(endPos))) {
				score += 5;
			}
		}

		if (validTiles < 18) {
			return null;
		}
		return new RoomCandidate(cornerX, cornerZ, rotation, score, validTiles, distanceSq);
	}

	private boolean isIceLikeBlock(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.PACKED_ICE
			|| block == Blocks.ICE
			|| block == Blocks.BLUE_ICE
			|| block == Blocks.FROSTED_ICE;
	}

	private boolean isWalkBlock(BlockState state) {
		if (state.isAir()) {
			return true;
		}

		Block block = state.getBlock();
		return block == Blocks.PACKED_ICE
			|| block == Blocks.ICE
			|| block == Blocks.BLUE_ICE
			|| block == Blocks.FROSTED_ICE
			|| block == Blocks.WATER;
	}

	private void applyCandidate(RoomCandidate candidate) {
		boolean changed = !inIce
			|| roomCornerX != candidate.cornerX
			|| roomCornerZ != candidate.cornerZ
			|| roomRotation != candidate.rotation;

		inIce = true;
		roomCornerX = candidate.cornerX;
		roomCornerZ = candidate.cornerZ;
		roomRotation = candidate.rotation;

		if (changed) {
			rescanPlatforms();
		}
	}

	private void rescanPlatforms() {
		boolean strictStart = !fastSolution.isToggled();
		lastStrictStart = strictStart;

		for (IcePlatform platform : platforms) {
			platform.rescan(roomCornerX, roomCornerZ, roomRotation, strictStart);
		}
	}

	private void resetIceRoom() {
		inIce = false;
		roomCornerX = Integer.MIN_VALUE;
		roomCornerZ = Integer.MIN_VALUE;
		roomRotation = 0;

		for (IcePlatform platform : platforms) {
			platform.solution = null;
		}
	}

	private void resetState(boolean clearWorldIdentity) {
		resetIceRoom();
		detectCooldownTicks = 0;
		lastStrictStart = !fastSolution.isToggled();
		if (clearWorldIdentity) {
			worldIdentity = Integer.MIN_VALUE;
		}
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

	private boolean isPlayerNearRoom(int cornerX, int cornerZ, double maxDistanceSq) {
		if (mc.player == null || cornerX == Integer.MIN_VALUE || cornerZ == Integer.MIN_VALUE) {
			return false;
		}

		return playerDistanceSqToRoomCenter(cornerX, cornerZ) <= maxDistanceSq;
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

	private final class IcePlatform {
		private final Coord end;
		private final Coord start;
		private final Coord corner1;
		private final Coord corner2;

		private final int maxId;
		private final CompWorld[] blocks;
		private CompWorld[] mutableBlocks;
		private Deque<Coord> solution;

		private final int[][] directions = new int[][] {
			{-1, 0},
			{1, 0},
			{0, -1},
			{0, 1}
		};

		private IcePlatform(Coord end, Coord start, Coord corner1, Coord corner2) {
			this.end = end;
			this.start = start;
			this.corner1 = corner1;
			this.corner2 = corner2;

			this.maxId = (corner2.x - corner1.x + 1) * (corner2.z - corner1.z + 1);
			if (this.maxId > 63) {
				throw new IllegalArgumentException("long bitset :(");
			}

			this.blocks = new CompWorld[maxId + 1];
			this.mutableBlocks = this.blocks.clone();
		}

		private int minimumDetectTiles() {
			int area = (corner2.x - corner1.x + 1) * (corner2.z - corner1.z + 1);
			return Math.max(3, area / 3);
		}

		private int idAt(int x, int z) {
			if (z == end.z) {
				return maxId;
			}
			return (x - corner1.x) + (corner2.x - corner1.x + 1) * (z - corner1.z);
		}

		private int parityOf(int x, int z) {
			return (x + z) & 1;
		}

		private void rescan(int cornerX, int cornerZ, int rotation, boolean endOnStart) {
			if (mc.world == null) {
				return;
			}

			Arrays.fill(blocks, null);

			blocks[maxId] = new CompWorld(
				end,
				fromComp(cornerX, cornerZ, end.x, end.z, end.y, rotation),
				parityOf(end.x, end.z),
				new int[] {idAt(end.x, end.z - 1)}
			);

			int y = end.y;
			for (int cx = corner1.x; cx <= corner2.x; cx++) {
				for (int cz = corner1.z; cz <= corner2.z; cz++) {
					BlockPos worldPos = fromComp(cornerX, cornerZ, cx, cz, y, rotation);
					BlockState above = mc.world.getBlockState(worldPos.up());
					if (!above.isAir()) {
						continue;
					}

					ArrayList<Integer> neighborList = new ArrayList<>();
					for (int[] dir : directions) {
						int nx = cx + dir[0];
						int nz = cz + dir[1];
						if (contains(nx, nz)) {
							neighborList.add(idAt(nx, nz));
						}
					}

					int[] neighbors = new int[neighborList.size()];
					for (int i = 0; i < neighborList.size(); i++) {
						neighbors[i] = neighborList.get(i);
					}

					blocks[idAt(cx, cz)] = new CompWorld(
						new Coord(cx, y, cz),
						worldPos,
						parityOf(cx, cz),
						neighbors
					);
				}
			}

			reset(cornerX, cornerZ, rotation);
			solve(cornerX, cornerZ, rotation, endOnStart, false);
		}

		private void reset(int cornerX, int cornerZ, int rotation) {
			if (mc.world == null) {
				return;
			}

			mutableBlocks = blocks.clone();

			for (int cx = corner1.x; cx <= corner2.x; cx++) {
				for (int cz = corner1.z; cz <= corner2.z; cz++) {
					BlockPos worldPos = fromComp(cornerX, cornerZ, cx, cz, end.y, rotation);
					if (mc.world.getBlockState(worldPos).getBlock() == Blocks.PACKED_ICE) {
						mutableBlocks[idAt(cx, cz)] = null;
					}
				}
			}

			solution = null;
		}

		private boolean contains(int x, int z) {
			return (x == end.x && z == end.z)
				|| (x >= corner1.x && x <= corner2.x && z >= corner1.z && z <= corner2.z);
		}

		private boolean contains(int cornerX, int cornerZ, int rotation, int x, int y, int z) {
			if (end.y != y) {
				return false;
			}

			int[] comp = toComp(cornerX, cornerZ, x, z, rotation);
			if (comp == null) {
				return false;
			}

			return contains(comp[0], comp[1]);
		}

		private boolean removeBlock(int cornerX, int cornerZ, int rotation, int wx, int wz) {
			int[] comp = toComp(cornerX, cornerZ, wx, wz, rotation);
			if (comp == null) {
				return false;
			}

			int cx = comp[0];
			int cz = comp[1];
			if (!contains(cx, cz)) {
				return false;
			}

			int id = idAt(cx, cz);
			mutableBlocks[id] = null;

			if (solution == null) {
				return true;
			}

			Coord head = solution.peekFirst();
			if (head != null && head.x == wx && head.z == wz) {
				solution.removeFirst();
				return false;
			}

			Coord tail = solution.peekLast();
			if (tail != null && tail.x == wx && tail.z == wz) {
				solution.removeLast();
				return false;
			}

			solution = null;
			return true;
		}

		private void solve(int cornerX, int cornerZ, int rotation, boolean endOnStart, boolean checkPlayer) {
			if (mc.player == null) {
				return;
			}

			int total = 0;
			int odd = 0;

			for (CompWorld data : mutableBlocks) {
				if (data == null) {
					continue;
				}
				total++;
				odd += data.parity;
			}

			int even = total - odd;
			if (total == 0) {
				solution = new LinkedList<>();
				return;
			}

			if (Math.abs(odd - even) >= 2) {
				return;
			}

			CompWorld first = null;

			if (checkPlayer) {
				double dy = mc.player.getY() - (end.y + 1.0);
				if (dy >= 0.0 && dy <= 0.1) {
					int wx = (int) Math.floor(mc.player.getX());
					int wz = (int) Math.floor(mc.player.getZ());
					int[] comp = toComp(cornerX, cornerZ, wx, wz, rotation);
					if (comp != null && contains(comp[0], comp[1])) {
						int id = idAt(comp[0], comp[1]);
						CompWorld playerTile = blocks[id];
						if (playerTile != null) {
							if (mutableBlocks[id] == null) {
								total++;
								odd += playerTile.parity;
								even += playerTile.parity ^ 1;
								if (Math.abs(odd - even) >= 2) {
									return;
								}
							}
							first = playerTile;
						}
					}
				}
			}

			if (first == null) {
				if (endOnStart) {
					first = pickFirstCandidate(mutableBlocks[idAt(start.x, start.z)], odd, even);
					if (first == null) {
						first = pickFirstCandidate(mutableBlocks[maxId], odd, even);
					}
				} else {
					first = pickFirstCandidate(mutableBlocks[maxId], odd, even);
					if (first == null) {
						first = pickFirstCandidate(mutableBlocks[idAt(start.x, start.z)], odd, even);
					}
				}

				if (first == null) {
					for (CompWorld data : mutableBlocks) {
						CompWorld picked = pickFirstCandidate(data, odd, even);
						if (picked != null) {
							first = picked;
							break;
						}
					}
				}
			}

			if (first == null) {
				return;
			}

			int minCost = 5318008;
			LinkedList<Coord> best = null;

			long[] visitedHist = new long[maxId + 2];
			Destination[] route = new Destination[total];
			ArrayDeque<Destination> queue = new ArrayDeque<>();

			queue.add(new Destination(first, -1, 0, 0, 1L << idAt(first.comp.x, first.comp.z)));

			int ops = 0;
			long searchStart = System.currentTimeMillis();

			while (!queue.isEmpty()) {
				if (ops++ > 100_000) {
					if (System.currentTimeMillis() - searchStart > 1_000L) {
						break;
					}
					ops = 0;
				}

				Destination d = queue.removeLast();

				int pathLen = d.size;
				if (d.cost + total - pathLen > minCost) {
					continue;
				}

				route[pathLen++] = d;
				long visited = visitedHist[d.size] | d.mask;
				visitedHist[pathLen] = visited;

				if (pathLen == total) {
					if (d.cost < minCost) {
						minCost = d.cost;
						LinkedList<Coord> found = new LinkedList<>();
						for (int i = 0; i < total; i++) {
							Destination step = route[i];
							if (step != null) {
								found.add(new Coord(step.data.worldX, step.data.comp.y, step.data.worldZ));
							}
						}
						best = found;
					}
					continue;
				}

				for (int nextId : d.data.neighbors) {
					CompWorld next = mutableBlocks[nextId];
					if (next == null) {
						continue;
					}

					long mask = 1L << nextId;
					if ((visited & mask) != 0L) {
						continue;
					}

					int dx = next.comp.x - d.data.comp.x;
					int dz = next.comp.z - d.data.comp.z;
					int dir = dx + dz * 100;

					queue.add(new Destination(
						next,
						dir,
						pathLen,
						d.cost + (dir == d.dir ? 0 : 1),
						mask
					));
				}
			}

			solution = best;
		}

		private CompWorld pickFirstCandidate(CompWorld data, int odd, int even) {
			if (data == null) {
				return null;
			}

			if (even == odd) {
				return data;
			}

			return ((odd > even) == (data.parity == 1)) ? data : null;
		}

		private final class Destination {
			private final CompWorld data;
			private final int dir;
			private final int size;
			private final int cost;
			private final long mask;

			private Destination(CompWorld data, int dir, int size, int cost, long mask) {
				this.data = data;
				this.dir = dir;
				this.size = size;
				this.cost = cost;
				this.mask = mask;
			}
		}

		private final class CompWorld {
			private final Coord comp;
			private final int worldX;
			private final int worldZ;
			private final int parity;
			private final int[] neighbors;

			private CompWorld(Coord comp, BlockPos worldPos, int parity, int[] neighbors) {
				this.comp = comp;
				this.worldX = worldPos.getX();
				this.worldZ = worldPos.getZ();
				this.parity = parity;
				this.neighbors = neighbors;
			}
		}
	}
}
