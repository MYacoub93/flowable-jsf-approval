/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.common.utils;


import java.util.Properties;
import java.util.Random;


public class CoreUtil {



//    public static String getBundle(String key, String locale, String... arguments) {
//        String messageString = getBundleKey(key, locale);
//        messageString = fillMessageArguments(messageString, arguments);
//
//        return messageString;
//    }

    public static String fillMessageArguments(String messageString, String... arguments) {
        if (arguments != null) {
            for (int i = 0; i < arguments.length; i++) {
                messageString = messageString.replace("{" + i + "}", arguments[i]);
            }
        }

        return messageString;
    }

    public static String getSaltString() {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 18) {
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        String saltStr = salt.toString();
        return saltStr;

    }

//    public static void main(String[] args) {
//        String bundle = getBundle("TEST", "en", "11111", "123123123132");
//        System.out.println("=========== " + bundle);
//
//    }

}
