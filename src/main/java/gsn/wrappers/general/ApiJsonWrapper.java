package gsn.wrappers.general;

import gsn.beans.*;
import gsn.wrappers.*;
import org.apache.log4j.*;

import java.io.*;
import java.net.*;

//import org.apache.commons.lang3.text.StrLookup;
//import org.apache.commons.lang3.text.StrSubstitutor;


public class ApiJsonWrapper extends AbstractWrapper {
    private static final String WRAPPER_NAME = "ApiJsonWrapper";
    private static final Logger logger = Logger.getLogger(ApiJsonWrapper.class);
    private static final int DEFAULT_RATE = 3600 * 1000; // time in milliseconds
    private static final int DEFAULT_INTERVAL = 3600 * 1000; // time in milliseconds

    private static final String simpleDateFormatGsn = "yyyy-MM-dd HH:mm:ss";
    private static final String MEASUREMENT_FREQUENCY = "measurement-frequency";
    private static String UPDATE_FREQUENCY = "update-frequency";
    private static String QUERING_FREQUENCY = "querying-frequency";
    private static String REQUEST_INTERVAL = "request-interval";
    private long queryingFrequency;
    private long requestInterval;
    private transient gsn.beans.DataField[] outputStructure = null;
    private String urlPath;
    private String urlPattern;
    private String urlFinal;
    private String restrictedPath = null;
    private boolean requestApiTimeOfMeasurement;
    private HttpURLConnection httpUrlConnection;
    private URL url;
    private AddressBean addressBean;
    private String requestFormat = null;
    private String requestParameters = null;
    private String authorization = null;
    private BufferedReader streamReader = null;


    String tomFormat;
    String tomDateField;
    String tomHourField;


    @Override
    public DataField[] getOutputFormat() {
        return new DataField[0];
    }

    @Override
    public boolean initialize() {

        this.addressBean = getActiveAddressBean();
        initializeParametersFromFile();

        try {
            logger.info("calling url: ");
            url = new URL(urlFinal);
            // call the API and get the JSON structure from it.
            // Flatten it and initialize the OutputStructure.
            httpUrlConnection = (HttpURLConnection) url.openConnection();
            httpUrlConnection.setRequestMethod("GET");
            httpUrlConnection.setRequestProperty("Accept", "application/json; charset=utf-8");
            if (authorization != null && !authorization.isEmpty()) {
                httpUrlConnection.setRequestProperty("Authorization", authorization);
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
            // String data = responseStrBuilder.toString();
        } catch (MalformedURLException e) {
            logger.error("MalformedURLException : " + e.getMessage(), e);
            return false;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.error("Initialization error. ", e);
            return false;
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

    private void initializeParametersFromFile() {

        String qfString = this.addressBean.getPredicateValue(QUERING_FREQUENCY);
        if (qfString != null && !qfString.isEmpty()) {
            queryingFrequency = Long.valueOf(qfString);
        } else {
            queryingFrequency = DEFAULT_RATE;
        }

        String riString = this.addressBean.getPredicateValue(REQUEST_INTERVAL);
        if (riString != null && riString.isEmpty()) {
            requestInterval = Long.valueOf(riString);
        } else {
            requestInterval = DEFAULT_INTERVAL;
        }

        urlPattern = this.addressBean.getPredicateValue("url");
        authorization = this.addressBean.getPredicateValue("authorization");
        tomFormat = this.addressBean.getPredicateValue("tom-format");
        tomDateField = this.addressBean.getPredicateValue("tom-date-field");
        tomHourField = this.addressBean.getPredicateValue("tom-date-hour");
        restrictedPath = this.addressBean.getPredicateValue("restricted-path");
        logger.debug("restricted path: " + restrictedPath);
    }

    @Override
    public void dispose() {
    }

    @Override
    public String getWrapperName() {
        return WRAPPER_NAME;
    }
}
