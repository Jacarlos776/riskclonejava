package com.mykogroup.riskclone.view;

import javafx.scene.shape.SVGPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class SvgMapLoader {

    // Define standard colors as constants for easy tweaking
    private static final String STYLE_NORMAL = "-fx-fill: #c2d5a8; -fx-stroke: #555555; -fx-stroke-width: 1;";
    private static final String STYLE_HOVER = "-fx-fill: #d3e5b9; -fx-stroke: #ffffff; -fx-stroke-width: 2.5;";

    public static Map<String, SVGPath> loadMap(String resourcePath) {
        Map<String, SVGPath> provinceNodes = new HashMap<>();

        try (InputStream is = SvgMapLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) throw new IllegalArgumentException("Cannot find SVG: " + resourcePath);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document document = factory.newDocumentBuilder().parse(is);
            NodeList pathList = document.getElementsByTagName("path");

            for (int i = 0; i < pathList.getLength(); i++) {
                Element pathElement = (Element) pathList.item(i);
                String id = pathElement.getAttribute("id");
                String d = pathElement.getAttribute("d");

                if (!id.isEmpty() && !d.isEmpty()) {
                    SVGPath svgPath = new SVGPath();
                    svgPath.setContent(d);
                    svgPath.setId(id);

                    // Set default appearance
                    svgPath.setStyle(STYLE_NORMAL);

                    // --- HOVER EVENTS ---

                    // When the mouse enters the province
                    svgPath.setOnMouseEntered(event -> {
                        svgPath.setStyle(STYLE_HOVER);
                        svgPath.toFront(); // Brings the border above neighboring provinces so it doesn't get hidden underneath neighboring provinces
                    });

                    // When the mouse leaves the province
                    svgPath.setOnMouseExited(event -> {
                        svgPath.setStyle(STYLE_NORMAL);
                    });

                    provinceNodes.put(id, svgPath);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return provinceNodes;
    }
}
