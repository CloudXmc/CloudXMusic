package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.*;
import java.util.*;

/** 一个搜索界面自己的元数据会话；和播放队列分别缓存、取消，关闭界面即释放。 */
public final class QQSearchMetadata implements AutoCloseable {
    private static final Set<QQSearchMetadata> ACTIVE=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final QQQueueMetadata metadata;
    private List<SongView> previous=List.of();
    QQSearchMetadata(QQQueueMetadata metadata){this.metadata=metadata;ACTIVE.add(this);}
    public boolean update(QQSearchController.View view,boolean enabled){
        // 可见行优先，先补当前屏幕；仍异步补全其余结果，最多两个并发请求。
        var ordered=new ArrayList<UnifiedSong>();
        int end=Math.min(view.songs().size(),view.scroll()+8);
        ordered.addAll(view.songs().subList(Math.min(view.scroll(),end),end));
        ordered.addAll(view.songs());
        metadata.update(ordered,enabled);
        var next=view.songs().stream().map(metadata::song).toList();
        boolean changed=!next.equals(previous);previous=next;return changed;
    }
    public SongView song(UnifiedSong song){return metadata.song(song);}
    static void closeAll(){ACTIVE.forEach(QQSearchMetadata::close);}
    @Override public void close(){ACTIVE.remove(this);metadata.close();previous=List.of();}
}
