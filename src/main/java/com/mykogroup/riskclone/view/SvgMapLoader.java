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

    public static Map<String, SVGPath> loadMap(String resourcePath) {
        Map<String, SVGPath> provinceNodes = new HashMap<>();

        try (InputStream is = SvgMapLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("Cannot find SVG file at: " + resourcePath);
            }

            // Standard Java XML Parsing
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(is);

            // Find all <path> elements
            NodeList pathList = document.getElementsByTagName("path");

            for (int i = 0; i < pathList.getLength(); i++) {
                Element pathElement = (Element) pathList.item(i);
                String id = pathElement.getAttribute("id");
                String d = pathElement.getAttribute("d");

                if (!id.isEmpty() && !d.isEmpty()) {
                    SVGPath svgPath = new SVGPath();
                    svgPath.setContent(d);
                    svgPath.setId(id);

                    // Set Default Styling
                    svgPath.setStyle("-fx-fill: lightgray; -fx-stroke: black; -fx-stroke-width: 1;");

                    provinceNodes.put(id, svgPath);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return provinceNodes;
    }
}
