package dev.lzasn.cloudxmusic.client.gui.login;

import java.util.*;

/** 只解析请求 Cookie，不接受响应属性、重复冲突或多行 HTTP 头；错误不回显凭证。 */
public final class LoginCookies {
    public static final int MAX_LENGTH=32768;
    private LoginCookies(){}
    public static Map<String,String> parse(String raw){
        if(raw==null||raw.isBlank()||raw.length()>MAX_LENGTH||raw.chars().anyMatch(c->c<32||c==127))throw new IllegalArgumentException("Cookie 格式无效，请复制完整的单行 Cookie");
        String text=raw.strip();if(text.regionMatches(true,0,"Cookie:",0,7))text=text.substring(7).strip();
        var values=new LinkedHashMap<String,String>();
        for(String part:text.split(";")){
            if(part.isBlank())continue;int separator=part.indexOf('=');if(separator<1)throw new IllegalArgumentException("Cookie 格式无效，请重新复制");
            String name=part.substring(0,separator).strip(),value=part.substring(separator+1).strip();
            if(!name.matches("[A-Za-z0-9_!#$%&'*+.^`|~-]{1,128}")||value.length()>8192)throw new IllegalArgumentException("Cookie 字段格式无效");
            if(value.length()>=2&&value.startsWith("\"")&&value.endsWith("\""))value=value.substring(1,value.length()-1);
            if(value.chars().anyMatch(c->c<33||c>126||c=='\"'||c=='\\'))throw new IllegalArgumentException("Cookie 字段格式无效");
            String old=values.putIfAbsent(name,value);if(old!=null&&!old.equals(value))throw new IllegalArgumentException("Cookie 中存在冲突字段，请重新复制");
            if(values.size()>128)throw new IllegalArgumentException("Cookie 字段过多");
        }
        return Collections.unmodifiableMap(values);
    }
    public static String required(Map<String,String> values,String name){String value=values.getOrDefault(name,"");if(value.isBlank())throw new IllegalArgumentException("Cookie 缺少 "+name+"，请复制完整 Cookie");return value;}
}
