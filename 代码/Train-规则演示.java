// 失控列车的规则演示（Train.java 里只抽了 demo() 这一个函数，别的是玩法代码，不公开）
// 原文件 Train.java 第 717 行起

    // ================= 规则演示 =================
    /** 先一句大白话；然后在第 1 节里摆三个示范的人：两个戴上面罩、一个没戴 → 放气 → 金丝雀死了、没戴的人倒下 → 「那这一节，你戴不戴？」。 */
    Demo demo() {
        Demo d = new Demo(cn(), N_TRAIN);
        double x = cx(1);
        double[] out0 = Demo.v(cx(2) + 6, 71.8, 296.1), out1 = Demo.v(cx(2) - 4, 71.6, 296.1), outL = Demo.v(cx(4), 70.8, 299.0);
        d.shot(0, 3, out0, out1, outL, outL, 60, true);
        // 11-25 用户：「一下子就转到这个莫名其妙的地方，然后就介绍自己身上有那个毒气罐……完全没有让人理解这个游戏会发生什么」
        // → 先讲输赢（停不下来就全员输），再演会发生什么（放气、金丝雀死、没戴面罩的倒下），再讲怎么活（面罩）和代价（滤罐有限），最后才讲每节怎么决定、期限多长
        d.gist(3, 6, "走到车头，把车停下", "停不下来，列车撞上终点，车上的人全输", out1, Demo.v(cx(2) - 7, 71.5, 296.1), outL, 60);
        double[] inA = Demo.v(x + 16.6, 72.9, 301.6), inB = Demo.v(x + 15.6, 72.7, 298.6), look = Demo.v(x + 8.5, 71.3, 300.0);
        Mannequin[] a = new Mannequin[3];
        double[][] pos = {{x + 8.2, 299.0}, {x + 9.6, 301.0}, {x + 7.2, 301.3}};
        d.at(0.1 - d.off, () -> {
            for (int i = 0; i < 3; i++) { a[i] = m.demoActor(d, new Location(m.world, pos[i][0], FL, pos[i][1], 90, 0), 4 + i); }
            a[0].getEquipment().setHelmet(maskHelmet()); a[1].getEquipment().setHelmet(maskHelmet());
            m.scene.dt(0); m.scene.set("tbird1.var", 0);
        });
        // 11-23 晚用户：「音效不是说你加满它就是好的」——只留三个关键时刻：放毒气的嘶声、没戴面罩的人倒下、讲到撞车的警报
        Demos.sfx(m, d, 3.5, "gas", 0.9f, 1f);
        Demos.sfx(m, d, 10.3, "choke", 0.9f, 1f);
        Demos.sfx(m, d, 27.3, "siren", 0.35f, 1f);
        d.cap(3, 9, "一路上，有的车厢会放毒气", "注意看笼子里的金丝雀", false);
        d.shot(3, 9, Demo.v(x + 10.4, 72.2, 298.6), Demo.v(x + 10.0, 72.3, 299.0), Demo.v(x + 9, CAGE_Y + 0.4, TZ), Demo.v(x + 9, CAGE_Y + 0.4, TZ), 52, true);
        // 11-25 实拍：镜头就在毒气里，贴着镜头的灰点放大成两块粉紫色方块——演示里镜头周围 2.5 格不放
        // 毒气一直飘到人倒下那屏结束（原来 3 秒就散了，金丝雀死的时候车厢里已经干干净净）
        for (int t = 0; t < 225; t += 3) { final int tt = t; boolean first = t < 110; d.at(3.5 + t / 20.0, () -> { if (first) fog(x, tt, x + 10.2, 72.25, 298.8); else fog(x, tt, x + 12.0, 72.2, 302.2); }); }
        d.cap(9, 15, "金丝雀死了，就是毒气", "没戴面罩的人倒下，出局", true);
        d.still(9, 15, Demo.v(x + 12.0, 72.2, 302.2), Demo.v(x + 8.3, 71.2, 300.4), 64);   // 11-25 实拍：笼子顶在画面上沿被切掉，看不出金丝雀死了——往上抬一点
        d.at(9.2, () -> { m.scene.dt(6); m.scene.set("tbird1.var", 1); });
        d.at(10.4, () -> { if (a[2] != null) a[2].setPose(Pose.SLEEPING, true); });
        d.cap(15, 21, "戴着面罩的人，活下来", "但每人只有 " + CANS + " 个滤罐，戴一节用掉一个，有没有毒都算", false);
        d.still(15, 21, Demo.v(x + 11.0, 71.9, 298.6), Demo.v(x + 8.4, 71.5, 300.2), 58);
        // 下一屏讲「每节都要赌」：把倒下的人、金丝雀、面罩都收回去，干干净净再看一次
        d.at(21.0, () -> {
            m.scene.dt(0); m.scene.set("tbird1.var", 0);
            if (a[2] != null) a[2].setPose(Pose.STANDING, true);
            for (Mannequin q : a) if (q != null) q.getEquipment().setHelmet(null);
        });
        d.cap(21, 27, "所以每进一节，都要赌一次", "门一锁，" + Math.round(maskSec()) + " 秒内决定：戴，还是不戴", false);
        d.shot(21, 27, inA, inB, look, look, 66, true);
        d.at(23.6, () -> { if (a[0] != null) a[0].getEquipment().setHelmet(maskHelmet()); });
        d.cap(27, 33, COUNT + " 节车厢里，有 " + GAS + " 节是毒气", span(crashSec()) + "后撞上终点，在那之前扳对车头的手柄", false);
        d.shot(27, 33.5, Demo.v(x + 13, 72.6, 301.6), inA, look, look, 66, true);
        d.cleanup.add(() -> { m.scene.dt(0); m.scene.set("tbird1.var", 0); });
        return d;
    }
