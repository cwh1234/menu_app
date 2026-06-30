package com.menuapp

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.menuapp.data.RecipeData
import com.menuapp.databinding.ActivityDetailBinding
import com.menuapp.model.Recipe
import com.menuapp.model.Step
import com.menuapp.model.Ingredient

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
    }

    private lateinit var binding: ActivityDetailBinding
    private lateinit var recipeData: RecipeData
    private var recipe: Recipe? = null

    /** 存储每个步骤的计时器实例，key = step order */
    private val timers = mutableMapOf<Int, CountDownTimer>()

    /** 当前重量缩放比例 (1.0 = 默认, 无缩放) */
    private var scalingFactor: Double = 1.0

    /** 重量输入框的 TextWatcher 引用，避免重复添加 */
    private var weightWatcher: TextWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeData = RecipeData.getInstance(this)

        val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1)
        if (recipeId != -1L) {
            recipe = recipeData.getRecipeById(recipeId)
        }

        setupToolbar()
        if (recipe != null) {
            populateUI()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        // 清理所有计时器
        timers.values.forEach { it.cancel() }
        timers.clear()
        super.onDestroy()
    }

    private fun setupToolbar() {
        binding.toolbar.title = recipe?.name ?: getString(R.string.detail_title)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnEdit.setOnClickListener {
            recipe?.let { r ->
                val intent = Intent(this, AddRecipeActivity::class.java).apply {
                    putExtra(AddRecipeActivity.EXTRA_RECIPE_ID, r.id)
                }
                startActivity(intent)
            }
        }

        binding.btnDelete.setOnClickListener {
            showDeleteDialog()
        }
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(R.string.confirm_delete_msg)
            .setPositiveButton(R.string.yes) { _, _ ->
                recipe?.let { recipeData.deleteRecipe(it.id) }
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun populateUI() {
        // 取消所有运行中的计时器（UI将被重建）
        timers.values.forEach { it.cancel() }
        timers.clear()

        val r = recipe ?: return

        binding.toolbar.title = r.name
        binding.tvRecipeName.text = r.name

        // 加载菜谱图片
        val imgFile = java.io.File(filesDir, "recipe_images/${r.id}.jpg")
        if (imgFile.exists()) {
            binding.ivRecipeImage.setImageBitmap(android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath))
            binding.ivRecipeImage.visibility = View.VISIBLE
        } else {
            binding.ivRecipeImage.visibility = View.GONE
        }

        binding.tvMainDishName.text = r.mainDish.name
        binding.tvMainDishWeight.text = getString(R.string.default_weight) + ": ${r.mainDish.defaultWeight}${getString(R.string.gram_unit)}"

        // 配菜
        populateIngredients(binding.containerSideDishes, r.sideDishes)
        // 隐藏配菜卡片如果没有配菜
        if (r.sideDishes.isEmpty()) {
            binding.cardSideDishes.visibility = View.GONE
        }

        // 调料
        populateIngredients(binding.containerSeasonings, r.seasonings)
        if (r.seasonings.isEmpty()) {
            binding.cardSeasonings.visibility = View.GONE
        }

        // 步骤
        populateSteps(binding.containerSteps, r.steps)

        // 清空重量输入并设置监听
        weightWatcher?.let { binding.etActualWeight.removeTextChangedListener(it) }
        binding.etActualWeight.setText("")
        setupWeightInput()
    }

    /** 填充配料/调料列表 */
    private fun populateIngredients(container: LinearLayout, ingredients: List<com.menuapp.model.Ingredient>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (ingredient in ingredients) {
            val itemView = inflater.inflate(R.layout.item_ingredient_detail, container, false)
            val tvName = itemView.findViewById<TextView>(R.id.tv_ingredient_name)
            val tvAmount = itemView.findViewById<TextView>(R.id.tv_ingredient_amount)

            tvName.text = ingredient.name
            tvAmount.text = ingredient.amount

            // 将原始用量字符串存为tag，供 refreshIngredientAmounts 使用
            itemView.tag = ingredient.amount

            container.addView(itemView)
        }
    }

    /** 填充步骤列表（含倒计时） */
    private fun populateSteps(container: LinearLayout, steps: List<Step>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (step in steps.sortedBy { it.order }) {
            val itemView = inflater.inflate(R.layout.item_step_detail, container, false)

            val tvStepOrder = itemView.findViewById<TextView>(R.id.tv_step_order)
            val tvStepDesc = itemView.findViewById<TextView>(R.id.tv_step_desc)
            val layoutTimer = itemView.findViewById<LinearLayout>(R.id.layout_timer)
            val tvTimerDisplay = itemView.findViewById<TextView>(R.id.tv_timer_display)
            val btnTimerStart = itemView.findViewById<Button>(R.id.btn_timer_start)
            val btnTimerReset = itemView.findViewById<Button>(R.id.btn_timer_reset)
            val progressTimer = itemView.findViewById<ProgressBar>(R.id.progress_timer)

            tvStepOrder.text = step.order.toString()
            tvStepDesc.text = step.description

            // 需要计时的步骤显示计时器
            if (step.durationSeconds > 0) {
                layoutTimer.visibility = View.VISIBLE
                val totalSeconds = step.durationSeconds
                tvTimerDisplay.text = formatTime(totalSeconds)

                btnTimerStart.setOnClickListener {
                    handleTimerAction(
                        step.order,
                        totalSeconds,
                        tvTimerDisplay,
                        btnTimerStart,
                        progressTimer
                    )
                }

                btnTimerReset.setOnClickListener {
                    // 取消现有计时器
                    timers[step.order]?.cancel()
                    timers.remove(step.order)
                    tvTimerDisplay.text = formatTime(totalSeconds)
                    tvTimerDisplay.setTextColor(getColor(R.color.timer_active))
                    btnTimerStart.text = getString(R.string.start_timer)
                    btnTimerStart.setBackgroundColor(getColor(R.color.timer_inactive))
                    progressTimer.progress = 0
                    progressTimer.visibility = View.GONE
                }
            }

            container.addView(itemView)
        }
    }

    /** 处理计时器开始/暂停 */
    private fun handleTimerAction(
        stepOrder: Int,
        totalSeconds: Int,
        tvDisplay: TextView,
        btnStart: Button,
        progressBar: ProgressBar
    ) {
        val existingTimer = timers[stepOrder]

        if (existingTimer != null) {
            // 计时器正在运行，暂停
            existingTimer.cancel()
            timers.remove(stepOrder)
            btnStart.text = getString(R.string.start_timer)
            btnStart.setBackgroundColor(getColor(R.color.timer_inactive))
            return
        }

        // 解析当前显示时间作为起始
        val currentDisplay = tvDisplay.text.toString()
        val parts = currentDisplay.split(":")
        val currentSeconds = if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            totalSeconds
        }

        if (currentSeconds <= 0) {
            tvDisplay.text = formatTime(totalSeconds)
            btnStart.text = getString(R.string.start_timer)
            return
        }

        // 显示进度条
        if (currentSeconds == totalSeconds) {
            progressBar.progress = 0
        }
        progressBar.visibility = View.VISIBLE

        // 开始倒计时
        btnStart.text = getString(R.string.pause_timer)
        btnStart.setBackgroundColor(getColor(R.color.timer_active))
        tvDisplay.setTextColor(getColor(R.color.timer_active))

        val timer = object : CountDownTimer(currentSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remainingSeconds = (millisUntilFinished / 1000).toInt()
                tvDisplay.text = formatTime(remainingSeconds)
                // 更新进度条
                val progress = ((totalSeconds - remainingSeconds).toFloat() / totalSeconds * 100).toInt()
                progressBar.progress = progress
            }

            override fun onFinish() {
                tvDisplay.text = "00:00"
                tvDisplay.setTextColor(Color.RED)
                progressBar.progress = 100
                btnStart.text = getString(R.string.start_timer)
                btnStart.setBackgroundColor(getColor(R.color.timer_inactive))
                timers.remove(stepOrder)
            }
        }

        timers[stepOrder] = timer
        timer.start()
    }

    /** 格式化时间为 MM:SS */
    private fun formatTime(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    /** 设置重量输入监听（TextWatcher） */
    private fun setupWeightInput() {
        // 先移除旧的监听器
        weightWatcher?.let { binding.etActualWeight.removeTextChangedListener(it) }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val inputStr = s?.toString()?.trim() ?: ""
                val actualWeight = inputStr.toDoubleOrNull()
                val defaultWeight = recipe?.mainDish?.defaultWeight ?: 0

                if (actualWeight != null && actualWeight > 0 && defaultWeight > 0) {
                    scalingFactor = actualWeight / defaultWeight
                    binding.tvWeightRatio.visibility = View.VISIBLE
                    binding.tvWeightRatio.text = getString(R.string.weight_ratio).format(scalingFactor)
                } else {
                    scalingFactor = 1.0
                    binding.tvWeightRatio.visibility = View.GONE
                }

                refreshIngredientAmounts()
            }
        }
        weightWatcher = watcher
        binding.etActualWeight.addTextChangedListener(watcher)
    }

    /** 刷新所有配菜和调料的用量显示（根据 scalingFactor 缩放） */
    private fun refreshIngredientAmounts() {
        refreshContainerAmounts(binding.containerSideDishes)
        refreshContainerAmounts(binding.containerSeasonings)
    }

    /** 刷新单个容器（配菜或调料）内的所有用量 */
    private fun refreshContainerAmounts(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            val itemView = container.getChildAt(i)
            val originalAmount = itemView.tag as? String ?: ""
            val tvAmount = itemView.findViewById<TextView>(R.id.tv_ingredient_amount)
            val tvAdjusted = itemView.findViewById<TextView>(R.id.tv_ingredient_adjusted)

            val (numericValue, unit) = Ingredient.parseAmount(originalAmount)

            if (numericValue != null && scalingFactor != 1.0) {
                // 计算调整后的值
                val adjustedValue = numericValue * scalingFactor
                // 格式化：若为整数则不显示小数
                val adjustedStr = if (adjustedValue == adjustedValue.toLong().toDouble()) {
                    "${adjustedValue.toLong()}$unit"
                } else {
                    "${"%.1f".format(adjustedValue)}$unit"
                }

                // 原用量变灰+删除线
                tvAmount.paintFlags = tvAmount.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvAmount.setTextColor(getColor(R.color.gray_medium))
                tvAmount.textSize = 12f

                // 调整后用量显示为主色加粗
                tvAdjusted.text = adjustedStr
                tvAdjusted.visibility = View.VISIBLE
            } else {
                // 恢复原始显示
                tvAmount.paintFlags = tvAmount.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvAmount.setTextColor(getColor(R.color.primary))
                tvAmount.textSize = 14f
                tvAmount.text = originalAmount
                tvAdjusted.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从编辑页返回时刷新数据
        val recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1)
        if (recipeId != -1L) {
            val updatedRecipe = recipeData.getRecipeById(recipeId)
            if (updatedRecipe != null && updatedRecipe != recipe) {
                recipe = updatedRecipe
                populateUI()
            }
        }
    }
}
