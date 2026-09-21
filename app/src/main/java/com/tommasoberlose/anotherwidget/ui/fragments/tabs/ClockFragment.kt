package com.ramybaheeg.yetanotherwidget.ui.fragments.tabs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chibatching.kotpref.bulk
import com.google.android.material.transition.MaterialSharedAxis
import com.ramybaheeg.yetanotherwidget.R
import com.ramybaheeg.yetanotherwidget.components.BottomSheetColorPicker
import com.ramybaheeg.yetanotherwidget.components.BottomSheetMenu
import com.ramybaheeg.yetanotherwidget.databinding.FragmentTabClockBinding
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.global.RequestCode
import com.ramybaheeg.yetanotherwidget.helpers.ColorHelper
import com.ramybaheeg.yetanotherwidget.helpers.ColorHelper.toHexValue
import com.ramybaheeg.yetanotherwidget.helpers.ColorHelper.toIntValue
import com.ramybaheeg.yetanotherwidget.helpers.IntentHelper
import com.ramybaheeg.yetanotherwidget.ui.activities.tabs.ChooseApplicationActivity
import com.ramybaheeg.yetanotherwidget.ui.activities.MainActivity
import com.ramybaheeg.yetanotherwidget.ui.activities.tabs.TimeZoneSelectorActivity
import com.ramybaheeg.yetanotherwidget.ui.viewmodels.MainViewModel
import com.ramybaheeg.yetanotherwidget.utils.isDarkTheme
import com.ramybaheeg.yetanotherwidget.utils.isDefaultSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ClockFragment : Fragment() {

    companion object {
        fun newInstance() = ClockFragment()
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var colors: IntArray
    private lateinit var binding: FragmentTabClockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        viewModel = ViewModelProvider(activity as MainActivity).get(MainViewModel::class.java)
        binding = FragmentTabClockBinding.inflate(inflater)

        subscribeUi(viewModel)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.ampmIndicatorToggle.setCheckedImmediatelyNoEvent(Preferences.showAMPMIndicator)

        lifecycleScope.launch(Dispatchers.IO) {
            val lazyColors = requireContext().resources.getIntArray(R.array.material_colors)
            withContext(Dispatchers.Main) {
                colors = lazyColors
            }
        }
        setupListener()

        binding.scrollView.viewTreeObserver?.addOnScrollChangedListener {
            viewModel.fragmentScrollY.value = binding.scrollView.scrollY
        }
    }

    private fun subscribeUi(
        viewModel: MainViewModel
    ) {
        binding.isClockVisible = Preferences.showClock
        binding.is24Format = DateFormat.is24HourFormat(requireContext())
        binding.isDarkModeEnabled = activity?.isDarkTheme() == true

        viewModel.clockTextSize.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                binding.clockTextSizeLabel.text = String.format("%.0fsp", it)
            }
        }

        viewModel.altTimezoneLabel.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                if (it != "") {
                    binding.altTimezoneClockLabel.text =
                        String.format("%s (%s)", it, Preferences.altTimezoneId)
                } else {
                    binding.altTimezoneClockLabel.text = getString(R.string.no_time_zone_label)
                }
            }
        }

        viewModel.showAMPMIndicator.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                binding.ampmIndicatorLabel.text = if (it) getString(R.string.settings_visible) else getString(R.string.settings_not_visible)
            }
        }

        viewModel.clockTextColor.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                if (Preferences.clockTextAlpha == "00") {
                    binding.clockTextColorLabel.text = getString(R.string.transparent)
                } else {
                    binding.clockTextColorLabel.text =
                        "#%s".format(Integer.toHexString(ColorHelper.getClockFontColor(activity?.isDarkTheme() == true))).uppercase()
                }
            }
        }

        viewModel.clockTextColorDark.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                if (Preferences.clockTextAlphaDark == "00") {
                    binding.clockTextColorLabel.text = getString(R.string.transparent)
                } else {
                    binding.clockTextColorLabel.text =
                        "#%s".format(Integer.toHexString(ColorHelper.getClockFontColor(activity?.isDarkTheme() == true))).uppercase()
                }
            }
        }
    }

    private fun setupListener() {

        binding.actionClockTextSize.setOnClickListener {
            val dialog = BottomSheetMenu<Float>(
                requireContext(),
                header = getString(R.string.settings_clock_text_size_title)
            ).setSelectedValue(Preferences.clockTextSize)
            (46 downTo 12).filter { it % 2 == 0 }.forEach {
                dialog.addItem("${it}sp", it.toFloat())
            }
            dialog.addOnSelectItemListener { value ->
                Preferences.clockTextSize = value
            }.show()
        }

        binding.actionAltTimezoneClock.setOnClickListener {
            startActivity(Intent(requireContext(), TimeZoneSelectorActivity::class.java))
        }

        binding.actionAmpmIndicatorSize.setOnClickListener {
            binding.ampmIndicatorToggle.isChecked = !binding.ampmIndicatorToggle.isChecked
        }

        binding.ampmIndicatorToggle.setOnCheckedChangeListener { _, isChecked ->
            Preferences.showAMPMIndicator = isChecked
        }

        binding.actionClockTextColor.setOnClickListener {
            BottomSheetColorPicker(requireContext(),
                colors = colors,
                header = getString(R.string.settings_font_color_title),
                getSelected = { ColorHelper.getClockFontColorRgb(activity?.isDarkTheme() == true) },
                onColorSelected = { color: Int ->
                    val colorString = Integer.toHexString(color)
                    if (activity?.isDarkTheme() == true) {
                        Preferences.clockTextColorDark =
                            "#" + if (colorString.length > 6) colorString.substring(2) else colorString
                    } else {
                        Preferences.clockTextColor =
                            "#" + if (colorString.length > 6) colorString.substring(2) else colorString
                    }
                },
                showAlphaSelector = true,
                alpha = if (activity?.isDarkTheme() == true) Preferences.clockTextAlphaDark.toIntValue() else Preferences.clockTextAlpha.toIntValue(),
                onAlphaChangeListener = { alpha ->
                    if (activity?.isDarkTheme() == true) {
                        Preferences.clockTextAlphaDark = alpha.toHexValue()
                    } else {
                        Preferences.clockTextAlpha = alpha.toHexValue()
                    }
                }
            ).show()
        }
    }

    override fun onResume() {
        binding.is24Format = DateFormat.is24HourFormat(requireContext())
        super.onResume()
    }

    private fun maintainScrollPosition(callback: () -> Unit) {
        binding.scrollView.isScrollable = false
        callback.invoke()
        lifecycleScope.launch {
            delay(200)
            binding.scrollView.isScrollable = true
        }
    }
}
