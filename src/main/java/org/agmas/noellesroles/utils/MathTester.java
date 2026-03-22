package org.agmas.noellesroles.utils;

import org.agmas.noellesroles.utils.MathProblemsManager.MathProblem;

import java.util.List;

public class MathTester {
    public static void main(String[] args) {
        MathProblemsManager manager = new MathProblemsManager();
        for (int i = 0; i < 5; i++) {
            MathProblem problem = manager.generateProblem(1);
            System.out.println("题目：" + problem.getQuestion());
            List<String> options = problem.getOptions();
            for (int j = 0; j < options.size(); j++) {
                System.out.println("  " + (j + 1) + ". " + options.get(j));
            }
            System.out.println("正确答案索引：" + problem.getCorrectIndex() + " (选项 " + (problem.getCorrectIndex() + 1) + ")\n");
        }
    }
}
