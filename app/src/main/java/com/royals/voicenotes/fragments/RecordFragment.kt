package com.royals.voicenotes.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.royals.voicenotes.Constants
import com.royals.voicenotes.MainActivity
import com.royals.voicenotes.Note
import com.royals.voicenotes.NoteViewModel
import com.royals.voicenotes.R
import com.royals.voicenotes.SpeechHelper
import com.royals.voicenotes.databinding.FragmentRecordBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class RecordFragment : Fragment() {

    // --- LOG TAG ---
    private val TAG = "RecordFragmentSpeech"

    private var _binding: FragmentRecordBinding? = null
    private val binding get() = _binding!!

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent

    private val noteViewModel: NoteViewModel by activityViewModels()

    private var isListening = false
    private var currentNoteText = ""
    private var isRecognitionAvailable = false
    private var currentLanguage = "en-US"
    private var currentEditingNote: Note? = null

    // --- YEH NAYA VARIABLE ADD KAREIN ---
    /**
     * Recording shuru hone se pehle ka text store karta hai.
     * Yeh feedback loop ko rokega.
     */
    private var stableText = ""
    private var hasReceivedResults = false  // Tracks if onResults was called in current session
    // ---

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i(TAG, "Permission GRANTED by user.")
            setupSpeechRecognizer()
        } else {
            Log.e(TAG, "Permission DENIED by user.")
            (activity as? MainActivity)?.showPermissionDeniedDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Fragment view is created.")

        // Restore state after rotation
        savedInstanceState?.let { state ->
            currentNoteText = state.getString(KEY_NOTE_TEXT, "")
            currentLanguage = state.getString(KEY_LANGUAGE, "en-US")
            stableText = state.getString(KEY_STABLE_TEXT, "")
            binding.etNoteText.setText(currentNoteText)
            if (currentNoteText.isNotEmpty()) {
                binding.etNoteText.setSelection(currentNoteText.length)
            }
        }

        setupClickListeners()
        checkPermissionAndSetup()
        setupTextWatcher()
        observeViewModel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_NOTE_TEXT, currentNoteText)
        outState.putString(KEY_LANGUAGE, currentLanguage)
        outState.putString(KEY_STABLE_TEXT, stableText)
    }

    companion object {
        private const val KEY_NOTE_TEXT = "key_note_text"
        private const val KEY_LANGUAGE = "key_language"
        private const val KEY_STABLE_TEXT = "key_stable_text"
    }

    private fun observeViewModel() {
        // Jab SavedNotesFragment se 'Edit' click ho
        noteViewModel.noteToEdit.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { note ->
                loadNoteForEditing(note)
            }
        }

        // Jab MainActivity se language change ho
        noteViewModel.languageChanged.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { langCode ->
                updateSpeechRecognizerLanguage(langCode)
            }
        }
    }

    private fun loadNoteForEditing(note: Note) {
        Log.d("RecordFragment", "Editing note: ${note.id}")
        binding.etNoteText.setText(note.content)
        currentNoteText = note.content // TextWatcher ise update kar dega
        currentEditingNote = note
        binding.scrollView.smoothScrollTo(0, 0)
        Toast.makeText(requireContext(), getString(R.string.note_loaded_editing), Toast.LENGTH_SHORT).show()
    }

    private fun setupTextWatcher() {
        binding.etNoteText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""

                // YEH AB MANUAL TYPING AUR FINAL RESULTS KO TRACK KAREGA
                currentNoteText = text

                val charCount = text.length
                val wordCount = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size
                binding.tvCharCount.text = "$charCount characters • $wordCount words"
            }
        })
    }

    private fun setupClickListeners() {
        binding.fabRecord.setOnClickListener {
            Log.d(TAG, "FAB Record Clicked.")
            if (!isRecognitionAvailable) {
                Log.e(TAG, "FAB Clicked, but recognition is NOT available.")
                Toast.makeText(requireContext(), getString(R.string.speech_not_available_status), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (isListening) {
                Log.d(TAG, "Was listening, now stopping.")
                stopListening()
            } else {
                Log.d(TAG, "Was not listening, now starting.")
                startListening()
            }
        }

        binding.btnSave.setOnClickListener { saveCurrentNote() }

        binding.btnClear.setOnClickListener {
            if (currentNoteText.isNotEmpty() || binding.etNoteText.text.toString().isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.clear_note_title))
                    .setMessage(getString(R.string.clear_note_message))
                    .setPositiveButton(getString(R.string.clear_text)) { _, _ -> clearCurrentNote() }
                    .setNegativeButton(getString(R.string.action_cancel), null)
                    .show()
            }
        }
    }

    private fun checkPermissionAndSetup() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i(TAG, "checkPermissionAndSetup: Permission already granted.")
                setupSpeechRecognizer()
            }
            else -> {
                Log.w(TAG, "checkPermissionAndSetup: Permission NOT granted. Requesting...")
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun updateSpeechRecognizerLanguage(language: String) {
        Log.i(TAG, "updateSpeechRecognizerLanguage: Language changed to $language")
        currentLanguage = language
        binding.tvLanguage.text = language.substring(0, 2).uppercase()
        // Re-setup speech recognizer with new language
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        Log.d(TAG, "setupSpeechRecognizer: Setting up...")
        try {
            if (::speechRecognizer.isInitialized) {
                if (isListening) {
                    isListening = false
                    try { speechRecognizer.cancel() } catch (_: Exception) {}
                }
                speechRecognizer.destroy()
            }
            isRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(requireContext())
            Log.i(TAG, "setupSpeechRecognizer: Is recognition available on device? -> $isRecognitionAvailable")

            if (!isRecognitionAvailable) {
                binding.fabRecord.isEnabled = false
                binding.tvStatus.text = "❌ Speech recognition not available"
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizerIntent = SpeechHelper.createSpeechRecognizerIntent(currentLanguage)
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.i(TAG, "[Listener] onReadyForSpeech: Ready to listen.")
                    binding.tvStatus.text = "🎤 Listening... Speak now!"
                    animateRecordingButton()
                }
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "[Listener] onBeginningOfSpeech: User started speaking.")
                    binding.tvStatus.text = "🔴 Recording..."
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d(TAG, "[Listener] onEndOfSpeech: User stopped speaking.")
                    binding.tvStatus.text = "🔄 Processing speech..."
                }
                override fun onError(error: Int) {
                    val errorMessage = SpeechHelper.getErrorMessage(error)
                    Log.e(TAG, "[Listener] onError: Code $error - Message: $errorMessage")

                    // ERROR_CLIENT after successful results is a known Android quirk
                    // where stopListening() triggers a spurious error callback.
                    // Use state flag instead of UI text to detect this reliably.
                    if (error == SpeechRecognizer.ERROR_CLIENT && hasReceivedResults) {
                        Log.w(TAG, "[Listener] Ignored spurious ERROR_CLIENT after successful recognition.")
                    } else {
                        if (_binding != null) {
                            binding.tvStatus.text = "❌ $errorMessage"
                        }
                        Log.e(TAG, "[Listener] Showing error to user: $errorMessage")
                    }
                    stopListening()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                        hasReceivedResults = true
                        Log.i(TAG, "[Listener] onResults: SUCCESS! Text: $recognizedText")

                        // --- FIX ---
                        // 'stableText' (jo pehle save kiya tha) + naya 'recognizedText'
                        val finalText = if (stableText.isEmpty()) recognizedText else "$stableText $recognizedText"

                        binding.etNoteText.setText(finalText)
                        binding.etNoteText.setSelection(finalText.length)
                        // TextWatcher ab 'currentNoteText' ko 'finalText' par set kar dega.
                        // ---

                        binding.tvStatus.text = "✅ Speech converted successfully!"
                        binding.fabRecord.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    } else {
                        Log.w(TAG, "[Listener] onResults: Received null or empty results.")
                    }
                    stopListening()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val partialText = matches[0]
                        Log.d(TAG, "[Listener] onPartialResults: $partialText")

                        // --- FIX ---
                        // 'stableText' (jo pehle save kiya tha) + naya 'partialText'
                        val displayText = if (stableText.isEmpty()) partialText else "$stableText $partialText"

                        binding.etNoteText.setText(displayText)
                        binding.etNoteText.setSelection(displayText.length)
                        // TextWatcher 'currentNoteText' ko update karega,
                        // lekin 'onPartialResults' ab 'currentNoteText' ko nahi padhta,
                        // isliye loop break ho gaya hai.
                        // ---
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {
                    Log.d(TAG, "[Listener] onEvent: $eventType")
                }
            })
            Log.i(TAG, "setupSpeechRecognizer: Setup COMPLETE.")
        } catch (e: Exception) {
            Log.e(TAG, "setupSpeechRecognizer: FAILED during setup.", e)
            binding.tvStatus.text = "❌ Failed to setup speech recognition"
        }
    }

    private fun startListening() {
        Log.d(TAG, "startListening: Attempting to start...")
        if (!isListening && isRecognitionAvailable && ::speechRecognizer.isInitialized) {
            try {
                // --- FIX ---
                // Recording shuru karne se pehle current text ko 'stableText' mein save karein
                stableText = binding.etNoteText.text.toString()
                hasReceivedResults = false
                // ---

                isListening = true
                binding.fabRecord.setImageResource(R.drawable.ic_stop)
              //  binding.fabRecord.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.red_500)
                binding.tvStatus.text = "🔄 Initializing..."

                Log.i(TAG, "startListening: Calling speechRecognizer.startListening()")
                speechRecognizer.startListening(speechRecognizerIntent)

            } catch (e: Exception) {
                Log.e(TAG, "startListening: FAILED to start listening.", e)
                resetRecordingState()
            }
        } else {
            Log.w(TAG, "startListening: CANNOT START. Conditions not met.")
            Log.w(TAG, "isListening=$isListening, isAvailable=$isRecognitionAvailable, isInitialized=${::speechRecognizer.isInitialized}")
        }
    }

    private fun stopListening() {
        if (isListening) {
            Log.d(TAG, "stopListening: Stopping...")
            isListening = false
            resetRecordingState()
            if (::speechRecognizer.isInitialized) {
                try {
                    speechRecognizer.stopListening()
                } catch (e: Exception) {
                    Log.e(TAG, "stopListening: FAILED to stop.", e)
                }
            }
            if (_binding != null && !binding.tvStatus.text.toString().startsWith("✅")) {
                binding.tvStatus.text = "Tap microphone to start recording"
            }
        }
    }

    private fun resetRecordingState() {
        isListening = false
        binding.fabRecord.setImageResource(R.drawable.ic_mic)
       // binding.fabRecord.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary_500)
    }

    private fun animateRecordingButton() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isListening) {
                binding.fabRecord.animate().scaleX(1.2f).scaleY(1.2f).setDuration(Constants.RECORDING_ANIMATION_DURATION_MS).withEndAction {
                    if (isListening) {
                        binding.fabRecord.animate().scaleX(1.0f).scaleY(1.0f).setDuration(Constants.RECORDING_ANIMATION_DURATION_MS).start()
                    }
                }.start()
                delay(1000)
            }
        }
    }

    private fun saveCurrentNote() {
        // Ab 'currentNoteText' hamesha up-to-date rahega 'TextWatcher' ki wajah se
        val noteText = currentNoteText.trim()

        if (noteText.isEmpty() || noteText.length < Constants.MIN_NOTE_LENGTH) {
            Toast.makeText(requireContext(), getString(R.string.note_too_short), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val currentTime = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(
                Date()
            )
            val title = if (noteText.length > Constants.MAX_TITLE_LENGTH) noteText.substring(0, Constants.MAX_TITLE_LENGTH) + "..." else noteText

            if (currentEditingNote != null) {
                // UPDATE existing note
                val updatedNote = currentEditingNote!!.copy(
                    title = title,
                    content = noteText,
                    timestamp = currentTime
                )
                noteViewModel.update(updatedNote)
                currentEditingNote = null
            } else {
                // CREATE new note
                val note = Note(id = 0, title = title, content = noteText, timestamp = currentTime)
                noteViewModel.insert(note)
            }
            clearCurrentNote()
        } catch (e: Exception) {
            Log.e("RecordFragment", "Error saving note", e)
            Toast.makeText(requireContext(), "Error saving note: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearCurrentNote() {
        binding.etNoteText.setText("")
        // currentNoteText 'TextWatcher' se automatically "" set ho jayega
        currentEditingNote = null
        stableText = "" // stableText ko bhi clear karein
        binding.tvStatus.text = "Tap microphone to start recording"
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Fragment paused. Stopping listening if active.")
        if (isListening) {
            stopListening()
        }
        // Cancel any pending recognition to free microphone resource
        if (::speechRecognizer.isInitialized) {
            try { speechRecognizer.cancel() } catch (_: Exception) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment view destroyed. Cleaning up speech recognizer.")
        isListening = false
        isRecognitionAvailable = false
        if (::speechRecognizer.isInitialized) {
            try {
                speechRecognizer.cancel()
                speechRecognizer.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "onDestroyView: Error destroying speech recognizer.", e)
            }
        }
        _binding = null
    }
}