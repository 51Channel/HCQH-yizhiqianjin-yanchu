// 失控列车的规则演示（Train.java 里只抽了 demo() 这一个函数，别的是玩法代码，不公开）
// 原文件 Train.java 第 721 行起

    // ================= 规则演示 =================
    /** 先一句大白话；然后在第 1 节里摆三个示范的人：两个戴上面罩、一个没戴 → 放气 → 金丝雀死了、没戴的人倒下 → 「那这一节，你戴不戴？」。 */
    Demo demo() {
        Demo d = new Demo(cn(), N_TRAIN);
        double x = cx(1);
        double[] out0 = Demo.v(cx(2) + 6, 71.8, 296.1), out1 = Demo.v(cx(2) - 4, 71.6, 296.1), outL = Demo.v(cx(4), 70.8, 299.0);
        d.shot(0, 3, out0, out1, outL, outL, 60, true);
        // 11-25 用户：「一下子就转到这个莫名其妙的地方，然后就介绍自己身上有那个毒气罐……完全没有让人理解这个游戏会发生什么」
        // → 先讲输赢（停不下来就全员输），再演会发生什么（放气、金丝雀死、没戴面罩的倒下），再讲怎么活（面罩）和代价（滤罐有限），最后才讲每节怎么决定、期限多长
        d.gist(3, 6, "穿过毒气车厢，去车头停车：猜准毒气，戴上面罩", "停不下车，列车撞上终点，车上的人全输", out1, Demo.v(cx(2) - 7, 71.5, 296.1), outL, 60);
        double[] inA = Demo.v(x + 16.6, 72.9, 301.6), inB = Demo.v(x + 15.6, 72.7, 298.6), look = Demo.v(x + 8.5, 71.3, 300.0);
        Mannequin[] a = new Mannequin[3];
        double[][] pos = {{x + 8.2, 299.0}, {x + 9.6, 301.0}, {x + 7.2, 301.3}};
        d.at(0.1 - d.off, () -> {
            for (int i = 0; i < 3; i++) { a[i] = m.demoActor(d, new Location(m.world, pos[i][0], FL, pos[i][1], 90, 0), 4 + i); }
            a[0].getEquipment().setHelmet(maskHelmet()); a[1].getEquipment().setHelmet(maskHelmet());
            m.scene.dt(0); m.scene.set("tbird1.var", 0);
        });
        // 11-23 晚用户：「音效不是说你加满它就是好的」——只留三个关键时刻：放毒气的嘶声、没戴面罩的人倒下、讲到撞车的警报
        Demos.sfx(m, d, 0, "train", 1f, 1f);   // 11-26 外部视听方案第 17 条：开头车外推进，很低的列车走行声
        Demos.sfx(m, d, 3.5, "gas", 1f, 1f);
        Demos.sfx(m, d, 9.2, "bird", 0.9f, 1f);   // 11-26 第 17 条：金丝雀叫一声就断了
        Demos.sfx(m, d, 10.3, "choke", 0.9f, 1f);
        Demos.sfx(m, d, 10.4, "thud", 0.9f, 1f);   // 11-26：没戴面罩的人倒地
        Demos.sfx(m, d, 25.0, "mask", 1f, 1f);   // 11-26 第 18 条：戴上面罩
        Demos.sfx(m, d, 27.3, "siren", 0.6f, 1f);   // 11-26 第 18 条：警报大一点，文件只有两声
        d.cap(3, 9, "哪一节放毒气，事先不知道", "", false);   // 11-25 用户：注意看鸟不用写这么明显，小字删掉
        d.shot(3, 9, Demo.v(x + 10.4, 72.2, 298.6), Demo.v(x + 10.0, 72.3, 299.0), Demo.v(x + 9, CAGE_Y + 0.4, TZ), Demo.v(x + 9, CAGE_Y + 0.4, TZ), 52, true);
        // 11-25 实拍：镜头就在毒气里，贴着镜头的灰点放大成两块粉紫色方块——演示里镜头周围 2.5 格不放
        // 毒气一直飘到人倒下那屏结束（原来 3 秒就散了，金丝雀死的时候车厢里已经干干净净）
        for (int t = 0; t < 225; t += 3) { final int tt = t; boolean first = t < 110; d.at(3.5 + t / 20.0, () -> { if (first) fog(x, tt, x + 10.2, 72.25, 298.8); else fog(x, tt, x + 12.0, 72.2, 302.2); }); }
        d.cap(9, 15, "金丝雀死了，就是毒气", "没戴面罩的人倒下，这一场出局", true);   // 11-25 审查：单写「出局」像整局淘汰
        d.still(9, 15, Demo.v(x + 12.0, 72.2, 302.2), Demo.v(x + 8.3, 71.2, 300.4), 64);   // 11-25 实拍：笼子顶在画面上沿被切掉，看不出金丝雀死了——往上抬一点
        d.at(9.2, () -> { m.scene.dt(6); m.scene.set("tbird1.var", 1); });
        d.at(10.4, () -> { if (a[2] != null) a[2].setPose(Pose.SLEEPING, true); });
        d.cap(15, 21, "戴着面罩的人，活下来", "但每人只有 " + CANS + " 个滤罐，戴一节用掉一个，有没有毒都算", false);
        d.still(15, 21, Demo.v(x + 11.0, 71.9, 298.6), Demo.v(x + 8.4, 71.5, 300.2), 58);
        // 11-25 审查：原来在同一节把倒下的人、死掉的金丝雀原地复活再讲「每节都要赌」，穿帮——改成活下来的两个人摘了面罩走进下一节，身后的门锁上
        double x2 = cx(2);
        d.at(20.6, () -> door(2, true));
        d.at(21.0, () -> { for (int i = 0; i < 2; i++) if (a[i] != null) a[i].getEquipment().setHelmet(null); });
        Demos.YAt fl = (xx, zz) -> FL;
        Demos.track(m, d, () -> a[0], fl, new double[]{21.0, pos[0][0], pos[0][1]}, new double[]{22.0, x + 16.5, 299.6}, new double[]{23.0, x2 + 3.2, 299.4});
        Demos.track(m, d, () -> a[1], fl, new double[]{21.3, pos[1][0], pos[1][1]}, new double[]{22.3, x + 16.5, 300.6}, new double[]{23.3, x2 + 4.4, 301.0});
        d.at(23.8, () -> door(2, false));
        d.cap(21, 27, "所以每进一节，都要赌一次", "门一锁，" + Math.round(maskSec()) + " 秒内决定：戴，还是不戴", false);
        d.still(21, 27, Demo.v(x2 + 11.5, 72.5, 301.6), Demo.v(x2 + 1.5, 71.3, 300.0), 62);
        d.at(25.0, () -> { if (a[0] != null) { a[0].getEquipment().setHelmet(maskHelmet()); a[0].setRotation(90, 0); } if (a[1] != null) a[1].setRotation(90, 0); });
        // 11-25 审查：最后一屏讲「走到车头」，画面就给车头：乘务室里的两根手柄（不说哪根是哪根）
        d.cap(27, 33, COUNT + " 节车厢里，有 " + GAS + " 节是毒气", span(crashSec()) + "后撞上终点，在那之前走到车头把车停下", false);
        // 站在乘务室里，对着左边那扇车窗看外面的轨道往前推（窗中间的立柱不竖在画面正中）；操纵台压在画面下沿外，手柄和铭牌都不进画
        d.shot(27, 33.5, Demo.v(CAB0 + 2.4, 72.45, TZ - 1.0), Demo.v(CAB0 + 3.3, 72.4, TZ - 1.0), Demo.v(CAB1 + 24, 72.2, TZ - 1.0), Demo.v(CAB1 + 24, 72.2, TZ - 1.0), 55, true);
        d.cleanup.add(() -> { m.scene.dt(0); m.scene.set("tbird1.var", 0); m.scene.set("tgd2a.tz", 0); m.scene.set("tgd2b.tz", 0); lockDoor(2, true); });   // 门关回去，不出声
        return d;
    }
