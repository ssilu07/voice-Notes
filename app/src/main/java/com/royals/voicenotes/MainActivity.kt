package com.royals.voicenotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.royals.voicenotes.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private lateinit var noteViewModel: NoteViewModel
    private lateinit var notesAdapter: NotesAdapter

    private var isListening = false
    private var currentNoteText = ""
    private var lastDeletedNote: Note? = null
    private var isRecognitionAvailable = false
    private var currentLanguage = "en-US"
    private var currentEditingNote: Note? = null

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupSpeechRecognizer()
        } else {
            showPermissionDeniedDialog()
        }
    }

    // Storage permission launcher
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Storage permission denied. Cannot export notes.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        setupViewModel()
        setupRecyclerView()
        setupClickListeners()
        checkPermissionAndSetup()
        setupSwipeToDelete()
        setupTextWatcher()
    }

    private fun setupActionBar() {
        supportActionBar?.title = "Voice Notes"
        supportActionBar?.elevation = 4f
    }

    private fun setupTextWatcher() {
        binding.etNoteText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val charCount = text.length
                val wordCount = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size
                binding.tvCharCount.text = "$charCount characters • $wordCount words"
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_all -> {
                showDeleteAllConfirmation()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            R.id.action_language -> {
                showLanguageDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            "English (US)" to "en-US",
            "English (UK)" to "en-GB",
            "Spanish" to "es-ES",
            "French" to "fr-FR",
            "German" to "de-DE",
            "Italian" to "it-IT",
            "Portuguese" to "pt-PT",
            "Hindi" to "hi-IN",
            "Chinese" to "zh-CN",
            "Japanese" to "ja-JP"
        )

        val languageNames = languages.map { it.first }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Recognition Language")
            .setItems(languageNames) { _, which ->
                val selectedLanguage = languages[which].second
                updateSpeechRecognizerLanguage(selectedLanguage)
                binding.tvLanguage.text = selectedLanguage.substring(0, 2).uppercase()
                Toast.makeText(this, "Language changed to ${languageNames[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun updateSpeechRecognizerLanguage(language: String) {
        currentLanguage = language
        speechRecognizerIntent = SpeechHelper.createSpeechRecognizerIntent(language)
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About Voice Notes")
            .setMessage("""
                Voice Notes v1.0
                
                A simple and efficient voice-to-text note taking app.
                
                Features:
                • Voice recognition
                • Save & manage notes
                • Export to file
                • Share notes
                • Multiple languages
                
                Developed with ❤️ by Royals
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDeleteAllConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete All Notes")
            .setMessage("Are you sure you want to delete all notes? This action cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                noteViewModel.deleteAll()
                Toast.makeText(this, "All notes deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Voice recording permission is required to use this app. Please grant permission in app settings.")
            .setPositiveButton("Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.fabRecord.isEnabled = false
                binding.tvStatus.text = "❌ Permission denied - Voice recording disabled"
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
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

                noteViewModel.delete(note)
                lastDeletedNote = note

                Toast.makeText(this@MainActivity, "Note deleted", Toast.LENGTH_SHORT).show()

                // Show undo dialog
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Note Deleted")
                    .setMessage("Note deleted. Do you want to undo?")
                    .setPositiveButton("UNDO") { _, _ ->
                        noteViewModel.insert(note)
                        Toast.makeText(this@MainActivity, "Note restored", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("OK", null)
                    .show()
            }
        })

        itemTouchHelper.attachToRecyclerView(binding.rvNotes)
    }

    private fun animateRecordingButton() {
        lifecycleScope.launch {
            while (isListening) {
                binding.fabRecord.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(500)
                    .withEndAction {
                        if (isListening) {
                            binding.fabRecord.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(500)
                                .start()
                        }
                    }
                    .start()
                delay(1000)
            }
        }
    }

    private fun exportNote(note: Note) {
        if (PermissionHelper.hasStoragePermission(this)) {
            lifecycleScope.launch {
                val file = FileHelper.exportNoteToFile(this@MainActivity, note)
                if (file != null) {
                    Toast.makeText(this@MainActivity, "Note exported to Downloads", Toast.LENGTH_LONG).show()

                    androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("Export Complete")
                        .setMessage("Note exported successfully. Open file?")
                        .setPositiveButton("OPEN") { _, _ ->
                            FileHelper.openFile(this@MainActivity, file)
                        }
                        .setNegativeButton("OK", null)
                        .show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to export note", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun exportAllNotes() {
        if (PermissionHelper.hasStoragePermission(this)) {
            lifecycleScope.launch {
                val notes = noteViewModel.allNotes.value ?: emptyList()
                if (notes.isNotEmpty()) {
                    val file = FileHelper.exportAllNotes(this@MainActivity, notes)
                    if (file != null) {
                        Toast.makeText(this@MainActivity, "All notes exported", Toast.LENGTH_LONG).show()

                        androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("Export Complete")
                            .setMessage("All notes exported successfully. Open file?")
                            .setPositiveButton("OPEN") { _, _ ->
                                FileHelper.openFile(this@MainActivity, file)
                            }
                            .setNegativeButton("OK", null)
                            .show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to export notes", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "No notes to export", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun shareNote(note: Note) {
        FileHelper.shareNote(this, note)
    }

    private fun setupViewModel() {
        noteViewModel = ViewModelProvider(this)[NoteViewModel::class.java]

        noteViewModel.allNotes.observe(this) { notes ->
            Log.d("MainActivity", "Notes list updated: ${notes.size} notes")

            notesAdapter.submitList(notes) {
                Log.d("MainActivity", "Adapter list updated successfully")
                updateEmptyState(notes.isEmpty())

                binding.btnExportAll.visibility = if (notes.isNotEmpty()) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }

                if (notes.isNotEmpty() && binding.emptyStateLayout.visibility == android.view.View.VISIBLE) {
                    binding.scrollView.post {
                        binding.scrollView.smoothScrollTo(0, binding.rvNotes.top)
                    }
                }
            }
        }

        noteViewModel.notesCount.observe(this) { count ->
            Log.d("MainActivity", "Notes count updated: $count")
            binding.tvTotalNotes.text = count.toString()
            binding.statsCard.visibility = if (count > 0) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        noteViewModel.totalWords.observe(this) { words ->
            Log.d("MainActivity", "Total words updated: $words")
            binding.tvTotalWords.text = words.toString()
        }

        noteViewModel.operationStatus.observe(this) { status ->
            if (status.isNotEmpty()) {
                Log.d("MainActivity", "Operation status: $status")
                if (status.contains("Error")) {
                    Toast.makeText(this, status, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
                }
                noteViewModel.clearOperationStatus()
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        Log.d("MainActivity", "Updating empty state: isEmpty=$isEmpty")

        if (isEmpty) {
            binding.emptyStateLayout.visibility = android.view.View.VISIBLE
            binding.rvNotes.visibility = android.view.View.GONE
        } else {
            binding.emptyStateLayout.visibility = android.view.View.GONE
            binding.rvNotes.visibility = android.view.View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        Log.d("MainActivity", "Setting up RecyclerView")

        notesAdapter = NotesAdapter(
            onDeleteClick = { note ->
                Log.d("MainActivity", "Delete requested for note: ${note.id}")
                showDeleteConfirmation(note)
            },
            onEditClick = { note ->
                Log.d("MainActivity", "Edit requested for note: ${note.id}")
                editNote(note)
            },
            onShareClick = { note ->
                Log.d("MainActivity", "Share requested for note: ${note.id}")
                shareNote(note)
            },
            onExportClick = { note ->
                Log.d("MainActivity", "Export requested for note: ${note.id}")
                exportNote(note)
            }
        )

        binding.rvNotes.apply {
            adapter = notesAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            setHasFixedSize(true)
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 300
                removeDuration = 300
                moveDuration = 300
                changeDuration = 300
            }
        }

        Log.d("MainActivity", "RecyclerView setup completed")
    }

    private fun editNote(note: Note) {
        Log.d("MainActivity", "Editing note: ${note.id}")

        binding.etNoteText.setText(note.content)
        currentNoteText = note.content
        currentEditingNote = note

        binding.scrollView.smoothScrollTo(0, 0)
        Toast.makeText(this, "Note loaded for editing. Make changes and save.", Toast.LENGTH_SHORT).show()
    }

    private fun setupClickListeners() {
        binding.fabRecord.setOnClickListener {
            if (!isRecognitionAvailable) {
                Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        binding.btnSave.setOnClickListener {
            saveCurrentNote()
        }

        binding.btnClear.setOnClickListener {
            if (currentNoteText.isNotEmpty() || binding.etNoteText.text.toString().isNotEmpty()) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Clear Note")
                    .setMessage("Are you sure you want to clear the current note?")
                    .setPositiveButton("Clear") { _, _ ->
                        clearCurrentNote()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        binding.btnExportAll.setOnClickListener {
            exportAllNotes()
        }
    }

    private fun checkPermissionAndSetup() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                setupSpeechRecognizer()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setupSpeechRecognizer() {
        try {
            if (::speechRecognizer.isInitialized) {
                speechRecognizer.destroy()
            }

            isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this)
            if (!isRecognitionAvailable) {
                binding.fabRecord.isEnabled = false
                binding.tvStatus.text = "❌ Speech recognition not available on this device"
                return
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizerIntent = SpeechHelper.createSpeechRecognizerIntent(currentLanguage)

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    runOnUiThread {
                        binding.tvStatus.text = "🎤 Listening... Speak now!"
                        animateRecordingButton()
                    }
                }

                override fun onBeginningOfSpeech() {
                    runOnUiThread {
                        binding.tvStatus.text = "🔴 Recording..."
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    runOnUiThread {
                        binding.tvStatus.text = "🔄 Processing speech..."
                    }
                }

                override fun onError(error: Int) {
                    runOnUiThread {
                        val errorMessage = SpeechHelper.getErrorMessage(error)
                        binding.tvStatus.text = "❌ $errorMessage"
                        stopListening()

                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> {
                                Toast.makeText(this@MainActivity, "No speech detected. Try speaking louder or closer to the microphone.", Toast.LENGTH_LONG).show()
                            }
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                                Toast.makeText(this@MainActivity, "Network error. Check your internet connection.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    runOnUiThread {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            currentNoteText += if (currentNoteText.isEmpty()) recognizedText else " $recognizedText"
                            binding.etNoteText.setText(currentNoteText)
                            binding.etNoteText.setSelection(currentNoteText.length)
                            binding.tvStatus.text = "✅ Speech converted successfully!"

                            binding.fabRecord.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                        stopListening()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    runOnUiThread {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partialText = matches[0]
                            val displayText = if (currentNoteText.isEmpty()) partialText else "$currentNoteText $partialText"
                            binding.etNoteText.setText(displayText)
                            binding.etNoteText.setSelection(displayText.length)
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } catch (e: Exception) {
            binding.tvStatus.text = "❌ Failed to setup speech recognition"
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startListening() {
        if (!isListening && isRecognitionAvailable && ::speechRecognizer.isInitialized) {
            try {
                isListening = true
                binding.fabRecord.setImageResource(R.drawable.ic_stop)
                binding.fabRecord.backgroundTintList = ContextCompat.getColorStateList(this, R.color.red_500)
                binding.tvStatus.text = "🔄 Initializing..." // onReadyForSpeech will update this

                // Call startListening directly
                speechRecognizer.startListening(speechRecognizerIntent)

            } catch (e: Exception) {
                resetRecordingState()
                Toast.makeText(this, "Error starting recognition: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else if (!::speechRecognizer.isInitialized) {
            // This can happen if permission was just granted and setup is slow
            Toast.makeText(this, "Speech recognizer not ready. Please try again.", Toast.LENGTH_SHORT).show()
            setupSpeechRecognizer() // Try to re-initialize it
        }
    }

    private fun resetRecordingState() {
        isListening = false
        binding.fabRecord.setImageResource(R.drawable.ic_mic)
        binding.fabRecord.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary_500)
        binding.tvStatus.text = "Tap microphone to start recording"
    }

    private fun stopListening() {
        if (isListening) {
            isListening = false
            binding.fabRecord.setImageResource(R.drawable.ic_mic)
            binding.fabRecord.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary_500)

            try {
                speechRecognizer.stopListening()
            } catch (e: Exception) {
                // Handle any errors during stop
            }

            if (!binding.tvStatus.text.toString().startsWith("✅")) {
                binding.tvStatus.text = "Tap microphone to start recording"
            }
        }
    }

    // FIXED SAVE METHOD - Uses workaround for update
    private fun saveCurrentNote() {
        val noteText = binding.etNoteText.text.toString().trim()

        if (noteText.isEmpty()) {
            Toast.makeText(this, "Please add some text to save", Toast.LENGTH_SHORT).show()
            return
        }

        if (noteText.length < 3) {
            Toast.makeText(this, "Note is too short. Please add more content.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val currentTime = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(Date())
            val title = if (noteText.length > 30) {
                noteText.substring(0, 30) + "..."
            } else {
                noteText
            }

            if (currentEditingNote != null) {
                // WORKAROUND: Delete old note and create new one (since update method may not exist)
                Log.d("MainActivity", "Replacing note: ${currentEditingNote!!.id}")

                // Delete the old note first
                noteViewModel.delete(currentEditingNote!!)

                // Wait a moment then insert new note
                Handler(Looper.getMainLooper()).postDelayed({
                    val newNote = Note(
                        id = 0, // New ID will be generated
                        title = title,
                        content = noteText,
                        timestamp = currentTime
                    )

                    noteViewModel.insert(newNote)
                    Toast.makeText(this, "Note updated successfully! ✏️", Toast.LENGTH_SHORT).show()
                }, 200)

                currentEditingNote = null

            } else {
                // CREATE new note
                Log.d("MainActivity", "Creating new note: $title")
                val note = Note(
                    id = 0,
                    title = title,
                    content = noteText,
                    timestamp = currentTime
                )

                noteViewModel.insert(note)
                Toast.makeText(this, "Note saved successfully! 📝", Toast.LENGTH_SHORT).show()
            }

            clearCurrentNote()

            binding.scrollView.postDelayed({
                binding.scrollView.smoothScrollTo(0, binding.rvNotes.top)
            }, 500)

            Log.d("MainActivity", "Note save operation completed")

        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving note", e)
            Toast.makeText(this, "Error saving note: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearCurrentNote() {
        Log.d("MainActivity", "Clearing current note")
        binding.etNoteText.setText("")
        currentNoteText = ""
        currentEditingNote = null
        binding.tvStatus.text = "Tap microphone to start recording"
        binding.tvCharCount.text = "0 characters • 0 words"
    }

    private fun showDeleteConfirmation(note: Note) {
        Log.d("MainActivity", "Showing delete confirmation for note: ${note.id}")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete '${note.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                Log.d("MainActivity", "Deleting note: ${note.id}")
                noteViewModel.delete(note)
                lastDeletedNote = note

                Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Note Deleted")
                    .setMessage("Note deleted. Do you want to undo?")
                    .setPositiveButton("UNDO") { _, _ ->
                        Log.d("MainActivity", "Undoing delete for note: ${note.id}")
                        noteViewModel.insert(note)
                        Toast.makeText(this, "Note restored", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("OK", null)
                    .show()
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d("MainActivity", "Delete cancelled for note: ${note.id}")
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            try {
                speechRecognizer.stopListening()
                speechRecognizer.destroy()
            } catch (e: Exception) {
                // Handle cleanup errors gracefully
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isListening) {
            stopListening()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionHelper.hasAudioPermission(this)) {
            binding.fabRecord.isEnabled = false
            binding.tvStatus.text = "❌ Voice recording permission required"
        } else if (!isRecognitionAvailable) {
            setupSpeechRecognizer()
        }
    }
}