package com.muoniumplayer.mixin;

import com.mojang.renderpearl.backend.opengl.GlSurface;
import com.muoniumplayer.compat.mc.WorldLyricsHudDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「光影直绘」（capture 模式）的合成时机：本帧画面已进默认帧缓冲、尚未 swap。
 *
 * <p>26.3 的 GUI 是延迟批处理 —— {@code GuiGraphicsExtractor} 只往 render state 追加元素，
 * 真正提交在帧末的 GUI 渲染器里。所以 HUD 元素回调、{@code Screen#extractRenderState}
 * 都处在收集阶段，那时 GUI 一个像素还没画，在那里直画必然被后续 pass 盖住。
 * {@code GlSurface#present()} 入口是唯一的「最终画面已成形、交换未发生」位置。</p>
 *
 * <p>挂在 {@code GlSurface} 而不是后端无关的 {@code GpuSurface}：直绘走 GL 互操作，
 * Vulkan 后端下钩子干脆不触发，而不是往不存在的 GL 上下文里写。</p>
 */
@Mixin(GlSurface.class)
public class GlSurfaceMixin {

    @Inject(method = "present()V", at = @At("HEAD"))
    private void cloudxmusic$compositeWorldLyrics(CallbackInfo ci) {
        WorldLyricsHudDraw.presentComposite();
    }
}
