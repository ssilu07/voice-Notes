package com.royals.voicenotes.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.royals.voicenotes.Note
import com.royals.voicenotes.NoteViewModel
import com.royals.voicenotes.R
import com.royals.voicenotes.databinding.FragmentViewNoteBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewNoteFragment : Fragment() {

    private var _binding: FragmentViewNoteBinding? = null
    private val binding get() = _binding!!

    private val noteViewModel: NoteViewModel by activityViewModels()
    private var currentNote: Note? = null
    private var isEditMode = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get note ID from arguments
        val noteId = arguments?.getInt("noteId", -1) ?: -1
        if (noteId != -1) {
            loadNote(noteId)
        } else {
            Toast.makeText(requireContext(), "Error loading note", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }

        setupClickListeners()
    }

    private fun loadNote(noteId: Int) {
        noteViewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            currentNote = notes.find { it.id == noteId }
            currentNote?.let { note ->
                displayNote(note)
            }
        }
    }

    private fun displayNote(note: Note) {
        binding.apply {
            tvNoteTitle.text = note.title
            etNoteContent.setText(note.content)
            tvTimestamp.text = note.timestamp

            // Make content non-editable initially
            etNoteContent.isEnabled = false
            etNoteContent.isFocusable = false
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            if (isEditMode) {
                showDiscardChangesDialog()
            } else {
                findNavController().navigateUp()
            }
        }

        binding.btnEdit.setOnClickListener {
            if (isEditMode) {
                saveNote()
            } else {
                enableEditMode()
            }
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun enableEditMode() {
        isEditMode = true
        binding.apply {
            etNoteContent.isEnabled = true
            etNoteContent.isFocusable = true
            etNoteContent.isFocusableInTouchMode = true
            etNoteContent.requestFocus()
            btnEdit.setImageResource(R.drawable.ic_save)
        }
    }

    private fun saveNote() {
        val updatedContent = binding.etNoteContent.text.toString().trim()

        if (updatedContent.isEmpty()) {
            Toast.makeText(requireContext(), "Note content cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        currentNote?.let { note ->
            val currentTime = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(
                Date()
            )
            val updatedTitle = if (updatedContent.length > 30)
                updatedContent.substring(0, 30) + "..."
            else
                updatedContent

            val updatedNote = note.copy(
                title = updatedTitle,
                content = updatedContent,
                timestamp = currentTime
            )

            noteViewModel.update(updatedNote)
            Toast.makeText(requireContext(), "Note updated", Toast.LENGTH_SHORT).show()

            isEditMode = false
            binding.btnEdit.setImageResource(R.drawable.ic_edit)
            binding.etNoteContent.isEnabled = false
            binding.etNoteContent.isFocusable = false

            findNavController().navigateUp()
        }
    }

    private fun showDiscardChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Discard Changes?")
            .setMessage("You have unsaved changes. Are you sure you want to discard them?")
            .setPositiveButton("Discard") { _, _ ->
                findNavController().navigateUp()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation() {
        currentNote?.let { note ->
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Note")
                .setMessage("Are you sure you want to delete '${note.title}'?")
                .setPositiveButton("Delete") { _, _ ->
                    noteViewModel.delete(note)
                    Toast.makeText(requireContext(), "Note deleted", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}