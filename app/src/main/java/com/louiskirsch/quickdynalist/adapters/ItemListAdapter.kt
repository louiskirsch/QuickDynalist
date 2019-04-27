package com.louiskirsch.quickdynalist.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ImageSpan
import android.view.*
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.OnLinkTouchListener
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import com.louiskirsch.quickdynalist.text.ThemedSpan
import com.louiskirsch.quickdynalist.utils.*
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.item_list_item.view.*
import kotlinx.android.synthetic.main.menu_color_picker.view.*
import nl.pvdberg.hashkode.compareFields
import nl.pvdberg.hashkode.hashKode
import java.lang.Exception
import java.util.*


class DynalistItemPopupMenu(context: Context, anchor: View, isImage: Boolean) {
    val popup = PopupMenu(context, anchor)
    private val colorPicker: ViewGroup = LayoutInflater.from(context)
            .inflate(R.layout.menu_color_picker, null) as ViewGroup
    private val colorList: ViewGroup = colorPicker.colorList
    private val colorPopup = PopupWindow(context, null, R.attr.popupMenuStyle).apply {
        contentView = colorPicker
        isFocusable = true
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
    }

    init {
        popup.inflate(R.menu.item_list_popup_menu)
        if (isImage)
            popup.inflate(R.menu.item_list_popup_image_extension)
        enableIcons()
        popup.menu.findItem(R.id.action_change_color).setOnMenuItemClickListener {
            colorPopup.showAsDropDown(anchor)
            true
        }
        anchor.setOnClickListener {
            popup.show()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun enableIcons() {
        popup.helper?.setForceShowIcon(true)
    }

    fun notifyColorPick(callback: (Int) -> Unit) {
        colorList.children.map { it as Button }.forEachIndexed { idx, btn ->
            btn.setOnClickListener {
                callback(idx)
                colorPopup.dismiss()
            }
        }
    }
}


class ItemListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    val itemCheckedStatus = itemView.itemCheckedStatus!!
    val itemParent = itemView.itemParent!!
    val itemText = itemView.itemText!!
    val itemNotes = itemView.itemNotes!!
    val itemChildren = itemView.itemChildren!!
    val itemImage = itemView.itemImage!!
    val itemMenu = itemView.itemMenu!!

    val menuPopup = DynalistItemPopupMenu(itemMenu.context, itemMenu, false)
    val imagePopup = DynalistItemPopupMenu(itemImage.context, itemImage, true)
    val popupMenus = listOf(menuPopup.popup.menu, imagePopup.popup.menu)
}

class DropOffViewHolder(val textView: TextView): RecyclerView.ViewHolder(textView)

class CachedDynalistItem(val item: DynalistItem, context: Context, displayMaxChildren: Int) {
    val spannableParent by lazy {
        item.parent.target?.getSpannableText(context)?.append(" >") as? Spannable
                ?: SpannableString("")
    }
    val spannableText: Spannable by lazy {
        item.getSpannableText(context).run {
            if (isBlank() && item.image != null)
                SpannableString(context.getString(R.string.placeholder_image))
            else
                this as Spannable
        }
    }
    val spannableNotes by lazy { item.getSpannableNotes(context) }
    val spannableChildren by lazy { item.getSpannableChildren(context, displayMaxChildren) }

    private val identifier = hashKode(item.modified, item.children.map { it.modified })

    fun eagerInitialize(includeParent: Boolean = false) {
        if (includeParent)
            spannableParent
        spannableText
        spannableNotes
        spannableChildren
    }

    private fun applyThemedSpans(context: Context, includeParent: Boolean) {
        if (includeParent)
            ThemedSpan.applyAll(context, spannableParent)
        ThemedSpan.applyAll(context, spannableText)
        ThemedSpan.applyAll(context, spannableNotes)
        ThemedSpan.applyAll(context, spannableChildren)
    }

    private fun applyImageTint(context: Context, includeParent: Boolean) {
        val accentColor = context.resolveColorAttribute(android.R.attr.colorAccent)
        val primaryColor = context.resolveColorAttribute(android.R.attr.textColorPrimary)
        val secondaryColor = context.resolveColorAttribute(android.R.attr.textColorSecondary)

        val list = (if (includeParent) listOf(Pair(spannableParent, accentColor))
        else emptyList()) + listOf(
                Pair(spannableText, primaryColor),
                Pair(spannableChildren, secondaryColor),
                Pair(spannableNotes, secondaryColor))

        list.forEach { (text, color) ->
            text.getSpans(0, text.length, ImageSpan::class.java).forEach {
                it.drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    fun applyTheme(context: Context, includeParent: Boolean = false) {
        applyThemedSpans(context, includeParent)
        applyImageTint(context, includeParent)
    }

    override fun equals(other: Any?) = compareFields(other) {
        one.identifier correspondsTo two.identifier
        one.item correspondsTo two.item
    }

    override fun hashCode() = hashKode(identifier, item)
}

class ItemListAdapter(context: Context, showChecklist: Boolean,
                      private val displayParentText: Boolean)
    : RecyclerView.Adapter<RecyclerView.ViewHolder>(), ItemTouchCallback.ItemTouchHelperContract {

    private val items = ArrayList<CachedDynalistItem>()
    private val idToItem = HashMap<Long, CachedDynalistItem>()
    private val itemColors = context.resources.getIntArray(R.array.itemColors)

    var moveInProgress: Boolean = false
        private set

    var onClickListener: ((DynalistItem) -> Unit)? = null
    var onLongClickListener: ((DynalistItem) -> Boolean)? = null
    var onPopupItemClickListener: ((DynalistItem, MenuItem) -> Boolean)? = null
    var onRowMovedListener: ((DynalistItem, Int) -> Unit)? = null
    var onRowMovedOnDropoffListener: ((DynalistItem, Int) -> Boolean)? = null
    var onRowMovedIntoListener: ((DynalistItem, DynalistItem) -> Unit)? = null
    var onRowSwipedListener: ((DynalistItem, Int) -> Boolean)? = null
    var onCheckedStatusChangedListener: ((DynalistItem, Boolean) -> Unit)? = null
    var onMoveStartListener: (() -> Unit)? = null
    var onColorSelected: ((DynalistItem, Int) -> Unit)? = null

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

    companion object {
        private val startDropOffs = intArrayOf()
        private val endDropOffs = intArrayOf(R.id.dropoff_parent)
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
            return items.size + (startDropOffs.size + endDropOffs.size)
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
        if (moveInProgress && (position < startDropOffs.size
                        || position >= itemCount - endDropOffs.size))
            return R.id.view_type_dropoff
        return R.id.view_type_item
    }

    private fun getItemId(item: CachedDynalistItem): Long = item.item.clientId
    override fun getItemId(position: Int): Long {
        return correctPositionForDropOffs(position)?.let { getItemId(items[it]) } ?: run {
            return if (position < startDropOffs.size)
                startDropOffs[position].toLong()
            else
                endDropOffs[position - itemCount + endDropOffs.size].toLong()
        }
    }

    private fun correctPositionForDropOffs(position: Int): Int? {
        if (!moveInProgress)
            return position
        if (getItemViewType(position) == R.id.view_type_dropoff)
            return null
        return position - startDropOffs.size
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
            else -> throw IllegalStateException("Invalid drop off point")
        }
        holder.textView.text = text
    }

    private fun onBindItemViewHolder(holder: ItemListViewHolder, position: Int) {
        val context = holder.itemView.context
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

        if (displayParentText) {
            holder.itemParent.visibility = if (item.spannableParent.isNotBlank())
                View.VISIBLE else View.GONE
            holder.itemParent.text = item.spannableParent
        }

        item.applyTheme(context)
        holder.itemText.text = item.spannableText
        holder.itemText.scaleImageSpans()
        holder.itemNotes.visibility = if (item.spannableNotes.isBlank()) View.GONE else View.VISIBLE
        holder.itemNotes.text = item.spannableNotes
        holder.itemNotes.scaleImageSpans()
        holder.itemChildren.visibility = if (item.spannableChildren.isBlank()) View.GONE else View.VISIBLE
        holder.itemChildren.text = item.spannableChildren
        holder.itemChildren.scaleImageSpans()
        holder.itemView.setOnClickListener { onClickListener?.invoke(idToItem[clientId]!!.item) }
        holder.itemView.isActivated = selectedItem == item.item
        holder.itemView.isPressed = highlightedPosition == position

        val color = item.item.color
        if (color > 0)
            holder.itemView.background.setTint(itemColors[color])
        else
            holder.itemView.background.setTintList(null)

        val popupListener = { menuItem: MenuItem ->
            onPopupItemClickListener?.invoke(idToItem[clientId]!!.item, menuItem) ?: false
        }
        val colorPickListener = { color: Int ->
            onColorSelected?.invoke(idToItem[clientId]!!.item, color) ?: Unit
        }
        holder.menuPopup.popup.setOnMenuItemClickListener(popupListener)
        holder.imagePopup.popup.setOnMenuItemClickListener(popupListener)
        holder.menuPopup.notifyColorPick(colorPickListener)
        holder.imagePopup.notifyColorPick(colorPickListener)
        holder.itemMenu.visibility = View.VISIBLE
        holder.itemImage.visibility = View.GONE
        holder.itemNotes.isEllipsized {
            holder.menuPopup.popup.menu.findItem(R.id.action_show_details).isVisible = it
        }
        holder.popupMenus.forEach {
            it.findItem(R.id.action_change_date_remove).isVisible = item.item.date != null
            it.findItem(R.id.action_move_to_bookmark).isVisible = !item.item.isBookmark
        }

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
                        holder.itemNotes.isEllipsized {
                            holder.imagePopup.popup.menu.
                                    findItem(R.id.action_show_details).isVisible = it
                        }
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

    override fun onRowSwiped(position: Int, direction: Int) {
        val item = items[position].item
        if (onRowSwipedListener?.invoke(item, direction) == true) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    override fun onMoveStart(position: Int) {
        onMoveStartListener?.invoke()
        moveInProgress = true
        if (startDropOffs.isNotEmpty())
            notifyItemRangeInserted(0, startDropOffs.size)
        if (endDropOffs.isNotEmpty())
            notifyItemRangeInserted(itemCount - endDropOffs.size, endDropOffs.size)
    }

    override fun onMoveEnd(position: Int) {
        if (startDropOffs.isNotEmpty())
            notifyItemRangeRemoved(0, startDropOffs.size)
        if (endDropOffs.isNotEmpty())
            notifyItemRangeRemoved(itemCount - endDropOffs.size, endDropOffs.size)
        moveInProgress = false
    }

    override fun onRowMovedInto(fromPosition: Int, intoPosition: Int) {
        val fromIndex = correctPositionForDropOffs(fromPosition)!!
        val fromItem = items[fromIndex].item
        if (getItemViewType(intoPosition) == R.id.view_type_dropoff) {
            val itemId = getItemId(intoPosition).toInt()
            val result = onRowMovedOnDropoffListener?.invoke(fromItem, itemId) ?: false
            if (result) {
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
        return position in startDropOffs.size until (itemCount - endDropOffs.size)
    }

    override fun onLongClick(position: Int) {
        val index = correctPositionForDropOffs(position)!!
        onLongClickListener?.invoke(items[index].item)
    }
}
