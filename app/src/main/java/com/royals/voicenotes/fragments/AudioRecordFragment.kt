package com.royals.voicenotes.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.royals.voicenotes.Constants
import com.royals.voicenotes.Note
import com.royals.voicenotes.NoteViewModel
import com.royals.voicenotes.R
import com.royals.voicenotes.databinding.FragmentAudioRecordBinding
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AudioRecordFragment : Fragment() {

    private val TAG = "AudioRecordFragment"

    private var _binding: FragmentAudioRecordBinding? = null
    private val binding get() = _binding!!

    private val noteViewModel: NoteViewModel by activityViewModels()

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFilePath: String? = null

    private var isRecording = false
    private var isPlaying = false
    private var isTransitioning = false  // Guard against rapid button clicks
    private var recordingStartTime: Long = 0
    private var recordingDuration: Long = 0

    private val handler = Handler(Looper.getMainLooper())
    private val updateTimerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && _binding != null) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                updateTimerDisplay(elapsed)
                handler.postDelayed(this, Constants.TIMER_UPDATE_INTERVAL_MS)
            }
        }
    }

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i(TAG, "Audio permission granted")
            startRecording()
        } else {
            Log.e(TAG, "Audio permission denied")
            Toast.makeText(requireContext(), getString(R.string.recording_permission_required), Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        checkPermissionAndStartRecording()
    }

    private fun setupClickListeners() {
        binding.btnStopRecording.setOnClickListener {
            if (isRecording && !isTransitioning) {
                stopRecording()
            }
        }

        binding.btnPlayPause.setOnClickListener {
            if (isTransitioning) return@setOnClickListener
            if (isPlaying) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }

        binding.btnSaveRecording.setOnClickListener {
            if (!isTransitioning) saveRecording()
        }

        binding.btnDiscardRecording.setOnClickListener {
            if (!isTransitioning) showDiscardConfirmation()
        }

        binding.btnClose.setOnClickListener {
            if (isRecording) {
                Toast.makeText(requireContext(), getString(R.string.stop_recording_first), Toast.LENGTH_SHORT).show()
            } else {
                findNavController().navigateUp()
            }
        }
    }

    private fun checkPermissionAndStartRecording() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startRecording()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecording() {
        if (isRecording || isTransitioning) return
        isTransitioning = true
        try {
            // Create file path
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "voice_recording_$timestamp.m4a"
            val audioDir = File(requireContext().getExternalFilesDir(null), "VoiceRecordings")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            audioFilePath = File(audioDir, fileName).absolutePath

            // Setup MediaRecorder
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = System.currentTimeMillis()

            // Update UI
            binding.tvStatus.text = "🔴 Recording..."
            binding.btnStopRecording.isEnabled = true
            binding.btnPlayPause.isEnabled = false
            binding.btnSaveRecording.isEnabled = false
            binding.recordingIndicator.visibility = View.VISIBLE
            binding.playbackControls.visibility = View.GONE

            // Start timer
            handler.post(updateTimerRunnable)
            animateRecordingIndicator()

            Log.i(TAG, "Recording started: $audioFilePath")

        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
            Toast.makeText(requireContext(), getString(R.string.recording_start_failed), Toast.LENGTH_SHORT).show()
        } finally {
            isTransitioning = false
        }
    }

    private fun stopRecording() {
        if (!isRecording || isTransitioning) return
        isTransitioning = true
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recorder", e)
                } finally {
                    release()
                }
            }
            mediaRecorder = null
            isRecording = false
            recordingDuration = System.currentTimeMillis() - recordingStartTime

            handler.removeCallbacks(updateTimerRunnable)

            binding.tvStatus.text = "✅ Recording completed"
            binding.btnStopRecording.isEnabled = false
            binding.btnPlayPause.isEnabled = true
            binding.btnSaveRecording.isEnabled = true
            binding.recordingIndicator.visibility = View.GONE
            binding.playbackControls.visibility = View.VISIBLE
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)

            Log.i(TAG, "Recording stopped. Duration: ${recordingDuration}ms")
            Toast.makeText(requireContext(), getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            Toast.makeText(requireContext(), getString(R.string.recording_stop_error), Toast.LENGTH_SHORT).show()
        } finally {
            isTransitioning = false
        }
    }

    private fun startPlayback() {
        try {
            if (audioFilePath != null && File(audioFilePath!!).exists()) {
                // Release existing player before creating new one
                releaseMediaPlayer()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioFilePath)
                    prepare()
                    start()
                }

                isPlaying = true
                binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
                binding.tvStatus.text = "▶️ Playing recording..."

                mediaPlayer?.setOnCompletionListener {
                    isPlaying = false
                    if (_binding != null) {
                        binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                        binding.tvStatus.text = "✅ Recording completed"
                    }
                }

                Log.i(TAG, "Playback started")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start playback", e)
            Toast.makeText(requireContext(), getString(R.string.playback_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun pausePlayback() {
        try {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
                binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                binding.tvStatus.text = "⏸️ Playback paused"
                Log.i(TAG, "Playback paused")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause playback", e)
        }
    }

    private fun saveRecording() {
        if (audioFilePath != null && File(audioFilePath!!).exists()) {
            val timestamp = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(
                Date()
            )
            val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val title = "Voice Recording $timeOnly"
            val fileName = File(audioFilePath!!).name
            val durationFormatted = formatDuration(recordingDuration)

            val content = "🎤 Audio recording\n📁 File: $fileName\n⏱️ Duration: $durationFormatted"

            // UPDATED: Save with audio type and file path
            val note = Note(
                id = 0,
                title = title,
                content = content,
                timestamp = timestamp,
                noteType = Note.Companion.TYPE_AUDIO,
                audioFilePath = audioFilePath
            )

            noteViewModel.insert(note)
            Toast.makeText(requireContext(), getString(R.string.recording_saved_success), Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        } else {
            Toast.makeText(requireContext(), getString(R.string.no_recording_to_save), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDiscardConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.discard_recording_title))
            .setMessage(getString(R.string.discard_recording_message))
            .setPositiveButton(getString(R.string.action_discard)) { _, _ ->
                discardRecording()
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun discardRecording() {
        // Delete file
        if (audioFilePath != null) {
            val file = File(audioFilePath!!)
            if (file.exists() && !file.delete()) {
                android.util.Log.e("AudioRecordFragment", "Failed to delete recording: $audioFilePath")
                Toast.makeText(requireContext(), getString(R.string.recording_delete_failed), Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(requireContext(), getString(R.string.recording_discarded), Toast.LENGTH_SHORT).show()
        findNavController().navigateUp()
    }

    private fun updateTimerDisplay(millis: Long) {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))

        val timeString = if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }

        binding.tvTimer.text = timeString
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun animateRecordingIndicator() {
        if (_binding == null) return
        binding.recordingIndicator.animate()
            .alpha(0.3f)
            .setDuration(Constants.RECORDING_ANIMATION_DURATION_MS)
            .withEndAction {
                if (isRecording && _binding != null) {
                    binding.recordingIndicator.animate()
                        .alpha(1f)
                        .setDuration(Constants.RECORDING_ANIMATION_DURATION_MS)
                        .withEndAction {
                            if (isRecording && _binding != null) {
                                animateRecordingIndicator()
                            }
                        }
                        .start()
                }
            }
            .start()
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.apply {
                try {
                    if (isPlaying) stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping player", e)
                } finally {
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing player", e)
        }
        mediaPlayer = null
        isPlaying = false
    }

    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.apply {
                try {
                    if (isRecording) stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recorder", e)
                } finally {
                    release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recorder", e)
        }
        mediaRecorder = null
        isRecording = false
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            pausePlayback()
        }
        // Stop recording when fragment goes to background (e.g. home button)
        if (isRecording) {
            stopRecording()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)  // Clear ALL pending callbacks
        releaseMediaPlayer()
        releaseMediaRecorder()
        _binding = null
    }
}