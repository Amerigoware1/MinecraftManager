module com.amerigoware.minecraftmanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;


    opens com.amerigoware.minecraftmanager to javafx.fxml;
    exports com.amerigoware.minecraftmanager;
}