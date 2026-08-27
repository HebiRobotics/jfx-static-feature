package us.hebi.graalvm.jfx.example;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * A context menu over an owner window, snapshotted through the popup's own scene rather than the owner's.
 *
 * @author Florian Enner
 * @since 27 Aug 2026
 */
class PopupMenu implements Scenario {

    private static final int OWNER_X = 80, OWNER_Y = 60, OWNER_WIDTH = 320, OWNER_HEIGHT = 200;
    private static final int MENU_INSET = 40;
    private static final int MIN_SIZE = 20;
    private static final Color OWNER = Color.web("#34495e");

    @Override
    public String name() {
        return "popup";
    }

    @Override
    public String skipReason() {
        return null;
    }

    @Override
    public WritableImage render() {
        Region root = new Region();
        Stage owner = new Stage();
        owner.setX(OWNER_X);
        owner.setY(OWNER_Y);
        owner.setScene(new Scene(root, OWNER_WIDTH, OWNER_HEIGHT, OWNER));
        owner.show();

        // show(node, x, y) takes screen coordinates, so the menu lands inside the owner wherever it opened
        ContextMenu menu = new ContextMenu(new MenuItem("Copy"), new MenuItem("Paste"));
        menu.show(root, OWNER_X + MENU_INSET, OWNER_Y + MENU_INSET);
        WritableImage image = menu.getScene().snapshot(null);
        menu.hide();
        owner.close();
        return image;
    }

    @Override
    public void check(WritableImage image, List<String> problems) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        if (width < MIN_SIZE || height < MIN_SIZE) {
            problems.add("the context menu is " + width + "x" + height + " rather than at least "
                    + MIN_SIZE + "x" + MIN_SIZE);
            return;
        }
        // A popup scene is transparent outside the menu, so its own corner is the background
        Color background = image.getPixelReader().getColor(0, 0);
        Pixels.checkForeground(problems, image, 0, 0, width, height, background, "the context menu");
    }

}
