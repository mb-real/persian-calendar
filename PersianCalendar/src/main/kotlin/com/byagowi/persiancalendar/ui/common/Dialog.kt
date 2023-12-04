package com.byagowi.persiancalendar.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun Dialog(
    title: (@Composable () -> Unit)? = null,
    onDismissRequest: () -> Unit,
    neutralButton: (@Composable () -> Unit)? = null,
    positiveButton: (@Composable () -> Unit)? = null,
    negativeButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column {
                title?.also { title ->
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.headlineSmall
                    ) { Box(Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)) { title() } }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) { content() }
                Spacer(modifier = Modifier.height(8.dp))
                if (neutralButton != null || negativeButton != null || positiveButton != null) {
                    Row(Modifier.padding(bottom = 16.dp, start = 24.dp, end = 24.dp)) {
                        neutralButton?.invoke()
                        Spacer(Modifier.weight(1f))
                        negativeButton?.invoke()
                        Spacer(Modifier.width(8.dp))
                        positiveButton?.invoke()
                    }
                }
            }
        }
    }
}