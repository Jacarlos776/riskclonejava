package com.mykogroup.riskclone.view;

import javafx.scene.input.MouseButton;
import javafx.scene.shape.SVGPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SvgMapLoader {

    // We no longer use a static STYLE_NORMAL. We generate it dynamically.
    public static String generateStyle(String hexFill, boolean isSelected, boolean isHovered) {
        String strokeColor = isSelected ? "#ffffff" : "#555555"; // White border if selected
        int strokeWidth = (isSelected || isHovered) ? 3 : 1;     // Thicker border if active

        // If hovered but not selected, we might want to slightly lighten the base color.
        // For now, we'll keep it simple and just rely on the thicker border to show hover.
        return String.format("-fx-fill: %s; -fx-stroke: %s; -fx-stroke-width: %d;",
                hexFill, strokeColor, strokeWidth);
    }

    // Original overload — game code calls this, behaviour unchanged
    public static Map<String, SVGPath> loadMap(String resourcePath, Consumer<SVGPath> onProvinceClicked) {
        return loadMap(resourcePath, onProvinceClicked, new HashMap<>());
    }

    // Extended overload — also populates outDisplayNames (id → human-readable title from SVG)
    public static Map<String, SVGPath> loadMap(String resourcePath, Consumer<SVGPath> onProvinceClicked,
                                               Map<String, String> outDisplayNames) {
        Map<String, SVGPath> provinceNodes = new HashMap<>();

        try (InputStream is = SvgMapLoader.class.getResourceAsStream(resourcePath)) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
            NodeList pathList = document.getElementsByTagName("path");

            for (int i = 0; i < pathList.getLength(); i++) {
                Element pathElement = (Element) pathList.item(i);
                String id = pathElement.getAttribute("id");
                String d = pathElement.getAttribute("d");

                if (!id.isEmpty() && !d.isEmpty() && id.startsWith("PH-")) {
                    // Extract the <title> child element for display name
                    org.w3c.dom.NodeList children = pathElement.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        org.w3c.dom.Node child = children.item(j);
                        if ("title".equals(child.getNodeName())) {
                            outDisplayNames.put(id, child.getTextContent().trim());
                            break;
                        }
                    }

                    SVGPath svgPath = new SVGPath();
                    svgPath.setContent(d);
                    svgPath.setId(id);

                    // Initialize properties
                    svgPath.getProperties().put("selected", false);
                    svgPath.getProperties().put("baseColor", ColorManager.NEUTRAL_COLOR);

                    // Set initial neutral style
                    svgPath.setStyle(generateStyle(ColorManager.NEUTRAL_COLOR, false, false));

                    // --- UPDATED HOVER LOGIC ---
                    svgPath.setOnMouseEntered(event -> {
                        boolean isSelected = (boolean) svgPath.getProperties().get("selected");
                        String baseColor = (String) svgPath.getProperties().get("baseColor");
                        svgPath.setStyle(generateStyle(baseColor, isSelected, true));
                        svgPath.toFront();
                    });

                    svgPath.setOnMouseExited(event -> {
                        boolean isSelected = (boolean) svgPath.getProperties().get("selected");
                        String baseColor = (String) svgPath.getProperties().get("baseColor");
                        svgPath.setStyle(generateStyle(baseColor, isSelected, false));
                    });

                    // --- CLICK LOGIC ---
                    svgPath.setOnMouseClicked(event -> {
                        if (event.getButton() == MouseButton.PRIMARY) {
                            onProvinceClicked.accept(svgPath);
                        }
                    });

                    provinceNodes.put(id, svgPath);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return provinceNodes;
    }

    // Helper method to update a node's selection state cleanly
    public static void setNodeSelected(SVGPath node, boolean selected) {
        node.getProperties().put("selected", selected);
        String baseColor = (String) node.getProperties().get("baseColor");
        node.setStyle(generateStyle(baseColor, selected, false));
    }
}
