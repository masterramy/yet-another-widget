package com.ramybaheeg.yetanotherwidget.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.ramybaheeg.yetanotherwidget.R
import com.ramybaheeg.yetanotherwidget.databinding.BottomSheetMenuHorBinding
import com.ramybaheeg.yetanotherwidget.databinding.BottomSheetMenuListBinding
import com.ramybaheeg.yetanotherwidget.helpers.ColorHelper.copyToClipboard
import com.ramybaheeg.yetanotherwidget.helpers.ColorHelper.isClipboardColor
import com.ramybaheeg.yetanotherwidget.helpers.ColorHelper.isColorDark
import com.ramybaheeg.yetanotherwidget.helpers.ColorHelper.pasteFromClipboard
import com.ramybaheeg.yetanotherwidget.helpers.ColorHelper.toIntValue
import com.ramybaheeg.yetanotherwidget.utils.isDarkTheme
import com.warkiz.widget.IndicatorSeekBar
import com.warkiz.widget.OnSeekChangeListener
import com.warkiz.widget.SeekParams
import kotlinx.coroutines.*
import net.idik.lib.slimadapter.SlimAdapter

class BottomSheetPicker<T>(
    context: Context,
    private val items: List<MenuItem<T>> = arrayListOf(),
    private val getSelected: (() -> T)? = null,
    private val header: String? = null,
    private val onItemSelected: ((selectedValue: T?) -> Unit)? = null,
) : BottomSheetDialog(context, R.style.BottomSheetDialogTheme) {

    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadingJobs: ArrayList<Job> = ArrayList()
    private lateinit var adapter: SlimAdapter

    private var binding: BottomSheetMenuHorBinding = BottomSheetMenuHorBinding.inflate(
        LayoutInflater.from(context))
    private var listBinding: BottomSheetMenuListBinding = BottomSheetMenuListBinding.inflate(
        LayoutInflater.from(context))

    override fun show() {
        window?.setDimAmount(0f)

        // Header
        binding.header.isVisible = header != null
        binding.headerText.text = header ?: ""

        // Alpha
        binding.alphaSelectorContainer.isVisible = false
        binding.actionContainer.isVisible = false

        // List
        adapter = SlimAdapter.create()

        loadingJobs.add(dialogScope.launch {
            listBinding.root.setHasFixedSize(true)
            val mLayoutManager = LinearLayoutManager(context)
            listBinding.root.layoutManager = mLayoutManager

            adapter
                .register<Int>(R.layout.bottom_sheet_menu_item) { position, injector ->
                    val item = items[position]
                    val isSelected = item.value == getSelected?.invoke()
                    injector
                        .text(R.id.label, item.title)
                        .textColor(R.id.label, ContextCompat.getColor(context, if (isSelected) R.color.colorAccent else R.color.colorSecondaryText))
                        .selected(R.id.item, isSelected)
                        .clicked(R.id.item) {
                            val oldIdx = items.toList().indexOfFirst { it.value == getSelected?.invoke() }
                            onItemSelected?.invoke(item.value)
                            adapter.notifyItemChanged(position)
                            adapter.notifyItemChanged(oldIdx)
                            (listBinding.root.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position,0)
                        }
                }
                .attachTo(listBinding.root)

            adapter.updateData((items.indices).toList())

            binding.loader.isVisible = false
            binding.listContainer.addView(listBinding.root)
            this@BottomSheetPicker.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            binding.listContainer.isVisible = true

            val idx = items.toList().indexOfFirst { it.value == getSelected?.invoke() }
            (listBinding.root.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(idx,0)
        })

        setContentView(binding.root)
        super.show()
    }

    override fun onStop() {
        dialogScope.coroutineContext.cancelChildren()
        loadingJobs.clear()
        super.onStop()
    }

    class MenuItem<T>(val title: String, val value: T? = null)

}