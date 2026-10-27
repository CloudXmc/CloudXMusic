package dev.lzasn.cloudxmusic.client.state;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** 播放队列的循环策略。 */
public enum PlaybackMode {
    REPEAT_ONE("单曲循环", "repeatOne"),
    REPEAT_LIST("列表循环", "repeatList"),
    SHUFFLE("随机播放", "shuffle");

    public final String label;
    public final String icon;

    PlaybackMode(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public int nextIndex(List<Track> queue, Track current) {
        return nextIndex(queue, current, ThreadLocalRandom.current());
    }

    int nextIndex(List<Track> queue, Track current, RandomGenerator random) {
        if (queue.isEmpty()) return -1;
        int currentIndex = queue.indexOf(current);
        return switch (this) {
            case REPEAT_ONE -> currentIndex < 0 ? 0 : currentIndex;
            case REPEAT_LIST -> (Math.max(currentIndex, -1) + 1) % queue.size();
            case SHUFFLE -> {
                if (queue.size() < 2) yield 0;
                if (currentIndex < 0) yield random.nextInt(queue.size());
                int next = random.nextInt(queue.size() - 1);
                if (currentIndex >= 0 && next >= currentIndex) next++;
                yield next;
            }
        };
    }
}
