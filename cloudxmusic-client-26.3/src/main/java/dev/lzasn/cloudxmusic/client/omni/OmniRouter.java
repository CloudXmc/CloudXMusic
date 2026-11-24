package dev.lzasn.cloudxmusic.client.omni;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 只负责 Provider 注册和查找，不主动创建音源或读取登录态。 */
public final class OmniRouter {
    private static final ConcurrentMap<String, OnlineMusicProvider> PROVIDERS = new ConcurrentHashMap<>();

    private OmniRouter() {}

    public static void register(OnlineMusicProvider provider) {
        Objects.requireNonNull(provider, "provider");
        String source = Objects.requireNonNull(provider.sourceId(), "sourceId");
        if (source.isBlank()) throw new IllegalArgumentException("sourceId must not be blank");
        // 原子注册，避免重复音源悄悄覆盖已有实现。
        if (PROVIDERS.putIfAbsent(source, provider) != null) {
            throw new IllegalArgumentException("Provider already registered: " + source);
        }
    }

    public static OnlineMusicProvider getProvider(String sourceId) {
        OnlineMusicProvider provider = PROVIDERS.get(Objects.requireNonNull(sourceId, "sourceId"));
        if (provider == null) throw new IllegalArgumentException("Unknown music source: " + sourceId);
        return provider;
    }
}
