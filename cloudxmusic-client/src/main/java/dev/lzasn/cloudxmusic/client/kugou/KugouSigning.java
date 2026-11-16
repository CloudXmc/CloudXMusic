package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

/** 酷狗公开 Web/App 协议签名；普通版扫码 token 必须交换成 Web 会话。来源及许可见 THIRD_PARTY_NOTICES.md。 */
final class KugouSigning {
    private static final String WEB="NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt",APP="OIlwieks28dk2k092lksi2UIkp";
    private static final String MODULUS="B1B1EC76A1BBDBF0D18E8CD9A87E53FA3881E2F004C67C9DDA2CA677DBEFA3D61DF8463FE12D84FF4B4699E02C9D41CAB917F5A8FB9E35580C4BDF97763A0420A476295D763EE10174E6F9EBF7DF8A77BA5B20CDA4EE705DEF5BBA3C88567B9656E52C9CD5CD95CA735FF2D25F762B133273EEEB7B4F3EA8B6DA29040F3B67CD";
    private KugouSigning(){}
    static String md5(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static String signature(Map<String,String> params,boolean web){String key=web?WEB:APP;StringBuilder text=new StringBuilder(key);new TreeMap<>(params).forEach((k,v)->text.append(k).append('=').append(v));return md5(text.append(key).toString());}
    static String signature(Map<String,String> params,String body,boolean web){String key=web?WEB:APP;StringBuilder text=new StringBuilder(key);new TreeMap<>(params).forEach((k,v)->text.append(k).append('=').append(v));return md5(text.append(body).append(key).toString());}
    static String profileToken(String token,long now)throws GeneralSecurityException{
        String key="MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB";
        var publicKey=(java.security.interfaces.RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(key)));
        var value=new JsonObject();value.addProperty("token",token);value.addProperty("clienttime",now);
        byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8),block=new byte[128];if(bytes.length>block.length)throw new GeneralSecurityException("酷狗会话参数过长");System.arraycopy(bytes,0,block,0,bytes.length);
        String hex=new BigInteger(1,block).modPow(publicKey.getPublicExponent(),publicKey.getModulus()).toString(16);return "0".repeat(256-hex.length())+hex;
    }
    static Map<String,String> web(Map<String,String> values,String device){
        return web(values,device,System.currentTimeMillis());
    }
    static Map<String,String> web(Map<String,String> values,String device,long now){
        Map<String,String> params=new LinkedHashMap<>(Map.of("appid","1014","srcappid","2919","clientver","20000","clienttime",Long.toString(now),"mid",device,"uuid",device,"dfid","-"));
        params.putAll(values);params.put("signature",signature(params,true).toUpperCase(Locale.ROOT));return params;
    }
    static Map<String,String> token(String token,long now)throws GeneralSecurityException {
        String alphabet="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";SecureRandom random=new SecureRandom();StringBuilder key=new StringBuilder();for(int i=0;i<16;i++)key.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return token(token,key.toString(),now);
    }
    static Map<String,String> token(String token,String key,long now)throws GeneralSecurityException {
        if(!key.matches("[A-Z0-9]{16}"))throw new IllegalArgumentException("酷狗登录密钥格式无效");
        String digest=md5(key);Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(digest.getBytes(StandardCharsets.US_ASCII),"AES"),new IvParameterSpec(digest.substring(16).getBytes(StandardCharsets.US_ASCII)));
        JsonObject payload=new JsonObject();payload.addProperty("token",token);String encrypted=HexFormat.of().formatHex(cipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8)));
        JsonObject metadata=new JsonObject();metadata.addProperty("clienttime_ms",now);metadata.addProperty("key",key);
        byte[] text=metadata.toString().getBytes(StandardCharsets.US_ASCII),block=new byte[128];if(text.length>=128)throw new IllegalArgumentException("酷狗登录参数过长");System.arraycopy(text,0,block,0,text.length);
        String rsa=new BigInteger(1,block).modPow(BigInteger.valueOf(65537),new BigInteger(MODULUS,16)).toString(16);
        rsa="0".repeat((4-rsa.length()%4)%4)+rsa;
        return Map.of("pk",rsa,"params",encrypted);
    }
}
