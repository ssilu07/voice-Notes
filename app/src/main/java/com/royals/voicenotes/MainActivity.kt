package com.royals.voicenotes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.royals.voicenotes.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // ViewModel ko yahan initialize karein
    private val noteViewModel: NoteViewModel by viewModels()

    private var isAuthenticated = false

    private val restoreFilePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleRestoreFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved dark mode preference before setContentView
        applySavedTheme()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActionBar()
        setupNavigation()
        observeViewModel()

        // Show biometric prompt if enabled
        if (BiometricHelper.isBiometricEnabled(this) && !isAuthenticated) {
            showBiometricAuth()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-authenticate when app returns from background (if enabled)
        if (BiometricHelper.isBiometricEnabled(this) && !isAuthenticated) {
            showBiometricAuth()
        }
    }

    private fun showBiometricAuth() {
        BiometricHelper.authenticate(
            activity = this,
            onSuccess = { isAuthenticated = true },
            onError = { errorMsg ->
                if (errorMsg.isNotEmpty()) {
                    Toast.makeText(this, getString(R.string.biometric_auth_failed, errorMsg), Toast.LENGTH_SHORT).show()
                }
                finish() // Close app if auth fails/cancelled
            }
        )
    }

    private fun setupActionBar() {
        supportActionBar?.title = "Voice Notes"
        supportActionBar?.elevation = 4f
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Custom tab click listeners
        binding.tabHome.setOnClickListener {
            if (navController.currentDestination?.id != R.id.nav_home) {
                navController.popBackStack(R.id.nav_home, false)
            }
            updateTabSelection(isHomeSelected = true)
        }

        binding.tabNotes.setOnClickListener {
            if (navController.currentDestination?.id != R.id.nav_saved_notes) {
                navController.navigate(R.id.nav_saved_notes)
            }
            updateTabSelection(isHomeSelected = false)
        }

        // NavController ke destination changes ko listen karein
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.nav_home -> updateTabSelection(isHomeSelected = true)
                R.id.nav_saved_notes -> updateTabSelection(isHomeSelected = false)
            }
        }

        binding.fab.setOnClickListener {
            navController.navigate(R.id.nav_record)
        }

        // Set initial state
        updateTabSelection(isHomeSelected = true)
    }

    private fun updateTabSelection(isHomeSelected: Boolean) {
        val activeColor = ContextCompat.getColor(this, R.color.primary_500)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_hint)

        // Home tab
        binding.icHome.setColorFilter(if (isHomeSelected) activeColor else inactiveColor)
        binding.tvHome.setTextColor(if (isHomeSelected) activeColor else inactiveColor)
        binding.tvHome.setTypeface(null, if (isHomeSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

        // Notes tab
        binding.icNotes.setColorFilter(if (!isHomeSelected) activeColor else inactiveColor)
        binding.tvNotes.setTextColor(if (!isHomeSelected) activeColor else inactiveColor)
        binding.tvNotes.setTypeface(null, if (!isHomeSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun observeViewModel() {
        // Jab 'Edit' click ho, Home tab par switch karein
        noteViewModel.navigateToHome.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                navController.popBackStack(R.id.nav_home, false)
                updateTabSelection(isHomeSelected = true)
            }
        }
    }

    // --- Options Menu Logic ---

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        // Set biometric toggle state
        val biometricItem = menu.findItem(R.id.action_biometric_lock)
        biometricItem?.isChecked = BiometricHelper.isBiometricEnabled(this)
        // Hide biometric option if not available on device
        if (!BiometricHelper.isBiometricAvailable(this)) {
            biometricItem?.isVisible = false
        }
        // Set dark mode toggle state
        val darkModeItem = menu.findItem(R.id.action_dark_mode)
        darkModeItem?.isChecked = isDarkModeEnabled()
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
            R.id.action_dark_mode -> {
                toggleDarkMode(item)
                true
            }
            R.id.action_biometric_lock -> {
                toggleBiometricLock(item)
                true
            }
            R.id.action_backup -> {
                backupNotes()
                true
            }
            R.id.action_restore -> {
                restoreNotes()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleBiometricLock(item: MenuItem) {
        val newState = !item.isChecked
        if (newState) {
            // Verify biometric before enabling
            BiometricHelper.authenticate(
                activity = this,
                onSuccess = {
                    BiometricHelper.setBiometricEnabled(this, true)
                    item.isChecked = true
                    Toast.makeText(this, getString(R.string.biometric_enabled), Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    if (errorMsg.isNotEmpty()) {
                        Toast.makeText(this, getString(R.string.biometric_auth_failed, errorMsg), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            BiometricHelper.setBiometricEnabled(this, false)
            item.isChecked = false
            Toast.makeText(this, getString(R.string.biometric_disabled), Toast.LENGTH_SHORT).show()
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
            .setTitle(getString(R.string.select_language_title))
            .setItems(languageNames) { _, which ->
                val selectedLanguage = languages[which].second
                noteViewModel.onLanguageSelected(selectedLanguage)
                Toast.makeText(this, getString(R.string.language_set_to, languageNames[which]), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showAboutDialog() {
        val aboutMessage = "${getString(R.string.about_version)}\n\n${getString(R.string.about_description)}\n\n${getString(R.string.about_developer)}"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_title))
            .setMessage(aboutMessage)
            .setPositiveButton(getString(R.string.action_ok), null)
            .show()
    }

    private fun showDeleteAllConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_all_title))
            .setMessage(getString(R.string.delete_all_confirmation))
            .setPositiveButton(getString(R.string.action_delete_all_confirm)) { _, _ ->
                noteViewModel.deleteAll()
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    // --- Backup & Restore ---

    private fun backupNotes() {
        val notes = noteViewModel.allNotes.value
        if (notes.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.no_notes_to_export), Toast.LENGTH_SHORT).show()
            return
        }
        val success = BackupHelper.exportBackup(this, notes)
        if (success) {
            Toast.makeText(this, getString(R.string.backup_success), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, getString(R.string.backup_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreNotes() {
        restoreFilePicker.launch("application/json")
    }

    private fun handleRestoreFile(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Toast.makeText(this, getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val notes = BackupHelper.parseBackup(inputStream)
        inputStream.close()

        if (notes == null) {
            Toast.makeText(this, getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.restore_confirm_title))
            .setMessage(getString(R.string.restore_confirm_message))
            .setPositiveButton(getString(R.string.action_restore)) { _, _ ->
                lifecycleScope.launch {
                    notes.forEach { note -> noteViewModel.insert(note) }
                    Toast.makeText(this@MainActivity, getString(R.string.restore_success, notes.size), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    // --- Permission Dialogs (HomeFragment se call ho sakte hain) ---

    fun showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_dialog_title))
            .setMessage(getString(R.string.permission_dialog_message))
            .setPositiveButton(getString(R.string.action_settings_open)) { _, _ -> openAppSettings() }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    // --- Dark Mode ---

    private fun applySavedTheme() {
        val mode = if (isDarkModeEnabled()) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun isDarkModeEnabled(): Boolean {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    private fun toggleDarkMode(item: MenuItem) {
        val newState = !item.isChecked
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, newState).apply()
        item.isChecked = newState

        val mode = if (newState) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    companion object {
        private const val KEY_DARK_MODE = "dark_mode_enabled"
    }

    // Handle back button press
    override fun onBackPressed() {
        // If we're at home or saved notes, exit the app
        if (navController.currentDestination?.id == R.id.nav_home ||
            navController.currentDestination?.id == R.id.nav_saved_notes) {
            super.onBackPressed()
        } else {
            // Otherwise, navigate back to home
            navController.popBackStack(R.id.nav_home, false)
        }
    }
}
