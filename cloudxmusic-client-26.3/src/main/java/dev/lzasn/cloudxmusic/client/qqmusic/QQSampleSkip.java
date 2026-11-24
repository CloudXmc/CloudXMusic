package dev.lzasn.cloudxmusic.client.qqmusic;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** 从头解码但按样本帧丢弃旧进度；不估算压缩字节偏移、不打开声卡播放被跳过的 PCM。 */
final class QQSampleSkip implements QQAudioDecoder.Sink {
    private final QQAudioDecoder.Sink output;
    private final double seconds;
    private final BooleanSupplier cancelled;
    private long remaining,total;
    private int rate,channels;
    private boolean opened;
    QQSampleSkip(QQAudioDecoder.Sink output,double seconds,BooleanSupplier cancelled){this.output=output;this.seconds=Math.max(0,seconds);this.cancelled=cancelled;}
    public void open(int rate,int channels,long total)throws Exception{
        check();this.rate=rate;this.channels=channels;this.total=total;remaining=(long)Math.floor(seconds*rate);
        if(total>0&&remaining>=total)remaining=Math.max(0,total-1);
        if(remaining==0)openOutput();
    }
    public void write(short[] samples,int frames,int channels)throws Exception{
        check();int skip=(int)Math.min(remaining,frames);remaining-=skip;
        if(skip==frames)return;
        if(!opened)openOutput();
        output.write(skip==0?samples:Arrays.copyOfRange(samples,skip*channels,frames*channels),frames-skip,channels);
    }
    private void openOutput()throws Exception{check();output.open(rate,channels,total);opened=true;}
    private void check()throws InterruptedException{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedException("QQ 进度恢复已取消");}
}
