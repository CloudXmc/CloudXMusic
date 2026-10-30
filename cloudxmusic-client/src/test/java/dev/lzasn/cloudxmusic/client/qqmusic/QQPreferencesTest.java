package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import static org.junit.jupiter.api.Assertions.*;

class QQPreferencesTest {
    @Test void savingAudioPreferencesMustNotOverwriteASeparatelyConfirmedAgreement()throws Exception{
        var config=new QQMusicConfig(root.resolve("config.json"));CountDownLatch loaded=new CountDownLatch(1);
        try(var preferences=new QQPreferences(config,Runnable::run,v->loaded.countDown())){
            assertTrue(loaded.await(2,TimeUnit.SECONDS));
            var current=config.load();current.addProperty("agreedTerms",true);current.addProperty("termsVersion",1);config.save(current);
            preferences.update(QQQuality.HQ,.4f,LyricMode.ORIGINAL);
        }
        QQAudioPlayerTest.waitFor(()->{try{return config.load().has("quality");}catch(Exception e){return false;}});
        assertTrue(config.load().has("agreedTerms"),"旧偏好快照不能覆盖刚保存的协议同意状态");
        assertTrue(config.load().get("agreedTerms").getAsBoolean());
    }
    @TempDir Path root;
    @Test void everyTierAndTranslationPreferenceSurvivesSaveAndRestore()throws Exception{
        for(QQQuality tier:QQQuality.values()){
            var config=new QQMusicConfig(root.resolve(tier.name()+".json"));CountDownLatch first=new CountDownLatch(1);
            try(var pref=new QQPreferences(config,Runnable::run,v->first.countDown())){
                assertTrue(first.await(2,TimeUnit.SECONDS));pref.update(tier,.5f,LyricMode.ORIGINAL);
            }
            QQAudioPlayerTest.waitFor(()->{try{return config.load().has("quality");}catch(Exception e){return false;}});
            CountDownLatch second=new CountDownLatch(1);
            try(var restored=new QQPreferences(config,Runnable::run,v->second.countDown())){
                assertTrue(second.await(2,TimeUnit.SECONDS));assertEquals(tier,restored.view().quality());assertEquals(LyricMode.ORIGINAL,restored.view().lyrics());
            }
        }
    }
    @Test void restoresAndSavesIndependentPreferenceWithoutRemovingUnknownKeys()throws Exception{
        Path file=root.resolve("config.json");Files.writeString(file,"{\"custom\":\"保留\",\"quality\":\"MP3\",\"volume\":0.25,\"lyricMode\":\"ORIGINAL\"}");
        var config=new QQMusicConfig(file);CountDownLatch loaded=new CountDownLatch(1);
        try(var pref=new QQPreferences(config,Runnable::run,v->loaded.countDown())){
            assertTrue(loaded.await(2,TimeUnit.SECONDS));assertEquals(QQQuality.HQ,pref.view().quality());assertEquals(.25f,pref.view().volume());
            pref.update(QQQuality.SQ,.6f,LyricMode.BILINGUAL);
        }
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(!config.load().get("quality").getAsString().equals("SQ")&&System.nanoTime()<end)Thread.sleep(5);
        var saved=config.load();assertEquals("SQ",saved.get("quality").getAsString());assertEquals("保留",saved.get("custom").getAsString());assertEquals(.6f,saved.get("volume").getAsFloat());
    }
    @Test void malformedConfigIsRetainedAndDefaultsRemainUsable()throws Exception{
        Path file=root.resolve("config.json");Files.writeString(file,"{bad");
        try(var pref=new QQPreferences(new QQMusicConfig(file),Runnable::run,v->{})){
            QQAudioPlayerTest.waitFor(()->!pref.view().error().isBlank());assertEquals(QQQuality.SQ,pref.view().quality());pref.update(QQQuality.HQ,.4f,LyricMode.OFF);
        }
        assertEquals("{bad",Files.readString(file));
    }
}
