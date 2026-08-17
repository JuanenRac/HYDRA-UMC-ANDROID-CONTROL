// =============================================================================
// HYDRA-UMC CONTROL - Native 3D Visor using Google Filament
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.android.filament.*

/**
 * High-performance native 3D visor.
 * Migrated from WebView to Google Filament for 60 FPS industrial control.
 */
@Composable
fun NativeThreeDScreen(robotId: Int) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Filament engine state
    var engine by remember { mutableStateOf<Engine?>(null) }
    var renderer by remember { mutableStateOf<Renderer?>(null) }
    var scene by remember { mutableStateOf<Scene?>(null) }
    var view by remember { mutableStateOf<View?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var swapChain by remember { mutableStateOf<SwapChain?>(null) }
    
    var isPaused by remember { mutableStateOf(false) }

    // Initialize Filament
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> isPaused = true
                Lifecycle.Event.ON_RESUME -> isPaused = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Load Filament native libraries
        Filament.init()
        
        engine = Engine.create()
        renderer = engine?.createRenderer()
        scene = engine?.createScene()
        view = engine?.createView()
        camera = engine?.createCamera(engine!!.entityManager.create())
        
        view?.scene = scene
        view?.camera = camera
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine?.let { e ->
                swapChain?.let { e.destroySwapChain(it) }
                renderer?.let { e.destroyRenderer(it) }
                scene?.let { e.destroyScene(it) }
                view?.let { e.destroyView(it) }
                camera?.let { e.destroyCameraComponent(it.entity) }
                e.destroy()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        swapChain = engine?.createSwapChain(holder.surface)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        view?.viewport = Viewport(0, 0, width, height)
                        // Simple camera setup
                        val aspect = width.toDouble() / height.toDouble()
                        camera?.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
                        camera?.lookAt(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        // handled in onDispose or here
                    }
                })
            }
        },
        update = { _ ->
            if (isPaused) return@AndroidView
            renderer?.let { r ->
                swapChain?.let { sc ->
                    if (r.beginFrame(sc, System.nanoTime())) {
                        r.render(view!!)
                        r.endFrame()
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
