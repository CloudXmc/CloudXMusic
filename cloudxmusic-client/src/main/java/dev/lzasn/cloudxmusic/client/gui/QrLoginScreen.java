package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.netease.QrLogin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 扫码登录界面：把登录链接画成二维码，手机直接扫。
 *
 * <p>Esc 关闭即取消轮询。登录成功后停 1.2 秒自动关闭。</p>
 */
public final class QrLoginScreen extends Screen implements QrLogin.Listener {
    private static final int QUIET_ZONE = 10;

    private boolean[][] modules;
    private String status = "正在申请二维码…";
    private boolean success;
    private boolean failed;
    private long doneAt;

    public QrLoginScreen() {
        super(Component.literal("扫码登录"));
    }

    @Override
    protected void init() {
        QrLogin.start(this);
    }

    // ------------------------------------------------------------ 回调（都在主线程）

    @Override
    public void onQrCode(boolean[][] modules) {
        this.modules = modules;
    }

    @Override
    public void onStatus(String text) {
        this.status = text;
    }

    @Override
    public void onSuccess(String nickname) {
        this.success = true;
        this.status = "登录成功：" + nickname;
        this.doneAt = System.currentTimeMillis();
    }

    @Override
    public void onFailure(String text) {
        this.failed = true;
        this.status = text;
    }

    @Override
    public void tick() {
        if (success && doneAt > 0 && System.currentTimeMillis() - doneAt > 1200) {
            if (minecraft != null) minecraft.setScreen(null);
        }
    }

    // ------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99101014);

        int moduleCount = modules == null ? 29 : modules.length;
        int maxQr = Math.min(width - 120, height - 160);
        int scale = Math.max(2, Math.min(8, maxQr / Math.max(1, moduleCount)));
        int qrSize = moduleCount * scale;
        int qrX = width / 2 - qrSize / 2;
        int qrY = height / 2 - qrSize / 2 + 6;

        int panelLeft = qrX - QUIET_ZONE - 18;
        int panelRight = qrX + qrSize + QUIET_ZONE + 18;
        int panelTop = qrY - 58;
        int panelBottom = qrY + qrSize + QUIET_ZONE + 34;

        GuiTextures.glass(graphics, panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 12);

        graphics.drawString(font, "扫码登录 · 网易云音乐", panelLeft + 18, panelTop + 14, Theme.TEXT, false);

        // 二维码白底（含静区）
        graphics.fill(qrX - QUIET_ZONE, qrY - QUIET_ZONE, qrX + qrSize + QUIET_ZONE, qrY + qrSize + QUIET_ZONE,
                0xFFFFFFFF);
        if (modules != null) {
            for (int y = 0; y < modules.length; y++) {
                for (int x = 0; x < modules[y].length; x++) {
                    if (!modules[y][x]) continue;
                    int px = qrX + x * scale;
                    int py = qrY + y * scale;
                    graphics.fill(px, py, px + scale, py + scale, 0xFF101014);
                }
            }
        } else {
            String waiting = "正在申请二维码…";
            graphics.drawString(font, waiting, qrX + QUIET_ZONE, qrY + qrSize / 2, Theme.MUTED, false);
        }

        int statusColor = success ? Theme.ACCENT_DEEP : failed ? 0xFFC0392B : Theme.MUTED;
        graphics.drawString(font, status, panelLeft + 18, qrY + qrSize + QUIET_ZONE + 10, statusColor, false);
        String hint = "扫不了？链接已复制到剪贴板（也写在 config/cloudxmusic/login-url.txt）";
        graphics.drawString(font, hint, panelLeft + 18, panelBottom - 30, Theme.FAINT, false);
        graphics.drawString(font, "Esc 关闭", panelLeft + 18, panelBottom - 16, Theme.FAINT, false);
    }

    @Override
    public void onClose() {
        QrLogin.cancel();
        super.onClose();
    }

    @Override
    public void removed() {
        QrLogin.cancel();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
