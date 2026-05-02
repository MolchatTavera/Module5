package com.example.task1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                GalleryApp(activity = this)
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
fun GalleryApp(activity: MainActivity) {
    var images by remember {
        mutableStateOf(loadImages(activity))
    }

    var currentPhotoFile by remember {
        mutableStateOf<File?>(null)
    }

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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
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
            ) {
                Text(text = "+")
            }
        }
    ) { paddingValues ->

        if (images.isEmpty()) {
            EmptyGalleryScreen(
                paddingValues = paddingValues,
                onTakeFirstPhoto = {
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
            )
        } else {
            PhotoGrid(
                paddingValues = paddingValues,
                images = images
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
fun PhotoGrid(
    paddingValues: PaddingValues,
    images: List<File>
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(images) { file ->
            Image(
                painter = rememberAsyncImagePainter(file),
                contentDescription = "Фото",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
                    .height(120.dp)
            )
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