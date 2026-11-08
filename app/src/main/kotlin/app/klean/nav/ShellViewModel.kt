package app.klean.nav

import androidx.lifecycle.ViewModel
import app.klean.core.ui.module.ModuleRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ShellViewModel @Inject constructor(val registry: ModuleRegistry) : ViewModel()
