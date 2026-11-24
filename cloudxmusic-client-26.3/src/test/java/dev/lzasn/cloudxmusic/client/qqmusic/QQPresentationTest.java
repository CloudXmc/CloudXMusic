package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackControllerTest.*;

class QQPresentationTest {
    @Test void oldSongAndAccountCallbacksCannotReplaceNewPresentation()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong account=new AtomicLong(1);CountDownLatch fetched=new CountDownLatch(1);
        try(var p=new QQPresentation(s->new QQTrackMetadata.Info("Media",100,"Album","cover"),s->{fetched.countDown();return Lyric.parse("[00:01.00]"+s.title(),"");},main::add,account::get)){
            p.update(A,true);assertTrue(fetched.await(1,TimeUnit.SECONDS));account.incrementAndGet();p.update(B,true);
            pump(main,()->!p.view().lyrics().isEmpty());assertEquals("B",p.view().song().platformId());assertEquals("乙",p.view().lyrics().getFirst().text());
            p.update(B,false);while(!main.isEmpty())main.remove().run();assertTrue(p.view().lyrics().isEmpty());
        }
    }
    @Test void metadataFailureDoesNotSuppressLyricsAndNoLyricsIsNotNetworkFailure()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(var p=new QQPresentation(s->{throw new IOException();},s->Lyric.parse("[00:01.00]line",""),main::add,()->1)){
            p.update(A,true);pump(main,()->!p.view().lyrics().isEmpty());assertTrue(p.view().status().contains("封面"));assertEquals("",p.view().song().coverUrl());
        }
    }
}
