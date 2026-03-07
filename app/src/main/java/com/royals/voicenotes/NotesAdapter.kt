package com.royals.voicenotes

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.royals.voicenotes.databinding.ItemNoteBinding

class NotesAdapter(
    private val onDeleteClick: (Note) -> Unit,
    private val onEditClick: (Note) -> Unit,
    private val onShareClick: (Note) -> Unit,
    private val onExportClick: (Note) -> Unit,
    private val onPinClick: (Note) -> Unit = {}
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)
        if (note != null) {
            holder.bind(note)
        }
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            try {
                binding.apply {
                    tvNoteTitle.text = note.title
                    tvNoteContent.text = note.content
                    tvNoteTimestamp.text = note.timestamp

                    val wordCount = if (note.content.isBlank()) 0
                    else note.content.trim().split("\\s+".toRegex()).size
                    tvWordCount.text = "$wordCount words"

                    // Pin indicator
                    btnPin.setImageResource(
                        if (note.isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin
                    )
                    btnPin.visibility = View.VISIBLE

                    btnPin.setOnClickListener {
                        onPinClick(note)
                    }

                    btnDelete.setOnClickListener { onDeleteClick(note) }
                    btnEdit.setOnClickListener { onEditClick(note) }
                    btnShare.setOnClickListener { onShareClick(note) }
                    btnExport.setOnClickListener { onExportClick(note) }
                    root.setOnClickListener { onEditClick(note) }

                    // Fade-in animation
                    root.alpha = 0f
                    root.animate().alpha(1f).setDuration(200).start()
                }
            } catch (e: Exception) {
                Log.e("NotesAdapter", "Error binding note: ${note.id}", e)
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean =
            oldItem == newItem
    }
}
