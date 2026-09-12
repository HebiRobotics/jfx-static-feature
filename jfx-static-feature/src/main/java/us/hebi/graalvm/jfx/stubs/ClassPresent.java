package us.hebi.graalvm.jfx.stubs;

import java.util.function.Predicate;

/**
 * Applies a condition only if the class actually exists.
 * Makes it more compatible across versions.
 *
 * @author Florian Enner
 * @since 09 Sep 2026
 */
final class ClassPresent implements Predicate<String> {

    @Override
    public boolean test(String className) {
        try {
            Class.forName(className, false, ClassPresent.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

}
