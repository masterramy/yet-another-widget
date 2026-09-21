package com.ramybaheeg.yetanotherwidget.ui.fragments.tabs

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.transition.MaterialSharedAxis
import com.ramybaheeg.yetanotherwidget.R
import com.ramybaheeg.yetanotherwidget.components.CustomNotesDialog
import com.ramybaheeg.yetanotherwidget.components.GlanceSettingsDialog
import com.ramybaheeg.yetanotherwidget.databinding.FragmentTabGlanceBinding
import com.ramybaheeg.yetanotherwidget.global.Constants
import com.ramybaheeg.yetanotherwidget.global.Preferences
import com.ramybaheeg.yetanotherwidget.helpers.ActiveNotificationsHelper
import com.ramybaheeg.yetanotherwidget.helpers.AlarmHelper
import com.ramybaheeg.yetanotherwidget.helpers.GlanceProviderHelper
import com.ramybaheeg.yetanotherwidget.helpers.MediaPlayerHelper
import com.ramybaheeg.yetanotherwidget.models.GlanceProvider
import com.ramybaheeg.yetanotherwidget.ui.activities.MainActivity
import com.ramybaheeg.yetanotherwidget.ui.viewmodels.MainViewModel
import com.ramybaheeg.yetanotherwidget.utils.checkGrantedPermission
import com.ramybaheeg.yetanotherwidget.utils.convertDpToPixel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.idik.lib.slimadapter.SlimAdapter

class GlanceTabFragment : Fragment() {
    companion object { fun newInstance() = GlanceTabFragment() }
    private var dialog: GlanceSettingsDialog? = null
    private lateinit var adapter: SlimAdapter
    private lateinit var viewModel: MainViewModel
    private val list: ArrayList<Constants.GlanceProviderId> by lazy { GlanceProviderHelper.getGlanceProviders(requireContext()) }
    private lateinit var binding: FragmentTabGlanceBinding

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true); returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false) }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(activity as MainActivity).get(MainViewModel::class.java)
        binding = FragmentTabGlanceBinding.inflate(inflater); binding.lifecycleOwner = this; binding.viewModel = viewModel; return binding.root
    }
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.providersList.hasFixedSize(); binding.providersList.isNestedScrollingEnabled = false; binding.providersList.layoutManager = LinearLayoutManager(context)
        adapter = SlimAdapter.create()
        adapter.register<GlanceProvider>(R.layout.glance_provider_item) { item, injector ->
            val provider = Constants.GlanceProviderId.from(item.id)!!
            injector.text(R.id.title, item.title).with<ImageView>(R.id.icon) { it.setImageDrawable(ContextCompat.getDrawable(requireContext(), item.icon)) }.clicked(R.id.item) {
                if (provider == Constants.GlanceProviderId.CUSTOM_INFO) CustomNotesDialog(requireContext()) { adapter.notifyItemRangeChanged(0, adapter.data.size) }.show()
                else { dialog = GlanceSettingsDialog(requireActivity(), provider) { adapter.notifyItemRangeChanged(0, adapter.data.size) }; dialog?.setOnDismissListener { dialog = null }; dialog?.show() }
            }
            var isVisible = false
            when (provider) {
                Constants.GlanceProviderId.PLAYING_SONG -> when {
                    ActiveNotificationsHelper.checkNotificationAccess(requireContext()) -> { MediaPlayerHelper.updatePlayingMediaInfo(requireContext()); injector.visibility(R.id.error_icon, View.GONE); injector.visibility(R.id.info_icon, View.VISIBLE); injector.text(R.id.label, if (Preferences.showMusic) getString(R.string.settings_visible) else getString(R.string.settings_not_visible)); isVisible = Preferences.showMusic }
                    Preferences.showMusic -> { injector.visibility(R.id.error_icon, View.VISIBLE); injector.visibility(R.id.info_icon, View.GONE); injector.text(R.id.label, getString(R.string.settings_not_visible)) }
                    else -> { injector.visibility(R.id.error_icon, View.GONE); injector.visibility(R.id.info_icon, View.VISIBLE); injector.text(R.id.label, getString(R.string.settings_not_visible)) }
                }
                Constants.GlanceProviderId.NEXT_CLOCK_ALARM -> { val wrong = AlarmHelper.isAlarmProbablyWrong(requireContext()); isVisible = Preferences.showNextAlarm && !wrong; injector.text(R.id.label, if (isVisible) getString(R.string.settings_visible) else getString(R.string.settings_not_visible)); injector.visibility(R.id.error_icon, if (Preferences.showNextAlarm && wrong) View.VISIBLE else View.GONE); injector.visibility(R.id.info_icon, if (Preferences.showNextAlarm && wrong) View.GONE else View.VISIBLE) }
                Constants.GlanceProviderId.BATTERY_LEVEL_LOW -> { isVisible = Preferences.showBatteryCharging; injector.text(R.id.label, if (isVisible) getString(R.string.settings_visible) else getString(R.string.settings_not_visible)); injector.visibility(R.id.error_icon, View.GONE); injector.visibility(R.id.info_icon, View.VISIBLE) }
                Constants.GlanceProviderId.NOTIFICATIONS -> when {
                    ActiveNotificationsHelper.checkNotificationAccess(requireContext()) -> { isVisible = Preferences.showNotifications; injector.visibility(R.id.error_icon, View.GONE); injector.visibility(R.id.info_icon, View.VISIBLE); injector.text(R.id.label, if (isVisible) getString(R.string.settings_visible) else getString(R.string.settings_not_visible)) }
                    Preferences.showNotifications -> { injector.visibility(R.id.error_icon, View.VISIBLE); injector.visibility(R.id.info_icon, View.GONE); injector.text(R.id.label, getString(R.string.settings_not_visible)) }
                    else -> { injector.visibility(R.id.error_icon, View.GONE); injector.visibility(R.id.info_icon, View.VISIBLE); injector.text(R.id.label, getString(R.string.settings_not_visible)) }
                }
                Constants.GlanceProviderId.GREETINGS -> { isVisible = Preferences.showGreetings; injector.text(R.id.label, if (isVisible) getString(R.string.settings_visible) else getString(R.string.settings_not_visible)); injector.visibility(R.id.error_icon, View.GONE); injector.visibility(R.id.info_icon, View.VISIBLE) }
                Constants.GlanceProviderId.CUSTOM_INFO -> { isVisible = Preferences.customNotes != ""; injector.text(R.id.label, if (isVisible) getString(R.string.settings_visible) else getString(R.string.settings_not_visible)); injector.visibility(R.id.error_icon, View.GONE); injector.visibility(R.id.info_icon, View.VISIBLE) }
                Constants.GlanceProviderId.EVENTS -> { isVisible = Preferences.showEventsAsGlanceProvider; val hasError = !Preferences.showEvents || !requireContext().checkGrantedPermission(Manifest.permission.READ_CALENDAR); injector.text(R.id.label, if (isVisible && !hasError) getString(R.string.settings_visible) else getString(R.string.settings_not_visible)); injector.visibility(R.id.error_icon, if (isVisible && hasError) View.VISIBLE else View.GONE); injector.visibility(R.id.info_icon, if (isVisible && hasError) View.GONE else View.VISIBLE) }
            }
            injector.alpha(R.id.title, if (isVisible) 1f else .25f); injector.alpha(R.id.label, if (isVisible) 1f else .25f); injector.alpha(R.id.icon, if (isVisible) 1f else .25f)
        }.attachTo(binding.providersList)
        val mIth = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean { adapter.notifyItemMoved(viewHolder.adapterPosition, target.adapterPosition); return true }
            override fun onMoved(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, fromPos: Int, target: RecyclerView.ViewHolder, toPos: Int, x: Int, y: Int) { with(list[toPos]) { list[toPos] = list[fromPos]; list[fromPos] = this }; super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y) }
            override fun isItemViewSwipeEnabled() = false
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) { super.clearView(recyclerView, viewHolder); GlanceProviderHelper.saveGlanceProviderOrder(list); adapter.updateData(list.mapNotNull { GlanceProviderHelper.getGlanceProviderById(requireContext(), it) }) }
            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) { val view = viewHolder.itemView as MaterialCardView; ViewCompat.setElevation(view, if (isCurrentlyActive) 8f.convertDpToPixel(requireContext()) else 0f); view.setCardBackgroundColor(ContextCompat.getColor(requireContext(), if (isCurrentlyActive) R.color.cardBorder else R.color.colorPrimary)); val topEdge = if ((view.top == 0 && dY < 0) || (view.top + view.height >= recyclerView.height - 32f.convertDpToPixel(requireContext()) && dY > 0)) 0f else dY; super.onChildDraw(c, recyclerView, viewHolder, dX, topEdge, actionState, isCurrentlyActive) }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }); mIth.attachToRecyclerView(binding.providersList)
        binding.scrollView.viewTreeObserver.addOnScrollChangedListener { viewModel.fragmentScrollY.value = binding.scrollView.scrollY }
        lifecycleScope.launch(Dispatchers.IO) { delay(500); val l = list.mapNotNull { GlanceProviderHelper.getGlanceProviderById(requireContext(), it) }; withContext(Dispatchers.Main) { binding.loader.animate().scaleX(0f).scaleY(0f).alpha(0f).start(); adapter.updateData(l); binding.providersList.layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_fall_down); adapter.notifyDataSetChanged(); binding.providersList.scheduleLayoutAnimation() } }
    }
    private val nextAlarmChangeBroadcastReceiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { adapter.notifyItemRangeChanged(0, adapter.data.size) } }
    override fun onStart() { super.onStart(); requireActivity().registerReceiver(nextAlarmChangeBroadcastReceiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)); dialog?.show() }
    override fun onStop() { requireActivity().unregisterReceiver(nextAlarmChangeBroadcastReceiver); super.onStop() }
    override fun onResume() { super.onResume(); adapter.notifyItemRangeChanged(0, adapter.data?.size ?: 0); dialog?.show() }
}
