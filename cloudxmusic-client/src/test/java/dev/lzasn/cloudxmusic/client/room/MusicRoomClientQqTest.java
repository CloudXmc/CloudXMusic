package dev.lzasn.cloudxmusic.client.room;

import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import dev.lzasn.cloudxmusic.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicRoomClientQqTest {
    @Test void rejectedVipMemberUsesRestrictedLabelInsteadOfBufferingOrGenericUnavailable()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("listener",outgoing);
        var track=new Packet.RoomTrack(new SongRef(MusicSourceType.QQ,"VIP1"),"VIP歌曲","歌手",180,"",true);
        room.receive(PacketCodec.encode(new Packet.RoomQueue(2,List.of(track),0,2)));
        room.receive(PacketCodec.encode(new Packet.RoomPlayback(3,true,0,180,RepeatMode.LIST,3)));
        room.reportSelfState(MemberState.UNAVAILABLE,"版权受限");
        assertEquals("版权受限",room.view().members().get(1).detail());
        assertEquals(dev.lzasn.cloudxmusic.client.gui.design.MusicView.MemberTone.UNAVAILABLE,room.view().members().get(1).tone());
        room.receive(PacketCodec.encode(new Packet.RoomUpdated(4,new Packet.RoomSnapshot("ABC123","owner",MusicSourceType.QQ,
                List.of(new Packet.RoomMember("owner",true),new Packet.RoomMember("listener",false,MemberState.BUFFERING)),20,true,4))));
        assertEquals("版权受限",room.view().members().get(1).detail(),"服务端旧快照不能覆盖本地权限失败");
        room.reportSelfState(MemberState.UNAVAILABLE,"请先登录QQ音乐");
        assertEquals("请先登录QQ音乐",room.view().members().get(1).detail());
        room.reportSelfState(MemberState.BUFFERING);
        assertEquals("正在获取音频...",room.view().members().get(1).detail());
        assertTrue(room.qqAudioStarted("VIP1",room.seekRevision()));
        assertEquals("正在跟听",room.view().members().get(1).detail());
        assertTrue(room.view().queue().getFirst().vip());assertTrue(room.view().nowVip());
        assertTrue(room.view().withQueueScroll(2).withMemberScroll(1).nowVip());
    }
    @Test void changingMidClearsPermissionDetailAndDuplicateReportsDoNotFloodNetwork()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("listener",outgoing);var owner=room("owner",outgoing);
        owner.syncQqOwnerQueue(songs(2));var initial=(Packet.QueueSet)outgoing.getFirst();ack(room,initial,0,10,true);
        room.reportSelfState(MemberState.UNAVAILABLE,"版权受限");outgoing.clear();
        for(int i=0;i<100;i++)room.reportSelfState(MemberState.UNAVAILABLE,"版权受限");
        assertTrue(outgoing.isEmpty());
        ack(room,initial,1,0,true);assertEquals("正在获取音频...",room.view().members().get(1).detail());
        room.reportSelfState(MemberState.UNAVAILABLE,"播放失败");
        assertEquals("播放失败",room.view().members().get(1).detail(),"网络/声卡错误不能被旧版权标记覆盖");
        assertFalse(room.view().nowVip());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"SINGLE,1,0","LIST,1,0","SHUFFLE,1,0","SINGLE,3,2","LIST,3,0","SHUFFLE,3,1","LIST,100,0","SHUFFLE,100,1"})
    void ownerEofUsesServerModeFullQueueAndWaitsForBroadcast(RepeatMode mode,int count,int expected)throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("owner",outgoing);room.random(bound->Math.min(1,bound-1));
        room.syncQqOwnerQueue(songs(count));outgoing.clear();room.jumpOwnerQueue(count-1);
        var set=outgoing.stream().filter(Packet.QueueSet.class::isInstance).map(Packet.QueueSet.class::cast).findFirst();
        List<Packet.RoomTrack> tracks=set.isPresent()?set.get().tracks():songs(count).stream().map(s->new Packet.RoomTrack(new SongRef(MusicSourceType.QQ,s.platformId()),s.title(),s.artist(),180,"",false)).toList();
        int index=set.isPresent()?set.get().index():count-1;
        room.receive(PacketCodec.encode(new Packet.RoomQueue(20,tracks,index,20)));
        room.receive(PacketCodec.encode(new Packet.RoomPlayback(21,true,1800,180,mode,21)));outgoing.clear();
        long timeline=room.seekRevision();room.qqAudioFinished("MID"+(count-1),timeline);
        int sent=outgoing.size();assertTrue(sent>0);room.qqAudioFinished("MID"+(count-1),timeline);assertEquals(sent,outgoing.size());
        if(count==1||mode==RepeatMode.SINGLE){
            assertTrue(outgoing.stream().anyMatch(p->p instanceof Packet.PlaybackControl c&&c.action()==PlaybackAction.SEEK&&c.positionTenths()==0));
            assertTrue(outgoing.stream().anyMatch(p->p instanceof Packet.PlaybackControl c&&c.action()==PlaybackAction.PLAY));
        }
        Packet.RoomTrack next=outgoing.stream().filter(Packet.QueueSet.class::isInstance).map(Packet.QueueSet.class::cast).map(q->q.tracks().get(q.index())).findFirst().orElseGet(()->{
            var jump=outgoing.stream().filter(Packet.PlaybackControl.class::isInstance).map(Packet.PlaybackControl.class::cast).filter(c->c.action()==PlaybackAction.JUMP).findFirst();
            return tracks.get(jump.map(Packet.PlaybackControl::index).orElse(expected));
        });
        assertEquals("MID"+expected,next.ref().songId());assertEquals("MID"+(count-1),room.currentRoomTrack().ref().songId(),"房主也等服务端确认后切歌");
    }
    @Test void listenerEofNeverSendsTransportAndOldEofCannotSkipNewSong()throws Exception{
        var outgoing=new ArrayList<Packet>();var owner=room("owner",outgoing);owner.syncQqOwnerQueue(songs(2));
        var initial=(Packet.QueueSet)outgoing.getFirst();var listener=room("listener",outgoing);
        ack(listener,initial,0,1800,true);outgoing.clear();long timeline=listener.seekRevision();
        listener.qqAudioFinished("MID0",timeline);assertTrue(outgoing.isEmpty());
        ack(listener,initial,1,0,true);listener.qqAudioFinished("MID0",timeline);assertTrue(outgoing.isEmpty());
    }
    @Test void hostCanRecoverFromServerEndOfQueuePauseUsingListMode()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("owner",outgoing);room.random(bound->0);
        room.syncQqOwnerQueue(songs(2));var initial=(Packet.QueueSet)outgoing.getFirst();
        ack(room,initial,1,1800,true);outgoing.clear();long timeline=room.seekRevision();
        room.receive(PacketCodec.encode(new Packet.RoomPlayback(4,false,1800,180,RepeatMode.LIST,4)));
        assertFalse(room.serverPlaying());room.qqAudioFinished("MID1",timeline);
        assertTrue(outgoing.stream().anyMatch(p->p instanceof Packet.PlaybackControl c&&c.action()==PlaybackAction.JUMP));
        assertEquals("MID1",room.currentRoomTrack().ref().songId(),"房主本地索引等待服务端 Queue 广播");
    }
    @Test void manualHostPauseWinsOverARacingEofAtTheEndOfTheSong()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("owner",outgoing);
        room.syncQqOwnerQueue(songs(1));var initial=(Packet.QueueSet)outgoing.getFirst();ack(room,initial,0,1800,true);
        long timeline=room.seekRevision();room.pause();outgoing.clear();
        room.receive(PacketCodec.encode(new Packet.RoomPlayback(4,false,1800,180,RepeatMode.LIST,4)));
        room.qqAudioFinished("MID0",timeline);assertTrue(outgoing.isEmpty(),"刚手动暂停时迟到 EOF 不能再次 PLAY");
    }
    @Test void firstAudioOutputOverridesStaleUnavailableMemberAndBroadcastsRecovery()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("listener",outgoing);var owner=room("owner",outgoing);
        owner.syncQqOwnerQueue(songs(2));var initial=(Packet.QueueSet)outgoing.getFirst();ack(room,initial,0,30,true);
        room.receive(PacketCodec.encode(new Packet.RoomUpdated(4,new Packet.RoomSnapshot("ABC123","owner",MusicSourceType.QQ,
                List.of(new Packet.RoomMember("owner",true),new Packet.RoomMember("listener",false,MemberState.UNAVAILABLE)),20,true,4))));
        room.reportSelfState(MemberState.UNAVAILABLE);assertEquals("本曲不可播放",room.view().members().get(1).detail());outgoing.clear();
        assertTrue(room.qqAudioStarted("MID0",room.seekRevision()));
        assertEquals(dev.lzasn.cloudxmusic.client.gui.design.MusicView.MemberTone.ACTIVE,room.view().members().get(1).tone());
        assertTrue(outgoing.getFirst() instanceof Packet.MemberStateUpdate update&&update.state()==MemberState.LISTENING);
        ack(room,initial,1,0,true);outgoing.clear();assertFalse(room.qqAudioStarted("MID0",room.seekRevision()-1));assertTrue(outgoing.isEmpty());
    }
    @Test void lateCoverMetadataIsPublishedWithoutChangingCurrentMid()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("owner",outgoing);var songs=songs(1);
        room.syncQqOwnerQueue(songs);var initial=(Packet.QueueSet)outgoing.getFirst();ack(room,initial,0,100,true);outgoing.clear();
        var song=songs.getFirst();room.syncQqOwnerQueue(List.of(new SongView(song.source(),song.platformId(),song.title(),song.artist(),"",180,"https://y.gtimg.cn/cover.jpg",false)));
        var updated=(Packet.QueueSet)outgoing.getFirst();assertEquals("https://y.gtimg.cn/cover.jpg",updated.tracks().getFirst().coverUrl());
        assertEquals("MID0",updated.tracks().get(updated.index()).ref().songId());
    }
    @Test void shortSameMidLoopBroadcastIsNotDiscardedAsAnEcho()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("owner",outgoing);
        room.syncQqOwnerQueue(songs(1));var initial=(Packet.QueueSet)outgoing.getFirst();
        ack(room,initial,0,8,true);long before=room.seekRevision();outgoing.clear();
        room.receive(PacketCodec.encode(new Packet.RoomPlayback(5,true,0,180,RepeatMode.LIST,5)));
        assertEquals(before+1,room.seekRevision(),"同 MID 短曲回到零点仍是新播放轮次");
        assertTrue(outgoing.isEmpty(),"广播回调不能反向发送控制包");
    }
    private List<SongView> songs(int count){return java.util.stream.IntStream.range(0,count).mapToObj(i->
            new SongView(MusicSource.QQ,"MID"+i,"歌"+i,"歌手","",180,"",false)).toList();}
    private MusicRoomClient room(String name,List<Packet> outgoing)throws Exception{
        var room=new MusicRoomClient(()->name,()->true,outgoing::add,()->{});
        room.receive(PacketCodec.encode(new Packet.RoomJoined(1,
                new Packet.RoomSnapshot("ABC123","owner",MusicSourceType.QQ,
                        name.equals("owner")?List.of(new Packet.RoomMember("owner",true)):
                                List.of(new Packet.RoomMember("owner",true),new Packet.RoomMember(name,false)),20,false,1))));
        return room;
    }
    private void ack(MusicRoomClient room,Packet.QueueSet sent,int index,int position,boolean playing)throws Exception{
        room.receive(PacketCodec.encode(new Packet.RoomQueue(2,sent.tracks(),index,2)));
        room.receive(PacketCodec.encode(new Packet.RoomPlayback(3,playing,position,180,RepeatMode.LIST,3)));
    }
    @Test void localLoadingPauseEofAndPersonalSingleModeCannotBroadcastControls()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("owner",outgoing);var songs=songs(10);
        room.syncQqOwnerQueue(songs);var initial=(Packet.QueueSet)outgoing.getFirst();ack(room,initial,0,1000,true);outgoing.clear();
        for(int i=0;i<100;i++)room.syncOwnerQueue(songs,"MID8",i%2==0?0:1800,i%2==0,PlaybackMode.REPEAT_ONE);
        assertTrue(outgoing.isEmpty(),"本地解码状态不得反向生成 PLAY/PAUSE/SEEK/JUMP/REPEAT");
        assertTrue(room.serverPlaying());assertEquals("MID0",room.currentRoomTrack().ref().songId());assertEquals(RepeatMode.LIST,room.repeatMode());
    }
    @Test void playbackEchoesAreIdempotentAndRealServerSeekNeverSendsAnEcho()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("owner",outgoing);
        room.syncQqOwnerQueue(songs(2));var initial=(Packet.QueueSet)outgoing.getFirst();ack(room,initial,0,100,true);outgoing.clear();
        long revision=room.seekRevision();
        for(int i=0;i<100;i++)room.receive(PacketCodec.encode(new Packet.RoomPlayback(4,true,100,180,RepeatMode.LIST,4)));
        assertEquals(revision,room.seekRevision());assertTrue(outgoing.isEmpty());
        room.receive(PacketCodec.encode(new Packet.RoomPlayback(5,true,500,180,RepeatMode.LIST,5)));
        assertEquals(revision+1,room.seekRevision());assertTrue(outgoing.isEmpty());
        room.receive(PacketCodec.encode(new Packet.RoomPlayback(6,true,900,180,RepeatMode.LIST,5)));
        assertEquals(revision+1,room.seekRevision(),"同一时间轴代次的周期回声不能被当成 seek");
    }
    @Test void slidingWindowKeepsServerPositionAndDoesNotRepeatWhileWaitingForAck()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("owner",outgoing);var songs=songs(100);
        room.syncQqOwnerQueue(songs);var initial=(Packet.QueueSet)outgoing.getFirst();ack(room,initial,25,500,true);outgoing.clear();
        room.syncQqOwnerQueue(songs);var refill=(Packet.QueueSet)outgoing.getFirst();
        assertEquals(30,refill.tracks().size());assertEquals("MID25",refill.tracks().get(refill.index()).ref().songId());
        assertTrue(outgoing.stream().anyMatch(p->p instanceof Packet.PlaybackControl c&&c.action()==PlaybackAction.SEEK&&c.positionTenths()>=500));
        int sent=outgoing.size();for(int i=0;i<100;i++)room.syncQqOwnerQueue(songs);assertEquals(sent,outgoing.size());
        ack(room,refill,refill.index(),500,true);room.syncQqOwnerQueue(songs);assertEquals(sent,outgoing.size());
        assertEquals(100,room.view().queue().size());assertEquals("MID25",room.view().currentMid());
    }
    @Test void listenerQueueHighlightUsesBroadcastMidAndCannotEdit()throws Exception{
        var outgoing=new ArrayList<Packet>();var owner=room("owner",outgoing);owner.syncQqOwnerQueue(songs(3));
        var initial=(Packet.QueueSet)outgoing.getFirst();outgoing.clear();var listener=room("listener",outgoing);ack(listener,initial,2,0,true);
        assertEquals("MID2",listener.view().currentMid());
        listener.syncQqOwnerQueue(songs(4));listener.replaceQqOwnerQueue(songs(4),0);listener.jumpOwnerQueue(0);
        assertTrue(outgoing.isEmpty());
    }
    @Test void deletingTheLastSongSendsAnEmptyWindowOnlyOnce()throws Exception{
        var outgoing=new ArrayList<Packet>();var room=room("owner",outgoing);room.syncQqOwnerQueue(songs(1));
        var initial=(Packet.QueueSet)outgoing.getFirst();ack(room,initial,0,300,true);outgoing.clear();
        room.syncQqOwnerQueue(List.of());var empty=(Packet.QueueSet)outgoing.getFirst();
        assertTrue(empty.tracks().isEmpty());assertEquals(-1,empty.index());
        // 确认仍使用原协议编码，空窗口不能在网络层变成无效包。
        assertEquals(empty,PacketCodec.decode(PacketCodec.encode(empty)));
        int sent=outgoing.size();for(int i=0;i<100;i++)room.syncQqOwnerQueue(List.of());assertEquals(sent,outgoing.size());
        room.receive(PacketCodec.encode(new Packet.RoomQueue(9,List.of(),-1,9)));outgoing.clear();
        room.syncQqOwnerQueue(List.of());assertTrue(outgoing.isEmpty());assertFalse(room.serverPlaying());
    }
}
