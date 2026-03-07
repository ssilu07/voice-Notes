package com.royals.voicenotes

import android.Manifest
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.royals.voicenotes.databinding.FragmentSavedNotesBinding
import kotlinx.coroutines.launch
import java.util.Locale

class SavedNotesFragment : Fragment() {

    private var _binding: FragmentSavedNotesBinding? = null
    private val binding get() = _binding!!

    private val noteViewModel: NoteViewModel by activityViewModels()
    private lateinit var notesAdapter: NotesAdapter

    private var currentSearchLiveData: LiveData<List<Note>>? = null

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
        setupSearch()

        binding.btnExportAll.setOnClickListener { exportAllNotes() }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                performSearch(query)
            }
        })
    }

    private fun performSearch(query: String) {
        // Remove old observer
        currentSearchLiveData?.removeObservers(viewLifecycleOwner)

        if (query.isEmpty()) {
            // Show all notes
            currentSearchLiveData = noteViewModel.allNotes
        } else {
            currentSearchLiveData = noteViewModel.searchNotes(query)
        }

        currentSearchLiveData?.observe(viewLifecycleOwner) { notes ->
            notesAdapter.submitList(notes)
            updateEmptyState(notes.isEmpty())
            binding.btnExportAll.visibility = if (notes.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupViewModelObservers() {
        noteViewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            Log.d("SavedNotesFragment", "Notes list updated: ${notes.size} notes")
            // Only update if no active search
            if (binding.etSearch.text.isNullOrBlank()) {
                notesAdapter.submitList(notes)
                updateEmptyState(notes.isEmpty())
                binding.btnExportAll.visibility = if (notes.isNotEmpty()) View.VISIBLE else View.GONE
            }
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
            onEditClick = { note -> handleNoteClick(note) },
            onShareClick = { note -> shareNote(note) },
            onExportClick = { note -> exportNote(note) },
            onPinClick = { note -> noteViewModel.togglePin(note) }
        )

        binding.rvNotes.apply {
            adapter = notesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }

    private fun handleNoteClick(note: Note) {
        when {
            note.isAudioNote() -> showAudioPlayer(note)
            note.isTextNote() -> {
                val bundle = bundleOf("noteId" to note.id)
                findNavController().navigate(R.id.action_saved_notes_to_view_note, bundle)
            }
            else -> Toast.makeText(requireContext(), "Unknown note type", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAudioPlayer(note: Note) {
        AudioPlayerDialog(
            context = requireContext(),
            note = note,
            onDeleteClick = {
                noteViewModel.delete(note)
                Toast.makeText(requireContext(), "Audio recording deleted", Toast.LENGTH_SHORT).show()
            }
        ).show()
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
                notesAdapter.notifyItemChanged(position)
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvNotes)
    }

    private fun showDeleteConfirmation(note: Note) {
        val noteType = if (note.isAudioNote()) "audio recording" else "note"
        val displayType = noteType.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Delete $displayType")
            .setMessage("Are you sure you want to delete '${note.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                if (note.isAudioNote() && note.audioFilePath != null) {
                    java.io.File(note.audioFilePath).delete()
                }
                noteViewModel.delete(note)

                // Undo delete snackbar
                Snackbar.make(binding.root, "$displayType deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        noteViewModel.insert(note.copy(id = 0))
                    }
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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
