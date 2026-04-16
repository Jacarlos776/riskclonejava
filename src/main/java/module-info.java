module com.mykogroup.riskclone {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;

    opens com.mykogroup.riskclone to javafx.fxml;
    exports com.mykogroup.riskclone;
}