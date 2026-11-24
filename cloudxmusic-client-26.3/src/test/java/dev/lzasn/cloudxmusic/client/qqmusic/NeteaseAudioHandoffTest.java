package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.audio.Mp3Player;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NeteaseAudioHandoffTest {
    static Mp3Player player(QQAudioPlayerTest.Device device)throws Exception{
        Mp3Player player=new Mp3Player();Class<?> type=Class.forName(Mp3Player.class.getName()+"$Engine");
        var ctor=type.getDeclaredConstructor(long.class);ctor.setAccessible(true);Object engine=ctor.newInstance(1L);
        var line=type.getDeclaredField("line");line.setAccessible(true);line.set(engine,device.line());
        var field=Mp3Player.class.getDeclaredField("engine");field.setAccessible(true);field.set(player,engine);device.open=true;device.running=true;
        return player;
    }
    @Test void handoffCompletionMeansOldNeteaseDeviceIsClosed()throws Exception{
        var device=new QQAudioPlayerTest.Device(new AtomicInteger(1),new AtomicInteger(1));Mp3Player player=player(device);
        player.pauseForHandoff().get(1,TimeUnit.SECONDS);assertFalse(device.running);assertFalse(device.open);assertFalse(player.isActive());assertTrue(player.isPaused());
    }
    @Test void failedNeteaseStopRemainsUnacknowledgedOnRepeatedHandoff()throws Exception{
        var device=new QQAudioPlayerTest.Device(new AtomicInteger(1),new AtomicInteger(1));Mp3Player player=player(device);device.failStop=true;
        assertThrows(ExecutionException.class,()->player.pauseForHandoff().get(1,TimeUnit.SECONDS));
        assertThrows(ExecutionException.class,()->player.pauseForHandoff().get(1,TimeUnit.SECONDS));device.failStop=false;
        player.pauseForHandoff().get(1,TimeUnit.SECONDS);assertFalse(device.running);assertFalse(device.open);
    }
}
