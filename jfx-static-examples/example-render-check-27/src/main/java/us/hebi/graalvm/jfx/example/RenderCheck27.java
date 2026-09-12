package us.hebi.graalvm.jfx.example;

/**
 * The render check with the stages that do not compile against JavaFX 26. Built by the {@code fx27} profile
 * with a {@code -Djavafx.version} of 27 or newer, everything else is the base module's.
 *
 * @author Florian Enner
 * @since 12 Sep 2026
 */
public class RenderCheck27 {

    public static void main(String[] args) throws Exception {
        RenderCheck.run(args, new MediaQuery(), new RichText27());
    }

}
