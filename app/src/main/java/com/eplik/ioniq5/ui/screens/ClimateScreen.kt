package com.eplik.ioniq5.ui.screens

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eplik.ioniq5.R
import com.eplik.ioniq5.car.CabinDrawing
import com.eplik.ioniq5.car.CabinState
import com.eplik.ioniq5.car.UserPhotos
import com.eplik.ioniq5.core.pid.Ioniq5Pids as P
import com.eplik.ioniq5.core.poll.Readings
import com.eplik.ioniq5.ui.components.ValueTile
import com.eplik.ioniq5.ui.components.formatValue
import com.eplik.ioniq5.ui.components.isFresh
import com.eplik.ioniq5.ui.components.rememberNow

/**
 * Kliima: Ioniq 5 salong pealtvaates (esiots vasakul, juht all), sama joonis nagu Android Autos
 * ([CabinDrawing]). Soojendused on värviga näha; all on temperatuurid ja niiskus.
 */
@Composable
fun ClimateScreen(readings: Readings, modifier: Modifier = Modifier) {
    val now by rememberNow()
    fun v(id: String) = readings[id]?.takeIf { it.isFresh(now) }?.value
    fun level(id: String) = v(id)?.toInt()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val photo = UserPhotos.load(LocalContext.current.getExternalFilesDir(null), UserPhotos.CABIN)
    val heaters = CabinState(
        driverSeat = level(P.SEAT_HEAT_DRIVER),
        passengerSeat = level(P.SEAT_HEAT_PASSENGER),
        rearLeftSeat = level(P.SEAT_HEAT_REAR_LEFT),
        rearRightSeat = level(P.SEAT_HEAT_REAR_RIGHT),
        driverVent = level(P.SEAT_VENT_DRIVER),
        passengerVent = level(P.SEAT_VENT_PASSENGER),
        steering = level(P.STEERING_HEAT),
        windshield = level(P.WINDSHIELD_HEAT),
        rearWindow = level(P.REAR_WINDOW_HEAT),
        mirrors = level(P.MIRROR_HEAT),
    )
    val cabin = v(P.CABIN_TEMP)
    val humidity = v(P.CABIN_HUMIDITY)
    val outdoor = v(P.OUTDOOR_TEMP)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.climate_title), style = MaterialTheme.typography.displayMedium)
        Canvas(Modifier.fillMaxWidth().aspectRatio(2f)) {
            drawIntoCanvas { canvas ->
                CabinDrawing(dark).draw(canvas.nativeCanvas, RectF(0f, 0f, size.width, size.height), heaters, photo)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(stringResource(R.string.cabin_temp), formatValue(cabin, 1), "°C", cabin != null, Modifier.weight(1f))
            ValueTile(
                stringResource(R.string.cabin_humidity),
                formatValue(humidity, 0),
                "%",
                humidity != null,
                Modifier.weight(1f),
                detail = stringResource(R.string.candidate),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(stringResource(R.string.outdoor_temp), formatValue(outdoor, 1), "°C", outdoor != null, Modifier.weight(1f))
            ValueTile(
                stringResource(R.string.outdoor_humidity),
                "—",
                "%",
                false,
                Modifier.weight(1f),
                detail = stringResource(R.string.outdoor_humidity_none),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(
                stringResource(R.string.set_temp),
                "${formatValue(v(P.SET_TEMP_DRIVER), 1)} / ${formatValue(v(P.SET_TEMP_PASSENGER), 1)}",
                "°C",
                v(P.SET_TEMP_DRIVER) != null,
                Modifier.weight(1f),
            )
            ValueTile(
                stringResource(R.string.evaporator_temp),
                formatValue(v(P.EVAPORATOR_TEMP), 1),
                "°C",
                v(P.EVAPORATOR_TEMP) != null,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(
                stringResource(R.string.vent_temp),
                "${formatValue(v(P.VENT_TEMP_1), 0)} / ${formatValue(v(P.VENT_TEMP_2), 0)}",
                "°C",
                v(P.VENT_TEMP_1) != null,
                Modifier.weight(1f),
                detail = stringResource(R.string.candidate),
            )
            ValueTile(
                stringResource(R.string.steering_heat),
                stringResource(if ((heaters.steering ?: 0) > 0) R.string.on else R.string.off),
                "",
                heaters.steering != null,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ValueTile(
                stringResource(R.string.ac_compressor),
                formatValue(v(P.AC_COMPRESSOR_RPM), 0),
                "rpm",
                v(P.AC_COMPRESSOR_RPM) != null,
                Modifier.weight(1f),
                detail = stringResource(R.string.candidate),
            )
            ValueTile(
                stringResource(R.string.hvac_coolant_temp),
                formatValue(v(P.HVAC_COOLANT_TEMP), 1),
                "°C",
                v(P.HVAC_COOLANT_TEMP) != null,
                Modifier.weight(1f),
                detail = stringResource(R.string.candidate),
            )
        }
        Text(
            stringResource(R.string.climate_heaters_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
