package com.example.wsplayer.ui.tv // Ujistěte se, že balíček odpovídá

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity // Použijeme AppCompatActivity pro snadnější práci
import androidx.lifecycle.ViewModelProvider
import com.example.wsplayer.R
import com.example.wsplayer.data.api.WebshareApiService
import com.example.wsplayer.databinding.ActivityCustomTvLoginBinding // ViewBinding pro nový layout
import com.example.wsplayer.ui.auth.LoginViewModel
import com.example.wsplayer.ui.auth.LoginViewModelFactory

class CustomTvLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomTvLoginBinding
    private lateinit var loginViewModel: LoginViewModel
    private val TAG = "CustomTvLoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomTvLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate")

        // Inicializace ViewModelu
        val apiService = WebshareApiService.create()
        val factory = LoginViewModelFactory(application, apiService) // Použít application context
        loginViewModel = ViewModelProvider(this, factory)[LoginViewModel::class.java]

        setupListeners()
        observeLoginState()
    }

    private fun setupListeners() {
        binding.tvButtonLogin.setOnClickListener {
            handleLoginAttempt()
        }

        // Volitelně: Zavřít klávesnici při ztrátě fokusu z EditTextů
        binding.tvEditTextUsername.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) hideKeyboard() }
        binding.tvEditTextPassword.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) hideKeyboard() }

        // Volitelně: Spustit přihlášení po stisku Enter na poli hesla
        binding.tvEditTextPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                handleLoginAttempt()
                true
            } else {
                false
            }
        }
    }

    private fun handleLoginAttempt() {
        val username = binding.tvEditTextUsername.text?.toString()?.trim() ?: ""
        val password = binding.tvEditTextPassword.text?.toString() ?: ""
        val rememberMe = binding.tvCheckBoxRememberMe.isChecked

        // Základní validace
        if (username.isEmpty()) {
            binding.tvUsernameInputLayout.error = getString(R.string.tv_login_error_username_empty)
            binding.tvEditTextUsername.requestFocus() // Vrátit fokus
            return
        } else {
            binding.tvUsernameInputLayout.error = null // Smazat chybu
        }

        if (password.isEmpty()) {
            binding.tvPasswordInputLayout.error = getString(R.string.tv_login_error_password_empty)
            binding.tvEditTextPassword.requestFocus()
            return
        } else {
            binding.tvPasswordInputLayout.error = null
        }

        Log.d(TAG, "Attempting login for user: $username, rememberMe: $rememberMe")
        hideKeyboard()
        loginViewModel.login(username, password, rememberMe)
    }


    private fun observeLoginState() {
        loginViewModel.loginState.observe(this) { state ->
            Log.d(TAG, "LoginState changed: $state")
            // Skrýt/zobrazit ProgressBar
            binding.tvProgressBarLogin.visibility = if (state is LoginViewModel.LoginState.Loading) View.VISIBLE else View.GONE
            // Povolit/zakázat tlačítko
            binding.tvButtonLogin.isEnabled = state !is LoginViewModel.LoginState.Loading

            // Skrýt/zobrazit chybovou hlášku
            binding.tvErrorText.visibility = if (state is LoginViewModel.LoginState.Error) View.VISIBLE else View.GONE

            when (state) {
                is LoginViewModel.LoginState.Success -> {
                    Toast.makeText(this, getString(R.string.login_success_toast), Toast.LENGTH_SHORT).show()
                    // Spustit hlavní TV aktivitu a ukončit tuto
                    val intent = Intent(this, TvMainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                is LoginViewModel.LoginState.Error -> {
                    binding.tvErrorText.text = getString(R.string.tv_login_error_toast, state.message)
                    // Můžete také zobrazit chybu přímo u polí, pokud je relevantní
                    // binding.tvPasswordInputLayout.error = state.message
                }
                is LoginViewModel.LoginState.Loading -> {
                    Log.d(TAG, "Login in progress...")
                    binding.tvUsernameInputLayout.error = null // Skrýt chyby během načítání
                    binding.tvPasswordInputLayout.error = null
                }
                is LoginViewModel.LoginState.Idle -> {
                    binding.tvErrorText.visibility = View.GONE // Skrýt chybu v Idle stavu
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
