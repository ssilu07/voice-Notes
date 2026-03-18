package com.royals.voicenotes

import android.Manifest
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SavedNotesFragment : Fragment() {

    private var _binding: FragmentSavedNotesBinding? = null
    private val binding get() = _binding!!

    private val noteViewModel: NoteViewModel by activityViewModels()
    private lateinit var notesAdapter: NotesAdapter

    private var currentSearchLiveData: LiveData<List<Note>>? = null
    private var selectedCategory: String? = null // null means "All"

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(requireContext(), getString(R.string.storage_permission_denied), Toast.LENGTH_LONG).show()
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
        setupSwipeGestures()
        setupSearch()
        setupCategoryChips()

        binding.btnExportAll.setOnClickListener { exportAllNotes() }
    }

    private fun setupCategoryChips() {
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCategory = when {
                checkedIds.contains(R.id.chipGeneral) -> Note.CATEGORY_GENERAL
                checkedIds.contains(R.id.chipWork) -> Note.CATEGORY_WORK
                checkedIds.contains(R.id.chipPersonal) -> Note.CATEGORY_PERSONAL
                checkedIds.contains(R.id.chipIdeas) -> Note.CATEGORY_IDEAS
                else -> null // "All" chip or none
            }
            refreshNotesList()
        }
    }

    private fun refreshNotesList() {
        val query = binding.etSearch.text?.toString()?.trim() ?: ""
        if (query.isNotEmpty()) {
            performSearch(query)
        } else {
            performCategoryFilter()
        }
    }

    private fun performCategoryFilter() {
        currentSearchLiveData?.removeObservers(viewLifecycleOwner)

        currentSearchLiveData = if (selectedCategory != null) {
            noteViewModel.getNotesByCategory(selectedCategory!!)
        } else {
            noteViewModel.allNotes
        }

        currentSearchLiveData?.observe(viewLifecycleOwner) { notes ->
            notesAdapter.submitList(notes)
            updateEmptyState(notes.isEmpty())
            binding.btnExportAll.visibility = if (notes.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    performCategoryFilter()
                } else {
                    performSearch(query)
                }
            }
        })
    }

    private fun performSearch(query: String) {
        currentSearchLiveData?.removeObservers(viewLifecycleOwner)

        if (query.isEmpty()) {
            performCategoryFilter()
            return
        }

        currentSearchLiveData = noteViewModel.searchNotes(query)

        currentSearchLiveData?.observe(viewLifecycleOwner) { notes ->
            val filtered = if (selectedCategory != null) {
                notes.filter { it.category == selectedCategory }
            } else {
                notes
            }
            notesAdapter.submitList(filtered)
            updateEmptyState(filtered.isEmpty())
            binding.btnExportAll.visibility = if (filtered.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupViewModelObservers() {
        noteViewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            Log.d("SavedNotesFragment", "Notes list updated: ${notes.size} notes")
            if (binding.etSearch.text.isNullOrBlank() && selectedCategory == null) {
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
            else -> Toast.makeText(requireContext(), getString(R.string.unknown_note_type), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAudioPlayer(note: Note) {
        AudioPlayerDialog(
            context = requireContext(),
            note = note,
            onDeleteClick = {
                noteViewModel.delete(note)
                Toast.makeText(requireContext(), getString(R.string.audio_recording_deleted), Toast.LENGTH_SHORT).show()
            }
        ).show()
    }

    /**
     * Enhanced swipe gestures:
     * - Swipe RIGHT = Toggle Pin (blue background)
     * - Swipe LEFT = Archive (orange background)
     */
    private fun setupSwipeGestures() {
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

                when (direction) {
                    ItemTouchHelper.RIGHT -> {
                        // Toggle Pin
                        noteViewModel.togglePin(note)
                    }
                    ItemTouchHelper.LEFT -> {
                        // Archive
                        noteViewModel.toggleArchive(note)
                        Snackbar.make(binding.root, getString(R.string.note_archived), Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.action_undo)) {
                                noteViewModel.toggleArchive(note.copy(isArchived = true))
                            }
                            .show()
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val paint = Paint()

                if (dX > 0) {
                    // Swiping RIGHT - Pin (blue)
                    paint.color = ContextCompat.getColor(requireContext(), R.color.primary_500)
                    val background = RectF(
                        itemView.left.toFloat(),
                        itemView.top.toFloat(),
                        itemView.left + dX,
                        itemView.bottom.toFloat()
                    )
                    c.drawRoundRect(background, 12f, 12f, paint)

                    // Draw pin icon text
                    paint.color = ContextCompat.getColor(requireContext(), R.color.white)
                    paint.textSize = 36f
                    paint.textAlign = Paint.Align.CENTER
                    val textY = itemView.top + (itemView.height / 2f) + 12f
                    c.drawText("PIN", itemView.left + 80f, textY, paint)

                } else if (dX < 0) {
                    // Swiping LEFT - Archive (orange)
                    paint.color = ContextCompat.getColor(requireContext(), R.color.secondary_500)
                    val background = RectF(
                        itemView.right + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat()
                    )
                    c.drawRoundRect(background, 12f, 12f, paint)

                    // Draw archive text
                    paint.color = ContextCompat.getColor(requireContext(), R.color.white)
                    paint.textSize = 32f
                    paint.textAlign = Paint.Align.CENTER
                    val textY = itemView.top + (itemView.height / 2f) + 12f
                    c.drawText("ARCHIVE", itemView.right - 100f, textY, paint)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvNotes)
    }

    private fun showDeleteConfirmation(note: Note) {
        NoteUtils.showDeleteConfirmation(
            context = requireContext(),
            note = note,
            noteViewModel = noteViewModel,
            snackbarRoot = binding.root,
            tag = "SavedNotesFragment"
        )
    }

    private fun exportNote(note: Note) {
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                val file = FileHelper.exportNoteToFile(requireContext(), note)
                if (file != null) {
                    Toast.makeText(requireContext(), getString(R.string.note_exported), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.note_export_failed), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(requireContext(), getString(R.string.all_notes_exported_success), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.notes_export_failed), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), getString(R.string.no_notes_to_export), Toast.LENGTH_SHORT).show()
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
