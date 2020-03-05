package com.louiskirsch.quickdynalist

import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NavUtils
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.louiskirsch.quickdynalist.objectbox.DynalistItem_
import com.louiskirsch.quickdynalist.objectbox.DynalistTag
import io.objectbox.kotlin.query
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread

class SettingsActivity: AppCompatActivity() {

    class SettingsFragment: PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            (findPreference("display_children_count") as ListPreference).apply {
                setOnPreferenceChangeListener { preference, newValue ->
                    val summary  = when (newValue) {
                        "0" -> R.string.pref_description_display_children_count_none
                        "-1" -> R.string.pref_description_display_children_count_all
                        else -> R.string.pref_description_display_children_count
                    }
                    preference.summary = getString(summary, newValue)
                    true
                }
                onPreferenceChangeListener.onPreferenceChange(this, value)
            }

            (findPreference("display_theme") as ListPreference).apply {
                setOnPreferenceChangeListener { _, newValue ->
                    Handler().postDelayed({
                        val mode = (newValue as String).toInt()
                        AppCompatDelegate.setDefaultNightMode(mode)
                        (activity as AppCompatActivity).delegate.applyDayNight()
                    }, 100)
                    true
                }
            }

            findPreference("cleanup_tags").setOnPreferenceClickListener {
                doAsync {
                    val toRemove = DynalistTag.box.all.filter { it.consumers.size == 0 }
                    DynalistTag.box.remove(toRemove)
                    uiThread {
                        context!!.toast(R.string.pref_cleanup_tags_success)
                    }
                }
                true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            NavUtils.navigateUpFromSameTask(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
