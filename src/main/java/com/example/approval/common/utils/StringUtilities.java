/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.common.utils;

import java.util.Collection;

/**
 *
 * @author ayasin
 */
public class StringUtilities {

    private StringUtilities() {
    }

    public static boolean isEmpty(String data) {
        if (data == null || data.trim().length() == 0) {
            return true;
        }
        return false;
    }

    public static boolean isNotEmpty(String data) {
        if (isEmpty(data)) {
            return false;
        }
        return true;
    }

    public static String intersperse(Collection<?> collection) {
        String delimiter = ",";
        StringBuilder sb = new StringBuilder();
        
        for (Object item : collection) {
            if (item == null) {
                continue;
            }
            sb.append(item).append(delimiter);
        }
        sb.setLength(sb.length() - delimiter.length());
        return sb.toString();
    }
}
