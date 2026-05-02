package com.example.task4

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore(name = "todo_settings")

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String,
    val completed: Boolean
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTasksCount(): Int
}

@Database(
    entities = [TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "todo_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}

class TodoPreferencesRepository(
    private val context: Context
) {
    private val completedColorKey = booleanPreferencesKey("completed_color_enabled")
    private val importedKey = booleanPreferencesKey("json_imported")

    val completedColorEnabled: Flow<Boolean> =
        context.dataStore.data.map { preferences ->
            preferences[completedColorKey] ?: true
        }

    suspend fun setCompletedColorEnabled(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[completedColorKey] = value
        }
    }

    suspend fun wasJsonImported(): Boolean {
        return context.dataStore.data.map { preferences ->
            preferences[importedKey] ?: false
        }.first()
    }

    suspend fun setJsonImported() {
        context.dataStore.edit { preferences ->
            preferences[importedKey] = true
        }
    }
}

class TaskRepository(
    private val dao: TaskDao
) {
    val tasks: Flow<List<TaskEntity>> = dao.getAllTasks()

    suspend fun addTask(title: String, description: String) {
        dao.insertTask(
            TaskEntity(
                title = title,
                description = description,
                completed = false
            )
        )
    }

    suspend fun updateTask(task: TaskEntity) {
        dao.updateTask(task)
    }

    suspend fun deleteTask(task: TaskEntity) {
        dao.deleteTask(task)
    }

    suspend fun toggleCompleted(task: TaskEntity) {
        dao.updateTask(
            task.copy(completed = !task.completed)
        )
    }

    suspend fun importTasksFromJson(context: Context) {
        val json = context.assets
            .open("todos.json")
            .bufferedReader()
            .use { reader -> reader.readText() }

        val array = JSONArray(json)

        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)

            dao.insertTask(
                TaskEntity(
                    title = item.getString("title"),
                    description = item.getString("description"),
                    completed = item.getBoolean("completed")
                )
            )
        }
    }
}

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = TaskDatabase.getDatabase(application)
    private val repository = TaskRepository(database.taskDao())
    private val preferencesRepository = TodoPreferencesRepository(application)

    val tasks: StateFlow<List<TaskEntity>> =
        repository.tasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val completedColorEnabled: StateFlow<Boolean> =
        preferencesRepository.completedColorEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    init {
        viewModelScope.launch {
            if (!preferencesRepository.wasJsonImported()) {
                repository.importTasksFromJson(getApplication())
                preferencesRepository.setJsonImported()
            }
        }
    }

    fun addTask(title: String, description: String) {
        if (title.isBlank()) return

        viewModelScope.launch {
            repository.addTask(
                title = title,
                description = description
            )
        }
    }

    fun updateTask(task: TaskEntity, title: String, description: String) {
        if (title.isBlank()) return

        viewModelScope.launch {
            repository.updateTask(
                task.copy(
                    title = title,
                    description = description
                )
            )
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun toggleCompleted(task: TaskEntity) {
        viewModelScope.launch {
            repository.toggleCompleted(task)
        }
    }

    fun setCompletedColorEnabled(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCompletedColorEnabled(value)
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: TodoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = TodoViewModel(application)

        setContent {
            MaterialTheme {
                TodoApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(viewModel: TodoViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val completedColorEnabled by viewModel.completedColorEnabled.collectAsState()

    var isDialogOpen by remember {
        mutableStateOf(false)
    }

    var editingTask by remember {
        mutableStateOf<TaskEntity?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "TodoList")
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(text = "Цвет завершенных")

                        Switch(
                            checked = completedColorEnabled,
                            onCheckedChange = { value ->
                                viewModel.setCompletedColorEnabled(value)
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTask = null
                    isDialogOpen = true
                }
            ) {
                Text(text = "+")
            }
        }
    ) { paddingValues ->

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Список задач пуст")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = tasks,
                    key = { task -> task.id }
                ) { task ->
                    TaskItem(
                        task = task,
                        completedColorEnabled = completedColorEnabled,
                        onCheckedChange = {
                            viewModel.toggleCompleted(task)
                        },
                        onEdit = {
                            editingTask = task
                            isDialogOpen = true
                        },
                        onDelete = {
                            viewModel.deleteTask(task)
                        }
                    )
                }
            }
        }
    }

    if (isDialogOpen) {
        TaskDialog(
            task = editingTask,
            onDismiss = {
                isDialogOpen = false
            },
            onSave = { title, description ->
                val currentTask = editingTask

                if (currentTask == null) {
                    viewModel.addTask(title, description)
                } else {
                    viewModel.updateTask(
                        task = currentTask,
                        title = title,
                        description = description
                    )
                }

                isDialogOpen = false
            }
        )
    }
}

@Composable
fun TaskItem(
    task: TaskEntity,
    completedColorEnabled: Boolean,
    onCheckedChange: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val backgroundColor =
        if (task.completed && completedColorEnabled) {
            Color(0xFFD6F5D6)
        } else {
            MaterialTheme.colorScheme.surface
        }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = {
                    onCheckedChange()
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Column {
                TextButton(
                    onClick = onEdit
                ) {
                    Text(text = "Изм.")
                }

                TextButton(
                    onClick = onDelete
                ) {
                    Text(text = "Удал.")
                }
            }
        }
    }
}

@Composable
fun TaskDialog(
    task: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember(task?.id) {
        mutableStateOf(task?.title ?: "")
    }

    var description by remember(task?.id) {
        mutableStateOf(task?.description ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (task == null) {
                    "Новая задача"
                } else {
                    "Редактирование задачи"
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                    },
                    label = {
                        Text(text = "Название")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                    },
                    label = {
                        Text(text = "Описание")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title, description)
                }
            ) {
                Text(text = "Сохранить")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(text = "Отмена")
            }
        }
    )
}