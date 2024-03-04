package org.zstack.utils;

public class NameUtils {

    /**
     *  validate name
     *  1~128 characters, support uppercase and lowercase letters,
     *  digits, underscores, and hyphens; It can only start with
     *  uppercase and lowercase letters; It does not start or end with a space
     */
    public static boolean validateString(String name) {
        String pattern = "^[a-zA-Z][\\w-]{0,127}$";
        return name.matches(pattern);
    }
}
