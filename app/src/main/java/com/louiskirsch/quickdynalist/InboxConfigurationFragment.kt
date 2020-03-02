package com.louiskirsch.quickdynalist


import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.louiskirsch.quickdynalist.network.SetInboxRequest
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import kotlinx.android.synthetic.main.fragment_inbox_configuration.*
import org.greenrobot.eventbus.EventBus
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.doAsyncResult
import org.jetbrains.anko.longToast
import org.jetbrains.anko.uiThread

class InboxConfigurationFragment : Fragment() {
    private var finishAfter: Boolean = false
    private val additionalLocations = ArrayList<ItemLocation>()
    private var location: DynalistItem? = null
    private lateinit var adapter: ArrayAdapter<ItemLocation>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            finishAfter = it.getBoolean(ARG_FINISH_AFTER)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_inbox_configuration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ArrayAdapter(context!!, R.layout.dropdown_menu_popup_item, ArrayList())
        adapter.setDropDownViewResource(R.layout.dropdown_menu_popup_item)
        inbox_location.adapter = adapter

        val model = ViewModelProviders.of(this).get(DynalistItemViewModel::class.java)
        model.bookmarksAndDocsLiveData.observe(this, Observer {
            adapter.clear()
            adapter.addAll(it.map { i -> ItemLocation(i) })
            adapter.addAll(additionalLocations)
        })
        inbox_search.setOnClickListener {
            Intent(context!!, SearchActivity::class.java).apply {
                val requestCode = resources.getInteger(R.integer.request_code_search_item)
                startActivityForResult(this, requestCode)
            }
        }
        inbox_location.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                location = null
                inbox_accept.isEnabled = false
            }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selection = parent?.getItemAtPosition(position) as? ItemLocation
                location = selection?.item
                inbox_accept.isEnabled = location != null
            }

        }
        inbox_accept.isEnabled = false

        inbox_accept.setOnClickListener {
            val location = this.location!!
            val fileId = location.serverFileId!!
            val itemId = location.serverItemId!!
            doAsync {
                val service = DynalistApp.instance.dynalistService
                val dynalist = Dynalist(context!!)
                val request = SetInboxRequest(fileId, itemId, dynalist.token!!)
                val result = service.setPreference(request).execute()
                val success = result.isSuccessful && result.body()?.isOK ?: false
                uiThread {
                    if (success) {
                        DynalistItem.updateLocally(location) {
                            it.isInbox = true
                            it.isBookmark = true
                        }
                        EventBus.getDefault().apply {
                            postSticky(InboxEvent(configured = true))
                        }
                        nextWizardScreen()
                    } else {
                        activity!!.longToast(R.string.inbox_error_set_request).show()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val searchRequestCode = resources.getInteger(R.integer.request_code_search_item)
        if (resultCode == Activity.RESULT_OK && requestCode == searchRequestCode) {
            val searchResult = data!!.getParcelableExtra<DynalistItem>(
                    DynalistApp.EXTRA_DISPLAY_ITEM)!!
            val newLocation = ItemLocation(searchResult)
            val prevIndex = additionalLocations.indexOf(newLocation)
            if (prevIndex >= 0) {
                val absoluteIndex = adapter.count - additionalLocations.size + prevIndex
                inbox_location.setSelection(absoluteIndex)
            } else {
                additionalLocations.add(newLocation)
                adapter.add(newLocation)
                inbox_location.setSelection(adapter.count - 1)
            }
        }
    }

    private fun nextWizardScreen() {
        if (finishAfter) {
            activity!!.finish()
        } else {
            activity!!.supportFragmentManager.beginTransaction().apply {
                setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                replace(R.id.fragment_container, DialogSetupFragment())
                commit()
            }
        }
    }


    companion object {
        private const val ARG_FINISH_AFTER = "FINISH_AFTER"
        fun newInstance(finishAfter: Boolean) =
                InboxConfigurationFragment().apply {
                    arguments = Bundle().apply {
                        putBoolean(ARG_FINISH_AFTER, finishAfter)
                    }
                }
    }
}
