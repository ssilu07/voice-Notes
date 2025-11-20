package com.royals.voicenotes

import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.widget.SeekBar
import com.royals.voicenotes.databinding.DialogAudioPlayerBinding
import java.io.File
import java.io.IOException

class AudioPlayerDialog(
    context: Context,
    private val note: Note,
    private val onDeleteClick: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogAudioPlayerBinding
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val currentPosition = player.currentPosition
                    val duration = player.duration

                    binding.seekBar.progress = currentPosition
                    binding.tvCurrentTime.text = formatTime(currentPosition)

                    handler.postDelayed(this, 100)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupMediaPlayer()
        setupClickListeners()
    }

    private fun setupUI() {
        binding.apply {
            tvNoteTitle.text = note.title
            tvTimestamp.text = note.timestamp

            // Parse duration from content if available
            val durationMatch = Regex("Duration: (\\d{2}:\\d{2})").find(note.content)
            val duration = durationMatch?.groupValues?.get(1) ?: "00:00"
            tvDuration.text = duration
        }
    }

    private fun setupMediaPlayer() {
        val audioPath = note.audioFilePath
        if (audioPath.isNullOrEmpty() || !File(audioPath).exists()) {
            binding.tvStatus.text = "❌ Audio file not found"
            binding.btnPlayPause.isEnabled = false
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()

                val duration = this.duration
                binding.seekBar.max = duration
                binding.tvDuration.text = formatTime(duration)

                setOnCompletionListener {
                    this@AudioPlayerDialog.isPlaying = false
                    binding.btnPlayPause.setImageResource(R.drawable.ic_play)
                    binding.seekBar.progress = 0
                    binding.tvCurrentTime.text = "00:00"
                    handler.removeCallbacks(updateSeekBarRunnable)
                }
            }

            binding.tvStatus.text = "Ready to play"

        } catch (e: IOException) {
            binding.tvStatus.text = "❌ Error loading audio"
            binding.btnPlayPause.isEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnPlayPause.setOnClickListener {
            if (isPlaying) {
                pausePlayback()
            } else {
                startPlayback()
            }
        }

        binding.btnRewind.setOnClickListener {
            mediaPlayer?.let { player ->
                val newPosition = (player.currentPosition - 10000).coerceAtLeast(0)
                player.seekTo(newPosition)
                binding.seekBar.progress = newPosition
            }
        }

        binding.btnFastForward.setOnClickListener {
            mediaPlayer?.let { player ->
                val newPosition = (player.currentPosition + 10000).coerceAtMost(player.duration)
                player.seekTo(newPosition)
                binding.seekBar.progress = newPosition
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateSeekBarRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                mediaPlayer?.seekTo(seekBar?.progress ?: 0)
                if (isPlaying) {
                    handler.post(updateSeekBarRunnable)
                }
            }
        })

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }

        binding.btnShare.setOnClickListener {
            FileHelper.shareNote(context, note)
        }
    }

    private fun startPlayback() {
        try {
            mediaPlayer?.start()
            isPlaying = true
            binding.btnPlayPause.setImageResource(R.drawable.ic_pause)
            binding.tvStatus.text = "▶️ Playing..."
            handler.post(updateSeekBarRunnable)
        } catch (e: Exception) {
            binding.tvStatus.text = "Error playing audio"
        }
    }

    private fun pausePlayback() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
            binding.btnPlayPause.setImageResource(R.drawable.ic_play)
            binding.tvStatus.text = "⏸️ Paused"
            handler.removeCallbacks(updateSeekBarRunnable)
        } catch (e: Exception) {
            binding.tvStatus.text = "Error pausing audio"
        }
    }

    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Delete Recording")
            .setMessage("Are you sure you want to delete this audio recording?")
            .setPositiveButton("Delete") { _, _ ->
                // Delete audio file
                note.audioFilePath?.let { path ->
                    File(path).delete()
                }
                onDeleteClick()
                dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun dismiss() {
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        super.dismiss()
    }
}