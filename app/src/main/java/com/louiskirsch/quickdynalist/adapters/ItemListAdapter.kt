package com.louiskirsch.quickdynalist.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.OnLinkTouchListener
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.utils.ImageCache
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.item_list_item.view.*
import nl.pvdberg.hashkode.compareFields
import nl.pvdberg.hashkode.hashKode
import java.lang.Exception
import java.util.*


class ItemListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    val itemCheckedStatus = itemView.itemCheckedStatus!!
    val itemText = itemView.itemText!!
    val itemNotes = itemView.itemNotes!!
    val itemChildren = itemView.itemChildren!!
    val itemImage = itemView.itemImage!!
    val itemMenu = itemView.itemMenu!!
    val menuPopup = PopupMenu(itemMenu.context, itemMenu).apply {
        inflate(R.menu.item_list_popup_menu)
        itemMenu.setOnClickListener { show() }
    }
    val imagePopup = PopupMenu(itemImage.context, itemImage).apply {
        inflate(R.menu.item_list_popup_menu)
        inflate(R.menu.item_list_popup_image_extension)
        itemImage.setOnClickListener { show() }
    }
}

class DropOffViewHolder(val textView: TextView): RecyclerView.ViewHolder(textView)

class CachedDynalistItem(val item: DynalistItem, context: Context) {
    val spannableText = item.getSpannableText(context).run {
        if (isBlank() && item.image != null)
            SpannableString(context.getString(R.string.placeholder_image))
        else
            this
    }
    val spannableNotes = item.getSpannableNotes(context)
    val spannableChildren = item.getSpannableChildren(context, 5)

    override fun equals(other: Any?) = compareFields(other) {
        one.spannableText.toString() correspondsTo two.spannableText.toString()
        one.spannableChildren.toString() correspondsTo two.spannableChildren.toString()
        one.spannableNotes.toString() correspondsTo two.spannableNotes.toString()
        one.item correspondsTo two.item
    }

    override fun hashCode() = hashKode(spannableText, spannableNotes, spannableChildren, item)
}

class ItemListAdapter(showChecklist: Boolean): RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        ItemTouchCallback.ItemTouchHelperContract {

    private val items = ArrayList<CachedDynalistItem>()
    private val idToItem = HashMap<Long, CachedDynalistItem>()

    var moveInProgress: Boolean = false
        private set

    var onClickListener: ((DynalistItem) -> Unit)? = null
    var onLongClickListener: ((DynalistItem) -> Boolean)? = null
    var onPopupItemClickListener: ((DynalistItem, MenuItem) -> Boolean)? = null
    var onRowMovedListener: ((DynalistItem, Int) -> Unit)? = null
    var onRowMovedOnDropoffListener: ((DynalistItem, Int) -> Boolean)? = null
    var onRowMovedIntoListener: ((DynalistItem, DynalistItem) -> Unit)? = null
    var onRowSwipedListener: ((DynalistItem) -> Unit)? = null
    var onCheckedStatusChangedListener: ((DynalistItem, Boolean) -> Unit)? = null
    var onMoveStartListener: (() -> Unit)? = null

    var selectedItem: DynalistItem? = null
        set(value) {
            if (field != null) {
                val index = items.indexOfFirst { it.item == field }
                if (index >= 0) notifyItemChanged(index)
            }
            if (value != null) {
                val index = items.indexOfFirst { it.item == value }
                if (index >= 0) notifyItemChanged(index)
            }
            field = value
        }

    private var highlightedPosition: Int? = null
    fun highlightPosition(position: Int) {
        highlightedPosition = position
        notifyItemChanged(position)
        Handler().postDelayed({
            highlightedPosition = null
            notifyItemChanged(position)
        }, 500)
    }

    init {
        setHasStableIds(true)
    }

    var showChecklist: Boolean = showChecklist
        set(value) {
            if (value != field) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    fun findPosition(item: DynalistItem) = items.indexOfFirst { it.item == item }

    fun updateItems(newItems: List<CachedDynalistItem>) {
        if (moveInProgress) {
            return
        }
        val update = {
            items.clear()
            items.addAll(newItems)
            idToItem.clear()
            idToItem.putAll(newItems.map { Pair(it.item.clientId, it) })
            Unit
        }
        val oldSize = items.size
        if (newItems.take(oldSize) == items) {
            val newCount = newItems.size - oldSize
            update()
            notifyItemRangeInserted(oldSize, newCount)
        } else {
            update()
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int {
        if (moveInProgress)
            return items.size + 2
        return items.size
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == R.id.view_type_item) {
            ItemListViewHolder(inflater.inflate(R.layout.item_list_item,
                    parent, false)).apply {
                itemText.setOnTouchListener(OnLinkTouchListener())
                itemNotes.setOnTouchListener(OnLinkTouchListener())
                itemChildren.setOnTouchListener(OnLinkTouchListener())
            }
        } else {
            DropOffViewHolder(inflater.inflate(R.layout.item_list_dropoff,
                    parent, false) as TextView)
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (moveInProgress && (position == 0 || position == itemCount - 1))
            return R.id.view_type_dropoff
        return R.id.view_type_item
    }

    private fun getItemId(item: CachedDynalistItem): Long = item.item.clientId
    override fun getItemId(position: Int): Long {
        return correctPositionForDropOffs(position)?.let { getItemId(items[it]) } ?: run {
            return if (position == 0)
                R.id.dropoff_parent.toLong()
            else
                R.id.dropoff_duplicate.toLong()
        }
    }

    private fun correctPositionForDropOffs(position: Int): Int? {
        if (!moveInProgress)
            return position
        if (getItemViewType(position) == R.id.view_type_dropoff)
            return null
        return position - 1
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == R.id.view_type_item) {
            val fixedPosition = correctPositionForDropOffs(position)!!
            onBindItemViewHolder(holder as ItemListViewHolder, fixedPosition)
        } else {
            onBindDropOffViewHolder(holder as DropOffViewHolder, position)
        }
    }

    private fun onBindDropOffViewHolder(holder: DropOffViewHolder, position: Int) {
        val context = holder.itemView.context
        val text = when (getItemId(position)) {
            R.id.dropoff_parent.toLong() -> context.getString(R.string.dropoff_parent)
            R.id.dropoff_duplicate.toLong() -> context.getString(R.string.dropoff_duplicate)
            else -> throw IllegalStateException("Invalid drop off point")
        }
        holder.textView.text = text
    }

    private fun onBindItemViewHolder(holder: ItemListViewHolder, position: Int) {
        val item = items[position]
        val clientId = item.item.clientId

        holder.itemCheckedStatus.apply {
            visibility = if (showChecklist) View.VISIBLE else View.GONE
            setOnCheckedChangeListener(null)
            isChecked = item.item.isChecked
            setOnCheckedChangeListener { _, isChecked ->
                onCheckedStatusChangedListener?.invoke(idToItem[clientId]!!.item, isChecked)
            }
        }

        holder.itemText.text = item.spannableText
        holder.itemNotes.visibility = if (item.spannableNotes.isBlank()) View.GONE else View.VISIBLE
        holder.itemNotes.text = item.spannableNotes
        holder.itemChildren.visibility = if (item.spannableChildren.isBlank()) View.GONE else View.VISIBLE
        holder.itemChildren.text = item.spannableChildren
        holder.itemView.setOnClickListener { onClickListener?.invoke(idToItem[clientId]!!.item) }
        holder.itemView.isActivated = selectedItem == item.item
        holder.itemView.isPressed = highlightedPosition == position

        val popupListener = { menuItem: MenuItem ->
            onPopupItemClickListener?.invoke(idToItem[clientId]!!.item, menuItem) ?: false
        }
        holder.menuPopup.setOnMenuItemClickListener(popupListener)
        holder.imagePopup.setOnMenuItemClickListener(popupListener)
        holder.itemMenu.visibility = View.VISIBLE
        holder.itemImage.visibility = View.GONE

        item.item.image?.also { image ->
            val picasso = Picasso.get()
            val imageCache = ImageCache(holder.itemView.context)
            val request = imageCache.getFile(image)?.let { picasso.load(it) } ?: picasso.load(image)
            request.apply {
                into(holder.itemImage, object: Callback {
                    override fun onError(e: Exception?) {}
                    override fun onSuccess() {
                        holder.itemMenu.visibility = View.GONE
                        holder.itemImage.visibility = View.VISIBLE
                    }
                })
                into(imageCache.getPutInCacheCallback(image))
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        val itemsFromPosition = correctPositionForDropOffs(fromPosition)!!
        val itemsToPosition = correctPositionForDropOffs(toPosition)!!
        if (itemsFromPosition < itemsToPosition) {
            for (i in itemsFromPosition until itemsToPosition) {
                Collections.swap(items, i, i + 1)
                items[i].item.position = items[i + 1].item.position.also {
                    items[i + 1].item.position = items[i].item.position
                }
            }
        } else {
            for (i in itemsFromPosition downTo itemsToPosition + 1) {
                Collections.swap(items, i, i - 1)
                items[i].item.position = items[i - 1].item.position.also {
                    items[i - 1].item.position = items[i].item.position
                }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowMovedToDestination(toPosition: Int) {
        val index = correctPositionForDropOffs(toPosition)!!
        val item = items[index].item
        onRowMovedListener?.invoke(item, item.position)
    }

    override fun onRowSwiped(position: Int) {
        val item = items[position].item
        items.removeAt(position)
        notifyItemRemoved(position)
        onRowSwipedListener?.invoke(item)
    }

    override fun onMoveStart(position: Int) {
        onMoveStartListener?.invoke()
        moveInProgress = true
        notifyItemInserted(0)
        notifyItemInserted(itemCount - 1)
    }

    override fun onMoveEnd(position: Int) {
        notifyItemRemoved(0)
        notifyItemRemoved(itemCount - 1)
        moveInProgress = false
    }

    override fun onRowMovedInto(fromPosition: Int, intoPosition: Int) {
        val fromIndex = correctPositionForDropOffs(fromPosition)!!
        val fromItem = items[fromIndex].item
        if (getItemViewType(intoPosition) == R.id.view_type_dropoff) {
            val itemId = getItemId(intoPosition).toInt()
            val result = onRowMovedOnDropoffListener?.invoke(fromItem, itemId) ?: false
            if (itemId != R.id.dropoff_duplicate && result) {
                items.removeAt(fromIndex)
                notifyItemRemoved(fromPosition)
            }
        } else {
            val intoIndex = correctPositionForDropOffs(intoPosition)!!
            val intoItem = items[intoIndex].item
            onRowMovedIntoListener?.invoke(fromItem, intoItem)
            items.removeAt(fromIndex)
            notifyItemRemoved(fromPosition)
        }
    }

    override fun canDropOver(position: Int): Boolean {
        return position in 1..(itemCount - 2)
    }

    override fun onLongClick(position: Int) {
        val index = correctPositionForDropOffs(position)!!
        onLongClickListener?.invoke(items[index].item)
    }
}
