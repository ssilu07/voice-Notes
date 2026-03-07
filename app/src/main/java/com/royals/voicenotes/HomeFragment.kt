package com.royals.voicenotes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.royals.voicenotes.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val noteViewModel: NoteViewModel by activityViewModels()
    private lateinit var notesAdapter: NotesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        binding.cardStartRecording.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_audio_record)
        }

        binding.cardNewTextNote.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_nav_record)
        }

        binding.cardSummarize.setOnClickListener {
            showNoteSelectorForSummarize()
        }

        binding.cardGenerateTags.setOnClickListener {
            showNoteSelectorForTags()
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

        binding.recyclerViewNotes.apply {
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
                findNavController().navigate(R.id.action_nav_home_to_view_note, bundle)
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

    private fun observeViewModel() {
        noteViewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            val recentNotes = notes.take(5)
            notesAdapter.submitList(recentNotes)

            if (recentNotes.isEmpty()) {
                binding.recyclerViewNotes.visibility = View.GONE
            } else {
                binding.recyclerViewNotes.visibility = View.VISIBLE
            }
        }
    }

    // --- Summarize Feature ---
    private fun showNoteSelectorForSummarize() {
        val notes = noteViewModel.allNotes.value?.filter { it.isTextNote() } ?: emptyList()
        if (notes.isEmpty()) {
            Toast.makeText(requireContext(), "No text notes to summarize", Toast.LENGTH_SHORT).show()
            return
        }

        val titles = notes.map { it.title }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select a note to summarize")
            .setItems(titles) { _, which ->
                val note = notes[which]
                val summary = TextAnalyzer.summarize(note.content)
                AlertDialog.Builder(requireContext())
                    .setTitle("Summary")
                    .setMessage(summary)
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Generate Tags Feature ---
    private fun showNoteSelectorForTags() {
        val notes = noteViewModel.allNotes.value?.filter { it.isTextNote() } ?: emptyList()
        if (notes.isEmpty()) {
            Toast.makeText(requireContext(), "No text notes for tag generation", Toast.LENGTH_SHORT).show()
            return
        }

        val titles = notes.map { it.title }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select a note for tags")
            .setItems(titles) { _, which ->
                val note = notes[which]
                val tags = TextAnalyzer.generateTags(note.content)
                val tagsText = if (tags.isEmpty()) "No tags could be generated"
                else tags.joinToString("  ")
                AlertDialog.Builder(requireContext())
                    .setTitle("Generated Tags")
                    .setMessage(tagsText)
                    .setPositiveButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun shareNote(note: Note) {
        FileHelper.shareNote(requireContext(), note)
    }

    private fun exportNote(note: Note) {
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                val file = FileHelper.exportNoteToFile(requireContext(), note)
                if (file != null) {
                    Toast.makeText(requireContext(), "Note exported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to export note", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(requireContext(), "Storage permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
