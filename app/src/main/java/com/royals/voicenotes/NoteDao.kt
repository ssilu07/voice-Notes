package com.royals.voicenotes

// Fixed NoteDao.kt - Ensure proper ordering and data consistency

import android.app.Application
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.royals.voicenotes.databinding.ItemNoteBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun getAllNotes(): LiveData<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note): Int

    @Delete
    suspend fun delete(note: Note): Int

    @Query("DELETE FROM notes")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getNotesCount(): Int

    @Query("SELECT SUM(LENGTH(TRIM(content)) - LENGTH(REPLACE(TRIM(content), ' ', '')) + 1) FROM notes WHERE TRIM(content) != ''")
    suspend fun getTotalWordCount(): Int?

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Int): Note?
}

// ====================================

// Fixed NoteViewModel.kt - Better error handling and state management



// (Event.kt file ko alag se banana hoga - Code neeche hai)

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    val allNotes: LiveData<List<Note>>

    private val _notesCount = MutableLiveData<Int>()
    val notesCount: LiveData<Int> = _notesCount

    private val _totalWords = MutableLiveData<Int>()
    val totalWords: LiveData<Int> = _totalWords

    private val _operationStatus = MutableLiveData<String>()
    val operationStatus: LiveData<String> = _operationStatus

    // --- YEH NAYA ADD KAREIN (Fragments ke beech communication ke liye) ---
    private val _navigateToHome = MutableLiveData<Event<Unit>>()
    val navigateToHome: LiveData<Event<Unit>> = _navigateToHome

    private val _noteToEdit = MutableLiveData<Event<Note>>()
    val noteToEdit: LiveData<Event<Note>> = _noteToEdit

    private val _languageChanged = MutableLiveData<Event<String>>()
    val languageChanged: LiveData<Event<String>> = _languageChanged
    // --- YAHAN TAK ---

    init {
        val noteDao = NoteDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(noteDao)
        allNotes = repository.allNotes

        // Update statistics when notes change
        allNotes.observeForever {
            updateStatistics()
        }

        // Initial statistics update
        updateStatistics()
    }

    fun insert(note: Note) = viewModelScope.launch {
        try {
            val insertedId = withContext(Dispatchers.IO) {
                repository.insert(note)
            }
            Log.d("NoteViewModel", "Note inserted with ID: $insertedId")
            _operationStatus.postValue("Note saved successfully!")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error inserting note", e)
            _operationStatus.postValue("Error saving note: ${e.message}")
        }
    }

    fun update(note: Note) = viewModelScope.launch {
        try {
            val rowsUpdated = withContext(Dispatchers.IO) {
                repository.update(note)
            }
            Log.d("NoteViewModel", "Note updated: $rowsUpdated rows affected")
            _operationStatus.postValue("Note updated successfully!")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error updating note", e)
            _operationStatus.postValue("Error updating note: ${e.message}")
        }
    }

    fun delete(note: Note) = viewModelScope.launch {
        try {
            val rowsDeleted = withContext(Dispatchers.IO) {
                repository.delete(note)
            }
            Log.d("NoteViewModel", "Note deleted: $rowsDeleted rows affected")
            _operationStatus.postValue("Note deleted")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error deleting note", e)
            _operationStatus.postValue("Error deleting note: ${e.message}")
        }
    }

    fun deleteAll() = viewModelScope.launch {
        try {
            val rowsDeleted = withContext(Dispatchers.IO) {
                repository.deleteAll()
            }
            Log.d("NoteViewModel", "All notes deleted: $rowsDeleted rows")
            _operationStatus.postValue("All notes deleted")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error deleting all notes", e)
            _operationStatus.postValue("Error deleting all notes: ${e.message}")
        }
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

    // --- YEH NAYE FUNCTIONS ADD KAREIN ---
    /**
     * SavedNotesFragment se call hoga jab edit button click hoga
     */
    fun onEditNoteClicked(note: Note) {
        _noteToEdit.value = Event(note)
        _navigateToHome.value = Event(Unit) // HomeFragment pe navigate karne ka signal
    }

    /**
     * Language change ko HomeFragment tak pahunchane ke liye
     */
    fun onLanguageSelected(languageCode: String) {
        _languageChanged.value = Event(languageCode)
    }
    // --- YAHAN TAK ---
}

// ====================================

// Fixed NoteRepository.kt - Better return types and error handling


class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: LiveData<List<Note>> = noteDao.getAllNotes()

    suspend fun insert(note: Note): Long {
        return noteDao.insert(note)
    }

    suspend fun update(note: Note): Int {
        return noteDao.update(note)
    }

    suspend fun delete(note: Note): Int {
        return noteDao.delete(note)
    }

    suspend fun deleteAll(): Int {
        return noteDao.deleteAll()
    }

    suspend fun getNotesCount(): Int {
        return noteDao.getNotesCount()
    }

    suspend fun getTotalWordCount(): Int {
        return noteDao.getTotalWordCount() ?: 0
    }

    suspend fun getNoteById(noteId: Int): Note? {
        return noteDao.getNoteById(noteId)
    }
}

// ====================================

// Fixed NotesAdapter.kt - Better data handling and animations


class NotesAdapter(
    private val onDeleteClick: (Note) -> Unit,
    private val onEditClick: (Note) -> Unit,
    private val onShareClick: (Note) -> Unit,
    private val onExportClick: (Note) -> Unit
) : ListAdapter<Note, NotesAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)
        if (note != null) {
            holder.bind(note)
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<Note>, currentList: MutableList<Note>) {
        super.onCurrentListChanged(previousList, currentList)
        Log.d("NotesAdapter", "List changed: Previous size=${previousList.size}, Current size=${currentList.size}")

        // Debug: Print current list contents
        currentList.forEachIndexed { index, note ->
            Log.d("NotesAdapter", "Note $index: ID=${note.id}, Title=${note.title}")
        }
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            try {
                binding.apply {
                    tvNoteTitle.text = note.title
                    tvNoteContent.text = note.content
                    tvNoteTimestamp.text = note.timestamp

                    // Calculate word count safely
                    val wordCount = if (note.content.isBlank()) {
                        0
                    } else {
                        note.content.trim().split("\\s+".toRegex()).size
                    }
                    tvWordCount.text = "$wordCount words"

                    // Set click listeners
                    btnDelete.setOnClickListener {
                        Log.d("NotesAdapter", "Delete clicked for note: ${note.id}")
                        onDeleteClick(note)
                    }

                    btnEdit.setOnClickListener {
                        Log.d("NotesAdapter", "Edit clicked for note: ${note.id}")
                        onEditClick(note)
                    }

                    btnShare.setOnClickListener {
                        Log.d("NotesAdapter", "Share clicked for note: ${note.id}")
                        onShareClick(note)
                    }

                    btnExport.setOnClickListener {
                        Log.d("NotesAdapter", "Export clicked for note: ${note.id}")
                        onExportClick(note)
                    }

                    // Click on entire item to edit
                    root.setOnClickListener {
                        Log.d("NotesAdapter", "Item clicked for note: ${note.id}")
                        onEditClick(note)
                    }

                    // Add subtle animation
                    root.alpha = 0f
                    root.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
            } catch (e: Exception) {
                Log.e("NotesAdapter", "Error binding note: ${note.id}", e)
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            val result = oldItem.id == newItem.id
            Log.d("NoteDiffCallback", "areItemsTheSame: ${oldItem.id} == ${newItem.id} = $result")
            return result
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            val result = oldItem == newItem
            Log.d("NoteDiffCallback", "areContentsTheSame: $result")
            return result
        }
    }
}