/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.example.approval.common.utils;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import static java.util.Calendar.DATE;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DateTimeUtil {

    public static SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss aa");
    public static SimpleDateFormat dateTime24Formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    public static SimpleDateFormat dateTimeWithoutSecondFormatter = new SimpleDateFormat("dd-MM-yyyy HH:mm");
    public static SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy");
    public static SimpleDateFormat dateFormatterLocale = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    public static SimpleDateFormat timeFormatter = new SimpleDateFormat("hh:mm:s");
    public static SimpleDateFormat dateDayFormatter = new SimpleDateFormat("EEEEE");
    public static SimpleDateFormat dateShortDayFormatter = new SimpleDateFormat("EEE");
    public static final long DAY_IN_MILLIS = 1000 * 60 * 60 * 24;

    private static final Logger LOG = Logger.getLogger(DateTimeUtil.class.getName());

    private static final List<SimpleDateFormat> dateFormats = new ArrayList<SimpleDateFormat>() {
        {
            add(new SimpleDateFormat("M/dd/yyyy"));
            add(new SimpleDateFormat("dd.M.yyyy"));
            add(new SimpleDateFormat("dd.MMM.yyyy"));
            add(new SimpleDateFormat("dd-MMM-yyyy"));
            add(new SimpleDateFormat("dd-mm-yyyy"));
            add(new SimpleDateFormat("yyyy-mm-dd"));
            add(new SimpleDateFormat("yyyy-MMM-dd"));
            add(new SimpleDateFormat("dd-MM-yy"));
            add(new SimpleDateFormat("dd-MM-yyyy"));
            add(new SimpleDateFormat("MM-dd-yyyy"));
            add(new SimpleDateFormat("yyyy-MM-dd"));
        }
    };

    private static List<String> dateSeparatorList = new ArrayList<String>() {
        {
            add("-");
            add("/");
            add(" ");
        }
    };

    public static LocalDateTime validateDate(String date, String dateFormat, boolean withDate) {

        // remove separator from entered date as well as in format
        try {
            for (String dateSeparator : dateSeparatorList) {
                date = date.replaceAll("\\" + dateSeparator, "");
                dateFormat = dateFormat.replaceAll("\\" + dateSeparator, "");
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
            // insert space between time & date
            if (date.length() > 8) {
                if (withDate) {
                    StringBuilder builder = new StringBuilder(date);
                    builder.insert(8, " ");
//                    builder.insert(14, " ");
                    date = builder.toString();
                    formatter = DateTimeFormatter.ofPattern(dateFormat + " HH:mm");
                } else {
                    StringBuilder builder = new StringBuilder(date);
                    builder.insert(8, " ");
                    //                builder.insert(14, "");
                    date = date.substring(0, 8);
                    formatter = DateTimeFormatter.ofPattern(dateFormat);
                }

            } else {
                formatter = new DateTimeFormatterBuilder().append(formatter)
                        .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                        .toFormatter();
            }

            LocalDateTime localDateTime = LocalDateTime.parse(date, formatter);
            return localDateTime;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, e.getMessage(), e);
            return null;
        }
    }

    public static Date validateDate(String input) throws ParseException {
        if (null == input) {
            throw new ParseException("Entered Date is null", 0);
        }
        boolean valid = false;
        for (SimpleDateFormat format : dateFormats) {
            try {
                format.setLenient(false);
                Date date = format.parse(input);
                valid = true;
                return date;
            } catch (ParseException e) {
                valid = false;
            }

        }
        if (!valid) {
            throw new ParseException("Not a valid date", 0);
        }
        return null;
    }

    public static LocalDate convertToLocalDateViaMilisecond(Date dateToConvert) {
        return Instant.ofEpochMilli(dateToConvert.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static long getAge(Date birthDate) {
        return Period.between(convertToLocalDateViaMilisecond(birthDate), LocalDate.now()).getYears();
    }

    public static long getAge(String year) {
        LocalDate start = LocalDate.of(Integer.parseInt(year), 1, 1);
        return ChronoUnit.YEARS.between(start, LocalDate.now());
    }

    public static LocalDateTime getLocalDateTimeFromDate(Date date) {
        Instant current = date.toInstant();
        return LocalDateTime.ofInstant(current, ZoneId.systemDefault());
    }

    public static LocalDateTime getCurrentLocalDateDay() {
        LocalDateTime date = LocalDateTime.now();
        return date.truncatedTo(ChronoUnit.DAYS);

    }

    public static Date getTimeOnly(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.YEAR, 2000);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        return calendar.getTime();
    }

    public static int getCurrentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    public static String getYearOfDate(Date date) {
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return String.valueOf(localDate.getYear());
    }

    public static String getMonthOfDate(Date date) {

        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return String.valueOf(localDate.getMonth().getValue());

    }

    public static Date getCurrentDate() {
        return new Date();
    }

    public static String getCurrentDate(String formateDateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat(formateDateStr);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public static Date getDateOnly(Date date) {

        // reset the time in the date to compare dates only
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTime();
    }

    public static String getFormatedDateTime(Date date) {
        if (date == null) {
            return "";
        }
        return dateTimeFormatter.format(date);
    }

    public static String getFormatedDate(Date date) {
        if (date == null) {
            return "";
        }
        return dateFormatter.format(date);
    }

    public static String getFormatedDateLocale(Date date) {
        if (date == null) {
            return "";
        }
        return dateFormatterLocale.format(date);
    }

    public static String getFormatedTime(Date date) {
        if (date == null) {
            return "";
        }
        return timeFormatter.format(date);
    }

    public static int getDateDiff(Date startDate, Date endDate) {
        return (int) ((endDate.getTime() - startDate.getTime()) / DAY_IN_MILLIS);
    }

    public static String getDayName(Date date) {
        return dateShortDayFormatter.format(date);
    }

    public static String getDurationString(int seconds) {

        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        seconds = seconds % 60;

        return twoDigitString(hours) + " : " + twoDigitString(minutes);
    }

    private static String twoDigitString(int number) {

        if (number == 0) {
            return "00";
        }

        if (number / 10 == 0) {
            return "0" + number;
        }

        return String.valueOf(number);
    }

    public static Date getPasredDate(String dateStr) {

        try {
            return dateFormatterLocale.parse(dateStr);
        } catch (ParseException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return null;
    }
    
    public static Date getPasredDateTime(String dateStr) {

        try {
            return dateTimeFormatter.parse(dateStr);
        } catch (ParseException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return null;
    }

    public static Date getPasredDate24Time(String dateStr) {

        try {
            return dateTime24Formatter.parse(dateStr);
        } catch (ParseException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return null;
    }

    public static Date getPasredDateWithoutSecondTime(String dateStr) {

        try {
            return dateTimeWithoutSecondFormatter.parse(dateStr);
        } catch (ParseException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return null;
    }

    public static Date convertStringToDate(String dateStr) {

        try {
            return dateFormatterLocale.parse(dateStr);
        } catch (ParseException ex) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return null;
    }

    public static Timestamp convertStringToTimestamp(String str_date) {
        try {
            DateFormat formatter;
            formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            Date date = (Date) formatter.parse(str_date);
            Timestamp timeStampDate = new Timestamp(date.getTime());

            return timeStampDate;
        } catch (ParseException e) {

            return null;
        }
    }

    public static Timestamp convertStringTo24Timestamp(String str_date) {
        try {
            DateFormat formatter;
            formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = (Date) formatter.parse(str_date);
            Timestamp timeStampDate = new Timestamp(date.getTime());

            return timeStampDate;
        } catch (ParseException e) {

            return null;
        }
    }

    public static int getDiffYears(Date first, Date last) {
        Calendar a = getCalendar(first);
        Calendar b = getCalendar(last);
        int diff = b.get(YEAR) - a.get(YEAR);
        if (a.get(MONTH) > b.get(MONTH)
                || (a.get(MONTH) == b.get(MONTH) && a.get(DATE) > b.get(DATE))) {
            diff--;
        }
        return diff;
    }

    public static Calendar getCalendar(Date date) {
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.setTime(date);
        return cal;
    }

    public static int getDateYear(Date date) {
        Calendar cal = Calendar.getInstance(Locale.US);
        return cal.get(Calendar.YEAR);
    }

    public static String getCurrentDateTime() {
        return dateTimeFormatter.format(new Date());
    }

    public static List<String> getDateSeparatorList() {
        return dateSeparatorList;
    }

    public static void setDateSeparatorList(List<String> dateSeparatorList) {
        DateTimeUtil.dateSeparatorList = dateSeparatorList;
    }

    public static List<Date> getDaysBetweenDates(Date startdate, Date enddate) {
        List<Date> dates = new ArrayList<>();
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(startdate);

        while (calendar.getTime().before(enddate)) {
            Date result = calendar.getTime();
            dates.add(result);
            calendar.add(Calendar.DATE, 1);
        }
        return dates;
    }

    public static int getDayCode(Date date) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.DAY_OF_WEEK);
    }

    public static void main(String[] args) {

        List<Date> daysBetweenDates = getDaysBetweenDates(getPasredDate("1/1/2021"), getPasredDate("30/8/2021"));
        System.out.println("----------- " + daysBetweenDates.size());
    }

}
