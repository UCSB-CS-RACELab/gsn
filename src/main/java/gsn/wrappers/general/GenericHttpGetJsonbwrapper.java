
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
import java.net.*;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public class GenericHttpGetJsonbwrapper extends AbstractWrapper {
    private static final int DEFAULT_RATE = 20000; // time in milliseconds
    private static final int TWO_DAYS = 48*3600*1000; // two days in milliseconds
    private static final int ONE_DAY = 24*3600*1000; // one day in milliseconds
    private static int threadCounter = 0;
    private final transient Logger logger = Logger.getLogger(HttpGetJsonWrapper.class);
    
	private transient DataField [ ]   outputStructureCache                  = new DataField [ ] { new DataField( FIELD_NAME_json , "jsonb" , "json usage")};
	private static final String       FIELD_NAME_json                       = "JSONB";   
	private static final String [ ]   FIELD_NAMES                           = new String [ ] {FIELD_NAME_json};

    
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
                String data = responseStrBuilder.toString().toLowerCase();  
                StreamElement se = new StreamElement( FIELD_NAMES , new Byte [ ] { DataTypes.JSONB } , new Serializable [ ] { data
                }, System.currentTimeMillis( ) );
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
    public String getWrapperName() {
        return "Generic Http Get Jsonb Wrapper";
    }

    public void dispose() {
        threadCounter--;
    }

    public DataField[] getOutputFormat() {
        logger.info("getOutputFormat.");
        //if (outputStructure == null) {
            initialize();
        //}
        return outputStructureCache;
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


