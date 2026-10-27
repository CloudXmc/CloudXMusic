package dev.lzasn.cloudxmusic.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/** 客户端线程专用的画布纹理桥接，每个视图只持有一张 GPU 纹理。 */
public final class UiTexture implements AutoCloseable {
    private static int nextId;
    private final Identifier id = Identifier.fromNamespaceAndPath("cloudxmusic", "canvas/" + nextId++);
    private DynamicTexture texture;
    private int width, height;

    public void update(BufferedImage source) {
        boolean resize = texture == null || width != source.getWidth() || height != source.getHeight();
        if (resize) { close(); width = source.getWidth(); height = source.getHeight(); }
        NativeImage pixels = resize ? new NativeImage(width, height, false) : texture.getPixels();
        if (pixels == null) throw new IllegalStateException("界面纹理已被释放");
        int[] argb = ((DataBufferInt) source.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int color = argb[y * width + x];
            pixels.setPixelABGR(x, y, (color & 0xFF00FF00) | ((color >>> 16) & 255) | ((color & 255) << 16));
        }
        if (resize) {
            try {
                texture = new DynamicTexture(() -> "CloudXmusic UI canvas", pixels);
                Minecraft.getInstance().getTextureManager().register(id, texture);
            } catch (RuntimeException e) {
                if (texture != null) texture.close(); else pixels.close();
                texture = null; throw e;
            }
        } else texture.upload();
    }

    public void draw(GuiGraphics graphics, int x, int y, int w, int h) {
        if (texture == null) return;
        // 已核对 1.21.11 GuiGraphics / BlitRenderState：UV 顺序为 u0,u1,v0,v1。
        graphics.blit(id, x, y, x + w, y + h, 0f, 1f, 0f, 1f);
    }

    @Override public void close() {
        if (texture != null) { Minecraft.getInstance().getTextureManager().release(id); texture = null; }
    }
}
