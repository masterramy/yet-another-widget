from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    data = p.read_text(encoding="utf-8")
    count = data.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, found {count}")
    p.write_text(data.replace(old, new, 1), encoding="utf-8")


manifest = "app/src/main/AndroidManifest.xml"
replace_once(
    manifest,
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n',
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n'
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />\n',
)

weather_helper = "app/src/main/java/com/tommasoberlose/anotherwidget/helpers/WeatherHelper.kt"
replace_once(
    weather_helper,
    'import android.Manifest\nimport android.content.Context\n',
    'import android.Manifest\nimport android.app.Activity\nimport android.content.Context\n',
)
replace_once(
    weather_helper,
    '''    suspend fun updateWeather(context: Context) {
        Kotpref.init(context)
        val networkApi = WeatherNetworkApi(context)
        if (Preferences.customLocationAdd != "") {
            networkApi.updateWeather()
        } else if (context.checkGrantedPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            LocationService.requestNewLocation(context)
        }
    }
''',
    '''    suspend fun updateWeather(context: Context) {
        Kotpref.init(context)
        val networkApi = WeatherNetworkApi(context)
        if (Preferences.customLocationAdd != "") {
            networkApi.updateWeather()
        } else if (context is Activity && context.checkGrantedPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Refresh device coordinates only from a visible app surface. Scheduled/background
            // refreshes reuse the last coordinates instead of starting a location FGS.
            LocationService.requestNewLocation(context)
        } else if (Preferences.customLocationLat != "" && Preferences.customLocationLon != "") {
            networkApi.updateWeather()
        }
    }
''',
)

custom_location = "app/src/main/java/com/tommasoberlose/anotherwidget/ui/activities/tabs/CustomLocationActivity.kt"
replace_once(
    custom_location,
    'import com.tommasoberlose.anotherwidget.global.Preferences\n',
    'import com.tommasoberlose.anotherwidget.global.Preferences\n'
    'import com.tommasoberlose.anotherwidget.services.LocationService\n',
)
replace_once(
    custom_location,
    '''                            Preferences.bulk {
                                remove(Preferences::customLocationLat)
                                remove(Preferences::customLocationLon)
                                remove(Preferences::customLocationAdd)
                            }
                            setResult(Activity.RESULT_OK)
                            finish()
''',
    '''                            Preferences.bulk {
                                remove(Preferences::customLocationLat)
                                remove(Preferences::customLocationLon)
                                remove(Preferences::customLocationAdd)
                            }
                            // GPS mode is user-selected here while this Activity is visible, so
                            // start the one-shot location service before returning to settings.
                            LocationService.requestNewLocation(this@CustomLocationActivity)
                            setResult(Activity.RESULT_OK)
                            finish()
''',
)

location_service = "app/src/main/java/com/tommasoberlose/anotherwidget/services/LocationService.kt"
replace_once(
    location_service,
    '''    override fun onCreate() {
        super.onCreate()
        startForeground(LOCATION_ACCESS_NOTIFICATION_ID, getLocationAccessNotification())
    }
''',
    '''    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            LOCATION_ACCESS_NOTIFICATION_ID,
            getLocationAccessNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }
''',
)
replace_once(
    location_service,
    '''    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(LOCATION_ACCESS_NOTIFICATION_ID, getLocationAccessNotification())
        job?.cancel()
''',
    '''    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        job?.cancel()
''',
)
replace_once(
    location_service,
    '        return START_STICKY\n',
    '        return START_NOT_STICKY\n',
)
replace_once(
    location_service,
    'builder.setContentIntent(PendingIntent.getActivity(this@LocationService, 0, Intent(this@LocationService, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT))',
    'builder.setContentIntent(PendingIntent.getActivity(this@LocationService, 0, Intent(this@LocationService, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))',
)

# Exact postconditions for the bounded compatibility/safety contract.
manifest_text = Path(manifest).read_text(encoding="utf-8")
if manifest_text.count("android.permission.FOREGROUND_SERVICE_LOCATION") != 1:
    raise SystemExit("location FGS permission contract missing or duplicated")
if 'android:foregroundServiceType="location"' not in manifest_text:
    raise SystemExit("location FGS type contract missing")

helper_text = Path(weather_helper).read_text(encoding="utf-8")
if "context is Activity && context.checkGrantedPermission" not in helper_text:
    raise SystemExit("foreground-only location refresh guard missing")
if 'Preferences.customLocationLat != "" && Preferences.customLocationLon != ""' not in helper_text:
    raise SystemExit("cached-coordinate background path missing")

custom_text = Path(custom_location).read_text(encoding="utf-8")
if "LocationService.requestNewLocation(this@CustomLocationActivity)" not in custom_text:
    raise SystemExit("GPS selection no longer primes location while visible")

service_text = Path(location_service).read_text(encoding="utf-8")
for required in (
    "ServiceCompat.startForeground(",
    "FOREGROUND_SERVICE_TYPE_LOCATION",
    "return START_NOT_STICKY",
    "PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE",
):
    if required not in service_text:
        raise SystemExit(f"LocationService postcondition missing: {required}")
if "return START_STICKY" in service_text:
    raise SystemExit("sticky location service behavior remains")

print("Location FGS hardening applied: foreground-only reacquisition, cached background weather coordinates, explicit type permission, non-sticky service, immutable notification PendingIntent.")
