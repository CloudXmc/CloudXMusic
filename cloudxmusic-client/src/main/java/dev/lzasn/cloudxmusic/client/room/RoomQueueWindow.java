package dev.lzasn.cloudxmusic.client.room;

import dev.lzasn.cloudxmusic.protocol.Packet;
import java.util.List;

/** 客户端完整队列到协议窗口的纯计算；不读取解码器播放状态。 */
final class RoomQueueWindow {
    static final int SIZE=30;
    record Selection(List<Packet.RoomTrack> tracks,int start,int index){}
    static Selection select(List<Packet.RoomTrack> full,int absoluteIndex,int previousStart){
        if(full.isEmpty())return new Selection(List.of(),0,-1);
        int selected=Math.clamp(absoluteIndex,0,full.size()-1);
        int start=Math.clamp(previousStart,0,Math.max(0,full.size()-SIZE));
        if(selected<start||selected>=start+SIZE||selected>=start+SIZE-5)
            start=Math.min(selected,Math.max(0,full.size()-SIZE));
        return new Selection(List.copyOf(full.subList(start,Math.min(full.size(),start+SIZE))),start,selected-start);
    }
}
