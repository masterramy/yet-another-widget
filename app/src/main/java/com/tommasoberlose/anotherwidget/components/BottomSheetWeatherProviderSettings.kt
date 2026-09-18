package com.tommasoberlose.anotherwidget.components

import android.content.Context
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tommasoberlose.anotherwidget.R
import com.tommasoberlose.anotherwidget.databinding.WeatherProviderSettingsLayoutBinding
import com.tommasoberlose.anotherwidget.global.Constants
import com.tommasoberlose.anotherwidget.global.Preferences
import com.tommasoberlose.anotherwidget.helpers.WeatherHelper
import com.tommasoberlose.anotherwidget.utils.openURI

class BottomSheetWeatherProviderSettings(context: Context, private val callback: () -> Unit = {}) :
    BottomSheetDialog(context, R.style.BottomSheetDialogTheme) {
    private val binding = WeatherProviderSettingsLayoutBinding.inflate(android.view.LayoutInflater.from(context))
    init {
        val provider = Constants.WeatherProvider.fromInt(Preferences.weatherProvider)
        val keyRequired = WeatherHelper.isKeyRequired(provider)
        binding.apiKeyContainer.isVisible = keyRequired
        binding.actionSaveKey.isVisible = keyRequired
        binding.infoTitle.text = WeatherHelper.getProviderInfoTitle(context, provider)
        binding.infoSubtitle.text = WeatherHelper.getProviderInfoSubtitle(context, provider)
        binding.infoTitle.isVisible = binding.infoTitle.text.isNotEmpty()
        binding.infoSubtitle.isVisible = binding.infoSubtitle.text.isNotEmpty()
        binding.infoProvider.text = WeatherHelper.getProviderName(context, provider)
        binding.apiKey.editText?.setText(if (provider == Constants.WeatherProvider.WEATHER_API) Preferences.weatherProviderApiWeatherApi else "")
        binding.actionOpenProvider.setOnClickListener { context.openURI(WeatherHelper.getProviderLink(provider)) }
        binding.actionSaveKey.setOnClickListener {
            if (provider == Constants.WeatherProvider.WEATHER_API) {
                Preferences.weatherProviderApiWeatherApi = binding.apiKey.editText?.text.toString().trim()
            }
            callback.invoke()
            dismiss()
        }
        setContentView(binding.root)
    }
}
