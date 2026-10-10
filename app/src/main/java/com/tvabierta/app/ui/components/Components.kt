package com.tvabierta.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tvabierta.app.data.Channel
import com.tvabierta.app.ui.theme.Palette

/**
 * A D-pad friendly clickable surface: a clear focus ring, an optional zoom, and a secondary
 * action on long-press of OK or on the remote's ≡ (menu) key.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = 1f,
    focusedColor: Color = Palette.Focus,
    unfocusedColor: Color = Color.Transparent,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "focusScale")
    // Holding OK on a remote reaches combinedClickable both through its own timer and through the
    // key's FLAG_LONG_PRESS repeat; without this a hold would toggle a favorite twice.
    val lastLongClick = remember { longArrayOf(0L) }
    val longClick = onLongClick?.let { action ->
        {
            val now = SystemClock.uptimeMillis()
            if (now - lastLongClick[0] > LONG_CLICK_GUARD_MS) {
                lastLongClick[0] = now
                action()
            }
        }
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) focusedColor else unfocusedColor)
            .border(2.dp, if (focused) Palette.Accent else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (longClick != null && event.key == Key.Menu) {
                    if (event.type == KeyEventType.KeyUp) longClick()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = longClick,
                onClick = onClick,
            ),
    ) {
        content(focused)
    }
}

private const val LONG_CLICK_GUARD_MS = 1_000L

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    TvClickable(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(21.dp),
        focusedColor = Palette.Accent,
        unfocusedColor = Palette.SurfaceHigh,
    ) { focused ->
        val color = if (focused) Color.Black else Palette.TextPrimary
        Row(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
        }
    }
}

/** The channel's logo on a light plate (most logos are dark-on-transparent), or its initials. */
@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier, initialsSize: TextUnit = 22.sp) {
    var failed by remember(channel.logo) { mutableStateOf(channel.logo == null) }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Palette.LogoPlate), contentAlignment = Alignment.Center) {
        if (failed) {
            Text(initials(channel.name), color = Color(0xFF3A4350), fontWeight = FontWeight.Bold, fontSize = initialsSize, maxLines = 1)
        } else {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(' ', '-', '_').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "TV" }

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Palette.SurfaceHigh, textColor: Color = Palette.TextSecondary) {
    Text(
        text,
        color = textColor,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Single-line text field that lets ▲▼ leave it (TV text fields otherwise trap the D-pad). */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { onImeAction() },
            onSearch = { onImeAction() },
            onGo = { onImeAction() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Accent,
            focusedLabelColor = Palette.Accent,
            cursorColor = Palette.Accent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            },
    )
}
