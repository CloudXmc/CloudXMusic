package dev.lzasn.cloudxmusic.client.netease;

import com.google.gson.Gson;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 网易云 weapi / eapi 加密。
 *
 * <p>weapi：先用固定密钥做一次 AES-CBC（PKCS5 + 固定 IV），再用随机 16 位密钥做第二次，
 * 随机密钥经 RSA(NoPadding) 加密成 encSecKey。eapi：整段明文套上
 * {@code path-36cd479b6b5-json-36cd479b6b5-md5} 后再 AES-ECB 转 hex。</p>
 *
 * <p>参数顺序、常量与社区实现一致，改错任何一个都会拿到 {@code code:-460}（"网络太拥挤"）。</p>
 */
public final class NeteaseCrypto {
    private static final Gson GSON = new Gson();

    private static final String IV = "0102030405060708";
    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String EAPI_KEY = "e82ckenh8dichen8";
    private static final String PUBLIC_KEY =
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdt"
                    + "LkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44"
                    + "oncaTWz7OBGLbCiK45wIDAQAB";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private NeteaseCrypto() {}

    /** weapi 表单体：{@code params=...&encSecKey=...}。 */
    public static String weapiForm(Map<String, Object> data) {
        String secretKey = randomBase62(16);
        String params = aesCbcBase64(aesCbcBase64(GSON.toJson(data), PRESET_KEY), secretKey);
        // 客户端把随机密钥倒序后再做 RSA，服务端会再倒回来
        String encSecKey = rsaHex(new StringBuilder(secretKey).reverse().toString());
        Map<String, String> form = new LinkedHashMap<>();
        form.put("params", params);
        form.put("encSecKey", encSecKey);
        return encode(form);
    }

    /** eapi 表单体：{@code params=<hex>}。 */
    public static String eapiForm(String path, Map<String, Object> data) {
        String text = GSON.toJson(data);
        String digest = md5("nobody" + path + "use" + text + "md5forencrypt");
        String payload = path + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("params", aesEcbHex(payload));
        return encode(form);
    }

    /** 登录接口的密码字段是明文的 MD5。 */
    public static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    private static String aesCbcBase64(String text, String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key(key), new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
            return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("weapi 加密失败", e);
        }
    }

    private static String aesEcbHex(String text) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key(EAPI_KEY));
            return hex(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("eapi 加密失败", e);
        }
    }

    private static String rsaHex(String text) {
        try {
            byte[] plain = new byte[128];
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(bytes, 0, plain, plain.length - bytes.length, bytes.length);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return hex(cipher.doFinal(plain));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA 加密失败", e);
        }
    }

    private static SecretKeySpec key(String value) {
        return new SecretKeySpec(value.getBytes(StandardCharsets.UTF_8), "AES");
    }

    private static String randomBase62(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        return sb.toString();
    }

    private static String encode(Map<String, String> values) {
        StringJoiner joiner = new StringJoiner("&");
        values.forEach((k, v) -> joiner.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return joiner.toString();
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
