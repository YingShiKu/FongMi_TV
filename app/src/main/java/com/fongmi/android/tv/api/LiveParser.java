package com.fongmi.android.tv.api;

import android.util.Base64;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiveParser {

    private static final Pattern GROUP = Pattern.compile(".*group-title=\"(.?|.+?)\".*");
    private static final Pattern LOGO = Pattern.compile(".*tvg-logo=\"(.?|.+?)\".*");
    private static final Pattern NAME = Pattern.compile(".*,(.+?)$");

    private static String extract(String line, Pattern pattern) {
        Matcher matcher = pattern.matcher(line.trim());
        if (matcher.matches()) return matcher.group(1);
        return "";
    }

    public static void start(Live live) {
        if (live.getGroups().size() > 0) return;
        if (live.getType() == 0) text(live, getText(live.getUrl()));
        if (live.getType() == 1) json(live, getText(live.getUrl()));
        if (live.getType() == 2) proxy(live, getText(UrlUtil.convert(live.getUrl())));
    }

    public static void text(Live live, String text) {
        int number = 0;
        if (live.getGroups().size() > 0) return;
        if (text.trim().startsWith("#EXTM3U")) m3u(live, text);
        else txt(live, text);
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                channel.setNumber(++number);
                channel.live(live);
            }
        }
    }

    private static void json(Live live, String text) {
        live.getGroups().addAll(Group.arrayFrom(text));
        for (Group group : live.getGroups()) {
            for (Channel channel : group.getChannel()) {
                channel.live(live);
            }
        }
    }

    private static void m3u(Live live, String text) {
        Setting setting = Setting.create();
        Channel channel = Channel.create("");
        for (String line : text.split("\n")) {
            if (Thread.interrupted()) break;
            if (setting.find(line)) {
                setting.check(line);
            } else if (line.startsWith("#EXTINF:")) {
                Group group = live.find(Group.create(extract(line, GROUP)));
                channel = group.find(Channel.create(extract(line, NAME)));
                channel.setLogo(extract(line, LOGO));
            } else if (!line.startsWith("#") && line.contains("://")) {
                String[] split = line.split("\\|");
                for (String s : split) setting.check(s);
                channel.getUrls().add(split[0]);
                setting.copy(channel).clear();
            }
        }
    }

    private static void txt(Live live, String text) {
        Setting setting = Setting.create();
        for (String line : text.split("\n")) {
            setting.check(line);
            String[] split = line.split(",");
            if (split.length < 2) continue;
            if (Thread.interrupted()) break;
            if (line.contains("#genre#")) live.getGroups().add(Group.create(split[0], live.isPass()));
            if (live.getGroups().isEmpty()) live.getGroups().add(Group.create(R.string.live_group));
            if (split[1].contains("://")) {
                Group group = live.getGroups().get(live.getGroups().size() - 1);
                Channel channel = group.find(Channel.create(split[0]));
                channel.addUrls(split[1].split("#"));
                setting.copy(channel);
            }
        }
    }

    private static void proxy(Live live, String text) {
        int number = 0;
        for (Live item : Live.arrayFrom(text)) {
            Group group = live.find(Group.create(item.getGroup()));
            for (Channel channel : item.getChannels()) {
                channel.setNumber(++number);
                channel.live(live);
                group.add(channel);
            }
        }
    }

    private static String getText(String url) {
        if (url.startsWith("file")) return Path.read(url);
        if (url.startsWith("http")) return OkHttp.string(url);
        if (url.endsWith(".txt") || url.endsWith(".m3u")) return getText(UrlUtil.convert(LiveConfig.getUrl(), url));
        if (url.length() > 0 && url.length() % 4 == 0) return getText(new String(Base64.decode(url, Base64.DEFAULT)));
        return "";
    }

    private static class Setting {

        private String ua;
        private String key;
        private String type;
        private String click;
        private String referer;
        private Integer parse;
        private Integer player;

        public static Setting create() {
            return new Setting();
        }

        public boolean find(String line) {
            return line.startsWith("ua") || line.startsWith("parse") || line.startsWith("click") || line.startsWith("player") || line.startsWith("referer") || line.startsWith("#EXTVLCOPT:") || line.startsWith("#KODIPROP:");
        }

        public void check(String line) {
            if (line.contains("#genre#")) clear();
            else if (line.startsWith("ua")) ua(line);
            else if (line.startsWith("parse")) parse(line);
            else if (line.startsWith("click")) click(line);
            else if (line.startsWith("player")) player(line);
            else if (line.startsWith("user-agent")) ua(line);
            else if (line.startsWith("User-Agent")) ua(line);
            else if (line.startsWith("referer")) referer(line);
            else if (line.startsWith("Referer")) referer(line);
            else if (line.startsWith("#EXTVLCOPT:http-user-agent")) ua(line);
            else if (line.startsWith("#EXTVLCOPT:http-referer")) referer(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key")) key(line);
            else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type")) type(line);
        }

        public Setting copy(Channel channel) {
            if (ua != null) channel.setUa(ua);
            if (parse != null) channel.setParse(parse);
            if (click != null) channel.setClick(click);
            if (referer != null) channel.setReferer(referer);
            if (player != null) channel.setPlayerType(player);
            if (key != null && type != null) channel.setDrm(Drm.create(key, type));
            return this;
        }

        private void ua(String line) {
            try {
                ua = line.split("ua=")[1].trim().replace("\"", "");
            } catch (Exception e) {
                ua = null;
            }
        }

        private void referer(String line) {
            try {
                referer = line.split("referer=")[1].trim().replace("\"", "");
            } catch (Exception e) {
                referer = null;
            }
        }

        private void parse(String line) {
            try {
                parse = Integer.parseInt(line.split("parse=")[1].trim());
            } catch (Exception e) {
                parse = null;
            }
        }

        private void click(String line) {
            try {
                click = line.split("click=")[1].trim();
            } catch (Exception e) {
                click = null;
            }
        }

        private void player(String line) {
            try {
                player = Integer.parseInt(line.split("player=")[1].trim());
            } catch (Exception e) {
                player = null;
            }
        }

        private void key(String line) {
            try {
                key = line.split("license_key=")[1].trim();
                if (!key.startsWith("http") && !key.startsWith("{") && key.contains(":")) convert();
            } catch (Exception e) {
                key = null;
            } finally {
                player = Players.EXO;
            }
        }

        private void type(String line) {
            try {
                type = line.split("license_type=")[1].trim();
            } catch (Exception e) {
                type = null;
            } finally {
                player = Players.EXO;
            }
        }

        private void convert() {
            String[] split = key.split(":");
            String k = Util.base64(Util.hex2byte(split[1])).replace("=", "");
            String kid = Util.base64(Util.hex2byte(split[0])).replace("=", "");
            key = String.format("{ \"keys\":[ { \"kty\":\"oct\", \"k\":\"%s\", \"kid\":\"%s\" } ], \"type\":\"temporary\" }", k, kid);
        }

        private void clear() {
            ua = null;
            key = null;
            type = null;
            parse = null;
            click = null;
            player = null;
            referer = null;
        }
    }
}
