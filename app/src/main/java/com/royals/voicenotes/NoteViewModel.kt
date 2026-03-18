package com.royals.voicenotes

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    val allNotes: LiveData<List<Note>> = repository.allNotes
    val archivedNotes: LiveData<List<Note>> = repository.getArchivedNotes()

    private val _notesCount = MutableLiveData<Int>()
    val notesCount: LiveData<Int> = _notesCount

    private val _totalWords = MutableLiveData<Int>()
    val totalWords: LiveData<Int> = _totalWords

    private val _operationStatus = MutableLiveData<String>()
    val operationStatus: LiveData<String> = _operationStatus

    private val _navigateToHome = MutableLiveData<Event<Unit>>()
    val navigateToHome: LiveData<Event<Unit>> = _navigateToHome

    private val _noteToEdit = MutableLiveData<Event<Note>>()
    val noteToEdit: LiveData<Event<Note>> = _noteToEdit

    private val _languageChanged = MutableLiveData<Event<String>>()
    val languageChanged: LiveData<Event<String>> = _languageChanged

    // Observer reference to remove in onCleared (fixes memory leak)
    private val notesObserver = Observer<List<Note>> { updateStatistics() }

    init {
        allNotes.observeForever(notesObserver)
        updateStatistics()
    }

    override fun onCleared() {
        super.onCleared()
        allNotes.removeObserver(notesObserver)
    }

    fun searchNotes(query: String): LiveData<List<Note>> = repository.searchNotes(query)

    fun getNotesByCategory(category: String): LiveData<List<Note>> = repository.getNotesByCategory(category)

    fun insert(note: Note) = viewModelScope.launch {
        try {
            val insertedId = withContext(Dispatchers.IO) { repository.insert(note) }
            Log.d("NoteViewModel", "Note inserted with ID: $insertedId")
            _operationStatus.postValue("Note saved successfully!")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error inserting note", e)
            _operationStatus.postValue("Error saving note: ${e.message}")
        }
    }

    fun update(note: Note) = viewModelScope.launch {
        try {
            val rowsUpdated = withContext(Dispatchers.IO) { repository.update(note) }
            Log.d("NoteViewModel", "Note updated: $rowsUpdated rows affected")
            _operationStatus.postValue("Note updated successfully!")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error updating note", e)
            _operationStatus.postValue("Error updating note: ${e.message}")
        }
    }

    fun delete(note: Note) = viewModelScope.launch {
        try {
            val rowsDeleted = withContext(Dispatchers.IO) { repository.delete(note) }
            Log.d("NoteViewModel", "Note deleted: $rowsDeleted rows affected")
            _operationStatus.postValue("Note deleted")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error deleting note", e)
            _operationStatus.postValue("Error deleting note: ${e.message}")
        }
    }

    fun deleteAll() = viewModelScope.launch {
        try {
            // Clean up audio files before deleting all notes
            val notes = allNotes.value ?: emptyList()
            withContext(Dispatchers.IO) {
                notes.forEach { note -> NoteUtils.deleteAudioFile(note, "NoteViewModel") }
                repository.deleteAll()
            }
            Log.d("NoteViewModel", "All notes and associated audio files deleted")
            _operationStatus.postValue("All notes deleted")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error deleting all notes", e)
            _operationStatus.postValue("Error deleting all notes: ${e.message}")
        }
    }

    fun togglePin(note: Note) = viewModelScope.launch {
        try {
            val newPinned = !note.isPinned
            withContext(Dispatchers.IO) {
                repository.updatePinStatus(note.id, newPinned)
            }
            _operationStatus.postValue(if (newPinned) "Note pinned" else "Note unpinned")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error toggling pin", e)
        }
    }

    fun toggleArchive(note: Note) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                repository.updateArchiveStatus(note.id, !note.isArchived)
            }
            val status = if (!note.isArchived) "Note archived" else "Note unarchived"
            _operationStatus.postValue(status)
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error toggling archive", e)
        }
    }

    fun setReminder(noteId: Int, reminderTime: Long?) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                repository.updateReminderTime(noteId, reminderTime)
            }
            val status = if (reminderTime != null) "Reminder set" else "Reminder removed"
            _operationStatus.postValue(status)
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error setting reminder", e)
        }
    }

    suspend fun getNoteById(noteId: Int): Note? = withContext(Dispatchers.IO) {
        repository.getNoteById(noteId)
    }

    private fun updateStatistics() = viewModelScope.launch {
        try {
            val count = withContext(Dispatchers.IO) { repository.getNotesCount() }
            val words = withContext(Dispatchers.IO) { repository.getTotalWordCount() }
            _notesCount.postValue(count)
            _totalWords.postValue(words)
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error updating statistics", e)
            _notesCount.postValue(0)
            _totalWords.postValue(0)
        }
    }

    fun clearOperationStatus() {
        _operationStatus.value = ""
    }

    fun onEditNoteClicked(note: Note) {
        _noteToEdit.value = Event(note)
        _navigateToHome.value = Event(Unit)
    }

    fun onLanguageSelected(languageCode: String) {
        _languageChanged.value = Event(languageCode)
    }
}
