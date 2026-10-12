package com.eplik.ioniq5.car

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.eplik.ioniq5.Ioniq5Application
import com.eplik.ioniq5.R

enum class CarPage(val label: Int) {
    OVERVIEW(R.string.tab_overview),
    BATTERY(R.string.tab_battery),
    WHEELS(R.string.tab_axles),
    CHARGING(R.string.tab_charging),
    SOLAR(R.string.tab_solar),
    CLIMATE(R.string.tab_climate),
}

/** Auto ekraan: navigatsioonimall, mille all joonistame näidikud ise ([CarRenderer]). */
class DashboardScreen(carContext: CarContext) : Screen(carContext) {

    private val renderer = CarRenderer(carContext)

    init {
        lifecycle.addObserver(renderer)
    }

    override fun onGetTemplate(): Template {
        // Navigatsioonimalli tööriistaribal tohib olla vaid üks tekstiga nupp: see vahetab lehti ringiratast
        val next = CarPage.entries[(renderer.page.ordinal + 1) % CarPage.entries.size]
        val pageButton = Action.Builder()
            .setTitle("${carContext.getString(renderer.page.label)} ›")
            .setOnClickListener {
                renderer.page = next
                renderer.draw()
                invalidate()
            }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(pageButton).build())
            .build()
    }
}

/** Joonistab valitud lehe auto pinnale kaks korda sekundis, kuni ekraan on nähtav. */
class CarRenderer(private val carContext: CarContext) : DefaultLifecycleObserver {

    var page = CarPage.OVERVIEW

    /** Aku lehel: false = elementide pinged, true = moodulite temperatuurid (vahetub puudutusega). */
    var batteryModules = false

    private val app = carContext.applicationContext as Ioniq5Application
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var visibleArea: Rect? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            draw()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    private val callback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            surface = surfaceContainer.surface
            width = surfaceContainer.width
            height = surfaceContainer.height
            draw()
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            this@CarRenderer.visibleArea = visibleArea
            draw()
        }

        override fun onStableAreaChanged(stableArea: Rect) = Unit

        // Puudutus joonistuspinnal (Car API 5+): aku lehel vahetab elementide ja moodulite vaate
        override fun onClick(x: Float, y: Float) {
            if (page == CarPage.BATTERY) {
                batteryModules = !batteryModules
                draw()
            }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            surface = null
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback)
    }

    override fun onStart(owner: LifecycleOwner) {
        handler.post(tick)
    }

    override fun onStop(owner: LifecycleOwner) {
        handler.removeCallbacks(tick)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        handler.removeCallbacks(tick)
        surface = null
    }

    fun draw() {
        val s = surface ?: return
        if (!s.isValid || width == 0 || height == 0) return
        val canvas = try {
            s.lockCanvas(null)
        } catch (e: Exception) {
            return
        }
        try {
            val area = visibleArea?.takeIf { !it.isEmpty } ?: Rect(0, 0, width, height)
            CarDrawing(carContext, carContext.isDarkMode).draw(
                canvas,
                area,
                page,
                app.connection.readings.value,
                app.connection.status.value,
                app.connection.sleeping.value,
                app.settings.settings.value,
                app.alerts.alerts.value,
                app.connection.solarEnergyWh.value,
                batteryModules,
            )
        } finally {
            s.unlockCanvasAndPost(canvas)
        }
    }

    private companion object {
        const val FRAME_MS = 500L
    }
}
