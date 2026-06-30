package com.menuapp.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.menuapp.R
import com.menuapp.model.Recipe
import java.io.File

/**
 * 菜谱列表适配器
 */
class RecipeAdapter(
    private var recipes: List<Recipe>,
    private val onItemClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.iv_recipe_thumb)
        val tvName: TextView = view.findViewById(R.id.tv_recipe_name)
        val tvMainDish: TextView = view.findViewById(R.id.tv_main_dish)
        val tvStepCount: TextView = view.findViewById(R.id.tv_step_count)
        val tvSideDishCount: TextView = view.findViewById(R.id.tv_side_dish_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recipe = recipes[position]

        // 加载缩略图
        val imgFile = File(holder.itemView.context.filesDir, "recipe_images/${recipe.id}.jpg")
        if (imgFile.exists()) {
            holder.ivThumb.setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
            holder.ivThumb.visibility = View.VISIBLE
        } else {
            holder.ivThumb.visibility = View.GONE
        }

        holder.tvName.text = recipe.name
        holder.tvMainDish.text = "${recipe.mainDish.name}  ${recipe.mainDish.defaultWeight}克"
        holder.tvStepCount.text = "${recipe.steps.size} 个步骤"
        holder.tvSideDishCount.text = "${recipe.sideDishes.size} 种配菜 · ${recipe.seasonings.size} 种调料"

        holder.itemView.setOnClickListener {
            onItemClick(recipe)
        }
    }

    override fun getItemCount(): Int = recipes.size

    /** 更新数据 */
    fun updateData(newRecipes: List<Recipe>) {
        recipes = newRecipes
        notifyDataSetChanged()
    }
}
