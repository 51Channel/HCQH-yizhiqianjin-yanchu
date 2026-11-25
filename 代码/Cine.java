package world.hechao.zhidou.client;

import static world.hechao.zhidou.client.Brand.a;
import static world.hechao.zhidou.client.Brand.rect;
import static world.hechao.zhidou.client.Brand.solid;
import static world.hechao.zhidou.client.Brand.text;
import static world.hechao.zhidou.client.Brand.width;
import static world.hechao.zhidou.client.ZdShow.*;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import world.hechao.zhidou.client.Brand.Face;

/**
 * 开场片头（OP）。插件开局发一条 {k:"cine", m:"op", len, n, host, acts}，所有人同一刻看同一个镜头，演完回到自己的第一人称：
 * <ol>
 *   <li>黑场：「30 个人进场 / 只有 1 个能通关」。</li>
 *   <li>亮灯：漏斗场一片漆黑，镜头在大门上空慢慢推进去，灯从外往里一圈一圈亮起来，最后中间的吊鼓亮。</li>
 *   <li>五个场地快切（每镜 2.6 秒、硬切）：哪一幕、叫什么、一句怎么玩。</li>
 *   <li>选手：大门口的起点平台上排成三排，镜头从左到右扫过去。</li>
 *   <li>主持人：坑底北边的黑王座，低机位慢慢推。</li>
 *   <li>片名：从北边高处往后拉，「赫朝一掷千金游戏」，马上开始第一幕。</li>
 * </ol>
 * 镜头、灯、字都在客户端算（服务器 20 刻一跳会一顿一顿），时间按插件给的 len 整段缩放（快节奏是一半）。
 * 片头期间：手、准星、聊天、快捷栏都不画，走路和转视角不动；插件 /zd next 会发 m:"stop" 提前收掉。
 * 声音：每亮一圈灯一声 zd.op_light；切镜、字、片名的声音见 opSounds；配乐放 bgm/op.ogg（旧空位 zd.op_music 也认）。
 */
public final class Cine {
    private Cine() {}
    /** 正常节奏一共多少秒（和插件 Match.OP_SEC 一致）。 */
    static final float TOTAL = 51.9f;
    static final float T_LIGHT = 5.5f, T_MONT = 16f, MONT = 13f / 6, T_LINE = 29f, T_HOST = 36f, T_DARE = 40.2f, T_TITLE = 44.9f, T_BLACK = 50.8f;
    // 11-23：配乐接缝重剪（原来少跳一小节，「有一段衔接不是很干脆利落，有一段卡的声音」），接缝后面整体晚了一小节（1.9 秒），片名、黑场跟着往后挪
    /** 灯一圈一圈亮：天上的航空灯 → 看台和大门 → 尺子环 → 帐篷台地 → 第一圈 → 坑底 → 中间吊鼓。 */
    private static final float[] BANK_T = {7.0f, 8.0f, 9.0f, 10.0f, 10.8f, 11.6f, 12.8f};
    private static final float DARK = 0.08f;
    private static final double C = 0.5, BZ = C - 300, HX = C - 300;

    private static boolean on, demo; private static long startAt, endAt; private static float scale = 1, demoLen;
    /** 规则演示（每一场开始前，像影视解说那样把这场游戏怎么玩完整演一遍）：镜头、字幕都是插件发过来的，这里只管放。 */
    private static final List<Shot> DSHOTS = new ArrayList<>();
    private record Cap(float t0, float t1, int no, String main, String sub, boolean red, boolean gist) {}
    private static final List<Cap> DCAPS = new ArrayList<>();
    private static String dKick = "", dTitle = "", dWord = "游戏规则";
    /** 演示开头的黑场片名卡有几秒（插件发 pre；片名在 SLAM 秒砸下来，pre 秒硬切进场景）。 */
    private static float dPre; private static final float SLAM_BEFORE_CUT = 1.6f;
    private static String host = "", next = ""; private static int count;
    private static final List<String[]> acts = new ArrayList<>();
    private static int bankDone; private static SoundInstance music;

    // ================= 镜头表 =================
    /** 一个镜头：t0–t1（正常节奏的秒）机位从 p0 挪到 p1、看的点从 l0 挪到 l1；ease = 两头慢中间快（长镜头），否则匀速（快切）。 */
    private record Shot(float t0, float t1, double[] p0, double[] p1, double[] l0, double[] l1, float fov, boolean ease) {}
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static double[] v(double x, double y, double z) { return new double[]{x, y, z}; }
    /** 漏斗场极坐标（和插件 Venue.P 一样：0 度正北、顺时针）。 */
    private static double[] ring(double r, double th, double y) { double a = Math.toRadians(th); return v(C + r * Math.sin(a), y, C - r * Math.cos(a)); }
    static {
        // 黑场和亮灯是同一个机位：大门南边上空，往北看整个漏斗场，慢慢推进去
        SHOTS.add(new Shot(0, T_MONT, v(C, 117, 101), v(C, 103, 76), v(C, 72, 4), v(C, 74, 0), 58, true));
        // 六个游戏一个一个过（顺序和插件发来的 acts 一样：零点八、高压、盲牌、失控列车、限定猜拳、弑君；11-23 站队删了）
        float t = T_MONT;
        SHOTS.add(new Shot(t, t += MONT, ring(30, 248, 80.5), ring(30, 262, 80.2), ring(40, 280, 76.3), ring(40, 294, 76.3), 52, false));   // 一 尺子环
        SHOTS.add(new Shot(t, t += MONT, v(-19, 123.2, BZ + 6), v(-14.5, 122.9, BZ + 6), v(18, 116.5, BZ + 2), v(18, 116.5, BZ + 2), 58, false));   // 二 渡桥：闸门外、梁上方往对面看（闸门上挂着规则牌，机位要在它前面）
        SHOTS.add(new Shot(t, t += MONT, v(HX, 72.6, C + 11), v(HX, 72.5, C + 16.5), v(HX, 71.6, C + 36), v(HX, 71.6, C + 36), 55, false));   // 三 监狱（盲牌）
        SHOTS.add(new Shot(t, t += MONT, v(-54, 71.6, 296.1), v(-57, 71.5, 296.1), v(-12, 70.8, 299.0), v(-12, 70.8, 299.0), 60, false));   // 四 失控列车：车窗外顺着车身往前看
        SHOTS.add(new Shot(t, t += MONT, ring(8.2, 50, 70.6), ring(8.2, 62, 70.6), ring(13.2, 56, 67.9), ring(13.2, 64, 67.9), 60, false));   // 五 限定猜拳：轮盘环上的对决桌（11-25：原来扫的 25°–37° 正好在两张桌子中间，拍到空地；桌子在 0/60/120/240/300°）
        SHOTS.add(new Shot(t, t += MONT, v(C + 4.4, 69.4, C + 3.6), v(C + 3.5, 68.8, C + 2.7), v(C, 65.9, C - 0.6), v(C, 66.0, C - 0.9), 50, false));   // 六 弑君的牌桌（正南有通关席的椅背挡着，从东南边高一点看）
        // 选手：起点平台上三排，镜头在他们前面从左（东）往右（西）扫
        SHOTS.add(new Shot(T_LINE, T_HOST, ring(31.0, 171, 79.0), ring(31.0, 189, 79.0), ring(38.5, 171, 77.4), ring(38.5, 189, 77.4), 50, true));
        // 主持人：坑底北边黑王座，低机位慢慢推
        SHOTS.add(new Shot(T_HOST, T_DARE, v(C + 2.9, 65.9, C + 2.4), v(C + 2.2, 66.05, C + 1.5), v(C, 67.0, C - 2.6), v(C, 67.0, C - 2.6), 46, true));   // 11-23 王座重做：脸在 (0.5, 67.0, -2.1)
        // 11-25 用户：「往后加多一个幕……如果可以击败我，可以获得神秘大奖……以我的这个人物皮肤去做一幕」——切到正面、贴近脸，主持人直接对镜头下战书
        // 11-25 实拍：贴到 2 米脸占满全屏、字压在胸口——退到 4 米、低一点往上看，脸在画面上三分之一，字落在膝盖上
        SHOTS.add(new Shot(T_DARE, T_TITLE, v(C + 0.15, 66.55, C + 1.6), v(C + 0.05, 66.65, C + 0.9), v(C, 67.25, C - 2.6), v(C, 67.3, C - 2.6), 40, true));
        // 片名：北边高处往后拉、往上升，看着整个漏斗场和南边大门
        SHOTS.add(new Shot(T_TITLE, TOTAL, v(C, 95, C - 44), v(C, 109, C - 62), v(C, 73, C), v(C, 72, C + 4), 60, true));
    }

    // ================= 收发 =================
    static void receive(JsonObject o) {
        switch (s(o, "m", "")) {
            case "op" -> {
                acts.clear();
                for (JsonObject a : arr(o, "acts")) acts.add(new String[]{s(a, "no", ""), s(a, "game", "")});
                count = i(o, "n", 30); host = s(o, "host", ""); next = s(o, "next", "");
                scale = Math.max(0.2f, i(o, "len", (int) (TOTAL * 20)) / 20f / TOTAL);
                startAt = now(); endAt = 0; on = true; hold = false; demo = false; bankDone = 0; opCue = 0; opBed = 0;
                OP_LINES.clear();
                if (o.has("lines") && o.get("lines").isJsonObject()) for (var e : o.getAsJsonObject("lines").entrySet()) if (e.getValue().isJsonPrimitive()) OP_LINES.put(e.getKey(), e.getValue().getAsString());
                for (Shot s : SHOTS) FilmsetRenderer.prewarm(s.p0[0], s.p0[2]);   // 快切要跳去的远处（列车在 300 格外）：黑场那几秒先把模型装好
                stopMusic();
                // 片头配乐：优先 bgm/op.ogg（11-22 起配乐都走 Bgm，一首接一首会淡入淡出），没有再退回旧的空位 zd.op_music
                if (Minecraft.getInstance().getSoundManager().getSoundEvent(Identifier.fromNamespaceAndPath("hechao_zhidou", "zd.bgm_op")) != null) { Bgm.play("op", false, 1f, 0); opBgm = true; }
                else if (Minecraft.getInstance().getSoundManager().getSoundEvent(Identifier.fromNamespaceAndPath("hechao_zhidou", "zd.op_music")) != null) { music = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("hechao_zhidou", "zd.op_music")), 1f, 1f); Minecraft.getInstance().getSoundManager().play(music); }
            }
            case "demo" -> {
                DSHOTS.clear(); DCAPS.clear();
                for (JsonObject s : arr(o, "shots")) {
                    double[] p = nums(s, "p"), l = nums(s, "l");
                    DSHOTS.add(new Shot((float) dbl(s, "a", 0), (float) dbl(s, "b", 1), v(p[0], p[1], p[2]), v(p[3], p[4], p[5]), v(l[0], l[1], l[2]), v(l[3], l[4], l[5]), (float) dbl(s, "f", 60), b(s, "e")));
                }
                int n = 0;
                for (JsonObject s : arr(o, "caps")) DCAPS.add(new Cap((float) dbl(s, "a", 0), (float) dbl(s, "b", 1), b(s, "x") ? 0 : ++n, s(s, "m", ""), s(s, "s", ""), b(s, "r"), b(s, "g")));
                dKick = s(o, "kick", ""); dTitle = s(o, "title", ""); dWord = s(o, "word", "游戏规则"); dPre = (float) dbl(o, "pre", 0);
                demoLen = i(o, "len", 600) / 20f; scale = 1; demo = true;
                startAt = now(); endAt = 0; on = !DSHOTS.isEmpty(); hold = false; bankDone = BANK_T.length; stopMusic(); synced = false; lastT = 0;
                for (Shot s : DSHOTS) FilmsetRenderer.prewarm(s.p0[0], s.p0[2]);
            }
            case "dsync" -> { if (demo && on) { syncT = (float) dbl(o, "t", 0); syncAt = now(); synced = true; } }
            case "stop" -> stop();
            default -> {}
        }
    }
    static void stop() { if (on || hold) { on = false; hold = false; endAt = now(); } stopMusic(); }
    static void clear() { on = false; hold = false; demo = false; endAt = 0; stopMusic(); }
    /**
     * 11-25 用户：「每切一个幕都会被第一人称切一下……我知道你是传送，但是不要显示出来」——
     * 本地时间放完时服务器还没说停（服务器按刻算，总要晚一点；演示和演示之间还隔 0.2 秒），原来这时就淡回第一人称，下一段一来又切走。
     * 现在自己放完先压着全黑（hold），等服务器的 stop：stop 和下一段同一刻到就直接接上，后面没有了才从黑里淡回第一人称。最多等 3 秒。
     */
    private static boolean hold; private static long holdAt;
    private static double[] nums(JsonObject o, String k) {
        double[] r = new double[6];
        if (o.has(k) && o.get(k).isJsonArray()) { var a = o.getAsJsonArray(k); for (int i = 0; i < Math.min(6, a.size()); i++) r[i] = a.get(i).getAsDouble(); }
        return r;
    }
    private static boolean opBgm;
    /**
     * 片头的声音（11-23：「现在一点音效都没有，这个演出要有那种电影感」）：和规则演示同一套——黑场两行字一沉一击，
     * 每个镜头切过去一声风、字出来一下重击，主持人那一下压低，片名前先往上推、片名出来「轰」一声，收尾一击。
     * 没放配乐（bgm/op.ogg）时底下垫一层低沉的环境声。时间是正常节奏的秒（快节奏 t() 已经缩好）。
     */
    private static final List<float[]> OP_CUES = new ArrayList<>();
    private static final List<String> OP_CUE_N = new ArrayList<>();
    private static int opCue;
    private static void cue(float t, String n, float vol, float pitch) { OP_CUES.add(new float[]{t, vol, pitch}); OP_CUE_N.add(n); }
    static {
        cue(0.5f, "subdrop", 0.9f, 1f); cue(2.0f, "hit", 0.8f, 1f);
        for (int k = 0; k < 6; k++) { float c = T_MONT + k * MONT; cue(c, "whoosh", 0.35f, 1f); cue(c + 0.3f, "hit", 0.5f, 1.1f); }
        cue(T_LINE, "whoosh", 0.4f, 1f); cue(T_LINE + 0.6f, "hit", 0.65f, 1f);
        cue(T_HOST, "whoosh", 0.4f, 0.9f); cue(T_HOST + 0.5f, "hit", 0.8f, 0.8f); cue(T_DARE, "whoosh", 0.35f, 0.8f); cue(T_DARE + 0.4f, "hit", 0.9f, 0.7f);
        cue(T_TITLE - 1.1f, "swell", 0.9f, 1f); cue(T_TITLE + 0.7f, "braam", 1f, 1f);
        cue(T_BLACK, "hit", 0.6f, 0.9f);
    }
    private static void opSounds(float t) {
        if (opBgm) return;   // 11-23：「音效不是说你加满它就是好的」——有配乐就只听配乐（亮灯那一下一下的「咔」照旧，在 bank 里）
        while (opCue < OP_CUES.size() && t >= OP_CUES.get(opCue)[0]) {
            float[] c = OP_CUES.get(opCue); String n = OP_CUE_N.get(opCue++);
            if (t - c[0] < 0.5f) ZdFx.play(n, c[2], c[1]);   // 卡了一下跳过去的不补放
        }
        if (!opBgm) { int k = (int) (t / 14f); if (k >= opBed && t < T_BLACK) { opBed = k + 1; ZdFx.play("drone", 1f, 0.8f); } }
    }
    private static int opBed;
    private static void stopBed() {
        var sm = Minecraft.getInstance().getSoundManager();
        sm.stop(Identifier.fromNamespaceAndPath("hechao_zhidou", "zd.drone"), null);
    }
    private static void stopMusic() { if (opBed > 0) { stopBed(); opBed = 0; } if (music != null) { Minecraft.getInstance().getSoundManager().stop(music); music = null; } if (opBgm) { opBgm = false; Bgm.stop(20); } }

    /**
     * 片头里的第几秒（正常节奏的秒）。规则演示跟着服务器走（11-22「过场动画里 NPC 动效每个人还不同步」）：示范的人是服务器一刻一刻挪的，
     * 插件每半秒发一次 dsync（演示放到第几秒，按服务器的刻）；两次之间往前推，最多推 0.6 秒（服务器卡住了镜头字幕就等着），不往回倒。
     */
    private static float syncT, lastT; private static long syncAt; private static boolean synced;
    static float t() {
        if (demo && synced) { float v = syncT + Math.min(0.6f, (now() - syncAt) / 1000f); if (v < lastT) v = lastT; lastT = v; return v; }
        return (now() - startAt) / 1000f / scale;
    }
    /** 片头正在放：镜头、灯、输入都归这里。 */
    public static boolean active() {
        if (on && t() >= (demo ? demoLen : TOTAL)) { on = false; hold = true; holdAt = now(); endAt = 0; }
        return on && Minecraft.getInstance().level != null;
    }

    // ================= 镜头（CameraMixin 在 Camera.setup 之后问这里） =================
    /** 现在的机位：{x, y, z, yaw, pitch}；不在片头里返回 null。 */
    public static double[] camera() {
        if (!active()) return null;
        List<Shot> list = demo ? DSHOTS : SHOTS;
        float t = t(); Shot sh = list.get(0);
        for (Shot s : list) if (t >= s.t0) sh = s;
        float k = Math.max(0, Math.min(1, (t - sh.t0) / (sh.t1 - sh.t0)));
        if (sh.ease) k = (float) (0.5 - 0.5 * Math.cos(Math.PI * k));
        double[] p = lerp(sh.p0, sh.p1, k), l = lerp(sh.l0, sh.l1, k);
        double dx = l[0] - p[0], dy = l[1] - p[1], dz = l[2] - p[2], h = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.toDegrees(Math.atan2(-dx, dz)), pitch = -Math.toDegrees(Math.atan2(dy, h));
        // 11-23 手持呼吸、「输了」那一下的震都删了（「这一个晃的演出效果我其实挺不满意的」）：镜头稳稳地走
        return new double[]{p[0], p[1], p[2], yaw, pitch};
    }
    public static float fov() {
        List<Shot> list = demo ? DSHOTS : SHOTS;
        if (list.isEmpty()) return 70;
        Shot sh = list.get(0); float t = t();
        for (Shot s : list) if (t >= s.t0) sh = s;
        return sh.fov;
    }
    private static double[] lerp(double[] a, double[] b, float k) { return v(a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k); }

    // ================= 灯（FilmsetRenderer 画漏斗场的时候问这里） =================
    /** 这会儿在不在「黑着、一圈一圈亮」那一段。 */
    static boolean lighting() { return active() && !demo && t() < BANK_T[BANK_T.length - 1] + 0.5f; }
    /** 第 b 圈亮了多少（0 黑 – 1 全亮）：一下子亮，不是慢慢淡入。 */
    private static float bank(int b) {
        float t = t(), k = Math.max(0, Math.min(1, (t - BANK_T[b]) / 0.16f));
        if (t >= BANK_T[b] && b >= bankDone) { bankDone = b + 1; ZdFx.play("op_light", 1f - 0.04f * b, 0.9f); }
        return k;
    }
    /** 离漏斗场中心多远算第几圈（远的先亮）。 */
    private static int bankOf(double r, double y) {
        if (y > 86 && r < 16) return 6;
        return r >= 56 ? 0 : r >= 44 ? 1 : r >= 34 ? 2 : r >= 22 ? 3 : r >= 12 ? 4 : 5;
    }
    private static boolean inFunnel(double x, double z) { return Math.abs(x - C) < 90 && Math.abs(z - C) < 90; }
    /** 不会动的场景（一大块一大块合并过的网格）：按这一块的中心在第几圈。 */
    static float staticLight(AABB b) {
        double x = (b.minX + b.maxX) / 2, z = (b.minZ + b.maxZ) / 2;
        if (!inFunnel(x, z)) return 1;
        float k = bank(bankOf(Math.hypot(x - C, z - C), (b.minY + b.maxY) / 2));
        return DARK + (1 - DARK) * k;
    }
    /** 会动的部件：灯组（L.*）、整圈地面（Z.*）按自己那一组；别的按支点在第几圈。灯泡没亮的时候是全黑。 */
    static float dynLight(String ltKey, Vector3f pivot) {
        if (ltKey != null && ltKey.startsWith("L.")) {
            int b = switch (ltKey) { case "L.sky" -> 0; case "L.stands", "L.gate" -> 1; case "L.rim" -> 2; case "L.terrace" -> 3; case "L.l1" -> 4; case "L.pit" -> 5; default -> 6; };
            return bank(b);
        }
        if (ltKey != null && ltKey.startsWith("Z.")) {
            int b = switch (ltKey) { case "Z.ruler" -> 2; case "Z.terrace" -> 3; case "Z.l1" -> 4; default -> 5; };
            return DARK + (1 - DARK) * bank(b);
        }
        if (pivot == null || !inFunnel(pivot.x, pivot.z)) return 1;
        return DARK + (1 - DARK) * bank(bankOf(Math.hypot(pivot.x - C, pivot.z - C), pivot.y));
    }
    /** 转播大屏：吊鼓亮了才开。 */
    static boolean screensOn() { return demo || !lighting() || bank(6) > 0.5f; }

    // ================= 画面上的字（ZdShow.render 在片头里只画这个） =================
    static void render(GuiGraphics g) {
        int w = g.guiWidth(), h = g.guiHeight();
        if (!active()) {   // 刚放完：从黑里淡回自己的第一人称
            if (opBed > 0) { stopBed(); opBed = 0; }   // 片头的底噪跟着片头一起停
            if (hold && now() - holdAt > 3000) { hold = false; endAt = now(); }   // 服务器一直没说停：别永远黑着
            if (hold) { rect(g, 0, 0, w, h, solid(0x000000)); return; }
            float k = endAt == 0 ? 0 : 1 - (now() - endAt) / 450f;
            if (k > 0) rect(g, 0, 0, w, h, a(0x000000, k));
            return;
        }
        float t = t();
        float bars = (float) h * (demo ? 0.12f * out(span(t, 0, 0.5f)) : 0.105f);   // 上下黑边（宽银幕；演示开头从上下合进来）
        rect(g, 0, 0, w, bars, solid(0x000000)); rect(g, 0, h - bars, w, bars, solid(0x000000));
        if (demo) { demoText(g, w, h, bars, t); return; }
        opSounds(t);
        if (t < T_LIGHT + 1.2f) hook(g, w, h, t);
        else if (t >= T_MONT && t < T_LINE) {
            int k = Math.min(5, (int) ((t - T_MONT) / MONT));
            if (k < acts.size()) { String[] a = acts.get(k); caption(g, w, h, bars, t - (T_MONT + k * MONT), MONT, a[0], a[1], GOLD); }
        } else if (t >= T_LINE && t < T_HOST) caption(g, w, h, bars, t - T_LINE - 0.6f, T_HOST - T_LINE - 0.6f, "选手入场", count + " 人", GOLD);
        else if (t >= T_HOST && t < T_DARE) caption(g, w, h, bars, t - T_HOST - 0.5f, T_DARE - T_HOST - 0.5f, "主持人", host, RED);
        else if (t >= T_DARE && t < T_TITLE) caption(g, w, h, bars, t - T_DARE - 0.4f, T_TITLE - T_DARE - 0.4f, "打败主持人", "赢走神秘大奖", GOLD);
        else if (t >= T_TITLE) title(g, w, h, t - T_TITLE);
        opLine(g, w, h, bars, t);
        // 收尾：整个压黑，片头结束的那一刻再从黑里亮回来
        float black = span(t, T_BLACK, T_BLACK + 0.8f);
        if (black > 0) rect(g, 0, 0, w, h, a(0x000000, black));
    }
    private static float span(float t, float a, float b) { return Math.max(0, Math.min(1, (t - a) / (b - a))); }
    private static float out(float x) { return 1 - (1 - x) * (1 - x) * (1 - x); }

    /** 黑场两行：「30 个人进场」「只有 1 个能通关」，1 是红的。 */
    private static void hook(GuiGraphics g, int w, int h, float t) {
        float black = 1 - span(t, T_LIGHT, T_LIGHT + 1.2f);
        rect(g, 0, 0, w, h, a(0x000000, black));
        float fade = 1 - span(t, 4.4f, 5.2f), a1 = span(t, 0.5f, 1.2f) * fade, a2 = span(t, 2.0f, 2.7f) * fade;
        float sc = 2.3f, y = h / 2f - 30;
        if (a1 > 0) line(g, Face.DISPLAY, w / 2f, y + D_UP * sc - 3 * (1 - out(span(t, 0.5f, 1.4f))), sc, a1, count + " 个人进场", INK);
        if (a2 > 0) line(g, Face.DISPLAY, w / 2f, y + 34 + D_UP * sc - 3 * (1 - out(span(t, 2.0f, 2.9f))), sc, a2, "只有 ", INK, "1", RED, " 个能通关", INK);
    }

    /**
     * 一镜一句（11-17 主播：底下那行介绍词「很尴尬，不要这么写了」——只留两级字，不写怎么玩）：
     * 上面一行小字（第几幕 / 主持人，字距拉开，金或红），下面一行大字（游戏名 / 名字）。11-25 大字底下那根金色细线删了（用户：「又是一个莫名其妙的杠」），片名卡、片名同样不画。
     * 字淡入、轻轻往上落一点，镜头切走前淡出。
     */
    private static void caption(GuiGraphics g, int w, int h, float bars, float t, float len, String kicker, String main, int kickColor) {
        if (t < 0) return;
        float al = span(t, 0.15f, 0.55f) * (1 - span(t, len - 0.3f, len));
        if (al <= 0.01f) return;
        float sc = 1.95f, base = h - bars - 14;   // 大字的底
        float top = base - (D_UP + D_DN) * sc, rise = 3 * (1 - out(span(t, 0.15f, 0.7f)));
        fadeUp(g, w, top - 30, h - bars, al * 0.55f);
        text(g, Face.MEDIUM, spaced(kicker), w / 2f, top - 14 + rise, 0.78f, 1, kickColor, al, false);
        line(g, Face.DISPLAY, w / 2f, top + D_UP * sc + rise, sc, al, main, INK);
    }
    /**
     * 规则演示的画面（11-23 重做：「虽然是一句话讲完，但是演出方式还是太没意思了。有没有更厉害的演出啊？……这个演出要有那种电影感」）：
     * <ol>
     *   <li>黑场片名卡（0 – pre 秒）：先亮一行小字「第几幕」，底下一口气越吸越紧（插件放 swell），片名在 pre − 1.6 秒「砸」下来（放大一下落定、白闪、画面一震，插件放 braam），然后硬切进场景。</li>
     *   <li>一句话讲完（gist）：画面正中两行大字，冒号前后各一行，一行一行落下来（插件每行放一下 hit），数字金色、「掉 / 受伤 / 出局」这种红色；小字最后出来。</li>
     *   <li>一步一步：像电影字幕，底下居中一行，不写 1 2 3 步数；「输了会怎样」那一步镜头往里一冲、一震，四边压一圈暗红（插件放 subdrop）。</li>
     *   <li>一直有：宽银幕黑边、四角压暗、一层很淡的胶片颗粒；最后一下硬切黑，回到自己的第一人称。</li>
     * </ol>
     */
    private static void demoText(GuiGraphics g, int w, int h, float bars, float t) {
        float L = demoLen;
        if (t < dPre) { titleCard(g, w, h, t); return; }
        film(g, w, h, t);
        text(g, Face.MEDIUM, spaced(dTitle + " · " + dWord), w / 2f, (bars - 8) / 2, 0.7f, 1, INK_QUIET, span(t, dPre + 0.3f, dPre + 0.9f), false);
        Cap newest = null;   // 同一时刻只画最新开始的那一句
        for (Cap c : DCAPS) if (t >= c.t0 && (newest == null || c.t0 >= newest.t0)) newest = c;
        for (Cap c : DCAPS) if (c.red && t >= c.t0 && t < c.t0 + 1.4f) redPulse(g, w, h, 1 - (t - c.t0) / 1.4f);
        if (newest != null && t <= newest.t1 + 0.4f) { if (newest.gist) tagline(g, w, h, t, newest); else subtitle(g, w, h, bars, t, newest); }
        if (t > L - 0.55f) rect(g, 0, 0, w, h, solid(0x000000));   // 硬切黑，不慢慢淡
    }
    /**
     * 黑场片名卡。11-23 换掉了「砸下来 + 白闪 + 画面一抖」（「好像打雷一样的那个特效其实有点弱智，我不喜欢，换一种演出效果」）：
     * 片名的字先散得很开、淡淡地出来，一口气往中间收拢落定（电影片头那种字距收拢），最后小字「怎么玩」。不闪、不抖、不放大。
     */
    private static void titleCard(GuiGraphics g, int w, int h, float t) {
        rect(g, 0, 0, w, h, solid(0x000000));
        float slam = dPre - SLAM_BEFORE_CUT, cy = h * 0.46f, sc = 3.3f;
        float ka = span(t, 0.3f, 0.9f) * (1 - span(t, dPre - 0.15f, dPre));
        text(g, Face.MEDIUM, spaced(dKick), w / 2f, cy - 46, 0.9f, 1, GOLD, ka, false);
        float in = t - (slam - 0.5f); if (in < 0) return;   // 比原来砸下来的时刻早半拍开始收，收拢落定正好在原来那一下
        float k = out(span(in, 0, 1.1f)), al = span(in, 0, 0.6f) * (1 - span(t, dPre - 0.12f, dPre));
        int[] cps = dTitle.codePoints().toArray(); float[] cw = new float[cps.length]; float sum = 0;
        for (int i = 0; i < cps.length; i++) { cw[i] = width(Face.DISPLAY, new String(Character.toChars(cps[i]))) * sc; sum += cw[i]; }
        float gap = sc * 13 * (1 - k), x = w / 2f - (sum + gap * (cps.length - 1)) / 2;
        for (int i = 0; i < cps.length; i++) { line(g, Face.DISPLAY, x + cw[i] / 2, cy, sc, al, new String(Character.toChars(cps[i])), INK); x += cw[i] + gap; }
        text(g, Face.MEDIUM, spaced(dWord), w / 2f, cy + D_DN * sc + 20, 0.85f, 1, INK_SOFT, span(in, 1.1f, 1.5f) * (1 - span(t, dPre - 0.12f, dPre)), false);
    }
    /** 一句话讲完：冒号前后两行大字一行一行落下来，小字最后。 */
    static final float TAG_P2 = 1.3f, TAG_SUB = 2.6f;
    private static void tagline(GuiGraphics g, int w, int h, float t, Cap c) {
        float out = 1 - span(t, c.t1, c.t1 + 0.4f); if (out <= 0.01f) return;
        if (c.main.contains("奴隶赢皇帝")) { ecardGist(g, w, h, t, c, out); return; }
        String m = c.main; int cut = m.indexOf('：'); String p1 = cut < 0 ? m : m.substring(0, cut), p2 = cut < 0 ? "" : m.substring(cut + 1);
        float sc = Math.min(1.85f, (w - 80) / Math.max(1f, Math.max(width(Face.DISPLAY, p1), width(Face.DISPLAY, p2))));
        float lh = (D_UP + D_DN) * sc + 10, cy = h * 0.44f, y1 = p2.isEmpty() ? cy : cy - lh / 2, y2 = y1 + lh;
        band(g, w, y1 - D_UP * sc - 34, (p2.isEmpty() ? y1 : y2) + D_DN * sc + 46, 0.72f * out * span(t, c.t0 - 0.1f, c.t0 + 0.25f));
        drop(g, w, y1, sc, t - c.t0, out, p1);
        if (!p2.isEmpty()) drop(g, w, y2, sc, t - c.t0 - TAG_P2, out, p2);
        if (!c.sub.isEmpty()) {
            float sa = span(t, c.t0 + TAG_SUB, c.t0 + TAG_SUB + 0.4f) * out;
            line(g, Face.MEDIUM, w / 2f, (p2.isEmpty() ? y1 : y2) + D_DN * sc + 18, 0.95f, sa, paint(c.sub, INK_SOFT, true));
        }
    }
    /**
     * 弑君的那一句（11-23：「和主持人打牌这个就很没感觉……要帅」）：不念一长行字，直接把三张牌亮出来——
     * 皇帝、市民、奴隶一张一张落到桌上，中间金色箭头「赢」，最后一条红线从奴隶底下绕回皇帝：奴隶赢皇帝。小字（几局、怎么换边）最后出来。
     */
    private static final Identifier[] EC = {Identifier.fromNamespaceAndPath("hechao_zhidou", "textures/filmset/ec_emp.png"),
            Identifier.fromNamespaceAndPath("hechao_zhidou", "textures/filmset/ec_cit.png"), Identifier.fromNamespaceAndPath("hechao_zhidou", "textures/filmset/ec_sla.png")};
    private static void ecardGist(GuiGraphics g, int w, int h, float t, Cap c, float out) {
        float dt = t - c.t0, ch = h * 0.30f, cw = ch * 256 / 360f, gap = cw * 0.95f, cy = h * 0.42f, top = cy - ch / 2;
        band(g, w, top - 40, cy + ch / 2 + 58, 0.78f * out * span(t, c.t0 - 0.1f, c.t0 + 0.25f));
        String head = c.main.contains("：") ? c.main.substring(0, c.main.indexOf('：')) : "和主持人打牌";
        text(g, Face.MEDIUM, spaced(head), w / 2f, top - 24, 0.95f, 1, GOLD, span(dt, 0, 0.4f) * out, false);
        float[] cx = {w / 2f - cw - gap, w / 2f, w / 2f + cw + gap};
        for (int i = 0; i < 3; i++) {   // 一张一张落下来：从上面一点点落定、淡进来，不闪不抖
            float k = span(dt, 0.25f + 0.35f * i, 0.6f + 0.35f * i); if (k <= 0) continue;
            float y = top - 10 * (1 - out(k)), al = Math.min(1, k * 1.6f) * out;
            g.pose().pushMatrix(); g.pose().translate(cx[i] - cw / 2, y); g.pose().scale(cw / 256f, ch / 360f);
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, EC[i], 0, 0, 0f, 0f, 256, 360, 256, 360, 256, 360, a(0xFFFFFF, al));
            g.pose().popMatrix();
        }
        for (int i = 0; i < 2; i++) {   // 皇帝 → 市民、市民 → 奴隶：金色箭头，上面一个「赢」
            float k = out(span(dt, 1.4f + 0.3f * i, 1.75f + 0.3f * i)); if (k <= 0) continue;
            float x0 = cx[i] + cw / 2 + 6, len = (gap - 12) * k, al = out;
            arrowR(g, x0, cy, len, GOLD, al);
            if (k > 0.6f) text(g, Face.MEDIUM, "赢", x0 + (gap - 12) / 2, cy - 15, 1.0f, 1, GOLD, al * span(k, 0.6f, 1), false);
        }
        float r = out(span(dt, 2.2f, 2.9f));   // 奴隶 → 皇帝：从奴隶底下往下、往左绕回皇帝底下，红的
        if (r > 0) {
            float yb = top + ch + 6, yl = yb + 16, xs = cx[2], xe = cx[0], al = out;
            float total = 16 + (xs - xe) + 16, d = total * r;
            rect(g, xs - 1, yb, 2, Math.min(16, d), a(RED, al));
            if (d > 16) rect(g, xs - Math.min(xs - xe, d - 16), yl - 1, Math.min(xs - xe, d - 16), 2, a(RED, al));
            if (d > 16 + (xs - xe)) { float up = d - 16 - (xs - xe); rect(g, xe - 1, yl - up, 2, up, a(RED, al)); if (r >= 1) for (int i = 0; i < 5; i++) rect(g, xe - 1 - i, yb + i, 2 + 2 * i, 1, a(RED, al)); }
            float la = span(dt, 2.7f, 3.1f) * out;
            if (la > 0) { String v = "奴隶赢皇帝"; float lw = width(Face.MEDIUM, v) * 1.05f + 14; rect(g, w / 2f - lw / 2, yl - 7, lw, 14, a(0x000000, 0.9f * la)); text(g, Face.MEDIUM, v, w / 2f, yl - 5.5f, 1.05f, 1, RED, la, false); }
        }
        if (!c.sub.isEmpty()) line(g, Face.MEDIUM, w / 2f, top + ch + 40, 0.95f, span(dt, 3.3f, 3.7f) * out, paint(c.sub, INK_SOFT, true));
    }
    /** 往右的箭头：一根线 + 一个尖。 */
    private static void arrowR(GuiGraphics g, float x, float y, float len, int col, float al) {
        if (len < 2) return;
        rect(g, x, y - 1, len, 2, a(col, al));
        for (int i = 0; i < 5; i++) rect(g, x + len - 5 + i, y - (5 - i), 1, 2 * (5 - i), a(col, al));
    }
    /** 一行大字落下来：放大一点点、一下子落定。 */
    private static void drop(GuiGraphics g, int w, float y, float sc, float dt, float out, String v) {
        if (dt < 0) return;
        float k = 1 - out(span(dt, 0, 0.18f)), s = sc * (1 + 0.16f * k);
        line(g, Face.DISPLAY, w / 2f, y - 4 * k, s, span(dt, 0, 0.06f) * out, paint(v, INK, false));
    }
    /** 一步一步的字幕：底下居中，大字一行、小字一行；红的那一步关键词是红的。 */
    private static void subtitle(GuiGraphics g, int w, int h, float bars, float t, Cap c) {
        float al = span(t, c.t0, c.t0 + 0.3f) * (1 - span(t, c.t1, c.t1 + 0.4f)); if (al <= 0.01f) return;
        float base = h - bars - 14, ms = 1.3f, ss = 0.86f, top = base - (c.sub.isEmpty() ? 14 : 30);
        fadeUp(g, w, top - 40, h - bars, 0.75f * al);
        line(g, Face.MEDIUM, w / 2f, top, ms, al, paint(c.main, INK, c.red));
        if (!c.sub.isEmpty()) line(g, Face.REGULAR, w / 2f, top + 17, ss, al * span(t, c.t0 + 0.25f, c.t0 + 0.6f), paint(c.sub, INK_SOFT, c.red));
    }
    /** 上色：数字金色；红词（掉、受伤、出局……）红色。 */
    private static final String[] RED_WORDS = {"掉进水里", "掉下去", "掉到", "受伤", "出局", "倒下", "放电", "撞上", "撞车", "输光", "翻开", "输了", "输两局", "追上", "被骗", "就输", "骗你", "反杀", "落败", "吹下"};
    private static Object[] paint(String v, int base, boolean reds) {
        List<Object> out = new ArrayList<>(); StringBuilder cur = new StringBuilder(); int curC = base;
        for (int i = 0; i < v.length(); ) {
            String hit = null; if (reds) for (String r : RED_WORDS) if (v.startsWith(r, i)) { hit = r; break; }
            int c = v.charAt(i); boolean digit = c >= '0' && c <= '9' || (c == '.' && i > 0 && Character.isDigit(v.charAt(i - 1)) && i + 1 < v.length() && Character.isDigit(v.charAt(i + 1)));
            int col = hit != null ? RED : digit ? GOLD : base; String piece = hit != null ? hit : String.valueOf((char) c);
            if (col != curC && cur.length() > 0) { out.add(cur.toString()); out.add(curC); cur.setLength(0); }
            curC = col; cur.append(piece); i += piece.length();
        }
        if (cur.length() > 0) { out.add(cur.toString()); out.add(curC); }
        return out.toArray();
    }
    /** 胶片感：四角压暗 + 很淡的颗粒（每帧换一次位置）。 */
    private static final java.util.Random GRAIN = new java.util.Random();
    private static void film(GuiGraphics g, int w, int h, float t) {
        int n = 10;
        for (int k = 0; k < n; k++) {   // 四边往里越来越亮：一圈压暗
            float f = (n - k) / (float) n, al = 0.32f * f * f, d = k * Math.min(w, h) * 0.022f, s = Math.min(w, h) * 0.022f;
            rect(g, 0, d, d + s, h - 2 * d, a(0x000000, al * 0.6f)); rect(g, w - d - s, d, d + s, h - 2 * d, a(0x000000, al * 0.6f));
        }
        for (int k = 0; k < 220; k++) { float x = GRAIN.nextFloat() * w, y = GRAIN.nextFloat() * h; rect(g, x, y, 1, 1, a(GRAIN.nextBoolean() ? 0xFFFFFF : 0x000000, 0.06f + 0.05f * GRAIN.nextFloat())); }
    }
    /** 「输了」那一下：四边一圈暗红压进来再退掉。 */
    private static void redPulse(GuiGraphics g, int w, int h, float k) {
        int n = 8; float al = 0.5f * k * k;
        for (int i = 0; i < n; i++) { float f = (n - i) / (float) n, d = i * Math.min(w, h) * 0.03f, s = Math.min(w, h) * 0.03f;
            rect(g, 0, 0, w, d + s, a(0x5a0000, al * f * f * 0.5f)); rect(g, 0, h - d - s, w, d + s, a(0x5a0000, al * f * f * 0.5f));
            rect(g, 0, 0, d + s, h, a(0x5a0000, al * f * f * 0.5f)); rect(g, w - d - s, 0, d + s, h, a(0x5a0000, al * f * f * 0.5f)); }
    }
    /** 小字拉开字距：「第一幕」→「第 一 幕」。 */
    private static String spaced(String v) {
        StringBuilder b = new StringBuilder();
        int prev = ' ';
        for (int i = 0; i < v.length(); ) { int c = v.codePointAt(i); if (b.length() > 0 && !Character.isWhitespace(c) && !Character.isWhitespace(prev) && c != 0x3000 && prev != 0x3000) b.append(' '); b.appendCodePoint(c); prev = c; i += Character.charCount(c); }
        return b.toString();
    }

    /** 一整条宽的暗：从 y0 往下越来越暗，到 y1（下面黑边）最暗。没有边、没有框。 */
    private static void fadeUp(GuiGraphics g, int w, float y0, float y1, float alpha) {
        int n = 14; float step = (y1 - y0) / n;
        for (int k = 0; k < n; k++) { float f = (k + 1f) / n, ya = Math.round(y0 + k * step), yb = Math.round(y0 + (k + 1) * step); rect(g, 0, ya, w, yb - ya, a(0x000000, alpha * f * f)); }   // 整数边、不重叠：重叠那一条会多暗一道横纹
    }

    /** 一整条宽的暗带：中间最暗，上下两头淡出去。 */
    private static void band(GuiGraphics g, int w, float y0, float y1, float alpha) {
        int n = 16; float step = (y1 - y0) / n;
        for (int k = 0; k < n; k++) { float m = 1 - Math.abs((k + 0.5f) / n * 2 - 1), ya = Math.round(y0 + k * step), yb = Math.round(y0 + (k + 1) * step); rect(g, 0, ya, w, yb - ya, a(0x000000, alpha * (float) Math.sqrt(m))); }
    }

    /**
     * 开场的台词（11-23：「这个预览，其实要底下有个字幕台词之类的，要让我来读，不然的话没有感觉」）：
     * 插件按段发过来（config.yml 的 op-lines，主播自己改），像电影字幕一样放在下面黑边里，一段一句；主播照着读，观众也看得到。
     */
    private static final java.util.Map<String, String> OP_LINES = new java.util.HashMap<>();
    private static void opLine(GuiGraphics g, int w, int h, float bars, float t) {
        String key; float a, b;
        if (t >= 6.0f && t < 10.8f) { key = "light1"; a = 6.0f; b = 10.8f; }
        else if (t >= 10.8f && t < T_MONT) { key = "light2"; a = 11.0f; b = T_MONT - 0.15f; }
        else if (t >= T_MONT && t < T_LINE) { int k = Math.min(5, (int) ((t - T_MONT) / MONT)); key = "act" + k; a = T_MONT + k * MONT + 0.1f; b = a + MONT - 0.15f; }
        else if (t >= T_LINE && t < T_HOST) { key = "line"; a = T_LINE + 0.6f; b = T_HOST - 0.15f; }
        else if (t >= T_HOST && t < T_DARE) { key = "host"; a = T_HOST + 0.5f; b = T_DARE - 0.15f; }
        else if (t >= T_DARE && t < T_TITLE) { key = "dare"; a = T_DARE + 0.4f; b = T_TITLE - 0.15f; }
        else return;
        String v = OP_LINES.getOrDefault(key, ""); if (v.isEmpty()) return;
        float al = span(t, a, a + 0.18f) * (1 - span(t, b - 0.18f, b)); if (al <= 0.01f) return;
        float sc = 1.3f;   // 主播要照着念：比别的小字大一号
        text(g, Face.MEDIUM, v, w / 2f, h - bars / 2 - Brand.LINE_H * sc / 2, sc, 1, INK, al, false);
    }

    /** 片名：金色大字「赫朝一掷千金游戏」；下面黑边里一行小字接下来是哪一幕。不写介绍词。 */
    private static void title(GuiGraphics g, int w, int h, float t) {
        float al = span(t, 0.7f, 1.6f), rise = 4 * (1 - out(span(t, 0.7f, 1.9f)));
        float sc = 2.5f, y = h * 0.40f;
        if (al > 0) {
            band(g, w, y - D_UP * sc - 26, y + D_DN * sc + 30, al * 0.6f);
            line(g, Face.DISPLAY, w / 2f, y + rise, sc, al, "赫朝一掷千金游戏", GOLD);
        }
        float a3 = span(t, 2.6f, 3.3f);
        if (a3 > 0 && !next.isEmpty()) text(g, Face.MEDIUM, spaced(next), w / 2f, h - h * 0.105f + (h * 0.105f - 9) / 2, 0.8f, 1, INK_SOFT, a3, false);
    }
}
