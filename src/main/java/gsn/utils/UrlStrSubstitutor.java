package gsn.utils;

import com.google.common.base.*;
import org.apache.commons.lang.text.*;
import org.joda.time.*;
import org.joda.time.format.*;

import static com.google.common.base.Preconditions.*;


public class UrlStrSubstitutor extends StrSubstitutor {
    private static final String DATE = "date:";

   public static String substitute(String urlPattern) {
       StrSubstitutor ss = new StrSubstitutor(new StrLookup() {
           @Override
           public String lookup(String key) {
               if (key.startsWith(DATE)) {
                   int secondDelimiter = key.indexOf(':', DATE.length());
                   checkArgument(secondDelimiter >= 0, "Malformed date key.");
                   checkArgument(secondDelimiter < key.length() - 1, "Date format is missing.");
                   String periodString = key.substring(DATE.length(), secondDelimiter);
                   DateTime dateTime;
                   if (Strings.isNullOrEmpty(periodString)) {
                       dateTime = DateTime.now();
                   } else {
                       Period period = Period.parse(periodString);
                       dateTime = DateTime.now().plus(period);
                   }
                   String formatString = key.substring(secondDelimiter + 1);
                   DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(formatString);
                   return dateTimeFormatter.print(dateTime);
               }
               return null;
           }
       });
       return ss.replace(urlPattern);
   }
}
