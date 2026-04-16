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

    // Define standard colors as constants for easy tweaking
    public static final String STYLE_NORMAL = "-fx-fill: #c2d5a8; -fx-stroke: #555555; -fx-stroke-width: 1;";
    public static final String STYLE_HOVER = "-fx-fill: #d3e5b9; -fx-stroke: #ffffff; -fx-stroke-width: 2.5;";
    public static final String STYLE_SELECTED = "-fx-fill: #f4a460; -fx-stroke: #8b4513; -fx-stroke-width: 2;"; // Amber/Gold
    public static final String STYLE_SELECTED_HOVER = "-fx-fill: #ffb470; -fx-stroke: #ffffff; -fx-stroke-width: 2.5;";

    public static Map<String, SVGPath> loadMap(String resourcePath, Consumer<SVGPath> onProvinceClicked) {
        Map<String, SVGPath> provinceNodes = new HashMap<>();

        try (InputStream is = SvgMapLoader.class.getResourceAsStream(resourcePath)) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
            NodeList pathList = document.getElementsByTagName("path");

            for (int i = 0; i < pathList.getLength(); i++) {
                Element pathElement = (Element) pathList.item(i);
                String id = pathElement.getAttribute("id");
                String d = pathElement.getAttribute("d");

                if (!id.isEmpty() && !d.isEmpty()) {
                    SVGPath svgPath = new SVGPath();
                    svgPath.setContent(d);
                    svgPath.setId(id);
                    svgPath.setStyle(STYLE_NORMAL);
                    svgPath.getProperties().put("selected", false);

                    // --- HOVER LOGIC ---
                    svgPath.setOnMouseEntered(event -> {
                        boolean isSelected = (boolean) svgPath.getProperties().get("selected");
                        svgPath.setStyle(isSelected ? STYLE_SELECTED_HOVER : STYLE_HOVER);
                        svgPath.toFront(); // Always bring to front on hover so borders aren't hidden
                    });

                    svgPath.setOnMouseExited(event -> {
                        boolean isSelected = (boolean) svgPath.getProperties().get("selected");
                        svgPath.setStyle(isSelected ? STYLE_SELECTED : STYLE_NORMAL);
                    });

                    // --- CLICK LOGIC ---
                    svgPath.setOnMouseClicked(event -> {
                        if (event.getButton() == MouseButton.PRIMARY) {
                            // Pass the clicked node back to the InteractiveMapPane
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
        node.setStyle(selected ? STYLE_SELECTED : STYLE_NORMAL);
    }
}
