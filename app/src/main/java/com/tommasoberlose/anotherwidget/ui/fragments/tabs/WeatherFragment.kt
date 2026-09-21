package com.ramybaheeg.yetanotherwidget.ui.fragments.tabs

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.ramybaheeg.yetanotherwidget.R
import com.ramybaheeg.yetanotherwidget.components.BottomSheetMenu
import com.ramybaheeg.yetanotherwidget.components.IconPackSelector
import com.ramybaheeg.yetanotherwidget.components.MaterialBottomSheetDialog
import com.ramybaheeg.yetanotherwidget.databinding.FragmentTabWeatherBinding
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.global.RequestCode
import com.ramybaheeg.yetanotherwidget.helpers.SettingsStringHelper
import com.ramybaheeg.yetanotherwidget.helpers.WeatherHelper
import com.ramybaheeg.yetanotherwidget.receivers.WeatherReceiver
import com.ramybaheeg.yetanotherwidget.ui.activities.tabs.ChooseApplicationActivity
import com.ramybaheeg.yetanotherwidget.ui.activities.tabs.CustomLocationActivity
import com.ramybaheeg.yetanotherwidget.ui.activities.MainActivity
import com.ramybaheeg.yetanotherwidget.ui.activities.tabs.WeatherProviderActivity
import com.ramybaheeg.yetanotherwidget.ui.viewmodels.MainViewModel
import com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget
import com.ramybaheeg.yetanotherwidget.utils.checkGrantedPermission
import com.ramybaheeg.yetanotherwidget.utils.collapse
import com.ramybaheeg.yetanotherwidget.utils.expand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WeatherFragment : Fragment() {

    companion object {
        fun newInstance() = WeatherFragment()
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var binding: FragmentTabWeatherBinding

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
        binding = FragmentTabWeatherBinding.inflate(inflater)

        subscribeUi(viewModel)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setupListener()

        binding.scrollView.viewTreeObserver.addOnScrollChangedListener {
            viewModel.fragmentScrollY.value = binding.scrollView.scrollY
        }
    }

    private fun subscribeUi(
        viewModel: MainViewModel
    ) {
        binding.isWeatherVisible = Preferences.showWeather

        viewModel.weatherProvider.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                val provider = Constants.WeatherProvider.fromInt(it)
                binding.labelWeatherProvider.text = WeatherHelper.getProviderName(requireContext(), provider)
                binding.weatherApiComplianceNotice.isVisible =
                    Preferences.showWeather && provider == Constants.WeatherProvider.WEATHER_API
                if (provider == Constants.WeatherProvider.WEATHER_API && Preferences.weatherRefreshPeriod > 1) {
                    Preferences.weatherRefreshPeriod = 1
                    WeatherReceiver.setUpdates(requireContext())
                }
                checkWeatherProviderConfig()
            }
        }

        viewModel.weatherProviderError.observe(viewLifecycleOwner) {
            checkWeatherProviderConfig()
        }

        viewModel.weatherProviderLocationError.observe(viewLifecycleOwner) {
            checkWeatherProviderConfig()
        }

        viewModel.customLocationAdd.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                binding.labelCustomLocation.text =
                    if (it == "") getString(R.string.custom_location_gps) else it
            }
            checkLocationPermission()
        }

        viewModel.weatherTempUnit.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                binding.tempUnit.text =
                    if (it == "F") getString(R.string.fahrenheit) else getString(R.string.celsius)
            }
            checkLocationPermission()
        }

        viewModel.weatherRefreshPeriod.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                binding.labelWeatherRefreshPeriod.text = getString(SettingsStringHelper.getRefreshPeriodString(it))
            }
            checkLocationPermission()
        }

        viewModel.weatherIconPack.observe(viewLifecycleOwner) {
            maintainScrollPosition {
                binding.labelWeatherIconPack.text = getString(R.string.settings_weather_icon_pack_default).format((it + 1))
            }
            checkLocationPermission()
        }
    }

    private var locationRefreshJob: Job? = null

    private fun checkLocationPermission() {
        if (requireActivity().checkGrantedPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            binding.locationPermissionAlert.isVisible = false
            if (
                Preferences.showWeather &&
                Preferences.customLocationAdd == "" &&
                (Preferences.customLocationLat == "" || Preferences.customLocationLon == "")
            ) {
                refreshDeviceLocationAndSchedule()
            } else {
                WeatherReceiver.setUpdates(requireContext())
            }
        } else if (Preferences.showWeather && Preferences.customLocationAdd == "") {
            binding.locationPermissionAlert.isVisible = true
            binding.locationPermissionAlert.setOnClickListener {
                requirePermission()
            }
        } else {
            binding.locationPermissionAlert.isVisible = false
        }
    }

    private fun refreshDeviceLocationAndSchedule() {
        if (locationRefreshJob?.isActive == true) return
        locationRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            WeatherHelper.updateWeather(requireContext())
            WeatherReceiver.setUpdates(requireContext())
        }
    }

    private fun checkWeatherProviderConfig() {
        binding.weatherProviderError.isVisible = Preferences.showWeather && Preferences.weatherProviderError != "" && Preferences.weatherProviderError != "-"
        binding.weatherProviderError.text = Preferences.weatherProviderError

        binding.weatherProviderLocationError.isVisible = Preferences.showWeather && Preferences.weatherProviderLocationError != ""
        binding.weatherProviderLocationError.text = Preferences.weatherProviderLocationError
    }

    private fun setupListener() {
        binding.actionWeatherProvider.setOnClickListener {
            startActivityForResult(
                Intent(requireContext(), WeatherProviderActivity::class.java),
                RequestCode.WEATHER_PROVIDER_REQUEST_CODE.code
            )
        }

        binding.actionCustomLocation.setOnClickListener {
            startActivityForResult(
                Intent(requireContext(), CustomLocationActivity::class.java),
                Constants.RESULT_CODE_CUSTOM_LOCATION
            )
        }

        binding.actionChangeUnit.setOnClickListener {
            BottomSheetMenu<String>(requireContext(), header = getString(R.string.settings_unit_title)).setSelectedValue(Preferences.weatherTempUnit)
                .addItem(getString(R.string.fahrenheit), "F")
                .addItem(getString(R.string.celsius), "C")
                .addOnSelectItemListener { value ->
                    if (value != Preferences.weatherTempUnit) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            WeatherHelper.updateWeather(requireContext())
                        }
                    }
                    Preferences.weatherTempUnit = value
                }.show()
        }

        binding.actionWeatherRefreshPeriod.setOnClickListener {
            val dialog =
                BottomSheetMenu<Int>(requireContext(), header = getString(R.string.settings_weather_refresh_period_title)).setSelectedValue(Preferences.weatherRefreshPeriod)
            val maxPeriod =
                if (Constants.WeatherProvider.fromInt(Preferences.weatherProvider) == Constants.WeatherProvider.WEATHER_API) 1 else 5
            (maxPeriod downTo 0).forEach {
                dialog.addItem(getString(SettingsStringHelper.getRefreshPeriodString(it)), it)
            }
            dialog
                .addOnSelectItemListener { value ->
                    Preferences.weatherRefreshPeriod = value
                }.show()
        }

        binding.actionWeatherIconPack.setOnClickListener {
            IconPackSelector(requireContext(), header = getString(R.string.settings_weather_icon_pack_title)).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                Constants.RESULT_CODE_CUSTOM_LOCATION -> {
                    WeatherReceiver.setUpdates(requireContext())
                    checkLocationPermission()
                }
                RequestCode.WEATHER_PROVIDER_REQUEST_CODE.code -> {
                    checkLocationPermission()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun requirePermission() {
        Dexter.withContext(requireContext())
            .withPermissions(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object: MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    report?.let {
                        if (report.areAllPermissionsGranted()){
                            binding.locationPermissionAlert.isVisible = false
                            refreshDeviceLocationAndSchedule()
                        }
                    }
                }
                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    // Remember to invoke this method when the custom rationale is closed
                    // or just by default if you don't want to use any custom rationale.
                    token?.continuePermissionRequest()
                }
            })
            .check()
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
