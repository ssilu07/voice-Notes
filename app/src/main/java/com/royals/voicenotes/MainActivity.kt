package com.royals.voicenotes

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.royals.voicenotes.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // ViewModel ko yahan initialize karein
    private val noteViewModel: NoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        setupNavigation()
        observeViewModel()
    }

    private fun setupActionBar() {
        supportActionBar?.title = "Voice Notes"
        supportActionBar?.elevation = 4f
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Bottom Nav ko NavController se jodein
        binding.bottomNavigationView.setupWithNavController(navController)
    }

    private fun observeViewModel() {
        // Jab 'Edit' click ho, Home tab par switch karein
        noteViewModel.navigateToHome.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                binding.bottomNavigationView.selectedItemId = R.id.nav_home
            }
        }
    }

    // --- Options Menu Logic ---

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
                // ViewModel ke through HomeFragment ko update karein
                noteViewModel.onLanguageSelected(selectedLanguage)
                Toast.makeText(this, "Language set to ${languageNames[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About Voice Notes")
            .setMessage("""
                Voice Notes v1.0
                
                A simple and efficient voice-to-text note taking app.
                
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
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Permission Dialogs (HomeFragment se call ho sakte hain) ---

    fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Voice recording permission is required. Please grant permission in app settings.")
            .setPositiveButton("Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}