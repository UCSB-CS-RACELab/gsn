package gsn.wrappers.general;
import com.jayway.jsonpath.*;

import gsn.beans.*;
import gsn.wrappers.*;
import gsn.utils.JsonFlattener;
import gsn.utils.ParamParser;

import org.apache.log4j.*;
import org.json.*;
import org.json.JSONObject;

import java.io.*;
import java.io.Serializable;
import java.lang.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.*;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;


public class HttpGetjsonbWrapper extends AbstractWrapper {
	 private static final int          DEFAULT_SAMPLING_RATE                 = 1000;
	   
	   private int                       samplingRate                          = DEFAULT_SAMPLING_RATE;
	   
	   private final transient Logger    logger                                = Logger.getLogger( MemoryMonitoringWrapper.class );
	   
	   private static int                threadCounter                         = 0;
	   
	   private transient DataField [ ]   outputStructureCache                  = new DataField [ ] { new DataField( FIELD_NAME_json , "jsonb" , "json usage")};
	   
	   private static final String       FIELD_NAME_json                       = "JSONB";
	   
	   private static final String [ ]   FIELD_NAMES                           = new String [ ] {FIELD_NAME_json};
	   
	   private int count = 0;
	   public boolean initialize ( ) {
	      AddressBean addressBean = getActiveAddressBean( );
	      if ( addressBean.getPredicateValue( "sampling-rate" ) != null ) {
	         samplingRate = ParamParser.getInteger( addressBean.getPredicateValue( "sampling-rate" ) , DEFAULT_SAMPLING_RATE );
	         if ( samplingRate <= 0 ) {
	            logger.warn( "The specified >sampling-rate< parameter for the >MemoryMonitoringWrapper< should be a positive number.\nGSN uses the default rate (" + DEFAULT_SAMPLING_RATE + "ms )." );
	            samplingRate = DEFAULT_SAMPLING_RATE;
	         }
	      }
	      return true;
	   }
	   
	   public void run ( ) {
	      while ( isActive( ) ) {
	         try {
	            Thread.sleep( samplingRate );
	         } catch ( InterruptedException e ) {
	            logger.error( e.getMessage( ) , e );
	         }
	         count++;
	         String jsonb_data = "{\"username\": \"varks\",\"posts\": {\"val\" :\"1002\"}}";
	         StreamElement streamElement = new StreamElement( FIELD_NAMES , new Byte [ ] { DataTypes.JSONB } , new Serializable [ ] { jsonb_data
	                }, System.currentTimeMillis( ) );
	         postStreamElement( streamElement );
	      }
	   }
	   
	   public void dispose ( ) {
	      threadCounter--;
	   }
	   
	   /**
	    * The output fields exported by this virtual sensor.
	    * 
	    * @return The strutcture of the output.
	    */
	   
	   public final DataField [ ] getOutputFormat ( ) {
	      return outputStructureCache;
	   }
	   
	   public String getWrapperName ( ) {
	      return "HTTP jsonb test wrapper";
	   }
}
