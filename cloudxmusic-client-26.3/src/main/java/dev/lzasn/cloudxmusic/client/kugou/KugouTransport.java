package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.JsonObject;
import java.net.CookieManager;
import java.util.Map;

/** 账号 HTTP 边界，注入测试响应；正式实现仅访问固定酷狗 API。 */
@FunctionalInterface
interface KugouTransport {
    JsonObject request(String endpoint,Map<String,String> params,Map<String,String> headers,String body,CookieManager cookies)throws Exception;
}
