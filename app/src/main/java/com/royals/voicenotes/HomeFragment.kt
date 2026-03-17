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
import com.royals.voicenotes.Constants
import com.royals.voicenotes.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
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

    private fun observeViewModel() {
        noteViewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            val recentNotes = notes.take(Constants.RECENT_NOTES_LIMIT)
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
            Toast.makeText(requireContext(), getString(R.string.no_text_notes_summarize), Toast.LENGTH_SHORT).show()
            return
        }

        val titles = notes.map { it.title }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_note_summarize))
            .setItems(titles) { _, which ->
                val note = notes[which]
                viewLifecycleOwner.lifecycleScope.launch {
                    val summary = withContext(Dispatchers.Default) {
                        TextAnalyzer.summarize(note.content)
                    }
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.summary_title))
                        .setMessage(summary)
                        .setPositiveButton(getString(R.string.action_ok), null)
                        .show()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    // --- Generate Tags Feature ---
    private fun showNoteSelectorForTags() {
        val notes = noteViewModel.allNotes.value?.filter { it.isTextNote() } ?: emptyList()
        if (notes.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_text_notes_tags), Toast.LENGTH_SHORT).show()
            return
        }

        val titles = notes.map { it.title }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_note_tags))
            .setItems(titles) { _, which ->
                val note = notes[which]
                viewLifecycleOwner.lifecycleScope.launch {
                    val tags = withContext(Dispatchers.Default) {
                        TextAnalyzer.generateTags(note.content)
                    }
                    val tagsText = if (tags.isEmpty()) getString(R.string.no_tags_generated)
                    else tags.joinToString("  ")
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.generated_tags_title))
                        .setMessage(tagsText)
                        .setPositiveButton(getString(R.string.action_ok), null)
                        .show()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showDeleteConfirmation(note: Note) {
        NoteUtils.showDeleteConfirmation(
            context = requireContext(),
            note = note,
            noteViewModel = noteViewModel,
            snackbarRoot = binding.root,
            tag = "HomeFragment"
        )
    }

    private fun shareNote(note: Note) {
        FileHelper.shareNote(requireContext(), note)
    }

    private fun exportNote(note: Note) {
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            viewLifecycleOwner.lifecycleScope.launch {
                val file = FileHelper.exportNoteToFile(requireContext(), note)
                if (file != null) {
                    Toast.makeText(requireContext(), getString(R.string.note_exported_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.note_export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.storage_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
