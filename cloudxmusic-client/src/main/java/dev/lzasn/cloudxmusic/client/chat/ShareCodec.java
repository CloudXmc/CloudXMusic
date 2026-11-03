package dev.lzasn.cloudxmusic.client.chat;

import dev.lzasn.cloudxmusic.client.omni.MusicSource;
import dev.lzasn.cloudxmusic.client.omni.SongView;
import dev.lzasn.cloudxmusic.protocol.ShareWire;
import dev.lzasn.cloudxmusic.protocol.SongRef;
import dev.lzasn.cloudxmusic.protocol.MusicSourceType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旧版公开聊天分享的兼容解析，以及专用通道的歌曲元数据转换。
 *
 * <p>正文保持人读，末尾挂一段短的机器标记：{@code #cxm:n:<歌曲ID>#}（n=网易云、q=QQ 音乐）。
 * 不用 {@code {}} / {@code []} 包裹标记，是因为不少服务器的聊天过滤插件对这类括号敏感；
 * 标记也尽量短——群组服常见 100～256 字符的聊天上限，标题已经占掉大半。</p>
 *
 * <p>标记只能被装了本模组的客户端识别：客户端发不出带 {@code ClickEvent} 的组件，
 * 那条消息的样式由服务端决定。</p>
 */
public final class ShareCodec {
    /** 接收端要把这串替换成可点击组件；发送端只当普通文字。 */
    public static final String CLICK = "[点击播放]";
    private static final String PREFIX = "分享了 ";
    private static final String SEP = " · ";

    static final Pattern MARK = Pattern.compile("#cxm:([nq]):([A-Za-z0-9]{1,64})(?::(\\d{1,5}))?(?::(v))?#");
    /** 标题/歌手截断长度：留出标记和「分享了」「[点击播放]」的余量，整条压在 100 字以内。 */
    private static final int MAX_TITLE = 24, MAX_ARTIST = 16;

    /**
     * @param kind   'n' 网易云 / 'q' QQ 音乐
     * @param body   已去掉标记的原文，可直接显示
     * @param title  从 body 还原的歌名（QQ 侧播放时要带回去，否则界面没有名字）
     * @param artist 从 body 还原的歌手，可能为空
     */
    public record Share(char kind, String id, String body, String title, String artist, int seconds, boolean vip) {
        public String source() { return kind == 'q' ? "qq" : "netease"; }
    }

    private ShareCodec() {}

    public static ShareWire.Song wireSong(SongView song) {
        return new ShareWire.Song(new SongRef(song.source() == MusicSource.QQ ? MusicSourceType.QQ : MusicSourceType.NETEASE,
                song.platformId()), song.title().isBlank() ? "未知歌曲" : trim(song.title(), 48), trim(song.artist(), 48),
                Math.max(0, Math.min(86_400, song.durationSeconds())), song.vip());
    }

    /** 旧版格式兼容工具：{@code 分享了 歌名 · 歌手 [点击播放] #cxm:n:id#}。
     * 正文不带「（VIP）」——VIP 只在悬停提示里提一句，聊天正文保持干净。 */
    public static String compose(SongView song) {
        String title = trim(song.title(), MAX_TITLE);
        String artist = trim(song.artist(), MAX_ARTIST);
        String name = artist.isBlank() ? title : title + SEP + artist;
        char kind = song.source() == MusicSource.QQ ? 'q' : 'n';
        // 时长和 VIP 都塞进标记里，正文保持干净：音频长度最多 5 位，vip 只占 ":v"。
        int seconds = Math.max(0, song.durationSeconds());
        String tail = (seconds > 0 ? ":" + seconds : "") + (song.vip() ? ":v" : "");
        return PREFIX + name + " " + CLICK + " #cxm:" + kind + ":" + song.platformId() + tail + "#";
    }

    /** 从聊天原文里找标记；找不到或格式非法都返回 null（照常显示原消息）。 */
    public static Share parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        Matcher matcher = MARK.matcher(raw);
        if (!matcher.find()) return null;
        char kind = matcher.group(1).charAt(0);
        String id = matcher.group(2);
        if (!valid(kind, id)) return null;
        // 标记可能被服务端加的前缀挤到中间，两头都要拼回去，再去掉多余空白。
        String body = (raw.substring(0, matcher.start()) + raw.substring(matcher.end())).trim();
        int seconds = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        boolean vip = matcher.group(4) != null;
        String name = describe(body);
        int split = name.lastIndexOf(SEP);
        String title = split > 0 ? name.substring(0, split) : name;
        String artist = split > 0 ? name.substring(split + SEP.length()) : "";
        return new Share(kind, id, body, title.strip(), artist.strip(), seconds, vip);
    }

    /** 取「分享了」之后、可点击标记之前的那段，就是「歌名 · 歌手」。 */
    private static String describe(String body) {
        String text = body;
        int head = text.indexOf(PREFIX);
        if (head >= 0) text = text.substring(head + PREFIX.length());
        int tail = text.indexOf(CLICK);
        if (tail >= 0) text = text.substring(0, tail);
        return text.strip();
    }

    private static boolean valid(char kind, String id) {
        if (kind == 'n' && id.length() > 20) return false;
        for (int i = 0; i < id.length(); i++) {
            char ch = id.charAt(i);
            if (kind == 'n' ? !Character.isDigit(ch) : !Character.isLetterOrDigit(ch)) return false;
        }
        return true;
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String text = value.strip();
        return text.codePointCount(0, text.length()) <= max ? text
                : text.substring(0, text.offsetByCodePoints(0, max - 1)).stripTrailing() + "…";
    }
}
