package dev.lzasn.cloudxmusic.client.qqmusic;

/** 无状态 PCM 转换与有界增益斜坡；输出始终为小端 16 bit。 */
final class QQPcm {
    private QQPcm() {}
    static short to16(int sample, int depth) {
        long value = depth > 16 ? ((long)sample >> (depth-16)) : ((long)sample << (16-depth));
        return (short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,value));
    }
    static byte[] tail(short[] last,int channels,int frames) {
        byte[] out = new byte[frames*channels*2];
        for(int f=0;f<frames;f++) for(int c=0;c<channels;c++) {
            int v=(int)Math.round(last[c]*(1.0-f/(double)Math.max(1,frames-1)));
            int i=(f*channels+c)*2;out[i]=(byte)v;out[i+1]=(byte)(v>>8);
        }
        return out;
    }
    static final class Gain {
        private float value;
        private float desired=-1,step;
        private int remaining;
        void reset() { value=0;desired=-1;remaining=0; }
        byte[] encode(short[] samples,int offsetFrames,int frames,int channels,float target,int rate) {
            target=Math.max(0,Math.min(1,target));
            if(target!=desired) {desired=target;remaining=Math.max(1,rate/100);step=(target-value)/remaining;}
            byte[] out=new byte[frames*channels*2];
            for(int f=0;f<frames;f++) {
                if(remaining>0) {value+=step;if(--remaining==0)value=desired;}
                for(int c=0;c<channels;c++) {
                    int v=Math.round(samples[(offsetFrames+f)*channels+c]*value),i=(f*channels+c)*2;
                    out[i]=(byte)v;out[i+1]=(byte)(v>>8);
                }
            }
            return out;
        }
    }
}
