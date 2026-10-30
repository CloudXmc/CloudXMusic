package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class QQPlaybackControllerTest {
    @Test void qualityCheckmarkFollowsFallbackThenRecoversSqOnNextTrackWithoutChangingPreference()throws Exception{
        Audio a=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();List<QQQuality> requests=new CopyOnWriteArrayList<>();
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,(song,q)->{
            requests.add(q);QQQuality actual=song==A?QQQuality.STANDARD:QQQuality.SQ;
            return new QQPlaybackController.Resolved("https://a.qq.com/"+song.platformId()+actual.extension(),180,actual);
        },()->1)){
            c.play(List.of(A,B),0);pump(main,()->!c.view().loading());
            assertEquals(QQQuality.SQ,c.preferredQuality());assertEquals(QQQuality.STANDARD,c.view().actualQuality());
            assertEquals(QQQuality.STANDARD.ordinal(),QQQualityMenu.selected(c.view(),c.preferredQuality()),"对勾跟随当前标准音源，不能跟随 SQ 偏好");
            c.toggle();pump(main,()->c.view().paused());assertEquals(QQQuality.STANDARD.ordinal(),QQQualityMenu.selected(c.view(),c.preferredQuality()));
            c.next();pump(main,()->a.played.size()==2&&!c.view().loading());
            assertEquals(QQQuality.SQ,c.view().actualQuality());assertEquals(QQQuality.SQ.ordinal(),QQQualityMenu.selected(c.view(),c.preferredQuality()));
            assertEquals(List.of(QQQuality.SQ,QQQuality.SQ),requests);assertEquals(QQQuality.SQ,c.preferredQuality());
        }
    }
    @Test void clickingPreferredSqAgainRetriesAFallenBackSongAtTheSameProgress()throws Exception{
        class OffsetAudio extends Audio {
            double offset;
            @Override public CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier allowed){offset=position;return super.play(uri,duration,allowed);}
        }
        OffsetAudio a=new OffsetAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger calls=new AtomicInteger();
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,(song,q)->{
            assertEquals(QQQuality.SQ,q);QQQuality actual=calls.getAndIncrement()==0?QQQuality.STANDARD:QQQuality.SQ;
            return new QQPlaybackController.Resolved("https://a.qq.com/A"+actual.extension(),180,actual);
        },()->1)){
            c.play(List.of(A),0);pump(main,()->!c.view().loading());a.v=new QQPlaybackAudio.View(QQPlaybackAudio.Status.PLAYING,42,180,.8f,"");
            c.quality(QQQuality.SQ);pump(main,()->calls.get()==2&&!c.view().loading());
            assertEquals(42,a.offset);assertEquals(QQQuality.SQ,c.view().actualQuality());assertEquals(QQQuality.SQ,c.preferredQuality());
        }
    }
    @Test void consecutiveUnavailableTracksAreTriedOnceInEveryModeAndKeepThePreference()throws Exception{
        var songs=java.util.stream.IntStream.range(0,6).mapToObj(i->new UnifiedSong("qq","Missing"+i,"","","")).toList();
        for(var mode:dev.lzasn.cloudxmusic.client.state.PlaybackMode.values()){
            Audio audio=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
            List<String> attempts=new CopyOnWriteArrayList<>();List<QQQuality> qualities=new CopyOnWriteArrayList<>();
            try(var c=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,(song,q)->{
                attempts.add(song.platformId());qualities.add(q);return null;
            },()->1)){
                c.mode(mode);c.random(bound->bound-1);c.quality(QQQuality.HQ);c.play(songs,0);
                pump(main,()->c.view().error());
                assertEquals(songs.size(),attempts.size(),mode.name());
                assertEquals(songs.size(),new HashSet<>(attempts).size(),"同一轮不应重复请求失败 MID");
                assertTrue(qualities.stream().allMatch(q->q==QQQuality.HQ));assertTrue(audio.played.isEmpty());
                c.tick();assertFalse(c.view().loading());assertTrue(c.view().status().contains("均无可用音源"));
            }
        }
    }
    @Test void aLateUnavailableResultCannotSkipTheUsersNewSelection()throws Exception{
        Audio audio=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger attempts=new AtomicInteger();
        try(var c=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,song->{
            attempts.incrementAndGet();
            if(song==A){entered.countDown();for(;;){try{release.await();break;}catch(InterruptedException ignored){}}return null;}
            return new QQPlaybackController.Resolved("https://a.qq.com/B.mp3",100);
        },()->1)){
            c.play(List.of(A,B),0);assertTrue(entered.await(1,TimeUnit.SECONDS));
            c.play(List.of(A,B),1);release.countDown();pump(main,()->!c.view().loading());
            assertEquals(B,c.view().song());assertEquals(List.of("/B.mp3"),audio.played);assertEquals(2,attempts.get());
        }finally{release.countDown();}
    }
    @Test void manualShuffleDoesNotFollowSequentialOrderAndNeverImmediatelyRepeats()throws Exception{
        Audio audio=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        var songs=java.util.stream.IntStream.range(0,6).mapToObj(i->new UnifiedSong("qq","R"+i,"","","")).toList();
        try(var c=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,song->new QQPlaybackController.Resolved("https://a.qq.com/"+song.platformId()+".mp3",100),()->1)){
            c.random(bound->bound-1);
            c.mode(dev.lzasn.cloudxmusic.client.state.PlaybackMode.SHUFFLE);c.play(songs,0);pump(main,()->!c.view().loading());
            boolean nonSequential=false;
            for(int i=0;i<20;i++){
                int before=songs.indexOf(c.view().song());c.next();int expected=i+2;
                pump(main,()->audio.played.size()==expected&&!c.view().loading());int after=songs.indexOf(c.view().song());
                assertNotEquals(before,after);nonSequential|=after!=(before+1)%songs.size();
            }
            assertTrue(nonSequential,"随机按钮不能仍然沿用顺序索引");
        }
    }
    @Test void unavailableTrackSkipsButWholeUnavailableQueueTerminates()throws Exception{
        for(boolean allMissing:new boolean[]{false,true}){
            Audio audio=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger calls=new AtomicInteger();
            try(var c=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,song->{calls.incrementAndGet();return song==A||allMissing?null:new QQPlaybackController.Resolved("https://a.qq.com/B.mp3",100);},()->1)){
                c.mode(dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_ONE);c.play(List.of(A,B),0);
                if(allMissing){pump(main,()->c.view().error());assertEquals(2,calls.get());assertTrue(audio.played.isEmpty());}
                else {pump(main,()->!audio.played.isEmpty());assertEquals(B,c.view().song());assertEquals(2,calls.get());}
            }
        }
    }
    @Test void skipNotifiesAndPendingNullResultCannotResumeAfterStopOrSourceHandoff()throws Exception{
        for(boolean stop:new boolean[]{false,true}){
            Audio audio=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();CompletableFuture<Void> quiet=new CompletableFuture<>();
            AtomicInteger calls=new AtomicInteger();List<String> notices=new ArrayList<>();
            try(var c=new QQPlaybackController(audio,()->quiet,main::add,song->{calls.incrementAndGet();return null;},()->1)){
                c.notice(notices::add);c.play(List.of(A,B),0);pump(main,()->calls.get()==1);
                if(stop)c.stop();else c.suspend();quiet.complete(null);
                pump(main,()->!c.view().loading());Runnable callback;while((callback=main.poll())!=null)callback.run();
                assertEquals(1,calls.get());assertTrue(notices.isEmpty());assertTrue(audio.played.isEmpty());
            }
        }
        Audio audio=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();List<String> notices=new ArrayList<>();List<QQQuality> qualities=new CopyOnWriteArrayList<>();
        try(var c=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,(song,q)->{qualities.add(q);return song==A?null:new QQPlaybackController.Resolved("https://a.qq.com/B.mp3",100,QQQuality.STANDARD);},()->1)){
            c.notice(notices::add);c.play(List.of(A,B),0);pump(main,()->!audio.played.isEmpty());
            assertEquals(List.of("该歌曲无可用音源，已跳过"),notices);assertEquals(List.of(QQQuality.SQ,QQQuality.SQ),qualities);assertEquals(QQQuality.SQ,c.preferredQuality());
        }
    }
    @Test void networkFailureIsNotMisclassifiedAsMissingCopyright()throws Exception{
        Audio audio=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger calls=new AtomicInteger();
        try(var c=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,song->{calls.incrementAndGet();throw new java.io.IOException("timeout");},()->1)){
            c.play(List.of(A,B),0);pump(main,()->c.view().error());assertEquals(1,calls.get());assertEquals(A,c.view().song());
        }
    }
    @Test void fallbackReportsActualQualityWithoutChangingPreferenceAndManualQueueInheritsIt()throws Exception{
        Audio a=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();List<QQQuality> requests=new CopyOnWriteArrayList<>();List<String> feedback=new ArrayList<>();
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,(s,q)->{requests.add(q);return new QQPlaybackController.Resolved("https://a.qq.com/"+s.platformId()+".mp3",100,QQQuality.STANDARD);},()->1)){
            c.qualityFeedback((requested,actual)->feedback.add(requested+":"+actual));
            c.play(List.of(A,B),0);pump(main,()->!c.view().loading());assertEquals("标准 128k",c.view().quality());assertEquals(QQQuality.SQ,c.preferredQuality());
            c.next();pump(main,()->a.played.size()==2&&!c.view().loading());c.previous();pump(main,()->a.played.size()==3&&!c.view().loading());
            assertEquals(List.of(QQQuality.SQ,QQQuality.SQ,QQQuality.SQ),requests);assertEquals(List.of("SQ:STANDARD","SQ:STANDARD","SQ:STANDARD"),feedback);
        }
    }
    @Test void accountChangeClearsEvenStoppedPrivateQueue(){
        Audio a=new Audio();AtomicLong revision=new AtomicLong(1);
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),Runnable::run,s->null,revision::get)){
            c.enqueue(A);c.stop();assertEquals(1,c.queue().size());revision.incrementAndGet();c.tick();assertTrue(c.queue().isEmpty());
        }
    }
    @Test void rapidQualityChoiceCannotCommitAnEarlierResolvedStream()throws Exception{
        class OffsetAudio extends Audio{
            double offset;
            @Override public CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier p){offset=position;return super.play(uri,duration,p);}
        }
        OffsetAudio a=new OffsetAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();CountDownLatch resolvingMp3=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,(song,q)->{
            if(q==QQQuality.HQ){resolvingMp3.countDown();while(true){try{release.await();break;}catch(InterruptedException ignored){}}}
            return new QQPlaybackController.Resolved("https://a.qq.com/A."+(q==QQQuality.SQ?"flac":"mp3"),180);
        },()->1)){
            c.play(List.of(A),0);pump(main,()->a.played.size()==1&&!c.view().loading());a.v=new QQPlaybackAudio.View(QQPlaybackAudio.Status.PLAYING,55.5,180,.8f,"");
            c.quality(QQQuality.HQ);assertTrue(resolvingMp3.await(1,TimeUnit.SECONDS));c.quality(QQQuality.SQ);release.countDown();
            pump(main,()->a.played.size()==2&&!c.view().loading());assertEquals(List.of("/A.flac","/A.flac"),a.played);assertEquals(55.5,a.offset);
        }finally{release.countDown();}
    }
    @Test void qualityReplacementPreservesOffsetPausedStateAndChosenFormat()throws Exception{
        class OffsetAudio extends Audio {
            double position;boolean paused;
            @Override public CompletableFuture<Void> playFrom(URI uri,int duration,double offset,boolean pause,BooleanSupplier permitted){
                position=offset;paused=pause;var result=super.play(uri,duration,permitted);v=new View(pause?Status.PAUSED:Status.PLAYING,offset,duration,.8f,"");return result;
            }
        }
        OffsetAudio a=new OffsetAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();List<QQQuality> requested=new CopyOnWriteArrayList<>();
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,(s,q)->{requested.add(q);return new QQPlaybackController.Resolved("https://a.qq.com/A."+(q==QQQuality.SQ?"flac":"mp3"),180);},()->1)){
            c.play(List.of(A),0);pump(main,()->a.played.size()==1&&!c.view().loading());
            a.v=new QQPlaybackAudio.View(QQPlaybackAudio.Status.PAUSED,42.125,180,.8f,"");
            c.quality(QQQuality.HQ);pump(main,()->a.played.size()==2&&!c.view().loading());
            assertEquals(42.125,a.position);assertTrue(a.paused);assertEquals(List.of(QQQuality.SQ,QQQuality.HQ),requested);assertEquals("/A.mp3",a.played.getLast());
        }
    }
    @Test void globalSourceGuardPreventsStartsAndSuspendPreservesQueue()throws Exception{
        Audio a=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicBoolean qq=new AtomicBoolean(true);
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/A.flac",180),()->1)){
            c.sourceGuards(qq::get,()->!qq.get());c.play(List.of(A,B),0);pump(main,()->!a.played.isEmpty());qq.set(false);c.suspend();assertEquals(2,c.queue().size());assertEquals(A,c.view().song());assertFalse(a.allowed.getAsBoolean());
            c.next();c.toggle();assertEquals(1,a.played.size());qq.set(true);c.toggle();pump(main,()->c.view().playing());assertEquals(2,c.queue().size());
        }
    }
    static final UnifiedSong A=new UnifiedSong("qq","A","甲","歌手","专辑"),B=new UnifiedSong("qq","B","乙","歌手","专辑");
    static class Audio implements QQPlaybackAudio {
        View v=new View(Status.STOPPED,0,100,.8f,"");List<String> played=new ArrayList<>();
        CompletableFuture<Void> pause=CompletableFuture.completedFuture(null);BooleanSupplier allowed=()->false;
        public CompletableFuture<Void> play(URI uri,int duration,BooleanSupplier p){allowed=p;played.add(uri.getPath());v=new View(Status.PLAYING,0,duration,.8f,"");return CompletableFuture.completedFuture(null);}
        public CompletableFuture<Void> pause(){v=new View(Status.PAUSED,1,100,.8f,"");return pause;}
        public CompletableFuture<Void> resume(BooleanSupplier p){allowed=p;v=new View(Status.PLAYING,1,100,.8f,"");return CompletableFuture.completedFuture(null);}
        public CompletableFuture<Void> stop(){v=new View(Status.STOPPED,0,0,.8f,"");return CompletableFuture.completedFuture(null);}
        public void volume(float f){v=new View(v.status(),v.position(),v.duration(),f,"");}public View view(){return v;}public void close(){}
    }
    static void pump(Queue<Runnable> q,java.util.function.BooleanSupplier done)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!done.getAsBoolean()&&System.nanoTime()<end){Runnable r;while((r=q.poll())!=null)r.run();Thread.sleep(2);}
        assertTrue(done.getAsBoolean(),"后台事件未在期限内完成");
    }
    @Test void qqWaitsForNeteaseOutputAcknowledgement()throws Exception{
        Audio a=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();CompletableFuture<Void> silent=new CompletableFuture<>();
        try(var c=new QQPlaybackController(a,()->silent,main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/"+s.platformId()+".flac",180),()->1)){
            c.play(List.of(A),0);Thread.sleep(30);Runnable r;while((r=main.poll())!=null)r.run();assertTrue(a.played.isEmpty());
            silent.complete(null);pump(main,()->!a.played.isEmpty());assertTrue(a.allowed.getAsBoolean());
        }
    }
    @Test void neteaseWaitsForQqPauseAcknowledgement(){
        Audio a=new Audio();a.pause=new CompletableFuture<>();AtomicBoolean started=new AtomicBoolean();
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),Runnable::run,s->null,()->1)){
            c.beforeNetease(()->started.set(true));assertFalse(started.get());a.pause.complete(null);assertTrue(started.get());
        }
    }
    @Test void failedHandoffCannotStartNetease(){
        Audio a=new Audio();a.pause=CompletableFuture.failedFuture(new IllegalStateException("声卡未暂停"));AtomicBoolean started=new AtomicBoolean();
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),Runnable::run,s->null,()->1)){
            c.beforeNetease(()->started.set(true));assertFalse(started.get());assertTrue(c.view().error());
        }
    }
    @Test void lateQqResolverCannotStealOutputAfterNeteaseRequest()throws Exception{
        Audio a=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,s->{entered.countDown();while(!release.await(10,TimeUnit.MILLISECONDS)){}return new QQPlaybackController.Resolved("https://a.qq.com/A.flac",180);},()->1)){
            c.play(List.of(A),0);assertTrue(entered.await(1,TimeUnit.SECONDS));AtomicBoolean net=new AtomicBoolean();c.beforeNetease(()->net.set(true));release.countDown();pump(main,net::get);Thread.sleep(30);Runnable r;while((r=main.poll())!=null)r.run();assertTrue(a.played.isEmpty());
        }finally{release.countDown();}
    }
    @Test void nextAndPreviousUseSeparateImmutableQqList()throws Exception{
        Audio a=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();ArrayList<UnifiedSong> list=new ArrayList<>(List.of(A,B));
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/"+s.platformId()+".mp3",120),()->1)){
            c.play(list,0);list.clear();pump(main,()->a.played.size()==1);c.next();pump(main,()->a.played.size()==2);assertEquals("/B.mp3",a.played.get(1));
            c.previous();pump(main,()->a.played.size()==3);assertEquals("/A.mp3",a.played.get(2));c.volume(.3f);assertEquals(.3f,c.view().volume());
        }
    }
    @Test void accountChangeInvalidatesPlaybackPermit()throws Exception{
        Audio a=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong account=new AtomicLong(1);
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/A.flac",180),account::get)){
            c.play(List.of(A),0);pump(main,()->!a.played.isEmpty());assertTrue(a.allowed.getAsBoolean());account.incrementAndGet();assertFalse(a.allowed.getAsBoolean());c.tick();assertFalse(c.hasTrack());
        }
    }
    @Test void stopCancelsLateStartAndHidesQqBar()throws Exception{
        Audio a=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();CompletableFuture<Void> silent=new CompletableFuture<>();
        try(var c=new QQPlaybackController(a,()->silent,main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/A.flac",180),()->1)){
            c.play(List.of(A),0);c.stop();silent.complete(null);Thread.sleep(30);Runnable r;while((r=main.poll())!=null)r.run();assertTrue(a.played.isEmpty());assertFalse(c.hasTrack());
        }
    }
    @Test void aNewNeteaseStartStillWaitsForEarlierNeteaseCloseBarrier()throws Exception{
        Audio a=new Audio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();CompletableFuture<Void> oldNetease=new CompletableFuture<>();AtomicBoolean started=new AtomicBoolean();
        try(var c=new QQPlaybackController(a,()->oldNetease,main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/A.flac",180),()->1)){
            c.play(List.of(A),0);c.beforeNetease(()->started.set(true));Runnable r;while((r=main.poll())!=null)r.run();assertFalse(started.get());
            oldNetease.complete(null);pump(main,started::get);assertTrue(a.played.isEmpty());
        }
    }
}
