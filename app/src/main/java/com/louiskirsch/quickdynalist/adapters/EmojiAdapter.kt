package com.louiskirsch.quickdynalist.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.louiskirsch.quickdynalist.R
import com.louiskirsch.quickdynalist.utils.EmojiFactory

class EmojiViewHolder(val textView: TextView): RecyclerView.ViewHolder(textView)

class EmojiAdapter: RecyclerView.Adapter<EmojiViewHolder>() {

    var selectedPosition: Int = 0
        set(value) {
            assert(value in 0..(itemCount - 1))
            val oldValue = field
            field = value
            notifyItemChanged(oldValue)
            notifyItemChanged(value)
        }

    var selectedValue: String
        get() = EmojiFactory.getEmojiAt(selectedPosition)
        set(value) = EmojiFactory.emojis.indexOf(value).let {
            if (it >= 0) selectedPosition = it
        }

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return EmojiViewHolder(inflater.inflate(R.layout.emoji_list_item,
                parent, false) as TextView)
    }

    override fun getItemCount(): Int = EmojiFactory.size
    override fun getItemId(position: Int): Long = position.toLong()

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        holder.textView.run {
            isActivated = selectedPosition == position
            text = EmojiFactory.getEmojiAt(position)
            setOnClickListener { selectedPosition = position }
            forceLayout()
        }
    }

}