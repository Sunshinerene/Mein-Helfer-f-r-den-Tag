package de.meinhelfer.sunny.launcher.idea;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IsolationTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void manifestVisibilityIsLimitedToNormalLaunchableActivities() throws Exception {
        Element manifest = readXml("AndroidManifest.xml");
        Element queries = onlyChild(manifest, "queries");
        assertLauncherIntent(onlyChild(queries, "intent"));
        assertEquals(List.of("intent"), tags(queries));
    }

    @Test
    public void onlyNormalEntryPointIsExportedAndManifestContainsNoPermissions() throws Exception {
        Element manifest = readXml("AndroidManifest.xml");
        assertFalse(manifest.hasAttributeNS(ANDROID, "sharedUserId"));
        assertEquals(List.of("queries", "application"), tags(manifest));

        Element app = onlyChild(manifest, "application");
        assertEquals(List.of("activity"), tags(app));
        Element activity = onlyChild(app, "activity");
        assertEquals(".MainActivity", activity.getAttributeNS(ANDROID, "name"));
        assertEquals("true", activity.getAttributeNS(ANDROID, "exported"));
        assertEquals(List.of("intent-filter"), tags(activity));
        assertLauncherIntent(onlyChild(activity, "intent-filter"));
    }

    @Test
    public void prototypePreferencesAreExcludedFromBackupsAndTransfers() throws Exception {
        Element app = onlyChild(readXml("AndroidManifest.xml"), "application");
        assertEquals("false", app.getAttributeNS(ANDROID, "allowBackup"));
        assertEquals("false", app.getAttributeNS(ANDROID, "fullBackupContent"));
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(ANDROID, "dataExtractionRules"));
        Element rules = readXml("res/xml/data_extraction_rules.xml");
        for (String type : List.of("cloud-backup", "device-transfer")) {
            Element exclusion = onlyChild(onlyChild(rules, type), "exclude");
            assertEquals("sharedpref", exclusion.getAttribute("domain"));
            assertEquals(".", exclusion.getAttribute("path"));
        }
    }

    @Test
    public void assistantCardHasOnlyTextAndAnExplicitPlaceholderMarker() throws Exception {
        Element card = readXml("res/layout/assistant_placeholder.xml");
        assertEquals("LinearLayout", card.getTagName());
        assertEquals(List.of("TextView", "TextView", "TextView"), tags(card));
        assertEquals(List.of("@string/assistant_placeholder", "@string/assistant_title",
                        "@string/assistant_explanation"), children(card).stream()
                .map(view -> view.getAttributeNS(ANDROID, "text")).toList());
        for (Element view : children(card)) {
            assertFalse(view.hasAttributeNS(ANDROID, "onClick"));
            assertFalse(view.hasAttributeNS(ANDROID, "clickable"));
        }
    }

    @Test
    public void bothLanguagesIdentifyAssistantAndSyntheticFallback() throws Exception {
        verifyCopy("values", "placeholder", "example", "unavailable");
        verifyCopy("values-de", "platzhalter", "beispiel", "nicht verfügbar");
    }

    private static void verifyCopy(String folder, String placeholder, String example, String disabled)
            throws Exception {
        Element strings = readXml("res/" + folder + "/strings.xml");
        assertEquals(placeholder, string(strings, "assistant_placeholder"));
        assertTrue(string(strings, "assistant_explanation").contains(disabled));
        assertTrue(string(strings, "examples_title").contains(example));
        assertTrue(string(strings, "example_camera").contains(example));
        assertTrue(string(strings, "example_gallery").contains(example));
    }

    private static String string(Element resources, String name) {
        return children(resources).stream().filter(element -> name.equals(element.getAttribute("name")))
                .findFirst().orElseThrow().getTextContent().toLowerCase(Locale.ROOT);
    }

    private static void assertLauncherIntent(Element intent) {
        assertEquals(List.of("action", "category"), tags(intent));
        assertEquals("android.intent.action.MAIN", onlyChild(intent, "action").getAttributeNS(ANDROID, "name"));
        assertEquals("android.intent.category.LAUNCHER",
                onlyChild(intent, "category").getAttributeNS(ANDROID, "name"));
    }

    private static Element readXml(String path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(Path.of("src/main", path).toFile()).getDocumentElement();
    }

    private static List<String> tags(Element root) {
        return children(root).stream().map(Element::getTagName).toList();
    }

    private static Element onlyChild(Element root, String tag) {
        List<Element> matches = children(root).stream().filter(node -> node.getTagName().equals(tag)).toList();
        assertEquals(tag, 1, matches.size());
        return matches.get(0);
    }

    private static List<Element> children(Element root) {
        List<Element> result = new ArrayList<>();
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) result.add(element);
        }
        return result;
    }
}
