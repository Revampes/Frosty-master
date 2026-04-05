package com.revampes.Fault.modules.impl.dungeon.PuzzleSolver;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.settings.impl.ColorSetting;
import com.revampes.Fault.settings.impl.SelectSetting;
import com.revampes.Fault.settings.impl.SliderSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlazeSolver extends Module {
	private static final Pattern BLAZE_HP_PATTERN = Pattern.compile("^\\[Lv15\\].*Blaze\\s+[\\d,]+/([\\d,]+).*$");

	private final ButtonSetting sendDoneMessage = new ButtonSetting("Send Done Message", false);
	private final ButtonSetting renderTracer = new ButtonSetting("Render Tracer", true);
	private final SliderSetting tracerLineWidth = new SliderSetting("Tracer Width", 2.0, 1.0, 5.0, 0.1);
	private final SelectSetting tracerAmount = new SelectSetting("Tracer Amount", 0, new String[] {"1", "2", "all"});

	private final ColorSetting firstOutlineColor = new ColorSetting("First Outline", new Color(0, 255, 0, 255));
	private final ColorSetting firstFilledColor = new ColorSetting("First Filled", new Color(0, 255, 0, 80));
	private final ColorSetting secondOutlineColor = new ColorSetting("Second Outline", new Color(255, 165, 0, 255));
	private final ColorSetting secondFilledColor = new ColorSetting("Second Filled", new Color(255, 165, 0, 80));
	private final ColorSetting thirdOutlineColor = new ColorSetting("Third Outline", new Color(255, 0, 0, 255));
	private final ColorSetting thirdFilledColor = new ColorSetting("Third Filled", new Color(255, 0, 0, 80));

	private final ColorSetting efficientOutlineColor = new ColorSetting("Efficient Block Outline", new Color(0, 255, 0, 255));
	private final ColorSetting efficientFilledColor = new ColorSetting("Efficient Block Filled", new Color(0, 255, 0, 80));

	private final Map<Integer, Integer> entityList = new ConcurrentHashMap<>();
	private final List<TrackedBlaze> blazes = new ArrayList<>();

	private boolean hasPlatform = false;
	private boolean inBlaze = false;
	private int lastBlazes = 0;
	private long startedAtMs = 0L;
	private BlockPos efficientPos = null;
	private BlockPos etherToPos = null;
	private boolean hasSent = false;

	private int worldIdentity = Integer.MIN_VALUE;
	private int roomCornerX = Integer.MIN_VALUE;
	private int roomCornerZ = Integer.MIN_VALUE;
	private int roomRotation = 0;

	private static final class TrackedBlaze {
		private final Entity entity;
		private final int maxHp;

		private TrackedBlaze(Entity entity, int maxHp) {
			this.entity = entity;
			this.maxHp = maxHp;
		}
	}

	public BlazeSolver() {
		super("BlazeSolver", category.Dungeon);
		this.registerSetting(sendDoneMessage);
		this.registerSetting(renderTracer);
		this.registerSetting(tracerLineWidth);
		this.registerSetting(tracerAmount);

		this.registerSetting(firstOutlineColor);
		this.registerSetting(firstFilledColor);
		this.registerSetting(secondOutlineColor);
		this.registerSetting(secondFilledColor);
		this.registerSetting(thirdOutlineColor);
		this.registerSetting(thirdFilledColor);

		this.registerSetting(efficientOutlineColor);
		this.registerSetting(efficientFilledColor);
	}

	@Override
	public String getDesc() {
		return "Highlights the correct blaze order in blaze puzzle.";
	}

	@Override
	public void guiUpdate() {
		this.tracerLineWidth.setVisibilityCondition(renderTracer::isToggled);
		this.tracerAmount.setVisibilityCondition(renderTracer::isToggled);
	}

	@Override
	public void onDisable() {
		resetState(true);
	}

	@EventHandler
	public void onPreUpdate(PreUpdateEvent event) {
		if (mc.world == null || mc.player == null || !DungeonUtils.isInDungeon()) {
			if (mc.world == null || !DungeonUtils.isInDungeon()) {
				hasSent = false;
				worldIdentity = Integer.MIN_VALUE;
			}
			resetState(false);
			return;
		}

		int currentWorldIdentity = System.identityHashCode(mc.world);
		if (currentWorldIdentity != worldIdentity) {
			worldIdentity = currentWorldIdentity;
			resetState(true);
		}

		updateEntityHpCache();
		rebuildBlazeList();

		if (blazes.isEmpty()) {
			onBlazesGone();
			return;
		}

		inBlaze = true;

		updateRoomInfo();

		blazes.sort(Comparator.comparingInt(o -> o.maxHp));
		if (!hasPlatform) {
			blazes.sort((a, b) -> Integer.compare(b.maxHp, a.maxHp));
		}

		if (blazes.size() >= 9 && startedAtMs == 0L) {
			startedAtMs = Util.getMeasuringTimeMs();
		}

		lastBlazes = blazes.size();
	}

	@EventHandler
	public void onRender3D(Render3DEvent event) {
		if (!inBlaze || blazes.isEmpty() || mc.player == null) {
			return;
		}

		MatrixStack stack = event.getMatrix();
		renderEfficientBlocks(stack);

		TrackedBlaze blaze1 = getTrackedBlaze(0);
		TrackedBlaze blaze2 = getTrackedBlaze(1);
		TrackedBlaze blaze3 = getTrackedBlaze(2);

		if (blaze1 == null || blaze2 == null) {
			return;
		}

		int tracerCount = getTracerCount();

		highlightBlaze(stack, blaze1.entity, firstOutlineColor.getColor(), firstFilledColor.getColor(), tracerCount >= 1, event.getDelta());
		highlightBlaze(stack, blaze2.entity, secondOutlineColor.getColor(), secondFilledColor.getColor(), tracerCount >= 2, event.getDelta());
		if (blaze3 != null) {
			highlightBlaze(stack, blaze3.entity, thirdOutlineColor.getColor(), thirdFilledColor.getColor(), tracerCount >= 3, event.getDelta());
		}

		Vec3d p1 = new Vec3d(blaze1.entity.getX(), blaze1.entity.getY() + 0.8, blaze1.entity.getZ());
		Vec3d p2 = new Vec3d(blaze2.entity.getX(), blaze2.entity.getY() + 0.8, blaze2.entity.getZ());
		RenderUtils.drawLine(stack, p1, p2, firstOutlineColor.getColor(), 1.8);

		if (blaze3 != null) {
			Vec3d p3 = new Vec3d(blaze3.entity.getX(), blaze3.entity.getY() + 0.8, blaze3.entity.getZ());
			RenderUtils.drawLine(stack, p2, p3, secondOutlineColor.getColor(), 1.8);
		}
	}

	private void updateEntityHpCache() {
		entityList.clear();

		for (Entity entity : mc.world.getEntities()) {
			if (!(entity instanceof ArmorStandEntity stand)) {
				continue;
			}

			if (stand.getCustomName() == null) {
				continue;
			}

			String cleanName = Utils.stripColor(stand.getCustomName().getString());
			Matcher matcher = BLAZE_HP_PATTERN.matcher(cleanName);
			if (!matcher.matches()) {
				continue;
			}

			int maxHp = parseHp(matcher.group(1));
			if (maxHp <= 0) {
				continue;
			}

			entityList.put(stand.getId(), maxHp);
		}
	}

	private void rebuildBlazeList() {
		blazes.clear();

		for (Map.Entry<Integer, Integer> entry : entityList.entrySet()) {
			Entity blaze = resolveBlazeEntity(entry.getKey());
			if (blaze != null) {
				blazes.add(new TrackedBlaze(blaze, entry.getValue()));
			}
		}
	}

	private Entity resolveBlazeEntity(int armorStandId) {
		for (int offset = 1; offset <= 3; offset++) {
			Entity entity = mc.world.getEntityById(armorStandId - offset);
			if (entity instanceof net.minecraft.entity.mob.BlazeEntity && entity.isAlive()) {
				return entity;
			}
		}
		return null;
	}

	private void onBlazesGone() {
		if (startedAtMs != 0L && lastBlazes == 1) {
			double seconds = (Util.getMeasuringTimeMs() - startedAtMs) / 1000.0;
			Utils.addChatMessage(String.format("\u00A7bBlaze took\u00A7f: \u00A76%.2fs", seconds));

			if (!hasSent && sendDoneMessage.isToggled() && mc.player != null && mc.player.networkHandler != null) {
				hasSent = true;
				mc.player.networkHandler.sendChatCommand("pc Blaze done");
			}
		}

		resetState(false);
	}

	private void updateRoomInfo() {
		TrackedBlaze ref = getTrackedBlaze(0);
		if (ref == null) {
			return;
		}

		int cornerX = toRoomCorner((int) Math.floor(ref.entity.getX()));
		int cornerZ = toRoomCorner((int) Math.floor(ref.entity.getZ()));

		roomCornerX = cornerX;
		roomCornerZ = cornerZ;

		Vec3d blazeCenter = getBlazeCenter();
		int bestRotation = roomRotation;
		boolean foundPlatformRotation = false;
		double bestDistance = Double.MAX_VALUE;

		for (int rot = 0; rot < 4; rot++) {
			BlockPos platformPos = fromComp(cornerX, cornerZ, 15, 14, 118, rot);
			boolean platformAtRotation = mc.world.getBlockState(platformPos).getBlock() == Blocks.COBBLESTONE;

			BlockPos anchorPos = fromComp(cornerX, cornerZ, 20, 11, 90, rot);
			double distance = blazeCenter.squaredDistanceTo(anchorPos.getX() + 0.5, blazeCenter.y, anchorPos.getZ() + 0.5);

			if (platformAtRotation) {
				if (!foundPlatformRotation || distance < bestDistance) {
					foundPlatformRotation = true;
					bestDistance = distance;
					bestRotation = rot;
				}
			} else if (!foundPlatformRotation && distance < bestDistance) {
				bestDistance = distance;
				bestRotation = rot;
			}
		}

		roomRotation = bestRotation;

		BlockPos platformPos = fromComp(cornerX, cornerZ, 15, 14, 118, roomRotation);
		hasPlatform = mc.world.getBlockState(platformPos).getBlock() == Blocks.COBBLESTONE;

		efficientPos = fromComp(cornerX, cornerZ, 20, 11, hasPlatform ? 103 : 53, roomRotation);
		etherToPos = hasPlatform ? fromComp(cornerX, cornerZ, 20, 11, 85, roomRotation) : null;
	}

	private Vec3d getBlazeCenter() {
		if (blazes.isEmpty()) {
			return new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		}

		double sx = 0.0;
		double sy = 0.0;
		double sz = 0.0;

		for (TrackedBlaze tracked : blazes) {
			sx += tracked.entity.getX();
			sy += tracked.entity.getY();
			sz += tracked.entity.getZ();
		}

		double size = blazes.size();
		return new Vec3d(sx / size, sy / size, sz / size);
	}

	private int toRoomCorner(int coord) {
		return Math.floorDiv(coord + 200, 32) * 32 - 200;
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

	private void renderEfficientBlocks(MatrixStack stack) {
		if (efficientPos == null) {
			return;
		}

		Box efficientBox = new Box(efficientPos);
		RenderUtils.drawBox(stack, efficientBox, efficientOutlineColor.getColor(), 2.0);
		RenderUtils.drawBoxFilled(stack, efficientBox, efficientFilledColor.getColor());

		if (hasPlatform && etherToPos != null) {
			Box etherBox = new Box(etherToPos);
			RenderUtils.drawBox(stack, etherBox, efficientOutlineColor.getColor(), 2.0);
			RenderUtils.drawBoxFilled(stack, etherBox, efficientFilledColor.getColor());
		}
	}

	private void highlightBlaze(
		MatrixStack stack,
		Entity entity,
		Color outlineColor,
		Color fillColor,
		boolean drawTracer,
		float tickDelta
	) {
		Box box = entity.getBoundingBox();
		RenderUtils.drawBox(stack, box, outlineColor, 2.0);
		RenderUtils.drawBoxFilled(stack, box, fillColor);

		if (drawTracer && renderTracer.isToggled() && mc.player != null) {
			Vec3d start = mc.player.getCameraPosVec(tickDelta).add(mc.player.getRotationVec(tickDelta).multiply(2.0));
			Vec3d end = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ());
			RenderUtils.drawLine(stack, start, end, outlineColor, tracerLineWidth.getInput());
		}
	}

	private TrackedBlaze getTrackedBlaze(int index) {
		if (index < 0 || index >= blazes.size()) {
			return null;
		}
		return blazes.get(index);
	}

	private int getTracerCount() {
		int mode = (int) tracerAmount.getValue();
		return switch (mode) {
			case 1 -> 2;
			case 2 -> 3;
			default -> 1;
		};
	}

	private int parseHp(String hpText) {
		try {
			return Integer.parseInt(hpText.replace(",", ""));
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private void resetState(boolean fullReset) {
		inBlaze = false;
		hasPlatform = false;
		startedAtMs = 0L;
		lastBlazes = 0;
		efficientPos = null;
		etherToPos = null;
		roomCornerX = Integer.MIN_VALUE;
		roomCornerZ = Integer.MIN_VALUE;
		roomRotation = 0;

		blazes.clear();
		entityList.clear();

		if (fullReset) {
			hasSent = false;
		}
	}
}
