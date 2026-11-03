package dev.lzasn.cloudxmusic.client.room;

import dev.lzasn.cloudxmusic.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomQueueWindowTest {
    private List<Packet.RoomTrack> tracks(int count){return java.util.stream.IntStream.range(0,count).mapToObj(i->
            new Packet.RoomTrack(new SongRef(MusicSourceType.QQ,"MID"+i),"歌"+i,"歌手",180,"",false)).toList();}
    @Test void noRefillBeforeTheLastFiveAndRefillKeepsCurrentMid(){
        var all=tracks(100);var before=RoomQueueWindow.select(all,24,0);
        assertEquals(0,before.start());assertEquals(24,before.index());
        var refill=RoomQueueWindow.select(all,25,0);
        assertEquals(25,refill.start());assertEquals(0,refill.index());assertEquals(30,refill.tracks().size());
        assertEquals("MID25",refill.tracks().get(refill.index()).ref().songId());assertEquals(100,all.size());
    }
    @Test void everyAbsoluteSelectionFitsWithinTheProtocolLimit(){
        var all=tracks(137);int start=0;
        for(int i=0;i<all.size();i++){
            var next=RoomQueueWindow.select(all,i,start);start=next.start();
            assertTrue(next.tracks().size()<=30);assertEquals(all.get(i),next.tracks().get(next.index()));
        }
    }
    @Test void fullQueueEditsAndBackwardsJumpKeepTheSelectedIdentity(){
        var all=new ArrayList<>(tracks(100));all.removeFirst();
        var edited=RoomQueueWindow.select(all,24,25);
        assertEquals("MID25",edited.tracks().get(edited.index()).ref().songId());
        var previous=RoomQueueWindow.select(all,0,edited.start());assertEquals("MID1",previous.tracks().get(previous.index()).ref().songId());
    }
    @Test void emptyAndShortQueuesHaveValidIndexes(){
        assertEquals(-1,RoomQueueWindow.select(List.of(),0,0).index());
        var shortQueue=RoomQueueWindow.select(tracks(3),2,90);assertEquals(0,shortQueue.start());assertEquals(2,shortQueue.index());
    }
}
