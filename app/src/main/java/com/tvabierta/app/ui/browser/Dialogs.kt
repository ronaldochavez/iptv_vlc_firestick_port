package com.tvabierta.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tvabierta.app.ui.components.TvButton
import com.tvabierta.app.ui.components.TvTextField
import com.tvabierta.app.ui.theme.Palette

@Composable
private fun TvDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.Surface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            content()
        }
    }
}

/** VLC's "Open network stream": play a URL now, or keep it as a playlist next to the countries. */
@Composable
fun OpenUrlDialog(onPlay: (String) -> Unit, onSave: (name: String, url: String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val urlFocus = remember { FocusRequester() }

    fun validUrl(): String? {
        val trimmed = url.trim()
        if ("://" in trimmed) return trimmed
        error = "Escribe la URL completa, por ejemplo http://servidor/lista.m3u"
        return null
    }

    TvDialog("Abrir URL", onDismiss) {
        Text(
            "Un flujo (m3u8, ts, rtsp, rtmp, udp…) para verlo ahora, o una lista M3U para guardarla junto a los países.",
            color = Palette.TextSecondary,
            fontSize = 14.sp,
        )
        TvTextField(
            value = url,
            onValueChange = {
                url = it
                error = null
            },
            label = "URL",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            focusRequester = urlFocus,
        )
        TvTextField(value = name, onValueChange = { name = it }, label = "Nombre (solo para guardar una lista)")
        error?.let { Text(it, color = Palette.Danger, fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Reproducir", icon = Icons.Default.PlayArrow, onClick = { validUrl()?.let(onPlay) })
            TvButton("Guardar lista", icon = Icons.Default.Add, onClick = { validUrl()?.let { onSave(name, it) } })
            TvButton("Cancelar", onClick = onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { urlFocus.requestFocus() } }
}

@Composable
fun SearchDialog(initial: String, onQueryChange: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    TvDialog("Buscar canal", onDismiss) {
        TvTextField(
            value = text,
            onValueChange = {
                text = it
                onQueryChange(it)
            },
            label = "Nombre del canal",
            imeAction = ImeAction.Search,
            onImeAction = onDismiss,
            focusRequester = fieldFocus,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Listo", onClick = onDismiss)
            TvButton("Borrar búsqueda", onClick = {
                text = ""
                onQueryChange("")
            })
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
}

@Composable
fun ConfirmRemoveDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    TvDialog("¿Quitar esta lista?", onDismiss) {
        Text(name, color = Palette.TextSecondary, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("Cancelar", onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus))
            TvButton("Quitar", icon = Icons.Default.Delete, onClick = onConfirm)
        }
    }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}
