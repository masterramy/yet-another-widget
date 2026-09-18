package com.tommasoberlose.anotherwidget.components

import android.content.Context
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tommasoberlose.anotherwidget.R
import com.tommasoberlose.anotherwidget.databinding.WeatherProviderSettingsLayoutBinding
import com.tommasoberlose.anotherwidget.helpers.WeatherHelper
import com.tommasoberlose.anotherwidget.utils.openURI

class BottomSheetWeatherProviderSettings(context: Context) :
    BottomSheetDialog(context, R.style.BottomSheetDialogTheme) {

    private val binding =
        WeatherProviderSettingsLayoutBinding.inflate(android.view.LayoutInflater.from(context))

    init {
        WeatherHelper.getProviderInfoTitle(context).let { title ->
            binding.infoTitle.text = title
            binding.infoTitle.isVisible = title.isNotEmpty()
        }

        WeatherHelper.getProviderInfoSubtitle(context).let { subtitle ->
            binding.infoSubtitle.text = subtitle
            binding.infoSubtitle.isVisible = subtitle.isNotEmpty()
        }

        binding.infoProvider.text = WeatherHelper.getProviderName(context)
        binding.actionOpenProvider.setOnClickListener {
            context.openURI(WeatherHelper.getProviderLink())
        }

        setContentView(binding.root)
    }
}
