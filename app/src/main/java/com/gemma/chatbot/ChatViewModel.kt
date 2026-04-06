package com.gemma.chatbot

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val gemmaModel = GemmaModel(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotLoaded)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch {
            _modelStatus.value = ModelStatus.Loading

            val modelPath = findModelFile()
            if (modelPath == null) {
                _modelStatus.value = ModelStatus.Error(
                    "Gemma 모델 파일을 찾을 수 없습니다.\n\n" +
                    "다음 경로 중 하나에 .bin 모델 파일을 넣어주세요:\n" +
                    "• /sdcard/Download/\n" +
                    "• /sdcard/Models/\n\n" +
                    "지원 형식: gemma-2b-it-*.bin"
                )
                return@launch
            }

            val result = gemmaModel.loadModel(modelPath)
            result.fold(
                onSuccess = {
                    _modelStatus.value = ModelStatus.Ready
                    addBotMessage("안녕하세요! Gemma 챗봇입니다. 무엇이든 물어보세요.")
                },
                onFailure = { e ->
                    _modelStatus.value = ModelStatus.Error(
                        "모델 로드 실패: ${e.message}"
                    )
                }
            )
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return

        addUserMessage(text)
        _isLoading.value = true

        viewModelScope.launch {
            val result = gemmaModel.generateResponse(text)
            result.fold(
                onSuccess = { response ->
                    addBotMessage(response)
                },
                onFailure = { e ->
                    addBotMessage("오류가 발생했습니다: ${e.message}")
                }
            )
            _isLoading.value = false
        }
    }

    private fun addUserMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(text = text, isUser = true)
    }

    private fun addBotMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(text = text, isUser = false)
    }

    fun retryLoadModel() {
        loadModel()
    }

    private fun findModelFile(): String? {
        val searchDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path),
            File(Environment.getExternalStorageDirectory(), "Models"),
            File(Environment.getExternalStorageDirectory(), "gemma"),
            File("/sdcard/Download"),
            File("/sdcard/Models"),
        )

        for (dir in searchDirs) {
            if (!dir.exists()) continue
            val modelFile = dir.listFiles()?.find { file ->
                file.name.endsWith(".bin") && file.name.contains("gemma", ignoreCase = true)
            }
            if (modelFile != null) return modelFile.absolutePath
        }

        return null
    }

    override fun onCleared() {
        super.onCleared()
        gemmaModel.close()
    }
}

sealed class ModelStatus {
    data object NotLoaded : ModelStatus()
    data object Loading : ModelStatus()
    data object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}
