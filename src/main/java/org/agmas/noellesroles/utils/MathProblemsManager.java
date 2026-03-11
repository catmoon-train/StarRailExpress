package org.agmas.noellesroles.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MathProblemsManager {
    private static final Random random = new Random();
    private static final char[] OPERATORS = { '+', '-', '*', '/' };

    /**
     * 随机生成一道数学题目
     * @param difficulty 难度。1为简单，2为难。
     * @return 包含题目及选项的 MathProblem 对象
     */
    public MathProblem generateProblem(int difficulty) {
        // forced ? 1 : 2
        // 随机选择题目类型：0 表示加减乘除，1 表示简单求导
        int type = random.nextInt(30);
        if (difficulty == 1) {
            type = random.nextInt(60);
        }
        return type == 0 ? generateDerivativeProblem(difficulty) : generateArithmeticProblem(difficulty);
    }

    /**
     * 生成一道加减乘除题目
     */
    private MathProblem generateArithmeticProblem(int difficulty) {
        
        int num1 = random.nextInt(-16, 17);
        int num2 = random.nextInt(-16, 17);
        if(difficulty == 1){
            num1 = random.nextInt(-10, 12);
            num2 = random.nextInt(-10, 12);
        }
        char op = OPERATORS[random.nextInt(OPERATORS.length)];
        int result;
        String opDisplay = "";
        switch (op) {
            case '+':
                result = num1 + num2;
                opDisplay = "+";
                break;
            case '-':
                result = num1 - num2;
                opDisplay = "-";
                break;
            case '*':
                result = num1 * num2;
                opDisplay = "×";
                break;
            case '/':
                // 修复 Bug 1：保证除数 num2 不为 0，避免出现 0 ÷ 0
                while (num2 == 0) {
                    num2 = random.nextInt(1, 17);
                }
                int multiplier = random.nextInt(5) + 1; // 1~5 倍
                num1 = num2 * multiplier;
                result = multiplier;
                opDisplay = "÷";
                break;
            default:
                result = 0;
        }

        String question = num1 + " " + opDisplay + " " + (num2 < 0 ? " (" + num2 + ")" : num2) + " = ";

        // 修复 Bug 2：统一用一个 Set 跟踪已用值，避免选项重复
        List<String> options = new ArrayList<>();
        java.util.Set<String> usedValues = new java.util.HashSet<>();

        String correctStr = String.valueOf(result);
        options.add(correctStr);
        usedValues.add(correctStr);

        // 优先候选：result 的相反数（需不重复）
        List<String> wrongCandidates = new ArrayList<>();
        wrongCandidates.add(String.valueOf(result * -1));

        // 在正确答案附近 ±1~±6 范围依次生成候选，避免随机死循环
        for (int delta = 1; delta <= 20 && wrongCandidates.size() < 10; delta++) {
            wrongCandidates.add(String.valueOf(result + delta));
            wrongCandidates.add(String.valueOf(result - delta));
        }
        // 兜底：追加一批随机值
        for (int i = 0; i < 20; i++) {
            wrongCandidates.add(String.valueOf(random.nextInt(-100, 100)));
        }

        List<String> wrongOptions = new ArrayList<>();
        for (String cand : wrongCandidates) {
            if (!usedValues.contains(cand)) {
                usedValues.add(cand);
                wrongOptions.add(cand);
                if (wrongOptions.size() >= 3)
                    break;
            }
        }

        // 取前 3 个错误选项（候选充足，正常情况不会不足）
        options.addAll(wrongOptions.subList(0, Math.min(3, wrongOptions.size())));
        Collections.shuffle(options);
        int correctIndex = options.indexOf(correctStr);

        return new MathProblem(question, options, correctIndex, 1);
    }

    /**
     * 格式化幂函数项：将系数和指数转换为美观的字符串
     * 例如 (2,2) -> "2x²", (1,1) -> "x", (3,0) -> "3"
     */
    private String formatTerm(int coeff, int exp) {
        if (coeff == 0) {
            return "0";
        }

        // 系数部分：系数为1且指数非0时省略系数
        String coeffPart = (coeff == 1 && exp != 0) ? "" : String.valueOf(coeff);

        if (exp == 0) {
            return String.valueOf(coeff);
        } else if (exp == 1) {
            return coeffPart + "x";
        } else {
            String[] powers = { "⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹" };
            StringBuilder expPart = new StringBuilder();
            int tmp = exp;
            java.util.ArrayList<String> digits = new java.util.ArrayList<>();
            while (tmp > 0) {
                digits.add(powers[tmp % 10]);
                tmp /= 10;
            }
            for (int i = digits.size() - 1; i >= 0; i--) {
                expPart.append(digits.get(i));
            }
            return coeffPart + "x" + expPart.toString();
        }
    }

    private MathProblem generateDerivativeProblem(int difficulty) {
        int coefficient = random.nextInt(-10, 10);

        int exponent = random.nextInt(1, 16);
        if (difficulty == 1) {
            coefficient = random.nextInt(-5, 5);
            exponent = random.nextInt(1, 5);
        }
        String function = formatTerm(coefficient, exponent);

        int newCoeff = coefficient * exponent;
        int newExp = exponent - 1;
        String derivative = formatTerm(newCoeff, newExp);

        String question = "F(x) = " + function + "; F'(x) = ?";

        List<String> options = new ArrayList<>();
        options.add(derivative);

        // 修复 Bug 2：用 Set 统一去重
        java.util.Set<String> usedValues = new java.util.HashSet<>();
        usedValues.add(derivative);

        List<String> wrongCandidates = new ArrayList<>();

        for (int delta : new int[] { 1, -1, 2, -2 }) {
            wrongCandidates.add(formatTerm(newCoeff + delta, newExp));
        }
        wrongCandidates.add(formatTerm(newCoeff * -1, newExp));

        for (int delta : new int[] { 1, -1 }) {
            int wrongExp = newExp + delta;
            if (wrongExp >= 0) {
                wrongCandidates.add(formatTerm(newCoeff, wrongExp));
            }
        }

        wrongCandidates.add(function);
        wrongCandidates.add(formatTerm(newCoeff, newExp + 2));

        if (newExp == 0) {
            wrongCandidates.add(formatTerm(newCoeff, 1));
        } else if (newExp == 1) {
            wrongCandidates.add(formatTerm(newCoeff, 2));
        } else {
            wrongCandidates.add(formatTerm(newCoeff, newExp - 1));
        }

        List<String> wrongOptions = new ArrayList<>();
        for (String cand : wrongCandidates) {
            if (cand != null && !usedValues.contains(cand)) {
                usedValues.add(cand);
                wrongOptions.add(cand);
                if (wrongOptions.size() >= 3)
                    break;
            }
        }

        // 兜底补充（候选不足时）
        if (wrongOptions.size() < 3) {
            String[] defaults = {
                    formatTerm(0, 0),
                    formatTerm(1, 0),
                    formatTerm(1, 1),
                    formatTerm(2, 1),
                    formatTerm(1, 2)
            };
            for (String def : defaults) {
                if (!usedValues.contains(def)) {
                    usedValues.add(def);
                    wrongOptions.add(def);
                    if (wrongOptions.size() >= 3)
                        break;
                }
            }
        }

        options.addAll(wrongOptions);
        Collections.shuffle(options);
        int correctIndex = options.indexOf(derivative);

        return new MathProblem(question, options, correctIndex, 2);
    }

    /**
     * 数学题目信息类
     * 包含题目名称、四个选项以及正确选项的索引
     */
    public static class MathProblem {
        private final String question;
        private final List<String> options;
        private final int correctIndex;
        private final int type;

        public MathProblem(String question, List<String> options, int correctIndex, int type) {
            this.question = question;
            this.options = options;
            this.correctIndex = correctIndex;
            this.type = type;
        }

        public int getType() {
            return this.type;
        }

        public String getQuestion() {
            return question;
        }

        public List<String> getOptions() {
            return options;
        }

        public int getCorrectIndex() {
            return correctIndex;
        }
    }
}