package com.hklh8.client.utils;

public class PropertiesValue {

    public static String getStringValue(String key) {
        return SpringContext.getEnvironment().getProperty(key);
    }

    public static String getStringValue(String key, String defaultValue) {
        String value = getStringValue(key);
        if (value == null) {
            return defaultValue;
        } else {
            return value;
        }
    }

    public static int getIntValue(String key, int defaultValue) {
        String value = getStringValue(key);
        if (value != null) {
            return Integer.valueOf(value);
        }
        return defaultValue;
    }


    public static Boolean getBooleanValue(String key, Boolean defaultValue) {
        String value = getStringValue(key);
        if (value != null) {
            try {
                return Boolean.parseBoolean(value);
            } catch (Exception e) {
                e.printStackTrace();
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
