package com.pv.realesrgan.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.pv.realesrgan.R
import com.pv.realesrgan.databinding.ActivityMainBinding
import com.pv.realesrgan.databinding.DialogFullscreenPreviewBinding
import com.pv.realesrgan.databinding.DialogLoadUrlBinding
import com.pv.realesrgan.ml.HardwareDelegate
import com.pv.realesrgan.ml.ModelArchitecture
import com.pv.realesrgan.ml.RealESRGANUpscalerEngine
import com.pv.realesrgan.ml.UpscaleConfig
import com.pv.realesrgan.ml.UpscaleProgressListener
import com.pv.realesrgan.utils.ImageUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var upscalerEngine: RealESRGANUpscalerEngine

    private var originalBitmap: Bitmap? = null
    private var upscaledBitmap: Bitmap? = null
    private var upscaleJob: Job? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            loadSelectedImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        upscalerEngine = RealESRGANUpscalerEngine(applicationContext)

        setupListeners()
        updateUiState(isProcessing = false)
    }

    private fun setupListeners() {
        binding.btnPickImageEmpty.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnLoadUrlEmpty.setOnClickListener {
            showLoadUrlDialog()
        }

        binding.btnLoadUrlTop.setOnClickListener {
            showLoadUrlDialog()
        }

        binding.btnChangeImage.setOnClickListener {
            showImageSourceChooser()
        }

        binding.btnInspectFullscreen.setOnClickListener {
            showFullscreenPreviewDialog()
        }

        binding.chipGroupScale.setOnCheckedStateChangeListener { _, checkedIds ->
            val isCustom = checkedIds.contains(R.id.chipScaleCustom)
            binding.layoutCustomScale.visibility = if (isCustom) View.VISIBLE else View.GONE
            updateResolutionInfo()
        }

        binding.sliderCustomScale.addOnChangeListener { _, value, _ ->
            binding.tvCustomScaleValue.text = getScaleDisplayString(value)
            updateResolutionInfo()
        }

        binding.chipPreset15x.setOnClickListener {
            binding.sliderCustomScale.value = 1.5f
        }
        binding.chipPreset3x.setOnClickListener {
            binding.sliderCustomScale.value = 3.0f
        }
        binding.chipPreset5x.setOnClickListener {
            binding.sliderCustomScale.value = 5.0f
        }
        binding.chipPreset8x.setOnClickListener {
            binding.sliderCustomScale.value = 8.0f
        }

        binding.btnUpscale.setOnClickListener {
            startUpscale()
        }

        binding.btnCancel.setOnClickListener {
            cancelUpscale()
        }

        binding.btnSave.setOnClickListener {
            saveUpscaledImage()
        }

        binding.btnShare.setOnClickListener {
            shareUpscaledImage()
        }

        binding.btnInspect.setOnClickListener {
            showFullscreenPreviewDialog()
        }

        binding.btnAboutCredits.setOnClickListener {
            showAboutCreditsDialog()
        }
    }

    private fun showImageSourceChooser() {
        val options = arrayOf(
            getString(R.string.source_gallery),
            getString(R.string.source_url)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_source_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> showLoadUrlDialog()
                }
            }
            .show()
    }

    private fun showLoadUrlDialog() {
        val dialogBinding = DialogLoadUrlBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_url_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.dialog_action_load), null)
            .setNegativeButton(getString(R.string.dialog_action_cancel), null)
            .create()

        dialog.setOnShowListener {
            val loadButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            loadButton.setOnClickListener {
                val inputUrl = dialogBinding.etImageUrl.text?.toString()?.trim().orEmpty()
                if (inputUrl.isEmpty()) {
                    dialogBinding.tilImageUrl.error = getString(R.string.error_empty_url)
                    return@setOnClickListener
                }
                if (!inputUrl.startsWith("http://", ignoreCase = true) && !inputUrl.startsWith("https://", ignoreCase = true)) {
                    dialogBinding.tilImageUrl.error = getString(R.string.error_invalid_url)
                    return@setOnClickListener
                }
                dialogBinding.tilImageUrl.error = null
                dialog.dismiss()
                loadImageFromUrl(inputUrl)
            }
        }

        // Quick sample chips for 1-click loading & testing
        dialogBinding.chipSamplePhoto.setOnClickListener {
            dialogBinding.etImageUrl.setText(getString(R.string.sample_photo_url))
            dialogBinding.tilImageUrl.error = null
        }

        dialogBinding.chipSampleAnime.setOnClickListener {
            dialogBinding.etImageUrl.setText(getString(R.string.sample_anime_url))
            dialogBinding.tilImageUrl.error = null
        }

        dialogBinding.chipPasteClipboard.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            val text = clipData?.getItemAt(0)?.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true))) {
                dialogBinding.etImageUrl.setText(text)
                dialogBinding.tilImageUrl.error = null
            } else {
                Toast.makeText(this, getString(R.string.toast_clipboard_empty), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun loadImageFromUrl(url: String) {
        binding.tvStatus.text = getString(R.string.status_downloading_url)
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true
        updateUiState(isProcessing = true)

        lifecycleScope.launch(Dispatchers.Main) {
            val result = ImageUtils.loadBitmapFromUrl(url)
            updateUiState(isProcessing = false)
            binding.progressBar.isIndeterminate = false
            binding.progressBar.visibility = View.GONE

            result.onSuccess { bitmap ->
                originalBitmap?.recycle()
                upscaledBitmap?.recycle()
                originalBitmap = bitmap
                upscaledBitmap = null

                binding.emptyStateView.visibility = View.GONE
                binding.sliderView.visibility = View.VISIBLE
                binding.btnInspectFullscreen.visibility = View.VISIBLE
                binding.sliderView.setBitmaps(bitmap, null)

                updateResolutionInfo()

                val scaleStr = getScaleDisplayString(getSelectedScaleMultiplier())
                binding.layoutSaveShare.visibility = View.GONE
                binding.tvStatus.text = getString(R.string.status_image_loaded, bitmap.width, bitmap.height, scaleStr)
            }.onFailure { exception ->
                val errorMsg = exception.localizedMessage ?: exception.message ?: "Network error"
                binding.tvStatus.text = getString(R.string.status_error, errorMsg)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_load_failed, errorMsg),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getSelectedScaleMultiplier(): Float {
        return when {
            binding.chipScaleCustom.isChecked -> binding.sliderCustomScale.value
            binding.chipScale2x.isChecked -> 2.0f
            else -> 4.0f
        }
    }

    private fun getScaleDisplayString(scale: Float): String {
        return if (scale % 1.0f == 0f) "${scale.toInt()}x" else String.format("%.1fx", scale)
    }

    private fun updateResolutionInfo() {
        val bitmap = originalBitmap ?: return
        val inW = bitmap.width
        val inH = bitmap.height
        val scale = getSelectedScaleMultiplier()
        val scaleStr = getScaleDisplayString(scale)
        val outW = (inW * scale).roundToInt()
        val outH = (inH * scale).roundToInt()

        binding.cardResolutionInfo.visibility = View.VISIBLE
        binding.tvResolutionInput.text = getString(R.string.resolution_input, inW, inH)
        binding.tvResolutionOutput.text = getString(R.string.resolution_output, scaleStr, outW, outH)
        binding.btnUpscale.text = getString(R.string.upscale_button_action, scaleStr)
    }

    private fun showFullscreenPreviewDialog() {
        val before = originalBitmap ?: return
        val after = upscaledBitmap
        val scale = getSelectedScaleMultiplier()
        val scaleStr = getScaleDisplayString(scale)

        val dialogBinding = DialogFullscreenPreviewBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogBinding.root)
            .create()

        // Force dialog to fill the entire screen
        dialog.window?.apply {
            setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        val badgeLabel = getString(R.string.slider_badge_upscaled, scaleStr)
        dialogBinding.fullscreenSliderView.setBitmaps(before, after, badgeLabel)

        dialogBinding.tvFullscreenTitle.text = if (after != null) {
            getString(R.string.resolution_output, scaleStr, after.width, after.height)
        } else {
            getString(R.string.resolution_input, before.width, before.height)
        }

        dialogBinding.btnCloseFullscreen.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnFullscreenResetZoom.setOnClickListener {
            dialogBinding.fullscreenSliderView.resetZoom()
        }

        dialogBinding.btnFullscreenShare.visibility = if (after != null) View.VISIBLE else View.GONE
        dialogBinding.btnFullscreenSave.visibility = if (after != null) View.VISIBLE else View.GONE

        dialogBinding.btnFullscreenShare.setOnClickListener {
            shareUpscaledImage()
        }

        dialogBinding.btnFullscreenSave.setOnClickListener {
            saveUpscaledImage()
        }

        dialog.show()

        // Apply layout AFTER show() so window is created
        dialog.window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    private fun showAboutCreditsDialog() {
        val creditsMessage = """
            🎨 Real-ESRGAN AI Super-Resolution
            
            Based on the pioneering work by Xintao Wang et al.
            
            • Paper: "Real-ESRGAN: Training Real-World Blind Super-Resolution with Pure Synthetic Data" (ICCVW 2021)
            • Authors: Xintao Wang, Liangbin Xie, Chao Dong, Ying Shan
            • Laboratory: ARC Lab, Tencent PCG & SIAT
            • Official Repo: github.com/xinntao/Real-ESRGAN
            • Model License: BSD 3-Clause License
            
            ⚙️ On-Device Runtime:
            • Engine: Microsoft ONNX Runtime Mobile (MIT License)
            • Acceleration: Android NNAPI / Qualcomm Hexagon NPU / ARM NEON
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("Credits & Attribution")
            .setMessage(creditsMessage)
            .setPositiveButton("Open GitHub") { _, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xinntao/Real-ESRGAN"))
                startActivity(browserIntent)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun loadSelectedImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = ImageUtils.loadBitmapFromUri(applicationContext, uri)
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    originalBitmap?.recycle()
                    upscaledBitmap?.recycle()
                    originalBitmap = bitmap
                    upscaledBitmap = null

                    binding.emptyStateView.visibility = View.GONE
                    binding.sliderView.visibility = View.VISIBLE
                    binding.btnInspectFullscreen.visibility = View.VISIBLE
                    binding.sliderView.setBitmaps(bitmap, null)

                    updateResolutionInfo()

                    val scaleStr = getScaleDisplayString(getSelectedScaleMultiplier())
                    binding.layoutSaveShare.visibility = View.GONE
                    binding.tvStatus.text = getString(R.string.status_image_loaded, bitmap.width, bitmap.height, scaleStr)
                    binding.progressBar.visibility = View.GONE
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_load_failed, "Could not open selected image"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startUpscale() {
        val input = originalBitmap
        if (input == null) {
            Toast.makeText(this, getString(R.string.toast_pick_image), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedModel = if (binding.chipModelAnime6B.isChecked) {
            ModelArchitecture.REAL_ESRGAN_ANIME_6B
        } else {
            ModelArchitecture.REAL_ESRGAN_X4PLUS
        }

        val selectedDelegate = when {
            binding.chipHwNpu.isChecked -> HardwareDelegate.NPU_NNAPI
            binding.chipHwCpu.isChecked -> HardwareDelegate.CPU
            else -> HardwareDelegate.AUTO
        }

        val tileSize = if (binding.chipTile128.isChecked) 128 else 256
        val selectedScale = getSelectedScaleMultiplier()
        val scaleStr = getScaleDisplayString(selectedScale)

        val config = UpscaleConfig(
            model = selectedModel,
            hardwareDelegate = selectedDelegate,
            tileSize = tileSize,
            tilePad = 10,
            customScaleMultiplier = selectedScale
        )

        updateUiState(isProcessing = true)

        upscaleJob = lifecycleScope.launch(Dispatchers.Default) {
            try {
                val result = upscalerEngine.upscale(
                    inputBitmap = input,
                    config = config,
                    listener = object : UpscaleProgressListener {
                        override fun onProgress(
                            currentTile: Int,
                            totalTiles: Int,
                            progressPercent: Int,
                            elapsedMs: Long
                        ) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                binding.progressBar.progress = progressPercent
                                val sec = String.format("%.1f", elapsedMs / 1000f)
                                binding.tvStatus.text = getString(
                                    R.string.status_upscaling_tile,
                                    currentTile,
                                    totalTiles,
                                    progressPercent,
                                    sec,
                                    config.hardwareDelegate.displayName
                                )
                            }
                        }

                        override fun onStatusUpdate(statusText: String) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                binding.tvStatus.text = statusText
                            }
                        }
                    }
                )

                withContext(Dispatchers.Main) {
                    upscaledBitmap?.recycle()
                    upscaledBitmap = result
                    val badgeLabel = getString(R.string.slider_badge_upscaled, scaleStr)
                    binding.sliderView.setBitmaps(originalBitmap, result, badgeLabel)
                    binding.tvStatus.text = getString(R.string.status_complete, scaleStr)
                    updateUiState(isProcessing = false)
                    binding.layoutSaveShare.visibility = View.VISIBLE
                    binding.btnInspectFullscreen.visibility = View.VISIBLE
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.status_cancelled_warning)
                    updateUiState(isProcessing = false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.status_error, e.localizedMessage ?: "")
                    updateUiState(isProcessing = false)
                    Toast.makeText(this@MainActivity, getString(R.string.toast_upscale_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun cancelUpscale() {
        upscaleJob?.cancel()
        upscaleJob = null
    }

    private fun saveUpscaledImage() {
        val bmp = upscaledBitmap
        if (bmp == null) {
            Toast.makeText(this, getString(R.string.toast_save_no_image), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val uri = ImageUtils.saveBitmapToGallery(applicationContext, bmp)
            withContext(Dispatchers.Main) {
                if (uri != null) {
                    Snackbar.make(binding.root, getString(R.string.gallery_saved_snackbar), Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.snackbar_view)) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "image/png")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(intent)
                        }.show()
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareUpscaledImage() {
        val bmp = upscaledBitmap ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val uri = ImageUtils.getShareableUri(applicationContext, bmp)
            withContext(Dispatchers.Main) {
                if (uri != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.chooser_share_title)))
                } else {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_share_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUiState(isProcessing: Boolean) {
        binding.btnUpscale.isEnabled = !isProcessing
        binding.btnChangeImage.isEnabled = !isProcessing
        binding.btnLoadUrlTop.isEnabled = !isProcessing
        binding.btnInspectFullscreen.isEnabled = !isProcessing
        binding.chipGroupModel.isEnabled = !isProcessing
        binding.chipGroupScale.isEnabled = !isProcessing
        binding.chipGroupHardware.isEnabled = !isProcessing
        binding.chipGroupTileSize.isEnabled = !isProcessing
        binding.sliderCustomScale.isEnabled = !isProcessing

        for (i in 0 until binding.chipGroupModel.childCount) {
            binding.chipGroupModel.getChildAt(i).isEnabled = !isProcessing
        }
        for (i in 0 until binding.chipGroupScale.childCount) {
            binding.chipGroupScale.getChildAt(i).isEnabled = !isProcessing
        }
        for (i in 0 until binding.chipGroupHardware.childCount) {
            binding.chipGroupHardware.getChildAt(i).isEnabled = !isProcessing
        }
        for (i in 0 until binding.chipGroupTileSize.childCount) {
            binding.chipGroupTileSize.getChildAt(i).isEnabled = !isProcessing
        }
        for (i in 0 until binding.chipGroupCustomPresets.childCount) {
            binding.chipGroupCustomPresets.getChildAt(i).isEnabled = !isProcessing
        }

        binding.btnCancel.visibility = if (isProcessing) View.VISIBLE else View.GONE
        binding.progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
        if (isProcessing) {
            binding.progressBar.progress = 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        upscaleJob?.cancel()
        upscalerEngine.close()
        originalBitmap?.recycle()
        upscaledBitmap?.recycle()
    }
}
