package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * URI builders and the WGS-84 to GCJ-02 conversion used when opening an
 * installed map app. Tiananmen is inside the China bounding box; Paris is not.
 */
public class TellomiMapUrisTest {

    private static final double TIANANMEN_LAT = 39.9087;
    private static final double TIANANMEN_LON = 116.3975;

    @Test
    public void tiananmenWgs84ConvertsToGcj02() {
        TellomiMapUris.LatLng gcj = TellomiMapUris.wgs84ToGcj02(TIANANMEN_LAT, TIANANMEN_LON);
        assertEquals(39.9101034993, gcj.latitude, 1e-6);
        assertEquals(116.4037435727, gcj.longitude, 1e-6);
        assertTrue(gcj.latitude > TIANANMEN_LAT);
        assertTrue(gcj.longitude > TIANANMEN_LON);
        assertFalse(TellomiMapUris.outOfChina(TIANANMEN_LAT, TIANANMEN_LON));
    }

    @Test
    public void pointsOutsideChinaStayOnWgs84() {
        TellomiMapUris.LatLng paris = TellomiMapUris.wgs84ToGcj02(48.8566, 2.3522);
        assertEquals(48.8566, paris.latitude, 0.0);
        assertEquals(2.3522, paris.longitude, 0.0);
        assertTrue(TellomiMapUris.outOfChina(48.8566, 2.3522));

        TellomiMapUris.LatLng sydney = TellomiMapUris.wgs84ToGcj02(-33.8688, 151.2093);
        assertEquals(-33.8688, sydney.latitude, 0.0);
        assertEquals(151.2093, sydney.longitude, 0.0);

        assertTrue(TellomiMapUris.outOfChina(39.9, 72.003));
        assertFalse(TellomiMapUris.outOfChina(39.9, 72.004));
        assertTrue(TellomiMapUris.outOfChina(0.8292, 116.4));
        assertFalse(TellomiMapUris.outOfChina(0.8293, 116.4));
    }

    @Test
    public void amapAndTencentAndPetalUseGcj02() {
        TellomiMapUris.LatLng gcj = TellomiMapUris.wgs84ToGcj02(TIANANMEN_LAT, TIANANMEN_LON);
        String gcjLat = TellomiMapUris.formatCoord(gcj.latitude);
        String gcjLon = TellomiMapUris.formatCoord(gcj.longitude);
        String wgsLat = TellomiMapUris.formatCoord(TIANANMEN_LAT);
        String wgsLon = TellomiMapUris.formatCoord(TIANANMEN_LON);

        String amapView = TellomiMapUris.uriFor(TellomiMapUris.AMAP, TIANANMEN_LAT, TIANANMEN_LON, "天安门", false);
        assertEquals("androidamap://viewMap?sourceApplication=Tellomi&poiname=" + TellomiMapUris.encode("天安门")
                + "&lat=" + gcjLat + "&lon=" + gcjLon + "&dev=0", amapView);
        assertFalse(amapView.contains(wgsLat));
        assertTrue(amapView.contains("dev=0"));

        String amapDirections = TellomiMapUris.uriFor(TellomiMapUris.AMAP, TIANANMEN_LAT, TIANANMEN_LON, "天安门", true);
        assertEquals("amapuri://route/plan/?sourceApplication=Tellomi&dlat=" + gcjLat + "&dlon=" + gcjLon
                + "&dname=" + TellomiMapUris.encode("天安门") + "&dev=0&t=0", amapDirections);

        String tencentView = TellomiMapUris.uriFor(TellomiMapUris.TENCENT, TIANANMEN_LAT, TIANANMEN_LON, "天安门", false);
        assertEquals("qqmap://map/marker?marker=coord:" + gcjLat + "," + gcjLon + ";title:"
                + TellomiMapUris.encode("天安门") + "&referer=Tellomi", tencentView);
        assertFalse(tencentView.contains(wgsLon));

        String tencentDirections = TellomiMapUris.uriFor(TellomiMapUris.TENCENT, TIANANMEN_LAT, TIANANMEN_LON, "天安门", true);
        assertEquals("qqmap://map/routeplan?type=drive&fromcoord=CurrentLocation&to="
                + TellomiMapUris.encode("天安门") + "&tocoord=" + gcjLat + "," + gcjLon + "&referer=Tellomi", tencentDirections);

        String petalView = TellomiMapUris.uriFor(TellomiMapUris.PETAL, TIANANMEN_LAT, TIANANMEN_LON, "天安门", false);
        assertEquals("petalmaps://poidetail?marker=" + gcjLat + "," + gcjLon + "&utm_source=Tellomi", petalView);
        String petalDirections = TellomiMapUris.uriFor(TellomiMapUris.PETAL, TIANANMEN_LAT, TIANANMEN_LON, "天安门", true);
        assertEquals("petalmaps://navigation?daddr=" + gcjLat + "," + gcjLon + "&type=drive&utm_source=Tellomi", petalDirections);
    }

    @Test
    public void baiduGoogleAndWebKeepWgs84() {
        String wgsLat = TellomiMapUris.formatCoord(TIANANMEN_LAT);
        String wgsLon = TellomiMapUris.formatCoord(TIANANMEN_LON);
        TellomiMapUris.LatLng gcj = TellomiMapUris.wgs84ToGcj02(TIANANMEN_LAT, TIANANMEN_LON);
        String gcjLat = TellomiMapUris.formatCoord(gcj.latitude);

        String baiduView = TellomiMapUris.uriFor(TellomiMapUris.BAIDU, TIANANMEN_LAT, TIANANMEN_LON, "Tiananmen", false);
        assertEquals("baidumap://map/marker?location=" + wgsLat + "," + wgsLon
                + "&title=Tiananmen&coord_type=wgs84&src=andr.tellomi.chat", baiduView);
        assertFalse(baiduView.contains(gcjLat));

        String baiduDirections = TellomiMapUris.uriFor(TellomiMapUris.BAIDU, TIANANMEN_LAT, TIANANMEN_LON, "Tiananmen", true);
        assertEquals("baidumap://map/direction?destination=latlng:" + wgsLat + "," + wgsLon
                + "|name:Tiananmen&coord_type=wgs84&mode=driving&src=andr.tellomi.chat", baiduDirections);

        String googleView = TellomiMapUris.uriFor(TellomiMapUris.GOOGLE, TIANANMEN_LAT, TIANANMEN_LON, "Tiananmen", false);
        assertEquals("geo:" + wgsLat + "," + wgsLon + "?q=" + wgsLat + "," + wgsLon + "(Tiananmen)", googleView);
        String googleDirections = TellomiMapUris.uriFor(TellomiMapUris.GOOGLE, TIANANMEN_LAT, TIANANMEN_LON, "Tiananmen", true);
        assertEquals("google.navigation:q=" + wgsLat + "," + wgsLon, googleDirections);

        String geo = TellomiMapUris.geoUri(TIANANMEN_LAT, TIANANMEN_LON, null);
        assertEquals("geo:" + wgsLat + "," + wgsLon + "?q=" + wgsLat + "," + wgsLon, geo);

        String web = TellomiMapUris.webFallback(TIANANMEN_LAT, TIANANMEN_LON, "Tiananmen");
        assertEquals("https://uri.amap.com/marker?position=" + wgsLon + "," + wgsLat
                + "&name=Tiananmen&coordinate=wgs84&src=tellomi", web);
        assertFalse(web.contains(gcjLat));
    }

    @Test
    public void outsideChinaAmapKeepsTheOriginalPoint() {
        String view = TellomiMapUris.uriFor(TellomiMapUris.AMAP, 48.8566, 2.3522, "Paris", false);
        assertEquals("androidamap://viewMap?sourceApplication=Tellomi&poiname=Paris&lat=48.856600&lon=2.352200&dev=0", view);
        String directions = TellomiMapUris.uriFor(TellomiMapUris.TENCENT, 48.8566, 2.3522, "", true);
        assertEquals("qqmap://map/routeplan?type=drive&fromcoord=CurrentLocation&tocoord=48.856600,2.352200&referer=Tellomi", directions);
    }

    @Test
    public void namesAreEncodedAndUnknownPackagesHaveNoPrivateScheme() {
        assertEquals("A%20%26%20B", TellomiMapUris.encode("A & B"));
        String amap = TellomiMapUris.uriFor(TellomiMapUris.AMAP, TIANANMEN_LAT, TIANANMEN_LON, "A & B", false);
        assertTrue(amap.contains("poiname=A%20%26%20B"));
        assertFalse(amap.contains("A & B"));
        String baidu = TellomiMapUris.uriFor(TellomiMapUris.BAIDU, TIANANMEN_LAT, TIANANMEN_LON, "a|b", true);
        assertTrue(baidu.contains("|name:a%20b"));
        assertNull(TellomiMapUris.uriFor("com.example.other", TIANANMEN_LAT, TIANANMEN_LON, "x", false));
        assertNull(TellomiMapUris.uriFor(null, TIANANMEN_LAT, TIANANMEN_LON, "x", true));
    }

    @Test
    public void callSitesAndManifestAskForInstalledMapApps() throws Exception {
        String chat = read("src/main/java/org/telegram/ui/ChatActivity.java");
        String location = read("src/main/java/org/telegram/ui/LocationActivity.java");
        String manifest = read("src/main/AndroidManifest.xml");

        assertFalse(chat.contains("isMapsInstalled(ChatActivity.this)"));
        assertTrue(chat.contains("TellomiMapApps.inAppMapCanRender()"));
        assertTrue(chat.contains("TellomiMapApps.showMapAppChooser(ChatActivity.this, media.geo.lat, media.geo._long, placeName, false)"));
        assertTrue(chat.contains("TellomiMapApps.showMapAppChooser(ChatActivity.this, geo.lat, geo._long, placeName, false)"));
        assertFalse(location.contains("maps.google.com"));
        assertFalse(location.contains("mapapp://navigation"));
        assertFalse(location.contains("geo:\" + lat"));
        assertTrue(location.contains("TellomiMapApps.showMapAppChooser(this, lat, lon, mapPlaceName(), false)"));
        assertTrue(location.contains("TellomiMapApps.showMapAppChooser(this, daddrLat, daddrLong, name, true)"));

        assertTrue(manifest.contains("<package android:name=\"com.autonavi.minimap\"/>"));
        assertTrue(manifest.contains("<package android:name=\"com.baidu.BaiduMap\"/>"));
        assertTrue(manifest.contains("<package android:name=\"com.tencent.map\"/>"));
        assertTrue(manifest.contains("<package android:name=\"com.huawei.maps.app\"/>"));
        assertTrue(manifest.contains("<package android:name=\"com.google.android.apps.maps\"/>"));
        assertTrue(manifest.contains("<data android:scheme=\"geo\"/>"));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.isRegularFile(path)) {
            path = Paths.get("TMessagesProj").resolve(relative);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
