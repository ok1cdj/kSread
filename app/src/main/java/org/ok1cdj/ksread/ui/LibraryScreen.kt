package org.ok1cdj.ksread.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import org.ok1cdj.ksread.R
import org.ok1cdj.ksread.data.BookEntry
import org.ok1cdj.ksread.data.SortMode

@Composable
fun LibraryScreen(vm: MainViewModel, state: UiState) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showSettings by remember { mutableStateOf(false) }
    var showType by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            vm.onFolderPicked(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Header: icon + name + about + settings
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
            )
            Spacer(Modifier.width(4.dp))
            TextMMD(
                text = stringResource(R.string.app_name),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconAppButton(
                iconRes = R.drawable.ic_info,
                contentDescription = stringResource(R.string.info),
                onClick = { showAbout = true },
                iconSize = 22,
            )
            Spacer(Modifier.width(6.dp))
            IconAppButton(
                iconRes = R.drawable.ic_settings,
                contentDescription = stringResource(R.string.settings),
                onClick = { showSettings = true },
                iconSize = 22,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                text = stringResource(
                    if (state.hasFolder) R.string.change_folder else R.string.choose_folder
                ),
                onClick = { folderPicker.launch(null) },
                bold = true,
                fontSize = 14.sp,
            )
            AppButton(
                text = stringResource(R.string.type_text),
                onClick = { showType = true },
                fontSize = 14.sp,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                text = stringResource(R.string.paste_clipboard),
                onClick = {
                    val text = clipboard.getText()?.text
                    if (text.isNullOrBlank()) {
                        vm.openText("", context.getString(R.string.clipboard_empty))
                    } else {
                        vm.openText(text, context.getString(R.string.pasted_title))
                    }
                },
                fontSize = 14.sp,
            )
            if (state.hasFolder && state.books.isNotEmpty()) {
                val modeLabel = stringResource(
                    if (state.sortMode == SortMode.PROGRESS) R.string.sort_progress else R.string.sort_name
                )
                AppButton(
                    text = stringResource(R.string.sort_label, modeLabel),
                    onClick = { vm.toggleSort() },
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDividerMMD(thickness = 1.dp)
        Spacer(Modifier.height(8.dp))

        state.error?.let {
            TextMMD(
                text = stringResource(R.string.open_failed, it),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        // Content area fills the rest and scrolls when there are many books.
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading -> TextMMD(text = stringResource(R.string.loading), fontSize = 16.sp)
                !state.hasFolder -> TextMMD(text = stringResource(R.string.no_folder), fontSize = 14.sp)
                state.books.isEmpty() -> TextMMD(text = stringResource(R.string.no_books), fontSize = 14.sp)
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (book in state.books) {
                        BookRow(book) { vm.openBook(book) }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            uppercase = state.uppercase,
            onToggleUppercase = { vm.toggleUppercase() },
            onClose = { showSettings = false },
        )
    }

    if (showType) {
        TypeTextDialog(
            onConfirm = { text ->
                showType = false
                if (text.isNotBlank()) vm.openText(text, context.getString(R.string.pasted_title))
            },
            onCancel = { showType = false },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun BookRow(book: BookEntry, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextMMD(text = book.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(8.dp))
            TextMMD(text = stringResource(R.string.percent_read, book.percent), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingsDialog(
    uppercase: Boolean,
    onToggleUppercase: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .background(Color.White)
                .border(1.dp, Color.Black, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextMMD(text = stringResource(R.string.settings), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextMMD(text = stringResource(R.string.uppercase), fontSize = 15.sp, modifier = Modifier.weight(1f))
                AppButton(
                    text = if (uppercase) "ON" else "OFF",
                    onClick = onToggleUppercase,
                    fontSize = 14.sp,
                )
            }
            AppButton(text = stringResource(R.string.close), onClick = onClose, bold = true, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TypeTextDialog(onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .background(Color.White)
                .border(1.dp, Color.Black, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextMMD(text = stringResource(R.string.type_text), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { TextMMD(text = stringResource(R.string.type_text_hint), fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(text = stringResource(R.string.cancel), onClick = onCancel, fontSize = 14.sp)
                AppButton(
                    text = stringResource(R.string.start_reading),
                    onClick = { onConfirm(text) },
                    bold = true,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
