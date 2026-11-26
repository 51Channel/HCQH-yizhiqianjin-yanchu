// 限定猜拳的规则演示（Rps.java 里只抽了 demo() 这一个函数）
// 原文件 Rps.java 第 556 行起

    // ================= 规则演示 =================
    /** 一张桌子两头摆两个示范的人：各扣一张牌、翻开（石头赢剪刀），一颗星从输的人胸口飞到赢的人胸口；再抬头看顶上的大屏幕。 */
    Demo demo() {
        Demo d = new Demo(Match.cn(Match.SEMI), N);
        Booth b = booths[0];
        double[] c = loc(b, 0, 0), camA = loc(b, 0.6, -5.4), camB = loc(b, -0.8, -4.4);
        double[] tbl = Demo.v(c[0], RY + TY + 0.1, c[1]);
        int mins = (int) Math.round(m.sec(LIMIT, LIMIT_FAST) / 60);
        d.shot(0, 3, Demo.v(Venue.C + 4, RY + 6, Venue.C - 4), Demo.v(Venue.C + 2, RY + 5, Venue.C - 6), tbl, tbl, 60, true);
        d.gist(3, 6, "手里的牌，出完就没了：找人猜拳，赢走对方的星", "最后星最多的那个人，上牌桌挑战主持人", Demo.v(camA[0], RY + 3.2, camA[1]), Demo.v(camB[0], RY + 2.8, camB[1]), Demo.v(c[0], RY + 1.3, c[1]), 62);
        Mannequin[] a = new Mannequin[2]; List<ItemDisplay> st = new ArrayList<>(); ItemDisplay[] cardD = new ItemDisplay[2];
        d.at(0.1 - d.off, () -> {
            for (int s = 0; s < 2; s++) { a[s] = m.demoActor(d, pad(b, s), 4 + s); }   // 示范的人站在桌子两头的金线圈里
            for (int s = 0; s < 2; s++) for (int i = 0; i < 3; i++) {
                ItemDisplay x = m.rider(a[s], new Vector3f((float) ((i - 1) * 0.13), -0.55f, 0.2f), 0.16f, Display.Billboard.FIXED);
                x.setItemStack(Match.modelItem("rps_star")); a[s].addPassenger(x); x.setRotation(a[s].getYaw(), 0); st.add(x); d.props.add(x);
            }
        });
        d.cap(3, 9, "找一个人，站进桌子两头的金线圈", "同两个人最多打 2 次", false);
        d.shot(3, 9, Demo.v(camB[0], RY + 2.8, camB[1]), Demo.v(camA[0], RY + 2.4, camA[1]), Demo.v(c[0], RY + 1.3, c[1]), Demo.v(c[0], RY + 1.3, c[1]), 62, true);
        d.cap(9, 15, "各扣一张牌，同时翻开", "石头、剪刀、布各 " + per + " 张", false);
        d.still(9, 15, Demo.v(c[0] + (camA[0] - c[0]) * 0.32, RY + 2.3, c[1] + (camA[1] - c[1]) * 0.32), tbl, 50);
        for (int s = 0; s < 2; s++) {
            final int ss = s;
            d.at(9.6 + s * 0.8, () -> {
                double[] p = loc(b, sx(ss) * 0.24, 0);
                cardD[ss] = m.world.spawn(new Location(m.world, p[0], RY + TY + 0.012, p[1], (float) -b.th, 0), ItemDisplay.class, e -> {
                    e.addScoreboardTag(Match.TAG); e.setPersistent(false); e.setItemStack(Match.modelItem("rps3")); e.setBrightness(new Display.Brightness(15, 15));
                    e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) (-Math.PI / 2), 1, 0, 0), new Vector3f(0.36f, 0.36f, 0.36f), new AxisAngle4f()));
                });
                d.props.add(cardD[ss]);
            });
        }
        // 11-23 用户：「音效不是说你加满它就是好的」——这段只留两下：翻牌、星被拿走
        Demos.sfx(m, d, 3.4, "kin", 0.9f, 1f);   // 11-26 外部视听方案第 19 条：站进金线圈，金属「叮」
        Demos.sfx(m, d, 9.6, "draw", 0.8f, 1f); Demos.sfx(m, d, 10.4, "draw", 0.8f, 1.05f);   // 11-26：两边扣牌
        Demos.sfx(m, d, 12.2, "flip", 1f, 1f);
        Demos.sfx(m, d, 16.1, "star", 0.9f, 1f);
        d.at(12.2, () -> { if (cardD[0] != null) cardD[0].setItemStack(Match.modelItem("rps0")); if (cardD[1] != null) cardD[1].setItemStack(Match.modelItem("rps1")); });
        d.cap(15, 18.6, "赢了拿走对方 1 颗星", "", false);
        d.still(15, 18.7, Demo.v(camA[0], RY + 2.2, camA[1]), Demo.v(c[0], RY + 1.5, c[1]), 60);
        // 11-26 用户：「看自己还有几颗星的那个位置，要能看到自己的物品栏还有手」：切回自己的第一人称，物品栏上摆好三种牌和 3 颗星，手里拿着星
        d.cap(18.7, 23.4, "物品栏上的星，就是你还剩几颗", "每人 3 颗，输光就出局；旁边几格是手里的牌", false);
        d.self(18.7, 23.5);
        java.util.Map<Integer, org.bukkit.inventory.ItemStack> inv = new java.util.HashMap<>();
        for (int t = 0; t < 3; t++) inv.put(RPS_SLOT[t], m.prop("rps" + t, RPS_NAME[t] + " · 还剩 " + per + " 张", per, "rpsinfo"));
        inv.put(STAR_SLOT, m.prop("rps_star", "星 · 3 颗（输光了就出局）", 3, "rpsinfo"));
        Demos.lend(m, d, 18.1, 23.5, inv, STAR_SLOT);
        d.off += 2.5;   // 多出来的这一屏：后面的时间整体往后挪
        d.at(15.6, () -> {   // 右边那个人胸口的一颗星飞到左边
            if (st.size() < 6 || a[0] == null) return;
            ItemDisplay x = st.get(5); a[1].removePassenger(x);
            Location to = a[0].getLocation().add(0, 1.25, 0);
            x.setTeleportDuration(10); x.setBillboard(Display.Billboard.CENTER);
            m.later(2, () -> { if (x.isValid()) x.teleport(to); });
        });
        // 顶上的大屏幕：从北边那张桌子外侧（轮盘环外圈，台地墙里面）抬头看北面那块屏（屏幕中心 (C, JY, C − JD)，往外、往下斜 30°）
        double[] scr = Demo.v(Venue.C, JY, Venue.C - JD);
        d.cap(21, 27, "抬头看大屏幕", "全场还剩多少牌、多少时间，都在上面", false);
        // 11-25 实拍：原机位在北桌吊灯正下方，白色灯框横在大屏前面——抬高 2 格、退到桌子外面正对着屏
        d.shot(21, 27, Demo.v(Venue.C - 1.7, RY + 3.2, Venue.C - 16.1), Demo.v(Venue.C, RY + 3.5, Venue.C - 15.4), Demo.v(Venue.C, JY - 0.6, Venue.C - JD), scr, 64, true);
        // 11-25 审查：大字是半句话、小字一行塞两件事——大字说完整，小字只补加赛
        d.cap(27, 33, mins + " 分钟后，星最多的人去决战", "星一样多，当场加赛一把", true);
        d.shot(27, 33.5, Demo.v(camB[0], RY + 2.6, camB[1]), Demo.v(camA[0], RY + 3.6, camA[1]), Demo.v(c[0], RY + 1.3, c[1]), Demo.v(c[0], RY + 1.3, c[1]), 62, true);
        return d;
    }
