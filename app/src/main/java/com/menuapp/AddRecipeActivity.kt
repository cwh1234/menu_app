package com.menuapp

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.textfield.TextInputLayout
import com.menuapp.api.OcrClient
import com.menuapp.api.RecipeTextParser
import com.menuapp.data.RecipeData
import com.menuapp.databinding.ActivityAddRecipeBinding
import com.menuapp.model.Ingredient
import com.menuapp.model.MainDish
import com.menuapp.model.Recipe
import com.menuapp.model.Step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

class AddRecipeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
    }

    private lateinit var binding: ActivityAddRecipeBinding
    private lateinit var recipeData: RecipeData
    private var editingRecipeId: Long = -1L
    private var isEditMode: Boolean = false

    /** 选中图像的 base64 编码（不含前缀） */
    private var selectedImageBase64: String? = null
    /** 选中图像的 MIME 类型 */
    private var selectedImageMediaType: String? = null
    /** 相机拍照的临时文件 URI */
    private var cameraUri: Uri? = null

    /** 相机拍照 launcher */
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { processSelectedImage(it) }
        }
    }

    /** 相册选图 launcher */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processSelectedImage(it) }
    }

    /** 相机权限请求 launcher */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeData = RecipeData.getInstance(this)

        // 检查是否为编辑模式
        editingRecipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
        isEditMode = editingRecipeId != -1L

        setupToolbar()
        setupButtons()
        setupImageImport()

        if (isEditMode) {
            loadRecipeForEdit()
        }
    }

    private fun setupToolbar() {
        val title = if (isEditMode) R.string.edit_recipe_title else R.string.add_recipe_title
        binding.toolbar.title = getString(title)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupButtons() {
        // 添加配菜按钮
        binding.btnAddSideDish.setOnClickListener {
            addIngredientForm(binding.containerSideDishes, getString(R.string.side_dish_name), getString(R.string.side_dish_amount))
        }

        // 添加调料按钮
        binding.btnAddSeasoning.setOnClickListener {
            addIngredientForm(binding.containerSeasonings, getString(R.string.seasoning_name), getString(R.string.seasoning_amount))
        }

        // 添加步骤按钮
        binding.btnAddStep.setOnClickListener {
            addStepForm(binding.containerSteps, binding.containerSteps.childCount + 1)
        }

        // 保存按钮
        binding.btnSave.setOnClickListener {
            saveRecipe()
        }
    }

    /** 添加配菜/调料表单行 */
    private fun addIngredientForm(container: android.widget.LinearLayout, nameHint: String, amountHint: String) {
        val inflater = LayoutInflater.from(this)
        val itemView = inflater.inflate(R.layout.item_ingredient_form, container, false)

        val tilName = itemView.findViewById<TextInputLayout>(R.id.til_name)
        val tilAmount = itemView.findViewById<TextInputLayout>(R.id.til_amount)
        val btnRemove = itemView.findViewById<ImageButton>(R.id.btn_remove)

        tilName.hint = nameHint
        tilAmount.hint = amountHint

        btnRemove.setOnClickListener {
            container.removeView(itemView)
        }

        container.addView(itemView)
    }

    /** 添加步骤表单行 */
    private fun addStepForm(container: android.widget.LinearLayout, stepNumber: Int) {
        val inflater = LayoutInflater.from(this)
        val itemView = inflater.inflate(R.layout.item_step_form, container, false)

        val tvStepNumber = itemView.findViewById<TextView>(R.id.tv_step_number)
        val btnRemove = itemView.findViewById<ImageButton>(R.id.btn_remove)

        tvStepNumber.text = stepNumber.toString()

        // 监听步骤数量变化以更新编号
        btnRemove.setOnClickListener {
            container.removeView(itemView)
            updateStepNumbers(container)
        }

        container.addView(itemView)
    }

    /** 更新步骤编号 */
    private fun updateStepNumbers(container: android.widget.LinearLayout) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val tvNumber = child.findViewById<TextView>(R.id.tv_step_number)
            tvNumber?.text = (i + 1).toString()
        }
    }

    /** 加载要编辑的菜谱 */
    private fun loadRecipeForEdit() {
        val recipe = recipeData.getRecipeById(editingRecipeId) ?: return

        // 填充菜谱名称
        binding.etRecipeName.setText(recipe.name)

        // 填充主菜
        binding.etMainDishName.setText(recipe.mainDish.name)
        binding.etMainDishWeight.setText(recipe.mainDish.defaultWeight.toString())

        // 填充配菜
        for (sideDish in recipe.sideDishes) {
            addIngredientForm(binding.containerSideDishes, getString(R.string.side_dish_name), getString(R.string.side_dish_amount))
            val lastView = binding.containerSideDishes.getChildAt(binding.containerSideDishes.childCount - 1)
            val etName = lastView.findViewById<EditText>(R.id.et_name)
            val etAmount = lastView.findViewById<EditText>(R.id.et_amount)
            etName.setText(sideDish.name)
            etAmount.setText(sideDish.amount)
        }

        // 填充调料
        for (seasoning in recipe.seasonings) {
            addIngredientForm(binding.containerSeasonings, getString(R.string.seasoning_name), getString(R.string.seasoning_amount))
            val lastView = binding.containerSeasonings.getChildAt(binding.containerSeasonings.childCount - 1)
            val etName = lastView.findViewById<EditText>(R.id.et_name)
            val etAmount = lastView.findViewById<EditText>(R.id.et_amount)
            etName.setText(seasoning.name)
            etAmount.setText(seasoning.amount)
        }

        // 填充步骤
        for (step in recipe.steps.sortedBy { it.order }) {
            addStepForm(binding.containerSteps, step.order)
            val lastView = binding.containerSteps.getChildAt(binding.containerSteps.childCount - 1)
            val etDesc = lastView.findViewById<EditText>(R.id.et_desc)
            val etDuration = lastView.findViewById<EditText>(R.id.et_duration)
            etDesc.setText(step.description)
            if (step.durationSeconds > 0) {
                etDuration.setText(step.durationSeconds.toString())
            }
        }
    }

    /** 收集表单数据并保存 */
    private fun saveRecipe() {
        val recipeName = binding.etRecipeName.text?.toString()?.trim() ?: ""
        val mainDishName = binding.etMainDishName.text?.toString()?.trim() ?: ""
        val mainDishWeightStr = binding.etMainDishWeight.text?.toString()?.trim() ?: "0"

        // 验证必填项
        if (recipeName.isEmpty()) {
            Toast.makeText(this, R.string.error_recipe_name_empty, Toast.LENGTH_SHORT).show()
            binding.etRecipeName.requestFocus()
            return
        }
        if (mainDishName.isEmpty()) {
            Toast.makeText(this, R.string.error_main_dish_empty, Toast.LENGTH_SHORT).show()
            binding.etMainDishName.requestFocus()
            return
        }

        val mainDishWeight = mainDishWeightStr.toIntOrNull() ?: 0

        // 收集配菜
        val sideDishes = collectIngredients(binding.containerSideDishes)

        // 收集调料
        val seasonings = collectIngredients(binding.containerSeasonings)

        // 收集步骤
        val steps = collectSteps(binding.containerSteps)

        val recipe = Recipe(
            id = if (isEditMode) editingRecipeId else System.currentTimeMillis(),
            name = recipeName,
            mainDish = MainDish(mainDishName, mainDishWeight),
            sideDishes = sideDishes,
            seasonings = seasonings,
            steps = steps
        )

        try {
            if (isEditMode) {
                recipeData.updateRecipe(recipe)
            } else {
                recipeData.addRecipe(recipe)
            }
            // 保存导入的图像文件
            saveRecipeImage(recipe.id)
            Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 图像导入与自动填充 ====================

    /** 设置图像导入和剪切板按钮 */
    private fun setupImageImport() {
        binding.btnPasteClipboard.setOnClickListener {
            pasteFromClipboard()
        }
        binding.btnImportImage.setOnClickListener {
            showImageSourceDialog()
        }
        binding.btnRemoveImage.setOnClickListener {
            clearImage()
        }
        binding.btnAnalyzeImage.setOnClickListener {
            analyzeRecipe()
        }
    }

    /** 从剪切板粘贴文本并智能填充菜谱信息 */
    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0).text?.toString() ?: ""
        if (text.isBlank()) {
            Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val parsed = RecipeTextParser.parse(text)
        autoFillFromParsed(parsed)
        Toast.makeText(this, R.string.clipboard_filled, Toast.LENGTH_SHORT).show()
    }

    /** 弹出图片来源选择对话框 */
    private fun showImageSourceDialog() {
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.pick_from_gallery))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_image))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    /** 检查相机权限后启动拍照 */
    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    /** 启动相机拍照 */
    private fun launchCamera() {
        val photoFile = File(cacheDir, "recipe_photo_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        takePictureLauncher.launch(cameraUri!!)
    }

    /** 处理选中的图像：压缩、编码、显示预览 */
    private fun processSelectedImage(uri: Uri) {
        try {
            // 读取图像
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                Toast.makeText(this, "无法读取图像", Toast.LENGTH_SHORT).show()
                return
            }

            // 压缩图像（最大边长 1024px）
            val compressedBitmap = compressBitmap(originalBitmap, 1024)

            // 转为 base64
            val byteArrayOutputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            selectedImageBase64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            selectedImageMediaType = "image/jpeg"

            // 显示预览
            binding.ivRecipePreview.setImageBitmap(compressedBitmap)
            binding.cardImagePreview.visibility = View.VISIBLE

        } catch (e: Exception) {
            Toast.makeText(this, "图像处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 压缩 Bitmap，使最大边长不超过 maxSize px */
    private fun compressBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /** 清除图像 */
    private fun clearImage() {
        selectedImageBase64 = null
        selectedImageMediaType = null
        binding.ivRecipePreview.setImageDrawable(null)
        binding.cardImagePreview.visibility = View.GONE
    }

    /** 将选中的图像保存到内部存储 */
    private fun saveRecipeImage(recipeId: Long) {
        selectedImageBase64?.let { b64 ->
            try {
                val dir = File(filesDir, "recipe_images")
                dir.mkdirs()
                val file = File(dir, "${recipeId}.jpg")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                file.writeBytes(bytes)
            } catch (e: Exception) {
                // 图像保存失败不影响菜谱保存
            }
        }
    }

    /** 使用 ML Kit OCR + 本地解析分析菜谱图像 */
    /** 使用 ML Kit OCR + 本地解析分析菜谱图像 */
    private fun analyzeRecipe() {
        if (selectedImageBase64 == null) {
            Toast.makeText(this, "请先选择图像", Toast.LENGTH_SHORT).show()
            return
        }

        @Suppress("DEPRECATION")
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage(getString(R.string.analyzing))
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.Main).launch {
            // 从 base64 解码 Bitmap
            val bytes = android.util.Base64.decode(selectedImageBase64, android.util.Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (bitmap == null) {
                progressDialog.dismiss()
                Toast.makeText(this@AddRecipeActivity, "图像解码失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val ocrResult = OcrClient.extractText(bitmap)
            progressDialog.dismiss()

            ocrResult.fold(
                onSuccess = { text ->
                    if (text.isBlank()) {
                        Toast.makeText(this@AddRecipeActivity, "未识别到文字，请确保图片清晰", Toast.LENGTH_SHORT).show()
                        return@fold
                    }
                    val parsed = RecipeTextParser.parse(text)
                    autoFillFromParsed(parsed)
                    Toast.makeText(this@AddRecipeActivity, R.string.analyze_success, Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(this@AddRecipeActivity, "OCR 识别失败: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    /** 从本地解析器结果填充表单 */
    private fun autoFillFromParsed(parsed: RecipeTextParser.ParsedRecipe) {
        binding.etRecipeName.setText(parsed.name)
        if (parsed.mainDish.name.isNotEmpty()) {
            binding.etMainDishName.setText(parsed.mainDish.name)
        }
        if (parsed.mainDish.defaultWeight > 0) {
            binding.etMainDishWeight.setText(parsed.mainDish.defaultWeight.toString())
        }

        for (item in parsed.sideDishes) {
            addIngredientForm(binding.containerSideDishes, getString(R.string.side_dish_name), getString(R.string.side_dish_amount))
            val lastView = binding.containerSideDishes.getChildAt(binding.containerSideDishes.childCount - 1)
            lastView.findViewById<EditText>(R.id.et_name).setText(item.name)
            lastView.findViewById<EditText>(R.id.et_amount).setText(item.amount)
        }

        for (item in parsed.seasonings) {
            addIngredientForm(binding.containerSeasonings, getString(R.string.seasoning_name), getString(R.string.seasoning_amount))
            val lastView = binding.containerSeasonings.getChildAt(binding.containerSeasonings.childCount - 1)
            lastView.findViewById<EditText>(R.id.et_name).setText(item.name)
            lastView.findViewById<EditText>(R.id.et_amount).setText(item.amount)
        }

        for (step in parsed.steps) {
            addStepForm(binding.containerSteps, binding.containerSteps.childCount + 1)
            val lastView = binding.containerSteps.getChildAt(binding.containerSteps.childCount - 1)
            lastView.findViewById<EditText>(R.id.et_desc).setText(step.description)
            if (step.durationSeconds > 0) {
                lastView.findViewById<EditText>(R.id.et_duration).setText(step.durationSeconds.toString())
            }
        }
    }

    // ==================== 原有数据收集方法 ====================

    /** 从容器中收集配料数据 */
    private fun collectIngredients(container: android.widget.LinearLayout): MutableList<Ingredient> {
        val ingredients = mutableListOf<Ingredient>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val etName = child.findViewById<EditText>(R.id.et_name)
            val etAmount = child.findViewById<EditText>(R.id.et_amount)

            val name = etName?.text?.toString()?.trim() ?: ""
            val amount = etAmount?.text?.toString()?.trim() ?: ""

            if (name.isNotEmpty()) {
                ingredients.add(Ingredient(name, amount))
            }
        }
        return ingredients
    }

    /** 从容器中收集步骤数据 */
    private fun collectSteps(container: android.widget.LinearLayout): MutableList<Step> {
        val steps = mutableListOf<Step>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val etDesc = child.findViewById<EditText>(R.id.et_desc)
            val etDuration = child.findViewById<EditText>(R.id.et_duration)

            val description = etDesc?.text?.toString()?.trim() ?: ""
            val durationStr = etDuration?.text?.toString()?.trim() ?: "0"
            val duration = durationStr.toIntOrNull() ?: 0

            if (description.isNotEmpty()) {
                steps.add(Step(i + 1, description, duration))
            }
        }
        return steps
    }
}
