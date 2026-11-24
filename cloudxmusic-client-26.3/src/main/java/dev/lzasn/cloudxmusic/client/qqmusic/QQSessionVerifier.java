package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.*;

/** 扫码推送的是候选票据，必须经账号接口验证后才允许作为登录态保存。 */
final class QQSessionVerifier {
    private final QQAuthHttp.Transport transport;
    QQSessionVerifier() { this(QQAuthHttp::post); }
    QQSessionVerifier(QQAuthHttp.Transport transport) { this.transport = transport; }
    static final class Rejected extends IOException {
        final long code;
        Rejected(long code) { super(switch ((int) code) {
            case 20279 -> "QQ 音乐登录设备数已达上限，请在手机管理设备后重试";
            case 104604 -> "QQ 音乐登录过于频繁，请稍后重试";
            case 1000, 104400, 104401 -> "QQ 音乐凭证已失效，请重新扫码";
            default -> "QQ 音乐登录验证失败，code=" + code;
        }); this.code = code; }
        boolean expired() { return authExpired(code); }
    }
    private record Reply(long code, JsonObject data) {}
    QQCredentials verify(QQCredentials value) throws IOException, InterruptedException {
        Reply reply = verifyReply(value, null);
        if (reply.code != 0) throw new Rejected(reply.code);
        return withProfile(value, reply.data);
    }
    QQCredentials complete(JsonObject payload, String qrId) throws IOException, InterruptedException {
        JsonObject cookies = QQAuthHttp.object(payload, "cookies");
        QQCredentials original = candidate(QQAuthHttp.string(QQAuthHttp.object(cookies, "qqmusic_uin"), "value"),
                QQAuthHttp.string(QQAuthHttp.object(cookies, "qqmusic_key"), "value"), new JsonObject());
        original=withProfile(original,payload);
        original=new QQCredentials(original.userId(),original.musicKey(),original.loginType(),original.nickname(),original.expiresAtMillis(),original.profile(),original.method(),QQRenewal.from(payload).merge(original.renewal()));
        Reply first = verifyReply(original, null);
        if (first.code != 0 && !authExpired(first.code)) throw new Rejected(first.code);
        JsonObject params = new JsonObject();
        params.addProperty("musicid", Long.parseLong(original.userId())); params.addProperty("qrCodeID", qrId);
        params.addProperty("token", original.musicKey()); params.addProperty("loginType", 6); params.addProperty("needCookie", 1);
        JsonObject request = QQAuthHttp.request("music.login.LoginServer", "Login", params);
        request.getAsJsonObject("comm").addProperty("tmeLoginType", 6);
        // 拒绝过的票据不装入换票请求的 authst 或 Cookie。
        CookieManager exchangedCookies = QQAuthHttp.emptyCookies();
        Reply exchanged;
        try{exchanged=call(request,exchangedCookies);}catch(IOException e){if(first.code==0)return withProfile(original,first.data);throw e;}
        if (exchanged.code == 0) {
            if(first.code==0&&(exchanged.data==null||QQAuthHttp.text(exchanged.data,"musickey").isBlank()))return withProfile(original,first.data);
            if (exchanged.data == null) throw new IOException("QQ 换票响应缺少账号资料");
            String id = QQAuthHttp.text(exchanged.data, "musicid");
            if (id.isEmpty()) id = QQAuthHttp.text(exchanged.data, "str_musicid");
            QQCredentials candidate = candidate(id, QQAuthHttp.string(exchanged.data, "musickey"), exchanged.data);
            QQCredentials converted = new QQCredentials(candidate.userId(),candidate.musicKey(),candidate.loginType(),candidate.nickname(),candidate.expiresAtMillis(),candidate.profile(),candidate.method(),candidate.renewal().merge(original.renewal()));
            if (!converted.userId().equals(original.userId())) throw new IOException("QQ 换票账号与扫码账号不一致");
            Reply confirmed = verifyReply(converted, exchangedCookies);
            if (authExpired(confirmed.code)) { Thread.sleep(300); confirmed = verifyReply(converted, exchangedCookies); }
            if (confirmed.code == 0) return withProfile(converted, confirmed.data);
            throw new Rejected(confirmed.code);
        }
        // 可用的手机票据仍可登录，但只有换票成功并返回 refresh_key 才能续期。
        if(first.code==0)return withProfile(original,first.data);
        if (authExpired(exchanged.code)) {
            Thread.sleep(300);
            return verify(original); // 只读复查一次，不重复执行登录写操作。
        }
        throw new Rejected(exchanged.code);
    }
    QQCredentials renew(QQCredentials value)throws IOException,InterruptedException{
        if(value.cookieLogin()||!value.renewal().available())return value;
        var request=QQAuthHttp.request("music.login.LoginServer","Login",value.renewal().params(value));
        var comm=request.getAsJsonObject("comm");comm.addProperty("uin",value.userId());comm.addProperty("authst",value.musicKey());comm.addProperty("g_tk",hash33(value.musicKey()));comm.addProperty("tmeLoginType",value.loginType());
        var cookies=QQAuthHttp.emptyCookies();installCookies(value,cookies);var reply=call(request,cookies);
        if(reply.code!=0)throw new Rejected(reply.code);if(reply.data==null)throw new IOException("QQ 续期返回空资料");
        String id=QQAuthHttp.text(reply.data,"musicid");if(id.isEmpty())id=QQAuthHttp.text(reply.data,"str_musicid");if(id.isEmpty())id=value.userId();
        if(!id.equals(value.userId()))throw new IOException("QQ 续期账号不一致");
        var data=reply.data.deepCopy();if(!data.has("loginType"))data.addProperty("loginType",value.loginType());
        var candidate=candidate(id,QQAuthHttp.string(data,"musickey"),data);
        var renewed=new QQCredentials(candidate.userId(),candidate.musicKey(),candidate.loginType(),value.nickname(),candidate.expiresAtMillis(),value.profile(),value.method(),candidate.renewal().merge(value.renewal()));
        return verify(renewed);
    }
    private Reply verifyReply(QQCredentials value, CookieManager cookies) throws IOException, InterruptedException {
        JsonObject request = QQAuthHttp.request("music.UserInfo.userInfoServer", "GetLoginUserInfo", new JsonObject());
        JsonObject comm = request.getAsJsonObject("comm");
        comm.addProperty("uin", value.userId()); comm.addProperty("authst", value.musicKey());
        int csrf = hash33(value.musicKey());
        comm.addProperty("g_tk", csrf);
        comm.addProperty("g_tk_new_20200303", csrf);
        comm.addProperty("tmeLoginType", value.loginType());
        if (cookies == null) cookies = QQAuthHttp.emptyCookies();
        installCookies(value,cookies);
        Reply reply = call(request, cookies);
        if (reply.code == 0) {
            if (reply.data == null || reply.data.size() == 0) throw new IOException("QQ 账号验证返回空资料，未接受登录");
            String id = QQAuthHttp.text(reply.data, "musicid");
            if (id.isEmpty()) id = QQAuthHttp.text(reply.data, "str_musicid");
            if (!id.isEmpty() && !id.equals(value.userId())) throw new IOException("QQ 账号验证身份不一致");
        }
        return reply;
    }
    private static void installCookies(QQCredentials value,CookieManager cookies){
        for (String[] pair : new String[][]{{"qqmusic_uin", value.userId()}, {"qqmusic_key", value.musicKey()},
                {"uin",value.userId()},{"qm_keyst",value.musicKey()},
                {"login_type", value.loginType() == 1 ? "2" : "1"}}) {
            HttpCookie cookie = new HttpCookie(pair[0], pair[1]);
            cookie.setDomain(".qq.com"); cookie.setPath("/"); cookie.setSecure(true); cookie.setVersion(0);
            cookies.getCookieStore().add(URI.create("https://u.y.qq.com/"), cookie);
        }
    }
    private Reply call(JsonObject request, CookieManager cookies) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        JsonObject response = transport.post(request, cookies);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        long rootCode = QQAuthHttp.integer(response, "code");
        if (rootCode != 0) return new Reply(rootCode, null);
        JsonObject block = QQAuthHttp.object(response, "req_0");
        long code = QQAuthHttp.integer(block, "code");
        return new Reply(code, block.has("data") && block.get("data").isJsonObject() ? block.getAsJsonObject("data") : null);
    }
    private static QQCredentials candidate(String id, String key, JsonObject data) throws IOException {
        int type = key.startsWith("W_X") ? 1 : 2;
        if (data.has("loginType")) type = Math.toIntExact(QQAuthHttp.integer(data, "loginType"));
        long expires=0;
        try{long created=Long.parseLong(QQAuthHttp.text(data,"musickeyCreateTime")),lifetime=Long.parseLong(QQAuthHttp.text(data,"keyExpiresIn"));long until=Math.multiplyExact(Math.addExact(created,lifetime),1000);if(until>System.currentTimeMillis()&&until-System.currentTimeMillis()<=java.util.concurrent.TimeUnit.DAYS.toMillis(7))expires=until;}catch(NumberFormatException|ArithmeticException ignored){}
        try { return new QQCredentials(id, key, type, "", expires,QQAccountProfile.login(data),dev.lzasn.cloudxmusic.client.gui.login.LoginMethod.QR,QQRenewal.from(data)); }
        catch (IllegalArgumentException e) { throw new IOException("QQ 返回的登录凭证无效"); }
    }
    private static QQCredentials withProfile(QQCredentials value, JsonObject data) {
        String nickname = nickname(data);
        QQAccountProfile profile=QQAccountProfile.login(data);
        if(profile.avatarUrl().isBlank()&&!value.profile().avatarUrl().isBlank())profile=value.profile();
        return new QQCredentials(value.userId(), value.musicKey(), value.loginType(),
                nickname.isEmpty() ? value.nickname() : nickname, value.expiresAtMillis(), profile,value.method(),value.renewal());
    }
    private static String nickname(JsonObject data) {
        if (data == null) return "";
        for (String key : new String[]{"nick", "nickname", "nickName"}) {
            String text = QQAuthHttp.text(data, key);
            if (!text.isBlank()) return text.length() > 256 ? text.substring(0, 256) : text;
        }
        for (String key : new String[]{"info", "userInfo", "userinfo", "base"}) {
            if (!data.has(key) || !data.get(key).isJsonObject()) continue;
            JsonObject child = data.getAsJsonObject(key);
            for (String name : new String[]{"nick", "nickname", "nickName"}) {
                String text = QQAuthHttp.text(child, name);
                if (!text.isBlank()) return text.length() > 256 ? text.substring(0, 256) : text;
            }
            if (child.has("base") && child.get("base").isJsonObject()) {
                String text = nickname(child.getAsJsonObject("base"));
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }
    static int hash33(String key) { int hash = 5381; for (int i = 0; i < key.length(); i++) hash = hash * 33 + key.charAt(i); return hash & 0x7fffffff; }
    static boolean authExpired(long code) { return code == 1000 || code == 104400 || code == 104401; }
}
