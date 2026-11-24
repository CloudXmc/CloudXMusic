/*
 * Ported from MuoniumPlayer-multiversion (Apache-2.0):
 * https://github.com/zihe114514/MuoniumPlayer-multiversion
 * Modified by CloudXMusic project for Minecraft 1.21.11.
 */package com.muoniumplayer.core.utils.other;

import java.util.regex.Pattern;

public class StringUtils {
    public static String removeFormattingCodes(String text) {
        return Pattern.compile("(?i)" + '§' + "[0-9A-FK-OR]").matcher(text).replaceAll("");
    }

    public static String returnEmptyStringIfNull(String input) {
        return input == null ? "" : input;
    }

}