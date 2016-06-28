// given rate dynamic form URL parameters
    // extend this with given time shifts
// given old format and old value plus new format - convert the time to the new format
// other time utils

package gsn.utils;

import com.google.common.base.*;
import gsn.beans.*;
import org.apache.log4j.*;
import org.joda.time.*;
import org.joda.time.format.*;

import java.io.*;
import java.text.*;
import java.util.*;

public class TimeFormatting {
    private static final String GSN_DATE_TIME_FORMAT = "yyyy-MM-dd hh:mm:ss";
    private static final String EMPTY = "0000-00-00 00:00:00";
    private static Logger logger = Logger.getLogger(TimeFormatting.class);

    public static String timeOfMeasurement(DataField[] outputStructure, Serializable[] dataValueFields, String tomDateFormat, String tomTimeFormat, String tomDateField, String tomTimeField ) {
        DateTime finalDT = null;
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(GSN_DATE_TIME_FORMAT);

        // Date
        if(Strings.isNullOrEmpty(tomDateField) || Strings.isNullOrEmpty(tomDateFormat)) {
            logger.error("Measurement date field not specified.");
            return EMPTY;
        }

        int tomDateFieldIndex = -1;
        // Find the index in the outputStructure corresponding to the date field from the JSON response
        for (int i = 0; i < outputStructure.length; i++) {
            if (tomDateField.equalsIgnoreCase(outputStructure[i].getName())) {
                tomDateFieldIndex = i;
            }
        }
        String dateString = String.valueOf(dataValueFields[tomDateFieldIndex]);

        if(tomDateFormat.equalsIgnoreCase("epoch")) {
            return dateTimeFormatter.print(new DateTime(Long.parseLong(dateString)*1000L));
        }
        DateTimeFormatter measurementDateFormatter = DateTimeFormat.forPattern(tomDateFormat);
        DateTime d = measurementDateFormatter.parseDateTime(dateString);

        int year = d.getYear();
        int month = d.getMonthOfYear();
        int day = d.getDayOfMonth();

        // Time

        if(Strings.isNullOrEmpty(tomTimeField) || Strings.isNullOrEmpty(tomTimeFormat)) {
            finalDT = new DateTime(year, month, day, 0, 0, 0);
        } else {
            int tomTimeFieldIndex = -1;

            for (int i = 0; i < outputStructure.length; i++) {
                if (tomTimeField.equalsIgnoreCase(outputStructure[i].getName())) {
                    tomTimeFieldIndex = i;
                }
            }
            String timeString = String.valueOf(dataValueFields[tomTimeFieldIndex]);

            if(tomTimeFormat.equalsIgnoreCase("hhhh")) {
                long hour = Long.parseLong(timeString)/100;
                finalDT = new DateTime(year, month, day, (int) hour, 0, 0);
            } else {
                DateTimeFormatter measurementTimeFormatter = DateTimeFormat.forPattern(tomTimeFormat);
                DateTime t = measurementTimeFormatter.parseDateTime(timeString);
                int hour = t.getHourOfDay();
                int minute = t.getMinuteOfHour();
                int second = t.getSecondOfMinute();
                finalDT = new DateTime(year, month, day, hour, minute, second);
            }
        }
        return dateTimeFormatter.print(finalDT);
    }

    public static String convertTime(String oldFormat, String newFormat, String oldDate) throws ParseException {
        logger.info("from: " + oldFormat + " to: " + newFormat + " for the field: " + oldDate);
        SimpleDateFormat oldSdf = new SimpleDateFormat(oldFormat, Locale.US);
        SimpleDateFormat newSdf = new SimpleDateFormat(newFormat, Locale.US);
        Date date = oldSdf.parse(oldDate);
        String newDate = newSdf.format(date);
        logger.info("new date is: " + newDate);
        return newDate;
    }

    public static String convertTimeHour(String oldFormat, String dateInOldFormat, String hourInOldFormat, String newFormat) throws ParseException {
        logger.info("from: " + oldFormat + " to: " + newFormat + " for the date: " + dateInOldFormat + " and hour: " + hourInOldFormat);
        SimpleDateFormat oldSdf = new SimpleDateFormat(oldFormat);
        SimpleDateFormat newSdf = new SimpleDateFormat(newFormat);
        Date date = oldSdf.parse(dateInOldFormat);
        // this is just a hack, CIMIS lists hours as 1200 1300
        date.setHours(Integer.valueOf(hourInOldFormat) / 100); // check deprecated
        String newTime = newSdf.format(date);
        logger.info("new hourly date " + newTime);
        return newTime;
    }

}