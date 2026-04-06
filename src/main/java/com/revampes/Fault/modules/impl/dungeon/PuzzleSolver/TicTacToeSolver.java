package com.revampes.Fault.modules.impl.dungeon.PuzzleSolver;

import com.revampes.Fault.events.impl.PreUpdateEvent;
import com.revampes.Fault.events.impl.ReceiveMessageEvent;
import com.revampes.Fault.events.impl.Render3DEvent;
import com.revampes.Fault.modules.Module;
import com.revampes.Fault.settings.impl.ButtonSetting;
import com.revampes.Fault.utility.DungeonUtils;
import com.revampes.Fault.utility.RenderUtils;
import com.revampes.Fault.utility.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class TicTacToeSolver extends Module {
    private static final Pattern COMPLETED_REGEX = Pattern.compile("^PUZZLE SOLVED! \\w+ tied Tic Tac Toe! Good job!$");
    private static final Pattern FAILED_REGEX = Pattern.compile("^PUZZLE FAIL! \\w+ lost Tic Tac Toe! Yikes!$");

    private static final int MAP_PIXELS = 128 * 128;

    private static final int ROOM_SIZE = 32;
    private static final int ROOM_WORLD_START = -200;
    private static final int[] SEARCH_DELTAS = new int[] {-ROOM_SIZE * 2, -ROOM_SIZE, 0, ROOM_SIZE, ROOM_SIZE * 2};

    private static final int BOARD_PRIMARY_COMP_X = 8;
    private static final int BOARD_ALT_COMP_X = 7;
    private static final int BOARD_MIN_Y = 70;
    private static final int BOARD_MAX_Y = 72;
    private static final int BOARD_MIN_Z = 15;
    private static final int BOARD_MAX_Z = 17;

    private static final int[][] BOARD_POSITIONS = new int[][] {
        {8, 72, 17}, {8, 72, 16}, {8, 72, 15},
        {8, 71, 17}, {8, 71, 16}, {8, 71, 15},
        {8, 70, 17}, {8, 70, 16}, {8, 70, 15}
    };

    private static final int[] BOARD_ORDER = new int[] {4, 0, 2, 6, 8, 1, 3, 5, 7};
    private static final int[][] WINNING_SIDES = new int[][] {
        {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
        {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
        {0, 4, 8}, {2, 4, 6}
    };

    private static final Color BEST_OUTLINE = new Color(0, 255, 0, 255);
    private static final Color BEST_FILL = new Color(0, 255, 0, 80);
    private static final Color PREDICT_OUTLINE = Color.ORANGE;
    private static final Color PREDICT_FILL = new Color(255, 165, 0, 80);

    private final ButtonSetting sendDoneMessage = new ButtonSetting("TTT Done Message", false);
    private final ButtonSetting predictNextMove = new ButtonSetting("TTT Prediction", false);

    private final String[] currentBoard = new String[9];

    private boolean inTTT = false;
    private boolean hasMoved = false;
    private String lastStatus = null;
    private int currentBestMove = -1;
    private int predictedMove = -1;
    private long enteredAtTick = -1L;
    private boolean hasSentDoneMessage = false;

    private int worldIdentity = Integer.MIN_VALUE;
    private int roomCornerX = Integer.MIN_VALUE;
    private int roomCornerZ = Integer.MIN_VALUE;
    private int roomRotation = 0;
    private int missingBoardTicks = 0;

    private static final class FrameMark {
        private final int x;
        private final int y;
        private final int z;
        private final String status;

        private FrameMark(int x, int y, int z, String status) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.status = status;
        }
    }

    private static final class RoomMatch {
        private final int cornerX;
        private final int cornerZ;
        private final int rotation;
        private final int matchedCells;
        private final double distanceSq;

        private RoomMatch(int cornerX, int cornerZ, int rotation, int matchedCells, double distanceSq) {
            this.cornerX = cornerX;
            this.cornerZ = cornerZ;
            this.rotation = rotation;
            this.matchedCells = matchedCells;
            this.distanceSq = distanceSq;
        }
    }

    public TicTacToeSolver() {
        super("TicTacToeSolver", category.Dungeon);
        this.registerSetting(sendDoneMessage);
        this.registerSetting(predictNextMove);
        Arrays.fill(currentBoard, null);
    }

    @Override
    public String getDesc() {
        return "Highlights the best move for Tic Tac Toe puzzle.";
    }

    @Override
    public void onDisable() {
        resetState(true);
        worldIdentity = Integer.MIN_VALUE;
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
            resetState(true);
        }

        FrameMark[] marks = collectFrameMarks();
        RoomMatch match = inTTT ? matchCurrentRoom(marks) : null;
        if (match == null) {
            match = findBestRoomMatch(marks);
        }

        if (match == null) {
            if (inTTT) {
                missingBoardTicks++;
                if (missingBoardTicks > 20) {
                    leaveTTT();
                }
            }
            return;
        }

        applyRoomMatch(match);
        missingBoardTicks = 0;

        updateBoard(marks);
        if (!hasMoved) {
            return;
        }

        if ("X".equals(lastStatus)) {
            onAIMove(currentBoard);
        }

        if (countFilled(currentBoard) == 8 && sendDoneMessage.isToggled() && !hasSentDoneMessage
            && mc.player != null && mc.player.networkHandler != null) {
            hasSentDoneMessage = true;
            mc.player.networkHandler.sendChatCommand("pc Tic Tac Toe done");
        }

        hasMoved = false;
        lastStatus = null;
    }

    @EventHandler
    public void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null) {
            return;
        }

        String text = Utils.stripColor(event.getMessage().getString());
        if (text == null) {
            return;
        }

        text = text.trim();
        if (text.isEmpty()) {
            return;
        }

        if (FAILED_REGEX.matcher(text).matches()) {
            resetPuzzleProgress();
            return;
        }

        if (!COMPLETED_REGEX.matcher(text).matches()) {
            return;
        }

        if (enteredAtTick >= 0L) {
            double seconds = (currentTick() - enteredAtTick) * 0.05;
            Utils.addChatMessage(String.format("\u00A7bTic Tac Toe took\u00A7f: \u00A76%.2fs", seconds));
        }

        resetPuzzleProgress();
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!inTTT || roomCornerX == Integer.MIN_VALUE || roomCornerZ == Integer.MIN_VALUE) {
            return;
        }

        MatrixStack stack = event.getMatrix();

        if (predictNextMove.isToggled() && predictedMove != -1) {
            renderMoveHighlight(stack, predictedMove, PREDICT_OUTLINE, PREDICT_FILL);
        }

        if (currentBestMove != -1) {
            renderMoveHighlight(stack, currentBestMove, BEST_OUTLINE, BEST_FILL);
        }
    }

    private void renderMoveHighlight(MatrixStack stack, int moveIndex, Color outline, Color fill) {
        BlockPos pos = moveToWorldPos(moveIndex);
        if (pos == null) {
            return;
        }

        Box box = new Box(pos);
        RenderUtils.drawBox(stack, box, outline, 2.0);
        RenderUtils.drawBoxFilled(stack, box, fill);
    }

    private FrameMark[] collectFrameMarks() {
        if (mc.world == null) {
            return new FrameMark[0];
        }

        List<FrameMark> marks = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity frame)) {
                continue;
            }

            FrameMark mark = toFrameMark(frame);
            if (mark != null) {
                marks.add(mark);
            }
        }

        return marks.toArray(new FrameMark[0]);
    }

    private FrameMark toFrameMark(ItemFrameEntity entity) {
        String status = extractFrameStatus(entity);
        if (status == null) {
            return null;
        }

        int x = (int) Math.floor(entity.getX());
        int y = (int) Math.floor(entity.getY());
        int z = (int) Math.floor(entity.getZ());
        return new FrameMark(x, y, z, status);
    }

    private String extractFrameStatus(ItemFrameEntity entity) {
        ItemStack stack = entity.getHeldItemStack();
        if (stack.isEmpty() || !isMapStack(stack)) {
            return null;
        }

        byte[] colors = extractMapColors(stack);
        if (colors == null) {
            return null;
        }

        int idx = firstColorIndex(colors, 114);
        if (idx == -1) {
            return null;
        }

        return idx == 2700 ? "X" : "O";
    }

    private boolean isMapStack(ItemStack stack) {
        return stack.isOf(Items.FILLED_MAP) || stack.get(DataComponentTypes.MAP_ID) != null;
    }

    private byte[] extractMapColors(ItemStack stack) {
        if (mc.world == null) {
            return null;
        }

        MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
        if (mapId == null) {
            return null;
        }

        MapState mapState = FilledMapItem.getMapState(mapId, mc.world);
        if (mapState == null || mapState.colors == null || mapState.colors.length != MAP_PIXELS) {
            return null;
        }

        return mapState.colors;
    }

    private int firstColorIndex(byte[] colors, int target) {
        for (int i = 0; i < colors.length; i++) {
            if ((colors[i] & 0xFF) == target) {
                return i;
            }
        }
        return -1;
    }

    private RoomMatch matchCurrentRoom(FrameMark[] marks) {
        if (!inTTT || roomCornerX == Integer.MIN_VALUE || roomCornerZ == Integer.MIN_VALUE) {
            return null;
        }

        int matched = countMatchedCells(marks, roomCornerX, roomCornerZ, roomRotation);
        if (matched < 1) {
            return null;
        }

        return new RoomMatch(roomCornerX, roomCornerZ, roomRotation, matched, playerDistanceSqToRoomCenter(roomCornerX, roomCornerZ));
    }

    private RoomMatch findBestRoomMatch(FrameMark[] marks) {
        if (marks.length == 0 || mc.player == null) {
            return null;
        }

        int baseCornerX = toRoomCorner((int) Math.floor(mc.player.getX()));
        int baseCornerZ = toRoomCorner((int) Math.floor(mc.player.getZ()));

        RoomMatch best = null;

        for (int dx : SEARCH_DELTAS) {
            for (int dz : SEARCH_DELTAS) {
                int cornerX = baseCornerX + dx;
                int cornerZ = baseCornerZ + dz;

                for (int rotation = 0; rotation < 4; rotation++) {
                    int matched = countMatchedCells(marks, cornerX, cornerZ, rotation);
                    if (matched < 1) {
                        continue;
                    }

                    double distanceSq = playerDistanceSqToRoomCenter(cornerX, cornerZ);
                    RoomMatch candidate = new RoomMatch(cornerX, cornerZ, rotation, matched, distanceSq);

                    if (best == null
                        || candidate.matchedCells > best.matchedCells
                        || (candidate.matchedCells == best.matchedCells && candidate.distanceSq < best.distanceSq)) {
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    private int countMatchedCells(FrameMark[] marks, int cornerX, int cornerZ, int rotation) {
        Set<Integer> matched = new HashSet<>();

        for (FrameMark mark : marks) {
            int[] comp = toComp(cornerX, cornerZ, mark.x, mark.z, rotation);
            if (comp == null) {
                continue;
            }

            int index = boardIndex(comp[0], mark.y, comp[1]);
            if (index >= 0) {
                matched.add(index);
            }
        }

        return matched.size();
    }

    private void applyRoomMatch(RoomMatch match) {
        boolean changed = !inTTT
            || roomCornerX != match.cornerX
            || roomCornerZ != match.cornerZ
            || roomRotation != match.rotation;

        inTTT = true;
        roomCornerX = match.cornerX;
        roomCornerZ = match.cornerZ;
        roomRotation = match.rotation;

        if (changed) {
            enteredAtTick = currentTick();
            hasSentDoneMessage = false;
            Arrays.fill(currentBoard, null);
            hasMoved = false;
            currentBestMove = -1;
            predictedMove = -1;
            lastStatus = null;
        }
    }

    private void updateBoard(FrameMark[] marks) {
        if (!inTTT || roomCornerX == Integer.MIN_VALUE || roomCornerZ == Integer.MIN_VALUE) {
            return;
        }

        String[] nextBoard = new String[9];
        for (FrameMark mark : marks) {
            int[] comp = toComp(roomCornerX, roomCornerZ, mark.x, mark.z, roomRotation);
            if (comp == null) {
                continue;
            }

            int index = boardIndex(comp[0], mark.y, comp[1]);
            if (index == -1) {
                continue;
            }

            nextBoard[index] = mark.status;
        }

        if (Arrays.equals(currentBoard, nextBoard)) {
            return;
        }

        String placed = detectPlacedStatus(currentBoard, nextBoard);
        if (placed == null) {
            placed = inferTurnFromBoard(nextBoard);
        }

        System.arraycopy(nextBoard, 0, currentBoard, 0, currentBoard.length);
        hasMoved = true;
        lastStatus = placed;
        currentBestMove = -1;
        predictedMove = -1;
    }

    private String detectPlacedStatus(String[] before, String[] after) {
        for (int i = 0; i < 9; i++) {
            if (before[i] == null && after[i] != null) {
                return after[i];
            }
        }
        return null;
    }

    private String inferTurnFromBoard(String[] board) {
        int x = 0;
        int o = 0;

        for (String cell : board) {
            if ("X".equals(cell)) {
                x++;
            } else if ("O".equals(cell)) {
                o++;
            }
        }

        if (x > o) {
            return "X";
        }
        if (o > x) {
            return "O";
        }
        return null;
    }

    private BlockPos moveToWorldPos(int moveIndex) {
        if (moveIndex < 0 || moveIndex >= BOARD_POSITIONS.length) {
            return null;
        }

        int[] pos = BOARD_POSITIONS[moveIndex];
        return fromComp(roomCornerX, roomCornerZ, pos[0], pos[2], pos[1], roomRotation);
    }

    private int boardIndex(int compX, int y, int compZ) {
        if (compX != BOARD_PRIMARY_COMP_X && compX != BOARD_ALT_COMP_X) {
            return -1;
        }
        if (y < BOARD_MIN_Y || y > BOARD_MAX_Y) {
            return -1;
        }
        if (compZ < BOARD_MIN_Z || compZ > BOARD_MAX_Z) {
            return -1;
        }

        int row = BOARD_MAX_Y - y;
        int col = BOARD_MAX_Z - compZ;
        return row * 3 + col;
    }

    private int countFilled(String[] board) {
        int count = 0;
        for (String cell : board) {
            if (cell != null) {
                count++;
            }
        }
        return count;
    }

    private void onAIMove(String[] board) {
        currentBestMove = bestMove(board, "O");
        predictedMove = -1;

        if (!predictNextMove.isToggled() || countFilled(board) == 0 || currentBestMove == -1) {
            return;
        }

        String[] nextBoard = board.clone();
        nextBoard[currentBestMove] = "O";

        int predictX = bestMove(nextBoard, "X");
        if (predictX == -1) {
            return;
        }

        String[] postBoard = nextBoard.clone();
        postBoard[predictX] = "X";

        predictedMove = bestMove(postBoard, "O");
    }

    private int bestMove(String[] board, String player) {
        boolean maximizing = "X".equals(player);
        int bestScore = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int bestIndex = -1;

        if (countFilled(board) == 1) {
            if (board[4] == null) {
                return 4;
            }
            return 0;
        }

        for (int index : BOARD_ORDER) {
            if (board[index] != null) {
                continue;
            }

            String[] temp = board.clone();
            temp[index] = player;

            int score = minMax(temp, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, !"X".equals(player));

            if (maximizing) {
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = index;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestIndex = index;
                }
            }
        }

        return bestIndex;
    }

    private int minMax(String[] board, int depth, int alpha, int beta, boolean isPlayer) {
        if (isWinner(board, "X")) {
            return 10 - depth;
        }
        if (isWinner(board, "O")) {
            return depth - 10;
        }
        if (countFilled(board) == 9) {
            return 0;
        }

        int a = alpha;
        int b = beta;

        if (isPlayer) {
            int best = Integer.MIN_VALUE;

            for (int index : BOARD_ORDER) {
                if (board[index] != null) {
                    continue;
                }

                String[] temp = board.clone();
                temp[index] = "X";

                int score = minMax(temp, depth + 1, a, b, false);
                best = Math.max(best, score);
                a = Math.max(a, score);
                if (b <= a) {
                    break;
                }
            }

            return best;
        }

        int best = Integer.MAX_VALUE;

        for (int index : BOARD_ORDER) {
            if (board[index] != null) {
                continue;
            }

            String[] temp = board.clone();
            temp[index] = "O";

            int score = minMax(temp, depth + 1, a, b, true);
            best = Math.min(best, score);
            b = Math.min(b, score);
            if (b <= a) {
                break;
            }
        }

        return best;
    }

    private boolean isWinner(String[] board, String player) {
        for (int[] side : WINNING_SIDES) {
            if (player.equals(board[side[0]]) && player.equals(board[side[1]]) && player.equals(board[side[2]])) {
                return true;
            }
        }
        return false;
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

    private long currentTick() {
        return mc.world == null ? 0L : mc.world.getTime();
    }

    private void resetPuzzleProgress() {
        Arrays.fill(currentBoard, null);
        hasMoved = false;
        lastStatus = null;
        currentBestMove = -1;
        predictedMove = -1;
        enteredAtTick = -1L;
    }

    private void leaveTTT() {
        inTTT = false;
        roomCornerX = Integer.MIN_VALUE;
        roomCornerZ = Integer.MIN_VALUE;
        roomRotation = 0;
        missingBoardTicks = 0;
        resetPuzzleProgress();
    }

    private void resetState(boolean fullReset) {
        leaveTTT();
        if (fullReset) {
            hasSentDoneMessage = false;
        }
    }
}