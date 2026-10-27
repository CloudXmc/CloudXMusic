package dev.lzasn.cloudxmusic.client.netease;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/** 把登录链接编码成二维码矩阵（不缩放，一个模块一个格子）。 */
public final class QrCodes {
    private QrCodes() {}

    /** @return 行优先的布尔矩阵，{@code true} 表示黑块；编码失败返回 null。 */
    public static boolean[][] encode(String text) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 0);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            // width/height 传 0：zxing 会按二维码真实模块数输出，不会做非整数缩放
            var matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
            boolean[][] modules = new boolean[matrix.getHeight()][matrix.getWidth()];
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    modules[y][x] = matrix.get(x, y);
                }
            }
            return modules;
        } catch (Exception e) {
            return null;
        }
    }
}
