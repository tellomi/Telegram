package org.telegram.ui.tellomi.feed;

import org.junit.Test;
import org.telegram.ui.TellomiPack;

import static org.junit.Assert.assertEquals;

public class TellomiDiscoverVideoTest {

    @Test
    public void sixtySecondsIsTheInclusiveCap() {
        assertEquals(60, TellomiDiscoverVideo.MAX_SECONDS);
        assertEquals(60_000L, TellomiDiscoverVideo.MAX_DURATION_MS);
        assertEquals(TellomiDiscoverVideo.Decision.ACCEPT, TellomiDiscoverVideo.select(1L));
        assertEquals(TellomiDiscoverVideo.Decision.ACCEPT, TellomiDiscoverVideo.select(60_000L));
        assertEquals(TellomiDiscoverVideo.Decision.TOO_LONG, TellomiDiscoverVideo.select(60_001L));
        assertEquals(TellomiDiscoverVideo.Decision.TOO_LONG, TellomiDiscoverVideo.select(120_000L));
        assertEquals(TellomiDiscoverVideo.Decision.UNKNOWN, TellomiDiscoverVideo.select(0L));
        assertEquals(TellomiDiscoverVideo.Decision.UNKNOWN, TellomiDiscoverVideo.select(-1L));
    }

    @Test
    public void uploadIsBlockedBeforeAnyRequest() {
        assertEquals(TellomiDiscoverVideo.Decision.TOO_LONG, TellomiDiscoverVideo.beforeUpload(60_001L));
        assertEquals(TellomiDiscoverVideo.Decision.UNKNOWN, TellomiDiscoverVideo.beforeUpload(0L));
        assertEquals(TellomiDiscoverVideo.Decision.NOT_UPLOADED, TellomiDiscoverVideo.beforeUpload(60_000L));
        assertEquals(TellomiDiscoverVideo.Decision.NOT_UPLOADED, TellomiDiscoverVideo.beforeUpload(42_000L));
    }

    @Test
    public void gallerySecondsUseTheSameCap() {
        assertEquals(TellomiDiscoverVideo.Decision.ACCEPT, TellomiDiscoverVideo.selectPhotoEntrySeconds(60));
        assertEquals(TellomiDiscoverVideo.Decision.TOO_LONG, TellomiDiscoverVideo.selectPhotoEntrySeconds(61));
        assertEquals(TellomiDiscoverVideo.Decision.UNKNOWN, TellomiDiscoverVideo.selectPhotoEntrySeconds(0));
    }

    @Test
    public void metadataDurationParsesMilliseconds() {
        assertEquals(60_000L, TellomiDiscoverVideo.parseMillis("60000"));
        assertEquals(60_000L, TellomiDiscoverVideo.parseMillis(" 60000.9 "));
        assertEquals(60_001L, TellomiDiscoverVideo.parseMillis("60001"));
        assertEquals(0L, TellomiDiscoverVideo.parseMillis(null));
        assertEquals(0L, TellomiDiscoverVideo.parseMillis(""));
        assertEquals(0L, TellomiDiscoverVideo.parseMillis("-5"));
        assertEquals(0L, TellomiDiscoverVideo.parseMillis("nope"));
    }

    @Test
    public void clockLabelsTheSelectedClip() {
        assertEquals("1:00", TellomiDiscoverVideo.clock(60_000L));
        assertEquals("0:59", TellomiDiscoverVideo.clock(59_000L));
        assertEquals("1:01", TellomiDiscoverVideo.clock(61_000L));
        assertEquals("0:01", TellomiDiscoverVideo.clock(500L));
        assertEquals("0:00", TellomiDiscoverVideo.clock(0L));
    }

    @Test
    public void chineseCopyNamesTheSixtySecondCap() {
        assertEquals("视频最长 60 秒。", TellomiPack.bundled("Tellomi.Discover.VideoHint", "zh-Hans-TW"));
        assertEquals("这段视频超过 60 秒，请换一段更短的。", TellomiPack.bundled("Tellomi.Discover.VideoTooLong", "zh-Hant"));
        assertEquals("读不到这段视频的时长，请换一个。", TellomiPack.bundled("Tellomi.Discover.VideoUnknown", "zh"));
        assertEquals("已选择 · %s", TellomiPack.bundled("Tellomi.Discover.VideoSelected", "zh-CN"));
        assertEquals("发现还不能上传视频，移除后可以发布文字。", TellomiPack.bundled("Tellomi.Discover.VideoNotUploaded", "zh-Hans"));
        assertEquals("Videos can be up to 60 seconds.", TellomiPack.bundled("Tellomi.Discover.VideoHint", "en"));
    }
}
