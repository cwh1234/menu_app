package com.menuapp.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable

/**
 * 菜谱数据模型
 */
data class Recipe(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",                           // 菜谱名称
    val mainDish: MainDish = MainDish(),             // 主菜
    val sideDishes: MutableList<Ingredient> = mutableListOf(),  // 配菜列表
    val seasonings: MutableList<Ingredient> = mutableListOf(),  // 调料列表
    val steps: MutableList<Step> = mutableListOf()               // 步骤列表
) : Serializable {

    companion object {
        private val gson = Gson()

        /** 将 Recipe 列表序列化为 JSON 字符串 */
        fun listToJson(list: List<Recipe>): String {
            return gson.toJson(list)
        }

        /** 从 JSON 字符串反序列化为 Recipe 列表 */
        fun listFromJson(json: String): List<Recipe> {
            val type = object : TypeToken<List<Recipe>>() {}.type
            return try {
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        /** 生成默认示例菜谱 */
        fun createSampleRecipes(): List<Recipe> {
            return listOf(
                Recipe(
                    id = 1,
                    name = "红烧肉",
                    mainDish = MainDish("五花肉", 500),
                    sideDishes = mutableListOf(
                        Ingredient("土豆", "2个"),
                        Ingredient("胡萝卜", "1根")
                    ),
                    seasonings = mutableListOf(
                        Ingredient("生抽", "2勺"),
                        Ingredient("老抽", "1勺"),
                        Ingredient("冰糖", "30克"),
                        Ingredient("料酒", "2勺"),
                        Ingredient("八角", "2个"),
                        Ingredient("桂皮", "1块"),
                        Ingredient("姜", "5片"),
                        Ingredient("葱", "2段")
                    ),
                    steps = mutableListOf(
                        Step(1, "五花肉切块，冷水下锅焯水，捞出洗净备用", 0),
                        Step(2, "锅中放少许油，加入冰糖小火炒至焦糖色", 120),
                        Step(3, "放入五花肉翻炒上色，加入料酒、生抽、老抽", 60),
                        Step(4, "加入八角、桂皮、姜片、葱段，倒入开水没过肉块", 0),
                        Step(5, "大火烧开后转小火，加盖炖煮", 1800),
                        Step(6, "加入切好的土豆和胡萝卜，继续炖煮", 900),
                        Step(7, "大火收汁，装盘即可", 300)
                    )
                ),
                Recipe(
                    id = 2,
                    name = "清炒西兰花",
                    mainDish = MainDish("西兰花", 300),
                    sideDishes = mutableListOf(
                        Ingredient("蒜末", "适量"),
                        Ingredient("红椒", "半个")
                    ),
                    seasonings = mutableListOf(
                        Ingredient("盐", "适量"),
                        Ingredient("生抽", "1勺"),
                        Ingredient("食用油", "适量")
                    ),
                    steps = mutableListOf(
                        Step(1, "西兰花切小朵，用盐水浸泡10分钟后洗净", 0),
                        Step(2, "锅中烧水，水开后放入西兰花焯水", 120),
                        Step(3, "捞出西兰花过凉水，沥干备用", 0),
                        Step(4, "热锅凉油，放入蒜末爆香", 30),
                        Step(5, "放入西兰花和红椒翻炒", 120),
                        Step(6, "加入盐和生抽调味，翻炒均匀出锅", 30)
                    )
                ),
                Recipe(
                    id = 3,
                    name = "番茄炒蛋",
                    mainDish = MainDish("鸡蛋", 200),
                    sideDishes = mutableListOf(
                        Ingredient("番茄", "2个"),
                        Ingredient("葱花", "适量")
                    ),
                    seasonings = mutableListOf(
                        Ingredient("盐", "适量"),
                        Ingredient("糖", "少许"),
                        Ingredient("食用油", "适量")
                    ),
                    steps = mutableListOf(
                        Step(1, "鸡蛋打散加少许盐搅匀，番茄切块备用", 0),
                        Step(2, "热锅多油，倒入蛋液炒至凝固盛出", 45),
                        Step(3, "锅中留底油，放入番茄块翻炒出汁", 120),
                        Step(4, "倒回炒好的鸡蛋，加盐和糖调味", 60),
                        Step(5, "翻炒均匀，撒上葱花出锅", 30)
                    )
                )
            )
        }
    }
}

/**
 * 主菜信息
 */
data class MainDish(
    val name: String = "",
    val defaultWeight: Int = 0  // 默认重量（克）
) : Serializable

/**
 * 配料/调料
 */
data class Ingredient(
    val name: String = "",
    val amount: String = ""  // 用量描述
) : Serializable {
    companion object {
        /**
         * 解析食材用量字符串，提取数值和单位
         * @return Pair<Double?, String> — (数值或null, 单位)
         *         例: "30克"→(30.0,"克"), "2个"→(2.0,"个"), "适量"→(null,"适量")
         */
        fun parseAmount(amount: String): Pair<Double?, String> {
            val regex = Regex("""^([\d.]+)\s*(.*)$""")
            val match = regex.find(amount.trim())
            return if (match != null) {
                val value = match.groupValues[1].toDoubleOrNull()
                val unit = match.groupValues[2].ifEmpty { "" }
                Pair(value, unit)
            } else {
                Pair(null, amount.trim())
            }
        }
    }
}

/**
 * 烹饪步骤
 */
data class Step(
    val order: Int = 0,           // 步骤序号
    val description: String = "", // 步骤描述
    val durationSeconds: Int = 0  // 计时时长（秒），0 表示无需计时
) : Serializable
