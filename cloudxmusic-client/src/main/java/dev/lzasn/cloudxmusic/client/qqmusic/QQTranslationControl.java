package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.state.LyricMode;

/** “译”开关只修改 QQ 偏好，主界面与 HUD 从同一份 lyricMode 快照读取。 */
public final class QQTranslationControl {
    public static final Box BUTTON=new Box(1008,520,36,28);
    private QQTranslationControl(){}
    public static LyricMode next(LyricMode mode){return mode==LyricMode.BILINGUAL?LyricMode.ORIGINAL:LyricMode.BILINGUAL;}
    public static String translation(Lyric.Line line,LyricMode mode){return mode==LyricMode.BILINGUAL?line.translated():"";}
}
