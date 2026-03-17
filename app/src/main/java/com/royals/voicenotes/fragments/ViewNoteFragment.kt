package com.royals.voicenotes.fragments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.royals.voicenotes.Constants
import com.royals.voicenotes.Note
import com.royals.voicenotes.NoteUtils
import com.royals.voicenotes.NoteViewModel
import com.royals.voicenotes.R
import com.royals.voicenotes.ReminderHelper
import com.royals.voicenotes.RichTextHelper
import com.royals.voicenotes.databinding.FragmentViewNoteBinding
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class ViewNoteFragment : Fragment() {

    private var _binding: FragmentViewNoteBinding? = null
    private val binding get() = _binding!!

    private val noteViewModel: NoteViewModel by activityViewModels()
    private var currentNote: Note? = null
    private var isEditMode = false
    private var originalContent: String = ""  // For undo support

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
            Toast.makeText(requireContext(), getString(R.string.error_loading_note), Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }

        setupClickListeners()
        setupFormattingToolbar()
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

            // Show reminder info if set
            if (note.hasReminder()) {
                val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                tvReminderInfo.text = getString(R.string.reminder_set_info, dateFormat.format(Date(note.reminderTime!!)))
                tvReminderInfo.visibility = View.VISIBLE
            } else {
                tvReminderInfo.visibility = View.GONE
            }

            // Make content non-editable initially
            etNoteContent.isEnabled = false
            etNoteContent.isFocusable = false
        }
    }

    private fun setupFormattingToolbar() {
        binding.btnBold.setOnClickListener {
            RichTextHelper.toggleBold(binding.etNoteContent)
        }
        binding.btnItalic.setOnClickListener {
            RichTextHelper.toggleItalic(binding.etNoteContent)
        }
        binding.btnUnderline.setOnClickListener {
            RichTextHelper.toggleUnderline(binding.etNoteContent)
        }
        binding.btnBulletList.setOnClickListener {
            RichTextHelper.insertBulletList(binding.etNoteContent)
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

        binding.btnReminder.setOnClickListener {
            showReminderPicker()
        }
    }

    private fun enableEditMode() {
        isEditMode = true
        originalContent = binding.etNoteContent.text.toString()
        binding.apply {
            etNoteContent.isEnabled = true
            etNoteContent.isFocusable = true
            etNoteContent.isFocusableInTouchMode = true
            etNoteContent.requestFocus()
            btnEdit.setImageResource(R.drawable.ic_save)
            formattingToolbar.visibility = View.VISIBLE
        }
    }

    private fun saveNote() {
        val updatedContent = binding.etNoteContent.text.toString().trim()

        if (updatedContent.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.note_content_empty), Toast.LENGTH_SHORT).show()
            return
        }

        currentNote?.let { note ->
            val currentTime = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(
                Date()
            )
            val updatedTitle = if (updatedContent.length > Constants.MAX_TITLE_LENGTH)
                updatedContent.substring(0, Constants.MAX_TITLE_LENGTH) + "..."
            else
                updatedContent

            val updatedNote = note.copy(
                title = updatedTitle,
                content = updatedContent,
                timestamp = currentTime
            )

            noteViewModel.update(updatedNote)

            // Undo support — restore original content if user taps Undo
            val previousNote = note.copy()
            Snackbar.make(binding.root, getString(R.string.note_updated), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.action_undo)) {
                    noteViewModel.update(previousNote)
                }
                .show()

            isEditMode = false
            binding.btnEdit.setImageResource(R.drawable.ic_edit)
            binding.etNoteContent.isEnabled = false
            binding.etNoteContent.isFocusable = false
            binding.formattingToolbar.visibility = View.GONE

            findNavController().navigateUp()
        }
    }

    private fun showReminderPicker() {
        currentNote?.let { note ->
            if (note.hasReminder()) {
                // Show option to remove or change reminder
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.reminder_title))
                    .setItems(arrayOf(
                        getString(R.string.reminder_change),
                        getString(R.string.reminder_remove)
                    )) { _, which ->
                        when (which) {
                            0 -> pickDateTime()
                            1 -> removeReminder()
                        }
                    }
                    .setNegativeButton(getString(R.string.action_cancel), null)
                    .show()
            } else {
                pickDateTime()
            }
        }
    }

    private fun pickDateTime() {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)

                        val reminderTime = calendar.timeInMillis
                        if (reminderTime <= System.currentTimeMillis()) {
                            Toast.makeText(requireContext(), getString(R.string.reminder_past_error), Toast.LENGTH_SHORT).show()
                            return@TimePickerDialog
                        }

                        setReminder(reminderTime)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    private fun setReminder(reminderTime: Long) {
        currentNote?.let { note ->
            noteViewModel.setReminder(note.id, reminderTime)
            ReminderHelper.scheduleReminder(requireContext(), note, reminderTime)

            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            Toast.makeText(
                requireContext(),
                getString(R.string.reminder_set_success, dateFormat.format(Date(reminderTime))),
                Toast.LENGTH_LONG
            ).show()

            binding.tvReminderInfo.text = getString(R.string.reminder_set_info, dateFormat.format(Date(reminderTime)))
            binding.tvReminderInfo.visibility = View.VISIBLE
        }
    }

    private fun removeReminder() {
        currentNote?.let { note ->
            noteViewModel.setReminder(note.id, null)
            ReminderHelper.cancelReminder(requireContext(), note.id)
            binding.tvReminderInfo.visibility = View.GONE
            Toast.makeText(requireContext(), getString(R.string.reminder_removed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDiscardChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.discard_changes_title))
            .setMessage(getString(R.string.discard_changes_message))
            .setPositiveButton(getString(R.string.action_discard)) { _, _ ->
                // Restore original content before navigating back
                binding.etNoteContent.setText(originalContent)
                isEditMode = false
                binding.formattingToolbar.visibility = View.GONE
                findNavController().navigateUp()
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showDeleteConfirmation() {
        currentNote?.let { note ->
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.action_delete))
                .setMessage(getString(R.string.delete_dialog_message, note.title))
                .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                    NoteUtils.deleteAudioFile(note, "ViewNoteFragment")
                    noteViewModel.delete(note)
                    Toast.makeText(requireContext(), getString(R.string.note_deleted), Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
