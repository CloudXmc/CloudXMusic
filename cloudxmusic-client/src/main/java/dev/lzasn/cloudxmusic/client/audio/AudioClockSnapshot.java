package dev.lzasn.cloudxmusic.client.audio;

/** 已消费的 PCM 帧快照；队列帧只是诊断值，不能从 positionFrames 再扣一次。 */
public record AudioClockSnapshot(long epoch,long positionFrames,int sampleRate,long queuedFrames,boolean running) {
    public double seconds(){return sampleRate>0?positionFrames/(double)sampleRate:0;}
    public long milliseconds(){return sampleRate>0?positionFrames*1000L/sampleRate:0;}
}
