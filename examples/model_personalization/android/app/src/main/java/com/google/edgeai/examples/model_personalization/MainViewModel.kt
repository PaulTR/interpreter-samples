package com.google.edgeai.examples.model_personalization

import android.content.Context
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.launch

class MainViewModel(private val helper: ModelPersonalizationHelper) : ViewModel() {
    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val helper = ModelPersonalizationHelper(context)
                return MainViewModel(helper) as T
            }
        }
    }

    fun addSample(imageProxy: ImageProxy, className: String) {
        viewModelScope.launch {
            helper.addSample(imageProxy, className)
            imageProxy.close()
        }
    }

    fun startTraining() {
        viewModelScope.launch {
            helper.startTraining()
        }
    }
}
