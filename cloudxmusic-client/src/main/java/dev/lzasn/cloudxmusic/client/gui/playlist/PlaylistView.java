package dev.lzasn.cloudxmusic.client.gui.playlist;

import java.util.List;
import static dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.*;

/** 只包含绘制数据；正在收集整单时，tracks 仍然保留已确认的浏览结果。 */
public record PlaylistView(Section section,Entry selected,List<Entry> playlists,List<Track> tracks,
                           boolean loading,boolean playingAll,boolean hasMore,boolean error,
                           String status,int scroll,int total) {
    public PlaylistView { playlists=List.copyOf(playlists);tracks=List.copyOf(tracks); }
    public boolean detail(){return section==Section.LIKED||selected!=null;}
    public static PlaylistView empty(){return new PlaylistView(Section.LIKED,null,List.of(),List.of(),false,false,false,false,"",0,0);}
}
