/**
 * Global Sensor Networks (GSN) Source Code
 * File: src/gsn/wrappers/general/GenericHttpGetWrapper.java
 *
 */

package gsn.wrappers.general;

import com.jayway.jsonpath.*;
import gsn.beans.*;
import gsn.utils.*;
import gsn.wrappers.*;
import org.apache.log4j.*;
import org.json.*;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

public class HttpGetJsonWrapper extends AbstractWrapper {
    private static final int DEFAULT_RATE = 3600 * 1000; // time in milliseconds
    private static final int TWO_DAYS = 48 * 3600 * 1000; // two days in milliseconds
    private static final int ONE_DAY = 24 * 3600 * 1000; // one day in milliseconds
    private static final int TWO_HOURS = 2 * 3600 * 1000; // one day in milliseconds
    private static final int ONE_HOUR = 3600 * 1000; // one day in milliseconds
    private static int threadCounter = 0;
    private final transient Logger logger = Logger.getLogger(HttpGetJsonWrapper.class);
    // unique date format for all the APIs
    //SimpleDateFormat sdfGSN = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String simpleDateFormatGsn = "yyyy-MM-dd HH:mm:ss";
    public static final String DAILY = "daily";
    public static final String HOURLY = "hourly";
    public static final String CURRENT = "current";
    private transient gsn.beans.DataField[] outputStructure = null;
    private String urlPath;
    private String restrictedPath = null;
    private boolean requestApiTimeOfMeasurement;
    private HttpURLConnection httpUrlConnection;
    private URL url;
    private AddressBean addressBean;
    private String inputRate;
    private int rate;
    private String requestFormat = null;
    private String requestParemeters = null;
    private String authorization = null;
    private String rateDynamic = null;
    private boolean alterUrl = false;
    private BufferedReader streamReader = null;
    private String attributesListString = null;

    public boolean initialize() {
        try {
            this.addressBean = getActiveAddressBean();
            inputRate = this.addressBean.getPredicateValue("rate");
            if (inputRate == null || inputRate.trim().length() == 0) {
                rate = DEFAULT_RATE;
            } else {
                rate = Integer.parseInt(inputRate);
            }
            restrictedPath = this.addressBean.getPredicateValue("restricted-path");
            logger.debug("restricted path: " + restrictedPath);
            urlPath = this.addressBean.getPredicateValue("url");
            requestFormat = this.addressBean.getPredicateValue("request-format");
            requestParemeters = this.addressBean.getPredicateValue("request-parameters");
            if (requestParemeters != null && requestParemeters.trim().length() != 0) {
                // TODO replace with URL builder class
                urlPath = urlPath + requestParemeters;
            }
            authorization = this.addressBean.getPredicateValue("authorization");

            String urlTime = manageUrlTime();
            try {
                logger.info("calling url: " + urlPath + urlTime);
                url = new URL(urlPath + urlTime);
            } catch (MalformedURLException e) {
                logger.error("Loading the http wrapper failed : " + e.getMessage(), e);
                return false;
            }
            // call the API and get the JSON structure from it.
            // Flatten it and initialize the OutputStructure.

            try {
                httpUrlConnection = (HttpURLConnection) url.openConnection();
                httpUrlConnection.setRequestMethod("GET");
                httpUrlConnection.setRequestProperty("Accept", requestFormat);
                if (authorization != null && authorization.trim().length() != 0) {
                    httpUrlConnection.setRequestProperty("Authorization", authorization);
                    logger.debug("Setting auth header to: " + authorization);
                }
                logger.info("connecting ...");
                httpUrlConnection.connect();
                logger.info("response code: " + httpUrlConnection.getResponseCode());
                streamReader = new BufferedReader(new InputStreamReader(httpUrlConnection.getInputStream(), "UTF-8"));
                StringBuilder responseStrBuilder = new StringBuilder();
                String inputStr;
                while ((inputStr = streamReader.readLine()) != null) {
                    responseStrBuilder.append(inputStr);
                }
                String data = responseStrBuilder.toString();
                extractAttributesForOutputStructure(data);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            } finally {
                httpUrlConnection.disconnect();
                if (streamReader != null) {
                    try {
                        streamReader.close();
                    } catch (IOException e) {
                        logger.error("Couldn't close the stream reader.", e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Initialize ", e);
        }
        return true;
    }

    public void run() {
        try {
            while (isActive()) {
                String urlTime = manageUrlTime();
                try {
                    logger.info("calling url: " + urlPath + urlTime);
                    url = new URL(urlPath + urlTime);
                } catch (MalformedURLException e) {
                    logger.error("Loading the http wrapper failed : " + e.getMessage(), e);
                    return;
                }

                try {

                    httpUrlConnection = (HttpURLConnection) url.openConnection();
                    httpUrlConnection.setRequestMethod("GET");
                    httpUrlConnection.setRequestProperty("Accept", requestFormat);
                    if (authorization != null && authorization.trim().length() != 0) {
                        httpUrlConnection.setRequestProperty("Authorization", authorization);
                        logger.info("apikey set to: " + authorization);
                    }
                    logger.info("connecting ...");
                    httpUrlConnection.connect();
                    logger.info("response code: " + httpUrlConnection.getResponseCode());
                    streamReader = new BufferedReader(new InputStreamReader(httpUrlConnection.getInputStream(), "UTF-8"));
                    StringBuilder responseStrBuilder = new StringBuilder();
                    String inputStr;
                    while ((inputStr = streamReader.readLine()) != null) {
                        responseStrBuilder.append(inputStr);
                    }
                    String data = responseStrBuilder.toString();
                    Serializable[] objects = extractAttributes(data);
                    logger.info("arrays: os:" + outputStructure.length + " o l " + objects.length);
                    StreamElement se = new StreamElement(outputStructure, objects);
                    postStreamElement(se);
                    logger.info("Wrapper active sleep for " + rate);
                    Thread.sleep(rate);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    logger.info("Wrapper active sleep for " + rate);
                    Thread.sleep(rate);
                } catch (InterruptedException e) {
                    logger.error(e.getMessage(), e);
                    logger.info("Wrapper active sleep for " + rate);
                    Thread.sleep(rate);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    logger.info("Wrapper active sleep for " + rate);
                    Thread.sleep(rate);
                } finally {
                    httpUrlConnection.disconnect();
                    if (streamReader != null) {
                        try {
                            streamReader.close();
                        } catch (IOException e) {
                            logger.error("Couldn't close the stream reader.", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("error in GSN run wrapper", e);
        }
    }

    private void extractAttributesForOutputStructure(final String data) {
        int j = 0;
        try {
            String s = JsonFlattener.encode(data).replace("{", "").replace("}", "").replace("\"", "");
            String[] entities = s.split("!!!");
            String[] jsonPathsFiledNames = new String[entities.length];
            String[] jsonFieldTypes = new String[entities.length];
            for (int i = 0; i < entities.length; i++) {
                String path = entities[i].split(":")[0].trim();
                logger.info("entity " + i + " : " + path);
                if (path.length() != 0) {
                    if (restrictedPath != null && !restrictedPath.isEmpty() && path.matches(restrictedPath)) {
                        logger.info("skipping " + path);
                        continue;
                    }
                    jsonPathsFiledNames[j] = "$." + path;
                    logger.debug("path " + jsonPathsFiledNames[j]);
                    String typeName = convertClassNameToGsnType(JsonPath.read(data, path).getClass().getName());
                    jsonFieldTypes[j] = typeName;
                    j++;
                }
            }
            // j is the numnber of entities in the json + 1 for data, so we add one more for measurement time
            outputStructure = new DataField[j];
            for (int k = 0; k < j - 1; k++) {
                String name = "_" + jsonPathsFiledNames[k].replaceAll("[\\.\\[\\$\\]]", "__");
                outputStructure[k] = new DataField(name.toUpperCase(), jsonFieldTypes[k], jsonPathsFiledNames[k]);
                logger.info("outputStructure: name: " + jsonPathsFiledNames[k] + " *** type: " + jsonFieldTypes[k]);
            }
            // outputStructure[j - 1] = new DataField("data", "varchar(100000)", "Entire response from a API.");
            outputStructure[j - 1] = new DataField("date_of_measurement", "varchar(100)", "Time when measurement occured");
            logger.debug("output structure size " + outputStructure.length);
            //logger.info("last " + outputStructure[j-1]);
        } catch (JSONException e) {
            logger.error("JsonE: ", e);
        }
    }

    private Serializable[] extractAttributes(final String data) {
        logger.debug("extracting attributes.");
        //logger.info("data " + data);
        Serializable[] dataValueFields = new Serializable[outputStructure.length];
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(data);
        for (int i = 0; i < outputStructure.length - 1; i++) {
            try {
                String str = outputStructure[i].getDescription().replace("\"", "");
                logger.debug("Extracting: " + str + " of type: " + outputStructure[i].getType());
                dataValueFields[i] = (Serializable) JsonPath.read(data, str);
                logger.info("field " + i + ": " + dataValueFields[i] + " type: " + dataValueFields[i].getClass().getName());
            } catch (Exception e) {
                dataValueFields[i] = "not found";
                logger.error(e.getMessage(), e);
            }
        }
        //dataValueFields[outputStructure.length - 2] = data;
        String dateInOldFormat = "";
        String hourInOldFormat = "";
        String tomField = this.addressBean.getPredicateValue("tom-field");
        String tomFormat = this.addressBean.getPredicateValue("tom-format");
        String tomDynamicRate = this.addressBean.getPredicateValue("rate-dynamic");
        String tomHourField = this.addressBean.getPredicateValue("tom-hour-field");
        if (!tomDynamicRate.isEmpty() && !tomField.isEmpty()) {
            dateInOldFormat = String.valueOf(dataValueFields[getTimeFieldIndex(tomField)]);
            if(tomDynamicRate.equalsIgnoreCase(DAILY)) {
                if (tomFormat.equalsIgnoreCase("epoch")) {
                    dataValueFields[outputStructure.length - 1] = String.valueOf((new SimpleDateFormat(simpleDateFormatGsn)).format(Long.valueOf(dateInOldFormat) * 1000));
                } else {
                    try {
                        dataValueFields[outputStructure.length - 1] = TimeFormatting.convertTime(tomFormat, simpleDateFormatGsn, dateInOldFormat);
                    } catch (ParseException e) {
                        logger.error("Error parsing the time field.", e);
                    }
                }
            } else if (tomDynamicRate.equalsIgnoreCase(HOURLY)) {
                hourInOldFormat = String.valueOf(dataValueFields[getTimeFieldIndex(tomHourField)]);
                try {
                    dataValueFields[outputStructure.length - 1] = TimeFormatting.convertTimeHour(tomFormat, dateInOldFormat, hourInOldFormat, simpleDateFormatGsn);
                } catch (ParseException e) {
                    logger.error("Error parsing the time and hour fields.", e);
                }
            } else if(tomDynamicRate.equalsIgnoreCase(CURRENT)) {
                if (tomFormat.equalsIgnoreCase("epoch")) {
                    dataValueFields[outputStructure.length - 1] = String.valueOf((new SimpleDateFormat(simpleDateFormatGsn)).format(Long.valueOf(dateInOldFormat) * 1000));
                } else {
                    try {
                        dataValueFields[outputStructure.length - 1] = TimeFormatting.convertTime(tomFormat, simpleDateFormatGsn, dateInOldFormat);
                    } catch (ParseException e) {
                        logger.error("Error parsing the time field.", e);
                    }
                }
            }
            logger.info("time of measurement is: " + dataValueFields[outputStructure.length - 1]);
        }
        logger.info("dataValueFields size is " + dataValueFields.length);
        return dataValueFields;
    }

    public String getWrapperName() {
        return "Http Get Json Wrapper";
    }

    public void dispose() {
        threadCounter--;
    }

    public DataField[] getOutputFormat() {
        logger.info("getOutputFormat.");
        if (outputStructure == null) {
            initialize();
        }
        return outputStructure;
    }

    public String convertClassNameToGsnType(String className) {
        String dataType = null;
        logger.debug("clas name: " + className);
        if (className.equalsIgnoreCase("java.lang.Integer")) {
            dataType = "Integer";
        } else if (className.equalsIgnoreCase("java.lang.Double")) {
            dataType = "Double";
        } else if (className.equalsIgnoreCase("java.lang.String")) {
            dataType = "varchar(100)";
        }
        logger.debug("type: " + dataType);
        return dataType;
    }

    /**
     * Find the index in the outputStructure corresponding to the date field from the JSON response
     */
    private int getTimeFieldIndex(String timeFieldName) {
        for (int i = 0; i < outputStructure.length; i++)
            if (timeFieldName.equalsIgnoreCase(outputStructure[i].getName())) {
                logger.debug("Index of date field is " + i);
                return i;
            }
        return -1;
    }

    // TODO parametrize time shift
    private String manageUrlTime() {
        String urlTime = "";
        rateDynamic = this.addressBean.getPredicateValue("rate-dynamic");
        if (rateDynamic != null && rateDynamic.trim().length() != 0) {
            if (!rateDynamic.isEmpty() && (rateDynamic.equalsIgnoreCase(DAILY) || rateDynamic.equalsIgnoreCase(HOURLY))) {
                String urlTimeFormat = this.addressBean.getPredicateValue("url-time-format");
                String urlTimeStartParameterName = this.addressBean.getPredicateValue("url-time-start-parameter-name");
                String urlTimeEndParameterName = this.addressBean.getPredicateValue("url-time-end-parameter-name");
                SimpleDateFormat sdf = new SimpleDateFormat(urlTimeFormat);

                Calendar cal = Calendar.getInstance();
                long startTime = cal.getTime().getTime() - TWO_DAYS;
                long endTime = cal.getTime().getTime() - ONE_DAY;
                String startDate = sdf.format(startTime);
                String endDate = sdf.format(endTime);
                urlTime = urlTimeStartParameterName + "=" + startDate + "&" + urlTimeEndParameterName + "=" + endDate;
            }
        }
        logger.info("urlTime: " + urlTime);
        return urlTime;
    }
}