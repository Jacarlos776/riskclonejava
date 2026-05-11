module com.mykogroup.riskclone {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.xml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;

    opens com.mykogroup.riskclone to javafx.fxml;
    opens com.mykogroup.riskclone.engine to com.fasterxml.jackson.databind;
    opens com.mykogroup.riskclone.model to com.fasterxml.jackson.databind;
    opens com.mykogroup.riskclone.network to com.fasterxml.jackson.databind;
    opens com.mykogroup.riskclone.network.payload to com.fasterxml.jackson.databind;

    exports com.mykogroup.riskclone;
    exports com.mykogroup.riskclone.network;
    exports com.mykogroup.riskclone.network.payload;
}
