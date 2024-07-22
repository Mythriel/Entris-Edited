package com.matthewperiut.entris.game;

import com.matthewperiut.entris.network.ClientNetworkHelper;
import com.matthewperiut.entris.network.payload.FinishEntrisPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import static com.matthewperiut.entris.Entris.MOD_ID;
import static org.lwjgl.glfw.GLFW.*;

public class TetrisGame {
    public enum Tile {
        NONE,
        BLUE,
        GREEN,
        LIGHT_BLUE,
        ORANGE,
        PURPLE,
        RED,
        YELLOW
    }

    public static Identifier[] TILE_ID = {
            Identifier.of(MOD_ID, "container/enchanting_table/tile_blue"),
            Identifier.of(MOD_ID, "container/enchanting_table/tile_green"),
            Identifier.of(MOD_ID, "container/enchanting_table/tile_light_blue"),
            Identifier.of(MOD_ID, "container/enchanting_table/tile_orange"),
            Identifier.of(MOD_ID, "container/enchanting_table/tile_purple"),
            Identifier.of(MOD_ID, "container/enchanting_table/tile_red"),
            Identifier.of(MOD_ID, "container/enchanting_table/tile_yellow")
    };

    public Tile[][] screen = new Tile[10][24];

    private Tetromino currentTetromino;
    public Tetromino holdTetromino;
    public Tetromino holdTetrominoDisplay;
    private boolean holdUsed;
    private int currentX, currentY;
    public boolean gameOver;
    private boolean isDropping;
    private int dropTimer;
    private Queue<Tetromino.Shape> bag = new LinkedList<>();
    public int score = 0;
    private int timeLeft;
    public boolean isStarted;
    private int countdown;
    private Random random = new Random();

    public TetrisGame() {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 24; j++) {
                screen[i][j] = Tile.NONE;
            }
        }
        fillBag();
        score = 0;
        timeLeft = 0;
        isStarted = false;
        countdown = 0;
        spawnNewTetromino();
    }

    private int calculateDropPosition(Tetromino tetromino, int startX, int startY) {
        int landingY = startY;
        while (isValidPosition(tetromino, startX, landingY - 1)) {
            landingY--;
        }
        return landingY;
    }

    public void render(DrawContext context, int x, int y) {
        if (!isStarted)
            return;

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 20; j++) {
                int id = screen[i][j].ordinal();
                if (id > 0) {
                    context.drawGuiTexture(TILE_ID[id - 1], x + (i * 8), y - (j * 8), 8, 8);
                }
            }
        }

        if (gameOver)
            return;

        // Render the current tetromino normally
        renderTetromino(context, currentTetromino, currentX, currentY, x, y);

        // Calculate and render the hard drop position as transparent
        int landingY = calculateDropPosition(currentTetromino, currentX, currentY);
        renderTransparentTetromino(context, currentTetromino, currentX, landingY, x, y);
    }

    int buttonCooldown = 0;
    boolean flipper = false;
    boolean leftRelease = true;
    boolean rightRelease = true;

    public void continuousInput(long handle) {
        if (gameOver || !isStarted)
            return;

        flipper = !flipper;

        if (buttonCooldown > 0) {
            buttonCooldown--;
        } else {
            if (flipper) {
                if (glfwGetKey(handle, GLFW.GLFW_KEY_LEFT) == GLFW_PRESS) {
                    leftRelease = false;
                    if (moveTetromino(-1, 0))
                        SoundHelper.moveSound();
                }
                if (glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT) == GLFW_PRESS) {
                    rightRelease = false;
                    if (moveTetromino(1, 0))
                        SoundHelper.moveSound();
                }
            }
        }

        if (glfwGetKey(handle, GLFW.GLFW_KEY_LEFT) == GLFW_RELEASE) {
            leftRelease = true;
        }
        if (glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT) == GLFW_RELEASE) {
            rightRelease = true;
        }

        if (glfwGetKey(handle, GLFW.GLFW_KEY_DOWN) == GLFW_PRESS) {
            isDropping = true;
        } else {
            isDropping = false;
        }
    }

    public boolean input(int keyCode, int scanCode, int modifiers) {
        if (gameOver || !isStarted)
            return false;

        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT:
            case GLFW_GAMEPAD_BUTTON_DPAD_LEFT:
                if (leftRelease) {
                    if (moveTetromino(-1, 0)) {
                        SoundHelper.moveSound();
                    }
                    buttonCooldown = 2;
                }
                break;
            case GLFW.GLFW_KEY_RIGHT:
            case GLFW_GAMEPAD_BUTTON_DPAD_RIGHT:
                if (rightRelease) {
                    if (moveTetromino(1, 0)) {
                        SoundHelper.moveSound();
                    }
                    buttonCooldown = 2;
                }
                break;
            case GLFW.GLFW_KEY_DOWN:
            case GLFW_GAMEPAD_BUTTON_DPAD_DOWN:
                break;
            case GLFW.GLFW_KEY_SPACE:
            case GLFW.GLFW_KEY_UP:
            case GLFW_GAMEPAD_BUTTON_DPAD_UP:
                hardDrop();
                break;
            case GLFW.GLFW_KEY_Z:
            case GLFW_GAMEPAD_BUTTON_A:
                rotateTetromino(1);
                break;
            case GLFW.GLFW_KEY_X:
            case GLFW_GAMEPAD_BUTTON_B:
                rotateTetromino(-1);
                break;
            case GLFW.GLFW_KEY_C:
            case GLFW_GAMEPAD_BUTTON_LEFT_BUMPER:
            case GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER:
                holdTetromino();
                break;
            default:
                return false;
    }

    boolean sentServerScore = false;
    public void tick() {
        if (!isStarted) return;

        if (countdown > 0) {
            countdown--;
            return;
        }

        if (gameOver && !sentServerScore) {
            ClientNetworkHelper.send(new FinishEntrisPayload(score));
            sentServerScore = true;
        }

        if (gameOver || timeLeft <= 0) {
            gameOver = true;
            return;
        }

        timeLeft--;

        dropTimer++;
        if (dropTimer >= (isDropping ? 1 : 20)) {
            if (!moveTetromino(0, -1)) {
                placeTetromino(false);
                clearLines();
                spawnNewTetromino();
                holdUsed = false;
            } else {
                SoundHelper.tickSound();
            }
            dropTimer = 0;
        }
    }

    private void fillBag() {
        ArrayList<Tetromino.Shape> shapes = new ArrayList<>();
        for (Tetromino.Shape shape : Tetromino.Shape.values()) {
            shapes.add(shape);
        }
        Collections.shuffle(shapes, random);
        bag.addAll(shapes);
    }

    private void spawnNewTetromino() {
        if (bag.isEmpty()) {
            fillBag();
        }
        currentTetromino = new Tetromino(bag.poll());
        currentX = 4;
        currentY = 18;

        if (!isValidPosition(currentTetromino, currentX, currentY)) {
            gameOver = true;
        }
    }

    private boolean moveTetromino(int dx, int dy) {
        if (isValidPosition(currentTetromino, currentX + dx, currentY + dy)) {
            currentX += dx;
            currentY += dy;
            return true;
        }
        return false;
    }

    public void rotateTetromino(int direction) {
        Tetromino rotatedTetromino = currentTetromino.rotate(direction);
        int[][] kickOffsets = Tetromino.getKickOffsets(currentTetromino.getShape(), currentTetromino.getRotationState(), direction);

        for (int[] offset : kickOffsets) {
            int newX = currentX + offset[0];
            int newY = currentY + offset[1];
            if (rotatedTetromino.isValidPosition(screen, newX, newY)) {
                currentTetromino = rotatedTetromino;
                currentX = newX;
                currentY = newY;
                SoundHelper.rotateSound();
                return;
            }
        }
    }

    private void hardDrop() {
        while (moveTetromino(0, -1)) ;
        placeTetromino(true);
        SoundHelper.hardDropSound();
        clearLines();
        spawnNewTetromino();
        holdUsed = false;
    }

    private void holdTetromino() {
        if (holdUsed) return;

        if (holdTetromino == null) {
            holdTetromino = currentTetromino;
            spawnNewTetromino();
        } else {
            Tetromino temp = currentTetromino;
            currentTetromino = holdTetromino;
            holdTetromino = temp;
            currentX = 4;
            currentY = 18;
        }

        holdTetrominoDisplay = new Tetromino(holdTetromino.getShape());

        SoundHelper.swapSound();
        holdUsed = true;
    }

    private void placeTetromino(boolean soundHandled) {
        for (int y = 0; y < currentTetromino.getTiles().length; y++) {
            for (int x = 0; x < currentTetromino.getTiles()[y].length; x++) {
                if (currentTetromino.getTileAt(x, y) != Tile.NONE) {
                    int worldX = currentX + x;
                    int worldY = currentY + y;
                    if (worldY < 24 && worldY >= 0 && worldX < 10 && worldX >= 0) {
                        screen[worldX][worldY] = currentTetromino.getTileAt(x, y);
                    }
                }
            }
        }
        if (!soundHandled) {
            SoundHelper.finalizeSound();
        }
    }

    private void clearLines() {
        int linesCleared = 0;
        for (int j = 0; j < 24; j++) {
            boolean fullLine = true;
            for (int i = 0; i < 10; i++) {
                if (screen[i][j] == Tile.NONE) {
                    fullLine = false;
                    break;
                }
            }
            if (fullLine) {
                linesCleared++;
                for (int y = j; y < 23; y++) {
                    for (int x = 0; x < 10; x++) {
                        screen[x][y] = screen[x][y + 1];
                    }
                }
                for (int x = 0; x < 10; x++) {
                    screen[x][23] = Tile.NONE;
                }
                j--;
            }
        }
        updateScore(linesCleared);
    }

    private void updateScore(int linesCleared) {
        switch (linesCleared) {
            case 1:
                score += 100;
                break;
            case 2:
                score += 300;
                break;
            case 3:
                score += 500;
                break;
            case 4:
                score += 800;
                break;
        }
    }

    private boolean isValidPosition(Tetromino tetromino, int x, int y) {
        return tetromino.isValidPosition(screen, x, y);
    }

    public void renderTetromino(DrawContext context, Tetromino tetromino, int x, int y, int offsetX, int offsetY) {
        for (int j = 0; j < tetromino.getTiles().length; j++) {
            for (int i = 0; i < tetromino.getTiles()[j].length; i++) {
                if (tetromino.getTileAt(i, j) != Tile.NONE) {
                    int tileX = x + i;
                    int tileY = y + j;
                    if (tileY >= 0 && tileY < 20 && tileX >= 0 && tileX < 10) {
                        int id = tetromino.getTileAt(i, j).ordinal();
                        context.drawGuiTexture(TILE_ID[id - 1], offsetX + (tileX * 8), offsetY - (tileY * 8), 8, 8);
                    }
                }
            }
        }
    }

    private void renderTransparentTetromino(DrawContext context, Tetromino tetromino, int x, int y, int offsetX, int offsetY) {
        for (int j = 0; j < tetromino.getTiles().length; j++) {
            for (int i = 0; i < tetromino.getTiles()[j].length; i++) {
                if (tetromino.getTileAt(i, j) != Tile.NONE) {
                    int tileX = x + i;
                    int tileY = y + j;
                    if (tileY >= 0 && tileY < 20 && tileX >= 0 && tileX < 10) {
                        int id = tetromino.getTileAt(i, j).ordinal();
                        DrawContextUtility.drawTransparentGuiTexture(context, TILE_ID[id - 1], offsetX + (tileX * 8), offsetY - (tileY * 8), 8, 8, 0.3f);
                    }
                }
            }
        }
    }

    public void startGame(int seconds) {
        isStarted = true;
        timeLeft = seconds * 20; // Convert minutes to ticks (20 ticks per second)
        countdown = 0;
        score = 0;
        gameOver = false;
        spawnNewTetromino();
    }

    public int getScore() {
        return score;
    }

    public int getMinutes() {
        return (int) Math.floor(timeLeft / (20.f * 60.f)); // Convert ticks to minutes
    }

    public int getSeconds() {
        return (timeLeft / (20)) - (getMinutes() * 60); // Convert ticks to seconds
    }
}
