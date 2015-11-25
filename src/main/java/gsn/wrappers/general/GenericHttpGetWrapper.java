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
import org.apache.log4j.*;
import org.json.*;
import org.json.JSONObject;

import java.io.*;
import java.io.Serializable;
import java.lang.*;
import java.lang.Exception;
import java.lang.Object;
import java.lang.String;
import java.net.*;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public class GenericHttpGetWrapper extends AbstractWrapper {
    private static final int DEFAULT_RATE = 10000; // time in milliseconds
    private static final int TWO_DAYS = 48*3600*1000; // two days in milliseconds
    private static int threadCounter = 0;
    private final transient Logger logger = Logger.getLogger(HttpGetWrapper.class);
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
    private BufferedReader streamReader = null;
    private String attributesListString = null;

    public boolean initialize() {
        this.addressBean = getActiveAddressBean();
        urlPath = this.addressBean.getPredicateValue("url");

        inputRate = this.addressBean.getPredicateValue("rate");
        if (inputRate == null || inputRate.trim().length() == 0) {
            rate = DEFAULT_RATE;
        } else {
            rate = Integer.parseInt(inputRate);
        }
        requestFormat = this.addressBean.getPredicateValue("request-format");
        requestParemeters = this.addressBean.getPredicateValue("request-parameters");
        if(requestParemeters != null && requestParemeters.trim().length() != 0) {
            // TODO check for URL builder class
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

        attributesListString = this.addressBean.getPredicateValue("attributes");
        if(attributesListString != null && attributesListString.trim().length() != 0) {
            initializeOtputStructure(attributesListString);
        } else {
            //    throw new IllegalStateException("missing attributes");
            outputStructure = new DataField[]{
                    new DataField("data", "varchar(10000)", "Entire response from a API.")};
        }
        try {
            logger.info("calling url: " + urlPath);
            url = new URL(urlPath);
        } catch (MalformedURLException e) {
            logger.error("Loading the http wrapper failed : " + e.getMessage(), e);
            return false;
        }
        return true;
    }

    public void run() {
        while (isActive()) {
            try {
                Thread.sleep(rate);
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

    private void initializeOtputStructure(String attributesListString) {
        logger.info("initialize otput structure");
        String[] attr = attributesListString.split(",");
        outputStructure = new DataField[attr.length + 1];
        int i = 0;
        outputStructure[i++] = new DataField("data", "varchar(10000)", "Entire response from a API.");
        for (String s : attr) {
            String[] row = s.split(":");
            outputStructure[i++] = new DataField(row[0], row[1], row[2]);
        }
        logger.debug("out str size is " + outputStructure.length);
    }

    private Serializable[] extractAttributes(final String data) {
        logger.info("extracting attributes.");
        logger.info("data " + data);
        Serializable[] dataValueFields = new Serializable[outputStructure.length];
        dataValueFields[0] = data;
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(data);
        for (int i = 1; i < outputStructure.length; i++) {
            try {
                dataValueFields[i] = (Serializable) JsonPath.read(data, outputStructure[i].getDescription());
                logger.info("field " + i + " " + dataValueFields[i]);
            } catch (Exception e) {
                dataValueFields[i] = "not found";
                logger.error(e.getMessage(), e);
            }
        }
        logger.info("data v f size is " + dataValueFields.length);
        return dataValueFields;
    }

    public String getWrapperName() {
        return "Generic Http Get Wrapper";
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
}
