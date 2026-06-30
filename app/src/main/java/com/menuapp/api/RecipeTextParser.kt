package com.menuapp.api

import com.menuapp.model.Ingredient
import com.menuapp.model.MainDish
import com.menuapp.model.Step

/**
 * 本地菜谱文本解析器
 * 从 OCR 提取的纯文本中解析出结构化菜谱信息
 * 作为 Claude API 的离线回退方案
 */
object RecipeTextParser {

    /** 解析结果 */
    data class ParsedRecipe(
        val name: String = "",
        val mainDish: MainDish = MainDish(),
        val sideDishes: List<Ingredient> = emptyList(),
        val seasonings: List<Ingredient> = emptyList(),
        val steps: List<Step> = emptyList()
    )

    /** 章节关键词 */
    private val MAIN_DISH_KEYS = listOf("主料", "主菜", "材料", "原料", "食材")
    private val SIDE_DISH_KEYS = listOf("配菜", "辅料", "配材")
    private val SEASONING_KEYS = listOf("调料", "调味", "佐料", "酱料")
    private val STEP_KEYS = listOf("做法", "步骤", "烹饪", "制作", "方法", "过程", "操作")

    /** 时间关键词 → 秒数 */
    private val TIME_PATTERNS = listOf(
        Regex("""(\d+)\s*小时""") to 3600,
        Regex("""(\d+)\s*分钟""") to 60,
        Regex("""(\d+)\s*分""") to 60,
        Regex("""(\d+)\s*秒""") to 1,
    )

    /**
     * 解析菜谱文本
     */
    fun parse(text: String): ParsedRecipe {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (lines.isEmpty()) return ParsedRecipe()

        val name = extractName(lines)
        val mainDish = extractMainDish(lines)
        val (sideDishes, seasonings) = extractIngredients(lines)
        val steps = extractSteps(lines)

        return ParsedRecipe(name, mainDish, sideDishes, seasonings, steps)
    }

    /** 提取菜谱名称：取第一行非空文字 */
    private fun extractName(lines: MutableList<String>): String {
        for (i in lines.indices) {
            val line = lines[i]
            // 跳过明显不是标题的行
            if (line.length in 2..30 &&
                !line.startsWith("http") &&
                !MAIN_DISH_KEYS.any { line.contains(it) } &&
                !STEP_KEYS.any { line.contains(it) }
            ) {
                lines.removeAt(i)
                return line
            }
        }
        return ""
    }

    /** 提取主菜 */
    private fun extractMainDish(lines: MutableList<String>): MainDish {
        // 在包含主料关键词的区域查找
        var inMainSection = false
        for (i in lines.indices) {
            val line = lines[i]
            if (MAIN_DISH_KEYS.any { line.contains(it) }) {
                inMainSection = true
                continue
            }
            if (inMainSection) {
                // 遇到下一个章节关键词则退出
                if (SIDE_DISH_KEYS.any { line.contains(it) } ||
                    SEASONING_KEYS.any { line.contains(it) } ||
                    STEP_KEYS.any { line.contains(it) }
                ) break

                // 尝试匹配 名称 数字+单位
                val parsed = parseIngredientLine(line)
                if (parsed != null) {
                    val weight = extractWeight(line)
                    return MainDish(parsed.first, weight)
                }
                // 纯文字可能是主菜名
                if (line.length in 1..20 && !line.startsWith("http")) {
                    val weight = extractWeight(line)
                    return MainDish(line, weight)
                }
            }
        }

        // 回退：在全文范围查找常见的肉类/蔬菜名+重量
        val weightPattern = Regex("""([一-鿿]+)\s*(\d+)\s*克""")
        for (line in lines) {
            val match = weightPattern.find(line)
            if (match != null) {
                return MainDish(match.groupValues[1], match.groupValues[2].toInt())
            }
        }

        return MainDish()
    }

    /** 提取配菜和调料 */
    private fun extractIngredients(lines: MutableList<String>): Pair<List<Ingredient>, List<Ingredient>> {
        val sideDishes = mutableListOf<Ingredient>()
        val seasonings = mutableListOf<Ingredient>()

        var currentSection = ""
        var inSection = false

        for (line in lines) {
            // 检测章节切换
            when {
                SIDE_DISH_KEYS.any { line.contains(it) } -> {
                    currentSection = "side"; inSection = true; continue
                }
                SEASONING_KEYS.any { line.contains(it) } -> {
                    currentSection = "seasoning"; inSection = true; continue
                }
                STEP_KEYS.any { line.contains(it) } -> {
                    inSection = false; continue
                }
            }

            if (!inSection) continue

            // 解析行内的食材
            val items = parseIngredientItems(line)
            for ((name, amount) in items) {
                if (name.isNotEmpty()) {
                    val ingredient = Ingredient(name, amount)
                    when (currentSection) {
                        "side" -> sideDishes.add(ingredient)
                        "seasoning" -> seasonings.add(ingredient)
                    }
                }
            }
        }

        return Pair(sideDishes, seasonings)
    }

    /** 解析一行中的多个食材项（用 、，, 分隔） */
    private fun parseIngredientItems(line: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        // 先按中文/英文逗号、顿号分隔
        val items = line.split(Regex("""[、，,;；]"""))
        for (item in items) {
            val trimmed = item.trim()
            if (trimmed.isEmpty()) continue
            val parsed = parseIngredientLine(trimmed)
            if (parsed != null) {
                result.add(parsed)
            }
        }
        return result
    }

    /** 解析单个食材行：名称 + 用量 */
    private fun parseIngredientLine(text: String): Pair<String, String>? {
        // 尝试匹配 "名称 数字+单位" 格式
        val patterns = listOf(
            Regex("""^([一-鿿\w]+)[：:]\s*(.+)$"""),  // 名称：用量
            Regex("""^([一-鿿]+)\s*(\d+[一-鿿]*)\s*$"""),  // 名称 30克
            Regex("""^([一-鿿]+)\s*([\d.]+\s*[一-鿿]+)\s*$"""),  // 名称 2个
            Regex("""^([一-鿿]{1,6})\s+(\S+)$"""),  // 短名称 + 任意非空
        )
        for (pattern in patterns) {
            val match = pattern.find(text.trim())
            if (match != null) {
                val name = match.groupValues[1].trim()
                val amount = match.groupValues[2].trim()
                if (name.isNotEmpty()) {
                    return Pair(name, amount)
                }
            }
        }
        return null
    }

    /** 从文本中提取重量（克） */
    private fun extractWeight(text: String): Int {
        val pattern = Regex("""(\d+)\s*克""")
        val match = pattern.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /** 提取烹饪步骤 */
    private fun extractSteps(lines: MutableList<String>): List<Step> {
        val steps = mutableListOf<Step>()
        var inStepSection = false
        var stepOrder = 0

        for (line in lines) {
            // 检测步骤章节开始
            if (STEP_KEYS.any { line.contains(it) }) {
                inStepSection = true
                continue
            }

            if (!inStepSection) continue

            // 遇到其他章节关键词则退出
            if (MAIN_DISH_KEYS.any { line.contains(it) } ||
                SIDE_DISH_KEYS.any { line.contains(it) } ||
                SEASONING_KEYS.any { line.contains(it) }
            ) break

            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 检查是否是编号步骤行
            val stepPattern = Regex("""^[（(]?(\d+)[）).、．]\s*(.+)$""")
            val match = stepPattern.find(trimmed)

            if (match != null) {
                stepOrder++
                val desc = match.groupValues[2].trim()
                val duration = extractDuration(desc)
                steps.add(Step(stepOrder, desc, duration))
            } else if (stepOrder > 0) {
                // 可能是步骤的续行
                stepOrder++
                val duration = extractDuration(trimmed)
                steps.add(Step(stepOrder, trimmed, duration))
            } else {
                // 未编号但长句可能是步骤
                if (trimmed.length > 6) {
                    stepOrder++
                    val duration = extractDuration(trimmed)
                    steps.add(Step(stepOrder, trimmed, duration))
                }
            }
        }

        return steps
    }

    /** 从步骤描述中提取时间（秒） */
    private fun extractDuration(description: String): Int {
        for ((pattern, multiplier) in TIME_PATTERNS) {
            val match = pattern.find(description)
            if (match != null) {
                val value = match.groupValues[1].toIntOrNull() ?: 0
                return value * multiplier
            }
        }
        return 0
    }
}
