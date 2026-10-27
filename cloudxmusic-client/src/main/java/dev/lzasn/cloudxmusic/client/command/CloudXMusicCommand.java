package dev.lzasn.cloudxmusic.client.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.gui.QrLoginScreen;
import dev.lzasn.cloudxmusic.client.netease.NeteaseApi;
import dev.lzasn.cloudxmusic.client.netease.NeteaseQuality;
import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import dev.lzasn.cloudxmusic.client.netease.QrLogin;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.client.util.Async;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * {@code /cxm ...} 客户端命令。
 *
 * <p>登录三条路：</p>
 * <ul>
 *   <li>{@code /cxm login qr} —— 游戏里弹出二维码，手机 App 直接扫</li>
 *   <li>{@code /cxm login phone <手机号>} —— 发短信验证码，再 {@code /cxm login code <验证码>}</li>
 *   <li>{@code /cxm login phone <手机号> <密码>} —— 密码直登</li>
 * </ul>
 */
public final class CloudXMusicCommand {
    /** 只填手机号时先存着，等用户拿验证码回来。 */
    private static String pendingPhone = "";

    private CloudXMusicCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(ClientCommandManager.literal("cxm")
                        .then(ClientCommandManager.literal("login")
                                .executes(c -> {
                                    loginHelp(c.getSource());
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("qr").executes(c -> {
                                    msg(c.getSource(), "正在打开扫码界面…");
                                    Minecraft minecraft = Minecraft.getInstance();
                                    // 关键：命令是在聊天框里跑的，命令返回后 ChatScreen 会 setScreen(null)。
                                    // 直接在这里 setScreen 会被立刻覆盖，必须丢到下一 tick。
                                    if (minecraft != null) {
                                        minecraft.execute(() -> minecraft.setScreen(new QrLoginScreen()));
                                    }
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("phone")
                                        .then(ClientCommandManager.argument("phone", StringArgumentType.word())
                                                // 只给手机号：发验证码
                                                .executes(c -> sendCaptcha(c.getSource(),
                                                        StringArgumentType.getString(c, "phone")))
                                                // 手机号 + 密码
                                                .then(ClientCommandManager.argument("password", StringArgumentType.greedyString())
                                                        .executes(c -> phoneLogin(c.getSource(),
                                                                StringArgumentType.getString(c, "phone"),
                                                                StringArgumentType.getString(c, "password"))))))
                                // 用上一步记住的手机号 + 验证码登录
                                .then(ClientCommandManager.literal("code")
                                        .then(ClientCommandManager.argument("code", StringArgumentType.word())
                                                .executes(c -> captchaLogin(c.getSource(), pendingPhone,
                                                        StringArgumentType.getString(c, "code")))))
                                .then(ClientCommandManager.literal("captcha")
                                        .then(ClientCommandManager.argument("phone", StringArgumentType.word())
                                                .executes(c -> sendCaptcha(c.getSource(),
                                                        StringArgumentType.getString(c, "phone")))
                                                .then(ClientCommandManager.argument("code", StringArgumentType.word())
                                                        .executes(c -> captchaLogin(c.getSource(),
                                                                StringArgumentType.getString(c, "phone"),
                                                                StringArgumentType.getString(c, "code"))))))
                                .then(ClientCommandManager.literal("email")
                                        .then(ClientCommandManager.argument("email", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("password", StringArgumentType.greedyString())
                                                        .executes(c -> emailLogin(c.getSource(),
                                                                StringArgumentType.getString(c, "email"),
                                                                StringArgumentType.getString(c, "password"))))))
                                .then(ClientCommandManager.literal("cancel").executes(c -> {
                                    QrLogin.cancel();
                                    msg(c.getSource(), "已取消扫码等待");
                                    return 1;
                                }))
                                .then(ClientCommandManager.literal("status").executes(c -> status(c.getSource()))))
                        .then(ClientCommandManager.literal("logout").executes(c -> {
                            if (!NeteaseSession.INSTANCE.loggedIn()) {
                                msg(c.getSource(), "还没登录，无需退出");
                                return 0;
                            }
                            NeteaseSession.INSTANCE.logout();
                            CloudXMusicClient.STATE.stopAll();
                            msg(c.getSource(), "已退出登录，播放已停止");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("refresh").executes(c -> {
                            CloudXMusicClient.STATE.refreshLibrary();
                            msg(c.getSource(), "正在拉取每日推荐…");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("playlist")
                                .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                        .executes(c -> {
                                            String id = StringArgumentType.getString(c, "id");
                                            CloudXMusicClient.STATE.loadPlaylist(id);
                                            msg(c.getSource(), "正在拉取歌单 " + id);
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("search")
                                .then(ClientCommandManager.argument("keyword", StringArgumentType.greedyString())
                                        .executes(c -> {
                                            String keyword = StringArgumentType.getString(c, "keyword");
                                            CloudXMusicClient.STATE.search(keyword);
                                            msg(c.getSource(), "正在搜索「" + keyword + "」，结果显示在 GUI 的搜索页");
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("fav").executes(c -> {
                            CloudXMusicClient.STATE.loadFavorites();
                            msg(c.getSource(), "正在拉取我喜欢的音乐…");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("play")
                                .then(ClientCommandManager.argument("keyword", StringArgumentType.greedyString())
                                        .executes(c -> {
                                            String keyword = StringArgumentType.getString(c, "keyword");
                                            CloudXMusicClient.STATE.searchAndPlay(keyword);
                                            msg(c.getSource(), "正在搜索「" + keyword + "」");
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("quality")
                                .executes(c -> {
                                    setQuality(c.getSource(), NeteaseSession.INSTANCE.quality().next());
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("value", StringArgumentType.word())
                                        .executes(c -> {
                                            setQuality(c.getSource(),
                                                    NeteaseQuality.of(StringArgumentType.getString(c, "value")));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("lyric")
                                .executes(c -> {
                                    LyricMode next = NeteaseSession.INSTANCE.lyricMode().next();
                                    NeteaseSession.INSTANCE.lyricMode(next);
                                    msg(c.getSource(), "灵动岛歌词：" + next.label);
                                    return 1;
                                })
                                .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                                        .executes(c -> {
                                            LyricMode mode = switch (StringArgumentType.getString(c, "mode").toLowerCase()) {
                                                case "off", "0" -> LyricMode.OFF;
                                                case "original", "one", "1" -> LyricMode.ORIGINAL;
                                                default -> LyricMode.BILINGUAL;
                                            };
                                            NeteaseSession.INSTANCE.lyricMode(mode);
                                            msg(c.getSource(), "灵动岛歌词：" + mode.label);
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("volume")
                                .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(c -> {
                                            int value = IntegerArgumentType.getInteger(c, "value");
                                            CloudXMusicClient.STATE.volume(value / 100f);
                                            msg(c.getSource(), "音量：" + value + "%");
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("stop").executes(c -> {
                            CloudXMusicClient.STATE.stopAll();
                            msg(c.getSource(), "已停止播放");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("pause").executes(c -> {
                            CloudXMusicClient.STATE.togglePlaying();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("next").executes(c -> {
                            CloudXMusicClient.STATE.next();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("prev").executes(c -> {
                            CloudXMusicClient.STATE.previous();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("test").executes(c -> {
                            // 自检曲目：免费、每天都能取到可用直链（2652820720）
                            CloudXMusicClient.STATE.select(new Track("自检音轨", "CloudXMusic", 279,
                                    "2652820720", "", ""));
                            msg(c.getSource(), "正在播放自检曲目（免费曲，用来区分是播放器问题还是这首歌放不了）");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("status").executes(c -> status(c.getSource())))
                ));
    }

    // ------------------------------------------------------------ 登录

    private static void loginHelp(FabricClientCommandSource source) {
        msg(source, "扫码：/cxm login qr（游戏里弹出二维码）");
        msg(source, "验证码：/cxm login phone <手机号> → /cxm login code <验证码>");
        msg(source, "密码：/cxm login phone <手机号> <密码>");
        msg(source, "邮箱：/cxm login email <邮箱> <密码>");
    }

    private static int sendCaptcha(FabricClientCommandSource source, String phone) {
        pendingPhone = phone;
        msg(source, "正在给 " + phone + " 发验证码…");
        Async.run(() -> {
            try {
                NeteaseApi.sendLoginCaptcha(phone, "86");
                Async.main(() -> {
                    msg(source, "验证码已发送，收到后执行：/cxm login code <验证码>");
                    msg(source, "（手机号要 11 位、不带 +86；没收到就等 60 秒再试）");
                });
            } catch (Exception e) {
                Async.main(() -> msg(source, "发送失败：" + e.getMessage()));
            }
        });
        return 1;
    }

    private static int captchaLogin(FabricClientCommandSource source, String phone, String code) {
        if (phone == null || phone.isBlank()) {
            msg(source, "没有记录手机号，请用：/cxm login captcha <手机号> <验证码>");
            return 0;
        }
        msg(source, "正在用验证码登录 " + phone + "…");
        Async.run(() -> {
            try {
                NeteaseApi.LoginResult result = NeteaseApi.cellphoneCaptchaLogin(phone, code, "86");
                NeteaseSession.INSTANCE.applyLogin(result);
                Async.main(() -> reportLogin(source, result));
            } catch (Exception e) {
                Async.main(() -> msg(source, "登录失败：" + e.getMessage()));
            }
        });
        return 1;
    }

    private static int phoneLogin(FabricClientCommandSource source, String phone, String password) {
        msg(source, "正在登录…");
        Async.run(() -> {
            try {
                NeteaseApi.LoginResult result = NeteaseApi.cellphoneLogin(phone, password, "86");
                NeteaseSession.INSTANCE.applyLogin(result);
                Async.main(() -> {
                    reportLogin(source, result);
                    if (!result.ok() && result.code() == 502) {
                        pendingPhone = phone;
                        msg(source, "可以改用验证码：/cxm login phone " + phone + " 然后 /cxm login code <验证码>");
                    }
                });
            } catch (Exception e) {
                Async.main(() -> msg(source, "登录失败：" + e.getMessage()));
            }
        });
        return 1;
    }

    private static int emailLogin(FabricClientCommandSource source, String email, String password) {
        msg(source, "正在登录…");
        Async.run(() -> {
            try {
                NeteaseApi.LoginResult result = NeteaseApi.emailPasswordLogin(email, password);
                NeteaseSession.INSTANCE.applyLogin(result);
                Async.main(() -> reportLogin(source, result));
            } catch (Exception e) {
                Async.main(() -> msg(source, "登录失败：" + e.getMessage()));
            }
        });
        return 1;
    }

    private static void reportLogin(FabricClientCommandSource source, NeteaseApi.LoginResult result) {
        if (result.ok() && NeteaseSession.INSTANCE.loggedIn()) {
            msg(source, "登录成功：" + result.nickname());
            CloudXMusicClient.STATE.refreshLibrary();
        } else if (result.ok()) {
            msg(source, "登录接口返回成功，但没有收到 MUSIC_U；请重试登录");
        } else {
            msg(source, "登录失败（code " + result.code() + "）：" + result.message());
            if (NeteaseSession.INSTANCE.loggedIn()) {
                msg(source, "现有登录态仍然有效，已保留，不会清空本地 Cookie");
            }
            if (result.raw() != null && !result.raw().isEmpty()) {
                CloudXMusicClient.LOGGER.warn("登录失败原始响应：{}", result.raw());
            }
        }
    }

    // ------------------------------------------------------------ 其它

    private static void setQuality(FabricClientCommandSource source, NeteaseQuality quality) {
        NeteaseSession.INSTANCE.quality(quality);
        msg(source, "音质：" + quality.label);
        CloudXMusicClient.STATE.applyQuality();
    }

    private static int status(FabricClientCommandSource source) {
        NeteaseSession session = NeteaseSession.INSTANCE;
        MusicState state = CloudXMusicClient.STATE;
        if (session.loggedIn()) {
            msg(source, "已登录：" + session.nickname() + "（uid " + session.uid() + "）");
        } else {
            msg(source, "未登录");
        }
        msg(source, "音质 " + session.quality().label + " ｜ 歌词 " + session.lyricMode().label
                + " ｜ 音量 " + Math.round(state.volume() * 100) + "%");
        msg(source, "当前：" + state.current().title() + " - " + state.current().artist());
        if (!state.statusLine().isEmpty()) msg(source, state.statusLine());
        return 1;
    }

    private static void msg(FabricClientCommandSource source, String text) {
        source.sendFeedback(Component.literal("[CloudXMusic] " + text));
    }
}
