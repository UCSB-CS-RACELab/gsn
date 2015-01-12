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
 * File: src/gsn/http/PostGisImages.java
 *
 * @author Nevena Golubovic
 *
 */

package gsn.http;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class PostGisImages {
    private static transient Logger logger = Logger.getLogger(PostGisImages.class);
    private static final int SRID = 4326;

    // populate DB with some values
    public static void populate() {
        try {
            Connection conn = GetSensorDataWithGeoPostGIS.connect();
            String st_create_table = "DROP INDEX IF EXISTS gist_images;"
                    + " DROP TABLE IF EXISTS images;"
                    + " CREATE EXTENSION IF NOT EXISTS postgis;"
                    + " CREATE TABLE images ( \"id\" int4 primary key, \"location\" geometry NOT NULL, url text);"
                    + " CREATE INDEX gist_images ON images USING GIST ( location ); ";
            logger.info("Running query: " + st_create_table);
            PreparedStatement preparedStatement = conn.prepareStatement(st_create_table);
            preparedStatement.execute();
            preparedStatement.close();
            // coordinates for the images
            String[] coordinates = {"(1 0, 3 0, 3 2, 1 2, 1 0)",
                                    "(2 1, 4 1, 4 3, 2 3, 2 1)",
                                    "(5 1, 7 1, 7 3, 5 3, 5 1)"};
            String[] urls = {"https://s3-us-west-1.amazonaws.com/gsn.postgis/img1.jpeg",
                    "https://s3-us-west-1.amazonaws.com/gsn.postgis/img2.jpeg",
                    "https://s3-us-west-1.amazonaws.com/gsn.postgis/img3.jpeg"};
            for (int i = 0; i < coordinates.length; i++) {
                String insert = "insert into images values ( '" + i + "'," +
                        " ST_MakePolygon(ST_GeomFromText('LINESTRING" + coordinates[i] + "', " + SRID + ")), '" + urls[i] + "');";
                PreparedStatement ps = conn.prepareStatement(insert);
                ps.execute();
                ps.close();
                logger.info(insert);
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            logger.warn(e.getMessage(), e);
        } catch (IOException e) {
            logger.warn(e.getMessage(), e);
        }
    }

    public static void query(double lat, double lon) {
        // ST_Contains(geometry, geometry)
        // Returns true if the first geometry fully contains the second geometry, otherwise false.
        // SELECT url, st_contains(location, ST_GeomFromText('POINT(1.3 1.2)', 4326))  FROM images;
    }

    public static String getImagesForLocation(Double lat, Double lon) {
        populate();
        //query();
        return "";
    }
}
