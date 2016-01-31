/**
 * Global Sensor Networks (GSN) Source Code
 * Copyright (c) 2006-2014, Ecole Polytechnique Federale de Lausanne (EPFL)
 *
 * This file is part of GSN.
 *
 * GSN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GSN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GSN.  If not, see <http://www.gnu.org/licenses/>.
 *
 * File: src/gsn/wrappers/general/GenericHttpGetWrapper.java
 *
 */

package gsn.wrappers.general;

import com.jayway.jsonpath.*;

import gsn.beans.*;
import gsn.wrappers.*;
import gsn.utils.JsonFlattener;
import org.apache.log4j.*;
import org.json.*;
import org.json.JSONObject;

import java.io.*;
import java.io.Serializable;
import java.lang.*;
import java.lang.Exception;
import java.lang.InterruptedException;
import java.lang.Object;
import java.lang.String;
import java.lang.Thread;
import java.net.*;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public class HttpGetJsonWrapper extends AbstractWrapper {
    private static final int DEFAULT_RATE = 3600*1000; // time in milliseconds
    private static final int TWO_DAYS = 48*3600*1000; // two days in milliseconds
    private static final int ONE_DAY = 24*3600*1000; // one day in milliseconds
    private static int threadCounter = 0;
    private final transient Logger logger = Logger.getLogger(HttpGetJsonWrapper.class);
    private transient DataField[] outputStructure = null;
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
            urlPath = this.addressBean.getPredicateValue("url");
            requestFormat = this.addressBean.getPredicateValue("request-format");
            requestParemeters = this.addressBean.getPredicateValue("request-parameters");
            if (requestParemeters != null && requestParemeters.trim().length() != 0) {
                // TODO replace with URL builder class
                urlPath = urlPath + requestParemeters;
            }


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
        } catch (Exception e ) {
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
                    logger.info("arrays: os:" + outputStructure.length + " o l " +  objects.length);
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
                logger.debug("entity " + i + " : " + path);
                if (path.length() != 0) {
                    if(restrictedPath != null && !restrictedPath.isEmpty() && path.contains(restrictedPath)) {
                        logger.debug("skipping " + path);
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
                String name = "_" + jsonPathsFiledNames[k].replaceAll("[\\.\\[\\$\\]]","__");
                outputStructure[k] = new DataField(name.toUpperCase(), jsonFieldTypes[k], jsonPathsFiledNames[k]);
                logger.info("outputStructure: name: " + jsonPathsFiledNames[k] + " *** type: " + jsonFieldTypes[k]);
            }
            // outputStructure[j - 1] = new DataField("data", "varchar(100000)", "Entire response from a API.");
            outputStructure[j-1] = new DataField("date_of_measurement", "varchar(100)", "Time when measurement occured");
            logger.info("output structure size " + outputStructure.length);
            //logger.info("last " + outputStructure[j-1]);
        } catch (JSONException e) {
            logger.error("JsonE: ", e);
        }
    }

    private Serializable[] extractAttributes(final String data) {
        logger.debug("extracting attributes.");
        logger.debug("data " + data);
        Serializable[] dataValueFields = new Serializable[outputStructure.length];
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(data);
        for (int i = 0; i < outputStructure.length - 1; i++) {
            try {
                String str = outputStructure[i].getDescription().replace("\"", "");
                logger.debug("Extracting: " + str + " of type: " + outputStructure[i].getType());
                dataValueFields[i] = (Serializable) JsonPath.read(data, str);
                logger.debug("field " + i + ": " + dataValueFields[i] + " type: " + dataValueFields[i].getClass().getName());
            } catch (Exception e) {
                dataValueFields[i] = "not found";
                logger.error(e.getMessage(), e);
            }
        }
        //dataValueFields[outputStructure.length - 2] = data;

        // formatting time
        // CIMIS daily
        String epochInOldFormat = "";
        String dateInOldFormat = "";
        String hourInOldFormat = "";

        if(!getTimeDateFieldName().isEmpty()) {
            dateInOldFormat = String.valueOf(dataValueFields[getDateFieldIndex()]);
        }

        if(!getTimeHourFieldName().isEmpty()) {
            hourInOldFormat = String.valueOf(dataValueFields[getHourFieldIndex()]);
        }

        if(!getTimeEpochFieldName().isEmpty()) {
            epochInOldFormat = String.valueOf(dataValueFields[getEpochFieldIndex()]);
        }

        logger.info("old format date " + dateInOldFormat + " old hour format " + hourInOldFormat + "old epoch format " + epochInOldFormat);
        dataValueFields[outputStructure.length - 1] = manageTimeFormat(dateInOldFormat, hourInOldFormat, epochInOldFormat);
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

// if format-time true:

    /**
     * Find the index in the outputStructure corresponding to the date field from the JSON response
     * */
    private int getDateFieldIndex() {
        String timeFieldName = getTimeDateFieldName();
        for (int i = 0; i < outputStructure.length; i++)
            if(timeFieldName.equalsIgnoreCase(outputStructure[i].getName())) {
                logger.info("Index of date field is " + i);
                return i;
            }
        return -1;
    }

    private int getHourFieldIndex() {
        String timeFieldName = getTimeHourFieldName();
        for (int i = 0; i < outputStructure.length; i++)
            if(timeFieldName.equalsIgnoreCase(outputStructure[i].getName())) {
                logger.info("Index of date field is " + i);
                return i;
            }
        return -1;
    }

    private int getEpochFieldIndex() {
        String timeFieldName = getTimeEpochFieldName();
        for (int i = 0; i < outputStructure.length; i++)
            if(timeFieldName.equalsIgnoreCase(outputStructure[i].getName())) {
                logger.info("Index of epoch field is " + i);
                return i;
            }
        return -1;
    }


    /**
     * From the XML specifiction return the Date format for the API response
     * */
    private SimpleDateFormat getTimeFormat() {
        SimpleDateFormat sdfGSN = new SimpleDateFormat(this.addressBean.getPredicateValue("time-date-format"));
        return sdfGSN;
    }

    private String getTimeDateFieldName() {
        String fieldName = this.addressBean.getPredicateValue("time-date-field-name");
        logger.info("Time date field name is: " + fieldName);
        return (fieldName == null) ? "" : fieldName;
    }

    private String getTimeHourFieldName() {
        String fieldName = this.addressBean.getPredicateValue("time-hour-field-name");
        logger.info("Time hour field name is: " + fieldName);
        return (fieldName == null) ? "" : fieldName;
    }

    private String getTimeEpochFieldName() {
        String fieldName = this.addressBean.getPredicateValue("time-epoch-field-name");
        logger.info("Time epoch field name is: " + fieldName);
        return (fieldName == null) ? "" : fieldName;
    }

    private String manageTimeFormat(String dateInOldFormat, String hourInOldFormat, String epochInOldFormat) {
        SimpleDateFormat originalApiFormat;
        String timeOfMeasurement = "";
        // TODO  replace with getTimeFormat
        SimpleDateFormat sdfGSN = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            if (rateDynamic.equalsIgnoreCase("cimis-daily")) {
                originalApiFormat = new SimpleDateFormat("yyyy-MM-dd");
                logger.info("old date: " + dateInOldFormat);
                Date date = originalApiFormat.parse(dateInOldFormat);
                logger.info(date.toString());
                timeOfMeasurement = sdfGSN.format(date);
                logger.info("new daily date " + timeOfMeasurement);
            } else if (rateDynamic.equalsIgnoreCase("cimis-hourly")) {
                originalApiFormat = new SimpleDateFormat("yyyy-MM-dd"); // getOutputFormat()
                logger.info("old date: " + dateInOldFormat);
                Date date = originalApiFormat.parse(dateInOldFormat);
                // this is just a hack, CIMIS lists hours as 1200 1300
                logger.info("hours string " + hourInOldFormat);
                date.setHours(Integer.valueOf(hourInOldFormat)/100); // check deprecated
                logger.info(date.toString());
                timeOfMeasurement = sdfGSN.format(date);
                logger.info("new hourly date " + timeOfMeasurement);
            } else if (rateDynamic.equalsIgnoreCase("wu-current")){
                timeOfMeasurement = sdfGSN.format(Long.valueOf(epochInOldFormat)*1000);
                logger.info("new wu date " + timeOfMeasurement);
            } else if (rateDynamic.equalsIgnoreCase("wu-daily")){
                originalApiFormat = new SimpleDateFormat("MMMM dd, yyyy"); // getOutputFormat()
                logger.info("old date: " + dateInOldFormat);
                Date date = originalApiFormat.parse(dateInOldFormat);
                // this is just a hack, CIMIS lists hours as 1200 1300
                logger.info("hours string " + hourInOldFormat);
                logger.info(date.toString());
                timeOfMeasurement = sdfGSN.format(date);
            }
        } catch (Exception e) {
            logger.error("Error parsing the time field.", e);
        }
        return timeOfMeasurement;
    }

    private String manageUrlTime() {
        String urlTime = "";
        rateDynamic = this.addressBean.getPredicateValue("rate-dynamic");
        if (rateDynamic != null && rateDynamic.trim().length() != 0) {
            if (rateDynamic.equalsIgnoreCase("cimis-daily") || rateDynamic.equalsIgnoreCase("cimis-hourly")) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Calendar cal = Calendar.getInstance();
                long timeToMeasure = cal.getTime().getTime() - TWO_DAYS;
                String date = sdf.format(timeToMeasure);
                logger.info("date: " + date);
                urlTime = "&startDate=" + date + "&endDate=" + date;

            } else {
                // TODO handle hourly calls
            }
        }
        return urlTime;
    }
}
