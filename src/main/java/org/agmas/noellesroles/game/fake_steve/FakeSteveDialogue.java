package org.agmas.noellesroles.game.fake_steve;

import java.util.List;
import java.util.Locale;

/** Short, deliberately ordinary lines used sparingly while the body is disguised. */
public final class FakeSteveDialogue {
    private static final List<String> DIRECTED_ROLE_REPLIES = List.of(
            "你猜", "先说你的", "我不方便说", "普通职业", "做好人任务的",
            "先别问身份", "后面再说", "你觉得呢", "我没技能", "别套我话",
            "看情况吧", "先找尸体", "我身份不重要", "你先证明自己");
    private static final List<String> COMMON = List.of(
            "6", "？", "干什么", "你什么职业", "有人吗", "在哪", "谁干的", "我路过", "没看见", "不知道",
            "先做任务", "等等我", "门开一下", "有钥匙吗", "这边有人", "刚才谁过去了", "别跟着我", "你去哪",
            "我去吃东西", "我去喝水", "我要睡觉", "找不到床", "椅子在哪", "有人报点吗", "尸体在哪", "别乱刀",
            "你确定吗", "真的假的", "我不信", "可能吧", "看起来不像", "先别急", "慢慢说", "谁有枪", "谁拿刀了",
            "灯怎么黑了", "开灯啊", "门锁了", "钥匙不对", "我进不去", "帮我开门", "你先走", "我在后面",
            "别堵门", "让一下", "借过", "这里安全", "这边危险", "快跑", "有人追我", "我被打了", "救一下",
            "别过来", "跟我来", "分开走", "一起走", "等一下", "马上", "来了", "走了", "好", "行", "可以",
            "不行", "算了", "随便", "都行", "笑死", "离谱", "什么情况", "怎么回事", "卡了吗", "我迷路了",
            "这地图好大", "任务点在哪", "我还有任务", "先做完这个", "换个任务", "这个做不了", "东西拿不到",
            "盘子空了", "有人吃了吗", "没饮料了", "我要找厕所", "我坐一会", "你在看什么", "别盯着我",
            "你是谁", "你哪边的", "你是好人吗", "你有身份吗", "别问我", "先说你的", "我不方便说", "后面再说",
            "有人死了吗", "人数不对", "少了一个", "刚刚还在", "往那边走了", "在车头", "在车尾", "在房间里",
            "门口见", "大厅集合", "不要单走", "注意身后", "小心点", "听到脚步了", "好像有人", "我去看看",
            "没事", "安全了", "虚惊一场", "别开枪", "留个人看着", "谁能证明", "有目击吗", "我能证明",
            "不是我", "我一直在做任务", "我刚从那边来", "我和他一起", "时间对不上", "先记一下", "下一轮再说",
            "别投错", "先跳过", "信息不够", "有点可疑", "我觉得是他", "也可能不是", "你们决定", "我没意见");

    private FakeSteveDialogue() {
    }

    public static int commonPhraseCount() {
        return COMMON.size();
    }

    public static String commonPhrase(int index) {
        return COMMON.get(Math.floorMod(index, COMMON.size()));
    }

    public static String directedRoleReply(int index) {
        return DIRECTED_ROLE_REPLIES.get(Math.floorMod(index, DIRECTED_ROLE_REPLIES.size()));
    }

    public static boolean isDirectedRoleQuestion(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT).replace(" ", "");
        return normalized.contains("什么职业") || normalized.contains("啥职业")
                || normalized.contains("什么身份") || normalized.contains("啥身份")
                || normalized.contains("干什么的") || normalized.contains("哪边的")
                || normalized.contains("whatrole") || normalized.contains("yourrole");
    }
}
