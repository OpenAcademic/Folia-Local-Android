package dev.folialocal.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.folialocal.app.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 未提供")
}
