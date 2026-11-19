import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Checks actual TV pixels, not just isDisplayed(). Inputs: idle XML/PNG, focused XML/PNG. */
class SceneButtonVisibilityCheck {
    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
    }

    private static double check(String xml, String png, boolean focused) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList nodes = factory.newDocumentBuilder().parse(new File(xml)).getElementsByTagName("node");
        BufferedImage image = ImageIO.read(new File(png));
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            if (!e.getAttribute("resource-id").endsWith("/viewing_scene")) continue;
            if (e.getAttribute("text").isBlank() || focused != Boolean.parseBoolean(e.getAttribute("focused")))
                throw new AssertionError("Missing label or wrong D-pad focus state: " + xml);
            Matcher bounds = Pattern.compile("\\d+").matcher(e.getAttribute("bounds"));
            int[] b = bounds.results().mapToInt(m -> Integer.parseInt(m.group())).toArray();
            int w = b[2] - b[0], h = b[3] - b[1];
            // Sample the label interior, excluding rounded corners, borders and the animated backdrop.
            int mx = w / 8, my = h / 10;
            double[] samples = new double[(w - 2 * mx) * (h - 2 * my)];
            double[] rowLight = new double[h - 2 * my];
            int j = 0;
            for (int y = b[1] + my; y < b[3] - my; y++) for (int x = b[0] + mx; x < b[2] - mx; x++) {
                int rgb = image.getRGB(x, y);
                samples[j++] = .2126 * linear(rgb >> 16 & 255) + .7152 * linear(rgb >> 8 & 255) + .0722 * linear(rgb & 255);
                rowLight[y - b[1] - my] += samples[j - 1];
            }
            Arrays.sort(samples);
            double contrast = (samples[j * 98 / 100] + .05) / (samples[j * 2 / 100] + .05);
            System.out.printf("focused=%s, rendered label contrast=%.2f:1%n", focused, contrast);
            if (contrast < 4.5) throw new AssertionError("Scene label is not legible");
            // Measure where the rendered glyphs sit, not just where the focus target sits.
            double surface = samples[j / 2], ink = 0, moment = 0;
            for (int r = 0; r < rowLight.length; r++) {
                double weight = Math.abs(rowLight[r] - surface * (w - 2 * mx));
                ink += weight;
                moment += weight * (r + my + 0.5);
            }
            double center = moment / (ink * h);
            System.out.printf("rendered label center=%.0f%% of button height%n", 100 * center);
            if (!(Math.abs(center - 0.5) <= 0.14)) throw new AssertionError("Label is not vertically centered");
            return surface;
        }
        throw new AssertionError("No scene action: " + xml);
    }

    public static void main(String[] args) throws Exception {
        double idle = check(args[0], args[1], false), focused = check(args[2], args[3], true);
        if (Math.abs(idle - focused) < .10) throw new AssertionError("Focus highlight is not visually distinct");
    }
}
