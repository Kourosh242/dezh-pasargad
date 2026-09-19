package com.pasargad.dezh.presentation.generator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.generator.PasswordGenerator.Options
import com.pasargad.dezh.generator.PasswordGenerator
import com.pasargad.dezh.settings.GeneratorSettings
import com.pasargad.dezh.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class GeneratorUiState(
    val length: Int = Options.DEFAULT_LENGTH,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
    val minUppercase: Int = 1,
    val minLowercase: Int = 1,
    val minDigits: Int = 1,
    val minSymbols: Int = 1,
    val error: Boolean = false,
    /** 0..100 entropy estimate for the strength ring (visual feedback only). */
    val meterScore: Int = 0,
    val password: String = "",
)

/** Generator state holder — generated values live only in this StateFlow and are never logged. */
class GeneratorViewModel(
    private val generator: PasswordGenerator,
    private val settingsRepository: SettingsRepository? = null,
    private val strengthMeter: com.pasargad.dezh.domain.PasswordStrengthMeter = com.pasargad.dezh.domain.PasswordStrengthMeter(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneratorUiState())
    val uiState: StateFlow<GeneratorUiState> = _uiState.asStateFlow()

    init {
        // Seed the controls from the persisted generator defaults.
        viewModelScope.launch {
            val defaults = settingsRepository?.current()?.generator ?: GeneratorSettings()
            _uiState.update {
                it.copy(
                    length = defaults.length,
                    includeUppercase = defaults.includeUppercase,
                    includeLowercase = defaults.includeLowercase,
                    includeDigits = defaults.includeDigits,
                    includeSymbols = defaults.includeSymbols,
                    excludeAmbiguous = defaults.excludeAmbiguous,
                    minUppercase = defaults.minUppercase,
                    minLowercase = defaults.minLowercase,
                    minDigits = defaults.minDigits,
                    minSymbols = defaults.minSymbols,
                )
            }
            regenerate()
        }
    }

    fun onLengthChanged(value: Int) = mutate {
        it.copy(length = value.coerceIn(Options.MIN_LENGTH, Options.MAX_LENGTH))
    }

    fun onUppercaseChanged(value: Boolean) = mutate { it.copy(includeUppercase = value) }
    fun onLowercaseChanged(value: Boolean) = mutate { it.copy(includeLowercase = value) }
    fun onDigitsChanged(value: Boolean) = mutate { it.copy(includeDigits = value) }
    fun onSymbolsChanged(value: Boolean) = mutate { it.copy(includeSymbols = value) }
    fun onExcludeAmbiguousChanged(value: Boolean) = mutate { it.copy(excludeAmbiguous = value) }
    fun onMinUppercaseChanged(value: Int) = mutate { it.copy(minUppercase = value.coerceIn(0, 8)) }
    fun onMinLowercaseChanged(value: Int) = mutate { it.copy(minLowercase = value.coerceIn(0, 8)) }
    fun onMinDigitsChanged(value: Int) = mutate { it.copy(minDigits = value.coerceIn(0, 8)) }
    fun onMinSymbolsChanged(value: Int) = mutate { it.copy(minSymbols = value.coerceIn(0, 8)) }

    fun regenerate() {
        val s = _uiState.value
        val generated = runCatching {
            generator.generate(
                Options(
                    length = s.length,
                    includeUppercase = s.includeUppercase,
                    includeLowercase = s.includeLowercase,
                    includeDigits = s.includeDigits,
                    includeSymbols = s.includeSymbols,
                    excludeAmbiguous = s.excludeAmbiguous,
                    minUppercase = s.minUppercase,
                    minLowercase = s.minLowercase,
                    minDigits = s.minDigits,
                    minSymbols = s.minSymbols,
                ),
            )
        }.getOrDefault("")
                val meterScore = if (generated.isEmpty()) {
            0
        } else {
            strengthMeter.evaluate(generated.toCharArray()).score
        }
        _uiState.update { it.copy(password = generated, error = generated.isEmpty(), meterScore = meterScore) }
    }

    private fun mutate(transform: (GeneratorUiState) -> GeneratorUiState) {
        _uiState.update(transform)
        persistDefaults()
        regenerate()
    }

    /** Write-through: option changes become the next session's defaults. */
    private fun persistDefaults() {
        val s = _uiState.value
        settingsRepository?.let { repo ->
            viewModelScope.launch {
                repo.update {
                    it.copy(
                        generator = GeneratorSettings(
                            length = s.length,
                            includeUppercase = s.includeUppercase,
                            includeLowercase = s.includeLowercase,
                            includeDigits = s.includeDigits,
                            includeSymbols = s.includeSymbols,
                            excludeAmbiguous = s.excludeAmbiguous,
                            minUppercase = s.minUppercase,
                            minLowercase = s.minLowercase,
                            minDigits = s.minDigits,
                            minSymbols = s.minSymbols,
                        ),
                    )
                }
            }
        }
    }
}
