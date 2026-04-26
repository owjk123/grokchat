package com.grokchat

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.grokchat.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Run data migrations once before fragments touch prefs
        Prefs.ensureMigrated(applicationContext)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        // Show/hide Up button and update title when back stack changes
        supportFragmentManager.addOnBackStackChangedListener { syncToolbar() }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainer, ChatFragment())
            }
        }
        syncToolbar()
    }

    private fun syncToolbar() {
        val count = supportFragmentManager.backStackEntryCount
        supportActionBar?.setDisplayHomeAsUpEnabled(count > 0)
        if (count == 0) {
            supportActionBar?.title = getString(R.string.app_name)
            invalidateOptionsMenu()
        } else {
            val frag = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            supportActionBar?.title = when (frag) {
                is SettingsFragment  -> getString(R.string.action_settings)
                is RolesFragment     -> getString(R.string.roles_title)
                is EditRoleFragment  -> getString(R.string.edit_role)
                else                 -> getString(R.string.app_name)
            }
            invalidateOptionsMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Only show menu items on the chat (root) screen
        if (supportFragmentManager.backStackEntryCount == 0)
            menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_roles -> {
                supportFragmentManager.commit {
                    replace(R.id.fragmentContainer, RolesFragment())
                    addToBackStack(null)
                }
                true
            }
            R.id.action_settings -> {
                supportFragmentManager.commit {
                    replace(R.id.fragmentContainer, SettingsFragment())
                    addToBackStack(null)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
