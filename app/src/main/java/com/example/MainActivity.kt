package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.data.GameDatabase
import com.example.data.GameRepository
import com.example.ui.screens.GameScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {

    // Initializing the Room Database
    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            GameDatabase::class.java,
            "supermarket_game.db"
        ).fallbackToDestructiveMigration().build()
    }

    // Creating the Repository
    private val repository by lazy {
        GameRepository(db.dao())
    }

    // Injecting Repository into ViewModel using Simple Factory
    private val viewModel: GameViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GameViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GameScreen(viewModel = viewModel)
            }
        }
    }
}
