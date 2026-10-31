package dev.lzasn.cloudxmusic.client.audio;

import javax.sound.sampled.SourceDataLine;

/** 声卡消费帧是唯一时钟；锁只保护计数，不持锁执行 write/drain 或网络操作。 */
public final class PcmPlaybackClock {
    private SourceDataLine line;
    private long epoch,baseFrames,origin,submitted,played;
    private int rate;
    public synchronized void reset(double seconds){
        line=null;epoch++;rate=1_000_000;baseFrames=(long)(Math.max(0,seconds)*rate);origin=submitted=played=0;
    }
    public synchronized void open(SourceDataLine output,long base){
        line=output;epoch++;rate=(int)output.getFormat().getSampleRate();baseFrames=Math.max(0,base);
        origin=output.getLongFramePosition();submitted=played=0;
    }
    /** 在阻塞 write 前登记本批样本，声卡消费进度才不会受解码线程回调频率限制。 */
    public synchronized void submitted(long frames){if(frames>0)submitted+=frames;}
    public synchronized AudioClockSnapshot snapshot(){
        boolean running=false;
        if(line!=null){
            try{played=Math.max(played,Math.min(submitted,Math.max(0,line.getLongFramePosition()-origin)));running=line.isRunning()&&played<submitted;}
            catch(RuntimeException ignored){/* 声卡关闭竞态保留最后确认的播放帧。 */}
        }
        return new AudioClockSnapshot(epoch,baseFrames+played,rate,Math.max(0,submitted-played),running);
    }
    public synchronized void freeze(){snapshot();line=null;}
}
