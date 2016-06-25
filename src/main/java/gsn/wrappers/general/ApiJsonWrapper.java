package gsn.wrappers.general;

import gsn.beans.*;
import gsn.wrappers.*;
import org.apache.log4j.*;

public class ApiJsonWrapper extends AbstractWrapper{
    private static final String WRAPPER_NAME = "ApiJsonWrapper";
    private static final Logger logger = Logger.getLogger(ApiJsonWrapper.class);

    private static final String simpleDateFormatGsn = "yyyy-MM-dd HH:mm:ss";
    private static String MEASUREMENT_FREQUENCY;
    private static String UPDATE_FREQUENCY;
    private static String QUERING_FREQUENCY;
    private static String REQUEST_FREQUENCY;

    private transient gsn.beans.DataField[] outputStructure = null;

    @Override
    public DataField[] getOutputFormat() {
        return new DataField[0];
    }

    @Override
    public boolean initialize() {
        return false;
    }

    @Override
    public void dispose() {
    }

    @Override
    public String getWrapperName() {
        return WRAPPER_NAME;
    }
}
