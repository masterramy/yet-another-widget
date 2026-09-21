package com.ramybaheeg.yetanotherwidget.ui.activities.tabs

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.chibatching.kotpref.bulk
import com.ramybaheeg.yetanotherwidget.R
import com.ramybaheeg.yetanotherwidget.databinding.ActivityTimeZoneSelectorBinding
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.ui.viewmodels.tabs.TimeZoneSelectorViewModel
import com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget
import net.idik.lib.slimadapter.SlimAdapter

class TimeZoneSelectorActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_TIME_ZONE = "__DEFAULT_TIME_ZONE__"
    }

    private lateinit var adapter: SlimAdapter
    private lateinit var viewModel: TimeZoneSelectorViewModel
    private lateinit var binding: ActivityTimeZoneSelectorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(TimeZoneSelectorViewModel::class.java)
        binding = ActivityTimeZoneSelectorBinding.inflate(layoutInflater)

        binding.listView.setHasFixedSize(true)
        binding.listView.layoutManager = LinearLayoutManager(this)
        binding.loader.visibility = View.GONE

        adapter = SlimAdapter.create()
        adapter
            .register<String>(R.layout.custom_location_item) { item, injector ->
                val label = if (item == DEFAULT_TIME_ZONE) {
                    getString(R.string.no_time_zone_label)
                } else {
                    item.replace('_', ' ')
                }
                injector.text(R.id.text, label)
                injector.clicked(R.id.item) {
                    if (item == DEFAULT_TIME_ZONE) {
                        Preferences.bulk {
                            altTimezoneId = ""
                            altTimezoneLabel = ""
                        }
                    } else {
                        Preferences.bulk {
                            altTimezoneId = item
                            altTimezoneLabel = item.substringAfterLast('/').replace('_', ' ')
                        }
                    }
                    MainWidget.updateWidget(this@TimeZoneSelectorActivity)
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            .attachTo(binding.listView)

        viewModel.timeZones.observe(this) { zones ->
            adapter.updateData(listOf(DEFAULT_TIME_ZONE) + zones)
        }

        viewModel.locationInput.observe(this) { query ->
            viewModel.filterTimeZones(query.orEmpty())
            binding.clearSearch.isVisible = !query.isNullOrBlank()
        }

        setupListener()

        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        binding.location.requestFocus()

        setContentView(binding.root)
    }

    private fun setupListener() {
        binding.actionBack.setOnClickListener {
            onBackPressed()
        }

        binding.clearSearch.setOnClickListener {
            viewModel.locationInput.value = ""
        }
    }
}
