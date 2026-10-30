package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.zip.InflaterInputStream;

/** QRC 格式解码。由 DangoMusic QqLyrics.kt (GPL-3.0) 翻译，保留 QQ 特有 S 盒与位序。 */
public final class QQQrcCodec {
    private static final int LIMIT=2*1024*1024;
    private static final int[] IP={58,50,42,34,26,18,10,2,60,52,44,36,28,20,12,4,62,54,46,38,30,22,14,6,64,56,48,40,32,24,16,8,57,49,41,33,25,17,9,1,59,51,43,35,27,19,11,3,61,53,45,37,29,21,13,5,63,55,47,39,31,23,15,7};
    private static final int[] PC1={57,49,41,33,25,17,9,1,58,50,42,34,26,18,10,2,59,51,43,35,27,19,11,3,60,52,44,36,63,55,47,39,31,23,15,7,62,54,46,38,30,22,14,6,61,53,45,37,29,21,13,5,28,20,12,4};
    private static final int[] PC2={14,17,11,24,1,5,3,28,15,6,21,10,23,19,12,4,26,8,16,7,27,20,13,2,41,52,31,37,47,55,30,40,51,45,33,48,44,49,39,56,34,53,46,42,50,36,29,32};
    private static final int[] SHIFTS={1,1,2,2,2,2,2,2,1,2,2,2,2,2,2,1};
    private static final int[] EXPAND={32,1,2,3,4,5,4,5,6,7,8,9,8,9,10,11,12,13,12,13,14,15,16,17,16,17,18,19,20,21,20,21,22,23,24,25,24,25,26,27,28,29,28,29,30,31,32,1};
    private static final int[] P={16,7,20,21,29,12,28,17,1,15,23,26,5,18,31,10,2,8,24,14,32,27,3,9,19,13,30,6,22,11,4,25};
    private static final int[][] BOXES={
        {14,4,13,1,2,15,11,8,3,10,6,12,5,9,0,7,0,15,7,4,14,2,13,1,10,6,12,11,9,5,3,8,4,1,14,8,13,6,2,11,15,12,9,7,3,10,5,0,15,12,8,2,4,9,1,7,5,11,3,14,10,0,6,13},
        {15,1,8,14,6,11,3,4,9,7,2,13,12,0,5,10,3,13,4,7,15,2,8,15,12,0,1,10,6,9,11,5,0,14,7,11,10,4,13,1,5,8,12,6,9,3,2,15,13,8,10,1,3,15,4,2,11,6,7,12,0,5,14,9},
        {10,0,9,14,6,3,15,5,1,13,12,7,11,4,2,8,13,7,0,9,3,4,6,10,2,8,5,14,12,11,15,1,13,6,4,9,8,15,3,0,11,1,2,12,5,10,14,7,1,10,13,0,6,9,8,7,4,15,14,3,11,5,2,12},
        {7,13,14,3,0,6,9,10,1,2,8,5,11,12,4,15,13,8,11,5,6,15,0,3,4,7,2,12,1,10,14,9,10,6,9,0,12,11,7,13,15,1,3,14,5,2,8,4,3,15,0,6,10,10,13,8,9,4,5,11,12,7,2,14},
        {2,12,4,1,7,10,11,6,8,5,3,15,13,0,14,9,14,11,2,12,4,7,13,1,5,0,15,10,3,9,8,6,4,2,1,11,10,13,7,8,15,9,12,5,6,3,0,14,11,8,12,7,1,14,2,13,6,15,0,9,10,4,5,3},
        {12,1,10,15,9,2,6,8,0,13,3,4,14,7,5,11,10,15,4,2,7,12,9,5,6,1,13,14,0,11,3,8,9,14,15,5,2,8,12,3,7,0,4,10,1,13,11,6,4,3,2,12,9,5,15,10,11,14,1,7,6,0,8,13},
        {4,11,2,14,15,0,8,13,3,12,9,7,5,10,6,1,13,0,11,7,4,9,1,10,14,3,5,12,2,15,8,6,1,4,11,13,12,3,7,14,10,15,6,8,0,5,9,2,6,11,13,8,1,4,10,7,9,5,0,15,14,2,3,12},
        {13,2,8,4,6,15,11,1,10,9,3,14,5,0,12,7,1,15,13,8,10,3,7,4,12,5,6,11,0,14,9,2,7,11,4,1,9,12,14,2,0,6,10,13,15,3,5,8,2,1,14,7,4,10,8,13,15,12,9,0,3,5,6,11}};

    private static final int[] INVERSE=new int[64],ORDER={3,2,1,0,7,6,5,4};
    private static final long[][] SCHEDULES;
    static {
        for(int i=0;i<64;i++)INVERSE[IP[i]-1]=i+1;
        byte[] key="!@#)(*$%123ZXC!@!@#)(NHL".getBytes(StandardCharsets.US_ASCII);
        SCHEDULES=new long[][]{keys(key,16,true),keys(key,8,false),keys(key,0,true)};
    }
    private QQQrcCodec() {}
    private static long permute(long value,int width,int[] table){long result=0;for(int bit:table)result=(result<<1)|((value>>>(width-bit))&1);return result;}
    private static long read(byte[] data,int offset){long result=0;for(int i:ORDER)result=(result<<8)|(data[offset+i]&255L);return result;}
    private static void write(long value,byte[] data,int offset){for(int i=0;i<8;i++)data[offset+ORDER[i]]=(byte)(value>>>(56-i*8));}
    private static long[] keys(byte[] key,int offset,boolean reverse){
        long bits=permute(read(key,offset),64,PC1),left=bits>>>28,right=bits&0xfffffffL;long[] keys=new long[16];
        for(int i=0;i<16;i++){int shift=SHIFTS[i];left=((left<<shift)|(left>>>(28-shift)))&0xfffffffL;right=((right<<shift)|(right>>>(28-shift)))&0xfffffffL;
            keys[reverse?15-i:i]=permute((left<<28)|((right<<1)&0xfffffffL),56,PC2);}
        return keys;
    }
    private static long des(long value,long[] keys){
        long bits=permute(value,64,IP),left=bits>>>32,right=bits&0xffffffffL;
        for(long key:keys){long input=permute(right,32,EXPAND)^key,substituted=0;
            for(int box=0;box<8;box++){int six=(int)((input>>>(42-box*6))&63),row=((six&32)>>>4)|(six&1);substituted=(substituted<<4)|BOXES[box][row*16+((six>>>1)&15)];}
            long next=left^permute(substituted,32,P);left=right;right=next;}
        return permute((right<<32)|left,64,INVERSE);
    }
    public static String plaintext(String value)throws IOException{
        if(value==null||value.length()>LIMIT)throw new IOException("QQ QRC 数据过大");
        String stripped=value.stripLeading();if(stripped.isEmpty()||stripped.startsWith("<")||stripped.startsWith("["))return value;
        byte[] data;try{data=HexFormat.of().parseHex(value);}catch(IllegalArgumentException e){throw new IOException("QQ QRC 不是有效十六进制",e);}
        if(data.length%8==0){byte[] decoded=new byte[data.length];
            for(int at=0;at<data.length;at+=8){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("QQ QRC 已取消");long block=read(data,at);for(long[] schedule:SCHEDULES)block=des(block,schedule);write(block,decoded,at);}
            try{return inflate(decoded);}catch(IOException ignored){/* 纯 zlib 是 QQ 的另一种合法格式。 */}
        }
        return inflate(data);
    }
    private static String inflate(byte[] data)throws IOException{
        try(var input=new InflaterInputStream(new ByteArrayInputStream(data));var output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];for(int count;(count=input.read(buffer))!=-1;){if(output.size()+count>LIMIT)throw new IOException("QQ QRC 解压超过限制");output.write(buffer,0,count);}
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString();
        }
    }
}
