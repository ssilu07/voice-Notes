package com.royals.voicenotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.royals.voicenotes.databinding.FragmentHomeBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    // --- LOG TAG ---
    private val TAG = "HomeFragmentSpeech"

    private var _binding: FragmentHomeBinding? = null
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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Fragment view is created.")

        setupClickListeners()
        checkPermissionAndSetup()
        setupTextWatcher()
        observeViewModel()
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
        Log.d("HomeFragment", "Editing note: ${note.id}")
        binding.etNoteText.setText(note.content)
        currentNoteText = note.content // TextWatcher ise update kar dega
        currentEditingNote = note
        binding.scrollView.smoothScrollTo(0, 0)
        Toast.makeText(requireContext(), "Note loaded for editing.", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), "Speech recognition not available", Toast.LENGTH_LONG).show()
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
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Clear Note")
                    .setMessage("Are you sure you want to clear the current note?")
                    .setPositiveButton("Clear") { _, _ -> clearCurrentNote() }
                    .setNegativeButton("Cancel", null)
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


                    // Check karein ki kya UI par pehle se hi success message hai
                    val isAlreadySuccessful = binding.tvStatus.text.toString().startsWith("✅")

                    // Agar error "Code 5" hai AUR hum pehle hi successful ho chuke hain,
                    // toh UI par error mat dikhao.
                    if (error == SpeechRecognizer.ERROR_CLIENT && isAlreadySuccessful) {
                        Log.w(TAG, "[Listener] Ignored UI update for Error 5, as success was already reported.")
                    } else {
                        // Baaki sabhi errors (No match, Network, etc.) ko UI par dikhao
                        binding.tvStatus.text = "❌ $errorMessage"
                    }
                    stopListening()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                        Log.i(TAG, "[Listener] onResults: SUCCESS! Text: $recognizedText")

                        // --- FIX ---
                        // 'stableText' (jo pehle save kiya tha) + naya 'recognizedText'
                        val finalText = if (stableText.isEmpty()) recognizedText else "$stableText $recognizedText"

                        binding.etNoteText.setText(finalText)
                        binding.etNoteText.setSelection(finalText.length)
                        // TextWatcher ab 'currentNoteText' ko 'finalText' par set kar dega.
                        // ---

                        binding.tvStatus.text = "✅ Speech converted successfully!"
                        binding.fabRecord.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
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
                // ---

                isListening = true
                binding.fabRecord.setImageResource(R.drawable.ic_stop)
                binding.fabRecord.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.red_500)
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
            try {
                speechRecognizer.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "stopListening: FAILED to stop.", e)
            }
            if (!binding.tvStatus.text.toString().startsWith("✅")) {
                binding.tvStatus.text = "Tap microphone to start recording"
            }
        }
    }

    private fun resetRecordingState() {
        isListening = false
        binding.fabRecord.setImageResource(R.drawable.ic_mic)
        binding.fabRecord.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary_500)
    }

    private fun animateRecordingButton() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isListening) {
                binding.fabRecord.animate().scaleX(1.2f).scaleY(1.2f).setDuration(500).withEndAction {
                    if (isListening) {
                        binding.fabRecord.animate().scaleX(1.0f).scaleY(1.0f).setDuration(500).start()
                    }
                }.start()
                delay(1000)
            }
        }
    }

    private fun saveCurrentNote() {
        // Ab 'currentNoteText' hamesha up-to-date rahega 'TextWatcher' ki wajah se
        val noteText = currentNoteText.trim()

        if (noteText.isEmpty() || noteText.length < 3) {
            Toast.makeText(requireContext(), "Note is too short", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val currentTime = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(Date())
            val title = if (noteText.length > 30) noteText.substring(0, 30) + "..." else noteText

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
            Log.e("HomeFragment", "Error saving note", e)
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment view destroyed. Cleaning up speech recognizer.")
        if (::speechRecognizer.isInitialized) {
            try {
                speechRecognizer.stopListening()
                speechRecognizer.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "onDestroyView: Error destroying speech recognizer.", e)
            }
        }
        _binding = null
    }
}