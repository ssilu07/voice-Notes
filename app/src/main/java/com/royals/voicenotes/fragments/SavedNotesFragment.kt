package com.royals.voicenotes

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.royals.voicenotes.databinding.FragmentSavedNotesBinding
import kotlinx.coroutines.launch

class SavedNotesFragment : Fragment() {

    private var _binding: FragmentSavedNotesBinding? = null
    private val binding get() = _binding!!

    // ViewModel ko Activity se share karein
    private val noteViewModel: NoteViewModel by activityViewModels()
    private lateinit var notesAdapter: NotesAdapter
    private var lastDeletedNote: Note? = null

    // Storage permission launcher
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "Storage permission denied", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupViewModelObservers()
        setupSwipeToDelete()

        binding.btnExportAll.setOnClickListener {
            exportAllNotes()
        }
    }

    private fun setupViewModelObservers() {
        noteViewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            Log.d("SavedNotesFragment", "Notes list updated: ${notes.size} notes")
            notesAdapter.submitList(notes)
            updateEmptyState(notes.isEmpty())
            binding.btnExportAll.visibility = if (notes.isNotEmpty()) View.VISIBLE else View.GONE
        }

        noteViewModel.notesCount.observe(viewLifecycleOwner) { count ->
            binding.tvTotalNotes.text = count.toString()
            binding.statsCard.visibility = if (count > 0) View.VISIBLE else View.GONE
        }

        noteViewModel.totalWords.observe(viewLifecycleOwner) { words ->
            binding.tvTotalWords.text = words.toString()
        }

        noteViewModel.operationStatus.observe(viewLifecycleOwner) { status ->
            if (status.isNotEmpty()) {
                if (status.contains("Error")) {
                    Toast.makeText(requireContext(), status, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
                }
                noteViewModel.clearOperationStatus()
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.rvNotes.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.rvNotes.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            onDeleteClick = { note -> showDeleteConfirmation(note) },
            onEditClick = { note -> handleNoteClick(note) }, // UPDATED: Handle different note types
            onShareClick = { note -> shareNote(note) },
            onExportClick = { note -> exportNote(note) }
        )

        binding.rvNotes.apply {
            adapter = notesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }

    // NEW: Handle note click based on note type
    private fun handleNoteClick(note: Note) {
        when {
            note.isAudioNote() -> {
                // Show audio player dialog
                showAudioPlayer(note)
            }
            note.isTextNote() -> {
                // Navigate to view/edit fragment
                val bundle = bundleOf("noteId" to note.id)
                findNavController().navigate(R.id.action_saved_notes_to_view_note, bundle)
            }
            else -> {
                Toast.makeText(requireContext(), "Unknown note type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // NEW: Show audio player dialog
    private fun showAudioPlayer(note: Note) {
        val audioPlayerDialog = AudioPlayerDialog(
            context = requireContext(),
            note = note,
            onDeleteClick = {
                noteViewModel.delete(note)
                Toast.makeText(requireContext(), "Audio recording deleted", Toast.LENGTH_SHORT).show()
            }
        )
        audioPlayerDialog.show()
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val note = notesAdapter.currentList[position]
                showDeleteConfirmation(note)
                // Note ko wapas laane ke liye adapter ko notify karein taaki swipe cancel ho
                notesAdapter.notifyItemChanged(position)
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvNotes)
    }

    private fun showDeleteConfirmation(note: Note) {
        val noteType = if (note.isAudioNote()) "audio recording" else "note"

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete ${noteType.capitalize()}")
            .setMessage("Are you sure you want to delete '${note.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                // Delete audio file if it's an audio note
                if (note.isAudioNote() && note.audioFilePath != null) {
                    java.io.File(note.audioFilePath).delete()
                }

                noteViewModel.delete(note)
                lastDeletedNote = note
                Toast.makeText(requireContext(), "${noteType.capitalize()} deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Helper Functions (Export/Share) ---

    private fun exportNote(note: Note) {
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                val file = FileHelper.exportNoteToFile(requireContext(), note)
                if (file != null) {
                    Toast.makeText(requireContext(), "Note exported to Downloads", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to export note", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun exportAllNotes() {
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                val notes = noteViewModel.allNotes.value ?: emptyList()
                if (notes.isNotEmpty()) {
                    val file = FileHelper.exportAllNotes(requireContext(), notes)
                    if (file != null) {
                        Toast.makeText(requireContext(), "All notes exported", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to export notes", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "No notes to export", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun shareNote(note: Note) {
        FileHelper.shareNote(requireContext(), note)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}