package com.royals.voicenotes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.royals.voicenotes.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ViewModel ko Activity se share karein
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

        // Updated: Navigate to audio recording for "Start Recording"
        binding.cardStartRecording.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_audio_record)
        }

        // Navigate to speech-to-text for "New Text Note"
        binding.cardNewTextNote.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_nav_record)
        }

        // Placeholder listeners for smart features
        binding.cardSummarize.setOnClickListener {
            Toast.makeText(requireContext(), "Summarize feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.cardGenerateTags.setOnClickListener {
            Toast.makeText(requireContext(), "Tag generation feature coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            onDeleteClick = { note -> showDeleteConfirmation(note) },
            onEditClick = { note -> handleNoteClick(note) }, // UPDATED: Handle different note types
            onShareClick = { note -> shareNote(note) },
            onExportClick = { note -> exportNote(note) }
        )

        binding.recyclerViewNotes.apply {
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
                findNavController().navigate(R.id.action_nav_home_to_view_note, bundle)
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

    private fun observeViewModel() {
        // Recent notes ko observe karein (sirf top 5 notes)
        noteViewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            // Recent notes ke liye sirf last 5 notes dikhayein
            val recentNotes = notes.take(5)
            notesAdapter.submitList(recentNotes)

            // Agar koi notes nahi hain to empty state dikhayein
            if (recentNotes.isEmpty()) {
                binding.recyclerViewNotes.visibility = View.GONE
                // Optionally show empty state message
            } else {
                binding.recyclerViewNotes.visibility = View.VISIBLE
            }
        }
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
                Toast.makeText(requireContext(), "${noteType.capitalize()} deleted", Toast.LENGTH_SHORT).show()
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