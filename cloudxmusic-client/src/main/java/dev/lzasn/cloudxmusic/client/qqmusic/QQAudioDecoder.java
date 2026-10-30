package dev.lzasn.cloudxmusic.client.qqmusic;

import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.AbstractFlacLowLevelInput;
import io.nayuki.flac.decode.FrameDecoder;
import io.nayuki.flac.common.FrameInfo;
import javazoom.jl.decoder.*;
import java.io.*;
import java.security.*;
import java.util.Arrays;

/** 只解码，不访问账号或界面；所有输出通过 Sink，便于无声卡测试。 */
final class QQAudioDecoder {
    interface Sink {
        void open(int rate,int channels,long frames) throws Exception;
        void write(short[] samples,int frames,int channels) throws Exception;
    }
    private QQAudioDecoder() {}
    static void decode(InputStream source,boolean flac,Sink sink) throws IOException,InterruptedException {
        try { if(flac) flac(source,sink); else mp3(source,sink); }
        catch(InterruptedException e) { throw e; }
        catch(IOException e) { throw e; }
        catch(Exception e) { throw new IOException("QQ 音频解码或输出失败",e); }
    }
    private static void flac(InputStream source,Sink sink) throws Exception {
        try(StreamInput input=new StreamInput(source)) {
            if(input.readUint(32)!=0x664C6143) throw new IOException("响应不是原生 FLAC");
            StreamInfo info=null;int metadata=0;
            for(int blocks=0;blocks<128;blocks++) {
                boolean last=input.readUint(1)!=0;int type=input.readUint(7),length=input.readUint(24);
                if((metadata+=length+4)>2*1024*1024 || type==127 || (blocks==0&&(type!=0||length!=34)) || (blocks>0&&type==0))
                    throw new IOException("FLAC 元数据无效或超过上限");
                byte[] data=new byte[length];input.readFully(data);
                if(type==0) {info=new StreamInfo(data);info.checkValues();}
                if(last) break;
                if(blocks==127) throw new IOException("FLAC 元数据块数量超限");
            }
            if(info==null||info.numChannels<1||info.numChannels>2||info.sampleDepth<8||info.sampleDepth>32||info.sampleRate<8000||info.sampleRate>192000)
                throw new IOException("暂不支持该 FLAC 采样格式（支持 8–32 bit / 8–192 kHz / 单双声道）");
            sink.open(info.sampleRate,info.numChannels,info.numSamples);
            int[][] planes=new int[info.numChannels][65536];short[] pcm=new short[65536*info.numChannels];
            FrameDecoder decoder=new FrameDecoder(input,info.sampleDepth);long count=0;MessageDigest md5=MessageDigest.getInstance("MD5");
            // 已知样本数即为音频终点；CDN 保持连接时无需再等待一次网络 EOF。
            while(info.numSamples==0||count<info.numSamples) {
                interrupted();FrameInfo frame=decoder.readFrame(planes,0);if(frame==null) break;info.checkFrame(frame);
                count+=frame.blockSize;
                if(info.numSamples!=0&&count>info.numSamples) throw new IOException("FLAC 样本数超过声明");
                byte[] original=new byte[frame.blockSize*info.numChannels*((info.sampleDepth+7)/8)];int p=0;
                for(int f=0;f<frame.blockSize;f++) for(int c=0;c<info.numChannels;c++) {
                    int sample=planes[c][f];pcm[f*info.numChannels+c]=QQPcm.to16(sample,info.sampleDepth);
                    for(int b=0;b<(info.sampleDepth+7)/8;b++) original[p++]=(byte)(sample>>(b*8));
                }
                md5.update(original);sink.write(pcm,frame.blockSize,info.numChannels);
            }
            if(count==0||(info.numSamples!=0&&count!=info.numSamples)) throw new IOException("FLAC 为空或提前结束");
            if(!Arrays.equals(info.md5Hash,new byte[16])&&!MessageDigest.isEqual(info.md5Hash,md5.digest())) throw new IOException("FLAC PCM 完整性校验失败");
        }
    }
    private static void mp3(InputStream source,Sink sink) throws Exception {
        BufferedInputStream buffered=new BufferedInputStream(source,64*1024);buffered.mark(16);byte[] prefix=buffered.readNBytes(10);buffered.reset();
        if(!QQAudioUrlVerifier.audioHeader(prefix,false))throw new IOException("响应不是 MP3 音频");
        if(prefix[0]=='I'&&prefix[1]=='D'&&prefix[2]=='3') {
            if(prefix.length<10)throw new IOException("MP3 ID3 元数据不完整");
            int size=0;for(int i=6;i<10;i++){if((prefix[i]&128)!=0)throw new IOException("MP3 ID3 长度无效");size=(size<<7)|(prefix[i]&127);}
            if(size>2*1024*1024)throw new IOException("MP3 ID3 元数据超过 2 MiB");
        }
        Bitstream bits=new Bitstream(buffered);Decoder decoder=new Decoder();
        int rate=0,channels=0;long frames=0;
        try {
            Header header;
            while((header=bits.readFrame())!=null) {
                interrupted();SampleBuffer buffer=(SampleBuffer)decoder.decodeFrame(header,bits);bits.closeFrame();
                int nextRate=buffer.getSampleFrequency(),nextChannels=buffer.getChannelCount();
                if(rate==0) {rate=nextRate;channels=nextChannels;if(channels<1||channels>2) throw new IOException("MP3 声道数不受支持");sink.open(rate,channels,0);}
                else if(rate!=nextRate||channels!=nextChannels) throw new IOException("MP3 流中途改变采样格式");
                int size=buffer.getBufferLength()/channels;sink.write(buffer.getBuffer(),size,channels);frames+=size;
            }
            if(frames==0) throw new IOException("MP3 为空或没有可解码帧");
        } finally { bits.close(); }
    }
    private static void interrupted() throws InterruptedException {if(Thread.currentThread().isInterrupted()) throw new InterruptedException("QQ 解码已取消");}
    static final class StreamInput extends AbstractFlacLowLevelInput {
        private final InputStream source;
        StreamInput(InputStream source) {this.source=source;}
        public long getLength(){return -1;}
        public void seekTo(long position){throw new UnsupportedOperationException("顺序 FLAC 流不使用字节估算跳转");}
        protected int readUnderlying(byte[] b,int off,int len) throws IOException {
            if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("QQ FLAC 已取消");
            return source.read(b,off,len);
        }
        public void close() throws IOException {try{source.close();}finally{super.close();}}
    }
}
