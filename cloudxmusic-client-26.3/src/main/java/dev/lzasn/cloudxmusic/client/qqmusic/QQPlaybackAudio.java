package dev.lzasn.cloudxmusic.client.qqmusic;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** 声卡输出边界；交接 Future 成功表示旧输出已经停止，而非仅修改了标志位。 */
interface QQPlaybackAudio extends AutoCloseable {
    enum Status { STOPPED, LOADING, PLAYING, PAUSED, FINISHED, ERROR }
    record View(Status status,double position,double duration,float volume,String error) {}
    CompletableFuture<Void> play(URI uri,int duration,BooleanSupplier permitted);
    default CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier permitted){return play(uri,duration,permitted);}
    default CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier permitted,Runnable finished){return playFrom(uri,duration,position,paused,permitted);}
    /** started 只表示实际音频输出开始；正式播放器不能以控制命令入队代替此事件。 */
    default CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier permitted,Runnable started,Runnable finished){
        return playFrom(uri,duration,position,paused,permitted,finished).thenRun(()->{
            if(!paused&&permitted.getAsBoolean()&&view().status()==Status.PLAYING)started.run();
        });
    }
    CompletableFuture<Void> pause();
    CompletableFuture<Void> resume(BooleanSupplier permitted);
    CompletableFuture<Void> stop();
    void volume(float value);
    View view();
    /** 曲目取链/网络路径已经终止；声卡故障不能冒充版权或账号不可播放。 */
    default boolean unavailable(){return false;}
    default QQPlaybackAccessException.Reason accessFailure(){return null;}
    void close();
}
