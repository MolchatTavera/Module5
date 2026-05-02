package com.example.module5

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiaryEntry(
    val fileName: String,
    val title: String,
    val text: String,
    val date: String
)

class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    val entries = mutableStateListOf<DiaryEntry>()

    var selectedEntry by mutableStateOf<DiaryEntry?>(null)
    var isEditorOpen by mutableStateOf(false)

    init {
        loadEntriesOnce()
    }

    private fun getFilesDir(): File {
        return getApplication<Application>().applicationContext.filesDir
    }

    private fun loadEntriesOnce() {
        val files = getFilesDir()
            .listFiles { file -> file.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        entries.clear()

        files.forEach { file ->
            val content = file.readText()
            val lines = content.lines()
            val title = lines.firstOrNull()?.removePrefix("TITLE:") ?: "Без названия"
            val text = lines.drop(1).joinToString("\n")

            entries.add(
                DiaryEntry(
                    fileName = file.name,
                    title = title.ifBlank { "Без названия" },
                    text = text,
                    date = formatDate(file.lastModified())
                )
            )
        }
    }

    fun openNewEntry() {
        selectedEntry = null
        isEditorOpen = true
    }

    fun openEntry(entry: DiaryEntry) {
        selectedEntry = entry
        isEditorOpen = true
    }

    fun closeEditor() {
        selectedEntry = null
        isEditorOpen = false
    }

    fun saveEntry(title: String, text: String) {
        if (text.isBlank()) return

        val finalTitle = title.ifBlank { "Без названия" }
        val oldEntry = selectedEntry

        if (oldEntry == null) {
            val fileName = createFileName(finalTitle)
            val file = File(getFilesDir(), fileName)

            file.writeText("TITLE:$finalTitle\n$text")

            val newEntry = DiaryEntry(
                fileName = fileName,
                title = finalTitle,
                text = text,
                date = formatDate(file.lastModified())
            )

            entries.add(0, newEntry)
        } else {
            val file = File(getFilesDir(), oldEntry.fileName)

            file.writeText("TITLE:$finalTitle\n$text")

            val updatedEntry = oldEntry.copy(
                title = finalTitle,
                text = text,
                date = formatDate(file.lastModified())
            )

            val index = entries.indexOfFirst { entry ->
                entry.fileName == oldEntry.fileName
            }

            if (index != -1) {
                entries[index] = updatedEntry
            }
        }

        closeEditor()
    }

    fun deleteEntry(entry: DiaryEntry) {
        val file = File(getFilesDir(), entry.fileName)

        if (file.exists()) {
            file.delete()
        }

        entries.removeAll { item ->
            item.fileName == entry.fileName
        }

        if (selectedEntry?.fileName == entry.fileName) {
            closeEditor()
        }
    }

    private fun createFileName(title: String): String {
        val timestamp = System.currentTimeMillis()

        val safeTitle = title
            .replace(" ", "_")
            .replace(Regex("[^A-Za-zА-Яа-я0-9_]"), "")
            .take(20)

        return if (safeTitle.isBlank()) {
            "${timestamp}_note.txt"
        } else {
            "${timestamp}_${safeTitle}.txt"
        }
    }

    private fun formatDate(time: Long): String {
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return formatter.format(Date(time))
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var diaryViewModel: DiaryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        diaryViewModel = DiaryViewModel(application)

        setContent {
            MaterialTheme {
                DiaryApp(viewModel = diaryViewModel)
            }
        }
    }
}

@Composable
fun DiaryApp(viewModel: DiaryViewModel) {
    if (viewModel.isEditorOpen) {
        DiaryEditorScreen(viewModel = viewModel)
    } else {
        DiaryListScreen(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(viewModel: DiaryViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Личный дневник")
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.openNewEntry()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Новая запись"
                )
            }
        }
    ) { paddingValues ->

        if (viewModel.entries.isEmpty()) {
            EmptyDiaryScreen(
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = viewModel.entries,
                    key = { entry -> entry.fileName }
                ) { entry ->
                    DiaryEntryItem(
                        entry = entry,
                        onClick = {
                            viewModel.openEntry(entry)
                        },
                        onDelete = {
                            viewModel.deleteEntry(entry)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyDiaryScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "У вас пока нет записей",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Нажмите +, чтобы создать первую",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun DiaryEntryItem(
    entry: DiaryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var isMenuOpen by remember {
        mutableStateOf(false)
    }

    var isDeleteDialogOpen by remember {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    isMenuOpen = true
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = entry.date,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Box {
                    IconButton(
                        onClick = {
                            isMenuOpen = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Меню"
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = {
                            isMenuOpen = false
                        }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(text = "Удалить")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить"
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                isDeleteDialogOpen = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = entry.text.take(40),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (isDeleteDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                isDeleteDialogOpen = false
            },
            title = {
                Text(text = "Удалить запись?")
            },
            text = {
                Text(text = "Эту запись нельзя будет восстановить.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteDialogOpen = false
                        onDelete()
                    }
                ) {
                    Text(text = "Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isDeleteDialogOpen = false
                    }
                ) {
                    Text(text = "Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditorScreen(viewModel: DiaryViewModel) {
    val editingEntry = viewModel.selectedEntry

    var title by remember(editingEntry?.fileName) {
        mutableStateOf(editingEntry?.title ?: "")
    }

    var text by remember(editingEntry?.fileName) {
        mutableStateOf(editingEntry?.text ?: "")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editingEntry == null) {
                            "Новая запись"
                        } else {
                            "Редактирование"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.closeEditor()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    if (editingEntry != null) {
                        IconButton(
                            onClick = {
                                viewModel.deleteEntry(editingEntry)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(text = "Заголовок")
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = {
                    Text(text = "Текст записи")
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.closeEditor()
                    }
                ) {
                    Text(text = "Назад")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        viewModel.saveEntry(
                            title = title,
                            text = text
                        )
                    }
                ) {
                    Text(text = "Сохранить")
                }
            }
        }
    }
}