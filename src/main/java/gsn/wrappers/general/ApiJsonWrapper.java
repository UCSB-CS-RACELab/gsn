package gsn.wrappers.general;

import com.google.common.base.*;
import com.jayway.jsonpath.*;
import gsn.beans.*;
import gsn.utils.*;
import gsn.wrappers.*;
import org.apache.commons.io.*;
import org.apache.log4j.*;

import java.io.*;
import java.io.InputStream;
import java.net.*;

public class ApiJsonWrapper extends AbstractWrapper {
    private static final Logger logger = Logger.getLogger(ApiJsonWrapper.class);
    private static final String WRAPPER_NAME = "ApiJsonWrapper";
    private static final String QUERING_FREQUENCY = "querying-frequency";
    private static final int DEFAULT_RATE = 3600 * 1000; // time in milliseconds

    private transient gsn.beans.DataField[] outputStructure = null;
    private URL url;
    private AddressBean addressBean;
    private long queryingFrequency;
    private String urlPattern;
    private String urlFinal;
    private String restrictedPath = null;
    private String authorization = null;
    private String tomDateFormat;
    private String tomDateField;
    private String tomTimeField;
    private String tomTimeFormat;

    @Override
    public DataField[] getOutputFormat() {
        logger.info("Getting output format.");
        if (outputStructure == null) {
            initialize();
        }
        return outputStructure;
    }

    @Override
    public boolean initialize() {
        this.addressBean = getActiveAddressBean();
        initializeParametersFromFile();
        try {
            logger.info("Calling url: " + urlFinal);
            url = new URL(urlFinal);
            // call the API and get the JSON structure from it.
            // Flatten it and initialize the OutputStructure.
            String data = makeHttpCall(url);
            extractAttributesForOutputStructure(data);
        } catch (Exception e) {
            logger.error("Initialization error. ", e);
            return false;
        }
        return true;
    }

    public void run() {
        while (isActive()) {
            try {
                urlFinal = UrlStrSubstitutor.substitute(urlPattern);
                logger.info("Calling url: " + urlFinal);
                url = new URL(urlFinal);
                String jsonData = makeHttpCall(url);
                Serializable[] objects = extractAttributes(jsonData);
                logger.info("Output structure size: " + outputStructure.length + ". Objects number:  " + objects.length);
                StreamElement se = new StreamElement(outputStructure, objects);
                postStreamElement(se);
            } catch (Exception e) {
                logger.error("Run: ", e);
            } finally {
                try {
                    Thread.sleep(queryingFrequency);
                } catch (InterruptedException e) {}
            }
        }
    }

    private String makeHttpCall(URL url) throws IOException {
        HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
        httpUrlConnection.setRequestMethod("GET");
        httpUrlConnection.setRequestProperty("Accept", "application/json; charset=utf-8");
        if (!Strings.isNullOrEmpty(authorization)) {
            logger.info("Setting authorization header: " + authorization);
            httpUrlConnection.setRequestProperty("Authorization", authorization);
        }
        logger.info("connecting ...");
        httpUrlConnection.connect();
        try (
                InputStream inputStream = httpUrlConnection.getInputStream();
                InputStream errorStream = httpUrlConnection.getErrorStream();
        ) {
            int responseCode = httpUrlConnection.getResponseCode();
            logger.info("response code: " + responseCode);
            if (responseCode == 200) {
                return IOUtils.toString(inputStream);
            } else {
                logger.error(IOUtils.toString(errorStream));
                throw new IOException("Unexpected http status code");
            }
        } finally {
            httpUrlConnection.disconnect();
        }
    }

    private void extractAttributesForOutputStructure(final String data) {
        int j = 0; // j is the number of entities in the json + 1 for the time-of-measurement
        try {
            String s = JsonFlattener.encode(data).replace("{", "").replace("}", "").replace("\"", "");
            String[] entities = s.split("!!!");
            String[] jsonPathsFiledNames = new String[entities.length];
            String[] jsonFieldTypes = new String[entities.length];
            for (int i = 0; i < entities.length; i++) {
                String path = entities[i].split(":")[0].trim();
                logger.debug("Entity " + i + " : " + path);
                if (path.length() != 0) {
                    if (restrictedPath != null && !restrictedPath.isEmpty() && path.matches(restrictedPath)) {
                        logger.debug("Skipping " + path);
                        continue;
                    }
                    jsonPathsFiledNames[j] = "$." + path;
                    logger.debug("Path " + jsonPathsFiledNames[j]);
                    String typeName = convertClassNameToGsnType(JsonPath.read(data, path).getClass().getName());
                    jsonFieldTypes[j] = typeName;
                    j++;
                }
            }
            outputStructure = new DataField[j+1];
            for (int k = 0; k < j; k++) {
                String name = "_" + jsonPathsFiledNames[k].replaceAll("[\\.\\[\\$\\]]", "__");
                outputStructure[k] = new DataField(name.toUpperCase(), jsonFieldTypes[k], jsonPathsFiledNames[k]);
                logger.debug("OutputStructure: Name: " + jsonPathsFiledNames[k] + " Type: " + jsonFieldTypes[k]);
            }
            outputStructure[j] = new DataField("date_of_measurement", "varchar(100)", "Time when measurement occurred.");
            logger.info("Output structure size: " + outputStructure.length);
        } catch (Exception e) {
            logger.error("JsonE: ", e);
        }
    }

    private Serializable[] extractAttributes(final String data) {
        logger.debug("extracting attributes.");
        //logger.info("data " + data);
        Serializable[] dataValueFields = new Serializable[outputStructure.length];
        String str = "";
        for (int i = 0; i < outputStructure.length - 1; i++) {
            try {
                str = outputStructure[i].getDescription().replace("\"", "");
                logger.debug("Extracting: " + str + " of type: " + outputStructure[i].getType());
                dataValueFields[i] = (Serializable) JsonPath.read(data, str);
                logger.debug("Field " + i + ": " + dataValueFields[i] + " type: " + dataValueFields[i].getClass().getName());
            } catch (Exception e) {
                dataValueFields[i] = "";
                logger.error(e.getMessage() + "str: " + str, e);
            }
        }
        String timeOfMeasurementValue = TimeFormatting.timeOfMeasurement(outputStructure, dataValueFields, tomDateFormat, tomTimeFormat, tomDateField, tomTimeField);
        dataValueFields[outputStructure.length - 1] = timeOfMeasurementValue;
        logger.info("dataValueFields size is " + dataValueFields.length);
        return dataValueFields;
    }

    public String convertClassNameToGsnType(String className) {
        String dataType = null;
        logger.debug("Class name: " + className);
        if (className.equalsIgnoreCase("java.lang.Integer")) {
            dataType = "Integer";
        } else if (className.equalsIgnoreCase("java.lang.Double")) {
            dataType = "Double";
        } else if (className.equalsIgnoreCase("java.lang.String")) {
            dataType = "varchar(100)";
        }
        logger.debug("Type: " + dataType);
        return dataType;
    }

    private void initializeParametersFromFile() {
        String qfString = this.addressBean.getPredicateValue(QUERING_FREQUENCY);
        if (qfString != null && !qfString.isEmpty()) {
            queryingFrequency = Long.valueOf(qfString);
        } else {
            queryingFrequency = DEFAULT_RATE;
        }
        urlPattern = this.addressBean.getPredicateValue("url");
        authorization = this.addressBean.getPredicateValue("authorization");
        tomDateFormat = this.addressBean.getPredicateValue("measurement-date-format");
        tomDateField = this.addressBean.getPredicateValue("measurement-date-field");
        tomTimeFormat = this.addressBean.getPredicateValue("measurement-time-format");
        tomTimeField = this.addressBean.getPredicateValue("measurement-time-field");
        restrictedPath = this.addressBean.getPredicateValue("restricted-path");
        logger.debug("restricted path: " + restrictedPath);
        urlFinal = UrlStrSubstitutor.substitute(urlPattern);
    }

    @Override
    public void dispose() {}

    @Override
    public String getWrapperName() {
        return WRAPPER_NAME;
    }
}