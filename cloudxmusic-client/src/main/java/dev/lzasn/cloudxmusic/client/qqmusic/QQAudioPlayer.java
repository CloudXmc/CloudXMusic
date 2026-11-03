package dev.lzasn.cloudxmusic.client.qqmusic;

import javax.sound.sampled.*;
import java.io.*;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import dev.lzasn.cloudxmusic.client.audio.PcmPlaybackClock;

/** 正式 QQ 流式播放器：网络、解码、声卡控制均在后台，不保存完整音频。 */
public final class QQAudioPlayer implements QQPlaybackAudio {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("CloudXMusic/QQAudio");
    @FunctionalInterface interface LineFactory { SourceDataLine create(AudioFormat format) throws LineUnavailableException; }
    @FunctionalInterface interface StreamFactory { InputStream open(URI uri,Session session) throws Exception; }
    private final ThreadPoolExecutor control=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),
            r->{Thread t=new Thread(r,"CloudXMusic-QQ-AudioControl");t.setDaemon(true);return t;});
    private final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"CloudXMusic-QQ-AudioClock");t.setDaemon(true);return t;});
    private final QQAudioHttp http=new QQAudioHttp();
    private final LineFactory lines;
    private final StreamFactory streams;
    private volatile Session current;
    private volatile float volume=.8f;
    private volatile boolean closed;
    private volatile boolean unavailable;
    private volatile QQPlaybackAccessException.Reason accessFailure;
    private volatile View view=new View(Status.STOPPED,0,0,.8f,"");
    public QQAudioPlayer() {this(AudioSystem::getSourceDataLine,null);}
    QQAudioPlayer(LineFactory lines,StreamFactory streams) {
        this.lines=lines;this.streams=streams==null?this::openHttp:streams;
        clock.scheduleAtFixedRate(this::tick,0,40,TimeUnit.MILLISECONDS);
    }
    private CompletableFuture<Void> command(Runnable action) {
        CompletableFuture<Void> done=new CompletableFuture<>();
        if(closed) return CompletableFuture.failedFuture(new IllegalStateException("QQ 播放器已关闭"));
        try {control.execute(()->{try{action.run();done.complete(null);}catch(Throwable e){done.completeExceptionally(e);}});}
        catch(RejectedExecutionException e){done.completeExceptionally(new IllegalStateException("QQ 音频控制繁忙"));}
        return done.orTimeout(3,TimeUnit.SECONDS);
    }
    @Override public CompletableFuture<Void> play(URI uri,int duration,BooleanSupplier permitted) {
        return playFrom(uri,duration,0,false,permitted);
    }
    @Override public CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier permitted){
        return playFrom(uri,duration,position,paused,permitted,()->{});
    }
    @Override public CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier permitted,Runnable finished){
        return playFrom(uri,duration,position,paused,permitted,()->{},finished);
    }
    @Override public CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier permitted,Runnable started,Runnable finished){
        return command(()->{
            haltCurrent();
            if(!permitted.getAsBoolean()) return;
            Session s=new Session(uri,Math.max(0,duration),permitted);s.started=started;s.finished=finished;s.startPosition=Double.isFinite(position)?Math.max(0,position):0;
            s.position=s.startPosition;s.pcmClock.reset(s.startPosition);s.paused=paused;current=s;publish(s,Status.LOADING,"");
            s.worker=Thread.ofVirtual().name("CloudXMusic-QQ-Decode").start(()->decode(s));
        });
    }
    @Override public CompletableFuture<Void> pause() {
        return command(()->{Session s=current;if(s==null)return;
            synchronized(s.output){
                boolean terminal=s.cancelled||view.status()==Status.FINISHED||view.status()==Status.ERROR;
                s.paused=true;
                // EOF 排空期间暂停只停线，保留尚未播放的 PCM，恢复后继续 drain。
                if(s.draining&&s.line!=null)s.line.stop();else quiet(s,true);
                if(!terminal&&view.status()!=Status.FINISHED&&view.status()!=Status.ERROR)publish(s,Status.PAUSED,"");
            }});
    }
    @Override public CompletableFuture<Void> resume(BooleanSupplier permitted) {
        return command(()->{Session s=current;if(s==null||s.cancelled)throw new IllegalStateException("QQ 音频会话已结束，请重新点歌");
            s.permitted=permitted;if(!permitted.getAsBoolean())return;
            synchronized(s.output){
                s.gain.reset();s.lastIo=System.nanoTime();
                if(s.rate>0&&s.line==null)try{openLine(s,s.rate,s.channels);}catch(Exception e){throw new IllegalStateException("QQ 声卡恢复失败",e);}
                s.paused=false;if(s.line!=null)s.line.start();
            }
            publish(s,s.rate==0?Status.LOADING:Status.PLAYING,"");});
    }
    @Override public CompletableFuture<Void> stop(){return command(()->{haltCurrent();view=new View(Status.STOPPED,0,0,volume,"");});}
    @Override public void volume(float value){if(Float.isFinite(value))volume=Math.max(0,Math.min(1,value));}
    @Override public synchronized View view(){
        View result=view;Session s=current;
        return s==null?result:new View(result.status(),s.pcmClock.snapshot().seconds(),result.duration(),volume,result.error());
    }
    @Override public boolean unavailable(){return view.status()==Status.ERROR&&unavailable;}
    @Override public QQPlaybackAccessException.Reason accessFailure(){return view.status()==Status.ERROR?accessFailure:null;}
    private void haltCurrent() {
        Session s=current;if(s==null)return;s.cancelled=true;
        try{synchronized(s.output){quiet(s,true);}}
        finally{if(s.pending!=null)s.pending.cancel(true);closeInput(s);if(s.worker!=null)s.worker.interrupt();}
        if(current==s)current=null;
    }
    /** 暂停、取消或切源的有界清理；自然 EOF 必须走 finishOutput，不能 flush 原曲尾音。 */
    private void quiet(Session s,boolean close) {
        SourceDataLine line=s.line;if(line==null)return;
        try {
            if(!s.draining&&line.isOpen()&&line.isRunning()&&s.rate>0&&s.submitted>s.baseFrames) {
                int frames=Math.max(1,s.rate/100);byte[] tail=QQPcm.tail(s.last,s.channels,frames);
                int n=line.write(tail,0,tail.length);s.injected+=Math.max(0,n)/(s.channels*2);
                long target=s.submitted-s.baseFrames+s.injected,until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(180);
                while(line.isRunning()&&line.getLongFramePosition()<target&&System.nanoTime()<until)LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
            }
            line.stop();s.pcmClock.freeze();s.position=s.pcmClock.snapshot().seconds();
            line.flush();if(close){line.close();if(line.isOpen())throw new IllegalStateException("QQ 声卡未能关闭");s.line=null;}
            else if(line.isRunning())throw new IllegalStateException("QQ 声卡未能暂停");
        } catch(RuntimeException e){throw new IllegalStateException("QQ 声卡交接失败，未启动另一音源",e);}
    }
    /** 只在解码后台等待真实 PCM 排空；不持有输出锁，让暂停、停止、换曲仍可关闭声卡。 */
    private void finishOutput(Session s)throws Exception {
        synchronized(s.output){
            check(s);s.draining=true;
            if(s.rate>0)s.duration=s.submitted/(double)s.rate;
            LOGGER.info("[QQ音频EOF] PCM 解码结束，等待声卡排空：frames={} rate={} queuedFrames={} duration={}s",
                    s.submitted,s.rate,s.pcmClock.snapshot().queuedFrames(),s.duration);
        }
        while(true){
            await(s);SourceDataLine line;
            synchronized(s.output){check(s);if(s.paused)continue;line=s.line;}
            if(line==null)throw new QQAudioFailure.Output(new IllegalStateException("QQ EOF 缺少音频输出"));
            try{
                line.drain();
                synchronized(s.output){
                    check(s);
                    // 某些驱动在 stop 后提前返回 drain；暂停时不能把未播出的队列判为结束。
                    if(s.paused||!s.permitted.getAsBoolean())continue;
                    if(s.line!=line)throw new IllegalStateException("QQ EOF 声卡会话已改变");
                    if(!line.isOpen())throw new IllegalStateException("QQ EOF 排空期间声卡意外关闭");
                    line.stop();s.pcmClock.freeze();s.position=s.pcmClock.snapshot().seconds();
                    line.close();if(line.isOpen())throw new IllegalStateException("QQ 声卡未能关闭");
                    s.line=null;s.draining=false;return;
                }
            }catch(InterruptedException e){throw e;}
            catch(RuntimeException e){throw new QQAudioFailure.Output(e);}
        }
    }
    private void decode(Session s) {
        boolean completed=false;
        try(InputStream stream=streams.open(s.uri,s)) {
            synchronized(s.resources){if(s.cancelled)throw new InterruptedException();s.input=stream;}
            QQAudioDecoder.Sink sink=new QQAudioDecoder.Sink(){
                public void open(int rate,int channels,long frames) throws Exception {
                    await(s);synchronized(s.output){check(s);s.rate=rate;s.channels=channels;s.last=new short[channels];
                        s.submitted=(long)Math.floor(s.startPosition*rate);
                        if(frames>0)s.submitted=Math.min(s.submitted,Math.max(0,frames-1));
                        s.position=s.submitted/(double)rate;
                        if(frames>0)s.duration=frames/(double)rate;
                        try{openLine(s,rate,channels);
                            if(!s.paused&&s.permitted.getAsBoolean())s.line.start();}
                        catch(InterruptedException e){throw e;}catch(Exception e){throw new QQAudioFailure.Output(e);}}
                    publish(s,s.paused?Status.PAUSED:Status.PLAYING,"");
                }
                public void write(short[] pcm,int frames,int channels) throws Exception {
                    for(int offset=0;offset<frames;) {
                        await(s);int size=Math.min(frames-offset,Math.max(1,s.rate/200));
                        boolean notifyStarted=false;
                        synchronized(s.output) {
                            check(s);if(s.paused||!s.permitted.getAsBoolean())continue;
                            byte[] bytes=s.gain.encode(pcm,offset,size,channels,volume,s.rate);int written=0;
                            s.pcmClock.submitted(size);
                            try{while(written<bytes.length){check(s);int n=s.line.write(bytes,written,bytes.length-written);
                                if(n<=0||n%(channels*2)!=0)throw new IOException("QQ 声卡拒绝 PCM 帧");written+=n;}}
                            catch(InterruptedException e){throw e;}catch(Exception e){throw new QQAudioFailure.Output(e);}
                            s.submitted+=size;
                            s.lastOutput=System.nanoTime();
                            notifyStarted=s.outputStarted.compareAndSet(false,true);
                            for(int c=0;c<channels;c++){int i=bytes.length-(channels-c)*2;s.last[c]=(short)((bytes[i]&255)|(bytes[i+1]<<8));}
                        }
                        // 不持有声卡锁派发客户端回调；旧会话取消后不得把新曲 UI 改为成功。
                        if(notifyStarted&&!s.cancelled&&current==s)s.started.run();
                        offset+=size;
                    }
                }
            };
            QQSampleSkip skipping=new QQSampleSkip(sink,s.startPosition,()->s.cancelled);
            QQAudioDecoder.decode(stream,s.uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".flac"),new QQAudioDecoder.Sink(){
                public void open(int rate,int channels,long frames)throws Exception{skipping.open(rate,channels,frames);}
                public void write(short[] pcm,int frames,int channels)throws Exception{
                    // 跳过阶段也遵守暂停/音源归属，切走后不继续在后台下载整首歌。
                    await(s);skipping.write(pcm,frames,channels);
                }
            });
            finishOutput(s);completed=true;
        } catch(Exception e) {
            completed=false;
            if(!s.cancelled) {
                synchronized(s.output){try{quiet(s,true);}catch(RuntimeException cleanup){e.addSuppressed(cleanup);}}
                org.slf4j.LoggerFactory.getLogger("cloudxmusic").warn("QQ 音频播放失败：networkBytes={} pcmFrames={} rate={} channels={}",
                        s.networkBytes,s.submitted,s.rate,s.channels,QQAudioFailure.safe(e));
                publishFailure(s,e);
            }
        } finally {closeInput(s);}
        // 输入正常关闭、声卡排空后才公开 FINISHED，轮询和回调都不能提前切歌。
        if(completed&&!s.cancelled&&current==s){
            publish(s,Status.FINISHED,"");
            LOGGER.info("[QQ音频EOF] 声卡已排空，发布播放完成：frames={} position={}s duration={}s",s.submitted,s.position,s.duration);
            if(!s.cancelled&&current==s)s.finished.run();
        }
    }
    private void openLine(Session s,int rate,int channels)throws Exception{
        check(s);AudioFormat format=new AudioFormat(rate,16,channels,true,false);
        SourceDataLine line=lines.create(format);s.line=line;s.baseFrames=s.submitted;s.injected=0;s.last=new short[channels];
        line.open(format,Math.max(1024,rate/25*channels*2));
        s.pcmClock.open(line,s.submitted);
    }
    private static void check(Session s) throws InterruptedException {if(s.cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException("QQ 播放已取消");}
    private static void await(Session s) throws InterruptedException {
        while(true){check(s);if(!s.paused&&s.permitted.getAsBoolean())return;LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));}
    }
    private InputStream openHttp(URI start,Session s) throws Exception {
        return http.open(start,()->s.cancelled,pending->s.pending=pending,count->{s.networkBytes+=count;s.lastIo=System.nanoTime();});
    }
    private synchronized void tick() {
        Session s=current;if(s==null)return;
        try {
            View old=view;if(old.status()==Status.PLAYING||old.status()==Status.PAUSED||old.status()==Status.LOADING) {
                publish(s,old.status(),old.error());
                if(!s.draining&&!s.paused&&s.permitted.getAsBoolean()&&System.nanoTime()-Math.max(s.lastIo,s.lastOutput)>TimeUnit.SECONDS.toNanos(25)&&s.timeout.compareAndSet(false,true))
                    command(()->{if(current==s){haltCurrent();unavailable=true;view=new View(Status.ERROR,view.position(),view.duration(),volume,"QQ 音频数据读取超时，请重新点歌");}});
            }
        }catch(RuntimeException ignored) { /* 设备关闭与时钟采样竞争时，下轮重读快照。 */ }
    }
    private synchronized void publish(Session s,Status status,String error) {
        if(current!=s)return;
        if(status!=Status.ERROR){unavailable=false;accessFailure=null;}
        if(s.paused&&(status==Status.PLAYING||status==Status.LOADING))status=Status.PAUSED;
        s.position=s.pcmClock.snapshot().seconds();
        view=new View(status,s.position,s.duration,volume,error);
    }
    private synchronized void publishFailure(Session s,Exception failure){
        if(current!=s)return;
        unavailable=QQAudioFailure.network(failure);
        accessFailure=QQPlaybackAccessException.reason(failure);
        publish(s,Status.ERROR,failure instanceof InterruptedException?"QQ 音频已取消":QQAudioFailure.message(failure));
    }
    private static void closeInput(Session s){synchronized(s.resources){InputStream in=s.input;s.input=null;if(in!=null)try{in.close();}catch(IOException ignored){}}}
    @Override public void close(){if(closed)return;CompletableFuture<Void> done=stop();closed=true;done.whenComplete((v,e)->{clock.shutdownNow();http.close();control.shutdown();});}
    static final class Session {
        final PcmPlaybackClock pcmClock=new PcmPlaybackClock();
        final URI uri;final Object output=new Object(),resources=new Object();final QQPcm.Gain gain=new QQPcm.Gain();final AtomicBoolean timeout=new AtomicBoolean(),outputStarted=new AtomicBoolean();
        volatile BooleanSupplier permitted;volatile boolean cancelled,paused,draining;volatile SourceDataLine line;volatile InputStream input;
        volatile CompletableFuture<?> pending;volatile Thread worker;volatile int rate,channels;volatile long networkBytes,submitted,injected,baseFrames,lastIo=System.nanoTime(),lastOutput=System.nanoTime();
        volatile double duration,position;double startPosition;short[] last;
        Runnable started=()->{},finished=()->{};
        Session(URI uri,int duration,BooleanSupplier permitted){this.uri=uri;this.duration=duration;this.permitted=permitted;}
    }
}
