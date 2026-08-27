package us.hebi.graalvm.jfx.example;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.DialogPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * An alert over an owner window. The OK and Cancel labels come out of the controls resource bundle, so the
 * button bar only has anything to show when the bundle made it into the image.
 *
 * @author Florian Enner
 * @since 27 Aug 2026
 */
class AlertDialog implements Scenario {

    private static final int OWNER_WIDTH = 320, OWNER_HEIGHT = 200;
    private static final int PANE_WIDTH = 360, PANE_HEIGHT = 160;
    // Modena lays the buttons out along the bottom of the pane, well inside this band
    private static final int BUTTON_BAND = 50;
    private static final Color OWNER = Color.web("#34495e");

    @Override
    public String name() {
        return "alert";
    }

    @Override
    public String skipReason() {
        return null;
    }

    @Override
    public WritableImage render() {
        Stage owner = new Stage();
        owner.setScene(new Scene(new Region(), OWNER_WIDTH, OWNER_HEIGHT, OWNER));
        owner.show();

        Alert alert = new Alert(AlertType.CONFIRMATION, "Static JavaFX");
        alert.initOwner(owner);
        DialogPane pane = alert.getDialogPane();
        pane.setPrefSize(PANE_WIDTH, PANE_HEIGHT);
        // showAndWait would spin a nested event loop that nothing closes
        alert.show();
        WritableImage image = pane.getScene().snapshot(null);
        alert.close();
        owner.close();
        return image;
    }

    @Override
    public void check(WritableImage image, List<String> problems) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        if (width < PANE_WIDTH || height < PANE_HEIGHT) {
            problems.add("the dialog pane is " + width + "x" + height + " rather than at least "
                    + PANE_WIDTH + "x" + PANE_HEIGHT);
            return;
        }
        Color background = image.getPixelReader().getColor(2, 2);
        if (background.getOpacity() < 1 || background.getBrightness() < 0.5) {
            problems.add("the dialog pane background at 2,2 is " + background + " rather than an opaque light fill");
        }
        // The buttons sit at the right, so the lower left corner is still the band's own background
        Color band = image.getPixelReader().getColor(2, height - 2);
        Pixels.checkForeground(problems, image, 0, height - BUTTON_BAND, width, BUTTON_BAND, band, "the button bar");
    }

}
