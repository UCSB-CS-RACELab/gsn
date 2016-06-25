// given rate dynamic form URL parameters
    // extend this with given time shifts
// given old format and old value plus new format - convert the time to the new format
// other time utils

package gsn.utils;

import org.apache.log4j.*;

import java.text.*;
import java.util.*;

public class TimeFormatting {
    private static Logger logger = Logger.getLogger(TimeFormatting.class);
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
        date.setHours(Integer.valueOf(hourInOldFormat)/100); // check deprecated
        String newTime = newSdf.format(date);
        logger.info("new hourly date " + newTime);
        return newTime;
    }

}