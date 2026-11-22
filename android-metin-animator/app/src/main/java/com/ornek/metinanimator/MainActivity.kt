package com.ornek.metinanimator

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.ornek.metinanimator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val parser = TextParser()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAnimate.setOnClickListener {
            val text = binding.inputText.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) {
                binding.inputText.error = getString(R.string.empty_warning)
                return@setOnClickListener
            }
            binding.animationView.playScene(parser.parse(text))
            hideKeyboard()
        }

        binding.btnClear.setOnClickListener {
            binding.inputText.setText("")
            binding.animationView.clear()
        }

        // Açılışta örnek bir sahne göster.
        val demo = "Güneşli bir günde kuşlar uçuyor, araba yolda gidiyor ve ağaçlar sallanıyor."
        binding.inputText.setText(demo)
        binding.animationView.post {
            binding.animationView.playScene(parser.parse(demo))
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.inputText.windowToken, 0)
    }
}
