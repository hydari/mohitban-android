package ir.mohitban.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.mohitban.app.AppContainer
import ir.mohitban.app.container

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container, createSavedStateHandle()) }
        },
    )
}
