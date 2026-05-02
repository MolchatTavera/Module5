package com.example.task3

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                GalleryExportApp(activity = this)
            }
        }
    }

    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())

        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return File.createTempFile(
            "IMG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }
}

@Composable
fun GalleryExportApp(activity: MainActivity) {
    var images by remember {
        mutableStateOf(loadImages(activity))
    }

    var currentPhotoFile by remember {
        mutableStateOf<File?>(null)
    }

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    val coroutineScope = rememberCoroutineScope()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            images = loadImages(activity)
        } else {
            currentPhotoFile?.delete()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = activity.createImageFile()
            currentPhotoFile = file

            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                file
            )

            cameraLauncher.launch(uri)
        }
    }

    fun takePhoto() {
        val hasPermission = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val file = activity.createImageFile()
            currentPhotoFile = file

            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                file
            )

            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    takePhoto()
                }
            ) {
                Text(text = "+")
            }
        }
    ) { paddingValues ->

        if (images.isEmpty()) {
            EmptyGalleryScreen(
                paddingValues = paddingValues,
                onTakeFirstPhoto = {
                    takePhoto()
                }
            )
        } else {
            PhotoGridWithExport(
                paddingValues = paddingValues,
                images = images,
                onExport = { file ->
                    val isExported = exportImageToGallery(
                        activity = activity,
                        sourceFile = file
                    )

                    coroutineScope.launch {
                        if (isExported) {
                            snackbarHostState.showSnackbar("Фото добавлено в галерею")
                        } else {
                            snackbarHostState.showSnackbar("Не удалось экспортировать фото")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun EmptyGalleryScreen(
    paddingValues: PaddingValues,
    onTakeFirstPhoto: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onTakeFirstPhoto
        ) {
            Text(text = "Сделать первое фото")
        }
    }
}

@Composable
fun PhotoGridWithExport(
    paddingValues: PaddingValues,
    images: List<File>,
    onExport: (File) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(images) { file ->
            PhotoItem(
                file = file,
                onExport = {
                    onExport(file)
                }
            )
        }
    }
}

@Composable
fun PhotoItem(
    file: File,
    onExport: () -> Unit
) {
    var menuExpanded by remember {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Image(
                painter = rememberAsyncImagePainter(file),
                contentDescription = "Фото",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                IconButton(
                    onClick = {
                        menuExpanded = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                    }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(text = "Экспорт в галерею")
                        },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        }
                    )
                }
            }
        }
    }
}

fun loadImages(activity: MainActivity): List<File> {
    val dir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)

    return dir
        ?.listFiles { file ->
            file.extension.lowercase() == "jpg"
        }
        ?.toList()
        ?.sortedByDescending { file ->
            file.lastModified()
        }
        ?: emptyList()
}

fun exportImageToGallery(
    activity: MainActivity,
    sourceFile: File
): Boolean {
    return try {
        val fileName = "EXPORTED_${System.currentTimeMillis()}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Module5Gallery"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = activity.contentResolver

        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return false

        resolver.openOutputStream(uri)?.use { outputStream ->
            sourceFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)

        resolver.update(
            uri,
            contentValues,
            null,
            null
        )

        true
    } catch (exception: Exception) {
        false
    }
}