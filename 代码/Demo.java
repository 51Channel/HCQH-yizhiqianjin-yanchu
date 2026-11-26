package world.hechao.zhidou.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Entity;

/**
 * 一段规则演示的脚本（11-17 深夜主播：「每玩到一个新游戏，我看那些影视解说都会把这个游戏的原理给你拆分一遍……
 * 在这个演出里面完整的演示这个游戏要怎么玩儿。你现在仅靠几句话，它其实是完全体现不出来这个游戏的内容的」）。
 * <p>
 * 通用的，别的企划照抄就能用：
 * <ul>
 *   <li>{@link #shot}：一个镜头（t0–t1 秒，机位 p0 → p1、看的点 l0 → l1、视角、两头慢不慢）。镜头由模组 Cine 的 demo 模式在客户端算，所有人同一刻看同一个画面。</li>
 *   <li>{@link #cap}：一步的字幕（大字这一步发生什么、小字补一句，red 是「输了会怎样」那一步）。</li>
 *   <li>{@link #at}：到第几秒在场景里做一件事（摆示范的人、翻板、通电、放电、地板塌……）。</li>
 *   <li>{@link #props}：演示里临时摆出来的东西（示范的人），演完自动删；{@link #cleanup}：演完把场景改回去。</li>
 * </ul>
 * Match.playDemo 负责放：发给模组、按时间做事、放完（或 /zd next 跳过）收拾干净再接正式的游戏。
 */
final class Demo {
    final String kick, title;
    /** 片名上面那个小字（规则演示是「怎么玩」；通关演出是「通关」）。 */
    String word = "游戏规则";
    double len;
    /**
     * 开头的黑场片名卡有几秒（11-23「要有那种电影感」）：片名在 SLAM 秒落定；11-25 加了气口（「你连一个气口都没有」）：
     * 字在 PRE − 1 秒开始淡、最后 0.6 秒全黑，PRE 秒起场景从黑里亮起来，所以 PRE 从 3.6 加到 4.4。
     * 11-26 外部视听方案：片名卡不再铺全黑，压暗 65% 的当幕场景上浮字（模组 Cine.titleCard），字淡掉的同时场景提亮，PRE 秒直接是第一镜。
     * 脚本里写的时间都从片名卡后面算起（off 一开始就是 PRE），各场的演示脚本不用改。
     */
    static final double PRE = 4.4, SLAM = 2.0;
    /** 之后写的时间都往后挪 off 秒（开头插一段大白话总结时，后面的脚本不用一个个改时间）。 */
    double off = PRE;
    /** 片名卡几秒（发给模组）；0 = 没有片名卡。 */
    double pre = PRE;
    /** 轻量机拍（揭牌那几秒）：不画顶上那行小字、结尾不压黑、放完直接切回自己的第一人称。 */
    boolean lite;
    final JsonArray shots = new JsonArray(), caps = new JsonArray();
    final List<Object[]> acts = new ArrayList<>();
    final List<Entity> props = new ArrayList<>();
    /** 演示里身上描红边的人（盲牌演示讲内鬼那一屏）：所有人的屏幕上都描，借内鬼看同伙那一套（ZdShow.isMate）。改了要 pushState。 */
    final List<java.util.UUID> glow = new ArrayList<>();
    final List<Runnable> cleanup = new ArrayList<>();
    final List<String[]> lines = new ArrayList<>();   // {秒, 一句}：没装模组的人在聊天里看

    Demo(String kick, String title) { this.kick = kick; this.title = title; }

    static double[] v(double x, double y, double z) { return new double[]{x, y, z}; }

    Demo shot(double t0, double t1, double[] p0, double[] p1, double[] l0, double[] l1, double fov, boolean ease) {
        t0 += off; t1 += off;
        JsonObject o = new JsonObject(); o.addProperty("a", t0); o.addProperty("b", t1);
        JsonArray p = new JsonArray(), l = new JsonArray();
        for (double x : p0) p.add(x); for (double x : p1) p.add(x); for (double x : l0) l.add(x); for (double x : l1) l.add(x);
        o.add("p", p); o.add("l", l); o.addProperty("f", fov); o.addProperty("e", ease);
        shots.add(o); len = Math.max(len, t1); return this;
    }
    /**
     * 切回每个人自己的第一人称（11-25 用户：「黑场切到玩家自己的视角，然后画面上出现一个红色的圆圈加一个红色的箭头指着自己的心」）：
     * 这段时间模组不接管镜头，物品栏、心照常画，进出都先压黑；走路、转视角照样不动。字幕画在画面中间偏上（底下是物品栏和心）。
     */
    Demo self(double t0, double t1) {
        t0 += off; t1 += off;
        JsonObject o = new JsonObject(); o.addProperty("a", t0); o.addProperty("b", t1); o.addProperty("self", true);
        JsonArray p = new JsonArray(); for (int i = 0; i < 6; i++) p.add(i % 3 == 1 ? 77 : 0.5);
        o.add("p", p); o.add("l", p); o.addProperty("f", 70); o.addProperty("e", false);
        shots.add(o); len = Math.max(len, t1); return this;
    }
    /** 上一句字幕在自己的第一人称里用红圈、红箭头圈住物品栏上的心（模组 Cine.heartMark，三下「冰」对着画面一下一下出来）。 */
    Demo heartMark() { caps.get(caps.size() - 1).getAsJsonObject().addProperty("h", true); return this; }
    /** 不动的镜头。 */
    Demo still(double t0, double t1, double[] p, double[] l, double fov) { return shot(t0, t1, p, p, l, l, fov, false); }

    Demo cap(double t0, double t1, String main, String sub, boolean red) { return cap(t0, t1, main, sub, red, true); }
    /** numbered = false：这一句不算一步（比如整局只讲一次的那句心），前面不画步数。 */
    Demo cap(double t0, double t1, String main, String sub, boolean red, boolean numbered) {
        t0 += off; t1 += off;
        JsonObject o = new JsonObject(); o.addProperty("x", !numbered); o.addProperty("a", t0); o.addProperty("b", t1); o.addProperty("m", main); o.addProperty("s", sub); o.addProperty("r", red);
        if (gistNext) { o.addProperty("g", true); gistAt.add(t0); gistNext = false; }
        caps.add(o); lines.add(new String[]{String.valueOf(t0), main + (sub.isEmpty() ? "" : "（" + sub + "）")});
        len = Math.max(len, t1 + 0.4); return this;
    }
    Demo at(double t, Runnable r) { acts.add(new Object[]{t + off, r}); return this; }
    /**
     * 开头那句大白话（11-18 用户：「说明一定要先最浅显的说明」）：片名之后先用一句话讲清楚这一场怎么赢、怎么输，
     * 镜头停在 p 看 l；讲完以后的脚本整体往后挪 sec 秒。
     */
    private boolean gistNext;
    /** 一句话讲完那句什么时候出来（插件照这个放落字的声音）。 */
    final List<Double> gistAt = new ArrayList<>();
    Demo gist(double at, double sec, String main, String sub, double[] p0, double[] p1, double[] l, double fov) {
        gistNext = true; cap(at, at + sec - 0.2, main, sub, false, false); shot(at, at + sec, p0, p1, l, l, fov, true); off += sec; return this;
    }

    /** 这段演示大概在哪拍：所有镜头看的点的平均（Match.playDemo 用它把离得太远的人的本体挪过来，不然服务器不给他同步示范的人）。 */
    double[] anchor() {
        double x = 0, y = 0, z = 0; int n = 0;
        for (var e : shots) { if (e.getAsJsonObject().has("self")) continue; var l = e.getAsJsonObject().getAsJsonArray("l"); x += l.get(0).getAsDouble() + l.get(3).getAsDouble(); y += l.get(1).getAsDouble() + l.get(4).getAsDouble(); z += l.get(2).getAsDouble() + l.get(5).getAsDouble(); n += 2; }
        return n == 0 ? null : new double[]{x / n, y / n, z / n};
    }
    /** 镜头换机位的时刻：每个镜头开始的那一秒。 */
    List<Double> cutTimes() { List<Double> l = new ArrayList<>(); for (var e : shots) l.add(e.getAsJsonObject().get("a").getAsDouble()); return l; }
    /** 每句字幕什么时候出来、是不是「输了会怎样」那句（Match.playDemo 照这个配音效）。 */
    List<double[]> capTimes() {
        List<double[]> l = new ArrayList<>();
        for (var e : caps) { var o = e.getAsJsonObject(); l.add(new double[]{o.get("a").getAsDouble(), o.get("r").getAsBoolean() ? 1 : 0}); }
        return l;
    }

    JsonObject packet() {
        JsonObject o = new JsonObject(); o.addProperty("k", "cine"); o.addProperty("m", "demo");
        o.addProperty("len", (int) Math.round(len * 20)); o.addProperty("pre", pre); o.addProperty("lite", lite); o.addProperty("kick", kick); o.addProperty("title", title); o.addProperty("word", word);
        o.add("shots", shots); o.add("caps", caps); return o;
    }
}
