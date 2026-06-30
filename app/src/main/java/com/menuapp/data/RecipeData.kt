package com.menuapp.data

import android.content.Context
import com.menuapp.model.Recipe

/**
 * 菜谱数据管理器
 * 使用 SharedPreferences + JSON 进行本地持久化
 */
class RecipeData private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var recipes: MutableList<Recipe> = mutableListOf()
    private val listeners: MutableList<OnDataChangedListener> = mutableListOf()

    init {
        loadFromPrefs()
        // 首次启动时加载示例数据
        if (recipes.isEmpty()) {
            recipes.addAll(Recipe.createSampleRecipes())
            saveToPrefs()
        }
    }

    /** 获取所有菜谱 */
    fun getAllRecipes(): List<Recipe> = recipes.toList()

    /** 根据 ID 获取菜谱 */
    fun getRecipeById(id: Long): Recipe? = recipes.find { it.id == id }

    /** 添加菜谱 */
    fun addRecipe(recipe: Recipe) {
        recipes.add(recipe)
        saveToPrefs()
        notifyDataChanged()
    }

    /** 更新菜谱 */
    fun updateRecipe(recipe: Recipe) {
        val index = recipes.indexOfFirst { it.id == recipe.id }
        if (index >= 0) {
            recipes[index] = recipe
            saveToPrefs()
            notifyDataChanged()
        }
    }

    /** 删除菜谱 */
    fun deleteRecipe(id: Long) {
        recipes.removeAll { it.id == id }
        saveToPrefs()
        notifyDataChanged()
    }

    /** 注册数据变更监听 */
    fun addOnDataChangedListener(listener: OnDataChangedListener) {
        listeners.add(listener)
    }

    /** 移除数据变更监听 */
    fun removeOnDataChangedListener(listener: OnDataChangedListener) {
        listeners.remove(listener)
    }

    private fun notifyDataChanged() {
        listeners.forEach { it.onDataChanged() }
    }

    /** 从 SharedPreferences 加载 */
    private fun loadFromPrefs() {
        val json = prefs.getString(KEY_RECIPES, null)
        if (!json.isNullOrEmpty()) {
            val list = Recipe.listFromJson(json)
            recipes = list.toMutableList()
        }
    }

    /** 保存到 SharedPreferences */
    private fun saveToPrefs() {
        val json = Recipe.listToJson(recipes)
        prefs.edit().putString(KEY_RECIPES, json).apply()
    }

    interface OnDataChangedListener {
        fun onDataChanged()
    }

    companion object {
        private const val PREFS_NAME = "menu_app_prefs"
        private const val KEY_RECIPES = "recipes"

        @Volatile
        private var instance: RecipeData? = null

        fun getInstance(context: Context): RecipeData {
            return instance ?: synchronized(this) {
                instance ?: RecipeData(context.applicationContext).also { instance = it }
            }
        }
    }
}
