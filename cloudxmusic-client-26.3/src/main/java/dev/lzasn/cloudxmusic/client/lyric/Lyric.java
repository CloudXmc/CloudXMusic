package dev.lzasn.cloudxmusic.client.lyric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 歌词 + 翻译。
 *
 * <p>原文和翻译是两条独立的 LRC，时间戳一般能对上，所以按毫秒时间合并；对不上的翻译直接丢掉。
 * 一行里可能有多个时间戳（{@code [00:12.00][01:20.00]副歌}），这里会展开成多条。</p>
 */
public final class Lyric {
    /** 一行歌词；{@code translated} 可能为空串。 */
    public record Line(int timeMs, String text, String translated) {
        public boolean hasTranslation() {
            return translated != null && !translated.isBlank();
        }
    }

    private static final Pattern TIME_TAG =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    public static final Lyric EMPTY = new Lyric(new Line[0]);

    private final Line[] lines;

    private Lyric(Line[] lines) {
        this.lines = lines;
    }

    public boolean isEmpty() {
        return lines.length == 0;
    }

    public Line[] lines() {
        return lines;
    }

    /**
     * 解析原文 + 翻译。
     *
     * @param lrc     原文 LRC（可为 null）
     * @param tlyric  翻译 LRC（可为 null）
     */
    public static Lyric parse(String lrc, String tlyric) {
        Map<Integer, String> origin = parseSingle(lrc);
        if (origin.isEmpty()) return EMPTY;
        Map<Integer, String> translated = parseSingle(tlyric);
        List<Line> lines = new ArrayList<>(origin.size());
        origin.forEach((time, text) -> lines.add(new Line(time, text, translated.getOrDefault(time, ""))));
        lines.sort(Comparator.comparingInt(Line::timeMs));
        return new Lyric(lines.toArray(new Line[0]));
    }

    private static Map<Integer, String> parseSingle(String lrc) {
        Map<Integer, String> result = new TreeMap<>();
        if (lrc == null || lrc.isBlank()) return result;
        for (String raw : lrc.split("\r?\n")) {
            Matcher matcher = TIME_TAG.matcher(raw);
            List<Integer> stamps = new ArrayList<>(2);
            int last = 0;
            while (matcher.find()) {
                stamps.add(toMillis(matcher.group(1), matcher.group(2), matcher.group(3)));
                last = matcher.end();
            }
            if (stamps.isEmpty()) continue;
            String text = raw.substring(last).trim();
            if (text.isEmpty()) continue;
            for (int stamp : stamps) result.put(stamp, text);
        }
        return result;
    }

    private static int toMillis(String minutes, String seconds, String fraction) {
        int min = Integer.parseInt(minutes);
        int sec = Integer.parseInt(seconds);
        int ms = 0;
        if (fraction != null) {
            ms = switch (fraction.length()) {
                case 1 -> Integer.parseInt(fraction) * 100;
                case 2 -> Integer.parseInt(fraction) * 10;
                default -> Integer.parseInt(fraction.substring(0, 3));
            };
        }
        return (min * 60 + sec) * 1000 + ms;
    }

    /** 当前时间对应的行下标；还没到第一行时返回 -1。 */
    public int indexAt(int positionMs) {
        int lo = 0;
        int hi = lines.length - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines[mid].timeMs() <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    /** 当前时间对应的那一行；还没到第一行时返回 null。 */
    public Line lineAt(int positionMs) {
        int index = indexAt(positionMs);
        return index < 0 ? null : lines[index];
    }

    /** 下一行（用于提前切行/淡出），没有就返回 null。 */
    public Line lineAfter(int positionMs) {
        for (Line line : lines) {
            if (line.timeMs() > positionMs) return line;
        }
        return null;
    }

    /** 当前行的下一行，用于双行显示的"下一句"。 */
    public Line nextLineOf(Line current) {
        if (current == null || lines.length == 0) return null;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == current) {
                return i + 1 < lines.length ? lines[i + 1] : null;
            }
        }
        return null;
    }
}
