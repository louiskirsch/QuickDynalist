package com.louiskirsch.quickdynalist.objectbox

import android.util.Log
import androidx.lifecycle.LiveData
import io.objectbox.BoxStore
import io.objectbox.query.Query
import io.objectbox.reactive.DataObserver
import io.objectbox.reactive.DataSubscription
import io.objectbox.reactive.DataTransformer

class SubscriberOBLiveData<T, TO>(private val boxStore: BoxStore,
                                  private val subscribeClass: Class<T>,
                                  private val query: Query<T>,
                                  private val transformer: (Query<T>) -> List<TO>) :
        LiveData<List<TO>>() {
    private var subscription: DataSubscription? = null
    private val listener = DataObserver<List<TO>> { data -> postValue(data) }

    override fun onActive() {
        // called when the LiveData object has an active observer
        if (subscription == null) {
            subscription = boxStore.subscribe(subscribeClass).transform {
                transformer(query)
            }.onError {
                Log.e(javaClass.name, "Transform failed", it)
            }.observer(listener)
        }
    }

    override fun onInactive() {
        // called when the LiveData object doesn't have any active observers
        if (!hasObservers()) {
            subscription!!.cancel()
            subscription = null
        }
    }
}
