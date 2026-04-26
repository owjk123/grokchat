package com.grokchat.pro

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
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainer, ChatFragment())
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
