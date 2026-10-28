package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.gui.design.IslandPainter;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** 透明调整界面：捕获鼠标，避免左键拖动时触发挖方块或放置方块。 */
public final class IslandPositionScreen extends Screen {
    private boolean dragging;
    private double dragDX, dragDY;

    public IslandPositionScreen() {
        super(Component.literal("调整灵动岛位置"));
    }

    @Override
    protected void init() {
        CloudXMusicClient.STATE.setAdjustingIslandPos(true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 遮罩、灵动岛和提示由 DynamicIslandHud 在同帧绘制。
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 透明调整界面不能触发原版菜单模糊，否则 HUD 也会被整帧模糊并压暗。
    }

    private double[] islandBounds() {
        MusicState state = CloudXMusicClient.STATE;
        double display = Math.min(.58, Math.min(
                (width - 16.0) / IslandPainter.WIDTH,
                (height - 8.0) / IslandPainter.HEIGHT)) * state.islandScale();
        int w = (int) Math.round((IslandPainter.WIDTH + 2 * IslandPainter.PAD) * display);
        int h = (int) Math.round((IslandPainter.HEIGHT + 2 * IslandPainter.PAD) * display);
        return new double[]{state.islandX() * Math.max(0, width - w),
                state.islandY() * Math.max(0, height - h), w, h};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() != 0) return super.mouseClicked(event, handled);
        double[] box = islandBounds();
        if (event.x() < box[0] || event.x() > box[0] + box[2]
                || event.y() < box[1] || event.y() > box[1] + box[3]) {
            minecraft.setScreen(null);
            return true;
        }
        dragging = true;
        dragDX = event.x() - (box[0] + box[2] / 2);
        dragDY = event.y() - (box[1] + box[3] / 2);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging || event.button() != 0) return super.mouseDragged(event, dx, dy);
        double[] box = islandBounds();
        double nx = event.x() - dragDX - box[2] / 2;
        double ny = event.y() - dragDY - box[3] / 2;
        CloudXMusicClient.STATE.islandPosSilent(
                (float) (nx / Math.max(1, width - box[2])),
                (float) (ny / Math.max(1, height - box[3])));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            CloudXMusicClient.STATE.persistAppearance();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        CloudXMusicClient.STATE.persistAppearance();
        CloudXMusicClient.STATE.setAdjustingIslandPos(false);
        dragging = false;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}