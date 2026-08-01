/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
