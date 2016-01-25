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
    private static final int DEFAULT_RATE = 20000; // time in milliseconds
    private static final int TWO_DAYS = 48*3600*1000; // two days in milliseconds
    private static final int ONE_DAY = 24*3600*1000; // one day in milliseconds
    private static int threadCounter = 0;
    private final transient Logger logger = Logger.getLogger(HttpGetJsonWrapper.class);
    private transient DataField[] outputStructure = null;
    private String urlPath;
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
        this.addressBean = getActiveAddressBean();
        inputRate = this.addressBean.getPredicateValue( "rate" );
        if ( inputRate == null || inputRate.trim( ).length( ) == 0 ) {
            rate = DEFAULT_RATE;
        }
        else {
            rate = Integer.parseInt(inputRate);
        }
        urlPath = this.addressBean.getPredicateValue("url");
        requestFormat = this.addressBean.getPredicateValue("request-format");
        requestParemeters = this.addressBean.getPredicateValue("request-parameters");
        if (requestParemeters != null && requestParemeters.trim().length() != 0) {
            // TODO replace with URL builder class
            urlPath = urlPath + requestParemeters;
        }
        rateDynamic = this.addressBean.getPredicateValue("rate-dynamic");
        if(rateDynamic != null && rateDynamic.trim().length() != 0) {
            if(rateDynamic.equalsIgnoreCase("daily")){
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Calendar cal = Calendar.getInstance();
                String date = sdf.format(cal.getTime().getTime() - TWO_DAYS);
                logger.info("date: " + date);
                urlPath = urlPath + "&startDate=" + date + "&endDate=" + date;
            } else {
                // TODO handle hourly calls
            }
        }
        try {
            logger.info("calling url: " + urlPath);
            url = new URL(urlPath);
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
        return true;
    }

    public void run() {
        while (isActive()) {
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
                StreamElement se = new StreamElement(outputStructure, objects);
                postStreamElement(se);
                Thread.sleep(rate);
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
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
                    jsonPathsFiledNames[j] = "$." + path;
                    logger.debug("path " + jsonPathsFiledNames[j]);
                    String typeName = convertClassNameToGsnType(JsonPath.read(data, path).getClass().getName());
                    jsonFieldTypes[j] = typeName;
                    j++;
                }
            }
            outputStructure = new DataField[j];
            for (int k = 0; k < j - 1; k++) {
                String name = "_" + jsonPathsFiledNames[k].replaceAll("[\\.\\[\\$\\]]","__");
                outputStructure[k] = new DataField(name.toUpperCase(), jsonFieldTypes[k], jsonPathsFiledNames[k]);
                logger.info("outputStructure: name: " + jsonPathsFiledNames[k] + " *** type: " + jsonFieldTypes[k]);
            }
            outputStructure[j - 1] = new DataField("data", "varchar(100000)", "Entire response from a API.");
        } catch (JSONException e) {
            logger.error("JsonE: ", e);
        }
    }

    private Serializable[] extractAttributes(final String data) {
        logger.info("extracting attributes.");
        logger.info("data " + data);
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
        dataValueFields[outputStructure.length - 1] = data;
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
            // dataType = DataTypes.VARCHAR_NAME;
            dataType = "varchar(100)";
        }
        logger.debug("type: " + dataType);
        return dataType;
    }
}
