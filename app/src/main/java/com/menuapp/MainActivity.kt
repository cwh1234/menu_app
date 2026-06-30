package com.menuapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.menuapp.adapter.RecipeAdapter
import com.menuapp.data.RecipeData
import com.menuapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), RecipeData.OnDataChangedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recipeData: RecipeData
    private lateinit var adapter: RecipeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeData = RecipeData.getInstance(this)
        recipeData.addOnDataChangedListener(this)

        setupToolbar()
        setupRecyclerView()
        setupFab()

        refreshUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        recipeData.removeOnDataChangedListener(this)
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun setupToolbar() {
        binding.toolbar.title = getString(R.string.main_title)
    }

    private fun setupRecyclerView() {
        binding.rvRecipes.layoutManager = LinearLayoutManager(this)

        adapter = RecipeAdapter(emptyList()) { recipe ->
            // 点击进入详情页
            val intent = Intent(this, DetailActivity::class.java).apply {
                putExtra(DetailActivity.EXTRA_RECIPE_ID, recipe.id)
            }
            startActivity(intent)
        }

        binding.rvRecipes.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, AddRecipeActivity::class.java)
            startActivity(intent)
        }
    }

    private fun refreshUI() {
        val recipes = recipeData.getAllRecipes()
        adapter.updateData(recipes)

        if (recipes.isEmpty()) {
            binding.tvEmptyHint.visibility = View.VISIBLE
            binding.rvRecipes.visibility = View.GONE
        } else {
            binding.tvEmptyHint.visibility = View.GONE
            binding.rvRecipes.visibility = View.VISIBLE
        }
    }

    override fun onDataChanged() {
        runOnUiThread {
            refreshUI()
        }
    }
}
