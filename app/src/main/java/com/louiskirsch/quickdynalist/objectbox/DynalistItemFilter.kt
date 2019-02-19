package com.louiskirsch.quickdynalist.objectbox

import android.os.Parcel
import android.os.Parcelable
import androidx.lifecycle.LiveData
import com.louiskirsch.quickdynalist.DynalistApp
import com.louiskirsch.quickdynalist.utils.*
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.converter.PropertyConverter
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.Query
import io.objectbox.query.QueryBuilder
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import java.util.*

@Entity
class DynalistItemFilter: Parcelable {
    enum class LogicMode { UNKNOWN, ALL, ANY }
    class LogicModeConverter: PropertyConverter<LogicMode, Int> {
        override fun convertToDatabaseValue(entityProperty: LogicMode?): Int
                = entityProperty?.ordinal ?: 0
        override fun convertToEntityProperty(databaseValue: Int?): LogicMode
                = databaseValue?.let { LogicMode.values()[it] } ?: LogicMode.UNKNOWN
    }
    enum class Order { UNKNOWN, MANUAL, DATE, MODIFIED_DATE }
    class OrderConverter: PropertyConverter<Order, Int> {
        override fun convertToDatabaseValue(entityProperty: Order?): Int
                = entityProperty?.ordinal ?: 0
        override fun convertToEntityProperty(databaseValue: Int?): Order
                = databaseValue?.let { Order.values()[it] } ?: Order.UNKNOWN
    }

    @Id var id: Long = 0
    var name: String? = null

    @Convert(converter = LogicModeConverter::class, dbType = Integer::class)
    var logicMode: LogicMode = LogicMode.ALL

    var minRelativeDate: Long? = null
    var maxRelativeDate: Long? = null
    var minRelativeModifiedDate: Long? = null
    var maxRelativeModifiedDate: Long? = null

    lateinit var tags: ToMany<DynalistTag>
    lateinit var parent: ToOne<DynalistItem>
    var searchDepth: Int = 1
    var containsText: String? = null
    var hideIfParentIncluded: Boolean = false
    var showAsChecklist: Boolean = false
    @Convert(converter = OrderConverter::class, dbType = Integer::class)
    var sortOrder: Order = Order.MANUAL
    var isCompleted: Boolean? = null
    var hasImage: Boolean? = null

    override fun toString(): String = name ?: ""
    override fun hashCode(): Int = (id % Int.MAX_VALUE).toInt()
    override fun equals(other: Any?): Boolean {
        return if (id > 0)
            return id == (other as? DynalistItemFilter)?.id
        else
            false
    }

    val symbol: String?
        get() = EmojiFactory.emojis.firstOrNull { name?.contains(it) ?: false }

    val nameWithoutSymbol: String?
        get() = symbol?.let { name?.replace(it, "")?.trim() } ?: name

    private val query: Query<DynalistItem> get() {
        val box = DynalistApp.instance.boxStore.boxFor<DynalistItem>()
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        return box.query {
            val filters = ArrayList<(DynalistItem) -> Boolean>()
            notEqual(DynalistItem_.name, "")
            equal(DynalistItem_.hidden, false)
            if (hasImage != null || minRelativeDate != null || maxRelativeDate != null) {
                link(DynalistItem_.metaData).run {
                    applyDateConditions(now)
                    if (hasImage != null) {
                        if (hasImage!!)
                            notNull(DynalistItemMetaData_.image)
                        else
                            isNull(DynalistItemMetaData_.image)
                    }
                }
                nextCondition()
            }
            applyModifiedDateConditions(now)
            if (containsText != null) {
                contains(DynalistItem_.name, containsText!!)
                or()
                contains(DynalistItem_.note, containsText!!)
                nextCondition()
            }
            if (isCompleted != null) {
                equal(DynalistItem_.isChecked, isCompleted!!)
                nextCondition()
            }
            if (parent.targetId > 0 && searchDepth == 1) {
                equal(DynalistItem_.parentId, parent.targetId)
                nextCondition()
            }
            if (parent.targetId > 0 && searchDepth > 1) {
                filters.add { it.hasParent(parent.targetId, searchDepth) }
            }
            if (tags.isNotEmpty()) {
                filters.add { candidate ->
                    val metaData = candidate.metaData.target
                    when (logicMode) {
                        LogicMode.ALL -> tags.all { metaData.tags.contains(it) }
                        LogicMode.ANY -> tags.any { metaData.tags.contains(it) }
                        else -> false
                    }
                }
            }
            applyFilters(filters)
            when (sortOrder) {
                Order.MANUAL -> {
                    order(DynalistItem_.serverParentId)
                    order(DynalistItem_.position)
                }
                Order.DATE -> {
                    link(DynalistItem_.metaData).run {
                        // Not sure this order is applied correctly
                        order(DynalistItemMetaData_.date)
                    }
                }
                Order.MODIFIED_DATE -> {
                    order(DynalistItem_.lastModified)
                }
                else -> Unit
            }
        }
    }

    private fun <T> QueryBuilder<T>.applyFilters(filters: List<(T) -> Boolean>) = {
        if (filters.isNotEmpty()) {
            filter { item ->
                when (logicMode) {
                    LogicMode.ALL -> filters.all { it(item) }
                    LogicMode.ANY -> filters.any { it(item) }
                    else -> false
                }
            }
        }
    }

    private fun <T> QueryBuilder<T>.nextCondition() = {
        // No need to call and(), because it is the default
        if (logicMode == LogicMode.ANY) or()
    }

    private fun QueryBuilder<DynalistItemMetaData>.applyDateConditions(now: Date) {
        if (minRelativeDate != null && maxRelativeDate != null) {
            between(DynalistItemMetaData_.date,
                    now.time + minRelativeDate!! - 1,
                    now.time + maxRelativeDate!!)
            nextCondition()
        } else if (minRelativeDate != null) {
            greater(DynalistItemMetaData_.date, now.time + minRelativeDate!! - 1)
            nextCondition()
        } else if (maxRelativeDate != null) {
            less(DynalistItemMetaData_.date, now.time + maxRelativeDate!!)
            nextCondition()
        }
    }

    private fun QueryBuilder<DynalistItem>.applyModifiedDateConditions(now: Date) {
        if (minRelativeModifiedDate != null && maxRelativeModifiedDate != null) {
            between(DynalistItem_.lastModified,
                    now.time + minRelativeModifiedDate!! - 1,
                    now.time + maxRelativeModifiedDate!!)
            nextCondition()
        } else if (minRelativeModifiedDate != null) {
            greater(DynalistItem_.lastModified, now.time + minRelativeModifiedDate!! - 1)
            nextCondition()
        } else if (maxRelativeModifiedDate != null) {
            less(DynalistItem_.lastModified, now.time + maxRelativeModifiedDate!!)
            nextCondition()
        }
    }

    val items: List<DynalistItem> get() = postQueryFilter(query.find())

    private fun postQueryFilter(items: List<DynalistItem>) = if (hideIfParentIncluded) {
            val foundSet = items.map { it.clientId }.toSet()
            items.filter { it.parent.targetId !in foundSet }
        } else {
            items
        }

    val liveData: LiveData<List<DynalistItem>>
        get() = TransformedOBLiveData(query) { postQueryFilter(it) }

    fun <T> transformedLiveData(transformer: (List<DynalistItem>) -> List<T>)
        = TransformedOBLiveData(query) { transformer(postQueryFilter(it)) }

    companion object {
        @Suppress("unused")
        @JvmField
        val CREATOR = object : Parcelable.Creator<DynalistItemFilter> {
            override fun createFromParcel(parcel: Parcel): DynalistItemFilter {
                with (parcel) {
                    val filterBox = DynalistApp.instance.boxStore.boxFor<DynalistItemFilter>()
                    val tagBox = DynalistApp.instance.boxStore.boxFor<DynalistTag>()
                    return DynalistItemFilter().apply {
                        id = readLong()
                        name = readString()
                        logicMode = LogicMode.values()[readInt()]
                        minRelativeDate = readNullableLong()
                        maxRelativeDate = readNullableLong()
                        minRelativeModifiedDate = readNullableLong()
                        maxRelativeModifiedDate = readNullableLong()
                        filterBox.attach(this)
                        tags.addAll(tagBox.get(createLongArray()!!))
                        parent.targetId = readLong()
                        searchDepth = readInt()
                        containsText = readString()
                        hideIfParentIncluded = readInt() > 0
                        showAsChecklist = readInt() > 0
                        sortOrder = Order.values()[readInt()]
                        isCompleted = readNullableBoolean()
                        hasImage = readNullableBoolean()
                    }
                }
            }
            override fun newArray(size: Int) = arrayOfNulls<DynalistItemFilter>(size)
        }
    }

    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.apply {
            writeLong(id)
            writeString(name)
            writeInt(logicMode.ordinal)
            writeNullableLong(minRelativeDate)
            writeNullableLong(maxRelativeDate)
            writeNullableLong(minRelativeModifiedDate)
            writeNullableLong(maxRelativeModifiedDate)
            writeLongArray(tags.map { it.id }.toLongArray())
            writeLong(parent.targetId)
            writeInt(searchDepth)
            writeString(containsText)
            writeInt(hideIfParentIncluded.int)
            writeInt(showAsChecklist.int)
            writeInt(sortOrder.ordinal)
            writeNullableBoolean(isCompleted)
            writeNullableBoolean(hasImage)
        }
    }

}