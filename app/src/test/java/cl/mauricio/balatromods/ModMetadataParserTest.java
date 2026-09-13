package cl.mauricio.balatromods;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModMetadataParserTest {
    @Test
    public void parsesThunderstoreManifest() {
        String json = """
                {
                  "name": "MobileLikeDragging",
                  "version_number": "2.0.1",
                  "description": "Dragging support",
                  "website_url": "https://example.test",
                  "dependencies": []
                }
                """;

        ModMetadataParser.ParsedMetadata result =
                ModMetadataParser.parse(json, "fallback");

        assertEquals("MobileLikeDragging", result.name());
        assertEquals("2.0.1", result.version());
        assertEquals("Dragging support", result.description());
        assertEquals("https://example.test", result.website());
    }

    @Test
    public void parsesAuthorArrayAndFallsBackOnInvalidJson() {
        ModMetadataParser.ParsedMetadata arrayResult =
                ModMetadataParser.parse(
                        "{\"name\":\"Zoomer\",\"author\":[\"Tyvation\",\"Helper\"]}",
                        "fallback"
                );
        assertEquals("Tyvation, Helper", arrayResult.author());

        ModMetadataParser.ParsedMetadata invalidResult =
                ModMetadataParser.parse("{broken", "FolderName");
        assertEquals("FolderName", invalidResult.name());
        assertTrue(!invalidResult.valid());
    }

    @Test
    public void preservesDependencyVersionRequirements() {
        ModMetadataParser.ParsedMetadata result = ModMetadataParser.parse(
                "{\"id\":\"Agarmons\",\"dependencies\":[\"Pokermon (>=3.8.1-0731b)\"]}",
                "Agarmons"
        );

        assertEquals("Pokermon (>=3.8.1-0731b)", result.dependencies().get(0));
    }

    @Test
    public void filtersByStatusAndText() {
        ModEntry active = entry("Zoomer", false);
        ModEntry hidden = entry("Paperback", true);

        assertTrue(ModFilter.matches(active, "zoom", ModFilter.Status.ACTIVE));
        assertTrue(ModFilter.matches(hidden, "paper", ModFilter.Status.HIDDEN));
        assertTrue(!ModFilter.matches(hidden, "", ModFilter.Status.ACTIVE));
    }

    private static ModEntry entry(String name, boolean hidden) {
        return new ModEntry(
                name,
                name,
                name,
                "1.0",
                "Autor",
                "Description",
                "",
                "manifest.json",
                hidden,
                1,
                0,
                java.util.List.of(),
                java.util.List.of(),
                "ok",
                null
        );
    }
}
