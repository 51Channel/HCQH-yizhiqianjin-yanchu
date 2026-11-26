package world.hechao.zhidou.game;

import static world.hechao.zhidou.game.Demo.v;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;

/**
 * 每一场开始前的规则演示（照影视解说：镜头在场景里，几个示范的人把这一场从头到尾演一遍，底下一步一条字幕）。
 * 只演怎么玩、输了会怎样；不念扣几颗心、每轮掉几个人这些数（11-17 深夜主播：「你只要介绍游戏怎么玩」）。
 * 示范的人是临时的人偶（没有名字），演完删掉；场景（翻板、通电、牢门、吊篮、牌）演完改回去。
 * 播放：Match.playDemo；镜头和字在模组 Cine 的 demo 模式里画。
 */
final class Demos {
    private Demos() {}
    static final double C = Venue.C;
    static double[] ring(double r, double th, double y) { double a = Math.toRadians(th); return v(C + r * Math.sin(a), y, C - r * Math.cos(a)); }
    static double[] pv(Location l) { return v(l.getX(), l.getY(), l.getZ()); }

    /** 示范的人按时间走：track = {{秒, x, z}, …}，两点之间匀速走、脸朝走的方向；y 由 yAt(x, z) 给。 */
    interface YAt { double y(double x, double z); }
    static void track(Match m, Demo d, java.util.function.Supplier<Entity> es, YAt yAt, double[]... pts) {
        for (int i = 0; i + 1 < pts.length; i++) {
            double[] a = pts[i], b = pts[i + 1];
            int n = Math.max(1, (int) Math.round((b[0] - a[0]) * 20));
            boolean still = Math.abs(b[1] - a[1]) + Math.abs(b[2] - a[2]) < 1e-3;
            float yaw = still ? Float.NaN : Venue.yawTo(a[1], a[2], b[1], b[2]);
            d.at(a[0], () -> m.demoTween(d, n, k -> {
                Entity e = es.get(); if (e == null || !e.isValid()) return;
                double x = a[1] + (b[1] - a[1]) * k, z = a[2] + (b[2] - a[2]) * k;
                e.teleport(new Location(m.world, x, yAt.y(x, z), z, Float.isNaN(yaw) ? e.getLocation().getYaw() : yaw, 0));
            }));
        }
    }
    /** 往下掉：t0 秒开始，ticks 刻里掉 dy 格（越掉越快），同时往旁边偏 (dx, dz)。 */
    static void fall(Match m, Demo d, java.util.function.Supplier<Entity> es, double t0, double dy, int ticks, double dx, double dz) {
        d.at(t0, () -> {
            m.soundV("hechao_zhidou:zd.plunge", 0.7f, 1f);
            Entity e = es.get(); if (e == null || !e.isValid()) return;
            Location from = e.getLocation();
            m.demoTween(d, ticks, k -> { if (e.isValid()) e.teleport(from.clone().add(dx * k, -dy * k * k, dz * k)); });
        });
    }
    /** 过场里 t 秒响一声（耳边放；11-23「专业电影的影视级音效……所有的那些配音都要齐全」：画面里每个动作都要有声音）。 */
    static void sfx(Match m, Demo d, double t, String n, float v, float p) { d.at(t, () -> m.soundV(Match.Z + n, v, p)); }
    /**
     * 演示里也先教会「配乐一停就要出事」（11-26 外部视听方案第二版第 10 条，用户：「动态配乐是对的……我让它的这整个问题就是为了修这个东西了」）：
     * t 秒把讲规则的配乐 0.2 秒抽干，hold 秒以后 1.5 秒淡回来（演示结束也会淡回来）。
     */
    static void vac(Match m, Demo d, double t, double hold) { d.at(t, () -> m.vacuum(Match.tk(hold))); }
    static void puff(Match m, Location l) { m.world.spawnParticle(Particle.CLOUD, l.clone().add(0, 0.8, 0), 30, 0.35, 0.5, 0.35, 0.03, null, true); m.sfx(l, "hechao_zhidou:zd.slide", 0.6f, 1.6f); }
    static void splash(Match m, Entity e) {
        m.sfx(e.getLocation(), "hechao_zhidou:zd.splash", 0.9f, 1f);
        Location l = e.getLocation(); l.setY(60.2);
        m.world.spawnParticle(Particle.SPLASH, l, 60, 0.5, 0.1, 0.5, 0.3, null, true);
        m.world.spawnParticle(Particle.BUBBLE_POP, l, 12, 0.4, 0.1, 0.4, 0.05, null, true);
    }

    // ================= 第一幕 · 零点八 =================
    /**
     * 11-18 用户：「说明一定要先最浅显的说明……场上有这么多数字，所有玩家选到的数乘以零点八，离得最近的才可以不会被掉下去」
     * 「不要说这个十几的数，是先要让人想到哦，所有人选一百，那我就选八十。那如果人选八十呢，那我就再选六十，要这样子让他自己想出这个思维」。
     * 所以：先一句大白话讲怎么赢怎么输 → 例一：四个人选 100、一个人选 80（目标 77，选 80 的站住）→ 例二：大家都这么想、都选 80，
     * 一个人选 60（目标 61，选 60 的站住）；11-25 用户：「选数字的时候避免64这个数字……一个80，一个60」→ 「那你，选几？」——只把想法推两步，不给答案。
     */
    static Demo zero(Match m) {
        Demo d = new Demo(Match.cn(1), Match.A_NAMES[0]);
        double o = 3;   // 11-25 审查：开头 7 秒空镜没有字，压到 3 秒
        d.shot(0, 3, v(C, 108, 84), v(C, 103, 76), v(C, 73, 4), v(C, 73, 4), 58, true);
        // 11-23 用户：「把这种最简单、最低成本、最快速的让人了解完这个游戏的玩法，说那些话就行……最简单一句话去说」——开头先一句话讲完
        // 11-25 审查：第一屏说「离目标最远」却没说目标是什么——小字直接交代
        d.gist(o, 6, "选一个 0 到 100 的数：全场平均乘 0.8 是目标", "离目标最远的人，脚下翻板，掉进水里", ring(13, 75, 87.5), ring(12, 70, 88), ring(39, 255, 76), 50);
        // ① 大白话：有哪些数、怎么算目标、谁站得住
        d.cap(o + 0, o + 5.8, "0 到 100，匿名选数", "用投票器选好，没人看得见", false);
        d.shot(o + 0, o + 6, ring(31, 214, 80.6), ring(31, 236, 80.4), ring(40, 232, 76.3), ring(40, 256, 76.3), 52, true);
        d.cap(o + 6, o + 11.8, "时间到，指针转到目标上", "", false);   // 公式开头说过了，这里只看指针
        d.shot(o + 6, o + 12, ring(4, 180, 92), ring(9, 0, 89), ring(39, Venue.thV(50), 76), ring(39, Venue.thV(50), 76), 60, true);
        d.at(o + 6.4, () -> { m.scene.dt(80); m.scene.needle(50); });
        sfx(m, d, o + 2.0, "dial", 0.9f, 1f); sfx(m, d, o + 2.35, "dial", 0.9f, 1.08f);   // 11-26 外部视听方案第 11 条：匿名选数，拨盘咔哒两下
        sfx(m, d, o + 10.4, "lock", 1f, 1f);   // 11-26 第 11 条：指针停住，原来的 reveal 换成清脆的锁定声
        vac(m, d, o + 10.2, 1.6);   // 指针停住前一拍抽空
        vac(m, d, o + 17.1, 5.2);   // 例一：指针停住 → 翻板 → 落水，全在安静里
        vac(m, d, o + 29.1, 5.1);   // 例二：同上
        // ② 例一：四个人选 100，一个人选 80 → 平均 96 × 0.8 = 76.8，目标 77
        Mannequin[] a = new Mannequin[5];
        // 示范的人站在尺子环上好几个地方（100、80、60），离大门那头最远 70 多格：服务器只给玩家同步 64 格以内的人。
        // 演示时把所有人的本体先挪到坑底正中间（离整圈尺子都在 50 格以内），演完放回原处（选手接着被送去起点）
        java.util.Map<org.bukkit.entity.Player, Location> was = new java.util.HashMap<>();
        d.at(-d.off, () -> {   // 开场就挪（不跟开头那句往后推）
            Location mid = new Location(m.world, C + 2.5, Venue.FY + 0.1, C + 2.5);
            for (org.bukkit.entity.Player pl : m.world.getPlayers()) { was.put(pl, pl.getLocation()); pl.teleport(mid); }
            for (Who w : m.alive()) if (w.player() == null) m.put(w, Venue.start(m.world, 0.6 + 0.4 * m.rnd.nextDouble(), m.rnd.nextDouble()));
        });
        d.cleanup.add(() -> { for (var e : was.entrySet()) if (e.getKey().isOnline()) e.getKey().teleport(e.getValue()); });
        d.cap(o + 12, o + 17.8, "比如：四个人选 100，一个人选 80", "平均 96，乘 0.8，目标是 77", false);
        d.still(o + 12, o + 15, ring(31, Venue.thV(100) - 11, 79.6), ring(39, Venue.thV(100), 76.9), 56);   // 从侧面拍：一块板上四个人一字排开，不叠成一个
        d.still(o + 15, o + 18, ring(29, Venue.thV(79), 80), ring(39, (Venue.thV(78) + 1.5), 76.8), 60);
        d.at(o + 12.2, () -> {
            m.scene.needlePark();
            double[] al = {0.12, 0.37, 0.63, 0.88};
            for (int i = 0; i < 4; i++) { Location l = Venue.onPlate(m.world, 100, al[i]); a[i] = m.demoActor(d, l, i + 3); puff(m, l); }
            Location l = Venue.onPlate(m.world, 80, 0.5); a[4] = m.demoActor(d, l, 7); puff(m, l);
        });
        d.at(o + 15.8, () -> { m.scene.dt(30); m.scene.needle(77); m.scene.mark(77, 0); });
        sfx(m, d, o + 17.3, "lock", 1f, 1f);   // 11-26：指针停住（锁定声）
        d.cap(o + 18, o + 23.8, "选 100 的最远", "四个人一起掉进水里，会受伤", true);
        d.still(o + 18, o + 24, ring(30, Venue.thV(100) - 12, 80.4), ring(39, Venue.thV(100), 76.3), 58);
        d.at(o + 18.3, () -> { m.scene.mark(100, 1); m.scene.mark(80, 0); });
        d.at(o + 20.3, () -> { m.scene.drop(100); m.soundV("hechao_zhidou:zd.trap", 1f, 0.95f); for (int i = 0; i < 4; i++) sink(m, d, a[i]); });
        // ③ 例二：大家都这么想，都选 80 → 一个人选 60：平均 76 × 0.8 = 60.8，目标 61（11-25 用户：「避免64这个数字……一个80，一个60」）
        d.cap(o + 24, o + 29.8, "再看一组：四人选 80，一人选 60", "平均 76，乘 0.8，目标变成 61", false);
        d.still(o + 24, o + 27, ring(31, Venue.thV(80) + 11, 79.6), ring(39, Venue.thV(80), 76.9), 56);
        d.still(o + 27, o + 30, ring(29, (Venue.thV(62) + 1.5), 80), ring(39, (Venue.thV(62) + 1.5), 76.8), 60);
        d.at(o + 24.1, () -> {
            m.scene.plateReset(); m.scene.needlePark(); m.scene.mark(77, -1); m.scene.mark(80, -1);
            double[] al = {0.12, 0.37, 0.63};
            for (int i = 0; i < 3; i++) place(m, a[i], Venue.onPlate(m.world, 80, al[i]));
            place(m, a[4], Venue.onPlate(m.world, 80, 0.88));
            place(m, a[3], Venue.onPlate(m.world, 60, 0.5));
        });
        d.at(o + 27.8, () -> { m.scene.dt(30); m.scene.needle(61); m.scene.mark(61, 0); });
        sfx(m, d, o + 29.3, "lock", 1f, 1f);   // 11-26：指针停住（锁定声）
        d.cap(o + 30, o + 35.3, "这一次，选 80 的最远", "四个人一起掉下去，选 60 的安全", true);
        d.still(o + 30, o + 35.5, ring(30, Venue.thV(80) + 12, 80.4), ring(39, Venue.thV(80), 76.3), 58);
        d.at(o + 30.3, () -> { m.scene.mark(80, 1); m.scene.mark(60, 0); });
        d.at(o + 32.2, () -> { m.scene.drop(80); m.soundV("hechao_zhidou:zd.trap", 1f, 1.05f); for (int i : new int[]{0, 1, 2, 4}) sink(m, d, a[i]); });
        // ④ 不给答案，留给他自己想
        // 11-25 审查：原来对着空场干念——同一个机位看翻板合上、掉下去的两个人爬回板上
        d.cap(o + 35.5, o + 41, "前几轮掉下去，还能爬回来接着选", "最后几轮掉下去，这一场出局", false);
        d.still(o + 35.5, o + 41.5, ring(30, Venue.thV(80) + 12, 80.4), ring(39, Venue.thV(80), 76.3), 58);
        d.at(o + 36.6, () -> m.scene.plateReset());
        d.at(o + 37.8, () -> { place(m, a[0], Venue.onPlate(m.world, 80, 0.3)); place(m, a[1], Venue.onPlate(m.world, 82, 0.6)); });
        d.cleanup.add(() -> { m.scene.plateReset(); m.scene.needlePark(); m.scene.ballsPark(); for (int v : new int[]{50, 60, 61, 77, 80, 100}) m.scene.mark(v, -1); });
        return d;
    }
    /** 示范的人从翻开的板子上掉进护城河。 */
    static void sink(Match m, Demo d, Mannequin e) {
        if (e == null || !e.isValid()) return;
        Location from = e.getLocation(); double y0 = from.getY();
        m.demoTween(d, 12, k -> { if (e.isValid()) { Location l = from.clone(); l.setY(y0 - (y0 - 58.4) * k * k); e.teleport(l); } });
        m.later(16, () -> { if (e.isValid()) splash(m, e); });
    }
    /** 让 e 转过来面朝 t（只转身子，不挪位置）。 */
    static void face(Entity e, Entity t) {
        if (e == null || t == null) return;
        double dx = t.getX() - e.getX(), dz = t.getZ() - e.getZ();
        e.setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0);
    }
    /** 示范的人换到另一块板上（冒一下烟）。 */
    static void place(Match m, Mannequin e, Location l) { if (e == null || !e.isValid()) return; e.teleport(l); puff(m, l); }
    /** 「物品栏上的心」大字落下后几秒放三下「冰」（和模组 Cine.BING_AFTER 一致）。 */
    static final double BING_AFTER = 0.9;
    /**
     * 整局只讲一次的「游戏目标」（11-25 用户：「先把游戏目标讲好，然后再去讲游戏，这个演出才是对的」）：
     * 开场片头放完、第一幕的片名卡之前，单独一段：心就是命（自己的第一人称，红圈圈住心）→ 输了受伤、心扣光出局 → 越往后伤得越重、撑到最后的去决战。
     */
    static Demo goal(Match m) {
        Demo d = new Demo("开始之前", "游戏目标"); d.word = "赫朝一掷千金游戏";
        // 11-25 用户：「黑场切到玩家自己的视角，然后画面上出现一个红色的圆圈加一个红色的箭头指着自己的心……然后这时候播放一个网络上特别搞笑的音效……冰冰冰」
        // 前两屏不再拍漏斗场：片名卡停黑之后亮起来就是每个人自己的第一人称（看着坑底），红圈圈住物品栏上的心、红箭头指过去，三下「冰」
        // 11-25 实拍：主持人、来看的人被放演示挪到了坑底正中间（对着王座）——用户：「应该把我放到那个开场的位置，跟其他玩家站在一起」
        d.at(-0.8, () -> {   // 片名卡停黑的时候：不在起点平台上的人也送过去，每个人转过来看着坑底、稍微往下看（演完放演示的收尾会送回原处）
            for (org.bukkit.entity.Player p : m.world.getPlayers()) {
                Who w = m.whoOf(p);
                Location l = w != null && w != m.host && w.alive ? p.getLocation() : Venue.start(m.world, m.rnd.nextDouble(), m.rnd.nextDouble());
                double dx = C - l.getX(), dz = C - l.getZ();
                if (dx * dx + dz * dz < 4) continue;
                l.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz))); l.setPitch(12); p.teleport(l);
            }
        });
        d.self(0, 10.4);
        d.cap(0.6, 5.0, "物品栏上的心，就是你的命", "", false, false).heartMark();
        sfx(m, d, 0.6 + BING_AFTER, "bing", 1f, 1f);   // 大字落下 0.9 秒后三下「冰」：红圈、红箭头、「就是你的命」一下一个（模组 Cine.BING_AFTER 同一个数）
        d.cap(5.2, 10.0, "每一场游戏输了，都会受伤", "心扣光，就出局", true, false);
        vac(m, d, 4.95, 2.6);   // 11-26 外部视听方案第二版第 8 条：最后一颗心空掉前一拍配乐骤停   // 还是自己的第一人称：红圈留着，最后一颗心空掉
        // 11-25 审查：「六场游戏、1 个人通关」片头已经说过——这里补片头没说的：出局了去哪
        // 镜头在北边高处慢慢推（11-25「跳得太快了」「怎么全部运到那个球上了」：六个铁球停在南边起点平台头顶，别从那边拍）
        d.cap(10.9, 16.0, "越往后的游戏，伤得越重", "撑到最后的人，坐上坑底牌桌和主持人决战", false, false);   // 压黑切回漏斗场（模组在自己视角进出都压黑）
        d.shot(10.4, 16.4, ring(47, 352, 119), ring(45, 6, 118), v(C, 66, C), v(C, 66, C), 60, true);   // 11-25 实拍：r57 高 102 时外圈吊环和指针尖的钢塔横在字幕上；退进吊环里、抬到 118 往下看就干净了
        return d;
    }

    /** 三道门 / 撞号：几个人被送进自己选的门（段），人最多的那道整道翻下去。 */
    static Demo groups(Match m, int mode) {
        Demo d = new Demo(Match.cn(1), Match.A_NAMES[mode]);
        boolean door = mode == 1;
        int[] picks = door ? new int[]{6, 14, 22, 92} : new int[]{3, 6, 9, 15, 95};
        int bad = 0;
        double o = 3;   // 11-25 审查：开头空镜压到 3 秒
        d.shot(0, 3, v(C, 108, 84), v(C, 103, 76), v(C, 73, 4), v(C, 73, 4), 58, true);
        d.gist(o, 5.5, door ? "选一道门站进去：人最多的那道门，整道翻下去" : "选一段站进去：人最多的那一段，整段翻下去", "掉下去会受伤", ring(13, 75, 87.5), ring(12, 70, 88), ring(39, 255, 76), 50);
        d.cap(o, o + 5.8, door ? "尺子环分成 A、B、C 三道门" : "尺子环分成 1 到 10 十段", door ? "在投票器上匿名选一道" : "在投票器上匿名选一段", false);
        d.shot(o, o + 6, ring(31, 214, 80.6), ring(31, 236, 80.4), ring(40, 232, 76.3), ring(40, 256, 76.3), 52, true);
        d.cap(o + 6, o + 11.8, door ? "时间到，所有人被送进自己选的门" : "时间到，所有人被送到自己选的那一段", door ? "A 门 3 个人，C 门 1 个人" : "第 1 段 3 个人，第 2 段、第 10 段各 1 个人", false);
        d.shot(o + 6, o + 12, v(C, 104, 46), v(C, 102, 42), v(C - 18, 74, C - 6), v(C - 18, 74, C - 6), 62, true);
        Mannequin[] a = new Mannequin[picks.length];
        d.at(-d.off, () -> { for (Who w : m.alive()) m.put(w, Venue.start(m.world, 0.6 + 0.4 * m.rnd.nextDouble(), m.rnd.nextDouble())); });
        d.at(o + 6.5, () -> { for (int i = 0; i < picks.length; i++) { Location l = Venue.onPlate(m.world, picks[i], 0.5); a[i] = m.demoActor(d, l, i + 3); puff(m, l); } });
        d.cap(o + 12, o + 18.8, door ? "人最多的门，整道翻下去" : "人最多的段，整段翻下去", "没投票，一样掉下去", true);
        d.shot(o + 12, o + 19, v(C, 104, 46), v(C, 101, 40), v(C - 18, 74, C - 6), v(C - 20, 72, C - 6), 62, true);
        d.at(o + 12.4, () -> { for (int v = 0; v <= 100; v++) if ((door ? Rules.door(v) : Rules.segment(v)) == bad) m.scene.mark(v, 1); });
        sfx(m, d, o + 15.2, "trap", 1f, 0.95f);   // 11-23 音效只留这一下（「音效不是说你加满它就是好的」）
        d.at(o + 15.2, () -> {
            for (int v = 0; v <= 100; v++) if ((door ? Rules.door(v) : Rules.segment(v)) == bad) m.scene.drop(v);
            for (int i = 0; i < picks.length; i++) if ((door ? Rules.door(picks[i]) : Rules.segment(picks[i])) == bad && a[i] != null) {
                Mannequin e = a[i]; Location from = e.getLocation(); double y0 = from.getY();
                m.demoTween(d, 12, k -> { if (e.isValid()) { Location l = from.clone(); l.setY(y0 - (y0 - 58.4) * k * k); e.teleport(l); } });
                m.later(16, () -> { if (e.isValid()) splash(m, e); });
            }
        });
        d.cap(o + 19, o + 24, "掉下去，这一场直接出局", "", false);
        d.shot(o + 19, o + 24.5, ring(30, 238, 87), ring(30, 242, 86), ring(41, 250, 61), ring(41, 250, 61), 56, true);
        d.cleanup.add(() -> { m.scene.plateReset(); m.scene.needlePark(); m.scene.ballsPark(); });
        return d;
    }

    // ================= 第二幕 · 高压 =================
    /** 梁面多高（11-22 起没有断口，一样高）。 */
    static double beamY(double x) { double t = Venue.beamTop(x); return Double.isNaN(t) ? Venue.B_Y : t; }
    /** 走钢梁的人：从 (t0, x0) 走到 (t1, x1)，身子左右晃（每 period 秒晃一下，晃 amp 格）。 */
    static void wobble(Match m, Demo d, java.util.function.Supplier<Entity> es, double z, double t0, double x0, double t1, double x1, double period, double amp) {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        int n = Math.max(1, (int) Math.round((t1 - t0) / period));
        for (int i = 0; i <= n; i++) { double f = i / (double) n; pts.add(new double[]{t0 + (t1 - t0) * f, x0 + (x1 - x0) * f, z + (i == 0 || i == n ? 0 : (i % 2 == 0 ? amp : -amp))}); }
        track(m, d, es, (x, zz) -> beamY(x), pts.toArray(new double[0][]));
    }
    /** 演示里电从起点往前爬：t0 秒起点隔间先亮，然后每 per 秒亮一段（5 格）。 */
    static void crawl(Match m, Demo d, int k, double t0, double per) {
        d.at(t0, () -> m.scene.set("bzb" + (k + 1) + ".var", 0));   // 声音在 bridge 里只响一次 surge（四根梁一起通电，不叠四声）
        for (int i = 0; i < Venue.B_NCH; i++) {
            final int c = i;
            d.at(t0 + 0.2 + i * per, () -> { m.scene.set("bz" + (k + 1) + "_" + c + ".var", 0); int x = Venue.B_GATE + c * Venue.B_CHUNK; m.sparks(k, x, x + 2); });
        }
    }
    /**
     * 11-22 重做：一人一根梁。示范四个人：一个人在宽梁上走、身子左右晃（往反方向按 A / D 扶正）；起风了一个蹲下、一个站着被吹下去、
     * 被接住送回起点；后半段细钢管上晃得更厉害；最前面那个人走到对面，电从每根梁的起点追上来——起点隔间里的人被电下去，
     * 钢管上的人被追上掉下去。
     */
    static Demo bridge(Match m) {
        Demo d = new Demo(Match.cn(Match.PHYS), Match.N_PHYS);
        int k0 = 4, k1 = 5, k2 = 6, k3 = 3;   // 11-25 梁从 30 根减到 10 根，中间那几根
        double z0 = Venue.beamZ(k0), z1 = Venue.beamZ(k1), z2 = Venue.beamZ(k2), z3 = Venue.beamZ(k3), BZ = Venue.BZ, Y = Venue.B_Y;
        YAt y = (x, zz) -> beamY(x);
        Mannequin[] p = new Mannequin[4];
        d.shot(0, 3, v(20, 142, BZ + 64), v(22, 138, BZ + 58), v(24, 118, BZ - 4), v(24, 118, BZ - 4), 64, true);
        // 11-25 审查：「名额有限、超时受伤」原来拖到最后一屏才说，前面看不懂为什么要急、为什么要推人——放进第一屏
        d.gist(3, 5.5, "抢在别人前面，走过钢梁：每根梁名额有限，过满通电", "时间到没走到对面，会受伤", v(22, 138, BZ + 58), v(18, 133, BZ + 50), v(24, 118, BZ - 4), 64);
        d.at(0.1 - d.off, () -> {   // 一开场就有人站在梁上：远景里看得见
            p[0] = m.demoActor(d, new Location(m.world, -2, Y, z0, -90, 0), 3);
            p[1] = m.demoActor(d, new Location(m.world, 13, Y, z1, -90, 0), 4);
            p[2] = m.demoActor(d, new Location(m.world, 15.5, Y, z2, -90, 0), 5);
            p[3] = m.demoActor(d, new Location(m.world, 40, Y, z3, -90, 0), 6);
        });
        // ① 梁会晃
        d.cap(3, 8.8, "钢梁会左右晃", "往哪边歪，就反方向按 A / D", false);
        d.shot(3, 9, v(-7, 122.5, z0 + 1.7), v(5, 122.5, z0 + 1.7), v(-1, 120.9, z0), v(11, 120.9, z0), 62, false);
        wobble(m, d, () -> p[0], z0, 3, -2, 9, 10, 0.75, 0.22);
        sfx(m, d, 3.4, "beam", 1f, 1f);   // 11-26 外部视听方案第 13 条：钢梁受压吱嘎一声（很轻，不循环）
        // ② 起风：一个蹲下，一个站着被吹下去，被接住送回起点
        d.cap(9, 12.2, "起风了，按住 Shift", "不蹲下，会被直接吹下去", false);
        // 11-25 审查：「掉下去回起点重来」原来写在最后一屏，和「通电之后回不去」前后打架——放到被吹下去、在起点冒出来的这一拍
        d.cap(12.2, 14.8, "掉下去，回起点重来", "", false);
        d.still(9, 12.2, v(16, 123.4, z2 + 5.5), v(14.5, 120.6, z1), 64);
        d.at(9.2, () -> { for (int t = 0; t < 60; t += 3) m.later(t, () -> { if (m.curDemo == d) m.windFx(1); }); });
        sfx(m, d, 9.0, "gust", 0.9f, 1f);   // 11-26 第 13 条：风声比风的粒子早 0.2 秒进
        sfx(m, d, 10.8, "hurt", 0.6f, 1f);
        sfx(m, d, 10.9, "swish", 0.8f, 1f);   // 11-26：被吹下去，一道失重的风切
        d.at(9.6, () -> { if (p[1].isValid()) p[1].setPose(Pose.SNEAKING, true); });
        d.at(13.8, () -> { if (p[1].isValid()) p[1].setPose(Pose.STANDING, false); });
        d.at(10.8, () -> { if (p[2].isValid()) p[2].playHurtAnimation(0); });
        fall(m, d, () -> p[2], 10.9, 13, 18, 0.3, 2.4);
        d.at(12.0, () -> { if (p[2].isValid()) { puff(m, p[2].getLocation()); p[2].teleport(new Location(m.world, -24, Y, z2, -90, 0)); puff(m, p[2].getLocation()); } });
        d.still(12.2, 15, v(-18.5, 122.6, z2 + 3.2), v(-24, 120.8, z2), 62);
        // ③ 后半段只剩细钢管
        d.cap(15, 19.8, "后半段，只剩细钢管", "比前面晃得更厉害", false);
        d.shot(15, 20, v(37, 121.9, z3 + 3), v(43, 121.9, z3 + 3), v(42, 120.5, z3), v(48, 120.5, z3), 62, false);
        wobble(m, d, () -> p[3], z3, 15, 40, 26, 50, 0.55, 0.3);
        // ④ 最前面的人走到对面：电从每根梁的起点追上来
        d.at(19.9, () -> { if (p[0].isValid()) p[0].teleport(new Location(m.world, 62, Y, z0, -90, 0)); });
        wobble(m, d, () -> p[0], z0, 20, 62, 21.6, 68.4, 0.55, 0.18);
        track(m, d, () -> p[0], y, new double[]{21.6, 68.4, z0}, new double[]{22.2, 71.5, z0});
        d.still(20, 22.4, v(63.5, 122.7, z0 + 4.2), v(68.5, 120.8, z0), 62);
        d.at(21.9, () -> m.soundV("hechao_zhidou:zd.up", 0.8f, 1.2f));
        d.cap(20, 25.8, "有人走到对面，起点通电", "通电之后，掉下去就回不去了", true);
        for (int k : new int[]{k3, k0, k1, k2}) crawl(m, d, k, 22.4, 0.3);
        vac(m, d, 22.0, 2.6);   // 起点通电：合闸、电弧、第一个人被电下去，配乐抽空
        vac(m, d, 26.1, 1.8);   // 电追上钢管上的人
        sfx(m, d, 22.2, "breaker", 1f, 1f); sfx(m, d, 22.5, "arc", 0.8f, 1f);   // 11-26 第 14 条：起点合闸「哐」一声，接电弧往前爬
        d.shot(22.4, 26, v(-15, 124.6, z2 + 4.5), v(-7, 124.6, z2 + 4.5), v(-22, 120.4, z0 + 1.5), v(-3, 120.4, z0 + 1.5), 66, true);
        d.at(22.6, () -> { if (p[2].isValid()) { p[2].playHurtAnimation(0); m.world.spawnParticle(Particle.ELECTRIC_SPARK, p[2].getLocation().add(0, 1, 0), 60, 0.3, 0.5, 0.3, 0.3, null, true); } });
        fall(m, d, () -> p[2], 22.7, 14, 18, 0, 1.5);
        sfx(m, d, 22.6, "shock", 0.9f, 1.05f);   // 11-23 音效只留这一下（「音效不是说你加满它就是好的」）
        // ⑤ 电越爬越快，追上钢管上的人
        d.cap(26, 30.8, "电越爬越快", "被追上，就掉下去", true);
        d.still(26, 31, v(54, 122.8, z3 + 4.2), v(47, 120.6, z3), 62);
        d.at(26.3, () -> { if (p[3].isValid()) { p[3].playHurtAnimation(0); m.world.spawnParticle(Particle.ELECTRIC_SPARK, p[3].getLocation().add(0, 1, 0), 60, 0.3, 0.5, 0.3, 0.3, null, true); } });
        fall(m, d, () -> p[3], 26.4, 14, 18, 0, 1.6);
        sfx(m, d, 26.3, "shock", 1f, 1f);   // 11-23 音效只留这一下（「音效不是说你加满它就是好的」）
        // 11-25 审查：原来对着远景干念——真演一次：后面的人追上来推一把，前面的人晃两下掉下去（最后一屏「名额有限」挪到开头了）
        d.cap(31, 36, "挡路的人，左键能推", "被推的人照屏幕提示按鼠标，按错就掉下去", true);
        double z7 = Venue.beamZ(7);   // 换一根没通电的梁演（前面通电的那几根梁上还亮着电）
        Mannequin[] pu = new Mannequin[2];
        d.at(30.9, () -> {
            pu[0] = m.demoActor(d, new Location(m.world, 24, Y, z7, -90, 0), 9);
            pu[1] = m.demoActor(d, new Location(m.world, 21.2, Y, z7, -90, 0), 8);
        });
        track(m, d, () -> pu[1], y, new double[]{31.1, 21.2, z7}, new double[]{32.4, 23.0, z7});
        d.still(31, 36.5, v(19.5, 122.3, z7 + 3.4), v(23.5, 120.6, z7), 60);
        d.at(32.5, () -> { if (pu[1] != null && pu[1].isValid()) pu[1].swingMainHand(); if (pu[0] != null && pu[0].isValid()) pu[0].playHurtAnimation(0); });
        wobble(m, d, () -> pu[0], z7, 32.6, 24, 33.6, 24.6, 0.25, 0.35);
        fall(m, d, () -> pu[0], 33.7, 14, 18, 0.3, 2.2);
        sfx(m, d, 32.5, "hurt", 0.6f, 1.1f);
        sfx(m, d, 32.5, "punch", 0.9f, 1f); sfx(m, d, 32.8, "uiwarn", 0.8f, 1f);   // 11-26 第 14 条：推人一声闷响，被推的人屏幕提示音
        d.cleanup.add(() -> {
            for (int b = 0; b < Venue.B_N; b++) {
                m.scene.set("bzb" + (b + 1) + ".var", -1);
                for (int i = 0; i < Venue.B_NCH; i++) m.scene.set("bz" + (b + 1) + "_" + i + ".var", -1);
            }
        });
        return d;
    }

    // ================= 第三幕 · 盲牌 =================
    /**
     * 三个人头上各一个花色（两个方块、一个红心），走进方块的牢区：先到的两个人各进了一间亮绿灯的房间（其中一个其实是红心，进错了），
     * 晚到的那个没有房间。门关上、揭晓：进对的门打开；进错的项圈放电；没进房间的也放电。
     */
    static Demo prison(Match m) {
        Demo d = new Demo(Match.cn(2), Match.N_HALL);
        int Z = 2;   // 南边的方块区
        double HX = Venue.HX, HZ = Venue.HZ, PY = Venue.P_Y;
        int[] col = {2, 1, 2};
        Who[] w = new Who[3];
        YAt y = (x, zz) -> PY;
        // 11-25 第二版审查：① 第一屏先说「自己看不见、要问别人」；② 大字原来正好压在看守所柱子的规则牌上（白字叠白字）——镜头往左转、压低一点，柱子挪到画面右边，前排的人背后的花色拍大一些
        double[] gl = v(HX + 5.5, 70.6, HZ + 10);
        d.shot(0, 3, v(HX + 8, 75.6, HZ - 8), v(HX + 7, 75.3, HZ - 7), gl, gl, 62, true);
        d.gist(3, 6, "自己看不见背后的花色：只能问别人，进对应房间", "进错了，项圈放电，这一场直接出局", v(HX + 7, 75.3, HZ - 7), v(HX + 6.2, 75.0, HZ - 6.2), gl, 62);
        // 演示的时候场上的人先请到北边牢区里（镜头都在南边，拍不到），演完回看守所、戴回项圈——不然三个示范的人混在三十个人里认不出来
        List<Who> crowd = new java.util.ArrayList<>(m.alive());
        d.at(2.95, () -> {   // 开头那句大白话的镜头切走的那一刻再请走（那个镜头里大家头上的花色正好看得见）
            java.util.Random r = new java.util.Random(7);
            for (Who x : crowd) { m.lampOff(x); double[] q = Venue.pw(0, 18 + r.nextDouble() * 14, (r.nextDouble() * 2 - 1) * 2.2); m.put(x, Venue.at(m.world, q[0], PY, q[1], Venue.HX, Venue.HZ)); }
        });
        d.cleanup.add(() -> { java.util.Random r = new java.util.Random(); for (Who x : crowd) if (x.alive) { m.put(x, Venue.hallSpot(m.world, r)); m.lampOn(x); } });
        d.at(2.97, () -> {
            double[][] st = {Venue.pw(Z, 6.5, -1.5), Venue.pw(Z, 6.5, 0), Venue.pw(Z, 6.6, 1.5)};
            for (int i = 0; i < 3; i++) {
                Mannequin e = m.demoActor(d, new Location(m.world, st[i][0], PY, st[i][1], 180, 0), i + 7);   // 先背对镜头：背后的花色看得见
                w[i] = new Who("示范", null, e); w[i].color = col[i]; m.lampOn(w[i]);
            }
        });
        d.cap(3, 8.8, "转过身，给别人看你的花色", "按住 Shift 蹲下，能把牌盖住", false);
        sfx(m, d, 5.6, "cloth", 1f, 1f);   // 11-26 外部视听方案第 15 条：蹲下盖牌，衣服「刷」一声
        d.at(5.6, () -> { if (w[2] != null) { w[2].covered = true; m.lampOn(w[2]); if (w[2].dummy != null) w[2].dummy.setPose(Pose.SNEAKING, true); } });    // 右边那个人蹲下把牌盖上，再站起来
        d.at(8.0, () -> { if (w[2] != null) { w[2].covered = false; m.lampOn(w[2]); if (w[2].dummy != null) w[2].dummy.setPose(Pose.STANDING, true); } });
        // 11-25 审查：原来三个人一直背对镜头，接着就分头进房，看不出是怎么知道自己花色的——左边两个先互相看一眼背后
        d.at(6.2, () -> { if (w[0] != null && w[1] != null) face(w[1].body(), w[0].body()); });   // 中间那个转过来看左边那个的背
        d.at(7.4, () -> { if (w[0] != null && w[1] != null) face(w[0].body(), w[1].body()); });   // 左边那个也转过来，看中间那个
        d.at(8.6, () -> { for (int i = 0; i < 2; i++) { Entity e = w[i] == null ? null : w[i].body(); if (e != null) e.setRotation(180, 0); } });
        d.still(3, 9, v(HX, 73.6, HZ + 9.6), v(HX, 71.5, HZ + 4.4), 50);   // 11-25「牌子被中间那个人的头挡住了」：机位抬高往下看，后面柱子上的规则牌露在三个人头顶上面   // 从牢区里往回看：三个人背对镜头，项圈后面的花色牌都在画面里
        d.cap(9, 15.3, "问清自己的花色，再走进那一区的房间", "告诉你花色的人，可能在骗你", false);
        d.still(9, 15.5, v(HX, 72.6, HZ + 21), v(HX, 70.9, HZ + 11.5), 62);   // 从走廊尽头往回看：人朝镜头走过来，分头进两边的房间
        d.at(9.1, () -> { m.setCell(Z, 0, true); m.setCell(Z, 1, true); });
        sfx(m, d, 9.1, "beep", 0.9f, 1f);   // 11-26 第 15 条：房间亮绿灯「滴」
        sfx(m, d, 16.4, "pulse", 0.8f, 1f);   // 11-26 第 15 条：没进房间的人在门外转来转去，两下心跳（方案说拿不准，嫌杂删这一行）
        {
            double[] a0 = Venue.pw(Z, 6.5, -1.5), a1 = Venue.pw(Z, 10.5, -0.6), a2 = Venue.pw(Z, 16, -2.2), a3 = Venue.pw(Z, 16, -4.0), a4 = Venue.pw(Z, 16, -5.8);
            track(m, d, () -> w[0] == null ? null : w[0].body(), y, new double[]{9.3, a0[0], a0[1]}, new double[]{10.6, a1[0], a1[1]}, new double[]{12.5, a2[0], a2[1]}, new double[]{13.0, a3[0], a3[1]}, new double[]{13.6, a4[0], a4[1]});
            double[] b0 = Venue.pw(Z, 6.5, 0), b1 = Venue.pw(Z, 10.5, 0.6), b2 = Venue.pw(Z, 16, 2.2), b3 = Venue.pw(Z, 16, 4.0), b4 = Venue.pw(Z, 16, 5.8);
            track(m, d, () -> w[1] == null ? null : w[1].body(), y, new double[]{9.7, b0[0], b0[1]}, new double[]{11.0, b1[0], b1[1]}, new double[]{12.9, b2[0], b2[1]}, new double[]{13.4, b3[0], b3[1]}, new double[]{14.0, b4[0], b4[1]});
            double[] c0 = Venue.pw(Z, 6.6, 1.5), c1 = Venue.pw(Z, 10.5, 0), c2 = Venue.pw(Z, 15.4, 0), c3 = Venue.pw(Z, 15.45, 0.05);
            track(m, d, () -> w[2] == null ? null : w[2].body(), y, new double[]{11.6, c0[0], c0[1]}, new double[]{13.6, c1[0], c1[1]}, new double[]{15.8, c2[0], c2[1]}, new double[]{16.2, c3[0], c3[1]});
        }
        d.at(13.7, () -> { m.cOwner[Z][1] = w[0]; w[0].cellD = Z; w[0].cellI = 1; m.scene.set("hcl" + Z + "_1.var", 2); });
        d.at(14.1, () -> { m.cOwner[Z][0] = w[1]; w[1].cellD = Z; w[1].cellI = 0; m.scene.set("hcl" + Z + "_0.var", 2); });
        d.cap(15.5, 20.8, "门关上之前，进好房间", "没进房间，一样算错", false);
        d.still(15.5, 21, v(HX, 73.2, HZ + 11), v(HX, 70.8, HZ + 17), 62);
        d.at(16.9, () -> { Entity e = w[2].body(); if (e != null) e.setRotation(90, 0); });
        d.at(17.9, () -> { Entity e = w[2].body(); if (e != null) e.setRotation(-90, 0); });
        d.at(18.9, () -> { Entity e = w[2].body(); if (e != null) e.setRotation(0, 0); });
        d.cap(21, 26, "门一关，当场揭晓", "进对了：绿灯亮、门打开，这一轮活下来", false);
        {   // 11-25：原来和上一步几乎同一个机位，像硬切了一下——改成对的那间（左边 1 号）门口的近景
            // 11-25 实拍：没进房间的那个人半个身子挡在画面右边——机位往左挪 1.8 格，他整个出画，门还在画面里
            double[] j0 = Venue.pw(Z, 11, -1.0), j1 = Venue.pw(Z, 16, -5.2);
            d.still(21, 26.3, v(j0[0], 72.4, j0[1]), v(j1[0], 71.2, j1[1]), 60);
        }
        d.at(21.2, () -> { for (int i = 0; i < 2; i++) { m.setCell(Z, i, false); m.scene.set("hcl" + Z + "_" + i + ".var", 2); } });
        sfx(m, d, 21.2, "slam", 1f, 1f);   // 11-26 第 16 条：实录牢门砸上
        vac(m, d, 21.15, 10.3);   // 门砸上的同一刻抽空：开门、两次放电都是干声，讲内鬼时再回来
        d.at(23.4, () -> {
            m.scene.dt(14); m.scene.set("hcd" + Z + "_1.sw", Venue.P_OPEN); m.scene.set("hcl" + Z + "_1.var", 1);
            int[] b = Venue.doorBlock(Z, 1); for (int yy = 0; yy < 2; yy++) m.world.getBlockAt(b[0], (int) PY + yy, b[1]).setType(Material.AIR, false);
            m.cOpen[Z][1] = true;
        });
        sfx(m, d, 23.4, "door", 0.9f, 1f);   // 11-26：实录重门滑开
        // 11-25「走到这个门口，他穿模了」：不再让人从房间里走出来，只亮绿灯、开门
        // 11-23（「花色的这一段感觉演出有一点问题」）：原来是广角从背后拍，放电的人背对镜头、进错门的人关在门里，画面上看不出发生了什么。
        // 改成两镜：先正面近景看没进房间的那个人被电（他正好朝着镜头），再切到进错的那一间门口，红灯亮、门缝里冒电火花。放电自己有声音（zap），不另加。
        d.cap(26.3, 31.3, "进错或没进房，项圈放电", "会受伤，这一场直接出局", true);   // 11-25 审查：先电的是没进房的那个，字要对上
        d.still(26.3, 28.9, v(HX, 72.0, HZ + 19.4), v(HX, 71.2, HZ + 15.4), 50);
        d.at(26.8, () -> { m.zap(w[2]); if (w[2].body() instanceof org.bukkit.entity.LivingEntity le) le.playHurtAnimation(0); });
        {
            double[] k0 = Venue.pw(Z, 13.4, 1.4), k1 = Venue.pw(Z, 16, 5.4);
            d.still(28.9, 31.5, v(k0[0], 72.0, k0[1]), v(k1[0], 71.0, k1[1]), 55);
        }
        d.at(29.3, () -> m.zap(w[1]));
        // 11-25 审查：原来讲内鬼、重新发牌时对着远景干念——两个人回到一开始那块地方：先描红边互相看（内鬼互相认识），再背过身、花色当场换一张
        d.cap(31.5, 36, m.jacks.size() + " 个内鬼，互相认识", "内鬼全死，好人全赢 · 好人不比内鬼多，内鬼赢", true);
        d.cap(36.2, 40, "每一轮，重新发牌", "一直打到内鬼全死，或者内鬼赢", false);
        d.still(31.5, 40.2, v(HX, 73.6, HZ + 9.6), v(HX, 71.5, HZ + 4.4), 50);
        d.at(31.4, () -> {
            double[] l0 = Venue.pw(Z, 6.5, -1.5), l2 = Venue.pw(Z, 6.6, 1.5);
            Entity e0 = w[0].body(), e2 = w[2].body();
            if (e0 != null) m.move(e0, new Location(m.world, l0[0], PY, l0[1], 180, 0));
            if (e2 != null) { m.move(e2, new Location(m.world, l2[0], PY, l2[1], 180, 0)); if (e2 instanceof org.bukkit.entity.LivingEntity le) le.setPose(Pose.STANDING, true); }
            // 中间那个人也站回原位（背对镜头），挡住地上通往方块牢区的蓝色引导线，不然它正好竖在字幕中间
            Entity e1 = w[1].body();
            if (e1 != null) { double[] l1 = Venue.pw(Z, 6.5, 0); m.move(e1, new Location(m.world, l1[0], PY, l1[1], 180, 0)); if (e1 instanceof org.bukkit.entity.LivingEntity le) le.setPose(Pose.STANDING, true); }
            m.later(1, () -> { face(e0, e2); face(e2, e0); });
            if (e0 != null) d.glow.add(e0.getUniqueId()); if (e2 != null) d.glow.add(e2.getUniqueId());
            m.pushState();
        });
        d.at(36.2, () -> { d.glow.clear(); m.pushState(); for (int i : new int[]{0, 2}) { Entity e = w[i].body(); if (e != null) e.setRotation(180, 0); } });
        d.at(37.6, () -> { for (int i : new int[]{0, 2}) { w[i].color = (w[i].color + 1) % 4; m.lampOn(w[i]); Entity e = w[i].body(); if (e != null) m.world.spawnParticle(Particle.CLOUD, e.getLocation().add(0, 1.6, 0), 6, 0.15, 0.1, 0.15, 0.01, null, true); } });
        d.cleanup.add(() -> { for (Who x : w) if (x != null) m.lampOff(x); m.prisonClose(); });
        return d;
    }

    static Demo ecard(Match m) {
        Demo d = new Demo(Match.cn(4), Match.D_NAMES[0]);
        int games = Rules.ecardGames(m.fast), per = Rules.ecardPer(m.fast);   // 11-23：6 局、每 3 局换边（快节奏 4 局、每 2 局）
        d.shot(0, 3, v(C + 6, 72, C + 5), v(C + 4.4, 69.4, C + 3.6), v(C, 65.9, C), v(C, 65.9, C), 55, true);
        d.gist(3, 6, "皇帝赢市民，市民赢奴隶：奴隶赢皇帝", "和主持人打 " + games + " 局，赢了通关拿大奖，输了活板翻开", v(C + 4.4, 69.4, C + 3.6), v(C + 3.9, 69, C + 3.2), v(C, 65.9, C), 55);
        double[] near = v(C + 2.3, 68.3, C + 2.5), table = v(C, 65.9, C + 0.1);
        d.cap(3, 9, "一边拿皇帝，一边拿奴隶", "另外各 4 张市民", false);
        d.shot(3, 27, near, v(C + 2.0, 68.4, C + 2.7), table, table, 52, true);
        d.at(3.3, () -> { m.hostEmp = true; m.ecSides(); m.ecDeal(); });
        // 11-26 外部视听方案第 20 条：三张牌一张一张落下（和模组 Cine.ecardGist 对齐：3.25 / 3.6 / 3.95 秒），红线绕回皇帝那一下拔刀声；发牌声
        for (int i = 0; i < 3; i++) sfx(m, d, 3.25 + 0.35 * i, "draw", 0.9f, 0.95f - 0.05f * i);
        sfx(m, d, 5.2, "blade", 0.9f, 1f);
        sfx(m, d, 3.6, "deal", 0.8f, 1f);
        d.cap(9, 15, "两边同时扣下一张牌", "牌扣下后不能再改，同时翻开", false);
        d.at(10.0, () -> { m.ecPut(true, 3); m.ecPut(false, 4); });
        sfx(m, d, 10.0, "draw", 0.8f, 1f); sfx(m, d, 10.3, "draw", 0.8f, 1.05f);   // 11-26：两边扣牌
        d.cap(15, 21, "同时翻牌", "市民碰市民作废，接着出", false);   // 克制链第一屏讲过了
        d.at(15.4, () -> { m.ecFlip(true); m.ecFlip(false); });
        sfx(m, d, 15.4, "flip", 1f, 1f);   // 11-26：实录翻牌
        vac(m, d, 15.0, 1.6); vac(m, d, 24.0, 2.0);   // 两次翻牌前 0.4 秒抽空
        vac(m, d, 30.5, 6);   // 活板翻开：抽空到演示结束
        d.at(18.2, () -> { m.ecDiscard(true); m.ecDiscard(false); });
        d.cap(21, 27, "奴隶碰上皇帝，奴隶赢", "每 " + per + " 局换边 · 奴隶赢 3 分，皇帝赢 1 分", false);
        d.at(21.2, () -> { m.hostEmp = false; m.ecSides(); m.ecDeal(); });
        sfx(m, d, 21.2, "deal", 0.8f, 1f); sfx(m, d, 22.3, "draw", 0.8f, 1f); sfx(m, d, 22.6, "draw", 0.8f, 1.05f);
        d.at(22.3, () -> { m.ecPut(true, 1); m.ecPut(false, 1); });
        d.at(24.4, () -> { m.ecFlip(true); m.ecFlip(false); });
        sfx(m, d, 24.4, "flip", 1f, 1f); sfx(m, d, 24.45, "impact", 0.8f, 1f);   // 11-26 第 20 条：奴隶赢皇帝，翻牌叠一声重低音
        // 挑战席上坐一个示范的人（真的挑战者演完才上桌）：最后一步他连人带活板翻下去
        Location seat = Venue.plate4(m.world);
        Who[] ch = new Who[1];
        d.at(0.1 - d.off, () -> { Mannequin e = m.demoActor(d, seat, 4); ch[0] = new Who("示范", null, e); m.seat(ch[0], seat); });   // 一开场就坐着（不受开头那句挪时间影响）
        // 11-25「打满什么什么翻开，太大白话了，要告诉大家会发生什么，要么你赢，要么你输」→ 拆成两屏：先讲赢，再演输
        d.cap(27, 30, games + " 局打完，总分赢过主持人", "你通关，赢走神秘大奖", false);
        d.cap(30, 35, "总分输了，脚下的活板翻开", "平分就加赛一局，一定分出输赢", true);
        d.still(27, 35.5, v(C + 4.6, seat.getY() + 2.6, C + 3.6), v(C, seat.getY() - 0.6, C + 0.6), 62);
        d.at(31.2, () -> {
            m.scene.dt(10); m.scene.set("plate4.sw", -900);
            if (ch[0] != null && ch[0].chair != null) m.slide(ch[0], ch[0].chair.getLocation().clone().add(0, -6, 0), 14);
        });
        sfx(m, d, 31.2, "trap", 1f, 1f); sfx(m, d, 31.5, "swish", 0.8f, 0.9f);   // 11-26 第 20 条：活板翻开轰一声，接一道往下掉的风
        d.at(33.6, () -> { if (ch[0] != null) { m.unseat(ch[0]); Entity e = ch[0].body(); if (e != null) e.remove(); } m.scene.dt(20); m.scene.set("plate4.sw", 0); });
        d.cleanup.add(() -> { if (ch[0] != null) m.unseat(ch[0]); m.scene.dt(20); m.scene.set("plate4.sw", 0); });
        return d;
    }
}
