package gsn.wrappers;

import gsn.beans.AddressBean;
import gsn.beans.DataField;
import gsn.beans.DataTypes;
import gsn.beans.StreamElement;
import gsn.utils.ParamParser;
import gsn.vsensor.Container;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.log4j.Logger;

/**
 * @author Ali Salehi (AliS, ali.salehi-at-epfl.ch)<br>
 */
public class GPSGenerator extends AbstractStreamProducer {
   
   private static final int         DEFAULT_SAMPLING_RATE = 1000;
   
   private static final String [ ]  FIELD_NAMES           = new String [ ] { "latitude" , "longitude" , "temperature" , "light" , "camera" };
   
   private static final Integer [ ] FIELD_TYPES           = new Integer [ ] { DataTypes.DOUBLE , DataTypes.DOUBLE , DataTypes.DOUBLE , DataTypes.INTEGER , DataTypes.BINARY };
   
   private static final String [ ]  FIELD_DESCRIPTION     = new String [ ] { "Latitude Reading" , "Longitude Reading" , "Temperature Sensor" , "Light Sensor" , "Camera Picture" };
   
   private static final String [ ]  FIELD_TYPES_STRING    = new String [ ] { "double" , "double" , "double" , "int" , "binary:jpeg" };
   
   private int                      samplingRate          = DEFAULT_SAMPLING_RATE;
   
   private final transient Logger   logger                = Logger.getLogger( GPSGenerator.class );
   
   private static int               threadCounter         = 0;
   
   private byte [ ]                 picture;
   
   private ArrayList < DataField >  outputStrcture        = new ArrayList < DataField >( );
   
   public Collection < DataField > getOutputFormat ( ) {
      return outputStrcture;
   }
   
   public boolean initialize ( TreeMap context ) {
      boolean toReturn = super.initialize( context );
      if ( toReturn == false ) return false;
      setName( "GPSGenerator-Thread" + ( ++threadCounter ) );
      AddressBean addressBean = ( AddressBean ) context.get( Container.STREAM_SOURCE_ACTIVE_ADDRESS_BEAN );
      if ( addressBean.getPredicateValue( "sampling-rate" ) != null ) {
         samplingRate = ParamParser.getInteger( addressBean.getPredicateValue( "rate" ) , DEFAULT_SAMPLING_RATE );
         if ( samplingRate <= 0 ) {
            logger.warn( "The specified >sampling-rate< parameter for the >MemoryMonitoringWrapper< should be a positive number.\nGSN uses the default rate (" + DEFAULT_SAMPLING_RATE + "ms )." );
            samplingRate = DEFAULT_SAMPLING_RATE;
         }
      }
      if ( addressBean.getPredicateValue( "picture" ) != null ) {
         String picture = addressBean.getPredicateValue( "picture" );
         File pictureF = new File( picture );
         if ( !pictureF.isFile( ) || !pictureF.canRead( ) ) {
            logger.warn( "The GPSGenerator can't access the specified picture file. Initialization failed." );
            return false;
         }
         try {
            BufferedInputStream fis = new BufferedInputStream( new FileInputStream( pictureF ) );
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            byte [ ] buffer = new byte [ 4 * 1024 ];
            while ( fis.available( ) > 0 )
               outputStream.write( buffer , 0 , fis.read( buffer )  );
            fis.close( );
            this.picture = outputStream.toByteArray( );
            outputStream.close( );
         } catch ( FileNotFoundException e ) {
            logger.warn( e.getMessage( ) , e );
            return false;
         } catch ( IOException e ) {
            logger.warn( e.getMessage( ) , e );
            return false;
         }
      } else {
         logger.warn( "The >picture< parameter is missing from the GPSGenerator wrapper." );
         return false;
      }
      for ( int i = 0 ; i < FIELD_NAMES.length ; i++ )
         outputStrcture.add( new DataField( FIELD_NAMES[ i ] , FIELD_TYPES_STRING[ i ] , FIELD_DESCRIPTION[ i ] ) );
      return true;
   }
   
   public void run ( ) {
      double latitude = 37.4419;
      double longitude = -122.1419;
      while ( isActive( ) ) {
         try {
            Thread.sleep( samplingRate );
         } catch ( InterruptedException e ) {
            logger.error( e.getMessage( ) , e );
         }
         if ( listeners.isEmpty( ) ) continue;
         
         StreamElement streamElement = new StreamElement( FIELD_NAMES , FIELD_TYPES , new Serializable [ ] { latitude , longitude , 25.5 , 650 , picture } );
         postStreamElement( streamElement );
      }
   }
   
   public void finalize ( HashMap context ) {
      super.finalize( context );
      threadCounter--;
   }
   
}