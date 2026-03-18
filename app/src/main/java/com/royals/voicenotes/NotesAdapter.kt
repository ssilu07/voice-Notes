package com.royals.voicenotes

import android.graphics.Color
import android.graphics.PorterDuff
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

    override fun submitList(list: List<Note>?) {
        super.submitList(if (list != null) ArrayList(list) else null)
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            try {
                binding.tvNoteTitle.text = note.title
                binding.tvNoteContent.text = note.content
                binding.tvNoteTimestamp.text = note.timestamp

                val wordCount = if (note.content.isBlank()) 0
                else note.content.trim().split("\\s+".toRegex()).size
                binding.tvWordCount.text = "$wordCount words"

                // Pin - use single drawable, change color with setColorFilter
                setPinColor(note.isPinned)

                binding.btnPin.setOnClickListener {
                    setPinColor(!note.isPinned)
                    onPinClick(note)
                }

                binding.btnDelete.setOnClickListener { onDeleteClick(note) }
                binding.btnEdit.setOnClickListener { onEditClick(note) }
                binding.btnShare.setOnClickListener { onShareClick(note) }
                binding.btnExport.setOnClickListener { onExportClick(note) }

                // Card click - but NOT intercept child button clicks
                binding.root.setOnClickListener { onEditClick(note) }

                // Fade-in animation
                binding.root.alpha = 0f
                binding.root.animate().alpha(1f).setDuration(200).start()

            } catch (e: Exception) {
                Log.e("NotesAdapter", "Error binding note: ${note.id}", e)
            }
        }

        private fun setPinColor(isPinned: Boolean) {
            if (isPinned) {
                binding.btnPin.setColorFilter(Color.parseColor("#E53935"), PorterDuff.Mode.SRC_IN)
            } else {
                binding.btnPin.setColorFilter(Color.parseColor("#9E9E9E"), PorterDuff.Mode.SRC_IN)
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
