package cl.mauricio.balatromods;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CatalogClientTest {
    @Test
    public void releasePickerPrefersTheModArchiveOverBundledSteamodded() throws Exception {
        JSONArray assets = new JSONArray()
                .put(new JSONObject()
                        .put("name", "smods-1.0.0-beta.zip")
                        .put("browser_download_url", "https://example.invalid/smods.zip")
                        .put("size", 80_000_000))
                .put(new JSONObject()
                        .put("name", "Pokermon-3.8.0.zip")
                        .put("browser_download_url", "https://example.invalid/pokermon.zip")
                        .put("size", 50_000_000));

        JSONObject selected = CatalogClient.selectReleaseAsset(
                assets,
                "Pokermon",
                "InertSteak/Pokermon"
        );
        assertEquals("Pokermon-3.8.0.zip", selected.optString("name"));
    }

    @Test
    public void releaseFeedProvidesComparableVersionsWhenGithubApiIsRateLimited() {
        String feed = """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <entry><updated>2026-06-13T00:10:22Z</updated>
                    <link rel="alternate" href="https://github.com/InertSteak/Pokermon/releases/tag/3.8.0" />
                  </entry>
                  <entry><updated>2026-04-22T21:11:09Z</updated>
                    <link rel="alternate" href="https://github.com/InertSteak/Pokermon/releases/tag/3.7.0" />
                  </entry>
                </feed>
                """;

        var releases = CatalogClient.parseGithubReleaseFeed("InertSteak/Pokermon", feed);

        assertEquals(2, releases.size());
        assertEquals("3.8.0", releases.get(0).version());
        assertTrue(releases.get(0).downloadUrl().endsWith("/refs/tags/3.8.0.zip"));
    }

    @Test
    public void expandedReleasePageStillPrefersUploadedModPackage() {
        String html = """
                <a href="/InertSteak/Pokermon/releases/download/3.8.0/Pokermon-3.8.0.zip">mod</a>
                <a href="/InertSteak/Pokermon/releases/download/3.8.0/smods-1.0.0-beta.zip">framework</a>
                <a href="/InertSteak/Pokermon/archive/refs/tags/3.8.0.zip">source</a>
                """;

        String selected = CatalogClient.selectExpandedReleaseAsset(
                html,
                "Pokermon",
                "InertSteak/Pokermon"
        );

        assertEquals(
                "https://github.com/InertSteak/Pokermon/releases/download/3.8.0/Pokermon-3.8.0.zip",
                selected
        );
    }
}
