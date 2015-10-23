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
 * File: src/gsn/wrappers/general/HttpGetWrapper.java
 *
 * @author Nevena Golubovic
 *
 */

package gsn.wrappers.general;

import gsn.beans.*;
import gsn.wrappers.*;
import org.apache.log4j.*;
import org.json.*;

import java.io.*;
import java.net.*;

public class GenericHttpGetWrapper extends AbstractWrapper {
    private static int threadCounter = 0;
    private final transient Logger logger = Logger.getLogger(HttpGetWrapper.class);
    private transient final DataField[] outputStructure = new DataField[]{new DataField("data", "varchar(10000)", "JPEG image from the remote networked camera.")};
    private int DEFAULT_RATE = 2000;
    private String urlPath;
    private HttpURLConnection httpURLConnection;
    private URL url;
    private AddressBean addressBean;
    private String inputRate;
    private int rate;
    BufferedReader streamReader = null;


    public boolean initialize() {
        this.addressBean = getActiveAddressBean();
        urlPath = this.addressBean.getPredicateValue("url");
        try {
            url = new URL(urlPath);
        } catch (MalformedURLException e) {
            logger.error("Loading the http wrapper failed : " + e.getMessage(), e);
            return false;
        }
        inputRate = this.addressBean.getPredicateValue("rate");
        if (inputRate == null || inputRate.trim().length() == 0) {
            rate = DEFAULT_RATE;
        } else {
            rate = Integer.parseInt(inputRate);
        }
        logger.debug("Calling URL: " + url + " at rate: " + rate);
        return true;
    }

    public void run() {
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream(1024 * 20);
        byte[] buffer = new byte[16 * 1024];
        BufferedInputStream content;
        while (isActive()) {
            try {
                Thread.sleep(rate);
                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.connect();
                logger.debug("response code: " + httpURLConnection.getResponseCode());
                streamReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), "UTF-8"));
                StringBuilder responseStrBuilder = new StringBuilder();
                String inputStr;
                while ((inputStr = streamReader.readLine()) != null)
                    responseStrBuilder.append(inputStr);
                try {
                    JSONObject jo = new JSONObject(responseStrBuilder.toString());
                    //logger.debug("object received: " + jo.toString());
                    postStreamElement(jo.toString());
                } catch (JSONException e) {
                    logger.error("JSON exception: " + e);
                }
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            } finally {
                httpURLConnection.disconnect();
                if(streamReader != null) {
                    try {
                        streamReader.close();
                    } catch (IOException e) {
                        logger.error("Couldn't close the stream reader: " + e);
                    }
                }
            }
        }
    }

    public String getWrapperName() {
        return "Generic Http Get Wrapper";
    }

    public void dispose() {
        threadCounter--;
    }

    public DataField[] getOutputFormat() {
        return outputStructure;
    }

}
