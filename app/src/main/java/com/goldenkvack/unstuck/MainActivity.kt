package com.goldenkvack.unstuck

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson

object FragmentToLoad {
    val MAIN = 0
    val SETTINGS = 1
}

class MainActivity : AppCompatActivity() {
    companion object {
        val gson = Gson()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_main,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        when (intent.extras?.getInt("frgToLoad")) {
            0 -> navController.navigate(R.id.navigation_main)
            1 -> navController.navigate(R.id.navigation_settings)
        }
    }

}
